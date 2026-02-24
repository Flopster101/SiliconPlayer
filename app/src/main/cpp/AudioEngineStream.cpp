#include "AudioEngine.h"
#include "AudioTrackJniBridge.h"

#include <android/log.h>
#include <android/api-level.h>
#include <algorithm>
#include <chrono>
#include <cstring>
#include <dlfcn.h>

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    constexpr int kOpenSlStartupReadyWaitColdMs = 90;
    constexpr int kOpenSlStartupReadyWaitFastMs = 40;
    constexpr int kOpenSlStartupStrictEnqueueWaitColdMs = 40;
    constexpr int kOpenSlStartupStrictEnqueueWaitFastMs = 16;
    constexpr int kOpenSlStartupPollIntervalMs = 2;
    constexpr int kOpenSlStartupMinQueuedBuffersCold = 2;
    constexpr int kOpenSlStartupMinQueuedBuffersFast = 1;
    constexpr int kAudioTrackStartupReadyWaitMs = 240;
    constexpr int kAudioTrackStartupPollIntervalMs = 2;

    int openSlBufferFramesForPreset(int bufferPreset) {
        switch (bufferPreset) {
            case 1:
                return 1024;
            case 3:
                return 4096;
            case 0:
            case 2:
            default:
                return 2048;
        }
    }

    int audioTrackBufferFramesForPreset(int bufferPreset) {
        switch (bufferPreset) {
            case 1:
                return 1024;
            case 3:
                return 4096;
            case 0:
            case 2:
            default:
                return 2048;
        }
    }

    bool openSlOk(SLresult result, const char* step) {
        if (result == SL_RESULT_SUCCESS) {
            return true;
        }
        LOGE("OpenSL step failed (%s): result=%d", step, static_cast<int>(result));
        return false;
    }

    namespace AAudioDyn {
        struct Api {
            void* handle = nullptr;
            bool attemptedLoad = false;
            bool available = false;
            aaudio_result_t (*createStreamBuilder)(AAudioStreamBuilder**) = nullptr;
            aaudio_result_t (*streamBuilderDelete)(AAudioStreamBuilder*) = nullptr;
            void (*streamBuilderSetFormat)(AAudioStreamBuilder*, aaudio_format_t) = nullptr;
            void (*streamBuilderSetChannelCount)(AAudioStreamBuilder*, int32_t) = nullptr;
            void (*streamBuilderSetPerformanceMode)(AAudioStreamBuilder*, aaudio_performance_mode_t) = nullptr;
            void (*streamBuilderSetDataCallback)(AAudioStreamBuilder*, AAudioStream_dataCallback, void*) = nullptr;
            void (*streamBuilderSetErrorCallback)(AAudioStreamBuilder*, AAudioStream_errorCallback, void*) = nullptr;
            aaudio_result_t (*streamBuilderOpenStream)(AAudioStreamBuilder*, AAudioStream**) = nullptr;
            const char* (*convertResultToText)(aaudio_result_t) = nullptr;
            int32_t (*streamGetSampleRate)(AAudioStream*) = nullptr;
            int32_t (*streamGetChannelCount)(AAudioStream*) = nullptr;
            int32_t (*streamGetFramesPerBurst)(AAudioStream*) = nullptr;
            int32_t (*streamGetBufferCapacityInFrames)(AAudioStream*) = nullptr;
            int32_t (*streamSetBufferSizeInFrames)(AAudioStream*, int32_t) = nullptr;
            aaudio_result_t (*streamRequestStart)(AAudioStream*) = nullptr;
            aaudio_result_t (*streamRequestStop)(AAudioStream*) = nullptr;
            aaudio_result_t (*streamClose)(AAudioStream*) = nullptr;
            aaudio_stream_state_t (*streamGetState)(AAudioStream*) = nullptr;
        };

        Api& api() {
            static Api instance;
            return instance;
        }

        template <typename T>
        bool loadSymbol(void* handle, const char* name, T& out) {
            out = reinterpret_cast<T>(dlsym(handle, name));
            return out != nullptr;
        }

        bool ensureLoaded() {
            Api& s = api();
            if (s.attemptedLoad) return s.available;
            s.attemptedLoad = true;

            if (android_get_device_api_level() < __ANDROID_API_O__) {
                return false;
            }

            s.handle = dlopen("libaaudio.so", RTLD_NOW | RTLD_LOCAL);
            if (s.handle == nullptr) {
                LOGD("AAudio runtime load skipped: libaaudio.so not available");
                return false;
            }

            const bool ok =
                    loadSymbol(s.handle, "AAudio_createStreamBuilder", s.createStreamBuilder) &&
                    loadSymbol(s.handle, "AAudioStreamBuilder_delete", s.streamBuilderDelete) &&
                    loadSymbol(s.handle, "AAudioStreamBuilder_setFormat", s.streamBuilderSetFormat) &&
                    loadSymbol(s.handle, "AAudioStreamBuilder_setChannelCount", s.streamBuilderSetChannelCount) &&
                    loadSymbol(s.handle, "AAudioStreamBuilder_setPerformanceMode", s.streamBuilderSetPerformanceMode) &&
                    loadSymbol(s.handle, "AAudioStreamBuilder_setDataCallback", s.streamBuilderSetDataCallback) &&
                    loadSymbol(s.handle, "AAudioStreamBuilder_setErrorCallback", s.streamBuilderSetErrorCallback) &&
                    loadSymbol(s.handle, "AAudioStreamBuilder_openStream", s.streamBuilderOpenStream) &&
                    loadSymbol(s.handle, "AAudio_convertResultToText", s.convertResultToText) &&
                    loadSymbol(s.handle, "AAudioStream_getSampleRate", s.streamGetSampleRate) &&
                    loadSymbol(s.handle, "AAudioStream_getChannelCount", s.streamGetChannelCount) &&
                    loadSymbol(s.handle, "AAudioStream_getFramesPerBurst", s.streamGetFramesPerBurst) &&
                    loadSymbol(s.handle, "AAudioStream_getBufferCapacityInFrames", s.streamGetBufferCapacityInFrames) &&
                    loadSymbol(s.handle, "AAudioStream_setBufferSizeInFrames", s.streamSetBufferSizeInFrames) &&
                    loadSymbol(s.handle, "AAudioStream_requestStart", s.streamRequestStart) &&
                    loadSymbol(s.handle, "AAudioStream_requestStop", s.streamRequestStop) &&
                    loadSymbol(s.handle, "AAudioStream_close", s.streamClose) &&
                    loadSymbol(s.handle, "AAudioStream_getState", s.streamGetState);

            if (!ok) {
                dlclose(s.handle);
                s.handle = nullptr;
                LOGE("AAudio runtime load failed: missing required symbols");
                return false;
            }

            s.available = true;
            return true;
        }

        const char* resultText(aaudio_result_t result) {
            Api& s = api();
            if (!s.available || s.convertResultToText == nullptr) return "AAudio unavailable";
            return s.convertResultToText(result);
        }
    }
}

bool AudioEngine::createAaudioStream() {
    if (!AAudioDyn::ensureLoaded()) {
        activeOutputBackend.store(0, std::memory_order_relaxed);
        outputStreamReady.store(false, std::memory_order_relaxed);
        return false;
    }
    auto& aaudio = AAudioDyn::api();

    AAudioStreamBuilder *builder;
    if (aaudio.createStreamBuilder(&builder) != AAUDIO_OK || builder == nullptr) {
        activeOutputBackend.store(0, std::memory_order_relaxed);
        outputStreamReady.store(false, std::memory_order_relaxed);
        LOGE("Failed to create AAudio stream builder");
        return false;
    }

    // Set parameters
    aaudio.streamBuilderSetFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    aaudio.streamBuilderSetChannelCount(builder, 2);
    const int configuredPerformanceMode = outputPerformanceMode;
    aaudio_performance_mode_t performanceMode = AAUDIO_PERFORMANCE_MODE_LOW_LATENCY;
    if (configuredPerformanceMode == 2) {
        performanceMode = AAUDIO_PERFORMANCE_MODE_NONE;
    } else if (configuredPerformanceMode == 3) {
        performanceMode = AAUDIO_PERFORMANCE_MODE_POWER_SAVING;
    }
    aaudio.streamBuilderSetPerformanceMode(builder, performanceMode);

    // Set callback
    aaudio.streamBuilderSetDataCallback(builder, dataCallback, this);
    aaudio.streamBuilderSetErrorCallback(builder, errorCallback, this);

    // Open the stream
    aaudio_result_t result = aaudio.streamBuilderOpenStream(builder, &stream);
    if (result != AAUDIO_OK) {
        activeOutputBackend.store(0, std::memory_order_relaxed);
        outputStreamReady.store(false, std::memory_order_relaxed);
        LOGE("Failed to open stream: %s", AAudioDyn::resultText(result));
    } else {
        activeOutputBackend.store(1, std::memory_order_relaxed);
        outputStreamReady.store(true, std::memory_order_relaxed);
        streamStartupPrerollPending = true;
        streamSampleRate = aaudio.streamGetSampleRate(stream);
        streamChannelCount = aaudio.streamGetChannelCount(stream);
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

    aaudio.streamBuilderDelete(builder);
    return outputStreamReady.load(std::memory_order_relaxed);
}

bool AudioEngine::createOpenSlStream() {
    streamSampleRate = streamSampleRate > 0 ? streamSampleRate : 48000;
    streamChannelCount = 2;
    openSlBufferFrames = openSlBufferFramesForPreset(outputBufferPreset);
    openSlFloatBuffer.assign(static_cast<size_t>(openSlBufferFrames) * 2u, 0.0f);
    for (auto& buffer : openSlPcmBuffers) {
        buffer.assign(static_cast<size_t>(openSlBufferFrames) * 2u, 0);
    }
    openSlNextBufferIndex = 0;
    openSlStopAfterCurrentBuffer.store(false, std::memory_order_relaxed);

    if (!openSlOk(slCreateEngine(&openSlEngineObject, 0, nullptr, 0, nullptr, nullptr), "slCreateEngine")) {
        closeOpenSlStream();
        return false;
    }
    if (!openSlOk((*openSlEngineObject)->Realize(openSlEngineObject, SL_BOOLEAN_FALSE), "Realize(engine)")) {
        closeOpenSlStream();
        return false;
    }
    if (!openSlOk((*openSlEngineObject)->GetInterface(openSlEngineObject, SL_IID_ENGINE, &openSlEngine), "GetInterface(engine)")) {
        closeOpenSlStream();
        return false;
    }

    if (!openSlOk((*openSlEngine)->CreateOutputMix(openSlEngine, &openSlOutputMixObject, 0, nullptr, nullptr), "CreateOutputMix")) {
        closeOpenSlStream();
        return false;
    }
    if (!openSlOk((*openSlOutputMixObject)->Realize(openSlOutputMixObject, SL_BOOLEAN_FALSE), "Realize(outputMix)")) {
        closeOpenSlStream();
        return false;
    }

    SLDataLocator_AndroidSimpleBufferQueue bufferQueueLocator {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
            static_cast<SLuint32>(kOpenSlBufferQueueCount)
    };
    SLDataFormat_PCM formatPcm {
            SL_DATAFORMAT_PCM,
            2,
            static_cast<SLuint32>(streamSampleRate * 1000),
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
            SL_BYTEORDER_LITTLEENDIAN
    };
    SLDataSource dataSource { &bufferQueueLocator, &formatPcm };

    SLDataLocator_OutputMix outputMixLocator { SL_DATALOCATOR_OUTPUTMIX, openSlOutputMixObject };
    SLDataSink dataSink { &outputMixLocator, nullptr };

    const SLInterfaceID interfaceIds[] = { SL_IID_ANDROIDSIMPLEBUFFERQUEUE };
    const SLboolean interfaceRequired[] = { SL_BOOLEAN_TRUE };

    if (!openSlOk(
            (*openSlEngine)->CreateAudioPlayer(
                    openSlEngine,
                    &openSlPlayerObject,
                    &dataSource,
                    &dataSink,
                    1,
                    interfaceIds,
                    interfaceRequired
            ),
            "CreateAudioPlayer"
    )) {
        closeOpenSlStream();
        return false;
    }
    if (!openSlOk((*openSlPlayerObject)->Realize(openSlPlayerObject, SL_BOOLEAN_FALSE), "Realize(player)")) {
        closeOpenSlStream();
        return false;
    }
    if (!openSlOk((*openSlPlayerObject)->GetInterface(openSlPlayerObject, SL_IID_PLAY, &openSlPlayerPlay), "GetInterface(play)")) {
        closeOpenSlStream();
        return false;
    }
    if (!openSlOk(
            (*openSlPlayerObject)->GetInterface(
                    openSlPlayerObject,
                    SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
                    &openSlBufferQueue
            ),
            "GetInterface(bufferQueue)"
    )) {
        closeOpenSlStream();
        return false;
    }
    if (!openSlOk(
            (*openSlBufferQueue)->RegisterCallback(openSlBufferQueue, openSlBufferQueueCallback, this),
            "RegisterCallback(bufferQueue)"
    )) {
        closeOpenSlStream();
        return false;
    }

    (*openSlPlayerPlay)->SetPlayState(openSlPlayerPlay, SL_PLAYSTATE_STOPPED);
    activeOutputBackend.store(2, std::memory_order_relaxed);
    outputStreamReady.store(true, std::memory_order_relaxed);
    streamStartupPrerollPending = true;
    LOGD(
            "OpenSL stream opened: sampleRate=%d, channels=%d, backendPref=%d, bufferPreset=%d, frames=%d, allowFallback=%d",
            streamSampleRate,
            streamChannelCount,
            outputBackendPreference,
            outputBufferPreset,
            openSlBufferFrames,
            outputAllowFallback ? 1 : 0
    );
    return true;
}

bool AudioEngine::createAudioTrackStream() {
    streamSampleRate = streamSampleRate > 0 ? streamSampleRate : 48000;
    streamChannelCount = 2;
    audioTrackBufferFrames = audioTrackBufferFramesForPreset(outputBufferPreset);
    audioTrackFloatBuffer.assign(static_cast<size_t>(audioTrackBufferFrames) * 2u, 0.0f);
    audioTrackPcmBuffer.assign(static_cast<size_t>(audioTrackBufferFrames) * 2u, 0);
    audioTrackStopRequested.store(false, std::memory_order_relaxed);

    if (!createAudioTrackOutput(
            streamSampleRate,
            audioTrackBufferFrames,
            outputPerformanceMode,
            outputBufferPreset
    )) {
        closeAudioTrackStream();
        LOGE("AudioTrack output creation failed");
        return false;
    }

    activeOutputBackend.store(3, std::memory_order_relaxed);
    outputStreamReady.store(true, std::memory_order_relaxed);
    streamStartupPrerollPending = true;
    LOGD(
            "AudioTrack stream opened: sampleRate=%d, channels=%d, backendPref=%d, perfMode=%d, bufferPreset=%d, frames=%d, allowFallback=%d",
            streamSampleRate,
            streamChannelCount,
            outputBackendPreference,
            outputPerformanceMode,
            outputBufferPreset,
            audioTrackBufferFrames,
            outputAllowFallback ? 1 : 0
    );
    return true;
}

void AudioEngine::createStream() {
    closeStream();

    auto tryBackend = [this](int backend) -> bool {
        if (backend == 1) {
            return createAaudioStream();
        }
        if (backend == 2) {
            return createOpenSlStream();
        }
        if (backend == 3) {
            return createAudioTrackStream();
        }
        return false;
    };

    std::array<int, 3> attempts {};
    int attemptCount = 0;
    auto addAttempt = [&](int backend) {
        if (backend <= 0) return;
        for (int i = 0; i < attemptCount; ++i) {
            if (attempts[i] == backend) return;
        }
        attempts[attemptCount++] = backend;
    };

    switch (outputBackendPreference) {
        case 1:
            addAttempt(1);
            if (outputAllowFallback) {
                addAttempt(2);
                addAttempt(3);
            }
            break;
        case 2:
            addAttempt(2);
            if (outputAllowFallback) {
                addAttempt(1);
                addAttempt(3);
            }
            break;
        case 3:
            addAttempt(3);
            if (outputAllowFallback) {
                addAttempt(2);
                addAttempt(1);
            }
            break;
        case 0:
        default:
            // Auto: prefer AAudio, then OpenSL, then AudioTrack fallback.
            addAttempt(1);
            if (outputAllowFallback) {
                addAttempt(2);
                addAttempt(3);
            }
            break;
    }

    for (int i = 0; i < attemptCount; ++i) {
        if (tryBackend(attempts[i])) {
            return;
        }
    }

    activeOutputBackend.store(0, std::memory_order_relaxed);
    outputStreamReady.store(false, std::memory_order_relaxed);
    LOGE("No audio backend could be created (pref=%d allowFallback=%d)", outputBackendPreference, outputAllowFallback ? 1 : 0);
}

void AudioEngine::applyStreamBufferPreset() {
    const int backend = activeOutputBackend.load(std::memory_order_relaxed);
    if (backend == 2) {
        openSlBufferFrames = openSlBufferFramesForPreset(outputBufferPreset);
        openSlFloatBuffer.assign(static_cast<size_t>(openSlBufferFrames) * 2u, 0.0f);
        for (auto& buffer : openSlPcmBuffers) {
            buffer.assign(static_cast<size_t>(openSlBufferFrames) * 2u, 0);
        }
        LOGD("OpenSL buffer preset applied: frames=%d", openSlBufferFrames);
        return;
    }
    if (backend == 3) {
        audioTrackBufferFrames = audioTrackBufferFramesForPreset(outputBufferPreset);
        audioTrackFloatBuffer.assign(static_cast<size_t>(audioTrackBufferFrames) * 2u, 0.0f);
        audioTrackPcmBuffer.assign(static_cast<size_t>(audioTrackBufferFrames) * 2u, 0);
        LOGD("AudioTrack buffer preset applied: frames=%d", audioTrackBufferFrames);
        return;
    }

    if (stream == nullptr) return;
    if (outputBufferPreset == 0) return;

    if (!AAudioDyn::ensureLoaded()) return;
    auto& aaudio = AAudioDyn::api();
    const int32_t burstFrames = aaudio.streamGetFramesPerBurst(stream);
    const int32_t bufferCapacity = aaudio.streamGetBufferCapacityInFrames(stream);
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
    const int32_t applied = aaudio.streamSetBufferSizeInFrames(stream, target);
    LOGD(
            "AAudio buffer preset applied: burst=%d capacity=%d target=%d applied=%d",
            burstFrames,
            bufferCapacity,
            target,
            applied
    );
}

bool AudioEngine::renderOutputCallbackFrames(float* outputData, int32_t numFrames, int callbackRate) {
    if (!outputData || numFrames <= 0) {
        return false;
    }

    if (seekInProgress.load()) {
        std::memset(outputData, 0, static_cast<size_t>(numFrames) * 2u * sizeof(float));
        return false;
    }

    if (pendingResumeFadeOnStart.exchange(false, std::memory_order_relaxed)) {
        beginPauseResumeFadeLocked(
                true,
                callbackRate > 0 ? callbackRate : 48000,
                pendingResumeFadeDurationMs.load(std::memory_order_relaxed),
                pendingResumeFadeAttenuationDb.load(std::memory_order_relaxed)
        );
    }
    if (pendingPauseFadeRequest.exchange(false, std::memory_order_relaxed)) {
        beginPauseResumeFadeLocked(
                false,
                callbackRate > 0 ? callbackRate : 48000,
                pendingPauseFadeDurationMs.load(std::memory_order_relaxed),
                pendingPauseFadeAttenuationDb.load(std::memory_order_relaxed)
        );
    }

    renderQueueCallbackCount.fetch_add(1, std::memory_order_relaxed);
    const int framesCopied = popRenderQueue(outputData, numFrames, 2);
    if (framesCopied < numFrames) {
        const uint64_t missingFrames = static_cast<uint64_t>(numFrames - framesCopied);
        const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        // Hold a higher queue target briefly after underrun to absorb transient CPU spikes
        // during app-switch/system UI animations.
        renderQueueRecoveryBoostUntilNs.store(
                nowNs + 2500000000LL,
                std::memory_order_relaxed
        );
        renderQueueUnderrunCount.fetch_add(1, std::memory_order_relaxed);
        renderQueueUnderrunFrames.fetch_add(missingFrames, std::memory_order_relaxed);
#ifndef NDEBUG
        const int64_t previousLogNs = renderQueueLastUnderrunLogNs.load(std::memory_order_relaxed);
        if (nowNs - previousLogNs > 1000000000LL) {
            const uint64_t underruns = renderQueueUnderrunCount.load(std::memory_order_relaxed);
            const uint64_t underrunFrames = renderQueueUnderrunFrames.load(std::memory_order_relaxed);
            const uint64_t callbacks = renderQueueCallbackCount.load(std::memory_order_relaxed);
            LOGD(
                    "Render queue underrun: missing=%llu callbacks=%llu underruns=%llu totalMissingFrames=%llu bufferedFrames=%d",
                    static_cast<unsigned long long>(missingFrames),
                    static_cast<unsigned long long>(callbacks),
                    static_cast<unsigned long long>(underruns),
                    static_cast<unsigned long long>(underrunFrames),
                    renderQueueFrames()
            );
            renderQueueLastUnderrunLogNs.store(nowNs, std::memory_order_relaxed);
        }
#endif
        std::memset(
                outputData + (static_cast<size_t>(framesCopied) * 2u),
                0,
                static_cast<size_t>(numFrames - framesCopied) * 2u * sizeof(float)
        );
    }

    for (int frame = 0; frame < numFrames; ++frame) {
        const float fadeGain = nextPauseResumeFadeGainLocked();
        if (fadeGain == 1.0f) continue;
        const size_t base = static_cast<size_t>(frame) * 2u;
        outputData[base] *= fadeGain;
        outputData[base + 1u] *= fadeGain;
    }
    if (shouldUpdateVisualization()) {
        updateVisualizationDataFromOutputCallback(outputData, numFrames, 2);
    }

    if (pauseResumeFadeOutStopPending) {
        pauseResumeFadeOutStopPending = false;
        isPlaying.store(false);
        naturalEndPending.store(false);
        clearRenderQueue();
        renderWorkerCv.notify_all();
        return true;
    }

    if (renderTerminalStopPending.load() && renderQueueFrames() <= 0) {
        renderTerminalStopPending.store(false);
        return true;
    }

    renderWorkerCv.notify_one();
    return false;
}

bool AudioEngine::enqueueOpenSlBuffer(bool allowUnderrun) {
    if (openSlBufferQueue == nullptr || openSlBufferFrames <= 0) {
        return false;
    }

    if (!allowUnderrun) {
        const bool fastStartup = openSlStartupProfile.load(std::memory_order_relaxed) == 1;
        const int strictWaitMs = fastStartup ? kOpenSlStartupStrictEnqueueWaitFastMs : kOpenSlStartupStrictEnqueueWaitColdMs;
        int bufferedFrames = renderQueueFrames();
        const auto strictDeadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(
                strictWaitMs
        );
        while (bufferedFrames < openSlBufferFrames &&
               std::chrono::steady_clock::now() < strictDeadline) {
            renderWorkerCv.notify_one();
            std::this_thread::sleep_for(std::chrono::milliseconds(kOpenSlStartupPollIntervalMs));
            bufferedFrames = renderQueueFrames();
        }
        if (bufferedFrames < openSlBufferFrames) {
            LOGD(
                    "OpenSL strict startup enqueue deferred: required=%d buffered=%d",
                    openSlBufferFrames,
                    bufferedFrames
            );
            return false;
        }
    }

    const size_t nextIndex = openSlNextBufferIndex % kOpenSlBufferQueueCount;
    openSlNextBufferIndex++;

    auto& pcmBuffer = openSlPcmBuffers[nextIndex];
    const size_t sampleCount = static_cast<size_t>(openSlBufferFrames) * 2u;
    if (pcmBuffer.size() != sampleCount) {
        pcmBuffer.assign(sampleCount, 0);
    }
    if (openSlFloatBuffer.size() != sampleCount) {
        openSlFloatBuffer.assign(sampleCount, 0.0f);
    }

    const bool shouldStop = renderOutputCallbackFrames(
            openSlFloatBuffer.data(),
            openSlBufferFrames,
            streamSampleRate > 0 ? streamSampleRate : 48000
    );

    for (size_t i = 0; i < sampleCount; ++i) {
        const float clamped = std::clamp(openSlFloatBuffer[i], -1.0f, 1.0f);
        pcmBuffer[i] = static_cast<int16_t>(clamped * 32767.0f);
    }

    const SLresult enqueueResult = (*openSlBufferQueue)->Enqueue(
            openSlBufferQueue,
            pcmBuffer.data(),
            static_cast<SLuint32>(pcmBuffer.size() * sizeof(int16_t))
    );
    if (!openSlOk(enqueueResult, "Enqueue")) {
        return false;
    }

    if (shouldStop) {
        openSlStopAfterCurrentBuffer.store(true, std::memory_order_relaxed);
    }

    return true;
}

bool AudioEngine::requestStreamStart() {
    const int backend = activeOutputBackend.load(std::memory_order_relaxed);
    if (backend == 2) {
        if (!outputStreamReady.load(std::memory_order_relaxed) ||
            openSlPlayerPlay == nullptr ||
            openSlBufferQueue == nullptr) {
            return false;
        }

        const bool fastStartup = openSlStartupProfile.load(std::memory_order_relaxed) == 1;
        const int minQueuedBuffers = fastStartup ? kOpenSlStartupMinQueuedBuffersFast : kOpenSlStartupMinQueuedBuffersCold;
        const int readyWaitMs = fastStartup ? kOpenSlStartupReadyWaitFastMs : kOpenSlStartupReadyWaitColdMs;
        const int minStartupFrames = std::max(
                openSlBufferFrames * minQueuedBuffers,
                renderWorkerChunkFrames.load(std::memory_order_relaxed) * 2
        );
        const auto readyDeadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(
                readyWaitMs
        );
        while (renderQueueFrames() < minStartupFrames &&
               std::chrono::steady_clock::now() < readyDeadline) {
            renderWorkerCv.notify_one();
            std::this_thread::sleep_for(std::chrono::milliseconds(kOpenSlStartupPollIntervalMs));
        }

        openSlStopAfterCurrentBuffer.store(false, std::memory_order_relaxed);
        openSlNextBufferIndex = 0;
        (*openSlBufferQueue)->Clear(openSlBufferQueue);

        int queued = 0;
        for (int i = 0; i < minQueuedBuffers; ++i) {
            if (!enqueueOpenSlBuffer(false)) {
                break;
            }
            queued++;
        }
        if (queued < minQueuedBuffers) {
            const int bufferedFrames = renderQueueFrames();
            LOGE(
                    "OpenSL startup prequeue incomplete: queued=%d/%d buffered=%d",
                    queued,
                    minQueuedBuffers,
                    bufferedFrames
            );
            return false;
        }

        if (!openSlOk((*openSlPlayerPlay)->SetPlayState(openSlPlayerPlay, SL_PLAYSTATE_PLAYING), "SetPlayState(PLAYING)")) {
            return false;
        }
        return true;
    }
    if (backend == 3) {
        if (!outputStreamReady.load(std::memory_order_relaxed)) {
            return false;
        }

        const int minStartupFrames = std::max(
                audioTrackBufferFrames * 2,
                renderWorkerChunkFrames.load(std::memory_order_relaxed) * 2
        );
        const auto readyDeadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(
                kAudioTrackStartupReadyWaitMs
        );
        while (renderQueueFrames() < minStartupFrames &&
               std::chrono::steady_clock::now() < readyDeadline) {
            renderWorkerCv.notify_one();
            std::this_thread::sleep_for(std::chrono::milliseconds(kAudioTrackStartupPollIntervalMs));
        }

        audioTrackStopRequested.store(false, std::memory_order_relaxed);
        if (!startAudioTrackOutput()) {
            LOGE("AudioTrack start request failed");
            return false;
        }

        if (audioTrackWriteThread.joinable()) {
            audioTrackWriteThread.join();
        }
        audioTrackWriteThread = std::thread([this]() { audioTrackRenderLoop(); });
        return true;
    }

    if (stream == nullptr) {
        return false;
    }
    if (!AAudioDyn::ensureLoaded()) {
        return false;
    }
    const aaudio_result_t result = AAudioDyn::api().streamRequestStart(stream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start stream: %s", AAudioDyn::resultText(result));
        return false;
    }
    return true;
}

void AudioEngine::requestStreamStop() {
    const int backend = activeOutputBackend.load(std::memory_order_relaxed);
    if (backend == 2) {
        openSlStopAfterCurrentBuffer.store(false, std::memory_order_relaxed);
        if (openSlPlayerPlay != nullptr) {
            (*openSlPlayerPlay)->SetPlayState(openSlPlayerPlay, SL_PLAYSTATE_STOPPED);
        }
        if (openSlBufferQueue != nullptr) {
            (*openSlBufferQueue)->Clear(openSlBufferQueue);
        }
        return;
    }
    if (backend == 3) {
        audioTrackStopRequested.store(true, std::memory_order_relaxed);
        stopAudioTrackOutput();
        if (audioTrackWriteThread.joinable()) {
            audioTrackWriteThread.join();
        }
        return;
    }

    if (stream == nullptr) {
        return;
    }
    if (!AAudioDyn::ensureLoaded()) {
        return;
    }
    AAudioDyn::api().streamRequestStop(stream);
}

bool AudioEngine::isStreamDisconnectedOrClosed() const {
    if (!outputStreamReady.load(std::memory_order_relaxed)) {
        return true;
    }

    const int backend = activeOutputBackend.load(std::memory_order_relaxed);
    if (backend == 2) {
        return openSlPlayerObject == nullptr || openSlPlayerPlay == nullptr || openSlBufferQueue == nullptr;
    }
    if (backend == 3) {
        if (!outputStreamReady.load(std::memory_order_relaxed)) {
            return true;
        }
        return isPlaying.load(std::memory_order_relaxed) && !audioTrackWriteThread.joinable();
    }

    if (stream == nullptr) {
        return true;
    }
    if (!AAudioDyn::ensureLoaded()) {
        return true;
    }
    const aaudio_stream_state_t state = AAudioDyn::api().streamGetState(stream);
    return state == AAUDIO_STREAM_STATE_DISCONNECTED ||
           state == AAUDIO_STREAM_STATE_CLOSING ||
           state == AAUDIO_STREAM_STATE_CLOSED;
}

int AudioEngine::getStreamBurstFrames() const {
    const int backend = activeOutputBackend.load(std::memory_order_relaxed);
    if (backend == 2) {
        return openSlBufferFrames;
    }
    if (backend == 3) {
        return audioTrackBufferFrames;
    }

    if (stream == nullptr) {
        return 0;
    }
    if (!AAudioDyn::ensureLoaded()) {
        return 0;
    }
    return static_cast<int>(AAudioDyn::api().streamGetFramesPerBurst(stream));
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

    if (!shouldResume) {
        return;
    }

    if (activeOutputBackend.load(std::memory_order_relaxed) == 2) {
        // Backend reconfigure while playing should stay responsive.
        openSlStartupProfile.store(1, std::memory_order_relaxed);
    }

    naturalEndPending.store(false);
    isPlaying.store(true);
    renderWorkerCv.notify_all();

    if (requestStreamStart()) {
        streamStartupPrerollPending = false;
        renderWorkerCv.notify_all();
        return;
    }

    LOGE("Reconfigure resume start failed");
    isPlaying.store(false);
}

void AudioEngine::closeAaudioStream() {
    if (stream != nullptr) {
        if (AAudioDyn::ensureLoaded()) {
            AAudioDyn::api().streamClose(stream);
        }
        stream = nullptr;
    }
}

void AudioEngine::closeOpenSlStream() {
    openSlStopAfterCurrentBuffer.store(false, std::memory_order_relaxed);

    if (openSlPlayerObject != nullptr) {
        (*openSlPlayerObject)->Destroy(openSlPlayerObject);
        openSlPlayerObject = nullptr;
    }
    openSlPlayerPlay = nullptr;
    openSlBufferQueue = nullptr;

    if (openSlOutputMixObject != nullptr) {
        (*openSlOutputMixObject)->Destroy(openSlOutputMixObject);
        openSlOutputMixObject = nullptr;
    }

    if (openSlEngineObject != nullptr) {
        (*openSlEngineObject)->Destroy(openSlEngineObject);
        openSlEngineObject = nullptr;
    }
    openSlEngine = nullptr;

    for (auto& buffer : openSlPcmBuffers) {
        buffer.clear();
    }
    openSlFloatBuffer.clear();
    openSlNextBufferIndex = 0;
}

void AudioEngine::audioTrackRenderLoop() {
    int callbackRate = streamSampleRate > 0 ? streamSampleRate : 48000;
    int callbackFrames = std::max(256, audioTrackBufferFrames);
    const size_t sampleCount = static_cast<size_t>(callbackFrames) * 2u;
    if (audioTrackFloatBuffer.size() != sampleCount) {
        audioTrackFloatBuffer.assign(sampleCount, 0.0f);
    }
    if (audioTrackPcmBuffer.size() != sampleCount) {
        audioTrackPcmBuffer.assign(sampleCount, 0);
    }

    while (!audioTrackStopRequested.load(std::memory_order_relaxed)) {
        const bool shouldStop = renderOutputCallbackFrames(
                audioTrackFloatBuffer.data(),
                callbackFrames,
                callbackRate
        );
        for (size_t i = 0; i < sampleCount; ++i) {
            const float clamped = std::clamp(audioTrackFloatBuffer[i], -1.0f, 1.0f);
            audioTrackPcmBuffer[i] = static_cast<int16_t>(clamped * 32767.0f);
        }

        if (!writeAudioTrackOutput(audioTrackPcmBuffer.data(), static_cast<int>(sampleCount))) {
            LOGE("AudioTrack write failed");
            break;
        }

        if (shouldStop) {
            audioTrackStopRequested.store(true, std::memory_order_relaxed);
            if (callbackRate > 0) {
                const auto drainMs = std::max(1, (callbackFrames * 1000) / callbackRate);
                std::this_thread::sleep_for(std::chrono::milliseconds(drainMs));
            }
            break;
        }
    }

    stopAudioTrackOutput();
}

void AudioEngine::closeAudioTrackStream() {
    audioTrackStopRequested.store(true, std::memory_order_relaxed);
    stopAudioTrackOutput();
    if (audioTrackWriteThread.joinable()) {
        audioTrackWriteThread.join();
    }
    releaseAudioTrackOutput();
    audioTrackFloatBuffer.clear();
    audioTrackPcmBuffer.clear();
}

void AudioEngine::closeStream() {
    closeAaudioStream();
    closeOpenSlStream();
    closeAudioTrackStream();
    activeOutputBackend.store(0, std::memory_order_relaxed);
    outputStreamReady.store(false, std::memory_order_relaxed);
}

void AudioEngine::errorCallback(
        AAudioStream * /*stream*/,
        void *userData,
        aaudio_result_t error) {
    auto *engine = static_cast<AudioEngine *>(userData);
    LOGE("AAudio stream error callback: %s", AAudioDyn::resultText(error));
    engine->resumeAfterRebuild.store(engine->isPlaying.load());
    engine->isPlaying.store(false);
    engine->streamNeedsRebuild.store(true);
}

void AudioEngine::recoverStreamIfNeeded() {
    if (!streamNeedsRebuild.load()) {
        return;
    }

    if (activeOutputBackend.load(std::memory_order_relaxed) != 1) {
        streamNeedsRebuild.store(false);
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
    const int callbackStreamRate = AAudioDyn::ensureLoaded()
            ? static_cast<int>(AAudioDyn::api().streamGetSampleRate(callbackStream))
            : 0;
    auto *outputData = static_cast<float *>(audioData);
    const int callbackRate = engine->streamSampleRate > 0
            ? engine->streamSampleRate
            : callbackStreamRate;

    if (engine->renderOutputCallbackFrames(outputData, numFrames, callbackRate)) {
        return AAUDIO_CALLBACK_RESULT_STOP;
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AudioEngine::openSlBufferQueueCallback(SLAndroidSimpleBufferQueueItf /*bufferQueue*/, void *context) {
    auto* engine = static_cast<AudioEngine*>(context);
    if (engine == nullptr) {
        return;
    }

    if (engine->openSlStopAfterCurrentBuffer.exchange(false, std::memory_order_relaxed)) {
        engine->requestStreamStop();
        return;
    }

    if (!engine->enqueueOpenSlBuffer()) {
        engine->requestStreamStop();
    }
}
