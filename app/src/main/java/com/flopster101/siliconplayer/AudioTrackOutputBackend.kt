package com.flopster101.siliconplayer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlin.math.max

internal object AudioTrackOutputBackend {
    private const val TAG = "AudioTrackBackend"
    private const val CHANNEL_COUNT = 2
    private const val BYTES_PER_SAMPLE = 2
    private const val DEFAULT_SAMPLE_RATE = 48000

    private val lock = Any()

    @Volatile
    private var audioTrack: AudioTrack? = null

    private fun framesForPreset(bufferPreset: Int): Int {
        return when (bufferPreset) {
            1 -> 1024
            3 -> 4096
            else -> 2048
        }
    }

    fun create(sampleRate: Int, bufferFrames: Int, performanceMode: Int, bufferPreset: Int): Boolean {
        synchronized(lock) {
            releaseLocked()

            val targetSampleRate = if (sampleRate > 0) sampleRate else DEFAULT_SAMPLE_RATE
            val targetBufferFrames = if (bufferFrames > 0) bufferFrames else framesForPreset(bufferPreset)
            val requestedBufferBytes = targetBufferFrames * CHANNEL_COUNT * BYTES_PER_SAMPLE
            val minBufferBytes = AudioTrack.getMinBufferSize(
                targetSampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferBytes <= 0) {
                Log.e(TAG, "AudioTrack min buffer size query failed: sampleRate=$targetSampleRate")
                return false
            }

            val finalBufferBytes = max(minBufferBytes, requestedBufferBytes)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(targetSampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()

            val track = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val builder = AudioTrack.Builder()
                        .setAudioAttributes(attributes)
                        .setAudioFormat(format)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(finalBufferBytes)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val perfMode = when (performanceMode) {
                            1 -> AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
                            3 -> AudioTrack.PERFORMANCE_MODE_POWER_SAVING
                            else -> AudioTrack.PERFORMANCE_MODE_NONE
                        }
                        builder.setPerformanceMode(perfMode)
                    }
                    builder.build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        targetSampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        finalBufferBytes,
                        AudioTrack.MODE_STREAM
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "AudioTrack creation threw", t)
                null
            }

            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                track?.release()
                Log.e(TAG, "AudioTrack failed to initialize")
                return false
            }

            audioTrack = track
            Log.d(
                TAG,
                "AudioTrack ready: sampleRate=$targetSampleRate requestedFrames=$targetBufferFrames finalBufferBytes=$finalBufferBytes minBufferBytes=$minBufferBytes"
            )
            return true
        }
    }

    fun start(): Boolean {
        val track = synchronized(lock) { audioTrack } ?: return false
        return try {
            track.play()
            track.playState == AudioTrack.PLAYSTATE_PLAYING
        } catch (t: Throwable) {
            Log.e(TAG, "AudioTrack start failed", t)
            false
        }
    }

    fun stop() {
        val track = synchronized(lock) { audioTrack } ?: return
        try {
            track.pause()
            track.flush()
        } catch (t: Throwable) {
            Log.d(TAG, "AudioTrack stop/flush ignored: ${t.message}")
        }
    }

    fun release() {
        synchronized(lock) {
            releaseLocked()
        }
    }

    fun writeBlocking(data: ShortArray, sampleCount: Int): Int {
        if (sampleCount <= 0) return 0
        val track = audioTrack ?: return AudioTrack.ERROR_INVALID_OPERATION
        if (track.state != AudioTrack.STATE_INITIALIZED) return AudioTrack.ERROR_INVALID_OPERATION

        var written = 0
        var stalls = 0
        while (written < sampleCount) {
            val remaining = sampleCount - written
            val result = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    track.write(data, written, remaining, AudioTrack.WRITE_BLOCKING)
                } else {
                    @Suppress("DEPRECATION")
                    track.write(data, written, remaining)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "AudioTrack write failed", t)
                return if (written > 0) written else AudioTrack.ERROR_INVALID_OPERATION
            }

            if (result > 0) {
                written += result
                stalls = 0
                continue
            }
            if (result == 0) {
                stalls++
                if (stalls >= 4) {
                    return written
                }
                Thread.yield()
                continue
            }
            return if (written > 0) written else result
        }
        return written
    }

    private fun releaseLocked() {
        val track = audioTrack
        audioTrack = null
        if (track == null) return
        try {
            track.pause()
            track.flush()
        } catch (_: Throwable) {
        }
        try {
            track.release()
        } catch (_: Throwable) {
        }
    }
}
