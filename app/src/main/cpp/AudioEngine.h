#ifndef SILICONPLAYER_AUDIOENGINE_H
#define SILICONPLAYER_AUDIOENGINE_H

#include <aaudio/AAudio.h>
#include <cstdint>
#include <thread>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include <memory>
#include <vector>
#include <unordered_map>
#include <array>
#include <chrono>
#include "decoders/AudioDecoder.h"

struct SwrContext;

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool start();
    bool startWithPauseResumeFade(int durationMs, float attenuationDb);
    void stop();
    void stopWithPauseResumeFade(int durationMs, float attenuationDb);
    bool isEnginePlaying() const;
    void restart();
    void setUrl(const char* url);
    double getDurationSeconds();
    double getPositionSeconds();
    void seekToSeconds(double seconds);
    bool isSeekInProgress() const;
    void setLooping(bool enabled);
    void setRepeatMode(int mode);
    int getRepeatModeCapabilities();
    int getPlaybackCapabilities();
    void setCoreOutputSampleRate(const std::string& coreName, int sampleRateHz);
    void setCoreOption(const std::string& coreName, const std::string& optionName, const std::string& optionValue);
    int getCoreOptionApplyPolicy(const std::string& coreName, const std::string& optionName);
    int getCoreCapabilities(const std::string& coreName);
    int getCoreFixedSampleRateHz(const std::string& coreName);
    void setAudioPipelineConfig(int backendPreference, int performanceMode, int bufferPreset, int resamplerPreference, bool allowFallback);
    bool consumeNaturalEndEvent();
    std::string getTitle();
    std::string getArtist();
    std::string getComposer();
    std::string getGenre();
    int getSampleRate();
    int getDisplayChannelCount();
    int getChannelCount();
    int getBitDepth();
    std::string getBitDepthLabel();
    std::string getCurrentDecoderName();
    int getSubtuneCount();
    int getCurrentSubtuneIndex();
    bool selectSubtune(int index);
    std::string getSubtuneTitle(int index);
    std::string getSubtuneArtist(int index);
    double getSubtuneDurationSeconds(int index);
    int getDecoderRenderSampleRateHz() const;
    int getOutputStreamSampleRateHz() const;
    std::string getOpenMptModuleTypeLong();
    std::string getOpenMptTracker();
    std::string getOpenMptSongMessage();
    int getOpenMptOrderCount();
    int getOpenMptPatternCount();
    int getOpenMptInstrumentCount();
    int getOpenMptSampleCount();
    std::string getOpenMptInstrumentNames();
    std::string getOpenMptSampleNames();
    std::vector<float> getOpenMptChannelVuLevels();
    std::vector<float> getChannelScopeSamples(int samplesPerChannel);
    std::vector<int32_t> getChannelScopeTextState(int maxChannels);
    std::vector<std::string> getDecoderToggleChannelNames();
    std::vector<uint8_t> getDecoderToggleChannelAvailability();
    void setDecoderToggleChannelMuted(int channelIndex, bool enabled);
    bool getDecoderToggleChannelMuted(int channelIndex);
    void clearDecoderToggleChannelMutes();
    std::string getVgmGameName();
    std::string getVgmSystemName();
    std::string getVgmReleaseDate();
    std::string getVgmEncodedBy();
    std::string getVgmNotes();
    std::string getVgmFileVersion();
    int getVgmDeviceCount();
    std::string getVgmUsedChipList();
    bool getVgmHasLoopPoint();
    std::string getFfmpegCodecName();
    std::string getFfmpegContainerName();
    std::string getFfmpegSampleFormatName();
    std::string getFfmpegChannelLayoutName();
    std::string getFfmpegEncoderName();
    std::string getGmeSystemName();
    std::string getGmeGameName();
    std::string getGmeCopyright();
    std::string getGmeComment();
    std::string getGmeDumper();
    int getGmeTrackCount();
    int getGmeVoiceCount();
    bool getGmeHasLoopPoint();
    int getGmeLoopStartMs();
    int getGmeLoopLengthMs();
    std::string getLazyUsf2GameName();
    std::string getLazyUsf2Copyright();
    std::string getLazyUsf2Year();
    std::string getLazyUsf2UsfBy();
    std::string getLazyUsf2LengthTag();
    std::string getLazyUsf2FadeTag();
    bool getLazyUsf2EnableCompare();
    bool getLazyUsf2EnableFifoFull();
    std::string getVio2sfGameName();
    std::string getVio2sfCopyright();
    std::string getVio2sfYear();
    std::string getVio2sfComment();
    std::string getVio2sfLengthTag();
    std::string getVio2sfFadeTag();
    std::string getSidFormatName();
    std::string getSidClockName();
    std::string getSidSpeedName();
    std::string getSidCompatibilityName();
    std::string getSidBackendName();
    int getSidChipCount();
    std::string getSidModelSummary();
    std::string getSidCurrentModelSummary();
    std::string getSidBaseAddressSummary();
    std::string getSidCommentSummary();
    std::string getSc68FormatName();
    std::string getSc68HardwareName();
    std::string getSc68PlatformName();
    std::string getSc68ReplayName();
    int getSc68ReplayRateHz();
    int getSc68TrackCount();
    std::string getSc68AlbumName();
    std::string getSc68Year();
    std::string getSc68Ripper();
    std::string getSc68Converter();
    std::string getSc68Timer();
    bool getSc68CanAsid();
    bool getSc68UsesYm();
    bool getSc68UsesSte();
    bool getSc68UsesAmiga();
    std::string getAdplugDescription();
    int getAdplugPatternCount();
    int getAdplugCurrentPattern();
    int getAdplugOrderCount();
    int getAdplugCurrentOrder();
    int getAdplugCurrentRow();
    int getAdplugCurrentSpeed();
    int getAdplugInstrumentCount();
    std::string getAdplugInstrumentNames();

    // Bitrate information
    int64_t getTrackBitrate();
    bool isTrackVBR();

    // Gain control
    void setMasterGain(float gainDb);
    void setPluginGain(float gainDb);
    void setSongGain(float gainDb);
    void setForceMono(bool enabled);
    void setMasterChannelMute(int channelIndex, bool enabled);
    void setMasterChannelSolo(int channelIndex, bool enabled);
    void setEndFadeApplyToAllTracks(bool enabled);
    void setEndFadeDurationMs(int durationMs);
    void setEndFadeCurve(int curve);
    std::vector<float> getVisualizationWaveformScope(int channelIndex, int windowMs, int triggerMode) const;
    std::vector<float> getVisualizationBars() const;
    std::vector<float> getVisualizationVuLevels() const;
    int getVisualizationChannelCount() const;
    float getMasterGain() const;
    float getPluginGain() const;
    float getSongGain() const;
    bool getForceMono() const;
    bool getMasterChannelMute(int channelIndex) const;
    bool getMasterChannelSolo(int channelIndex) const;

private:
    AAudioStream *stream = nullptr;
    int streamSampleRate = 48000;
    int streamChannelCount = 2;
    int outputBackendPreference = 0; // 0 auto, 1 aaudio, 2 opensl, 3 audiotrack
    int outputPerformanceMode = 2; // 0 auto, 1 low-latency, 2 none, 3 power-saving
    int outputBufferPreset = 0; // 0 auto, 1 small, 2 medium, 3 large
    int outputResamplerPreference = 1; // 1 built-in, 2 sox
    bool outputAllowFallback = true;
    std::atomic<bool> isPlaying { false };
    std::atomic<bool> looping { false };
    std::atomic<int> repeatMode { 0 };
    std::atomic<double> positionSeconds { 0.0 };
    std::atomic<double> cachedDurationSeconds { 0.0 };

    std::unique_ptr<AudioDecoder> decoder;
    std::mutex decoderMutex;
    std::unordered_map<std::string, int> coreOutputSampleRateHz;
    std::unordered_map<std::string, std::unordered_map<std::string, std::string>> coreOptions;
    int decoderRenderSampleRate = 48000;
    std::vector<float> resampleInputBuffer;
    int resampleInputStartFrame = 0;
    double resampleInputPosition = 0.0;
    std::vector<float> resampleDecodeScratch;
    int64_t sharedAbsoluteInputPosition = 0;  // Frames consumed, shared between resamplers
    double sharedAbsoluteInputPositionBaseSeconds = 0.0;
    SwrContext* outputSoxrContext = nullptr;
    int outputSoxrInputRate = 0;
    int outputSoxrOutputRate = 0;
    int outputSoxrChannels = 0;
    bool outputSoxrUnavailable = false;
    bool resamplerPathLoggedForCurrentTrack = false;
    bool resamplerNoOpLoggedForCurrentTrack = false;
    double pendingBackwardTimelineTargetSeconds = -1.0;
    int pendingBackwardTimelineConfirmations = 0;
    double outputClockSeconds = 0.0;
    bool timelineSmootherInitialized = false;
    double timelineSmoothedSeconds = 0.0;
    std::atomic<bool> naturalEndPending { false };

    // Gain control state
    std::atomic<float> masterGainDb { 0.0f };
    std::atomic<float> pluginGainDb { 0.0f };
    std::atomic<float> songGainDb { 0.0f };
    std::atomic<bool> forceMono { false };
    std::atomic<bool> masterMuteLeft { false };
    std::atomic<bool> masterMuteRight { false };
    std::atomic<bool> masterSoloLeft { false };
    std::atomic<bool> masterSoloRight { false };
    std::atomic<bool> endFadeApplyToAllTracks { false };
    std::atomic<int> endFadeDurationMs { 10000 };
    std::atomic<int> endFadeCurve { 0 }; // 0 linear, 1 ease-in, 2 ease-out
    mutable std::mutex visualizationMutex;
    std::array<float, 256> visualizationWaveformLeft {};
    std::array<float, 256> visualizationWaveformRight {};
    std::array<float, 16384> visualizationScopeHistoryLeft {};
    std::array<float, 16384> visualizationScopeHistoryRight {};
    int visualizationScopeWriteIndex = 0;
    mutable std::array<int, 2> visualizationScopePrevTriggerIndex { -1, -1 };
    std::array<float, 256> visualizationBars {};
    std::array<float, 2> visualizationVuLevels {};
    std::atomic<int> visualizationChannelCount { 2 };
    std::array<float, 4096> visualizationMonoHistory {};
    int visualizationMonoWriteIndex = 0;
    int visualizationCallbacksSinceAnalysis = 0;
    mutable std::atomic<int64_t> visualizationLastRequestNs { 0 };

    int resolveOutputSampleRateForCore(const std::string& coreName) const;
    void reconfigureStream(bool resumePlayback);
    void applyStreamBufferPreset();
    void resetResamplerStateLocked(bool preserveBuffer = false);
    bool ensureOutputSoxrContextLocked(int channels, int inputRate, int outputRate);
    void freeOutputSoxrContextLocked();
    int readFromDecoderLocked(float* buffer, int numFrames, int channels, bool& reachedEnd);
    void renderResampledLocked(float* outputData, int32_t numFrames, int channels, int streamRate, bool& reachedEnd);
    void renderSoxrResampledLocked(float* outputData, int32_t numFrames, int channels, int streamRate, int renderRate, bool& reachedEnd);
    void recoverStreamIfNeeded();
    void clearRenderQueue();
    void appendRenderQueue(const float* data, int numFrames, int channels);
    int popRenderQueue(float* outputData, int numFrames, int channels);
    int renderQueueFrames() const;
    void renderWorkerLoop();
    void updateRenderQueueTuning();

    void createStream();
    void closeStream();

    // Gain processing helpers
    static float dbToGain(float db);
    float computeEndFadeGainLocked(double playbackPositionSeconds) const;
    void beginPauseResumeFadeLocked(bool fadeIn, int streamRate, int durationMs, float attenuationDb);
    float nextPauseResumeFadeGainLocked();
    void applyGain(float* buffer, int numFrames, int channels, float extraGain = 1.0f);
    void applyMasterChannelRouting(float* buffer, int numFrames, int channels);
    void applyMonoDownmix(float* buffer, int numFrames, int channels);
    void updateVisualizationDataLocked(const float* buffer, int numFrames, int channels);
    void markVisualizationRequested() const;
    bool shouldUpdateVisualization() const;

    // Callback
    static aaudio_data_callback_result_t dataCallback(
            AAudioStream *stream,
            void *userData,
            void *audioData,
            int32_t numFrames);
    static void errorCallback(
            AAudioStream *stream,
            void *userData,
            aaudio_result_t error);

    float phase = 0.0f;
    std::atomic<bool> streamNeedsRebuild { false };
    std::atomic<bool> resumeAfterRebuild { false };
    std::atomic<uint64_t> decoderSerial { 0 };
    std::thread seekWorkerThread;
    std::mutex seekWorkerMutex;
    std::condition_variable seekWorkerCv;
    bool seekWorkerStop = false;
    bool seekRequestPending = false;
    double seekRequestSeconds = 0.0;
    uint64_t seekRequestDecoderSerial = 0;
    std::atomic<bool> seekInProgress { false };
    std::atomic<bool> seekAbortRequested { false };
    std::atomic<bool> stopStreamAfterSeek { false };
    std::vector<float> asyncSeekDiscardBuffer;
    double runAsyncSeekLocked(double targetSeconds);
    void seekWorkerLoop();
    std::thread renderWorkerThread;
    mutable std::mutex renderQueueMutex;
    std::condition_variable renderWorkerCv;
    std::vector<float> renderQueueSamples;
    size_t renderQueueOffset = 0;
    std::atomic<int> renderWorkerChunkFrames { 1024 };
    std::atomic<int> renderWorkerTargetFrames { 8192 };
    std::atomic<int64_t> renderQueueRecoveryBoostUntilNs { 0 };
    std::atomic<uint64_t> renderQueueUnderrunCount { 0 };
    std::atomic<uint64_t> renderQueueUnderrunFrames { 0 };
    std::atomic<uint64_t> renderQueueCallbackCount { 0 };
#ifndef NDEBUG
    std::atomic<int64_t> renderQueueLastUnderrunLogNs { 0 };
#endif
    bool renderWorkerStop = false;
    std::atomic<bool> renderTerminalStopPending { false };
    std::atomic<bool> pendingResumeFadeOnStart { false };
    std::atomic<int> pendingResumeFadeDurationMs { 100 };
    std::atomic<float> pendingResumeFadeAttenuationDb { 16.0f };
    std::atomic<bool> pendingPauseFadeRequest { false };
    std::atomic<int> pendingPauseFadeDurationMs { 100 };
    std::atomic<float> pendingPauseFadeAttenuationDb { 16.0f };
    bool pauseResumeFadeOutStopPending = false;
    int pauseResumeFadeTotalFrames = 0;
    int pauseResumeFadeProcessedFrames = 0;
    float pauseResumeFadeFromGain = 1.0f;
    float pauseResumeFadeToGain = 1.0f;
};

#endif //SILICONPLAYER_AUDIOENGINE_H
