#ifndef SILICONPLAYER_DECODERREGISTRY_H
#define SILICONPLAYER_DECODERREGISTRY_H

#include <vector>
#include <string>
#include <functional>
#include <memory>
#include "AudioDecoder.h"

// Factory function type
using DecoderFactory = std::function<std::unique_ptr<AudioDecoder>()>;

struct DecoderInfo {
    std::string name;
    std::vector<std::string> supportedExtensions;
    DecoderFactory factory;
    int priority; // Higher priority decoders are tried first for matching extensions
    bool enabled; // Whether this decoder is enabled
    std::vector<std::string> enabledExtensions; // Subset of supportedExtensions that are enabled (empty = all enabled)
};

class DecoderRegistry {
public:
    static DecoderRegistry& getInstance();

    void registerDecoder(const std::string& name, const std::vector<std::string>& extensions, DecoderFactory factory, int priority = 0);

    std::unique_ptr<AudioDecoder> createDecoder(const char* path);
    std::unique_ptr<AudioDecoder> createDecoderByName(const std::string& name);

    // List supported extensions (only from enabled decoders with enabled extensions)
    std::vector<std::string> getSupportedExtensions();

    // Plugin management
    void setDecoderEnabled(const std::string& name, bool enabled);
    bool isDecoderEnabled(const std::string& name);
    void setDecoderPriority(const std::string& name, int priority);
    int getDecoderPriority(const std::string& name);
    void setDecoderEnabledExtensions(const std::string& name, const std::vector<std::string>& extensions);
    std::vector<std::string> getDecoderEnabledExtensions(const std::string& name);
    std::vector<std::string> getDecoderSupportedExtensions(const std::string& name);
    std::vector<std::string> getRegisteredDecoderNames();

private:
    DecoderRegistry() = default;
    std::vector<DecoderInfo> decoders;

    DecoderInfo* findDecoderInfo(const std::string& name);
    void sortDecodersByPriority();
};

#endif //SILICONPLAYER_DECODERREGISTRY_H
