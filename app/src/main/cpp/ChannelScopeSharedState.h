#ifndef SILICONPLAYER_CHANNEL_SCOPE_SHARED_STATE_H
#define SILICONPLAYER_CHANNEL_SCOPE_SHARED_STATE_H

#include <cstdint>
#include <mutex>
#include <vector>

struct ChannelScopeSharedState {
    mutable std::mutex mutex;
    static constexpr int kMaxSamples = 8192;

    std::vector<float> snapshotRaw;
    std::vector<float> snapshotVu;
    int snapshotChannels = 0;
    uint64_t snapshotSerial = 0;

    std::vector<float> prevSnapshot;
    std::vector<float> interpolatedPrev;
    std::vector<float> interpolatedCurr;
    std::vector<std::uint8_t> frozenFrameCount;
    int lastChannels = 0;
    uint64_t consumedSerial = 0;
    bool interpolationInitialized = false;

    std::vector<float> getProcessedSamples(int samplesPerChannel, int presentationDelayFrames = 0);
    void clear();
};

#endif // SILICONPLAYER_CHANNEL_SCOPE_SHARED_STATE_H
