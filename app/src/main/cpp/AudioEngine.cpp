#include "AudioEngine.h"
#include "decoders/DecoderRegistry.h"
#include "decoders/DecoderRegistry.h"
#include "decoders/FFmpegDecoder.h"
#include "decoders/LibOpenMPTDecoder.h"
#include <android/log.h>
#include <cmath>
#include <cstring>

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
            decoder->setOutputSampleRate(resolveOutputSampleRateForCore(decoder->getName()));
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

#include "decoders/FFmpegDecoder.h"

// ... (other includes)

// ...

aaudio_data_callback_result_t AudioEngine::dataCallback(
        AAudioStream *stream,
        void *userData,
        void *audioData,
        int32_t numFrames) {
    auto *engine = static_cast<AudioEngine *>(userData);
    auto *outputData = static_cast<float *>(audioData);

    // output silence to avoid blocking the audio thread.
    std::unique_lock<std::mutex> lock(engine->decoderMutex, std::try_to_lock);

    if (lock.owns_lock() && engine->decoder) {
        const int channels = engine->decoder->getChannelCount();
        const int outputSampleRate = AAudioStream_getSampleRate(stream) > 0
                ? AAudioStream_getSampleRate(stream)
                : engine->streamSampleRate;
        int totalFramesRead = 0;
        int remainingFrames = numFrames;
        bool reachedEnd = false;

        while (remainingFrames > 0) {
            int framesRead = engine->decoder->read(
                    outputData + (totalFramesRead * channels),
                    remainingFrames
            );

            if (framesRead <= 0) {
                if (engine->looping.load()) {
                    engine->decoder->seek(0.0);
                    engine->positionSeconds.store(0.0);
                    continue;
                }
                reachedEnd = true;
                break;
            }

            totalFramesRead += framesRead;
            remainingFrames -= framesRead;
        }

        if (outputSampleRate > 0 && totalFramesRead > 0) {
            engine->positionSeconds.fetch_add(static_cast<double>(totalFramesRead) / outputSampleRate);
        }

        // Fill remaining with silence if decoder didn't produce enough
        if (totalFramesRead < numFrames) {
            memset(
                    outputData + (totalFramesRead * channels),
                    0,
                    (numFrames - totalFramesRead) * channels * sizeof(float)
            );
        }

        if (reachedEnd && !engine->looping.load()) {
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
                decoder->setOutputSampleRate(resolveOutputSampleRateForCore(decoder->getName()));
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
        decoder = std::move(newDecoder);
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
    positionSeconds.store(seconds < 0.0 ? 0.0 : seconds);
}

void AudioEngine::setLooping(bool enabled) {
    looping.store(enabled);
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
        decoder->setOutputSampleRate(resolveOutputSampleRateForCore(coreName));
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
