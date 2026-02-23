#include "AudioEngine.h"

#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cstring>

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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
        activeOutputBackend.store(0, std::memory_order_relaxed);
        LOGE("Failed to open stream: %s", AAudio_convertResultToText(result));
    } else {
        activeOutputBackend.store(1, std::memory_order_relaxed);
        streamStartupPrerollPending = true;
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

bool AudioEngine::requestStreamStart() {
    if (stream == nullptr) {
        return false;
    }
    const aaudio_result_t result = AAudioStream_requestStart(stream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start stream: %s", AAudio_convertResultToText(result));
        return false;
    }
    return true;
}

void AudioEngine::requestStreamStop() {
    if (stream == nullptr) {
        return;
    }
    AAudioStream_requestStop(stream);
}

bool AudioEngine::isStreamDisconnectedOrClosed() const {
    if (stream == nullptr) {
        return true;
    }
    const aaudio_stream_state_t state = AAudioStream_getState(stream);
    return state == AAUDIO_STREAM_STATE_DISCONNECTED ||
           state == AAUDIO_STREAM_STATE_CLOSING ||
           state == AAUDIO_STREAM_STATE_CLOSED;
}

int AudioEngine::getStreamBurstFrames() const {
    if (stream == nullptr) {
        return 0;
    }
    return static_cast<int>(AAudioStream_getFramesPerBurst(stream));
}

std::string AudioEngine::getAudioBackendLabel() const {
    if (!isPlaying.load(std::memory_order_relaxed)) {
        return "(inactive)";
    }

    switch (activeOutputBackend.load(std::memory_order_relaxed)) {
        case 1:
            return "AAudio";
        case 2:
            return "OpenSL ES";
        case 3:
            return "AudioTrack";
        default:
            return "Unknown";
    }
}

void AudioEngine::reconfigureStream(bool resumePlayback) {
    const bool shouldResume = resumePlayback && isPlaying.load();
    requestStreamStop();
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

    if (shouldResume && requestStreamStart()) {
        isPlaying.store(true);
    }
}

void AudioEngine::closeStream() {
    if (stream != nullptr) {
        AAudioStream_close(stream);
        stream = nullptr;
    }
    activeOutputBackend.store(0, std::memory_order_relaxed);
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
        if (requestStreamStart()) {
            isPlaying.store(true);
        }
    }
}

aaudio_data_callback_result_t AudioEngine::dataCallback(
        AAudioStream *callbackStream,
        void *userData,
        void *audioData,
        int32_t numFrames) {
    auto *engine = static_cast<AudioEngine *>(userData);
    auto *outputData = static_cast<float *>(audioData);
    if (engine->seekInProgress.load()) {
        std::memset(outputData, 0, static_cast<size_t>(numFrames) * 2u * sizeof(float));
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    const int callbackRate = engine->streamSampleRate > 0
            ? engine->streamSampleRate
            : static_cast<int>(AAudioStream_getSampleRate(callbackStream));
    if (engine->pendingResumeFadeOnStart.exchange(false, std::memory_order_relaxed)) {
        engine->beginPauseResumeFadeLocked(
                true,
                callbackRate > 0 ? callbackRate : 48000,
                engine->pendingResumeFadeDurationMs.load(std::memory_order_relaxed),
                engine->pendingResumeFadeAttenuationDb.load(std::memory_order_relaxed)
        );
    }
    if (engine->pendingPauseFadeRequest.exchange(false, std::memory_order_relaxed)) {
        engine->beginPauseResumeFadeLocked(
                false,
                callbackRate > 0 ? callbackRate : 48000,
                engine->pendingPauseFadeDurationMs.load(std::memory_order_relaxed),
                engine->pendingPauseFadeAttenuationDb.load(std::memory_order_relaxed)
        );
    }

    engine->renderQueueCallbackCount.fetch_add(1, std::memory_order_relaxed);
    const int framesCopied = engine->popRenderQueue(outputData, numFrames, 2);
    if (framesCopied < numFrames) {
        const uint64_t missingFrames = static_cast<uint64_t>(numFrames - framesCopied);
        const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        // Hold a higher queue target briefly after underrun to absorb transient CPU spikes
        // during app-switch/system UI animations.
        engine->renderQueueRecoveryBoostUntilNs.store(
                nowNs + 2500000000LL,
                std::memory_order_relaxed
        );
        engine->renderQueueUnderrunCount.fetch_add(1, std::memory_order_relaxed);
        engine->renderQueueUnderrunFrames.fetch_add(missingFrames, std::memory_order_relaxed);
#ifndef NDEBUG
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

    for (int frame = 0; frame < numFrames; ++frame) {
        const float fadeGain = engine->nextPauseResumeFadeGainLocked();
        if (fadeGain == 1.0f) continue;
        const size_t base = static_cast<size_t>(frame) * 2u;
        outputData[base] *= fadeGain;
        outputData[base + 1u] *= fadeGain;
    }

    if (engine->pauseResumeFadeOutStopPending) {
        engine->pauseResumeFadeOutStopPending = false;
        engine->isPlaying.store(false);
        engine->naturalEndPending.store(false);
        engine->clearRenderQueue();
        engine->renderWorkerCv.notify_all();
        return AAUDIO_CALLBACK_RESULT_STOP;
    }

    if (engine->renderTerminalStopPending.load() && engine->renderQueueFrames() <= 0) {
        engine->renderTerminalStopPending.store(false);
        return AAUDIO_CALLBACK_RESULT_STOP;
    }

    engine->renderWorkerCv.notify_one();

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
