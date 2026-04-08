#include "Sc68Decoder.h"

#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <memory>
#include <mutex>
#include <vector>

extern "C" {
#include <sc68/sc68.h>
#include <unice68.h>
}

#define LOG_TAG "Sc68Decoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr int kDefaultSampleRateHz = 44100;
constexpr int kMinSampleRateHz = 8000;
constexpr int kMaxSampleRateHz = 192000;
constexpr int kSeekDiscardChunkFrames = 1024;
constexpr int kInfoTrackCurrent = SC68_CUR_TRACK;
constexpr int kTrackDiskInfo = 0;
constexpr int kChannelScopeTextStride = 10;
constexpr int kSc68ScopeMaxChannels = static_cast<int>(SC68_SCOPE_MAX_CHANNELS);
constexpr int kChannelScopeTextFlagActive = 1 << 0;
constexpr int kChannelScopeTextFlagAmigaLeft = 1 << 1;
constexpr int kChannelScopeTextFlagAmigaRight = 1 << 2;
constexpr float kSc68ShadowScopeGain = 0.9f;
constexpr float kSc68YmScopeGain = 0.9f;
constexpr float kSc68SteScopeGain = 0.65f;
constexpr float kSc68PaulaScopeGain = 0.8f;

std::mutex gSc68ApiMutex;
int gSc68ApiRefCount = 0;

bool ensureSc68Api() {
    std::lock_guard<std::mutex> lock(gSc68ApiMutex);
    if (gSc68ApiRefCount > 0) {
        ++gSc68ApiRefCount;
        return true;
    }
    sc68_init_t init{};
    init.flags.no_load_config = 1;
    init.flags.no_save_config = 1;
    if (sc68_init(&init) != 0) {
        return false;
    }
    gSc68ApiRefCount = 1;
    return true;
}

void releaseSc68Api() {
    std::lock_guard<std::mutex> lock(gSc68ApiMutex);
    if (gSc68ApiRefCount <= 0) {
        return;
    }
    --gSc68ApiRefCount;
    if (gSc68ApiRefCount == 0) {
        sc68_shutdown();
    }
}

std::string safeString(const char* value) {
    return value ? std::string(value) : std::string();
}

std::string normalizeText(std::string value) {
    // Drop simple CR/LF noise from some tags.
    value.erase(std::remove(value.begin(), value.end(), '\r'), value.end());
    value.erase(std::remove(value.begin(), value.end(), '\n'), value.end());
    return value;
}

std::string deriveSc68PlatformName(bool ym, bool ste, bool amiga, const char* fallbackHw) {
    static const char* kPlatformNames[8] = {
            "?",
            "ST",
            "STE",
            "YM+STE",
            "Amiga",
            "Amiga+ST",
            "Amiga+STE",
            "Amiga++"
    };
    const int idx = (ym ? 1 : 0) | (ste ? 2 : 0) | (amiga ? 4 : 0);
    if (idx > 0) {
        return kPlatformNames[idx];
    }
    return normalizeText(safeString(fallbackHw));
}

bool containsIgnoreCase(const std::string& text, const std::string& needle) {
    if (needle.empty()) return true;
    if (text.size() < needle.size()) return false;
    for (size_t start = 0; start + needle.size() <= text.size(); ++start) {
        bool match = true;
        for (size_t i = 0; i < needle.size(); ++i) {
            const char a = static_cast<char>(std::tolower(static_cast<unsigned char>(text[start + i])));
            const char b = static_cast<char>(std::tolower(static_cast<unsigned char>(needle[i])));
            if (a != b) {
                match = false;
                break;
            }
        }
        if (match) return true;
    }
    return false;
}

bool equalsIgnoreCase(const char* a, const char* b) {
    if (!a || !b) return false;
    while (*a && *b) {
        const char ca = static_cast<char>(std::tolower(static_cast<unsigned char>(*a)));
        const char cb = static_cast<char>(std::tolower(static_cast<unsigned char>(*b)));
        if (ca != cb) return false;
        ++a;
        ++b;
    }
    return *a == '\0' && *b == '\0';
}

int toSc68AsidMode(const int optionAsid) {
    if (optionAsid == 1) {
        return SC68_ASID_ON;
    }
    if (optionAsid >= 2) {
        return SC68_ASID_FORCE;
    }
    return SC68_ASID_OFF;
}

int parseIntString(const char* value, int fallback) {
    if (!value || value[0] == '\0') return fallback;
    char* end = nullptr;
    const long parsed = std::strtol(value, &end, 10);
    if (end == value || (end && *end != '\0')) return fallback;
    return static_cast<int>(parsed);
}

bool parseBoolString(const char* value, bool fallback) {
    if (!value) return fallback;
    std::string normalized;
    for (const char* c = value; *c; ++c) {
        normalized.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(*c))));
    }
    if (normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on") return true;
    if (normalized == "0" || normalized == "false" || normalized == "no" || normalized == "off") return false;
    return fallback;
}

std::string detectSndhTimerTagFromFile(const std::string& path) {
    if (path.empty()) return "";
    std::string ext = std::filesystem::path(path).extension().string();
    std::transform(ext.begin(), ext.end(), ext.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    if (ext != ".sndh") {
        return "";
    }

    std::ifstream in(path, std::ios::binary | std::ios::ate);
    if (!in) return "";
    const std::streamsize fileSize = in.tellg();
    if (fileSize <= 0) return "";
    in.seekg(0, std::ios::beg);

    std::vector<unsigned char> packed(static_cast<size_t>(fileSize));
    in.read(reinterpret_cast<char*>(packed.data()), fileSize);
    const size_t size = static_cast<size_t>(in.gcount());
    if (size < 3) return "";
    packed.resize(size);

    const unsigned char* scanData = packed.data();
    size_t scanSize = packed.size();
    std::vector<unsigned char> unpacked;

    // Match Droidsound-E behavior for ICE-packed SNDH payloads.
    if (scanSize >= 4 && std::memcmp(scanData, "ICE!", 4) == 0) {
        int compressedSize = static_cast<int>(scanSize);
        const int depackedSize = unice68_depacked_size(scanData, &compressedSize);
        if (depackedSize > 0) {
            unpacked.resize(static_cast<size_t>(depackedSize));
            if (unice68_depacker(unpacked.data(), scanData) == 0) {
                scanData = unpacked.data();
                scanSize = unpacked.size();
            }
        }
    }

    if (scanSize < 20 || std::memcmp(scanData + 12, "SNDH", 4) != 0) {
        return "";
    }

    size_t hdnsOffset = scanSize;
    for (size_t i = 0; i + 3 < scanSize; ++i) {
        if (scanData[i] == 'H' &&
            scanData[i + 1] == 'D' &&
            scanData[i + 2] == 'N' &&
            scanData[i + 3] == 'S') {
            hdnsOffset = i;
            break;
        }
    }

    const size_t start = 16;
    const size_t end = (hdnsOffset < scanSize && hdnsOffset > start) ? hdnsOffset : scanSize;
    for (size_t i = start; i + 2 < end; ++i) {
        const unsigned char a = scanData[i];
        const unsigned char b = scanData[i + 1];
        const unsigned char c = scanData[i + 2];
        if (a == 'T' &&
            (b == 'A' || b == 'B' || b == 'C' || b == 'D') &&
            std::isdigit(c)) {
            return std::string(1, static_cast<char>(b));
        }
    }

    return "";
}

float clampScopeSample(float value) {
    return std::clamp(value, -1.0f, 1.0f);
}

float readSignedSampleAt(
        const uint8_t* memory,
        uint64_t cursor,
        uint32_t fixedShift
) {
    if (!memory) {
        return 0.0f;
    }
    const size_t index = static_cast<size_t>(cursor >> fixedShift);
    const int8_t sample = static_cast<int8_t>(memory[index]);
    return static_cast<float>(sample) / 128.0f;
}

float synthesizePaulaScopeSample(sc68_scope_channel_t& channel) {
    if ((channel.flags & SC68_SCOPE_FLAG_ACTIVE) == 0u ||
        !channel.memory ||
        channel.end <= channel.cursor ||
        channel.volume == 0u) {
        return 0.0f;
    }

    const uint64_t one = channel.fixed_shift >= 63u ? 0u : (uint64_t{1} << channel.fixed_shift);
    const uint64_t fracMask = one > 0u ? (one - 1u) : 0u;
    const float current = readSignedSampleAt(channel.memory, channel.cursor, channel.fixed_shift);
    float sample = current;

    if (fracMask != 0u) {
        uint64_t nextCursor = channel.cursor + one;
        if (nextCursor >= channel.end) {
            nextCursor = channel.loop_start;
        }
        const float next = readSignedSampleAt(channel.memory, nextCursor, channel.fixed_shift);
        const float frac = static_cast<float>(channel.cursor & fracMask) / static_cast<float>(one);
        sample = current + (next - current) * frac;
    }

    sample *= (static_cast<float>(std::clamp<uint32_t>(channel.volume, 0u, 64u)) / 64.0f) * kSc68PaulaScopeGain;

    channel.cursor += channel.step;
    if (channel.cursor >= channel.end) {
        const uint64_t loopStart = channel.loop_start;
        const uint64_t loopEnd = channel.loop_end > loopStart ? channel.loop_end : channel.end;
        if ((channel.flags & SC68_SCOPE_FLAG_LOOP) != 0u && loopEnd > loopStart) {
            const uint64_t length = loopEnd - loopStart;
            if (length > 0u) {
                channel.cursor = loopStart + ((channel.cursor - loopStart) % length);
            }
        } else {
            channel.flags &= ~SC68_SCOPE_FLAG_ACTIVE;
        }
    }

    return clampScopeSample(sample);
}

float synthesizeSteScopeSample(sc68_scope_channel_t& channel) {
    if ((channel.flags & SC68_SCOPE_FLAG_ACTIVE) == 0u ||
        !channel.memory ||
        channel.end <= channel.cursor) {
        return 0.0f;
    }

    float sample = 0.0f;
    if ((channel.flags & SC68_SCOPE_FLAG_STEREO) != 0u) {
        size_t index = static_cast<size_t>(channel.cursor >> channel.fixed_shift);
        index &= ~static_cast<size_t>(1u);
        const int8_t left = static_cast<int8_t>(channel.memory[index]);
        const int8_t right = static_cast<int8_t>(channel.memory[index + 1u]);
        sample = (static_cast<float>(left) + static_cast<float>(right)) / 256.0f;
    } else {
        sample = readSignedSampleAt(channel.memory, channel.cursor, channel.fixed_shift);
    }
    sample *= kSc68SteScopeGain;

    channel.cursor += channel.step;
    if (channel.cursor >= channel.end) {
        const uint64_t loopStart = channel.loop_start;
        const uint64_t loopEnd = channel.loop_end > loopStart ? channel.loop_end : channel.end;
        if ((channel.flags & SC68_SCOPE_FLAG_LOOP) != 0u && loopEnd > loopStart) {
            const uint64_t length = loopEnd - loopStart;
            if (length > 0u) {
                channel.cursor = loopStart + ((channel.cursor - loopStart) % length);
            }
        } else {
            channel.flags &= ~SC68_SCOPE_FLAG_ACTIVE;
        }
    }

    return clampScopeSample(sample);
}

float synthesizeYmScopeSample(sc68_scope_channel_t& channel, uint32_t outputHz) {
    if ((channel.flags & SC68_SCOPE_FLAG_ACTIVE) == 0u ||
        outputHz == 0u ||
        channel.volume == 0u ||
        ((channel.flags & (SC68_SCOPE_FLAG_TONE | SC68_SCOPE_FLAG_NOISE)) == 0u)) {
        return 0.0f;
    }

    const bool toneEnabled = (channel.flags & SC68_SCOPE_FLAG_TONE) != 0u;
    const bool noiseEnabled = (channel.flags & SC68_SCOPE_FLAG_NOISE) != 0u;
    const bool squareHigh = channel.state != 0u;
    const bool noiseHigh = channel.aux_state != 0u;
    const bool toneGate = !toneEnabled || squareHigh;
    const bool noiseGate = !noiseEnabled || noiseHigh;
    const float amplitude =
            (static_cast<float>(std::clamp<uint32_t>(channel.volume, 0u, 31u)) / 31.0f) *
            kSc68YmScopeGain;
    const float polarity = toneEnabled
            ? (squareHigh ? 1.0f : -1.0f)
            : (noiseHigh ? 1.0f : -1.0f);
    float sample = (toneGate && noiseGate) ? (amplitude * polarity) : 0.0f;

    if (toneEnabled && channel.period > 0u && channel.rate_hz > 0u) {
        double cursor = static_cast<double>(channel.cursor);
        if (cursor <= 0.0) {
            cursor = static_cast<double>(channel.period);
        }
        cursor -= static_cast<double>(channel.rate_hz) / static_cast<double>(outputHz);
        while (cursor <= 0.0) {
            cursor += static_cast<double>(channel.period);
            channel.state = channel.state == 0u ? 1u : 0u;
        }
        channel.cursor = static_cast<uint64_t>(std::max<long long>(1LL, std::llround(cursor)));
    }

    if (noiseEnabled && channel.aux_period > 0u && channel.aux_rate_hz > 0u) {
        double cursor = static_cast<double>(channel.aux_cursor);
        if (cursor <= 0.0) {
            cursor = static_cast<double>(channel.aux_period);
        }
        cursor -= static_cast<double>(channel.aux_rate_hz) / static_cast<double>(outputHz);
        while (cursor <= 0.0) {
            cursor += static_cast<double>(channel.aux_period);
            channel.aux_state = channel.aux_state == 0u ? 1u : 0u;
        }
        channel.aux_cursor = static_cast<uint64_t>(std::max<long long>(1LL, std::llround(cursor)));
    }

    return clampScopeSample(sample);
}

}

Sc68Decoder::Sc68Decoder() : channelScopeState(std::make_shared<ChannelScopeSharedState>()) {}

Sc68Decoder::~Sc68Decoder() {
    close();
}

bool Sc68Decoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();

    if (!path || path[0] == '\0') {
        return false;
    }
    if (!ensureSc68Api()) {
        LOGE("sc68_init failed");
        return false;
    }

    // Some options (for example YM engine model) are consumed when SC68 creates
    // chip/runtime state, so push defaults first.
    applyCoreDefaultsLocked();

    sc68_create_t create{};
    create.sampling_rate = static_cast<unsigned>(requestedSampleRateHz > 0 ? requestedSampleRateHz : kDefaultSampleRateHz);
    handle = sc68_create(&create);
    if (!handle) {
        LOGE("sc68_create failed");
        releaseSc68Api();
        return false;
    }

    // S16 output is the most stable/publicly supported path in this snapshot.
    sc68_cntl(handle, SC68_SET_PCM, SC68_PCM_S16);
    sc68_cntl(handle, SC68_SET_SPR, requestedSampleRateHz > 0 ? requestedSampleRateHz : kDefaultSampleRateHz);
    applyCoreOptionsLocked();

    if (sc68_load_uri(handle, path) != 0) {
        LOGE("sc68_load_uri failed for: %s", path);
        closeInternalLocked();
        return false;
    }

    // Keep core playback internally looped; app repeat modes are enforced by
    // virtual EOF behavior in read() for non-LP modes.
    if (sc68_play(handle, SC68_DEF_TRACK, SC68_INF_LOOP) != 0) {
        LOGE("sc68_play(default) failed");
        closeInternalLocked();
        return false;
    }

    sourcePath = path;
    playbackPositionSeconds = 0.0;
    lastCorePositionMs = 0;
    isOpen = true;
    resetChannelScopeLocked();
    refreshTrackStateLocked();
    refreshMetadataLocked();
    refreshDurationLocked();
    rebuildToggleChannelsLocked();
    applyToggleChannelMutesLocked();
    refreshScopeAudioShadowsLocked(0);
    return true;
}

void Sc68Decoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

void Sc68Decoder::closeScopeAudioShadowsLocked() {
    for (auto& shadow : scopeAudioShadows) {
        if (shadow.handle) {
            sc68_close(shadow.handle);
            sc68_destroy(shadow.handle);
            shadow.handle = nullptr;
        }
    }
    scopeAudioShadows.clear();
}

void Sc68Decoder::closeInternalLocked() {
    closeScopeAudioShadowsLocked();
    if (handle) {
        sc68_close(handle);
        sc68_destroy(handle);
        handle = nullptr;
        releaseSc68Api();
    }
    isOpen = false;
    sampleRateHz = kDefaultSampleRateHz;
    subtuneCount = 1;
    currentTrack1Based = 1;
    durationReliable.store(false);
    durationSeconds = 0.0;
    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    formatName.clear();
    hardwareName.clear();
    platformName.clear();
    replayName.clear();
    replayRateHz = 0;
    trackCountInfo = 0;
    albumName.clear();
    yearTag.clear();
    ripperTag.clear();
    converterTag.clear();
    timerTag.clear();
    trackCanAsid = false;
    trackHasYm = false;
    trackHasSte = false;
    trackHasAmiga = false;
    trackUsesAgaPath = false;
    playbackPositionSeconds = 0.0;
    lastCorePositionMs = -1;
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    resetChannelScopeLocked();
}

bool Sc68Decoder::createScopeAudioShadowLocked(ScopeAudioShadow& shadow, int positionMs) {
    if (sourcePath.empty()) {
        return false;
    }

    sc68_create_t create{};
    create.sampling_rate = static_cast<unsigned>(
            requestedSampleRateHz > 0 ? requestedSampleRateHz : kDefaultSampleRateHz
    );
    shadow.handle = sc68_create(&create);
    if (!shadow.handle) {
        return false;
    }

    sc68_cntl(shadow.handle, SC68_SET_PCM, SC68_PCM_S16);
    sc68_cntl(
            shadow.handle,
            SC68_SET_SPR,
            requestedSampleRateHz > 0 ? requestedSampleRateHz : kDefaultSampleRateHz
    );
    applyCoreOptionsToHandleLocked(shadow.handle);

    if (sc68_load_uri(shadow.handle, sourcePath.c_str()) != 0 ||
        sc68_play(shadow.handle, currentTrack1Based, SC68_INF_LOOP) != 0) {
        sc68_close(shadow.handle);
        sc68_destroy(shadow.handle);
        shadow.handle = nullptr;
        return false;
    }

    sc68_cntl(shadow.handle, SC68_SET_OPT_INT, "ym-chans", shadow.ymMask);
    sc68_cntl(shadow.handle, SC68_SET_OPT_INT, "ste-enable", shadow.steEnable);
    if (positionMs > 0) {
        sc68_cntl(shadow.handle, SC68_SET_POS, positionMs);
    }
    return true;
}

void Sc68Decoder::refreshScopeAudioShadowsLocked(int positionMs) {
    closeScopeAudioShadowsLocked();

    if (!handle || sourcePath.empty() || trackUsesAgaPath || trackHasAmiga) {
        return;
    }

    std::vector<ScopeAudioShadow> shadows;
    if (trackHasYm) {
        for (int voice = 0; voice < 3; ++voice) {
            ScopeAudioShadow shadow;
            shadow.ymMask = 1 << voice;
            shadow.steEnable = 0;
            shadows.push_back(shadow);
        }
    }
    if (trackHasSte) {
        ScopeAudioShadow shadow;
        shadow.ymMask = 0;
        shadow.steEnable = 1;
        shadows.push_back(shadow);
    }
    if (shadows.empty()) {
        return;
    }

    const int seekPositionMs = positionMs >= 0 ? positionMs : std::max(0, lastCorePositionMs);
    for (auto& shadow : shadows) {
        if (!createScopeAudioShadowLocked(shadow, seekPositionMs)) {
            for (auto& created : shadows) {
                if (created.handle) {
                    sc68_close(created.handle);
                    sc68_destroy(created.handle);
                    created.handle = nullptr;
                }
            }
            return;
        }
    }

    scopeAudioShadows = std::move(shadows);
    applyToggleChannelMutesLocked();
}

void Sc68Decoder::resetChannelScopeLocked() {
    scopeRingRaw.clear();
    scopeChannelVolumes.clear();
    scopeChannelFlags.clear();
    scopeRingChannels = 0;
    scopeRingWritePos = 0;
    scopeRingSamples = 0;
    channelScopeSourceSerial = 0;
    if (channelScopeState) {
        channelScopeState->clear();
    }
}

void Sc68Decoder::ensureScopeRingShapeLocked(int channels) {
    const int clampedChannels = std::clamp(channels, 0, kSc68ScopeMaxChannels);
    if (clampedChannels <= 0) {
        resetChannelScopeLocked();
        return;
    }
    if (scopeRingChannels == clampedChannels &&
        scopeRingRaw.size() == static_cast<size_t>(clampedChannels * ChannelScopeSharedState::kMaxSamples)) {
        return;
    }

    scopeRingChannels = clampedChannels;
    scopeRingRaw.assign(
            static_cast<size_t>(clampedChannels * ChannelScopeSharedState::kMaxSamples),
            0.0f
    );
    scopeChannelVolumes.assign(static_cast<size_t>(clampedChannels), 0);
    scopeChannelFlags.assign(static_cast<size_t>(clampedChannels), 0);
    scopeRingWritePos = 0;
    scopeRingSamples = 0;
    channelScopeSourceSerial = 0;
    if (channelScopeState) {
        channelScopeState->clear();
    }
}

void Sc68Decoder::appendScopeFrameLocked(const float* perChannelSamples, int channels) {
    if (!perChannelSamples || channels <= 0) {
        return;
    }
    ensureScopeRingShapeLocked(channels);
    if (scopeRingChannels <= 0 || scopeRingRaw.empty()) {
        return;
    }

    for (int channel = 0; channel < scopeRingChannels; ++channel) {
        const size_t index =
                static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                static_cast<size_t>(scopeRingWritePos);
        scopeRingRaw[index] = perChannelSamples[channel];
    }
    scopeRingWritePos = (scopeRingWritePos + 1) % ChannelScopeSharedState::kMaxSamples;
    scopeRingSamples = std::min(scopeRingSamples + 1, ChannelScopeSharedState::kMaxSamples);
}

void Sc68Decoder::publishScopeSnapshotLocked() {
    if (!channelScopeState) {
        return;
    }
    if (scopeRingChannels <= 0 || scopeRingRaw.empty() || scopeRingSamples <= 0) {
        channelScopeState->clear();
        return;
    }

    std::vector<float> raw(
            static_cast<size_t>(scopeRingChannels * ChannelScopeSharedState::kMaxSamples),
            0.0f
    );
    const int filledSamples = std::clamp(scopeRingSamples, 0, ChannelScopeSharedState::kMaxSamples);
    const int zeroPrefix = ChannelScopeSharedState::kMaxSamples - filledSamples;
    for (int channel = 0; channel < scopeRingChannels; ++channel) {
        float* destination =
                raw.data() + static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples;
        for (int i = 0; i < filledSamples; ++i) {
            const int sourceIndex =
                    (scopeRingWritePos - filledSamples + i + ChannelScopeSharedState::kMaxSamples) %
                    ChannelScopeSharedState::kMaxSamples;
            destination[zeroPrefix + i] =
                    scopeRingRaw[
                            static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                            static_cast<size_t>(sourceIndex)
                    ];
        }
    }

    const int trailingSamples = std::clamp(sampleRateHz > 0 ? sampleRateHz / 50 : 64, 64, 1024);
    std::vector<float> vu(static_cast<size_t>(scopeRingChannels), 0.0f);
    for (int channel = 0; channel < scopeRingChannels; ++channel) {
        const size_t base = static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples;
        const int start = std::max(0, ChannelScopeSharedState::kMaxSamples - trailingSamples);
        float peak = 0.0f;
        for (int sample = start; sample < ChannelScopeSharedState::kMaxSamples; ++sample) {
            peak = std::max(peak, std::abs(raw[base + static_cast<size_t>(sample)]));
        }
        vu[static_cast<size_t>(channel)] = std::clamp(peak, 0.0f, 1.0f);
    }

    {
        std::lock_guard<std::mutex> scopeLock(channelScopeState->mutex);
        channelScopeState->snapshotRaw = std::move(raw);
        channelScopeState->snapshotVu = std::move(vu);
        channelScopeState->snapshotChannels = scopeRingChannels;
        channelScopeState->snapshotSerial = ++channelScopeSourceSerial;
    }
}

void Sc68Decoder::updateScopeTextStateLocked(const sc68_scope_snapshot_t& snapshot) {
    const int channels = std::min(
            static_cast<int>(snapshot.channel_count),
            std::max(0, scopeRingChannels)
    );
    if (channels <= 0) {
        return;
    }
    if (static_cast<int>(scopeChannelVolumes.size()) < channels) {
        scopeChannelVolumes.resize(static_cast<size_t>(channels), 0);
    }
    if (static_cast<int>(scopeChannelFlags.size()) < channels) {
        scopeChannelFlags.resize(static_cast<size_t>(channels), 0);
    }
    for (int channel = 0; channel < channels; ++channel) {
        const auto& source = snapshot.channels[channel];
        int flags = 0;
        if ((source.flags & SC68_SCOPE_FLAG_LEFT) != 0u) {
            flags |= kChannelScopeTextFlagAmigaLeft;
        }
        if ((source.flags & SC68_SCOPE_FLAG_RIGHT) != 0u) {
            flags |= kChannelScopeTextFlagAmigaRight;
        }
        scopeChannelFlags[static_cast<size_t>(channel)] = flags;
        scopeChannelVolumes[static_cast<size_t>(channel)] = static_cast<int>(source.volume);
    }
}

void Sc68Decoder::captureChannelScopeBlockLocked(const sc68_scope_snapshot_t& snapshot, int frames) {
    const int channels = std::clamp(static_cast<int>(snapshot.channel_count), 0, kSc68ScopeMaxChannels);
    if (channels <= 0 || frames <= 0) {
        return;
    }

    ensureScopeRingShapeLocked(channels);
    sc68_scope_snapshot_t working = snapshot;
    if (working.output_hz == 0u) {
        working.output_hz = static_cast<uint32_t>(sampleRateHz > 0 ? sampleRateHz : kDefaultSampleRateHz);
    }

    std::vector<float> frame(static_cast<size_t>(channels), 0.0f);
    for (int sample = 0; sample < frames; ++sample) {
        for (int channel = 0; channel < channels; ++channel) {
            auto& source = working.channels[channel];
            switch (source.kind) {
                case SC68_SCOPE_CHANNEL_YM:
                    frame[static_cast<size_t>(channel)] =
                            synthesizeYmScopeSample(source, working.output_hz);
                    break;
                case SC68_SCOPE_CHANNEL_STE:
                    frame[static_cast<size_t>(channel)] =
                            synthesizeSteScopeSample(source);
                    break;
                case SC68_SCOPE_CHANNEL_PAULA:
                    frame[static_cast<size_t>(channel)] =
                            synthesizePaulaScopeSample(source);
                    break;
                default:
                    frame[static_cast<size_t>(channel)] = 0.0f;
                    break;
            }
        }
        appendScopeFrameLocked(frame.data(), channels);
    }
}

bool Sc68Decoder::captureChannelScopeFromShadowsLocked(int frames) {
    if (frames <= 0 || scopeAudioShadows.empty()) {
        return false;
    }

    const int channels = static_cast<int>(scopeAudioShadows.size());
    ensureScopeRingShapeLocked(channels);
    if (scopeRingChannels <= 0) {
        return false;
    }

    std::vector<int16_t> pcm(static_cast<size_t>(frames) * 2u);
    std::vector<float> mono(
            static_cast<size_t>(channels * frames),
            0.0f
    );

    bool capturedAny = false;
    for (int channel = 0; channel < channels; ++channel) {
        if (channel < static_cast<int>(toggleChannelMuted.size()) &&
            toggleChannelMuted[static_cast<size_t>(channel)]) {
            continue;
        }

        auto& shadow = scopeAudioShadows[static_cast<size_t>(channel)];
        if (!shadow.handle) {
            continue;
        }

        int producedFrames = frames;
        const int status = sc68_process(shadow.handle, pcm.data(), &producedFrames);
        if (status == SC68_ERROR || producedFrames <= 0) {
            continue;
        }
        capturedAny = true;

        float* destination = mono.data() + static_cast<size_t>(channel * frames);
        for (int frame = 0; frame < producedFrames; ++frame) {
            const int left = pcm[static_cast<size_t>(frame) * 2u];
            const int right = pcm[static_cast<size_t>(frame) * 2u + 1u];
            destination[frame] = clampScopeSample(
                    (static_cast<float>(left + right) / 65536.0f) * kSc68ShadowScopeGain
            );
        }
    }

    if (!capturedAny) {
        return false;
    }

    std::vector<float> frame(static_cast<size_t>(channels), 0.0f);
    for (int sample = 0; sample < frames; ++sample) {
        for (int channel = 0; channel < channels; ++channel) {
            frame[static_cast<size_t>(channel)] =
                    mono[static_cast<size_t>(channel * frames + sample)];
        }
        appendScopeFrameLocked(frame.data(), channels);
    }
    return true;
}

bool Sc68Decoder::refreshTrackStateLocked() {
    if (!handle) return false;

    const int queriedRate = sc68_cntl(handle, SC68_GET_SPR);
    if (queriedRate > 0) {
        sampleRateHz = queriedRate;
    }

    const int queriedTracks = sc68_cntl(handle, SC68_GET_TRACKS);
    if (queriedTracks > 0) {
        subtuneCount = queriedTracks;
    } else {
        subtuneCount = 1;
    }

    const int queriedTrack = sc68_cntl(handle, SC68_GET_TRACK);
    if (queriedTrack > 0) {
        currentTrack1Based = queriedTrack;
    } else if (currentTrack1Based <= 0) {
        currentTrack1Based = 1;
    }
    currentTrack1Based = std::clamp(currentTrack1Based, 1, std::max(1, subtuneCount));
    return true;
}

void Sc68Decoder::refreshMetadataLocked() {
    if (!handle) return;

    sc68_music_info_t info{};
    if (sc68_music_info(handle, &info, kInfoTrackCurrent, nullptr) != 0) {
        title.clear();
        artist.clear();
        composer.clear();
        genre.clear();
        formatName.clear();
        hardwareName.clear();
        platformName.clear();
        replayName.clear();
        replayRateHz = 0;
        trackCountInfo = 0;
        albumName.clear();
        yearTag.clear();
        ripperTag.clear();
        converterTag.clear();
        timerTag.clear();
        trackCanAsid = false;
        return;
    }

    title = normalizeText(safeString(info.title));
    artist = normalizeText(safeString(info.artist));
    composer = artist;
    genre = normalizeText(safeString(info.genre));
    formatName = normalizeText(safeString(info.format));
    albumName = normalizeText(safeString(info.album));
    platformName = deriveSc68PlatformName(
            info.dsk.ym != 0,
            info.dsk.ste != 0,
            info.dsk.amiga != 0,
            info.dsk.hw
    );
    replayName = normalizeText(safeString(info.replay));
    replayRateHz = static_cast<int>(info.rate);
    trackCountInfo = info.tracks;
    yearTag = normalizeText(safeString(info.year));
    ripperTag = normalizeText(safeString(info.ripper));
    converterTag = normalizeText(safeString(info.converter));
    trackCanAsid = info.trk.asid != 0;
    timerTag.clear();
    if (info.trk.tags > 0 && info.trk.tag) {
        for (int i = 0; i < info.trk.tags; ++i) {
            const sc68_tag_t& tag = info.trk.tag[i];
            if (equalsIgnoreCase(tag.key, "TIMER")) {
                timerTag = normalizeText(safeString(tag.val));
                break;
            }
        }
    }
    if (timerTag.empty() && info.dsk.tags > 0 && info.dsk.tag) {
        for (int i = 0; i < info.dsk.tags; ++i) {
            const sc68_tag_t& tag = info.dsk.tag[i];
            if (equalsIgnoreCase(tag.key, "TIMER")) {
                timerTag = normalizeText(safeString(tag.val));
                break;
            }
        }
    }
    if (timerTag.empty() && handle) {
        const char* timerFromTag = sc68_tag(handle, "TIMER", SC68_CUR_TRACK, nullptr);
        timerTag = normalizeText(safeString(timerFromTag));
    }
    if (timerTag.empty()) {
        timerTag = detectSndhTimerTagFromFile(sourcePath);
    }
    trackHasYm = info.trk.ym != 0;
    trackHasSte = info.trk.ste != 0;
    trackHasAmiga = info.trk.amiga != 0;
    trackUsesAgaPath = false;
    const std::string hw = normalizeText(safeString(info.trk.hw));
    hardwareName = hw;
    if (!hw.empty()) {
        // libsc68 runtime mixer path keys off hardware flags. Prefer that over
        // occasionally sparse boolean flags in metadata.
        if (containsIgnoreCase(hw, "paula") || containsIgnoreCase(hw, "amiga")) {
            trackHasAmiga = true;
            trackUsesAgaPath = true;
        }
        if (containsIgnoreCase(hw, "yamaha") || containsIgnoreCase(hw, "ym-2149")) {
            trackHasYm = true;
        }
        if (containsIgnoreCase(hw, "microwire") || containsIgnoreCase(hw, "ste")) {
            trackHasSte = true;
        }
    }
    if (title.empty()) {
        title = albumName;
    }
    if (title.empty() && !sourcePath.empty()) {
        title = std::filesystem::path(sourcePath).stem().string();
    }
}

void Sc68Decoder::refreshDurationLocked() {
    if (!handle) return;

    int lengthMs = 0;

    // When running with infinite internal loops, SC68_GET_LEN/TRKLEN can report
    // 0 for current runtime state. Query the disk metadata with default loop
    // semantics instead, so UI duration/repeat modes stay available.
    sc68_disk_t disk = nullptr;
    if (sc68_cntl(handle, SC68_GET_DISK, &disk) == 0 && disk) {
        sc68_music_info_t info{};
        if (sc68_music_info(nullptr, &info, currentTrack1Based, disk) == 0) {
            lengthMs = static_cast<int>(info.trk.time_ms);
        }
    }

    if (lengthMs <= 0) {
        lengthMs = sc68_cntl(handle, SC68_GET_TRKLEN, currentTrack1Based);
    }
    if (lengthMs <= 0) {
        lengthMs = sc68_cntl(handle, SC68_GET_LEN);
    }

    durationReliable.store(lengthMs > 0);
    durationSeconds = durationReliable.load() ? (static_cast<double>(lengthMs) / 1000.0) : 0.0;
}

void Sc68Decoder::updatePlaybackPositionLocked(int producedFrames) {
    if (!handle) return;
    const int mode = repeatMode.load();
    const bool repeatTrackSingle = (mode == 1 && std::max(1, subtuneCount) == 1);
    const int posMs = sc68_cntl(handle, SC68_GET_POS);
    const bool canAdvanceFromFrames = (producedFrames > 0 && sampleRateHz > 0);

    if (posMs >= 0) {
        const double posSeconds = static_cast<double>(posMs) / 1000.0;
        if (mode == 2) {
            playbackPositionSeconds = posSeconds;
        } else if (repeatTrackSingle &&
                   lastCorePositionMs >= 0 &&
                   posMs <= lastCorePositionMs &&
                   canAdvanceFromFrames) {
            // Some SC68 tracks stall/wrap the reported position while still
            // rendering. Keep Repeat Track timeline linear.
            playbackPositionSeconds += static_cast<double>(producedFrames) / sampleRateHz;
        } else {
            playbackPositionSeconds = posSeconds;
        }
        lastCorePositionMs = posMs;
    } else if (canAdvanceFromFrames) {
        playbackPositionSeconds += static_cast<double>(producedFrames) / sampleRateHz;
        lastCorePositionMs = -1;
    }

    if (playbackPositionSeconds < 0.0) {
        playbackPositionSeconds = 0.0;
    }
}

void Sc68Decoder::rebuildToggleChannelsLocked() {
    std::vector<std::string> names;
    if (trackUsesAgaPath || trackHasAmiga) {
        names.emplace_back("Paula 1");
        names.emplace_back("Paula 2");
        names.emplace_back("Paula 3");
        names.emplace_back("Paula 4");
    } else {
        if (trackHasYm) {
            names.emplace_back("YM A");
            names.emplace_back("YM B");
            names.emplace_back("YM C");
        }
        if (trackHasSte) {
            names.emplace_back("Digi");
        }
    }
    if (names != toggleChannelNames) {
        toggleChannelNames = std::move(names);
        toggleChannelMuted.assign(toggleChannelNames.size(), false);
    }
}

void Sc68Decoder::applyToggleChannelMutesLocked() {
    if (!handle || toggleChannelNames.empty()) return;

    int ymMask = 0;
    int paulaMask = 0;
    int idx = 0;

    if (trackUsesAgaPath || trackHasAmiga) {
        for (int i = 0; i < 4 && idx < static_cast<int>(toggleChannelMuted.size()); ++i, ++idx) {
            if (!toggleChannelMuted[static_cast<size_t>(idx)]) {
                paulaMask |= (1 << i);
            }
        }
        sc68_cntl(handle, SC68_SET_OPT_INT, "amiga-chans", paulaMask);
        return;
    }

    if (trackHasYm) {
        for (int i = 0; i < 3 && idx < static_cast<int>(toggleChannelMuted.size()); ++i, ++idx) {
            if (!toggleChannelMuted[static_cast<size_t>(idx)]) {
                ymMask |= (1 << i);
            }
        }
        sc68_cntl(handle, SC68_SET_OPT_INT, "ym-chans", ymMask);
    }
    if (trackHasSte && idx < static_cast<int>(toggleChannelMuted.size())) {
        const int digiEnabled = toggleChannelMuted[static_cast<size_t>(idx)] ? 0 : 1;
        sc68_cntl(handle, SC68_SET_OPT_INT, "ste-enable", digiEnabled);
        ++idx;
    }
}

void Sc68Decoder::applyCoreOptionsToHandleLocked(sc68_t* target) {
    if (!target) return;
    const int asidMode = toSc68AsidMode(optionAsid);
    sc68_cntl(target, SC68_SET_ASID, asidMode);
    sc68_cntl(target, SC68_SET_OPT_INT, "default-time", optionDefaultTimeSeconds);
    sc68_cntl(target, SC68_SET_OPT_INT, "ym-engine", optionYmEngine);
    sc68_cntl(target, SC68_SET_OPT_INT, "ym-volmodel", optionYmVolModel);
    sc68_cntl(target, SC68_SET_OPT_INT, "amiga-filter", optionAmigaFilter ? 1 : 0);
    sc68_cntl(target, SC68_SET_OPT_INT, "amiga-blend", optionAmigaBlend);
    sc68_cntl(target, SC68_SET_OPT_INT, "amiga-clock", optionAmigaClock);
}

void Sc68Decoder::applyCoreOptionsLocked() {
    applyCoreOptionsToHandleLocked(handle);
    for (auto& shadow : scopeAudioShadows) {
        applyCoreOptionsToHandleLocked(shadow.handle);
    }
}

void Sc68Decoder::applyCoreDefaultsLocked() {
    const int asidMode = toSc68AsidMode(optionAsid);
    sc68_cntl(nullptr, SC68_SET_ASID, asidMode);
    sc68_cntl(nullptr, SC68_SET_OPT_INT, "default-time", optionDefaultTimeSeconds);
    sc68_cntl(nullptr, SC68_SET_OPT_INT, "ym-engine", optionYmEngine);
    sc68_cntl(nullptr, SC68_SET_OPT_INT, "ym-volmodel", optionYmVolModel);
    sc68_cntl(nullptr, SC68_SET_OPT_INT, "amiga-filter", optionAmigaFilter ? 1 : 0);
    sc68_cntl(nullptr, SC68_SET_OPT_INT, "amiga-blend", optionAmigaBlend);
    sc68_cntl(nullptr, SC68_SET_OPT_INT, "amiga-clock", optionAmigaClock);
}

int Sc68Decoder::processIntoLocked(float* buffer, int numFrames) {
    if (!handle || !buffer || numFrames <= 0) {
        return 0;
    }

    sc68_scope_snapshot_t scopeBefore{};
    const bool haveScopeBefore = sc68_get_scope_snapshot(handle, &scopeBefore) > 0;
    std::vector<int16_t> pcm(static_cast<size_t>(numFrames) * 2u);
    int requestedFrames = numFrames;
    const int status = sc68_process(handle, pcm.data(), &requestedFrames);
    if (status == SC68_ERROR || requestedFrames <= 0) {
        return 0;
    }

    const size_t sampleCount = static_cast<size_t>(requestedFrames) * 2u;
    for (size_t i = 0; i < sampleCount; ++i) {
        buffer[i] = static_cast<float>(pcm[i]) / 32768.0f;
    }

    if (!captureChannelScopeFromShadowsLocked(requestedFrames) && haveScopeBefore) {
        captureChannelScopeBlockLocked(scopeBefore, requestedFrames);
    }

    sc68_scope_snapshot_t scopeAfter{};
    if (sc68_get_scope_snapshot(handle, &scopeAfter) > 0) {
        updateScopeTextStateLocked(scopeAfter);
    }
    publishScopeSnapshotLocked();

    updatePlaybackPositionLocked(requestedFrames);
    if ((status & SC68_CHANGE) != 0) {
        refreshTrackStateLocked();
        refreshMetadataLocked();
        refreshDurationLocked();
        rebuildToggleChannelsLocked();
        applyToggleChannelMutesLocked();
        refreshScopeAudioShadowsLocked(lastCorePositionMs);
    }
    return requestedFrames;
}

int Sc68Decoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen) return 0;
    // For repeat-track with a single subtune, let elapsed time continue past
    // nominal duration while SC68 loops internally.
    const int mode = repeatMode.load();
    const bool gateVirtualEof =
            (mode == 0) ||
            (mode == 3) ||
            (mode == 1 && std::max(1, subtuneCount) > 1);
    // Some SNDH files report placeholder/invalid tiny durations (for example
    // when no reliable timer length exists). Avoid immediate false EOF.
    if (gateVirtualEof && durationSeconds >= 1.0 && playbackPositionSeconds >= durationSeconds) {
        return 0;
    }
    int produced = processIntoLocked(buffer, numFrames);
    if (produced > 0) {
        return produced;
    }

    // SC68 can occasionally emit transient 0-frame reads on startup/state
    // transitions. Retry a few times before signaling terminal EOF.
    for (int retry = 0; retry < 4; ++retry) {
        produced = processIntoLocked(buffer, numFrames);
        if (produced > 0) {
            return produced;
        }
    }
    return 0;
}

void Sc68Decoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return;

    const double clamped = std::max(0.0, seconds);
    const int targetMs = static_cast<int>(std::llround(clamped * 1000.0));

    // Preferred path: patched libsc68 direct seek command.
    const int directSeekPosMs = sc68_cntl(handle, SC68_SET_POS, targetMs);
    if (directSeekPosMs >= 0) {
        playbackPositionSeconds = static_cast<double>(directSeekPosMs) / 1000.0;
        lastCorePositionMs = directSeekPosMs;
        resetChannelScopeLocked();
        refreshScopeAudioShadowsLocked(directSeekPosMs);
        return;
    }

    // Fallback for unpatched snapshots: restart + decode-discard.
    if (!setTrackLocked(currentTrack1Based)) return;
    if (clamped <= 0.0) return;

    int64_t framesToSkip = static_cast<int64_t>(std::llround(clamped * static_cast<double>(sampleRateHz)));
    std::vector<float> discard(static_cast<size_t>(kSeekDiscardChunkFrames) * 2u);
    while (framesToSkip > 0) {
        const int chunk = static_cast<int>(std::min<int64_t>(kSeekDiscardChunkFrames, framesToSkip));
        const int produced = processIntoLocked(discard.data(), chunk);
        if (produced <= 0) {
            break;
        }
        framesToSkip -= produced;
    }
    updatePlaybackPositionLocked(0);
}

double Sc68Decoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationSeconds;
}

int Sc68Decoder::getSampleRate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sampleRateHz;
}

int Sc68Decoder::getBitDepth() {
    return bitDepth;
}

std::string Sc68Decoder::getBitDepthLabel() {
    return "16-bit PCM";
}

int Sc68Decoder::getDisplayChannelCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    rebuildToggleChannelsLocked();
    const int logicalChannels = static_cast<int>(toggleChannelNames.size());
    return logicalChannels > 0 ? logicalChannels : channels;
}

int Sc68Decoder::getChannelCount() {
    return channels;
}

int Sc68Decoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return std::max(1, subtuneCount);
}

int Sc68Decoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return std::max(0, currentTrack1Based - 1);
}

bool Sc68Decoder::setTrackLocked(int track1Based) {
    if (!handle) return false;
    if (track1Based < 1 || track1Based > std::max(1, subtuneCount)) return false;
    if (sc68_play(handle, track1Based, SC68_INF_LOOP) != 0) {
        return false;
    }
    currentTrack1Based = track1Based;
    playbackPositionSeconds = 0.0;
    lastCorePositionMs = 0;
    resetChannelScopeLocked();
    // sc68 applies track changes on process calls, so run one minimal pass.
    float scratch[2] = { 0.0f, 0.0f };
    processIntoLocked(scratch, 1);
    refreshTrackStateLocked();
    refreshMetadataLocked();
    refreshDurationLocked();
    rebuildToggleChannelsLocked();
    applyToggleChannelMutesLocked();
    refreshScopeAudioShadowsLocked(lastCorePositionMs);
    resetChannelScopeLocked();
    return true;
}

bool Sc68Decoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return false;
    return setTrackLocked(index + 1);
}

std::string Sc68Decoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return "";
    sc68_music_info_t info{};
    if (sc68_music_info(handle, &info, index + 1, nullptr) != 0) {
        return "";
    }
    std::string value = normalizeText(safeString(info.title));
    if (value.empty()) {
        value = normalizeText(safeString(info.album));
    }
    return value;
}

std::string Sc68Decoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return "";
    sc68_music_info_t info{};
    if (sc68_music_info(handle, &info, index + 1, nullptr) != 0) {
        return "";
    }
    return normalizeText(safeString(info.artist));
}

double Sc68Decoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return 0.0;
    int lengthMs = sc68_cntl(handle, SC68_GET_TRKLEN, index + 1);
    if (lengthMs <= 0) {
        sc68_music_info_t info{};
        if (sc68_music_info(handle, &info, index + 1, nullptr) == 0) {
            lengthMs = static_cast<int>(info.trk.time_ms);
        }
    }
    return lengthMs > 0 ? (static_cast<double>(lengthMs) / 1000.0) : 0.0;
}

std::string Sc68Decoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string Sc68Decoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string Sc68Decoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string Sc68Decoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string Sc68Decoder::getAlbum() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return albumName;
}

std::string Sc68Decoder::getYear() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return yearTag;
}

std::string Sc68Decoder::getFormatName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return formatName;
}

std::string Sc68Decoder::getHardwareName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return hardwareName;
}

std::string Sc68Decoder::getPlatformName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return platformName;
}

std::string Sc68Decoder::getReplayName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return replayName;
}

int Sc68Decoder::getReplayRateHz() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return replayRateHz;
}

int Sc68Decoder::getTrackCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return std::max(1, trackCountInfo);
}

std::string Sc68Decoder::getAlbumName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return albumName;
}

std::string Sc68Decoder::getYearTag() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return yearTag;
}

std::string Sc68Decoder::getRipperTag() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return ripperTag;
}

std::string Sc68Decoder::getConverterTag() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return converterTag;
}

std::string Sc68Decoder::getTimerTag() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return timerTag;
}

bool Sc68Decoder::getCanAsid() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return trackCanAsid;
}

bool Sc68Decoder::getUsesYm() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return trackHasYm;
}

bool Sc68Decoder::getUsesSte() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return trackHasSte;
}

bool Sc68Decoder::getUsesAmiga() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return trackHasAmiga;
}

std::vector<std::string> Sc68Decoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    rebuildToggleChannelsLocked();
    return toggleChannelNames;
}

std::vector<uint8_t> Sc68Decoder::getToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    rebuildToggleChannelsLocked();
    return std::vector<uint8_t>(toggleChannelNames.size(), 1u);
}

void Sc68Decoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    rebuildToggleChannelsLocked();
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyToggleChannelMutesLocked();
    resetChannelScopeLocked();
}

bool Sc68Decoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void Sc68Decoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (toggleChannelMuted.empty()) return;
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyToggleChannelMutesLocked();
    resetChannelScopeLocked();
}

void Sc68Decoder::setOutputSampleRate(int sampleRate) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (sampleRate <= 0) {
        requestedSampleRateHz = kDefaultSampleRateHz;
        return;
    }
    requestedSampleRateHz = std::clamp(sampleRate, kMinSampleRateHz, kMaxSampleRateHz);
}

void Sc68Decoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode.store((mode >= 0 && mode <= 3) ? mode : 0);
}

int Sc68Decoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int Sc68Decoder::getPlaybackCapabilities() const {
    int caps = PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    if (durationReliable.load()) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

int Sc68Decoder::getFixedSampleRateHz() const {
    return 0;
}

double Sc68Decoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return 0.0;
    return playbackPositionSeconds;
}

std::vector<int32_t> Sc68Decoder::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (scopeRingChannels <= 0 || scopeRingRaw.empty() || scopeRingSamples <= 0) {
        return {};
    }

    const int channels = std::min(
            std::clamp(maxChannels, 1, kSc68ScopeMaxChannels),
            scopeRingChannels
    );
    std::vector<int32_t> flat(static_cast<size_t>(channels * kChannelScopeTextStride), -1);
    const int trailingSamples = std::clamp(sampleRateHz > 0 ? sampleRateHz / 50 : 64, 64, 1024);
    const int recentSamples = std::min(scopeRingSamples, trailingSamples);

    for (int channel = 0; channel < channels; ++channel) {
        float recentPeak = 0.0f;
        for (int i = 0; i < recentSamples; ++i) {
            const int ringIndex =
                    (scopeRingWritePos - recentSamples + i + ChannelScopeSharedState::kMaxSamples) %
                    ChannelScopeSharedState::kMaxSamples;
            recentPeak = std::max(
                    recentPeak,
                    std::abs(
                            scopeRingRaw[
                                    static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                                    static_cast<size_t>(ringIndex)
                            ]
                    )
            );
        }

        const size_t base = static_cast<size_t>(channel * kChannelScopeTextStride);
        const int exportedVolume =
                channel < static_cast<int>(scopeChannelVolumes.size())
                ? scopeChannelVolumes[static_cast<size_t>(channel)]
                : 0;
        const int derivedVolume = static_cast<int>(std::lround(std::clamp(recentPeak, 0.0f, 1.0f) * 64.0f));
        int flags =
                channel < static_cast<int>(scopeChannelFlags.size())
                ? scopeChannelFlags[static_cast<size_t>(channel)]
                : 0;
        if (recentPeak > 0.0015f || exportedVolume > 0) {
            flags |= kChannelScopeTextFlagActive;
        }

        flat[base + 0] = channel;
        flat[base + 1] = -1;
        flat[base + 2] = std::max(exportedVolume, derivedVolume);
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

std::vector<std::string> Sc68Decoder::getSupportedExtensions() {
    return { "sc68", "sndh" };
}

void Sc68Decoder::setOption(const char* name, const char* value) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!name) return;

    const std::string optionName(name);
    if (optionName == "sc68.asid") {
        optionAsid = std::clamp(parseIntString(value, optionAsid), 0, 2);
    } else if (optionName == "sc68.default_time_seconds") {
        optionDefaultTimeSeconds = std::clamp(parseIntString(value, optionDefaultTimeSeconds), 0, 24 * 60 * 60 - 1);
    } else if (optionName == "sc68.ym_engine") {
        optionYmEngine = std::clamp(parseIntString(value, optionYmEngine), 0, 1);
    } else if (optionName == "sc68.ym_volmodel") {
        optionYmVolModel = std::clamp(parseIntString(value, optionYmVolModel), 0, 1);
    } else if (optionName == "sc68.amiga_filter") {
        optionAmigaFilter = parseBoolString(value, optionAmigaFilter);
    } else if (optionName == "sc68.amiga_blend") {
        optionAmigaBlend = std::clamp(parseIntString(value, optionAmigaBlend), 0, 0xFF);
    } else if (optionName == "sc68.amiga_clock") {
        optionAmigaClock = std::clamp(parseIntString(value, optionAmigaClock), 0, 1);
    } else {
        return;
    }

    if (handle) {
        applyCoreOptionsLocked();
        refreshScopeAudioShadowsLocked(lastCorePositionMs);
        resetChannelScopeLocked();
    }
    applyToggleChannelMutesLocked();
}

int Sc68Decoder::getOptionApplyPolicy(const char* name) const {
    if (!name) return OPTION_APPLY_LIVE;
    const std::string optionName(name);
    if (optionName == "sc68.asid" ||
        optionName == "sc68.default_time_seconds" ||
        optionName == "sc68.ym_engine" ||
        optionName == "sc68.ym_volmodel" ||
        optionName == "sc68.amiga_filter" ||
        optionName == "sc68.amiga_blend" ||
        optionName == "sc68.amiga_clock") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    return OPTION_APPLY_LIVE;
}
