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
    info.defaultPriority = priority;
    info.priority = priority;
    info.enabled = true; // Enabled by default
    info.enabledExtensions = {}; // Empty means all extensions enabled

    decoders.push_back(info);

    sortDecodersByPriority();

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

    // Try to find an enabled decoder that supports this extension
    for (const auto& info : decoders) {
        // Skip disabled decoders
        if (!info.enabled) {
            continue;
        }

        // Check if extension is in the enabled list
        bool extensionEnabled = false;

        // If enabledExtensions is empty, all extensions are enabled
        if (info.enabledExtensions.empty()) {
            // Check if extension is in supportedExtensions
            for (const auto& ext : info.supportedExtensions) {
                std::string supportedExt = ext;
                std::transform(supportedExt.begin(), supportedExt.end(), supportedExt.begin(), ::tolower);
                if (supportedExt == extension) {
                    extensionEnabled = true;
                    break;
                }
            }
        } else {
            // Check if extension is in enabledExtensions
            for (const auto& ext : info.enabledExtensions) {
                std::string enabledExt = ext;
                std::transform(enabledExt.begin(), enabledExt.end(), enabledExt.begin(), ::tolower);
                if (enabledExt == extension) {
                    extensionEnabled = true;
                    break;
                }
            }
        }

        if (extensionEnabled) {
            LOGD("Found matching decoder: %s (priority %d)", info.name.c_str(), info.priority);
            auto decoder = info.factory();
            if (decoder) {
                return decoder;
            }
        }
    }

    LOGE("No enabled decoder found for extension: %s", extension.c_str());
    return nullptr;
}

std::unique_ptr<AudioDecoder> DecoderRegistry::createDecoderByName(const std::string& name) {
    for (const auto& info : decoders) {
        if (info.name == name) {
            return info.factory();
        }
    }
    return nullptr;
}

std::vector<std::string> DecoderRegistry::getSupportedExtensions() {
    std::vector<std::string> allExtensions;
    for (const auto& info : decoders) {
        // Skip disabled decoders
        if (!info.enabled) {
            continue;
        }

        // If enabledExtensions is empty, use all supportedExtensions
        if (info.enabledExtensions.empty()) {
            allExtensions.insert(allExtensions.end(), info.supportedExtensions.begin(), info.supportedExtensions.end());
        } else {
            // Use only enabled extensions
            allExtensions.insert(allExtensions.end(), info.enabledExtensions.begin(), info.enabledExtensions.end());
        }
    }
    // De-duplicate
    std::sort(allExtensions.begin(), allExtensions.end());
    allExtensions.erase(std::unique(allExtensions.begin(), allExtensions.end()), allExtensions.end());
    return allExtensions;
}

DecoderInfo* DecoderRegistry::findDecoderInfo(const std::string& name) {
    for (auto& info : decoders) {
        if (info.name == name) {
            return &info;
        }
    }
    return nullptr;
}

void DecoderRegistry::sortDecodersByPriority() {
    std::sort(decoders.begin(), decoders.end(), [](const DecoderInfo& a, const DecoderInfo& b) {
        return a.priority < b.priority;
    });
}

void DecoderRegistry::setDecoderEnabled(const std::string& name, bool enabled) {
    DecoderInfo* info = findDecoderInfo(name);
    if (info) {
        info->enabled = enabled;
        LOGD("Decoder %s %s", name.c_str(), enabled ? "enabled" : "disabled");
    }
}

bool DecoderRegistry::isDecoderEnabled(const std::string& name) {
    DecoderInfo* info = findDecoderInfo(name);
    return info ? info->enabled : false;
}

void DecoderRegistry::setDecoderPriority(const std::string& name, int priority) {
    DecoderInfo* info = findDecoderInfo(name);
    if (info) {
        info->priority = priority;
        sortDecodersByPriority();
        LOGD("Decoder %s priority set to %d", name.c_str(), priority);
    }
}

int DecoderRegistry::getDecoderPriority(const std::string& name) {
    DecoderInfo* info = findDecoderInfo(name);
    return info ? info->priority : 0;
}

int DecoderRegistry::getDecoderDefaultPriority(const std::string& name) {
    DecoderInfo* info = findDecoderInfo(name);
    return info ? info->defaultPriority : 0;
}

void DecoderRegistry::setDecoderEnabledExtensions(const std::string& name, const std::vector<std::string>& extensions) {
    DecoderInfo* info = findDecoderInfo(name);
    if (info) {
        info->enabledExtensions = extensions;
        LOGD("Decoder %s enabled extensions updated (%zu extensions)", name.c_str(), extensions.size());
    }
}

std::vector<std::string> DecoderRegistry::getDecoderEnabledExtensions(const std::string& name) {
    DecoderInfo* info = findDecoderInfo(name);
    if (info) {
        // If empty, return all supported extensions (means all are enabled)
        if (info->enabledExtensions.empty()) {
            return info->supportedExtensions;
        }
        return info->enabledExtensions;
    }
    return {};
}

std::vector<std::string> DecoderRegistry::getDecoderSupportedExtensions(const std::string& name) {
    DecoderInfo* info = findDecoderInfo(name);
    return info ? info->supportedExtensions : std::vector<std::string>{};
}

std::vector<std::string> DecoderRegistry::getRegisteredDecoderNames() {
    std::vector<std::string> names;
    for (const auto& info : decoders) {
        names.push_back(info.name);
    }
    return names;
}
