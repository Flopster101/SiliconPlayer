#ifndef SILICONPLAYER_AUDIOENGINE_H
#define SILICONPLAYER_AUDIOENGINE_H

#include <aaudio/AAudio.h>
#include <thread>
#include <atomic>
#include <mutex>
#include <memory>
#include "decoders/AudioDecoder.h"

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool start();
    void stop();
    void restart();
    void setUrl(const char* url);
    double getDurationSeconds();
    double getPositionSeconds();
    void seekToSeconds(double seconds);
    void setLooping(bool enabled);
    std::string getTitle();
    std::string getArtist();
    int getSampleRate();
    int getDisplayChannelCount();
    int getChannelCount();
    int getBitDepth();
    std::string getBitDepthLabel();

private:
    AAudioStream *stream = nullptr;
    std::atomic<bool> isPlaying { false };
    std::atomic<bool> looping { false };
    std::atomic<double> positionSeconds { 0.0 };

    std::unique_ptr<AudioDecoder> decoder;
    std::mutex decoderMutex;

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
