#include "LibSidPlayFpDecoder.h"

#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <sstream>
#include <vector>

#include <sidplayfp/sidplayfp.h>
#include <sidplayfp/SidConfig.h>
#include <sidplayfp/SidInfo.h>
#include <sidplayfp/SidTune.h>
#include <sidplayfp/SidTuneInfo.h>
#include <sidplayfp/builders/sidlite.h>

#define LOG_TAG "LibSidPlayFpDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr unsigned int kRenderCyclesMin = 2000;
constexpr unsigned int kRenderCyclesMax = 20000;
constexpr unsigned int kEstimatedSidCyclesPerSecond = 1000000;
constexpr int kTransientEmptyPlayRetries = 4;
constexpr double kDefaultSidDurationSeconds = 180.0;

std::string safeString(const char* value) {
    return value ? std::string(value) : "";
}

unsigned int computeRenderCyclesForFrames(int framesNeeded, int sampleRateHz) {
    if (framesNeeded <= 0 || sampleRateHz <= 0) {
        return kRenderCyclesMin;
    }
    const uint64_t estimated =
            (static_cast<uint64_t>(framesNeeded) * kEstimatedSidCyclesPerSecond) /
            static_cast<uint64_t>(sampleRateHz);
    return static_cast<unsigned int>(std::clamp<uint64_t>(
            estimated,
            kRenderCyclesMin,
            kRenderCyclesMax
    ));
}

int parseIntString(const std::string& raw, int fallback) {
    try {
        size_t consumed = 0;
        const int parsed = std::stoi(raw, &consumed);
        if (consumed != raw.size()) return fallback;
        return parsed;
    } catch (...) {
        return fallback;
    }
}

std::string sidClockToString(SidTuneInfo::clock_t clock) {
    switch (clock) {
        case SidTuneInfo::CLOCK_PAL: return "PAL";
        case SidTuneInfo::CLOCK_NTSC: return "NTSC";
        case SidTuneInfo::CLOCK_ANY: return "Any";
        case SidTuneInfo::CLOCK_UNKNOWN:
        default: return "Unknown";
    }
}

std::string sidCompatibilityToString(SidTuneInfo::compatibility_t compatibility) {
    switch (compatibility) {
        case SidTuneInfo::COMPATIBILITY_C64: return "C64";
        case SidTuneInfo::COMPATIBILITY_PSID: return "PSID";
        case SidTuneInfo::COMPATIBILITY_R64: return "Real C64";
        case SidTuneInfo::COMPATIBILITY_BASIC: return "C64 BASIC";
        default: return "Unknown";
    }
}

std::string sidModelToString(SidTuneInfo::model_t model) {
    switch (model) {
        case SidTuneInfo::SIDMODEL_6581: return "6581";
        case SidTuneInfo::SIDMODEL_8580: return "8580";
        case SidTuneInfo::SIDMODEL_ANY: return "Any";
        case SidTuneInfo::SIDMODEL_UNKNOWN:
        default: return "Unknown";
    }
}

std::string sidBaseAddressToString(uint_least16_t address) {
    std::ostringstream out;
    out << "0x" << std::hex << std::uppercase << static_cast<int>(address);
    return out.str();
}
}

LibSidPlayFpDecoder::LibSidPlayFpDecoder() = default;

LibSidPlayFpDecoder::~LibSidPlayFpDecoder() {
    close();
}

std::vector<std::string> LibSidPlayFpDecoder::getSupportedExtensions() {
    return {
            "sid", "psid", "rsid", "mus", "str",
            "prg", "p00", "c64", "dat"
    };
}

bool LibSidPlayFpDecoder::applyConfigLocked() {
    if (!player || !config || !sidBuilder) return false;
    config->frequency = static_cast<uint_least32_t>(requestedSampleRate);
    config->playback = SidConfig::STEREO;
    config->samplingMethod = SidConfig::INTERPOLATE;
    config->sidEmulation = sidBuilder.get();
    if (!player->config(*config)) {
        LOGE("sidplayfp config failed: %s", player->error());
        return false;
    }
    activeSampleRate = requestedSampleRate;
    return true;
}

void LibSidPlayFpDecoder::refreshMetadataLocked() {
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    sidChipCount = 1;
    sidVoiceCount = 3;
    sidFormatName.clear();
    sidClockName.clear();
    sidSpeedName.clear();
    sidCompatibilityName.clear();
    sidModelSummary.clear();
    sidBaseAddressSummary.clear();
    sidCommentSummary.clear();
    subtuneTitles.assign(std::max(1, subtuneCount), "");
    subtuneArtists.assign(std::max(1, subtuneCount), "");
    subtuneDurationsSeconds.assign(std::max(1, subtuneCount), fallbackDurationSeconds);

    if (!tune) return;
    const SidTuneInfo* info = tune->getInfo();
    if (info == nullptr) return;

    sidChipCount = std::max(1, info->sidChips());
    if (player) {
        sidChipCount = std::max(sidChipCount, static_cast<int>(player->info().numberOfSIDs()));
        sidSpeedName = safeString(player->info().speedString());
    }
    sidVoiceCount = std::max(1, sidChipCount * 3);
    sidFormatName = safeString(info->formatString());
    sidClockName = sidClockToString(info->clockSpeed());
    sidCompatibilityName = sidCompatibilityToString(info->compatibility());

    {
        std::ostringstream modelSummary;
        bool hasModel = false;
        for (int i = 0; i < sidChipCount; ++i) {
            if (hasModel) modelSummary << ", ";
            modelSummary << "SID" << (i + 1) << ":" << sidModelToString(info->sidModel(static_cast<unsigned int>(i)));
            hasModel = true;
        }
        sidModelSummary = hasModel ? modelSummary.str() : "";
    }

    {
        std::ostringstream baseSummary;
        bool hasBase = false;
        for (int i = 0; i < sidChipCount; ++i) {
            const uint_least16_t base = info->sidChipBase(static_cast<unsigned int>(i));
            if (base == 0u) continue;
            if (hasBase) baseSummary << ", ";
            baseSummary << "SID" << (i + 1) << ":" << sidBaseAddressToString(base);
            hasBase = true;
        }
        sidBaseAddressSummary = hasBase ? baseSummary.str() : "";
    }

    if (info->numberOfCommentStrings() > 0) {
        std::ostringstream comments;
        bool hasComment = false;
        for (unsigned int i = 0; i < info->numberOfCommentStrings(); ++i) {
            const std::string comment = safeString(info->commentString(i));
            if (comment.empty()) continue;
            if (hasComment) comments << " | ";
            comments << comment;
            hasComment = true;
        }
        sidCommentSummary = hasComment ? comments.str() : "";
    }
    const unsigned int numInfoStrings = info->numberOfInfoStrings();
    if (numInfoStrings > 0) title = safeString(info->infoString(0));
    if (numInfoStrings > 1) artist = safeString(info->infoString(1));
    if (numInfoStrings > 2) genre = safeString(info->infoString(2));
    composer = artist;

    for (int i = 0; i < subtuneCount; ++i) {
        const SidTuneInfo* songInfo = tune->getInfo(static_cast<unsigned int>(i + 1));
        if (songInfo == nullptr) continue;
        const unsigned int songInfoStrings = songInfo->numberOfInfoStrings();
        if (songInfoStrings > 0) subtuneTitles[i] = safeString(songInfo->infoString(0));
        if (songInfoStrings > 1) subtuneArtists[i] = safeString(songInfo->infoString(1));
    }
}

bool LibSidPlayFpDecoder::selectSubtuneLocked(int index) {
    if (!player || !tune || index < 0 || index >= subtuneCount) {
        return false;
    }
    tune->selectSong(static_cast<unsigned int>(index + 1));
    if (!player->load(tune.get())) {
        LOGE("sidplayfp load(subtune) failed: %s", player->error());
        return false;
    }
    player->reset();
    player->initMixer(true);
    const SidInfo& info = player->info();
    outputChannels = std::clamp(static_cast<int>(info.channels()), 1, 2);
    const int runtimeSidChips = std::max(1, static_cast<int>(info.numberOfSIDs()));
    sidChipCount = runtimeSidChips;
    sidVoiceCount = std::max(1, runtimeSidChips * 3);
    sidSpeedName = safeString(info.speedString());
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
    currentSubtuneIndex = index;
    return true;
}

bool LibSidPlayFpDecoder::openInternalLocked(const char* path) {
    if (!path) return false;

    player = std::make_unique<sidplayfp>();
    sidBuilder = std::make_unique<SIDLiteBuilder>("SiliconPlayer");
    config = std::make_unique<SidConfig>(player->config());

    if (!applyConfigLocked()) {
        return false;
    }

    tune = std::make_unique<SidTune>(path);
    if (!tune->getStatus()) {
        LOGE("SidTune open failed: %s", tune->statusString());
        return false;
    }

    const SidTuneInfo* tuneInfo = tune->getInfo();
    subtuneCount = tuneInfo ? std::max(1u, tuneInfo->songs()) : 1u;
    const unsigned int startSong = tuneInfo ? tuneInfo->startSong() : 1u;
    currentSubtuneIndex = std::clamp(static_cast<int>(startSong) - 1, 0, subtuneCount - 1);
    if (!(fallbackDurationSeconds > 0.0)) {
        fallbackDurationSeconds = kDefaultSidDurationSeconds;
    }

    if (!selectSubtuneLocked(currentSubtuneIndex)) {
        return false;
    }
    refreshMetadataLocked();

    return true;
}

bool LibSidPlayFpDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    tune.reset();
    sidBuilder.reset();
    config.reset();
    player.reset();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    subtuneTitles.clear();
    subtuneArtists.clear();
    subtuneDurationsSeconds.clear();
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    outputChannels = 2;
    sidChipCount = 1;
    sidVoiceCount = 3;
    sidFormatName.clear();
    sidClockName.clear();
    sidSpeedName.clear();
    sidCompatibilityName.clear();
    sidModelSummary.clear();
    sidBaseAddressSummary.clear();
    sidCommentSummary.clear();
    return openInternalLocked(path);
}

void LibSidPlayFpDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    tune.reset();
    sidBuilder.reset();
    config.reset();
    player.reset();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    subtuneTitles.clear();
    subtuneArtists.clear();
    subtuneDurationsSeconds.clear();
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    outputChannels = 2;
    sidChipCount = 1;
    sidVoiceCount = 3;
    sidFormatName.clear();
    sidClockName.clear();
    sidSpeedName.clear();
    sidCompatibilityName.clear();
    sidModelSummary.clear();
    sidBaseAddressSummary.clear();
    sidCommentSummary.clear();
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
}

int LibSidPlayFpDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !buffer || numFrames <= 0) return 0;
    const int channels = std::clamp(outputChannels, 1, 2);

    int framesWritten = 0;
    int emptyPlayRetries = 0;
    while (framesWritten < numFrames) {
        const size_t pendingAvailableSamples =
                pendingMixedSamples.size() > pendingMixedOffset
                        ? (pendingMixedSamples.size() - pendingMixedOffset)
                        : 0u;
        const int pendingFrames = static_cast<int>(pendingAvailableSamples / static_cast<size_t>(channels));
        if (pendingFrames > 0) {
            const int framesToCopy = std::min(numFrames - framesWritten, pendingFrames);
            const int samplesToCopy = framesToCopy * channels;
            for (int i = 0; i < samplesToCopy; ++i) {
                buffer[(framesWritten * channels) + i] =
                        static_cast<float>(pendingMixedSamples[pendingMixedOffset + static_cast<size_t>(i)]) / 32768.0f;
            }
            framesWritten += framesToCopy;
            pendingMixedOffset += static_cast<size_t>(samplesToCopy);

            if (pendingMixedOffset >= pendingMixedSamples.size()) {
                pendingMixedSamples.clear();
                pendingMixedOffset = 0;
            } else if (pendingMixedOffset > 4096u) {
                pendingMixedSamples.erase(
                        pendingMixedSamples.begin(),
                        pendingMixedSamples.begin() + static_cast<std::ptrdiff_t>(pendingMixedOffset)
                );
                pendingMixedOffset = 0;
            }
            continue;
        }

        const int framesRemaining = numFrames - framesWritten;
        const unsigned int renderCycles = computeRenderCyclesForFrames(framesRemaining, activeSampleRate);
        const int produced = player->play(renderCycles);
        if (produced < 0) {
            LOGE("sidplayfp play failed: %s", player->error());
            break;
        }
        if (produced == 0) {
            emptyPlayRetries += 1;
            if (emptyPlayRetries >= kTransientEmptyPlayRetries) {
                if (repeatMode.load() == 2) {
                    if (selectSubtuneLocked(currentSubtuneIndex)) {
                        emptyPlayRetries = 0;
                        continue;
                    }
                }
                break;
            }
            continue;
        }
        emptyPlayRetries = 0;

        std::vector<int16_t> mixed(static_cast<size_t>(produced) * static_cast<size_t>(channels));
        const unsigned int mixedSamples = player->mix(mixed.data(), static_cast<unsigned int>(produced));
        if (mixedSamples < static_cast<unsigned int>(channels)) {
            emptyPlayRetries += 1;
            if (emptyPlayRetries >= kTransientEmptyPlayRetries) {
                if (repeatMode.load() == 2) {
                    if (selectSubtuneLocked(currentSubtuneIndex)) {
                        emptyPlayRetries = 0;
                        continue;
                    }
                }
                break;
            }
            continue;
        }
        emptyPlayRetries = 0;
        pendingMixedSamples.insert(
                pendingMixedSamples.end(),
                mixed.begin(),
                mixed.begin() + static_cast<std::ptrdiff_t>(mixedSamples)
        );
    }

    return framesWritten;
}

void LibSidPlayFpDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !tune) return;
    if (seconds <= 0.0) {
        selectSubtuneLocked(currentSubtuneIndex);
        return;
    }

    // Basic seek fallback: restart subtune then fast-forward in chunks.
    if (!selectSubtuneLocked(currentSubtuneIndex)) return;
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
    const uint32_t targetMs = static_cast<uint32_t>(seconds * 1000.0);
    while (player->timeMs() < targetMs) {
        const unsigned int renderCycles = computeRenderCyclesForFrames(1024, activeSampleRate);
        const int produced = player->play(renderCycles);
        if (produced <= 0) break;
    }
}

double LibSidPlayFpDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (currentSubtuneIndex >= 0 &&
        currentSubtuneIndex < static_cast<int>(subtuneDurationsSeconds.size())) {
        return subtuneDurationsSeconds[currentSubtuneIndex];
    }
    return fallbackDurationSeconds;
}

int LibSidPlayFpDecoder::getSampleRate() {
    return player ? activeSampleRate : requestedSampleRate;
}

void LibSidPlayFpDecoder::setOutputSampleRate(int sampleRateHz) {
    if (sampleRateHz <= 0) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (requestedSampleRate == sampleRateHz) return;
    requestedSampleRate = sampleRateHz;
    // SID sample-rate changes are restart-required.
    // Keep the requested value and apply it on next configure/open.
    if (!player) {
        activeSampleRate = sampleRateHz;
    }
}

void LibSidPlayFpDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    const std::string optionName(name);
    const std::string optionValue(value);
    if (optionName == "sidplayfp.unknown_duration_seconds") {
        const int parsed = parseIntString(optionValue, static_cast<int>(fallbackDurationSeconds));
        const int clamped = std::clamp(parsed, 1, 86400);
        fallbackDurationSeconds = static_cast<double>(clamped);
        if (!subtuneDurationsSeconds.empty()) {
            for (double& durationSeconds : subtuneDurationsSeconds) {
                durationSeconds = fallbackDurationSeconds;
            }
        }
    }
}

int LibSidPlayFpDecoder::getOptionApplyPolicy(const char* name) const {
    if (!name) return OPTION_APPLY_LIVE;
    const std::string optionName(name);
    if (optionName == "sidplayfp.unknown_duration_seconds") {
        return OPTION_APPLY_LIVE;
    }
    return OPTION_APPLY_LIVE;
}

int LibSidPlayFpDecoder::getBitDepth() {
    return 16;
}

std::string LibSidPlayFpDecoder::getBitDepthLabel() {
    return "16 bit";
}

int LibSidPlayFpDecoder::getDisplayChannelCount() {
    return sidVoiceCount;
}

int LibSidPlayFpDecoder::getChannelCount() {
    return outputChannels;
}

int LibSidPlayFpDecoder::getSubtuneCount() const {
    return subtuneCount;
}

int LibSidPlayFpDecoder::getCurrentSubtuneIndex() const {
    return currentSubtuneIndex;
}

bool LibSidPlayFpDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return selectSubtuneLocked(index);
}

std::string LibSidPlayFpDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= static_cast<int>(subtuneTitles.size())) return "";
    return subtuneTitles[index];
}

std::string LibSidPlayFpDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= static_cast<int>(subtuneArtists.size())) return "";
    return subtuneArtists[index];
}

double LibSidPlayFpDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= static_cast<int>(subtuneDurationsSeconds.size())) return 0.0;
    return subtuneDurationsSeconds[index];
}

std::string LibSidPlayFpDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string LibSidPlayFpDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string LibSidPlayFpDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string LibSidPlayFpDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string LibSidPlayFpDecoder::getSidFormatName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidFormatName;
}

std::string LibSidPlayFpDecoder::getSidClockName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidClockName;
}

std::string LibSidPlayFpDecoder::getSidSpeedName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidSpeedName;
}

std::string LibSidPlayFpDecoder::getSidCompatibilityName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidCompatibilityName;
}

int LibSidPlayFpDecoder::getSidChipCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidChipCount;
}

std::string LibSidPlayFpDecoder::getSidModelSummary() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidModelSummary;
}

std::string LibSidPlayFpDecoder::getSidBaseAddressSummary() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidBaseAddressSummary;
}

std::string LibSidPlayFpDecoder::getSidCommentSummary() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidCommentSummary;
}

void LibSidPlayFpDecoder::setRepeatMode(int mode) {
    const int normalizedMode = (mode >= 0 && mode <= 3) ? mode : 0;
    repeatMode.store(normalizedMode);
}

int LibSidPlayFpDecoder::getPlaybackCapabilities() const {
    return PLAYBACK_CAP_SEEK |
           PLAYBACK_CAP_RELIABLE_DURATION |
           PLAYBACK_CAP_LIVE_REPEAT_MODE |
           PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
}

int LibSidPlayFpDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

double LibSidPlayFpDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) return -1.0;
    return static_cast<double>(player->timeMs()) / 1000.0;
}
