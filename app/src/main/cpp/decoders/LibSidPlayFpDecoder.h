#ifndef SILICONPLAYER_LIBSIDPLAYFPDECODER_H
#define SILICONPLAYER_LIBSIDPLAYFPDECODER_H

#include "../ChannelScopeSharedState.h"
#include "AudioDecoder.h"
#include "SidMetadataProvider.h"
#include <sidplayfp/SidConfig.h>
#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
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

class LibSidPlayFpDecoder : public AudioDecoder, public SidMetadataProvider {
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
    std::shared_ptr<ChannelScopeSharedState> getChannelScopeSharedState() const override { return channelScopeState; }

    const char* getName() const override { return "LibSIDPlayFP"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    struct ScopeConfigSnapshot;
    struct ScopeShadow;

    mutable std::mutex decodeMutex;
    std::unique_ptr<sidplayfp> player;
    std::unique_ptr<sidbuilder> sidBuilder;
    std::unique_ptr<SidTune> tune;
    std::unique_ptr<SidConfig> config;

    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::string sourcePath;
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
    std::atomic<int> repeatMode { 0 }; // 0 none, 1 repeat track-set, 2 loop-point, 3 repeat current subtune
    std::vector<int16_t> pendingMixedSamples;
    size_t pendingMixedOffset = 0;
    std::vector<int16_t> mixedScratchSamples;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;
    std::shared_ptr<ChannelScopeSharedState> channelScopeState;
    std::vector<std::unique_ptr<ScopeShadow>> scopeVoiceShadows;
    bool scopeCaptureEnabled = false;
    bool scopeCaptureDirty = false;
    std::vector<float> scopeRingRaw;
    std::vector<float> scopeFrameScratch;
    int scopeRingChannels = 0;
    int scopeRingWritePos = 0;
    int scopeRingSamples = 0;
    uint64_t channelScopeSourceSerial = 0;
    std::mutex scopeWorkerMutex;
    std::condition_variable scopeWorkerCv;
    std::thread scopeWorkerThread;
    std::atomic<bool> scopeWorkerStop { false };
    std::atomic<uint32_t> scopeTargetPositionMs { 0 };
    uint64_t scopeConfigGeneration = 1;
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
    ScopeConfigSnapshot captureScopeConfigSnapshotLocked() const;
    void ensureScopeWorkerStartedLocked();
    void stopScopeWorker();
    void markScopeConfigDirtyLocked(bool clearPublishedSnapshot);
    void scopeWorkerLoop();
    bool applyConfigToScopeShadowLocked(const ScopeConfigSnapshot& snapshot, ScopeShadow& shadow) const;
    bool recreateScopeCaptureLocked(const ScopeConfigSnapshot& snapshot);
    void closeScopeCaptureLocked();
    void resetChannelScopeLocked();
    void ensureScopeRingShapeLocked(int channelsToKeep);
    void appendScopeFrameLocked(const float* perVoiceSamples, int channelsToWrite);
    void publishScopeSnapshotLocked();
    bool captureChannelScopeBlockLocked(unsigned int renderCycles);
    uint32_t getScopePlaybackPositionMsLocked() const;
    void applyToggleChannelMutesToScopeShadowLocked(
            const ScopeConfigSnapshot& snapshot,
            ScopeShadow& shadow,
            int soloChannel
    ) const;
    void applySidBackendOptionsLocked();
    void applySidFilterOptionsLocked();
    void rebuildToggleChannelsLocked();
    void applyToggleChannelMutesLocked();
    void refreshMetadataLocked();
};

#endif // SILICONPLAYER_LIBSIDPLAYFPDECODER_H
