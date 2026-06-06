#include "AdPlugDecoder.h"

#include <adplug/adplug.h>
#include <adplug/emuopl.h>
#include <adplug/kemuopl.h>
#include <adplug/nemuopl.h>
#include <adplug/player.h>
#include <adplug/wemuopl.h>

#include <algorithm>
#include <android/log.h>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <filesystem>

#include "../ChannelScopeSharedState.h"

#define LOG_TAG "AdPlugDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
class TrackingOplProxy final : public Copl {
public:
    enum class Engine : int {
        DosBox = 0,
        KenSilverman = 1,
        Mame = 2,
        Nuked = 3
    };

    static std::unique_ptr<TrackingOplProxy> create(Engine engine, int sampleRateHz) {
        std::unique_ptr<Copl> backend;
        bool promoteDualOnSecondChip = false;
        bool mirrorMonoWhenSingleChip = false;

        switch (engine) {
            case Engine::DosBox:
                backend = std::make_unique<CWemuopl>(sampleRateHz, true, true);
                break;
            case Engine::KenSilverman:
                backend = std::make_unique<CKemuopl>(sampleRateHz, true, true);
                mirrorMonoWhenSingleChip = true;
                break;
            case Engine::Nuked:
                backend = std::make_unique<CNemuopl>(sampleRateHz);
                break;
            case Engine::Mame:
            default:
                backend = std::make_unique<CEmuopl>(sampleRateHz, true, true);
                // CEmuopl defaults to dual mode, which sends single-chip tunes
                // to one side. Promote to dual only once chip 1 is actually used.
                promoteDualOnSecondChip = true;
                break;
        }

        return std::unique_ptr<TrackingOplProxy>(
                new TrackingOplProxy(std::move(backend), promoteDualOnSecondChip, mirrorMonoWhenSingleChip, engine));
    }

    void write(int reg, int val) override {
        if (!backend_) {
            return;
        }
        const int chip = backend_->getchip();
        const auto mapping = mapTotalLevelRegister(reg);
        if (mapping.valid && chip >= 0 && chip < kChipCount) {
            rawTotalLevel_[chip][mapping.channel][mapping.slot] = val;
            const bool muted = channelMuted_[chip][mapping.channel];
            const int effective = muted ? forceMuteTotalLevel(val) : val;
            backend_->write(reg, effective);
            return;
        }
        backend_->write(reg, val);
    }

    void setchip(int n) override {
        if (!backend_) {
            return;
        }
        backend_->setchip(n);
        currChip = backend_->getchip();
        if (n == 1) {
            dualChipActive_ = true;
            if (promoteDualOnSecondChip_) {
                if (auto* emuOpl = dynamic_cast<CEmuopl*>(backend_.get())) {
                    emuOpl->settype(TYPE_DUAL_OPL2);
                    currType = TYPE_DUAL_OPL2;
                }
            }
        }
    }

    int getchip() override {
        if (!backend_) {
            return currChip;
        }
        return backend_->getchip();
    }

    void update(short* buf, int samples) override {
        update_and_scope(buf, samples, nullptr);
    }

    void update_and_scope(short* buf, int samples, short* scope_buf) override {
        if (!backend_ || !buf || samples <= 0) {
            return;
        }
        backend_->update_and_scope(buf, samples, scope_buf);
        if (mirrorMonoWhenSingleChip_ && !dualChipActive_) {
            for (int i = 0; i < samples; ++i) {
                buf[(i * 2) + 1] = buf[i * 2];
            }
        }
    }

    void init() override {
        if (!backend_) {
            return;
        }
        backend_->init();
        currChip = backend_->getchip();
        currType = backend_->gettype();
        dualChipActive_ = false;
        std::memset(rawTotalLevel_, 0, sizeof(rawTotalLevel_));
        if (auto* emuOpl = dynamic_cast<CEmuopl*>(backend_.get())) {
            emuOpl->settype(TYPE_OPL2);
            currType = TYPE_OPL2;
        }
    }

    int getVoiceCount() const {
        return dualChipActive_ ? 18 : 9;
    }

    bool isScopeInterleaved() const {
        return engine_ == Engine::Nuked;
    }

    int getScopeStridePerFrame() const {
        // Nuked always writes 18 interleaved values per frame.
        // Planar engines don't have a per-frame stride.
        return engine_ == Engine::Nuked ? 18 : 0;
    }

    void setVoiceMuted(int voiceIndex, bool muted) {
        if (voiceIndex < 0 || voiceIndex >= 18) {
            return;
        }
        const int chip = voiceIndex / 9;
        const int channel = voiceIndex % 9;
        channelMuted_[chip][channel] = muted;
        applyChannelTotalLevel(chip, channel);
    }

    bool getVoiceMuted(int voiceIndex) const {
        if (voiceIndex < 0 || voiceIndex >= 18) {
            return false;
        }
        const int chip = voiceIndex / 9;
        const int channel = voiceIndex % 9;
        return channelMuted_[chip][channel];
    }

private:
    static constexpr int kChipCount = 2;
    static constexpr int kChannelCountPerChip = 9;
    static constexpr int kOpTable[kChannelCountPerChip] = { 0, 1, 2, 8, 9, 10, 16, 17, 18 };

    struct RegMapping {
        bool valid = false;
        int channel = 0;
        int slot = 0;
    };

    static RegMapping mapTotalLevelRegister(int reg) {
        for (int channel = 0; channel < kChannelCountPerChip; ++channel) {
            const int op = kOpTable[channel];
            if (reg == (0x40 + op)) {
                return { true, channel, 0 };
            }
            if (reg == (0x43 + op)) {
                return { true, channel, 1 };
            }
        }
        return {};
    }

    static int forceMuteTotalLevel(int rawValue) {
        return (rawValue & 0xC0) | 0x3F;
    }

    void applyChannelTotalLevel(int chip, int channel) {
        if (!backend_ ||
            chip < 0 || chip >= kChipCount ||
            channel < 0 || channel >= kChannelCountPerChip) {
            return;
        }
        const int previousChip = backend_->getchip();
        backend_->setchip(chip);

        const int op = kOpTable[channel];
        for (int slot = 0; slot < 2; ++slot) {
            const int raw = rawTotalLevel_[chip][channel][slot];
            const int reg = (slot == 0) ? (0x40 + op) : (0x43 + op);
            const int effective = channelMuted_[chip][channel] ? forceMuteTotalLevel(raw) : raw;
            backend_->write(reg, effective);
        }

        backend_->setchip(previousChip);
    }

    explicit TrackingOplProxy(
            std::unique_ptr<Copl> backend,
            bool promoteDualOnSecondChip,
            bool mirrorMonoWhenSingleChip,
            Engine engine)
        : backend_(std::move(backend)),
          promoteDualOnSecondChip_(promoteDualOnSecondChip),
          mirrorMonoWhenSingleChip_(mirrorMonoWhenSingleChip),
          engine_(engine) {
        if (backend_) {
            currChip = backend_->getchip();
            currType = backend_->gettype();
            if (auto* emuOpl = dynamic_cast<CEmuopl*>(backend_.get())) {
                emuOpl->settype(TYPE_OPL2);
                currType = TYPE_OPL2;
            }
        }
    }

    std::unique_ptr<Copl> backend_;
    Engine engine_ = Engine::Mame;
    bool promoteDualOnSecondChip_ = false;
    bool mirrorMonoWhenSingleChip_ = false;
    bool dualChipActive_ = false;
    int rawTotalLevel_[kChipCount][kChannelCountPerChip][2] {};
    bool channelMuted_[kChipCount][kChannelCountPerChip] {};
};

int normalizeAdlibCore(int value) {
    if (value < 0 || value > 3) {
        return 2;
    }
    return value;
}

std::string safeString(const std::string& value) {
    return value;
}
}

AdPlugDecoder::AdPlugDecoder()
    : channelScopeState(std::make_shared<ChannelScopeSharedState>()) {}

AdPlugDecoder::~AdPlugDecoder() {
    close();
}

bool AdPlugDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();

    if (!path) {
        return false;
    }

    sourcePath = path;
    const auto engine = static_cast<TrackingOplProxy::Engine>(normalizeAdlibCore(adlibCore));
    opl = TrackingOplProxy::create(engine, sampleRateHz);
    player.reset(CAdPlug::factory(sourcePath, opl.get()));
    if (!player) {
        LOGE("CAdPlug::factory failed for file: %s", sourcePath.c_str());
        closeInternalLocked();
        return false;
    }
    player->setEndlessLoopMode(repeatMode.load() == 2);

    title = safeString(player->gettitle());
    if (title.empty()) {
        title = std::filesystem::path(sourcePath).stem().string();
    }
    artist = safeString(player->getauthor());
    composer = artist;
    genre = safeString(player->gettype());

    subtuneCount = std::max(1u, player->getsubsongs());
    currentSubtuneIndex = std::clamp(static_cast<int>(player->getsubsong()), 0, subtuneCount - 1);

    const unsigned long durationMs = player->songlength(currentSubtuneIndex);
    durationReliable = durationMs > 0;
    durationSeconds = durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;

    remainingTickFrames = 0;
    playbackPositionSeconds = 0.0;
    reachedEnd = false;
    pcmScratch.clear();
    syncToggleChannelsLocked();
    applyToggleMutesLocked();
    return true;
}

void AdPlugDecoder::closeInternalLocked() {
    player.reset();
    opl.reset();
    pcmScratch.clear();
    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    remainingTickFrames = 0;
    durationReliable = false;
    durationSeconds = 0.0;
    playbackPositionSeconds = 0.0;
    reachedEnd = false;
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
}

void AdPlugDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

int AdPlugDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);

    if (!player || !opl || !buffer || numFrames <= 0) {
        return 0;
    }
    syncToggleChannelsLocked();

    const int mode = repeatMode.load();
    const bool hasReliableDuration = durationSeconds >= 1.0;
    const bool gateVirtualEof =
            (mode == 0) ||
            (mode == 3) ||
            (mode == 1 && std::max(1, subtuneCount) > 1);

    if (hasReliableDuration && mode != 2 && playbackPositionSeconds >= durationSeconds) {
        if (mode == 1 && std::max(1, subtuneCount) == 1) {
            // Repeat Track on single-subtune files: restart at duration boundary.
            player->rewind(currentSubtuneIndex);
            playbackPositionSeconds = 0.0;
            remainingTickFrames = 0;
            reachedEnd = false;
        } else if (gateVirtualEof) {
            return 0;
        }
    }

    auto refreshToTickFrames = [this]() {
        const float refreshHz = player->getrefresh();
        const double safeRefreshHz =
                (std::isfinite(refreshHz) && refreshHz > 0.0f) ? static_cast<double>(refreshHz) : 70.0;
        return std::max(
                1,
                static_cast<int>(std::lround(static_cast<double>(sampleRateHz) / safeRefreshHz))
        );
    };

    int framesWritten = 0;
    int loopRecoveries = 0;
    constexpr int kMaxLoopRecoveriesPerRead = 512;

    while (framesWritten < numFrames) {
        if (remainingTickFrames <= 0) {
            const bool hasNextTick = player->update();
            if (!hasNextTick) {
                if (mode == 1 || mode == 2) {
                    bool recovered = false;
                    // Some AdPlug players expect rewind() with default subsong and do not
                    // reliably recover from rewind(0) after terminal update()==false.
                    for (int attempt = 0; attempt < 2 && !recovered; ++attempt) {
                        if (attempt == 0) {
                            player->rewind(currentSubtuneIndex);
                        } else {
                            player->rewind();
                        }
                        remainingTickFrames = 0;
                        reachedEnd = false;
                        if (mode == 1) {
                            // Repeat Track restarts timeline at track start.
                            playbackPositionSeconds = 0.0;
                        }

                        if (player->update()) {
                            remainingTickFrames = refreshToTickFrames();
                            recovered = true;
                            loopRecoveries = 0;
                        }
                    }

                    if (recovered) {
                        continue;
                    }

                    if (loopRecoveries < kMaxLoopRecoveriesPerRead) {
                        ++loopRecoveries;
                        continue;
                    }
                }
                reachedEnd = true;
                break;
            }
            loopRecoveries = 0;
            remainingTickFrames = refreshToTickFrames();
        }

        const int framesLeft = numFrames - framesWritten;
        const int chunkFrames = std::min(framesLeft, remainingTickFrames);
        const int chunkSamples = chunkFrames * channels;

        if (static_cast<int>(pcmScratch.size()) < chunkSamples) {
            pcmScratch.resize(chunkSamples);
        }

        if (channelScopeState && scopeScratch.size() < chunkFrames * 18) {
            scopeScratch.resize(chunkFrames * 18);
        }

        if (channelScopeState) {
            std::fill(scopeScratch.begin(), scopeScratch.end(), 0);
        }

        opl->update_and_scope(pcmScratch.data(), chunkFrames, channelScopeState ? scopeScratch.data() : nullptr);
        if (channelScopeState) {
            captureScopeSnapshotLocked(chunkFrames);
        }
        for (int sample = 0; sample < chunkSamples; ++sample) {
            buffer[(framesWritten * channels) + sample] =
                    static_cast<float>(pcmScratch[sample]) / 32768.0f;
        }

        remainingTickFrames -= chunkFrames;
        framesWritten += chunkFrames;
        playbackPositionSeconds += static_cast<double>(chunkFrames) / static_cast<double>(sampleRateHz);
    }

    return framesWritten;
}

void AdPlugDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return;
    }

    double targetSeconds = std::max(0.0, seconds);
    if (durationSeconds > 0.0) {
        targetSeconds = std::min(targetSeconds, durationSeconds);
    }
    const auto targetMs = static_cast<unsigned long>(std::llround(targetSeconds * 1000.0));
    player->seek(targetMs);
    playbackPositionSeconds = targetSeconds;
    remainingTickFrames = 0;
    reachedEnd = false;
}

double AdPlugDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationSeconds;
}

int AdPlugDecoder::getSampleRate() {
    return sampleRateHz;
}

int AdPlugDecoder::getBitDepth() {
    return bitDepth;
}

std::string AdPlugDecoder::getBitDepthLabel() {
    return "16 bit";
}

int AdPlugDecoder::getDisplayChannelCount() {
    return channels;
}

int AdPlugDecoder::getChannelCount() {
    return channels;
}

int AdPlugDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return subtuneCount;
}

int AdPlugDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return currentSubtuneIndex;
}

bool AdPlugDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || index < 0 || index >= subtuneCount) {
        return false;
    }
    player->rewind(index);
    currentSubtuneIndex = index;
    remainingTickFrames = 0;
    playbackPositionSeconds = 0.0;
    reachedEnd = false;

    const unsigned long durationMs = player->songlength(currentSubtuneIndex);
    durationReliable = durationMs > 0;
    durationSeconds = durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;
    return true;
}

std::string AdPlugDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) {
        return "";
    }
    return title;
}

std::string AdPlugDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) {
        return "";
    }
    return artist;
}

double AdPlugDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || index < 0 || index >= subtuneCount) {
        return 0.0;
    }
    const unsigned long durationMs = player->songlength(index);
    return durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;
}

std::string AdPlugDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string AdPlugDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string AdPlugDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string AdPlugDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string AdPlugDecoder::getDescription() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return "";
    }
    return safeString(player->getdesc());
}

int AdPlugDecoder::getPatternCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getpatterns());
}

int AdPlugDecoder::getCurrentPatternInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getpattern());
}

int AdPlugDecoder::getOrderCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getorders());
}

int AdPlugDecoder::getCurrentOrderInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getorder());
}

int AdPlugDecoder::getCurrentRowInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getrow());
}

int AdPlugDecoder::getCurrentSpeedInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getspeed());
}

int AdPlugDecoder::getInstrumentCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getinstruments());
}

std::string AdPlugDecoder::getInstrumentNamesInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return "";
    }
    const int instrumentCount = static_cast<int>(player->getinstruments());
    if (instrumentCount <= 0) {
        return "";
    }

    std::string names;
    names.reserve(static_cast<size_t>(instrumentCount) * 10);
    for (int i = 0; i < instrumentCount; ++i) {
        const std::string instrumentName = safeString(player->getinstrument(static_cast<unsigned int>(i)));
        if (instrumentName.empty()) {
            continue;
        }
        if (!names.empty()) {
            names += "\n";
        }
        names += std::to_string(i + 1);
        names += ". ";
        names += instrumentName;
    }
    return names;
}

std::vector<std::string> AdPlugDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    return toggleChannelNames;
}

std::vector<uint8_t> AdPlugDecoder::getToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    return std::vector<uint8_t>(toggleChannelNames.size(), 1u);
}

void AdPlugDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyToggleMutesLocked();
}

bool AdPlugDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void AdPlugDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (toggleChannelMuted.empty()) {
        return;
    }
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyToggleMutesLocked();
}

void AdPlugDecoder::syncToggleChannelsLocked() {
    auto* trackingOpl = dynamic_cast<TrackingOplProxy*>(opl.get());
    const int voiceCount = trackingOpl ? trackingOpl->getVoiceCount() : 9;

    if (voiceCount == static_cast<int>(toggleChannelNames.size()) &&
        voiceCount == static_cast<int>(toggleChannelMuted.size())) {
        return;
    }

    std::vector<bool> previousMuted = toggleChannelMuted;
    toggleChannelNames.clear();
    toggleChannelNames.reserve(static_cast<size_t>(voiceCount));
    toggleChannelMuted.assign(static_cast<size_t>(voiceCount), false);

    for (int i = 0; i < voiceCount; ++i) {
        if (voiceCount > 9) {
            const int chipIndex = i / 9;
            const int chipChannel = (i % 9) + 1;
            const char chipLabel = static_cast<char>('A' + chipIndex);
            toggleChannelNames.push_back(
                    "Chip " + std::string(1, chipLabel) + " Ch " + std::to_string(chipChannel)
            );
        } else {
            toggleChannelNames.push_back("OPL Ch " + std::to_string(i + 1));
        }
        if (i < static_cast<int>(previousMuted.size())) {
            toggleChannelMuted[static_cast<size_t>(i)] = previousMuted[static_cast<size_t>(i)];
        }
    }

    applyToggleMutesLocked();
}

void AdPlugDecoder::applyToggleMutesLocked() {
    auto* trackingOpl = dynamic_cast<TrackingOplProxy*>(opl.get());
    if (!trackingOpl) {
        return;
    }
    for (int i = 0; i < static_cast<int>(toggleChannelMuted.size()); ++i) {
        trackingOpl->setVoiceMuted(i, toggleChannelMuted[static_cast<size_t>(i)]);
    }
}

void AdPlugDecoder::setRepeatMode(int mode) {
    const int normalized = (mode >= 0 && mode <= 3) ? mode : 0;
    repeatMode.store(normalized);
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (player) {
        player->setEndlessLoopMode(normalized == 2);
    }
}

int AdPlugDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int AdPlugDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int capabilities = PLAYBACK_CAP_SEEK | PLAYBACK_CAP_LIVE_REPEAT_MODE | PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    if (durationReliable) {
        capabilities |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    // Seek is supported but is decode-forward (not direct/random access).
    return capabilities;
}

void AdPlugDecoder::setOutputSampleRate(int sampleRate) {
    const int normalized = (sampleRate > 0) ? std::clamp(sampleRate, 8000, 192000) : 44100;
    std::lock_guard<std::mutex> lock(decodeMutex);
    sampleRateHz = normalized;
}

void AdPlugDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) {
        return;
    }
    if (std::strcmp(name, "adplug.opl_engine") != 0) {
        return;
    }

    const int parsed = std::atoi(value);
    std::lock_guard<std::mutex> lock(decodeMutex);
    adlibCore = normalizeAdlibCore(parsed);
}

int AdPlugDecoder::getOptionApplyPolicy(const char* name) const {
    if (!name) {
        return OPTION_APPLY_LIVE;
    }
    if (std::strcmp(name, "adplug.opl_engine") == 0) {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    return OPTION_APPLY_LIVE;
}

int AdPlugDecoder::getFixedSampleRateHz() const {
    return 0;
}

double AdPlugDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (durationSeconds > 0.0 && repeatMode.load() != 2) {
        return std::min(playbackPositionSeconds, durationSeconds);
    }
    return playbackPositionSeconds;
}

AudioDecoder::TimelineMode AdPlugDecoder::getTimelineMode() const {
    return TimelineMode::ContinuousLinear;
}

std::string AdPlugDecoder::getCoreStringInfo(const char* name) {
    if (name == nullptr) return "";
    if (std::strcmp(name, "description") == 0) return getDescription();
    if (std::strcmp(name, "instrumentNames") == 0) return getInstrumentNamesInfo();
    return "";
}

int AdPlugDecoder::getCoreIntInfo(const char* name, int fallback) {
    if (name == nullptr) return fallback;
    if (std::strcmp(name, "patternCount") == 0) return getPatternCountInfo();
    if (std::strcmp(name, "currentPattern") == 0) return getCurrentPatternInfo();
    if (std::strcmp(name, "orderCount") == 0) return getOrderCountInfo();
    if (std::strcmp(name, "currentOrder") == 0) return getCurrentOrderInfo();
    if (std::strcmp(name, "currentRow") == 0) return getCurrentRowInfo();
    if (std::strcmp(name, "currentSpeed") == 0) return getCurrentSpeedInfo();
    if (std::strcmp(name, "instrumentCount") == 0) return getInstrumentCountInfo();
    return fallback;
}

std::vector<std::string> AdPlugDecoder::getSupportedExtensions() {
    // Do not touch CAdPlug::players during process startup:
    // decoder registration runs in global init, and AdPlug's own global
    // player list can still be in static-initialization-order limbo there.
    // Keep this list aligned with external/adplug/src/adplug.cpp (allplayers).
    static const std::vector<std::string> kExtensions = {
            "hsc", "sng", "imf", "wlf", "adlib", "a2m", "a2t", "xms",
            "bam", "cmf", "adl", "d00", "dfm", "hsp", "ksm", "mad",
            "mus", "mdy", "ims", "mdi", "mid", "sci", "laa", "mkj",
            "cff", "dmo", "s3m", "dtm", "mtk", "mtr", "rad", "rac",
            "raw", "sat", "sa2", "xad", "lds", "plx", "m", "rol",
            "xsm", "dro", "pis", "msc", "rix", "mkf", "jbm", "got",
            "vgm", "vgz", "sop", "hsq", "sqx", "sdb", "agd", "ha2"
    };
    return kExtensions;
}

std::shared_ptr<ChannelScopeSharedState> AdPlugDecoder::getChannelScopeSharedState() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return channelScopeState;
}

std::vector<int32_t> AdPlugDecoder::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (scopeRingChannels <= 0 || scopeRingSamples <= 0 || scopeRingRaw.empty()) {
        return {};
    }

    const int channelsToExport = std::min(scopeRingChannels, std::clamp(maxChannels, 1, 18));
    std::vector<int32_t> flat(static_cast<size_t>(channelsToExport * 10), -1);
    const int trailingSamples = std::clamp(sampleRateHz > 0 ? sampleRateHz / 50 : 64, 64, 1024);
    const int recentSamples = std::min(scopeRingSamples, trailingSamples);

    for (int channel = 0; channel < channelsToExport; ++channel) {
        float recentPeak = 0.0f;
        for (int i = 0; i < recentSamples; ++i) {
            const int ringIndex =
                    (scopeRingWritePos - recentSamples + i + ChannelScopeSharedState::kMaxSamples) %
                    ChannelScopeSharedState::kMaxSamples;
            recentPeak = std::max(
                    recentPeak,
                    std::abs(scopeRingRaw[
                            static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                            static_cast<size_t>(ringIndex)
                    ])
            );
        }

        const size_t base = static_cast<size_t>(channel * 10);
        int flags = 0;
        if (recentPeak > 0.0015f) {
            flags |= 1; // kCrsidChannelScopeTextFlagActive
        }

        flat[base + 0] = channel;
        flat[base + 1] = -1;
        flat[base + 2] = std::clamp(static_cast<int>(std::lround(recentPeak * 64.0f)), 0, 64);
        flat[base + 3] = 0;
        flat[base + 4] = -1;
        flat[base + 5] = 0;
        flat[base + 6] = -1;
        flat[base + 7] = -1;
        flat[base + 8] = -1;
        flat[base + 9] = flags;
    }
    return flat;
}

void AdPlugDecoder::captureScopeSnapshotLocked(int numFrames) {
    if (!channelScopeState || !opl) {
        return;
    }
    auto* trackingProxy = dynamic_cast<TrackingOplProxy*>(opl.get());
    const int activeChannels = trackingProxy ? trackingProxy->getVoiceCount() : 18;

    if (scopeRingChannels != activeChannels || scopeRingRaw.size() != static_cast<size_t>(activeChannels * ChannelScopeSharedState::kMaxSamples)) {
        scopeRingRaw.assign(static_cast<size_t>(activeChannels * ChannelScopeSharedState::kMaxSamples), 0.0f);
        scopeRingChannels = activeChannels;
        scopeRingWritePos = 0;
        scopeRingSamples = 0;
    }

    const bool isInterleaved = trackingProxy && trackingProxy->isScopeInterleaved();

    if (isInterleaved) {
        // Nuked OPL: interleaved format with 18 values per frame
        for (int frame = 0; frame < numFrames; ++frame) {
            for (int ch = 0; ch < activeChannels; ++ch) {
                scopeRingRaw[
                    static_cast<size_t>(ch) * ChannelScopeSharedState::kMaxSamples +
                    static_cast<size_t>(scopeRingWritePos)
                ] = std::clamp(static_cast<float>(scopeScratch[frame * 18 + ch]) / 32768.0f, -1.0f, 1.0f);
            }
            scopeRingWritePos = (scopeRingWritePos + 1) % ChannelScopeSharedState::kMaxSamples;
            scopeRingSamples = std::min(scopeRingSamples + 1, ChannelScopeSharedState::kMaxSamples);
        }
    } else {
        // Planar engines (Mame, DosBox, KenSilverman): channels in sequence
        for (int frame = 0; frame < numFrames; ++frame) {
            const int dstIndex = (scopeRingWritePos + frame) % ChannelScopeSharedState::kMaxSamples;
            for (int ch = 0; ch < activeChannels; ++ch) {
                const int srcIndex = ch * numFrames + frame;
                scopeRingRaw[
                    static_cast<size_t>(ch) * ChannelScopeSharedState::kMaxSamples +
                    static_cast<size_t>(dstIndex)
                ] = std::clamp(static_cast<float>(scopeScratch[srcIndex]) / 32768.0f, -1.0f, 1.0f);
            }
        }
        scopeRingWritePos = (scopeRingWritePos + numFrames) % ChannelScopeSharedState::kMaxSamples;
        scopeRingSamples = std::min(scopeRingSamples + numFrames, ChannelScopeSharedState::kMaxSamples);
    }

    if (scopeRingChannels <= 0 || scopeRingRaw.empty() || scopeRingSamples <= 0) {
        channelScopeState->clear();
        return;
    }

    std::vector<float> raw(
            static_cast<size_t>(scopeRingChannels) * ChannelScopeSharedState::kMaxSamples,
            0.0f
    );
    std::vector<float> vu(static_cast<size_t>(scopeRingChannels), 0.0f);
    const int filledSamples = std::clamp(scopeRingSamples, 0, ChannelScopeSharedState::kMaxSamples);
    const int zeroPrefix = ChannelScopeSharedState::kMaxSamples - filledSamples;
    const int trailingSamples = std::clamp(sampleRateHz > 0 ? sampleRateHz / 50 : 64, 64, 1024);

    for (int channel = 0; channel < scopeRingChannels; ++channel) {
        float* dst = raw.data() + static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples;
        for (int i = 0; i < filledSamples; ++i) {
            const int ringIndex =
                    (scopeRingWritePos - filledSamples + i + ChannelScopeSharedState::kMaxSamples) %
                    ChannelScopeSharedState::kMaxSamples;
            dst[zeroPrefix + i] = scopeRingRaw[
                    static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                    static_cast<size_t>(ringIndex)
            ];
        }

        float peak = 0.0f;
        const int start = std::max(0, ChannelScopeSharedState::kMaxSamples - trailingSamples);
        for (int i = start; i < ChannelScopeSharedState::kMaxSamples; ++i) {
            peak = std::max(peak, std::abs(dst[i]));
        }
        vu[static_cast<size_t>(channel)] = std::clamp(peak, 0.0f, 1.0f);
    }

    static uint64_t channelScopeSourceSerial = 0;
    std::lock_guard<std::mutex> channelScopeLock(channelScopeState->mutex);
    channelScopeState->snapshotRaw = std::move(raw);
    channelScopeState->snapshotVu = std::move(vu);
    channelScopeState->snapshotChannels = scopeRingChannels;
    channelScopeState->snapshotSerial = ++channelScopeSourceSerial;
}
