#ifndef SILICONPLAYER_GMEDECODER_H
#define SILICONPLAYER_GMEDECODER_H

#include "../ChannelScopeSharedState.h"
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
    std::string getCopyright() override;
    std::string getComment() override;
    std::string getSystemName();
    std::string getGameName();
    std::string getDumper();
    int getTrackCountInfo();
    int getVoiceCountInfo();
    bool getHasLoopPointInfo();
    int getLoopStartMsInfo();
    int getLoopLengthMsInfo();
    std::vector<std::string> getToggleChannelNames() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    void setOutputSampleRate(int sampleRate) override;
    void setOption(const char* name, const char* value) override;
    int getOptionApplyPolicy(const char* name) const override;
    int getPlaybackCapabilities() const override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override;
    std::shared_ptr<ChannelScopeSharedState> getChannelScopeSharedState() const override { return channelScopeState; }
    std::vector<int32_t> getChannelScopeTextState(int maxChannels) override;

    const char* getName() const override { return "Game Music Emu"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    Music_Emu* emu = nullptr;
    Music_Emu* scopeMultiEmu = nullptr;
    Music_Emu* scopeApuEmu = nullptr;
    Music_Emu* scopeVrc6Emu = nullptr;
    Music_Emu* scopeMmc5Emu = nullptr;
    std::vector<Music_Emu*> scopeVoiceEmus;
    mutable std::mutex decodeMutex;

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
    bool durationReliable = true;
    double playbackPositionSeconds = 0.0;
    int lastTellMs = -1;
    double tempo = 1.0;
    double stereoDepth = 0.0;
    bool echoEnabled = true;
    bool accuracyEnabled = false;
    double eqTrebleDb = 0.0;
    double eqBassHz = 90.0;
    bool spcUseBuiltInFade = false;
    int unknownDurationSeconds = 180;

    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::string systemName;
    std::string gameName;
    std::string copyrightText;
    std::string commentText;
    std::string dumper;
    std::string sourcePath;
    int voiceCount = 0;
    std::vector<std::string> rawVoiceNames;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;
    std::vector<int> displayToActualVoice;
    std::vector<int> actualToDisplayVoice;
    std::vector<float> scopeVoiceGains;
    int scopeVrc6BaseVoice = -1;
    int scopeMmc5BaseVoice = -1;
    std::shared_ptr<ChannelScopeSharedState> channelScopeState;
    std::vector<float> scopeRingRaw;
    std::vector<short> scopePcmScratch;
    std::vector<short> scopeApuScratch;
    std::vector<short> scopeVrc6Scratch;
    std::vector<short> scopeMmc5Scratch;
    int scopeRingChannels = 0;
    int scopeRingWritePos = 0;
    int scopeRingSamples = 0;
    uint64_t channelScopeSourceSerial = 0;

    void closeInternal();
    bool applyTrackInfoLocked(int trackIndex);
    void closeScopeCaptureLocked();
    Music_Emu* createScopeShadowLocked(bool multiChannel);
    bool createScopeCaptureLocked();
    bool syncScopeCaptureLocked(int positionMs = 0);
    void resetChannelScopeLocked();
    void ensureScopeRingShapeLocked(int channels);
    void appendScopeFrameLocked(const float* perVoiceSamples, int channels);
    void publishScopeSnapshotLocked();
    void captureChannelScopeBlockLocked(int frames);
    void applyRepeatBehaviorToEmuLocked(Music_Emu* target);
    void applyRepeatBehaviorLocked();
    void applyCoreOptionsToEmuLocked(Music_Emu* target, bool forScopeCapture = false) const;
    void applyCoreOptionsLocked();
    void applyToggleChannelMutesToEmuLocked(Music_Emu* target, int soloVoice = -1) const;
    int buildScopeApuMuteMaskLocked() const;
    int buildScopeVrc6MuteMaskLocked() const;
    int buildScopeMmc5MuteMaskLocked() const;
    void rebuildToggleChannelsLocked();
    void rebuildDisplayVoiceOrderLocked();
    void rebuildScopeVoiceGainsLocked();
    void rebuildScopeVrc6BaseVoiceLocked();
    void rebuildScopeMmc5BaseVoiceLocked();
    void applyToggleChannelMutesLocked();
    int resolveOpenSampleRateLocked(const char* path) const;

    int requestedSampleRate = 48000;
    int activeSampleRate = 48000;
    int spcInterpolation = 0;
    bool spcUseNativeSampleRate = true;
};

#endif // SILICONPLAYER_GMEDECODER_H
