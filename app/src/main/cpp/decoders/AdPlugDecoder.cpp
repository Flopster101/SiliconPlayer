#include "AdPlugDecoder.h"

#include <adplug/adplug.h>
#include <adplug/emuopl.h>
#include <adplug/player.h>

#include <algorithm>
#include <android/log.h>
#include <cmath>
#include <filesystem>

#define LOG_TAG "AdPlugDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::string safeString(const std::string& value) {
    return value;
}
}

AdPlugDecoder::AdPlugDecoder() = default;

AdPlugDecoder::~AdPlugDecoder() {
    close();
}

bool AdPlugDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();

    if (!path) {
        return false;
    }

    sourcePath = path;
    opl = std::make_unique<CEmuopl>(sampleRateHz, true, true);
    player.reset(CAdPlug::factory(sourcePath, opl.get()));
    if (!player) {
        LOGE("CAdPlug::factory failed for file: %s", sourcePath.c_str());
        closeInternalLocked();
        return false;
    }

    title = safeString(player->gettitle());
    if (title.empty()) {
        title = std::filesystem::path(sourcePath).stem().string();
    }
    artist = safeString(player->getauthor());
    composer = artist;
    genre = safeString(player->gettype());

    subtuneCount = std::max(1u, player->getsubsongs());
    currentSubtuneIndex = std::clamp(static_cast<int>(player->getsubsong()), 0, subtuneCount - 1);

    const unsigned long durationMs = player->songlength(currentSubtuneIndex);
    durationReliable = durationMs > 0;
    durationSeconds = durationReliable ? static_cast<double>(durationMs) / 1000.0 : 0.0;

    remainingTickFrames = 0;
    playbackPositionSeconds = 0.0;
    reachedEnd = false;
    pcmScratch.clear();
    return true;
}

void AdPlugDecoder::closeInternalLocked() {
    player.reset();
    opl.reset();
    pcmScratch.clear();
    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    remainingTickFrames = 0;
    durationReliable = false;
    durationSeconds = 0.0;
    playbackPositionSeconds = 0.0;
    reachedEnd = false;
}

void AdPlugDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

int AdPlugDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);

    if (!player || !opl || !buffer || numFrames <= 0) {
        return 0;
    }

    int framesWritten = 0;
    int restartAttempts = 0;

    while (framesWritten < numFrames) {
        if (remainingTickFrames <= 0) {
            const bool hasNextTick = player->update();
            if (!hasNextTick) {
                const int mode = repeatMode.load();
                if (mode == 1 && restartAttempts < 2) {
                    player->rewind(currentSubtuneIndex);
                    playbackPositionSeconds = 0.0;
                    remainingTickFrames = 0;
                    reachedEnd = false;
                    restartAttempts++;
                    continue;
                }
                reachedEnd = true;
                break;
            }

            const float refreshHz = player->getrefresh();
            const double safeRefreshHz =
                    (std::isfinite(refreshHz) && refreshHz > 0.0f) ? static_cast<double>(refreshHz) : 70.0;
            remainingTickFrames = std::max(
                    1,
                    static_cast<int>(std::lround(static_cast<double>(sampleRateHz) / safeRefreshHz))
            );
        }

        const int framesLeft = numFrames - framesWritten;
        const int chunkFrames = std::min(framesLeft, remainingTickFrames);
        const int chunkSamples = chunkFrames * channels;

        if (static_cast<int>(pcmScratch.size()) < chunkSamples) {
            pcmScratch.resize(chunkSamples);
        }

        opl->update(pcmScratch.data(), chunkFrames);
        for (int sample = 0; sample < chunkSamples; ++sample) {
            buffer[(framesWritten * channels) + sample] =
                    static_cast<float>(pcmScratch[sample]) / 32768.0f;
        }

        remainingTickFrames -= chunkFrames;
        framesWritten += chunkFrames;
        playbackPositionSeconds += static_cast<double>(chunkFrames) / static_cast<double>(sampleRateHz);
    }

    return framesWritten;
}

void AdPlugDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return;
    }

    double targetSeconds = std::max(0.0, seconds);
    if (durationSeconds > 0.0) {
        targetSeconds = std::min(targetSeconds, durationSeconds);
    }
    const auto targetMs = static_cast<unsigned long>(std::llround(targetSeconds * 1000.0));
    player->seek(targetMs);
    playbackPositionSeconds = targetSeconds;
    remainingTickFrames = 0;
    reachedEnd = false;
}

double AdPlugDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationSeconds;
}

int AdPlugDecoder::getSampleRate() {
    return sampleRateHz;
}

int AdPlugDecoder::getBitDepth() {
    return bitDepth;
}

std::string AdPlugDecoder::getBitDepthLabel() {
    return "16 bit";
}

int AdPlugDecoder::getDisplayChannelCount() {
    return channels;
}

int AdPlugDecoder::getChannelCount() {
    return channels;
}

int AdPlugDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return subtuneCount;
}

int AdPlugDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return currentSubtuneIndex;
}

bool AdPlugDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || index < 0 || index >= subtuneCount) {
        return false;
    }
    player->rewind(index);
    currentSubtuneIndex = index;
    remainingTickFrames = 0;
    playbackPositionSeconds = 0.0;
    reachedEnd = false;

    const unsigned long durationMs = player->songlength(currentSubtuneIndex);
    durationReliable = durationMs > 0;
    durationSeconds = durationReliable ? static_cast<double>(durationMs) / 1000.0 : 0.0;
    return true;
}

std::string AdPlugDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) {
        return "";
    }
    return title;
}

std::string AdPlugDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) {
        return "";
    }
    return artist;
}

double AdPlugDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || index < 0 || index >= subtuneCount) {
        return 0.0;
    }
    const unsigned long durationMs = player->songlength(index);
    return durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;
}

std::string AdPlugDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string AdPlugDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string AdPlugDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string AdPlugDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

void AdPlugDecoder::setRepeatMode(int mode) {
    repeatMode.store((mode >= 0 && mode <= 3) ? mode : 0);
}

int AdPlugDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK;
}

int AdPlugDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int capabilities = PLAYBACK_CAP_SEEK | PLAYBACK_CAP_LIVE_REPEAT_MODE | PLAYBACK_CAP_FIXED_SAMPLE_RATE;
    if (durationReliable) {
        capabilities |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return capabilities;
}

int AdPlugDecoder::getFixedSampleRateHz() const {
    return sampleRateHz;
}

double AdPlugDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (durationSeconds > 0.0 && repeatMode.load() != 2) {
        return std::min(playbackPositionSeconds, durationSeconds);
    }
    return playbackPositionSeconds;
}

AudioDecoder::TimelineMode AdPlugDecoder::getTimelineMode() const {
    return TimelineMode::ContinuousLinear;
}

std::vector<std::string> AdPlugDecoder::getSupportedExtensions() {
    // Do not touch CAdPlug::players during process startup:
    // decoder registration runs in global init, and AdPlug's own global
    // player list can still be in static-initialization-order limbo there.
    // Keep this list aligned with external/adplug/src/adplug.cpp (allplayers).
    static const std::vector<std::string> kExtensions = {
            "hsc", "sng", "imf", "wlf", "adlib", "a2m", "a2t", "xms",
            "bam", "cmf", "adl", "d00", "dfm", "hsp", "ksm", "mad",
            "mus", "mdy", "ims", "mdi", "mid", "sci", "laa", "mkj",
            "cff", "dmo", "s3m", "dtm", "mtk", "mtr", "rad", "rac",
            "raw", "sat", "sa2", "xad", "lds", "plx", "m", "rol",
            "xsm", "dro", "pis", "msc", "rix", "mkf", "jbm", "got",
            "vgm", "vgz", "sop", "hsq", "sqx", "sdb", "agd", "ha2"
    };
    return kExtensions;
}
