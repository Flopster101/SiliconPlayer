#include "DecoderRegistry.h"
#include <iostream>
#include <algorithm>
#include <filesystem>
#include <android/log.h>
#include <cctype>
#include <unordered_set>

#define LOG_TAG "DecoderRegistry"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
std::string toLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

std::vector<std::string> buildExtensionCandidates(const std::string& filePath) {
    std::string baseName = std::filesystem::path(filePath).filename().string();
    if (baseName.empty()) {
        baseName = filePath;
    }

    std::vector<std::string> rawCandidates;
    const std::size_t firstDot = baseName.find('.');
    const std::size_t lastDot = baseName.rfind('.');

    if (lastDot != std::string::npos && lastDot > 0 && (lastDot + 1) < baseName.size()) {
        rawCandidates.push_back(baseName.substr(lastDot + 1)); // trailing extension
    }

    if (lastDot != std::string::npos && lastDot > 0) {
        const std::size_t secondLastDot = baseName.rfind('.', lastDot - 1);
        if (secondLastDot != std::string::npos && secondLastDot < lastDot) {
            rawCandidates.push_back(baseName.substr(secondLastDot + 1)); // compound extension
        }
    }

    if (firstDot != std::string::npos && firstDot > 0) {
        rawCandidates.push_back(baseName.substr(0, firstDot)); // prefix extension
    }

    std::vector<std::string> candidates;
    std::unordered_set<std::string> seen;
    for (std::string candidate : rawCandidates) {
        candidate = toLowerAscii(std::move(candidate));
        if (candidate.empty()) continue;
        if (seen.insert(candidate).second) {
            candidates.push_back(std::move(candidate));
        }
    }
    return candidates;
}

bool decoderSupportsExtension(const DecoderInfo& info, const std::string& extension) {
    if (info.enabledExtensions.empty()) {
        for (const auto& ext : info.supportedExtensions) {
            if (toLowerAscii(ext) == extension) return true;
        }
        return false;
    }
    for (const auto& ext : info.enabledExtensions) {
        if (toLowerAscii(ext) == extension) return true;
    }
    return false;
}
}

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
    const std::vector<std::string> extensionCandidates = buildExtensionCandidates(filePath);
    if (extensionCandidates.empty()) {
        LOGE("No extension candidates resolved for file: %s", filePath.c_str());
        return nullptr;
    }

    LOGD("Looking for decoder for extension candidates: first=%s count=%zu",
         extensionCandidates.front().c_str(),
         extensionCandidates.size());

    // Try to find an enabled decoder that supports this extension
    for (const auto& extension : extensionCandidates) {
        for (const auto& info : decoders) {
            if (!info.enabled) {
                continue;
            }
            if (!decoderSupportsExtension(info, extension)) {
                continue;
            }
            LOGD("Found matching decoder: %s (priority %d) for extension: %s",
                 info.name.c_str(), info.priority, extension.c_str());
            auto decoder = info.factory();
            if (decoder) {
                return decoder;
            }
        }
    }

    LOGE("No enabled decoder found for any extension candidate (file=%s)", filePath.c_str());
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
