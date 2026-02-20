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
    std::string getFormatName();
    std::string getHardwareName();
    std::string getReplayName();
    int getReplayRateHz();
    int getTrackCountInfo();
    std::string getAlbumName();
    std::string getYearTag();
    std::string getRipperTag();
    std::string getConverterTag();
    bool getUsesYm() const;
    bool getUsesSte() const;
    bool getUsesAmiga() const;
    std::vector<std::string> getToggleChannelNames() override;
    std::vector<uint8_t> getToggleChannelAvailability() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    void setOutputSampleRate(int sampleRate) override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    int getFixedSampleRateHz() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::ContinuousLinear; }
    void setOption(const char* name, const char* value) override;
    int getOptionApplyPolicy(const char* name) const override;

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
    std::string formatName;
    std::string hardwareName;
    std::string replayName;
    int replayRateHz = 0;
    int trackCountInfo = 0;
    std::string albumName;
    std::string yearTag;
    std::string ripperTag;
    std::string converterTag;
    bool trackHasYm = false;
    bool trackHasSte = false;
    bool trackHasAmiga = false;
    bool trackUsesAgaPath = false;
    double playbackPositionSeconds = 0.0;
    int lastCorePositionMs = -1;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;
    std::atomic<int> repeatMode { 0 };
    int optionAsid = 1;
    int optionDefaultTimeSeconds = 0;
    int optionYmEngine = 0;
    int optionYmVolModel = 0;
    bool optionAmigaFilter = true;
    int optionAmigaBlend = 0x50;
    int optionAmigaClock = 0;

    void closeInternalLocked();
    bool refreshTrackStateLocked();
    void refreshMetadataLocked();
    void refreshDurationLocked();
    void updatePlaybackPositionLocked(int producedFrames);
    void rebuildToggleChannelsLocked();
    void applyToggleChannelMutesLocked();
    void applyCoreOptionsLocked();
    bool setTrackLocked(int track1Based);
    int processIntoLocked(float* buffer, int numFrames);
};

#endif // SILICONPLAYER_SC68DECODER_H
