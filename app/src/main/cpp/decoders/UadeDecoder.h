#ifndef SILICONPLAYER_UADEDECODER_H
#define SILICONPLAYER_UADEDECODER_H

#include "AudioDecoder.h"

#include <atomic>
#include <mutex>
#include <string>
#include <vector>

struct uade_state;

class UadeDecoder : public AudioDecoder {
public:
    UadeDecoder();
    ~UadeDecoder() override;

    static void setRuntimePaths(const std::string& baseDir, const std::string& uadeCorePath);

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
    std::string getFormatName() const;
    std::string getModuleName() const;
    std::string getPlayerName() const;
    std::string getModuleFileName() const;
    std::string getPlayerFileName() const;
    std::string getModuleMd5() const;
    std::string getDetectionExtension() const;
    std::string getDetectedFormatName() const;
    std::string getDetectedFormatVersion() const;
    bool getDetectionByContent() const;
    bool getDetectionIsCustom() const;
    int getSubsongMin() const;
    int getSubsongMax() const;
    int getSubsongDefault() const;
    int getCurrentSubsong() const;
    int64_t getModuleBytes() const;
    int64_t getSongBytes() const;
    int64_t getSubsongBytes() const;
    std::vector<std::string> getToggleChannelNames() override;
    std::vector<uint8_t> getToggleChannelAvailability() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    void setOption(const char* name, const char* value) override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::ContinuousLinear; }

    const char* getName() const override { return "UADE"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    mutable std::mutex decodeMutex;
    uade_state* state = nullptr;
    std::string sourcePath;
    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    int sampleRateHz = 44100;
    int channels = 2;
    int bitDepth = 16;
    int subtuneMin = 0;
    int subtuneMax = 0;
    int subtuneDefault = 0;
    int currentSubsong = 0;
    bool detectionByContent = false;
    bool detectionIsCustom = false;
    int64_t moduleBytes = 0;
    int64_t songBytes = 0;
    int64_t subsongBytes = 0;
    std::string formatName;
    std::string moduleName;
    std::string playerName;
    std::string moduleFileName;
    std::string playerFileName;
    std::string moduleMd5;
    std::string detectionExtension;
    std::string detectedFormatName;
    std::string detectedFormatVersion;
    std::atomic<bool> durationReliable { false };
    int unknownDurationSeconds = 0;
    double durationSeconds = 0.0;
    double playbackPositionSeconds = 0.0;
    int64_t renderedFrames = 0;
    std::atomic<int> repeatMode { 0 };
    std::vector<int16_t> pcmScratch;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;

    void closeInternalLocked();
    uade_state* createStateLocked();
    bool refreshSongInfoLocked();
    uint32_t getToggleMuteMaskLocked() const;
    void applyToggleMutesLocked();
    void ensureToggleChannelsLocked();
};

#endif // SILICONPLAYER_UADEDECODER_H
