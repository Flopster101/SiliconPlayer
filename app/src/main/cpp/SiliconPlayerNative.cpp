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
