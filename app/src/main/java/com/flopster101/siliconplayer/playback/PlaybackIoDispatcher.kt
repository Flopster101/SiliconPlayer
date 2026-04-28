package com.flopster101.siliconplayer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * A dedicated single-threaded dispatcher for all JNI reads against NativeBridge.
 *
 * Using a private thread prevents JNI mutex contention from starving either
 * the main thread or the shared [Dispatchers.Default] pool (which Compose
 * also uses for snapshot and layout work).  All playback-state polling,
 * metadata reads, and other native queries should be dispatched here.
 */
internal val Dispatchers.PlaybackIo: CoroutineDispatcher
    get() = PlaybackIoHolder.dispatcher

private object PlaybackIoHolder {
    val dispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "PlaybackIo").apply { isDaemon = true }
        }.asCoroutineDispatcher()
}
