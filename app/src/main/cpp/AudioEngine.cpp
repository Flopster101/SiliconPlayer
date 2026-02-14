#include "AudioEngine.h"
#include "decoders/DecoderRegistry.h"
#include "decoders/FFmpegDecoder.h"
#include "decoders/LibOpenMPTDecoder.h"
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <algorithm>
extern "C" {
#include <libswresample/swresample.h>
#include <libavutil/opt.h>
#include <libavutil/channel_layout.h>
#include <libavutil/mathematics.h>
}

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// register decoders
namespace {
    const char* outputResamplerName(int preference) {
        return preference == 2 ? "SoX" : "Built-in";
    }

    struct DecoderRegistration {
        DecoderRegistration() {
             DecoderRegistry::getInstance().registerDecoder("FFmpeg", FFmpegDecoder::getSupportedExtensions(), []() {
                return std::make_unique<FFmpegDecoder>();
            }, 0);

            DecoderRegistry::getInstance().registerDecoder("LibOpenMPT", LibOpenMPTDecoder::getSupportedExtensions(), []() {
                return std::make_unique<LibOpenMPTDecoder>();
            }, 10);
        }
    };
    static DecoderRegistration registration;
}

AudioEngine::AudioEngine() {
    createStream();
}

AudioEngine::~AudioEngine() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    freeOutputSoxrContextLocked();
    closeStream();
}

void AudioEngine::createStream() {
    AAudioStreamBuilder *builder;
    AAudio_createStreamBuilder(&builder);

    // Set parameters
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    const int configuredPerformanceMode = outputPerformanceMode;
    aaudio_performance_mode_t performanceMode = AAUDIO_PERFORMANCE_MODE_LOW_LATENCY;
    if (configuredPerformanceMode == 2) {
        performanceMode = AAUDIO_PERFORMANCE_MODE_NONE;
    } else if (configuredPerformanceMode == 3) {
        performanceMode = AAUDIO_PERFORMANCE_MODE_POWER_SAVING;
    }
    AAudioStreamBuilder_setPerformanceMode(builder, performanceMode);

    // Set callback
    AAudioStreamBuilder_setDataCallback(builder, dataCallback, this);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallback, this);

    // Open the stream
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to open stream: %s", AAudio_convertResultToText(result));
    } else {
        streamSampleRate = AAudioStream_getSampleRate(stream);
        streamChannelCount = AAudioStream_getChannelCount(stream);
        if (streamSampleRate <= 0) streamSampleRate = 48000;
        if (streamChannelCount <= 0) streamChannelCount = 2;
        applyStreamBufferPreset();
        LOGD(
                "AAudio stream opened: sampleRate=%d, channels=%d, backendPref=%d, perfMode=%d, bufferPreset=%d, allowFallback=%d",
                streamSampleRate,
                streamChannelCount,
                outputBackendPreference,
                outputPerformanceMode,
                outputBufferPreset,
                outputAllowFallback ? 1 : 0
        );
    }

    AAudioStreamBuilder_delete(builder);
}

void AudioEngine::applyStreamBufferPreset() {
    if (stream == nullptr) return;
    if (outputBufferPreset == 0) return;

    const int32_t burstFrames = AAudioStream_getFramesPerBurst(stream);
    const int32_t bufferCapacity = AAudioStream_getBufferCapacityInFrames(stream);
    if (burstFrames <= 0 || bufferCapacity <= 0) return;

    int multiplier = 2;
    if (outputBufferPreset == 2) {
        multiplier = 4;
    } else if (outputBufferPreset == 3) {
        multiplier = 8;
    }

    const int32_t target = std::clamp(
            burstFrames * multiplier,
            burstFrames,
            bufferCapacity
    );
    const int32_t applied = AAudioStream_setBufferSizeInFrames(stream, target);
    LOGD(
            "AAudio buffer preset applied: burst=%d capacity=%d target=%d applied=%d",
            burstFrames,
            bufferCapacity,
            target,
            applied
    );
}

void AudioEngine::reconfigureStream(bool resumePlayback) {
    const bool shouldResume = resumePlayback && isPlaying.load();
    if (stream != nullptr) {
        AAudioStream_requestStop(stream);
    }
    isPlaying.store(false);

    closeStream();
    createStream();

    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder) {
            decoderRenderSampleRate = resolveOutputSampleRateForCore(decoder->getName());
            decoder->setOutputSampleRate(decoderRenderSampleRate);
            // When only resampler preference changed, preserve the buffer to maintain position
            resetResamplerStateLocked(true);
        }
    }

    if (shouldResume && stream != nullptr) {
        const aaudio_result_t result = AAudioStream_requestStart(stream);
        if (result == AAUDIO_OK) {
            isPlaying.store(true);
        } else {
            LOGE("Failed to resume stream after reconfigure: %s", AAudio_convertResultToText(result));
        }
    }
}

void AudioEngine::closeStream() {
    if (stream != nullptr) {
        AAudioStream_close(stream);
        stream = nullptr;
    }
}

void AudioEngine::errorCallback(
        AAudioStream * /*stream*/,
        void *userData,
        aaudio_result_t error) {
    auto *engine = static_cast<AudioEngine *>(userData);
    LOGE("AAudio stream error callback: %s", AAudio_convertResultToText(error));
    engine->resumeAfterRebuild.store(engine->isPlaying.load());
    engine->isPlaying.store(false);
    engine->streamNeedsRebuild.store(true);
}

void AudioEngine::recoverStreamIfNeeded() {
    if (!streamNeedsRebuild.load()) {
        return;
    }

    closeStream();
    createStream();
    streamNeedsRebuild.store(false);

    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder) {
            decoderRenderSampleRate = resolveOutputSampleRateForCore(decoder->getName());
            decoder->setOutputSampleRate(decoderRenderSampleRate);
            // Preserve resampler state during stream recovery to maintain position
            resetResamplerStateLocked(true);
        }
    }

    if (resumeAfterRebuild.load()) {
        resumeAfterRebuild.store(false);
        if (stream != nullptr) {
            aaudio_result_t result = AAudioStream_requestStart(stream);
            if (result == AAUDIO_OK) {
                isPlaying.store(true);
                return;
            }
            LOGE("Failed to auto-resume after stream rebuild: %s", AAudio_convertResultToText(result));
        }
    }
}

void AudioEngine::resetResamplerStateLocked(bool preserveBuffer) {
    if (!preserveBuffer) {
        resampleInputBuffer.clear();
        resampleInputStartFrame = 0;
        resampleInputPosition = 0.0;
        sharedAbsoluteInputPosition = 0;
        const double currentPosition = positionSeconds.load();
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

int AudioEngine::readFromDecoderLocked(float* buffer, int numFrames, bool& reachedEnd) {
    if (!decoder || !buffer || numFrames <= 0) return 0;

    int framesRead = decoder->read(buffer, numFrames);
    if (framesRead > 0) {
        return framesRead;
    }

    if (repeatMode.load() == 2) {
        // Loop-point mode can return transient 0-frame reads at wrap boundaries.
        for (int retry = 0; retry < 4; ++retry) {
            framesRead = decoder->read(buffer, numFrames);
            if (framesRead > 0) {
                return framesRead;
            }
        }
    }

    if (repeatMode.load() == 1) {
        decoder->seek(0.0);
        positionSeconds.store(0.0);
        resetResamplerStateLocked();
        framesRead = decoder->read(buffer, numFrames);
        if (framesRead > 0) {
            return framesRead;
        }
    }

    if (repeatMode.load() != 2) {
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
        int framesRead = readFromDecoderLocked(outputData, numFrames, reachedEnd);
        if (framesRead < numFrames) {
            memset(
                    outputData + (framesRead * channels),
                    0,
                    (numFrames - framesRead) * channels * sizeof(float)
            );
        }
        return;
    }

    if (outputResamplerPreference == 2 && !outputSoxrUnavailable) {
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
            int decoded = readFromDecoderLocked(resampleDecodeScratch.data(), decodeChunkFrames, reachedEnd);
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
            int decoded = readFromDecoderLocked(resampleDecodeScratch.data(), decodeChunkFrames, reachedEnd);
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
            int decoded = readFromDecoderLocked(resampleDecodeScratch.data(), decodeChunkFrames, reachedEnd);
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

aaudio_data_callback_result_t AudioEngine::dataCallback(
        AAudioStream *stream,
        void *userData,
        void *audioData,
        int32_t numFrames) {
    auto *engine = static_cast<AudioEngine *>(userData);
    auto *outputData = static_cast<float *>(audioData);

    std::lock_guard<std::mutex> lock(engine->decoderMutex);
    if (engine->decoder) {
        const int channels = engine->decoder->getChannelCount();
        const int outputSampleRate = AAudioStream_getSampleRate(stream) > 0
                ? AAudioStream_getSampleRate(stream)
                : engine->streamSampleRate;
        bool reachedEnd = false;
        engine->renderResampledLocked(outputData, numFrames, channels, outputSampleRate, reachedEnd);

        const double callbackDeltaSeconds = (outputSampleRate > 0 && numFrames > 0)
                ? static_cast<double>(numFrames) / outputSampleRate
                : 0.0;
        if (!reachedEnd && callbackDeltaSeconds > 0.0) {
            engine->outputClockSeconds += callbackDeltaSeconds;
        }

        // Calculate position based on frames consumed by resampler at decoder rate
        double calculatedPosition = -1.0;
        if (engine->decoderRenderSampleRate > 0) {
            calculatedPosition = static_cast<double>(engine->sharedAbsoluteInputPosition) / engine->decoderRenderSampleRate;
        }
        const double decoderPosition = engine->decoder->getPlaybackPositionSeconds();
        const AudioDecoder::TimelineMode timelineMode = engine->decoder->getTimelineMode();

        if (timelineMode == AudioDecoder::TimelineMode::Discontinuous) {
            if (!engine->timelineSmootherInitialized) {
                if (decoderPosition >= 0.0) {
                    engine->timelineSmoothedSeconds = decoderPosition;
                } else if (calculatedPosition >= 0.0 && engine->sharedAbsoluteInputPosition > 0) {
                    engine->timelineSmoothedSeconds = calculatedPosition;
                } else {
                    engine->timelineSmoothedSeconds = engine->outputClockSeconds;
                }
                engine->timelineSmootherInitialized = true;
            }

            double nextPosition = engine->timelineSmoothedSeconds;
            if (!reachedEnd && callbackDeltaSeconds > 0.0) {
                nextPosition += callbackDeltaSeconds;
            }

            if (decoderPosition >= 0.0) {
                const double correction = decoderPosition - nextPosition;
                constexpr double hardJumpThresholdSeconds = 0.35;
                constexpr double softBlendRatio = 0.18;
                if (std::fabs(correction) >= hardJumpThresholdSeconds || correction < -0.08) {
                    nextPosition = decoderPosition;
                } else {
                    nextPosition += correction * softBlendRatio;
                }
            } else if (calculatedPosition >= 0.0 && engine->sharedAbsoluteInputPosition > 0) {
                const double correction = calculatedPosition - nextPosition;
                constexpr double fallbackBlendRatio = 0.10;
                if (std::fabs(correction) >= 0.75) {
                    nextPosition = calculatedPosition;
                } else {
                    nextPosition += correction * fallbackBlendRatio;
                }
            }

            const double durationNow = engine->decoder->getDuration();
            if (durationNow > 0.0) {
                if (engine->repeatMode.load() == 2) {
                    nextPosition = std::fmod(nextPosition, durationNow);
                    if (nextPosition < 0.0) {
                        nextPosition += durationNow;
                    }
                } else {
                    nextPosition = std::clamp(nextPosition, 0.0, durationNow);
                }
            } else if (nextPosition < 0.0) {
                nextPosition = 0.0;
            }

            engine->timelineSmoothedSeconds = nextPosition;
            engine->positionSeconds.store(nextPosition);
        } else if (calculatedPosition >= 0.0 && engine->sharedAbsoluteInputPosition > 0) {
            // Continuous timelines (or unknown fallback) track consumed input frames.
            engine->positionSeconds.store(calculatedPosition);
        } else if (decoderPosition >= 0.0) {
            engine->positionSeconds.store(decoderPosition);
        } else if (!reachedEnd && callbackDeltaSeconds > 0.0) {
            engine->positionSeconds.fetch_add(callbackDeltaSeconds);
        }

        if (reachedEnd && engine->repeatMode.load() != 1) {
            const double durationAtEnd = engine->decoder->getDuration();
            if (durationAtEnd > 0.0) {
                engine->positionSeconds.store(durationAtEnd);
            }
            if (engine->repeatMode.load() == 0) {
                engine->naturalEndPending.store(true);
            }
            engine->isPlaying.store(false);
            return AAUDIO_CALLBACK_RESULT_STOP;
        }
    } else {
        // Output silence
        memset(outputData, 0, numFrames * 2 * sizeof(float));
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

bool AudioEngine::start() {
    recoverStreamIfNeeded();

    if (stream == nullptr || streamNeedsRebuild.load()) {
        closeStream();
        createStream();
        streamNeedsRebuild.store(false);
    }
    if (stream != nullptr) {
        aaudio_stream_state_t state = AAudioStream_getState(stream);
        if (state == AAUDIO_STREAM_STATE_DISCONNECTED ||
            state == AAUDIO_STREAM_STATE_CLOSING ||
            state == AAUDIO_STREAM_STATE_CLOSED) {
            closeStream();
            createStream();
        }

        {
            std::lock_guard<std::mutex> lock(decoderMutex);
            if (decoder) {
                decoderRenderSampleRate = resolveOutputSampleRateForCore(decoder->getName());
                decoder->setOutputSampleRate(decoderRenderSampleRate);
                resetResamplerStateLocked();
                const double durationNow = decoder->getDuration();
                if (durationNow > 0.0 && positionSeconds.load() >= (durationNow - 0.01)) {
                    decoder->seek(0.0);
                    positionSeconds.store(0.0);
                    resetResamplerStateLocked();
                }
            }
        }

        aaudio_result_t result = AAudioStream_requestStart(stream);
        if (result != AAUDIO_OK) {
            LOGE("Failed to start stream: %s", AAudio_convertResultToText(result));
            closeStream();
            createStream();
            if (stream == nullptr) {
                return false;
            }
            result = AAudioStream_requestStart(stream);
            if (result != AAUDIO_OK) {
                LOGE("Retry start failed: %s", AAudio_convertResultToText(result));
                isPlaying = false;
                return false;
            }
        }
        isPlaying = true;
        naturalEndPending.store(false);
        return true;
    }
    return false;
}

void AudioEngine::stop() {
    if (stream != nullptr) {
        resumeAfterRebuild.store(false);
        AAudioStream_requestStop(stream);
        isPlaying = false;
        naturalEndPending.store(false);
    }
}

bool AudioEngine::isEnginePlaying() const {
    return isPlaying.load();
}

void AudioEngine::setUrl(const char* url) {
    LOGD("URL set to: %s", url);

    auto newDecoder = DecoderRegistry::getInstance().createDecoder(url);
    if (newDecoder && newDecoder->open(url)) {
        std::lock_guard<std::mutex> lock(decoderMutex);
        const int targetRate = resolveOutputSampleRateForCore(newDecoder->getName());
        newDecoder->setOutputSampleRate(targetRate);
        decoderRenderSampleRate = targetRate;
        newDecoder->setRepeatMode(repeatMode.load());
        const auto optionsIt = coreOptions.find(newDecoder->getName());
        if (optionsIt != coreOptions.end()) {
            for (const auto& [name, value] : optionsIt->second) {
                newDecoder->setOption(name.c_str(), value.c_str());
            }
        }
        decoder = std::move(newDecoder);
        resetResamplerStateLocked();
        positionSeconds.store(0.0);
        outputClockSeconds = 0.0;
        timelineSmoothedSeconds = 0.0;
        timelineSmootherInitialized = false;
        naturalEndPending.store(false);
    } else {
        LOGE("Failed to open file: %s", url);
    }
}

void AudioEngine::restart() {
    stop();
    start();
}

double AudioEngine::getDurationSeconds() {
    const_cast<AudioEngine*>(this)->recoverStreamIfNeeded();
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0.0;
    }
    return decoder->getDuration();
}

double AudioEngine::getPositionSeconds() {
    recoverStreamIfNeeded();
    return positionSeconds.load();
}

void AudioEngine::seekToSeconds(double seconds) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return;
    }
    decoder->seek(seconds);
    resetResamplerStateLocked();
    const double clamped = seconds < 0.0 ? 0.0 : seconds;
    positionSeconds.store(clamped);
    outputClockSeconds = clamped;
    timelineSmoothedSeconds = clamped;
    timelineSmootherInitialized = false;
    naturalEndPending.store(false);
}

void AudioEngine::setLooping(bool enabled) {
    setRepeatMode(enabled ? 1 : 0);
}

void AudioEngine::setRepeatMode(int mode) {
    const int normalized = (mode >= 0 && mode <= 2) ? mode : 0;
    repeatMode.store(normalized);
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (decoder) {
        decoder->setRepeatMode(normalized);
    }
}

int AudioEngine::getRepeatModeCapabilities() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return AudioDecoder::REPEAT_CAP_TRACK;
    }
    return decoder->getRepeatModeCapabilities();
}

int AudioEngine::getPlaybackCapabilities() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return AudioDecoder::PLAYBACK_CAP_SEEK |
               AudioDecoder::PLAYBACK_CAP_RELIABLE_DURATION |
               AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE;
    }
    return decoder->getPlaybackCapabilities();
}

int AudioEngine::resolveOutputSampleRateForCore(const std::string& coreName) const {
    auto it = coreOutputSampleRateHz.find(coreName);
    if (it != coreOutputSampleRateHz.end() && it->second > 0) {
        return it->second;
    }
    return (streamSampleRate > 0) ? streamSampleRate : 48000;
}

void AudioEngine::setCoreOutputSampleRate(const std::string& coreName, int sampleRateHz) {
    if (coreName.empty()) return;
    const int normalizedRate = sampleRateHz > 0 ? sampleRateHz : 0;

    std::lock_guard<std::mutex> lock(decoderMutex);
    coreOutputSampleRateHz[coreName] = normalizedRate;

    if (decoder && coreName == decoder->getName()) {
        decoderRenderSampleRate = resolveOutputSampleRateForCore(coreName);
        decoder->setOutputSampleRate(decoderRenderSampleRate);
        resetResamplerStateLocked();
    }
}

void AudioEngine::setCoreOption(
        const std::string& coreName,
        const std::string& optionName,
        const std::string& optionValue) {
    if (coreName.empty() || optionName.empty()) return;

    std::lock_guard<std::mutex> lock(decoderMutex);
    coreOptions[coreName][optionName] = optionValue;
    if (decoder && coreName == decoder->getName()) {
        decoder->setOption(optionName.c_str(), optionValue.c_str());
    }
}

void AudioEngine::setAudioPipelineConfig(
        int backendPreference,
        int performanceMode,
        int bufferPreset,
        int resamplerPreference,
        bool allowFallback) {
    const int normalizedBackend = (backendPreference >= 0 && backendPreference <= 3) ? backendPreference : 0;
    const int normalizedPerformance = (performanceMode >= 0 && performanceMode <= 3) ? performanceMode : 1;
    const int normalizedBufferPreset = (bufferPreset >= 0 && bufferPreset <= 3) ? bufferPreset : 0;
    const int normalizedResampler = (resamplerPreference >= 1 && resamplerPreference <= 2) ? resamplerPreference : 1;

    const bool changed =
            outputBackendPreference != normalizedBackend ||
            outputPerformanceMode != normalizedPerformance ||
            outputBufferPreset != normalizedBufferPreset ||
            outputResamplerPreference != normalizedResampler ||
            outputAllowFallback != allowFallback;

    outputBackendPreference = normalizedBackend;
    outputPerformanceMode = normalizedPerformance;
    outputBufferPreset = normalizedBufferPreset;
    outputResamplerPreference = normalizedResampler;
    outputAllowFallback = allowFallback;
    outputSoxrUnavailable = false;
    LOGD(
            "Audio pipeline config: backend=%d perf=%d buffer=%d resampler=%s(%d) allowFallback=%d changed=%d",
            outputBackendPreference,
            outputPerformanceMode,
            outputBufferPreset,
            outputResamplerName(outputResamplerPreference),
            outputResamplerPreference,
            outputAllowFallback ? 1 : 0,
            changed ? 1 : 0
    );

    if (!changed) return;
    reconfigureStream(true);
}

bool AudioEngine::consumeNaturalEndEvent() {
    return naturalEndPending.exchange(false);
}

std::string AudioEngine::getTitle() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getTitle();
}

std::string AudioEngine::getArtist() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getArtist();
}

int AudioEngine::getSampleRate() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getSampleRate();
}

int AudioEngine::getDisplayChannelCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getDisplayChannelCount();
}

int AudioEngine::getChannelCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getChannelCount();
}

int AudioEngine::getBitDepth() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getBitDepth();
}

std::string AudioEngine::getBitDepthLabel() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "Unknown";
    }
    return decoder->getBitDepthLabel();
}

std::string AudioEngine::getCurrentDecoderName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getName();
}
