#include "LibSidPlayFpDecoder.h"

#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <sstream>
#include <vector>

#include <sidplayfp/sidplayfp.h>
#include <sidplayfp/SidConfig.h>
#include <sidplayfp/SidInfo.h>
#include <sidplayfp/SidTune.h>
#include <sidplayfp/SidTuneInfo.h>
#include <sidplayfp/builders/residfp.h>
#include <sidplayfp/builders/sidlite.h>
#include "sid/ReSidBuilder.h"

#define LOG_TAG "LibSidPlayFpDecoder"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr unsigned int kRenderCyclesMin = 2000;
constexpr unsigned int kRenderCyclesMax = 200000;
constexpr unsigned int kEstimatedSidCyclesPerSecond = 1000000;
constexpr int kTransientEmptyPlayRetries = 12;
constexpr int kSidReadPrefillFrames = 1024;
constexpr double kDefaultSidDurationSeconds = 180.0;
constexpr int kSidLiteMinSampleRateHz = 8000;
constexpr int kSidLiteMaxSampleRateHz = 48000;
constexpr int kSidGlobalMinSampleRateHz = 8000;
constexpr int kSidGlobalMaxSampleRateHz = 192000;

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

double parseDoubleString(const std::string& raw, double fallback) {
    try {
        size_t consumed = 0;
        const double parsed = std::stod(raw, &consumed);
        if (consumed != raw.size()) return fallback;
        return parsed;
    } catch (...) {
        return fallback;
    }
}

bool parseBoolString(const std::string& raw, bool fallback) {
    std::string normalized;
    normalized.reserve(raw.size());
    for (char ch : raw) {
        normalized.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(ch))));
    }
    if (normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on") {
        return true;
    }
    if (normalized == "0" || normalized == "false" || normalized == "no" || normalized == "off") {
        return false;
    }
    return fallback;
}

SidBackend parseSidBackend(const std::string& raw) {
    std::string normalized;
    normalized.reserve(raw.size());
    for (char ch : raw) {
        normalized.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(ch))));
    }
    if (normalized == "2" || normalized == "resid") return SidBackend::ReSID;
    if (normalized == "1" || normalized == "sidlite") return SidBackend::SIDLite;
    return SidBackend::ReSIDfp;
}

int clampSampleRateForBackend(int sampleRateHz, SidBackend backend) {
    const int globalClamped = std::clamp(sampleRateHz, kSidGlobalMinSampleRateHz, kSidGlobalMaxSampleRateHz);
    if (backend == SidBackend::SIDLite) {
        return std::clamp(globalClamped, kSidLiteMinSampleRateHz, kSidLiteMaxSampleRateHz);
    }
    return globalClamped;
}

SidConfig::sid_model_t parseSidModel(const std::string& raw, SidConfig::sid_model_t fallback) {
    std::string normalized;
    normalized.reserve(raw.size());
    for (char ch : raw) {
        normalized.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(ch))));
    }
    if (normalized == "1" || normalized == "6581" || normalized == "mos6581") {
        return SidConfig::MOS6581;
    }
    if (normalized == "2" || normalized == "8580" || normalized == "mos8580") {
        return SidConfig::MOS8580;
    }
    return fallback;
}

SidClockMode parseSidClockMode(const std::string& raw, SidClockMode fallback) {
    std::string normalized;
    normalized.reserve(raw.size());
    for (char ch : raw) {
        normalized.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(ch))));
    }
    if (normalized == "0" || normalized == "auto") return SidClockMode::Auto;
    if (normalized == "1" || normalized == "pal") return SidClockMode::Pal;
    if (normalized == "2" || normalized == "ntsc") return SidClockMode::Ntsc;
    return fallback;
}

SidModelMode parseSidModelMode(const std::string& raw, SidModelMode fallback) {
    std::string normalized;
    normalized.reserve(raw.size());
    for (char ch : raw) {
        normalized.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(ch))));
    }
    if (normalized == "0" || normalized == "auto") return SidModelMode::Auto;
    if (normalized == "1" || normalized == "6581" || normalized == "mos6581") return SidModelMode::Mos6581;
    if (normalized == "2" || normalized == "8580" || normalized == "mos8580") return SidModelMode::Mos8580;
    return fallback;
}

SidConfig::sid_cw_t parseCombinedWaveformsStrength(const std::string& raw, SidConfig::sid_cw_t fallback) {
    std::string normalized;
    normalized.reserve(raw.size());
    for (char ch : raw) {
        normalized.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(ch))));
    }
    if (normalized == "0" || normalized == "average") {
        return SidConfig::AVERAGE;
    }
    if (normalized == "1" || normalized == "weak") {
        return SidConfig::WEAK;
    }
    if (normalized == "2" || normalized == "strong") {
        return SidConfig::STRONG;
    }
    return fallback;
}

std::unique_ptr<sidbuilder> createBuilderForBackend(SidBackend backend) {
    switch (backend) {
        case SidBackend::ReSID:
            return std::make_unique<ReSidBuilder>("SiliconPlayer ReSID");
        case SidBackend::SIDLite:
            return std::make_unique<SIDLiteBuilder>("SiliconPlayer SIDLite");
        case SidBackend::ReSIDfp:
        default:
            return std::make_unique<ReSIDfpBuilder>("SiliconPlayer ReSIDfp");
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
    applySidBackendOptionsLocked();
    const int normalizedSampleRate = clampSampleRateForBackend(requestedSampleRate, activeBackend);
    config->frequency = static_cast<uint_least32_t>(normalizedSampleRate);
    config->playback = SidConfig::STEREO;
    switch (sidClockMode) {
        case SidClockMode::Pal:
            config->defaultC64Model = SidConfig::PAL;
            config->forceC64Model = true;
            break;
        case SidClockMode::Ntsc:
            config->defaultC64Model = SidConfig::NTSC;
            config->forceC64Model = true;
            break;
        case SidClockMode::Auto:
        default:
            config->defaultC64Model = SidConfig::PAL;
            config->forceC64Model = false;
            break;
    }
    switch (sidModelMode) {
        case SidModelMode::Mos6581:
            config->defaultSidModel = SidConfig::MOS6581;
            config->forceSidModel = true;
            break;
        case SidModelMode::Mos8580:
            config->defaultSidModel = SidConfig::MOS8580;
            config->forceSidModel = true;
            break;
        case SidModelMode::Auto:
        default:
            config->defaultSidModel = SidConfig::MOS8580;
            config->forceSidModel = false;
            break;
    }
    const bool force8580Model = (sidModelMode == SidModelMode::Mos8580);
    const bool allowFastSampling = !(activeBackend == SidBackend::ReSID && force8580Model);
    config->samplingMethod =
            (reSidFpFastSampling && allowFastSampling)
            ? SidConfig::INTERPOLATE
            : SidConfig::RESAMPLE_INTERPOLATE;
    config->digiBoost = digiBoost8580;
    config->sidEmulation = sidBuilder.get();
    if (!player->config(*config)) {
        LOGE("sidplayfp config failed: %s", player->error());
        return false;
    }
    activeSampleRate = normalizedSampleRate;
    return true;
}

void LibSidPlayFpDecoder::applySidBackendOptionsLocked() {
    if (!sidBuilder) return;
    if (activeBackend != SidBackend::ReSIDfp) return;
    auto* reSidBuilder = static_cast<ReSIDfpBuilder*>(sidBuilder.get());
    if (!reSidBuilder) return;
    reSidBuilder->filter6581Curve(reSidFpFilterCurve6581);
    reSidBuilder->filter6581Range(reSidFpFilterRange6581);
    reSidBuilder->filter8580Curve(reSidFpFilterCurve8580);
    reSidBuilder->combinedWaveformsStrength(reSidFpCombinedWaveformsStrength);
}

void LibSidPlayFpDecoder::applySidFilterOptionsLocked() {
    if (!player || !tune) return;
    const SidTuneInfo* info = tune->getInfo();
    const int sidCount = std::max(1, static_cast<int>(player->info().numberOfSIDs()));
    for (int sidIndex = 0; sidIndex < sidCount; ++sidIndex) {
        SidConfig::sid_model_t model = SidConfig::MOS8580;
        bool hasForcedModel = false;
        if (sidModelMode == SidModelMode::Mos6581) {
            model = SidConfig::MOS6581;
            hasForcedModel = true;
        } else if (sidModelMode == SidModelMode::Mos8580) {
            model = SidConfig::MOS8580;
            hasForcedModel = true;
        }
        if (!hasForcedModel && info != nullptr) {
            const auto tuneModel = info->sidModel(static_cast<unsigned int>(sidIndex));
            if (tuneModel == SidTuneInfo::SIDMODEL_6581) {
                model = SidConfig::MOS6581;
            } else if (tuneModel == SidTuneInfo::SIDMODEL_8580) {
                model = SidConfig::MOS8580;
            }
        }
        const bool enabled = (model == SidConfig::MOS6581) ? filter6581Enabled : filter8580Enabled;
        player->filter(static_cast<unsigned int>(sidIndex), enabled);
    }
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
    sidCurrentModelSummary.clear();
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
        std::ostringstream modelSummary;
        bool hasModel = false;
        const SidInfo* runtimeInfo = player ? &player->info() : nullptr;
        const int runtimeSidCount = runtimeInfo ? std::max(1, static_cast<int>(runtimeInfo->numberOfSIDs())) : 0;
        for (int i = 0; i < runtimeSidCount; ++i) {
            if (hasModel) modelSummary << ", ";
            modelSummary << "SID" << (i + 1) << ":" << sidModelToString(runtimeInfo->sidModel(static_cast<unsigned int>(i)));
            hasModel = true;
        }
        sidCurrentModelSummary = hasModel ? modelSummary.str() : "";
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
    if (currentSubtuneIndex >= 0 &&
        currentSubtuneIndex < static_cast<int>(subtuneDurationsSeconds.size())) {
        currentSubtuneDurationSecondsAtomic.store(
                subtuneDurationsSeconds[currentSubtuneIndex],
                std::memory_order_relaxed
        );
    } else {
        currentSubtuneDurationSecondsAtomic.store(fallbackDurationSeconds, std::memory_order_relaxed);
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
    applySidFilterOptionsLocked();
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
    playbackPositionSecondsAtomic.store(0.0, std::memory_order_relaxed);
    if (index >= 0 && index < static_cast<int>(subtuneDurationsSeconds.size())) {
        currentSubtuneDurationSecondsAtomic.store(
                subtuneDurationsSeconds[index],
                std::memory_order_relaxed
        );
    } else {
        currentSubtuneDurationSecondsAtomic.store(fallbackDurationSeconds, std::memory_order_relaxed);
    }
    currentSubtuneIndex = index;
    return true;
}

bool LibSidPlayFpDecoder::openInternalLocked(const char* path) {
    if (!path) return false;

    player = std::make_unique<sidplayfp>();
    sidBuilder = createBuilderForBackend(selectedBackend);
    activeBackend = selectedBackend;
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
    currentSubtuneDurationSecondsAtomic.store(fallbackDurationSeconds, std::memory_order_relaxed);
    playbackPositionSecondsAtomic.store(0.0, std::memory_order_relaxed);
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
    pendingMixedSamples.reserve(static_cast<size_t>(kSidReadPrefillFrames) * 4u);
    mixedScratchSamples.clear();
    mixedScratchSamples.reserve(static_cast<size_t>(kSidReadPrefillFrames) * 4u);

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
    sidCurrentModelSummary.clear();
    sidBaseAddressSummary.clear();
    sidCommentSummary.clear();
    playbackPositionSecondsAtomic.store(0.0, std::memory_order_relaxed);
    currentSubtuneDurationSecondsAtomic.store(fallbackDurationSeconds, std::memory_order_relaxed);
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
    mixedScratchSamples.clear();
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
    sidCurrentModelSummary.clear();
    sidBaseAddressSummary.clear();
    sidCommentSummary.clear();
    playbackPositionSecondsAtomic.store(0.0, std::memory_order_relaxed);
    currentSubtuneDurationSecondsAtomic.store(fallbackDurationSeconds, std::memory_order_relaxed);
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
    mixedScratchSamples.clear();
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
                const size_t remainingSamples = pendingMixedSamples.size() - pendingMixedOffset;
                std::memmove(
                        pendingMixedSamples.data(),
                        pendingMixedSamples.data() + pendingMixedOffset,
                        remainingSamples * sizeof(int16_t)
                );
                pendingMixedSamples.resize(remainingSamples);
                pendingMixedOffset = 0;
            }
            continue;
        }

        const int framesRemaining = numFrames - framesWritten;
        const int renderTargetFrames = std::max(framesRemaining, kSidReadPrefillFrames);
        const unsigned int renderCycles = computeRenderCyclesForFrames(renderTargetFrames, activeSampleRate);
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

        const size_t requiredMixedSamples = static_cast<size_t>(produced) * static_cast<size_t>(channels);
        if (mixedScratchSamples.size() < requiredMixedSamples) {
            mixedScratchSamples.resize(requiredMixedSamples);
        }
        const unsigned int mixedSamples = player->mix(
                mixedScratchSamples.data(),
                static_cast<unsigned int>(produced)
        );
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
                mixedScratchSamples.begin(),
                mixedScratchSamples.begin() + static_cast<std::ptrdiff_t>(mixedSamples)
        );
    }

    playbackPositionSecondsAtomic.store(
            static_cast<double>(player->timeMs()) / 1000.0,
            std::memory_order_relaxed
    );
    return framesWritten;
}

void LibSidPlayFpDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !tune) return;
    if (seconds <= 0.0) {
        selectSubtuneLocked(currentSubtuneIndex);
        playbackPositionSecondsAtomic.store(0.0, std::memory_order_relaxed);
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
    playbackPositionSecondsAtomic.store(
            static_cast<double>(player->timeMs()) / 1000.0,
            std::memory_order_relaxed
    );
}

double LibSidPlayFpDecoder::getDuration() {
    return currentSubtuneDurationSecondsAtomic.load(std::memory_order_relaxed);
}

int LibSidPlayFpDecoder::getSampleRate() {
    return player ? activeSampleRate : requestedSampleRate;
}

void LibSidPlayFpDecoder::setOutputSampleRate(int sampleRateHz) {
    if (sampleRateHz <= 0) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    const SidBackend backendForRate = player ? activeBackend : selectedBackend;
    const int normalizedRate = clampSampleRateForBackend(sampleRateHz, backendForRate);
    if (requestedSampleRate == normalizedRate) return;
    requestedSampleRate = normalizedRate;
    // SID sample-rate changes are restart-required.
    // Keep the requested value and apply it on next configure/open.
    if (!player) {
        activeSampleRate = normalizedRate;
    }
}

void LibSidPlayFpDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    const std::string optionName(name);
    const std::string optionValue(value);
    if (optionName == "sidplayfp.backend") {
        selectedBackend = parseSidBackend(optionValue);
        if (!player) {
            activeBackend = selectedBackend;
        }
        return;
    }
    if (optionName == "sidplayfp.clock_mode") {
        sidClockMode = parseSidClockMode(optionValue, sidClockMode);
        return;
    }
    if (optionName == "sidplayfp.sid_model_mode") {
        sidModelMode = parseSidModelMode(optionValue, sidModelMode);
        if (player && tune) {
            applySidFilterOptionsLocked();
        }
        return;
    }
    if (optionName == "sidplayfp.force_sid_model") {
        const bool forceModel = parseBoolString(optionValue, sidModelMode != SidModelMode::Auto);
        sidModelMode = forceModel ? SidModelMode::Mos8580 : SidModelMode::Auto;
        if (player && tune) {
            applySidFilterOptionsLocked();
        }
        return;
    }
    if (optionName == "sidplayfp.sid_model") {
        const SidConfig::sid_model_t legacyModel = parseSidModel(optionValue, SidConfig::MOS8580);
        sidModelMode = (legacyModel == SidConfig::MOS6581) ? SidModelMode::Mos6581 : SidModelMode::Mos8580;
        if (player && tune) {
            applySidFilterOptionsLocked();
        }
        return;
    }
    if (optionName == "sidplayfp.filter_6581_enabled") {
        filter6581Enabled = parseBoolString(optionValue, filter6581Enabled);
        if (player && tune) {
            applySidFilterOptionsLocked();
        }
        return;
    }
    if (optionName == "sidplayfp.filter_8580_enabled") {
        filter8580Enabled = parseBoolString(optionValue, filter8580Enabled);
        if (player && tune) {
            applySidFilterOptionsLocked();
        }
        return;
    }
    if (optionName == "sidplayfp.digiboost_8580") {
        digiBoost8580 = parseBoolString(optionValue, digiBoost8580);
        return;
    }
    if (optionName == "sidplayfp.filter_curve_6581") {
        const double parsed = parseDoubleString(optionValue, reSidFpFilterCurve6581);
        reSidFpFilterCurve6581 = std::clamp(parsed, 0.0, 1.0);
        if (player && activeBackend == SidBackend::ReSIDfp) {
            applySidBackendOptionsLocked();
        }
        return;
    }
    if (optionName == "sidplayfp.filter_range_6581") {
        const double parsed = parseDoubleString(optionValue, reSidFpFilterRange6581);
        reSidFpFilterRange6581 = std::clamp(parsed, 0.0, 1.0);
        if (player && activeBackend == SidBackend::ReSIDfp) {
            applySidBackendOptionsLocked();
        }
        return;
    }
    if (optionName == "sidplayfp.filter_curve_8580") {
        const double parsed = parseDoubleString(optionValue, reSidFpFilterCurve8580);
        reSidFpFilterCurve8580 = std::clamp(parsed, 0.0, 1.0);
        if (player && activeBackend == SidBackend::ReSIDfp) {
            applySidBackendOptionsLocked();
        }
        return;
    }
    if (optionName == "sidplayfp.residfp_fast_sampling") {
        reSidFpFastSampling = parseBoolString(optionValue, reSidFpFastSampling);
        return;
    }
    if (optionName == "sidplayfp.residfp_combined_waveforms_strength") {
        reSidFpCombinedWaveformsStrength = parseCombinedWaveformsStrength(
                optionValue,
                reSidFpCombinedWaveformsStrength
        );
        if (player && activeBackend == SidBackend::ReSIDfp) {
            applySidBackendOptionsLocked();
        }
        return;
    }
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
    if (optionName == "sidplayfp.backend") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "sidplayfp.clock_mode") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "sidplayfp.sid_model_mode") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "sidplayfp.force_sid_model") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "sidplayfp.sid_model") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "sidplayfp.filter_6581_enabled") {
        return OPTION_APPLY_LIVE;
    }
    if (optionName == "sidplayfp.filter_8580_enabled") {
        return OPTION_APPLY_LIVE;
    }
    if (optionName == "sidplayfp.digiboost_8580") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "sidplayfp.filter_curve_6581") {
        return OPTION_APPLY_LIVE;
    }
    if (optionName == "sidplayfp.filter_range_6581") {
        return OPTION_APPLY_LIVE;
    }
    if (optionName == "sidplayfp.filter_curve_8580") {
        return OPTION_APPLY_LIVE;
    }
    if (optionName == "sidplayfp.residfp_fast_sampling") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "sidplayfp.residfp_combined_waveforms_strength") {
        return OPTION_APPLY_LIVE;
    }
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

std::string LibSidPlayFpDecoder::getSidBackendName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const SidBackend backend = player ? activeBackend : selectedBackend;
    switch (backend) {
        case SidBackend::ReSID:
            return "ReSID";
        case SidBackend::SIDLite:
            return "SIDLite";
        case SidBackend::ReSIDfp:
        default:
            return "ReSIDfp";
    }
}

int LibSidPlayFpDecoder::getSidChipCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidChipCount;
}

std::string LibSidPlayFpDecoder::getSidModelSummary() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidModelSummary;
}

std::string LibSidPlayFpDecoder::getSidCurrentModelSummary() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidCurrentModelSummary;
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
    return playbackPositionSecondsAtomic.load(std::memory_order_relaxed);
}
