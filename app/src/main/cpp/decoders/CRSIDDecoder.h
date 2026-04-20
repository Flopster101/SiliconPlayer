#ifndef SILICONPLAYER_CRSIDDECODER_H
#define SILICONPLAYER_CRSIDDECODER_H

#include "AudioDecoder.h"
#include "SidMetadataProvider.h"
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

struct cRSID_SIDheader;

class CRSIDDecoder : public AudioDecoder, public SidMetadataProvider {
public:
    CRSIDDecoder();
    ~CRSIDDecoder() override;

    bool open(const char* path) override;
    void close() override;
    int read(float* buffer, int numFrames) override;
    void seek(double seconds) override;
    double getDuration() override;
    int getSampleRate() override;
    int getBitDepth() override;
    std::string getBitDepthLabel() override;
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
    std::string getComment() override;
    void setOutputSampleRate(int sampleRateHz) override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override;
    void setOption(const char* name, const char* value) override;
    int getOptionApplyPolicy(const char* name) const override;

    std::string getSidFormatName() override;
    std::string getSidClockName() override;
    std::string getSidSpeedName() override;
    std::string getSidCompatibilityName() override;
    std::string getSidBackendName() override;
    int getSidChipCountInfo() override;
    std::string getSidModelSummary() override;
    std::string getSidCurrentModelSummary() override;
    std::string getSidBaseAddressSummary() override;
    std::string getSidCommentSummary() override;

    const char* getName() const override { return "cRSID"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    enum class SidModelMode {
        Auto = 0,
        Mos6581 = 1,
        Mos8580 = 2
    };

    enum class QualityMode {
        Light = 0,
        High = 1,
        Sinc = 2
    };

    enum class ClockMode {
        Auto = 0,
        Pal = 1,
        Ntsc = 2
    };

    enum class Filter6581Preset {
        Stock = 0,
        R4ar = 1,
        R3 = 2,
        R2 = 3
    };

    mutable std::mutex decodeMutex;

    std::vector<unsigned char> fileData;
    std::string sourcePath;
    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::string comment;
    std::vector<double> declaredSubtuneDurationsSeconds;
    std::vector<double> subtuneDurationsSeconds;

    int requestedSampleRate = 48000;
    int activeSampleRate = 48000;
    int currentSubtuneIndex = 0;
    int subtuneCount = 1;
    int sidChipCount = 1;
    ClockMode clockMode = ClockMode::Auto;
    SidModelMode sidModelMode = SidModelMode::Auto;
    QualityMode qualityMode = QualityMode::High;
    Filter6581Preset filter6581Preset = Filter6581Preset::Stock;
    bool durationReliable = false;
    bool endReached = false;
    double currentDurationSeconds = 0.0;
    double playbackPositionSeconds = 0.0;
    double fallbackDurationSeconds = 180.0;
    std::atomic<int> repeatMode { 0 };

    std::string sidFormatName;
    std::string sidClockName;
    std::string sidSpeedName;
    std::string sidCompatibilityName;
    std::string sidModelSummary;
    std::string sidCurrentModelSummary;
    std::string sidBaseAddressSummary;
    std::string sidCommentSummary;

    bool loadFileLocked(const char* path);
    bool initializeEngineLocked(int subtuneIndex);
    bool startSubtuneLocked(int subtuneIndex);
    void closeLocked();
    void applyPlaybackOptionsLocked();
    void refreshHeaderMetadataLocked(const cRSID_SIDheader* header);
    void refreshRuntimeMetadataLocked(const cRSID_SIDheader* header);
};

#endif // SILICONPLAYER_CRSIDDECODER_H
