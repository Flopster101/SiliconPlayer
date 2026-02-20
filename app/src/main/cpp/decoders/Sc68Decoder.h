#ifndef SILICONPLAYER_SC68DECODER_H
#define SILICONPLAYER_SC68DECODER_H

#include "AudioDecoder.h"
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

struct _sc68_s;
typedef struct _sc68_s sc68_t;

class Sc68Decoder : public AudioDecoder {
public:
    Sc68Decoder();
    ~Sc68Decoder() override;

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
    int getSubtuneCount() const override;
    int getCurrentSubtuneIndex() const override;
    bool selectSubtune(int index) override;
    std::string getSubtuneTitle(int index) override;
    std::string getSubtuneArtist(int index) override;
    double getSubtuneDurationSeconds(int index) override;
    std::string getTitle() override;
    std::string getArtist() override;
    std::string getComposer() override;
    std::string getGenre() override;
    void setOutputSampleRate(int sampleRate) override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    int getFixedSampleRateHz() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::ContinuousLinear; }

    const char* getName() const override { return "SC68"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    mutable std::mutex decodeMutex;
    sc68_t* handle = nullptr;
    bool isOpen = false;
    int sampleRateHz = 44100;
    int requestedSampleRateHz = 44100;
    int channels = 2;
    int bitDepth = 16;
    int subtuneCount = 1;
    int currentTrack1Based = 1;
    std::atomic<bool> durationReliable { false };
    double durationSeconds = 0.0;
    std::string sourcePath;
    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::atomic<int> repeatMode { 0 };

    void closeInternalLocked();
    bool refreshTrackStateLocked();
    void refreshMetadataLocked();
    void refreshDurationLocked();
    bool setTrackLocked(int track1Based);
    int processIntoLocked(float* buffer, int numFrames);
};

#endif // SILICONPLAYER_SC68DECODER_H
