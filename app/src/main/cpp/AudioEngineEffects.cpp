#include "AudioEngine.h"
#include "decoders/FFmpegDecoder.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace {
    constexpr int kVisualizationWaveformSize = 256;
    constexpr int kVisualizationFftSize = 2048;
    constexpr int kVisualizationSpectrumBins = 256;

    void fftInPlace(std::array<float, kVisualizationFftSize>& real,
                    std::array<float, kVisualizationFftSize>& imag) {
        static bool bitReverseInitialized = false;
        static std::array<int, kVisualizationFftSize> bitReverse {};
        if (!bitReverseInitialized) {
            constexpr int kBitCount = 11; // log2(2048)
            for (int i = 0; i < kVisualizationFftSize; ++i) {
                int x = i;
                int reversed = 0;
                for (int bit = 0; bit < kBitCount; ++bit) {
                    reversed = (reversed << 1) | (x & 1);
                    x >>= 1;
                }
                bitReverse[i] = reversed;
            }
            bitReverseInitialized = true;
        }

        for (int i = 0; i < kVisualizationFftSize; ++i) {
            const int j = bitReverse[i];
            if (j > i) {
                std::swap(real[i], real[j]);
                std::swap(imag[i], imag[j]);
            }
        }

        for (int len = 2; len <= kVisualizationFftSize; len <<= 1) {
            const int halfLen = len >> 1;
            const float theta = -2.0f * static_cast<float>(M_PI) / static_cast<float>(len);
            const float phaseStepReal = std::cos(theta);
            const float phaseStepImag = std::sin(theta);
            for (int i = 0; i < kVisualizationFftSize; i += len) {
                float twiddleReal = 1.0f;
                float twiddleImag = 0.0f;
                for (int j = 0; j < halfLen; ++j) {
                    const int even = i + j;
                    const int odd = even + halfLen;
                    const float oddReal = real[odd];
                    const float oddImag = imag[odd];
                    const float tReal = (twiddleReal * oddReal) - (twiddleImag * oddImag);
                    const float tImag = (twiddleReal * oddImag) + (twiddleImag * oddReal);
                    const float evenReal = real[even];
                    const float evenImag = imag[even];

                    real[odd] = evenReal - tReal;
                    imag[odd] = evenImag - tImag;
                    real[even] = evenReal + tReal;
                    imag[even] = evenImag + tImag;

                    const float nextTwiddleReal =
                            (twiddleReal * phaseStepReal) - (twiddleImag * phaseStepImag);
                    const float nextTwiddleImag =
                            (twiddleReal * phaseStepImag) + (twiddleImag * phaseStepReal);
                    twiddleReal = nextTwiddleReal;
                    twiddleImag = nextTwiddleImag;
                }
            }
        }
    }
}

// Gain control implementation
void AudioEngine::setMasterGain(float gainDb) {
    masterGainDb.store(gainDb);
}

void AudioEngine::setPluginGain(float gainDb) {
    pluginGainDb.store(gainDb);
}

void AudioEngine::setSongGain(float gainDb) {
    songGainDb.store(gainDb);
}

void AudioEngine::setForceMono(bool enabled) {
    forceMono.store(enabled);
}

void AudioEngine::setOutputLimiterEnabled(bool enabled) {
    outputLimiterEnabled.store(enabled);
}

void AudioEngine::setMasterChannelMute(int channelIndex, bool enabled) {
    if (channelIndex == 0) {
        masterMuteLeft.store(enabled);
    } else if (channelIndex == 1) {
        masterMuteRight.store(enabled);
    }
}

void AudioEngine::setMasterChannelSolo(int channelIndex, bool enabled) {
    if (channelIndex == 0) {
        masterSoloLeft.store(enabled);
    } else if (channelIndex == 1) {
        masterSoloRight.store(enabled);
    }
}

void AudioEngine::setEndFadeApplyToAllTracks(bool enabled) {
    endFadeApplyToAllTracks.store(enabled);
}

void AudioEngine::setEndFadeDurationMs(int durationMs) {
    const int normalized = std::clamp(durationMs, 100, 120000);
    endFadeDurationMs.store(normalized);
}

void AudioEngine::setEndFadeCurve(int curve) {
    const int normalized = (curve >= 0 && curve <= 2) ? curve : 0;
    endFadeCurve.store(normalized);
}

float AudioEngine::getMasterGain() const {
    return masterGainDb.load();
}

float AudioEngine::getPluginGain() const {
    return pluginGainDb.load();
}

float AudioEngine::getSongGain() const {
    return songGainDb.load();
}

bool AudioEngine::getForceMono() const {
    return forceMono.load();
}

bool AudioEngine::getMasterChannelMute(int channelIndex) const {
    if (channelIndex == 0) return masterMuteLeft.load();
    if (channelIndex == 1) return masterMuteRight.load();
    return false;
}

bool AudioEngine::getMasterChannelSolo(int channelIndex) const {
    if (channelIndex == 0) return masterSoloLeft.load();
    if (channelIndex == 1) return masterSoloRight.load();
    return false;
}

// Convert dB to linear gain
float AudioEngine::dbToGain(float db) {
    return std::pow(10.0f, db / 20.0f);
}

void AudioEngine::beginPauseResumeFadeLocked(bool fadeIn, int streamRate, int durationMs, float attenuationDb) {
    const int safeRate = std::max(1, streamRate);
    const int safeDurationMs = std::clamp(durationMs, 1, 5000);
    const float safeAttenuationDb = std::clamp(attenuationDb, 0.0f, 60.0f);
    const int totalFrames = std::max(
            1,
            static_cast<int>((static_cast<int64_t>(safeRate) * safeDurationMs) / 1000)
    );
    const float floorGain = std::clamp(dbToGain(-safeAttenuationDb), 0.0f, 1.0f);

    pauseResumeFadeTotalFrames = totalFrames;
    pauseResumeFadeProcessedFrames = 0;
    pauseResumeFadeFromGain = fadeIn ? floorGain : 1.0f;
    pauseResumeFadeToGain = fadeIn ? 1.0f : floorGain;
    pauseResumeFadeOutStopPending = false;
}

float AudioEngine::nextPauseResumeFadeGainLocked() {
    if (pauseResumeFadeTotalFrames <= 0) {
        return 1.0f;
    }

    if (pauseResumeFadeProcessedFrames < pauseResumeFadeTotalFrames) {
        pauseResumeFadeProcessedFrames++;
    }
    const float t = std::clamp(
            static_cast<float>(pauseResumeFadeProcessedFrames) /
            static_cast<float>(pauseResumeFadeTotalFrames),
            0.0f,
            1.0f
    );
    const float curveT =
            0.5f - 0.5f * std::cos(static_cast<float>(M_PI) * t);
    const float gain = pauseResumeFadeFromGain + (pauseResumeFadeToGain - pauseResumeFadeFromGain) * curveT;

    if (pauseResumeFadeProcessedFrames >= pauseResumeFadeTotalFrames) {
        if (pauseResumeFadeToGain < 1.0f) {
            // Fade-out reached floor: hold floor gain for remaining frames in this chunk
            // until render loop flips the stream into terminal stop.
            pauseResumeFadeOutStopPending = true;
            pauseResumeFadeProcessedFrames = pauseResumeFadeTotalFrames;
        } else {
            pauseResumeFadeTotalFrames = 0;
            pauseResumeFadeProcessedFrames = 0;
            pauseResumeFadeFromGain = 1.0f;
            pauseResumeFadeToGain = 1.0f;
        }
    }
    return std::clamp(gain, 0.0f, 1.0f);
}

float AudioEngine::computeEndFadeGainLocked(double playbackPositionSeconds) const {
    if (!decoder) return 1.0f;

    const int mode = repeatMode.load();
    if (mode == 2) {
        return 1.0f; // Repeat at loop point bypasses end fade.
    }
    if (mode != 0 && mode != 1 && mode != 3) {
        return 1.0f;
    }

    const double durationNow = decoder->getDuration();
    if (!(durationNow > 0.0) || !std::isfinite(durationNow)) {
        return 1.0f;
    }

    const int fadeMs = endFadeDurationMs.load();
    if (fadeMs <= 0) {
        return 1.0f;
    }
    const double fadeSeconds = static_cast<double>(fadeMs) / 1000.0;
    if (!(fadeSeconds > 0.0)) {
        return 1.0f;
    }

    const bool applyToAll = endFadeApplyToAllTracks.load();
    const bool reliableDuration =
            (decoder->getPlaybackCapabilities() & AudioDecoder::PLAYBACK_CAP_RELIABLE_DURATION) != 0;
    if (reliableDuration && !applyToAll) {
        return 1.0f;
    }

    const double fadeStart = std::max(0.0, durationNow - fadeSeconds);
    if (playbackPositionSeconds <= fadeStart) {
        return 1.0f;
    }
    if (playbackPositionSeconds >= durationNow) {
        return 0.0f;
    }

    const double progress = std::clamp(
            (playbackPositionSeconds - fadeStart) / std::max(0.001, fadeSeconds),
            0.0,
            1.0
    );
    float gain = static_cast<float>(1.0 - progress);
    const int curve = endFadeCurve.load();
    if (curve == 1) {
        // Ease-in fade: softer attenuation at fade start, stronger near end.
        gain = static_cast<float>(1.0 - (progress * progress));
    } else if (curve == 2) {
        // Ease-out fade: stronger attenuation near fade start.
        gain = gain * gain;
    }
    return std::clamp(gain, 0.0f, 1.0f);
}

// Apply two-stage gain pipeline: Master → (Plugin or Song)
void AudioEngine::applyGain(float* buffer, int numFrames, int channels, float extraGain) {
    const float masterDb = masterGainDb.load();
    const float pluginDb = pluginGainDb.load();
    const float songDb = songGainDb.load();

    // Calculate total gain
    const float masterGain = dbToGain(masterDb);
    // Song volume overrides plugin volume when not at neutral (0dB)
    const float secondaryGain = (songDb != 0.0f) ? dbToGain(songDb) : dbToGain(pluginDb);
    const float baseGain = masterGain * secondaryGain * std::clamp(extraGain, 0.0f, 1.0f);

    if (baseGain == 1.0f) {
        return;
    }
    for (int frame = 0; frame < numFrames; ++frame) {
        const int baseIndex = frame * channels;
        for (int channel = 0; channel < channels; ++channel) {
            buffer[baseIndex + channel] *= baseGain;
        }
    }
}

void AudioEngine::applyMasterChannelRouting(float* buffer, int numFrames, int channels) {
    if (!buffer || numFrames <= 0 || channels < 2) {
        return;
    }

    const bool muteLeft = masterMuteLeft.load();
    const bool muteRight = masterMuteRight.load();
    const bool soloLeft = masterSoloLeft.load();
    const bool soloRight = masterSoloRight.load();
    const bool anySolo = soloLeft || soloRight;

    const bool leftEnabled = anySolo ? soloLeft : !muteLeft;
    const bool rightEnabled = anySolo ? soloRight : !muteRight;

    if (leftEnabled && rightEnabled) {
        return;
    }

    for (int i = 0; i < numFrames; ++i) {
        const int base = i * channels;
        if (!leftEnabled) {
            buffer[base] = 0.0f;
        }
        if (!rightEnabled) {
            buffer[base + 1] = 0.0f;
        }
    }
}

// Downmix stereo to mono
void AudioEngine::applyMonoDownmix(float* buffer, int numFrames, int channels) {
    if (!forceMono.load() || channels != 2) {
        return;
    }

    // Average left and right channels
    for (int i = 0; i < numFrames; i++) {
        const float mono = (buffer[i * 2] + buffer[i * 2 + 1]) * 0.5f;
        buffer[i * 2] = mono;
        buffer[i * 2 + 1] = mono;
    }
}

void AudioEngine::applyOutputLimiter(float* buffer, int numFrames, int channels) {
    if (!buffer || numFrames <= 0 || channels <= 0) {
        return;
    }

    const bool limiterEnabledNow = outputLimiterEnabled.load(std::memory_order_relaxed);
    const int totalSamples = numFrames * channels;
    float limiterGain = 1.0f;
    if (limiterEnabledNow) {
        float peak = 0.0f;
        for (int i = 0; i < totalSamples; ++i) {
            peak = std::max(peak, std::abs(buffer[i]));
        }
        const float targetGain = (peak > 1.0f) ? (1.0f / peak) : 1.0f;
        const float attack = 0.45f;
        const float release = 0.04f;
        const float coeff = (targetGain < outputLimiterGain) ? attack : release;
        outputLimiterGain += (targetGain - outputLimiterGain) * coeff;
        outputLimiterGain = std::clamp(outputLimiterGain, 0.1f, 1.0f);
        limiterGain = outputLimiterGain;
    } else {
        outputLimiterGain = 1.0f;
    }

    constexpr float kSoftClipStart = 0.92f;
    constexpr float kSoftClipDrive = 1.45f;
    const float tanhNorm = std::tanh(kSoftClipDrive);
    for (int i = 0; i < totalSamples; ++i) {
        float sample = buffer[i] * limiterGain;
        const float absSample = std::abs(sample);
        if (absSample > kSoftClipStart) {
            sample = std::tanh(sample * kSoftClipDrive) / tanhNorm;
        }
        buffer[i] = std::clamp(sample, -1.0f, 1.0f);
    }
}

void AudioEngine::updateVisualizationDataLocked(const float* buffer, int numFrames, int channels) {
    if (!buffer || numFrames <= 0 || channels <= 0) {
        return;
    }

    std::array<float, 256> waveL {};
    std::array<float, 256> waveR {};
    std::array<float, 256> bars {};
    std::array<float, 2> vu {};

    for (int n = 0; n < kVisualizationWaveformSize; ++n) {
        const int srcFrame = (n * numFrames) / kVisualizationWaveformSize;
        const int frameIndex = std::min(srcFrame, numFrames - 1);
        const int base = frameIndex * channels;
        const float left = buffer[base];
        const float right = channels > 1 ? buffer[base + 1] : left;
        waveL[n] = std::clamp(left, -1.0f, 1.0f);
        waveR[n] = std::clamp(right, -1.0f, 1.0f);
    }

    double sumSqL = 0.0;
    double sumSqR = 0.0;
    for (int frame = 0; frame < numFrames; ++frame) {
        const int base = frame * channels;
        const float left = buffer[base];
        const float right = channels > 1 ? buffer[base + 1] : left;
        const float mono = 0.5f * (left + right);
        visualizationScopeHistoryLeft[visualizationScopeWriteIndex] = left;
        visualizationScopeHistoryRight[visualizationScopeWriteIndex] = right;
        visualizationScopeWriteIndex =
                (visualizationScopeWriteIndex + 1) % static_cast<int>(visualizationScopeHistoryLeft.size());
        visualizationMonoHistory[visualizationMonoWriteIndex] = mono;
        visualizationMonoWriteIndex = (visualizationMonoWriteIndex + 1) % static_cast<int>(visualizationMonoHistory.size());
        sumSqL += static_cast<double>(left) * left;
        sumSqR += static_cast<double>(right) * right;
    }
    const double invFrames = 1.0 / static_cast<double>(numFrames);
    vu[0] = static_cast<float>(std::clamp(std::sqrt(sumSqL * invFrames), 0.0, 1.0));
    vu[1] = static_cast<float>(std::clamp(std::sqrt(sumSqR * invFrames), 0.0, 1.0));

    std::array<float, kVisualizationFftSize> fftReal {};
    std::array<float, kVisualizationFftSize> fftImag {};
    const int historySize = static_cast<int>(visualizationMonoHistory.size());
    for (int n = 0; n < kVisualizationFftSize; ++n) {
        const int historyIndex =
                (visualizationMonoWriteIndex - kVisualizationFftSize + n + historySize) % historySize;
        fftReal[n] = visualizationMonoHistory[historyIndex];
    }

    // Remove DC and apply Hann window before FFT.
    double mean = 0.0;
    for (float sample : fftReal) {
        mean += sample;
    }
    mean /= static_cast<double>(kVisualizationFftSize);
    const float invSizeMinusOne = 1.0f / static_cast<float>(kVisualizationFftSize - 1);
    for (int n = 0; n < kVisualizationFftSize; ++n) {
        const float centered = fftReal[n] - static_cast<float>(mean);
        const float phase = static_cast<float>(n) * invSizeMinusOne;
        const float hann = 0.5f - (0.5f * std::cos(2.0f * static_cast<float>(M_PI) * phase));
        fftReal[n] = centered * hann;
        fftImag[n] = 0.0f;
    }

    fftInPlace(fftReal, fftImag);

    const int fftHalf = kVisualizationFftSize / 2;
    const float sampleRate = static_cast<float>(std::max(streamSampleRate, 1));
    const int minBin = std::clamp(
            static_cast<int>(std::floor((35.0f / sampleRate) * static_cast<float>(kVisualizationFftSize))),
            1,
            fftHalf - 2
    );
    const int maxBin = fftHalf - 1;
    const int usableBins = std::max(1, maxBin - minBin + 1);
    for (int band = 0; band < kVisualizationSpectrumBins; ++band) {
        const int startBin = minBin + ((band * usableBins) / kVisualizationSpectrumBins);
        const int endBin = minBin + ((((band + 1) * usableBins) / kVisualizationSpectrumBins) - 1);
        const int clampedStart = std::clamp(startBin, minBin, maxBin);
        const int clampedEnd = std::clamp(std::max(endBin, clampedStart), clampedStart, maxBin);

        double powerSum = 0.0;
        int count = 0;
        for (int bin = clampedStart; bin <= clampedEnd; ++bin) {
            const double re = fftReal[bin];
            const double im = fftImag[bin];
            powerSum += (re * re) + (im * im);
            count += 1;
        }
        if (count <= 0) {
            bars[band] = 0.0f;
            continue;
        }

        const double avgPower = powerSum / static_cast<double>(count);
        const double magnitude = std::sqrt(avgPower) / static_cast<double>(kVisualizationFftSize);
        const float freqNorm = static_cast<float>(clampedStart - minBin) / static_cast<float>(usableBins);
        // High-band lift + low-band restraint to avoid first-bar dominance.
        const float tiltCompensation = 0.45f + (0.95f * std::pow(std::clamp(freqNorm, 0.0f, 1.0f), 0.62f));
        const double weighted = magnitude * static_cast<double>(90.0f * tiltCompensation);
        // Soft knee prevents early saturation while preserving detail.
        bars[band] = static_cast<float>(std::clamp(weighted / (1.0 + weighted), 0.0, 1.0));
    }

    std::lock_guard<std::mutex> visLock(visualizationMutex);
    visualizationWaveformLeft = waveL;
    visualizationWaveformRight = waveR;
    visualizationBars = bars;
    visualizationVuLevels = vu;
    visualizationChannelCount.store(std::clamp(channels, 1, 2));
}

void AudioEngine::markVisualizationRequested() const {
    const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    visualizationLastRequestNs.store(nowNs, std::memory_order_relaxed);
}

bool AudioEngine::shouldUpdateVisualization() const {
    const int64_t lastRequestNs = visualizationLastRequestNs.load(std::memory_order_relaxed);
    if (lastRequestNs <= 0) {
        return false;
    }
    const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    constexpr int64_t kVisualizationDemandWindowNs = 750'000'000; // 750 ms
    return (nowNs - lastRequestNs) <= kVisualizationDemandWindowNs;
}

std::vector<float> AudioEngine::getVisualizationWaveformScope(
        int channelIndex,
        int windowMs,
        int triggerMode
) const {
    markVisualizationRequested();
    std::lock_guard<std::mutex> lock(visualizationMutex);
    constexpr int kOutputSize = 1024;
    const auto& history = channelIndex == 1 ? visualizationScopeHistoryRight : visualizationScopeHistoryLeft;
    const int historySize = static_cast<int>(history.size());
    if (historySize <= 0) {
        return std::vector<float>(kOutputSize, 0.0f);
    }

    const int sampleRate = std::max(streamSampleRate, 8000);
    const int clampedWindowMs = std::clamp(windowMs, 5, 200);
    int windowFrames = (sampleRate * clampedWindowMs) / 1000;
    // Allow smaller windows than output size; linear interpolation below will
    // upsample without collapsing to blocky nearest-neighbor segments.
    windowFrames = std::clamp(windowFrames, 128, historySize - 1);

    const int writeIndex = visualizationScopeWriteIndex;
    int startIndex = (writeIndex - windowFrames + historySize) % historySize;

    if (triggerMode == 1 || triggerMode == 2) {
        const bool rising = triggerMode == 1;
        const int preTrigger = windowFrames / 2;
        const int anchorOffset = preTrigger;
        const int prevTrigger = (channelIndex == 1)
                                ? visualizationScopePrevTriggerIndex[1]
                                : visualizationScopePrevTriggerIndex[0];
        auto circularDistance = [historySize](int a, int b) -> int {
            const int raw = std::abs(a - b);
            return std::min(raw, historySize - raw);
        };

        int bestTriggerIndex = -1;
        float bestScore = -1.0e9f;
        for (int offset = 2; offset < windowFrames - 2; ++offset) {
            const int prevIndex = (startIndex + offset - 1) % historySize;
            const int currIndex = (startIndex + offset) % historySize;
            const float prev = history[prevIndex];
            const float curr = history[currIndex];
            const bool crossed = rising ? (prev < 0.0f && curr >= 0.0f) : (prev > 0.0f && curr <= 0.0f);
            if (!crossed) {
                continue;
            }

            const int leftIndex = (currIndex - 2 + historySize) % historySize;
            const int rightIndex = (currIndex + 1) % historySize;
            const float left = history[leftIndex];
            const float right = history[rightIndex];
            const float slope = std::abs(curr - prev);
            const float edgeEnergy = 0.5f * (std::abs(curr) + std::abs(prev));
            const float curvature = std::abs((right - curr) - (curr - left));
            const float anchorPenalty =
                    static_cast<float>(std::abs(offset - anchorOffset)) / static_cast<float>(windowFrames);
            const float continuityPenalty = (prevTrigger >= 0)
                                            ? static_cast<float>(circularDistance(currIndex, prevTrigger)) /
                                              static_cast<float>(historySize)
                                            : 0.0f;

            const float score =
                    (slope * 2.8f) +
                    (edgeEnergy * 0.9f) +
                    (curvature * 0.35f) -
                    (anchorPenalty * 1.6f) -
                    (continuityPenalty * 1.1f);
            if (score > bestScore) {
                bestScore = score;
                bestTriggerIndex = currIndex;
            }
        }

        if (bestTriggerIndex < 0) {
            // Fallback: pick a near-zero sample close to center for stable idle behavior.
            float bestAbs = std::numeric_limits<float>::max();
            int bestOffset = anchorOffset;
            for (int offset = 0; offset < windowFrames; ++offset) {
                const int idx = (startIndex + offset) % historySize;
                const float sample = std::abs(history[idx]);
                const float anchorPenalty =
                        static_cast<float>(std::abs(offset - anchorOffset)) / static_cast<float>(windowFrames);
                const float continuityPenalty = (prevTrigger >= 0)
                                                ? static_cast<float>(circularDistance(idx, prevTrigger)) /
                                                  static_cast<float>(historySize)
                                                : 0.0f;
                const float ranking = sample + (anchorPenalty * 0.10f) + (continuityPenalty * 0.08f);
                if (ranking < bestAbs) {
                    bestAbs = ranking;
                    bestOffset = offset;
                    bestTriggerIndex = idx;
                }
            }
            (void)bestOffset;
        }

        if (bestTriggerIndex >= 0) {
            if (channelIndex == 1) {
                visualizationScopePrevTriggerIndex[1] = bestTriggerIndex;
            } else {
                visualizationScopePrevTriggerIndex[0] = bestTriggerIndex;
            }
            startIndex = (bestTriggerIndex - preTrigger + historySize) % historySize;
        }
    } else {
        if (channelIndex == 1) {
            visualizationScopePrevTriggerIndex[1] = -1;
        } else {
            visualizationScopePrevTriggerIndex[0] = -1;
        }
    }

    std::vector<float> output(kOutputSize, 0.0f);
    const double scale = static_cast<double>(windowFrames - 1) / static_cast<double>(kOutputSize - 1);
    for (int i = 0; i < kOutputSize; ++i) {
        const double frameOffset = static_cast<double>(i) * scale;
        const int frameFloor = static_cast<int>(std::floor(frameOffset));
        const float frac = static_cast<float>(frameOffset - static_cast<double>(frameFloor));
        const int idx0 = (startIndex + frameFloor) % historySize;
        const int idx1 = (idx0 + 1) % historySize;
        const float sample0 = history[idx0];
        const float sample1 = history[idx1];
        const float sample = sample0 + ((sample1 - sample0) * frac);
        output[i] = std::clamp(sample, -1.0f, 1.0f);
    }
    return output;
}

std::vector<float> AudioEngine::getVisualizationBars() const {
    markVisualizationRequested();
    std::lock_guard<std::mutex> lock(visualizationMutex);
    return std::vector<float>(visualizationBars.begin(), visualizationBars.end());
}

std::vector<float> AudioEngine::getVisualizationVuLevels() const {
    markVisualizationRequested();
    std::lock_guard<std::mutex> lock(visualizationMutex);
    return std::vector<float>(visualizationVuLevels.begin(), visualizationVuLevels.end());
}

int AudioEngine::getVisualizationChannelCount() const {
    markVisualizationRequested();
    return visualizationChannelCount.load();
}

// Bitrate information
int64_t AudioEngine::getTrackBitrate() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    // Check if decoder is FFmpegDecoder
    FFmpegDecoder* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    if (ffmpegDecoder) {
        return ffmpegDecoder->getBitrate();
    }
    return 0;
}

bool AudioEngine::isTrackVBR() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return false;
    }
    // Check if decoder is FFmpegDecoder
    FFmpegDecoder* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    if (ffmpegDecoder) {
        return ffmpegDecoder->isVBR();
    }
    return false;
}
