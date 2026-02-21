#include "UadeDecoder.h"

#include <algorithm>
#include <android/log.h>
#include <cctype>
#include <cerrno>
#include <cmath>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <mutex>
#include <unistd.h>

extern "C" {
#include <uade/uade.h>
#include <uade/uadeipc.h>
#include <uade/uadestate.h>
}

#define LOG_TAG "UadeDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex gRuntimeConfigMutex;
std::string gRuntimeBaseDir;
std::string gUadeCorePath;

std::string safeString(const char* value) {
    return value ? std::string(value) : std::string();
}

int clampSubsong(int subsong, int minSubsong, int maxSubsong) {
    if (minSubsong > maxSubsong) {
        return 0;
    }
    return std::clamp(subsong, minSubsong, maxSubsong);
}

bool parseBoolOptionString(const char* value, bool fallback) {
    if (!value) return fallback;
    std::string normalized(value);
    std::transform(normalized.begin(), normalized.end(), normalized.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    if (normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on") {
        return true;
    }
    if (normalized == "0" || normalized == "false" || normalized == "no" || normalized == "off") {
        return false;
    }
    return fallback;
}

int parseIntOptionString(const char* value, int fallback) {
    if (!value) return fallback;
    char* end = nullptr;
    const long parsed = std::strtol(value, &end, 10);
    if (end == value || (end && *end != '\0')) {
        return fallback;
    }
    return static_cast<int>(parsed);
}

double panningAmountForMode(int mode) {
    switch (mode) {
        case 0: return 2.0; // Mono
        case 1: return 1.5; // Some
        case 2: return 1.0; // 50/50
        case 3: return 0.5; // Lots
        case 4: return 0.0; // Full stereo
        default: return 0.0;
    }
}

std::string getRuntimeBaseDir() {
    std::lock_guard<std::mutex> lock(gRuntimeConfigMutex);
    return gRuntimeBaseDir;
}

std::string getRuntimeUadeCorePath() {
    std::lock_guard<std::mutex> lock(gRuntimeConfigMutex);
    return gUadeCorePath;
}

std::string buildUadeMuteMaskOption(uint32_t muteMask) {
    std::string option;
    option.reserve(10);
    for (int i = 0; i <= 9; ++i) {
        if ((muteMask & (1u << static_cast<uint32_t>(i))) != 0u) {
            option.push_back(static_cast<char>('0' + i));
        }
    }
    return option;
}

int uadeVoiceBitForUiIndex(int uiIndex) {
    // uadecore voice layout is 0,1,2,3 where (0,3)=left and (1,2)=right.
    // UI order requested: L1, L2, R1, R2.
    switch (uiIndex) {
        case 0: return 0; // L1
        case 1: return 3; // L2
        case 2: return 1; // R1
        case 3: return 2; // R2
        default: return uiIndex;
    }
}
}

UadeDecoder::UadeDecoder() = default;

UadeDecoder::~UadeDecoder() {
    close();
}

void UadeDecoder::setRuntimePaths(const std::string& baseDir, const std::string& uadeCorePath) {
    std::lock_guard<std::mutex> lock(gRuntimeConfigMutex);
    gRuntimeBaseDir = baseDir;
    gUadeCorePath = uadeCorePath;
}

bool UadeDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();

    if (!path || path[0] == '\0') {
        return false;
    }

    sourcePath = path;
    state = createStateLocked();
    if (!state) {
        LOGE("uade_new_state failed");
        return false;
    }

    const int playRc = uade_play(sourcePath.c_str(), -1, state);
    if (playRc != 1) {
        LOGE("uade_play failed for %s (rc=%d)", sourcePath.c_str(), playRc);
        closeInternalLocked();
        return false;
    }

    sampleRateHz = std::max(8000, uade_get_sampling_rate(state));
    channels = 2;
    bitDepth = 16;
    renderedFrames = 0;
    playbackPositionSeconds = 0.0;
    pcmScratch.clear();
    applyToggleMutesLocked();
    refreshSongInfoLocked();
    return true;
}

uade_state* UadeDecoder::createStateLocked() {
    uade_config* config = nullptr;
    const std::string runtimeBaseDir = getRuntimeBaseDir();
    const std::string runtimeUadeCorePath = getRuntimeUadeCorePath();
    if (!runtimeBaseDir.empty()) {
        config = uade_new_config();
        if (!config) {
            LOGE("uade_new_config failed; falling back to default config");
        } else {
            const std::string uadeCorePath =
                    !runtimeUadeCorePath.empty() ? runtimeUadeCorePath : (runtimeBaseDir + "/uadecore");
            const std::string scorePath = runtimeBaseDir + "/score";
            const std::string uaercPath = runtimeBaseDir + "/uaerc";
            const int coreExecCheck = access(uadeCorePath.c_str(), X_OK);
            const int coreErrno = (coreExecCheck != 0) ? errno : 0;
            const int scoreReadCheck = access(scorePath.c_str(), R_OK);
            const int scoreErrno = (scoreReadCheck != 0) ? errno : 0;
            const int uaercReadCheck = access(uaercPath.c_str(), R_OK);
            const int uaercErrno = (uaercReadCheck != 0) ? errno : 0;
            ensureToggleChannelsLocked();
            const std::string muteMaskOption = buildUadeMuteMaskOption(getToggleMuteMaskLocked());
            uade_config_set_option(config, UC_BASE_DIR, runtimeBaseDir.c_str());
            uade_config_set_option(config, UC_UADECORE_FILE, uadeCorePath.c_str());
            uade_config_set_option(config, UC_SCORE_FILE, scorePath.c_str());
            uade_config_set_option(config, UC_UAE_CONFIG_FILE, uaercPath.c_str());
            uade_config_set_option(config, UC_ONE_SUBSONG, nullptr);
            uade_config_set_option(config, UC_MUTEMASK, muteMaskOption.c_str());
            uade_config_set_option(config, UC_FREQUENCY, std::to_string(requestedSampleRateHz).c_str());
            if (optionFilterEnabled) {
                uade_config_set_option(config, UC_FILTER_TYPE, "a500");
            } else {
                uade_config_set_option(config, UC_NO_FILTER, nullptr);
            }
            if (optionNtscMode) {
                uade_config_set_option(config, UC_NTSC, nullptr);
            } else {
                uade_config_set_option(config, UC_PAL, nullptr);
            }
            if (optionPanningMode >= 4) {
                uade_config_set_option(config, UC_NO_PANNING, nullptr);
            } else {
                char panningValue[16];
                std::snprintf(
                        panningValue,
                        sizeof(panningValue),
                        "%.2f",
                        panningAmountForMode(optionPanningMode)
                );
                uade_config_set_option(config, UC_PANNING_VALUE, panningValue);
            }
            // Keep UADE running internally; app repeat modes enforce end/restart semantics.
            uade_config_set_option(config, UC_NO_EP_END, nullptr);
            uade_config_set_option(config, UC_DISABLE_TIMEOUTS, nullptr);
            if (coreExecCheck != 0 || scoreReadCheck != 0 || uaercReadCheck != 0) {
                LOGE(
                        "UADE runtime assets missing/inaccessible: base=%s core=%s "
                        "(coreX=%d errno=%d:%s scoreR=%d errno=%d:%s uaercR=%d errno=%d:%s)",
                        runtimeBaseDir.c_str(),
                        uadeCorePath.c_str(),
                        coreExecCheck,
                        coreErrno,
                        std::strerror(coreErrno),
                        scoreReadCheck,
                        scoreErrno,
                        std::strerror(scoreErrno),
                        uaercReadCheck,
                        uaercErrno,
                        std::strerror(uaercErrno)
                );
            }
        }
    } else {
        LOGE("UADE runtime base dir is not configured; default compiled paths will be used");
    }

    uade_state* created = uade_new_state(config);
    if (config) {
        std::free(config);
        config = nullptr;
    }
    return created;
}

void UadeDecoder::closeInternalLocked() {
    if (state) {
        uade_stop(state);
        uade_cleanup_state(state);
        state = nullptr;
    }
    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    sampleRateHz = 44100;
    channels = 2;
    bitDepth = 16;
    subtuneMin = 0;
    subtuneMax = 0;
    subtuneDefault = 0;
    currentSubsong = 0;
    detectionByContent = false;
    detectionIsCustom = false;
    moduleBytes = 0;
    songBytes = 0;
    subsongBytes = 0;
    formatName.clear();
    moduleName.clear();
    playerName.clear();
    moduleFileName.clear();
    playerFileName.clear();
    moduleMd5.clear();
    detectionExtension.clear();
    detectedFormatName.clear();
    detectedFormatVersion.clear();
    durationReliable.store(false);
    durationSeconds = 0.0;
    renderedFrames = 0;
    playbackPositionSeconds = 0.0;
    pcmScratch.clear();
    toggleChannelMuted.clear();
}

void UadeDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

bool UadeDecoder::refreshSongInfoLocked() {
    if (!state) {
        return false;
    }

    const struct uade_song_info* info = uade_get_song_info(state);
    if (!info) {
        return false;
    }

    subtuneMin = info->subsongs.min;
    subtuneMax = info->subsongs.max;
    subtuneDefault = clampSubsong(info->subsongs.def, subtuneMin, subtuneMax);
    currentSubsong = clampSubsong(info->subsongs.cur, subtuneMin, subtuneMax);
    detectionByContent = info->detectioninfo.content != 0;
    detectionIsCustom = info->detectioninfo.custom != 0;
    moduleBytes = static_cast<int64_t>(info->modulebytes);
    songBytes = info->songbytes;
    subsongBytes = info->subsongbytes;
    formatName = safeString(info->formatname);
    moduleName = safeString(info->modulename);
    playerName = safeString(info->playername);
    moduleFileName = safeString(info->modulefname);
    playerFileName = safeString(info->playerfname);
    moduleMd5 = safeString(info->modulemd5);
    detectionExtension = safeString(info->detectioninfo.ext);
    detectedFormatName.clear();
    detectedFormatVersion.clear();
    if (!detectionExtension.empty()) {
        if (const auto* detected = uade_file_ext_to_format_version(&info->detectioninfo)) {
            if (detected->format != nullptr) {
                detectedFormatName = detected->format;
            }
            if (detected->version != nullptr) {
                detectedFormatVersion = detected->version;
            }
        }
    }

    title = moduleName;
    if (title.empty()) {
        title = std::filesystem::path(sourcePath).stem().string();
    }
    artist = playerName;
    composer = artist;
    genre = safeString(info->formatname);

    const double reportedDuration = info->duration;
    const bool hasReliableDuration =
            reportedDuration > 0.0 && std::isfinite(reportedDuration);
    durationReliable.store(hasReliableDuration);
    durationSeconds = hasReliableDuration
            ? reportedDuration
            : ((unknownDurationSeconds > 0) ? static_cast<double>(unknownDurationSeconds) : 0.0);

    return true;
}

int UadeDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);

    if (!state || !buffer || numFrames <= 0) {
        return 0;
    }

    int framesToRead = numFrames;
    const int mode = repeatMode.load();
    if (mode != 2 && durationSeconds > 0.0 && sampleRateHz > 0) {
        const int64_t durationFrames = static_cast<int64_t>(
                std::llround(durationSeconds * static_cast<double>(sampleRateHz))
        );
        const int64_t remaining = durationFrames - renderedFrames;
        if (remaining <= 0) {
            return 0;
        }
        framesToRead = static_cast<int>(std::min<int64_t>(framesToRead, remaining));
    }

    const int requestedBytes = framesToRead * UADE_BYTES_PER_FRAME;
    if (requestedBytes <= 0) {
        return 0;
    }

    if (static_cast<int>(pcmScratch.size()) < framesToRead * channels) {
        pcmScratch.resize(framesToRead * channels);
    }

    ssize_t bytesRead = uade_read(pcmScratch.data(), static_cast<size_t>(requestedBytes), state);
    if (bytesRead < 0) {
        LOGE("uade_read failed");
        return 0;
    }

    if (bytesRead == 0) {
        if (mode == 2) {
            // LP mode is core-driven: never force decoder-level wrap/seek-back.
            constexpr int kCoreContinuationReadRetries = 64;
            for (int retry = 0; retry < kCoreContinuationReadRetries; ++retry) {
                bytesRead = uade_read(pcmScratch.data(), static_cast<size_t>(requestedBytes), state);
                if (bytesRead != 0) break;
            }
        }

        if (bytesRead <= 0) {
            return 0;
        }
    }

    const int framesRead = static_cast<int>(bytesRead / UADE_BYTES_PER_FRAME);
    renderedFrames += framesRead;
    const int samplesRead = framesRead * channels;
    for (int i = 0; i < samplesRead; ++i) {
        buffer[i] = static_cast<float>(pcmScratch[i]) / 32768.0f;
    }

    const double reportedPosition = uade_get_time_position(UADE_SEEK_SUBSONG_RELATIVE, state);
    if (std::isfinite(reportedPosition) && reportedPosition >= 0.0) {
        playbackPositionSeconds = reportedPosition;
    } else {
        playbackPositionSeconds = static_cast<double>(renderedFrames) / static_cast<double>(sampleRateHz);
    }
    refreshSongInfoLocked();
    return framesRead;
}

void UadeDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);

    if (!state) {
        return;
    }

    const double target = std::max(0.0, seconds);
    const int seekSubsong = (currentSubsong >= 0) ? currentSubsong : -1;
    if (uade_seek(UADE_SEEK_SUBSONG_RELATIVE, target, seekSubsong, state) == 0) {
        renderedFrames = static_cast<int64_t>(std::llround(target * static_cast<double>(sampleRateHz)));
        if (renderedFrames < 0) renderedFrames = 0;
        playbackPositionSeconds = target;
    }
}

double UadeDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!state) return 0.0;
    refreshSongInfoLocked();
    return durationSeconds > 0.0 ? durationSeconds : 0.0;
}

int UadeDecoder::getSampleRate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sampleRateHz;
}

int UadeDecoder::getBitDepth() {
    return bitDepth;
}

std::string UadeDecoder::getBitDepthLabel() {
    return "16-bit";
}

int UadeDecoder::getDisplayChannelCount() {
    return channels;
}

int UadeDecoder::getChannelCount() {
    return channels;
}

int UadeDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (subtuneMax < subtuneMin) return 1;
    return std::max(1, subtuneMax - subtuneMin + 1);
}

int UadeDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return std::max(0, currentSubsong - subtuneMin);
}

bool UadeDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);

    if (!state) return false;
    if (subtuneMax < subtuneMin) return false;
    if (index < 0 || index >= (subtuneMax - subtuneMin + 1)) return false;

    const int targetSubsong = subtuneMin + index;
    if (uade_stop(state) < 0) return false;
    const int playRc = uade_play(sourcePath.c_str(), targetSubsong, state);
    if (playRc != 1) return false;

    playbackPositionSeconds = 0.0;
    renderedFrames = 0;
    refreshSongInfoLocked();
    return true;
}

std::string UadeDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const int count = (subtuneMax >= subtuneMin) ? (subtuneMax - subtuneMin + 1) : 1;
    if (index < 0 || index >= count) return "";
    return title;
}

std::string UadeDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const int count = (subtuneMax >= subtuneMin) ? (subtuneMax - subtuneMin + 1) : 1;
    if (index < 0 || index >= count) return "";
    return artist;
}

double UadeDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const int count = (subtuneMax >= subtuneMin) ? (subtuneMax - subtuneMin + 1) : 1;
    if (index < 0 || index >= count) return 0.0;
    return durationSeconds > 0.0 ? durationSeconds : 0.0;
}

std::string UadeDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string UadeDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string UadeDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string UadeDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

void UadeDecoder::setOutputSampleRate(int sampleRateHz) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    requestedSampleRateHz = std::clamp(sampleRateHz, 8000, 192000);
}

std::string UadeDecoder::getFormatName() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return formatName;
}

std::string UadeDecoder::getModuleName() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return moduleName;
}

std::string UadeDecoder::getPlayerName() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return playerName;
}

std::string UadeDecoder::getModuleFileName() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return moduleFileName;
}

std::string UadeDecoder::getPlayerFileName() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return playerFileName;
}

std::string UadeDecoder::getModuleMd5() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return moduleMd5;
}

std::string UadeDecoder::getDetectionExtension() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return detectionExtension;
}

std::string UadeDecoder::getDetectedFormatName() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return detectedFormatName;
}

std::string UadeDecoder::getDetectedFormatVersion() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return detectedFormatVersion;
}

bool UadeDecoder::getDetectionByContent() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return detectionByContent;
}

bool UadeDecoder::getDetectionIsCustom() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return detectionIsCustom;
}

int UadeDecoder::getSubsongMin() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return subtuneMin;
}

int UadeDecoder::getSubsongMax() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return subtuneMax;
}

int UadeDecoder::getSubsongDefault() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return subtuneDefault;
}

int UadeDecoder::getCurrentSubsong() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return currentSubsong;
}

int64_t UadeDecoder::getModuleBytes() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return moduleBytes;
}

int64_t UadeDecoder::getSongBytes() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return songBytes;
}

int64_t UadeDecoder::getSubsongBytes() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return subsongBytes;
}

std::vector<std::string> UadeDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    ensureToggleChannelsLocked();
    return toggleChannelNames;
}

std::vector<uint8_t> UadeDecoder::getToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    ensureToggleChannelsLocked();
    return std::vector<uint8_t>(toggleChannelNames.size(), 1u);
}

void UadeDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    ensureToggleChannelsLocked();
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyToggleMutesLocked();
}

bool UadeDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void UadeDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    ensureToggleChannelsLocked();
    if (toggleChannelMuted.empty()) {
        return;
    }
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyToggleMutesLocked();
}

uint32_t UadeDecoder::getToggleMuteMaskLocked() const {
    uint32_t mask = 0;
    const int count = std::min<int>(static_cast<int>(toggleChannelMuted.size()), 4);
    for (int i = 0; i < count; ++i) {
        if (toggleChannelMuted[static_cast<size_t>(i)]) {
            const int bit = uadeVoiceBitForUiIndex(i);
            if (bit >= 0 && bit < 32) {
                mask |= (1u << static_cast<uint32_t>(bit));
            }
        }
    }
    return mask;
}

void UadeDecoder::applyToggleMutesLocked() {
    if (!state) {
        return;
    }
    const uint32_t muteMask = getToggleMuteMaskLocked();
    state->config.mutemask = static_cast<int>(muteMask);
    state->config.mutemask_set = 1;
    const auto priorIpcState = state->ipc.state;
    if (priorIpcState == UADE_R_STATE) {
        state->ipc.state = UADE_S_STATE;
    }
    const int rc = uade_send_u32(UADE_COMMAND_SET_MUTE_MASK, muteMask, &state->ipc);
    if (priorIpcState == UADE_R_STATE) {
        state->ipc.state = UADE_R_STATE;
    }
    if (rc != 0) {
        LOGE("applyToggleMutesLocked: failed to send mute mask=%u rc=%d", muteMask, rc);
    }
}

void UadeDecoder::ensureToggleChannelsLocked() {
    if (!toggleChannelNames.empty() && toggleChannelMuted.size() == toggleChannelNames.size()) {
        return;
    }
    toggleChannelNames = {
            "Paula L1",
            "Paula L2",
            "Paula R1",
            "Paula R2"
    };
    if (toggleChannelMuted.size() != toggleChannelNames.size()) {
        toggleChannelMuted.assign(toggleChannelNames.size(), false);
    }
}

void UadeDecoder::setOption(const char* name, const char* value) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!name || !value) {
        return;
    }

    if (std::strcmp(name, "uade.filter_enabled") == 0) {
        optionFilterEnabled = parseBoolOptionString(value, optionFilterEnabled);
    } else if (std::strcmp(name, "uade.ntsc_mode") == 0) {
        optionNtscMode = parseBoolOptionString(value, optionNtscMode);
    } else if (std::strcmp(name, "uade.panning_mode") == 0) {
        optionPanningMode = std::clamp(parseIntOptionString(value, optionPanningMode), 0, 4);
    } else if (std::strcmp(name, "uade.unknown_duration_seconds") == 0) {
        unknownDurationSeconds = std::clamp(parseIntOptionString(value, unknownDurationSeconds), 1, 86400);
        if (!durationReliable.load()) {
            durationSeconds = static_cast<double>(unknownDurationSeconds);
        }
    }
}

int UadeDecoder::getOptionApplyPolicy(const char* name) const {
    if (!name) {
        return OPTION_APPLY_LIVE;
    }
    if (std::strcmp(name, "uade.filter_enabled") == 0 ||
        std::strcmp(name, "uade.ntsc_mode") == 0 ||
        std::strcmp(name, "uade.panning_mode") == 0) {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (std::strcmp(name, "uade.unknown_duration_seconds") == 0) {
        return OPTION_APPLY_LIVE;
    }
    return OPTION_APPLY_LIVE;
}

void UadeDecoder::setRepeatMode(int mode) {
    int normalized = mode;
    if (normalized < 0 || normalized > 3) normalized = 0;
    repeatMode.store(normalized);
}

int UadeDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int UadeDecoder::getPlaybackCapabilities() const {
    int caps = PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    if (durationReliable.load()) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

double UadeDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!state) return 0.0;
    const double reportedPosition = uade_get_time_position(UADE_SEEK_SUBSONG_RELATIVE, state);
    if (std::isfinite(reportedPosition) && reportedPosition >= 0.0) {
        playbackPositionSeconds = reportedPosition;
    } else if (sampleRateHz > 0) {
        playbackPositionSeconds = static_cast<double>(renderedFrames) / static_cast<double>(sampleRateHz);
    }
    return std::max(0.0, playbackPositionSeconds);
}

std::vector<std::string> UadeDecoder::getSupportedExtensions() {
    static const std::vector<std::string> extensions = {
            "!pm!",
            "40a",
            "40b",
            "41a",
            "50a",
            "60a",
            "61a",
            "aam",
            "abk",
            "ac1",
            "ac1d",
            "adpcm",
            "adsc",
            "agi",
            "ahx",
            "alp",
            "amc",
            "aon",
            "aon4",
            "aon8",
            "aps",
            "arp",
            "ash",
            "ast",
            "aval",
            "avp",
            "bd",
            "bds",
            "bfc",
            "bp",
            "bp3",
            "bsi",
            "bss",
            "bye",
            "chan",
            "cin",
            "cm",
            "core",
            "cp",
            "cplx",
            "crb",
            "cus",
            "cust",
            "custom",
            "dat",
            "db",
            "dh",
            "di",
            "digi",
            "dl",
            "dl_deli",
            "dlm1",
            "dlm2",
            "dln",
            "dm",
            "dm1",
            "dm2",
            "dmu",
            "dmu2",
            "dns",
            "doda",
            "dp",
            "dsc",
            "dsr",
            "dss",
            "dum",
            "dw",
            "dwold",
            "dz",
            "ea",
            "emod",
            "ems",
            "emsv6",
            "eu",
            "ex",
            "fc",
            "fc-bsi",
            "fc-m",
            "fc13",
            "fc14",
            "fc3",
            "fc4",
            "fcm",
            "fp",
            "fred",
            "ft",
            "ftm",
            "fuz",
            "fuzz",
            "fw",
            "glue",
            "gm",
            "gmc",
            "gray",
            "gv",
            "hd",
            "hip",
            "hip7",
            "hipc",
            "hmc",
            "hn",
            "hot",
            "hrt",
            "hrt!",
            "hst",
            "ice",
            "ims",
            "is",
            "is20",
            "ism",
            "it1",
            "jam",
            "jb",
            "jc",
            "jcb",
            "jcbo",
            "jd",
            "jmf",
            "jo",
            "jp",
            "jpn",
            "jpnd",
            "jpo",
            "jpold",
            "js",
            "jt",
            "kef",
            "kef7",
            "kh",
            "kim",
            "kris",
            "krs",
            "ksm",
            "lax",
            "lion",
            "lme",
            "ma",
            "max",
            "mc",
            "mcmd",
            "mcmd_org",
            "mco",
            "mcr",
            "md",
            "mdat",
            "mdst",
            "med",
            "mexxmp",
            "mfp",
            "mg",
            "midi",
            "mk2",
            "mkii",
            "mkiio",
            "ml",
            "mm4",
            "mm8",
            "mmd0",
            "mmd1",
            "mmd2",
            "mmd3",
            "mmdc",
            "mms",
            "mod",
            "mod15",
            "mod15_mst",
            "mod15_st-iv",
            "mod15_ust",
            "mod3",
            "mod_adsc4",
            "mod_comp",
            "mod_doc",
            "mod_flt4",
            "mod_ntk",
            "mod_ntk1",
            "mod_ntk2",
            "mod_ntkamp",
            "mok",
            "mon",
            "mon_old",
            "mosh",
            "mpro",
            "mso",
            "mth",
            "mtp2",
            "mug",
            "mug2",
            "mus",
            "mw",
            "noisepacker2",
            "noisepacker3",
            "np",
            "np1",
            "np2",
            "np3",
            "npp",
            "nr",
            "nru",
            "ntp",
            "ntpk",
            "octamed",
            "okt",
            "okta",
            "oldw",
            "one",
            "osp",
            "oss",
            "p10",
            "p21",
            "p30",
            "p40a",
            "p40b",
            "p41a",
            "p4x",
            "p50a",
            "p5a",
            "p5x",
            "p60",
            "p60a",
            "p61",
            "p61a",
            "p6x",
            "pap",
            "pat",
            "pha",
            "pin",
            "pm",
            "pm0",
            "pm01",
            "pm1",
            "pm10c",
            "pm18a",
            "pm2",
            "pm20",
            "pm4",
            "pm40",
            "pmz",
            "pn",
            "polk",
            "powt",
            "pp10",
            "pp20",
            "pp21",
            "pp30",
            "ppk",
            "pr1",
            "pr2",
            "prom",
            "prt",
            "pru",
            "pru1",
            "pru2",
            "prun",
            "prun1",
            "prun2",
            "ps",
            "psa",
            "psf",
            "pt",
            "ptm",
            "puma",
            "pvp",
            "pwr",
            "pyg",
            "pygm",
            "pygmy",
            "qc",
            "qpa",
            "qts",
            "rh",
            "rho",
            "riff",
            "rj",
            "rjp",
            "rk",
            "rkb",
            "s-c",
            "s7g",
            "sa",
            "sa-p",
            "sa_old",
            "sas",
            "sb",
            "sc",
            "scn",
            "scr",
            "sct",
            "scumm",
            "sdata",
            "sdc",
            "sdr",
            "sfx",
            "sfx13",
            "sfx20",
            "sg",
            "sid",
            "sid1",
            "sid2",
            "sj",
            "sjs",
            "skt",
            "skyt",
            "sm",
            "sm1",
            "sm2",
            "sm3",
            "smn",
            "smod",
            "smpro",
            "smus",
            "sndmon",
            "sng",
            "snk",
            "snt",
            "snt!",
            "snx",
            "soc",
            "sog",
            "sonic",
            "spl",
            "sqt",
            "ss",
            "st",
            "st2",
            "st26",
            "st30",
            "star",
            "stpk",
            "sun",
            "syn",
            "synmod",
            "tcb",
            "tf",
            "tfhd1.5",
            "tfhd7v",
            "tfhdpro",
            "tfmx",
            "tfmx1.5",
            "tfmx7v",
            "tfmxpro",
            "thm",
            "thn",
            "thx",
            "tiny",
            "tits",
            "tme",
            "tmk",
            "tp",
            "tp1",
            "tp2",
            "tp3",
            "tpu",
            "trc",
            "tro",
            "tron",
            "tronic",
            "tw",
            "two",
            "uds",
            "ufo",
            "un2",
            "unic",
            "unic2",
            "vss",
            "wb",
            "wn",
            "xan",
            "xann",
            "ym",
            "ymst",
            "zen",
    };
    return extensions;
}
