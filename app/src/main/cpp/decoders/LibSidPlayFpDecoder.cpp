#include "LibSidPlayFpDecoder.h"

#include <android/log.h>
#include <algorithm>
#include <array>
#include <cctype>
#include <chrono>
#include <cmath>
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
constexpr int kSidToggleChannelsPerChip = 4;
constexpr int kSidMaxToggleChipCount = 3;
constexpr float kSidVoiceScopeGain = 1.20f;
constexpr float kSidDigiScopeGain = 6.05f;
constexpr float kSidScopeDcFollow = 0.0025f;

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

int sidScopeChannelCount(int chipCount) {
    return std::clamp(chipCount, 1, kSidMaxToggleChipCount) * kSidToggleChannelsPerChip;
}

float applySidScopeDcBlock(int16_t sample, float& dcEstimate, float gain) {
    const float normalized = static_cast<float>(sample) / 32768.0f;
    dcEstimate += (normalized - dcEstimate) * kSidScopeDcFollow;
    return std::clamp((normalized - dcEstimate) * gain, -1.0f, 1.0f);
}

void applySidBackendOptionsToBuilder(
        sidbuilder* builder,
        SidBackend backend,
        double filterCurve6581,
        double filterRange6581,
        double filterCurve8580,
        SidConfig::sid_cw_t combinedWaveformsStrength
) {
    if (backend != SidBackend::ReSIDfp || builder == nullptr) {
        return;
    }
    auto* reSidBuilder = static_cast<ReSIDfpBuilder*>(builder);
    if (!reSidBuilder) {
        return;
    }
    reSidBuilder->filter6581Curve(filterCurve6581);
    reSidBuilder->filter6581Range(filterRange6581);
    reSidBuilder->filter8580Curve(filterCurve8580);
    reSidBuilder->combinedWaveformsStrength(combinedWaveformsStrength);
}

void applySidFilterOptionsToPlayer(
        sidplayfp* targetPlayer,
        const SidTune* targetTune,
        SidModelMode sidModelMode,
        bool filter6581Enabled,
        bool filter8580Enabled
) {
    if (!targetPlayer || !targetTune) {
        return;
    }

    const SidTuneInfo* info = targetTune->getInfo();
    const int sidCount = std::max(1, static_cast<int>(targetPlayer->info().numberOfSIDs()));
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
        targetPlayer->filter(static_cast<unsigned int>(sidIndex), enabled);
    }
}
}

struct LibSidPlayFpDecoder::ScopeConfigSnapshot {
    bool valid = false;
    std::string sourcePath;
    int subtuneIndex = 0;
    int sampleRate = 48000;
    int chipCount = 1;
    SidBackend backend = SidBackend::ReSIDfp;
    SidClockMode clockMode = SidClockMode::Auto;
    SidModelMode modelMode = SidModelMode::Auto;
    bool filter6581Enabled = true;
    bool filter8580Enabled = true;
    bool digiBoost8580 = false;
    double filterCurve6581 = 0.5;
    double filterRange6581 = 0.5;
    double filterCurve8580 = 0.5;
    bool reSidFpFastSampling = true;
    SidConfig::sid_cw_t combinedWaveformsStrength = SidConfig::AVERAGE;
    std::vector<bool> toggleChannelMuted;
};

struct LibSidPlayFpDecoder::ScopeShadow {
    std::unique_ptr<sidplayfp> player;
    std::unique_ptr<sidbuilder> sidBuilder;
    std::unique_ptr<SidTune> tune;
    std::unique_ptr<SidConfig> config;
    std::array<short*, kSidMaxToggleChipCount> chipBuffers { nullptr, nullptr, nullptr };
    float dcEstimate = 0.0f;
    int soloChannel = -1;
    int soloChip = 0;
};

LibSidPlayFpDecoder::LibSidPlayFpDecoder()
    : channelScopeState(std::make_shared<ChannelScopeSharedState>()) {}

LibSidPlayFpDecoder::~LibSidPlayFpDecoder() {
    close();
    stopScopeWorker();
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
    applySidBackendOptionsToBuilder(
            sidBuilder.get(),
            activeBackend,
            reSidFpFilterCurve6581,
            reSidFpFilterRange6581,
            reSidFpFilterCurve8580,
            reSidFpCombinedWaveformsStrength
    );
}

void LibSidPlayFpDecoder::applySidFilterOptionsLocked() {
    applySidFilterOptionsToPlayer(
            player.get(),
            tune.get(),
            sidModelMode,
            filter6581Enabled,
            filter8580Enabled
    );
}

LibSidPlayFpDecoder::ScopeConfigSnapshot LibSidPlayFpDecoder::captureScopeConfigSnapshotLocked() const {
    ScopeConfigSnapshot snapshot;
    if (!scopeCaptureEnabled || sourcePath.empty() || !player || !tune) {
        return snapshot;
    }

    snapshot.valid = true;
    snapshot.sourcePath = sourcePath;
    snapshot.subtuneIndex = currentSubtuneIndex;
    snapshot.sampleRate = activeSampleRate;
    snapshot.chipCount = std::clamp(sidChipCount, 1, kSidMaxToggleChipCount);
    snapshot.backend = activeBackend;
    snapshot.clockMode = sidClockMode;
    snapshot.modelMode = sidModelMode;
    snapshot.filter6581Enabled = filter6581Enabled;
    snapshot.filter8580Enabled = filter8580Enabled;
    snapshot.digiBoost8580 = digiBoost8580;
    snapshot.filterCurve6581 = reSidFpFilterCurve6581;
    snapshot.filterRange6581 = reSidFpFilterRange6581;
    snapshot.filterCurve8580 = reSidFpFilterCurve8580;
    snapshot.reSidFpFastSampling = reSidFpFastSampling;
    snapshot.combinedWaveformsStrength = reSidFpCombinedWaveformsStrength;
    snapshot.toggleChannelMuted = toggleChannelMuted;
    return snapshot;
}

void LibSidPlayFpDecoder::ensureScopeWorkerStartedLocked() {
    if (scopeWorkerThread.joinable()) {
        return;
    }
    scopeWorkerStop.store(false);
    scopeWorkerThread = std::thread(&LibSidPlayFpDecoder::scopeWorkerLoop, this);
}

void LibSidPlayFpDecoder::stopScopeWorker() {
    scopeWorkerStop.store(true);
    scopeWorkerCv.notify_all();
    if (scopeWorkerThread.joinable()) {
        scopeWorkerThread.join();
    }
}

void LibSidPlayFpDecoder::markScopeConfigDirtyLocked(bool clearPublishedSnapshot) {
    scopeCaptureDirty = scopeCaptureEnabled;
    scopeConfigGeneration += 1;
    if (player) {
        scopeTargetPositionMs.store(player->timeMs(), std::memory_order_relaxed);
    } else {
        scopeTargetPositionMs.store(0, std::memory_order_relaxed);
    }
    if (clearPublishedSnapshot && channelScopeState) {
        channelScopeState->clear();
    }
    if (scopeCaptureEnabled && player && tune && !sourcePath.empty()) {
        ensureScopeWorkerStartedLocked();
    }
    scopeWorkerCv.notify_all();
}

bool LibSidPlayFpDecoder::applyConfigToScopeShadowLocked(
        const ScopeConfigSnapshot& snapshot,
        ScopeShadow& shadow
) const {
    if (!shadow.player) {
        return false;
    }

    shadow.sidBuilder = createBuilderForBackend(snapshot.backend);
    if (!shadow.sidBuilder) {
        return false;
    }
    shadow.config = std::make_unique<SidConfig>(shadow.player->config());
    applySidBackendOptionsToBuilder(
            shadow.sidBuilder.get(),
            snapshot.backend,
            snapshot.filterCurve6581,
            snapshot.filterRange6581,
            snapshot.filterCurve8580,
            snapshot.combinedWaveformsStrength
    );

    const int normalizedSampleRate = clampSampleRateForBackend(snapshot.sampleRate, snapshot.backend);
    shadow.config->frequency = static_cast<uint_least32_t>(normalizedSampleRate);
    shadow.config->playback = SidConfig::MONO;
    switch (snapshot.clockMode) {
        case SidClockMode::Pal:
            shadow.config->defaultC64Model = SidConfig::PAL;
            shadow.config->forceC64Model = true;
            break;
        case SidClockMode::Ntsc:
            shadow.config->defaultC64Model = SidConfig::NTSC;
            shadow.config->forceC64Model = true;
            break;
        case SidClockMode::Auto:
        default:
            shadow.config->defaultC64Model = SidConfig::PAL;
            shadow.config->forceC64Model = false;
            break;
    }
    switch (snapshot.modelMode) {
        case SidModelMode::Mos6581:
            shadow.config->defaultSidModel = SidConfig::MOS6581;
            shadow.config->forceSidModel = true;
            break;
        case SidModelMode::Mos8580:
            shadow.config->defaultSidModel = SidConfig::MOS8580;
            shadow.config->forceSidModel = true;
            break;
        case SidModelMode::Auto:
        default:
            shadow.config->defaultSidModel = SidConfig::MOS8580;
            shadow.config->forceSidModel = false;
            break;
    }
    const bool force8580Model = (snapshot.modelMode == SidModelMode::Mos8580);
    const bool allowFastSampling = !(snapshot.backend == SidBackend::ReSID && force8580Model);
    shadow.config->samplingMethod =
            (snapshot.reSidFpFastSampling && allowFastSampling)
            ? SidConfig::INTERPOLATE
            : SidConfig::RESAMPLE_INTERPOLATE;
    shadow.config->digiBoost = snapshot.digiBoost8580;
    shadow.config->sidEmulation = shadow.sidBuilder.get();
    return shadow.player->config(*shadow.config);
}

void LibSidPlayFpDecoder::closeScopeCaptureLocked() {
    scopeVoiceShadows.clear();
}

void LibSidPlayFpDecoder::resetChannelScopeLocked() {
    scopeRingRaw.clear();
    scopeFrameScratch.clear();
    scopeRingChannels = 0;
    scopeRingWritePos = 0;
    scopeRingSamples = 0;
    channelScopeSourceSerial = 0;
    if (channelScopeState) {
        channelScopeState->clear();
    }
}

void LibSidPlayFpDecoder::ensureScopeRingShapeLocked(int channelsToKeep) {
    const int clampedChannels = std::clamp(
            channelsToKeep,
            0,
            kSidMaxToggleChipCount * kSidToggleChannelsPerChip
    );
    if (clampedChannels <= 0) {
        scopeRingRaw.clear();
        scopeFrameScratch.clear();
        scopeRingChannels = 0;
        scopeRingWritePos = 0;
        scopeRingSamples = 0;
        return;
    }

    const size_t requiredSize =
            static_cast<size_t>(clampedChannels) * ChannelScopeSharedState::kMaxSamples;
    if (scopeRingChannels == clampedChannels && scopeRingRaw.size() == requiredSize) {
        if (scopeFrameScratch.size() != static_cast<size_t>(clampedChannels)) {
            scopeFrameScratch.assign(static_cast<size_t>(clampedChannels), 0.0f);
        }
        return;
    }

    scopeRingRaw.assign(requiredSize, 0.0f);
    scopeFrameScratch.assign(static_cast<size_t>(clampedChannels), 0.0f);
    scopeRingChannels = clampedChannels;
    scopeRingWritePos = 0;
    scopeRingSamples = 0;
}

void LibSidPlayFpDecoder::appendScopeFrameLocked(const float* perVoiceSamples, int channelsToWrite) {
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

void LibSidPlayFpDecoder::publishScopeSnapshotLocked() {
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

    std::lock_guard<std::mutex> scopeLock(channelScopeState->mutex);
    channelScopeState->snapshotRaw = std::move(raw);
    channelScopeState->snapshotVu = std::move(vu);
    channelScopeState->snapshotChannels = scopeRingChannels;
    channelScopeState->snapshotSerial = ++channelScopeSourceSerial;
}

void LibSidPlayFpDecoder::applyToggleChannelMutesToScopeShadowLocked(
        const ScopeConfigSnapshot& snapshot,
        ScopeShadow& shadow,
        int soloChannel
) const {
    if (!shadow.player) {
        return;
    }

    const int chipCount = std::clamp(snapshot.chipCount, 1, kSidMaxToggleChipCount);
    for (int chip = 0; chip < chipCount; ++chip) {
        for (int voice = 0; voice < kSidToggleChannelsPerChip; ++voice) {
            const int channelIndex = (chip * kSidToggleChannelsPerChip) + voice;
            bool muted =
                    channelIndex < 0 ||
                    channelIndex >= static_cast<int>(snapshot.toggleChannelMuted.size()) ||
                    snapshot.toggleChannelMuted[static_cast<size_t>(channelIndex)];
            if (soloChannel >= 0 && channelIndex != soloChannel) {
                muted = true;
            }
            shadow.player->mute(
                    static_cast<unsigned int>(chip),
                    static_cast<unsigned int>(voice),
                    muted
            );
        }
    }
}

bool LibSidPlayFpDecoder::recreateScopeCaptureLocked(const ScopeConfigSnapshot& snapshot) {
    closeScopeCaptureLocked();
    resetChannelScopeLocked();

    if (!snapshot.valid) {
        scopeCaptureDirty = false;
        return false;
    }

    const int chipCount = std::clamp(snapshot.chipCount, 1, kSidMaxToggleChipCount);
    const int totalChannels = sidScopeChannelCount(chipCount);
    ensureScopeRingShapeLocked(totalChannels);
    scopeVoiceShadows.reserve(static_cast<size_t>(totalChannels));

    for (int shadowIndex = 0; shadowIndex < totalChannels; ++shadowIndex) {
        auto shadow = std::make_unique<ScopeShadow>();
        shadow->soloChannel = shadowIndex;
        shadow->soloChip = shadowIndex / kSidToggleChannelsPerChip;
        shadow->player = std::make_unique<sidplayfp>();
        if (!applyConfigToScopeShadowLocked(snapshot, *shadow)) {
            LOGE("sidplayfp scope config failed: %s", shadow->player ? shadow->player->error() : "n/a");
            closeScopeCaptureLocked();
            resetChannelScopeLocked();
            scopeCaptureDirty = false;
            return false;
        }

        shadow->tune = std::make_unique<SidTune>(snapshot.sourcePath.c_str());
        if (!shadow->tune->getStatus()) {
            LOGE("SidTune scope open failed: %s", shadow->tune->statusString());
            closeScopeCaptureLocked();
            resetChannelScopeLocked();
            scopeCaptureDirty = false;
            return false;
        }
        shadow->tune->selectSong(static_cast<unsigned int>(snapshot.subtuneIndex + 1));
        if (!shadow->player->load(shadow->tune.get())) {
            LOGE("sidplayfp scope load failed: %s", shadow->player->error());
            closeScopeCaptureLocked();
            resetChannelScopeLocked();
            scopeCaptureDirty = false;
            return false;
        }
        if (!shadow->player->reset()) {
            LOGE("sidplayfp scope reset failed: %s", shadow->player->error());
            closeScopeCaptureLocked();
            resetChannelScopeLocked();
            scopeCaptureDirty = false;
            return false;
        }
        shadow->player->initMixer(false);
        applySidFilterOptionsToPlayer(
                shadow->player.get(),
                shadow->tune.get(),
                snapshot.modelMode,
                snapshot.filter6581Enabled,
                snapshot.filter8580Enabled
        );
        applyToggleChannelMutesToScopeShadowLocked(snapshot, *shadow, shadow->soloChannel);
        shadow->dcEstimate = 0.0f;
        scopeVoiceShadows.push_back(std::move(shadow));
    }

    scopeCaptureDirty = false;
    return !scopeVoiceShadows.empty();
}

bool LibSidPlayFpDecoder::captureChannelScopeBlockLocked(unsigned int renderCycles) {
    if (scopeVoiceShadows.empty()) {
        return false;
    }

    const int totalChannels = std::min(
            scopeRingChannels,
            static_cast<int>(scopeVoiceShadows.size())
    );
    ensureScopeRingShapeLocked(totalChannels);
    if (scopeFrameScratch.size() < static_cast<size_t>(totalChannels)) {
        scopeFrameScratch.assign(static_cast<size_t>(totalChannels), 0.0f);
    }

    std::vector<int> producedFrames(static_cast<size_t>(totalChannels), 0);
    int maxProducedFrames = 0;
    for (int channel = 0; channel < totalChannels; ++channel) {
        auto& shadow = scopeVoiceShadows[static_cast<size_t>(channel)];
        if (!shadow || !shadow->player) {
            continue;
        }
        const int produced = shadow->player->play(renderCycles);
        if (produced <= 0) {
            continue;
        }
        shadow->chipBuffers.fill(nullptr);
        shadow->player->buffers(shadow->chipBuffers.data());
        const short* chipBuffer =
                (shadow->soloChip >= 0 && shadow->soloChip < kSidMaxToggleChipCount)
                ? shadow->chipBuffers[static_cast<size_t>(shadow->soloChip)]
                : nullptr;
        if (!chipBuffer) {
            continue;
        }
        producedFrames[static_cast<size_t>(channel)] = produced;
        maxProducedFrames = std::max(maxProducedFrames, produced);
    }

    if (maxProducedFrames <= 0) {
        return false;
    }

    for (int frame = 0; frame < maxProducedFrames; ++frame) {
        std::fill(scopeFrameScratch.begin(), scopeFrameScratch.end(), 0.0f);
        for (int channel = 0; channel < totalChannels; ++channel) {
            auto& shadow = scopeVoiceShadows[static_cast<size_t>(channel)];
            if (!shadow || frame >= producedFrames[static_cast<size_t>(channel)]) {
                continue;
            }
            const short* chipBuffer =
                    (shadow->soloChip >= 0 && shadow->soloChip < kSidMaxToggleChipCount)
                    ? shadow->chipBuffers[static_cast<size_t>(shadow->soloChip)]
                    : nullptr;
            if (!chipBuffer) {
                continue;
            }
            const float gain =
                    ((channel % kSidToggleChannelsPerChip) == (kSidToggleChannelsPerChip - 1))
                    ? kSidDigiScopeGain
                    : kSidVoiceScopeGain;
            scopeFrameScratch[static_cast<size_t>(channel)] = applySidScopeDcBlock(
                    chipBuffer[frame],
                    shadow->dcEstimate,
                    gain
            );
        }
        appendScopeFrameLocked(scopeFrameScratch.data(), totalChannels);
    }

    publishScopeSnapshotLocked();
    return true;
}

uint32_t LibSidPlayFpDecoder::getScopePlaybackPositionMsLocked() const {
    for (const auto& shadow : scopeVoiceShadows) {
        if (shadow && shadow->player) {
            return shadow->player->timeMs();
        }
    }
    return 0;
}

void LibSidPlayFpDecoder::scopeWorkerLoop() {
    uint64_t appliedGeneration = 0;

    while (!scopeWorkerStop.load(std::memory_order_relaxed)) {
        ScopeConfigSnapshot snapshot;
        uint64_t generation = 0;
        uint32_t targetMs = 0;
        {
            std::lock_guard<std::mutex> decodeLock(decodeMutex);
            snapshot = captureScopeConfigSnapshotLocked();
            generation = scopeConfigGeneration;
            targetMs = scopeTargetPositionMs.load(std::memory_order_relaxed);
        }

        if (!snapshot.valid) {
            if (!scopeVoiceShadows.empty() || scopeRingSamples > 0) {
                closeScopeCaptureLocked();
                resetChannelScopeLocked();
            }
            appliedGeneration = generation;
            std::unique_lock<std::mutex> waitLock(scopeWorkerMutex);
            scopeWorkerCv.wait_for(waitLock, std::chrono::milliseconds(64));
            continue;
        }

        if (generation != appliedGeneration || scopeVoiceShadows.empty()) {
            recreateScopeCaptureLocked(snapshot);
            appliedGeneration = generation;
        }

        if (scopeVoiceShadows.empty()) {
            std::unique_lock<std::mutex> waitLock(scopeWorkerMutex);
            scopeWorkerCv.wait_for(waitLock, std::chrono::milliseconds(16));
            continue;
        }

        uint32_t shadowMs = getScopePlaybackPositionMsLocked();
        if (shadowMs > targetMs + 250) {
            recreateScopeCaptureLocked(snapshot);
            shadowMs = getScopePlaybackPositionMsLocked();
        }

        const uint32_t lagMs = targetMs > shadowMs ? (targetMs - shadowMs) : 0u;
        int maxBlocks = 0;
        int blockFrames = 1024;
        if (lagMs > 20000u) {
            maxBlocks = 32;
            blockFrames = 4096;
        } else if (lagMs > 5000u) {
            maxBlocks = 24;
            blockFrames = 4096;
        } else if (lagMs > 1000u) {
            maxBlocks = 16;
            blockFrames = 2048;
        } else if (lagMs > 200u) {
            maxBlocks = 8;
            blockFrames = 1024;
        } else if (lagMs > 0u) {
            maxBlocks = 4;
            blockFrames = 512;
        }

        bool advanced = false;
        while (!scopeWorkerStop.load(std::memory_order_relaxed) && maxBlocks-- > 0) {
            shadowMs = getScopePlaybackPositionMsLocked();
            if (shadowMs >= targetMs) {
                break;
            }
            const unsigned int renderCycles = computeRenderCyclesForFrames(blockFrames, snapshot.sampleRate);
            if (!captureChannelScopeBlockLocked(renderCycles)) {
                break;
            }
            advanced = true;
        }

        std::unique_lock<std::mutex> waitLock(scopeWorkerMutex);
        scopeWorkerCv.wait_for(
                waitLock,
                std::chrono::milliseconds(advanced ? 1 : 8)
        );
    }

    closeScopeCaptureLocked();
    resetChannelScopeLocked();
}

void LibSidPlayFpDecoder::refreshMetadataLocked() {
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    sidChipCount = 1;
    sidVoiceCount = sidScopeChannelCount(1);
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
    durationReliableAtomic.store(false, std::memory_order_relaxed);

    if (!tune) return;
    const SidTuneInfo* info = tune->getInfo();
    if (info == nullptr) return;

    sidChipCount = std::max(1, info->sidChips());
    if (player) {
        sidChipCount = std::max(sidChipCount, static_cast<int>(player->info().numberOfSIDs()));
        sidSpeedName = safeString(player->info().speedString());
    }
    sidVoiceCount = sidScopeChannelCount(sidChipCount);
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
    rebuildToggleChannelsLocked();
    applyToggleChannelMutesLocked();
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
    sidVoiceCount = sidScopeChannelCount(runtimeSidChips);
    sidSpeedName = safeString(info.speedString());
    applySidFilterOptionsLocked();
    rebuildToggleChannelsLocked();
    applyToggleChannelMutesLocked();
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
    markScopeConfigDirtyLocked(true);
    return true;
}

bool LibSidPlayFpDecoder::openInternalLocked(const char* path) {
    if (!path) return false;
    sourcePath = path;

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
    durationReliableAtomic.store(false, std::memory_order_relaxed);
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
    if (scopeCaptureEnabled) {
        ensureScopeWorkerStartedLocked();
        markScopeConfigDirtyLocked(true);
    }

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
    sourcePath.clear();
    subtuneTitles.clear();
    subtuneArtists.clear();
    subtuneDurationsSeconds.clear();
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    outputChannels = 2;
    sidChipCount = 1;
    sidVoiceCount = sidScopeChannelCount(1);
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
    durationReliableAtomic.store(false, std::memory_order_relaxed);
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
    mixedScratchSamples.clear();
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    markScopeConfigDirtyLocked(true);
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
    sourcePath.clear();
    subtuneTitles.clear();
    subtuneArtists.clear();
    subtuneDurationsSeconds.clear();
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    outputChannels = 2;
    sidChipCount = 1;
    sidVoiceCount = sidScopeChannelCount(1);
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
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    markScopeConfigDirtyLocked(true);
}

int LibSidPlayFpDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !buffer || numFrames <= 0) return 0;
    const int channels = std::clamp(outputChannels, 1, 2);
    const auto shouldSignalTrackEndForEngine = [&]() -> bool {
        // Repeat Track (1) and Repeat Subtune (3) are enforced by AudioEngine.
        // Signal end when the configured duration is reached so engine restart
        // paths trigger even if sidplayfp continues producing audio internally.
        const int mode = repeatMode.load(std::memory_order_relaxed);
        if (mode != 1 && mode != 3) return false;

        const double durationSeconds = currentSubtuneDurationSecondsAtomic.load(std::memory_order_relaxed);
        if (!(durationSeconds > 0.0)) return false;

        constexpr double kDurationEndEpsilonSeconds = 0.001;
        const double playbackSeconds = static_cast<double>(player->timeMs()) / 1000.0;
        return playbackSeconds + kDurationEndEpsilonSeconds >= durationSeconds;
    };

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

        if (shouldSignalTrackEndForEngine()) {
            break;
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

    const uint32_t playbackTimeMs = player->timeMs();
    playbackPositionSecondsAtomic.store(
            static_cast<double>(playbackTimeMs) / 1000.0,
            std::memory_order_relaxed
    );
    if (scopeCaptureEnabled) {
        scopeTargetPositionMs.store(playbackTimeMs, std::memory_order_relaxed);
        scopeWorkerCv.notify_all();
    }
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
    markScopeConfigDirtyLocked(true);
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
    if (optionName == "visualization.channel_scope_active") {
        const bool enabled = parseBoolString(optionValue, scopeCaptureEnabled);
        if (scopeCaptureEnabled == enabled) {
            return;
        }
        scopeCaptureEnabled = enabled;
        if (scopeCaptureEnabled) {
            ensureScopeWorkerStartedLocked();
        }
        markScopeConfigDirtyLocked(true);
        return;
    }
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
            markScopeConfigDirtyLocked(false);
        }
        return;
    }
    if (optionName == "sidplayfp.force_sid_model") {
        const bool forceModel = parseBoolString(optionValue, sidModelMode != SidModelMode::Auto);
        sidModelMode = forceModel ? SidModelMode::Mos8580 : SidModelMode::Auto;
        if (player && tune) {
            applySidFilterOptionsLocked();
            markScopeConfigDirtyLocked(false);
        }
        return;
    }
    if (optionName == "sidplayfp.sid_model") {
        const SidConfig::sid_model_t legacyModel = parseSidModel(optionValue, SidConfig::MOS8580);
        sidModelMode = (legacyModel == SidConfig::MOS6581) ? SidModelMode::Mos6581 : SidModelMode::Mos8580;
        if (player && tune) {
            applySidFilterOptionsLocked();
            markScopeConfigDirtyLocked(false);
        }
        return;
    }
    if (optionName == "sidplayfp.filter_6581_enabled") {
        filter6581Enabled = parseBoolString(optionValue, filter6581Enabled);
        if (player && tune) {
            applySidFilterOptionsLocked();
            markScopeConfigDirtyLocked(false);
        }
        return;
    }
    if (optionName == "sidplayfp.filter_8580_enabled") {
        filter8580Enabled = parseBoolString(optionValue, filter8580Enabled);
        if (player && tune) {
            applySidFilterOptionsLocked();
            markScopeConfigDirtyLocked(false);
        }
        return;
    }
    if (optionName == "sidplayfp.digiboost_8580") {
        digiBoost8580 = parseBoolString(optionValue, digiBoost8580);
        markScopeConfigDirtyLocked(false);
        return;
    }
    if (optionName == "sidplayfp.filter_curve_6581") {
        const double parsed = parseDoubleString(optionValue, reSidFpFilterCurve6581);
        reSidFpFilterCurve6581 = std::clamp(parsed, 0.0, 1.0);
        if (player && activeBackend == SidBackend::ReSIDfp) {
            applySidBackendOptionsLocked();
            markScopeConfigDirtyLocked(false);
        }
        return;
    }
    if (optionName == "sidplayfp.filter_range_6581") {
        const double parsed = parseDoubleString(optionValue, reSidFpFilterRange6581);
        reSidFpFilterRange6581 = std::clamp(parsed, 0.0, 1.0);
        if (player && activeBackend == SidBackend::ReSIDfp) {
            applySidBackendOptionsLocked();
            markScopeConfigDirtyLocked(false);
        }
        return;
    }
    if (optionName == "sidplayfp.filter_curve_8580") {
        const double parsed = parseDoubleString(optionValue, reSidFpFilterCurve8580);
        reSidFpFilterCurve8580 = std::clamp(parsed, 0.0, 1.0);
        if (player && activeBackend == SidBackend::ReSIDfp) {
            applySidBackendOptionsLocked();
            markScopeConfigDirtyLocked(false);
        }
        return;
    }
    if (optionName == "sidplayfp.residfp_fast_sampling") {
        reSidFpFastSampling = parseBoolString(optionValue, reSidFpFastSampling);
        markScopeConfigDirtyLocked(false);
        return;
    }
    if (optionName == "sidplayfp.residfp_combined_waveforms_strength") {
        reSidFpCombinedWaveformsStrength = parseCombinedWaveformsStrength(
                optionValue,
                reSidFpCombinedWaveformsStrength
        );
        if (player && activeBackend == SidBackend::ReSIDfp) {
            applySidBackendOptionsLocked();
            markScopeConfigDirtyLocked(false);
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
    if (optionName == "visualization.channel_scope_active") {
        return OPTION_APPLY_LIVE;
    }
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

std::vector<std::string> LibSidPlayFpDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return toggleChannelNames;
}

void LibSidPlayFpDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) return;
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyToggleChannelMutesLocked();
}

bool LibSidPlayFpDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) return false;
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void LibSidPlayFpDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) return;
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyToggleChannelMutesLocked();
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
    int capabilities = PLAYBACK_CAP_SEEK |
                       PLAYBACK_CAP_LIVE_REPEAT_MODE |
                       PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    if (durationReliableAtomic.load(std::memory_order_relaxed)) {
        capabilities |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return capabilities;
}

int LibSidPlayFpDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

double LibSidPlayFpDecoder::getPlaybackPositionSeconds() {
    return playbackPositionSecondsAtomic.load(std::memory_order_relaxed);
}

void LibSidPlayFpDecoder::rebuildToggleChannelsLocked() {
    const int chipCount = std::clamp(sidChipCount, 1, kSidMaxToggleChipCount);
    const int totalChannels = chipCount * kSidToggleChannelsPerChip;

    const std::vector<bool> previousMuted = toggleChannelMuted;
    toggleChannelNames.clear();
    toggleChannelNames.reserve(static_cast<size_t>(totalChannels));

    const bool includeChipPrefix = chipCount > 1;
    for (int chip = 0; chip < chipCount; ++chip) {
        const std::string chipPrefix = includeChipPrefix
                ? ("SID " + std::to_string(chip + 1) + " ")
                : "";
        toggleChannelNames.push_back(chipPrefix + "Voice 1");
        toggleChannelNames.push_back(chipPrefix + "Voice 2");
        toggleChannelNames.push_back(chipPrefix + "Voice 3");
        toggleChannelNames.push_back(chipPrefix + "Digi");
    }

    toggleChannelMuted.assign(static_cast<size_t>(totalChannels), false);
    const size_t preserved = std::min(previousMuted.size(), toggleChannelMuted.size());
    for (size_t i = 0; i < preserved; ++i) {
        toggleChannelMuted[i] = previousMuted[i];
    }
}

void LibSidPlayFpDecoder::applyToggleChannelMutesLocked() {
    if (!player) return;
    const int chipCount = std::clamp(sidChipCount, 1, kSidMaxToggleChipCount);
    for (int chip = 0; chip < chipCount; ++chip) {
        for (int voice = 0; voice < kSidToggleChannelsPerChip; ++voice) {
            const int channelIndex = (chip * kSidToggleChannelsPerChip) + voice;
            const bool muted =
                    channelIndex >= 0 &&
                    channelIndex < static_cast<int>(toggleChannelMuted.size()) &&
                    toggleChannelMuted[static_cast<size_t>(channelIndex)];
            player->mute(
                    static_cast<unsigned int>(chip),
                    static_cast<unsigned int>(voice),
                    muted
            );
        }
    }
    if (scopeCaptureEnabled) {
        markScopeConfigDirtyLocked(false);
    }
}
