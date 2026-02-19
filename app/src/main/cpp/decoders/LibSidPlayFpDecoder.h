#ifndef SILICONPLAYER_LIBSIDPLAYFPDECODER_H
#define SILICONPLAYER_LIBSIDPLAYFPDECODER_H

#include "AudioDecoder.h"
#include <sidplayfp/SidConfig.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

class sidplayfp;
class SidTune;
class SidConfig;
class sidbuilder;

enum class SidBackend {
    ReSID,
    ReSIDfp,
    SIDLite
};

enum class SidClockMode {
    Auto,
    Pal,
    Ntsc
};

enum class SidModelMode {
    Auto,
    Mos6581,
    Mos8580
};

class LibSidPlayFpDecoder : public AudioDecoder {
public:
    LibSidPlayFpDecoder();
    ~LibSidPlayFpDecoder() override;

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
    std::string getSidFormatName();
    std::string getSidClockName();
    std::string getSidSpeedName();
    std::string getSidCompatibilityName();
    std::string getSidBackendName();
    int getSidChipCountInfo();
    std::string getSidModelSummary();
    std::string getSidCurrentModelSummary();
    std::string getSidBaseAddressSummary();
    std::string getSidCommentSummary();
    std::vector<std::string> getToggleChannelNames() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    void setOutputSampleRate(int sampleRateHz) override;
    void setOption(const char* name, const char* value) override;
    int getOptionApplyPolicy(const char* name) const override;
    void setRepeatMode(int mode) override;
    int getPlaybackCapabilities() const override;
    int getRepeatModeCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::Discontinuous; }

    const char* getName() const override { return "LibSIDPlayFP"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    mutable std::mutex decodeMutex;
    std::unique_ptr<sidplayfp> player;
    std::unique_ptr<sidbuilder> sidBuilder;
    std::unique_ptr<SidTune> tune;
    std::unique_ptr<SidConfig> config;

    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::vector<std::string> subtuneTitles;
    std::vector<std::string> subtuneArtists;
    std::vector<double> subtuneDurationsSeconds;
    int subtuneCount = 1;
    int currentSubtuneIndex = 0;
    int requestedSampleRate = 48000;
    int activeSampleRate = 48000;
    int outputChannels = 2;
    int sidChipCount = 1;
    int sidVoiceCount = 3;
    std::string sidFormatName;
    std::string sidClockName;
    std::string sidSpeedName;
    std::string sidCompatibilityName;
    std::string sidModelSummary; // Declared in tune metadata
    std::string sidCurrentModelSummary; // Effective model used by player
    std::string sidBaseAddressSummary;
    std::string sidCommentSummary;
    double fallbackDurationSeconds = 180.0;
    std::atomic<int> repeatMode { 0 }; // 0 none, 1 repeat track, 2 repeat loop-point style
    std::vector<int16_t> pendingMixedSamples;
    size_t pendingMixedOffset = 0;
    std::vector<int16_t> mixedScratchSamples;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;
    std::atomic<double> playbackPositionSecondsAtomic { 0.0 };
    std::atomic<double> currentSubtuneDurationSecondsAtomic { 180.0 };
    std::atomic<bool> durationReliableAtomic { false };
    SidBackend selectedBackend = SidBackend::ReSIDfp;
    SidBackend activeBackend = SidBackend::ReSIDfp;
    SidClockMode sidClockMode = SidClockMode::Auto;
    SidModelMode sidModelMode = SidModelMode::Auto;
    bool filter6581Enabled = true;
    bool filter8580Enabled = true;
    bool digiBoost8580 = false;
    double reSidFpFilterCurve6581 = 0.5;
    double reSidFpFilterRange6581 = 0.5;
    double reSidFpFilterCurve8580 = 0.5;
    bool reSidFpFastSampling = true;
    SidConfig::sid_cw_t reSidFpCombinedWaveformsStrength = SidConfig::AVERAGE;

    bool openInternalLocked(const char* path);
    bool applyConfigLocked();
    bool selectSubtuneLocked(int index);
    void applySidBackendOptionsLocked();
    void applySidFilterOptionsLocked();
    void rebuildToggleChannelsLocked();
    void applyToggleChannelMutesLocked();
    void refreshMetadataLocked();
};

#endif // SILICONPLAYER_LIBSIDPLAYFPDECODER_H
