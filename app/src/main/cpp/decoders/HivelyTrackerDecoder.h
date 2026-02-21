#ifndef SILICONPLAYER_HIVELYTRACKERDECODER_H
#define SILICONPLAYER_HIVELYTRACKERDECODER_H

#include "AudioDecoder.h"

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

struct hvl_tune;

class HivelyTrackerDecoder : public AudioDecoder {
public:
    HivelyTrackerDecoder();
    ~HivelyTrackerDecoder() override;

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
    std::string getFormatNameInfo();
    int getFormatVersionInfo();
    int getPositionCountInfo();
    int getRestartPositionInfo();
    int getTrackLengthRowsInfo();
    int getTrackCountInfo();
    int getInstrumentCountInfo();
    int getSpeedMultiplierInfo();
    int getCurrentPositionInfo();
    int getCurrentRowInfo();
    int getCurrentTempoInfo();
    int getMixGainPercentInfo();
    std::string getInstrumentNamesInfo();
    std::vector<std::string> getToggleChannelNames() override;
    std::vector<uint8_t> getToggleChannelAvailability() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    void setOutputSampleRate(int sampleRateHz) override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override;

    const char* getName() const override { return "HivelyTracker"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    mutable std::mutex decodeMutex;
    hvl_tune* tune = nullptr;

    std::string sourcePath;
    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::string formatName;
    int formatVersion = 0;

    int sampleRateHz = 44100;
    int requestedSampleRateHz = 44100;
    int channels = 2;
    int displayChannels = 2;
    int bitDepth = 8;
    int subtuneCount = 1;
    int currentSubtuneIndex = 0;
    std::atomic<int> repeatMode { 0 };
    double playbackPositionSeconds = 0.0;
    double durationSeconds = 0.0;
    std::atomic<bool> durationReliable { false };

    std::vector<float> pendingInterleaved;
    std::size_t pendingReadOffset = 0;
    std::vector<int16_t> decodeInterleavedScratch;
    std::vector<double> subtuneDurationSeconds;
    std::vector<uint8_t> subtuneDurationKnown;
    std::vector<uint8_t> subtuneDurationReliable;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;
    bool stopAfterPendingDrain = false;

    void closeInternalLocked();
    int getFrameSamplesPerDecodeLocked() const;
    bool decodeFrameIntoPendingLocked();
    bool resetToSubtuneStartLocked();
    bool analyzeSubtuneDurationLocked(int index);
    void updateCurrentDurationFromCacheLocked();
    void syncToggleChannelsLocked();
    void applyToggleMutesLocked();
};

#endif // SILICONPLAYER_HIVELYTRACKERDECODER_H
