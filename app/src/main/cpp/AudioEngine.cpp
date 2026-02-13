#include "AudioEngine.h"
#include "decoders/DecoderRegistry.h"
#include "decoders/DecoderRegistry.h"
#include "decoders/FFmpegDecoder.h"
#include "decoders/LibOpenMPTDecoder.h"
#include <android/log.h>
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    // Register decoders
    DecoderRegistry::getInstance().registerDecoder("FFmpeg", FFmpegDecoder::getSupportedExtensions(), []() {
        return std::make_unique<FFmpegDecoder>();
    }, 0);

    DecoderRegistry::getInstance().registerDecoder("LibOpenMPT", LibOpenMPTDecoder::getSupportedExtensions(), []() {
        return std::make_unique<LibOpenMPTDecoder>();
    }, 10); // Higher priority than FFmpeg for supported extensions

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

    // Open the stream
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to open stream: %s", AAudio_convertResultToText(result));
    }

    AAudioStreamBuilder_delete(builder);
}

void AudioEngine::closeStream() {
    if (stream != nullptr) {
        AAudioStream_close(stream);
        stream = nullptr;
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
        int framesRead = engine->decoder->read(outputData, numFrames);

        // Fill remaining with silence if decoder didn't produce enough
        if (framesRead < numFrames) {
             const int channels = 2; // Stereo
             memset(outputData + (framesRead * channels), 0, (numFrames - framesRead) * channels * sizeof(float));
        }
    } else {
        // Output silence
        memset(outputData, 0, numFrames * 2 * sizeof(float));
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

bool AudioEngine::start() {
    if (stream != nullptr) {
        aaudio_result_t result = AAudioStream_requestStart(stream);
        if (result != AAUDIO_OK) {
            LOGE("Failed to start stream: %s", AAudio_convertResultToText(result));
            return false;
        }
        isPlaying = true;
        return true;
    }
    return false;
}

void AudioEngine::stop() {
    if (stream != nullptr) {
        AAudioStream_requestStop(stream);
        isPlaying = false;
    }
}

void AudioEngine::setUrl(const char* url) {
    LOGD("URL set to: %s", url);

    auto newDecoder = DecoderRegistry::getInstance().createDecoder(url);
    if (newDecoder && newDecoder->open(url)) {
        std::lock_guard<std::mutex> lock(decoderMutex);
        decoder = std::move(newDecoder);
        if (!isPlaying) {
             start();
        }
    } else {
        LOGE("Failed to open file: %s", url);
    }
}

void AudioEngine::restart() {
    stop();
    start();
}
