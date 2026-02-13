#include "DecoderRegistry.h"
#include <iostream>
#include <algorithm>
#include <filesystem>
#include <android/log.h>

#define LOG_TAG "DecoderRegistry"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

DecoderRegistry& DecoderRegistry::getInstance() {
    static DecoderRegistry instance;
    return instance;
}

void DecoderRegistry::registerDecoder(const std::string& name, const std::vector<std::string>& extensions, DecoderFactory factory, int priority) {
    DecoderInfo info;
    info.name = name;
    info.supportedExtensions = extensions;
    info.factory = factory;
    info.priority = priority;

    decoders.push_back(info);

    // Sort by priority (descending)
    std::sort(decoders.begin(), decoders.end(), [](const DecoderInfo& a, const DecoderInfo& b) {
        return a.priority > b.priority;
    });

    LOGD("Registered decoder: %s with priority %d", name.c_str(), priority);
}

std::unique_ptr<AudioDecoder> DecoderRegistry::createDecoder(const char* path) {
    if (!path) return nullptr;

    std::string filePath = path;
    std::string extension = std::filesystem::path(filePath).extension().string();

    // Normalize extension to lowercase and remove dot
    if (!extension.empty() && extension[0] == '.') {
        extension = extension.substr(1);
    }
    std::transform(extension.begin(), extension.end(), extension.begin(), ::tolower);

    LOGD("Looking for decoder for extension: %s", extension.c_str());

    // 1. Try to find a decoder that supports this extension
    for (const auto& info : decoders) {
        for (const auto& ext : info.supportedExtensions) {
            std::string supportedExt = ext;
             // Normalize just in case
            std::transform(supportedExt.begin(), supportedExt.end(), supportedExt.begin(), ::tolower);

            if (supportedExt == extension) {
                LOGD("Found matching decoder: %s", info.name.c_str());
                auto decoder = info.factory();
                if (decoder) {
                    return decoder;
                }
            }
        }
    }

    // 2. If no specific match, maybe try a fallback (or just try them all?)
    // FFmpeg usually handles almost everything, so if it's registered as a catch-all or low priority, we might want to try it.
    // For now, let's just loop through remaining decoders that claim to support "*" or similar?
    // Or simpler: FFmpeg should just list all its extensions, or we hardcode a fallback behavior.

    // Let's iterate again and try any decoder that didn't explicitly match but might handle it (e.g. catch-all)
    // For now we assume extension matching is required.

    // Fallback: Try the one with lowest priority (usually generic FFmpeg if registered as such)
    // Actually, "FFmpeg" usually claims everything.

    LOGE("No decoder found for extension: %s", extension.c_str());
    return nullptr;
}

std::vector<std::string> DecoderRegistry::getSupportedExtensions() {
    std::vector<std::string> allExtensions;
    for (const auto& info : decoders) {
        allExtensions.insert(allExtensions.end(), info.supportedExtensions.begin(), info.supportedExtensions.end());
    }
    // De-duplicate
    std::sort(allExtensions.begin(), allExtensions.end());
    allExtensions.erase(std::unique(allExtensions.begin(), allExtensions.end()), allExtensions.end());
    return allExtensions;
}
