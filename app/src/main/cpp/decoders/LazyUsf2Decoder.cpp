#include "LazyUsf2Decoder.h"

#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <limits>

extern "C" {
#include <lazyusf2/usf/usf.h>
}

#define LOG_TAG "LazyUsf2Decoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr int kMinSampleRate = 8000;
constexpr int kMaxSampleRate = 192000;
constexpr int kRenderChunkFrames = 2048;
constexpr unsigned long kInvalidPsfTime = 0xC0CAC01A;
constexpr double kFallbackDurationSeconds = 180.0;
}

LazyUsf2Decoder::LazyUsf2Decoder() {
    emulatorState.resize(usf_get_state_size(), 0);
    state = emulatorState.data();
}

LazyUsf2Decoder::~LazyUsf2Decoder() {
    close();
}

bool LazyUsf2Decoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternal();

    if (!path || path[0] == '\0') {
        return false;
    }

    sourcePath = path;
    usf_clear(state);
    enableCompare = false;
    enableFifoFull = false;
    useHleAudio = true;
    durationSeconds = 0.0;
    durationReliable = false;
    renderedFrames = 0;
    needsShutdown = false;
    title.clear();
    artist.clear();
    composer.clear();
    genre = "USF";
    gameName.clear();
    copyrightText.clear();
    year.clear();
    usfBy.clear();
    lengthTag.clear();
    fadeTag.clear();

    if (!loadPsfTree(sourcePath)) {
        closeInternal();
        return false;
    }

    usf_set_compare(state, enableCompare ? 1 : 0);
    usf_set_fifo_full(state, enableFifoFull ? 1 : 0);
    usf_set_hle_audio(state, useHleAudio ? 1 : 0);

    needsShutdown = true;
    int32_t nativeRate = 0;
    const char* warmupErr = usf_render(state, nullptr, 0, &nativeRate);
    if (warmupErr != nullptr) {
        LOGE("usf_render warmup failed: %s", warmupErr);
        closeInternal();
        return false;
    }

    if (title.empty()) {
        title = std::filesystem::path(sourcePath).stem().string();
    }
    if (artist.empty()) {
        artist = "Unknown Artist";
    }
    if (composer.empty()) {
        composer = artist;
    }
    if (genre.empty()) {
        genre = "USF";
    }

    isOpen = true;
    return true;
}

void LazyUsf2Decoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternal();
}

void LazyUsf2Decoder::closeInternal() {
    if (needsShutdown) {
        usf_shutdown(state);
    }
    isOpen = false;
    needsShutdown = false;
    renderedFrames = 0;
    durationSeconds = 0.0;
    durationReliable = false;
    enableCompare = false;
    enableFifoFull = false;
    useHleAudio = true;
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    gameName.clear();
    copyrightText.clear();
    year.clear();
    usfBy.clear();
    lengthTag.clear();
    fadeTag.clear();
    sourcePath.clear();
}

int LazyUsf2Decoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !buffer || numFrames <= 0) {
        return 0;
    }

    if (durationSeconds > 0.0) {
        const double playbackSeconds = static_cast<double>(renderedFrames) / outputSampleRate;
        if (playbackSeconds >= durationSeconds) {
            return 0;
        }
    }

    int framesToRender = numFrames;
    if (durationSeconds > 0.0) {
        const int64_t durationFrames = static_cast<int64_t>(std::llround(durationSeconds * outputSampleRate));
        const int64_t remaining = durationFrames - renderedFrames;
        if (remaining <= 0) {
            return 0;
        }
        framesToRender = static_cast<int>(std::min<int64_t>(framesToRender, remaining));
    }

    std::vector<int16_t> pcm(static_cast<size_t>(framesToRender) * channels);
    const char* renderErr = usf_render_resampled(state, pcm.data(), static_cast<size_t>(framesToRender), outputSampleRate);
    if (renderErr != nullptr) {
        LOGE("usf_render_resampled failed: %s", renderErr);
        return 0;
    }

    const size_t sampleCount = static_cast<size_t>(framesToRender) * channels;
    for (size_t i = 0; i < sampleCount; ++i) {
        buffer[i] = static_cast<float>(pcm[i]) / 32768.0f;
    }

    renderedFrames += framesToRender;
    return framesToRender;
}

void LazyUsf2Decoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    seekInternalLocked(seconds);
}

void LazyUsf2Decoder::seekInternalLocked(double seconds) {
    if (!isOpen) return;

    const double clamped = std::max(0.0, seconds);
    usf_restart(state);
    usf_set_compare(state, enableCompare ? 1 : 0);
    usf_set_fifo_full(state, enableFifoFull ? 1 : 0);
    usf_set_hle_audio(state, useHleAudio ? 1 : 0);
    renderedFrames = 0;

    if (clamped <= 0.0) {
        return;
    }

    int64_t samplesToSkip = static_cast<int64_t>(std::llround(clamped * outputSampleRate));
    std::vector<int16_t> discard(static_cast<size_t>(kRenderChunkFrames) * channels);
    while (samplesToSkip > 0) {
        const int chunk = static_cast<int>(std::min<int64_t>(kRenderChunkFrames, samplesToSkip));
        const char* renderErr = usf_render_resampled(state, discard.data(), static_cast<size_t>(chunk), outputSampleRate);
        if (renderErr != nullptr) {
            LOGE("seek discard render failed: %s", renderErr);
            break;
        }
        samplesToSkip -= chunk;
        renderedFrames += chunk;
    }
}

double LazyUsf2Decoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationSeconds;
}

int LazyUsf2Decoder::getSampleRate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return outputSampleRate;
}

int LazyUsf2Decoder::getBitDepth() {
    return bitDepth;
}

std::string LazyUsf2Decoder::getBitDepthLabel() {
    return "16-bit PCM";
}

int LazyUsf2Decoder::getChannelCount() {
    return channels;
}

std::string LazyUsf2Decoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string LazyUsf2Decoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string LazyUsf2Decoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string LazyUsf2Decoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string LazyUsf2Decoder::getGameName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return gameName;
}

std::string LazyUsf2Decoder::getCopyright() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return copyrightText;
}

std::string LazyUsf2Decoder::getYear() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return year;
}

std::string LazyUsf2Decoder::getUsfBy() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return usfBy;
}

std::string LazyUsf2Decoder::getLengthTag() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return lengthTag;
}

std::string LazyUsf2Decoder::getFadeTag() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return fadeTag;
}

bool LazyUsf2Decoder::getEnableCompare() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return enableCompare;
}

bool LazyUsf2Decoder::getEnableFifoFull() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return enableFifoFull;
}

void LazyUsf2Decoder::setOutputSampleRate(int sampleRate) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    outputSampleRate = std::clamp(sampleRate, kMinSampleRate, kMaxSampleRate);
}

void LazyUsf2Decoder::setOption(const char* name, const char* value) {
    if (!name || !value) {
        return;
    }
    std::lock_guard<std::mutex> lock(decodeMutex);
    const std::string key = toLowerAscii(trimAscii(name));
    const std::string rawValue = trimAscii(value);
    if (key == "lazyusf2.use_hle_audio") {
        useHleAudio = parseBoolTag(rawValue);
    }
}

int LazyUsf2Decoder::getOptionApplyPolicy(const char* name) const {
    if (!name) {
        return OPTION_APPLY_LIVE;
    }
    std::string key(name);
    std::transform(key.begin(), key.end(), key.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    if (key == "lazyusf2.use_hle_audio") {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    return OPTION_APPLY_LIVE;
}

void LazyUsf2Decoder::setRepeatMode(int mode) {
    repeatMode.store(mode);
}

int LazyUsf2Decoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK;
}

int LazyUsf2Decoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int caps = PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
               PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE;
    if (durationReliable) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

double LazyUsf2Decoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (outputSampleRate <= 0) return 0.0;
    return static_cast<double>(renderedFrames) / outputSampleRate;
}

std::vector<std::string> LazyUsf2Decoder::getSupportedExtensions() {
    return {"usf", "miniusf"};
}

bool LazyUsf2Decoder::loadPsfTree(const std::string& path) {
    std::unordered_set<std::string> recursionStack;
    std::unordered_set<std::string> loadedPaths;
    return loadPsfRecursive(path, recursionStack, loadedPaths, true);
}

bool LazyUsf2Decoder::loadPsfRecursive(
        const std::string& path,
        std::unordered_set<std::string>& recursionStack,
        std::unordered_set<std::string>& loadedPaths,
        bool metadataAllowed
) {
    const std::string normalized = normalizePath(path);
    if (normalized.empty()) {
        return false;
    }
    if (recursionStack.find(normalized) != recursionStack.end()) {
        LOGE("USF _lib recursion detected: %s", normalized.c_str());
        return false;
    }
    if (loadedPaths.find(normalized) != loadedPaths.end()) {
        return true;
    }

    ParsedPsf parsed;
    if (!parsePsfFile(normalized, parsed)) {
        return false;
    }

    recursionStack.insert(normalized);

    auto findTag = [&](const char* key) -> std::string {
        const std::string lowered = toLowerAscii(key);
        auto it = parsed.tags.find(lowered);
        if (it == parsed.tags.end()) {
            return "";
        }
        return it->second;
    };

    const std::string baseLib = findTag("_lib");
    if (!baseLib.empty()) {
        const std::string resolved = resolveRelativePath(normalized, baseLib);
        if (!loadPsfRecursive(resolved, recursionStack, loadedPaths, false)) {
            recursionStack.erase(normalized);
            return false;
        }
    }

    if (!parsed.reservedSection.empty()) {
        const int uploadResult = usf_upload_section(state, parsed.reservedSection.data(), parsed.reservedSection.size());
        if (uploadResult != 0) {
            LOGE("usf_upload_section failed for %s", normalized.c_str());
            recursionStack.erase(normalized);
            return false;
        }
    }

    std::vector<std::pair<int, std::string>> numberedLibs;
    numberedLibs.reserve(8);
    for (const auto& [key, value] : parsed.tags) {
        if (key.rfind("_lib", 0) != 0 || key == "_lib") {
            continue;
        }
        const std::string suffix = key.substr(4);
        if (suffix.empty()) {
            continue;
        }
        char* end = nullptr;
        const long index = std::strtol(suffix.c_str(), &end, 10);
        if (end == suffix.c_str() || (end && *end != '\0') || index <= 1) {
            continue;
        }
        if (value.empty()) {
            continue;
        }
        numberedLibs.emplace_back(static_cast<int>(index), value);
    }
    std::sort(numberedLibs.begin(), numberedLibs.end(), [](const auto& lhs, const auto& rhs) {
        return lhs.first < rhs.first;
    });
    for (const auto& [index, libPath] : numberedLibs) {
        (void) index;
        const std::string resolved = resolveRelativePath(normalized, libPath);
        if (!loadPsfRecursive(resolved, recursionStack, loadedPaths, false)) {
            recursionStack.erase(normalized);
            return false;
        }
    }

    if (metadataAllowed) {
        applyMetadataFromTags(parsed.tags);
    }
    applyCoreTags(parsed.tags);

    recursionStack.erase(normalized);
    loadedPaths.insert(normalized);
    return true;
}

bool LazyUsf2Decoder::parsePsfFile(const std::string& path, ParsedPsf& parsed) const {
    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) {
        LOGE("Failed to open USF file: %s", path.c_str());
        return false;
    }

    file.seekg(0, std::ios::end);
    const std::streamoff length = file.tellg();
    if (length < 16) {
        LOGE("USF file too small: %s", path.c_str());
        return false;
    }
    file.seekg(0, std::ios::beg);

    std::vector<uint8_t> bytes(static_cast<size_t>(length));
    file.read(reinterpret_cast<char*>(bytes.data()), length);
    if (!file.good() && !file.eof()) {
        LOGE("USF read failed: %s", path.c_str());
        return false;
    }

    if (bytes[0] != 'P' || bytes[1] != 'S' || bytes[2] != 'F' || bytes[3] != 0x21) {
        LOGE("Invalid PSF/USF header: %s", path.c_str());
        return false;
    }

    const uint32_t reservedSize = readLe32(bytes.data() + 4);
    const uint32_t compressedExeSize = readLe32(bytes.data() + 8);
    const size_t reservedOffset = 16u + static_cast<size_t>(compressedExeSize);
    if (reservedOffset > bytes.size()) {
        LOGE("Invalid USF section layout: %s", path.c_str());
        return false;
    }
    if (compressedExeSize > 0) {
        LOGE("USF executable section not supported: %s", path.c_str());
        return false;
    }
    if (reservedOffset + static_cast<size_t>(reservedSize) > bytes.size()) {
        LOGE("Invalid USF reserved section size: %s", path.c_str());
        return false;
    }

    parsed.reservedSection.assign(
            bytes.begin() + static_cast<std::ptrdiff_t>(reservedOffset),
            bytes.begin() + static_cast<std::ptrdiff_t>(reservedOffset + reservedSize)
    );

    const size_t tagOffset = reservedOffset + static_cast<size_t>(reservedSize);
    if (tagOffset + 5 <= bytes.size() && std::memcmp(bytes.data() + tagOffset, "[TAG]", 5) == 0) {
        std::string tagText(
                reinterpret_cast<const char*>(bytes.data() + tagOffset + 5),
                bytes.size() - (tagOffset + 5)
        );
        size_t lineStart = 0;
        while (lineStart < tagText.size()) {
            size_t lineEnd = tagText.find('\n', lineStart);
            if (lineEnd == std::string::npos) {
                lineEnd = tagText.size();
            }
            std::string line = trimAscii(tagText.substr(lineStart, lineEnd - lineStart));
            if (!line.empty()) {
                const size_t equals = line.find('=');
                if (equals != std::string::npos) {
                    const std::string key = toLowerAscii(trimAscii(line.substr(0, equals)));
                    const std::string value = trimAscii(line.substr(equals + 1));
                    if (!key.empty()) {
                        parsed.tags[key] = value;
                    }
                }
            }
            lineStart = lineEnd + 1;
        }
    }

    return true;
}

bool LazyUsf2Decoder::applyMetadataFromTags(const std::unordered_map<std::string, std::string>& tags) {
    auto getTag = [&](const char* key) -> std::string {
        auto it = tags.find(toLowerAscii(key));
        return it != tags.end() ? it->second : "";
    };

    title = getTag("title");
    artist = getTag("artist");
    if (artist.empty()) {
        artist = getTag("game");
    }
    composer = getTag("composer");
    if (composer.empty()) {
        composer = artist;
    }
    gameName = getTag("game");
    copyrightText = getTag("copyright");
    year = getTag("year");
    usfBy = getTag("usfby");
    genre = getTag("genre");
    if (genre.empty()) {
        genre = "USF";
    }

    lengthTag = getTag("length");
    fadeTag = getTag("fade");
    bool lengthOk = false;
    bool fadeOk = false;
    const unsigned long lengthMs = parsePsfTimeMs(lengthTag, lengthOk);
    const unsigned long fadeMs = parsePsfTimeMs(fadeTag, fadeOk);
    if (lengthOk) {
        const unsigned long totalMs = lengthMs + (fadeOk ? fadeMs : 0u);
        durationSeconds = static_cast<double>(totalMs) / 1000.0;
        durationReliable = durationSeconds > 0.0;
    } else {
        durationSeconds = kFallbackDurationSeconds;
        durationReliable = false;
    }
    return true;
}

void LazyUsf2Decoder::applyCoreTags(const std::unordered_map<std::string, std::string>& tags) {
    auto getTag = [&](const char* key) -> std::string {
        auto it = tags.find(toLowerAscii(key));
        return it != tags.end() ? it->second : "";
    };
    if (parseBoolTag(getTag("_enablecompare"))) {
        enableCompare = true;
    }
    if (parseBoolTag(getTag("_enablefifofull"))) {
        enableFifoFull = true;
    }
}

std::string LazyUsf2Decoder::toLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

std::string LazyUsf2Decoder::trimAscii(std::string value) {
    auto isTrim = [](unsigned char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    };
    while (!value.empty() && isTrim(static_cast<unsigned char>(value.front()))) {
        value.erase(value.begin());
    }
    while (!value.empty() && isTrim(static_cast<unsigned char>(value.back()))) {
        value.pop_back();
    }
    return value;
}

std::string LazyUsf2Decoder::normalizePath(const std::string& value) {
    if (value.empty()) {
        return "";
    }
    std::error_code ec;
    std::filesystem::path p(value);
    const auto weak = std::filesystem::weakly_canonical(p, ec);
    if (!ec) {
        return weak.string();
    }
    return p.lexically_normal().string();
}

std::string LazyUsf2Decoder::resolveRelativePath(const std::string& baseFilePath, const std::string& relative) {
    std::filesystem::path libPath(relative);
    if (libPath.is_absolute()) {
        return normalizePath(libPath.string());
    }
    const std::filesystem::path base(baseFilePath);
    return normalizePath((base.parent_path() / libPath).string());
}

bool LazyUsf2Decoder::parseBoolTag(const std::string& value) {
    if (value.empty()) {
        return false;
    }
    const std::string lowered = toLowerAscii(trimAscii(value));
    return lowered != "0" && lowered != "false" && lowered != "no" && lowered != "off";
}

unsigned long LazyUsf2Decoder::parsePsfTimeMs(const std::string& input, bool& ok) {
    ok = false;
    if (input.empty()) {
        return 0;
    }
    unsigned long value = 0;
    unsigned long multiplier = 1000;
    const char* ptr = input.c_str();
    unsigned long colonCount = 0;

    while (*ptr && ((*ptr >= '0' && *ptr <= '9') || *ptr == ':')) {
        colonCount += (*ptr == ':') ? 1u : 0u;
        ++ptr;
    }
    if (colonCount > 2) return 0;
    if (*ptr && *ptr != '.' && *ptr != ',') return 0;
    if (*ptr) ++ptr;
    while (*ptr && *ptr >= '0' && *ptr <= '9') ++ptr;
    if (*ptr) return 0;

    ptr = std::strrchr(input.c_str(), ':');
    if (!ptr) {
        ptr = input.c_str();
    }

    for (;;) {
        char* end = nullptr;
        if (ptr != input.c_str()) ++ptr;
        if (multiplier == 1000) {
            const double temp = std::strtod(ptr, &end);
            if (temp >= 60.0) return 0;
            value = static_cast<unsigned long>(temp * 1000.0);
        } else {
            const unsigned long temp = std::strtoul(ptr, &end, 10);
            if (temp >= 60 && multiplier < 3600000) return 0;
            value += temp * multiplier;
        }
        if (ptr == input.c_str()) break;
        ptr -= 2;
        while (ptr > input.c_str() && *ptr != ':') --ptr;
        multiplier *= 60;
    }

    if (value == kInvalidPsfTime) {
        return 0;
    }
    ok = true;
    return value;
}

uint32_t LazyUsf2Decoder::readLe32(const uint8_t* data) {
    return static_cast<uint32_t>(data[0]) |
           (static_cast<uint32_t>(data[1]) << 8u) |
           (static_cast<uint32_t>(data[2]) << 16u) |
           (static_cast<uint32_t>(data[3]) << 24u);
}
