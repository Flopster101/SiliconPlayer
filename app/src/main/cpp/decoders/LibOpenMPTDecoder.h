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
    std::string getTitle() override;
    std::string getArtist() override;
    void setOutputSampleRate(int sampleRate) override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    void setOption(const char* name, const char* value) override;

    // Framework
    const char* getName() const override { return "LibOpenMPT"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    std::unique_ptr<openmpt::module> module;
    std::mutex decodeMutex;

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
    int interpolationFilterLength = 0;
    int volumeRampingStrength = -1;
    int masterGainMilliBel = 0;
    int amigaResamplerMode = 2; // 0 None, 1 Unfiltered, 2 A500, 3 A1200
    bool surroundEnabled = false;
    std::string title;
    std::string artist;

    void applyRenderSettingsLocked();
};

#endif //SILICONPLAYER_LIBOPENMPTDECODER_H
