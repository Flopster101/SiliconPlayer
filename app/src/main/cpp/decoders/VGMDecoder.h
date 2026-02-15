#ifndef SILICONPLAYER_VGMDECODER_H
#define SILICONPLAYER_VGMDECODER_H

#include "AudioDecoder.h"
#include <vector>
#include <mutex>
#include <memory>
#include <string>
#include <atomic>

// Forward declarations for libvgm types
class PlayerA;
struct _data_loader;
typedef struct _data_loader DATA_LOADER;

class VGMDecoder : public AudioDecoder {
public:
    VGMDecoder();
    ~VGMDecoder() override;

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
    std::string getTitle() override;
    std::string getArtist() override;
    void setOutputSampleRate(int sampleRate) override;
    int getPlaybackCapabilities() const override {
        return PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_RELIABLE_DURATION |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    }
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override;
    void setOption(const char* name, const char* value) override;

    // Framework
    const char* getName() const override { return "VGMPlay"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    std::unique_ptr<PlayerA> player;
    std::mutex decodeMutex;

    // File data buffer
    std::vector<uint8_t> fileData;
    DATA_LOADER* dataLoaderHandle = nullptr;

    double duration = 0.0;
    int sampleRate = 44100; // Default VGM playback rate
    int bitDepth = 16; // VGM outputs 16-bit samples
    int channels = 2; // Stereo output
    std::atomic<int> repeatMode { 0 }; // 0 = no repeat, 1 = repeat track, 2 = repeat at loop point
    std::string title;
    std::string artist;
    std::string gameName;
    std::string systemName;

    // Playback state
    uint32_t currentLoop = 0;
    uint32_t finiteLoopCount = 1; // Play through once for non-loop-point modes.
    bool hasLooped = false;
    bool playerStarted = false; // Track if player has been started
    bool pendingTerminalEnd = false;
    double playbackTimeOffsetSeconds = 0.0;

    // Internal close method that doesn't acquire mutex (for use within locked methods)
    void closeInternal();

    // Ensure player is started with correct sample rate (called on first read)
    void ensurePlayerStarted();
};

#endif //SILICONPLAYER_VGMDECODER_H
