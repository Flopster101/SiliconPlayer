#include "LibOpenMPTDecoder.h"
#include <android/log.h>
#include <fstream>
#include <algorithm>
#include <cctype>
#include <unordered_set>
#include <sstream>

#define LOG_TAG "LibOpenMPTDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
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

LibOpenMPTDecoder::LibOpenMPTDecoder() {
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
        module = std::make_unique<openmpt::module>(fileBuffer);
        isAmigaModule = detectAmigaModule(path ? path : "", module.get());
        isXmModule = detectXmModule(path ? path : "", module.get());
        applyRenderSettingsLocked();
        if (repeatMode == 2) {
            module->set_repeat_count(0);
            module->ctl_set_text("play.at_end", "continue");
        } else {
            module->set_repeat_count(0);
            module->ctl_set_text("play.at_end", "stop");
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
}

int LibOpenMPTDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return 0;

    // render audio
    size_t count = module->read_interleaved_stereo(renderSampleRate, numFrames, buffer);

    return (int)count;
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
    return sampleRate;
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
        module->set_repeat_count(0);
        module->ctl_set_text("play.at_end", "continue");
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
    return title;
}

std::string LibOpenMPTDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
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
