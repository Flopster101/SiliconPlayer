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
};

class DecoderRegistry {
public:
    static DecoderRegistry& getInstance();

    void registerDecoder(const std::string& name, const std::vector<std::string>& extensions, DecoderFactory factory, int priority = 0);

    std::unique_ptr<AudioDecoder> createDecoder(const char* path);

    // List supported extensions
    std::vector<std::string> getSupportedExtensions();

private:
    DecoderRegistry() = default;
    std::vector<DecoderInfo> decoders;
};

#endif //SILICONPLAYER_DECODERREGISTRY_H
