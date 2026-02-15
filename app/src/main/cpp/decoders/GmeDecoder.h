#ifndef SILICONPLAYER_GMEDECODER_H
#define SILICONPLAYER_GMEDECODER_H

#include "AudioDecoder.h"
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

struct Music_Emu;

class GmeDecoder : public AudioDecoder {
public:
    GmeDecoder();
    ~GmeDecoder() override;

    bool open(const char* path) override;
    void close() override;
    int read(float* buffer, int numFrames) override;
    void seek(double seconds) override;
    double getDuration() override;
    int getSampleRate() override;
    int getBitDepth() override;
    std::string getBitDepthLabel() override;
    int getDisplayChannelCount() override;
    int getChannelCount() override;
    std::string getTitle() override;
    std::string getArtist() override;
    std::string getComposer() override;
    std::string getGenre() override;
    void setOutputSampleRate(int sampleRate) override;
    int getPlaybackCapabilities() const override {
        return PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_RELIABLE_DURATION |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    }
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::ContinuousLinear; }

    const char* getName() const override { return "Game Music Emu"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    Music_Emu* emu = nullptr;
    std::mutex decodeMutex;

    double duration = 0.0;
    int sampleRate = 48000;
    int bitDepth = 16;
    int channels = 2;
    int trackCount = 0;
    int activeTrack = 0;
    std::atomic<int> repeatMode { 0 }; // 0 none, 1 repeat track, 2 loop-point (unsupported -> treated as none)
    bool pendingTerminalEnd = false;

    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;

    void closeInternal();
};

#endif // SILICONPLAYER_GMEDECODER_H
