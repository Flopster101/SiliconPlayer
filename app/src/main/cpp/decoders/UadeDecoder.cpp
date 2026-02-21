#include "UadeDecoder.h"

#include <algorithm>
#include <android/log.h>
#include <cerrno>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <filesystem>
#include <mutex>
#include <unistd.h>

extern "C" {
#include <uade/uade.h>
}

#define LOG_TAG "UadeDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex gRuntimeConfigMutex;
std::string gRuntimeBaseDir;
std::string gUadeCorePath;

std::string safeString(const char* value) {
    return value ? std::string(value) : std::string();
}

int clampSubsong(int subsong, int minSubsong, int maxSubsong) {
    if (minSubsong > maxSubsong) {
        return 0;
    }
    return std::clamp(subsong, minSubsong, maxSubsong);
}

std::string getRuntimeBaseDir() {
    std::lock_guard<std::mutex> lock(gRuntimeConfigMutex);
    return gRuntimeBaseDir;
}

std::string getRuntimeUadeCorePath() {
    std::lock_guard<std::mutex> lock(gRuntimeConfigMutex);
    return gUadeCorePath;
}
}

UadeDecoder::UadeDecoder() = default;

UadeDecoder::~UadeDecoder() {
    close();
}

void UadeDecoder::setRuntimePaths(const std::string& baseDir, const std::string& uadeCorePath) {
    std::lock_guard<std::mutex> lock(gRuntimeConfigMutex);
    gRuntimeBaseDir = baseDir;
    gUadeCorePath = uadeCorePath;
}

bool UadeDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();

    if (!path || path[0] == '\0') {
        return false;
    }

    sourcePath = path;
    uade_config* config = nullptr;
    const std::string runtimeBaseDir = getRuntimeBaseDir();
    const std::string runtimeUadeCorePath = getRuntimeUadeCorePath();
    if (!runtimeBaseDir.empty()) {
        config = uade_new_config();
        if (!config) {
            LOGE("uade_new_config failed; falling back to default config");
        } else {
            const std::string uadeCorePath =
                    !runtimeUadeCorePath.empty() ? runtimeUadeCorePath : (runtimeBaseDir + "/uadecore");
            const std::string scorePath = runtimeBaseDir + "/score";
            const std::string uaercPath = runtimeBaseDir + "/uaerc";
            const int coreExecCheck = access(uadeCorePath.c_str(), X_OK);
            const int coreErrno = (coreExecCheck != 0) ? errno : 0;
            const int scoreReadCheck = access(scorePath.c_str(), R_OK);
            const int scoreErrno = (scoreReadCheck != 0) ? errno : 0;
            const int uaercReadCheck = access(uaercPath.c_str(), R_OK);
            const int uaercErrno = (uaercReadCheck != 0) ? errno : 0;
            uade_config_set_option(config, UC_BASE_DIR, runtimeBaseDir.c_str());
            uade_config_set_option(config, UC_UADECORE_FILE, uadeCorePath.c_str());
            uade_config_set_option(config, UC_SCORE_FILE, scorePath.c_str());
            uade_config_set_option(config, UC_UAE_CONFIG_FILE, uaercPath.c_str());
            if (coreExecCheck != 0 || scoreReadCheck != 0 || uaercReadCheck != 0) {
                LOGE(
                        "UADE runtime assets missing/inaccessible: base=%s core=%s "
                        "(coreX=%d errno=%d:%s scoreR=%d errno=%d:%s uaercR=%d errno=%d:%s)",
                        runtimeBaseDir.c_str(),
                        uadeCorePath.c_str(),
                        coreExecCheck,
                        coreErrno,
                        std::strerror(coreErrno),
                        scoreReadCheck,
                        scoreErrno,
                        std::strerror(scoreErrno),
                        uaercReadCheck,
                        uaercErrno,
                        std::strerror(uaercErrno)
                );
            }
        }
    } else {
        LOGE("UADE runtime base dir is not configured; default compiled paths will be used");
    }

    state = uade_new_state(config);
    if (config) {
        std::free(config);
        config = nullptr;
    }
    if (!state) {
        LOGE("uade_new_state failed");
        return false;
    }

    const int playRc = uade_play(sourcePath.c_str(), -1, state);
    if (playRc != 1) {
        LOGE("uade_play failed for %s (rc=%d)", sourcePath.c_str(), playRc);
        closeInternalLocked();
        return false;
    }

    sampleRateHz = std::max(8000, uade_get_sampling_rate(state));
    channels = 2;
    bitDepth = 16;
    playbackPositionSeconds = 0.0;
    pcmScratch.clear();
    refreshSongInfoLocked();
    return true;
}

void UadeDecoder::closeInternalLocked() {
    if (state) {
        uade_stop(state);
        uade_cleanup_state(state);
        state = nullptr;
    }
    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    sampleRateHz = 44100;
    channels = 2;
    bitDepth = 16;
    subtuneMin = 0;
    subtuneMax = 0;
    currentSubsong = 0;
    durationReliable.store(false);
    durationSeconds = 0.0;
    playbackPositionSeconds = 0.0;
    pcmScratch.clear();
}

void UadeDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

bool UadeDecoder::refreshSongInfoLocked() {
    if (!state) {
        return false;
    }

    const struct uade_song_info* info = uade_get_song_info(state);
    if (!info) {
        return false;
    }

    subtuneMin = info->subsongs.min;
    subtuneMax = info->subsongs.max;
    currentSubsong = clampSubsong(info->subsongs.cur, subtuneMin, subtuneMax);

    title = safeString(info->modulename);
    if (title.empty()) {
        title = std::filesystem::path(sourcePath).stem().string();
    }
    artist = safeString(info->playername);
    composer = artist;
    genre = safeString(info->formatname);

    durationSeconds = info->duration;
    durationReliable.store(durationSeconds > 0.0 && std::isfinite(durationSeconds));

    return true;
}

bool UadeDecoder::restartCurrentSubsongLocked() {
    if (!state || sourcePath.empty()) {
        return false;
    }
    const int restartSubsong = currentSubsong;
    if (uade_stop(state) < 0) {
        return false;
    }
    const int playRc = uade_play(sourcePath.c_str(), restartSubsong, state);
    if (playRc != 1) {
        return false;
    }
    playbackPositionSeconds = 0.0;
    refreshSongInfoLocked();
    return true;
}

int UadeDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);

    if (!state || !buffer || numFrames <= 0) {
        return 0;
    }

    const int requestedBytes = numFrames * UADE_BYTES_PER_FRAME;
    if (requestedBytes <= 0) {
        return 0;
    }

    if (static_cast<int>(pcmScratch.size()) < numFrames * channels) {
        pcmScratch.resize(numFrames * channels);
    }

    ssize_t bytesRead = uade_read(pcmScratch.data(), static_cast<size_t>(requestedBytes), state);
    if (bytesRead < 0) {
        LOGE("uade_read failed");
        return 0;
    }

    if (bytesRead == 0) {
        const int mode = repeatMode.load();
        if ((mode == 1 || mode == 2) && restartCurrentSubsongLocked()) {
            bytesRead = uade_read(pcmScratch.data(), static_cast<size_t>(requestedBytes), state);
            if (bytesRead <= 0) {
                return 0;
            }
        } else {
            return 0;
        }
    }

    const int framesRead = static_cast<int>(bytesRead / UADE_BYTES_PER_FRAME);
    const int samplesRead = framesRead * channels;
    for (int i = 0; i < samplesRead; ++i) {
        buffer[i] = static_cast<float>(pcmScratch[i]) / 32768.0f;
    }

    const double reportedPosition = uade_get_time_position(UADE_SEEK_SUBSONG_RELATIVE, state);
    if (std::isfinite(reportedPosition) && reportedPosition >= 0.0) {
        playbackPositionSeconds = reportedPosition;
    } else {
        playbackPositionSeconds += static_cast<double>(framesRead) / static_cast<double>(sampleRateHz);
    }
    refreshSongInfoLocked();
    return framesRead;
}

void UadeDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);

    if (!state) {
        return;
    }

    const double target = std::max(0.0, seconds);
    if (uade_seek(UADE_SEEK_SUBSONG_RELATIVE, target, -1, state) == 0) {
        playbackPositionSeconds = target;
    }
}

double UadeDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!state) return 0.0;
    refreshSongInfoLocked();
    return durationReliable.load() ? durationSeconds : 0.0;
}

int UadeDecoder::getSampleRate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sampleRateHz;
}

int UadeDecoder::getBitDepth() {
    return bitDepth;
}

std::string UadeDecoder::getBitDepthLabel() {
    return "16-bit";
}

int UadeDecoder::getDisplayChannelCount() {
    return channels;
}

int UadeDecoder::getChannelCount() {
    return channels;
}

int UadeDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (subtuneMax < subtuneMin) return 1;
    return std::max(1, subtuneMax - subtuneMin + 1);
}

int UadeDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return std::max(0, currentSubsong - subtuneMin);
}

bool UadeDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);

    if (!state) return false;
    if (subtuneMax < subtuneMin) return false;
    if (index < 0 || index >= (subtuneMax - subtuneMin + 1)) return false;

    const int targetSubsong = subtuneMin + index;
    if (uade_stop(state) < 0) return false;
    const int playRc = uade_play(sourcePath.c_str(), targetSubsong, state);
    if (playRc != 1) return false;

    playbackPositionSeconds = 0.0;
    refreshSongInfoLocked();
    return true;
}

std::string UadeDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const int count = (subtuneMax >= subtuneMin) ? (subtuneMax - subtuneMin + 1) : 1;
    if (index < 0 || index >= count) return "";
    return title;
}

std::string UadeDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const int count = (subtuneMax >= subtuneMin) ? (subtuneMax - subtuneMin + 1) : 1;
    if (index < 0 || index >= count) return "";
    return artist;
}

double UadeDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    const int count = (subtuneMax >= subtuneMin) ? (subtuneMax - subtuneMin + 1) : 1;
    if (index < 0 || index >= count) return 0.0;
    return durationReliable.load() ? durationSeconds : 0.0;
}

std::string UadeDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string UadeDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string UadeDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string UadeDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

void UadeDecoder::setRepeatMode(int mode) {
    int normalized = mode;
    if (normalized < 0 || normalized > 3) normalized = 0;
    repeatMode.store(normalized);
}

int UadeDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int UadeDecoder::getPlaybackCapabilities() const {
    int caps = PLAYBACK_CAP_SEEK | PLAYBACK_CAP_LIVE_REPEAT_MODE;
    if (durationReliable.load()) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

double UadeDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!state) return 0.0;
    const double reportedPosition = uade_get_time_position(UADE_SEEK_SUBSONG_RELATIVE, state);
    if (std::isfinite(reportedPosition) && reportedPosition >= 0.0) {
        playbackPositionSeconds = reportedPosition;
    }
    return std::max(0.0, playbackPositionSeconds);
}

std::vector<std::string> UadeDecoder::getSupportedExtensions() {
    static const std::vector<std::string> extensions = {
            "!pm!",
            "40a",
            "40b",
            "41a",
            "50a",
            "60a",
            "61a",
            "aam",
            "abk",
            "ac1",
            "ac1d",
            "adpcm",
            "adsc",
            "agi",
            "ahx",
            "alp",
            "amc",
            "aon",
            "aon4",
            "aon8",
            "aps",
            "arp",
            "ash",
            "ast",
            "aval",
            "avp",
            "bd",
            "bds",
            "bfc",
            "bp",
            "bp3",
            "bsi",
            "bss",
            "bye",
            "chan",
            "cin",
            "cm",
            "core",
            "cp",
            "cplx",
            "crb",
            "cus",
            "cust",
            "custom",
            "dat",
            "db",
            "dh",
            "di",
            "digi",
            "dl",
            "dl_deli",
            "dlm1",
            "dlm2",
            "dln",
            "dm",
            "dm1",
            "dm2",
            "dmu",
            "dmu2",
            "dns",
            "doda",
            "dp",
            "dsc",
            "dsr",
            "dss",
            "dum",
            "dw",
            "dwold",
            "dz",
            "ea",
            "emod",
            "ems",
            "emsv6",
            "eu",
            "ex",
            "fc",
            "fc-bsi",
            "fc-m",
            "fc13",
            "fc14",
            "fc3",
            "fc4",
            "fcm",
            "fp",
            "fred",
            "ft",
            "ftm",
            "fuz",
            "fuzz",
            "fw",
            "glue",
            "gm",
            "gmc",
            "gray",
            "gv",
            "hd",
            "hip",
            "hip7",
            "hipc",
            "hmc",
            "hn",
            "hot",
            "hrt",
            "hrt!",
            "hst",
            "ice",
            "ims",
            "is",
            "is20",
            "ism",
            "it1",
            "jam",
            "jb",
            "jc",
            "jcb",
            "jcbo",
            "jd",
            "jmf",
            "jo",
            "jp",
            "jpn",
            "jpnd",
            "jpo",
            "jpold",
            "js",
            "jt",
            "kef",
            "kef7",
            "kh",
            "kim",
            "kris",
            "krs",
            "ksm",
            "lax",
            "lion",
            "lme",
            "ma",
            "max",
            "mc",
            "mcmd",
            "mcmd_org",
            "mco",
            "mcr",
            "md",
            "mdat",
            "mdst",
            "med",
            "mexxmp",
            "mfp",
            "mg",
            "midi",
            "mk2",
            "mkii",
            "mkiio",
            "ml",
            "mm4",
            "mm8",
            "mmd0",
            "mmd1",
            "mmd2",
            "mmd3",
            "mmdc",
            "mms",
            "mod",
            "mod15",
            "mod15_mst",
            "mod15_st-iv",
            "mod15_ust",
            "mod3",
            "mod_adsc4",
            "mod_comp",
            "mod_doc",
            "mod_flt4",
            "mod_ntk",
            "mod_ntk1",
            "mod_ntk2",
            "mod_ntkamp",
            "mok",
            "mon",
            "mon_old",
            "mosh",
            "mpro",
            "mso",
            "mth",
            "mtp2",
            "mug",
            "mug2",
            "mus",
            "mw",
            "noisepacker2",
            "noisepacker3",
            "np",
            "np1",
            "np2",
            "np3",
            "npp",
            "nr",
            "nru",
            "ntp",
            "ntpk",
            "octamed",
            "okt",
            "okta",
            "oldw",
            "one",
            "osp",
            "oss",
            "p10",
            "p21",
            "p30",
            "p40a",
            "p40b",
            "p41a",
            "p4x",
            "p50a",
            "p5a",
            "p5x",
            "p60",
            "p60a",
            "p61",
            "p61a",
            "p6x",
            "pap",
            "pat",
            "pha",
            "pin",
            "pm",
            "pm0",
            "pm01",
            "pm1",
            "pm10c",
            "pm18a",
            "pm2",
            "pm20",
            "pm4",
            "pm40",
            "pmz",
            "pn",
            "polk",
            "powt",
            "pp10",
            "pp20",
            "pp21",
            "pp30",
            "ppk",
            "pr1",
            "pr2",
            "prom",
            "prt",
            "pru",
            "pru1",
            "pru2",
            "prun",
            "prun1",
            "prun2",
            "ps",
            "psa",
            "psf",
            "pt",
            "ptm",
            "puma",
            "pvp",
            "pwr",
            "pyg",
            "pygm",
            "pygmy",
            "qc",
            "qpa",
            "qts",
            "rh",
            "rho",
            "riff",
            "rj",
            "rjp",
            "rk",
            "rkb",
            "s-c",
            "s7g",
            "sa",
            "sa-p",
            "sa_old",
            "sas",
            "sb",
            "sc",
            "scn",
            "scr",
            "sct",
            "scumm",
            "sdata",
            "sdc",
            "sdr",
            "sfx",
            "sfx13",
            "sfx20",
            "sg",
            "sid",
            "sid1",
            "sid2",
            "sj",
            "sjs",
            "skt",
            "skyt",
            "sm",
            "sm1",
            "sm2",
            "sm3",
            "smn",
            "smod",
            "smpro",
            "smus",
            "sndmon",
            "sng",
            "snk",
            "snt",
            "snt!",
            "snx",
            "soc",
            "sog",
            "sonic",
            "spl",
            "sqt",
            "ss",
            "st",
            "st2",
            "st26",
            "st30",
            "star",
            "stpk",
            "sun",
            "syn",
            "synmod",
            "tcb",
            "tf",
            "tfhd1.5",
            "tfhd7v",
            "tfhdpro",
            "tfmx",
            "tfmx1.5",
            "tfmx7v",
            "tfmxpro",
            "thm",
            "thn",
            "thx",
            "tiny",
            "tits",
            "tme",
            "tmk",
            "tp",
            "tp1",
            "tp2",
            "tp3",
            "tpu",
            "trc",
            "tro",
            "tron",
            "tronic",
            "tw",
            "two",
            "uds",
            "ufo",
            "un2",
            "unic",
            "unic2",
            "vss",
            "wb",
            "wn",
            "xan",
            "xann",
            "ym",
            "ymst",
            "zen",
    };
    return extensions;
}
