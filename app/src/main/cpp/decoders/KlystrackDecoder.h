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
    std::string getFormatNameInfo();
    int getTrackCountInfo();
    int getInstrumentCountInfo();
    int getSongLengthRowsInfo();
    int getCurrentRowInfo();
    std::string getInstrumentNamesInfo();
    std::vector<std::string> getToggleChannelNames() override;
    std::vector<uint8_t> getToggleChannelAvailability() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    void setOutputSampleRate(int sampleRateHz) override;
    void setOption(const char* name, const char* value) override;
    int getOptionApplyPolicy(const char* name) const override;
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
    std::string formatName = "KT";
    int trackCount = 0;
    int instrumentCount = 0;
    std::string instrumentNames;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;

    int sampleRateHz = 44100;
    int requestedSampleRateHz = 44100;
    int playerQuality = 2;
    int channels = 2;
    std::atomic<int> repeatMode { 0 };
    double durationSeconds = 0.0;
    bool durationReliable = false;
    double playbackPositionSeconds = 0.0;
    int songLengthRows = 0;
    std::vector<int16_t> pcmScratch;

    void closeInternalLocked();
    void applyRepeatModeLocked();
    void updateSongInfoLocked();
    void syncToggleChannelsLocked();
    void applyToggleMutesLocked();
    int resolveRowForTimeMsLocked(int targetMs) const;
    static int normalizeRepeatMode(int mode);
};

#endif // SILICONPLAYER_KLYSTRACKDECODER_H
