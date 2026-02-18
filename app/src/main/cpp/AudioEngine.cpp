#include "AudioEngine.h"
#include "decoders/DecoderRegistry.h"
#include "decoders/FFmpegDecoder.h"
#include "decoders/LibOpenMPTDecoder.h"
#include "decoders/VGMDecoder.h"
#include "decoders/GmeDecoder.h"
#include "decoders/LibSidPlayFpDecoder.h"
#include "decoders/LazyUsf2Decoder.h"
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <limits>
#include <chrono>
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

    constexpr int kVisualizationWaveformSize = 256;
    constexpr int kVisualizationFftSize = 2048;
    constexpr int kVisualizationSpectrumBins = 256;
    constexpr int kRenderChunkFramesSmall = 512;
    constexpr int kRenderChunkFramesMedium = 1024;
    constexpr int kRenderChunkFramesLarge = 2048;
    constexpr int kRenderTargetFramesSmall = 4096;
    constexpr int kRenderTargetFramesMedium = 8192;
    constexpr int kRenderTargetFramesLarge = 16384;

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

    struct DecoderRegistration {
        DecoderRegistration() {
             DecoderRegistry::getInstance().registerDecoder("FFmpeg", FFmpegDecoder::getSupportedExtensions(), []() {
                return std::make_unique<FFmpegDecoder>();
            }, 0);

            DecoderRegistry::getInstance().registerDecoder("LibOpenMPT", LibOpenMPTDecoder::getSupportedExtensions(), []() {
                return std::make_unique<LibOpenMPTDecoder>();
            }, 10);

            DecoderRegistry::getInstance().registerDecoder("VGMPlay", VGMDecoder::getSupportedExtensions(), []() {
                return std::make_unique<VGMDecoder>();
            }, 5);

            DecoderRegistry::getInstance().registerDecoder("Game Music Emu", GmeDecoder::getSupportedExtensions(), []() {
                return std::make_unique<GmeDecoder>();
            }, 6);

            DecoderRegistry::getInstance().registerDecoder("LibSIDPlayFP", LibSidPlayFpDecoder::getSupportedExtensions(), []() {
                return std::make_unique<LibSidPlayFpDecoder>();
            }, 7);

            DecoderRegistry::getInstance().registerDecoder("LazyUSF2", LazyUsf2Decoder::getSupportedExtensions(), []() {
                return std::make_unique<LazyUsf2Decoder>();
            }, 8);
        }
    };
    static DecoderRegistration registration;
}

AudioEngine::AudioEngine() {
    updateRenderQueueTuning();
    seekWorkerThread = std::thread([this]() { seekWorkerLoop(); });
    renderWorkerThread = std::thread([this]() { renderWorkerLoop(); });
    createStream();
}

AudioEngine::~AudioEngine() {
    {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekWorkerStop = true;
        seekRequestPending = false;
        seekAbortRequested.store(true);
    }
    seekWorkerCv.notify_all();
    if (seekWorkerThread.joinable()) {
        seekWorkerThread.join();
    }
    {
        std::lock_guard<std::mutex> lock(renderQueueMutex);
        renderWorkerStop = true;
    }
    renderWorkerCv.notify_all();
    if (renderWorkerThread.joinable()) {
        renderWorkerThread.join();
    }
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
            const int desiredRate = resolveOutputSampleRateForCore(decoder->getName());
            decoder->setOutputSampleRate(desiredRate);
            decoderRenderSampleRate = decoder->getSampleRate();
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
            const int desiredRate = resolveOutputSampleRateForCore(decoder->getName());
            decoder->setOutputSampleRate(desiredRate);
            decoderRenderSampleRate = decoder->getSampleRate();
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

int AudioEngine::readFromDecoderLocked(float* buffer, int numFrames, bool& reachedEnd) {
    if (!decoder || !buffer || numFrames <= 0) return 0;

    int framesRead = decoder->read(buffer, numFrames);
    if (framesRead > 0) {
        return framesRead;
    }

    const int mode = repeatMode.load();

    if (mode == 2) {
        // Loop-point mode can return transient 0-frame reads at wrap boundaries.
        for (int retry = 0; retry < 4; ++retry) {
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
            framesRead = decoder->read(buffer, numFrames);
            if (framesRead > 0) {
                return framesRead;
            }
        } else {
            decoder->seek(0.0);
            positionSeconds.store(0.0);
            resetResamplerStateLocked();
            sharedAbsoluteInputPositionBaseSeconds = 0.0;
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

void AudioEngine::updateRenderQueueTuning() {
    int chunkFrames = kRenderChunkFramesMedium;
    int targetFrames = kRenderTargetFramesMedium;
    switch (outputBufferPreset) {
        case 1:
            chunkFrames = kRenderChunkFramesSmall;
            targetFrames = kRenderTargetFramesSmall;
            break;
        case 3:
            chunkFrames = kRenderChunkFramesLarge;
            targetFrames = kRenderTargetFramesLarge;
            break;
        case 0:
        case 2:
        default:
            chunkFrames = kRenderChunkFramesMedium;
            targetFrames = kRenderTargetFramesMedium;
            break;
    }
    if (targetFrames < chunkFrames * 2) {
        targetFrames = chunkFrames * 2;
    }
    renderWorkerChunkFrames.store(chunkFrames, std::memory_order_relaxed);
    renderWorkerTargetFrames.store(targetFrames, std::memory_order_relaxed);
    LOGD("Render queue tuning: preset=%d chunk=%d target=%d", outputBufferPreset, chunkFrames, targetFrames);
}

void AudioEngine::renderWorkerLoop() {
    std::vector<float> localBuffer;
    localBuffer.resize(static_cast<size_t>(kRenderChunkFramesMedium) * 2u);

    for (;;) {
        const int targetFrames = std::max(
                renderWorkerChunkFrames.load(std::memory_order_relaxed) * 2,
                renderWorkerTargetFrames.load(std::memory_order_relaxed)
        );
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
        }

        if (!isPlaying.load() || seekInProgress.load()) {
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
                    const double correction = decoderPosition - nextPosition;
                    nextPosition += std::clamp(correction * 0.12, -0.25, 0.25);
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
            applyMonoDownmix(localBuffer.data(), chunkFrames, channels);
            if (shouldUpdateVisualization()) {
                updateVisualizationDataLocked(localBuffer.data(), chunkFrames, channels);
            }

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

        if (!isPlaying.load() && renderTerminalStopPending.load()) {
            renderWorkerCv.notify_all();
            continue;
        }

        appendRenderQueue(localBuffer.data(), chunkFrames, channels);
    }
}

aaudio_data_callback_result_t AudioEngine::dataCallback(
        AAudioStream *stream,
        void *userData,
        void *audioData,
        int32_t numFrames) {
    (void)stream;
    auto *engine = static_cast<AudioEngine *>(userData);
    auto *outputData = static_cast<float *>(audioData);
    if (engine->seekInProgress.load()) {
        memset(outputData, 0, static_cast<size_t>(numFrames) * 2 * sizeof(float));
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    engine->renderQueueCallbackCount.fetch_add(1, std::memory_order_relaxed);
    const int framesCopied = engine->popRenderQueue(outputData, numFrames, 2);
    if (framesCopied < numFrames) {
        const uint64_t missingFrames = static_cast<uint64_t>(numFrames - framesCopied);
        engine->renderQueueUnderrunCount.fetch_add(1, std::memory_order_relaxed);
        engine->renderQueueUnderrunFrames.fetch_add(missingFrames, std::memory_order_relaxed);
#ifndef NDEBUG
        const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        const int64_t previousLogNs = engine->renderQueueLastUnderrunLogNs.load(std::memory_order_relaxed);
        if (nowNs - previousLogNs > 1000000000LL) {
            const uint64_t underruns = engine->renderQueueUnderrunCount.load(std::memory_order_relaxed);
            const uint64_t underrunFrames = engine->renderQueueUnderrunFrames.load(std::memory_order_relaxed);
            const uint64_t callbacks = engine->renderQueueCallbackCount.load(std::memory_order_relaxed);
            LOGD(
                    "Render queue underrun: missing=%llu callbacks=%llu underruns=%llu totalMissingFrames=%llu bufferedFrames=%d",
                    static_cast<unsigned long long>(missingFrames),
                    static_cast<unsigned long long>(callbacks),
                    static_cast<unsigned long long>(underruns),
                    static_cast<unsigned long long>(underrunFrames),
                    engine->renderQueueFrames()
            );
            engine->renderQueueLastUnderrunLogNs.store(nowNs, std::memory_order_relaxed);
        }
#endif
        std::memset(
                outputData + (static_cast<size_t>(framesCopied) * 2u),
                0,
                static_cast<size_t>(numFrames - framesCopied) * 2u * sizeof(float)
        );
    }

    if (engine->renderTerminalStopPending.load() && engine->renderQueueFrames() <= 0) {
        engine->renderTerminalStopPending.store(false);
        return AAUDIO_CALLBACK_RESULT_STOP;
    }

    engine->renderWorkerCv.notify_one();

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
                const int desiredRate = resolveOutputSampleRateForCore(decoder->getName());
                decoder->setOutputSampleRate(desiredRate);
                decoderRenderSampleRate = decoder->getSampleRate();
                resetResamplerStateLocked();
                const double durationNow = decoder->getDuration();
                if (durationNow > 0.0 && positionSeconds.load() >= (durationNow - 0.01)) {
                    decoder->seek(0.0);
                    positionSeconds.store(0.0);
                    resetResamplerStateLocked();
                    sharedAbsoluteInputPositionBaseSeconds = 0.0;
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
        clearRenderQueue();
        renderWorkerCv.notify_one();
        return true;
    }
    return false;
}

void AudioEngine::stop() {
    const bool wasSeeking = seekInProgress.load();
    if (wasSeeking) {
        decoderSerial.fetch_add(1);
        {
            std::lock_guard<std::mutex> lock(seekWorkerMutex);
            seekAbortRequested.store(true);
            seekRequestPending = false;
        }
        stopStreamAfterSeek.store(true);
        seekWorkerCv.notify_one();
        isPlaying.store(false);
        naturalEndPending.store(false);
        clearRenderQueue();
        renderWorkerCv.notify_all();
        return;
    }

    if (stream != nullptr) {
        resumeAfterRebuild.store(false);
        AAudioStream_requestStop(stream);
        isPlaying = false;
        naturalEndPending.store(false);
        clearRenderQueue();
        renderWorkerCv.notify_all();
    }
}

bool AudioEngine::isEnginePlaying() const {
    return isPlaying.load();
}

void AudioEngine::setUrl(const char* url) {
    LOGD("URL set to: %s", url);

    // Ensure background seek work is fully quiesced before replacing decoder.
    // This prevents worker-thread reads from racing decoder teardown.
    if (seekInProgress.load()) {
        {
            std::lock_guard<std::mutex> lock(seekWorkerMutex);
            seekAbortRequested.store(true);
            seekRequestPending = false;
        }
        seekWorkerCv.notify_one();
        while (seekInProgress.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }

    decoderSerial.fetch_add(1);
    clearRenderQueue();

    // Drop any previously loaded decoder first. If opening the new source fails,
    // playback should not continue from stale decoder state.
    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        decoder.reset();
        cachedDurationSeconds.store(0.0);
        resetResamplerStateLocked();
        decoderRenderSampleRate = streamSampleRate;
        positionSeconds.store(0.0);
        sharedAbsoluteInputPositionBaseSeconds = 0.0;
        outputClockSeconds = 0.0;
        timelineSmoothedSeconds = 0.0;
        timelineSmootherInitialized = false;
        naturalEndPending.store(false);
    }
    {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekAbortRequested.store(false);
        seekRequestPending = false;
        seekInProgress.store(false);
        stopStreamAfterSeek.store(false);
    }

    auto newDecoder = DecoderRegistry::getInstance().createDecoder(url);
    if (newDecoder) {
        const int targetRate = resolveOutputSampleRateForCore(newDecoder->getName());
        std::unordered_map<std::string, std::string> optionsForDecoder;
        {
            std::lock_guard<std::mutex> lock(decoderMutex);
            const auto optionsIt = coreOptions.find(newDecoder->getName());
            if (optionsIt != coreOptions.end()) {
                optionsForDecoder = optionsIt->second;
            }
        }

        newDecoder->setOutputSampleRate(targetRate);
        if (!optionsForDecoder.empty()) {
            for (const auto& [name, value] : optionsForDecoder) {
                newDecoder->setOption(name.c_str(), value.c_str());
            }
        }
        if (!newDecoder->open(url)) {
            LOGE("Failed to open file: %s", url);
            return;
        }
        std::lock_guard<std::mutex> lock(decoderMutex);
        decoderRenderSampleRate = newDecoder->getSampleRate();
        newDecoder->setRepeatMode(repeatMode.load());
        if (!optionsForDecoder.empty()) {
            for (const auto& [name, value] : optionsForDecoder) {
                newDecoder->setOption(name.c_str(), value.c_str());
            }
        }
        decoder = std::move(newDecoder);
        cachedDurationSeconds.store(decoder->getDuration());
        resetResamplerStateLocked();
        positionSeconds.store(0.0);
        sharedAbsoluteInputPositionBaseSeconds = 0.0;
        outputClockSeconds = 0.0;
        timelineSmoothedSeconds = 0.0;
        timelineSmootherInitialized = false;
        naturalEndPending.store(false);
        renderWorkerCv.notify_one();
    } else {
        LOGE("Failed to create decoder for file: %s", url);
    }
}

void AudioEngine::restart() {
    stop();
    start();
}

double AudioEngine::getDurationSeconds() {
    const_cast<AudioEngine*>(this)->recoverStreamIfNeeded();
    if (seekInProgress.load()) {
        return cachedDurationSeconds.load();
    }
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        // Avoid blocking real-time audio callback. Return last known value.
        return cachedDurationSeconds.load();
    }
    if (!decoder) {
        return 0.0;
    }
    const double duration = decoder->getDuration();
    cachedDurationSeconds.store(duration);
    return duration;
}

double AudioEngine::getPositionSeconds() {
    recoverStreamIfNeeded();
    return positionSeconds.load();
}

bool AudioEngine::isSeekInProgress() const {
    return seekInProgress.load();
}

void AudioEngine::seekToSeconds(double seconds) {
    const uint64_t targetDecoderSerial = decoderSerial.load();
    const double normalizedTarget = std::max(0.0, seconds);
    bool handledDirectSeek = false;
    clearRenderQueue();

    // Cancel any pending async-seek request first so a stale worker cycle
    // cannot overwrite a direct-seek result.
    {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekAbortRequested.store(true);
        seekRequestPending = false;
    }
    seekWorkerCv.notify_one();

    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder) {
            const int capabilities = decoder->getPlaybackCapabilities();
            if ((capabilities & AudioDecoder::PLAYBACK_CAP_DIRECT_SEEK) != 0 &&
                (capabilities & AudioDecoder::PLAYBACK_CAP_SEEK) != 0) {
                decoder->seek(normalizedTarget);
                const double decoderPosition = decoder->getPlaybackPositionSeconds();
                const double resolvedPosition = decoderPosition >= 0.0 ? decoderPosition : normalizedTarget;
                const double duration = decoder->getDuration();
                cachedDurationSeconds.store(duration);
                resetResamplerStateLocked();
                positionSeconds.store(resolvedPosition);
                sharedAbsoluteInputPositionBaseSeconds = resolvedPosition;
                outputClockSeconds = resolvedPosition;
                timelineSmoothedSeconds = resolvedPosition;
                timelineSmootherInitialized = false;
                naturalEndPending.store(false);
                handledDirectSeek = true;
            }
        }
    }

    if (handledDirectSeek) {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekAbortRequested.store(false);
        seekInProgress.store(false);
        stopStreamAfterSeek.store(false);
        renderWorkerCv.notify_one();
        return;
    }

    positionSeconds.store(normalizedTarget);
    naturalEndPending.store(false);
    {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekAbortRequested.store(false);
        seekRequestSeconds = normalizedTarget;
        seekRequestDecoderSerial = targetDecoderSerial;
        seekRequestPending = true;
        seekInProgress.store(true);
    }
    seekWorkerCv.notify_one();
    renderWorkerCv.notify_one();
}

double AudioEngine::runAsyncSeekLocked(double targetSeconds) {
    if (!decoder) {
        return 0.0;
    }

    const int capabilities = decoder->getPlaybackCapabilities();
    if ((capabilities & AudioDecoder::PLAYBACK_CAP_SEEK) == 0) {
        const double position = decoder->getPlaybackPositionSeconds();
        return position >= 0.0 ? position : 0.0;
    }
    const double clampedTarget = std::max(0.0, targetSeconds);

    // Prefer direct/random-access seek when a decoder can do it reliably.
    // We still execute it on the async seek worker to keep UI interactions non-blocking.
    if ((capabilities & AudioDecoder::PLAYBACK_CAP_DIRECT_SEEK) != 0) {
        decoder->seek(clampedTarget);
        const double decoderPosition = decoder->getPlaybackPositionSeconds();
        return decoderPosition >= 0.0 ? decoderPosition : clampedTarget;
    }

    decoder->seek(0.0);
    const int channels = std::max(1, decoder->getChannelCount());
    int decoderRate = decoderRenderSampleRate > 0 ? decoderRenderSampleRate : decoder->getSampleRate();
    if (decoderRate <= 0) {
        decoderRate = 48000;
    }

    const int64_t targetFrames = static_cast<int64_t>(std::llround(clampedTarget * decoderRate));
    int64_t skippedFrames = 0;
    constexpr int kAsyncSeekChunkFrames = 4096;

    while (skippedFrames < targetFrames) {
        {
            std::lock_guard<std::mutex> seekLock(seekWorkerMutex);
            if (seekRequestPending || seekWorkerStop || seekAbortRequested.load()) {
                break;
            }
        }
        const int framesToRead = static_cast<int>(std::min<int64_t>(kAsyncSeekChunkFrames, targetFrames - skippedFrames));
        const size_t neededSamples = static_cast<size_t>(framesToRead) * channels;
        if (asyncSeekDiscardBuffer.size() < neededSamples) {
            asyncSeekDiscardBuffer.resize(neededSamples);
        }
        const int framesRead = decoder->read(asyncSeekDiscardBuffer.data(), framesToRead);
        if (framesRead <= 0) {
            break;
        }
        skippedFrames += framesRead;
    }

    const double decoderPosition = decoder->getPlaybackPositionSeconds();
    if (decoderPosition >= 0.0) {
        return decoderPosition;
    }
    return static_cast<double>(skippedFrames) / static_cast<double>(decoderRate);
}

void AudioEngine::seekWorkerLoop() {
    for (;;) {
        double targetSeconds = 0.0;
        uint64_t targetDecoderSerial = 0;
        {
            std::unique_lock<std::mutex> lock(seekWorkerMutex);
            seekWorkerCv.wait(lock, [this]() { return seekWorkerStop || seekRequestPending; });
            if (seekWorkerStop) {
                break;
            }
            targetSeconds = seekRequestSeconds;
            targetDecoderSerial = seekRequestDecoderSerial;
            seekRequestPending = false;
        }

        if (decoder && targetDecoderSerial == decoderSerial.load() && !seekAbortRequested.load()) {
            double resolvedPosition = runAsyncSeekLocked(targetSeconds);
            if (!seekAbortRequested.load()) {
                const double duration = decoder->getDuration();
                if (duration > 0.0 && repeatMode.load() != 2) {
                    resolvedPosition = std::clamp(resolvedPosition, 0.0, duration);
                } else if (resolvedPosition < 0.0) {
                    resolvedPosition = 0.0;
                }
                cachedDurationSeconds.store(duration);
                {
                    std::lock_guard<std::mutex> lock(decoderMutex);
                    resetResamplerStateLocked();
                    positionSeconds.store(resolvedPosition);
                    sharedAbsoluteInputPositionBaseSeconds = resolvedPosition;
                    outputClockSeconds = resolvedPosition;
                    timelineSmoothedSeconds = resolvedPosition;
                    timelineSmootherInitialized = false;
                    naturalEndPending.store(false);
                }
            }
        }

        if (stopStreamAfterSeek.exchange(false) && stream != nullptr) {
            resumeAfterRebuild.store(false);
            AAudioStream_requestStop(stream);
        }

        {
            std::lock_guard<std::mutex> lock(seekWorkerMutex);
            if (!seekRequestPending) {
                seekInProgress.store(false);
                seekAbortRequested.store(false);
            }
        }
        renderWorkerCv.notify_one();
    }
}

void AudioEngine::setLooping(bool enabled) {
    setRepeatMode(enabled ? 1 : 0);
}

void AudioEngine::setRepeatMode(int mode) {
    const int normalized = (mode >= 0 && mode <= 3) ? mode : 0;
    const int previousMode = repeatMode.exchange(normalized);
    bool shouldStopForTerminalState = false;
    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder) {
            decoder->setRepeatMode(normalized);

            // If we are leaving LP mode while already at/after track end, apply the
            // newly selected repeat semantics immediately instead of waiting for a
            // future decoder terminal event that may not occur promptly.
            if (previousMode == 2 && normalized != 2) {
                const double durationNow = decoder->getDuration();
                const double currentPosition = positionSeconds.load();
                const bool atOrPastEnd = durationNow > 0.0 && currentPosition >= (durationNow - 0.01);
                if (atOrPastEnd) {
                    if (normalized == 1) {
                        const int subtuneCount = std::max(1, decoder->getSubtuneCount());
                        if (subtuneCount > 1) {
                            const int currentIndex = std::clamp(decoder->getCurrentSubtuneIndex(), 0, subtuneCount - 1);
                            const int nextIndex = (currentIndex + 1) % subtuneCount;
                            if (!decoder->selectSubtune(nextIndex)) {
                                decoder->seek(0.0);
                            }
                        } else {
                            decoder->seek(0.0);
                        }
                        resetResamplerStateLocked();
                        positionSeconds.store(0.0);
                        sharedAbsoluteInputPositionBaseSeconds = 0.0;
                        outputClockSeconds = 0.0;
                        timelineSmoothedSeconds = 0.0;
                        timelineSmootherInitialized = false;
                        naturalEndPending.store(false);
                    } else if (normalized == 3) {
                        decoder->seek(0.0);
                        resetResamplerStateLocked();
                        positionSeconds.store(0.0);
                        sharedAbsoluteInputPositionBaseSeconds = 0.0;
                        outputClockSeconds = 0.0;
                        timelineSmoothedSeconds = 0.0;
                        timelineSmootherInitialized = false;
                        naturalEndPending.store(false);
                    } else if (normalized == 0) {
                        shouldStopForTerminalState = true;
                        naturalEndPending.store(true);
                    }
                }
            }
        }
    }

    if (shouldStopForTerminalState) {
        if (stream != nullptr) {
            AAudioStream_requestStop(stream);
        }
        isPlaying.store(false);
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
        const bool supportsLiveRateChange =
                (decoder->getPlaybackCapabilities() & AudioDecoder::PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE) != 0;
        if (supportsLiveRateChange) {
            const int desiredRate = resolveOutputSampleRateForCore(coreName);
            decoder->setOutputSampleRate(desiredRate);
            decoderRenderSampleRate = decoder->getSampleRate();
            resetResamplerStateLocked();
        }
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

int AudioEngine::getCoreOptionApplyPolicy(
        const std::string& coreName,
        const std::string& optionName) {
    if (coreName.empty() || optionName.empty()) {
        return AudioDecoder::OPTION_APPLY_LIVE;
    }

    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder && decoder->getName() == coreName) {
            return decoder->getOptionApplyPolicy(optionName.c_str());
        }
    }

    auto tempDecoder = DecoderRegistry::getInstance().createDecoderByName(coreName);
    if (tempDecoder) {
        return tempDecoder->getOptionApplyPolicy(optionName.c_str());
    }
    return AudioDecoder::OPTION_APPLY_LIVE;
}

int AudioEngine::getCoreCapabilities(const std::string& coreName) {
    if (coreName.empty()) return 0;

    // Check if we already have this decoder loaded to avoid re-creation
    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder && decoder->getName() == coreName) {
            return decoder->getPlaybackCapabilities();
        }
    }

    // Create a temporary instance to query capabilities
    auto tempDecoder = DecoderRegistry::getInstance().createDecoderByName(coreName);
    if (tempDecoder) {
        return tempDecoder->getPlaybackCapabilities();
    }
    return 0;
}

int AudioEngine::getCoreFixedSampleRateHz(const std::string& coreName) {
    if (coreName.empty()) return 0;

    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder && decoder->getName() == coreName) {
            return decoder->getFixedSampleRateHz();
        }
    }

    auto tempDecoder = DecoderRegistry::getInstance().createDecoderByName(coreName);
    if (tempDecoder) {
        return tempDecoder->getFixedSampleRateHz();
    }
    return 0;
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
    updateRenderQueueTuning();
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

std::string AudioEngine::getComposer() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getComposer();
}

std::string AudioEngine::getGenre() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getGenre();
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

int AudioEngine::getSubtuneCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getSubtuneCount();
}

int AudioEngine::getCurrentSubtuneIndex() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getCurrentSubtuneIndex();
}

bool AudioEngine::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return false;
    }
    return decoder->selectSubtune(index);
}

std::string AudioEngine::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getSubtuneTitle(index);
}

std::string AudioEngine::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getSubtuneArtist(index);
}

double AudioEngine::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0.0;
    }
    return decoder->getSubtuneDurationSeconds(index);
}

int AudioEngine::getDecoderRenderSampleRateHz() const {
    return decoderRenderSampleRate;
}

int AudioEngine::getOutputStreamSampleRateHz() const {
    return streamSampleRate;
}

std::string AudioEngine::getOpenMptModuleTypeLong() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getModuleTypeLong() : "";
}

std::string AudioEngine::getOpenMptTracker() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getTracker() : "";
}

std::string AudioEngine::getOpenMptSongMessage() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getSongMessage() : "";
}

int AudioEngine::getOpenMptOrderCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getOrderCount() : 0;
}

int AudioEngine::getOpenMptPatternCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getPatternCount() : 0;
}

int AudioEngine::getOpenMptInstrumentCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getInstrumentCount() : 0;
}

int AudioEngine::getOpenMptSampleCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getSampleCount() : 0;
}

std::string AudioEngine::getOpenMptInstrumentNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getInstrumentNames() : "";
}

std::string AudioEngine::getOpenMptSampleNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getSampleNames() : "";
}

std::vector<float> AudioEngine::getOpenMptChannelVuLevels() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getCurrentChannelVuLevels() : std::vector<float>{};
}

std::vector<float> AudioEngine::getChannelScopeSamples(int samplesPerChannel) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getCurrentChannelScopeSamples(samplesPerChannel) : std::vector<float>{};
}

std::vector<int32_t> AudioEngine::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getChannelScopeTextState(maxChannels) : std::vector<int32_t>{};
}

std::string AudioEngine::getVgmGameName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getGameName() : "";
}

std::string AudioEngine::getVgmSystemName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getSystemName() : "";
}

std::string AudioEngine::getVgmReleaseDate() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getReleaseDate() : "";
}

std::string AudioEngine::getVgmEncodedBy() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getEncodedBy() : "";
}

std::string AudioEngine::getVgmNotes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getNotes() : "";
}

std::string AudioEngine::getVgmFileVersion() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getFileVersion() : "";
}

int AudioEngine::getVgmDeviceCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getDeviceCount() : 0;
}

std::string AudioEngine::getVgmUsedChipList() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getUsedChipList() : "";
}

bool AudioEngine::getVgmHasLoopPoint() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->hasLoopPoint() : false;
}

std::string AudioEngine::getFfmpegCodecName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    return ffmpegDecoder ? ffmpegDecoder->getCodecName() : "";
}

std::string AudioEngine::getFfmpegContainerName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    return ffmpegDecoder ? ffmpegDecoder->getContainerName() : "";
}

std::string AudioEngine::getFfmpegSampleFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    return ffmpegDecoder ? ffmpegDecoder->getSampleFormatName() : "";
}

std::string AudioEngine::getFfmpegChannelLayoutName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    return ffmpegDecoder ? ffmpegDecoder->getChannelLayoutName() : "";
}

std::string AudioEngine::getFfmpegEncoderName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    return ffmpegDecoder ? ffmpegDecoder->getEncoderName() : "";
}

std::string AudioEngine::getGmeSystemName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getSystemName() : "";
}

std::string AudioEngine::getGmeGameName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getGameName() : "";
}

std::string AudioEngine::getGmeCopyright() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getCopyright() : "";
}

std::string AudioEngine::getGmeComment() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getComment() : "";
}

std::string AudioEngine::getGmeDumper() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getDumper() : "";
}

int AudioEngine::getGmeTrackCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getTrackCountInfo() : 0;
}

int AudioEngine::getGmeVoiceCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getVoiceCountInfo() : 0;
}

bool AudioEngine::getGmeHasLoopPoint() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getHasLoopPointInfo() : false;
}

int AudioEngine::getGmeLoopStartMs() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getLoopStartMsInfo() : -1;
}

int AudioEngine::getGmeLoopLengthMs() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getLoopLengthMsInfo() : -1;
}

std::string AudioEngine::getLazyUsf2GameName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getGameName() : "";
}

std::string AudioEngine::getLazyUsf2Copyright() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getCopyright() : "";
}

std::string AudioEngine::getLazyUsf2Year() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getYear() : "";
}

std::string AudioEngine::getLazyUsf2UsfBy() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getUsfBy() : "";
}

std::string AudioEngine::getLazyUsf2LengthTag() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getLengthTag() : "";
}

std::string AudioEngine::getLazyUsf2FadeTag() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getFadeTag() : "";
}

bool AudioEngine::getLazyUsf2EnableCompare() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getEnableCompare() : false;
}

bool AudioEngine::getLazyUsf2EnableFifoFull() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getEnableFifoFull() : false;
}

std::string AudioEngine::getSidFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidFormatName() : "";
}

std::string AudioEngine::getSidClockName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidClockName() : "";
}

std::string AudioEngine::getSidSpeedName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidSpeedName() : "";
}

std::string AudioEngine::getSidCompatibilityName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidCompatibilityName() : "";
}

std::string AudioEngine::getSidBackendName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidBackendName() : "";
}

int AudioEngine::getSidChipCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidChipCountInfo() : 0;
}

std::string AudioEngine::getSidModelSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidModelSummary() : "";
}

std::string AudioEngine::getSidCurrentModelSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidCurrentModelSummary() : "";
}

std::string AudioEngine::getSidBaseAddressSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidBaseAddressSummary() : "";
}

std::string AudioEngine::getSidCommentSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidCommentSummary() : "";
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

// Convert dB to linear gain
float AudioEngine::dbToGain(float db) {
    return std::pow(10.0f, db / 20.0f);
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
    const float totalGain = masterGain * secondaryGain * std::clamp(extraGain, 0.0f, 1.0f);

    // Apply gain if not unity (1.0)
    if (totalGain != 1.0f) {
        const int totalSamples = numFrames * channels;
        for (int i = 0; i < totalSamples; i++) {
            buffer[i] *= totalGain;
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
    constexpr int kOutputSize = 256;
    const auto& history = channelIndex == 1 ? visualizationScopeHistoryRight : visualizationScopeHistoryLeft;
    const int historySize = static_cast<int>(history.size());
    if (historySize <= 0) {
        return std::vector<float>(kOutputSize, 0.0f);
    }

    const int sampleRate = std::max(streamSampleRate, 8000);
    const int clampedWindowMs = std::clamp(windowMs, 5, 200);
    int windowFrames = (sampleRate * clampedWindowMs) / 1000;
    windowFrames = std::clamp(windowFrames, kOutputSize, historySize - 1);

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
        const int frameOffset = static_cast<int>(std::round(i * scale));
        const int idx = (startIndex + frameOffset) % historySize;
        output[i] = std::clamp(history[idx], -1.0f, 1.0f);
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
