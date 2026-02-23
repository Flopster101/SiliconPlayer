#ifndef SILICONPLAYER_AUDIOTRACKJNIBRIDGE_H
#define SILICONPLAYER_AUDIOTRACKJNIBRIDGE_H

#include <jni.h>
#include <cstdint>

bool initAudioTrackJniBridge(JavaVM* vm, JNIEnv* env);
void shutdownAudioTrackJniBridge(JNIEnv* env);
bool createAudioTrackOutput(int sampleRate, int bufferFrames, int performanceMode, int bufferPreset);
bool startAudioTrackOutput();
void stopAudioTrackOutput();
void releaseAudioTrackOutput();
bool writeAudioTrackOutput(const int16_t* pcmData, int sampleCount);

#endif // SILICONPLAYER_AUDIOTRACKJNIBRIDGE_H
