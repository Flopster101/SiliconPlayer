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
    int getChannelCount() override;

    // Framework
    const char* getName() const override { return "LibOpenMPT"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    std::unique_ptr<openmpt::module> module;
    std::mutex decodeMutex;

    // Buffer to hold file data in memory
    std::vector<char> fileBuffer;

    double duration = 0.0;
    int sampleRate = 48000;
    int channels = 2; // Stereo
};

#endif //SILICONPLAYER_LIBOPENMPTDECODER_H
