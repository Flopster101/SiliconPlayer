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
    std::string getComposer() override;
    std::string getGenre() override;
    std::string getAlbum() override;
    std::string getYear() override;
    std::string getDate() override;
    std::string getCopyright() override;
    std::string getComment() override;
    std::string getCodecName() const;
    std::string getContainerName() const;
    std::string getSampleFormatName() const;
    std::string getChannelLayoutName() const;
    std::string getEncoderName() const;
    std::vector<std::string> getToggleChannelNames() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    void setOutputSampleRate(int sampleRate) override;
    void setRepeatMode(int mode) override;
    void setOption(const char* name, const char* value) override;
    int getOptionApplyPolicy(const char* name) const override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override {
        return PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_RELIABLE_DURATION |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
               PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE |
               PLAYBACK_CAP_DIRECT_SEEK;
    }
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::ContinuousLinear; }

    // Bitrate information
    int64_t getBitrate() const;
    bool isVBR() const;

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
    std::string composer;
    std::string genre;
    std::string album;
    std::string year;
    std::string date;
    std::string copyrightText;
    std::string comment;
    std::string codecName;
    std::string containerName;
    std::string sampleFormatName;
    std::string channelLayoutName;
    std::string encoderName;
    int64_t bitrate = 0;
    bool vbr = false;

    // Resampling buffer
    std::vector<float> sampleBuffer;
    size_t sampleBufferCursor = 0; // Current read position in buffer
    bool decoderDrainStarted = false;
    int64_t totalFramesOutput = 0; // Total frames output for position tracking
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;
    int repeatMode = 0;
    bool hasLoopPoint = false;
    double loopStartSeconds = 0.0;
    double loopEndSeconds = 0.0;
    bool gaplessRepeatTrack = false;

    mutable std::mutex decodeMutex;

    bool initResampler();
    void freeResampler();
    bool seekInternalLocked(double seconds);
    void rebuildToggleChannelsLocked();
    int decodeFrame(); // Decodes one frame and appends to sampleBuffer. Returns 0 on success, <0 on error/EOF
};

#endif //SILICONPLAYER_FFMPEGDECODER_H
