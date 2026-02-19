#include "GmeDecoder.h"
#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <limits>
#include <vector>

extern "C" {
#include <gme/gme.h>
}

#define LOG_TAG "GmeDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {
constexpr int kRenderBlockFrames = 512;
constexpr int kUnknownTaggedDurationMs = 150000;

std::string safeString(const char* value) {
    return value ? std::string(value) : "";
}

std::string toLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

int resolveLoopStartMs(const gme_info_t* info) {
    if (!info || info->loop_length <= 0) {
        return -1;
    }
    if (info->intro_length >= 0) {
        return info->intro_length;
    }
    if (info->play_length > info->loop_length) {
        return info->play_length - info->loop_length;
    }
    if (info->length > info->loop_length) {
        return info->length - info->loop_length;
    }
    return -1;
}

bool isLikelyUnknownDuration(const gme_info_t* info) {
    if (!info) return false;
    if (info->play_length != kUnknownTaggedDurationMs) {
        return false;
    }
    const bool hasExplicitLength = info->length > 0 && info->length != kUnknownTaggedDurationMs;
    const bool hasIntroLoop = info->intro_length > 0 && info->loop_length > 0;
    return !hasExplicitLength && !hasIntroLoop;
}

int resolveDurationMs(const gme_info_t* info, int fallbackDurationMs, bool* reliableOut) {
    if (!info) {
        if (reliableOut) *reliableOut = false;
        return 0;
    }

    if (isLikelyUnknownDuration(info)) {
        if (reliableOut) *reliableOut = false;
        return std::max(0, fallbackDurationMs);
    }

    int durationMs = info->play_length;
    if (durationMs <= 0) durationMs = info->length;
    if (durationMs <= 0 && info->intro_length > 0 && info->loop_length > 0) {
        durationMs = info->intro_length + (info->loop_length * 2);
    }
    if (reliableOut) *reliableOut = durationMs > 0;
    return durationMs;
}

double parseDoubleString(const std::string& value, double fallback) {
    char* end = nullptr;
    const double parsed = std::strtod(value.c_str(), &end);
    if (end == value.c_str() || (end != nullptr && *end != '\0')) {
        return fallback;
    }
    return parsed;
}

bool parseBoolString(const std::string& value, bool fallback) {
    const std::string normalized = toLowerAscii(value);
    if (normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on") return true;
    if (normalized == "0" || normalized == "false" || normalized == "no" || normalized == "off") return false;
    return fallback;
}

bool hasExtension(const char* path, const char* extensionWithDot) {
    if (!path || !extensionWithDot) return false;
    std::string lowerPath(path);
    lowerPath = toLowerAscii(lowerPath);
    std::string lowerExt(extensionWithDot);
    lowerExt = toLowerAscii(lowerExt);
    if (lowerPath.size() < lowerExt.size()) return false;
    return lowerPath.compare(lowerPath.size() - lowerExt.size(), lowerExt.size(), lowerExt) == 0;
}
}

GmeDecoder::GmeDecoder() = default;

GmeDecoder::~GmeDecoder() {
    close();
}

bool GmeDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternal();

    if (!path) {
        return false;
    }

    const int openSampleRate = resolveOpenSampleRateLocked(path);
    const gme_err_t openErr = gme_open_file(path, &emu, openSampleRate);
    if (openErr != nullptr || emu == nullptr) {
        LOGE("gme_open_file failed: %s", openErr ? openErr : "unknown error");
        emu = nullptr;
        return false;
    }
    activeSampleRate = openSampleRate;

    trackCount = std::max(1, gme_track_count(emu));
    activeTrack = 0;
    pendingTerminalEnd = false;
    loopStartMs = -1;
    loopLengthMs = -1;
    hasLoopPoint = false;
    isSpcTrack = false;
    playbackPositionSeconds = 0.0;
    lastTellMs = 0;
    isSpcTrack = gme_type(emu) == gme_spc_type;

    // Must be applied before start_track(): libgme sets fade/end behavior there.
    applyRepeatBehaviorLocked();
    applyCoreOptionsLocked();

    const gme_err_t startErr = gme_start_track(emu, activeTrack);
    if (startErr != nullptr) {
        LOGE("gme_start_track failed: %s", startErr);
        closeInternal();
        return false;
    }

    applyTrackInfoLocked(activeTrack);
    voiceCount = std::max(0, gme_voice_count(emu));
    rebuildToggleChannelsLocked();
    applyToggleChannelMutesLocked();

    if (isSpcTrack && spcUseNativeSampleRate && activeSampleRate != 32000) {
        gme_delete(emu);
        emu = nullptr;
        const gme_err_t reopenErr = gme_open_file(path, &emu, 32000);
        if (reopenErr != nullptr || emu == nullptr) {
            LOGE("gme_open_file(SPC native sample rate) failed: %s", reopenErr ? reopenErr : "unknown error");
            emu = nullptr;
            closeInternal();
            return false;
        }
        activeSampleRate = 32000;
        applyRepeatBehaviorLocked();
        applyCoreOptionsLocked();
        const gme_err_t restartErr = gme_start_track(emu, activeTrack);
        if (restartErr != nullptr) {
            LOGE("gme_start_track(reopen) failed: %s", restartErr);
            closeInternal();
            return false;
        }
        applyTrackInfoLocked(activeTrack);
        voiceCount = std::max(0, gme_voice_count(emu));
        rebuildToggleChannelsLocked();
        applyToggleChannelMutesLocked();
    }

    applyRepeatBehaviorLocked();
    applyCoreOptionsLocked();
    return true;
}

bool GmeDecoder::applyTrackInfoLocked(int trackIndex) {
    if (!emu || trackIndex < 0 || trackIndex >= trackCount) {
        duration = 0.0;
        durationReliable = false;
        loopStartMs = -1;
        loopLengthMs = -1;
        hasLoopPoint = false;
        title.clear();
        artist.clear();
        composer.clear();
        genre.clear();
        systemName.clear();
        gameName.clear();
        copyrightText.clear();
        commentText.clear();
        dumper.clear();
        return false;
    }

    gme_info_t* info = nullptr;
    const gme_err_t infoErr = gme_track_info(emu, &info, trackIndex);
    if (infoErr != nullptr || info == nullptr) {
        duration = 0.0;
        durationReliable = false;
        loopStartMs = -1;
        loopLengthMs = -1;
        hasLoopPoint = false;
        title.clear();
        artist.clear();
        composer.clear();
        genre.clear();
        systemName.clear();
        gameName.clear();
        copyrightText.clear();
        commentText.clear();
        dumper.clear();
        return false;
    }

    systemName = safeString(info->system);
    gameName = safeString(info->game);
    title = safeString(info->song);
    artist = safeString(info->author);
    composer = safeString(info->author);
    genre = systemName;
    copyrightText = safeString(info->copyright);
    commentText = safeString(info->comment);
    dumper = safeString(info->dumper);

    const int durationMs = resolveDurationMs(info, unknownDurationSeconds * 1000, &durationReliable);
    loopStartMs = resolveLoopStartMs(info);
    loopLengthMs = info->loop_length;
    hasLoopPoint = loopStartMs >= 0 && loopLengthMs > 0;
    duration = durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;
    gme_free_info(info);
    return true;
}

void GmeDecoder::closeInternal() {
    if (emu != nullptr) {
        gme_delete(emu);
        emu = nullptr;
    }

    duration = 0.0;
    durationReliable = true;
    trackCount = 0;
    activeTrack = 0;
    pendingTerminalEnd = false;
    loopStartMs = -1;
    loopLengthMs = -1;
    hasLoopPoint = false;
    isSpcTrack = false;
    activeSampleRate = requestedSampleRate;
    playbackPositionSeconds = 0.0;
    lastTellMs = -1;
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    systemName.clear();
    gameName.clear();
    copyrightText.clear();
    commentText.clear();
    dumper.clear();
    voiceCount = 0;
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
}

int GmeDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int capabilities = PLAYBACK_CAP_SEEK |
                       PLAYBACK_CAP_LIVE_REPEAT_MODE |
                       PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    if (durationReliable) {
        capabilities |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return capabilities;
}

void GmeDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternal();
}

int GmeDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu || !buffer || numFrames <= 0) {
        return 0;
    }

    if (pendingTerminalEnd) {
        pendingTerminalEnd = false;
        return 0;
    }

    int framesRead = 0;
    std::vector<short> pcmBlock(kRenderBlockFrames * channels);

    while (framesRead < numFrames) {
        const int framesToRead = std::min(kRenderBlockFrames, numFrames - framesRead);
        const int samplesToRead = framesToRead * channels;
        const gme_err_t playErr = gme_play(emu, samplesToRead, pcmBlock.data());
        if (playErr != nullptr) {
            LOGE("gme_play failed: %s", playErr);
            break;
        }

        for (int i = 0; i < samplesToRead; ++i) {
            buffer[(framesRead * channels) + i] = static_cast<float>(pcmBlock[i]) / 32768.0f;
        }
        framesRead += framesToRead;

        if (gme_track_ended(emu)) {
            const int mode = repeatMode.load();
            if (mode == 3) {
                // Repeat-subtune mode: restart current subtune only.
                applyRepeatBehaviorLocked();
                const gme_err_t restartErr = gme_start_track(emu, activeTrack);
                if (restartErr != nullptr) {
                    LOGE("gme_start_track(repeat) failed: %s", restartErr);
                    pendingTerminalEnd = true;
                    break;
                }
                applyCoreOptionsLocked();
                applyToggleChannelMutesLocked();
                playbackPositionSeconds = 0.0;
                lastTellMs = 0;
                continue;
            }
            if (mode == 2) {
                // LP mode must never terminate. Re-arm playback if libgme still
                // reports track end.
                applyRepeatBehaviorLocked();
                const gme_err_t restartErr = gme_start_track(emu, activeTrack);
                if (restartErr != nullptr) {
                    LOGE("gme_start_track(loop) failed: %s", restartErr);
                    pendingTerminalEnd = true;
                    break;
                }
                applyCoreOptionsLocked();
                applyToggleChannelMutesLocked();
                if (hasLoopPoint && loopStartMs >= 0) {
                    const gme_err_t loopSeekErr = gme_seek(emu, loopStartMs);
                    if (loopSeekErr != nullptr) {
                        LOGE("gme_seek(loop) failed: %s", loopSeekErr);
                    } else {
                        playbackPositionSeconds = static_cast<double>(loopStartMs) / 1000.0;
                        lastTellMs = loopStartMs;
                    }
                } else {
                    playbackPositionSeconds = 0.0;
                    lastTellMs = 0;
                }
                continue;
            }

            pendingTerminalEnd = true;
            break;
        }
    }

    if (framesRead > 0) {
        const int currentTellMs = gme_tell(emu);
        if (repeatMode.load() == 2) {
            if (lastTellMs < 0 || currentTellMs > lastTellMs) {
                playbackPositionSeconds = static_cast<double>(currentTellMs) / 1000.0;
            } else {
                // Some tracks keep rendering but gme_tell() stalls at tagged end.
                // Keep LP timeline moving from rendered frames.
                playbackPositionSeconds += static_cast<double>(framesRead) / activeSampleRate;
            }

            if (hasLoopPoint && loopLengthMs > 0) {
                const double loopStartSec = std::max(0.0, static_cast<double>(loopStartMs) / 1000.0);
                const double loopLengthSec = static_cast<double>(loopLengthMs) / 1000.0;
                if (playbackPositionSeconds >= loopStartSec + loopLengthSec) {
                    playbackPositionSeconds =
                            loopStartSec + std::fmod(playbackPositionSeconds - loopStartSec, loopLengthSec);
                }
            }
        } else {
            playbackPositionSeconds = static_cast<double>(currentTellMs) / 1000.0;
        }
        lastTellMs = currentTellMs;
    }

    return framesRead;
}

void GmeDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu) return;

    const int targetMs = static_cast<int>(std::max(0.0, seconds) * 1000.0);
    const gme_err_t seekErr = gme_seek(emu, targetMs);
    if (seekErr != nullptr) {
        LOGE("gme_seek failed: %s", seekErr);
    }
    playbackPositionSeconds = static_cast<double>(targetMs) / 1000.0;
    lastTellMs = targetMs;
    pendingTerminalEnd = false;
}

double GmeDecoder::getDuration() {
    return duration;
}

int GmeDecoder::getSampleRate() {
    return emu ? activeSampleRate : requestedSampleRate;
}

int GmeDecoder::getBitDepth() {
    return bitDepth;
}

std::string GmeDecoder::getBitDepthLabel() {
    return "16 bit";
}

int GmeDecoder::getDisplayChannelCount() {
    return channels;
}

int GmeDecoder::getChannelCount() {
    return channels;
}

int GmeDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return trackCount;
}

int GmeDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return activeTrack;
}

bool GmeDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu || index < 0 || index >= trackCount) {
        return false;
    }
    if (index == activeTrack) {
        return true;
    }

    // libgme repeat/fade behavior is latched on track start.
    applyRepeatBehaviorLocked();
    applyCoreOptionsLocked();
    const gme_err_t startErr = gme_start_track(emu, index);
    if (startErr != nullptr) {
        LOGE("gme_start_track(selectSubtune) failed: %s", startErr);
        return false;
    }
    activeTrack = index;
    pendingTerminalEnd = false;
    playbackPositionSeconds = 0.0;
    lastTellMs = 0;
    applyRepeatBehaviorLocked();
    applyCoreOptionsLocked();
    applyTrackInfoLocked(activeTrack);
    voiceCount = std::max(0, gme_voice_count(emu));
    rebuildToggleChannelsLocked();
    applyToggleChannelMutesLocked();
    return true;
}

std::string GmeDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu || index < 0 || index >= trackCount) {
        return "";
    }
    gme_info_t* info = nullptr;
    const gme_err_t infoErr = gme_track_info(emu, &info, index);
    if (infoErr != nullptr || info == nullptr) {
        return "";
    }
    const std::string value = safeString(info->song);
    gme_free_info(info);
    return value;
}

std::string GmeDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu || index < 0 || index >= trackCount) {
        return "";
    }
    gme_info_t* info = nullptr;
    const gme_err_t infoErr = gme_track_info(emu, &info, index);
    if (infoErr != nullptr || info == nullptr) {
        return "";
    }
    const std::string value = safeString(info->author);
    gme_free_info(info);
    return value;
}

double GmeDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu || index < 0 || index >= trackCount) {
        return 0.0;
    }
    gme_info_t* info = nullptr;
    const gme_err_t infoErr = gme_track_info(emu, &info, index);
    if (infoErr != nullptr || info == nullptr) {
        return 0.0;
    }
    const int durationMs = resolveDurationMs(info, unknownDurationSeconds * 1000, nullptr);
    gme_free_info(info);
    return durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;
}

std::string GmeDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string GmeDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string GmeDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string GmeDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string GmeDecoder::getSystemName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return systemName;
}

std::string GmeDecoder::getGameName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return gameName;
}

std::string GmeDecoder::getCopyright() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return copyrightText;
}

std::string GmeDecoder::getComment() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return commentText;
}

std::string GmeDecoder::getDumper() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return dumper;
}

int GmeDecoder::getTrackCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return trackCount;
}

int GmeDecoder::getVoiceCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return voiceCount;
}

bool GmeDecoder::getHasLoopPointInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return hasLoopPoint;
}

int GmeDecoder::getLoopStartMsInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return loopStartMs;
}

int GmeDecoder::getLoopLengthMsInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return loopLengthMs;
}

std::vector<std::string> GmeDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return toggleChannelNames;
}

void GmeDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu) return;
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyToggleChannelMutesLocked();
}

bool GmeDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu) return false;
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void GmeDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu) return;
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyToggleChannelMutesLocked();
}

void GmeDecoder::setOutputSampleRate(int rate) {
    if (rate <= 0) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    // libgme sample rate is selected when opening the file.
    // Keep this value for the next open().
    requestedSampleRate = rate;
    if (!emu) {
        activeSampleRate = rate;
    }
}

void GmeDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) return;
    std::lock_guard<std::mutex> lock(decodeMutex);

    const std::string optionName(name);
    const std::string optionValue(value);

    if (optionName == "gme.tempo") {
        tempo = std::clamp(parseDoubleString(optionValue, tempo), 0.5, 2.0);
    } else if (optionName == "gme.stereo_separation") {
        stereoDepth = std::clamp(parseDoubleString(optionValue, stereoDepth), 0.0, 1.0);
    } else if (optionName == "gme.echo_enabled") {
        echoEnabled = parseBoolString(optionValue, echoEnabled);
    } else if (optionName == "gme.accuracy_enabled") {
        accuracyEnabled = parseBoolString(optionValue, accuracyEnabled);
    } else if (optionName == "gme.eq_treble_db") {
        eqTrebleDb = std::clamp(parseDoubleString(optionValue, eqTrebleDb), -50.0, 5.0);
    } else if (optionName == "gme.eq_bass_hz") {
        eqBassHz = std::clamp(parseDoubleString(optionValue, eqBassHz), 1.0, 16000.0);
    } else if (optionName == "gme.spc_use_builtin_fade") {
        spcUseBuiltInFade = parseBoolString(optionValue, spcUseBuiltInFade);
        applyRepeatBehaviorLocked();
    } else if (optionName == "gme.spc_interpolation") {
        spcInterpolation = std::clamp(static_cast<int>(parseDoubleString(optionValue, spcInterpolation)), -2, 2);
    } else if (optionName == "gme.spc_use_native_sample_rate") {
        spcUseNativeSampleRate = parseBoolString(optionValue, spcUseNativeSampleRate);
    } else if (optionName == "gme.unknown_duration_seconds") {
        unknownDurationSeconds = std::clamp(static_cast<int>(parseDoubleString(optionValue, unknownDurationSeconds)), 1, 86400);
        applyTrackInfoLocked(activeTrack);
        applyRepeatBehaviorLocked();
    } else {
        return;
    }

    applyCoreOptionsLocked();
}

int GmeDecoder::getOptionApplyPolicy(const char* name) const {
    if (!name) return OPTION_APPLY_LIVE;
    const std::string optionName(name);
    if (optionName == "gme.tempo" ||
        optionName == "gme.stereo_separation" ||
        optionName == "gme.echo_enabled" ||
        optionName == "gme.accuracy_enabled" ||
        optionName == "gme.eq_treble_db" ||
        optionName == "gme.eq_bass_hz") {
        return OPTION_APPLY_LIVE;
    }
    if (optionName == "gme.spc_use_builtin_fade") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "gme.spc_interpolation") {
        return OPTION_APPLY_LIVE;
    }
    if (optionName == "gme.spc_use_native_sample_rate") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "gme.unknown_duration_seconds") {
        return OPTION_APPLY_LIVE;
    }
    return OPTION_APPLY_LIVE;
}

void GmeDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode.store(mode);
    applyRepeatBehaviorLocked();
}

int GmeDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

double GmeDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu) return 0.0;
    if (repeatMode.load() == 2) {
        return playbackPositionSeconds;
    }
    return static_cast<double>(gme_tell(emu)) / 1000.0;
}

AudioDecoder::TimelineMode GmeDecoder::getTimelineMode() const {
    return TimelineMode::Discontinuous;
}

std::vector<std::string> GmeDecoder::getSupportedExtensions() {
    std::vector<std::string> extensions;
    const gme_type_t* typeList = gme_type_list();
    if (!typeList) {
        return extensions;
    }

    for (size_t i = 0; typeList[i] != nullptr; ++i) {
        const char* ext = gme_type_extension(typeList[i]);
        if (!ext || ext[0] == '\0') {
            continue;
        }
        extensions.push_back(toLowerAscii(ext));
    }

    std::sort(extensions.begin(), extensions.end());
    extensions.erase(std::unique(extensions.begin(), extensions.end()), extensions.end());
    return extensions;
}

void GmeDecoder::applyRepeatBehaviorLocked() {
    if (!emu) return;
    const int mode = repeatMode.load();

    if (mode == 2) {
        // LP mode: prefer emulator-native looping behavior, especially for sets
        // that do not expose explicit intro/loop metadata.
        gme_set_autoload_playback_limit(emu, 0);
        gme_ignore_silence(emu, 1);
        gme_set_fade_msecs(emu, std::numeric_limits<int>::max() / 2, 1);
    } else {
        gme_set_autoload_playback_limit(emu, 1);
        gme_ignore_silence(emu, 0);
        if (duration > 0.0 && !(isSpcTrack && spcUseBuiltInFade)) {
            const int fadeStartMs = static_cast<int>(duration * 1000.0);
            gme_set_fade_msecs(emu, fadeStartMs, 50);
        }
    }
}

void GmeDecoder::applyCoreOptionsLocked() {
    if (!emu) return;
    gme_set_tempo(emu, tempo);
    gme_set_stereo_depth(emu, stereoDepth);
    gme_disable_echo(emu, echoEnabled ? 0 : 1);
    gme_enable_accuracy(emu, accuracyEnabled ? 1 : 0);
    gme_equalizer_t eq{};
    eq.treble = eqTrebleDb;
    eq.bass = eqBassHz;
    gme_set_equalizer(emu, &eq);

    if (isSpcTrack) {
        gme_set_spc_interpolation(emu, spcInterpolation);
    }
}

void GmeDecoder::rebuildToggleChannelsLocked() {
    if (!emu) {
        toggleChannelNames.clear();
        toggleChannelMuted.clear();
        return;
    }
    const int totalVoices = std::max(0, gme_voice_count(emu));
    std::vector<bool> previous = toggleChannelMuted;
    toggleChannelNames.clear();
    toggleChannelMuted.assign(static_cast<size_t>(totalVoices), false);
    toggleChannelNames.reserve(static_cast<size_t>(totalVoices));
    for (int voice = 0; voice < totalVoices; ++voice) {
        const char* rawName = gme_voice_name(emu, voice);
        std::string name = safeString(rawName);
        if (name.empty()) {
            name = "Voice " + std::to_string(voice + 1);
        }
        toggleChannelNames.push_back(name);
        if (voice < static_cast<int>(previous.size())) {
            toggleChannelMuted[static_cast<size_t>(voice)] = previous[static_cast<size_t>(voice)];
        }
    }
}

void GmeDecoder::applyToggleChannelMutesLocked() {
    if (!emu) return;
    const int totalVoices = std::min(
            std::max(0, gme_voice_count(emu)),
            static_cast<int>(toggleChannelMuted.size())
    );
    for (int voice = 0; voice < totalVoices; ++voice) {
        gme_mute_voice(
                emu,
                voice,
                toggleChannelMuted[static_cast<size_t>(voice)] ? 1 : 0
        );
    }
}

int GmeDecoder::resolveOpenSampleRateLocked(const char* path) const {
    if (spcUseNativeSampleRate && hasExtension(path, ".spc")) {
        return 32000;
    }
    return requestedSampleRate;
}
