#include "AudioEngine.h"

#include "decoders/FFmpegDecoder.h"
#include "decoders/LibOpenMPTDecoder.h"
#include "decoders/VGMDecoder.h"
#include "decoders/GmeDecoder.h"
#include "decoders/LibSidPlayFpDecoder.h"
#include "decoders/LazyUsf2Decoder.h"
#include "decoders/Vio2sfDecoder.h"
#include "decoders/Sc68Decoder.h"
#include "decoders/AdPlugDecoder.h"
#include "decoders/HivelyTrackerDecoder.h"
#include "decoders/KlystrackDecoder.h"
#include "decoders/UadeDecoder.h"

bool AudioEngine::consumeNaturalEndEvent() {
    return naturalEndPending.exchange(false);
}

std::string AudioEngine::getTitle() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getTitle();
}

std::string AudioEngine::getArtist() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getArtist();
}

std::string AudioEngine::getComposer() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getComposer();
}

std::string AudioEngine::getGenre() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getGenre();
}

std::string AudioEngine::getAlbum() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getAlbum();
}

std::string AudioEngine::getYear() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getYear();
}

std::string AudioEngine::getDate() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getDate();
}

std::string AudioEngine::getCopyright() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getCopyright();
}

std::string AudioEngine::getComment() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getComment();
}

int AudioEngine::getSampleRate() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getSampleRate();
}

int AudioEngine::getDisplayChannelCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getDisplayChannelCount();
}

int AudioEngine::getChannelCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getChannelCount();
}

int AudioEngine::getBitDepth() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getBitDepth();
}

std::string AudioEngine::getBitDepthLabel() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "Unknown";
    }
    return decoder->getBitDepthLabel();
}

std::string AudioEngine::getCurrentDecoderName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getName();
}

int AudioEngine::getSubtuneCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getSubtuneCount();
}

int AudioEngine::getCurrentSubtuneIndex() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getCurrentSubtuneIndex();
}

bool AudioEngine::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return false;
    }
    return decoder->selectSubtune(index);
}

std::string AudioEngine::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getSubtuneTitle(index);
}

std::string AudioEngine::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return "";
    }
    return decoder->getSubtuneArtist(index);
}

double AudioEngine::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0.0;
    }
    return decoder->getSubtuneDurationSeconds(index);
}

int AudioEngine::getDecoderRenderSampleRateHz() const {
    return decoderRenderSampleRate;
}

int AudioEngine::getOutputStreamSampleRateHz() const {
    return streamSampleRate;
}

std::string AudioEngine::getOpenMptModuleTypeLong() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getModuleTypeLong() : "";
}

std::string AudioEngine::getOpenMptTracker() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getTracker() : "";
}

std::string AudioEngine::getOpenMptSongMessage() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getSongMessage() : "";
}

int AudioEngine::getOpenMptOrderCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getOrderCount() : 0;
}

int AudioEngine::getOpenMptPatternCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getPatternCount() : 0;
}

int AudioEngine::getOpenMptInstrumentCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getInstrumentCount() : 0;
}

int AudioEngine::getOpenMptSampleCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getSampleCount() : 0;
}

std::string AudioEngine::getOpenMptInstrumentNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getInstrumentNames() : "";
}

std::string AudioEngine::getOpenMptSampleNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getSampleNames() : "";
}

std::vector<float> AudioEngine::getOpenMptChannelVuLevels() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getCurrentChannelVuLevels() : std::vector<float>{};
}

std::vector<float> AudioEngine::getChannelScopeSamples(int samplesPerChannel) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getCurrentChannelScopeSamples(samplesPerChannel) : std::vector<float>{};
}

std::vector<int32_t> AudioEngine::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    auto* openMptDecoder = dynamic_cast<LibOpenMPTDecoder*>(decoder.get());
    return openMptDecoder ? openMptDecoder->getChannelScopeTextState(maxChannels) : std::vector<int32_t>{};
}

std::vector<std::string> AudioEngine::getDecoderToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    return decoder->getToggleChannelNames();
}

std::vector<uint8_t> AudioEngine::getDecoderToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    return decoder->getToggleChannelAvailability();
}

void AudioEngine::setDecoderToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return;
    decoder->setToggleChannelMuted(channelIndex, enabled);
}

bool AudioEngine::getDecoderToggleChannelMuted(int channelIndex) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getToggleChannelMuted(channelIndex);
}

void AudioEngine::clearDecoderToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return;
    decoder->clearToggleChannelMutes();
}

std::string AudioEngine::getVgmGameName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getGameName() : "";
}

std::string AudioEngine::getVgmSystemName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getSystemName() : "";
}

std::string AudioEngine::getVgmReleaseDate() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getReleaseDate() : "";
}

std::string AudioEngine::getVgmEncodedBy() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getEncodedBy() : "";
}

std::string AudioEngine::getVgmNotes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getNotes() : "";
}

std::string AudioEngine::getVgmFileVersion() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getFileVersion() : "";
}

int AudioEngine::getVgmDeviceCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getDeviceCount() : 0;
}

std::string AudioEngine::getVgmUsedChipList() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->getUsedChipList() : "";
}

bool AudioEngine::getVgmHasLoopPoint() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* vgmDecoder = dynamic_cast<VGMDecoder*>(decoder.get());
    return vgmDecoder ? vgmDecoder->hasLoopPoint() : false;
}

std::string AudioEngine::getFfmpegCodecName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    return ffmpegDecoder ? ffmpegDecoder->getCodecName() : "";
}

std::string AudioEngine::getFfmpegContainerName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    return ffmpegDecoder ? ffmpegDecoder->getContainerName() : "";
}

std::string AudioEngine::getFfmpegSampleFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    return ffmpegDecoder ? ffmpegDecoder->getSampleFormatName() : "";
}

std::string AudioEngine::getFfmpegChannelLayoutName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    return ffmpegDecoder ? ffmpegDecoder->getChannelLayoutName() : "";
}

std::string AudioEngine::getFfmpegEncoderName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* ffmpegDecoder = dynamic_cast<FFmpegDecoder*>(decoder.get());
    return ffmpegDecoder ? ffmpegDecoder->getEncoderName() : "";
}

std::string AudioEngine::getGmeSystemName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getSystemName() : "";
}

std::string AudioEngine::getGmeGameName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getGameName() : "";
}

std::string AudioEngine::getGmeCopyright() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getCopyright() : "";
}

std::string AudioEngine::getGmeComment() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getComment() : "";
}

std::string AudioEngine::getGmeDumper() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getDumper() : "";
}

int AudioEngine::getGmeTrackCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getTrackCountInfo() : 0;
}

int AudioEngine::getGmeVoiceCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getVoiceCountInfo() : 0;
}

bool AudioEngine::getGmeHasLoopPoint() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getHasLoopPointInfo() : false;
}

int AudioEngine::getGmeLoopStartMs() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getLoopStartMsInfo() : -1;
}

int AudioEngine::getGmeLoopLengthMs() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    auto* gmeDecoder = dynamic_cast<GmeDecoder*>(decoder.get());
    return gmeDecoder ? gmeDecoder->getLoopLengthMsInfo() : -1;
}

std::string AudioEngine::getLazyUsf2GameName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getGameName() : "";
}

std::string AudioEngine::getLazyUsf2Copyright() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getCopyright() : "";
}

std::string AudioEngine::getLazyUsf2Year() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getYear() : "";
}

std::string AudioEngine::getLazyUsf2UsfBy() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getUsfBy() : "";
}

std::string AudioEngine::getLazyUsf2LengthTag() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getLengthTag() : "";
}

std::string AudioEngine::getLazyUsf2FadeTag() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getFadeTag() : "";
}

bool AudioEngine::getLazyUsf2EnableCompare() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getEnableCompare() : false;
}

bool AudioEngine::getLazyUsf2EnableFifoFull() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* lazyUsf2Decoder = dynamic_cast<LazyUsf2Decoder*>(decoder.get());
    return lazyUsf2Decoder ? lazyUsf2Decoder->getEnableFifoFull() : false;
}

std::string AudioEngine::getVio2sfGameName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vio2sfDecoder = dynamic_cast<Vio2sfDecoder*>(decoder.get());
    return vio2sfDecoder ? vio2sfDecoder->getGameName() : "";
}

std::string AudioEngine::getVio2sfCopyright() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vio2sfDecoder = dynamic_cast<Vio2sfDecoder*>(decoder.get());
    return vio2sfDecoder ? vio2sfDecoder->getCopyright() : "";
}

std::string AudioEngine::getVio2sfYear() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vio2sfDecoder = dynamic_cast<Vio2sfDecoder*>(decoder.get());
    return vio2sfDecoder ? vio2sfDecoder->getYear() : "";
}

std::string AudioEngine::getVio2sfComment() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vio2sfDecoder = dynamic_cast<Vio2sfDecoder*>(decoder.get());
    return vio2sfDecoder ? vio2sfDecoder->getComment() : "";
}

std::string AudioEngine::getVio2sfLengthTag() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vio2sfDecoder = dynamic_cast<Vio2sfDecoder*>(decoder.get());
    return vio2sfDecoder ? vio2sfDecoder->getLengthTag() : "";
}

std::string AudioEngine::getVio2sfFadeTag() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* vio2sfDecoder = dynamic_cast<Vio2sfDecoder*>(decoder.get());
    return vio2sfDecoder ? vio2sfDecoder->getFadeTag() : "";
}

std::string AudioEngine::getSidFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidFormatName() : "";
}

std::string AudioEngine::getSidClockName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidClockName() : "";
}

std::string AudioEngine::getSidSpeedName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidSpeedName() : "";
}

std::string AudioEngine::getSidCompatibilityName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidCompatibilityName() : "";
}

std::string AudioEngine::getSidBackendName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidBackendName() : "";
}

int AudioEngine::getSidChipCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidChipCountInfo() : 0;
}

std::string AudioEngine::getSidModelSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidModelSummary() : "";
}

std::string AudioEngine::getSidCurrentModelSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidCurrentModelSummary() : "";
}

std::string AudioEngine::getSidBaseAddressSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidBaseAddressSummary() : "";
}

std::string AudioEngine::getSidCommentSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sidDecoder = dynamic_cast<LibSidPlayFpDecoder*>(decoder.get());
    return sidDecoder ? sidDecoder->getSidCommentSummary() : "";
}

std::string AudioEngine::getSc68FormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getFormatName() : "";
}

std::string AudioEngine::getSc68HardwareName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getHardwareName() : "";
}

std::string AudioEngine::getSc68PlatformName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getPlatformName() : "";
}

std::string AudioEngine::getSc68ReplayName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getReplayName() : "";
}

int AudioEngine::getSc68ReplayRateHz() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getReplayRateHz() : 0;
}

int AudioEngine::getSc68TrackCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getTrackCountInfo() : 0;
}

std::string AudioEngine::getSc68AlbumName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getAlbumName() : "";
}

std::string AudioEngine::getSc68Year() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getYearTag() : "";
}

std::string AudioEngine::getSc68Ripper() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getRipperTag() : "";
}

std::string AudioEngine::getSc68Converter() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getConverterTag() : "";
}

std::string AudioEngine::getSc68Timer() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getTimerTag() : "";
}

bool AudioEngine::getSc68CanAsid() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getCanAsid() : false;
}

bool AudioEngine::getSc68UsesYm() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getUsesYm() : false;
}

bool AudioEngine::getSc68UsesSte() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getUsesSte() : false;
}

bool AudioEngine::getSc68UsesAmiga() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* sc68Decoder = dynamic_cast<Sc68Decoder*>(decoder.get());
    return sc68Decoder ? sc68Decoder->getUsesAmiga() : false;
}

std::string AudioEngine::getAdplugDescription() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* adplugDecoder = dynamic_cast<AdPlugDecoder*>(decoder.get());
    return adplugDecoder ? adplugDecoder->getDescription() : "";
}

int AudioEngine::getAdplugPatternCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* adplugDecoder = dynamic_cast<AdPlugDecoder*>(decoder.get());
    return adplugDecoder ? adplugDecoder->getPatternCountInfo() : 0;
}

int AudioEngine::getAdplugCurrentPattern() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* adplugDecoder = dynamic_cast<AdPlugDecoder*>(decoder.get());
    return adplugDecoder ? adplugDecoder->getCurrentPatternInfo() : 0;
}

int AudioEngine::getAdplugOrderCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* adplugDecoder = dynamic_cast<AdPlugDecoder*>(decoder.get());
    return adplugDecoder ? adplugDecoder->getOrderCountInfo() : 0;
}

int AudioEngine::getAdplugCurrentOrder() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* adplugDecoder = dynamic_cast<AdPlugDecoder*>(decoder.get());
    return adplugDecoder ? adplugDecoder->getCurrentOrderInfo() : 0;
}

int AudioEngine::getAdplugCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* adplugDecoder = dynamic_cast<AdPlugDecoder*>(decoder.get());
    return adplugDecoder ? adplugDecoder->getCurrentRowInfo() : 0;
}

int AudioEngine::getAdplugCurrentSpeed() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* adplugDecoder = dynamic_cast<AdPlugDecoder*>(decoder.get());
    return adplugDecoder ? adplugDecoder->getCurrentSpeedInfo() : 0;
}

int AudioEngine::getAdplugInstrumentCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* adplugDecoder = dynamic_cast<AdPlugDecoder*>(decoder.get());
    return adplugDecoder ? adplugDecoder->getInstrumentCountInfo() : 0;
}

std::string AudioEngine::getAdplugInstrumentNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* adplugDecoder = dynamic_cast<AdPlugDecoder*>(decoder.get());
    return adplugDecoder ? adplugDecoder->getInstrumentNamesInfo() : "";
}

std::string AudioEngine::getHivelyFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getFormatNameInfo() : "";
}

int AudioEngine::getHivelyFormatVersion() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getFormatVersionInfo() : 0;
}

int AudioEngine::getHivelyPositionCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getPositionCountInfo() : 0;
}

int AudioEngine::getHivelyRestartPosition() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getRestartPositionInfo() : -1;
}

int AudioEngine::getHivelyTrackLengthRows() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getTrackLengthRowsInfo() : 0;
}

int AudioEngine::getHivelyTrackCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getTrackCountInfo() : 0;
}

int AudioEngine::getHivelyInstrumentCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getInstrumentCountInfo() : 0;
}

int AudioEngine::getHivelySpeedMultiplier() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getSpeedMultiplierInfo() : 0;
}

int AudioEngine::getHivelyCurrentPosition() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getCurrentPositionInfo() : -1;
}

int AudioEngine::getHivelyCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getCurrentRowInfo() : -1;
}

int AudioEngine::getHivelyCurrentTempo() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getCurrentTempoInfo() : 0;
}

int AudioEngine::getHivelyMixGainPercent() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getMixGainPercentInfo() : 0;
}

std::string AudioEngine::getHivelyInstrumentNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* hivelyDecoder = dynamic_cast<HivelyTrackerDecoder*>(decoder.get());
    return hivelyDecoder ? hivelyDecoder->getInstrumentNamesInfo() : "";
}

std::string AudioEngine::getKlystrackFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* klystrackDecoder = dynamic_cast<KlystrackDecoder*>(decoder.get());
    return klystrackDecoder ? klystrackDecoder->getFormatNameInfo() : "";
}

int AudioEngine::getKlystrackTrackCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* klystrackDecoder = dynamic_cast<KlystrackDecoder*>(decoder.get());
    return klystrackDecoder ? klystrackDecoder->getTrackCountInfo() : 0;
}

int AudioEngine::getKlystrackInstrumentCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* klystrackDecoder = dynamic_cast<KlystrackDecoder*>(decoder.get());
    return klystrackDecoder ? klystrackDecoder->getInstrumentCountInfo() : 0;
}

int AudioEngine::getKlystrackSongLengthRows() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* klystrackDecoder = dynamic_cast<KlystrackDecoder*>(decoder.get());
    return klystrackDecoder ? klystrackDecoder->getSongLengthRowsInfo() : 0;
}

int AudioEngine::getKlystrackCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    auto* klystrackDecoder = dynamic_cast<KlystrackDecoder*>(decoder.get());
    return klystrackDecoder ? klystrackDecoder->getCurrentRowInfo() : -1;
}

std::string AudioEngine::getKlystrackInstrumentNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* klystrackDecoder = dynamic_cast<KlystrackDecoder*>(decoder.get());
    return klystrackDecoder ? klystrackDecoder->getInstrumentNamesInfo() : "";
}

std::string AudioEngine::getUadeFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getFormatName() : "";
}

std::string AudioEngine::getUadeModuleName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getModuleName() : "";
}

std::string AudioEngine::getUadePlayerName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getPlayerName() : "";
}

std::string AudioEngine::getUadeModuleFileName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getModuleFileName() : "";
}

std::string AudioEngine::getUadePlayerFileName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getPlayerFileName() : "";
}

std::string AudioEngine::getUadeModuleMd5() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getModuleMd5() : "";
}

std::string AudioEngine::getUadeDetectionExtension() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getDetectionExtension() : "";
}

std::string AudioEngine::getUadeDetectedFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getDetectedFormatName() : "";
}

std::string AudioEngine::getUadeDetectedFormatVersion() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getDetectedFormatVersion() : "";
}

bool AudioEngine::getUadeDetectionByContent() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getDetectionByContent() : false;
}

bool AudioEngine::getUadeDetectionIsCustom() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getDetectionIsCustom() : false;
}

int AudioEngine::getUadeSubsongMin() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getSubsongMin() : 0;
}

int AudioEngine::getUadeSubsongMax() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getSubsongMax() : 0;
}

int AudioEngine::getUadeSubsongDefault() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getSubsongDefault() : 0;
}

int AudioEngine::getUadeCurrentSubsong() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getCurrentSubsong() : 0;
}

int64_t AudioEngine::getUadeModuleBytes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getModuleBytes() : 0;
}

int64_t AudioEngine::getUadeSongBytes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getSongBytes() : 0;
}

int64_t AudioEngine::getUadeSubsongBytes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    auto* uadeDecoder = dynamic_cast<UadeDecoder*>(decoder.get());
    return uadeDecoder ? uadeDecoder->getSubsongBytes() : 0;
}
