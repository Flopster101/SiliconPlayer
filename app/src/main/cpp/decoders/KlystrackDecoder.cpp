#include "KlystrackDecoder.h"

#include <algorithm>
#include <cmath>
#include <filesystem>

extern "C" {
#include <klystrack/ksnd.h>
#if defined(__GNUC__)
__attribute__((weak))
#endif
void KSND_SetChannelMute(KPlayer* player, int channel, int muted);
}

namespace {
int clampSampleRate(int sampleRateHz) {
    return std::clamp(sampleRateHz, 8000, 192000);
}
}

KlystrackDecoder::KlystrackDecoder() = default;

KlystrackDecoder::~KlystrackDecoder() {
    close();
}

int KlystrackDecoder::normalizeRepeatMode(int mode) {
    if (mode < 0 || mode > 3) return 0;
    return mode;
}

bool KlystrackDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();

    if (!path || path[0] == '\0') {
        return false;
    }

    sourcePath = path;
    title = std::filesystem::path(sourcePath).stem().string();
    artist.clear();
    genre = "Klystrack";
    formatName = "KT";
    trackCount = 0;
    instrumentCount = 0;
    instrumentNames.clear();
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    playbackPositionSeconds = 0.0;
    durationSeconds = 0.0;
    durationReliable = false;
    songLengthRows = 0;

    player = KSND_CreatePlayerUnregistered(clampSampleRate(sampleRateHz));
    if (!player) {
        closeInternalLocked();
        return false;
    }

    song = KSND_LoadSong(player, sourcePath.c_str());
    if (!song) {
        closeInternalLocked();
        return false;
    }

    sampleRateHz = clampSampleRate(sampleRateHz);
    channels = 2;
    KSND_SetPlayerQuality(player, 2);
    applyRepeatModeLocked();
    KSND_PlaySong(player, song, 0);
    updateSongInfoLocked();
    syncToggleChannelsLocked();
    applyToggleMutesLocked();

    songLengthRows = std::max(0, KSND_GetSongLength(song));
    if (songLengthRows > 0) {
        const int durationMs = KSND_GetPlayTime(song, songLengthRows);
        if (durationMs > 0) {
            durationSeconds = static_cast<double>(durationMs) / 1000.0;
            durationReliable = true;
        }
    }

    return true;
}

void KlystrackDecoder::closeInternalLocked() {
    if (song) {
        KSND_FreeSong(song);
        song = nullptr;
    }
    if (player) {
        KSND_FreePlayer(player);
        player = nullptr;
    }
    sourcePath.clear();
    title.clear();
    artist.clear();
    genre.clear();
    formatName = "KT";
    trackCount = 0;
    instrumentCount = 0;
    instrumentNames.clear();
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    durationSeconds = 0.0;
    durationReliable = false;
    playbackPositionSeconds = 0.0;
    songLengthRows = 0;
    channels = 2;
    pcmScratch.clear();
}

void KlystrackDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

void KlystrackDecoder::updateSongInfoLocked() {
    trackCount = 0;
    instrumentCount = 0;
    instrumentNames.clear();
    if (!song) {
        return;
    }

    KSongInfo info{};
    const KSongInfo* resolvedInfo = KSND_GetSongInfo(song, &info);
    if (!resolvedInfo) {
        return;
    }

    if (resolvedInfo->song_title && resolvedInfo->song_title[0] != '\0') {
        title = resolvedInfo->song_title;
    }

    trackCount = std::max(0, resolvedInfo->n_channels);
    instrumentCount = std::max(0, resolvedInfo->n_instruments);
    const int maxInstruments = std::min(instrumentCount, 128);
    instrumentNames.reserve(static_cast<size_t>(maxInstruments) * 12);
    for (int i = 0; i < maxInstruments; ++i) {
        if (!instrumentNames.empty()) {
            instrumentNames.push_back('\n');
        }
        instrumentNames.append(std::to_string(i + 1));
        instrumentNames.append(". ");
        const char* instrumentName = resolvedInfo->instrument_name[i];
        if (instrumentName) {
            instrumentNames.append(instrumentName);
        }
    }
}

void KlystrackDecoder::syncToggleChannelsLocked() {
    const int channelCount = std::max(0, trackCount);
    if (channelCount == static_cast<int>(toggleChannelNames.size()) &&
        channelCount == static_cast<int>(toggleChannelMuted.size())) {
        return;
    }

    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    toggleChannelNames.reserve(static_cast<size_t>(channelCount));
    toggleChannelMuted.reserve(static_cast<size_t>(channelCount));
    for (int i = 0; i < channelCount; ++i) {
        toggleChannelNames.push_back("Channel " + std::to_string(i + 1));
        toggleChannelMuted.push_back(false);
    }
}

void KlystrackDecoder::applyToggleMutesLocked() {
    if (!player || toggleChannelMuted.empty() || KSND_SetChannelMute == nullptr) {
        return;
    }
    const int channelCount = std::min(trackCount, static_cast<int>(toggleChannelMuted.size()));
    for (int i = 0; i < channelCount; ++i) {
        KSND_SetChannelMute(
                player,
                i,
                toggleChannelMuted[static_cast<size_t>(i)] ? 1 : 0
        );
    }
}

void KlystrackDecoder::applyRepeatModeLocked() {
    if (!player) return;
    const int mode = normalizeRepeatMode(repeatMode.load());
    // Upstream quirk: KSND_SetLooping(0) loops, KSND_SetLooping(1) disables loop.
    KSND_SetLooping(player, mode == 2 ? 0 : 1);
}

int KlystrackDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !song || !buffer || numFrames <= 0) {
        return 0;
    }

    const int sampleCount = numFrames * channels;
    if (static_cast<int>(pcmScratch.size()) < sampleCount) {
        pcmScratch.resize(static_cast<size_t>(sampleCount));
    }
    std::fill_n(pcmScratch.data(), sampleCount, static_cast<int16_t>(0));
    applyToggleMutesLocked();

    const int requestedBytes = sampleCount * static_cast<int>(sizeof(int16_t));
    const int framesRead = std::max(0, KSND_FillBuffer(player, pcmScratch.data(), requestedBytes));

    for (int i = 0; i < framesRead * channels; ++i) {
        buffer[i] = static_cast<float>(pcmScratch[static_cast<size_t>(i)]) / 32768.0f;
    }

    if (framesRead > 0) {
        const int currentRow = std::max(0, KSND_GetPlayPosition(player));
        const int positionMs = KSND_GetPlayTime(song, currentRow);
        if (positionMs >= 0) {
            playbackPositionSeconds = static_cast<double>(positionMs) / 1000.0;
        } else {
            playbackPositionSeconds += static_cast<double>(framesRead) / static_cast<double>(sampleRateHz);
        }
    }

    return framesRead;
}

int KlystrackDecoder::resolveRowForTimeMsLocked(int targetMs) const {
    if (!song || songLengthRows <= 0) return 0;
    if (targetMs <= 0) return 0;

    int low = 0;
    int high = songLengthRows;
    while (low < high) {
        const int mid = low + ((high - low) / 2);
        const int midMs = KSND_GetPlayTime(song, mid);
        if (midMs < targetMs) {
            low = mid + 1;
        } else {
            high = mid;
        }
    }

    int row = std::clamp(low, 0, songLengthRows);
    if (row > 0) {
        const int prevMs = KSND_GetPlayTime(song, row - 1);
        const int currMs = KSND_GetPlayTime(song, row);
        if (std::abs(prevMs - targetMs) <= std::abs(currMs - targetMs)) {
            row -= 1;
        }
    }
    return row;
}

void KlystrackDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !song) return;

    double normalizedSeconds = std::max(0.0, seconds);
    if (durationReliable && durationSeconds > 0.0) {
        if (repeatMode.load() == 2) {
            normalizedSeconds = std::fmod(normalizedSeconds, durationSeconds);
            if (normalizedSeconds < 0.0) {
                normalizedSeconds += durationSeconds;
            }
        } else {
            normalizedSeconds = std::min(normalizedSeconds, durationSeconds);
        }
    }
    if (songLengthRows <= 0) {
        playbackPositionSeconds = normalizedSeconds;
        return;
    }

    const int targetMs = static_cast<int>(std::llround(normalizedSeconds * 1000.0));
    const int targetRow = resolveRowForTimeMsLocked(targetMs);
    KSND_PlaySong(player, song, targetRow);
    applyRepeatModeLocked();
    applyToggleMutesLocked();

    const int resolvedMs = KSND_GetPlayTime(song, targetRow);
    playbackPositionSeconds = resolvedMs >= 0
            ? static_cast<double>(resolvedMs) / 1000.0
            : normalizedSeconds;
}

double KlystrackDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationReliable ? durationSeconds : 0.0;
}

int KlystrackDecoder::getSampleRate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sampleRateHz;
}

int KlystrackDecoder::getBitDepth() {
    return 16;
}

std::string KlystrackDecoder::getBitDepthLabel() {
    return "16-bit";
}

int KlystrackDecoder::getDisplayChannelCount() {
    return getChannelCount();
}

int KlystrackDecoder::getChannelCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return channels;
}

std::string KlystrackDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string KlystrackDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string KlystrackDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string KlystrackDecoder::getFormatNameInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return formatName;
}

int KlystrackDecoder::getTrackCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return trackCount;
}

int KlystrackDecoder::getInstrumentCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return instrumentCount;
}

int KlystrackDecoder::getSongLengthRowsInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return songLengthRows;
}

int KlystrackDecoder::getCurrentRowInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return -1;
    }
    return std::max(0, KSND_GetPlayPosition(player));
}

std::string KlystrackDecoder::getInstrumentNamesInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return instrumentNames;
}

std::vector<std::string> KlystrackDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    return toggleChannelNames;
}

std::vector<uint8_t> KlystrackDecoder::getToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    return std::vector<uint8_t>(toggleChannelNames.size(), 1u);
}

void KlystrackDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyToggleMutesLocked();
}

bool KlystrackDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void KlystrackDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    if (toggleChannelMuted.empty()) {
        return;
    }
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyToggleMutesLocked();
}

void KlystrackDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode.store(normalizeRepeatMode(mode));
    applyRepeatModeLocked();
}

int KlystrackDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int KlystrackDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int caps = PLAYBACK_CAP_SEEK | PLAYBACK_CAP_LIVE_REPEAT_MODE;
    if (durationReliable && songLengthRows > 0) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION | PLAYBACK_CAP_DIRECT_SEEK;
    }
    return caps;
}

double KlystrackDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !song) {
        return -1.0;
    }
    const int currentRow = std::max(0, KSND_GetPlayPosition(player));
    const int positionMs = KSND_GetPlayTime(song, currentRow);
    if (positionMs >= 0) {
        playbackPositionSeconds = static_cast<double>(positionMs) / 1000.0;
    }
    if (durationReliable && durationSeconds > 0.0) {
        if (repeatMode.load() == 2) {
            playbackPositionSeconds = std::fmod(playbackPositionSeconds, durationSeconds);
            if (playbackPositionSeconds < 0.0) {
                playbackPositionSeconds += durationSeconds;
            }
        } else {
            playbackPositionSeconds = std::clamp(playbackPositionSeconds, 0.0, durationSeconds);
        }
    } else if (playbackPositionSeconds < 0.0) {
        playbackPositionSeconds = 0.0;
    }
    return playbackPositionSeconds;
}

AudioDecoder::TimelineMode KlystrackDecoder::getTimelineMode() const {
    return TimelineMode::Discontinuous;
}

std::vector<std::string> KlystrackDecoder::getSupportedExtensions() {
    return {
            "kt",
    };
}
