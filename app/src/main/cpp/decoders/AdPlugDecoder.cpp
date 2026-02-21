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
class TrackingEmuopl final : public CEmuopl {
public:
    explicit TrackingEmuopl(int rate, bool bit16, bool useStereo)
        : CEmuopl(rate, bit16, useStereo) {
        // Most AdPlug tracks are single-chip OPL2. Start in OPL2 so mono
        // content is mirrored to both channels instead of left-only output.
        settype(TYPE_OPL2);
    }

    void setchip(int n) override {
        CEmuopl::setchip(n);
        if (n == 1) {
            // Auto-promote to dual-chip output once a track actually uses chip 1.
            settype(TYPE_DUAL_OPL2);
        }
    }
};

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
    opl = std::make_unique<TrackingEmuopl>(sampleRateHz, true, true);
    player.reset(CAdPlug::factory(sourcePath, opl.get()));
    if (!player) {
        LOGE("CAdPlug::factory failed for file: %s", sourcePath.c_str());
        closeInternalLocked();
        return false;
    }
    player->setEndlessLoopMode(repeatMode.load() == 2);

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
    durationSeconds = durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;

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

    const int mode = repeatMode.load();
    const bool hasReliableDuration = durationSeconds >= 1.0;
    const bool gateVirtualEof =
            (mode == 0) ||
            (mode == 3) ||
            (mode == 1 && std::max(1, subtuneCount) > 1);

    if (hasReliableDuration && mode != 2 && playbackPositionSeconds >= durationSeconds) {
        if (mode == 1 && std::max(1, subtuneCount) == 1) {
            // Repeat Track on single-subtune files: restart at duration boundary.
            player->rewind(currentSubtuneIndex);
            playbackPositionSeconds = 0.0;
            remainingTickFrames = 0;
            reachedEnd = false;
        } else if (gateVirtualEof) {
            return 0;
        }
    }

    auto refreshToTickFrames = [this]() {
        const float refreshHz = player->getrefresh();
        const double safeRefreshHz =
                (std::isfinite(refreshHz) && refreshHz > 0.0f) ? static_cast<double>(refreshHz) : 70.0;
        return std::max(
                1,
                static_cast<int>(std::lround(static_cast<double>(sampleRateHz) / safeRefreshHz))
        );
    };

    int framesWritten = 0;
    int loopRecoveries = 0;
    constexpr int kMaxLoopRecoveriesPerRead = 512;

    while (framesWritten < numFrames) {
        if (remainingTickFrames <= 0) {
            const bool hasNextTick = player->update();
            if (!hasNextTick) {
                if (mode == 1 || mode == 2) {
                    bool recovered = false;
                    // Some AdPlug players expect rewind() with default subsong and do not
                    // reliably recover from rewind(0) after terminal update()==false.
                    for (int attempt = 0; attempt < 2 && !recovered; ++attempt) {
                        if (attempt == 0) {
                            player->rewind(currentSubtuneIndex);
                        } else {
                            player->rewind();
                        }
                        remainingTickFrames = 0;
                        reachedEnd = false;
                        if (mode == 1) {
                            // Repeat Track restarts timeline at track start.
                            playbackPositionSeconds = 0.0;
                        }

                        if (player->update()) {
                            remainingTickFrames = refreshToTickFrames();
                            recovered = true;
                            loopRecoveries = 0;
                        }
                    }

                    if (recovered) {
                        continue;
                    }

                    if (loopRecoveries < kMaxLoopRecoveriesPerRead) {
                        ++loopRecoveries;
                        continue;
                    }
                }
                reachedEnd = true;
                break;
            }
            loopRecoveries = 0;
            remainingTickFrames = refreshToTickFrames();
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
    durationSeconds = durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;
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

std::string AdPlugDecoder::getDescription() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return "";
    }
    return safeString(player->getdesc());
}

int AdPlugDecoder::getPatternCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getpatterns());
}

int AdPlugDecoder::getCurrentPatternInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getpattern());
}

int AdPlugDecoder::getOrderCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getorders());
}

int AdPlugDecoder::getCurrentOrderInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getorder());
}

int AdPlugDecoder::getCurrentRowInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getrow());
}

int AdPlugDecoder::getCurrentSpeedInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getspeed());
}

int AdPlugDecoder::getInstrumentCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getinstruments());
}

std::string AdPlugDecoder::getInstrumentNamesInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return "";
    }
    const int instrumentCount = static_cast<int>(player->getinstruments());
    if (instrumentCount <= 0) {
        return "";
    }

    std::string names;
    names.reserve(static_cast<size_t>(instrumentCount) * 10);
    for (int i = 0; i < instrumentCount; ++i) {
        const std::string instrumentName = safeString(player->getinstrument(static_cast<unsigned int>(i)));
        if (instrumentName.empty()) {
            continue;
        }
        if (!names.empty()) {
            names += "\n";
        }
        names += std::to_string(i + 1);
        names += ". ";
        names += instrumentName;
    }
    return names;
}

void AdPlugDecoder::setRepeatMode(int mode) {
    const int normalized = (mode >= 0 && mode <= 3) ? mode : 0;
    repeatMode.store(normalized);
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (player) {
        player->setEndlessLoopMode(normalized == 2);
    }
}

int AdPlugDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int AdPlugDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int capabilities = PLAYBACK_CAP_SEEK | PLAYBACK_CAP_LIVE_REPEAT_MODE | PLAYBACK_CAP_FIXED_SAMPLE_RATE;
    if (durationReliable) {
        capabilities |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    // Seek is supported but is decode-forward (not direct/random access).
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
