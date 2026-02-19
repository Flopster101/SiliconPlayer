#include "Vio2sfDecoder.h"

#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <limits>

#include <zlib.h>

extern "C" {
#include <psflib.h>
#include <vio2sf/desmume/state.h>
}

#define LOG_TAG "Vio2sfDecoder"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr int kNativeSampleRate = 44100;
constexpr int kNativeChannels = 2;
constexpr int kRenderChunkFrames = 1024;
constexpr double kFallbackDurationSeconds = 180.0;
constexpr unsigned long kInvalidPsfTime = 0xC0CAC01A;
constexpr int kNdsVoices = 16;

struct PsfStatusContext {
    const char* stage = "?";
};

static void* stdioFopen(void* context, const char* path) {
    (void)context;
    if (!path) return nullptr;
    std::string normalized(path);
    std::replace(normalized.begin(), normalized.end(), '\\', '/');
    constexpr const char* kFileScheme = "file://";
    if (normalized.rfind(kFileScheme, 0) == 0) {
        normalized.erase(0, std::strlen(kFileScheme));
    }
    return std::fopen(normalized.c_str(), "rb");
}

static size_t stdioFread(void* buffer, size_t size, size_t count, void* handle) {
    if (!buffer || !handle) return 0;
    return std::fread(buffer, size, count, static_cast<FILE*>(handle));
}

static int stdioFseek(void* handle, int64_t offset, int whence) {
    if (!handle) return -1;
    return std::fseek(static_cast<FILE*>(handle), static_cast<long>(offset), whence);
}

static int stdioFclose(void* handle) {
    if (!handle) return -1;
    return std::fclose(static_cast<FILE*>(handle));
}

static long stdioFtell(void* handle) {
    if (!handle) return -1;
    return std::ftell(static_cast<FILE*>(handle));
}

static const psf_file_callbacks kStdIoCallbacks = {
        "\\/:",
        nullptr,
        stdioFopen,
        stdioFread,
        stdioFseek,
        stdioFclose,
        stdioFtell
};

static bool equalsIgnoreCase(const char* lhs, const char* rhs) {
    if (!lhs || !rhs) return false;
    while (*lhs && *rhs) {
        const char a = static_cast<char>(std::tolower(static_cast<unsigned char>(*lhs)));
        const char b = static_cast<char>(std::tolower(static_cast<unsigned char>(*rhs)));
        if (a != b) return false;
        ++lhs;
        ++rhs;
    }
    return *lhs == '\0' && *rhs == '\0';
}

static uint32_t readLe32(const uint8_t* data) {
    return static_cast<uint32_t>(data[0]) |
           (static_cast<uint32_t>(data[1]) << 8u) |
           (static_cast<uint32_t>(data[2]) << 16u) |
           (static_cast<uint32_t>(data[3]) << 24u);
}

static unsigned long parsePsfTimeMs(const std::string& input, bool& ok) {
    ok = false;
    if (input.empty()) {
        return 0;
    }
    unsigned long value = 0;
    unsigned long multiplier = 1000;
    const char* ptr = input.c_str();
    unsigned long colonCount = 0;

    while (*ptr && ((*ptr >= '0' && *ptr <= '9') || *ptr == ':')) {
        colonCount += (*ptr == ':') ? 1u : 0u;
        ++ptr;
    }
    if (colonCount > 2) return 0;
    if (*ptr && *ptr != '.' && *ptr != ',') return 0;
    if (*ptr) ++ptr;
    while (*ptr && *ptr >= '0' && *ptr <= '9') ++ptr;
    if (*ptr) return 0;

    ptr = std::strrchr(input.c_str(), ':');
    if (!ptr) {
        ptr = input.c_str();
    }

    for (;;) {
        char* end = nullptr;
        if (ptr != input.c_str()) ++ptr;
        if (multiplier == 1000) {
            const double temp = std::strtod(ptr, &end);
            if (temp >= 60.0) return 0;
            value = static_cast<unsigned long>(temp * 1000.0);
        } else {
            const unsigned long temp = std::strtoul(ptr, &end, 10);
            if (temp >= 60 && multiplier < 3600000) return 0;
            value += temp * multiplier;
        }
        if (ptr == input.c_str()) break;
        ptr -= 2;
        while (ptr > input.c_str() && *ptr != ':') --ptr;
        multiplier *= 60;
    }

    if (value == kInvalidPsfTime) {
        return 0;
    }
    ok = true;
    return value;
}

static int loadTwosfMap(
        Vio2sfDecoder::TwosfLoaderState* state,
        int isSave,
        const uint8_t* udata,
        unsigned usize) {
    if (!state || !udata || usize < 8) {
        return -1;
    }

    uint8_t* iptr = nullptr;
    size_t isize = 0;
    const unsigned xsize = readLe32(udata + 4);
    const unsigned xofs = readLe32(udata + 0);

    if (isSave) {
        iptr = state->state;
        isize = state->stateSize;
        state->state = nullptr;
        state->stateSize = 0;
    } else {
        iptr = state->rom;
        isize = state->romSize;
        state->rom = nullptr;
        state->romSize = 0;
    }

    if (!iptr) {
        size_t rsize = xofs + xsize;
        if (!isSave) {
            rsize -= 1;
            rsize |= rsize >> 1;
            rsize |= rsize >> 2;
            rsize |= rsize >> 4;
            rsize |= rsize >> 8;
            rsize |= rsize >> 16;
            rsize += 1;
        }
        iptr = static_cast<uint8_t*>(std::malloc(rsize + 10));
        if (!iptr) return -1;
        std::memset(iptr, 0, rsize + 10);
        isize = rsize;
    } else if (isize < xofs + xsize) {
        size_t rsize = xofs + xsize;
        if (!isSave) {
            rsize -= 1;
            rsize |= rsize >> 1;
            rsize |= rsize >> 2;
            rsize |= rsize >> 4;
            rsize |= rsize >> 8;
            rsize |= rsize >> 16;
            rsize += 1;
        }
        uint8_t* xptr = static_cast<uint8_t*>(std::realloc(iptr, xofs + rsize + 10));
        if (!xptr) {
            std::free(iptr);
            return -1;
        }
        iptr = xptr;
        isize = rsize;
    }

    std::memcpy(iptr + xofs, udata + 8, xsize);
    if (isSave) {
        state->state = iptr;
        state->stateSize = isize;
    } else {
        state->rom = iptr;
        state->romSize = isize;
    }
    return 0;
}

static int loadTwosfMapZ(
        Vio2sfDecoder::TwosfLoaderState* state,
        int isSave,
        const uint8_t* zdata,
        unsigned zsize,
        unsigned zcrc) {
    (void)zcrc;
    if (!state || !zdata || zsize == 0) {
        return -1;
    }

    int zerr;
    uLongf usize = 8;
    uLongf rsize = usize;
    uint8_t* udata = static_cast<uint8_t*>(std::malloc(usize));
    if (!udata) {
        return -1;
    }

    while (Z_OK != (zerr = uncompress(udata, &usize, zdata, zsize))) {
        if (zerr != Z_MEM_ERROR && zerr != Z_BUF_ERROR) {
            std::free(udata);
            return -1;
        }
        if (usize >= 8) {
            usize = readLe32(udata + 4) + 8;
            if (usize < rsize) {
                rsize += rsize;
                usize = rsize;
            } else {
                rsize = usize;
            }
        } else {
            rsize += rsize;
            usize = rsize;
        }
        uint8_t* rdata = static_cast<uint8_t*>(std::realloc(udata, usize));
        if (!rdata) {
            std::free(udata);
            return -1;
        }
        udata = rdata;
    }

    uint8_t* rdata = static_cast<uint8_t*>(std::realloc(udata, usize));
    if (!rdata) {
        std::free(udata);
        return -1;
    }

    const int ret = loadTwosfMap(state, isSave, rdata, static_cast<unsigned>(usize));
    std::free(rdata);
    return ret;
}

static int twosfLoader(
        void* context,
        const uint8_t* exe,
        size_t exeSize,
        const uint8_t* reserved,
        size_t reservedSize) {
    auto* state = static_cast<Vio2sfDecoder::TwosfLoaderState*>(context);
    if (!state) return -1;

    if (exe && exeSize >= 8) {
        if (loadTwosfMap(state, 0, exe, static_cast<unsigned>(exeSize)) != 0) {
            return -1;
        }
    }

    if (reserved && reservedSize > 0) {
        size_t resvPos = 0;
        if (reservedSize < 16) return -1;

        while (resvPos + 12 < reservedSize) {
            const unsigned saveSize = readLe32(reserved + resvPos + 4);
            const unsigned saveCrc = readLe32(reserved + resvPos + 8);
            if (readLe32(reserved + resvPos + 0) == 0x45564153) {
                if (resvPos + 12 + saveSize > reservedSize) {
                    return -1;
                }
                if (loadTwosfMapZ(state, 1, reserved + resvPos + 12, saveSize, saveCrc) != 0) {
                    return -1;
                }
            }
            resvPos += 12 + saveSize;
        }
    }

    return 0;
}

static int twosfInfoCore(void* context, const char* name, const char* value) {
    auto* state = static_cast<Vio2sfDecoder::TwosfLoaderState*>(context);
    if (!state || !name || !value) {
        return 0;
    }

    char* end = nullptr;
    if (equalsIgnoreCase(name, "_frames")) {
        state->initialFrames = static_cast<int>(std::strtol(value, &end, 10));
    } else if (equalsIgnoreCase(name, "_clockdown")) {
        state->clockdown = static_cast<int>(std::strtol(value, &end, 10));
    } else if (equalsIgnoreCase(name, "_vio2sf_sync_type")) {
        state->syncType = static_cast<int>(std::strtol(value, &end, 10));
    } else if (equalsIgnoreCase(name, "_vio2sf_arm9_clockdown_level")) {
        state->arm9ClockdownLevel = static_cast<int>(std::strtol(value, &end, 10));
    } else if (equalsIgnoreCase(name, "_vio2sf_arm7_clockdown_level")) {
        state->arm7ClockdownLevel = static_cast<int>(std::strtol(value, &end, 10));
    }
    return 0;
}

static int twosfInfoMetadata(void* context, const char* name, const char* value) {
    auto* metadata = static_cast<Vio2sfDecoder::MetadataState*>(context);
    if (!metadata || !name || !value) {
        return 0;
    }

    if (equalsIgnoreCase(name, "length")) {
        bool ok = false;
        const unsigned long parsed = parsePsfTimeMs(value, ok);
        if (ok) {
            metadata->hasLength = true;
            metadata->lengthMs = parsed;
            metadata->lengthTag = value;
        }
    } else if (equalsIgnoreCase(name, "fade")) {
        bool ok = false;
        const unsigned long parsed = parsePsfTimeMs(value, ok);
        if (ok) {
            metadata->hasFade = true;
            metadata->fadeMs = parsed;
            metadata->fadeTag = value;
        }
    } else if (equalsIgnoreCase(name, "title")) {
        metadata->title = value;
    } else if (equalsIgnoreCase(name, "artist")) {
        metadata->artist = value;
    } else if (equalsIgnoreCase(name, "composer")) {
        metadata->composer = value;
    } else if (equalsIgnoreCase(name, "genre")) {
        metadata->genre = value;
    } else if (equalsIgnoreCase(name, "game")) {
        metadata->game = value;
    } else if (equalsIgnoreCase(name, "copyright")) {
        metadata->copyright = value;
    } else if (equalsIgnoreCase(name, "year")) {
        metadata->year = value;
    } else if (equalsIgnoreCase(name, "comment")) {
        metadata->comment = value;
    }
    return 0;
}

static void twosfStatus(void* context, const char* message) {
    const auto* status = static_cast<const PsfStatusContext*>(context);
    const char* stage = status ? status->stage : "?";
    if (!message || message[0] == '\0') {
        return;
    }
    LOGD("psflib[%s]: %s", stage, message);
}
}

Vio2sfDecoder::TwosfLoaderState::~TwosfLoaderState() {
    clear();
}

void Vio2sfDecoder::TwosfLoaderState::clear() {
    if (rom) {
        std::free(rom);
        rom = nullptr;
    }
    if (state) {
        std::free(state);
        state = nullptr;
    }
    romSize = 0;
    stateSize = 0;
    initialFrames = -1;
    syncType = 0;
    clockdown = 0;
    arm9ClockdownLevel = 0;
    arm7ClockdownLevel = 0;
}

Vio2sfDecoder::Vio2sfDecoder() = default;

Vio2sfDecoder::~Vio2sfDecoder() {
    close();
}

bool Vio2sfDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
    if (!path || path[0] == '\0') {
        return false;
    }
    sourcePath = path;
    PsfStatusContext metadataStatusContext{"meta"};
    PsfStatusContext coreStatusContext{"core"};

    MetadataState metadata;
    const int metadataLoadResult = psf_load(
            path,
            &kStdIoCallbacks,
            0x24,
            nullptr,
            nullptr,
            twosfInfoMetadata,
            &metadata,
            0,
            twosfStatus,
            &metadataStatusContext
    );
    if (metadataLoadResult <= 0) {
        // Some sets can still decode even when metadata pass fails; don't abort.
        LOGW("psf_load(metadata) failed for 2SF (continuing): %s", path);
    }

    const int coreLoadResult = psf_load(
            path,
            &kStdIoCallbacks,
            0x24,
            twosfLoader,
            &loaderState,
            twosfInfoCore,
            &loaderState,
            1,
            twosfStatus,
            &coreStatusContext
    );
    if (coreLoadResult <= 0) {
        LOGE("psf_load(core) failed for 2SF: %s", path);
        closeInternalLocked();
        return false;
    }
    if ((!loaderState.rom || loaderState.romSize == 0) &&
        (!loaderState.state || loaderState.stateSize == 0)) {
        LOGE("2SF load produced neither ROM nor state data: %s", path);
        closeInternalLocked();
        return false;
    }
    if (!loaderState.state || loaderState.stateSize == 0) {
        LOGW("2SF state blob missing; continuing with ROM-only boot: %s", path);
    }

    title = metadata.title;
    artist = metadata.artist;
    composer = metadata.composer;
    genre = metadata.genre;
    if (artist.empty()) {
        artist = metadata.game;
    }
    if (composer.empty()) {
        composer = artist;
    }
    if (genre.empty()) {
        genre = "2SF";
    }
    if (title.empty()) {
        title = std::filesystem::path(path).stem().string();
    }

    if (metadata.hasLength) {
        const unsigned long totalMs = metadata.lengthMs + (metadata.hasFade ? metadata.fadeMs : 0u);
        durationSeconds = static_cast<double>(totalMs) / 1000.0;
        durationReliable = durationSeconds > 0.0;
    } else {
        durationSeconds = kFallbackDurationSeconds;
        durationReliable = false;
    }

    ensureToggleChannelsLocked();
    if (!resetCoreLocked()) {
        LOGE("Failed to initialize vio2sf emulator core: %s", path);
        closeInternalLocked();
        return false;
    }

    isOpen = true;
    return true;
}

void Vio2sfDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

void Vio2sfDecoder::closeInternalLocked() {
    if (emu) {
        state_deinit(emu.get());
        emu.reset();
    }
    loaderState.clear();
    pcmScratch.clear();
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    isOpen = false;
    repeatMode = 0;
    renderedFrames = 0;
    durationSeconds = kFallbackDurationSeconds;
    durationReliable = false;
    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
}

bool Vio2sfDecoder::resetCoreLocked() {
    if (emu) {
        state_deinit(emu.get());
    } else {
        emu = std::make_unique<NDS_state>();
    }
    std::memset(emu.get(), 0, sizeof(NDS_state));
    if (state_init(emu.get()) != 0) {
        emu.reset();
        return false;
    }

    int arm7Clock = loaderState.arm7ClockdownLevel;
    int arm9Clock = loaderState.arm9ClockdownLevel;
    if (arm7Clock == 0) arm7Clock = loaderState.clockdown;
    if (arm9Clock == 0) arm9Clock = loaderState.clockdown;

    emu->dwInterpolation = static_cast<unsigned long>(std::clamp(interpolationQuality, 0, 4));
    emu->dwChannelMute = 0;
    emu->initial_frames = loaderState.initialFrames;
    emu->sync_type = loaderState.syncType;
    emu->arm7_clockdown_level = arm7Clock;
    emu->arm9_clockdown_level = arm9Clock;

    if (loaderState.rom && loaderState.romSize > 0) {
        state_setrom(emu.get(), loaderState.rom, static_cast<u32>(loaderState.romSize), 0);
    }
    state_loadstate(emu.get(), loaderState.state, static_cast<u32>(loaderState.stateSize));

    renderedFrames = 0;
    applyToggleChannelMutesLocked();
    return true;
}

int Vio2sfDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !emu || !buffer || numFrames <= 0) {
        return 0;
    }

    int framesToRender = numFrames;
    if (repeatMode != 2 && durationSeconds > 0.0) {
        const int64_t durationFrames = static_cast<int64_t>(std::llround(durationSeconds * sampleRate));
        const int64_t remaining = durationFrames - renderedFrames;
        if (remaining <= 0) {
            return 0;
        }
        framesToRender = static_cast<int>(std::min<int64_t>(framesToRender, remaining));
    }
    if (framesToRender <= 0) {
        return 0;
    }

    const size_t sampleCount = static_cast<size_t>(framesToRender) * channels;
    pcmScratch.resize(sampleCount);
    state_render(emu.get(), pcmScratch.data(), static_cast<unsigned int>(framesToRender));

    for (size_t i = 0; i < sampleCount; ++i) {
        buffer[i] = static_cast<float>(pcmScratch[i]) / 32768.0f;
    }
    renderedFrames += framesToRender;
    return framesToRender;
}

void Vio2sfDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !emu) {
        return;
    }

    const double clampedSeconds = std::max(0.0, seconds);
    if (!resetCoreLocked()) {
        closeInternalLocked();
        return;
    }
    if (clampedSeconds <= 0.0) {
        return;
    }

    int64_t framesToSkip = static_cast<int64_t>(std::llround(clampedSeconds * sampleRate));
    if (framesToSkip <= 0) {
        return;
    }

    std::vector<int16_t> discard(static_cast<size_t>(kRenderChunkFrames) * channels);
    while (framesToSkip > 0) {
        const int chunk = static_cast<int>(std::min<int64_t>(framesToSkip, kRenderChunkFrames));
        state_render(emu.get(), discard.data(), static_cast<unsigned int>(chunk));
        framesToSkip -= chunk;
        renderedFrames += chunk;
    }
}

double Vio2sfDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationSeconds;
}

int Vio2sfDecoder::getSampleRate() {
    return sampleRate;
}

int Vio2sfDecoder::getBitDepth() {
    return bitDepth;
}

std::string Vio2sfDecoder::getBitDepthLabel() {
    return "16-bit PCM";
}

int Vio2sfDecoder::getChannelCount() {
    return channels;
}

std::string Vio2sfDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string Vio2sfDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string Vio2sfDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string Vio2sfDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

void Vio2sfDecoder::ensureToggleChannelsLocked() {
    if (!toggleChannelNames.empty()) {
        if (toggleChannelMuted.size() != toggleChannelNames.size()) {
            toggleChannelMuted.assign(toggleChannelNames.size(), false);
        }
        return;
    }
    toggleChannelNames.reserve(kNdsVoices);
    for (int i = 0; i < kNdsVoices; ++i) {
        toggleChannelNames.push_back("Ch " + std::to_string(i + 1));
    }
    toggleChannelMuted.assign(toggleChannelNames.size(), false);
}

std::vector<std::string> Vio2sfDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    ensureToggleChannelsLocked();
    return toggleChannelNames;
}

std::vector<uint8_t> Vio2sfDecoder::getToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    ensureToggleChannelsLocked();
    return std::vector<uint8_t>(toggleChannelNames.size(), 1);
}

void Vio2sfDecoder::applyToggleChannelMutesLocked() {
    if (!emu) return;
    ensureToggleChannelsLocked();
    uint32_t mask = 0u;
    const int count = std::min<int>(kNdsVoices, static_cast<int>(toggleChannelMuted.size()));
    for (int i = 0; i < count; ++i) {
        if (toggleChannelMuted[static_cast<size_t>(i)]) {
            mask |= (1u << i);
        }
    }
    emu->dwChannelMute = mask;
}

void Vio2sfDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    ensureToggleChannelsLocked();
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyToggleChannelMutesLocked();
}

bool Vio2sfDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void Vio2sfDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    ensureToggleChannelsLocked();
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyToggleChannelMutesLocked();
}

void Vio2sfDecoder::setOutputSampleRate(int sampleRateHz) {
    (void)sampleRateHz;
}

void Vio2sfDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = (mode >= 0 && mode <= 3) ? mode : 0;
}

int Vio2sfDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK;
}

int Vio2sfDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int caps = PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_FIXED_SAMPLE_RATE;
    if (durationReliable) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

int Vio2sfDecoder::getFixedSampleRateHz() const {
    return sampleRate;
}

double Vio2sfDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (sampleRate <= 0) return 0.0;
    return static_cast<double>(renderedFrames) / sampleRate;
}

std::vector<std::string> Vio2sfDecoder::getSupportedExtensions() {
    return {"2sf", "mini2sf"};
}
