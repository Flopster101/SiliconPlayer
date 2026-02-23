#include "AudioEngine.h"
#include "decoders/DecoderRegistry.h"

#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cerrno>
#include <thread>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <unistd.h>

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    pid_t currentThreadId() {
#ifdef SYS_gettid
        return static_cast<pid_t>(syscall(SYS_gettid));
#else
        return getpid();
#endif
    }

    void promoteThreadForAudio(const char* role, int targetNice) {
        const pid_t tid = currentThreadId();
        const int before = getpriority(PRIO_PROCESS, tid);
        errno = 0;
        if (setpriority(PRIO_PROCESS, tid, targetNice) == 0) {
            const int after = getpriority(PRIO_PROCESS, tid);
            LOGD(
                    "Thread priority promoted for %s: tid=%d nice(before=%d after=%d target=%d)",
                    role,
                    static_cast<int>(tid),
                    before,
                    after,
                    targetNice
            );
            return;
        }

        const int err = errno;
        LOGD(
                "Thread priority promotion skipped for %s: tid=%d targetNice=%d errno=%d",
                role,
                static_cast<int>(tid),
                targetNice,
                err
        );
    }
}

bool AudioEngine::start() {
    recoverStreamIfNeeded();

    if (stream == nullptr || streamNeedsRebuild.load()) {
        closeStream();
        createStream();
        streamNeedsRebuild.store(false);
    }
    if (stream != nullptr) {
        if (isStreamDisconnectedOrClosed()) {
            closeStream();
            createStream();
        }

        {
            std::lock_guard<std::mutex> lock(decoderMutex);
            if (decoder) {
                const int desiredRate = resolveOutputSampleRateForCore(decoder->getName());
                decoder->setOutputSampleRate(desiredRate);
                decoderRenderSampleRate = decoder->getSampleRate();
                resetResamplerStateLocked();
                const double durationNow = decoder->getDuration();
                const bool loopPointRepeatMode = repeatMode.load() == 2;
                if (durationNow > 0.0 &&
                    !loopPointRepeatMode &&
                    positionSeconds.load() >= (durationNow - 0.01)) {
                    decoder->seek(0.0);
                    positionSeconds.store(0.0);
                    resetResamplerStateLocked();
                    sharedAbsoluteInputPositionBaseSeconds = 0.0;
                }
            }
            outputLimiterGain = 1.0f;
            pauseResumeFadeTotalFrames = 0;
            pauseResumeFadeProcessedFrames = 0;
            pauseResumeFadeFromGain = 1.0f;
            pauseResumeFadeToGain = 1.0f;
            pauseResumeFadeOutStopPending = false;
        }

        // Prime render queue before starting callback-driven playback.
        // This avoids audible startup gaps for decoders that need a short warmup
        // (notably SID cores) and reduces first-second underruns.
        clearRenderQueue();
        isPlaying = true;
        naturalEndPending.store(false);
        const int startupChunkFrames = std::max(256, renderWorkerChunkFrames.load(std::memory_order_relaxed));
        const int startupBaseTargetFrames = std::max(
                startupChunkFrames * 2,
                std::min(renderWorkerTargetFrames.load(std::memory_order_relaxed), 4096)
        );
        int startupPrerollFrames = 0;
        if (streamStartupPrerollPending) {
            int burstFrames = getStreamBurstFrames();
            if (burstFrames <= 0) {
                burstFrames = startupChunkFrames;
            }
            startupPrerollFrames = std::clamp(burstFrames, 128, 2048);
            std::vector<float> prerollSilence(static_cast<size_t>(startupPrerollFrames) * 2u, 0.0f);
            appendRenderQueue(prerollSilence.data(), startupPrerollFrames, 2);
            LOGD("Applying one-time startup preroll: %d frames", startupPrerollFrames);
        }
        const int startupTargetFrames = startupBaseTargetFrames + startupPrerollFrames;
        renderWorkerCv.notify_one();
        const auto prefillDeadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(220);
        while (renderQueueFrames() < startupTargetFrames &&
               std::chrono::steady_clock::now() < prefillDeadline) {
            std::this_thread::sleep_for(std::chrono::milliseconds(2));
            renderWorkerCv.notify_one();
        }

        if (!requestStreamStart()) {
            closeStream();
            createStream();
            if (stream == nullptr) {
                isPlaying = false;
                return false;
            }
            if (!requestStreamStart()) {
                LOGE("Retry start failed");
                isPlaying = false;
                return false;
            }
        }
        streamStartupPrerollPending = false;
        renderWorkerCv.notify_all();
        return true;
    }
    return false;
}

void AudioEngine::stop() {
    pendingPauseFadeRequest.store(false, std::memory_order_relaxed);
    pendingResumeFadeOnStart.store(false, std::memory_order_relaxed);
    const bool wasSeeking = seekInProgress.load();
    if (wasSeeking) {
        decoderSerial.fetch_add(1);
        {
            std::lock_guard<std::mutex> lock(seekWorkerMutex);
            seekAbortRequested.store(true);
            seekRequestPending = false;
        }
        stopStreamAfterSeek.store(true);
        seekWorkerCv.notify_one();
        isPlaying.store(false);
        naturalEndPending.store(false);
        clearRenderQueue();
        renderWorkerCv.notify_all();
        return;
    }

    if (stream != nullptr) {
        resumeAfterRebuild.store(false);
        requestStreamStop();
        isPlaying = false;
        naturalEndPending.store(false);
        clearRenderQueue();
        renderWorkerCv.notify_all();
    }
}

bool AudioEngine::startWithPauseResumeFade(int durationMs, float attenuationDb) {
    if (isPlaying.load()) {
        return true;
    }
    pendingResumeFadeDurationMs.store(durationMs, std::memory_order_relaxed);
    pendingResumeFadeAttenuationDb.store(attenuationDb, std::memory_order_relaxed);
    pendingResumeFadeOnStart.store(true, std::memory_order_relaxed);
    return start();
}

void AudioEngine::stopWithPauseResumeFade(int durationMs, float attenuationDb) {
    if (!isPlaying.load() || seekInProgress.load()) {
        stop();
        return;
    }
    pendingPauseFadeDurationMs.store(durationMs, std::memory_order_relaxed);
    pendingPauseFadeAttenuationDb.store(attenuationDb, std::memory_order_relaxed);
    pendingPauseFadeRequest.store(true, std::memory_order_relaxed);
    renderWorkerCv.notify_all();
}

bool AudioEngine::isEnginePlaying() const {
    return isPlaying.load();
}

void AudioEngine::setUrl(const char* url) {
    LOGD("URL set to: %s", url);

    // Ensure background seek work is fully quiesced before replacing decoder.
    // This prevents worker-thread reads from racing decoder teardown.
    if (seekInProgress.load()) {
        {
            std::lock_guard<std::mutex> lock(seekWorkerMutex);
            seekAbortRequested.store(true);
            seekRequestPending = false;
        }
        seekWorkerCv.notify_one();
        while (seekInProgress.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }

    decoderSerial.fetch_add(1);
    clearRenderQueue();

    // Drop any previously loaded decoder first. If opening the new source fails,
    // playback should not continue from stale decoder state.
    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        decoder.reset();
        cachedDurationSeconds.store(0.0);
        resetResamplerStateLocked();
        outputLimiterGain = 1.0f;
        decoderRenderSampleRate = streamSampleRate;
        positionSeconds.store(0.0);
        sharedAbsoluteInputPositionBaseSeconds = 0.0;
        outputClockSeconds = 0.0;
        timelineSmoothedSeconds = 0.0;
        timelineSmootherInitialized = false;
        naturalEndPending.store(false);
    }
    {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekAbortRequested.store(false);
        seekRequestPending = false;
        seekInProgress.store(false);
        stopStreamAfterSeek.store(false);
    }

    auto newDecoder = DecoderRegistry::getInstance().createDecoder(url);
    if (newDecoder) {
        const int targetRate = resolveOutputSampleRateForCore(newDecoder->getName());
        std::unordered_map<std::string, std::string> optionsForDecoder;
        {
            std::lock_guard<std::mutex> lock(decoderMutex);
            const auto optionsIt = coreOptions.find(newDecoder->getName());
            if (optionsIt != coreOptions.end()) {
                optionsForDecoder = optionsIt->second;
            }
        }

        newDecoder->setOutputSampleRate(targetRate);
        if (!optionsForDecoder.empty()) {
            for (const auto& [name, value] : optionsForDecoder) {
                newDecoder->setOption(name.c_str(), value.c_str());
            }
        }
        if (!newDecoder->open(url)) {
            LOGE("Failed to open file: %s", url);
            return;
        }
        std::lock_guard<std::mutex> lock(decoderMutex);
        decoderRenderSampleRate = newDecoder->getSampleRate();
        newDecoder->setRepeatMode(repeatMode.load());
        if (!optionsForDecoder.empty()) {
            for (const auto& [name, value] : optionsForDecoder) {
                newDecoder->setOption(name.c_str(), value.c_str());
            }
        }
        decoder = std::move(newDecoder);
        cachedDurationSeconds.store(decoder->getDuration());
        resetResamplerStateLocked();
        positionSeconds.store(0.0);
        sharedAbsoluteInputPositionBaseSeconds = 0.0;
        outputClockSeconds = 0.0;
        timelineSmoothedSeconds = 0.0;
        timelineSmootherInitialized = false;
        naturalEndPending.store(false);
        renderWorkerCv.notify_one();
    } else {
        LOGE("Failed to create decoder for file: %s", url);
    }
}

void AudioEngine::restart() {
    stop();
    start();
}

double AudioEngine::getDurationSeconds() {
    const_cast<AudioEngine*>(this)->recoverStreamIfNeeded();
    if (seekInProgress.load()) {
        return cachedDurationSeconds.load();
    }
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        // Avoid blocking real-time audio callback. Return last known value.
        return cachedDurationSeconds.load();
    }
    if (!decoder) {
        return 0.0;
    }
    const double duration = decoder->getDuration();
    cachedDurationSeconds.store(duration);
    return duration;
}

double AudioEngine::getPositionSeconds() {
    recoverStreamIfNeeded();
    return positionSeconds.load();
}

bool AudioEngine::isSeekInProgress() const {
    return seekInProgress.load();
}

void AudioEngine::seekToSeconds(double seconds) {
    const uint64_t targetDecoderSerial = decoderSerial.load();
    const double normalizedTarget = std::max(0.0, seconds);
    bool handledDirectSeek = false;
    clearRenderQueue();

    // Cancel any pending async-seek request first so a stale worker cycle
    // cannot overwrite a direct-seek result.
    {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekAbortRequested.store(true);
        seekRequestPending = false;
    }
    seekWorkerCv.notify_one();

    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder) {
            const int capabilities = decoder->getPlaybackCapabilities();
            if ((capabilities & AudioDecoder::PLAYBACK_CAP_DIRECT_SEEK) != 0 &&
                (capabilities & AudioDecoder::PLAYBACK_CAP_SEEK) != 0) {
                decoder->seek(normalizedTarget);
                const double decoderPosition = decoder->getPlaybackPositionSeconds();
                const double resolvedPosition = decoderPosition >= 0.0 ? decoderPosition : normalizedTarget;
                const double duration = decoder->getDuration();
                cachedDurationSeconds.store(duration);
                resetResamplerStateLocked();
                positionSeconds.store(resolvedPosition);
                sharedAbsoluteInputPositionBaseSeconds = resolvedPosition;
                outputClockSeconds = resolvedPosition;
                timelineSmoothedSeconds = resolvedPosition;
                timelineSmootherInitialized = false;
                naturalEndPending.store(false);
                handledDirectSeek = true;
            }
        }
    }

    if (handledDirectSeek) {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekAbortRequested.store(false);
        seekInProgress.store(false);
        stopStreamAfterSeek.store(false);
        renderWorkerCv.notify_one();
        return;
    }

    positionSeconds.store(normalizedTarget);
    naturalEndPending.store(false);
    {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekAbortRequested.store(false);
        seekRequestSeconds = normalizedTarget;
        seekRequestDecoderSerial = targetDecoderSerial;
        seekRequestPending = true;
        seekInProgress.store(true);
    }
    seekWorkerCv.notify_one();
    renderWorkerCv.notify_one();
}

double AudioEngine::runAsyncSeekLocked(double targetSeconds) {
    if (!decoder) {
        return 0.0;
    }

    const int capabilities = decoder->getPlaybackCapabilities();
    if ((capabilities & AudioDecoder::PLAYBACK_CAP_SEEK) == 0) {
        const double position = decoder->getPlaybackPositionSeconds();
        return position >= 0.0 ? position : 0.0;
    }
    const double clampedTarget = std::max(0.0, targetSeconds);

    // Prefer direct/random-access seek when a decoder can do it reliably.
    // We still execute it on the async seek worker to keep UI interactions non-blocking.
    if ((capabilities & AudioDecoder::PLAYBACK_CAP_DIRECT_SEEK) != 0) {
        decoder->seek(clampedTarget);
        const double decoderPosition = decoder->getPlaybackPositionSeconds();
        return decoderPosition >= 0.0 ? decoderPosition : clampedTarget;
    }

    decoder->seek(0.0);
    const int channels = std::max(1, decoder->getChannelCount());
    int decoderRate = decoderRenderSampleRate > 0 ? decoderRenderSampleRate : decoder->getSampleRate();
    if (decoderRate <= 0) {
        decoderRate = 48000;
    }

    const int64_t targetFrames = static_cast<int64_t>(std::llround(clampedTarget * decoderRate));
    int64_t skippedFrames = 0;
    constexpr int kAsyncSeekChunkFrames = 4096;

    while (skippedFrames < targetFrames) {
        {
            std::lock_guard<std::mutex> seekLock(seekWorkerMutex);
            if (seekRequestPending || seekWorkerStop || seekAbortRequested.load()) {
                break;
            }
        }
        const int framesToRead = static_cast<int>(std::min<int64_t>(kAsyncSeekChunkFrames, targetFrames - skippedFrames));
        const size_t neededSamples = static_cast<size_t>(framesToRead) * channels;
        if (asyncSeekDiscardBuffer.size() < neededSamples) {
            asyncSeekDiscardBuffer.resize(neededSamples);
        }
        const int framesRead = decoder->read(asyncSeekDiscardBuffer.data(), framesToRead);
        if (framesRead <= 0) {
            break;
        }
        skippedFrames += framesRead;
    }

    const double decoderPosition = decoder->getPlaybackPositionSeconds();
    if (decoderPosition >= 0.0) {
        return decoderPosition;
    }
    return static_cast<double>(skippedFrames) / static_cast<double>(decoderRate);
}

void AudioEngine::seekWorkerLoop() {
    pthread_setname_np(pthread_self(), "sp_seek");
    // Keep seek worker responsive but below render worker importance.
    promoteThreadForAudio("seek-worker", -8);

    for (;;) {
        double targetSeconds = 0.0;
        uint64_t targetDecoderSerial = 0;
        {
            std::unique_lock<std::mutex> lock(seekWorkerMutex);
            seekWorkerCv.wait(lock, [this]() { return seekWorkerStop || seekRequestPending; });
            if (seekWorkerStop) {
                break;
            }
            targetSeconds = seekRequestSeconds;
            targetDecoderSerial = seekRequestDecoderSerial;
            seekRequestPending = false;
        }

        if (decoder && targetDecoderSerial == decoderSerial.load() && !seekAbortRequested.load()) {
            double resolvedPosition = runAsyncSeekLocked(targetSeconds);
            if (!seekAbortRequested.load()) {
                const double duration = decoder->getDuration();
                if (duration > 0.0 && repeatMode.load() != 2) {
                    resolvedPosition = std::clamp(resolvedPosition, 0.0, duration);
                } else if (resolvedPosition < 0.0) {
                    resolvedPosition = 0.0;
                }
                cachedDurationSeconds.store(duration);
                {
                    std::lock_guard<std::mutex> lock(decoderMutex);
                    resetResamplerStateLocked();
                    positionSeconds.store(resolvedPosition);
                    sharedAbsoluteInputPositionBaseSeconds = resolvedPosition;
                    outputClockSeconds = resolvedPosition;
                    timelineSmoothedSeconds = resolvedPosition;
                    timelineSmootherInitialized = false;
                    naturalEndPending.store(false);
                }
            }
        }

        if (stopStreamAfterSeek.exchange(false) && stream != nullptr) {
            resumeAfterRebuild.store(false);
            requestStreamStop();
        }

        {
            std::lock_guard<std::mutex> lock(seekWorkerMutex);
            if (!seekRequestPending) {
                seekInProgress.store(false);
                seekAbortRequested.store(false);
            }
        }
        renderWorkerCv.notify_one();
    }
}

void AudioEngine::setLooping(bool enabled) {
    setRepeatMode(enabled ? 1 : 0);
}

void AudioEngine::setRepeatMode(int mode) {
    const int normalized = (mode >= 0 && mode <= 3) ? mode : 0;
    const int previousMode = repeatMode.exchange(normalized);
    bool shouldStopForTerminalState = false;
    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder) {
            decoder->setRepeatMode(normalized);

            // If we are leaving LP mode while already at/after track end, apply the
            // newly selected repeat semantics immediately instead of waiting for a
            // future decoder terminal event that may not occur promptly.
            if (previousMode == 2 && normalized != 2) {
                const double durationNow = decoder->getDuration();
                const double currentPosition = positionSeconds.load();
                const double decoderPosition = decoder->getPlaybackPositionSeconds();
                const int playbackCaps = decoder->getPlaybackCapabilities();
                const bool hasDirectSeek = (playbackCaps & AudioDecoder::PLAYBACK_CAP_DIRECT_SEEK) != 0;
                const bool hasReliableDuration =
                        (playbackCaps & AudioDecoder::PLAYBACK_CAP_RELIABLE_DURATION) != 0;
                const bool uiAtOrPastEnd = durationNow > 0.0 && currentPosition >= (durationNow - 0.01);
                const bool decoderAtOrPastEnd =
                        durationNow > 0.0 &&
                        decoderPosition >= 0.0 &&
                        decoderPosition >= (durationNow - 0.01);
                const bool requireDecoderConfirmation = hasDirectSeek && hasReliableDuration;
                const bool atOrPastEnd = requireDecoderConfirmation
                        ? (uiAtOrPastEnd && decoderAtOrPastEnd)
                        : uiAtOrPastEnd;
                if (atOrPastEnd) {
                    if (normalized == 1) {
                        const int subtuneCount = std::max(1, decoder->getSubtuneCount());
                        if (subtuneCount > 1) {
                            const int currentIndex = std::clamp(decoder->getCurrentSubtuneIndex(), 0, subtuneCount - 1);
                            const int nextIndex = (currentIndex + 1) % subtuneCount;
                            if (!decoder->selectSubtune(nextIndex)) {
                                decoder->seek(0.0);
                            }
                        } else {
                            decoder->seek(0.0);
                        }
                        resetResamplerStateLocked();
                        positionSeconds.store(0.0);
                        sharedAbsoluteInputPositionBaseSeconds = 0.0;
                        outputClockSeconds = 0.0;
                        timelineSmoothedSeconds = 0.0;
                        timelineSmootherInitialized = false;
                        naturalEndPending.store(false);
                    } else if (normalized == 3) {
                        decoder->seek(0.0);
                        resetResamplerStateLocked();
                        positionSeconds.store(0.0);
                        sharedAbsoluteInputPositionBaseSeconds = 0.0;
                        outputClockSeconds = 0.0;
                        timelineSmoothedSeconds = 0.0;
                        timelineSmootherInitialized = false;
                        naturalEndPending.store(false);
                    } else if (normalized == 0) {
                        shouldStopForTerminalState = true;
                        naturalEndPending.store(true);
                    }
                }
            }
        }
    }

    if (shouldStopForTerminalState) {
        if (stream != nullptr) {
            requestStreamStop();
        }
        isPlaying.store(false);
    }
}

int AudioEngine::getRepeatModeCapabilities() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return AudioDecoder::REPEAT_CAP_TRACK;
    }
    return decoder->getRepeatModeCapabilities();
}

int AudioEngine::getPlaybackCapabilities() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return AudioDecoder::PLAYBACK_CAP_SEEK |
               AudioDecoder::PLAYBACK_CAP_RELIABLE_DURATION |
               AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE;
    }
    return decoder->getPlaybackCapabilities();
}

int AudioEngine::getTimelineMode() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return static_cast<int>(AudioDecoder::TimelineMode::Unknown);
    }
    return static_cast<int>(decoder->getTimelineMode());
}
