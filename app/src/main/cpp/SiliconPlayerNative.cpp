#include <jni.h>
#include <string>
#include "AudioEngine.h"
#include "decoders/DecoderRegistry.h"
#include <vector>

#include <mutex>
static AudioEngine *audioEngine = nullptr;
static std::mutex engineMutex;

static void ensureEngine() {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (audioEngine == nullptr) {
        audioEngine = new AudioEngine();
    }
}

static jfloatArray toJFloatArray(JNIEnv* env, const std::vector<float>& values) {
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(values.size()));
    if (array == nullptr || values.empty()) {
        return array;
    }
    env->SetFloatArrayRegion(
            array,
            0,
            static_cast<jsize>(values.size()),
            reinterpret_cast<const jfloat*>(values.data())
    );
    return array;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from AAudio C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_startEngine(JNIEnv* env, jobject) {
    ensureEngine();
    audioEngine->start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_stopEngine(JNIEnv* env, jobject) {
    if (audioEngine != nullptr) {
        audioEngine->stop();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_MainActivity_isEnginePlaying(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return JNI_FALSE;
    }
    return audioEngine->isEnginePlaying() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_loadAudio(JNIEnv* env, jobject, jstring path) {
    ensureEngine();
    const char *nativePath = env->GetStringUTFChars(path, 0);
    audioEngine->setUrl(nativePath);
    env->ReleaseStringUTFChars(path, nativePath);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getSupportedExtensions(JNIEnv* env, jobject) {
    std::vector<std::string> extensions = DecoderRegistry::getInstance().getSupportedExtensions();

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(extensions.size(), stringClass, nullptr);

    for (size_t i = 0; i < extensions.size(); ++i) {
        jstring ext = env->NewStringUTF(extensions[i].c_str());
        env->SetObjectArrayElement(result, i, ext);
        env->DeleteLocalRef(ext);
    }

    return result;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getDuration(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return 0.0;
    }
    return audioEngine->getDurationSeconds();
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getPosition(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return 0.0;
    }
    return audioEngine->getPositionSeconds();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_MainActivity_consumeNaturalEndEvent(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return JNI_FALSE;
    }
    return audioEngine->consumeNaturalEndEvent() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_seekTo(JNIEnv* env, jobject, jdouble seconds) {
    if (audioEngine == nullptr) {
        return;
    }
    audioEngine->seekToSeconds(static_cast<double>(seconds));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_setLooping(JNIEnv* env, jobject, jboolean enabled) {
    if (audioEngine == nullptr) {
        return;
    }
    audioEngine->setLooping(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_setRepeatMode(JNIEnv* env, jobject, jint mode) {
    if (audioEngine == nullptr) {
        return;
    }
    audioEngine->setRepeatMode(static_cast<int>(mode));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_setCoreOutputSampleRate(
        JNIEnv* env, jobject, jstring coreName, jint sampleRateHz) {
    ensureEngine();
    const char* nativeCoreName = env->GetStringUTFChars(coreName, 0);
    audioEngine->setCoreOutputSampleRate(nativeCoreName, static_cast<int>(sampleRateHz));
    env->ReleaseStringUTFChars(coreName, nativeCoreName);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_setCoreOption(
        JNIEnv* env, jobject, jstring coreName, jstring optionName, jstring optionValue) {
    ensureEngine();
    const char* nativeCoreName = env->GetStringUTFChars(coreName, 0);
    const char* nativeOptionName = env->GetStringUTFChars(optionName, 0);
    const char* nativeOptionValue = env->GetStringUTFChars(optionValue, 0);
    audioEngine->setCoreOption(nativeCoreName, nativeOptionName, nativeOptionValue);
    env->ReleaseStringUTFChars(optionValue, nativeOptionValue);
    env->ReleaseStringUTFChars(optionName, nativeOptionName);
    env->ReleaseStringUTFChars(coreName, nativeCoreName);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getCoreCapabilities(
        JNIEnv* env, jobject, jstring coreName) {
    ensureEngine();
    const char* nativeCoreName = env->GetStringUTFChars(coreName, 0);
    int caps = audioEngine->getCoreCapabilities(nativeCoreName);
    env->ReleaseStringUTFChars(coreName, nativeCoreName);
    return static_cast<jint>(caps);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getCoreOptionApplyPolicy(
        JNIEnv* env, jobject, jstring coreName, jstring optionName) {
    ensureEngine();
    const char* nativeCoreName = env->GetStringUTFChars(coreName, 0);
    const char* nativeOptionName = env->GetStringUTFChars(optionName, 0);
    const int policy = audioEngine->getCoreOptionApplyPolicy(nativeCoreName, nativeOptionName);
    env->ReleaseStringUTFChars(optionName, nativeOptionName);
    env->ReleaseStringUTFChars(coreName, nativeCoreName);
    return static_cast<jint>(policy);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getCoreFixedSampleRateHz(
        JNIEnv* env, jobject, jstring coreName) {
    ensureEngine();
    const char* nativeCoreName = env->GetStringUTFChars(coreName, 0);
    int hz = audioEngine->getCoreFixedSampleRateHz(nativeCoreName);
    env->ReleaseStringUTFChars(coreName, nativeCoreName);
    return static_cast<jint>(hz);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_setAudioPipelineConfig(
        JNIEnv*,
        jobject,
        jint backendPreference,
        jint performanceMode,
        jint bufferPreset,
        jint resamplerPreference,
        jboolean allowFallback) {
    ensureEngine();
    audioEngine->setAudioPipelineConfig(
            static_cast<int>(backendPreference),
            static_cast<int>(performanceMode),
            static_cast<int>(bufferPreset),
            static_cast<int>(resamplerPreference),
            allowFallback == JNI_TRUE
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_setEndFadeApplyToAllTracks(
        JNIEnv*,
        jobject,
        jboolean enabled) {
    ensureEngine();
    audioEngine->setEndFadeApplyToAllTracks(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_setEndFadeDurationMs(
        JNIEnv*,
        jobject,
        jint durationMs) {
    ensureEngine();
    audioEngine->setEndFadeDurationMs(static_cast<int>(durationMs));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_setEndFadeCurve(
        JNIEnv*,
        jobject,
        jint curve) {
    ensureEngine();
    audioEngine->setEndFadeCurve(static_cast<int>(curve));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getTrackTitle(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    std::string value = audioEngine->getTitle();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getTrackArtist(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    std::string value = audioEngine->getArtist();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getTrackComposer(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    std::string value = audioEngine->getComposer();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getTrackGenre(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    std::string value = audioEngine->getGenre();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getTrackSampleRate(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getSampleRate());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getTrackChannelCount(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getDisplayChannelCount());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getTrackBitDepth(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getBitDepth());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getTrackBitDepthLabel(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("Unknown");
    }
    std::string value = audioEngine->getBitDepthLabel();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getRepeatModeCapabilities(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return 1; // Track repeat support by default.
    }
    return static_cast<jint>(audioEngine->getRepeatModeCapabilities());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getPlaybackCapabilities(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return static_cast<jint>(
                AudioDecoder::PLAYBACK_CAP_SEEK |
                AudioDecoder::PLAYBACK_CAP_RELIABLE_DURATION |
                AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE
        );
    }
    return static_cast<jint>(audioEngine->getPlaybackCapabilities());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getCurrentDecoderName(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    std::string value = audioEngine->getCurrentDecoderName();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getDecoderRenderSampleRateHz(JNIEnv*, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getDecoderRenderSampleRateHz());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_MainActivity_getOutputStreamSampleRateHz(JNIEnv*, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getOutputStreamSampleRateHz());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOpenMptModuleTypeLong(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    std::string value = audioEngine->getOpenMptModuleTypeLong();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOpenMptTracker(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    std::string value = audioEngine->getOpenMptTracker();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOpenMptSongMessage(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    std::string value = audioEngine->getOpenMptSongMessage();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOpenMptOrderCount(JNIEnv*, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getOpenMptOrderCount());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOpenMptPatternCount(JNIEnv*, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getOpenMptPatternCount());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOpenMptInstrumentCount(JNIEnv*, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getOpenMptInstrumentCount());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOpenMptSampleCount(JNIEnv*, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getOpenMptSampleCount());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOpenMptInstrumentNames(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    std::string value = audioEngine->getOpenMptInstrumentNames();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOpenMptSampleNames(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    std::string value = audioEngine->getOpenMptSampleNames();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOpenMptChannelVuLevels(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewFloatArray(0);
    }
    return toJFloatArray(env, audioEngine->getOpenMptChannelVuLevels());
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getChannelScopeSamples(JNIEnv* env, jobject, jint samplesPerChannel) {
    if (audioEngine == nullptr) {
        return env->NewFloatArray(0);
    }
    return toJFloatArray(env, audioEngine->getChannelScopeSamples(static_cast<int>(samplesPerChannel)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVgmGameName(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getVgmGameName();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVgmSystemName(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getVgmSystemName();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVgmReleaseDate(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getVgmReleaseDate();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVgmEncodedBy(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getVgmEncodedBy();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVgmNotes(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getVgmNotes();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVgmFileVersion(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getVgmFileVersion();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVgmDeviceCount(JNIEnv*, jobject) {
    if (audioEngine == nullptr) return 0;
    return static_cast<jint>(audioEngine->getVgmDeviceCount());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVgmUsedChipList(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getVgmUsedChipList();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVgmHasLoopPoint(JNIEnv*, jobject) {
    if (audioEngine == nullptr) return JNI_FALSE;
    return audioEngine->getVgmHasLoopPoint() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getFfmpegCodecName(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getFfmpegCodecName();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getFfmpegContainerName(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getFfmpegContainerName();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getFfmpegSampleFormatName(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getFfmpegSampleFormatName();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getFfmpegChannelLayoutName(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getFfmpegChannelLayoutName();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getFfmpegEncoderName(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getFfmpegEncoderName();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getGmeSystemName(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getGmeSystemName();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getGmeGameName(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getGmeGameName();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getGmeCopyright(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getGmeCopyright();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getGmeComment(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getGmeComment();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getGmeDumper(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) return env->NewStringUTF("");
    std::string value = audioEngine->getGmeDumper();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getGmeTrackCount(JNIEnv*, jobject) {
    if (audioEngine == nullptr) return 0;
    return static_cast<jint>(audioEngine->getGmeTrackCount());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getGmeVoiceCount(JNIEnv*, jobject) {
    if (audioEngine == nullptr) return 0;
    return static_cast<jint>(audioEngine->getGmeVoiceCount());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getGmeHasLoopPoint(JNIEnv*, jobject) {
    if (audioEngine == nullptr) return JNI_FALSE;
    return audioEngine->getGmeHasLoopPoint() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getGmeLoopStartMs(JNIEnv*, jobject) {
    if (audioEngine == nullptr) return -1;
    return static_cast<jint>(audioEngine->getGmeLoopStartMs());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getGmeLoopLengthMs(JNIEnv*, jobject) {
    if (audioEngine == nullptr) return -1;
    return static_cast<jint>(audioEngine->getGmeLoopLengthMs());
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_startEngine(JNIEnv* env, jobject thiz) {
    Java_com_flopster101_siliconplayer_MainActivity_startEngine(env, thiz);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_stopEngine(JNIEnv* env, jobject thiz) {
    Java_com_flopster101_siliconplayer_MainActivity_stopEngine(env, thiz);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_isEnginePlaying(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_isEnginePlaying(env, thiz);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_loadAudio(JNIEnv* env, jobject thiz, jstring path) {
    Java_com_flopster101_siliconplayer_MainActivity_loadAudio(env, thiz, path);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getSupportedExtensions(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getSupportedExtensions(env, thiz);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getDuration(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getDuration(env, thiz);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getCoreFixedSampleRateHz(
        JNIEnv* env, jobject thiz, jstring coreName) {
    return Java_com_flopster101_siliconplayer_MainActivity_getCoreFixedSampleRateHz(env, thiz, coreName);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getPosition(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getPosition(env, thiz);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_consumeNaturalEndEvent(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_consumeNaturalEndEvent(env, thiz);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_seekTo(JNIEnv* env, jobject thiz, jdouble seconds) {
    Java_com_flopster101_siliconplayer_MainActivity_seekTo(env, thiz, seconds);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setLooping(JNIEnv* env, jobject thiz, jboolean enabled) {
    Java_com_flopster101_siliconplayer_MainActivity_setLooping(env, thiz, enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setRepeatMode(JNIEnv* env, jobject thiz, jint mode) {
    Java_com_flopster101_siliconplayer_MainActivity_setRepeatMode(env, thiz, mode);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getTrackTitle(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getTrackTitle(env, thiz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getTrackArtist(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getTrackArtist(env, thiz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getTrackComposer(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getTrackComposer(env, thiz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getTrackGenre(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getTrackGenre(env, thiz);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getTrackSampleRate(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getTrackSampleRate(env, thiz);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getTrackChannelCount(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getTrackChannelCount(env, thiz);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getTrackBitDepth(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getTrackBitDepth(env, thiz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getTrackBitDepthLabel(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getTrackBitDepthLabel(env, thiz);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getRepeatModeCapabilities(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getRepeatModeCapabilities(env, thiz);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getPlaybackCapabilities(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getPlaybackCapabilities(env, thiz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getCurrentDecoderName(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getCurrentDecoderName(env, thiz);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getSubtuneCount(JNIEnv*, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getSubtuneCount());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getCurrentSubtuneIndex(JNIEnv*, jobject) {
    if (audioEngine == nullptr) {
        return 0;
    }
    return static_cast<jint>(audioEngine->getCurrentSubtuneIndex());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_selectSubtune(JNIEnv*, jobject, jint index) {
    if (audioEngine == nullptr) {
        return JNI_FALSE;
    }
    return audioEngine->selectSubtune(static_cast<int>(index)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getSubtuneTitle(JNIEnv* env, jobject, jint index) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    const std::string value = audioEngine->getSubtuneTitle(static_cast<int>(index));
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getSubtuneArtist(JNIEnv* env, jobject, jint index) {
    if (audioEngine == nullptr) {
        return env->NewStringUTF("");
    }
    const std::string value = audioEngine->getSubtuneArtist(static_cast<int>(index));
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getSubtuneDurationSeconds(JNIEnv*, jobject, jint index) {
    if (audioEngine == nullptr) {
        return 0.0;
    }
    return static_cast<jdouble>(audioEngine->getSubtuneDurationSeconds(static_cast<int>(index)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getDecoderRenderSampleRateHz(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getDecoderRenderSampleRateHz(env, thiz);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getOutputStreamSampleRateHz(JNIEnv* env, jobject thiz) {
    return Java_com_flopster101_siliconplayer_MainActivity_getOutputStreamSampleRateHz(env, thiz);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getTrackBitrate(JNIEnv* env, jobject thiz) {
    if (audioEngine == nullptr) return 0;
    return static_cast<jlong>(audioEngine->getTrackBitrate());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_isTrackVBR(JNIEnv* env, jobject thiz) {
    if (audioEngine == nullptr) return JNI_FALSE;
    return audioEngine->isTrackVBR() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVisualizationWaveformScope(
        JNIEnv* env, jobject, jint channelIndex, jint windowMs, jint triggerMode) {
    if (audioEngine == nullptr) {
        return env->NewFloatArray(0);
    }
    return toJFloatArray(
            env,
            audioEngine->getVisualizationWaveformScope(
                    static_cast<int>(channelIndex),
                    static_cast<int>(windowMs),
                    static_cast<int>(triggerMode)
            )
    );
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVisualizationBars(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewFloatArray(0);
    }
    return toJFloatArray(env, audioEngine->getVisualizationBars());
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVisualizationVuLevels(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        return env->NewFloatArray(0);
    }
    return toJFloatArray(env, audioEngine->getVisualizationVuLevels());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getVisualizationChannelCount(JNIEnv*, jobject) {
    if (audioEngine == nullptr) {
        return 2;
    }
    return static_cast<jint>(audioEngine->getVisualizationChannelCount());
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setCoreOutputSampleRate(
        JNIEnv* env, jobject thiz, jstring coreName, jint sampleRateHz) {
    Java_com_flopster101_siliconplayer_MainActivity_setCoreOutputSampleRate(
            env, thiz, coreName, sampleRateHz
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setCoreOption(
        JNIEnv* env, jobject thiz, jstring coreName, jstring optionName, jstring optionValue) {
    Java_com_flopster101_siliconplayer_MainActivity_setCoreOption(
            env, thiz, coreName, optionName, optionValue
    );
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getCoreCapabilities(
        JNIEnv* env, jobject thiz, jstring coreName) {
    return Java_com_flopster101_siliconplayer_MainActivity_getCoreCapabilities(env, thiz, coreName);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getCoreOptionApplyPolicy(
        JNIEnv* env, jobject thiz, jstring coreName, jstring optionName) {
    return Java_com_flopster101_siliconplayer_MainActivity_getCoreOptionApplyPolicy(
            env, thiz, coreName, optionName
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setAudioPipelineConfig(
        JNIEnv* env,
        jobject thiz,
        jint backendPreference,
        jint performanceMode,
        jint bufferPreset,
        jint resamplerPreference,
        jboolean allowFallback) {
    Java_com_flopster101_siliconplayer_MainActivity_setAudioPipelineConfig(
            env,
            thiz,
            backendPreference,
            performanceMode,
            bufferPreset,
            resamplerPreference,
            allowFallback
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setEndFadeApplyToAllTracks(
        JNIEnv* env, jobject thiz, jboolean enabled) {
    Java_com_flopster101_siliconplayer_MainActivity_setEndFadeApplyToAllTracks(env, thiz, enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setEndFadeDurationMs(
        JNIEnv* env, jobject thiz, jint durationMs) {
    Java_com_flopster101_siliconplayer_MainActivity_setEndFadeDurationMs(env, thiz, durationMs);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setEndFadeCurve(
        JNIEnv* env, jobject thiz, jint curve) {
    Java_com_flopster101_siliconplayer_MainActivity_setEndFadeCurve(env, thiz, curve);
}

// Gain control JNI methods
extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setMasterGain(
        JNIEnv* env, jobject thiz, jfloat gainDb) {
    ensureEngine();
    audioEngine->setMasterGain(static_cast<float>(gainDb));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setPluginGain(
        JNIEnv* env, jobject thiz, jfloat gainDb) {
    ensureEngine();
    audioEngine->setPluginGain(static_cast<float>(gainDb));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setSongGain(
        JNIEnv* env, jobject thiz, jfloat gainDb) {
    ensureEngine();
    audioEngine->setSongGain(static_cast<float>(gainDb));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setForceMono(
        JNIEnv* env, jobject thiz, jboolean enabled) {
    ensureEngine();
    audioEngine->setForceMono(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getMasterGain(
        JNIEnv* env, jobject thiz) {
    if (audioEngine == nullptr) {
        return 0.0f;
    }
    return static_cast<jfloat>(audioEngine->getMasterGain());
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getPluginGain(
        JNIEnv* env, jobject thiz) {
    if (audioEngine == nullptr) {
        return 0.0f;
    }
    return static_cast<jfloat>(audioEngine->getPluginGain());
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getSongGain(
        JNIEnv* env, jobject thiz) {
    if (audioEngine == nullptr) {
        return 0.0f;
    }
    return static_cast<jfloat>(audioEngine->getSongGain());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getForceMono(
        JNIEnv* env, jobject thiz) {
    if (audioEngine == nullptr) {
        return JNI_FALSE;
    }
    return audioEngine->getForceMono() ? JNI_TRUE : JNI_FALSE;
}

// ============================================================================
// Decoder Registry Management JNI Methods
// ============================================================================

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getRegisteredDecoderNames(
        JNIEnv* env, jobject thiz) {
    std::vector<std::string> names = DecoderRegistry::getInstance().getRegisteredDecoderNames();

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(names.size(), stringClass, nullptr);

    for (size_t i = 0; i < names.size(); ++i) {
        jstring name = env->NewStringUTF(names[i].c_str());
        env->SetObjectArrayElement(result, i, name);
        env->DeleteLocalRef(name);
    }

    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setDecoderEnabled(
        JNIEnv* env, jobject thiz, jstring decoderName, jboolean enabled) {
    const char* name = env->GetStringUTFChars(decoderName, 0);
    DecoderRegistry::getInstance().setDecoderEnabled(name, enabled == JNI_TRUE);
    env->ReleaseStringUTFChars(decoderName, name);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_isDecoderEnabled(
        JNIEnv* env, jobject thiz, jstring decoderName) {
    const char* name = env->GetStringUTFChars(decoderName, 0);
    bool enabled = DecoderRegistry::getInstance().isDecoderEnabled(name);
    env->ReleaseStringUTFChars(decoderName, name);
    return enabled ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setDecoderPriority(
        JNIEnv* env, jobject thiz, jstring decoderName, jint priority) {
    const char* name = env->GetStringUTFChars(decoderName, 0);
    DecoderRegistry::getInstance().setDecoderPriority(name, static_cast<int>(priority));
    env->ReleaseStringUTFChars(decoderName, name);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getDecoderPriority(
        JNIEnv* env, jobject thiz, jstring decoderName) {
    const char* name = env->GetStringUTFChars(decoderName, 0);
    int priority = DecoderRegistry::getInstance().getDecoderPriority(name);
    env->ReleaseStringUTFChars(decoderName, name);
    return static_cast<jint>(priority);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getDecoderDefaultPriority(
        JNIEnv* env, jobject thiz, jstring decoderName) {
    const char* name = env->GetStringUTFChars(decoderName, 0);
    int priority = DecoderRegistry::getInstance().getDecoderDefaultPriority(name);
    env->ReleaseStringUTFChars(decoderName, name);
    return static_cast<jint>(priority);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getDecoderSupportedExtensions(
        JNIEnv* env, jobject thiz, jstring decoderName) {
    const char* name = env->GetStringUTFChars(decoderName, 0);
    std::vector<std::string> extensions = DecoderRegistry::getInstance().getDecoderSupportedExtensions(name);
    env->ReleaseStringUTFChars(decoderName, name);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(extensions.size(), stringClass, nullptr);

    for (size_t i = 0; i < extensions.size(); ++i) {
        jstring ext = env->NewStringUTF(extensions[i].c_str());
        env->SetObjectArrayElement(result, i, ext);
        env->DeleteLocalRef(ext);
    }

    return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getDecoderEnabledExtensions(
        JNIEnv* env, jobject thiz, jstring decoderName) {
    const char* name = env->GetStringUTFChars(decoderName, 0);
    std::vector<std::string> extensions = DecoderRegistry::getInstance().getDecoderEnabledExtensions(name);
    env->ReleaseStringUTFChars(decoderName, name);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(extensions.size(), stringClass, nullptr);

    for (size_t i = 0; i < extensions.size(); ++i) {
        jstring ext = env->NewStringUTF(extensions[i].c_str());
        env->SetObjectArrayElement(result, i, ext);
        env->DeleteLocalRef(ext);
    }

    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_setDecoderEnabledExtensions(
        JNIEnv* env, jobject thiz, jstring decoderName, jobjectArray extensions) {
    const char* name = env->GetStringUTFChars(decoderName, 0);

    std::vector<std::string> extVector;
    jsize length = env->GetArrayLength(extensions);
    for (jsize i = 0; i < length; ++i) {
        jstring ext = (jstring) env->GetObjectArrayElement(extensions, i);
        const char* extChars = env->GetStringUTFChars(ext, 0);
        extVector.push_back(extChars);
        env->ReleaseStringUTFChars(ext, extChars);
        env->DeleteLocalRef(ext);
    }

    DecoderRegistry::getInstance().setDecoderEnabledExtensions(name, extVector);
    env->ReleaseStringUTFChars(decoderName, name);
}
