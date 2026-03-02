#include "ChannelScopeTrigger.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <limits>

// ---- Helpers ----------------------------------------------------------------

static void normalizeInPlace(float* buf, int len) {
    float mx = 0.0f;
    for (int i = 0; i < len; ++i) {
        float a = std::abs(buf[i]);
        if (a > mx) mx = a;
    }
    if (mx < 0.01f) return;
    float inv = 1.0f / mx;
    for (int i = 0; i < len; ++i) buf[i] *= inv;
}

static void gaussianWindow(float* out, int size, float std) {
    if (size <= 0 || std <= 0.0f) {
        std::memset(out, 0, size * sizeof(float));
        return;
    }
    float mid = (size - 1) / 2.0f;
    float invTwoSigmaSq = -0.5f / (std * std);
    for (int i = 0; i < size; ++i) {
        float d = i - mid;
        out[i] = std::exp(d * d * invTwoSigmaSq);
    }
}

// Un-normalized "valid" cross-correlation: out[i] = sum_j data[i+j]*kernel[j]
static void correlateValid(const float* data, int dataLen,
                           const float* kernel, int kernelLen,
                           float* out, int outLen) {
    for (int i = 0; i < outLen; ++i) {
        float sum = 0.0f;
        for (int j = 0; j < kernelLen; ++j) {
            sum += data[i + j] * kernel[j];
        }
        out[i] = sum;
    }
}

// ---- Period estimation ------------------------------------------------------

int ChannelScopeTrigger::estimateSignalPeriod(
    const float* data, int n, float subsmpPerS, float maxFreq
) {
    if (n < 16) return 0;
    int stride = std::max(1, n / 256);
    int downN = n / stride;
    if (downN < 8) return 0;

    double meanAcc = 0.0;
    for (int i = 0; i < downN; ++i) meanAcc += data[i * stride];
    float mean = static_cast<float>(meanAcc / downN);

    int minPeriod = (maxFreq > 0.0f) ? std::max(2, static_cast<int>(subsmpPerS / maxFreq)) : 2;
    int minLag = std::max(2, minPeriod / stride);
    int maxLag = downN / 2;
    if (minLag >= maxLag) return 0;

    // Find first zero crossing of autocorrelation.
    int zeroCrossLag = minLag;
    for (int lag = minLag; lag <= maxLag; ++lag) {
        double sum = 0.0;
        int count = downN - lag;
        for (int j = 0; j < count; ++j) {
            sum += static_cast<double>(data[j * stride] - mean) *
                   static_cast<double>(data[(j + lag) * stride] - mean);
        }
        if (sum < 0.0) { zeroCrossLag = lag; break; }
    }

    // Find peak after zero crossing.
    double bestCorr = 0.0;
    int bestLag = 0;
    for (int lag = zeroCrossLag; lag <= maxLag; ++lag) {
        double sum = 0.0;
        int count = downN - lag;
        for (int j = 0; j < count; ++j) {
            sum += static_cast<double>(data[j * stride] - mean) *
                   static_cast<double>(data[(j + lag) * stride] - mean);
        }
        double corr = sum / count;
        if (corr > bestCorr) { bestCorr = corr; bestLag = lag; }
    }

    int rawPeriod = (bestCorr > 0.0) ? bestLag * stride : 0;

    // Edge compensation for long periods.
    if (rawPeriod > 0 && rawPeriod > n / 10) {
        std::vector<float> compensated(downN);
        for (int i = 0; i < downN; ++i) compensated[i] = data[i * stride] - mean;
        constexpr float edgeComp = 0.9f;
        for (int i = 0; i < downN; ++i) {
            float div = std::max(0.5f, 1.0f - edgeComp * static_cast<float>(i) / downN);
            compensated[i] /= div;
        }
        double bestCorrComp = 0.0;
        int bestLagComp = 0;
        for (int lag = zeroCrossLag; lag <= maxLag; ++lag) {
            double sum = 0.0;
            int count = downN - lag;
            for (int j = 0; j < count; ++j) {
                sum += static_cast<double>(compensated[j]) *
                       static_cast<double>(compensated[j + lag]);
            }
            double corr = sum / count;
            if (corr > bestCorrComp) { bestCorrComp = corr; bestLagComp = lag; }
        }
        return (bestCorrComp > 0.0) ? bestLagComp * stride : rawPeriod;
    }
    return rawPeriod;
}

// ---- Fast zero-crossing trigger (O(n)) ------------------------------------

int ChannelScopeTrigger::findTriggerFast(
    const float* channelData, int n, int triggerModeNative
) {
    int center = n / 2;
    if (n < 8 || triggerModeNative == 0) return center;

    // Handle falling-edge mode by thinking of crossings in the opposite direction.
    bool rising = (triggerModeNative != 2);

    // Silence check.
    float absMax = 0.0f;
    for (int i = 0; i < n; ++i) {
        float a = std::abs(channelData[i]);
        if (a > absMax) absMax = a;
    }
    if (absMax < 0.01f) return center;

    // Search outward from center for the nearest zero-crossing of the desired
    // polarity. This gives the most centered trigger point.
    // A rising zero-crossing: data[i-1] <= 0 && data[i] > 0
    // A falling zero-crossing: data[i-1] >= 0 && data[i] < 0
    int bestIdx = -1;
    int bestDist = n;
    // Search in the centerable zone [n/4, 3n/4] to guarantee the display can
    // be centered on the trigger.
    int lo = n / 4;
    int hi = (3 * n) / 4;
    for (int i = lo + 1; i < hi; ++i) {
        bool isCrossing;
        if (rising) {
            isCrossing = (channelData[i - 1] <= 0.0f && channelData[i] > 0.0f);
        } else {
            isCrossing = (channelData[i - 1] >= 0.0f && channelData[i] < 0.0f);
        }
        if (isCrossing) {
            int dist = std::abs(i - center);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
    }
    return (bestIdx >= 0) ? bestIdx : center;
}

// ---- Per-channel trigger ----------------------------------------------------

int ChannelScopeTrigger::findTriggerForChannel(
    const float* channelData, int n, int triggerModeNative,
    ChannelTriggerState& state
) {
    int center = n / 2;
    if (n < 8 || triggerModeNative == 0) return center;

    // Handle falling-edge mode by negating.
    std::vector<float> negated;
    const float* data = channelData;
    if (triggerModeNative == 2) {
        negated.resize(n);
        for (int i = 0; i < n; ++i) negated[i] = -channelData[i];
        data = negated.data();
    }

    // Silence check.
    float absMax = 0.0f;
    for (int i = 0; i < n; ++i) {
        float a = std::abs(data[i]);
        if (a > absMax) absMax = a;
    }
    if (absMax < 0.01f) return center;

    // --- Sizing ---
    int kernelSize = (n * 2) / 3;
    int triggerDiameter = n - kernelSize;
    int halfKernel = kernelSize / 2;
    int corrNsamp = triggerDiameter + 1;
    if (kernelSize < 8 || corrNsamp < 2) return center;

    // --- Mean removal ---
    constexpr float meanResp = 1.0f;
    float dataMean = 0.0f;
    for (int i = 0; i < n; ++i) dataMean += data[i];
    dataMean /= n;
    state.prevMean += meanResp * (dataMean - state.prevMean);

    std::vector<float> meanRemoved(n);
    for (int i = 0; i < n; ++i) meanRemoved[i] = data[i] - state.prevMean;

    // --- Period estimation ---
    constexpr float subsmpPerS = 44100.0f;
    constexpr float maxFreq = 4000.0f;
    int period = estimateSignalPeriod(meanRemoved.data(), n, subsmpPerS, maxFreq);

    // --- Slope finder (recompute if period changed significantly) ---
    constexpr float recalcSemitones = 1.0f;
    bool needRecalc = state.prevSlopeFinder.empty() ||
        static_cast<int>(state.prevSlopeFinder.size()) != kernelSize ||
        (state.prevPeriod > 0 && period > 0 &&
            std::abs(std::log(static_cast<float>(period) / state.prevPeriod) / std::log(2.0f) * 12.0f) > recalcSemitones) ||
        (state.prevPeriod == 0 && period > 0);

    constexpr float edgeStrength = 2.0f;
    constexpr float bufferStrength = 1.0f;
    constexpr float slopeWidthFraction = 0.25f;

    if (needRecalc) {
        float slopeWidth = (period > 0)
            ? std::clamp(slopeWidthFraction * period, 1.0f, halfKernel / 3.0f)
            : std::clamp(kernelSize / 12.0f, 1.0f, halfKernel / 3.0f);
        float slopeStrength = edgeStrength * 2.0f;

        state.prevSlopeFinder.resize(kernelSize);
        for (int j = 0; j < kernelSize; ++j) {
            state.prevSlopeFinder[j] = (j < halfKernel) ? -slopeStrength / 2.0f : slopeStrength / 2.0f;
        }
        std::vector<float> gw(kernelSize);
        gaussianWindow(gw.data(), kernelSize, slopeWidth);
        for (int j = 0; j < kernelSize; ++j) state.prevSlopeFinder[j] *= gw[j];
        state.prevPeriod = period;
    }

    // --- Build combined kernel ---
    bool corrEnabled = !state.corrBuffer.empty() &&
        static_cast<int>(state.corrBuffer.size()) == kernelSize;

    std::vector<float> combinedKernel(kernelSize);
    if (corrEnabled) {
        for (int j = 0; j < kernelSize; ++j) {
            combinedKernel[j] = state.prevSlopeFinder[j] + state.corrBuffer[j] * bufferStrength;
        }
    } else {
        std::copy(state.prevSlopeFinder.begin(), state.prevSlopeFinder.end(), combinedKernel.begin());
    }

    // --- Cross-correlation ---
    std::vector<float> corr(corrNsamp);
    correlateValid(meanRemoved.data(), n, combinedKernel.data(), kernelSize, corr.data(), corrNsamp);

    // --- Buffer quality peaks ---
    std::vector<float> peaks(corrNsamp, 0.0f);
    if (corrEnabled) {
        std::vector<float> bq(corrNsamp);
        correlateValid(meanRemoved.data(), n, state.corrBuffer.data(), kernelSize, bq.data(), corrNsamp);
        for (int i = 0; i < corrNsamp; ++i) peaks[i] = bq[i] * bufferStrength;
    }

    // --- Cumulative-sum edge score ---
    {
        int cumsumStart = halfKernel - 1;
        if (cumsumStart >= 0 && cumsumStart + corrNsamp <= n) {
            float cumSum = 0.0f;
            for (int i = 0; i < corrNsamp; ++i) {
                cumSum += meanRemoved[cumsumStart + i];
                peaks[i] += -cumSum * edgeStrength;
            }
        }
    }

    // --- Restrict search radius by period ---
    constexpr float triggerRadiusPeriods = 1.5f;
    int triggerRadius = (period > 0)
        ? std::min(static_cast<int>(period * triggerRadiusPeriods), corrNsamp / 2)
        : corrNsamp / 2;

    // --- find_peak with local-maxima filtering ---
    int mid = corrNsamp / 2;
    int left = std::max(0, mid - triggerRadius);
    int right = std::min(corrNsamp, mid + triggerRadius + 1);
    int windowLen = right - left;
    if (windowLen < 2) return center;

    std::vector<float> wCorr(windowLen);
    std::vector<float> wPeaks(windowLen);
    for (int i = 0; i < windowLen; ++i) {
        wCorr[i] = corr[left + i];
        wPeaks[i] = peaks[left + i];
    }

    float minCorr = std::numeric_limits<float>::max();
    for (int i = 0; i < windowLen; ++i) {
        if (wCorr[i] < minCorr) minCorr = wCorr[i];
    }

    // Suppress non-local-maxima of peak score.
    for (int i = 0; i < windowLen - 1; ++i) {
        if (wPeaks[i] < wPeaks[i + 1]) wCorr[i] = minCorr;
    }
    for (int i = 1; i < windowLen; ++i) {
        if (wPeaks[i] < wPeaks[i - 1]) wCorr[i] = minCorr;
    }
    wCorr[0] = minCorr;
    wCorr[windowLen - 1] = minCorr;

    // Pick best local maximum.
    int bestIdx = windowLen / 2;
    float bestVal = minCorr;
    for (int i = 0; i < windowLen; ++i) {
        if (wCorr[i] > bestVal) { bestVal = wCorr[i]; bestIdx = i; }
    }
    int peakOffset = (bestVal <= minCorr) ? mid : (left + bestIdx);
    int triggerIdx = std::clamp(peakOffset + halfKernel, 0, n - 1);

    // --- Update correlation buffer ---
    int alignStart = std::clamp(triggerIdx - halfKernel, 0, n - kernelSize);
    std::vector<float> aligned(kernelSize);
    for (int j = 0; j < kernelSize; ++j) aligned[j] = meanRemoved[alignStart + j];

    float resultMean = 0.0f;
    for (int j = 0; j < kernelSize; ++j) resultMean += aligned[j];
    resultMean /= kernelSize;
    for (int j = 0; j < kernelSize; ++j) aligned[j] -= resultMean;

    normalizeInPlace(aligned.data(), kernelSize);

    float bufStd = (period > 0) ? (period * 0.5f) : (kernelSize / 4.0f);
    std::vector<float> window(kernelSize);
    gaussianWindow(window.data(), kernelSize, bufStd);
    for (int j = 0; j < kernelSize; ++j) aligned[j] *= window[j];

    constexpr float responsiveness = 0.2f;
    if (state.corrBuffer.empty() || static_cast<int>(state.corrBuffer.size()) != kernelSize) {
        state.corrBuffer = std::move(aligned);
    } else {
        normalizeInPlace(state.corrBuffer.data(), kernelSize);
        for (int j = 0; j < kernelSize; ++j) {
            state.corrBuffer[j] = state.corrBuffer[j] * (1.0f - responsiveness) + aligned[j] * responsiveness;
        }
    }

    return triggerIdx;
}

// ---- Public API -------------------------------------------------------------

std::vector<int32_t> ChannelScopeTrigger::computeTriggerIndices(
    const float* flatScopeData, int samplesPerChannel, int numChannels,
    int triggerModeNative, int algorithmMode
) {
    if (numChannels <= 0 || samplesPerChannel < 8) return {};

    // Resize state vector to match channel count (only needed for accurate mode).
    if (algorithmMode == TRIGGER_ALGORITHM_ACCURATE) {
        if (static_cast<int>(states_.size()) < numChannels) {
            states_.resize(numChannels);
        } else if (static_cast<int>(states_.size()) > numChannels) {
            states_.resize(numChannels);
        }
    }

    std::vector<int32_t> indices(numChannels);
    for (int ch = 0; ch < numChannels; ++ch) {
        const float* chData = flatScopeData + static_cast<size_t>(ch) * samplesPerChannel;
        if (algorithmMode == TRIGGER_ALGORITHM_ACCURATE) {
            indices[ch] = findTriggerForChannel(chData, samplesPerChannel, triggerModeNative, states_[ch]);
        } else {
            indices[ch] = findTriggerFast(chData, samplesPerChannel, triggerModeNative);
        }
    }
    return indices;
}

void ChannelScopeTrigger::reset() {
    states_.clear();
}
