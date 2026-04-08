#include "ChannelScopeSharedState.h"

#include <algorithm>
#include <cmath>

void ChannelScopeSharedState::clear() {
    std::lock_guard<std::mutex> lock(mutex);
    snapshotRaw.clear();
    snapshotVu.clear();
    snapshotChannels = 0;
    snapshotSerial = 0;
    prevSnapshot.clear();
    interpolatedPrev.clear();
    interpolatedCurr.clear();
    frozenFrameCount.clear();
    lastChannels = 0;
    consumedSerial = 0;
    interpolationInitialized = false;
}

std::vector<float> ChannelScopeSharedState::getProcessedSamples(
        int samplesPerChannel,
        int presentationDelayFrames
) {
    std::vector<float> localRaw;
    std::vector<float> localVu;
    int localChannels = 0;
    uint64_t localSerial = 0;
    {
        std::lock_guard<std::mutex> lock(mutex);
        if (snapshotChannels <= 0 || snapshotRaw.empty()) {
            return {};
        }
        localChannels = snapshotChannels;
        localSerial = snapshotSerial;
        localRaw = snapshotRaw;
        localVu = snapshotVu;
    }

    const int clampedSamples = std::clamp(samplesPerChannel, 16, kMaxSamples);
    const int totalChannels = localChannels;
    const int fullSamplesPerChannel = kMaxSamples;
    const size_t processedFullSize = static_cast<size_t>(totalChannels * fullSamplesPerChannel);
    const size_t flattenedSize = static_cast<size_t>(totalChannels * clampedSamples);

    const bool scopeShapeChanged =
            lastChannels != totalChannels ||
            prevSnapshot.size() != processedFullSize ||
            interpolatedPrev.size() != processedFullSize ||
            interpolatedCurr.size() != processedFullSize ||
            frozenFrameCount.size() != static_cast<size_t>(totalChannels);
    if (scopeShapeChanged) {
        prevSnapshot.assign(processedFullSize, 0.0f);
        interpolatedPrev.assign(processedFullSize, 0.0f);
        interpolatedCurr.assign(processedFullSize, 0.0f);
        frozenFrameCount.assign(static_cast<size_t>(totalChannels), 0);
        lastChannels = totalChannels;
        interpolationInitialized = false;
        consumedSerial = localSerial;
    }
    if (localSerial != consumedSerial || !interpolationInitialized) {
        std::vector<float> raw(processedFullSize, 0.0f);
        std::vector<float> processed(processedFullSize, 0.0f);
        for (int channel = 0; channel < totalChannels; ++channel) {
            const size_t channelOffset = static_cast<size_t>(channel) * fullSamplesPerChannel;
            float* rawDestination = raw.data() + channelOffset;
            const size_t snapshotOffset = static_cast<size_t>(channel) * kMaxSamples;
            const int copyLen = fullSamplesPerChannel;
            std::copy(
                    localRaw.data() + snapshotOffset,
                    localRaw.data() + snapshotOffset + copyLen,
                    rawDestination
            );

            const float* previous = prevSnapshot.data() + channelOffset;
            bool sameAsPrevious = true;
            float peak = 0.0f;
            float prevPeak = 0.0f;
            float deltaSum = 0.0f;
            float rmsAcc = 0.0f;
            for (int i = 0; i < fullSamplesPerChannel; ++i) {
                const float value = rawDestination[i];
                const float prevValue = previous[i];
                if (prevValue != value) sameAsPrevious = false;
                deltaSum += std::abs(value - prevValue);
                rmsAcc += value * value;
                peak = std::max(peak, std::abs(value));
                prevPeak = std::max(prevPeak, std::abs(prevValue));
            }

            auto& frozen = frozenFrameCount[static_cast<size_t>(channel)];
            const float channelVu = (static_cast<size_t>(channel) < localVu.size())
                    ? localVu[static_cast<size_t>(channel)]
                    : 0.0f;
            const float meanDelta = deltaSum / static_cast<float>(fullSamplesPerChannel);
            const float rms = std::sqrt(rmsAcc / static_cast<float>(fullSamplesPerChannel));
            const bool frameNearlyFrozen = meanDelta < 0.0005f;
            const bool looksSilentNow = (channelVu < 0.00035f) && (rms < 0.0045f);
            const bool abruptTailFreeze =
                    looksSilentNow &&
                    (peak > 0.018f || prevPeak > 0.018f) &&
                    (sameAsPrevious || frameNearlyFrozen);
            const bool likelyFreshSignal =
                    (peak > 0.001f) &&
                    (!frameNearlyFrozen || channelVu > 0.03f);
            bool suppressStaleScope = false;
            if (abruptTailFreeze) {
                frozen = 3;
                suppressStaleScope = true;
            } else if (likelyFreshSignal) {
                frozen = 0;
            } else {
                if (frozen < 255) {
                    frozen++;
                }
                if (!suppressStaleScope &&
                    frameNearlyFrozen &&
                    frozen >= 6 &&
                    channelVu < 0.03f &&
                    (peak > 0.012f || prevPeak > 0.012f)) {
                    suppressStaleScope = true;
                }
                if (frozen >= 3) {
                    suppressStaleScope = true;
                }
            }

            float* processedDestination = processed.data() + channelOffset;
            if (suppressStaleScope) {
                std::fill(processedDestination, processedDestination + fullSamplesPerChannel, 0.0f);
            } else {
                std::copy(rawDestination, rawDestination + fullSamplesPerChannel, processedDestination);
            }
        }
        prevSnapshot = std::move(raw);
        if (!interpolationInitialized) {
            interpolatedPrev = processed;
            interpolatedCurr = processed;
            interpolationInitialized = true;
        } else {
            interpolatedPrev = interpolatedCurr;
            interpolatedCurr = processed;
        }
        consumedSerial = localSerial;
    }

    if (!interpolationInitialized || interpolatedCurr.size() != processedFullSize) {
        return std::vector<float>(flattenedSize, 0.0f);
    }

    const int maxPresentationDelay = std::max(0, fullSamplesPerChannel - clampedSamples);
    const int clampedDelay = std::clamp(presentationDelayFrames, 0, maxPresentationDelay);
    const int windowStart = maxPresentationDelay - clampedDelay;

    std::vector<float> flattened(flattenedSize, 0.0f);
    for (int channel = 0; channel < totalChannels; ++channel) {
        const size_t sourceOffset =
                static_cast<size_t>(channel) * static_cast<size_t>(fullSamplesPerChannel) +
                static_cast<size_t>(windowStart);
        const size_t destinationOffset = static_cast<size_t>(channel) * static_cast<size_t>(clampedSamples);
        std::copy(
                interpolatedCurr.data() + sourceOffset,
                interpolatedCurr.data() + sourceOffset + clampedSamples,
                flattened.data() + destinationOffset
        );
    }
    return flattened;
}
