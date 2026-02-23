#include "FurnaceDecoder.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iterator>
#include <limits>

#include <furnace/engine/engine.h>

namespace {
constexpr double kSeekEpsilonSeconds = 0.000001;
constexpr double kLoopEpsilonSeconds = 0.000001;

std::vector<unsigned char> readBinaryFile(const std::string& path) {
    std::ifstream stream(path, std::ios::binary | std::ios::ate);
    if (!stream) {
        return {};
    }

    const std::streamsize size = stream.tellg();
    if (size <= 0) {
        return {};
    }

    std::vector<unsigned char> data(static_cast<size_t>(size));
    stream.seekg(0, std::ios::beg);
    if (!stream.read(reinterpret_cast<char*>(data.data()), size)) {
        return {};
    }
    return data;
}

std::string uppercaseExtensionWithoutDot(const std::filesystem::path& path) {
    std::string ext = path.extension().string();
    if (!ext.empty() && ext[0] == '.') {
        ext.erase(ext.begin());
    }
    std::transform(ext.begin(), ext.end(), ext.begin(), [](unsigned char ch) {
        return static_cast<char>(std::toupper(ch));
    });
    return ext;
}

int parseIntOptionString(const char* value, int fallback) {
    if (!value) return fallback;
    char* end = nullptr;
    const long parsed = std::strtol(value, &end, 10);
    if (end == value || (end && *end != '\0')) {
        return fallback;
    }
    return static_cast<int>(parsed);
}
} // namespace

FurnaceDecoder::FurnaceDecoder() = default;

FurnaceDecoder::~FurnaceDecoder() {
    close();
}

int FurnaceDecoder::normalizeRepeatMode(int mode) {
    if (mode < 0 || mode > 3) {
        return 0;
    }
    return mode;
}

int FurnaceDecoder::clampSampleRate(int sampleRateHz) {
    return std::clamp(sampleRateHz, 8000, 192000);
}

bool FurnaceDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();

    if (!path || path[0] == '\0') {
        return false;
    }

    sourcePath = path;
    const auto fileData = readBinaryFile(sourcePath);
    if (fileData.empty()) {
        closeInternalLocked();
        return false;
    }

    auto localEngine = std::make_unique<DivEngine>();
    localEngine->setAudio(DIV_AUDIO_DUMMY);
    localEngine->setView(DIV_STATUS_NOTHING);
    localEngine->setConsoleMode(true, false);
    localEngine->setConf("audioRate", clampSampleRate(requestedSampleRateHz));
    localEngine->setConf("audioChans", 2);
    localEngine->setConf("audioBufSize", 1024);
    applyCoreOptionsLocked(localEngine.get());

    auto* ownedData = new unsigned char[fileData.size()];
    std::memcpy(ownedData, fileData.data(), fileData.size());

    if (!localEngine->load(ownedData, fileData.size(), sourcePath.c_str())) {
        localEngine->quit(false);
        closeInternalLocked();
        return false;
    }

    // Keep playback/render backend selection consistent even if loading touched config.
    applyCoreOptionsLocked(localEngine.get());

    if (!localEngine->init()) {
        localEngine->quit(false);
        closeInternalLocked();
        return false;
    }

    engine = std::move(localEngine);
    sampleRateHz = std::max(8000, static_cast<int>(std::lround(engine->getAudioDescGot().rate)));
    channels = 2;
    playbackPositionSeconds = 0.0;
    subtuneCount = std::max(1, static_cast<int>(engine->song.subsong.size()));
    currentSubtuneIndex = std::clamp(static_cast<int>(engine->getCurrentSubSong()), 0, subtuneCount - 1);
    timelineRepeatMode = normalizeRepeatMode(repeatMode.load());
    trackRepeatVirtualInitialized = false;
    trackRepeatVirtualSeconds = 0.0;
    subtuneDurations.assign(static_cast<size_t>(subtuneCount), 0.0);

    refreshMetadataLocked();
    refreshTimelineLocked();
    syncToggleChannelsLocked();
    applyToggleChannelMutesLocked();
    applyRepeatModeLocked();

    if (!engine->play()) {
        closeInternalLocked();
        return false;
    }

    return true;
}

void FurnaceDecoder::closeInternalLocked() {
    if (engine) {
        engine->quit(false);
        engine.reset();
    }

    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    formatName = "Furnace";
    sampleRateHz = 44100;
    channels = 2;
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    durationReliable = false;
    durationSeconds = 0.0;
    loopRegionReliable = false;
    loopStartSeconds = 0.0;
    loopLengthSeconds = 0.0;
    timelineRepeatMode = 0;
    trackRepeatVirtualInitialized = false;
    trackRepeatVirtualSeconds = 0.0;
    playbackPositionSeconds = 0.0;
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    seekTimeline.clear();
    subtuneDurations.clear();
    leftScratch.clear();
    rightScratch.clear();
}

void FurnaceDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

void FurnaceDecoder::refreshMetadataLocked() {
    const auto sourcePathFs = std::filesystem::path(sourcePath);
    title = sourcePathFs.stem().string();
    artist.clear();
    composer.clear();
    genre = "Tracker";
    formatName = "Furnace";

    const std::string extensionLabel = uppercaseExtensionWithoutDot(sourcePathFs);
    if (!extensionLabel.empty()) {
        formatName = extensionLabel;
    }

    if (!engine) {
        return;
    }

    subtuneCount = std::max(1, static_cast<int>(engine->song.subsong.size()));
    currentSubtuneIndex = std::clamp(static_cast<int>(engine->getCurrentSubSong()), 0, subtuneCount - 1);
    if (static_cast<int>(subtuneDurations.size()) != subtuneCount) {
        subtuneDurations.assign(static_cast<size_t>(subtuneCount), 0.0);
    }

    if (!engine->song.name.empty()) {
        title = engine->song.name;
    }
    if (engine->curSubSong != nullptr && !engine->curSubSong->name.empty()) {
        title = engine->curSubSong->name;
    }
    if (!engine->song.author.empty()) {
        artist = engine->song.author;
        composer = artist;
    }
}

void FurnaceDecoder::refreshTimelineLocked() {
    seekTimeline.clear();
    durationReliable = false;
    durationSeconds = 0.0;
    loopRegionReliable = false;
    loopStartSeconds = 0.0;
    loopLengthSeconds = 0.0;

    if (!engine || !engine->curSubSong) {
        return;
    }

    engine->calcSongTimestamps();
    auto* subSong = engine->curSubSong;

    const double totalDuration = subSong->ts.totalTime.toDouble();
    if (std::isfinite(totalDuration) && totalDuration > 0.0) {
        durationReliable = true;
        durationSeconds = totalDuration;
        if (currentSubtuneIndex >= 0 && currentSubtuneIndex < static_cast<int>(subtuneDurations.size())) {
            subtuneDurations[static_cast<size_t>(currentSubtuneIndex)] = totalDuration;
        }
    }
    if (durationReliable && subSong->ts.isLoopDefined && subSong->ts.isLoopable) {
        const double loopStart = subSong->ts.loopStartTime.toDouble();
        if (std::isfinite(loopStart) &&
            loopStart >= 0.0 &&
            durationSeconds > loopStart + kLoopEpsilonSeconds) {
            loopRegionReliable = true;
            loopStartSeconds = loopStart;
            loopLengthSeconds = durationSeconds - loopStartSeconds;
        }
    }

    const int orderCount = std::max(0, subSong->ordersLen);
    const int rowCount = std::max(1, subSong->patLen);
    seekTimeline.reserve(static_cast<size_t>(orderCount * rowCount));
    for (int order = 0; order < orderCount; ++order) {
        for (int row = 0; row < rowCount; ++row) {
            TimeMicros stamp = subSong->ts.getTimes(order, row);
            if (stamp.seconds < 0) {
                continue;
            }

            const double timestampSeconds = stamp.toDouble();
            if (!std::isfinite(timestampSeconds) || timestampSeconds < 0.0) {
                continue;
            }

            if (!seekTimeline.empty()) {
                const double lastSeconds = seekTimeline.back().seconds;
                if (timestampSeconds + kSeekEpsilonSeconds < lastSeconds) {
                    continue;
                }
                if (std::abs(timestampSeconds - lastSeconds) <= kSeekEpsilonSeconds) {
                    continue;
                }
            }

            seekTimeline.push_back(SeekPoint {
                    timestampSeconds,
                    order,
                    row
            });
        }
    }

    if (seekTimeline.empty()) {
        seekTimeline.push_back(SeekPoint { 0.0, 0, 0 });
    }
}

void FurnaceDecoder::applyRepeatModeLocked() {
    if (!engine) {
        return;
    }

    const int mode = normalizeRepeatMode(repeatMode.load());
    if (mode == 0) {
        // Stop after first full song pass in non-repeat mode.
        engine->setLoops(1);
    } else {
        engine->setLoops(-1);
    }
}

void FurnaceDecoder::syncToggleChannelsLocked() {
    if (!engine) {
        toggleChannelNames.clear();
        toggleChannelMuted.clear();
        return;
    }

    const int channelCount = std::clamp(engine->getTotalChannelCount(), 0, DIV_MAX_CHANS);
    std::vector<std::string> nextNames;
    nextNames.reserve(static_cast<size_t>(channelCount));

    for (int i = 0; i < channelCount; ++i) {
        std::string channelName;
        const char* name = engine->getChannelName(i);
        if (name && name[0] != '\0' && std::strcmp(name, "??") != 0) {
            channelName = name;
        } else {
            const char* shortName = engine->getChannelShortName(i);
            if (shortName && shortName[0] != '\0' && std::strcmp(shortName, "??") != 0) {
                channelName = shortName;
            }
        }
        if (channelName.empty()) {
            channelName = "Channel " + std::to_string(i + 1);
        }
        nextNames.push_back(channelName);
    }

    if (nextNames == toggleChannelNames &&
        static_cast<int>(toggleChannelMuted.size()) == channelCount) {
        return;
    }

    std::vector<bool> nextMuted(static_cast<size_t>(channelCount), false);
    const int preserved = std::min(channelCount, static_cast<int>(toggleChannelMuted.size()));
    for (int i = 0; i < preserved; ++i) {
        nextMuted[static_cast<size_t>(i)] = toggleChannelMuted[static_cast<size_t>(i)];
    }
    toggleChannelNames = std::move(nextNames);
    toggleChannelMuted = std::move(nextMuted);
}

void FurnaceDecoder::applyToggleChannelMutesLocked() {
    if (!engine || toggleChannelMuted.empty()) {
        return;
    }
    const int channelCount = std::min(
            engine->getTotalChannelCount(),
            static_cast<int>(toggleChannelMuted.size())
    );
    for (int i = 0; i < channelCount; ++i) {
        engine->muteChannel(i, toggleChannelMuted[static_cast<size_t>(i)]);
    }
}

void FurnaceDecoder::applyCoreOptionsLocked(DivEngine* targetEngine) const {
    if (!targetEngine) {
        return;
    }
    targetEngine->setConf("ym2612Core", optionYm2612Core);
    targetEngine->setConf("ym2612CoreRender", optionYm2612Core);
    targetEngine->setConf("snCore", optionSnCore);
    targetEngine->setConf("snCoreRender", optionSnCore);
    targetEngine->setConf("nesCore", optionNesCore);
    targetEngine->setConf("nesCoreRender", optionNesCore);
    targetEngine->setConf("c64Core", optionC64Core);
    targetEngine->setConf("c64CoreRender", optionC64Core);
    targetEngine->setConf("gbQuality", optionGbQuality);
    targetEngine->setConf("gbQualityRender", optionGbQuality);
    targetEngine->setConf("dsidQuality", optionDsidQuality);
    targetEngine->setConf("dsidQualityRender", optionDsidQuality);
    targetEngine->setConf("ayCore", optionAyCore);
    targetEngine->setConf("ayCoreRender", optionAyCore);
}

double FurnaceDecoder::normalizeTimelinePositionLocked(double seconds) const {
    if (!std::isfinite(seconds)) {
        return playbackPositionSeconds;
    }

    double normalized = std::max(0.0, seconds);
    if (!durationReliable || durationSeconds <= 0.0) {
        return normalized;
    }

    const int mode = normalizeRepeatMode(repeatMode.load());
    if (mode == 2) {
        if (loopRegionReliable && loopLengthSeconds > kLoopEpsilonSeconds) {
            if (normalized >= loopStartSeconds) {
                normalized = loopStartSeconds + std::fmod(normalized - loopStartSeconds, loopLengthSeconds);
            }
        } else {
            normalized = std::fmod(normalized, durationSeconds);
        }
    } else if (mode == 1 || mode == 3) {
        normalized = std::fmod(normalized, durationSeconds);
    } else {
        normalized = std::clamp(normalized, 0.0, durationSeconds);
    }

    if (normalized < 0.0) {
        normalized += durationSeconds;
    }
    return normalized;
}

double FurnaceDecoder::normalizeSeekTargetLocked(double seconds) const {
    return normalizeTimelinePositionLocked(std::max(0.0, seconds));
}

bool FurnaceDecoder::seekToTimelineLocked(double targetSeconds) {
    if (!engine || seekTimeline.empty()) {
        return false;
    }

    auto it = std::lower_bound(
            seekTimeline.begin(),
            seekTimeline.end(),
            targetSeconds,
            [](const SeekPoint& point, double value) {
                return point.seconds < value;
            });

    if (it == seekTimeline.end()) {
        it = std::prev(seekTimeline.end());
    } else if (it != seekTimeline.begin() && it->seconds > targetSeconds + kSeekEpsilonSeconds) {
        it = std::prev(it);
    }

    const int order = std::max(0, it->order);
    const int row = std::max(0, it->row);
    if (engine->isPlaying()) {
        engine->stop();
    }
    engine->setOrder(static_cast<unsigned char>(std::clamp(order, 0, 255)));
    engine->playToRow(std::max(0, row));
    return true;
}

int FurnaceDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine || !buffer || numFrames <= 0) {
        return 0;
    }

    if (!engine->isPlaying()) {
        return 0;
    }

    if (static_cast<int>(leftScratch.size()) < numFrames) {
        leftScratch.resize(static_cast<size_t>(numFrames));
    }
    if (static_cast<int>(rightScratch.size()) < numFrames) {
        rightScratch.resize(static_cast<size_t>(numFrames));
    }

    std::fill_n(leftScratch.data(), numFrames, 0.0f);
    std::fill_n(rightScratch.data(), numFrames, 0.0f);
    float* planarOut[2] = { leftScratch.data(), rightScratch.data() };
    engine->nextBuf(nullptr, planarOut, 0, 2, static_cast<unsigned int>(numFrames));

    for (int i = 0; i < numFrames; ++i) {
        const float left = leftScratch[static_cast<size_t>(i)];
        const float right = rightScratch[static_cast<size_t>(i)];
        buffer[static_cast<size_t>(i * channels)] = left;
        if (channels > 1) {
            buffer[static_cast<size_t>(i * channels + 1)] = right;
        }
    }

    const int mode = normalizeRepeatMode(repeatMode.load());
    const double deltaSeconds = (sampleRateHz > 0)
            ? (static_cast<double>(numFrames) / static_cast<double>(sampleRateHz))
            : 0.0;
    if (mode != timelineRepeatMode) {
        timelineRepeatMode = mode;
        trackRepeatVirtualInitialized = false;
        trackRepeatVirtualSeconds = 0.0;
    }

    const double rawPlaybackSeconds = std::max(0.0, engine->getCurTime().toDouble());

    if ((mode == 1 || mode == 3) && durationReliable && durationSeconds > 0.0) {
        if (!trackRepeatVirtualInitialized) {
            const double initial = std::clamp(playbackPositionSeconds, 0.0, durationSeconds);
            trackRepeatVirtualSeconds = initial;
            trackRepeatVirtualInitialized = true;
        }

        trackRepeatVirtualSeconds = std::max(0.0, trackRepeatVirtualSeconds + deltaSeconds);
        const bool wrappedTrackEnd = trackRepeatVirtualSeconds >= durationSeconds - kLoopEpsilonSeconds;
        if (wrappedTrackEnd) {
            trackRepeatVirtualSeconds = std::fmod(trackRepeatVirtualSeconds, durationSeconds);
            if (trackRepeatVirtualSeconds < 0.0) {
                trackRepeatVirtualSeconds += durationSeconds;
            }
            if (engine->isPlaying()) {
                engine->stop();
            }
            engine->setOrder(0);
            engine->playToRow(0);
            applyRepeatModeLocked();
            if (!engine->isPlaying()) {
                engine->play();
            }
        }

        playbackPositionSeconds = std::clamp(trackRepeatVirtualSeconds, 0.0, durationSeconds);
    } else {
        trackRepeatVirtualInitialized = false;
        trackRepeatVirtualSeconds = 0.0;
        if (mode == 0 &&
            durationReliable &&
            durationSeconds > 0.0 &&
            rawPlaybackSeconds > durationSeconds + kLoopEpsilonSeconds) {
            // Core time can keep increasing after a loop; keep UI timeline continuous
            // and enforce a real terminal stop at track end in no-repeat mode.
            const double continuityBase = std::clamp(playbackPositionSeconds, 0.0, durationSeconds);
            playbackPositionSeconds = std::clamp(continuityBase + deltaSeconds, 0.0, durationSeconds);
            if (playbackPositionSeconds >= durationSeconds - kLoopEpsilonSeconds && engine->isPlaying()) {
                engine->stop();
            }
        } else {
            playbackPositionSeconds = normalizeTimelinePositionLocked(rawPlaybackSeconds);
        }
    }

    return numFrames;
}

void FurnaceDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return;
    }

    const double targetSeconds = normalizeSeekTargetLocked(seconds);

    if (targetSeconds <= 0.0) {
        if (engine->isPlaying()) {
            engine->stop();
        }
        engine->setOrder(0);
        engine->playToRow(0);
    } else if (!seekToTimelineLocked(targetSeconds)) {
        playbackPositionSeconds = targetSeconds;
        return;
    }

    applyRepeatModeLocked();
    if (!engine->isPlaying()) {
        engine->play();
    }

    const int mode = normalizeRepeatMode(repeatMode.load());
    timelineRepeatMode = mode;
    if ((mode == 1 || mode == 3) && durationReliable && durationSeconds > 0.0) {
        trackRepeatVirtualSeconds = std::clamp(targetSeconds, 0.0, durationSeconds);
        trackRepeatVirtualInitialized = true;
        playbackPositionSeconds = trackRepeatVirtualSeconds;
    } else {
        trackRepeatVirtualInitialized = false;
        trackRepeatVirtualSeconds = 0.0;
        playbackPositionSeconds = normalizeTimelinePositionLocked(engine->getCurTime().toDouble());
    }

    if (!std::isfinite(playbackPositionSeconds) || playbackPositionSeconds < 0.0) {
        playbackPositionSeconds = targetSeconds;
    }
}

double FurnaceDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationReliable ? durationSeconds : 0.0;
}

int FurnaceDecoder::getSampleRate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sampleRateHz;
}

int FurnaceDecoder::getBitDepth() {
    return 32;
}

std::string FurnaceDecoder::getBitDepthLabel() {
    return "32-bit float";
}

int FurnaceDecoder::getDisplayChannelCount() {
    return getChannelCount();
}

int FurnaceDecoder::getChannelCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return channels;
}

int FurnaceDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return subtuneCount;
}

int FurnaceDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return currentSubtuneIndex;
}

bool FurnaceDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine || index < 0 || index >= subtuneCount) {
        return false;
    }
    if (index == currentSubtuneIndex) {
        return true;
    }

    engine->changeSongP(static_cast<size_t>(index));
    currentSubtuneIndex = index;
    playbackPositionSeconds = 0.0;
    timelineRepeatMode = normalizeRepeatMode(repeatMode.load());
    trackRepeatVirtualInitialized = false;
    trackRepeatVirtualSeconds = 0.0;
    refreshMetadataLocked();
    refreshTimelineLocked();
    syncToggleChannelsLocked();
    applyToggleChannelMutesLocked();
    applyRepeatModeLocked();
    return engine->play();
}

std::string FurnaceDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine || index < 0 || index >= subtuneCount) {
        return "";
    }
    if (index >= 0 && index < static_cast<int>(engine->song.subsong.size())) {
        auto* subSong = engine->song.subsong[static_cast<size_t>(index)];
        if (subSong != nullptr && !subSong->name.empty()) {
            return subSong->name;
        }
    }
    if (subtuneCount <= 1) {
        return title;
    }
    return "Subsong " + std::to_string(index + 1);
}

std::string FurnaceDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) {
        return "";
    }
    return artist;
}

double FurnaceDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= static_cast<int>(subtuneDurations.size())) {
        return 0.0;
    }
    return subtuneDurations[static_cast<size_t>(index)];
}

std::string FurnaceDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string FurnaceDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string FurnaceDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string FurnaceDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::vector<std::string> FurnaceDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    return toggleChannelNames;
}

std::vector<uint8_t> FurnaceDecoder::getToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    return std::vector<uint8_t>(toggleChannelNames.size(), 1u);
}

void FurnaceDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    if (engine) {
        engine->muteChannel(channelIndex, enabled);
    }
}

bool FurnaceDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void FurnaceDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (toggleChannelMuted.empty()) {
        return;
    }
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    if (engine) {
        engine->unmuteAll();
    }
}

void FurnaceDecoder::setOutputSampleRate(int sampleRateHzValue) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    requestedSampleRateHz = clampSampleRate(sampleRateHzValue > 0 ? sampleRateHzValue : 44100);
}

void FurnaceDecoder::setOption(const char* name, const char* value) {
    if (!name) {
        return;
    }

    std::lock_guard<std::mutex> lock(decodeMutex);
    if (std::strcmp(name, "furnace.ym2612_core") == 0) {
        optionYm2612Core = std::clamp(parseIntOptionString(value, optionYm2612Core), 0, 2);
    } else if (std::strcmp(name, "furnace.sn_core") == 0) {
        optionSnCore = std::clamp(parseIntOptionString(value, optionSnCore), 0, 1);
    } else if (std::strcmp(name, "furnace.nes_core") == 0) {
        optionNesCore = std::clamp(parseIntOptionString(value, optionNesCore), 0, 1);
    } else if (std::strcmp(name, "furnace.c64_core") == 0) {
        optionC64Core = std::clamp(parseIntOptionString(value, optionC64Core), 0, 2);
    } else if (std::strcmp(name, "furnace.gb_quality") == 0) {
        optionGbQuality = std::clamp(parseIntOptionString(value, optionGbQuality), 0, 5);
    } else if (std::strcmp(name, "furnace.dsid_quality") == 0) {
        optionDsidQuality = std::clamp(parseIntOptionString(value, optionDsidQuality), 0, 5);
    } else if (std::strcmp(name, "furnace.ay_core") == 0) {
        optionAyCore = std::clamp(parseIntOptionString(value, optionAyCore), 0, 1);
    } else {
        return;
    }

    if (engine) {
        applyCoreOptionsLocked(engine.get());
    }
}

int FurnaceDecoder::getOptionApplyPolicy(const char* name) const {
    if (!name) {
        return OPTION_APPLY_LIVE;
    }
    if (std::strcmp(name, "furnace.ym2612_core") == 0 ||
        std::strcmp(name, "furnace.sn_core") == 0 ||
        std::strcmp(name, "furnace.nes_core") == 0 ||
        std::strcmp(name, "furnace.c64_core") == 0 ||
        std::strcmp(name, "furnace.gb_quality") == 0 ||
        std::strcmp(name, "furnace.dsid_quality") == 0 ||
        std::strcmp(name, "furnace.ay_core") == 0) {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    return OPTION_APPLY_LIVE;
}

void FurnaceDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const int normalizedMode = normalizeRepeatMode(mode);
    repeatMode.store(normalizedMode);
    applyRepeatModeLocked();
    timelineRepeatMode = normalizedMode;
    if (normalizedMode == 1 || normalizedMode == 3) {
        // Initialize on next audio read from current on-screen timeline position.
        trackRepeatVirtualInitialized = false;
        trackRepeatVirtualSeconds = 0.0;
    } else {
        trackRepeatVirtualInitialized = false;
        trackRepeatVirtualSeconds = 0.0;
    }
}

int FurnaceDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int FurnaceDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int caps = PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_DIRECT_SEEK |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    if (durationReliable) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

double FurnaceDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return -1.0;
    }

    const int mode = normalizeRepeatMode(repeatMode.load());
    if ((mode == 1 || mode == 3) && durationReliable && durationSeconds > 0.0 && trackRepeatVirtualInitialized) {
        playbackPositionSeconds = std::clamp(trackRepeatVirtualSeconds, 0.0, durationSeconds);
        return playbackPositionSeconds;
    }

    playbackPositionSeconds = normalizeTimelinePositionLocked(playbackPositionSeconds);
    return playbackPositionSeconds;
}

AudioDecoder::TimelineMode FurnaceDecoder::getTimelineMode() const {
    return TimelineMode::Discontinuous;
}

std::string FurnaceDecoder::getFormatNameInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return formatName;
}

int FurnaceDecoder::getSongVersionInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return 0;
    }
    return std::max(0, static_cast<int>(engine->song.version));
}

std::string FurnaceDecoder::getSystemNameInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return "";
    }
    if (engine->song.systemName.empty()) {
        return "";
    }
    return std::string("1. ") + engine->song.systemName;
}

std::string FurnaceDecoder::getSystemNamesInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return "";
    }

    const int count = std::min(std::max(0, static_cast<int>(engine->song.systemLen)), DIV_MAX_CHIPS);
    std::vector<std::string> names;
    for (int i = 0; i < count; ++i) {
        const char* systemName = engine->getSystemName(engine->song.system[i]);
        if (!systemName || systemName[0] == '\0') {
            continue;
        }
        names.emplace_back(systemName);
    }

    if (!names.empty()) {
        std::string numberedList;
        for (size_t i = 0; i < names.size(); ++i) {
            if (!numberedList.empty()) {
                numberedList += '\n';
            }
            numberedList += std::to_string(i + 1);
            numberedList += ". ";
            numberedList += names[i];
        }
        return numberedList;
    }
    return engine->song.systemName;
}

int FurnaceDecoder::getSystemCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return 0;
    }
    return std::max(0, static_cast<int>(engine->song.systemLen));
}

int FurnaceDecoder::getSongChannelCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return 0;
    }
    return std::max(0, engine->song.chans);
}

int FurnaceDecoder::getInstrumentCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return 0;
    }
    return std::max(0, engine->song.insLen);
}

int FurnaceDecoder::getWavetableCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return 0;
    }
    return std::max(0, engine->song.waveLen);
}

int FurnaceDecoder::getSampleCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return 0;
    }
    return std::max(0, engine->song.sampleLen);
}

int FurnaceDecoder::getOrderCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine || !engine->curSubSong) {
        return 0;
    }
    return std::max(0, engine->curSubSong->ordersLen);
}

int FurnaceDecoder::getRowsPerPatternInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine || !engine->curSubSong) {
        return 0;
    }
    return std::max(0, engine->curSubSong->patLen);
}

int FurnaceDecoder::getCurrentOrderInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return -1;
    }
    return std::max(0, static_cast<int>(engine->getOrder()));
}

int FurnaceDecoder::getCurrentRowInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return -1;
    }
    return std::max(0, engine->getRow());
}

int FurnaceDecoder::getCurrentTickInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return -1;
    }
    int order = 0;
    int row = 0;
    int tick = 0;
    int speed = 0;
    engine->getPlayPosTick(order, row, tick, speed);
    return std::max(0, tick);
}

int FurnaceDecoder::getCurrentSpeedInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return 0;
    }
    int order = 0;
    int row = 0;
    int tick = 0;
    int speed = 0;
    engine->getPlayPosTick(order, row, tick, speed);
    return std::max(0, speed);
}

int FurnaceDecoder::getGrooveLengthInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return 0;
    }
    return std::max(0, static_cast<int>(engine->getSpeeds().len));
}

float FurnaceDecoder::getCurrentHzInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!engine) {
        return 0.0f;
    }
    const float hz = engine->getCurHz();
    if (!std::isfinite(hz) || hz <= 0.0f) {
        return 0.0f;
    }
    return hz;
}

std::vector<std::string> FurnaceDecoder::getSupportedExtensions() {
    return {
            "fur",
            "dmf"
    };
}
