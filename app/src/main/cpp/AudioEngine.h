#ifndef SILICONPLAYER_AUDIOENGINE_H
#define SILICONPLAYER_AUDIOENGINE_H

#include <aaudio/AAudio.h>
#include <thread>
#include <atomic>
#include <mutex>
#include <memory>
#include <unordered_map>
#include "decoders/AudioDecoder.h"

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool start();
    void stop();
    bool isEnginePlaying() const;
    void restart();
    void setUrl(const char* url);
    double getDurationSeconds();
    double getPositionSeconds();
    void seekToSeconds(double seconds);
    void setLooping(bool enabled);
    void setCoreOutputSampleRate(const std::string& coreName, int sampleRateHz);
    std::string getTitle();
    std::string getArtist();
    int getSampleRate();
    int getDisplayChannelCount();
    int getChannelCount();
    int getBitDepth();
    std::string getBitDepthLabel();

private:
    AAudioStream *stream = nullptr;
    int streamSampleRate = 48000;
    int streamChannelCount = 2;
    std::atomic<bool> isPlaying { false };
    std::atomic<bool> looping { false };
    std::atomic<double> positionSeconds { 0.0 };

    std::unique_ptr<AudioDecoder> decoder;
    std::mutex decoderMutex;
    std::unordered_map<std::string, int> coreOutputSampleRateHz;

    int resolveOutputSampleRateForCore(const std::string& coreName) const;
    void recoverStreamIfNeeded();

    void createStream();
    void closeStream();

    // Callback
    static aaudio_data_callback_result_t dataCallback(
            AAudioStream *stream,
            void *userData,
            void *audioData,
            int32_t numFrames);
    static void errorCallback(
            AAudioStream *stream,
            void *userData,
            aaudio_result_t error);

    float phase = 0.0f;
    std::atomic<bool> streamNeedsRebuild { false };
    std::atomic<bool> resumeAfterRebuild { false };
};

#endif //SILICONPLAYER_AUDIOENGINE_H
