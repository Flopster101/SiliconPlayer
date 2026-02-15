#include "FFmpegDecoder.h"
#include <android/log.h>
#include <sstream>
#include <vector>
#include <mutex>
#include <libavutil/error.h>

#define LOG_TAG "FFmpegDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
std::once_flag gFfmpegNetworkInitOnce;

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
    ensureFfmpegNetworkInitialized();

    // Open input
    const int openResult = avformat_open_input(&formatContext, path, nullptr, nullptr);
    if (openResult != 0) {
        char errbuf[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(openResult, errbuf, sizeof(errbuf));
        LOGE("Failed to open file: %s (fferr=%d msg=%s)", path, openResult, errbuf);
        return false;
    }

    // Find stream info
    if (avformat_find_stream_info(formatContext, nullptr) < 0) {
        LOGE("Failed to find stream info");
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
        return false;
    }

    AVCodecParameters* codecParams = formatContext->streams[audioStreamIndex]->codecpar;
    const AVCodec* codec = avcodec_find_decoder(codecParams->codec_id);
    if (!codec) {
        LOGE("Decoder not found for codec ID: %d", codecParams->codec_id);
        return false;
    }

    codecContext = avcodec_alloc_context3(codec);
    if (!codecContext) {
        LOGE("Failed to allocate codec context");
        return false;
    }

    if (avcodec_parameters_to_context(codecContext, codecParams) < 0) {
        LOGE("Failed to copy codec params to context");
        return false;
    }

    if (avcodec_open2(codecContext, codec, nullptr) < 0) {
        LOGE("Failed to open codec");
        return false;
    }

    if (!initResampler()) {
        LOGE("Failed to initialize resampler");
        return false;
    }

    if (formatContext->duration != AV_NOPTS_VALUE) {
        duration = (double)formatContext->duration / AV_TIME_BASE;
    } else {
        duration = 0.0;
    }

    sourceSampleRate = codecParams->sample_rate > 0 ? codecParams->sample_rate : codecContext->sample_rate;
    sourceChannelCount = codecParams->ch_layout.nb_channels > 0
            ? codecParams->ch_layout.nb_channels
            : codecContext->ch_layout.nb_channels;
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

    title = getFirstMetadataValue(formatContext->metadata, {"title"});
    artist = getFirstMetadataValue(formatContext->metadata, {"artist", "album_artist", "author", "composer"});
    composer = getFirstMetadataValue(formatContext->metadata, {"composer", "author"});
    genre = getFirstMetadataValue(formatContext->metadata, {"genre"});
    encoderName = getFirstMetadataValue(formatContext->metadata, {"encoder", "encoded_by"});
    if (title.empty() || artist.empty()) {
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
        if (encoderName.empty()) {
            encoderName = getFirstMetadataValue(streamMetadata, {"encoder", "encoded_by"});
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
    codecName.clear();
    containerName.clear();
    sampleFormatName.clear();
    channelLayoutName.clear();
    encoderName.clear();
    bitrate = 0;
    vbr = false;
}

bool FFmpegDecoder::initResampler() {
    if (swrContext) swr_free(&swrContext);

    // Calculate channel layout from channel count logic since layout might be 0 for some files
    AVChannelLayout out_ch_layout;
    av_channel_layout_default(&out_ch_layout, outputChannelCount);

    AVChannelLayout in_ch_layout;
    // Use ch_layout (new FFmpeg standard) if available, otherwise fallback to default
    if (codecContext->ch_layout.nb_channels > 0) {
        av_channel_layout_copy(&in_ch_layout, &codecContext->ch_layout);
    } else {
         av_channel_layout_default(&in_ch_layout, 2);
    }

    int result = swr_alloc_set_opts2(
        &swrContext,
        &out_ch_layout,
        AV_SAMPLE_FMT_FLT, // Output format: Float Interleaved (Packed)
        outputSampleRate,
        &in_ch_layout,
        codecContext->sample_fmt,
        codecContext->sample_rate,
        0, nullptr
    );

    av_channel_layout_uninit(&out_ch_layout);
    av_channel_layout_uninit(&in_ch_layout);

    if (result < 0 || swr_init(swrContext) < 0) {
         LOGE("swr_init failed");
         return false;
    }
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
                 // EOF or Error
                 break;
            }
        }
    }
    // Track total frames output for position calculation
    totalFramesOutput += framesRead;
    return framesRead;
}

int FFmpegDecoder::decodeFrame() {
    int ret;
    while (true) {
        // Try receive frame first
        ret = avcodec_receive_frame(codecContext, frame);
        if (ret == 0) {
             // Frame received, proceed to resample
             int dst_nb_samples = av_rescale_rnd(swr_get_delay(swrContext, codecContext->sample_rate) +
                                    frame->nb_samples, outputSampleRate, codecContext->sample_rate, AV_ROUND_UP);

             // We need a temporary buffer for the resampler output
             // swr_convert requires pointers to pointers for planar data, but for packed float (AV_SAMPLE_FMT_FLT)
             // it usually expects a single array in data[0].

             // Intermediate buffer for resampler output
             // swr_convert expects specific data pointer layout
             std::vector<float> resampledLocal(dst_nb_samples * outputChannelCount);
             uint8_t* out_data[1] = { (uint8_t*)resampledLocal.data() };

             int converted_samples = swr_convert(swrContext, out_data, dst_nb_samples, (const uint8_t**)frame->data, frame->nb_samples);

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
        if (av_read_frame(formatContext, packet) < 0) {
             // No more packets: flush buffered decoder frames once.
             if (!decoderDrainStarted) {
                 decoderDrainStarted = true;
                 avcodec_send_packet(codecContext, nullptr);
                 continue;
             }
             return -1;
        }

        if (packet->stream_index == audioStreamIndex) {
            if (avcodec_send_packet(codecContext, packet) < 0) {
                LOGE("Error sending packet to decoder");
                av_packet_unref(packet);
                return -1;
            }
        }
        av_packet_unref(packet);
    }
}

void FFmpegDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!formatContext) return;

    int64_t targetTimestamp = (int64_t)(seconds * AV_TIME_BASE);
    av_seek_frame(formatContext, -1, targetTimestamp, AVSEEK_FLAG_BACKWARD);
    avcodec_flush_buffers(codecContext);
    sampleBuffer.clear();
    sampleBufferCursor = 0;
    decoderDrainStarted = false;
    // Reset frame counter to match seek position
    if (outputSampleRate > 0) {
        totalFramesOutput = static_cast<int64_t>(seconds * outputSampleRate);
    } else {
        totalFramesOutput = 0;
    }
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

double FFmpegDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    // Calculate position from total frames output divided by sample rate
    if (outputSampleRate > 0) {
        return static_cast<double>(totalFramesOutput) / outputSampleRate;
    }
    return 0.0;
}

std::vector<std::string> FFmpegDecoder::getSupportedExtensions() {
    return {
        // Common Audio
        "mp3", "flac", "ogg", "m4a", "wav", "aac", "wma", "opus", "ape", "wv",
        "alac", "mpc", "aiff", "aif", "amr", "awb", "ac3", "dts", "ra", "rm",
        "tta", "shn", "voc", "au", "snd", "oga", "mka", "weba", "caf", "qcp",
        "dsf", "dff", "mlp", "truehd", "mp2", "mp1"
    };
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
