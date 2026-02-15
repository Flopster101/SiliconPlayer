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
    void setOption(const char* name, const char* value) override;
    int getOptionApplyPolicy(const char* name) const override;
    int getPlaybackCapabilities() const override {
        return PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_RELIABLE_DURATION |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    }
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override;

    const char* getName() const override { return "Game Music Emu"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    Music_Emu* emu = nullptr;
    std::mutex decodeMutex;

    double duration = 0.0;
    int bitDepth = 16;
    int channels = 2;
    int trackCount = 0;
    int activeTrack = 0;
    std::atomic<int> repeatMode { 0 }; // 0 none, 1 repeat track, 2 repeat at loop point
    bool pendingTerminalEnd = false;
    int loopStartMs = -1;
    int loopLengthMs = -1;
    bool hasLoopPoint = false;
    bool isSpcTrack = false;
    double playbackPositionSeconds = 0.0;
    int lastTellMs = -1;
    double tempo = 1.0;
    double stereoDepth = 0.0;
    bool echoEnabled = true;
    bool accuracyEnabled = false;
    double eqTrebleDb = 0.0;
    double eqBassHz = 90.0;
    bool spcUseBuiltInFade = false;

    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;

    void closeInternal();
    void applyRepeatBehaviorLocked();
    void applyCoreOptionsLocked();
    int resolveOpenSampleRateLocked(const char* path) const;

    int requestedSampleRate = 48000;
    int activeSampleRate = 48000;
    int spcInterpolation = 0;
    bool spcUseNativeSampleRate = true;
    bool loggedSpcInterpolationCompat = false;
};

#endif // SILICONPLAYER_GMEDECODER_H
