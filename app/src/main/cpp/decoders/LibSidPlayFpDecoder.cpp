#include "LibSidPlayFpDecoder.h"

#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <vector>

#include <sidplayfp/sidplayfp.h>
#include <sidplayfp/SidConfig.h>
#include <sidplayfp/SidInfo.h>
#include <sidplayfp/SidTune.h>
#include <sidplayfp/SidTuneInfo.h>
#include <sidplayfp/builders/sidlite.h>

#define LOG_TAG "LibSidPlayFpDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr unsigned int kRenderCyclesPerChunk = 5000;

std::string safeString(const char* value) {
    return value ? std::string(value) : "";
}
}

LibSidPlayFpDecoder::LibSidPlayFpDecoder() = default;

LibSidPlayFpDecoder::~LibSidPlayFpDecoder() {
    close();
}

std::vector<std::string> LibSidPlayFpDecoder::getSupportedExtensions() {
    return {
            "sid", "psid", "rsid", "mus", "str",
            "prg", "p00", "c64", "dat"
    };
}

bool LibSidPlayFpDecoder::applyConfigLocked() {
    if (!player || !config || !sidBuilder) return false;
    config->frequency = static_cast<uint_least32_t>(sampleRate);
    config->playback = SidConfig::STEREO;
    config->samplingMethod = SidConfig::INTERPOLATE;
    config->sidEmulation = sidBuilder.get();
    if (!player->config(*config)) {
        LOGE("sidplayfp config failed: %s", player->error());
        return false;
    }
    return true;
}

void LibSidPlayFpDecoder::refreshMetadataLocked() {
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    sidChipCount = 1;
    subtuneTitles.assign(std::max(1, subtuneCount), "");
    subtuneArtists.assign(std::max(1, subtuneCount), "");

    if (!tune) return;
    const SidTuneInfo* info = tune->getInfo();
    if (info == nullptr) return;

    sidChipCount = std::max(1, info->sidChips());
    const unsigned int numInfoStrings = info->numberOfInfoStrings();
    if (numInfoStrings > 0) title = safeString(info->infoString(0));
    if (numInfoStrings > 1) artist = safeString(info->infoString(1));
    if (numInfoStrings > 2) genre = safeString(info->infoString(2));
    composer = artist;

    for (int i = 0; i < subtuneCount; ++i) {
        const SidTuneInfo* songInfo = tune->getInfo(static_cast<unsigned int>(i + 1));
        if (songInfo == nullptr) continue;
        const unsigned int songInfoStrings = songInfo->numberOfInfoStrings();
        if (songInfoStrings > 0) subtuneTitles[i] = safeString(songInfo->infoString(0));
        if (songInfoStrings > 1) subtuneArtists[i] = safeString(songInfo->infoString(1));
    }
}

bool LibSidPlayFpDecoder::selectSubtuneLocked(int index) {
    if (!player || !tune || index < 0 || index >= subtuneCount) {
        return false;
    }
    tune->selectSong(static_cast<unsigned int>(index + 1));
    if (!player->load(tune.get())) {
        LOGE("sidplayfp load(subtune) failed: %s", player->error());
        return false;
    }
    player->reset();
    player->initMixer(true);
    const SidInfo& info = player->info();
    outputChannels = std::clamp(static_cast<int>(info.channels()), 1, 2);
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
    currentSubtuneIndex = index;
    return true;
}

bool LibSidPlayFpDecoder::openInternalLocked(const char* path) {
    if (!path) return false;

    player = std::make_unique<sidplayfp>();
    sidBuilder = std::make_unique<SIDLiteBuilder>("SiliconPlayer");
    config = std::make_unique<SidConfig>(player->config());

    if (!applyConfigLocked()) {
        return false;
    }

    tune = std::make_unique<SidTune>(path);
    if (!tune->getStatus()) {
        LOGE("SidTune open failed: %s", tune->statusString());
        return false;
    }

    const SidTuneInfo* tuneInfo = tune->getInfo();
    subtuneCount = tuneInfo ? std::max(1u, tuneInfo->songs()) : 1u;
    const unsigned int startSong = tuneInfo ? tuneInfo->startSong() : 1u;
    currentSubtuneIndex = std::clamp(static_cast<int>(startSong) - 1, 0, subtuneCount - 1);

    if (!selectSubtuneLocked(currentSubtuneIndex)) {
        return false;
    }
    refreshMetadataLocked();

    return true;
}

bool LibSidPlayFpDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    tune.reset();
    sidBuilder.reset();
    config.reset();
    player.reset();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    subtuneTitles.clear();
    subtuneArtists.clear();
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    outputChannels = 2;
    sidChipCount = 1;
    return openInternalLocked(path);
}

void LibSidPlayFpDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    tune.reset();
    sidBuilder.reset();
    config.reset();
    player.reset();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    subtuneTitles.clear();
    subtuneArtists.clear();
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    outputChannels = 2;
    sidChipCount = 1;
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
}

int LibSidPlayFpDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !buffer || numFrames <= 0) return 0;
    const int channels = std::clamp(outputChannels, 1, 2);

    int framesWritten = 0;
    while (framesWritten < numFrames) {
        const size_t pendingAvailableSamples =
                pendingMixedSamples.size() > pendingMixedOffset
                        ? (pendingMixedSamples.size() - pendingMixedOffset)
                        : 0u;
        const int pendingFrames = static_cast<int>(pendingAvailableSamples / static_cast<size_t>(channels));
        if (pendingFrames > 0) {
            const int framesToCopy = std::min(numFrames - framesWritten, pendingFrames);
            const int samplesToCopy = framesToCopy * channels;
            for (int i = 0; i < samplesToCopy; ++i) {
                buffer[(framesWritten * channels) + i] =
                        static_cast<float>(pendingMixedSamples[pendingMixedOffset + static_cast<size_t>(i)]) / 32768.0f;
            }
            framesWritten += framesToCopy;
            pendingMixedOffset += static_cast<size_t>(samplesToCopy);

            if (pendingMixedOffset >= pendingMixedSamples.size()) {
                pendingMixedSamples.clear();
                pendingMixedOffset = 0;
            } else if (pendingMixedOffset > 4096u) {
                pendingMixedSamples.erase(
                        pendingMixedSamples.begin(),
                        pendingMixedSamples.begin() + static_cast<std::ptrdiff_t>(pendingMixedOffset)
                );
                pendingMixedOffset = 0;
            }
            continue;
        }

        const int produced = player->play(kRenderCyclesPerChunk);
        if (produced < 0) {
            LOGE("sidplayfp play failed: %s", player->error());
            break;
        }
        if (produced == 0) {
            break;
        }

        std::vector<int16_t> mixed(static_cast<size_t>(produced) * static_cast<size_t>(channels));
        const unsigned int mixedSamples = player->mix(mixed.data(), static_cast<unsigned int>(produced));
        if (mixedSamples < static_cast<unsigned int>(channels)) {
            break;
        }
        pendingMixedSamples.insert(
                pendingMixedSamples.end(),
                mixed.begin(),
                mixed.begin() + static_cast<std::ptrdiff_t>(mixedSamples)
        );
    }

    return framesWritten;
}

void LibSidPlayFpDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !tune) return;
    if (seconds <= 0.0) {
        selectSubtuneLocked(currentSubtuneIndex);
        return;
    }

    // Basic seek fallback: restart subtune then fast-forward in chunks.
    if (!selectSubtuneLocked(currentSubtuneIndex)) return;
    pendingMixedSamples.clear();
    pendingMixedOffset = 0;
    const uint32_t targetMs = static_cast<uint32_t>(seconds * 1000.0);
    while (player->timeMs() < targetMs) {
        const int produced = player->play(kRenderCyclesPerChunk);
        if (produced <= 0) break;
    }
}

double LibSidPlayFpDecoder::getDuration() {
    return 0.0; // SID files typically have no reliable embedded duration.
}

int LibSidPlayFpDecoder::getSampleRate() {
    return sampleRate;
}

void LibSidPlayFpDecoder::setOutputSampleRate(int sampleRateHz) {
    if (sampleRateHz <= 0) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (sampleRate == sampleRateHz) return;
    sampleRate = sampleRateHz;
    if (player && config) {
        applyConfigLocked();
        if (tune) {
            selectSubtuneLocked(currentSubtuneIndex);
        }
    }
}

int LibSidPlayFpDecoder::getBitDepth() {
    return 16;
}

std::string LibSidPlayFpDecoder::getBitDepthLabel() {
    return "16 bit";
}

int LibSidPlayFpDecoder::getDisplayChannelCount() {
    return sidChipCount;
}

int LibSidPlayFpDecoder::getChannelCount() {
    return outputChannels;
}

int LibSidPlayFpDecoder::getSubtuneCount() const {
    return subtuneCount;
}

int LibSidPlayFpDecoder::getCurrentSubtuneIndex() const {
    return currentSubtuneIndex;
}

bool LibSidPlayFpDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return selectSubtuneLocked(index);
}

std::string LibSidPlayFpDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= static_cast<int>(subtuneTitles.size())) return "";
    return subtuneTitles[index];
}

std::string LibSidPlayFpDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= static_cast<int>(subtuneArtists.size())) return "";
    return subtuneArtists[index];
}

double LibSidPlayFpDecoder::getSubtuneDurationSeconds(int /*index*/) {
    return 0.0;
}

std::string LibSidPlayFpDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string LibSidPlayFpDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string LibSidPlayFpDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string LibSidPlayFpDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

int LibSidPlayFpDecoder::getPlaybackCapabilities() const {
    return PLAYBACK_CAP_LIVE_REPEAT_MODE;
}

int LibSidPlayFpDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK;
}

double LibSidPlayFpDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) return -1.0;
    return static_cast<double>(player->timeMs()) / 1000.0;
}
