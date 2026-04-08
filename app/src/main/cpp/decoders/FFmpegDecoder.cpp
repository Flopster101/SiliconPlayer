#include "FFmpegDecoder.h"
#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <sstream>
#include <vector>
#include <mutex>
#include <optional>
#include <cstdlib>
#include <set>
#include <cstdio>
#include <strings.h>
#include <libavutil/error.h>

#define LOG_TAG "FFmpegDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern bool openSmbAvioHandleForNative(const std::string& requestUri, int64_t* outHandleId);
extern int readSmbAvioHandleForNative(int64_t handleId, int64_t offset, uint8_t* buffer, int length);
extern int64_t getSmbAvioHandleSizeForNative(int64_t handleId);
extern void closeSmbAvioHandleForNative(int64_t handleId);

namespace {
std::once_flag gFfmpegNetworkInitOnce;
constexpr int kSmbAvioBufferSize = 64 * 1024;

std::string ffErrString(int errnum) {
    char errbuf[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(errnum, errbuf, sizeof(errbuf));
    return std::string(errbuf);
}

void ensureFfmpegNetworkInitialized() {
    std::call_once(gFfmpegNetworkInitOnce, []() {
        const int result = avformat_network_init();
        if (result < 0) {
            LOGE("avformat_network_init failed: %d", result);
        } else {
            LOGD("FFmpeg network initialized");
        }
        const char* httpProto = avio_find_protocol_name("http://example.com");
        const char* httpsProto = avio_find_protocol_name("https://example.com");
        const char* tlsProto = avio_find_protocol_name("tls://example.com");
        LOGD(
            "FFmpeg protocol support http=%s https=%s tls=%s",
            httpProto ? "yes" : "no",
            httpsProto ? "yes" : "no",
            tlsProto ? "yes" : "no"
        );
    });
}

std::string getMetadataValue(AVDictionary* metadata, const char* key) {
    if (!metadata || !key) {
        return "";
    }
    AVDictionaryEntry* entry = av_dict_get(metadata, key, nullptr, 0);
    if (!entry || !entry->value) {
        return "";
    }
    return entry->value;
}

std::string getFirstMetadataValue(AVDictionary* metadata, const std::initializer_list<const char*>& keys) {
    for (const char* key : keys) {
        std::string value = getMetadataValue(metadata, key);
        if (!value.empty()) {
            return value;
        }
    }
    return "";
}

std::string toLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

std::string trimAscii(std::string value) {
    auto isSpace = [](unsigned char c) { return std::isspace(c) != 0; };
    value.erase(value.begin(), std::find_if(value.begin(), value.end(), [&](char c) {
        return !isSpace(static_cast<unsigned char>(c));
    }));
    value.erase(std::find_if(value.rbegin(), value.rend(), [&](char c) {
        return !isSpace(static_cast<unsigned char>(c));
    }).base(), value.end());
    return value;
}

std::vector<std::string> splitExtensionsCsv(const char* csv) {
    std::vector<std::string> result;
    if (!csv || *csv == '\0') {
        return result;
    }

    std::stringstream ss(csv);
    std::string ext;
    while (std::getline(ss, ext, ',')) {
        ext = trimAscii(toLowerAscii(ext));
        if (!ext.empty()) {
            result.push_back(ext);
        }
    }
    return result;
}

bool parseBoolString(const std::string& value, bool fallback) {
    const std::string normalized = toLowerAscii(trimAscii(value));
    if (normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on") {
        return true;
    }
    if (normalized == "0" || normalized == "false" || normalized == "no" || normalized == "off") {
        return false;
    }
    return fallback;
}

std::optional<double> parseDoubleStrict(const std::string& raw) {
    if (raw.empty()) return std::nullopt;
    char* end = nullptr;
    const double value = std::strtod(raw.c_str(), &end);
    if (end == raw.c_str() || (end != nullptr && *end != '\0') || !std::isfinite(value)) {
        return std::nullopt;
    }
    return value;
}

std::optional<double> parseLoopValueSeconds(
        const std::string& rawValue,
        int sampleRate,
        double durationSeconds) {
    std::string value = trimAscii(rawValue);
    if (value.empty()) {
        return std::nullopt;
    }

    std::string lower = toLowerAscii(value);
    bool explicitMilliseconds = false;
    bool explicitSeconds = false;
    if (lower.size() > 2 && lower.rfind("ms") == (lower.size() - 2)) {
        explicitMilliseconds = true;
        lower.resize(lower.size() - 2);
    } else if (!lower.empty() && lower.back() == 's') {
        explicitSeconds = true;
        lower.pop_back();
    }
    lower = trimAscii(lower);

    const auto parsed = parseDoubleStrict(lower);
    if (!parsed.has_value()) {
        return std::nullopt;
    }
    const double numeric = parsed.value();
    if (numeric < 0.0) {
        return std::nullopt;
    }

    if (explicitMilliseconds) {
        return numeric / 1000.0;
    }
    if (explicitSeconds) {
        return numeric;
    }

    // Bare numeric loop tags are commonly sample positions.
    if (sampleRate > 0) {
        if (durationSeconds > 0.0) {
            if (numeric > durationSeconds * 2.0) {
                return numeric / static_cast<double>(sampleRate);
            }
            return numeric;
        }
        if (numeric > static_cast<double>(sampleRate) * 2.0) {
            return numeric / static_cast<double>(sampleRate);
        }
    }
    return numeric;
}

std::string getMetadataValueLoopAware(
        AVDictionary* metadata,
        const std::initializer_list<const char*>& keys) {
    if (!metadata) return "";
    AVDictionaryEntry* entry = nullptr;
    while ((entry = av_dict_get(metadata, "", entry, AV_DICT_IGNORE_SUFFIX)) != nullptr) {
        if (!entry->key || !entry->value) continue;
        const std::string loweredKey = toLowerAscii(entry->key);
        for (const char* wanted : keys) {
            if (loweredKey == wanted) {
                return entry->value;
            }
        }
    }
    return "";
}

bool isSmbRequestPath(const char* path) {
    if (path == nullptr) {
        return false;
    }
    constexpr char kPrefix[] = "smb://";
    return strncasecmp(path, kPrefix, sizeof(kPrefix) - 1) == 0;
}
}

FFmpegDecoder::FFmpegDecoder() {
    packet = av_packet_alloc();
    frame = av_frame_alloc();
}

FFmpegDecoder::~FFmpegDecoder() {
    close();
    if (packet) av_packet_free(&packet);
    if (frame) av_frame_free(&frame);
}

bool FFmpegDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    close(); // close if already open
    decoderDrainStarted = false;
    return openLocked(path);
}

bool FFmpegDecoder::openLocked(const char* path) {
    ensureFfmpegNetworkInitialized();

    usingSmbCustomIo = isSmbRequestPath(path);
    if (usingSmbCustomIo) {
        if (!openSmbCustomIoLocked(path)) {
            close();
            return false;
        }
    } else {
        const int openResult = avformat_open_input(&formatContext, path, nullptr, nullptr);
        if (openResult != 0) {
            char errbuf[AV_ERROR_MAX_STRING_SIZE] = {0};
            av_strerror(openResult, errbuf, sizeof(errbuf));
            LOGE("Failed to open file: %s (fferr=%d msg=%s)", path, openResult, errbuf);
            close();
            return false;
        }
    }

    // Find stream info
    if (avformat_find_stream_info(formatContext, nullptr) < 0) {
        LOGE("Failed to find stream info");
        close();
        return false;
    }

    // Find audio stream
    audioStreamIndex = -1;
    for (unsigned int i = 0; i < formatContext->nb_streams; i++) {
        if (formatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audioStreamIndex = i;
            break;
        }
    }

    if (audioStreamIndex == -1) {
        LOGE("No audio stream found");
        close();
        return false;
    }

    AVCodecParameters* codecParams = formatContext->streams[audioStreamIndex]->codecpar;
    const AVCodec* codec = avcodec_find_decoder(codecParams->codec_id);
    if (!codec) {
        LOGE("Decoder not found for codec ID: %d", codecParams->codec_id);
        close();
        return false;
    }

    codecContext = avcodec_alloc_context3(codec);
    if (!codecContext) {
        LOGE("Failed to allocate codec context");
        close();
        return false;
    }

    if (avcodec_parameters_to_context(codecContext, codecParams) < 0) {
        LOGE("Failed to copy codec params to context");
        close();
        return false;
    }

    if (avcodec_open2(codecContext, codec, nullptr) < 0) {
        LOGE("Failed to open codec");
        close();
        return false;
    }

    sourceSampleRate = codecParams->sample_rate > 0 ? codecParams->sample_rate : codecContext->sample_rate;
    sourceChannelCount = codecContext->ch_layout.nb_channels > 0
            ? codecContext->ch_layout.nb_channels
            : codecParams->ch_layout.nb_channels;
    if (sourceChannelCount <= 0) {
        sourceChannelCount = outputChannelCount;
    }
    sourceBitDepth = codecParams->bits_per_raw_sample;
    if (sourceBitDepth <= 0) {
        sourceBitDepth = codecParams->bits_per_coded_sample;
    }
    if (sourceBitDepth <= 0) {
        int bytesPerSample = av_get_bytes_per_sample(codecContext->sample_fmt);
        sourceBitDepth = bytesPerSample > 0 ? bytesPerSample * 8 : 0;
    }
    codecName = codec && codec->name ? codec->name : avcodec_get_name(codecParams->codec_id);
    if (codec && codec->long_name) {
        codecName += " (";
        codecName += codec->long_name;
        codecName += ")";
    }
    containerName = (formatContext->iformat && formatContext->iformat->long_name)
            ? formatContext->iformat->long_name
            : ((formatContext->iformat && formatContext->iformat->name) ? formatContext->iformat->name : "");
    const char* sampleFmt = av_get_sample_fmt_name(codecContext->sample_fmt);
    sampleFormatName = sampleFmt ? sampleFmt : "";
    char chLayoutBuf[128] = {0};
    if (av_channel_layout_describe(&codecContext->ch_layout, chLayoutBuf, sizeof(chLayoutBuf)) > 0) {
        channelLayoutName = chLayoutBuf;
    } else {
        channelLayoutName.clear();
    }
    rebuildToggleChannelsLocked();

    if (!initResampler()) {
        LOGE("Failed to initialize resampler");
        close();
        return false;
    }

    if (formatContext->duration != AV_NOPTS_VALUE) {
        duration = (double)formatContext->duration / AV_TIME_BASE;
    } else {
        duration = 0.0;
    }

    title = getFirstMetadataValue(formatContext->metadata, {"title"});
    artist = getFirstMetadataValue(formatContext->metadata, {"artist", "album_artist", "author", "composer"});
    composer = getFirstMetadataValue(formatContext->metadata, {"composer", "author"});
    genre = getFirstMetadataValue(formatContext->metadata, {"genre"});
    album = getFirstMetadataValue(formatContext->metadata, {"album"});
    date = getFirstMetadataValue(formatContext->metadata, {"date"});
    year = getFirstMetadataValue(formatContext->metadata, {"year"});
    copyrightText = getFirstMetadataValue(formatContext->metadata, {"copyright"});
    comment = getFirstMetadataValue(formatContext->metadata, {"comment", "description"});
    encoderName = getFirstMetadataValue(formatContext->metadata, {"encoder", "encoded_by"});
    if (title.empty() || artist.empty() || composer.empty() || genre.empty() ||
        album.empty() || date.empty() || year.empty() || copyrightText.empty() || comment.empty() || encoderName.empty()) {
        AVDictionary* streamMetadata = formatContext->streams[audioStreamIndex]->metadata;
        if (title.empty()) {
            title = getFirstMetadataValue(streamMetadata, {"title"});
        }
        if (artist.empty()) {
            artist = getFirstMetadataValue(streamMetadata, {"artist", "album_artist", "author", "composer"});
        }
        if (composer.empty()) {
            composer = getFirstMetadataValue(streamMetadata, {"composer", "author"});
        }
        if (genre.empty()) {
            genre = getFirstMetadataValue(streamMetadata, {"genre"});
        }
        if (album.empty()) {
            album = getFirstMetadataValue(streamMetadata, {"album"});
        }
        if (date.empty()) {
            date = getFirstMetadataValue(streamMetadata, {"date"});
        }
        if (year.empty()) {
            year = getFirstMetadataValue(streamMetadata, {"year"});
        }
        if (copyrightText.empty()) {
            copyrightText = getFirstMetadataValue(streamMetadata, {"copyright"});
        }
        if (comment.empty()) {
            comment = getFirstMetadataValue(streamMetadata, {"comment", "description"});
        }
        if (encoderName.empty()) {
            encoderName = getFirstMetadataValue(streamMetadata, {"encoder", "encoded_by"});
        }
    }

    if (year.empty() && !date.empty()) {
        if (date.size() >= 4) {
            const std::string yearCandidate = date.substr(0, 4);
            if (std::all_of(yearCandidate.begin(), yearCandidate.end(), ::isdigit)) {
                year = yearCandidate;
            }
        }
    }

    // Loop point metadata for sampled formats (LOOPSTART/LOOPEND/LOOPLENGTH style tags).
    auto readLoopTag = [&](const std::initializer_list<const char*>& keys) -> std::string {
        std::string value = getMetadataValueLoopAware(formatContext->metadata, keys);
        if (!value.empty()) return value;
        if (audioStreamIndex >= 0 && audioStreamIndex < static_cast<int>(formatContext->nb_streams)) {
            return getMetadataValueLoopAware(formatContext->streams[audioStreamIndex]->metadata, keys);
        }
        return "";
    };
    const auto parsedLoopStart = parseLoopValueSeconds(
            readLoopTag({"loopstart", "loop_start", "loop-start", "loop start"}),
            sourceSampleRate,
            duration
    );
    const auto parsedLoopEnd = parseLoopValueSeconds(
            readLoopTag({"loopend", "loop_end", "loop-end", "loop end"}),
            sourceSampleRate,
            duration
    );
    const auto parsedLoopLength = parseLoopValueSeconds(
            readLoopTag({"looplength", "loop_length", "loop-length", "loop length", "looplen"}),
            sourceSampleRate,
            duration
    );
    hasLoopPoint = false;
    loopStartSeconds = 0.0;
    loopEndSeconds = 0.0;
    if (parsedLoopStart.has_value() || parsedLoopEnd.has_value() || parsedLoopLength.has_value()) {
        double start = parsedLoopStart.value_or(0.0);
        double end = parsedLoopEnd.value_or(-1.0);
        if (end <= start && parsedLoopLength.has_value()) {
            end = start + parsedLoopLength.value();
        }
        if (duration > 0.0) {
            start = std::clamp(start, 0.0, duration);
            end = std::clamp(end, 0.0, duration);
        }
        if (end > start + 0.01) {
            hasLoopPoint = true;
            loopStartSeconds = start;
            loopEndSeconds = end;
            LOGD(
                    "FFmpeg loop point detected: start=%.3f end=%.3f duration=%.3f",
                    loopStartSeconds,
                    loopEndSeconds,
                    duration
            );
        }
    }

    // Extract bitrate information
    bitrate = codecParams->bit_rate;
    if (bitrate <= 0 && formatContext->bit_rate > 0) {
        bitrate = formatContext->bit_rate;
    }

    // Detect VBR: bitrate is 0 or max_rate differs from bit_rate
    vbr = (bitrate == 0) || (codecContext->rc_max_rate > 0 && codecContext->rc_max_rate != bitrate);

    totalFramesOutput = 0;
    openedPath = path ? path : "";
    LOGD("Opened file: %s, duration: %.2f", path, duration);
    return true;
}

void FFmpegDecoder::close() {
    // Note: Mutex should be locked by caller if needed, or this called from destructor/open which locks
    freeResampler();

    if (codecContext) {
        avcodec_free_context(&codecContext);
        codecContext = nullptr;
    }
    if (formatContext) {
        avformat_close_input(&formatContext);
        formatContext = nullptr;
    }
    closeSmbCustomIoLocked();
    sampleBuffer.clear();
    sampleBufferCursor = 0;
    decoderDrainStarted = false;
    duration = 0.0;
    sourceSampleRate = 0;
    sourceChannelCount = 0;
    sourceBitDepth = 0;
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    album.clear();
    year.clear();
    date.clear();
    copyrightText.clear();
    comment.clear();
    codecName.clear();
    containerName.clear();
    sampleFormatName.clear();
    channelLayoutName.clear();
    encoderName.clear();
    bitrate = 0;
    vbr = false;
    repeatMode = 0;
    hasLoopPoint = false;
    loopStartSeconds = 0.0;
    loopEndSeconds = 0.0;
    gaplessRepeatTrack = false;
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    openedPath.clear();
}

bool FFmpegDecoder::openSmbCustomIoLocked(const char* path) {
    if (path == nullptr) {
        return false;
    }

    formatContext = avformat_alloc_context();
    if (formatContext == nullptr) {
        LOGE("Failed to allocate format context for SMB AVIO");
        return false;
    }

    if (!openSmbAvioHandleForNative(path, &smbAvioHandleId)) {
        LOGE("Failed to open SMB AVIO handle for %s", path);
        return false;
    }

    smbAvioPosition = 0;
    avioBuffer = static_cast<uint8_t*>(av_malloc(kSmbAvioBufferSize));
    if (avioBuffer == nullptr) {
        LOGE("Failed to allocate SMB AVIO buffer");
        return false;
    }

    avioContext = avio_alloc_context(
            avioBuffer,
            kSmbAvioBufferSize,
            0,
            this,
            &FFmpegDecoder::readPacketCallback,
            nullptr,
            &FFmpegDecoder::seekCallback
    );
    if (avioContext == nullptr) {
        LOGE("Failed to allocate SMB AVIO context");
        return false;
    }
    avioContext->seekable = AVIO_SEEKABLE_NORMAL;
    avioContext->direct = 1;

    formatContext->pb = avioContext;
    formatContext->flags |= AVFMT_FLAG_CUSTOM_IO;
    const int openResult = avformat_open_input(&formatContext, nullptr, nullptr, nullptr);
    if (openResult < 0) {
        LOGE(
                "Failed to open SMB AVIO input: %s (fferr=%d msg=%s)",
                path,
                openResult,
                ffErrString(openResult).c_str()
        );
        return false;
    }

    LOGD("Opened SMB AVIO input: %s", path);
    return true;
}

void FFmpegDecoder::closeSmbCustomIoLocked() {
    if (formatContext != nullptr && formatContext->pb == avioContext) {
        formatContext->pb = nullptr;
    }
    if (avioContext != nullptr) {
        if (avioContext->buffer != nullptr) {
            av_freep(&avioContext->buffer);
        }
        avio_context_free(&avioContext);
    }
    avioBuffer = nullptr;
    if (smbAvioHandleId > 0) {
        closeSmbAvioHandleForNative(smbAvioHandleId);
        smbAvioHandleId = 0;
    }
    smbAvioPosition = 0;
    usingSmbCustomIo = false;
}

int FFmpegDecoder::readPacketCallback(void* opaque, uint8_t* buffer, int bufferSize) {
    auto* decoder = static_cast<FFmpegDecoder*>(opaque);
    if (decoder == nullptr || decoder->smbAvioHandleId <= 0 || buffer == nullptr || bufferSize <= 0) {
        return AVERROR(EINVAL);
    }

    const int bytesRead = readSmbAvioHandleForNative(
            decoder->smbAvioHandleId,
            decoder->smbAvioPosition,
            buffer,
            bufferSize
    );
    if (bytesRead < 0) {
        return AVERROR(EIO);
    }
    if (bytesRead == 0) {
        return AVERROR_EOF;
    }
    decoder->smbAvioPosition += bytesRead;
    return bytesRead;
}

int64_t FFmpegDecoder::currentSmbLogicalPositionLocked() const {
    if (avioContext == nullptr) {
        return smbAvioPosition;
    }
    const int64_t unreadBytes = std::max<int64_t>(0, avioContext->buf_end - avioContext->buf_ptr);
    return std::max<int64_t>(0, smbAvioPosition - unreadBytes);
}

int64_t FFmpegDecoder::seekCallback(void* opaque, int64_t offset, int whence) {
    auto* decoder = static_cast<FFmpegDecoder*>(opaque);
    if (decoder == nullptr || decoder->smbAvioHandleId <= 0) {
        return AVERROR(EINVAL);
    }

    if ((whence & AVSEEK_SIZE) == AVSEEK_SIZE) {
        return getSmbAvioHandleSizeForNative(decoder->smbAvioHandleId);
    }

    const int baseWhence = whence & ~AVSEEK_FORCE;
    const int64_t sizeBytes = getSmbAvioHandleSizeForNative(decoder->smbAvioHandleId);
    if (sizeBytes < 0) {
        return AVERROR(EIO);
    }

    int64_t base = 0;
    switch (baseWhence) {
        case SEEK_SET:
            base = 0;
            break;
        case SEEK_CUR:
            base = decoder->currentSmbLogicalPositionLocked();
            break;
        case SEEK_END:
            base = sizeBytes;
            break;
        default:
            return AVERROR(EINVAL);
    }

    const int64_t target = base + offset;
    if (target < 0) {
        return AVERROR(EINVAL);
    }
    const int64_t clampedTarget = std::min(target, sizeBytes);
    decoder->smbAvioPosition = clampedTarget;
    return clampedTarget;
}

bool FFmpegDecoder::initResampler() {
    // Build and initialize a new resampler first. Only swap it in on success.
    SwrContext* newSwrContext = nullptr;

    // Calculate channel layout from channel count logic since layout might be 0 for some files.
    AVChannelLayout out_ch_layout{};
    av_channel_layout_default(&out_ch_layout, outputChannelCount);

    AVChannelLayout in_ch_layout{};
    // Use ch_layout (new FFmpeg standard) if available, otherwise fallback to default
    if (codecContext->ch_layout.nb_channels > 0) {
        av_channel_layout_copy(&in_ch_layout, &codecContext->ch_layout);
    } else {
         av_channel_layout_default(&in_ch_layout, std::max(1, sourceChannelCount));
    }

    int result = swr_alloc_set_opts2(
        &newSwrContext,
        &out_ch_layout,
        AV_SAMPLE_FMT_FLT, // Output format: Float Interleaved (Packed)
        outputSampleRate,
        &in_ch_layout,
        codecContext->sample_fmt,
        codecContext->sample_rate,
        0, nullptr
    );

    if (result < 0) {
         av_channel_layout_uninit(&out_ch_layout);
         av_channel_layout_uninit(&in_ch_layout);
         if (newSwrContext) {
             swr_free(&newSwrContext);
         }
         LOGE("swr_alloc_set_opts2 failed");
         return false;
    }

    const int inChannelCount = std::max(0, in_ch_layout.nb_channels);
    const int outChannelCount = std::max(0, out_ch_layout.nb_channels);

    const bool hasMutedSourceChannels = std::any_of(
            toggleChannelMuted.begin(),
            toggleChannelMuted.end(),
            [](bool muted) { return muted; }
    );
    if (hasMutedSourceChannels && inChannelCount > 0 && outChannelCount > 0) {
        std::vector<double> matrix(
                static_cast<size_t>(inChannelCount) * static_cast<size_t>(outChannelCount),
                0.0
        );
        const int stride = inChannelCount;
        const bool useDeterministicMultichannelMuteMatrix = inChannelCount > outChannelCount;
        if (useDeterministicMultichannelMuteMatrix) {
            // For multichannel->fewer-channel output with per-channel mutes enabled, use a
            // stable index-mapped matrix so muting one input does not rebalance unrelated inputs.
            std::vector<int> outputLoad(static_cast<size_t>(outChannelCount), 0);
            for (int inputChannel = 0; inputChannel < inChannelCount; ++inputChannel) {
                const int mappedOutput = inputChannel % outChannelCount;
                outputLoad[static_cast<size_t>(mappedOutput)]++;
            }
            for (int inputChannel = 0; inputChannel < inChannelCount; ++inputChannel) {
                const int mappedOutput = inputChannel % outChannelCount;
                const int load = outputLoad[static_cast<size_t>(mappedOutput)];
                const double gain = load > 0 ? (1.0 / static_cast<double>(load)) : 0.0;
                matrix[inputChannel + stride * mappedOutput] = gain;
            }
            result = 0;
        } else {
            result = swr_build_matrix2(
                    &in_ch_layout,
                    &out_ch_layout,
                    1.0,
                    1.0,
                    1.0,
                    1.0,
                    1.0,
                    matrix.data(),
                    stride,
                    AV_MATRIX_ENCODING_NONE,
                    nullptr
            );
            if (result < 0) {
                LOGD("swr_build_matrix2 failed; using index-mapped fallback matrix");
                std::vector<int> outputLoad(static_cast<size_t>(outChannelCount), 0);
                for (int inputChannel = 0; inputChannel < inChannelCount; ++inputChannel) {
                    const int mappedOutput = inputChannel % outChannelCount;
                    outputLoad[static_cast<size_t>(mappedOutput)]++;
                }
                for (int inputChannel = 0; inputChannel < inChannelCount; ++inputChannel) {
                    const int mappedOutput = inputChannel % outChannelCount;
                    const int load = outputLoad[static_cast<size_t>(mappedOutput)];
                    const double gain = load > 0 ? (1.0 / static_cast<double>(load)) : 0.0;
                    matrix[inputChannel + stride * mappedOutput] = gain;
                }
                result = 0;
            }
        }
        if (result >= 0) {
            for (int inputChannel = 0; inputChannel < inChannelCount; ++inputChannel) {
                if (inputChannel >= static_cast<int>(toggleChannelMuted.size()) ||
                    !toggleChannelMuted[static_cast<size_t>(inputChannel)]) {
                    continue;
                }
                for (int outputChannel = 0; outputChannel < outChannelCount; ++outputChannel) {
                    matrix[inputChannel + stride * outputChannel] = 0.0;
                }
            }
            result = swr_set_matrix(newSwrContext, matrix.data(), stride);
        }
    }

    av_channel_layout_uninit(&out_ch_layout);
    av_channel_layout_uninit(&in_ch_layout);

    if (result < 0 || swr_init(newSwrContext) < 0) {
         if (newSwrContext) {
             swr_free(&newSwrContext);
         }
         LOGE("swr_init failed");
         return false;
    }
    if (swrContext) {
        swr_free(&swrContext);
    }
    swrContext = newSwrContext;
    return true;
}

void FFmpegDecoder::freeResampler() {
    if (swrContext) {
        swr_free(&swrContext);
        swrContext = nullptr;
    }
}

int FFmpegDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!formatContext || !codecContext) return 0;

    int framesRead = 0;
    while (framesRead < numFrames) {
        // 1. Consume existing buffer
        if (sampleBufferCursor < sampleBuffer.size()) {
            int available = (sampleBuffer.size() - sampleBufferCursor) / outputChannelCount;
            int toCopy = std::min(numFrames - framesRead, available);

            // buffer is interleaved: L R L R
            // sampleBuffer is also interleaved FLT
            memcpy(buffer + (framesRead * outputChannelCount),
                   sampleBuffer.data() + sampleBufferCursor,
                   toCopy * outputChannelCount * sizeof(float));

            framesRead += toCopy;
            sampleBufferCursor += toCopy * outputChannelCount;

            // Reset buffer if empty
            if (sampleBufferCursor >= sampleBuffer.size()) {
                sampleBuffer.clear();
                sampleBufferCursor = 0;
            }
        }

        // 2. If we still need frames, decode more
        if (framesRead < numFrames) {
            int ret = decodeFrame();
            if (ret < 0) {
                // EOF or error. In loop-point mode, wrap to tagged loop start.
                if (repeatMode == 2 && hasLoopPoint) {
                    if (!seekInternalLocked(loopStartSeconds)) {
                        break;
                    }
                    continue;
                }
                // For regular repeat-track mode, optionally seek internally and continue
                // filling this request without returning an EOF boundary gap.
                if (repeatMode == 1 && gaplessRepeatTrack) {
                    if (!seekInternalLocked(0.0)) {
                        break;
                    }
                    continue;
                }
                break;
            }
        }
    }
    // Track total frames output for position calculation
    totalFramesOutput += framesRead;
    return framesRead;
}

int FFmpegDecoder::decodeFrame() {
    if (!swrContext || outputChannelCount <= 0) {
        return -1;
    }

    int ret;
    while (true) {
        // Try receive frame first
        ret = avcodec_receive_frame(codecContext, frame);
        if (ret == 0) {
             // Frame received, proceed to resample
             int dst_nb_samples = av_rescale_rnd(swr_get_delay(swrContext, codecContext->sample_rate) +
                                    frame->nb_samples, outputSampleRate, codecContext->sample_rate, AV_ROUND_UP);
             if (dst_nb_samples <= 0) {
                 continue;
             }

             // We need a temporary buffer for the resampler output
             // swr_convert requires pointers to pointers for planar data, but for packed float (AV_SAMPLE_FMT_FLT)
             // it usually expects a single array in data[0].

             // Intermediate buffer for resampler output
             // swr_convert expects specific data pointer layout
             std::vector<float> resampledLocal(dst_nb_samples * outputChannelCount);
             uint8_t* out_data[1] = { (uint8_t*)resampledLocal.data() };

             int converted_samples = swr_convert(swrContext, out_data, dst_nb_samples, (const uint8_t**)frame->data, frame->nb_samples);
             if (converted_samples < 0) {
                 LOGE("swr_convert failed: fferr=%d msg=%s", converted_samples, ffErrString(converted_samples).c_str());
                 return -1;
             }

             if (converted_samples > 0) {
                  // Append to main buffer
                  size_t oldSize = sampleBuffer.size();
                  sampleBuffer.resize(oldSize + converted_samples * outputChannelCount);
                  memcpy(sampleBuffer.data() + oldSize, resampledLocal.data(), converted_samples * outputChannelCount * sizeof(float));
                  return 0; // Success
             }
             continue;
        } else if (ret == AVERROR(EAGAIN)) {
             // Need more input unless we've already started draining.
             if (decoderDrainStarted) {
                 return -1;
             }
        } else if (ret == AVERROR_EOF) {
             return -1; // EOF
        } else {
             LOGE("Error decoding frame: %d", ret);
             return -1;
        }

        // Read packet
        const int readFrameResult = av_read_frame(formatContext, packet);
        if (readFrameResult < 0) {
             // No more packets: flush buffered decoder frames once.
             if (!decoderDrainStarted) {
                 decoderDrainStarted = true;
                 const int flushRet = avcodec_send_packet(codecContext, nullptr);
                 if (flushRet < 0 && flushRet != AVERROR_EOF && flushRet != AVERROR(EAGAIN)) {
                     LOGE("Error starting decoder drain: fferr=%d msg=%s", flushRet, ffErrString(flushRet).c_str());
                 }
                 continue;
             }
             return -1;
        }

        if (packet->stream_index == audioStreamIndex) {
            const int sendRet = avcodec_send_packet(codecContext, packet);
            if (sendRet == AVERROR(EAGAIN)) {
                // Decoder output queue is full; consume frames on next loop iteration.
                av_packet_unref(packet);
                continue;
            }
            if (sendRet == AVERROR_INVALIDDATA) {
                // Corrupt packet; skip it and continue decoding.
                LOGE("Skipping invalid packet: fferr=%d msg=%s", sendRet, ffErrString(sendRet).c_str());
                av_packet_unref(packet);
                continue;
            }
            if (sendRet < 0) {
                LOGE("Error sending packet to decoder: fferr=%d msg=%s", sendRet, ffErrString(sendRet).c_str());
                av_packet_unref(packet);
                continue;
            }
        }
        av_packet_unref(packet);
    }
}

bool FFmpegDecoder::performSeekWithinCurrentContextLocked(double seconds) {
    if (!formatContext || !codecContext) return false;

    const double clamped = std::max(0.0, seconds);
    const int64_t targetTimestamp = static_cast<int64_t>(clamped * AV_TIME_BASE);
    if (packet != nullptr) {
        av_packet_unref(packet);
    }
    if (frame != nullptr) {
        av_frame_unref(frame);
    }

    int seekResult = AVERROR(ENOSYS);
    if (smbAvioHandleId > 0) {
        seekResult = avformat_seek_file(
                formatContext,
                -1,
                INT64_MIN,
                targetTimestamp,
                INT64_MAX,
                0
        );
    }
    if (seekResult < 0) {
        seekResult = av_seek_frame(formatContext, -1, targetTimestamp, AVSEEK_FLAG_BACKWARD);
    }
    if (seekResult < 0 && audioStreamIndex >= 0 && audioStreamIndex < static_cast<int>(formatContext->nb_streams)) {
        AVStream* stream = formatContext->streams[audioStreamIndex];
        if (stream) {
            const int64_t streamTimestamp = av_rescale_q(
                    static_cast<int64_t>(clamped * AV_TIME_BASE),
                    AV_TIME_BASE_Q,
                    stream->time_base
            );
            if (smbAvioHandleId > 0) {
                seekResult = avformat_seek_file(
                        formatContext,
                        audioStreamIndex,
                        INT64_MIN,
                        streamTimestamp,
                        INT64_MAX,
                        AVSEEK_FLAG_BACKWARD
                );
            }
            if (seekResult < 0) {
                seekResult = av_seek_frame(formatContext, audioStreamIndex, streamTimestamp, AVSEEK_FLAG_BACKWARD);
            }
        }
    }
    if (seekResult < 0) {
        LOGE("FFmpeg seek failed: fferr=%d msg=%s", seekResult, ffErrString(seekResult).c_str());
        return false;
    }

    avformat_flush(formatContext);
    avcodec_flush_buffers(codecContext);
    if (packet != nullptr) {
        av_packet_unref(packet);
    }
    if (frame != nullptr) {
        av_frame_unref(frame);
    }
    if (avioContext != nullptr) {
        avioContext->eof_reached = 0;
        avioContext->error = 0;
        avioContext->buf_ptr = avioContext->buffer;
        avioContext->buf_end = avioContext->buffer;
        avioContext->pos = smbAvioPosition;
    }
    sampleBuffer.clear();
    sampleBufferCursor = 0;
    decoderDrainStarted = false;
    // Reset frame counter to match seek position.
    if (outputSampleRate > 0) {
        totalFramesOutput = static_cast<int64_t>(clamped * outputSampleRate);
    } else {
        totalFramesOutput = 0;
    }
    return true;
}

bool FFmpegDecoder::reopenSmbContextForSeekLocked(double seconds) {
    if (openedPath.empty()) {
        return false;
    }

    const std::string reopenPath = openedPath;
    const int preservedRepeatMode = repeatMode;
    const bool preservedGaplessRepeatTrack = gaplessRepeatTrack;

    close();
    decoderDrainStarted = false;
    if (!openLocked(reopenPath.c_str())) {
        return false;
    }

    repeatMode = preservedRepeatMode;
    gaplessRepeatTrack = preservedGaplessRepeatTrack;

    if (seconds <= 0.0) {
        totalFramesOutput = 0;
        return true;
    }

    return performSeekWithinCurrentContextLocked(seconds);
}

bool FFmpegDecoder::seekInternalLocked(double seconds) {
    if (smbAvioHandleId > 0) {
        if (performSeekWithinCurrentContextLocked(seconds)) {
            return true;
        }
        return reopenSmbContextForSeekLocked(seconds);
    }
    return performSeekWithinCurrentContextLocked(seconds);
}

void FFmpegDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    seekInternalLocked(seconds);
}

double FFmpegDecoder::getDuration() {
    return duration;
}

int FFmpegDecoder::getSampleRate() {
    return outputSampleRate;
}

int FFmpegDecoder::getBitDepth() {
    return sourceBitDepth;
}

std::string FFmpegDecoder::getBitDepthLabel() {
    if (sourceBitDepth > 0) {
        std::ostringstream ss;
        ss << sourceBitDepth << "-bit";
        return ss.str();
    }
    return "Unknown";
}

int FFmpegDecoder::getChannelCount() {
    return outputChannelCount;
}

int FFmpegDecoder::getDisplayChannelCount() {
    return sourceChannelCount > 0 ? sourceChannelCount : outputChannelCount;
}

std::vector<std::string> FFmpegDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return toggleChannelNames;
}

void FFmpegDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!codecContext) return;
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    const size_t target = static_cast<size_t>(channelIndex);
    const bool previous = toggleChannelMuted[target];
    if (previous == enabled) {
        return;
    }
    toggleChannelMuted[target] = enabled;
    if (!initResampler()) {
        toggleChannelMuted[target] = previous;
        return;
    }
    sampleBuffer.clear();
    sampleBufferCursor = 0;
}

bool FFmpegDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!codecContext) return false;
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void FFmpegDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!codecContext) return;
    const std::vector<bool> previous = toggleChannelMuted;
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    if (!initResampler()) {
        toggleChannelMuted = previous;
        return;
    }
    sampleBuffer.clear();
    sampleBufferCursor = 0;
}

std::string FFmpegDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string FFmpegDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string FFmpegDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string FFmpegDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string FFmpegDecoder::getAlbum() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return album;
}

std::string FFmpegDecoder::getYear() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return year;
}

std::string FFmpegDecoder::getDate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return date;
}

std::string FFmpegDecoder::getCopyright() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return copyrightText;
}

std::string FFmpegDecoder::getComment() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return comment;
}

void FFmpegDecoder::setOutputSampleRate(int sampleRate) {
    if (sampleRate <= 0) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (outputSampleRate == sampleRate) return;
    // Preserve timeline continuity when render sample rate changes.
    // totalFramesOutput is tracked in units of outputSampleRate.
    if (outputSampleRate > 0 && totalFramesOutput > 0) {
        const double seconds = static_cast<double>(totalFramesOutput) / outputSampleRate;
        totalFramesOutput = static_cast<int64_t>(seconds * sampleRate);
    } else {
        totalFramesOutput = 0;
    }
    outputSampleRate = sampleRate;
    if (codecContext) {
        initResampler();
        sampleBuffer.clear();
        sampleBufferCursor = 0;
    }
}

void FFmpegDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = mode;
}

void FFmpegDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) {
        return;
    }
    std::lock_guard<std::mutex> lock(decodeMutex);
    const std::string optionName(name);
    if (optionName == "ffmpeg.gapless_repeat_track") {
        gaplessRepeatTrack = parseBoolString(value, gaplessRepeatTrack);
    }
}

int FFmpegDecoder::getOptionApplyPolicy(const char* name) const {
    if (!name) {
        return OPTION_APPLY_LIVE;
    }
    const std::string optionName(name);
    if (optionName == "ffmpeg.gapless_repeat_track") {
        return OPTION_APPLY_LIVE;
    }
    return OPTION_APPLY_LIVE;
}

int FFmpegDecoder::getRepeatModeCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int capabilities = REPEAT_CAP_TRACK;
    if (hasLoopPoint) {
        capabilities |= REPEAT_CAP_LOOP_POINT;
    }
    return capabilities;
}

int FFmpegDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int capabilities = PLAYBACK_CAP_SEEK |
            PLAYBACK_CAP_RELIABLE_DURATION |
            PLAYBACK_CAP_LIVE_REPEAT_MODE |
            PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
            PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE;
    if (usingSmbCustomIo) {
        capabilities |= PLAYBACK_CAP_ASYNC_DIRECT_SEEK;
    } else {
        capabilities |= PLAYBACK_CAP_DIRECT_SEEK;
    }
    return capabilities;
}

double FFmpegDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    // Calculate position from total frames output divided by sample rate
    if (outputSampleRate > 0) {
        return static_cast<double>(totalFramesOutput) / outputSampleRate;
    }
    return 0.0;
}

std::vector<std::string> FFmpegDecoder::getSupportedExtensions() {
    static std::vector<std::string> cached;
    static std::once_flag once;

    std::call_once(once, []() {
        std::set<std::string> dedup;
        void* opaque = nullptr;
        const AVInputFormat* iformat = nullptr;

        while ((iformat = av_demuxer_iterate(&opaque)) != nullptr) {
            if (!iformat->extensions || *iformat->extensions == '\0') {
                continue;
            }

            const auto extensions = splitExtensionsCsv(iformat->extensions);
            if (extensions.empty()) {
                continue;
            }

            for (const auto& ext : extensions) {
                dedup.insert(ext);
            }
        }

        // Keep formats reachable when enabled demuxers lack .p.extensions metadata.
        // This list is intentionally "missing-only" and sourced from FFmpeg metadata audit.
        static const std::initializer_list<const char*> kEssentialAudioExtensions = {
                "aif", "aiff", "afc", "aifc",
                "amr",
                "asf", "wmv", "wma",
                "caf",
                "dsf",
                "opus",
                "qcp",
                "voc",
                "w64", "wav",
                "wv",
                "xwma"
        };
        for (const char* ext : kEssentialAudioExtensions) {
            dedup.insert(ext);
        }

        cached.assign(dedup.begin(), dedup.end());
        LOGD("FFmpeg demuxer extension discovery: %zu extensions", cached.size());
    });

    return cached;
}

int64_t FFmpegDecoder::getBitrate() const {
    return bitrate;
}

bool FFmpegDecoder::isVBR() const {
    return vbr;
}

std::string FFmpegDecoder::getCodecName() const {
    return codecName;
}

std::string FFmpegDecoder::getContainerName() const {
    return containerName;
}

std::string FFmpegDecoder::getSampleFormatName() const {
    return sampleFormatName;
}

std::string FFmpegDecoder::getChannelLayoutName() const {
    return channelLayoutName;
}

std::string FFmpegDecoder::getEncoderName() const {
    return encoderName;
}

void FFmpegDecoder::rebuildToggleChannelsLocked() {
    const int totalChannels = std::clamp(sourceChannelCount, 0, 64);
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    if (totalChannels <= 0) {
        return;
    }
    toggleChannelNames.reserve(static_cast<size_t>(totalChannels));
    toggleChannelMuted.assign(static_cast<size_t>(totalChannels), false);

    const std::string layoutLower = toLowerAscii(channelLayoutName);
    const bool stereoPairLabels =
            (totalChannels % 2 == 0) &&
            (
                    totalChannels == 2 ||
                    layoutLower.find("stereo") != std::string::npos
            );

    if (stereoPairLabels) {
        for (int channel = 0; channel < totalChannels; ++channel) {
            const int pairIndex = (channel / 2) + 1;
            const bool left = (channel % 2) == 0;
            toggleChannelNames.push_back(
                    std::string(left ? "Left " : "Right ") + std::to_string(pairIndex)
            );
        }
        return;
    }

    for (int channel = 0; channel < totalChannels; ++channel) {
        toggleChannelNames.push_back("Ch " + std::to_string(channel + 1));
    }
}
