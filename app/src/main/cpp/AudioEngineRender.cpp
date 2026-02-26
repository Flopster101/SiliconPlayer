#include "AudioEngine.h"

#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <cerrno>
#include <pthread.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <unistd.h>

extern "C" {
#include <libswresample/swresample.h>
#include <libavutil/opt.h>
#include <libavutil/channel_layout.h>
#include <libavutil/mathematics.h>
}

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    pid_t currentThreadId() {
#ifdef SYS_gettid
        return static_cast<pid_t>(syscall(SYS_gettid));
#else
        return getpid();
#endif
    }

    void promoteThreadForAudio(const char* role, int targetNice) {
        const pid_t tid = currentThreadId();
        const int before = getpriority(PRIO_PROCESS, tid);
        errno = 0;
        if (setpriority(PRIO_PROCESS, tid, targetNice) == 0) {
            const int after = getpriority(PRIO_PROCESS, tid);
            LOGD(
                    "Thread priority promoted for %s: tid=%d nice(before=%d after=%d target=%d)",
                    role,
                    static_cast<int>(tid),
                    before,
                    after,
                    targetNice
            );
            return;
        }

        const int err = errno;
        LOGD(
                "Thread priority promotion skipped for %s: tid=%d targetNice=%d errno=%d",
                role,
                static_cast<int>(tid),
                targetNice,
                err
        );
    }

    const char* outputResamplerName(int preference) {
        return preference == 2 ? "SoX" : "Built-in";
    }
}

void AudioEngine::resetResamplerStateLocked(bool preserveBuffer) {
    if (!preserveBuffer) {
        resampleInputBuffer.clear();
        resampleInputStartFrame = 0;
        resampleInputPosition = 0.0;
        sharedAbsoluteInputPosition = 0;
        const double currentPosition = positionSeconds.load();
        sharedAbsoluteInputPositionBaseSeconds = currentPosition >= 0.0 ? currentPosition : 0.0;
        outputClockSeconds = currentPosition >= 0.0 ? currentPosition : 0.0;
        timelineSmoothedSeconds = outputClockSeconds;
    } else {
        // When switching resamplers, preserve the tracking but reset the buffer
        // New buffer starts empty, so position relative to buffer start is 0
        resampleInputBuffer.clear();
        resampleInputStartFrame = 0;
        resampleInputPosition = 0.0;
    }
    timelineSmootherInitialized = false;
    pendingBackwardTimelineTargetSeconds = -1.0;
    pendingBackwardTimelineConfirmations = 0;
    resamplerPathLoggedForCurrentTrack = false;
    resamplerNoOpLoggedForCurrentTrack = false;
    if (outputSoxrContext != nullptr) {
        swr_close(outputSoxrContext);
        swr_init(outputSoxrContext);
        // SoX uses internal buffering, so we don't set position explicitly.
        // It will continue processing from the next frame read from decoder.
    }
}

void AudioEngine::freeOutputSoxrContextLocked() {
    if (outputSoxrContext != nullptr) {
        swr_free(&outputSoxrContext);
    }
    outputSoxrInputRate = 0;
    outputSoxrOutputRate = 0;
    outputSoxrChannels = 0;
}

bool AudioEngine::ensureOutputSoxrContextLocked(int channels, int inputRate, int outputRate) {
    if (channels <= 0 || inputRate <= 0 || outputRate <= 0) {
        return false;
    }

    if (outputSoxrContext != nullptr &&
        outputSoxrChannels == channels &&
        outputSoxrInputRate == inputRate &&
        outputSoxrOutputRate == outputRate) {
        return true;
    }

    freeOutputSoxrContextLocked();

    AVChannelLayout inLayout;
    if (channels == 1) {
        inLayout = AV_CHANNEL_LAYOUT_MONO;
    } else {
        inLayout = AV_CHANNEL_LAYOUT_STEREO;
    }
    AVChannelLayout outLayout = inLayout;

    int result = swr_alloc_set_opts2(
            &outputSoxrContext,
            &outLayout,
            AV_SAMPLE_FMT_FLT,
            outputRate,
            &inLayout,
            AV_SAMPLE_FMT_FLT,
            inputRate,
            0,
            nullptr
    );

    if (result < 0 || outputSoxrContext == nullptr) {
        LOGE("SoX context allocation failed (channels=%d inRate=%d outRate=%d result=%d)",
             channels, inputRate, outputRate, result);
        freeOutputSoxrContextLocked();
        return false;
    }

    // Select SoX resampler engine in libswresample.
    if (av_opt_set_int(outputSoxrContext, "resampler", SWR_ENGINE_SOXR, 0) < 0) {
        LOGE("Failed to select SWR_ENGINE_SOXR in swresample options");
        freeOutputSoxrContextLocked();
        return false;
    }

    if (swr_init(outputSoxrContext) < 0) {
        LOGE("SoX swr_init failed (channels=%d inRate=%d outRate=%d)", channels, inputRate, outputRate);
        freeOutputSoxrContextLocked();
        return false;
    }

    LOGD("SoX context ready (channels=%d inRate=%d outRate=%d)", channels, inputRate, outputRate);

    outputSoxrChannels = channels;
    outputSoxrInputRate = inputRate;
    outputSoxrOutputRate = outputRate;
    return true;
}

int AudioEngine::readFromDecoderLocked(float* buffer, int numFrames, int channels, bool& reachedEnd) {
    if (!decoder || !buffer || numFrames <= 0 || channels <= 0) return 0;

    const int mode = repeatMode.load();

    int framesRead = decoder->read(buffer, numFrames);
    if (framesRead > 0) {
        if (mode == 2 && framesRead < numFrames) {
            // Keep filling in loop-point mode to avoid inserting silence when a
            // decoder emits a short chunk around wrap boundaries.
            int total = framesRead;
            constexpr int kMaxTopUpRounds = 8;
            for (int round = 0; round < kMaxTopUpRounds && total < numFrames; ++round) {
                float* writePtr = buffer + static_cast<size_t>(total) * channels;
                const int remaining = numFrames - total;
                int more = decoder->read(writePtr, remaining);
                if (more > 0) {
                    total += more;
                    continue;
                }

                bool recovered = false;
                for (int retry = 0; retry < 8; ++retry) {
                    more = decoder->read(writePtr, remaining);
                    if (more > 0) {
                        total += more;
                        recovered = true;
                        break;
                    }
                }
                if (!recovered) {
                    break;
                }
            }
            return total;
        }
        return framesRead;
    }

    if (mode == 2) {
        // Loop-point mode can return transient 0-frame reads at wrap boundaries.
        for (int retry = 0; retry < 32; ++retry) {
            framesRead = decoder->read(buffer, numFrames);
            if (framesRead > 0) {
                return framesRead;
            }
        }
    }

    if (mode == 3) {
        // Repeat current subtune/track only.
        decoder->seek(0.0);
        positionSeconds.store(0.0);
        resetResamplerStateLocked();
        sharedAbsoluteInputPositionBaseSeconds = 0.0;
        outputClockSeconds = 0.0;
        timelineSmoothedSeconds = 0.0;
        timelineSmootherInitialized = false;
        framesRead = decoder->read(buffer, numFrames);
        if (framesRead > 0) {
            return framesRead;
        }
    }

    if (mode == 1) {
        // Repeat whole track set: advance subtune when available, otherwise restart from start.
        const int subtuneCount = std::max(1, decoder->getSubtuneCount());
        if (subtuneCount > 1) {
            const int currentIndex = std::clamp(decoder->getCurrentSubtuneIndex(), 0, subtuneCount - 1);
            bool switched = false;
            if (currentIndex + 1 < subtuneCount) {
                switched = decoder->selectSubtune(currentIndex + 1);
            } else {
                switched = decoder->selectSubtune(0);
                if (switched) {
                    decoder->seek(0.0);
                }
            }
            if (!switched) {
                decoder->seek(0.0);
            }
            positionSeconds.store(0.0);
            resetResamplerStateLocked();
            sharedAbsoluteInputPositionBaseSeconds = 0.0;
            outputClockSeconds = 0.0;
            timelineSmoothedSeconds = 0.0;
            timelineSmootherInitialized = false;
            framesRead = decoder->read(buffer, numFrames);
            if (framesRead > 0) {
                return framesRead;
            }
        } else {
            decoder->seek(0.0);
            positionSeconds.store(0.0);
            resetResamplerStateLocked();
            sharedAbsoluteInputPositionBaseSeconds = 0.0;
            outputClockSeconds = 0.0;
            timelineSmoothedSeconds = 0.0;
            timelineSmootherInitialized = false;
            framesRead = decoder->read(buffer, numFrames);
            if (framesRead > 0) {
                return framesRead;
            }
        }
    }

    if (mode != 2) {
        reachedEnd = true;
    }
    return 0;
}

void AudioEngine::renderResampledLocked(
        float* outputData,
        int32_t numFrames,
        int channels,
        int streamRate,
        bool& reachedEnd) {
    if (!decoder || !outputData || numFrames <= 0 || channels <= 0) {
        if (outputData && numFrames > 0) {
            memset(outputData, 0, numFrames * std::max(channels, 1) * sizeof(float));
        }
        return;
    }

    const int renderRate = decoderRenderSampleRate > 0 ? decoderRenderSampleRate : streamRate;
    if (streamRate <= 0 || renderRate == streamRate) {
        if (!resamplerNoOpLoggedForCurrentTrack) {
            LOGD(
                    "Resampler bypassed: decoderRate=%d streamRate=%d (decoder=%s preference=%s)",
                    renderRate,
                    streamRate,
                    decoder ? decoder->getName() : "none",
                    outputResamplerName(outputResamplerPreference)
            );
            resamplerNoOpLoggedForCurrentTrack = true;
        }
        int framesRead = readFromDecoderLocked(outputData, numFrames, channels, reachedEnd);
        if (framesRead < numFrames) {
            memset(
                    outputData + (framesRead * channels),
                    0,
                    (numFrames - framesRead) * channels * sizeof(float)
            );
        }
        return;
    }

    const bool decoderHasDiscontinuousTimeline =
            decoder->getTimelineMode() == AudioDecoder::TimelineMode::Discontinuous;
    const bool allowSoxForCurrentDecoder = !decoderHasDiscontinuousTimeline;
    if (outputResamplerPreference == 2 && !outputSoxrUnavailable && allowSoxForCurrentDecoder) {
        if (!resamplerPathLoggedForCurrentTrack) {
            LOGD(
                    "Resampler path selected: SoX (decoderRate=%d -> streamRate=%d, decoder=%s)",
                    renderRate,
                    streamRate,
                    decoder ? decoder->getName() : "none"
            );
            resamplerPathLoggedForCurrentTrack = true;
        }
        renderSoxrResampledLocked(outputData, numFrames, channels, streamRate, renderRate, reachedEnd);
        return;
    }

    if (outputResamplerPreference == 2 && decoderHasDiscontinuousTimeline && !resamplerPathLoggedForCurrentTrack) {
        LOGD(
                "Resampler path selected: Built-in linear (SoX experimental guard, decoderRate=%d -> streamRate=%d, decoder=%s)",
                renderRate,
                streamRate,
                decoder ? decoder->getName() : "none"
        );
        resamplerPathLoggedForCurrentTrack = true;
    }

    if (!resamplerPathLoggedForCurrentTrack) {
        LOGD(
                "Resampler path selected: Built-in linear (decoderRate=%d -> streamRate=%d, decoder=%s, pref=%s, soxUnavailable=%d)",
                renderRate,
                streamRate,
                decoder ? decoder->getName() : "none",
                outputResamplerName(outputResamplerPreference),
                outputSoxrUnavailable ? 1 : 0
        );
        resamplerPathLoggedForCurrentTrack = true;
    }

    const double inputPerOutputFrame = static_cast<double>(renderRate) / static_cast<double>(streamRate);
    constexpr int decodeChunkFrames = 1024;
    const size_t neededScratchSize = static_cast<size_t>(decodeChunkFrames) * channels;
    if (resampleDecodeScratch.size() < neededScratchSize) {
        resampleDecodeScratch.resize(neededScratchSize);
    }

    int outFrame = 0;
    while (outFrame < numFrames) {
        int totalFrames = static_cast<int>(resampleInputBuffer.size() / channels);
        int availableFrames = std::max(0, totalFrames - resampleInputStartFrame);
        int baseFrame = static_cast<int>(std::floor(resampleInputPosition));

        while (baseFrame + 1 >= availableFrames) {
            int decoded = readFromDecoderLocked(resampleDecodeScratch.data(), decodeChunkFrames, channels, reachedEnd);
            if (decoded <= 0) {
                break;
            }
            sharedAbsoluteInputPosition += decoded;  // Track total frames consumed
            const size_t oldSize = resampleInputBuffer.size();
            const size_t appendCount = static_cast<size_t>(decoded) * channels;
            resampleInputBuffer.resize(oldSize + appendCount);
            memcpy(
                    resampleInputBuffer.data() + oldSize,
                    resampleDecodeScratch.data(),
                    appendCount * sizeof(float)
            );
            totalFrames = static_cast<int>(resampleInputBuffer.size() / channels);
            availableFrames = std::max(0, totalFrames - resampleInputStartFrame);
            baseFrame = static_cast<int>(std::floor(resampleInputPosition));
        }

        totalFrames = static_cast<int>(resampleInputBuffer.size() / channels);
        availableFrames = std::max(0, totalFrames - resampleInputStartFrame);
        baseFrame = static_cast<int>(std::floor(resampleInputPosition));
        if (baseFrame >= availableFrames) {
            break;
        }

        int nextFrame = std::min(baseFrame + 1, availableFrames - 1);
        const double frac = std::clamp(resampleInputPosition - baseFrame, 0.0, 1.0);

        const int absoluteBaseFrame = resampleInputStartFrame + baseFrame;
        const int absoluteNextFrame = resampleInputStartFrame + nextFrame;
        for (int c = 0; c < channels; ++c) {
            const float a = resampleInputBuffer[static_cast<size_t>(absoluteBaseFrame) * channels + c];
            const float b = resampleInputBuffer[static_cast<size_t>(absoluteNextFrame) * channels + c];
            outputData[static_cast<size_t>(outFrame) * channels + c] =
                    static_cast<float>(a + (b - a) * frac);
        }

        outFrame++;
        resampleInputPosition += inputPerOutputFrame;
    }

    if (outFrame < numFrames) {
        memset(
                outputData + (static_cast<size_t>(outFrame) * channels),
                0,
                (numFrames - outFrame) * channels * sizeof(float)
        );
    }

    const int totalFrames = static_cast<int>(resampleInputBuffer.size() / channels);
    const int availableFrames = std::max(0, totalFrames - resampleInputStartFrame);
    int trimFrames = std::max(0, static_cast<int>(std::floor(resampleInputPosition)) - 1);
    trimFrames = std::min(trimFrames, availableFrames);
    if (trimFrames > 0) {
        resampleInputStartFrame += trimFrames;
        resampleInputPosition -= trimFrames;
    }

    // Compact infrequently to avoid per-callback front erases.
    if (resampleInputStartFrame > 4096) {
        resampleInputBuffer.erase(
                resampleInputBuffer.begin(),
                resampleInputBuffer.begin() + (static_cast<size_t>(resampleInputStartFrame) * channels)
        );
        resampleInputStartFrame = 0;
    }
}

void AudioEngine::renderSoxrResampledLocked(
        float* outputData,
        int32_t numFrames,
        int channels,
        int streamRate,
        int renderRate,
        bool& reachedEnd) {
    if (!ensureOutputSoxrContextLocked(channels, renderRate, streamRate)) {
        outputSoxrUnavailable = true;
        LOGE("SoX resampler unavailable in current native build, falling back to built-in resampler");
        renderResampledLocked(outputData, numFrames, channels, streamRate, reachedEnd);
        return;
    }

    constexpr int decodeChunkFrames = 1024;
    const size_t neededScratchSize = static_cast<size_t>(decodeChunkFrames) * channels;
    if (resampleDecodeScratch.size() < neededScratchSize) {
        resampleDecodeScratch.resize(neededScratchSize);
    }

    int outFrame = 0;
    bool draining = false;

    while (outFrame < numFrames) {
        const int outputCapacity = numFrames - outFrame;
        uint8_t* outData[1] = {
                reinterpret_cast<uint8_t*>(outputData + static_cast<size_t>(outFrame) * channels)
        };

        const int64_t delay = swr_get_delay(outputSoxrContext, renderRate);
        const int64_t bufferedOutput = av_rescale_rnd(delay, streamRate, renderRate, AV_ROUND_DOWN);

        const uint8_t* inData[1] = { nullptr };
        int inCount = 0;
        bool didRead = false;

        // Only read more input if the buffer is insufficient for the requested output.
        if (bufferedOutput < outputCapacity && !draining) {
            int decoded = readFromDecoderLocked(resampleDecodeScratch.data(), decodeChunkFrames, channels, reachedEnd);
            if (decoded > 0) {
                sharedAbsoluteInputPosition += decoded;
                inData[0] = reinterpret_cast<const uint8_t*>(resampleDecodeScratch.data());
                inCount = decoded;
                didRead = true;
            } else if (reachedEnd) {
                draining = true;
            }
        }

        int converted = swr_convert(outputSoxrContext, outData, outputCapacity, inData, inCount);
        if (converted < 0) {
            LOGE("swr_convert failed: %d", converted);
            break;
        }

        outFrame += converted;

        if (converted == 0 && !didRead && !draining) {
            // Estimated buffer was sufficient, but resampler produced no output.
            // Force a read to advance the pipeline.
            int decoded = readFromDecoderLocked(resampleDecodeScratch.data(), decodeChunkFrames, channels, reachedEnd);
            if (decoded > 0) {
                sharedAbsoluteInputPosition += decoded;
                inData[0] = reinterpret_cast<const uint8_t*>(resampleDecodeScratch.data());
                inCount = decoded;
                didRead = true;
                // Retry conversion immediately
                converted = swr_convert(outputSoxrContext, outData, outputCapacity, inData, inCount);
                if (converted > 0) {
                    outFrame += converted;
                }
            } else if (reachedEnd) {
                draining = true;
                // Retry conversion in draining mode
                converted = swr_convert(outputSoxrContext, outData, outputCapacity, nullptr, 0);
                if (converted > 0) {
                    outFrame += converted;
                }
            }
        }

        // Check for completion or stall
        if (outFrame >= numFrames) break;

        if (draining && converted == 0) break; // Drain complete

        // If input was provided but no output produced (filter priming), continue loop.
        // If force-read failed (EOF/error), terminate.
        if (didRead && inCount == 0 && !draining) break;
    }

    if (outFrame < numFrames) {
        memset(
                outputData + (static_cast<size_t>(outFrame) * channels),
                0,
                (numFrames - outFrame) * channels * sizeof(float)
        );
    }
}

void AudioEngine::clearRenderQueue() {
    std::lock_guard<std::mutex> lock(renderQueueMutex);
    renderQueueSamples.clear();
    renderQueueOffset = 0;
    renderTerminalStopPending.store(false);
}

void AudioEngine::appendRenderQueue(const float* data, int numFrames, int channels) {
    if (!data || numFrames <= 0 || channels <= 0) return;
    std::lock_guard<std::mutex> lock(renderQueueMutex);
    if (channels == 2) {
        const size_t sampleCount = static_cast<size_t>(numFrames) * 2u;
        renderQueueSamples.insert(renderQueueSamples.end(), data, data + sampleCount);
        return;
    }

    renderQueueSamples.reserve(renderQueueSamples.size() + static_cast<size_t>(numFrames) * 2u);
    for (int i = 0; i < numFrames; ++i) {
        const float mono = data[i * channels];
        renderQueueSamples.push_back(mono);
        renderQueueSamples.push_back(mono);
    }
}

int AudioEngine::popRenderQueue(float* outputData, int numFrames, int channels) {
    if (!outputData || numFrames <= 0 || channels <= 0) return 0;
    std::lock_guard<std::mutex> lock(renderQueueMutex);
    const size_t availableSamples = renderQueueSamples.size() > renderQueueOffset
            ? (renderQueueSamples.size() - renderQueueOffset)
            : 0u;
    const int availableFrames = static_cast<int>(availableSamples / static_cast<size_t>(channels));
    const int framesToCopy = std::min(numFrames, availableFrames);
    const int samplesToCopy = framesToCopy * channels;
    if (samplesToCopy > 0) {
        std::memcpy(
                outputData,
                renderQueueSamples.data() + renderQueueOffset,
                static_cast<size_t>(samplesToCopy) * sizeof(float)
        );
        renderQueueOffset += static_cast<size_t>(samplesToCopy);
        if (renderQueueOffset >= renderQueueSamples.size()) {
            renderQueueSamples.clear();
            renderQueueOffset = 0;
        } else if (renderQueueOffset > 8192u) {
            const size_t remaining = renderQueueSamples.size() - renderQueueOffset;
            std::memmove(
                    renderQueueSamples.data(),
                    renderQueueSamples.data() + renderQueueOffset,
                    remaining * sizeof(float)
            );
            renderQueueSamples.resize(remaining);
            renderQueueOffset = 0;
        }
    }
    return framesToCopy;
}

int AudioEngine::renderQueueFrames() const {
    std::lock_guard<std::mutex> lock(renderQueueMutex);
    const size_t availableSamples = renderQueueSamples.size() > renderQueueOffset
            ? (renderQueueSamples.size() - renderQueueOffset)
            : 0u;
    return static_cast<int>(availableSamples / 2u);
}

void AudioEngine::renderWorkerLoop() {
    pthread_setname_np(pthread_self(), "sp_render");
    // Best effort: keep decoder/render worker responsive under UI/system load.
    promoteThreadForAudio("render-worker", -16);

    std::vector<float> localBuffer;
    localBuffer.resize(1024u * 2u);

    for (;;) {
        const int baseTargetFrames = std::max(
                renderWorkerChunkFrames.load(std::memory_order_relaxed) * 2,
                renderWorkerTargetFrames.load(std::memory_order_relaxed)
        );
        const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        const int64_t boostUntilNs = renderQueueRecoveryBoostUntilNs.load(std::memory_order_relaxed);
        const bool recoveryBoostActive = nowNs < boostUntilNs;
        const int targetFrames = recoveryBoostActive
                ? std::max(baseTargetFrames, baseTargetFrames * 3)
                : baseTargetFrames;
        bool needsFill = false;
        {
            std::unique_lock<std::mutex> lock(renderQueueMutex);
            renderWorkerCv.wait_for(lock, std::chrono::milliseconds(8), [this, targetFrames]() {
                if (renderWorkerStop) return true;
                if (!isPlaying.load() || seekInProgress.load()) return false;
                const size_t availableSamples = renderQueueSamples.size() > renderQueueOffset
                        ? (renderQueueSamples.size() - renderQueueOffset)
                        : 0u;
                const int bufferedFrames = static_cast<int>(availableSamples / 2u);
                return bufferedFrames < targetFrames;
            });
            if (renderWorkerStop) {
                break;
            }
            if (!isPlaying.load() || seekInProgress.load()) {
                continue;
            }
            const size_t availableSamples = renderQueueSamples.size() > renderQueueOffset
                    ? (renderQueueSamples.size() - renderQueueOffset)
                    : 0u;
            const int bufferedFrames = static_cast<int>(availableSamples / 2u);
            needsFill = bufferedFrames < targetFrames;
        }

        if (!needsFill) {
            continue;
        }

        bool reachedEnd = false;
        int channels = 2;
        const int chunkFrames = std::max(256, renderWorkerChunkFrames.load(std::memory_order_relaxed));
        {
            std::lock_guard<std::mutex> lock(decoderMutex);
            if (!decoder || !isPlaying.load()) {
                continue;
            }
            channels = std::clamp(decoder->getChannelCount(), 1, 2);
            if (channels <= 0) channels = 2;
            localBuffer.resize(static_cast<size_t>(chunkFrames) * static_cast<size_t>(channels));

            const int outputSampleRate = streamSampleRate > 0 ? streamSampleRate : 48000;
            renderResampledLocked(localBuffer.data(), chunkFrames, channels, outputSampleRate, reachedEnd);

            const double callbackDeltaSeconds = (outputSampleRate > 0)
                    ? static_cast<double>(chunkFrames) / outputSampleRate
                    : 0.0;
            if (!reachedEnd && callbackDeltaSeconds > 0.0) {
                outputClockSeconds += callbackDeltaSeconds;
            }

            double calculatedPosition = -1.0;
            if (decoderRenderSampleRate > 0) {
                calculatedPosition =
                        sharedAbsoluteInputPositionBaseSeconds +
                        (static_cast<double>(sharedAbsoluteInputPosition) / decoderRenderSampleRate);
                if (calculatedPosition < 0.0) {
                    calculatedPosition = 0.0;
                }
            }
            double decoderPosition = decoder->getPlaybackPositionSeconds();
            const AudioDecoder::TimelineMode timelineMode = decoder->getTimelineMode();

            if (timelineMode == AudioDecoder::TimelineMode::Discontinuous) {
                if (!timelineSmootherInitialized) {
                    timelineSmoothedSeconds = (calculatedPosition >= 0.0 && sharedAbsoluteInputPosition > 0)
                            ? calculatedPosition
                            : outputClockSeconds;
                    timelineSmootherInitialized = true;
                }
                double nextPosition = timelineSmoothedSeconds;
                if (!reachedEnd && callbackDeltaSeconds > 0.0) {
                    nextPosition += callbackDeltaSeconds;
                }
                if (decoderPosition >= 0.0) {
                    const bool loopPointRepeatMode = repeatMode.load() == 2;
                    const int repeatModeNow = repeatMode.load();
                    const double backwardJumpSeconds = nextPosition - decoderPosition;
                    const bool restartLikeBackwardJump =
                            !loopPointRepeatMode &&
                            (repeatModeNow == 1 || repeatModeNow == 3) &&
                            backwardJumpSeconds > 1.0 &&
                            decoderPosition < 2.0;
                    if (loopPointRepeatMode && backwardJumpSeconds > 0.5) {
                        // In loop-point mode, backward timeline jumps are expected at wrap.
                        // Snap immediately to avoid visible "step-back" drift in the seek bar.
                        nextPosition = decoderPosition;
                        outputClockSeconds = decoderPosition;
                    } else if (restartLikeBackwardJump) {
                        // Repeat-track/subtune restarts should also snap immediately instead
                        // of easing back from the previous end position.
                        nextPosition = decoderPosition;
                        outputClockSeconds = decoderPosition;
                    } else {
                        const double correction = decoderPosition - nextPosition;
                        nextPosition += std::clamp(correction * 0.12, -0.25, 0.25);
                    }
                } else if (calculatedPosition >= 0.0) {
                    const double correction = calculatedPosition - nextPosition;
                    nextPosition += correction * 0.10;
                }
                const double durationNow = decoder->getDuration();
                if (durationNow > 0.0 && repeatMode.load() != 2) {
                    nextPosition = std::clamp(nextPosition, 0.0, durationNow);
                } else if (nextPosition < 0.0) {
                    nextPosition = 0.0;
                }
                timelineSmoothedSeconds = nextPosition;
                positionSeconds.store(nextPosition);
            } else if (calculatedPosition >= 0.0 && sharedAbsoluteInputPosition > 0) {
                positionSeconds.store(calculatedPosition);
            } else if (decoderPosition >= 0.0) {
                positionSeconds.store(decoderPosition);
            } else if (!reachedEnd && callbackDeltaSeconds > 0.0) {
                positionSeconds.fetch_add(callbackDeltaSeconds);
            }

            const double gainTimelinePosition = positionSeconds.load();
            const float endFadeGain = computeEndFadeGainLocked(gainTimelinePosition);
            applyGain(localBuffer.data(), chunkFrames, channels, endFadeGain);
            applyMasterChannelRouting(localBuffer.data(), chunkFrames, channels);
            applyOpenMptDspEffects(localBuffer.data(), chunkFrames, channels, outputSampleRate);
            applyMonoDownmix(localBuffer.data(), chunkFrames, channels);
            applyOutputLimiter(localBuffer.data(), chunkFrames, channels);

            const int mode = repeatMode.load();
            if (reachedEnd && mode != 1 && mode != 3) {
                const double durationAtEnd = decoder->getDuration();
                if (durationAtEnd > 0.0) {
                    positionSeconds.store(durationAtEnd);
                }
                if (mode == 0) {
                    naturalEndPending.store(true);
                }
                isPlaying.store(false);
                renderTerminalStopPending.store(true);
            }
        }

        appendRenderQueue(localBuffer.data(), chunkFrames, channels);

        if (!isPlaying.load() && renderTerminalStopPending.load()) {
            renderWorkerCv.notify_all();
            continue;
        }
    }
}
