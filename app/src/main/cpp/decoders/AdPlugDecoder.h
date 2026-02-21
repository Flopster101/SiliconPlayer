#ifndef SILICONPLAYER_ADPLUGDECODER_H
#define SILICONPLAYER_ADPLUGDECODER_H

#include "AudioDecoder.h"

#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

class CEmuopl;
class CPlayer;

class AdPlugDecoder : public AudioDecoder {
public:
    AdPlugDecoder();
    ~AdPlugDecoder() override;

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
    std::string getDescription();
    int getPatternCountInfo();
    int getCurrentPatternInfo();
    int getOrderCountInfo();
    int getCurrentOrderInfo();
    int getCurrentRowInfo();
    int getCurrentSpeedInfo();
    int getInstrumentCountInfo();
    std::string getInstrumentNamesInfo();
    std::vector<std::string> getToggleChannelNames() override;
    std::vector<uint8_t> getToggleChannelAvailability() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    int getFixedSampleRateHz() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override;

    const char* getName() const override { return "AdPlug"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    mutable std::mutex decodeMutex;
    std::unique_ptr<CEmuopl> opl;
    std::unique_ptr<CPlayer> player;
    std::vector<short> pcmScratch;

    int sampleRateHz = 44100;
    int channels = 2;
    int bitDepth = 16;
    std::atomic<int> repeatMode { 0 };
    int subtuneCount = 1;
    int currentSubtuneIndex = 0;
    int remainingTickFrames = 0;
    bool durationReliable = false;
    bool reachedEnd = false;
    double durationSeconds = 0.0;
    double playbackPositionSeconds = 0.0;

    std::string sourcePath;
    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;

    void closeInternalLocked();
    void syncToggleChannelsLocked();
    void applyToggleMutesLocked();
};

#endif // SILICONPLAYER_ADPLUGDECODER_H
