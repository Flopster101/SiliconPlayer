#ifndef SILICONPLAYER_AUDIOENGINE_H
#define SILICONPLAYER_AUDIOENGINE_H

#include <aaudio/AAudio.h>
#include <thread>
#include <atomic>

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool start();
    void stop();
    void restart();
    void setUrl(const char* url);

private:
    AAudioStream *stream = nullptr;
    std::atomic<bool> isPlaying { false };

    void createStream();
    void closeStream();

    // Callback
    static aaudio_data_callback_result_t dataCallback(
            AAudioStream *stream,
            void *userData,
            void *audioData,
            int32_t numFrames);

    float phase = 0.0f;
};

#endif //SILICONPLAYER_AUDIOENGINE_H
