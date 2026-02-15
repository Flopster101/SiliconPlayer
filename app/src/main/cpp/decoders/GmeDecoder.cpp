#include "GmeDecoder.h"
#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <vector>

extern "C" {
#include <gme/gme.h>
}

#define LOG_TAG "GmeDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr int kRenderBlockFrames = 512;

std::string safeString(const char* value) {
    return value ? std::string(value) : "";
}

std::string toLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}
}

GmeDecoder::GmeDecoder() = default;

GmeDecoder::~GmeDecoder() {
    close();
}

bool GmeDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternal();

    if (!path) {
        return false;
    }

    const gme_err_t openErr = gme_open_file(path, &emu, sampleRate);
    if (openErr != nullptr || emu == nullptr) {
        LOGE("gme_open_file failed: %s", openErr ? openErr : "unknown error");
        emu = nullptr;
        return false;
    }

    trackCount = std::max(1, gme_track_count(emu));
    activeTrack = 0;
    pendingTerminalEnd = false;

    gme_set_autoload_playback_limit(emu, 1);
    gme_ignore_silence(emu, 0);

    const gme_err_t startErr = gme_start_track(emu, activeTrack);
    if (startErr != nullptr) {
        LOGE("gme_start_track failed: %s", startErr);
        closeInternal();
        return false;
    }

    gme_info_t* info = nullptr;
    const gme_err_t infoErr = gme_track_info(emu, &info, activeTrack);
    if (infoErr == nullptr && info != nullptr) {
        title = safeString(info->song);
        artist = safeString(info->author);
        composer = safeString(info->author);
        genre = safeString(info->system);

        int durationMs = info->play_length;
        if (durationMs <= 0) durationMs = info->length;
        if (durationMs <= 0 && info->intro_length > 0 && info->loop_length > 0) {
            durationMs = info->intro_length + (info->loop_length * 2);
        }
        duration = durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;
        gme_free_info(info);
    } else {
        duration = 0.0;
    }

    return true;
}

void GmeDecoder::closeInternal() {
    if (emu != nullptr) {
        gme_delete(emu);
        emu = nullptr;
    }

    duration = 0.0;
    trackCount = 0;
    activeTrack = 0;
    pendingTerminalEnd = false;
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
}

void GmeDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternal();
}

int GmeDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu || !buffer || numFrames <= 0) {
        return 0;
    }

    if (pendingTerminalEnd) {
        pendingTerminalEnd = false;
        return 0;
    }

    int framesRead = 0;
    std::vector<short> pcmBlock(kRenderBlockFrames * channels);

    while (framesRead < numFrames) {
        const int framesToRead = std::min(kRenderBlockFrames, numFrames - framesRead);
        const int samplesToRead = framesToRead * channels;
        const gme_err_t playErr = gme_play(emu, samplesToRead, pcmBlock.data());
        if (playErr != nullptr) {
            LOGE("gme_play failed: %s", playErr);
            break;
        }

        for (int i = 0; i < samplesToRead; ++i) {
            buffer[(framesRead * channels) + i] = static_cast<float>(pcmBlock[i]) / 32768.0f;
        }
        framesRead += framesToRead;

        if (gme_track_ended(emu)) {
            const int mode = repeatMode.load();
            if (mode == 1) {
                const gme_err_t restartErr = gme_start_track(emu, activeTrack);
                if (restartErr != nullptr) {
                    LOGE("gme_start_track(repeat) failed: %s", restartErr);
                    pendingTerminalEnd = true;
                    break;
                }
                continue;
            }

            pendingTerminalEnd = true;
            break;
        }
    }

    return framesRead;
}

void GmeDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu) return;

    const int targetMs = static_cast<int>(std::max(0.0, seconds) * 1000.0);
    const gme_err_t seekErr = gme_seek(emu, targetMs);
    if (seekErr != nullptr) {
        LOGE("gme_seek failed: %s", seekErr);
    }
    pendingTerminalEnd = false;
}

double GmeDecoder::getDuration() {
    return duration;
}

int GmeDecoder::getSampleRate() {
    return sampleRate;
}

int GmeDecoder::getBitDepth() {
    return bitDepth;
}

std::string GmeDecoder::getBitDepthLabel() {
    return "16 bit";
}

int GmeDecoder::getDisplayChannelCount() {
    return channels;
}

int GmeDecoder::getChannelCount() {
    return channels;
}

std::string GmeDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string GmeDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string GmeDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string GmeDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

void GmeDecoder::setOutputSampleRate(int rate) {
    if (rate <= 0) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    // libgme sample rate is selected when opening the file.
    // Keep this value for the next open().
    sampleRate = rate;
}

void GmeDecoder::setRepeatMode(int mode) {
    repeatMode.store(mode);
}

int GmeDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK;
}

double GmeDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!emu) return 0.0;
    return static_cast<double>(gme_tell(emu)) / 1000.0;
}

std::vector<std::string> GmeDecoder::getSupportedExtensions() {
    std::vector<std::string> extensions;
    const gme_type_t* typeList = gme_type_list();
    if (!typeList) {
        return extensions;
    }

    for (size_t i = 0; typeList[i] != nullptr; ++i) {
        const char* ext = gme_type_extension(typeList[i]);
        if (!ext || ext[0] == '\0') {
            continue;
        }
        extensions.push_back(toLowerAscii(ext));
    }

    std::sort(extensions.begin(), extensions.end());
    extensions.erase(std::unique(extensions.begin(), extensions.end()), extensions.end());
    return extensions;
}
