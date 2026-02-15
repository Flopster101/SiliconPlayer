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

namespace {
constexpr int kRenderBlockFrames = 512;

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

    const gme_err_t openErr = gme_open_file(path, &emu, sampleRate);
    if (openErr != nullptr || emu == nullptr) {
        LOGE("gme_open_file failed: %s", openErr ? openErr : "unknown error");
        emu = nullptr;
        return false;
    }

    trackCount = std::max(1, gme_track_count(emu));
    activeTrack = 0;
    pendingTerminalEnd = false;
    loopStartMs = -1;
    loopLengthMs = -1;
    hasLoopPoint = false;
    playbackPositionSeconds = 0.0;
    lastTellMs = 0;

    // Must be applied before start_track(): libgme sets fade/end behavior there.
    applyRepeatBehaviorLocked();
    applyCoreOptionsLocked();

    const gme_err_t startErr = gme_start_track(emu, activeTrack);
    if (startErr != nullptr) {
        LOGE("gme_start_track failed: %s", startErr);
        closeInternal();
        return false;
    }

    gme_info_t* info = nullptr;
    const gme_err_t infoErr = gme_track_info(emu, &info, activeTrack);
    if (infoErr == nullptr && info != nullptr) {
        title = safeString(info->song);
        artist = safeString(info->author);
        composer = safeString(info->author);
        genre = safeString(info->system);

        int durationMs = info->play_length;
        if (durationMs <= 0) durationMs = info->length;
        if (durationMs <= 0 && info->intro_length > 0 && info->loop_length > 0) {
            durationMs = info->intro_length + (info->loop_length * 2);
        }
        loopStartMs = resolveLoopStartMs(info);
        loopLengthMs = info->loop_length;
        hasLoopPoint = loopStartMs >= 0 && loopLengthMs > 0;
        duration = durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;
        gme_free_info(info);
    } else {
        duration = 0.0;
    }

    applyRepeatBehaviorLocked();
    applyCoreOptionsLocked();
    return true;
}

void GmeDecoder::closeInternal() {
    if (emu != nullptr) {
        gme_delete(emu);
        emu = nullptr;
    }

    duration = 0.0;
    trackCount = 0;
    activeTrack = 0;
    pendingTerminalEnd = false;
    loopStartMs = -1;
    loopLengthMs = -1;
    hasLoopPoint = false;
    playbackPositionSeconds = 0.0;
    lastTellMs = -1;
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
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
            if (mode == 1) {
                applyRepeatBehaviorLocked();
                const gme_err_t restartErr = gme_start_track(emu, activeTrack);
                if (restartErr != nullptr) {
                    LOGE("gme_start_track(repeat) failed: %s", restartErr);
                    pendingTerminalEnd = true;
                    break;
                }
                applyCoreOptionsLocked();
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
                playbackPositionSeconds += static_cast<double>(framesRead) / sampleRate;
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
    return sampleRate;
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

void GmeDecoder::setOutputSampleRate(int rate) {
    if (rate <= 0) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    // libgme sample rate is selected when opening the file.
    // Keep this value for the next open().
    sampleRate = rate;
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
    } else {
        return;
    }

    applyCoreOptionsLocked();
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
        if (duration > 0.0) {
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
}
