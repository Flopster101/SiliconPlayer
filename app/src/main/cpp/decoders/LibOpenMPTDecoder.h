#ifndef SILICONPLAYER_LIBOPENMPTDECODER_H
#define SILICONPLAYER_LIBOPENMPTDECODER_H

#include "AudioDecoder.h"
#include <libopenmpt/libopenmpt.hpp>
#include <memory>
#include <vector>
#include <mutex>
#include <filesystem>
#include <fstream>
#include <string>

class LibOpenMPTDecoder : public AudioDecoder {
public:
    LibOpenMPTDecoder();
    ~LibOpenMPTDecoder() override;

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
    std::string getModuleTypeLong();
    std::string getTracker();
    std::string getSongMessage();
    int getOrderCount();
    int getPatternCount();
    int getInstrumentCount();
    int getSampleCount();
    std::string getInstrumentNames();
    std::string getSampleNames();
    int getModuleChannelCount();
    std::vector<float> getCurrentChannelVuLevels();
    std::vector<float> getCurrentChannelScopeSamples(int samplesPerChannel);
    void setOutputSampleRate(int sampleRate) override;
    int getPlaybackCapabilities() const override {
        return PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_RELIABLE_DURATION |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
               PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE;
    }
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::Discontinuous; }
    void setOption(const char* name, const char* value) override;
    int getOptionApplyPolicy(const char* name) const override;

    // Framework
    const char* getName() const override { return "LibOpenMPT"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    std::unique_ptr<openmpt::module> module;
    mutable std::mutex decodeMutex;

    // Buffer to hold file data in memory
    std::vector<char> fileBuffer;

    double duration = 0.0;
    int sampleRate = 48000; // Reported playback technical default
    int renderSampleRate = 48000; // Actual render rate used for output
    int bitDepth = 32;
    int channels = 2; // Rendered output is stereo
    int moduleChannels = 0;
    int repeatMode = 0;
    int stereoSeparationPercent = 100;
    int stereoSeparationAmigaPercent = 100;
    int interpolationFilterLength = 0;
    int volumeRampingStrength = 1;
    int masterGainMilliBel = 0;
    int amigaResamplerMode = 2; // 0 None, 1 Unfiltered, 2 A500, 3 A1200
    bool applyAmigaResamplerToAllModules = false;
    bool ft2XmVolumeRamping = false;
    bool surroundEnabled = false;
    bool isAmigaModule = false;
    bool isXmModule = false;
    std::string title;
    std::string artist;
    std::string moduleTypeLong;
    std::string tracker;
    std::string songMessage;
    std::string instrumentNames;
    std::string sampleNames;
    int subtuneCount = 1;
    int currentSubtuneIndex = 0;
    std::vector<std::string> subtuneNames;
    std::vector<double> subtuneDurationsSeconds;

    void applyRenderSettingsLocked();
};

#endif //SILICONPLAYER_LIBOPENMPTDECODER_H
