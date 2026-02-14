#ifndef SILICONPLAYER_AUDIOENGINE_H
#define SILICONPLAYER_AUDIOENGINE_H

#include <aaudio/AAudio.h>
#include <thread>
#include <atomic>
#include <mutex>
#include <memory>
#include <vector>
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
    void setRepeatMode(int mode);
    int getRepeatModeCapabilities();
    void setCoreOutputSampleRate(const std::string& coreName, int sampleRateHz);
    void setCoreOption(const std::string& coreName, const std::string& optionName, const std::string& optionValue);
    std::string getTitle();
    std::string getArtist();
    int getSampleRate();
    int getDisplayChannelCount();
    int getChannelCount();
    int getBitDepth();
    std::string getBitDepthLabel();
    std::string getCurrentDecoderName();

private:
    AAudioStream *stream = nullptr;
    int streamSampleRate = 48000;
    int streamChannelCount = 2;
    std::atomic<bool> isPlaying { false };
    std::atomic<bool> looping { false };
    std::atomic<int> repeatMode { 0 };
    std::atomic<double> positionSeconds { 0.0 };

    std::unique_ptr<AudioDecoder> decoder;
    std::mutex decoderMutex;
    std::unordered_map<std::string, int> coreOutputSampleRateHz;
    std::unordered_map<std::string, std::unordered_map<std::string, std::string>> coreOptions;
    int decoderRenderSampleRate = 48000;
    std::vector<float> resampleInputBuffer;
    int resampleInputStartFrame = 0;
    double resampleInputPosition = 0.0;
    std::vector<float> resampleDecodeScratch;

    int resolveOutputSampleRateForCore(const std::string& coreName) const;
    void resetResamplerStateLocked();
    int readFromDecoderLocked(float* buffer, int numFrames, bool& reachedEnd);
    void renderResampledLocked(float* outputData, int32_t numFrames, int channels, int streamRate, bool& reachedEnd);
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
