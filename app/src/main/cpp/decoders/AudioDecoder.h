#ifndef SILICONPLAYER_AUDIODECODER_H
#define SILICONPLAYER_AUDIODECODER_H

#include <cstdint>

class AudioDecoder {
public:
    virtual ~AudioDecoder() = default;

    virtual bool open(const char* path) = 0;
    virtual void close() = 0;

    // Reads interleaved float samples into buffer. Returns number of frames read.
    // buffer size must be at least numFrames * getChannelCount()
    virtual int read(float* buffer, int numFrames) = 0;

    virtual void seek(double seconds) = 0;
    virtual double getDuration() = 0;
    virtual int getSampleRate() = 0;
    virtual int getChannelCount() = 0;
};

#endif //SILICONPLAYER_AUDIODECODER_H
