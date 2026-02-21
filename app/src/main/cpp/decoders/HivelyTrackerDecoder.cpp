#include "HivelyTrackerDecoder.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <filesystem>

extern "C" {
#include <hivelytracker/hvl_replay.h>
}

namespace {
std::string copyFixedString(const char* value, std::size_t maxLen) {
    if (!value || maxLen == 0) {
        return {};
    }
    std::size_t len = 0;
    while (len < maxLen && value[len] != '\0') {
        ++len;
    }
    return std::string(value, len);
}

int normalizeRepeatMode(int mode) {
    if (mode < 0 || mode > 3) return 0;
    return mode;
}

int clampSampleRate(int sampleRateHz) {
    return std::clamp(sampleRateHz, 8000, 192000);
}

std::string lowercase(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return value;
}
}

HivelyTrackerDecoder::HivelyTrackerDecoder() = default;

HivelyTrackerDecoder::~HivelyTrackerDecoder() {
    close();
}

bool HivelyTrackerDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();

    if (!path || path[0] == '\0') {
        return false;
    }

    hvl_InitReplayer();

    sourcePath = path;
    tune = hvl_LoadTune(sourcePath.c_str(), static_cast<uint32>(clampSampleRate(requestedSampleRateHz)), 2);
    if (!tune) {
        closeInternalLocked();
        return false;
    }

    sampleRateHz = std::max(8000, static_cast<int>(tune->ht_Frequency));
    displayChannels = std::clamp(static_cast<int>(tune->ht_Channels), 1, MAX_CHANNELS);
    subtuneCount = std::max(1, static_cast<int>(tune->ht_SubsongNr) + 1);
    currentSubtuneIndex = std::clamp(static_cast<int>(tune->ht_SongNum), 0, subtuneCount - 1);
    if (tune->ht_mixgain <= 0) {
        // Some files can surface zero gain through loader/header oddities.
        tune->ht_mixgain = (76 * 256) / 100;
    }
    if (!hvl_InitSubsong(tune, static_cast<uint32>(currentSubtuneIndex))) {
        closeInternalLocked();
        return false;
    }

    const std::string extension = lowercase(std::filesystem::path(sourcePath).extension().string());
    if (extension == ".ahx") {
        formatName = "AHX";
        formatVersion = 0;
    } else if (extension == ".hvl") {
        formatName = "HVL";
        formatVersion = std::max(0, static_cast<int>(tune->ht_Version));
    } else {
        formatName = "AHX/HVL";
        formatVersion = 0;
    }

    title = copyFixedString(tune->ht_Name, sizeof(tune->ht_Name));
    if (title.empty()) {
        title = std::filesystem::path(sourcePath).stem().string();
    }
    artist.clear();
    composer.clear();
    genre = "AHX/HVL";

    stopAfterPendingDrain = false;
    pendingInterleaved.clear();
    pendingReadOffset = 0;
    playbackPositionSeconds = 0.0;
    decodeInterleavedScratch.clear();
    subtuneDurationSeconds.assign(static_cast<size_t>(subtuneCount), 0.0);
    subtuneDurationKnown.assign(static_cast<size_t>(subtuneCount), 0u);
    subtuneDurationReliable.assign(static_cast<size_t>(subtuneCount), 0u);
    analyzeSubtuneDurationLocked(currentSubtuneIndex);
    updateCurrentDurationFromCacheLocked();
    return true;
}

void HivelyTrackerDecoder::closeInternalLocked() {
    if (tune) {
        hvl_FreeTune(tune);
        tune = nullptr;
    }
    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    formatName.clear();
    formatVersion = 0;
    sampleRateHz = 44100;
    channels = 2;
    displayChannels = 2;
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    durationSeconds = 0.0;
    durationReliable.store(false);
    stopAfterPendingDrain = false;
    pendingInterleaved.clear();
    pendingReadOffset = 0;
    playbackPositionSeconds = 0.0;
    decodeInterleavedScratch.clear();
    subtuneDurationSeconds.clear();
    subtuneDurationKnown.clear();
    subtuneDurationReliable.clear();
}

void HivelyTrackerDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

int HivelyTrackerDecoder::getFrameSamplesPerDecodeLocked() const {
    return std::max(1, sampleRateHz / 50);
}

bool HivelyTrackerDecoder::decodeFrameIntoPendingLocked() {
    if (!tune) {
        return false;
    }

    const int frameSamples = getFrameSamplesPerDecodeLocked();
    const int sampleCount = frameSamples * channels;
    if (static_cast<int>(decodeInterleavedScratch.size()) < sampleCount) {
        decodeInterleavedScratch.resize(sampleCount);
    }

    const bool songEndBefore = tune->ht_SongEndReached != 0;
    int8* interleavedBase = reinterpret_cast<int8*>(decodeInterleavedScratch.data());
    hvl_DecodeFrame(
            tune,
            interleavedBase,
            interleavedBase + static_cast<int32>(sizeof(int16_t)),
            static_cast<int32>(sizeof(int16_t) * channels));
    const bool songEndAfter = tune->ht_SongEndReached != 0;

    pendingInterleaved.resize(static_cast<std::size_t>(frameSamples * channels));
    pendingReadOffset = 0;
    for (int i = 0; i < frameSamples; ++i) {
        const int16_t left = decodeInterleavedScratch[static_cast<std::size_t>(i * 2)];
        const int16_t right = decodeInterleavedScratch[static_cast<std::size_t>(i * 2 + 1)];
        pendingInterleaved[static_cast<std::size_t>(i * 2)] =
                static_cast<float>(left) / 32768.0f;
        pendingInterleaved[static_cast<std::size_t>(i * 2 + 1)] =
                static_cast<float>(right) / 32768.0f;
    }

    if (!songEndBefore &&
        songEndAfter &&
        repeatMode.load() == 0 &&
        !durationReliable.load()) {
        stopAfterPendingDrain = true;
    }
    return true;
}

bool HivelyTrackerDecoder::resetToSubtuneStartLocked() {
    if (!tune) return false;
    if (!hvl_InitSubsong(tune, static_cast<uint32>(currentSubtuneIndex))) {
        return false;
    }
    stopAfterPendingDrain = false;
    pendingInterleaved.clear();
    pendingReadOffset = 0;
    playbackPositionSeconds = 0.0;
    return true;
}

bool HivelyTrackerDecoder::analyzeSubtuneDurationLocked(int index) {
    if (index < 0 || index >= subtuneCount) {
        return false;
    }
    const size_t cacheIndex = static_cast<size_t>(index);
    if (cacheIndex < subtuneDurationKnown.size() && subtuneDurationKnown[cacheIndex] != 0u) {
        return subtuneDurationReliable[cacheIndex] != 0u;
    }
    if (sourcePath.empty()) {
        return false;
    }

    hvl_InitReplayer();
    hvl_tune* analysisTune = hvl_LoadTune(
            sourcePath.c_str(),
            static_cast<uint32>(sampleRateHz),
            2
    );
    if (!analysisTune) {
        if (cacheIndex < subtuneDurationKnown.size()) {
            subtuneDurationKnown[cacheIndex] = 1u;
            subtuneDurationReliable[cacheIndex] = 0u;
            subtuneDurationSeconds[cacheIndex] = 0.0;
        }
        return false;
    }

    if (!hvl_InitSubsong(analysisTune, static_cast<uint32>(index))) {
        hvl_FreeTune(analysisTune);
        if (cacheIndex < subtuneDurationKnown.size()) {
            subtuneDurationKnown[cacheIndex] = 1u;
            subtuneDurationReliable[cacheIndex] = 0u;
            subtuneDurationSeconds[cacheIndex] = 0.0;
        }
        return false;
    }

    const int analysisRate = std::max(8000, static_cast<int>(analysisTune->ht_Frequency));
    const int frameSamples = std::max(1, analysisRate / 50);
    std::vector<int16_t> scratch(static_cast<size_t>(frameSamples * 2));
    int8* scratchBytes = reinterpret_cast<int8*>(scratch.data());
    const int64_t maxFramesToAnalyze = static_cast<int64_t>(analysisRate) * 60 * 30; // 30 minutes cap.
    int64_t decodedFrames = 0;
    bool reachedSongEnd = false;

    while (decodedFrames < maxFramesToAnalyze) {
        if (analysisTune->ht_SongEndReached != 0) {
            reachedSongEnd = true;
            break;
        }
        hvl_DecodeFrame(
                analysisTune,
                scratchBytes,
                scratchBytes + static_cast<int32>(sizeof(int16_t)),
                static_cast<int32>(sizeof(int16_t) * 2)
        );
        decodedFrames += frameSamples;
    }

    hvl_FreeTune(analysisTune);

    if (cacheIndex < subtuneDurationKnown.size()) {
        subtuneDurationKnown[cacheIndex] = 1u;
        subtuneDurationReliable[cacheIndex] = reachedSongEnd ? 1u : 0u;
        subtuneDurationSeconds[cacheIndex] = reachedSongEnd
                ? static_cast<double>(decodedFrames) / static_cast<double>(analysisRate)
                : 0.0;
    }

    return reachedSongEnd;
}

void HivelyTrackerDecoder::updateCurrentDurationFromCacheLocked() {
    if (currentSubtuneIndex < 0 || currentSubtuneIndex >= subtuneCount) {
        durationSeconds = 0.0;
        durationReliable.store(false);
        return;
    }
    const size_t cacheIndex = static_cast<size_t>(currentSubtuneIndex);
    if (cacheIndex >= subtuneDurationKnown.size() || subtuneDurationKnown[cacheIndex] == 0u) {
        durationSeconds = 0.0;
        durationReliable.store(false);
        return;
    }
    const bool reliable = subtuneDurationReliable[cacheIndex] != 0u;
    durationSeconds = reliable ? subtuneDurationSeconds[cacheIndex] : 0.0;
    durationReliable.store(reliable);
}

int HivelyTrackerDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune || !buffer || numFrames <= 0) {
        return 0;
    }

    int framesTarget = numFrames;
    const int mode = repeatMode.load();
    const bool hasReliableDuration = durationReliable.load() && durationSeconds > 0.0;
    if (hasReliableDuration && mode != 2) {
        const int64_t durationFrames = static_cast<int64_t>(
                std::llround(durationSeconds * static_cast<double>(sampleRateHz))
        );
        const int64_t playedFrames = static_cast<int64_t>(
                std::llround(playbackPositionSeconds * static_cast<double>(sampleRateHz))
        );
        const int64_t remainingFrames = durationFrames - playedFrames;
        if (remainingFrames <= 0) {
            return 0;
        }
        framesTarget = static_cast<int>(std::min<int64_t>(framesTarget, remainingFrames));
    }

    int framesWritten = 0;
    while (framesWritten < framesTarget) {
        if (pendingReadOffset >= pendingInterleaved.size()) {
            pendingInterleaved.clear();
            pendingReadOffset = 0;
            if (stopAfterPendingDrain) {
                break;
            }
            if (!decodeFrameIntoPendingLocked()) {
                break;
            }
        }

        const std::size_t availableSamples = pendingInterleaved.size() - pendingReadOffset;
        const int availableFrames = static_cast<int>(availableSamples / channels);
        if (availableFrames <= 0) {
            break;
        }

        const int copyFrames = std::min(framesTarget - framesWritten, availableFrames);
        const std::size_t copySamples = static_cast<std::size_t>(copyFrames * channels);
        std::copy_n(
                pendingInterleaved.data() + pendingReadOffset,
                copySamples,
                buffer + static_cast<std::size_t>(framesWritten * channels));
        pendingReadOffset += copySamples;
        framesWritten += copyFrames;
        playbackPositionSeconds += static_cast<double>(copyFrames) / static_cast<double>(sampleRateHz);
    }

    return framesWritten;
}

void HivelyTrackerDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) {
        return;
    }

    double clampedTarget = std::max(0.0, seconds);
    const bool hasReliableDuration = durationReliable.load() && durationSeconds > 0.0;
    const int mode = repeatMode.load();
    if (hasReliableDuration) {
        if (mode == 2) {
            clampedTarget = std::fmod(clampedTarget, durationSeconds);
            if (clampedTarget < 0.0) {
                clampedTarget += durationSeconds;
            }
        } else {
            clampedTarget = std::min(clampedTarget, durationSeconds);
        }
    }
    if (!resetToSubtuneStartLocked()) {
        return;
    }

    if (clampedTarget <= 0.0) {
        return;
    }

    const int frameSamples = getFrameSamplesPerDecodeLocked();
    const int64_t targetFrames = static_cast<int64_t>(
            std::llround(clampedTarget * static_cast<double>(sampleRateHz))
    );
    int64_t decodedFrames = 0;
    const int64_t maxWholeFrameIterations = std::max<int64_t>(1, (targetFrames / frameSamples) + 2);

    const int sampleCount = frameSamples * channels;
    if (static_cast<int>(decodeInterleavedScratch.size()) < sampleCount) {
        decodeInterleavedScratch.resize(sampleCount);
    }

    for (int64_t i = 0; i < maxWholeFrameIterations && (decodedFrames + frameSamples) <= targetFrames; ++i) {
        const bool songEndBefore = tune->ht_SongEndReached != 0;
        int8* interleavedBase = reinterpret_cast<int8*>(decodeInterleavedScratch.data());
        hvl_DecodeFrame(
                tune,
                interleavedBase,
                interleavedBase + static_cast<int32>(sizeof(int16_t)),
                static_cast<int32>(sizeof(int16_t) * channels));
        const bool songEndAfter = tune->ht_SongEndReached != 0;
        decodedFrames += frameSamples;

        if (!songEndBefore && songEndAfter && repeatMode.load() == 0) {
            stopAfterPendingDrain = true;
            pendingInterleaved.clear();
            pendingReadOffset = 0;
            playbackPositionSeconds =
                    static_cast<double>(decodedFrames) / static_cast<double>(sampleRateHz);
            return;
        }
    }

    const int64_t remainingFrames = targetFrames - decodedFrames;
    if (remainingFrames > 0) {
        if (decodeFrameIntoPendingLocked()) {
            const int availableFrames = static_cast<int>(pendingInterleaved.size() / channels);
            const int skipFrames = std::clamp<int>(
                    static_cast<int>(remainingFrames),
                    0,
                    availableFrames
            );
            pendingReadOffset = static_cast<std::size_t>(skipFrames * channels);
            decodedFrames += skipFrames;
            if (pendingReadOffset >= pendingInterleaved.size()) {
                pendingInterleaved.clear();
                pendingReadOffset = 0;
            }
        }
    }

    playbackPositionSeconds = static_cast<double>(decodedFrames) / static_cast<double>(sampleRateHz);
}

double HivelyTrackerDecoder::getDuration() {
    return durationSeconds;
}

int HivelyTrackerDecoder::getSampleRate() {
    return sampleRateHz;
}

int HivelyTrackerDecoder::getBitDepth() {
    return bitDepth;
}

std::string HivelyTrackerDecoder::getBitDepthLabel() {
    return "8-bit";
}

int HivelyTrackerDecoder::getDisplayChannelCount() {
    return displayChannels;
}

int HivelyTrackerDecoder::getChannelCount() {
    return channels;
}

int HivelyTrackerDecoder::getSubtuneCount() const {
    return subtuneCount;
}

int HivelyTrackerDecoder::getCurrentSubtuneIndex() const {
    return currentSubtuneIndex;
}

bool HivelyTrackerDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune || index < 0 || index >= subtuneCount) {
        return false;
    }
    if (!hvl_InitSubsong(tune, static_cast<uint32>(index))) {
        return false;
    }
    currentSubtuneIndex = index;
    analyzeSubtuneDurationLocked(currentSubtuneIndex);
    updateCurrentDurationFromCacheLocked();
    stopAfterPendingDrain = false;
    pendingInterleaved.clear();
    pendingReadOffset = 0;
    playbackPositionSeconds = 0.0;
    return true;
}

std::string HivelyTrackerDecoder::getSubtuneTitle(int index) {
    return (index >= 0 && index < subtuneCount) ? title : std::string();
}

std::string HivelyTrackerDecoder::getSubtuneArtist(int index) {
    return (index >= 0 && index < subtuneCount) ? artist : std::string();
}

double HivelyTrackerDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (subtuneCount <= 0) return 0.0;
    if (index < 0 || index >= subtuneCount) return 0.0;
    analyzeSubtuneDurationLocked(index);
    const size_t cacheIndex = static_cast<size_t>(index);
    if (cacheIndex < subtuneDurationKnown.size() &&
        subtuneDurationKnown[cacheIndex] != 0u &&
        subtuneDurationReliable[cacheIndex] != 0u) {
        return subtuneDurationSeconds[cacheIndex];
    }
    return 0.0;
}

std::string HivelyTrackerDecoder::getTitle() {
    return title;
}

std::string HivelyTrackerDecoder::getArtist() {
    return artist;
}

std::string HivelyTrackerDecoder::getComposer() {
    return composer;
}

std::string HivelyTrackerDecoder::getGenre() {
    return genre;
}

std::string HivelyTrackerDecoder::getFormatNameInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return formatName;
}

int HivelyTrackerDecoder::getFormatVersionInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return formatVersion;
}

int HivelyTrackerDecoder::getPositionCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return 0;
    return std::max(0, static_cast<int>(tune->ht_PositionNr));
}

int HivelyTrackerDecoder::getRestartPositionInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return -1;
    return std::max(0, static_cast<int>(tune->ht_Restart));
}

int HivelyTrackerDecoder::getTrackLengthRowsInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return 0;
    return std::max(0, static_cast<int>(tune->ht_TrackLength));
}

int HivelyTrackerDecoder::getTrackCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return 0;
    return std::max(0, static_cast<int>(tune->ht_TrackNr) + 1);
}

int HivelyTrackerDecoder::getInstrumentCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return 0;
    return std::max(0, static_cast<int>(tune->ht_InstrumentNr));
}

int HivelyTrackerDecoder::getSpeedMultiplierInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return 0;
    return std::max(0, static_cast<int>(tune->ht_SpeedMultiplier));
}

int HivelyTrackerDecoder::getCurrentPositionInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return -1;
    return std::max(0, static_cast<int>(tune->ht_PosNr));
}

int HivelyTrackerDecoder::getCurrentRowInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return -1;
    return std::max(0, static_cast<int>(tune->ht_NoteNr));
}

int HivelyTrackerDecoder::getCurrentTempoInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return 0;
    return std::max(0, static_cast<int>(tune->ht_Tempo));
}

int HivelyTrackerDecoder::getMixGainPercentInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return 0;
    return std::max(0, static_cast<int>((static_cast<int64_t>(tune->ht_mixgain) * 100 + 128) / 256));
}

std::string HivelyTrackerDecoder::getInstrumentNamesInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return "";
    const int instrumentCount = std::max(0, static_cast<int>(tune->ht_InstrumentNr));
    std::string names;
    for (int i = 1; i <= instrumentCount; ++i) {
        const std::string name = copyFixedString(tune->ht_Instruments[i].ins_Name, sizeof(tune->ht_Instruments[i].ins_Name));
        if (!names.empty()) {
            names.push_back('\n');
        }
        names.append(std::to_string(i));
        names.append(". ");
        names.append(name);
    }
    return names;
}

void HivelyTrackerDecoder::setOutputSampleRate(int sampleRate) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    requestedSampleRateHz = clampSampleRate(sampleRate);
    if (!tune) {
        sampleRateHz = requestedSampleRateHz;
    }
}

void HivelyTrackerDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const int normalized = normalizeRepeatMode(mode);
    repeatMode.store(normalized);
    if (normalized != 0) {
        stopAfterPendingDrain = false;
    }
}

int HivelyTrackerDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int HivelyTrackerDecoder::getPlaybackCapabilities() const {
    int caps = PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    if (durationReliable.load()) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

double HivelyTrackerDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!tune) return -1.0;
    double position = playbackPositionSeconds;
    const bool hasReliableDuration = durationReliable.load() && durationSeconds > 0.0;
    if (hasReliableDuration) {
        if (repeatMode.load() == 2) {
            position = std::fmod(position, durationSeconds);
            if (position < 0.0) {
                position += durationSeconds;
            }
        } else {
            position = std::min(position, durationSeconds);
        }
    }
    return position;
}

AudioDecoder::TimelineMode HivelyTrackerDecoder::getTimelineMode() const {
    return TimelineMode::Discontinuous;
}

std::vector<std::string> HivelyTrackerDecoder::getSupportedExtensions() {
    return {
            "ahx",
            "hvl"
    };
}
