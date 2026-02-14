#ifndef SILICONPLAYER_FFMPEGDECODER_H
#define SILICONPLAYER_FFMPEGDECODER_H

#include "AudioDecoder.h"
#include <vector>
#include <mutex>
#include <memory>
#include <cstdint>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libswresample/swresample.h>
#include <libavutil/opt.h>
#include <libavutil/samplefmt.h>
}

class FFmpegDecoder : public AudioDecoder {
public:
    FFmpegDecoder();
    ~FFmpegDecoder() override;

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
    double getPlaybackPositionSeconds() override;

    // Configuration
    const char* getName() const override { return "FFmpeg"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    AVFormatContext* formatContext = nullptr;
    AVCodecContext* codecContext = nullptr;
    SwrContext* swrContext = nullptr;
    int audioStreamIndex = -1;

    AVFrame* frame = nullptr;
    AVPacket* packet = nullptr;

    double duration = 0.0;
    int outputSampleRate = 48000;
    int sourceSampleRate = 0;
    int sourceBitDepth = 0;
    int sourceChannelCount = 0;
    int outputChannelCount = 2; // Output channels (stereo)
    std::string title;
    std::string artist;

    // Resampling buffer
    std::vector<float> sampleBuffer;
    size_t sampleBufferCursor = 0; // Current read position in buffer
    bool decoderDrainStarted = false;
    int64_t totalFramesOutput = 0; // Total frames output for position tracking

    std::mutex decodeMutex;

    bool initResampler();
    void freeResampler();
    int decodeFrame(); // Decodes one frame and appends to sampleBuffer. Returns 0 on success, <0 on error/EOF
};

#endif //SILICONPLAYER_FFMPEGDECODER_H
