#include "VGMDecoder.h"
#include <android/log.h>
#include <algorithm>
#include <cstring>
#include <fstream>
#include <limits>
#include <vector>

// libvgm includes
#include <vgm/player/playera.hpp>
#include <vgm/player/playerbase.hpp>
#include <vgm/player/vgmplayer.hpp>
#include <vgm/utils/DataLoader.h>
#include <vgm/utils/MemoryLoader.h>

#define LOG_TAG "VGMDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
constexpr uint32_t kInitialRenderDebugLogs = 5;
}

VGMDecoder::VGMDecoder() {
}

VGMDecoder::~VGMDecoder() {
    close();
}

bool VGMDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternal();

    player = std::make_unique<PlayerA>();
    player->RegisterPlayerEngine(new VGMPlayer());

    if (sampleRate <= 0) {
        sampleRate = 44100;
    }

    if (player->SetOutputSettings(sampleRate, 2, 16, 1024) != 0x00) {
        LOGE("SetOutputSettings failed");
        return false;
    }

    player->SetLoopCount(repeatMode == 0 ? maxLoops : 0);

    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open file: %s", path);
        return false;
    }

    const std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);
    if (size <= 0) {
        LOGE("Invalid file size for %s", path);
        return false;
    }
    if (static_cast<uint64_t>(size) > std::numeric_limits<UINT32>::max()) {
        LOGE("File too large for libvgm loader: %lld", static_cast<long long>(size));
        return false;
    }

    fileData.resize(static_cast<size_t>(size));
    if (!file.read(reinterpret_cast<char*>(fileData.data()), size)) {
        LOGE("Failed to read file: %s", path);
        return false;
    }
    file.close();

    DATA_LOADER* dataLoader = MemoryLoader_Init(fileData.data(), static_cast<UINT32>(fileData.size()));
    if (dataLoader == nullptr) {
        LOGE("MemoryLoader_Init failed");
        return false;
    }

    const UINT8 loadResult = DataLoader_Load(dataLoader);
    if (loadResult != 0x00) {
        LOGE("DataLoader_Load failed: 0x%02X", loadResult);
        DataLoader_Deinit(dataLoader);
        return false;
    }

    const UINT8 result = player->LoadFile(dataLoader);
    DataLoader_Deinit(dataLoader);
    if (result != 0x00) {
        LOGE("LoadFile failed: 0x%02X", result);
        player.reset();
        return false;
    }

    PlayerBase* playerBase = player->GetPlayer();
    if (!playerBase) {
        LOGE("No active player engine after LoadFile");
        return false;
    }

    VGMPlayer* vgmPlayer = dynamic_cast<VGMPlayer*>(playerBase);
    if (!vgmPlayer) {
        LOGE("Active player engine is not VGMPlayer");
        return false;
    }

    const char* const* tags = vgmPlayer->GetTags();
    if (tags) {
        for (int i = 0; tags[i] != nullptr; i += 2) {
            const char* key = tags[i];
            const char* value = tags[i + 1];
            if (!value || std::strlen(value) == 0) continue;

            if (std::strcmp(key, "TITLE") == 0 || std::strcmp(key, "TITLE-JPN") == 0) {
                if (title.empty()) title = value;
            } else if (std::strcmp(key, "ARTIST") == 0 || std::strcmp(key, "ARTIST-JPN") == 0) {
                if (artist.empty()) artist = value;
            } else if (std::strcmp(key, "GAME") == 0 || std::strcmp(key, "GAME-JPN") == 0) {
                if (gameName.empty()) gameName = value;
            } else if (std::strcmp(key, "SYSTEM") == 0 || std::strcmp(key, "SYSTEM-JPN") == 0) {
                if (systemName.empty()) systemName = value;
            }
        }
    }

    PLR_SONG_INFO songInfo{};
    if (vgmPlayer->GetSongInfo(songInfo) == 0x00) {
        duration = vgmPlayer->Tick2Second(songInfo.songLen);
    }

    renderDebugLogCount = 0;
    return true;
}

void VGMDecoder::closeInternal() {
    if (player) {
        if (playerStarted) {
            player->Stop();
        }
        player->UnloadFile();
        player.reset();
    }

    fileData.clear();
    title.clear();
    artist.clear();
    gameName.clear();
    systemName.clear();
    duration = 0.0;
    currentLoop = 0;
    hasLooped = false;
    playerStarted = false;
    renderDebugLogCount = 0;
}

void VGMDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternal();
}

void VGMDecoder::ensurePlayerStarted() {
    if (!player || playerStarted) {
        return;
    }

    player->SetSampleRate(sampleRate);
    const UINT8 startResult = player->Start();
    if (startResult != 0x00) {
        LOGE("Player start failed: 0x%02X", startResult);
        return;
    }
    playerStarted = true;
}

int VGMDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !buffer || numFrames <= 0) {
        return 0;
    }

    ensurePlayerStarted();
    if (!playerStarted) {
        return 0;
    }

    PlayerBase* playerBase = player->GetPlayer();
    uint32_t filePosBefore = 0;
    uint32_t playTickBefore = 0;
    const uint8_t stateBefore = player->GetState();
    const bool shouldLog = renderDebugLogCount < kInitialRenderDebugLogs && playerBase != nullptr;
    if (shouldLog) {
        filePosBefore = playerBase->GetCurPos(PLAYPOS_FILEOFS);
        playTickBefore = playerBase->GetCurPos(PLAYPOS_TICK);
    }

    std::vector<int16_t> int16Buffer(numFrames * channels);
    const uint32_t bytesRequested = static_cast<uint32_t>(numFrames * channels * sizeof(int16_t));
    const uint32_t bytesRendered = player->Render(bytesRequested, int16Buffer.data());
    const int framesRendered = static_cast<int>(bytesRendered / (channels * sizeof(int16_t)));

    if (shouldLog) {
        const uint32_t filePosAfter = playerBase->GetCurPos(PLAYPOS_FILEOFS);
        const uint32_t playTickAfter = playerBase->GetCurPos(PLAYPOS_TICK);
        const uint8_t stateAfter = player->GetState();
        LOGD(
                "[render %u] req=%d got=%d filePos=0x%X->0x%X tick=%u->%u state=0x%02X->0x%02X",
                renderDebugLogCount + 1,
                numFrames,
                framesRendered,
                filePosBefore,
                filePosAfter,
                playTickBefore,
                playTickAfter,
                stateBefore,
                stateAfter
        );
        renderDebugLogCount++;
    }

    if (framesRendered <= 0) {
        return 0;
    }

    for (int i = 0; i < framesRendered * channels; ++i) {
        buffer[i] = static_cast<float>(int16Buffer[i]) / 32768.0f;
    }

    const uint32_t loopCount = player->GetCurLoop();
    if (loopCount > currentLoop) {
        currentLoop = loopCount;
        hasLooped = true;
    }

    return framesRendered;
}

void VGMDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return;
    }

    const uint32_t targetSample = static_cast<uint32_t>(std::max(0.0, seconds) * sampleRate);
    player->Seek(PLAYPOS_SAMPLE, targetSample);
}

double VGMDecoder::getDuration() {
    return duration;
}

int VGMDecoder::getSampleRate() {
    return sampleRate;
}

int VGMDecoder::getBitDepth() {
    return bitDepth;
}

std::string VGMDecoder::getBitDepthLabel() {
    return "16 bit";
}

int VGMDecoder::getDisplayChannelCount() {
    return channels;
}

int VGMDecoder::getChannelCount() {
    return channels;
}

std::string VGMDecoder::getTitle() {
    return title;
}

std::string VGMDecoder::getArtist() {
    if (!artist.empty()) {
        return artist;
    }
    if (!gameName.empty()) {
        return gameName;
    }
    return "";
}

void VGMDecoder::setOutputSampleRate(int rate) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (rate <= 0 || rate == sampleRate) {
        return;
    }

    const int oldSampleRate = sampleRate;
    sampleRate = rate;

    if (!player) {
        return;
    }

    if (!playerStarted) {
        if (player->SetOutputSettings(sampleRate, 2, 16, 1024) != 0x00) {
            LOGE("SetOutputSettings failed before start");
        }
        return;
    }

    const uint32_t currentSample = player->GetCurPos(PLAYPOS_SAMPLE);
    const double currentSeconds = oldSampleRate > 0
            ? static_cast<double>(currentSample) / static_cast<double>(oldSampleRate)
            : 0.0;

    player->Stop();
    playerStarted = false;
    if (player->SetOutputSettings(sampleRate, 2, 16, 1024) != 0x00) {
        LOGE("SetOutputSettings failed while active");
        return;
    }
    if (player->Start() != 0x00) {
        LOGE("Start failed while applying sample-rate change");
        return;
    }
    playerStarted = true;
    player->Seek(PLAYPOS_SAMPLE, static_cast<uint32_t>(std::max(0.0, currentSeconds) * sampleRate));
}

void VGMDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = (mode >= 0 && mode <= 2) ? mode : 0;

    if (player) {
        player->SetLoopCount(repeatMode == 0 ? maxLoops : 0);
    }
}

int VGMDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

double VGMDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || sampleRate <= 0) {
        return 0.0;
    }
    const uint32_t currentSample = player->GetCurPos(PLAYPOS_SAMPLE);
    return static_cast<double>(currentSample) / sampleRate;
}

void VGMDecoder::setOption(const char* /*name*/, const char* /*value*/) {
}

std::vector<std::string> VGMDecoder::getSupportedExtensions() {
    return {
            "vgm",
            "vgz",
            "vgm.gz"
    };
}
