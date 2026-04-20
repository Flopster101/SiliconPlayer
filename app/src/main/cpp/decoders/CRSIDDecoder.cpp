#include "CRSIDDecoder.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstdint>
#include <fstream>
#include <iterator>
#include <sstream>

#if defined(__clang__)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wregister"
#endif
extern "C" {
#include <crsid/libcRSID.h>
}
#if defined(__clang__)
#pragma clang diagnostic pop
#endif

namespace {
constexpr int kCrsidMinSampleRateHz = 8000;
constexpr int kCrsidMaxSampleRateHz = 65535;
constexpr int kCrsidOutputChannels = 2;
constexpr int kCrsidBufferFrames = 2048;
constexpr int kCrsidSeekDiscardChunkFrames = 4096;
constexpr unsigned char kCrsidFileVersionWebSid = 0x4E;

int clampSampleRate(int sampleRateHz) {
    return std::clamp(sampleRateHz, kCrsidMinSampleRateHz, kCrsidMaxSampleRateHz);
}

bool parseBoolString(const std::string& value, bool fallback) {
    if (value == "1" || value == "true" || value == "TRUE" || value == "True" ||
        value == "yes" || value == "YES" || value == "on" || value == "ON") {
        return true;
    }
    if (value == "0" || value == "false" || value == "FALSE" || value == "False" ||
        value == "no" || value == "NO" || value == "off" || value == "OFF") {
        return false;
    }
    return fallback;
}

int parseIntString(const std::string& value, int fallback) {
    try {
        return std::stoi(value);
    } catch (...) {
        return fallback;
    }
}

std::string trimAscii(std::string value) {
    const auto isTrimmed = [](unsigned char ch) {
        return ch == 0 || std::isspace(ch) != 0;
    };

    while (!value.empty() && isTrimmed(static_cast<unsigned char>(value.back()))) {
        value.pop_back();
    }

    size_t start = 0;
    while (start < value.size() && isTrimmed(static_cast<unsigned char>(value[start]))) {
        ++start;
    }
    if (start > 0) {
        value.erase(0, start);
    }
    return value;
}

template <size_t N>
std::string sidHeaderString(const char (&raw)[N]) {
    size_t length = 0;
    while (length < N && raw[length] != '\0') {
        ++length;
    }
    return trimAscii(std::string(raw, raw + length));
}

std::string sidFormatNameFromHeader(const cRSID_SIDheader* header) {
    if (!header) return "";
    const std::string magic(reinterpret_cast<const char*>(header->MagicString), 4);
    if (header->Version == kCrsidFileVersionWebSid) {
        return "WebSID";
    }
    std::ostringstream out;
    out << trimAscii(magic);
    if (header->Version > 0) {
        out << " v" << static_cast<int>(header->Version);
    }
    return out.str();
}

std::string sidClockFromHeader(const cRSID_SIDheader* header) {
    if (!header) return "";
    switch ((header->ModelFormatStandard & 0x0C) >> 2) {
        case 1: return "PAL";
        case 2: return "NTSC";
        case 3: return "Any";
        default: return "Unknown";
    }
}

std::string sidClockFromRuntime() {
    return cRSID.VideoStandard ? "PAL" : "NTSC";
}

std::string sidClockOverrideLabel(int mode) {
    switch (mode) {
        case 1: return "PAL";
        case 2: return "NTSC";
        default: return "";
    }
}

std::string sidCompatibilityFromHeader(const cRSID_SIDheader* header) {
    if (!header) return "";
    if (header->MagicString[0] == 'R') {
        return "Real C64";
    }
    return "PSID";
}

std::string sidSpeedFromRuntime() {
    return cRSID.TimerSource ? "CIA" : "Vertical blank";
}

std::string sidModelBitsToString(unsigned char bits) {
    switch (bits & 0x03) {
        case 1: return "6581";
        case 2: return "8580";
        case 3: return "6581/8580";
        default: return "Unknown";
    }
}

std::string declaredModelForIndex(const cRSID_SIDheader* header, int sidIndex) {
    if (!header || sidIndex < 1 || sidIndex > 4) {
        return "Unknown";
    }

    if (header->Version == kCrsidFileVersionWebSid) {
        switch (sidIndex) {
            case 1: return sidModelBitsToString((header->ModelFormatStandard & 0x30) >> 4);
            case 2: return sidModelBitsToString((header->SID2flagsL & 0x30) >> 4);
            case 3: return sidModelBitsToString((header->SID3flagsL & 0x30) >> 4);
            case 4: return sidModelBitsToString((header->SID4flagsL & 0x30) >> 4);
            default: return "Unknown";
        }
    }

    if (sidIndex == 1) {
        return sidModelBitsToString((header->ModelFormatStandard & 0x30) >> 4);
    }

    if (sidIndex == 2) {
        const unsigned char bits = (header->ModelFormatStandard & 0xC0) >> 6;
        return bits == 0 ? declaredModelForIndex(header, 1) : sidModelBitsToString(bits);
    }

    if (sidIndex == 3) {
        const unsigned char bits = header->ModelFormatStandardH & 0x03;
        return bits == 0 ? declaredModelForIndex(header, 1) : sidModelBitsToString(bits);
    }

    return "Unknown";
}

std::string currentModelForChip(int sidNumber) {
    switch (cRSID_getSIDmodel(sidNumber)) {
        case 6581: return "6581";
        case 8580: return "8580";
        default: return "Unknown";
    }
}

std::string joinStringParts(const std::vector<std::string>& parts) {
    std::ostringstream out;
    bool first = true;
    for (const std::string& part : parts) {
        if (part.empty()) continue;
        if (!first) out << ", ";
        out << part;
        first = false;
    }
    return out.str();
}
}

CRSIDDecoder::CRSIDDecoder() = default;

CRSIDDecoder::~CRSIDDecoder() {
    close();
}

bool CRSIDDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeLocked();

    if (!path || !loadFileLocked(path)) {
        return false;
    }

    sourcePath = path;
    return initializeEngineLocked(-1);
}

void CRSIDDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeLocked();
    fileData.clear();
    sourcePath.clear();
}

int CRSIDDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!buffer || numFrames <= 0 || fileData.empty()) {
        return 0;
    }

    const int mode = repeatMode.load();
    const bool loopPointRepeatActive = mode == 2;
    const bool canRepeatCurrent = mode == 1 || mode == 3;
    int framesWritten = 0;
    while (framesWritten < numFrames) {
        if (!loopPointRepeatActive &&
            durationReliable &&
            currentDurationSeconds > 0.0 &&
            playbackPositionSeconds >= currentDurationSeconds) {
            if (canRepeatCurrent) {
                if (!startSubtuneLocked(currentSubtuneIndex)) {
                    break;
                }
                continue;
            }
            endReached = true;
            break;
        }

        const cRSID_Output output = cRSID_generateSample();
        buffer[framesWritten * 2] = static_cast<float>(output.L) / 32768.0f;
        buffer[(framesWritten * 2) + 1] = static_cast<float>(output.R) / 32768.0f;
        ++framesWritten;
        playbackPositionSeconds += 1.0 / static_cast<double>(activeSampleRate);
    }

    return framesWritten;
}

void CRSIDDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (fileData.empty()) {
        return;
    }

    double targetSeconds = std::max(0.0, seconds);
    if (durationReliable && currentDurationSeconds > 0.0) {
        targetSeconds = std::min(targetSeconds, currentDurationSeconds);
    }

    if (!startSubtuneLocked(currentSubtuneIndex)) {
        return;
    }

    const uint64_t targetFrames = static_cast<uint64_t>(targetSeconds * static_cast<double>(activeSampleRate));
    std::array<cRSID_Output, kCrsidSeekDiscardChunkFrames> discardBuffer {};
    uint64_t discardedFrames = 0;
    while (discardedFrames < targetFrames) {
        const uint64_t remaining = targetFrames - discardedFrames;
        const int framesThisPass = static_cast<int>(std::min<uint64_t>(remaining, discardBuffer.size()));
        for (int i = 0; i < framesThisPass; ++i) {
            discardBuffer[static_cast<size_t>(i)] = cRSID_generateSample();
        }
        discardedFrames += static_cast<uint64_t>(framesThisPass);
    }

    playbackPositionSeconds = static_cast<double>(discardedFrames) / static_cast<double>(activeSampleRate);
    endReached = durationReliable && currentDurationSeconds > 0.0 && playbackPositionSeconds >= currentDurationSeconds;
}

double CRSIDDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return currentDurationSeconds;
}

int CRSIDDecoder::getSampleRate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return activeSampleRate;
}

int CRSIDDecoder::getBitDepth() {
    return 16;
}

std::string CRSIDDecoder::getBitDepthLabel() {
    return "16-bit";
}

int CRSIDDecoder::getChannelCount() {
    return kCrsidOutputChannels;
}

int CRSIDDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return subtuneCount;
}

int CRSIDDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return currentSubtuneIndex;
}

bool CRSIDDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) {
        return false;
    }
    return startSubtuneLocked(index);
}

std::string CRSIDDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) {
        return "";
    }
    return title;
}

std::string CRSIDDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) {
        return "";
    }
    return artist;
}

double CRSIDDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= static_cast<int>(subtuneDurationsSeconds.size())) {
        return 0.0;
    }
    return subtuneDurationsSeconds[static_cast<size_t>(index)];
}

std::string CRSIDDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string CRSIDDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string CRSIDDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string CRSIDDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string CRSIDDecoder::getComment() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return comment;
}

void CRSIDDecoder::setOutputSampleRate(int sampleRateHz) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const int normalizedRate = clampSampleRate(sampleRateHz);
    if (requestedSampleRate == normalizedRate) {
        return;
    }

    requestedSampleRate = normalizedRate;
    // cRSID sample-rate changes are restart-required.
    // Keep the requested value and apply it on the next open()/reinitialize path
    // instead of restarting playback on every resume.
    if (fileData.empty()) {
        activeSampleRate = normalizedRate;
    }
}

void CRSIDDecoder::setRepeatMode(int mode) {
    repeatMode.store(mode);
}

int CRSIDDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int CRSIDDecoder::getPlaybackCapabilities() const {
    return PLAYBACK_CAP_SEEK |
           PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
           PLAYBACK_CAP_LIVE_REPEAT_MODE;
}

double CRSIDDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return playbackPositionSeconds;
}

AudioDecoder::TimelineMode CRSIDDecoder::getTimelineMode() const {
    return TimelineMode::Discontinuous;
}

std::string CRSIDDecoder::getSidFormatName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidFormatName;
}

std::string CRSIDDecoder::getSidClockName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidClockName;
}

std::string CRSIDDecoder::getSidSpeedName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidSpeedName;
}

std::string CRSIDDecoder::getSidCompatibilityName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidCompatibilityName;
}

std::string CRSIDDecoder::getSidBackendName() {
    return "cRSID";
}

int CRSIDDecoder::getSidChipCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidChipCount;
}

std::string CRSIDDecoder::getSidModelSummary() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidModelSummary;
}

std::string CRSIDDecoder::getSidCurrentModelSummary() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidCurrentModelSummary;
}

std::string CRSIDDecoder::getSidBaseAddressSummary() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidBaseAddressSummary;
}

std::string CRSIDDecoder::getSidCommentSummary() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sidCommentSummary;
}

std::vector<std::string> CRSIDDecoder::getSupportedExtensions() {
    return { "sid", "psid", "rsid" };
}

bool CRSIDDecoder::loadFileLocked(const char* path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        return false;
    }

    fileData.assign(
            std::istreambuf_iterator<char>(input),
            std::istreambuf_iterator<char>()
    );
    return !fileData.empty();
}

bool CRSIDDecoder::initializeEngineLocked(int subtuneIndex) {
    closeLocked();
    if (fileData.empty()) {
        return false;
    }

    activeSampleRate = clampSampleRate(requestedSampleRate);
    if (cRSID_init(static_cast<unsigned short>(activeSampleRate), kCrsidBufferFrames) == nullptr) {
        return false;
    }

    cRSID.AutoAdvance = 0;
    cRSID.AutoExit = 0;
    cRSID.FadeOut = 0;
    cRSID.PlaybackSpeed = 1;
    cRSID.FallbackPlayTime = 0;
    applyPlaybackOptionsLocked();

    auto* header = cRSID_processSIDfileData(fileData.data(), static_cast<int>(fileData.size()));
    if (!header) {
        closeLocked();
        return false;
    }

    refreshHeaderMetadataLocked(header);
    subtuneCount = std::clamp(static_cast<int>(header->SubtuneAmount), 1, static_cast<int>(CRSID_SUBTUNE_AMOUNT_MAX));
    declaredSubtuneDurationsSeconds.assign(static_cast<size_t>(subtuneCount), 0.0);
    subtuneDurationsSeconds.assign(static_cast<size_t>(subtuneCount), 0.0);
    for (int i = 0; i < subtuneCount; ++i) {
        const unsigned short seconds = cRSID.SubtuneDurations[i + 1];
        const double declaredDuration = seconds > 0 ? static_cast<double>(seconds) : 0.0;
        declaredSubtuneDurationsSeconds[static_cast<size_t>(i)] = declaredDuration;
        subtuneDurationsSeconds[static_cast<size_t>(i)] = declaredDuration > 0.0
                ? declaredDuration
                : fallbackDurationSeconds;
    }

    const int defaultIndex = std::clamp(
            static_cast<int>((header->DefaultSubtune == 0 ? 1 : header->DefaultSubtune) - 1),
            0,
            subtuneCount - 1
    );
    const int targetIndex = subtuneIndex >= 0 ? subtuneIndex : defaultIndex;
    return startSubtuneLocked(targetIndex);
}

bool CRSIDDecoder::startSubtuneLocked(int subtuneIndex) {
    if (fileData.empty() || subtuneIndex < 0 || subtuneIndex >= subtuneCount) {
        return false;
    }

    auto* header = cRSID_processSIDfileData(fileData.data(), static_cast<int>(fileData.size()));
    if (!header) {
        return false;
    }

    cRSID_initSIDtune(header, static_cast<char>(subtuneIndex + 1));
    cRSID_playSIDtune();

    currentSubtuneIndex = subtuneIndex;
    playbackPositionSeconds = 0.0;
    currentDurationSeconds = subtuneDurationsSeconds[static_cast<size_t>(subtuneIndex)];
    durationReliable = declaredSubtuneDurationsSeconds[static_cast<size_t>(subtuneIndex)] > 0.0;
    endReached = false;
    refreshRuntimeMetadataLocked(header);
    return true;
}

void CRSIDDecoder::closeLocked() {
    cRSID_close();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    comment.clear();
    declaredSubtuneDurationsSeconds.clear();
    subtuneDurationsSeconds.clear();
    sidFormatName.clear();
    sidClockName.clear();
    sidSpeedName.clear();
    sidCompatibilityName.clear();
    sidModelSummary.clear();
    sidCurrentModelSummary.clear();
    sidBaseAddressSummary.clear();
    sidCommentSummary.clear();
    currentSubtuneIndex = 0;
    subtuneCount = 1;
    sidChipCount = 1;
    durationReliable = false;
    endReached = false;
    currentDurationSeconds = 0.0;
    playbackPositionSeconds = 0.0;
}

void CRSIDDecoder::applyPlaybackOptionsLocked() {
    cRSID.MainVolume = 255;
    cRSID.Stereo = CRSID_CHANNELMODE_STEREO;
    cRSID.FallbackPlayTime = static_cast<int>(std::clamp(
            fallbackDurationSeconds,
            0.0,
            86400.0
    ));

    switch (qualityMode) {
        case QualityMode::Light:
            cRSID.HighQualitySID = 0;
            cRSID.HighQualityResampler = 0;
            break;
        case QualityMode::Sinc:
            cRSID.HighQualitySID = 1;
            cRSID.HighQualityResampler = 1;
            break;
        case QualityMode::High:
        default:
            cRSID.HighQualitySID = 1;
            cRSID.HighQualityResampler = 0;
            break;
    }

    switch (sidModelMode) {
        case SidModelMode::Mos6581:
            cRSID.SelectedSIDmodel = 6581;
            break;
        case SidModelMode::Mos8580:
            cRSID.SelectedSIDmodel = 8580;
            break;
        case SidModelMode::Auto:
        default:
            cRSID.SelectedSIDmodel = 0;
            break;
    }

    switch (clockMode) {
        case ClockMode::Pal:
            cRSID.ForcedVideoStandard = CRSID_VIDEOSTANDARD_PAL;
            break;
        case ClockMode::Ntsc:
            cRSID.ForcedVideoStandard = CRSID_VIDEOSTANDARD_NTSC;
            break;
        case ClockMode::Auto:
        default:
            cRSID.ForcedVideoStandard = CRSID_VIDEOSTANDARD_AUTO;
            break;
    }

    cRSID_set6581FilterPreset(static_cast<unsigned char>(filter6581Preset));
}

void CRSIDDecoder::refreshHeaderMetadataLocked(const cRSID_SIDheader* header) {
    if (!header) {
        return;
    }

    title = sidHeaderString(header->Title);
    if (title.empty() && !sourcePath.empty()) {
        const size_t separator = sourcePath.find_last_of("/\\");
        title = separator == std::string::npos ? sourcePath : sourcePath.substr(separator + 1);
    }
    artist = sidHeaderString(header->Author);
    composer = artist;
    genre = "SID";
    comment = sidHeaderString(header->ReleaseInfo);
    sidFormatName = sidFormatNameFromHeader(header);
    sidClockName = sidClockFromHeader(header);
    sidCompatibilityName = sidCompatibilityFromHeader(header);
    sidCommentSummary = comment;
}

void CRSIDDecoder::refreshRuntimeMetadataLocked(const cRSID_SIDheader* header) {
    sidSpeedName = sidSpeedFromRuntime();
    const std::string declaredClock = sidClockFromHeader(header);
    const std::string effectiveClock = sidClockFromRuntime();
    const std::string forcedClock = sidClockOverrideLabel(static_cast<int>(clockMode));

    if (forcedClock.empty()) {
        sidClockName = (declaredClock.empty() || declaredClock == "Unknown" || declaredClock == "Any")
                ? effectiveClock
                : declaredClock;
    } else if (declaredClock.empty() || declaredClock == "Unknown" || declaredClock == "Any" || declaredClock == forcedClock) {
        sidClockName = forcedClock + " (forced)";
    } else {
        sidClockName = forcedClock + " (forced; header: " + declaredClock + ")";
    }

    sidChipCount = 0;
    std::vector<std::string> declaredModels;
    std::vector<std::string> currentModels;
    std::vector<std::string> baseAddresses;
    for (int sidNumber = 1; sidNumber <= 4; ++sidNumber) {
        const unsigned short base = cRSID_getSIDbase(sidNumber);
        if (base == 0) {
            continue;
        }

        ++sidChipCount;

        std::ostringstream baseLabel;
        baseLabel << "SID " << sidNumber << ": 0x" << std::hex << std::uppercase << base;
        baseAddresses.push_back(baseLabel.str());

        declaredModels.push_back("SID " + std::to_string(sidNumber) + ": " + declaredModelForIndex(header, sidNumber));
        currentModels.push_back("SID " + std::to_string(sidNumber) + ": " + currentModelForChip(sidNumber));
    }

    sidChipCount = std::max(sidChipCount, 1);
    sidModelSummary = joinStringParts(declaredModels);
    sidCurrentModelSummary = joinStringParts(currentModels);
    sidBaseAddressSummary = joinStringParts(baseAddresses);
}

void CRSIDDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) {
        return;
    }

    std::lock_guard<std::mutex> lock(decodeMutex);
    const std::string optionName(name);
    const std::string optionValue(value);
    if (optionName == "crsid.sid_model_mode") {
        const int parsed = parseIntString(optionValue, static_cast<int>(sidModelMode));
        switch (parsed) {
            case 1:
                sidModelMode = SidModelMode::Mos6581;
                break;
            case 2:
                sidModelMode = SidModelMode::Mos8580;
                break;
            case 0:
            default:
                sidModelMode = SidModelMode::Auto;
                break;
        }
        return;
    }

    if (optionName == "crsid.clock_mode") {
        const int parsed = parseIntString(optionValue, static_cast<int>(clockMode));
        switch (parsed) {
            case 1:
                clockMode = ClockMode::Pal;
                break;
            case 2:
                clockMode = ClockMode::Ntsc;
                break;
            case 0:
            default:
                clockMode = ClockMode::Auto;
                break;
        }
        return;
    }

    if (optionName == "crsid.quality_mode") {
        const int parsed = parseIntString(optionValue, static_cast<int>(qualityMode));
        switch (parsed) {
            case 0:
                qualityMode = QualityMode::Light;
                break;
            case 2:
                qualityMode = QualityMode::Sinc;
                break;
            case 1:
            default:
                qualityMode = QualityMode::High;
                break;
        }
        return;
    }

    if (optionName == "crsid.filter_6581_preset") {
        const int parsed = parseIntString(optionValue, static_cast<int>(filter6581Preset));
        switch (parsed) {
            case 1:
                filter6581Preset = Filter6581Preset::R4ar;
                break;
            case 2:
                filter6581Preset = Filter6581Preset::R3;
                break;
            case 3:
                filter6581Preset = Filter6581Preset::R2;
                break;
            case 0:
            default:
                filter6581Preset = Filter6581Preset::Stock;
                break;
        }
        return;
    }

    if (optionName == "crsid.stereo") {
        const bool enabled = parseBoolString(optionValue, true);
        cRSID.Stereo = enabled ? CRSID_CHANNELMODE_STEREO : CRSID_CHANNELMODE_MONO;
        return;
    }

    if (optionName == "crsid.unknown_duration_seconds") {
        const int parsed = parseIntString(optionValue, static_cast<int>(fallbackDurationSeconds));
        fallbackDurationSeconds = static_cast<double>(std::clamp(parsed, 1, 86400));
        const size_t count = std::min(declaredSubtuneDurationsSeconds.size(), subtuneDurationsSeconds.size());
        for (size_t i = 0; i < count; ++i) {
            subtuneDurationsSeconds[i] = declaredSubtuneDurationsSeconds[i] > 0.0
                    ? declaredSubtuneDurationsSeconds[i]
                    : fallbackDurationSeconds;
        }
        if (currentSubtuneIndex >= 0 && currentSubtuneIndex < static_cast<int>(subtuneDurationsSeconds.size())) {
            currentDurationSeconds = subtuneDurationsSeconds[static_cast<size_t>(currentSubtuneIndex)];
            durationReliable = declaredSubtuneDurationsSeconds[static_cast<size_t>(currentSubtuneIndex)] > 0.0;
        }
        return;
    }
}

int CRSIDDecoder::getOptionApplyPolicy(const char* name) const {
    if (!name) {
        return OPTION_APPLY_LIVE;
    }

    const std::string optionName(name);
    if (optionName == "crsid.clock_mode") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "crsid.sid_model_mode") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "crsid.quality_mode") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "crsid.filter_6581_preset") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "crsid.stereo") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    if (optionName == "crsid.unknown_duration_seconds") {
        return OPTION_APPLY_LIVE;
    }
    return OPTION_APPLY_LIVE;
}
