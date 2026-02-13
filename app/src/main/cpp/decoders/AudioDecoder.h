#ifndef SILICONPLAYER_AUDIODECODER_H
#define SILICONPLAYER_AUDIODECODER_H

#include <cstdint>
#include <string>

class AudioDecoder {
public:
    static constexpr int REPEAT_CAP_TRACK = 1 << 0;
    static constexpr int REPEAT_CAP_LOOP_POINT = 1 << 1;

    virtual ~AudioDecoder() = default;

    virtual bool open(const char* path) = 0;
    virtual void close() = 0;

    // Reads interleaved float samples into buffer. Returns number of frames read.
    // buffer size must be at least numFrames * getChannelCount()
    virtual int read(float* buffer, int numFrames) = 0;

    virtual void seek(double seconds) = 0;
    virtual double getDuration() = 0;
    virtual int getSampleRate() = 0;
    virtual int getBitDepth() { return 0; }
    virtual std::string getBitDepthLabel() { return "Unknown"; }
    virtual int getDisplayChannelCount() { return getChannelCount(); }
    virtual int getChannelCount() = 0;
    virtual std::string getTitle() = 0;
    virtual std::string getArtist() = 0;
    virtual void setOutputSampleRate(int /*sampleRate*/) {}
    virtual void setRepeatMode(int /*mode*/) {}
    virtual int getRepeatModeCapabilities() const { return REPEAT_CAP_TRACK; }
    virtual double getPlaybackPositionSeconds() { return -1.0; }

    // Configuration
    virtual void setOption(const char* /*name*/, const char* /*value*/) {}
    virtual const char* getName() const = 0; // Instance name
};

#endif //SILICONPLAYER_AUDIODECODER_H
