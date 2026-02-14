#include "AudioEngine.h"
#include "decoders/DecoderRegistry.h"
#include "decoders/DecoderRegistry.h"
#include "decoders/FFmpegDecoder.h"
#include "decoders/LibOpenMPTDecoder.h"
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// register decoders
namespace {
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
    closeStream();
}

void AudioEngine::createStream() {
    AAudioStreamBuilder *builder;
    AAudio_createStreamBuilder(&builder);

    // Set parameters
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);

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
        LOGD("AAudio stream opened: sampleRate=%d, channels=%d", streamSampleRate, streamChannelCount);
    }

    AAudioStreamBuilder_delete(builder);
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
            resetResamplerStateLocked();
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

void AudioEngine::resetResamplerStateLocked() {
    resampleInputBuffer.clear();
    resampleInputStartFrame = 0;
    resampleInputPosition = 0.0;
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

        const double decoderPosition = engine->decoder->getPlaybackPositionSeconds();
        if (decoderPosition >= 0.0) {
            engine->positionSeconds.store(decoderPosition);
        } else if (outputSampleRate > 0 && numFrames > 0 && !reachedEnd) {
            engine->positionSeconds.fetch_add(static_cast<double>(numFrames) / outputSampleRate);
        }

        if (reachedEnd && engine->repeatMode.load() != 1) {
            const double durationAtEnd = engine->decoder->getDuration();
            if (durationAtEnd > 0.0) {
                engine->positionSeconds.store(durationAtEnd);
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
        return true;
    }
    return false;
}

void AudioEngine::stop() {
    if (stream != nullptr) {
        resumeAfterRebuild.store(false);
        AAudioStream_requestStop(stream);
        isPlaying = false;
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
    positionSeconds.store(seconds < 0.0 ? 0.0 : seconds);
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
