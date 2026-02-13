#include "LibOpenMPTDecoder.h"
#include <android/log.h>
#include <fstream>
#include <algorithm>

#define LOG_TAG "LibOpenMPTDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

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

        duration = module->get_duration_seconds();
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
}

int LibOpenMPTDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!module) return 0;

    // render audio
    size_t count = module->read_interleaved_stereo(sampleRate, numFrames, buffer);

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

int LibOpenMPTDecoder::getChannelCount() {
    return channels;
}

std::vector<std::string> LibOpenMPTDecoder::getSupportedExtensions() {
    // Common tracker formats
    return {
        "mod", "s3m", "xm", "it", "mptm", "stm", "nst", "m15", "wow", "ult", "669",
        "mtm", "med", "far", "mdl", "ams", "dsm", "amf", "okta", "dmf", "ptm", "dbm",
        "mt2", "psm", "j2b"
    };
}
