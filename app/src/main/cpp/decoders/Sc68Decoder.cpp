#include "Sc68Decoder.h"

#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <filesystem>
#include <memory>
#include <mutex>
#include <vector>

extern "C" {
#include <sc68/sc68.h>
}

#define LOG_TAG "Sc68Decoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr int kDefaultSampleRateHz = 44100;
constexpr int kMinSampleRateHz = 8000;
constexpr int kMaxSampleRateHz = 192000;
constexpr int kSeekDiscardChunkFrames = 1024;
constexpr int kInfoTrackCurrent = -1;
constexpr int kTrackDiskInfo = 0;

std::mutex gSc68ApiMutex;
int gSc68ApiRefCount = 0;

bool ensureSc68Api() {
    std::lock_guard<std::mutex> lock(gSc68ApiMutex);
    if (gSc68ApiRefCount > 0) {
        ++gSc68ApiRefCount;
        return true;
    }
    sc68_init_t init{};
    init.flags.no_load_config = 1;
    init.flags.no_save_config = 1;
    if (sc68_init(&init) != 0) {
        return false;
    }
    gSc68ApiRefCount = 1;
    return true;
}

void releaseSc68Api() {
    std::lock_guard<std::mutex> lock(gSc68ApiMutex);
    if (gSc68ApiRefCount <= 0) {
        return;
    }
    --gSc68ApiRefCount;
    if (gSc68ApiRefCount == 0) {
        sc68_shutdown();
    }
}

std::string safeString(const char* value) {
    return value ? std::string(value) : std::string();
}

std::string normalizeText(std::string value) {
    // Drop simple CR/LF noise from some tags.
    value.erase(std::remove(value.begin(), value.end(), '\r'), value.end());
    value.erase(std::remove(value.begin(), value.end(), '\n'), value.end());
    return value;
}

bool containsIgnoreCase(const std::string& text, const std::string& needle) {
    if (needle.empty()) return true;
    if (text.size() < needle.size()) return false;
    for (size_t start = 0; start + needle.size() <= text.size(); ++start) {
        bool match = true;
        for (size_t i = 0; i < needle.size(); ++i) {
            const char a = static_cast<char>(std::tolower(static_cast<unsigned char>(text[start + i])));
            const char b = static_cast<char>(std::tolower(static_cast<unsigned char>(needle[i])));
            if (a != b) {
                match = false;
                break;
            }
        }
        if (match) return true;
    }
    return false;
}

}

Sc68Decoder::Sc68Decoder() = default;

Sc68Decoder::~Sc68Decoder() {
    close();
}

bool Sc68Decoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();

    if (!path || path[0] == '\0') {
        return false;
    }
    if (!ensureSc68Api()) {
        LOGE("sc68_init failed");
        return false;
    }

    sc68_create_t create{};
    create.sampling_rate = static_cast<unsigned>(requestedSampleRateHz > 0 ? requestedSampleRateHz : kDefaultSampleRateHz);
    handle = sc68_create(&create);
    if (!handle) {
        LOGE("sc68_create failed");
        releaseSc68Api();
        return false;
    }

    // S16 output is the most stable/publicly supported path in this snapshot.
    sc68_cntl(handle, SC68_SET_PCM, SC68_PCM_S16);
    sc68_cntl(handle, SC68_SET_SPR, requestedSampleRateHz > 0 ? requestedSampleRateHz : kDefaultSampleRateHz);

    if (sc68_load_uri(handle, path) != 0) {
        LOGE("sc68_load_uri failed for: %s", path);
        closeInternalLocked();
        return false;
    }

    // Keep core playback internally looped; app repeat modes are enforced by
    // virtual EOF behavior in read() for non-LP modes.
    if (sc68_play(handle, SC68_DEF_TRACK, SC68_INF_LOOP) != 0) {
        LOGE("sc68_play(default) failed");
        closeInternalLocked();
        return false;
    }

    sourcePath = path;
    playbackPositionSeconds = 0.0;
    lastCorePositionMs = 0;
    isOpen = true;
    refreshTrackStateLocked();
    refreshMetadataLocked();
    refreshDurationLocked();
    rebuildToggleChannelsLocked();
    applyToggleChannelMutesLocked();
    return true;
}

void Sc68Decoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

void Sc68Decoder::closeInternalLocked() {
    if (handle) {
        sc68_close(handle);
        sc68_destroy(handle);
        handle = nullptr;
        releaseSc68Api();
    }
    isOpen = false;
    sampleRateHz = kDefaultSampleRateHz;
    subtuneCount = 1;
    currentTrack1Based = 1;
    durationReliable.store(false);
    durationSeconds = 0.0;
    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    trackHasYm = false;
    trackHasSte = false;
    trackHasAmiga = false;
    trackUsesAgaPath = false;
    playbackPositionSeconds = 0.0;
    lastCorePositionMs = -1;
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
}

bool Sc68Decoder::refreshTrackStateLocked() {
    if (!handle) return false;

    const int queriedRate = sc68_cntl(handle, SC68_GET_SPR);
    if (queriedRate > 0) {
        sampleRateHz = queriedRate;
    }

    const int queriedTracks = sc68_cntl(handle, SC68_GET_TRACKS);
    if (queriedTracks > 0) {
        subtuneCount = queriedTracks;
    } else {
        subtuneCount = 1;
    }

    const int queriedTrack = sc68_cntl(handle, SC68_GET_TRACK);
    if (queriedTrack > 0) {
        currentTrack1Based = queriedTrack;
    } else if (currentTrack1Based <= 0) {
        currentTrack1Based = 1;
    }
    currentTrack1Based = std::clamp(currentTrack1Based, 1, std::max(1, subtuneCount));
    return true;
}

void Sc68Decoder::refreshMetadataLocked() {
    if (!handle) return;

    sc68_music_info_t info{};
    if (sc68_music_info(handle, &info, kInfoTrackCurrent, nullptr) != 0) {
        title.clear();
        artist.clear();
        composer.clear();
        genre.clear();
        return;
    }

    title = normalizeText(safeString(info.title));
    artist = normalizeText(safeString(info.artist));
    composer = artist;
    genre = normalizeText(safeString(info.genre));
    trackHasYm = info.trk.ym != 0;
    trackHasSte = info.trk.ste != 0;
    trackHasAmiga = info.trk.amiga != 0;
    trackUsesAgaPath = false;
    const std::string hw = normalizeText(safeString(info.trk.hw));
    if (!hw.empty()) {
        // libsc68 runtime mixer path keys off hardware flags. Prefer that over
        // occasionally sparse boolean flags in metadata.
        if (containsIgnoreCase(hw, "paula") || containsIgnoreCase(hw, "amiga")) {
            trackHasAmiga = true;
            trackUsesAgaPath = true;
        }
        if (containsIgnoreCase(hw, "yamaha") || containsIgnoreCase(hw, "ym-2149")) {
            trackHasYm = true;
        }
        if (containsIgnoreCase(hw, "microwire") || containsIgnoreCase(hw, "ste")) {
            trackHasSte = true;
        }
    }
    if (title.empty()) {
        title = normalizeText(safeString(info.album));
    }
    if (title.empty() && !sourcePath.empty()) {
        title = std::filesystem::path(sourcePath).stem().string();
    }
}

void Sc68Decoder::refreshDurationLocked() {
    if (!handle) return;

    int lengthMs = 0;

    // When running with infinite internal loops, SC68_GET_LEN/TRKLEN can report
    // 0 for current runtime state. Query the disk metadata with default loop
    // semantics instead, so UI duration/repeat modes stay available.
    sc68_disk_t disk = nullptr;
    if (sc68_cntl(handle, SC68_GET_DISK, &disk) == 0 && disk) {
        sc68_music_info_t info{};
        if (sc68_music_info(nullptr, &info, currentTrack1Based, disk) == 0) {
            lengthMs = static_cast<int>(info.trk.time_ms);
        }
    }

    if (lengthMs <= 0) {
        lengthMs = sc68_cntl(handle, SC68_GET_TRKLEN, currentTrack1Based);
    }
    if (lengthMs <= 0) {
        lengthMs = sc68_cntl(handle, SC68_GET_LEN);
    }

    durationReliable.store(lengthMs > 0);
    durationSeconds = durationReliable.load() ? (static_cast<double>(lengthMs) / 1000.0) : 0.0;
}

void Sc68Decoder::updatePlaybackPositionLocked(int producedFrames) {
    if (!handle) return;
    const int mode = repeatMode.load();
    const bool repeatTrackSingle = (mode == 1 && std::max(1, subtuneCount) == 1);
    const int posMs = sc68_cntl(handle, SC68_GET_POS);
    const bool canAdvanceFromFrames = (producedFrames > 0 && sampleRateHz > 0);

    if (posMs >= 0) {
        const double posSeconds = static_cast<double>(posMs) / 1000.0;
        if (mode == 2) {
            playbackPositionSeconds = posSeconds;
            if (durationSeconds > 0.0 && playbackPositionSeconds > durationSeconds) {
                playbackPositionSeconds = durationSeconds;
            }
        } else if (repeatTrackSingle &&
                   lastCorePositionMs >= 0 &&
                   posMs <= lastCorePositionMs &&
                   canAdvanceFromFrames) {
            // Some SC68 tracks stall/wrap the reported position while still
            // rendering. Keep Repeat Track timeline linear.
            playbackPositionSeconds += static_cast<double>(producedFrames) / sampleRateHz;
        } else {
            playbackPositionSeconds = posSeconds;
        }
        lastCorePositionMs = posMs;
    } else if (canAdvanceFromFrames) {
        playbackPositionSeconds += static_cast<double>(producedFrames) / sampleRateHz;
        lastCorePositionMs = -1;
    }

    if (playbackPositionSeconds < 0.0) {
        playbackPositionSeconds = 0.0;
    }
}

void Sc68Decoder::rebuildToggleChannelsLocked() {
    std::vector<std::string> names;
    if (trackUsesAgaPath || trackHasAmiga) {
        names.emplace_back("Paula 1");
        names.emplace_back("Paula 2");
        names.emplace_back("Paula 3");
        names.emplace_back("Paula 4");
    } else {
        if (trackHasYm) {
            names.emplace_back("YM A");
            names.emplace_back("YM B");
            names.emplace_back("YM C");
        }
        if (trackHasSte) {
            names.emplace_back("Digi");
        }
    }
    if (names != toggleChannelNames) {
        toggleChannelNames = std::move(names);
        toggleChannelMuted.assign(toggleChannelNames.size(), false);
    }
}

void Sc68Decoder::applyToggleChannelMutesLocked() {
    if (!handle || toggleChannelNames.empty()) return;

    int ymMask = 0;
    int paulaMask = 0;
    int idx = 0;

    if (trackUsesAgaPath || trackHasAmiga) {
        for (int i = 0; i < 4 && idx < static_cast<int>(toggleChannelMuted.size()); ++i, ++idx) {
            if (!toggleChannelMuted[static_cast<size_t>(idx)]) {
                paulaMask |= (1 << i);
            }
        }
        sc68_cntl(handle, SC68_SET_OPT_INT, "amiga-chans", paulaMask);
        return;
    }

    if (trackHasYm) {
        for (int i = 0; i < 3 && idx < static_cast<int>(toggleChannelMuted.size()); ++i, ++idx) {
            if (!toggleChannelMuted[static_cast<size_t>(idx)]) {
                ymMask |= (1 << i);
            }
        }
        sc68_cntl(handle, SC68_SET_OPT_INT, "ym-chans", ymMask);
    }
    if (trackHasSte && idx < static_cast<int>(toggleChannelMuted.size())) {
        const int digiEnabled = toggleChannelMuted[static_cast<size_t>(idx)] ? 0 : 1;
        sc68_cntl(handle, SC68_SET_OPT_INT, "ste-enable", digiEnabled);
        ++idx;
    }
}

int Sc68Decoder::processIntoLocked(float* buffer, int numFrames) {
    if (!handle || !buffer || numFrames <= 0) {
        return 0;
    }

    std::vector<int16_t> pcm(static_cast<size_t>(numFrames) * 2u);
    int requestedFrames = numFrames;
    const int status = sc68_process(handle, pcm.data(), &requestedFrames);
    if (status == SC68_ERROR || requestedFrames <= 0) {
        return 0;
    }

    const size_t sampleCount = static_cast<size_t>(requestedFrames) * 2u;
    for (size_t i = 0; i < sampleCount; ++i) {
        buffer[i] = static_cast<float>(pcm[i]) / 32768.0f;
    }

    if ((status & SC68_CHANGE) != 0) {
        refreshTrackStateLocked();
        refreshMetadataLocked();
        refreshDurationLocked();
        rebuildToggleChannelsLocked();
        applyToggleChannelMutesLocked();
    }
    updatePlaybackPositionLocked(requestedFrames);
    return requestedFrames;
}

int Sc68Decoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen) return 0;
    // For repeat-track with a single subtune, let elapsed time continue past
    // nominal duration while SC68 loops internally.
    const int mode = repeatMode.load();
    const bool gateVirtualEof =
            (mode == 0) ||
            (mode == 3) ||
            (mode == 1 && std::max(1, subtuneCount) > 1);
    if (gateVirtualEof && durationSeconds > 0.0 && playbackPositionSeconds >= durationSeconds) {
        return 0;
    }
    return processIntoLocked(buffer, numFrames);
}

void Sc68Decoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return;

    const double clamped = std::max(0.0, seconds);
    const int targetMs = static_cast<int>(std::llround(clamped * 1000.0));

    // Preferred path: patched libsc68 direct seek command.
    const int directSeekPosMs = sc68_cntl(handle, SC68_SET_POS, targetMs);
    if (directSeekPosMs >= 0) {
        playbackPositionSeconds = static_cast<double>(directSeekPosMs) / 1000.0;
        lastCorePositionMs = directSeekPosMs;
        return;
    }

    // Fallback for unpatched snapshots: restart + decode-discard.
    if (!setTrackLocked(currentTrack1Based)) return;
    if (clamped <= 0.0) return;

    int64_t framesToSkip = static_cast<int64_t>(std::llround(clamped * static_cast<double>(sampleRateHz)));
    std::vector<float> discard(static_cast<size_t>(kSeekDiscardChunkFrames) * 2u);
    while (framesToSkip > 0) {
        const int chunk = static_cast<int>(std::min<int64_t>(kSeekDiscardChunkFrames, framesToSkip));
        const int produced = processIntoLocked(discard.data(), chunk);
        if (produced <= 0) {
            break;
        }
        framesToSkip -= produced;
    }
    updatePlaybackPositionLocked(0);
}

double Sc68Decoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationSeconds;
}

int Sc68Decoder::getSampleRate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sampleRateHz;
}

int Sc68Decoder::getBitDepth() {
    return bitDepth;
}

std::string Sc68Decoder::getBitDepthLabel() {
    return "16-bit PCM";
}

int Sc68Decoder::getDisplayChannelCount() {
    return channels;
}

int Sc68Decoder::getChannelCount() {
    return channels;
}

int Sc68Decoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return std::max(1, subtuneCount);
}

int Sc68Decoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return std::max(0, currentTrack1Based - 1);
}

bool Sc68Decoder::setTrackLocked(int track1Based) {
    if (!handle) return false;
    if (track1Based < 1 || track1Based > std::max(1, subtuneCount)) return false;
    if (sc68_play(handle, track1Based, SC68_INF_LOOP) != 0) {
        return false;
    }
    currentTrack1Based = track1Based;
    playbackPositionSeconds = 0.0;
    lastCorePositionMs = 0;
    // sc68 applies track changes on process calls, so run one minimal pass.
    float scratch[2] = { 0.0f, 0.0f };
    processIntoLocked(scratch, 1);
    refreshTrackStateLocked();
    refreshMetadataLocked();
    refreshDurationLocked();
    rebuildToggleChannelsLocked();
    applyToggleChannelMutesLocked();
    return true;
}

bool Sc68Decoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return false;
    return setTrackLocked(index + 1);
}

std::string Sc68Decoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return "";
    sc68_music_info_t info{};
    if (sc68_music_info(handle, &info, index + 1, nullptr) != 0) {
        return "";
    }
    std::string value = normalizeText(safeString(info.title));
    if (value.empty()) {
        value = normalizeText(safeString(info.album));
    }
    return value;
}

std::string Sc68Decoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return "";
    sc68_music_info_t info{};
    if (sc68_music_info(handle, &info, index + 1, nullptr) != 0) {
        return "";
    }
    return normalizeText(safeString(info.artist));
}

double Sc68Decoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return 0.0;
    int lengthMs = sc68_cntl(handle, SC68_GET_TRKLEN, index + 1);
    if (lengthMs <= 0) {
        sc68_music_info_t info{};
        if (sc68_music_info(handle, &info, index + 1, nullptr) == 0) {
            lengthMs = static_cast<int>(info.trk.time_ms);
        }
    }
    return lengthMs > 0 ? (static_cast<double>(lengthMs) / 1000.0) : 0.0;
}

std::string Sc68Decoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string Sc68Decoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string Sc68Decoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string Sc68Decoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::vector<std::string> Sc68Decoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    rebuildToggleChannelsLocked();
    return toggleChannelNames;
}

std::vector<uint8_t> Sc68Decoder::getToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    rebuildToggleChannelsLocked();
    return std::vector<uint8_t>(toggleChannelNames.size(), 1u);
}

void Sc68Decoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    rebuildToggleChannelsLocked();
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyToggleChannelMutesLocked();
}

bool Sc68Decoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void Sc68Decoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (toggleChannelMuted.empty()) return;
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyToggleChannelMutesLocked();
}

void Sc68Decoder::setOutputSampleRate(int sampleRate) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    requestedSampleRateHz = std::clamp(sampleRate, kMinSampleRateHz, kMaxSampleRateHz);
}

void Sc68Decoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode.store((mode >= 0 && mode <= 3) ? mode : 0);
}

int Sc68Decoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int Sc68Decoder::getPlaybackCapabilities() const {
    int caps = PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_FIXED_SAMPLE_RATE;
    if (durationReliable.load()) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

int Sc68Decoder::getFixedSampleRateHz() const {
    return kDefaultSampleRateHz;
}

double Sc68Decoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !handle) return 0.0;
    double posSeconds = playbackPositionSeconds;
    if (repeatMode.load() == 2 && durationSeconds > 0.0 && posSeconds > durationSeconds) {
        posSeconds = durationSeconds;
    }
    return posSeconds;
}

std::vector<std::string> Sc68Decoder::getSupportedExtensions() {
    return { "sc68", "sndh" };
}
