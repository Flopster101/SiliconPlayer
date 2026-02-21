#include "AudioEngine.h"

#include "decoders/DecoderRegistry.h"
#include "decoders/FFmpegDecoder.h"
#include "decoders/LibOpenMPTDecoder.h"
#include "decoders/VGMDecoder.h"
#include "decoders/GmeDecoder.h"
#include "decoders/LibSidPlayFpDecoder.h"
#include "decoders/LazyUsf2Decoder.h"
#include "decoders/Vio2sfDecoder.h"
#include "decoders/Sc68Decoder.h"
#include "decoders/AdPlugDecoder.h"

namespace {
    struct DecoderRegistration {
        DecoderRegistration() {
            DecoderRegistry::getInstance().registerDecoder("FFmpeg", FFmpegDecoder::getSupportedExtensions(), []() {
                return std::make_unique<FFmpegDecoder>();
            }, 0);

            DecoderRegistry::getInstance().registerDecoder("LibOpenMPT", LibOpenMPTDecoder::getSupportedExtensions(), []() {
                return std::make_unique<LibOpenMPTDecoder>();
            }, 10);

            DecoderRegistry::getInstance().registerDecoder("VGMPlay", VGMDecoder::getSupportedExtensions(), []() {
                return std::make_unique<VGMDecoder>();
            }, 5);

            DecoderRegistry::getInstance().registerDecoder("Game Music Emu", GmeDecoder::getSupportedExtensions(), []() {
                return std::make_unique<GmeDecoder>();
            }, 6);

            DecoderRegistry::getInstance().registerDecoder("LibSIDPlayFP", LibSidPlayFpDecoder::getSupportedExtensions(), []() {
                return std::make_unique<LibSidPlayFpDecoder>();
            }, 7);

            DecoderRegistry::getInstance().registerDecoder("LazyUSF2", LazyUsf2Decoder::getSupportedExtensions(), []() {
                return std::make_unique<LazyUsf2Decoder>();
            }, 8);

            DecoderRegistry::getInstance().registerDecoder("Vio2SF", Vio2sfDecoder::getSupportedExtensions(), []() {
                return std::make_unique<Vio2sfDecoder>();
            }, 9);

            DecoderRegistry::getInstance().registerDecoder("SC68", Sc68Decoder::getSupportedExtensions(), []() {
                return std::make_unique<Sc68Decoder>();
            }, 11);

            DecoderRegistry::getInstance().registerDecoder("AdPlug", AdPlugDecoder::getSupportedExtensions(), []() {
                return std::make_unique<AdPlugDecoder>();
            }, 12);
        }
    };

    static DecoderRegistration registration;
}

AudioEngine::AudioEngine() {
    updateRenderQueueTuning();
    seekWorkerThread = std::thread([this]() { seekWorkerLoop(); });
    renderWorkerThread = std::thread([this]() { renderWorkerLoop(); });
    createStream();
}

AudioEngine::~AudioEngine() {
    {
        std::lock_guard<std::mutex> lock(seekWorkerMutex);
        seekWorkerStop = true;
        seekRequestPending = false;
        seekAbortRequested.store(true);
    }
    seekWorkerCv.notify_all();
    if (seekWorkerThread.joinable()) {
        seekWorkerThread.join();
    }
    {
        std::lock_guard<std::mutex> lock(renderQueueMutex);
        renderWorkerStop = true;
    }
    renderWorkerCv.notify_all();
    if (renderWorkerThread.joinable()) {
        renderWorkerThread.join();
    }
    std::lock_guard<std::mutex> lock(decoderMutex);
    freeOutputSoxrContextLocked();
    closeStream();
}
