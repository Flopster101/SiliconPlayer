#ifndef SILICONPLAYER_AUDIODECODER_H
#define SILICONPLAYER_AUDIODECODER_H

#include <cstdint>
#include <string>
#include <vector>

class AudioDecoder {
public:
    static constexpr int REPEAT_CAP_TRACK = 1 << 0;
    static constexpr int REPEAT_CAP_LOOP_POINT = 1 << 1;
    static constexpr int PLAYBACK_CAP_SEEK = 1 << 0;
    static constexpr int PLAYBACK_CAP_RELIABLE_DURATION = 1 << 1;
    static constexpr int PLAYBACK_CAP_LIVE_REPEAT_MODE = 1 << 2;
    static constexpr int PLAYBACK_CAP_CUSTOM_SAMPLE_RATE = 1 << 3;
    static constexpr int PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE = 1 << 4;
    static constexpr int PLAYBACK_CAP_FIXED_SAMPLE_RATE = 1 << 5;
    static constexpr int PLAYBACK_CAP_DIRECT_SEEK = 1 << 6;
    static constexpr int OPTION_APPLY_LIVE = 0;
    static constexpr int OPTION_APPLY_REQUIRES_PLAYBACK_RESTART = 1;
    enum class TimelineMode {
        Unknown = 0,
        ContinuousLinear = 1,
        Discontinuous = 2
    };

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
    virtual int getSubtuneCount() const { return 1; }
    virtual int getCurrentSubtuneIndex() const { return 0; }
    virtual bool selectSubtune(int /*index*/) { return false; }
    virtual std::string getSubtuneTitle(int /*index*/) { return ""; }
    virtual std::string getSubtuneArtist(int /*index*/) { return ""; }
    virtual double getSubtuneDurationSeconds(int /*index*/) { return 0.0; }
    virtual std::string getTitle() = 0;
    virtual std::string getArtist() = 0;
    virtual std::string getComposer() { return ""; }
    virtual std::string getGenre() { return ""; }
    virtual void setOutputSampleRate(int /*sampleRate*/) {}
    virtual void setRepeatMode(int /*mode*/) {}
    virtual int getRepeatModeCapabilities() const { return REPEAT_CAP_TRACK; }
    virtual int getPlaybackCapabilities() const {
        return PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_RELIABLE_DURATION |
               PLAYBACK_CAP_LIVE_REPEAT_MODE;
    }
    virtual int getFixedSampleRateHz() const { return 0; }
    virtual double getPlaybackPositionSeconds() { return -1.0; }
    virtual TimelineMode getTimelineMode() const { return TimelineMode::Unknown; }

    // Configuration
    virtual void setOption(const char* /*name*/, const char* /*value*/) {}
    virtual int getOptionApplyPolicy(const char* /*name*/) const { return OPTION_APPLY_LIVE; }
    // Flat per-channel state payload for channel-scope text overlays.
    // Stride/field semantics are defined on the app side.
    virtual std::vector<int32_t> getChannelScopeTextState(int /*maxChannels*/) { return {}; }
    virtual const char* getName() const = 0; // Instance name
};

#endif //SILICONPLAYER_AUDIODECODER_H
