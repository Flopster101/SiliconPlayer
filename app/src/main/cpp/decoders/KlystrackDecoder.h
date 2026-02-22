#ifndef SILICONPLAYER_KLYSTRACKDECODER_H
#define SILICONPLAYER_KLYSTRACKDECODER_H

#include "AudioDecoder.h"

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

struct KPlayer_t;
struct KSong_t;

class KlystrackDecoder : public AudioDecoder {
public:
    KlystrackDecoder();
    ~KlystrackDecoder() override;

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
    std::string getGenre() override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override;

    const char* getName() const override { return "Klystrack"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    mutable std::mutex decodeMutex;
    KPlayer_t* player = nullptr;
    KSong_t* song = nullptr;

    std::string sourcePath;
    std::string title;
    std::string artist;
    std::string genre;

    int sampleRateHz = 44100;
    int channels = 2;
    std::atomic<int> repeatMode { 0 };
    double durationSeconds = 0.0;
    double playbackPositionSeconds = 0.0;
    int songLengthRows = 0;
    std::vector<int16_t> pcmScratch;

    void closeInternalLocked();
    void applyRepeatModeLocked();
    int resolveRowForTimeMsLocked(int targetMs) const;
    static int normalizeRepeatMode(int mode);
};

#endif // SILICONPLAYER_KLYSTRACKDECODER_H
