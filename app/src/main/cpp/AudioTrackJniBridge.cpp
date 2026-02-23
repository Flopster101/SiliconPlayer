#include "AudioTrackJniBridge.h"

#include <android/log.h>
#include <mutex>

#define LOG_TAG "AudioTrackBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    JavaVM* gVm = nullptr;
    jclass gNativeBridgeClass = nullptr;
    jmethodID gCreateMethod = nullptr;
    jmethodID gStartMethod = nullptr;
    jmethodID gStopMethod = nullptr;
    jmethodID gReleaseMethod = nullptr;
    jmethodID gWriteMethod = nullptr;
    jshortArray gWriteArray = nullptr;
    int gWriteArrayCapacity = 0;
    std::mutex gBridgeMutex;

    JNIEnv* getEnv(bool& didAttach) {
        didAttach = false;
        if (gVm == nullptr) {
            return nullptr;
        }
        JNIEnv* env = nullptr;
        const jint envResult = gVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (envResult == JNI_OK) {
            return env;
        }
        if (envResult != JNI_EDETACHED) {
            return nullptr;
        }
        if (gVm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return nullptr;
        }
        didAttach = true;
        return env;
    }

    void detachIfNeeded(bool didAttach) {
        if (didAttach && gVm != nullptr) {
            gVm->DetachCurrentThread();
        }
    }

    bool clearExceptionIfAny(JNIEnv* env, const char* context) {
        if (env == nullptr || !env->ExceptionCheck()) {
            return false;
        }
        env->ExceptionClear();
        LOGE("JNI exception in %s", context);
        return true;
    }

    bool ensureWriteArrayCapacityLocked(JNIEnv* env, int sampleCount) {
        if (env == nullptr || sampleCount <= 0) {
            return false;
        }
        if (gWriteArray != nullptr && gWriteArrayCapacity >= sampleCount) {
            return true;
        }

        if (gWriteArray != nullptr) {
            env->DeleteGlobalRef(gWriteArray);
            gWriteArray = nullptr;
            gWriteArrayCapacity = 0;
        }

        jshortArray localArray = env->NewShortArray(static_cast<jsize>(sampleCount));
        if (localArray == nullptr) {
            clearExceptionIfAny(env, "NewShortArray");
            return false;
        }
        gWriteArray = reinterpret_cast<jshortArray>(env->NewGlobalRef(localArray));
        env->DeleteLocalRef(localArray);
        if (gWriteArray == nullptr) {
            clearExceptionIfAny(env, "NewGlobalRef(writeArray)");
            return false;
        }
        gWriteArrayCapacity = sampleCount;
        return true;
    }
}

bool initAudioTrackJniBridge(JavaVM* vm, JNIEnv* env) {
    std::lock_guard<std::mutex> lock(gBridgeMutex);
    if (vm == nullptr || env == nullptr) {
        return false;
    }

    if (gNativeBridgeClass != nullptr) {
        return true;
    }

    gVm = vm;
    jclass localClass = env->FindClass("com/flopster101/siliconplayer/NativeBridge");
    if (localClass == nullptr) {
        clearExceptionIfAny(env, "FindClass(NativeBridge)");
        return false;
    }

    gNativeBridgeClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    env->DeleteLocalRef(localClass);
    if (gNativeBridgeClass == nullptr) {
        clearExceptionIfAny(env, "NewGlobalRef(NativeBridge)");
        return false;
    }

    gCreateMethod = env->GetStaticMethodID(gNativeBridgeClass, "createAudioTrackOutput", "(IIII)Z");
    gStartMethod = env->GetStaticMethodID(gNativeBridgeClass, "startAudioTrackOutput", "()Z");
    gStopMethod = env->GetStaticMethodID(gNativeBridgeClass, "stopAudioTrackOutput", "()V");
    gReleaseMethod = env->GetStaticMethodID(gNativeBridgeClass, "releaseAudioTrackOutput", "()V");
    gWriteMethod = env->GetStaticMethodID(gNativeBridgeClass, "writeAudioTrackOutput", "([SI)I");

    if (gCreateMethod == nullptr ||
        gStartMethod == nullptr ||
        gStopMethod == nullptr ||
        gReleaseMethod == nullptr ||
        gWriteMethod == nullptr) {
        clearExceptionIfAny(env, "GetStaticMethodID(AudioTrackOutput)");
        shutdownAudioTrackJniBridge(env);
        return false;
    }

    LOGD("AudioTrack JNI bridge initialized");
    return true;
}

void shutdownAudioTrackJniBridge(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(gBridgeMutex);
    if (env != nullptr && gWriteArray != nullptr) {
        env->DeleteGlobalRef(gWriteArray);
    }
    gWriteArray = nullptr;
    gWriteArrayCapacity = 0;

    if (env != nullptr && gNativeBridgeClass != nullptr) {
        env->DeleteGlobalRef(gNativeBridgeClass);
    }
    gNativeBridgeClass = nullptr;
    gCreateMethod = nullptr;
    gStartMethod = nullptr;
    gStopMethod = nullptr;
    gReleaseMethod = nullptr;
    gWriteMethod = nullptr;
}

bool createAudioTrackOutput(int sampleRate, int bufferFrames, int performanceMode, int bufferPreset) {
    bool didAttach = false;
    JNIEnv* env = getEnv(didAttach);
    if (env == nullptr) {
        return false;
    }

    bool created = false;
    {
        std::lock_guard<std::mutex> lock(gBridgeMutex);
        if (gNativeBridgeClass != nullptr && gCreateMethod != nullptr) {
            const jboolean result = env->CallStaticBooleanMethod(
                    gNativeBridgeClass,
                    gCreateMethod,
                    static_cast<jint>(sampleRate),
                    static_cast<jint>(bufferFrames),
                    static_cast<jint>(performanceMode),
                    static_cast<jint>(bufferPreset)
            );
            created = !clearExceptionIfAny(env, "createAudioTrackOutput") && result == JNI_TRUE;
        }
    }

    detachIfNeeded(didAttach);
    return created;
}

bool startAudioTrackOutput() {
    bool didAttach = false;
    JNIEnv* env = getEnv(didAttach);
    if (env == nullptr) {
        return false;
    }

    bool started = false;
    {
        std::lock_guard<std::mutex> lock(gBridgeMutex);
        if (gNativeBridgeClass != nullptr && gStartMethod != nullptr) {
            const jboolean result = env->CallStaticBooleanMethod(gNativeBridgeClass, gStartMethod);
            started = !clearExceptionIfAny(env, "startAudioTrackOutput") && result == JNI_TRUE;
        }
    }

    detachIfNeeded(didAttach);
    return started;
}

void stopAudioTrackOutput() {
    bool didAttach = false;
    JNIEnv* env = getEnv(didAttach);
    if (env == nullptr) {
        return;
    }

    {
        std::lock_guard<std::mutex> lock(gBridgeMutex);
        if (gNativeBridgeClass != nullptr && gStopMethod != nullptr) {
            env->CallStaticVoidMethod(gNativeBridgeClass, gStopMethod);
            clearExceptionIfAny(env, "stopAudioTrackOutput");
        }
    }

    detachIfNeeded(didAttach);
}

void releaseAudioTrackOutput() {
    bool didAttach = false;
    JNIEnv* env = getEnv(didAttach);
    if (env == nullptr) {
        return;
    }

    {
        std::lock_guard<std::mutex> lock(gBridgeMutex);
        if (gNativeBridgeClass != nullptr && gReleaseMethod != nullptr) {
            env->CallStaticVoidMethod(gNativeBridgeClass, gReleaseMethod);
            clearExceptionIfAny(env, "releaseAudioTrackOutput");
        }
        if (gWriteArray != nullptr) {
            env->DeleteGlobalRef(gWriteArray);
            gWriteArray = nullptr;
            gWriteArrayCapacity = 0;
        }
    }

    detachIfNeeded(didAttach);
}

bool writeAudioTrackOutput(const int16_t* pcmData, int sampleCount) {
    if (pcmData == nullptr || sampleCount <= 0) {
        return false;
    }

    bool didAttach = false;
    JNIEnv* env = getEnv(didAttach);
    if (env == nullptr) {
        return false;
    }

    bool success = false;
    {
        std::lock_guard<std::mutex> lock(gBridgeMutex);
        if (gNativeBridgeClass != nullptr &&
            gWriteMethod != nullptr &&
            ensureWriteArrayCapacityLocked(env, sampleCount)) {
            env->SetShortArrayRegion(
                    gWriteArray,
                    0,
                    static_cast<jsize>(sampleCount),
                    reinterpret_cast<const jshort*>(pcmData)
            );
            if (!clearExceptionIfAny(env, "SetShortArrayRegion(writeAudioTrackOutput)")) {
                const jint writtenSamples = env->CallStaticIntMethod(
                        gNativeBridgeClass,
                        gWriteMethod,
                        gWriteArray,
                        static_cast<jint>(sampleCount)
                );
                success = !clearExceptionIfAny(env, "writeAudioTrackOutput") &&
                          writtenSamples == static_cast<jint>(sampleCount);
            }
        }
    }

    detachIfNeeded(didAttach);
    return success;
}
