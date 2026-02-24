package com.flopster101.siliconplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val nativeAudioSessionMutex = Mutex()

internal suspend fun <T> runWithNativeAudioSession(block: () -> T): T {
    return withContext(Dispatchers.IO) {
        nativeAudioSessionMutex.withLock {
            block()
        }
    }
}

