#include "AudioEngine.h"
#include "ChannelScopeSharedState.h"

#include <algorithm>
#include <cmath>

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
    return decoder->getCoreStringInfo("moduleTypeLong");
}

std::string AudioEngine::getOpenMptTracker() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("tracker");
}

std::string AudioEngine::getOpenMptSongMessage() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("songMessage");
}

int AudioEngine::getOpenMptOrderCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("orderCount", 0);
}

int AudioEngine::getOpenMptPatternCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("patternCount", 0);
}

int AudioEngine::getOpenMptInstrumentCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

int AudioEngine::getOpenMptSampleCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("sampleCount", 0);
}

std::string AudioEngine::getOpenMptInstrumentNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getOpenMptSampleNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sampleNames");
}

std::vector<float> AudioEngine::getOpenMptChannelVuLevels() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    return decoder->getCoreFloatVectorInfo("channelVuLevels");
}

std::vector<float> AudioEngine::getChannelScopeSamples(int samplesPerChannel) {
    // Declare vis demand so the render worker bumps the snapshot serial
    // frequently and keeps `visualizationLastCallbackNs` fresh.
    markVisualizationRequested(kVisualizationFeatureChannelScope);
    std::shared_ptr<ChannelScopeSharedState> state;
    int decoderSampleRate = 0;
    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (!decoder) return {};
        state = decoder->getChannelScopeSharedState();
        decoderSampleRate = decoderRenderSampleRate > 0 ? decoderRenderSampleRate : decoder->getSampleRate();
    }
    if (!state) return {};
    const int outputSampleRate = streamSampleRate > 0 ? streamSampleRate : decoderSampleRate;
    int callbackFrames = 0;
    int64_t callbackNs = 0;
    {
        std::lock_guard<std::mutex> lock(visualizationMutex);
        callbackFrames = std::max(visualizationLastCallbackFrames, 0);
        callbackNs = visualizationLastCallbackNs;
    }

    // Decoder-output -> ear FIFO drained at outputSampleRate so the window
    // slides smoothly between callbacks across all backends.
    const int outputBackend = activeOutputBackend.load(std::memory_order_relaxed);
    int backendBufferedFrames = 0;
    switch (outputBackend) {
        case 1:
            backendBufferedFrames = std::max(aaudioBufferFrames, 0);
            break;
        case 2:
            backendBufferedFrames = std::max(openSlBufferFrames, 0);
            break;
        case 3:
            backendBufferedFrames = std::max(audioTrackBufferFrames, 0);
            break;
        default:
            break;
    }

    double presentationDelayOutputFrames = static_cast<double>(renderQueueFrames());
    if (lookaheadClipperMode.load(std::memory_order_relaxed) > 0) {
        const int lookaheadFrames = std::clamp((outputSampleRate * 5) / 1000, 32, 512);
        presentationDelayOutputFrames += static_cast<double>(lookaheadFrames);
    }
    if (outputSampleRate > 0 && callbackNs > 0) {
        const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        const int64_t elapsedNs = std::max<int64_t>(0, nowNs - callbackNs);
        const double elapsedFrames =
                (static_cast<double>(elapsedNs) * static_cast<double>(outputSampleRate)) / 1.0e9;
        const double bufferedAheadFrames =
                static_cast<double>(callbackFrames) +
                static_cast<double>(backendBufferedFrames);
        presentationDelayOutputFrames += std::max(0.0, bufferedAheadFrames - elapsedFrames);
    } else {
        // No callback timestamp yet (cold start). Fall back to the static
        // backend buffer size so we still report a sane delay on the first
        // poll.
        presentationDelayOutputFrames += static_cast<double>(backendBufferedFrames);
    }
    double presentationDelayDecoderFrames = presentationDelayOutputFrames;
    if (decoderSampleRate > 0 && outputSampleRate > 0 && decoderSampleRate != outputSampleRate) {
        presentationDelayDecoderFrames =
                presentationDelayOutputFrames *
                (static_cast<double>(decoderSampleRate) / static_cast<double>(outputSampleRate));
    }
    return state->getProcessedSamples(
            samplesPerChannel,
            static_cast<int>(std::ceil(std::max(0.0, presentationDelayDecoderFrames)))
    );
}

std::vector<int32_t> AudioEngine::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    return decoder->getChannelScopeTextState(maxChannels);
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
    return decoder->getCoreStringInfo("gameName");
}

std::string AudioEngine::getVgmSystemName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("systemName");
}

std::string AudioEngine::getVgmReleaseDate() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("releaseDate");
}

std::string AudioEngine::getVgmEncodedBy() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("encodedBy");
}

std::string AudioEngine::getVgmNotes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("notes");
}

std::string AudioEngine::getVgmFileVersion() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("fileVersion");
}

int AudioEngine::getVgmDeviceCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("deviceCount", 0);
}

std::string AudioEngine::getVgmUsedChipList() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("usedChipList");
}

bool AudioEngine::getVgmHasLoopPoint() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getCoreIntInfo("hasLoopPoint", 0) != 0;
}

std::string AudioEngine::getFfmpegCodecName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("codecName");
}

std::string AudioEngine::getFfmpegContainerName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("containerName");
}

std::string AudioEngine::getFfmpegSampleFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sampleFormatName");
}

std::string AudioEngine::getFfmpegChannelLayoutName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("channelLayoutName");
}

std::string AudioEngine::getFfmpegEncoderName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("encoderName");
}

std::string AudioEngine::getGmeSystemName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("systemName");
}

std::string AudioEngine::getGmeGameName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("gameName");
}

std::string AudioEngine::getGmeCopyright() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("copyright");
}

std::string AudioEngine::getGmeComment() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("comment");
}

std::string AudioEngine::getGmeDumper() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("dumper");
}

int AudioEngine::getGmeTrackCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("trackCount", 0);
}

int AudioEngine::getGmeVoiceCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("voiceCount", 0);
}

bool AudioEngine::getGmeHasLoopPoint() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getCoreIntInfo("hasLoopPoint", 0) != 0;
}

int AudioEngine::getGmeLoopStartMs() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("loopStartMs", -1);
}

int AudioEngine::getGmeLoopLengthMs() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("loopLengthMs", -1);
}

std::string AudioEngine::getLazyUsf2GameName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("gameName");
}

std::string AudioEngine::getLazyUsf2Copyright() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("copyright");
}

std::string AudioEngine::getLazyUsf2Year() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("year");
}

std::string AudioEngine::getLazyUsf2UsfBy() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("usfBy");
}

std::string AudioEngine::getLazyUsf2LengthTag() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("lengthTag");
}

std::string AudioEngine::getLazyUsf2FadeTag() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("fadeTag");
}

bool AudioEngine::getLazyUsf2EnableCompare() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getCoreIntInfo("enableCompare", 0) != 0;
}

bool AudioEngine::getLazyUsf2EnableFifoFull() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getCoreIntInfo("enableFifoFull", 0) != 0;
}

std::string AudioEngine::getVio2sfGameName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("gameName");
}

std::string AudioEngine::getVio2sfCopyright() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("copyright");
}

std::string AudioEngine::getVio2sfYear() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("year");
}

std::string AudioEngine::getVio2sfComment() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("comment");
}

std::string AudioEngine::getVio2sfLengthTag() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("lengthTag");
}

std::string AudioEngine::getVio2sfFadeTag() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("fadeTag");
}

std::string AudioEngine::getSidFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sidFormatName");
}

std::string AudioEngine::getSidClockName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sidClockName");
}

std::string AudioEngine::getSidSpeedName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sidSpeedName");
}

std::string AudioEngine::getSidCompatibilityName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sidCompatibilityName");
}

std::string AudioEngine::getSidBackendName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sidBackendName");
}

int AudioEngine::getSidChipCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("sidChipCount", 0);
}

std::string AudioEngine::getSidModelSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sidModelSummary");
}

std::string AudioEngine::getSidCurrentModelSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sidCurrentModelSummary");
}

std::string AudioEngine::getSidBaseAddressSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sidBaseAddressSummary");
}

std::string AudioEngine::getSidCommentSummary() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sidCommentSummary");
}

std::string AudioEngine::getSc68FormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

std::string AudioEngine::getSc68HardwareName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("hardwareName");
}

std::string AudioEngine::getSc68PlatformName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("platformName");
}

std::string AudioEngine::getSc68ReplayName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("replayName");
}

int AudioEngine::getSc68ReplayRateHz() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("replayRateHz", 0);
}

int AudioEngine::getSc68TrackCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("trackCount", 0);
}

std::string AudioEngine::getSc68AlbumName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("albumName");
}

std::string AudioEngine::getSc68Year() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("year");
}

std::string AudioEngine::getSc68Ripper() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("ripper");
}

std::string AudioEngine::getSc68Converter() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("converter");
}

std::string AudioEngine::getSc68Timer() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("timer");
}

bool AudioEngine::getSc68CanAsid() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getCoreIntInfo("canAsid", 0) != 0;
}

bool AudioEngine::getSc68UsesYm() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getCoreIntInfo("usesYm", 0) != 0;
}

bool AudioEngine::getSc68UsesSte() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getCoreIntInfo("usesSte", 0) != 0;
}

bool AudioEngine::getSc68UsesAmiga() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getCoreIntInfo("usesAmiga", 0) != 0;
}

std::string AudioEngine::getAdplugDescription() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("description");
}

int AudioEngine::getAdplugPatternCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("patternCount", 0);
}

int AudioEngine::getAdplugCurrentPattern() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("currentPattern", 0);
}

int AudioEngine::getAdplugOrderCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("orderCount", 0);
}

int AudioEngine::getAdplugCurrentOrder() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("currentOrder", 0);
}

int AudioEngine::getAdplugCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("currentRow", 0);
}

int AudioEngine::getAdplugCurrentSpeed() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("currentSpeed", 0);
}

int AudioEngine::getAdplugInstrumentCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

std::string AudioEngine::getAdplugInstrumentNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getHivelyFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

int AudioEngine::getHivelyFormatVersion() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("formatVersion", 0);
}

int AudioEngine::getHivelyPositionCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("positionCount", 0);
}

int AudioEngine::getHivelyRestartPosition() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("restartPosition", -1);
}

int AudioEngine::getHivelyTrackLengthRows() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("trackLengthRows", 0);
}

int AudioEngine::getHivelyTrackCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("trackCount", 0);
}

int AudioEngine::getHivelyInstrumentCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

int AudioEngine::getHivelySpeedMultiplier() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("speedMultiplier", 0);
}

int AudioEngine::getHivelyCurrentPosition() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentPosition", -1);
}

int AudioEngine::getHivelyCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentRow", -1);
}

int AudioEngine::getHivelyCurrentTempo() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("currentTempo", 0);
}

int AudioEngine::getHivelyMixGainPercent() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("mixGainPercent", 0);
}

std::string AudioEngine::getHivelyInstrumentNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getKlystrackFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

int AudioEngine::getKlystrackTrackCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("trackCount", 0);
}

int AudioEngine::getKlystrackInstrumentCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

int AudioEngine::getKlystrackSongLengthRows() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("songLengthRows", 0);
}

int AudioEngine::getKlystrackCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentRow", -1);
}

std::string AudioEngine::getKlystrackInstrumentNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getFurnaceInstrumentNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getFurnaceSampleNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("sampleNames");
}

std::string AudioEngine::getFurnaceFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

int AudioEngine::getFurnaceSongVersion() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("songVersion", 0);
}

std::string AudioEngine::getFurnaceSystemName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("systemName");
}

std::string AudioEngine::getFurnaceSystemNames() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("systemNames");
}

int AudioEngine::getFurnaceSystemCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("systemCount", 0);
}

int AudioEngine::getFurnaceSongChannelCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("songChannelCount", 0);
}

int AudioEngine::getFurnaceInstrumentCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

int AudioEngine::getFurnaceWavetableCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("wavetableCount", 0);
}

int AudioEngine::getFurnaceSampleCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("sampleCount", 0);
}

int AudioEngine::getFurnaceOrderCount() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("orderCount", 0);
}

int AudioEngine::getFurnaceRowsPerPattern() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("rowsPerPattern", 0);
}

int AudioEngine::getFurnaceCurrentOrder() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentOrder", -1);
}

int AudioEngine::getFurnaceCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentRow", -1);
}

int AudioEngine::getFurnaceCurrentTick() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentTick", -1);
}

int AudioEngine::getFurnaceCurrentSpeed() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("currentSpeed", 0);
}

int AudioEngine::getFurnaceGrooveLength() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("grooveLength", 0);
}

float AudioEngine::getFurnaceCurrentHz() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0.0f;
    return decoder->getCoreFloatInfo("currentHz", 0.0f);
}

std::string AudioEngine::getUadeFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

std::string AudioEngine::getUadeModuleName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("moduleName");
}

std::string AudioEngine::getUadePlayerName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("playerName");
}

std::string AudioEngine::getUadeModuleFileName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("moduleFileName");
}

std::string AudioEngine::getUadePlayerFileName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("playerFileName");
}

std::string AudioEngine::getUadeModuleMd5() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("moduleMd5");
}

std::string AudioEngine::getUadeDetectionExtension() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("detectionExtension");
}

std::string AudioEngine::getUadeDetectedFormatName() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("detectedFormatName");
}

std::string AudioEngine::getUadeDetectedFormatVersion() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return "";
    return decoder->getCoreStringInfo("detectedFormatVersion");
}

bool AudioEngine::getUadeDetectionByContent() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getCoreIntInfo("detectionByContent", 0) != 0;
}

bool AudioEngine::getUadeDetectionIsCustom() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return false;
    return decoder->getCoreIntInfo("detectionIsCustom", 0) != 0;
}

int AudioEngine::getUadeSubsongMin() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("subsongMin", 0);
}

int AudioEngine::getUadeSubsongMax() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("subsongMax", 0);
}

int AudioEngine::getUadeSubsongDefault() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("subsongDefault", 0);
}

int AudioEngine::getUadeCurrentSubsong() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreIntInfo("currentSubsong", 0);
}

int64_t AudioEngine::getUadeModuleBytes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreInt64Info("moduleBytes", 0);
}

int64_t AudioEngine::getUadeSongBytes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreInt64Info("songBytes", 0);
}

int64_t AudioEngine::getUadeSubsongBytes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreInt64Info("subsongBytes", 0);
}
