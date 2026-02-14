#include "LibOpenMPTDecoder.h"
#include <android/log.h>
#include <fstream>
#include <algorithm>
#include <cctype>

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
    title.clear();
    artist.clear();
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
    } else if (optionName == "openmpt.interpolation_filter_length") {
        interpolationFilterLength = std::max(0, parseIntString(optionValue, interpolationFilterLength));
    } else if (optionName == "openmpt.volume_ramping_strength") {
        volumeRampingStrength = std::clamp(parseIntString(optionValue, volumeRampingStrength), -1, 10);
    } else if (optionName == "openmpt.master_gain_millibel") {
        masterGainMilliBel = parseIntString(optionValue, masterGainMilliBel);
    } else if (optionName == "openmpt.amiga_resampler_mode") {
        amigaResamplerMode = std::clamp(parseIntString(optionValue, amigaResamplerMode), 0, 3);
    } else if (optionName == "openmpt.surround_enabled") {
        // Stored for forward compatibility. Actual surround rendering path will be added later.
        surroundEnabled = parseBoolString(optionValue, surroundEnabled);
    } else {
        return;
    }

    applyRenderSettingsLocked();
}

void LibOpenMPTDecoder::applyRenderSettingsLocked() {
    if (!module) return;
    try {
        module->set_render_param(
                openmpt::module::RENDER_STEREOSEPARATION_PERCENT,
                stereoSeparationPercent
        );
        module->set_render_param(
                openmpt::module::RENDER_INTERPOLATIONFILTER_LENGTH,
                interpolationFilterLength
        );
        module->set_render_param(
                openmpt::module::RENDER_VOLUMERAMPING_STRENGTH,
                volumeRampingStrength
        );
        module->set_render_param(
                openmpt::module::RENDER_MASTERGAIN_MILLIBEL,
                masterGainMilliBel
        );
        if (amigaResamplerMode <= 0) {
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
