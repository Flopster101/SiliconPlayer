#ifndef SILICONPLAYER_VIO2SFDECODER_H
#define SILICONPLAYER_VIO2SFDECODER_H

#include "AudioDecoder.h"
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

struct NDS_state;

class Vio2sfDecoder : public AudioDecoder {
public:
    Vio2sfDecoder();
    ~Vio2sfDecoder() override;

    bool open(const char* path) override;
    void close() override;
    int read(float* buffer, int numFrames) override;
    void seek(double seconds) override;
    double getDuration() override;
    int getSampleRate() override;
    int getBitDepth() override;
    std::string getBitDepthLabel() override;
    int getChannelCount() override;
    std::string getTitle() override;
    std::string getArtist() override;
    std::string getComposer() override;
    std::string getGenre() override;
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

    const char* getName() const override { return "Vio2SF"; }
    static std::vector<std::string> getSupportedExtensions();

public:
    struct TwosfLoaderState {
        uint8_t* rom = nullptr;
        uint8_t* state = nullptr;
        size_t romSize = 0;
        size_t stateSize = 0;
        int initialFrames = -1;
        int syncType = 0;
        int clockdown = 0;
        int arm9ClockdownLevel = 0;
        int arm7ClockdownLevel = 0;

        void clear();
        ~TwosfLoaderState();
    };

    struct MetadataState {
        std::string title;
        std::string artist;
        std::string composer;
        std::string genre;
        std::string game;
        std::string copyright;
        std::string year;
        std::string comment;
        std::string lengthTag;
        std::string fadeTag;
        bool hasLength = false;
        bool hasFade = false;
        unsigned long lengthMs = 0;
        unsigned long fadeMs = 0;
    };

private:
    mutable std::mutex decodeMutex;
    std::unique_ptr<NDS_state> emu;
    TwosfLoaderState loaderState;
    std::vector<int16_t> pcmScratch;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;
    bool isOpen = false;
    int repeatMode = 0;
    int sampleRate = 44100;
    int channels = 2;
    int bitDepth = 16;
    int interpolationQuality = 4;
    bool durationReliable = false;
    double durationSeconds = 180.0;
    int64_t renderedFrames = 0;
    std::string sourcePath;
    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;

    void closeInternalLocked();
    bool resetCoreLocked();
    void applyToggleChannelMutesLocked();
    void ensureToggleChannelsLocked();
};

#endif // SILICONPLAYER_VIO2SFDECODER_H
