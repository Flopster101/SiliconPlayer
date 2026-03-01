#include "AudioEngine.h"

#include <android/log.h>

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
    const char* outputResamplerName(int preference) {
        return preference == 2 ? "SoX" : "Built-in";
    }

    constexpr int kRenderChunkFramesVerySmall = 256;
    constexpr int kRenderChunkFramesSmall = 512;
    constexpr int kRenderChunkFramesMedium = 1024;
    constexpr int kRenderChunkFramesLarge = 2048;
    constexpr int kRenderTargetFramesVerySmall = 2048;
    constexpr int kRenderTargetFramesSmall = 4096;
    constexpr int kRenderTargetFramesMedium = 8192;
    constexpr int kRenderTargetFramesLarge = 16384;
}

void AudioEngine::setAudioPipelineConfig(
        int backendPreference,
        int performanceMode,
        int bufferPreset,
        int resamplerPreference,
        bool allowFallback) {
    const int normalizedBackend = (backendPreference >= 0 && backendPreference <= 3) ? backendPreference : 0;
    const int normalizedPerformance = (performanceMode >= 0 && performanceMode <= 3) ? performanceMode : 1;
    const int normalizedBufferPreset = (bufferPreset >= 0 && bufferPreset <= 3) ? bufferPreset : 1;
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

void AudioEngine::updateRenderQueueTuning() {
    int chunkFrames = kRenderChunkFramesSmall;
    int targetFrames = kRenderTargetFramesSmall;
    switch (outputBufferPreset) {
        case 0:
            chunkFrames = kRenderChunkFramesVerySmall;
            targetFrames = kRenderTargetFramesVerySmall;
            break;
        case 1:
            chunkFrames = kRenderChunkFramesSmall;
            targetFrames = kRenderTargetFramesSmall;
            break;
        case 3:
            chunkFrames = kRenderChunkFramesLarge;
            targetFrames = kRenderTargetFramesLarge;
            break;
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
