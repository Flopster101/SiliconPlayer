#include "VGMDecoder.h"
#include <android/log.h>
#include <algorithm>
#include <cstring>
#include <fstream>
#include <limits>
#include <cctype>
#include <vector>
#include <sstream>
#include <iomanip>

// libvgm includes
#include <vgm/emu/EmuCores.h>
#include <vgm/emu/EmuStructs.h>
#include <vgm/emu/SoundDevs.h>
#include <vgm/player/playera.hpp>
#include <vgm/player/playerbase.hpp>
#include <vgm/player/vgmplayer.hpp>
#include <vgm/utils/DataLoader.h>
#include <vgm/utils/MemoryLoader.h>

#define LOG_TAG "VGMDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr uint32_t kPlayerOutputBufferFrames = 4096;

bool parseBoolString(const std::string& value, bool fallback) {
    std::string normalized;
    normalized.reserve(value.size());
    for (char c : value) {
        normalized.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
    }
    if (normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on") {
        return true;
    }
    if (normalized == "0" || normalized == "false" || normalized == "no" || normalized == "off") {
        return false;
    }
    return fallback;
}

int parseIntString(const std::string& value, int fallback) {
    try {
        return std::stoi(value);
    } catch (...) {
        return fallback;
    }
}

bool startsWith(const std::string& value, const std::string& prefix) {
    return value.rfind(prefix, 0) == 0;
}

std::string bcdToString(uint16_t bcdValue) {
    std::ostringstream out;
    bool started = false;
    for (int shift = 12; shift >= 0; shift -= 4) {
        uint8_t digit = static_cast<uint8_t>((bcdValue >> shift) & 0x0F);
        if (!started && digit == 0 && shift > 0) {
            continue;
        }
        started = true;
        out << static_cast<char>('0' + std::min<uint8_t>(digit, 9));
    }
    return started ? out.str() : "0";
}

std::string fallbackChipName(uint8_t type) {
    switch (type) {
        case DEVID_SN76496: return "SN76496";
        case DEVID_YM2413: return "YM2413";
        case DEVID_YM2612: return "YM2612";
        case DEVID_YM2151: return "YM2151";
        case DEVID_YM2203: return "YM2203";
        case DEVID_YM2608: return "YM2608";
        case DEVID_YM2610: return "YM2610";
        case DEVID_YM3812: return "YM3812";
        case DEVID_YMF262: return "YMF262";
        case DEVID_AY8910: return "AY8910";
        case DEVID_NES_APU: return "NES APU";
        case DEVID_QSOUND: return "QSound";
        case DEVID_SAA1099: return "SAA1099";
        case DEVID_C6280: return "HuC6280";
        default: return "Unknown chip";
    }
}
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

    if (player->SetOutputSettings(sampleRate, 2, 16, kPlayerOutputBufferFrames) != 0x00) {
        LOGE("SetOutputSettings failed");
        return false;
    }

    const int activeRepeatMode = repeatMode.load();
    player->SetLoopCount(activeRepeatMode == 2 ? 0 : finiteLoopCount);
    pendingTerminalEnd = false;
    playbackTimeOffsetSeconds = 0.0;

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

    dataLoaderHandle = MemoryLoader_Init(fileData.data(), static_cast<UINT32>(fileData.size()));
    if (dataLoaderHandle == nullptr) {
        LOGE("MemoryLoader_Init failed");
        return false;
    }

    const UINT8 loadResult = DataLoader_Load(dataLoaderHandle);
    if (loadResult != 0x00) {
        LOGE("DataLoader_Load failed: 0x%02X", loadResult);
        DataLoader_Deinit(dataLoaderHandle);
        dataLoaderHandle = nullptr;
        return false;
    }

    const UINT8 result = player->LoadFile(dataLoaderHandle);
    if (result != 0x00) {
        LOGE("LoadFile failed: 0x%02X", result);
        player.reset();
        DataLoader_Deinit(dataLoaderHandle);
        dataLoaderHandle = nullptr;
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
            } else if (std::strcmp(key, "DATE") == 0) {
                if (releaseDate.empty()) releaseDate = value;
            } else if (std::strcmp(key, "ENCODED_BY") == 0) {
                if (encodedBy.empty()) encodedBy = value;
            } else if (std::strcmp(key, "COMMENT") == 0) {
                if (notes.empty()) notes = value;
            }
        }
    }

    PLR_SONG_INFO songInfo{};
    if (vgmPlayer->GetSongInfo(songInfo) == 0x00) {
        songHasLoopPoint = songInfo.loopTick != static_cast<uint32_t>(-1);
        fileVersionMajorBcd = songInfo.fileVerMaj;
        fileVersionMinorBcd = songInfo.fileVerMin;
        deviceCount = songInfo.deviceCnt;
        const double totalTime = player->GetTotalTime(PLAYTIME_LOOP_EXCL);
        duration = totalTime > 0.0 ? totalTime : 0.0;
    }
    std::vector<PLR_DEV_INFO> deviceInfos;
    if (vgmPlayer->GetSongDeviceInfo(deviceInfos) == 0x00) {
        std::ostringstream chipsOut;
        int visibleIndex = 0;
        for (const auto& dev : deviceInfos) {
            if (dev.parentIdx != std::numeric_limits<uint32_t>::max()) {
                continue; // Skip linked devices; present only main chips.
            }
            if (visibleIndex > 0) chipsOut << '\n';
            const char* declName = (dev.devDecl && dev.devDecl->name)
                    ? dev.devDecl->name(dev.devCfg)
                    : nullptr;
            const std::string chipName = (declName && declName[0] != '\0')
                    ? std::string(declName)
                    : fallbackChipName(dev.type);
            chipsOut << (visibleIndex + 1) << ". " << chipName;
            if (dev.instance != 0xFFFF && dev.instance > 0) {
                chipsOut << " #" << static_cast<int>(dev.instance + 1);
            }
            visibleIndex++;
        }
        usedChipList = chipsOut.str();
    }

    applyPlayerOptionsLocked();
    applyDeviceOptionsLocked(vgmPlayer);
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
    if (dataLoaderHandle != nullptr) {
        DataLoader_Deinit(dataLoaderHandle);
        dataLoaderHandle = nullptr;
    }

    fileData.clear();
    title.clear();
    artist.clear();
    gameName.clear();
    systemName.clear();
    releaseDate.clear();
    encodedBy.clear();
    notes.clear();
    fileVersionMajorBcd = 0;
    fileVersionMinorBcd = 0;
    deviceCount = 0;
    usedChipList.clear();
    duration = 0.0;
    currentLoop = 0;
    hasLooped = false;
    playerStarted = false;
    pendingTerminalEnd = false;
    playbackTimeOffsetSeconds = 0.0;
    songHasLoopPoint = false;
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

    // Match vgmplay-reference behavior: process initialization block immediately.
    player->Render(0, nullptr);

    playerStarted = true;
}

int VGMDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || !buffer || numFrames <= 0) {
        return 0;
    }
    if (pendingTerminalEnd) {
        pendingTerminalEnd = false;
        return 0;
    }

    ensurePlayerStarted();
    if (!playerStarted) {
        return 0;
    }

    std::vector<int16_t> int16Buffer(numFrames * channels);
    int framesRendered = 0;
    while (framesRendered < numFrames) {
        const int framesRemaining = numFrames - framesRendered;
        const uint32_t bytesRequested = static_cast<uint32_t>(framesRemaining * channels * sizeof(int16_t));
        const uint32_t bytesRendered = player->Render(
                bytesRequested,
                int16Buffer.data() + (framesRendered * channels)
        );
        const int chunkFrames = static_cast<int>(bytesRendered / (channels * sizeof(int16_t)));
        if (chunkFrames <= 0) {
            break;
        }
        framesRendered += chunkFrames;
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

    const int activeMode = repeatMode.load();
    if (activeMode != 2) {
        const UINT8 state = player->GetState();
        if ((state & (PLAYSTATE_END | PLAYSTATE_FIN)) != 0) {
            if (activeMode == 1 && allowNonLoopingLoop && !songHasLoopPoint) {
                player->Seek(PLAYPOS_SAMPLE, 0);
                pendingTerminalEnd = false;
                playbackTimeOffsetSeconds = 0.0;
                return framesRendered;
            }
            // Let one final rendered chunk pass through, then report EOF on next read.
            pendingTerminalEnd = true;
        }
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
    pendingTerminalEnd = false;
    playbackTimeOffsetSeconds = 0.0;
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

std::string VGMDecoder::getGameName() {
    return gameName;
}

std::string VGMDecoder::getSystemName() {
    return systemName;
}

std::string VGMDecoder::getReleaseDate() {
    return releaseDate;
}

std::string VGMDecoder::getEncodedBy() {
    return encodedBy;
}

std::string VGMDecoder::getNotes() {
    return notes;
}

std::string VGMDecoder::getFileVersion() {
    if (fileVersionMajorBcd == 0 && fileVersionMinorBcd == 0) {
        return "";
    }
    std::ostringstream out;
    out << bcdToString(fileVersionMajorBcd);
    if (fileVersionMinorBcd != 0) {
        out << "." << bcdToString(fileVersionMinorBcd);
    }
    return out.str();
}

int VGMDecoder::getDeviceCount() {
    return static_cast<int>(deviceCount);
}

std::string VGMDecoder::getUsedChipList() {
    return usedChipList;
}

bool VGMDecoder::hasLoopPoint() {
    return songHasLoopPoint;
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
        if (player->SetOutputSettings(sampleRate, 2, 16, kPlayerOutputBufferFrames) != 0x00) {
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
    if (player->SetOutputSettings(sampleRate, 2, 16, kPlayerOutputBufferFrames) != 0x00) {
        LOGE("SetOutputSettings failed while active");
        return;
    }
    if (player->Start() != 0x00) {
        LOGE("Start failed while applying sample-rate change");
        return;
    }
    playerStarted = true;
    player->Seek(PLAYPOS_SAMPLE, static_cast<uint32_t>(std::max(0.0, currentSeconds) * sampleRate));
    pendingTerminalEnd = false;
    playbackTimeOffsetSeconds = 0.0;
}

void VGMDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const int previousMode = repeatMode.load();
    const int normalizedMode = (mode >= 0 && mode <= 2) ? mode : 0;
    repeatMode.store(normalizedMode);

    if (player && previousMode == 2 && normalizedMode != 2) {
        const double includeTime = player->GetCurTime(PLAYTIME_LOOP_INCL);
        const double excludeTime = player->GetCurTime(PLAYTIME_LOOP_EXCL);
        if (includeTime >= 0.0 && excludeTime >= 0.0) {
            playbackTimeOffsetSeconds = std::max(0.0, includeTime - excludeTime);
        }
    } else if (previousMode != 2 && normalizedMode == 2) {
        playbackTimeOffsetSeconds = 0.0;
    }

    if (player) {
        player->SetLoopCount(normalizedMode == 2 ? 0 : finiteLoopCount);
    }
    pendingTerminalEnd = false;
}

int VGMDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

double VGMDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || sampleRate <= 0) {
        return 0.0;
    }
    const UINT8 timeFlags = (repeatMode.load() == 2)
            ? PLAYTIME_LOOP_EXCL
            : PLAYTIME_LOOP_INCL;
    const double currentTime = player->GetCurTime(timeFlags);
    if (currentTime >= 0.0) {
        if (repeatMode.load() == 2) {
            return currentTime;
        }
        return std::max(0.0, currentTime - playbackTimeOffsetSeconds);
    }
    const uint32_t currentSample = player->GetCurPos(PLAYPOS_SAMPLE);
    return static_cast<double>(currentSample) / sampleRate;
}

AudioDecoder::TimelineMode VGMDecoder::getTimelineMode() const {
    return repeatMode.load() == 2
            ? TimelineMode::Discontinuous
            : TimelineMode::ContinuousLinear;
}

void VGMDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) return;
    std::lock_guard<std::mutex> lock(decodeMutex);

    const std::string optionName(name);
    const std::string optionValue(value);

    if (optionName == "vgmplay.loop_count") {
        finiteLoopCount = static_cast<uint32_t>(std::clamp(parseIntString(optionValue, static_cast<int>(finiteLoopCount)), 1, 99));
        if (player) {
            const int activeRepeatMode = repeatMode.load();
            player->SetLoopCount(activeRepeatMode == 2 ? 0 : finiteLoopCount);
        }
    } else if (optionName == "vgmplay.allow_non_looping_loop") {
        allowNonLoopingLoop = parseBoolString(optionValue, allowNonLoopingLoop);
    } else if (optionName == "vgmplay.vsync_rate_hz") {
        const int parsed = parseIntString(optionValue, static_cast<int>(vgmPlaybackRateHz));
        vgmPlaybackRateHz = (parsed == 50 || parsed == 60) ? static_cast<uint32_t>(parsed) : 0;
        applyPlayerOptionsLocked();
    } else if (optionName == "vgmplay.resample_mode") {
        chipResampleMode = static_cast<uint8_t>(std::clamp(parseIntString(optionValue, chipResampleMode), 0, 2));
        PlayerBase* playerBase = player ? player->GetPlayer() : nullptr;
        if (auto* vgmPlayer = dynamic_cast<VGMPlayer*>(playerBase)) {
            applyDeviceOptionsLocked(vgmPlayer);
        }
    } else if (optionName == "vgmplay.chip_sample_mode") {
        chipSampleMode = static_cast<uint8_t>(std::clamp(parseIntString(optionValue, chipSampleMode), 0, 2));
        PlayerBase* playerBase = player ? player->GetPlayer() : nullptr;
        if (auto* vgmPlayer = dynamic_cast<VGMPlayer*>(playerBase)) {
            applyDeviceOptionsLocked(vgmPlayer);
        }
    } else if (optionName == "vgmplay.chip_sample_rate_hz") {
        const int parsed = parseIntString(optionValue, static_cast<int>(chipSampleRateHz));
        chipSampleRateHz = static_cast<uint32_t>(std::clamp(parsed, 8000, 192000));
        PlayerBase* playerBase = player ? player->GetPlayer() : nullptr;
        if (auto* vgmPlayer = dynamic_cast<VGMPlayer*>(playerBase)) {
            applyDeviceOptionsLocked(vgmPlayer);
        }
    } else if (startsWith(optionName, "vgmplay.chip_core.")) {
        const std::string chipKey = optionName.substr(std::strlen("vgmplay.chip_core."));
        int chipType = -1;
        if (chipKey == "SN76496") chipType = DEVID_SN76496;
        else if (chipKey == "YM2151") chipType = DEVID_YM2151;
        else if (chipKey == "YM2413") chipType = DEVID_YM2413;
        else if (chipKey == "YM2612") chipType = DEVID_YM2612;
        else if (chipKey == "YM2203") chipType = DEVID_YM2203;
        else if (chipKey == "YM2608") chipType = DEVID_YM2608;
        else if (chipKey == "YM2610") chipType = DEVID_YM2610;
        else if (chipKey == "YM3812") chipType = DEVID_YM3812;
        else if (chipKey == "YMF262") chipType = DEVID_YMF262;
        else if (chipKey == "AY8910") chipType = DEVID_AY8910;
        else if (chipKey == "NES_APU") chipType = DEVID_NES_APU;
        else if (chipKey == "qsound") chipType = DEVID_QSOUND;
        else if (chipKey == "saa1099") chipType = DEVID_SAA1099;
        else if (chipKey == "c6280") chipType = DEVID_C6280;
        if (chipType < 0) return;

        const int choiceValue = parseIntString(optionValue, 0);
        chipCoreOverrideByType[static_cast<uint8_t>(chipType)] =
                resolveChipCoreForOption(static_cast<uint8_t>(chipType), choiceValue);
        PlayerBase* playerBase = player ? player->GetPlayer() : nullptr;
        if (auto* vgmPlayer = dynamic_cast<VGMPlayer*>(playerBase)) {
            applyDeviceOptionsLocked(vgmPlayer);
        }
    }
}

void VGMDecoder::applyPlayerOptionsLocked() {
    PlayerBase* playerBase = player ? player->GetPlayer() : nullptr;
    auto* vgmPlayer = dynamic_cast<VGMPlayer*>(playerBase);
    if (!vgmPlayer) {
        return;
    }

    VGM_PLAY_OPTIONS playOptions{};
    if (vgmPlayer->GetPlayerOptions(playOptions) != 0x00) {
        return;
    }
    playOptions.playbackHz = vgmPlaybackRateHz;
    vgmPlayer->SetPlayerOptions(playOptions);
}

void VGMDecoder::applyDeviceOptionsLocked(VGMPlayer* vgmPlayer) {
    if (!vgmPlayer) return;

    std::vector<PLR_DEV_INFO> deviceInfos;
    if (vgmPlayer->GetSongDeviceInfo(deviceInfos) != 0x00) {
        return;
    }

    for (const auto& deviceInfo : deviceInfos) {
        PLR_DEV_OPTS deviceOptions{};
        if (vgmPlayer->GetDeviceOptions(deviceInfo.id, deviceOptions) != 0x00) {
            continue;
        }

        deviceOptions.srMode = chipSampleMode;
        deviceOptions.resmplMode = chipResampleMode;
        deviceOptions.smplRate = chipSampleRateHz;

        auto coreIt = chipCoreOverrideByType.find(deviceInfo.type);
        if (coreIt != chipCoreOverrideByType.end()) {
            deviceOptions.emuCore[0] = coreIt->second;
        }

        vgmPlayer->SetDeviceOptions(deviceInfo.id, deviceOptions);
    }
}

uint32_t VGMDecoder::resolveChipCoreForOption(uint8_t deviceType, int optionValue) const {
    switch (deviceType) {
        case DEVID_SN76496:
            return optionValue == 1 ? FCC_MAXM : FCC_MAME;
        case DEVID_YM2151:
            return optionValue == 1 ? FCC_NUKE : FCC_MAME;
        case DEVID_YM2413:
            if (optionValue == 1) return FCC_MAME;
            if (optionValue == 2) return FCC_NUKE;
            return FCC_EMU_;
        case DEVID_YM2612:
            if (optionValue == 1) return FCC_NUKE;
            if (optionValue == 2) return FCC_GENS;
            return FCC_GPGX;
        case DEVID_YM2203:
        case DEVID_YM2608:
        case DEVID_YM2610:
        case DEVID_AY8910:
            return optionValue == 1 ? FCC_MAME : FCC_EMU_;
        case DEVID_YM3812:
        case DEVID_YMF262:
            if (optionValue == 1) return FCC_MAME;
            if (optionValue == 2) return FCC_NUKE;
            return FCC_ADLE;
        case DEVID_NES_APU:
            return optionValue == 1 ? FCC_MAME : FCC_NSFP;
        case DEVID_QSOUND:
            return optionValue == 1 ? FCC_MAME : FCC_CTR_;
        case DEVID_SAA1099:
            return optionValue == 1 ? FCC_MAME : FCC_VBEL;
        case DEVID_C6280:
            return optionValue == 1 ? FCC_MAME : FCC_OOTK;
        default:
            return 0;
    }
}

std::vector<std::string> VGMDecoder::getSupportedExtensions() {
    return {
            "vgm",
            "vgz",
            "vgm.gz"
    };
}
