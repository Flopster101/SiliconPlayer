package com.flopster101.siliconplayer

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private class SmbAvioHandle(
    private val cache: ProgressiveRandomAccessCache
) {
    fun read(offset: Long, buffer: ByteArray, length: Int): Int {
        return cache.readAt(offset, buffer, length)
    }

    fun sizeBytes(): Long = cache.sizeBytes

    fun close() {
        cache.close()
    }
}

internal object SmbAvioBridge {
    private val nextHandleId = AtomicLong(1L)
    private val activeHandles = ConcurrentHashMap<Long, SmbAvioHandle>()

    fun openHandle(requestUri: String): Long {
        val spec = parseSmbSourceSpecFromInput(requestUri)
            ?: throw IllegalArgumentException("Invalid SMB AVIO request URI")
        val remotePath = spec.path?.trim().orEmpty()
        if (spec.share.isBlank()) {
            throw IllegalArgumentException("SMB AVIO request URI must include a share")
        }
        if (remotePath.isBlank()) {
            throw IllegalArgumentException("SMB AVIO request URI must point to a file inside the share")
        }

        val handleId = nextHandleId.getAndIncrement().coerceAtLeast(1L)
        activeHandles[handleId] = SmbAvioHandle(
            cache = ProgressiveRandomAccessCache(
                context = NativeBridge.requireAppContext(),
                transport = SmbProgressiveRandomAccessTransport(
                    spec = spec,
                    remotePath = remotePath
                ),
                prefetchTransportFactory = {
                    SmbProgressiveRandomAccessTransport(
                        spec = spec,
                        remotePath = remotePath
                    )
                }
            )
        )
        return handleId
    }

    fun readHandle(handleId: Long, offset: Long, buffer: ByteArray, length: Int): Int {
        val handle = activeHandles[handleId]
            ?: throw IllegalStateException("SMB AVIO handle is not open")
        return try {
            handle.read(offset = offset, buffer = buffer, length = length)
        } catch (t: Throwable) {
            activeHandles.remove(handleId, handle)
            handle.close()
            throw t
        }
    }

    fun getHandleSize(handleId: Long): Long {
        return activeHandles[handleId]?.sizeBytes()
            ?: throw IllegalStateException("SMB AVIO handle is not open")
    }

    fun closeHandle(handleId: Long) {
        val handle = activeHandles.remove(handleId) ?: return
        handle.close()
    }
}
