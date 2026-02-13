#include <jni.h>
#include <string>
#include "AudioEngine.h"

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
