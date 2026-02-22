#ifndef SILICONPLAYER_FURNACEDECODER_H
#define SILICONPLAYER_FURNACEDECODER_H

#include "AudioDecoder.h"

#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

class DivEngine;

class FurnaceDecoder : public AudioDecoder {
public:
    FurnaceDecoder();
    ~FurnaceDecoder() override;

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
    std::string getFormatNameInfo();
    int getSongVersionInfo();
    std::string getSystemNameInfo();
    std::string getSystemNamesInfo();
    int getSystemCountInfo();
    int getSongChannelCountInfo();
    int getInstrumentCountInfo();
    int getWavetableCountInfo();
    int getSampleCountInfo();
    int getOrderCountInfo();
    int getRowsPerPatternInfo();
    int getCurrentOrderInfo();
    int getCurrentRowInfo();
    int getCurrentTickInfo();
    int getCurrentSpeedInfo();
    int getGrooveLengthInfo();
    float getCurrentHzInfo();

    const char* getName() const override { return "Furnace"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    struct SeekPoint {
        double seconds = 0.0;
        int order = 0;
        int row = 0;
    };

    mutable std::mutex decodeMutex;
    std::unique_ptr<DivEngine> engine;

    std::string sourcePath;
    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::string formatName = "Furnace";

    int sampleRateHz = 44100;
    int requestedSampleRateHz = 44100;
    int channels = 2;
    int optionYm2612Core = 0;
    int optionSnCore = 0;
    int optionNesCore = 0;
    int optionC64Core = 0;
    int optionGbQuality = 3;
    int optionDsidQuality = 3;
    int optionAyCore = 0;
    int subtuneCount = 1;
    int currentSubtuneIndex = 0;
    std::atomic<int> repeatMode { 0 };
    bool durationReliable = false;
    double durationSeconds = 0.0;
    bool loopRegionReliable = false;
    double loopStartSeconds = 0.0;
    double loopLengthSeconds = 0.0;
    int timelineRepeatMode = 0;
    bool trackRepeatVirtualInitialized = false;
    double trackRepeatVirtualSeconds = 0.0;
    double playbackPositionSeconds = 0.0;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;

    std::vector<SeekPoint> seekTimeline;
    std::vector<double> subtuneDurations;
    std::vector<float> leftScratch;
    std::vector<float> rightScratch;

    void closeInternalLocked();
    void refreshMetadataLocked();
    void refreshTimelineLocked();
    void syncToggleChannelsLocked();
    void applyToggleChannelMutesLocked();
    void applyCoreOptionsLocked(DivEngine* targetEngine) const;
    void applyRepeatModeLocked();
    double normalizeTimelinePositionLocked(double seconds) const;
    double normalizeSeekTargetLocked(double seconds) const;
    bool seekToTimelineLocked(double targetSeconds);
    static int normalizeRepeatMode(int mode);
    static int clampSampleRate(int sampleRateHz);
};

#endif // SILICONPLAYER_FURNACEDECODER_H
