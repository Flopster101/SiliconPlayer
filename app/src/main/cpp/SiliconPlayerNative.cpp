#include <jni.h>
#include <string>
#include "AudioEngine.h"
#include "decoders/DecoderRegistry.h"
#include <vector>

static AudioEngine *audioEngine = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from AAudio C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_startEngine(JNIEnv* env, jobject) {
    if (audioEngine == nullptr) {
        audioEngine = new AudioEngine();
    }
    audioEngine->start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_stopEngine(JNIEnv* env, jobject) {
    if (audioEngine != nullptr) {
        audioEngine->stop();
        delete audioEngine;
        audioEngine = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_MainActivity_loadAudio(JNIEnv* env, jobject, jstring path) {
    if (audioEngine == nullptr) {
        audioEngine = new AudioEngine();
    }
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
