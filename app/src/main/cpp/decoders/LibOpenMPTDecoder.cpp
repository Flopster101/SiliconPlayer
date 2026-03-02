#include "LibOpenMPTDecoder.h"
#include <android/log.h>
#include <fstream>
#include <algorithm>
#include <cctype>
#include <cmath>
#include <chrono>
#include <cstddef>
#include <concepts>
#include <unordered_set>
#include <sstream>

#define LOG_TAG "LibOpenMPTDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
template <typename T>
concept HasChannelScopeApi = requires(T moduleRef, int channel, float *scope, int count) {
    moduleRef.get_current_channel_scope(channel, scope, count);
};

template <typename T>
concept HasChannelTextStateApi = requires(T moduleRef, int channel, int32_t *state, int count) {
    moduleRef.get_current_channel_text_state(channel, state, count);
};

std::string getFirstNonEmptyMetadata(openmpt::module* module, const std::initializer_list<const char*>& keys) {
    if (!module) {
        return "";
    }
    for (const char* key : keys) {
        std::string value = module->get_metadata(key);
        if (!value.empty()) {
            return value;
        }
    }
    return "";
}

bool parseBoolString(const std::string& value, bool fallback) {
    std::string normalized;
    normalized.reserve(value.size());
    for (char c : value) {
        normalized.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
    }
    if (normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on") {
        return true;
    }
    if (normalized == "0" || normalized == "false" || normalized == "no" || normalized == "off") {
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

std::string toLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

bool detectAmigaModule(const std::string& path, openmpt::module* module) {
    static const std::unordered_set<std::string> amigaExtensions = {
            "mod", "m15", "stk", "st26", "ice", "dbm", "med", "okt", "sfx"
    };
    const std::filesystem::path fsPath(path);
    const std::string ext = toLowerAscii(fsPath.extension().string());
    if (!ext.empty()) {
        const std::string extNoDot = ext[0] == '.' ? ext.substr(1) : ext;
        if (amigaExtensions.find(extNoDot) != amigaExtensions.end()) {
            return true;
        }
    }
    if (!module) return false;
    const std::string type = toLowerAscii(module->get_metadata("type"));
    const std::string typeLong = toLowerAscii(module->get_metadata("type_long"));
    return type.find("mod") != std::string::npos ||
           type.find("med") != std::string::npos ||
           type.find("okt") != std::string::npos ||
           typeLong.find("amiga") != std::string::npos;
}

bool detectXmModule(const std::string& path, openmpt::module* module) {
    const std::filesystem::path fsPath(path);
    const std::string ext = toLowerAscii(fsPath.extension().string());
    if (!ext.empty()) {
        const std::string extNoDot = ext[0] == '.' ? ext.substr(1) : ext;
        if (extNoDot == "xm") {
            return true;
        }
    }
    if (!module) return false;
    const std::string type = toLowerAscii(module->get_metadata("type"));
    const std::string typeLong = toLowerAscii(module->get_metadata("type_long"));
    return type == "xm" ||
           type.find("fasttracker") != std::string::npos ||
           typeLong.find("xm") != std::string::npos ||
           typeLong.find("fasttracker") != std::string::npos;
}

bool isAmigaStyleLeftChannel(int channel) {
    const int mod4 = channel & 3;
    return mod4 == 0 || mod4 == 3;
}

std::string joinNamedEntries(const std::vector<std::string>& names) {
    if (names.empty()) return "";
    std::ostringstream out;
    for (size_t i = 0; i < names.size(); ++i) {
        if (i > 0) out << '\n';
        const std::string& rawName = names[i];
        out << (i + 1) << ". " << rawName;
    }
    return out.str();
}
}

LibOpenMPTDecoder::LibOpenMPTDecoder()
    : channelScopeState(std::make_shared<ChannelScopeSharedState>()) {
}

LibOpenMPTDecoder::~LibOpenMPTDecoder() {
    close();
}

bool LibOpenMPTDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    close();

    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open file: %s", path);
        return false;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    if (size <= 0) {
        LOGE("File is empty: %s", path);
        return false;
    }

    try {
        fileBuffer.resize(size);
        if (!file.read(fileBuffer.data(), size)) {
             LOGE("Failed to read file: %s", path);
             return false;
        }

        // Create module from memory buffer
        module = std::make_unique<openmpt::module_ext>(fileBuffer);
        isAmigaModule = detectAmigaModule(path ? path : "", module.get());
        isXmModule = detectXmModule(path ? path : "", module.get());
        applyRenderSettingsLocked();
        if (repeatMode == 2) {
            module->set_repeat_count(-1);
            module->ctl_set_text("play.at_end", "stop");
        } else {
            module->set_repeat_count(0);
            module->ctl_set_text("play.at_end", "stop");
        }

        subtuneCount = 1;
        currentSubtuneIndex = 0;
        subtuneNames.clear();
        subtuneDurationsSeconds.clear();
        try {
            const int detectedSubtunes = static_cast<int>(module->get_num_subsongs());
            subtuneCount = std::max(1, detectedSubtunes);
            subtuneNames = module->get_subsong_names();
            if (static_cast<int>(subtuneNames.size()) < subtuneCount) {
                subtuneNames.resize(subtuneCount);
            }
            subtuneDurationsSeconds.assign(subtuneCount, 0.0);
            for (int i = 0; i < subtuneCount; ++i) {
                module->select_subsong(i);
                const double subtuneDuration = module->get_duration_seconds();
                subtuneDurationsSeconds[i] = subtuneDuration > 0.0 ? subtuneDuration : 0.0;
            }
            module->select_subsong(0);
            currentSubtuneIndex = 0;
        } catch (const openmpt::exception& e) {
            LOGE("Failed to initialize OpenMPT subsongs: %s", e.what());
            subtuneCount = 1;
            currentSubtuneIndex = 0;
            subtuneNames.assign(1, "");
            subtuneDurationsSeconds.assign(1, 0.0);
        } catch (...) {
            LOGE("Failed to initialize OpenMPT subsongs: unknown error");
            subtuneCount = 1;
            currentSubtuneIndex = 0;
            subtuneNames.assign(1, "");
            subtuneDurationsSeconds.assign(1, 0.0);
        }

        duration = module->get_duration_seconds();
        moduleChannels = static_cast<int>(module->get_num_channels());
        title = getFirstNonEmptyMetadata(module.get(), {"title", "songtitle"});
        artist = getFirstNonEmptyMetadata(module.get(), {"artist", "author", "composer"});
        moduleTypeLong = getFirstNonEmptyMetadata(module.get(), {"type_long", "type"});
        tracker = getFirstNonEmptyMetadata(module.get(), {"tracker"});
        songMessage = getFirstNonEmptyMetadata(module.get(), {"message_raw", "message"});
        instrumentNames = joinNamedEntries(module->get_instrument_names());
        sampleNames = joinNamedEntries(module->get_sample_names());
        rebuildToggleChannelsLocked();
        applyToggleChannelMutesLocked();
        LOGD("Opened module: %s, duration: %.2f", path, duration);
        return true;
    } catch (const openmpt::exception& e) {
        LOGE("OpenMPT exception: %s", e.what());
        return false;
    } catch (...) {
        LOGE("Unknown exception while opening module");
        return false;
    }
}

void LibOpenMPTDecoder::close() {
    // lock should be held by caller or strictly sequential
    module.reset();
    fileBuffer.clear();
    duration = 0.0;
    moduleChannels = 0;
    isAmigaModule = false;
    isXmModule = false;
    title.clear();
    artist.clear();
    moduleTypeLong.clear();
    tracker.clear();
    songMessage.clear();
    instrumentNames.clear();
    sampleNames.clear();
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    subtuneNames.clear();
    subtuneDurationsSeconds.clear();
    channelScopeSourceSerial = 0;
    channelScopeLastReadFrames = 0;
    channelScopeLastReadNs = 0;
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    if (channelScopeState) {
        channelScopeState->clear();
    }
}

void LibOpenMPTDecoder::captureChannelScopeSnapshotLocked() {
    if constexpr (!HasChannelScopeApi<openmpt::module>) {
        return;
    }
    if (!channelScopeState) return;

    const int totalChannels = std::clamp(
            moduleChannels > 0 ? moduleChannels : static_cast<int>(module->get_num_channels()),
            0, 64);
    if (totalChannels <= 0) return;

    const int maxSamples = ChannelScopeSharedState::kMaxSamples;
    const size_t flatSize = static_cast<size_t>(totalChannels) * maxSamples;

    thread_local std::vector<float> scratchRaw;
    thread_local std::vector<float> scratchVu;
    scratchRaw.resize(flatSize);
    scratchVu.resize(static_cast<size_t>(totalChannels));

    for (int ch = 0; ch < totalChannels; ++ch) {
        float* dest = scratchRaw.data() + static_cast<size_t>(ch) * maxSamples;
        const int written = static_cast<int>(
                module->get_current_channel_scope(ch, dest, maxSamples));
        if (written < maxSamples && written > 0) {
            std::fill(dest + written, dest + maxSamples, 0.0f);
        } else if (written <= 0) {
            std::fill(dest, dest + maxSamples, 0.0f);
        }
        scratchVu[static_cast<size_t>(ch)] =
                std::clamp(module->get_current_channel_vu_mono(ch), 0.0f, 1.0f);
    }

    {
        std::lock_guard<std::mutex> scopeLock(channelScopeState->mutex);
        if (channelScopeState->snapshotRaw.size() != flatSize) {
            channelScopeState->snapshotRaw.resize(flatSize);
        }
        std::copy(scratchRaw.begin(), scratchRaw.begin() + static_cast<ptrdiff_t>(flatSize),
                  channelScopeState->snapshotRaw.begin());
        if (channelScopeState->snapshotVu.size() != static_cast<size_t>(totalChannels)) {
            channelScopeState->snapshotVu.resize(static_cast<size_t>(totalChannels));
        }
        std::copy(scratchVu.begin(), scratchVu.begin() + totalChannels,
                  channelScopeState->snapshotVu.begin());
        channelScopeState->snapshotChannels = totalChannels;
        channelScopeState->snapshotSerial = channelScopeSourceSerial;
    }
}

int LibOpenMPTDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return 0;

    size_t count = module->read_interleaved_stereo(renderSampleRate, numFrames, buffer);
    if (count > 0) {
        channelScopeSourceSerial++;
        channelScopeLastReadFrames = static_cast<int>(count);
        channelScopeLastReadNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        captureChannelScopeSnapshotLocked();
    }

    return static_cast<int>(count);
}

void LibOpenMPTDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return;
    module->set_position_seconds(seconds);
}

double LibOpenMPTDecoder::getDuration() {
    return duration;
}

int LibOpenMPTDecoder::getSampleRate() {
    return renderSampleRate;
}

void LibOpenMPTDecoder::setOutputSampleRate(int sampleRateHz) {
    if (sampleRateHz <= 0) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    renderSampleRate = sampleRateHz;
}

void LibOpenMPTDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = mode;
    if (!module) return;
    if (repeatMode == 2) {
        module->set_repeat_count(-1);
        module->ctl_set_text("play.at_end", "stop");
    } else {
        module->set_repeat_count(0);
        module->ctl_set_text("play.at_end", "stop");
    }
}

int LibOpenMPTDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

double LibOpenMPTDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return -1.0;
    const int order = static_cast<int>(module->get_current_order());
    const int row = static_cast<int>(module->get_current_row());
    if (order >= 0 && row >= 0) {
        const double timelinePosition = module->get_time_at_position(order, row);
        if (timelinePosition >= 0.0) {
            return timelinePosition;
        }
    }
    return module->get_position_seconds();
}

int LibOpenMPTDecoder::getBitDepth() {
    return bitDepth;
}

std::string LibOpenMPTDecoder::getBitDepthLabel() {
    return "Mixed";
}

int LibOpenMPTDecoder::getChannelCount() {
    return channels;
}

int LibOpenMPTDecoder::getDisplayChannelCount() {
    return moduleChannels > 0 ? moduleChannels : channels;
}

std::string LibOpenMPTDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (subtuneCount > 1 &&
        currentSubtuneIndex >= 0 &&
        currentSubtuneIndex < static_cast<int>(subtuneNames.size()) &&
        !subtuneNames[currentSubtuneIndex].empty()) {
        return subtuneNames[currentSubtuneIndex];
    }
    return title;
}

std::string LibOpenMPTDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

int LibOpenMPTDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return subtuneCount;
}

int LibOpenMPTDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return currentSubtuneIndex;
}

bool LibOpenMPTDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module || index < 0 || index >= subtuneCount) {
        return false;
    }
    if (index == currentSubtuneIndex) {
        return true;
    }
    try {
        module->select_subsong(index);
        currentSubtuneIndex = index;
        const double subtuneDuration = module->get_duration_seconds();
        duration = subtuneDuration > 0.0 ? subtuneDuration : 0.0;
        if (index >= 0 && index < static_cast<int>(subtuneDurationsSeconds.size())) {
            subtuneDurationsSeconds[index] = duration;
        }
        applyToggleChannelMutesLocked();
        return true;
    } catch (const openmpt::exception& e) {
        LOGE("OpenMPT select_subsong failed: %s", e.what());
        return false;
    } catch (...) {
        LOGE("OpenMPT select_subsong failed: unknown error");
        return false;
    }
}

std::string LibOpenMPTDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) return "";
    if (index < static_cast<int>(subtuneNames.size())) {
        return subtuneNames[index];
    }
    return "";
}

std::string LibOpenMPTDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) return "";
    return artist;
}

double LibOpenMPTDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) return 0.0;
    if (index < static_cast<int>(subtuneDurationsSeconds.size())) {
        return subtuneDurationsSeconds[index];
    }
    return 0.0;
}

std::string LibOpenMPTDecoder::getModuleTypeLong() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return moduleTypeLong;
}

std::string LibOpenMPTDecoder::getTracker() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return tracker;
}

std::string LibOpenMPTDecoder::getSongMessage() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return songMessage;
}

int LibOpenMPTDecoder::getOrderCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return 0;
    return static_cast<int>(module->get_num_orders());
}

int LibOpenMPTDecoder::getPatternCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return 0;
    return static_cast<int>(module->get_num_patterns());
}

int LibOpenMPTDecoder::getInstrumentCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return 0;
    return static_cast<int>(module->get_num_instruments());
}

int LibOpenMPTDecoder::getSampleCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return 0;
    return static_cast<int>(module->get_num_samples());
}

int LibOpenMPTDecoder::getModuleChannelCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (moduleChannels > 0) {
        return moduleChannels;
    }
    if (!module) {
        return 0;
    }
    return static_cast<int>(module->get_num_channels());
}

std::vector<float> LibOpenMPTDecoder::getCurrentChannelVuLevels() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) {
        return {};
    }
    const int totalChannels = std::clamp(
            moduleChannels > 0 ? moduleChannels : static_cast<int>(module->get_num_channels()),
            0,
            64
    );
    if (totalChannels <= 0) {
        return {};
    }
    std::vector<float> levels;
    levels.reserve(static_cast<size_t>(totalChannels));
    for (int channel = 0; channel < totalChannels; ++channel) {
        const float vu = module->get_current_channel_vu_mono(channel);
        levels.push_back(std::clamp(vu, 0.0f, 1.0f));
    }
    return levels;
}

std::vector<float> LibOpenMPTDecoder::getCurrentChannelScopeSamples(int samplesPerChannel) {
    if (!channelScopeState) return {};
    return channelScopeState->getProcessedSamples(samplesPerChannel);
}

void ChannelScopeSharedState::clear() {
    std::lock_guard<std::mutex> lock(mutex);
    snapshotRaw.clear();
    snapshotVu.clear();
    snapshotChannels = 0;
    snapshotSerial = 0;
    prevSnapshot.clear();
    interpolatedPrev.clear();
    interpolatedCurr.clear();
    frozenFrameCount.clear();
    lastChannels = 0;
    lastSamplesPerChannel = 0;
    consumedSerial = 0;
    interpolationInitialized = false;
    cachedOutput.clear();
    outputSerial = 0;
}

std::vector<float> ChannelScopeSharedState::getProcessedSamples(int samplesPerChannel) {
    std::vector<float> localRaw;
    std::vector<float> localVu;
    int localChannels = 0;
    uint64_t localSerial = 0;
    {
        std::lock_guard<std::mutex> lock(mutex);
        if (snapshotChannels <= 0 || snapshotRaw.empty()) {
            return {};
        }
        localChannels = snapshotChannels;
        localSerial = snapshotSerial;
        localRaw = snapshotRaw;
        localVu = snapshotVu;
    }

    const int clampedSamples = std::clamp(samplesPerChannel, 16, kMaxSamples);
    const int totalChannels = localChannels;
    const size_t flattenedSize = static_cast<size_t>(totalChannels * clampedSamples);
    std::vector<float> flattened(flattenedSize, 0.0f);

    const bool scopeShapeChanged =
            lastChannels != totalChannels ||
            lastSamplesPerChannel != clampedSamples ||
            prevSnapshot.size() != flattenedSize ||
            interpolatedPrev.size() != flattenedSize ||
            interpolatedCurr.size() != flattenedSize ||
            frozenFrameCount.size() != static_cast<size_t>(totalChannels);
    if (scopeShapeChanged) {
        prevSnapshot.assign(flattenedSize, 0.0f);
        interpolatedPrev.assign(flattenedSize, 0.0f);
        interpolatedCurr.assign(flattenedSize, 0.0f);
        frozenFrameCount.assign(static_cast<size_t>(totalChannels), 0);
        lastChannels = totalChannels;
        lastSamplesPerChannel = clampedSamples;
        interpolationInitialized = false;
        consumedSerial = localSerial;
    }
    if (localSerial != consumedSerial || !interpolationInitialized) {
        std::vector<float> raw(flattenedSize, 0.0f);
        std::vector<float> processed(flattenedSize, 0.0f);
        for (int channel = 0; channel < totalChannels; ++channel) {
            const size_t channelOffset = static_cast<size_t>(channel) * clampedSamples;
            float* rawDestination = raw.data() + channelOffset;
            const size_t snapshotOffset = static_cast<size_t>(channel) * kMaxSamples;
            const int copyLen = std::min(clampedSamples, kMaxSamples);
            std::copy(localRaw.data() + snapshotOffset,
                      localRaw.data() + snapshotOffset + copyLen,
                      rawDestination);
            if (copyLen < clampedSamples) {
                std::fill(rawDestination + copyLen, rawDestination + clampedSamples, 0.0f);
            }

            const float* previous = prevSnapshot.data() + channelOffset;
            bool sameAsPrevious = true;
            float peak = 0.0f;
            float prevPeak = 0.0f;
            float deltaSum = 0.0f;
            float rmsAcc = 0.0f;
            for (int i = 0; i < clampedSamples; ++i) {
                const float value = rawDestination[i];
                const float prevValue = previous[i];
                if (prevValue != value) sameAsPrevious = false;
                deltaSum += std::abs(value - prevValue);
                rmsAcc += value * value;
                peak = std::max(peak, std::abs(value));
                prevPeak = std::max(prevPeak, std::abs(prevValue));
            }

            auto& frozen = frozenFrameCount[static_cast<size_t>(channel)];
            const float channelVu = (static_cast<size_t>(channel) < localVu.size())
                    ? localVu[static_cast<size_t>(channel)]
                    : 0.0f;
            const float meanDelta = deltaSum / static_cast<float>(clampedSamples);
            const float rms = std::sqrt(rmsAcc / static_cast<float>(clampedSamples));
            const bool frameNearlyFrozen = meanDelta < 0.0005f;
            const bool looksSilentNow = (channelVu < 0.00035f) && (rms < 0.0045f);
            const bool abruptTailFreeze =
                    looksSilentNow &&
                    (peak > 0.018f || prevPeak > 0.018f) &&
                    (sameAsPrevious || frameNearlyFrozen);
            const bool likelyFreshSignal =
                    (peak > 0.001f) &&
                    (!frameNearlyFrozen || channelVu > 0.03f);
            bool suppressStaleScope = false;
            if (abruptTailFreeze) {
                frozen = 3;
                suppressStaleScope = true;
            } else if (likelyFreshSignal) {
                frozen = 0;
            } else {
                if (frozen < 255) {
                    frozen++;
                }
                if (!suppressStaleScope &&
                    frameNearlyFrozen &&
                    frozen >= 6 &&
                    channelVu < 0.03f &&
                    (peak > 0.012f || prevPeak > 0.012f)) {
                    suppressStaleScope = true;
                }
                if (frozen >= 3) {
                    suppressStaleScope = true;
                }
            }

            float* processedDestination = processed.data() + channelOffset;
            if (suppressStaleScope) {
                std::fill(processedDestination, processedDestination + clampedSamples, 0.0f);
            } else {
                std::copy(rawDestination, rawDestination + clampedSamples, processedDestination);
            }
        }
        prevSnapshot = std::move(raw);
        if (!interpolationInitialized) {
            interpolatedPrev = processed;
            interpolatedCurr = processed;
            interpolationInitialized = true;
        } else {
            interpolatedPrev = interpolatedCurr;
            interpolatedCurr = processed;
        }
        consumedSerial = localSerial;
        for (int channel = 0; channel < totalChannels; ++channel) {
            const size_t channelOffset = static_cast<size_t>(channel) * static_cast<size_t>(clampedSamples);
            const float* curr = interpolatedCurr.data() + channelOffset;
            float* out = flattened.data() + channelOffset;
            std::copy(curr, curr + clampedSamples, out);
        }
        cachedOutput = flattened;
        outputSerial = consumedSerial;
        return flattened;
    }
    // No new audio data — return cached output.
    if (!cachedOutput.empty() && cachedOutput.size() == flattenedSize) {
        return cachedOutput;
    }
    return flattened;
}

std::vector<int32_t> LibOpenMPTDecoder::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) {
        return {};
    }
    if constexpr (!HasChannelTextStateApi<openmpt::module>) {
        return {};
    }
    constexpr int kStride = 8;
    constexpr int kFlagActive = 1 << 0;
    constexpr int kFlagAmigaLeft = 1 << 1;
    constexpr int kFlagAmigaRight = 1 << 2;
    const int totalChannels = std::clamp(
            moduleChannels > 0 ? moduleChannels : static_cast<int>(module->get_num_channels()),
            0,
            64
    );
    if (totalChannels <= 0) {
        return {};
    }
    const int requested = std::clamp(maxChannels, 1, 64);
    const int channels = std::min(totalChannels, requested);
    std::vector<int32_t> flat(static_cast<size_t>(channels * kStride), -1);
    for (int channel = 0; channel < channels; ++channel) {
        const size_t base = static_cast<size_t>(channel * kStride);
        int32_t nativeFields[6] = { -1, 0, 0, -1, -1, -1 };
        const int written = static_cast<int>(module->get_current_channel_text_state(channel, nativeFields, 6));
        const int note = (written >= 1) ? nativeFields[0] : -1;
        const int volume = (written >= 2) ? std::clamp(nativeFields[1], 0, 256) : 0;
        const int effectLetter = (written >= 3) ? nativeFields[2] : 0;
        const int effectParam = (written >= 4) ? nativeFields[3] : -1;
        const int instrument = (written >= 5) ? nativeFields[4] : -1;
        const int sample = (written >= 6) ? nativeFields[5] : -1;
        int flags = 0;
        if (volume > 0 || note > 0 || instrument > 0 || sample > 0) {
            flags |= kFlagActive;
        }
        if (isAmigaModule) {
            // ProTracker-style hard panning map per 4-channel group: L R R L.
            const int mod4 = channel & 3;
            if (mod4 == 0 || mod4 == 3) {
                flags |= kFlagAmigaLeft;
            } else {
                flags |= kFlagAmigaRight;
            }
        }
        flat[base + 0] = channel;
        flat[base + 1] = note;
        flat[base + 2] = volume;
        flat[base + 3] = effectLetter;
        flat[base + 4] = effectParam;
        flat[base + 5] = instrument;
        flat[base + 6] = sample;
        flat[base + 7] = flags;
    }
    return flat;
}

std::string LibOpenMPTDecoder::getInstrumentNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return instrumentNames;
}

std::string LibOpenMPTDecoder::getSampleNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sampleNames;
}

std::vector<std::string> LibOpenMPTDecoder::getSupportedExtensions() {
    return openmpt::get_supported_extensions();
}

std::vector<std::string> LibOpenMPTDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return toggleChannelNames;
}

void LibOpenMPTDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return;
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) return;
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyToggleChannelMutesLocked();
}

bool LibOpenMPTDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return false;
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) return false;
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void LibOpenMPTDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return;
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyToggleChannelMutesLocked();
}

void LibOpenMPTDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) return;
    std::lock_guard<std::mutex> lock(decodeMutex);

    const std::string optionName(name);
    const std::string optionValue(value);

    if (optionName == "openmpt.stereo_separation_percent") {
        stereoSeparationPercent = std::clamp(parseIntString(optionValue, stereoSeparationPercent), 0, 200);
    } else if (optionName == "openmpt.stereo_separation_amiga_percent") {
        stereoSeparationAmigaPercent = std::clamp(parseIntString(optionValue, stereoSeparationAmigaPercent), 0, 200);
    } else if (optionName == "openmpt.interpolation_filter_length") {
        interpolationFilterLength = std::max(0, parseIntString(optionValue, interpolationFilterLength));
    } else if (optionName == "openmpt.volume_ramping_strength") {
        volumeRampingStrength = std::clamp(parseIntString(optionValue, volumeRampingStrength), -1, 10);
    } else if (optionName == "openmpt.ft2_xm_volume_ramping") {
        ft2XmVolumeRamping = parseBoolString(optionValue, ft2XmVolumeRamping);
    } else if (optionName == "openmpt.master_gain_millibel") {
        masterGainMilliBel = parseIntString(optionValue, masterGainMilliBel);
    } else if (optionName == "openmpt.amiga_resampler_mode") {
        amigaResamplerMode = std::clamp(parseIntString(optionValue, amigaResamplerMode), 0, 3);
    } else if (optionName == "openmpt.amiga_resampler_apply_all_modules") {
        applyAmigaResamplerToAllModules = parseBoolString(optionValue, applyAmigaResamplerToAllModules);
    } else if (optionName == "openmpt.surround_enabled") {
        // Stored for forward compatibility. Actual surround rendering path will be added later.
        surroundEnabled = parseBoolString(optionValue, surroundEnabled);
    } else {
        return;
    }

    applyRenderSettingsLocked();
}

int LibOpenMPTDecoder::getOptionApplyPolicy(const char* name) const {
    if (!name) return OPTION_APPLY_LIVE;
    const std::string optionName(name);
    if (optionName == "openmpt.stereo_separation_percent" ||
        optionName == "openmpt.stereo_separation_amiga_percent" ||
        optionName == "openmpt.interpolation_filter_length" ||
        optionName == "openmpt.volume_ramping_strength" ||
        optionName == "openmpt.ft2_xm_volume_ramping" ||
        optionName == "openmpt.master_gain_millibel" ||
        optionName == "openmpt.amiga_resampler_mode" ||
        optionName == "openmpt.amiga_resampler_apply_all_modules" ||
        optionName == "openmpt.surround_enabled") {
        return OPTION_APPLY_LIVE;
    }
    return OPTION_APPLY_LIVE;
}

void LibOpenMPTDecoder::applyRenderSettingsLocked() {
    if (!module) return;
    try {
        const int effectiveVolumeRamping =
                (ft2XmVolumeRamping && isXmModule) ? 5 : volumeRampingStrength;
        module->set_render_param(
                openmpt::module::RENDER_STEREOSEPARATION_PERCENT,
                isAmigaModule ? stereoSeparationAmigaPercent : stereoSeparationPercent
        );
        module->set_render_param(
                openmpt::module::RENDER_INTERPOLATIONFILTER_LENGTH,
                interpolationFilterLength
        );
        module->set_render_param(
                openmpt::module::RENDER_VOLUMERAMPING_STRENGTH,
                effectiveVolumeRamping
        );
        module->set_render_param(
                openmpt::module::RENDER_MASTERGAIN_MILLIBEL,
                masterGainMilliBel
        );
        const bool shouldEnableAmigaResampler =
                amigaResamplerMode > 0 && (applyAmigaResamplerToAllModules || isAmigaModule);
        if (!shouldEnableAmigaResampler) {
            module->ctl_set_boolean("render.resampler.emulate_amiga", false);
        } else {
            module->ctl_set_boolean("render.resampler.emulate_amiga", true);
            switch (amigaResamplerMode) {
                case 1:
                    module->ctl_set_text("render.resampler.emulate_amiga_type", "unfiltered");
                    break;
                case 3:
                    module->ctl_set_text("render.resampler.emulate_amiga_type", "a1200");
                    break;
                case 2:
                default:
                    module->ctl_set_text("render.resampler.emulate_amiga_type", "a500");
                    break;
            }
        }
    } catch (const openmpt::exception& e) {
        LOGE("Failed to apply render settings: %s", e.what());
    } catch (...) {
        LOGE("Failed to apply render settings: unknown error");
    }
}

void LibOpenMPTDecoder::rebuildToggleChannelsLocked() {
    const int totalChannels = std::clamp(
            moduleChannels > 0 ? moduleChannels : (module ? static_cast<int>(module->get_num_channels()) : 0),
            0,
            64
    );
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    if (totalChannels <= 0) {
        return;
    }

    toggleChannelNames.reserve(static_cast<size_t>(totalChannels));
    toggleChannelMuted.assign(static_cast<size_t>(totalChannels), false);

    if (isAmigaModule) {
        int leftIndex = 0;
        int rightIndex = 0;
        for (int channel = 0; channel < totalChannels; ++channel) {
            if (isAmigaStyleLeftChannel(channel)) {
                leftIndex++;
                toggleChannelNames.push_back("Paula L" + std::to_string(leftIndex));
            } else {
                rightIndex++;
                toggleChannelNames.push_back("Paula R" + std::to_string(rightIndex));
            }
        }
        return;
    }

    for (int channel = 0; channel < totalChannels; ++channel) {
        toggleChannelNames.push_back("Ch " + std::to_string(channel + 1));
    }
}

void LibOpenMPTDecoder::applyToggleChannelMutesLocked() {
    if (!module) return;
    auto* interactive = static_cast<openmpt::ext::interactive*>(
            module->get_interface(openmpt::ext::interactive_id)
    );
    if (!interactive) {
        return;
    }
    const int totalChannels = std::min(
            static_cast<int>(toggleChannelMuted.size()),
            std::max(0, static_cast<int>(module->get_num_channels()))
    );
    for (int channel = 0; channel < totalChannels; ++channel) {
        interactive->set_channel_mute_status(
                channel,
                toggleChannelMuted[static_cast<size_t>(channel)]
        );
    }
}
