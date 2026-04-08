#include "GmeDecoder.h"
#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <limits>
#include <memory>
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
constexpr int kChannelScopeTextStride = 10;
constexpr int kChannelScopeTextFlagActive = 1 << 0;
constexpr int kGmeScopeMaxVoices = 32;
constexpr int kGmeMultiChannelVoices = 8;
constexpr float kGmeScopeGain = 0.9f;
constexpr float kGmeNesScopeGain = 1.35f;
constexpr float kGmeNesDpcmScopeGain = 0.3f;

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

bool isNesGmeType(gme_type_t type) {
    return type == gme_nsf_type || type == gme_nsfe_type;
}

bool isDpcmLikeVoiceName(const std::string& name) {
    const std::string normalized = toLowerAscii(name);
    return normalized == "dmc" || normalized == "dpcm" || normalized == "pcm";
}

bool isVrc6SawVoiceName(const std::string& name) {
    return toLowerAscii(name) == "saw wave";
}

bool isMmc5VoiceTripletAt(const std::vector<std::string>& names, size_t index) {
    if (index + 2 >= names.size()) {
        return false;
    }
    return toLowerAscii(names[index]) == "square 3" &&
           toLowerAscii(names[index + 1]) == "square 4" &&
           toLowerAscii(names[index + 2]) == "pcm";
}
}

GmeDecoder::GmeDecoder() : channelScopeState(std::make_shared<ChannelScopeSharedState>()) {}

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
    sourcePath = path;
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
    createScopeCaptureLocked();
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

void GmeDecoder::closeScopeCaptureLocked() {
    if (scopeMultiEmu != nullptr) {
        gme_delete(scopeMultiEmu);
        scopeMultiEmu = nullptr;
    }
    if (scopeApuEmu != nullptr) {
        gme_delete(scopeApuEmu);
        scopeApuEmu = nullptr;
    }
    if (scopeVrc6Emu != nullptr) {
        gme_delete(scopeVrc6Emu);
        scopeVrc6Emu = nullptr;
    }
    if (scopeMmc5Emu != nullptr) {
        gme_delete(scopeMmc5Emu);
        scopeMmc5Emu = nullptr;
    }
    for (Music_Emu* shadow : scopeVoiceEmus) {
        if (shadow != nullptr) {
            gme_delete(shadow);
        }
    }
    scopeVoiceEmus.clear();
    scopePcmScratch.clear();
    scopeApuScratch.clear();
    scopeVrc6Scratch.clear();
    scopeMmc5Scratch.clear();
}

Music_Emu* GmeDecoder::createScopeShadowLocked(bool multiChannel) {
    if (!emu || sourcePath.empty()) {
        return nullptr;
    }

    const gme_type_t type = gme_type(emu);
    if (type == nullptr) {
        return nullptr;
    }

    Music_Emu* shadow = multiChannel
            ? gme_new_emu_multi_channel(type, activeSampleRate)
            : gme_new_emu(type, activeSampleRate);
    if (shadow == nullptr) {
        return nullptr;
    }

    const gme_err_t loadErr = gme_load_file(shadow, sourcePath.c_str());
    if (loadErr != nullptr) {
        LOGW("gme_load_file(scope shadow) failed: %s", loadErr);
        gme_delete(shadow);
        return nullptr;
    }
    return shadow;
}

void GmeDecoder::resetChannelScopeLocked() {
    scopeRingRaw.clear();
    scopeRingChannels = 0;
    scopeRingWritePos = 0;
    scopeRingSamples = 0;
    scopePcmScratch.clear();
    if (channelScopeState) {
        channelScopeState->clear();
    }
}

void GmeDecoder::ensureScopeRingShapeLocked(int channelsToKeep) {
    const int clampedChannels = std::clamp(channelsToKeep, 0, kGmeScopeMaxVoices);
    if (clampedChannels <= 0) {
        scopeRingRaw.clear();
        scopeRingChannels = 0;
        scopeRingWritePos = 0;
        scopeRingSamples = 0;
        return;
    }
    const size_t requiredSize =
            static_cast<size_t>(clampedChannels) * ChannelScopeSharedState::kMaxSamples;
    if (scopeRingChannels == clampedChannels && scopeRingRaw.size() == requiredSize) {
        return;
    }
    scopeRingRaw.assign(requiredSize, 0.0f);
    scopeRingChannels = clampedChannels;
    scopeRingWritePos = 0;
    scopeRingSamples = 0;
}

void GmeDecoder::appendScopeFrameLocked(const float* perVoiceSamples, int channelsToWrite) {
    if (!perVoiceSamples || channelsToWrite <= 0) {
        return;
    }
    ensureScopeRingShapeLocked(channelsToWrite);
    if (scopeRingChannels <= 0 || scopeRingRaw.empty()) {
        return;
    }
    const int storedChannels = std::min(scopeRingChannels, channelsToWrite);
    for (int channel = 0; channel < storedChannels; ++channel) {
        scopeRingRaw[
                static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                static_cast<size_t>(scopeRingWritePos)
        ] = std::clamp(perVoiceSamples[channel], -1.0f, 1.0f);
    }
    for (int channel = storedChannels; channel < scopeRingChannels; ++channel) {
        scopeRingRaw[
                static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                static_cast<size_t>(scopeRingWritePos)
        ] = 0.0f;
    }
    scopeRingWritePos = (scopeRingWritePos + 1) % ChannelScopeSharedState::kMaxSamples;
    scopeRingSamples = std::min(scopeRingSamples + 1, ChannelScopeSharedState::kMaxSamples);
}

void GmeDecoder::publishScopeSnapshotLocked() {
    if (!channelScopeState) {
        return;
    }
    if (scopeRingChannels <= 0 || scopeRingRaw.empty() || scopeRingSamples <= 0) {
        channelScopeState->clear();
        return;
    }

    std::vector<float> raw(
            static_cast<size_t>(scopeRingChannels) * ChannelScopeSharedState::kMaxSamples,
            0.0f
    );
    std::vector<float> vu(static_cast<size_t>(scopeRingChannels), 0.0f);
    const int filledSamples = std::clamp(scopeRingSamples, 0, ChannelScopeSharedState::kMaxSamples);
    const int zeroPrefix = ChannelScopeSharedState::kMaxSamples - filledSamples;
    const int trailingSamples = std::clamp(activeSampleRate > 0 ? activeSampleRate / 50 : 64, 64, 1024);

    for (int channel = 0; channel < scopeRingChannels; ++channel) {
        float* dst = raw.data() + static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples;
        for (int i = 0; i < filledSamples; ++i) {
            const int ringIndex =
                    (scopeRingWritePos - filledSamples + i + ChannelScopeSharedState::kMaxSamples) %
                    ChannelScopeSharedState::kMaxSamples;
            dst[zeroPrefix + i] = scopeRingRaw[
                    static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                    static_cast<size_t>(ringIndex)
            ];
        }

        float peak = 0.0f;
        const int start = std::max(0, ChannelScopeSharedState::kMaxSamples - trailingSamples);
        for (int i = start; i < ChannelScopeSharedState::kMaxSamples; ++i) {
            peak = std::max(peak, std::abs(dst[i]));
        }
        vu[static_cast<size_t>(channel)] = std::clamp(peak, 0.0f, 1.0f);
    }

    std::lock_guard<std::mutex> channelScopeLock(channelScopeState->mutex);
    channelScopeState->snapshotRaw = std::move(raw);
    channelScopeState->snapshotVu = std::move(vu);
    channelScopeState->snapshotChannels = scopeRingChannels;
    channelScopeState->snapshotSerial = ++channelScopeSourceSerial;
}

void GmeDecoder::applyRepeatBehaviorToEmuLocked(Music_Emu* target) {
    if (!target) return;
    const int mode = repeatMode.load();

    if (mode == 2) {
        gme_set_autoload_playback_limit(target, 0);
        gme_ignore_silence(target, 1);
        gme_set_fade_msecs(target, std::numeric_limits<int>::max() / 2, 1);
    } else {
        gme_set_autoload_playback_limit(target, 1);
        gme_ignore_silence(target, 0);
        if (duration > 0.0 && !(isSpcTrack && spcUseBuiltInFade)) {
            const int fadeStartMs = static_cast<int>(duration * 1000.0);
            gme_set_fade_msecs(target, fadeStartMs, 50);
        }
    }
}

void GmeDecoder::applyCoreOptionsToEmuLocked(Music_Emu* target, bool forScopeCapture) const {
    if (!target) return;
    gme_set_tempo(target, tempo);
    gme_set_stereo_depth(target, forScopeCapture ? 0.0 : stereoDepth);
    gme_disable_echo(target, echoEnabled ? 0 : 1);
    gme_enable_accuracy(target, accuracyEnabled ? 1 : 0);
    gme_equalizer_t eq{};
    eq.treble = eqTrebleDb;
    eq.bass = eqBassHz;
    gme_set_equalizer(target, &eq);

    if (isSpcTrack) {
        gme_set_spc_interpolation(target, spcInterpolation);
    }
}

void GmeDecoder::applyToggleChannelMutesToEmuLocked(Music_Emu* target, int soloVoice) const {
    if (!target) return;
    const int totalVoices = std::min(
            std::max(0, gme_voice_count(target)),
            static_cast<int>(toggleChannelMuted.size())
    );
    for (int voice = 0; voice < totalVoices; ++voice) {
        bool mute = toggleChannelMuted[static_cast<size_t>(voice)];
        if (soloVoice >= 0 && voice != soloVoice) {
            mute = true;
        }
        gme_mute_voice(target, voice, mute ? 1 : 0);
    }
}

int GmeDecoder::buildScopeVrc6MuteMaskLocked() const {
    if (scopeVrc6BaseVoice < 0) {
        return 0;
    }

    int mask = 0;
    for (int offset = 0; offset < 3; ++offset) {
        const int voice = scopeVrc6BaseVoice + offset;
        if (voice >= static_cast<int>(toggleChannelMuted.size()) ||
            toggleChannelMuted[static_cast<size_t>(voice)]) {
            mask |= (1 << offset);
        }
    }
    return mask;
}

int GmeDecoder::buildScopeApuMuteMaskLocked() const {
    int mask = 0;
    const int totalBaseVoices = std::min(5, static_cast<int>(toggleChannelMuted.size()));
    for (int voice = 0; voice < totalBaseVoices; ++voice) {
        if (toggleChannelMuted[static_cast<size_t>(voice)]) {
            mask |= (1 << voice);
        }
    }
    return mask;
}

int GmeDecoder::buildScopeMmc5MuteMaskLocked() const {
    if (scopeMmc5BaseVoice < 0) {
        return 0;
    }

    int mask = 0;
    for (int offset = 0; offset < 3; ++offset) {
        const int voice = scopeMmc5BaseVoice + offset;
        if (voice >= static_cast<int>(toggleChannelMuted.size()) ||
            toggleChannelMuted[static_cast<size_t>(voice)]) {
            mask |= (1 << offset);
        }
    }
    return mask;
}

bool GmeDecoder::syncScopeCaptureLocked(int positionMs) {
    if (scopeMultiEmu == nullptr && scopeApuEmu == nullptr && scopeVrc6Emu == nullptr &&
            scopeMmc5Emu == nullptr && scopeVoiceEmus.empty()) {
        return false;
    }

    auto syncShadow = [&](Music_Emu* shadow, int soloVoice) -> bool {
        if (!shadow) return false;
        applyRepeatBehaviorToEmuLocked(shadow);
        applyCoreOptionsToEmuLocked(shadow, true);
        const gme_err_t startErr = gme_start_track(shadow, activeTrack);
        if (startErr != nullptr) {
            LOGW("gme_start_track(scope shadow) failed: %s", startErr);
            return false;
        }
        applyRepeatBehaviorToEmuLocked(shadow);
        applyCoreOptionsToEmuLocked(shadow, true);
        applyToggleChannelMutesToEmuLocked(shadow, soloVoice);
        if (positionMs > 0) {
            const gme_err_t seekErr = gme_seek(shadow, positionMs);
            if (seekErr != nullptr) {
                LOGW("gme_seek(scope shadow) failed: %s", seekErr);
                return false;
            }
        }
        return true;
    };

    if (scopeMultiEmu != nullptr && !syncShadow(scopeMultiEmu, -1)) {
        closeScopeCaptureLocked();
        resetChannelScopeLocked();
        return false;
    }
    if (scopeApuEmu != nullptr && !syncShadow(scopeApuEmu, -1)) {
        closeScopeCaptureLocked();
        resetChannelScopeLocked();
        return false;
    }
    if (scopeVrc6Emu != nullptr && !syncShadow(scopeVrc6Emu, -1)) {
        closeScopeCaptureLocked();
        resetChannelScopeLocked();
        return false;
    }
    if (scopeMmc5Emu != nullptr && !syncShadow(scopeMmc5Emu, -1)) {
        closeScopeCaptureLocked();
        resetChannelScopeLocked();
        return false;
    }
    if (scopeVrc6Emu != nullptr) {
        gme_set_nsf_vrc6_scope_mute_mask(scopeVrc6Emu, buildScopeVrc6MuteMaskLocked());
        gme_clear_nsf_vrc6_scope(scopeVrc6Emu);
    }
    if (scopeApuEmu != nullptr) {
        gme_set_nsf_apu_scope_mute_mask(scopeApuEmu, buildScopeApuMuteMaskLocked());
        gme_clear_nsf_apu_scope(scopeApuEmu);
    }
    if (scopeMmc5Emu != nullptr) {
        gme_set_nsf_mmc5_scope_mute_mask(scopeMmc5Emu, buildScopeMmc5MuteMaskLocked());
        gme_clear_nsf_mmc5_scope(scopeMmc5Emu);
    }
    for (int voice = 0; voice < static_cast<int>(scopeVoiceEmus.size()); ++voice) {
        Music_Emu* shadow = scopeVoiceEmus[static_cast<size_t>(voice)];
        if (shadow == nullptr) {
            continue;
        }
        if (!syncShadow(shadow, voice)) {
            closeScopeCaptureLocked();
            resetChannelScopeLocked();
            return false;
        }
    }

    resetChannelScopeLocked();
    return true;
}

bool GmeDecoder::createScopeCaptureLocked() {
    closeScopeCaptureLocked();
    resetChannelScopeLocked();

    const int totalVoices = std::min(std::max(0, voiceCount), kGmeScopeMaxVoices);
    if (!emu || sourcePath.empty() || totalVoices <= 0) {
        return false;
    }

    if (Music_Emu* multiShadow = createScopeShadowLocked(true)) {
        if (gme_multi_channel(multiShadow) != 0) {
            scopeMultiEmu = multiShadow;
        } else {
            gme_delete(multiShadow);
        }
    }

    scopeVoiceEmus.assign(static_cast<size_t>(totalVoices), nullptr);

    if (scopeMultiEmu == nullptr) {
        for (int voice = 0; voice < totalVoices; ++voice) {
            Music_Emu* shadow = createScopeShadowLocked(false);
            if (shadow == nullptr) {
                closeScopeCaptureLocked();
                resetChannelScopeLocked();
                return false;
            }
            scopeVoiceEmus[static_cast<size_t>(voice)] = shadow;
        }
    } else {
        for (int voice = kGmeMultiChannelVoices; voice < totalVoices; ++voice) {
            Music_Emu* shadow = createScopeShadowLocked(false);
            if (shadow == nullptr) {
                closeScopeCaptureLocked();
                resetChannelScopeLocked();
                return false;
            }
            scopeVoiceEmus[static_cast<size_t>(voice)] = shadow;
        }
        if (isNesGmeType(gme_type(emu)) && totalVoices > 0 && gme_nsf_has_apu_scope(emu) != 0) {
            scopeApuEmu = createScopeShadowLocked(false);
            if (scopeApuEmu == nullptr) {
                closeScopeCaptureLocked();
                resetChannelScopeLocked();
                return false;
            }
        }
        if (scopeVrc6BaseVoice >= 0 && gme_nsf_has_vrc6(emu) != 0) {
            scopeVrc6Emu = createScopeShadowLocked(false);
            if (scopeVrc6Emu == nullptr) {
                closeScopeCaptureLocked();
                resetChannelScopeLocked();
                return false;
            }
        }
        if (scopeMmc5BaseVoice >= 0 && gme_nsf_has_mmc5(emu) != 0) {
            scopeMmc5Emu = createScopeShadowLocked(false);
            if (scopeMmc5Emu == nullptr) {
                closeScopeCaptureLocked();
                resetChannelScopeLocked();
                return false;
            }
        }
    }

    return syncScopeCaptureLocked(0);
}

void GmeDecoder::captureChannelScopeBlockLocked(int frames) {
    const int totalVoices = std::min(std::max(0, voiceCount), kGmeScopeMaxVoices);
    if (frames <= 0 || totalVoices <= 0 || (!scopeMultiEmu && scopeApuEmu == nullptr && scopeVrc6Emu == nullptr &&
            scopeMmc5Emu == nullptr && scopeVoiceEmus.empty())) {
        return;
    }

    std::vector<float> perVoiceFrame(static_cast<size_t>(totalVoices), 0.0f);
    std::vector<float> capturedBlocks(static_cast<size_t>(totalVoices * frames), 0.0f);
    if (scopeMultiEmu != nullptr) {
        const int multiVoices = std::min(totalVoices, kGmeMultiChannelVoices);
        const int outputChannels = kGmeMultiChannelVoices * 2;
        const int samplesToRead = frames * outputChannels;
        if (static_cast<int>(scopePcmScratch.size()) < samplesToRead) {
            scopePcmScratch.resize(static_cast<size_t>(samplesToRead));
        }
        const gme_err_t playErr = gme_play(scopeMultiEmu, samplesToRead, scopePcmScratch.data());
        if (playErr != nullptr) {
            LOGW("gme_play(scope multi) failed: %s", playErr);
            closeScopeCaptureLocked();
            resetChannelScopeLocked();
            return;
        }

        for (int frame = 0; frame < frames; ++frame) {
            for (int voice = 0; voice < multiVoices; ++voice) {
                const size_t base =
                        (static_cast<size_t>(frame) * kGmeMultiChannelVoices + static_cast<size_t>(voice)) * 2u;
                const float left = static_cast<float>(scopePcmScratch[base]) / 32768.0f;
                const float right = static_cast<float>(scopePcmScratch[base + 1u]) / 32768.0f;
                const float voiceGain = voice < static_cast<int>(scopeVoiceGains.size())
                        ? scopeVoiceGains[static_cast<size_t>(voice)]
                        : kGmeScopeGain;
                capturedBlocks[static_cast<size_t>(voice * frames + frame)] =
                        std::clamp(((left + right) * 0.5f) * voiceGain, -1.0f, 1.0f);
            }
        }
    }

    const int stereoSamples = frames * channels;
    if (static_cast<int>(scopePcmScratch.size()) < stereoSamples) {
        scopePcmScratch.resize(static_cast<size_t>(stereoSamples));
    }
    for (int voice = 0; voice < totalVoices; ++voice) {
        Music_Emu* shadow = voice < static_cast<int>(scopeVoiceEmus.size())
                ? scopeVoiceEmus[static_cast<size_t>(voice)]
                : nullptr;
        if (!shadow) continue;

        const gme_err_t playErr = gme_play(shadow, stereoSamples, scopePcmScratch.data());
        if (playErr != nullptr) {
            LOGW("gme_play(scope voice shadow) failed: %s", playErr);
            closeScopeCaptureLocked();
            resetChannelScopeLocked();
            return;
        }
        for (int frame = 0; frame < frames; ++frame) {
            const size_t base = static_cast<size_t>(frame * channels);
            const float left = static_cast<float>(scopePcmScratch[base]) / 32768.0f;
            const float right = static_cast<float>(scopePcmScratch[base + 1u]) / 32768.0f;
            const float voiceGain = voice < static_cast<int>(scopeVoiceGains.size())
                    ? scopeVoiceGains[static_cast<size_t>(voice)]
                    : kGmeScopeGain;
            capturedBlocks[static_cast<size_t>(voice * frames + frame)] =
                    std::clamp(((left + right) * 0.5f) * voiceGain, -1.0f, 1.0f);
        }
    }

    if (scopeVrc6Emu != nullptr && scopeVrc6BaseVoice >= 0 && scopeVrc6BaseVoice + 2 < totalVoices) {
        const size_t required = static_cast<size_t>(frames * 3);
        if (scopeVrc6Scratch.size() < required) {
            scopeVrc6Scratch.resize(required);
        }
        const gme_err_t playErr = gme_play_nsf_vrc6_scope(scopeVrc6Emu, frames, scopeVrc6Scratch.data());
        if (playErr != nullptr) {
            LOGW("gme_play_nsf_vrc6_scope failed: %s", playErr);
            closeScopeCaptureLocked();
            resetChannelScopeLocked();
            return;
        }
        for (int frame = 0; frame < frames; ++frame) {
            for (int offset = 0; offset < 3; ++offset) {
                const int voice = scopeVrc6BaseVoice + offset;
                float sample = static_cast<float>(scopeVrc6Scratch[static_cast<size_t>(frame * 3 + offset)]) / 32768.0f;
                const float voiceGain = voice < static_cast<int>(scopeVoiceGains.size())
                        ? scopeVoiceGains[static_cast<size_t>(voice)]
                        : kGmeScopeGain;
                if (voice < static_cast<int>(toggleChannelMuted.size()) &&
                    toggleChannelMuted[static_cast<size_t>(voice)]) {
                    sample = 0.0f;
                } else {
                    sample = std::clamp(sample * voiceGain, -1.0f, 1.0f);
                }
                capturedBlocks[static_cast<size_t>(voice * frames + frame)] = sample;
            }
        }
    }
    if (scopeApuEmu != nullptr) {
        const int apuVoices = std::min(5, totalVoices);
        const size_t required = static_cast<size_t>(frames * 5);
        if (scopeApuScratch.size() < required) {
            scopeApuScratch.resize(required);
        }
        const gme_err_t playErr = gme_play_nsf_apu_scope(scopeApuEmu, frames, scopeApuScratch.data());
        if (playErr != nullptr) {
            LOGW("gme_play_nsf_apu_scope failed: %s", playErr);
            closeScopeCaptureLocked();
            resetChannelScopeLocked();
            return;
        }
        for (int frame = 0; frame < frames; ++frame) {
            for (int voice = 0; voice < apuVoices; ++voice) {
                float sample = static_cast<float>(scopeApuScratch[static_cast<size_t>(frame * 5 + voice)]) / 32768.0f;
                const float voiceGain = voice < static_cast<int>(scopeVoiceGains.size())
                        ? scopeVoiceGains[static_cast<size_t>(voice)]
                        : kGmeScopeGain;
                if (voice < static_cast<int>(toggleChannelMuted.size()) &&
                    toggleChannelMuted[static_cast<size_t>(voice)]) {
                    sample = 0.0f;
                } else {
                    sample = std::clamp(sample * voiceGain, -1.0f, 1.0f);
                }
                capturedBlocks[static_cast<size_t>(voice * frames + frame)] = sample;
            }
        }
    }
    if (scopeMmc5Emu != nullptr && scopeMmc5BaseVoice >= 0 && scopeMmc5BaseVoice + 2 < totalVoices) {
        const size_t required = static_cast<size_t>(frames * 3);
        if (scopeMmc5Scratch.size() < required) {
            scopeMmc5Scratch.resize(required);
        }
        const gme_err_t playErr = gme_play_nsf_mmc5_scope(scopeMmc5Emu, frames, scopeMmc5Scratch.data());
        if (playErr != nullptr) {
            LOGW("gme_play_nsf_mmc5_scope failed: %s", playErr);
            closeScopeCaptureLocked();
            resetChannelScopeLocked();
            return;
        }
        for (int frame = 0; frame < frames; ++frame) {
            for (int offset = 0; offset < 3; ++offset) {
                const int voice = scopeMmc5BaseVoice + offset;
                float sample = static_cast<float>(scopeMmc5Scratch[static_cast<size_t>(frame * 3 + offset)]) / 32768.0f;
                const float voiceGain = voice < static_cast<int>(scopeVoiceGains.size())
                        ? scopeVoiceGains[static_cast<size_t>(voice)]
                        : kGmeScopeGain;
                if (voice < static_cast<int>(toggleChannelMuted.size()) &&
                    toggleChannelMuted[static_cast<size_t>(voice)]) {
                    sample = 0.0f;
                } else {
                    sample = std::clamp(sample * voiceGain, -1.0f, 1.0f);
                }
                capturedBlocks[static_cast<size_t>(voice * frames + frame)] = sample;
            }
        }
    }
    for (int frame = 0; frame < frames; ++frame) {
        std::fill(perVoiceFrame.begin(), perVoiceFrame.end(), 0.0f);
        for (int voice = 0; voice < totalVoices; ++voice) {
            perVoiceFrame[static_cast<size_t>(voice)] =
                    capturedBlocks[static_cast<size_t>(voice * frames + frame)];
        }
        appendScopeFrameLocked(perVoiceFrame.data(), totalVoices);
    }
    publishScopeSnapshotLocked();
}

void GmeDecoder::closeInternal() {
    closeScopeCaptureLocked();
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
    sourcePath.clear();
    voiceCount = 0;
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    scopeVoiceGains.clear();
    scopeVrc6BaseVoice = -1;
    scopeMmc5BaseVoice = -1;
    resetChannelScopeLocked();
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
        captureChannelScopeBlockLocked(framesToRead);
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
                syncScopeCaptureLocked(0);
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
                int scopeRestartMs = 0;
                if (hasLoopPoint && loopStartMs >= 0) {
                    const gme_err_t loopSeekErr = gme_seek(emu, loopStartMs);
                    if (loopSeekErr != nullptr) {
                        LOGE("gme_seek(loop) failed: %s", loopSeekErr);
                    } else {
                        playbackPositionSeconds = static_cast<double>(loopStartMs) / 1000.0;
                        lastTellMs = loopStartMs;
                        scopeRestartMs = loopStartMs;
                    }
                } else {
                    playbackPositionSeconds = 0.0;
                    lastTellMs = 0;
                }
                syncScopeCaptureLocked(scopeRestartMs);
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
    syncScopeCaptureLocked(targetMs);
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
    createScopeCaptureLocked();
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
    resetChannelScopeLocked();
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
    resetChannelScopeLocked();
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

std::vector<int32_t> GmeDecoder::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (scopeRingChannels <= 0 || scopeRingSamples <= 0 || scopeRingRaw.empty()) {
        return {};
    }

    const int channelsToExport = std::min(scopeRingChannels, std::clamp(maxChannels, 1, kGmeScopeMaxVoices));
    std::vector<int32_t> flat(static_cast<size_t>(channelsToExport * kChannelScopeTextStride), -1);
    const int trailingSamples = std::clamp(activeSampleRate > 0 ? activeSampleRate / 50 : 64, 64, 1024);
    const int recentSamples = std::min(scopeRingSamples, trailingSamples);
    for (int channel = 0; channel < channelsToExport; ++channel) {
        float recentPeak = 0.0f;
        for (int i = 0; i < recentSamples; ++i) {
            const int ringIndex =
                    (scopeRingWritePos - recentSamples + i + ChannelScopeSharedState::kMaxSamples) %
                    ChannelScopeSharedState::kMaxSamples;
            recentPeak = std::max(
                    recentPeak,
                    std::abs(scopeRingRaw[
                            static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                            static_cast<size_t>(ringIndex)
                    ])
            );
        }

        const size_t base = static_cast<size_t>(channel * kChannelScopeTextStride);
        int flags = 0;
        if (recentPeak > 0.0015f) {
            flags |= kChannelScopeTextFlagActive;
        }

        flat[base + 0] = channel;
        flat[base + 1] = -1;
        flat[base + 2] = std::clamp(static_cast<int>(std::lround(recentPeak * 64.0f)), 0, 64);
        flat[base + 3] = 0;
        flat[base + 4] = -1;
        flat[base + 5] = 0;
        flat[base + 6] = -1;
        flat[base + 7] = -1;
        flat[base + 8] = -1;
        flat[base + 9] = flags;
    }
    return flat;
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
    applyRepeatBehaviorToEmuLocked(emu);
    if (scopeMultiEmu != nullptr) {
        applyRepeatBehaviorToEmuLocked(scopeMultiEmu);
    }
    if (scopeVrc6Emu != nullptr) {
        applyRepeatBehaviorToEmuLocked(scopeVrc6Emu);
    }
    if (scopeApuEmu != nullptr) {
        applyRepeatBehaviorToEmuLocked(scopeApuEmu);
    }
    if (scopeMmc5Emu != nullptr) {
        applyRepeatBehaviorToEmuLocked(scopeMmc5Emu);
    }
    for (Music_Emu* shadow : scopeVoiceEmus) {
        applyRepeatBehaviorToEmuLocked(shadow);
    }
}

void GmeDecoder::applyCoreOptionsLocked() {
    applyCoreOptionsToEmuLocked(emu, false);
    if (scopeMultiEmu != nullptr) {
        applyCoreOptionsToEmuLocked(scopeMultiEmu, true);
    }
    if (scopeVrc6Emu != nullptr) {
        applyCoreOptionsToEmuLocked(scopeVrc6Emu, true);
    }
    if (scopeApuEmu != nullptr) {
        applyCoreOptionsToEmuLocked(scopeApuEmu, true);
    }
    if (scopeMmc5Emu != nullptr) {
        applyCoreOptionsToEmuLocked(scopeMmc5Emu, true);
    }
    for (Music_Emu* shadow : scopeVoiceEmus) {
        applyCoreOptionsToEmuLocked(shadow, true);
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
    rebuildScopeVoiceGainsLocked();
    rebuildScopeVrc6BaseVoiceLocked();
    rebuildScopeMmc5BaseVoiceLocked();
}

void GmeDecoder::rebuildScopeVoiceGainsLocked() {
    scopeVoiceGains.assign(toggleChannelNames.size(), kGmeScopeGain);
    if (!emu || !isNesGmeType(gme_type(emu))) {
        return;
    }

    for (size_t voice = 0; voice < scopeVoiceGains.size(); ++voice) {
        scopeVoiceGains[voice] = isDpcmLikeVoiceName(toggleChannelNames[voice])
                ? kGmeNesDpcmScopeGain
                : kGmeNesScopeGain;
    }
}

void GmeDecoder::rebuildScopeVrc6BaseVoiceLocked() {
    scopeVrc6BaseVoice = -1;
    if (!emu || !isNesGmeType(gme_type(emu))) {
        return;
    }

    auto sawIt = std::find_if(
            toggleChannelNames.begin(),
            toggleChannelNames.end(),
            [](const std::string& name) { return isVrc6SawVoiceName(name); }
    );
    if (sawIt == toggleChannelNames.end()) {
        return;
    }
    scopeVrc6BaseVoice = static_cast<int>(std::distance(toggleChannelNames.begin(), sawIt));
}

void GmeDecoder::rebuildScopeMmc5BaseVoiceLocked() {
    scopeMmc5BaseVoice = -1;
    if (!emu || !isNesGmeType(gme_type(emu))) {
        return;
    }

    for (size_t index = 0; index < toggleChannelNames.size(); ++index) {
        if (isMmc5VoiceTripletAt(toggleChannelNames, index)) {
            scopeMmc5BaseVoice = static_cast<int>(index);
            return;
        }
    }
}

void GmeDecoder::applyToggleChannelMutesLocked() {
    applyToggleChannelMutesToEmuLocked(emu);
    if (scopeMultiEmu != nullptr) {
        applyToggleChannelMutesToEmuLocked(scopeMultiEmu);
    }
    if (scopeVrc6Emu != nullptr) {
        applyToggleChannelMutesToEmuLocked(scopeVrc6Emu);
        gme_set_nsf_vrc6_scope_mute_mask(scopeVrc6Emu, buildScopeVrc6MuteMaskLocked());
    }
    if (scopeApuEmu != nullptr) {
        applyToggleChannelMutesToEmuLocked(scopeApuEmu);
        gme_set_nsf_apu_scope_mute_mask(scopeApuEmu, buildScopeApuMuteMaskLocked());
    }
    if (scopeMmc5Emu != nullptr) {
        applyToggleChannelMutesToEmuLocked(scopeMmc5Emu);
        gme_set_nsf_mmc5_scope_mute_mask(scopeMmc5Emu, buildScopeMmc5MuteMaskLocked());
    }
    const int totalShadows = std::min(
            static_cast<int>(scopeVoiceEmus.size()),
            static_cast<int>(toggleChannelMuted.size())
    );
    for (int voice = 0; voice < totalShadows; ++voice) {
        applyToggleChannelMutesToEmuLocked(scopeVoiceEmus[static_cast<size_t>(voice)], voice);
    }
}

int GmeDecoder::resolveOpenSampleRateLocked(const char* path) const {
    if (spcUseNativeSampleRate && hasExtension(path, ".spc")) {
        return 32000;
    }
    return requestedSampleRate;
}
