#include "AudioEngine.h"
#include "decoders/DecoderRegistry.h"

int AudioEngine::resolveOutputSampleRateForCore(const std::string& coreName) const {
    auto it = coreOutputSampleRateHz.find(coreName);
    if (it != coreOutputSampleRateHz.end() && it->second > 0) {
        return it->second;
    }
    return (streamSampleRate > 0) ? streamSampleRate : 48000;
}

void AudioEngine::setCoreOutputSampleRate(const std::string& coreName, int sampleRateHz) {
    if (coreName.empty()) return;
    const int normalizedRate = sampleRateHz > 0 ? sampleRateHz : 0;

    std::lock_guard<std::mutex> lock(decoderMutex);
    coreOutputSampleRateHz[coreName] = normalizedRate;

    if (decoder && coreName == decoder->getName()) {
        const bool supportsLiveRateChange =
                (decoder->getPlaybackCapabilities() & AudioDecoder::PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE) != 0;
        if (supportsLiveRateChange) {
            const int desiredRate = resolveOutputSampleRateForCore(coreName);
            decoder->setOutputSampleRate(desiredRate);
            decoderRenderSampleRate = decoder->getSampleRate();
            resetResamplerStateLocked();
        }
    }
}

void AudioEngine::setCoreOption(
        const std::string& coreName,
        const std::string& optionName,
        const std::string& optionValue) {
    if (coreName.empty() || optionName.empty()) return;

    std::lock_guard<std::mutex> lock(decoderMutex);
    coreOptions[coreName][optionName] = optionValue;
    if (decoder && coreName == decoder->getName()) {
        decoder->setOption(optionName.c_str(), optionValue.c_str());
    }
}

int AudioEngine::getCoreOptionApplyPolicy(
        const std::string& coreName,
        const std::string& optionName) {
    if (coreName.empty() || optionName.empty()) {
        return AudioDecoder::OPTION_APPLY_LIVE;
    }

    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder && decoder->getName() == coreName) {
            return decoder->getOptionApplyPolicy(optionName.c_str());
        }
    }

    auto tempDecoder = DecoderRegistry::getInstance().createDecoderByName(coreName);
    if (tempDecoder) {
        return tempDecoder->getOptionApplyPolicy(optionName.c_str());
    }
    return AudioDecoder::OPTION_APPLY_LIVE;
}

int AudioEngine::getCoreCapabilities(const std::string& coreName) {
    if (coreName.empty()) return 0;

    // Check if we already have this decoder loaded to avoid re-creation.
    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder && decoder->getName() == coreName) {
            return decoder->getPlaybackCapabilities();
        }
    }

    // Create a temporary instance to query capabilities.
    auto tempDecoder = DecoderRegistry::getInstance().createDecoderByName(coreName);
    if (tempDecoder) {
        return tempDecoder->getPlaybackCapabilities();
    }
    return 0;
}

int AudioEngine::getCoreFixedSampleRateHz(const std::string& coreName) {
    if (coreName.empty()) return 0;

    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder && decoder->getName() == coreName) {
            return decoder->getFixedSampleRateHz();
        }
    }

    auto tempDecoder = DecoderRegistry::getInstance().createDecoderByName(coreName);
    if (tempDecoder) {
        return tempDecoder->getFixedSampleRateHz();
    }
    return 0;
}
