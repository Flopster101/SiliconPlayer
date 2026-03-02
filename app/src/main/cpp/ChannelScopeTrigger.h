#pragma once

#include <vector>
#include <cstdint>

// Algorithm mode constants.
static constexpr int TRIGGER_ALGORITHM_FAST = 0;
static constexpr int TRIGGER_ALGORITHM_ACCURATE = 1;

// Per-channel persistent state for the correlation trigger.
struct ChannelTriggerState {
    std::vector<float> corrBuffer;
    std::vector<float> prevSlopeFinder;
    int prevPeriod = 0;
    float prevMean = 0.0f;
};

// Stateful trigger engine that holds per-channel state across frames.
class ChannelScopeTrigger {
public:
    // Compute trigger indices for all channels.
    // flatScopeData: interleaved [ch0_s0..ch0_sN, ch1_s0..ch1_sN, ...]
    // samplesPerChannel: number of samples per channel in flatScopeData
    // numChannels: derived from flatScopeData.size() / samplesPerChannel
    // triggerModeNative: 0 = off, 1 = rising, 2 = falling
    // algorithmMode: 0 = fast (zero-crossing), 1 = accurate (correlation)
    // Returns one trigger index per channel.
    std::vector<int32_t> computeTriggerIndices(
        const float* flatScopeData,
        int samplesPerChannel,
        int numChannels,
        int triggerModeNative,
        int algorithmMode = TRIGGER_ALGORITHM_FAST
    );

    // Reset all persistent state (e.g. on track change).
    void reset();

private:
    std::vector<ChannelTriggerState> states_;

    // Accurate correlation trigger.
    static int findTriggerForChannel(
        const float* channelData,
        int n,
        int triggerModeNative,
        ChannelTriggerState& state
    );

    // Fast zero-crossing trigger — O(n).
    static int findTriggerFast(
        const float* channelData,
        int n,
        int triggerModeNative
    );

    static int estimateSignalPeriod(
        const float* data,
        int n,
        float subsmpPerS,
        float maxFreq
    );
};
