#include "FFmpegDecoder.h"
#include <android/log.h>

#define LOG_TAG "FFmpegDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
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

    // Open input
    if (avformat_open_input(&formatContext, path, nullptr, nullptr) != 0) {
        LOGE("Failed to open file: %s", path);
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

    title = getFirstMetadataValue(formatContext->metadata, {"title"});
    artist = getFirstMetadataValue(formatContext->metadata, {"artist", "album_artist", "author", "composer"});
    if (title.empty() || artist.empty()) {
        AVDictionary* streamMetadata = formatContext->streams[audioStreamIndex]->metadata;
        if (title.empty()) {
            title = getFirstMetadataValue(streamMetadata, {"title"});
        }
        if (artist.empty()) {
            artist = getFirstMetadataValue(streamMetadata, {"artist", "album_artist", "author", "composer"});
        }
    }

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
    duration = 0.0;
    title.clear();
    artist.clear();
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
             // Need new packet
        } else if (ret == AVERROR_EOF) {
             return -1; // EOF
        } else {
             LOGE("Error decoding frame: %d", ret);
             return -1;
        }

        // Read packet
        if (av_read_frame(formatContext, packet) < 0) {
             // EOF or error
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
}

double FFmpegDecoder::getDuration() {
    return duration;
}

int FFmpegDecoder::getSampleRate() {
    return outputSampleRate;
}

int FFmpegDecoder::getChannelCount() {
    return outputChannelCount;
}

std::string FFmpegDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string FFmpegDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
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
