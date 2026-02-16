#ifndef SILICONPLAYER_GMEDECODER_H
#define SILICONPLAYER_GMEDECODER_H

#include "AudioDecoder.h"
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

struct Music_Emu;

class GmeDecoder : public AudioDecoder {
public:
    GmeDecoder();
    ~GmeDecoder() override;

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
    std::string getComposer() override;
    std::string getGenre() override;
    std::string getSystemName();
    std::string getGameName();
    std::string getCopyright();
    std::string getComment();
    std::string getDumper();
    int getTrackCountInfo();
    int getVoiceCountInfo();
    bool getHasLoopPointInfo();
    int getLoopStartMsInfo();
    int getLoopLengthMsInfo();
    void setOutputSampleRate(int sampleRate) override;
    void setOption(const char* name, const char* value) override;
    int getOptionApplyPolicy(const char* name) const override;
    int getPlaybackCapabilities() const override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override;

    const char* getName() const override { return "Game Music Emu"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    Music_Emu* emu = nullptr;
    mutable std::mutex decodeMutex;

    double duration = 0.0;
    int bitDepth = 16;
    int channels = 2;
    int trackCount = 0;
    int activeTrack = 0;
    std::atomic<int> repeatMode { 0 }; // 0 none, 1 repeat track, 2 repeat at loop point
    bool pendingTerminalEnd = false;
    int loopStartMs = -1;
    int loopLengthMs = -1;
    bool hasLoopPoint = false;
    bool isSpcTrack = false;
    bool durationReliable = true;
    double playbackPositionSeconds = 0.0;
    int lastTellMs = -1;
    double tempo = 1.0;
    double stereoDepth = 0.0;
    bool echoEnabled = true;
    bool accuracyEnabled = false;
    double eqTrebleDb = 0.0;
    double eqBassHz = 90.0;
    bool spcUseBuiltInFade = false;
    int unknownDurationSeconds = 180;

    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::string systemName;
    std::string gameName;
    std::string copyrightText;
    std::string commentText;
    std::string dumper;
    int voiceCount = 0;

    void closeInternal();
    bool applyTrackInfoLocked(int trackIndex);
    void applyRepeatBehaviorLocked();
    void applyCoreOptionsLocked();
    int resolveOpenSampleRateLocked(const char* path) const;

    int requestedSampleRate = 48000;
    int activeSampleRate = 48000;
    int spcInterpolation = 0;
    bool spcUseNativeSampleRate = true;
};

#endif // SILICONPLAYER_GMEDECODER_H
