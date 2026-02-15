#include "VGMDecoder.h"
#include <android/log.h>
#include <fstream>
#include <algorithm>
#include <cstring>

// libvgm includes
#include <vgm/player/vgmplayer.hpp>
#include <vgm/player/playerbase.hpp>
#include <vgm/utils/FileLoader.h>
#include <vgm/utils/MemoryLoader.h>
#include <vgm/utils/DataLoader.h>
#include <vgm/emu/Resampler.h>

#define LOG_TAG "VGMDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

VGMDecoder::VGMDecoder() {
}

VGMDecoder::~VGMDecoder() {
    close();
}

bool VGMDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);

    closeInternal();

    // Load file into memory
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open file: %s", path);
        return false;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    if (size <= 0) {
        LOGE("File is empty: %s", path);
        return false;
    }

    try {
        fileData.resize(size);
        if (!file.read(reinterpret_cast<char*>(fileData.data()), size)) {
            LOGE("Failed to read file: %s", path);
            return false;
        }

        // Create VGM player
        player = std::make_unique<VGMPlayer>();

        // Create data loader from memory (MemoryLoader handles gzip decompression automatically)
        DATA_LOADER* dataLoader = MemoryLoader_Init(fileData.data(), static_cast<UINT32>(fileData.size()));
        if (dataLoader == nullptr) {
            LOGE("Failed to initialize memory loader");
            return false;
        }

        // Load the data loader
        UINT8 loadResult = DataLoader_Load(dataLoader);
        if (loadResult != 0x00) {
            LOGE("Failed to load data loader, error: 0x%02X", loadResult);
            DataLoader_Deinit(dataLoader);
            return false;
        }

        // Load file into player
        UINT8 result = player->LoadFile(dataLoader);
        DataLoader_Deinit(dataLoader);

        if (result != 0x00) {
            LOGE("Failed to load VGM file, error code: 0x%02X", result);
            player.reset();
            return false;
        }

        // Get metadata
        const char* const* tags = player->GetTags();
        if (tags) {
            for (int i = 0; tags[i] != nullptr; i += 2) {
                const char* key = tags[i];
                const char* value = tags[i + 1];

                if (strcmp(key, "TITLE") == 0 || strcmp(key, "TITLE-JPN") == 0) {
                    if (title.empty() && value && strlen(value) > 0) title = value;
                } else if (strcmp(key, "ARTIST") == 0 || strcmp(key, "ARTIST-JPN") == 0) {
                    if (artist.empty() && value && strlen(value) > 0) artist = value;
                } else if (strcmp(key, "GAME") == 0 || strcmp(key, "GAME-JPN") == 0) {
                    if (gameName.empty() && value && strlen(value) > 0) gameName = value;
                } else if (strcmp(key, "SYSTEM") == 0 || strcmp(key, "SYSTEM-JPN") == 0) {
                    if (systemName.empty() && value && strlen(value) > 0) systemName = value;
                }
            }
        }

        // Get duration
        PLR_SONG_INFO songInfo;
        if (player->GetSongInfo(songInfo) == 0x00) {
            // Duration is in samples, convert to seconds
            duration = static_cast<double>(songInfo.songLen) / sampleRate;
        }

        // Allocate render buffer (1024 frames is a reasonable chunk size)
        renderBuffer.resize(1024);

        return true;
    } catch (const std::exception& e) {
        LOGE("Exception opening VGM: %s", e.what());
        return false;
    } catch (...) {
        LOGE("Unknown exception opening VGM");
        return false;
    }
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
    renderBuffer.clear();
    title.clear();
    artist.clear();
    gameName.clear();
    systemName.clear();
    duration = 0.0;
    currentLoop = 0;
    hasLooped = false;
    playerStarted = false;
}

void VGMDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternal();
}

void VGMDecoder::ensurePlayerStarted() {
    // This method should be called with decodeMutex already locked
    if (!player || playerStarted) {
        return;
    }

    player->SetSampleRate(sampleRate);
    player->Start();
    playerStarted = true;
}

int VGMDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }

    // Ensure player is started with the correct sample rate (set by setOutputSampleRate)
    ensurePlayerStarted();

    // CRITICAL: VGMPlayer's resampler ADDS to the buffer instead of replacing it!
    // We must zero the buffer before rendering
    std::memset(buffer, 0, numFrames * 2 * sizeof(float));

    int framesRead = 0;
    while (framesRead < numFrames) {
        int framesToRender = std::min(numFrames - framesRead, static_cast<int>(renderBuffer.size()));

        // Zero the render buffer before calling Render (libvgm uses += not =)
        std::memset(renderBuffer.data(), 0, framesToRender * sizeof(WAVE_32BS));

        // Render from VGM player
        uint32_t renderedFrames = player->Render(framesToRender, renderBuffer.data());

        if (renderedFrames == 0) {
            // End of playback
            break;
        }

        // Convert from WAVE_32BS (32-bit stereo) to float
        // Apply 200x gain boost because libvgm outputs very quiet samples
        const float gain = 200.0f;
        for (uint32_t i = 0; i < renderedFrames; i++) {
            // WAVE_32BS contains left and right as 32-bit signed integers
            // Convert to float range [-1.0, 1.0] and apply gain
            buffer[(framesRead + i) * 2 + 0] = (renderBuffer[i].L / 2147483648.0f) * gain; // Left
            buffer[(framesRead + i) * 2 + 1] = (renderBuffer[i].R / 2147483648.0f) * gain; // Right
        }

        framesRead += renderedFrames;

        // Check if we've looped
        uint32_t currentLoopCount = player->GetCurLoop();
        if (currentLoopCount > currentLoop) {
            currentLoop = currentLoopCount;
            hasLooped = true;

            // If we've reached max loops and repeat mode is not enabled, stop
            if (repeatMode == 0 && currentLoop >= maxLoops) {
                break;
            }
        }
    }

    return framesRead;
}

void VGMDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return;
    }

    // Convert seconds to samples
    uint32_t targetSample = static_cast<uint32_t>(seconds * sampleRate);

    // VGM player uses sample-based seeking
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
    // If artist is empty, try game name
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
    if (rate > 0) {
        sampleRate = rate;
        if (player && playerStarted) {
            // If player is already started, update its sample rate
            player->SetSampleRate(sampleRate);
        }
    }
}

void VGMDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = mode;

    // VGM player doesn't have SetLoopCount, we'll handle looping in the read() method
    // by checking GetCurLoop() and stopping when appropriate
}

int VGMDecoder::getRepeatModeCapabilities() const {
    // VGM supports: Repeat track and Repeat at loop point
    // No repeat is handled by stopping after maxLoops
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

double VGMDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0.0;
    }

    // Get current position in samples
    uint32_t currentSample = player->GetCurPos(PLAYPOS_SAMPLE);
    return static_cast<double>(currentSample) / sampleRate;
}

void VGMDecoder::setOption(const char* name, const char* value) {
    // Options can be added later for VGM-specific settings
    // e.g., chip volumes, resampling quality, etc.
}

std::vector<std::string> VGMDecoder::getSupportedExtensions() {
    return {
        "vgm",  // Video Game Music
        "vgz",  // Compressed VGM (gzip)
        "vgm.gz" // Alternative compressed VGM extension
    };
}
