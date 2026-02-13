#include "AudioEngine.h"
#include <android/log.h>
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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

aaudio_data_callback_result_t AudioEngine::dataCallback(
        AAudioStream *stream,
        void *userData,
        void *audioData,
        int32_t numFrames) {
    auto *engine = static_cast<AudioEngine *>(userData);
    auto *outputData = static_cast<float *>(audioData);

    // Assuming 48000Hz sample rate (default usually, but good to check)
    // In a real app we'd get this from stream properties
    const float sampleRate = 48000.0f;
    const float frequency = 440.0f; // A4
    const float amplitude = 0.5f;

    for (int i = 0; i < numFrames; i++) {
        float sample = sinf(engine->phase) * amplitude;

        // Stereo output (interleaved)
        outputData[i * 2] = sample;     // Left
        outputData[i * 2 + 1] = sample; // Right

        engine->phase += 2.0f * M_PI * frequency / sampleRate;
        if (engine->phase > 2.0f * M_PI) {
            engine->phase -= 2.0f * M_PI;
        }
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
    // TODO: Initialize decoder with this URL
}

void AudioEngine::restart() {
    stop();
    start();
}
