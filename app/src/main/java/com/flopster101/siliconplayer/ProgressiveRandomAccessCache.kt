package com.flopster101.siliconplayer

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.min

internal const val PROGRESSIVE_REMOTE_SOURCE_CACHE_DIR = "progressive_remote_sources"
private const val PROGRESSIVE_REMOTE_SOURCE_CACHE_VERSION = 1
private const val DEFAULT_PROGRESSIVE_CACHE_CHUNK_SIZE_BYTES = 64 * 1024
private const val CHUNK_STATE_UNCACHED: Byte = 0
private const val CHUNK_STATE_CACHED: Byte = 1
private const val CHUNK_STATE_FETCHING: Byte = 2

internal interface ProgressiveRandomAccessTransport {
    val sourceId: String
    val sizeBytes: Long

    fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int

    fun close()
}

internal data class ProgressiveRandomAccessPrefetchConfig(
    val maxBytesToPrefetch: Long? = null,
    val rateLimitBytesPerSecond: Long? = null
)

private data class ProgressiveRandomAccessCacheFiles(
    val dataFile: File,
    val chunkMapFile: File,
    val metaFile: File
)

private fun progressiveRandomAccessCacheFiles(
    context: Context,
    sourceId: String
): ProgressiveRandomAccessCacheFiles {
    val cacheRoot = File(context.cacheDir, PROGRESSIVE_REMOTE_SOURCE_CACHE_DIR)
    val dataFile = remoteCacheFileForSource(cacheRoot, sourceId)
    return ProgressiveRandomAccessCacheFiles(
        dataFile = dataFile,
        chunkMapFile = File(dataFile.absolutePath + ".chunks"),
        metaFile = File(dataFile.absolutePath + ".meta")
    )
}

private fun progressiveRandomAccessMetaContents(sizeBytes: Long, chunkSizeBytes: Int): String {
    return buildString {
        append("version=")
        append(PROGRESSIVE_REMOTE_SOURCE_CACHE_VERSION)
        append('\n')
        append("sizeBytes=")
        append(sizeBytes)
        append('\n')
        append("chunkSizeBytes=")
        append(chunkSizeBytes)
        append('\n')
    }
}

private fun progressiveRandomAccessMetaMatches(
    file: File,
    sizeBytes: Long,
    chunkSizeBytes: Int
): Boolean {
    if (!file.exists() || !file.isFile) return false
    return runCatching {
        file.readText() == progressiveRandomAccessMetaContents(sizeBytes, chunkSizeBytes)
    }.getOrDefault(false)
}

internal class ProgressiveRandomAccessCache(
    context: Context,
    private val transport: ProgressiveRandomAccessTransport,
    private val chunkSizeBytes: Int = DEFAULT_PROGRESSIVE_CACHE_CHUNK_SIZE_BYTES,
    private val prefetchTransportFactory: (() -> ProgressiveRandomAccessTransport)? = null,
    private val prefetchConfig: ProgressiveRandomAccessPrefetchConfig = ProgressiveRandomAccessPrefetchConfig()
) {
    private val lock = Object()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val files = progressiveRandomAccessCacheFiles(context, transport.sourceId)
    private val chunkCount = when {
        transport.sizeBytes <= 0L -> 0
        else -> ((transport.sizeBytes + chunkSizeBytes - 1L) / chunkSizeBytes).toInt()
    }
    private var closed = false
    private var prefetchJob: Job? = null
    private var prefetchTransport: ProgressiveRandomAccessTransport? = null

    private val dataFileRaf: RandomAccessFile
    private val chunkMapRaf: RandomAccessFile
    private val chunkStates: ByteArray

    val sizeBytes: Long
        get() = transport.sizeBytes

    val cachedPrefixBytes: Long
        get() = synchronized(lock) { cachedPrefixBytesLocked() }

    init {
        val parent = files.dataFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        if (!progressiveRandomAccessMetaMatches(files.metaFile, transport.sizeBytes, chunkSizeBytes)) {
            runCatching { files.dataFile.delete() }
            runCatching { files.chunkMapFile.delete() }
            files.metaFile.writeText(
                progressiveRandomAccessMetaContents(
                    sizeBytes = transport.sizeBytes,
                    chunkSizeBytes = chunkSizeBytes
                )
            )
        }

        dataFileRaf = RandomAccessFile(files.dataFile, "rw")
        chunkMapRaf = RandomAccessFile(files.chunkMapFile, "rw")
        if (transport.sizeBytes <= 0L) {
            dataFileRaf.setLength(0L)
        }
        chunkStates = ByteArray(chunkCount)
        if (chunkCount > 0) {
            val chunkMapLength = chunkMapRaf.length()
            if (chunkMapLength > chunkCount.toLong()) {
                chunkMapRaf.setLength(chunkCount.toLong())
            }
            chunkMapRaf.seek(0L)
            val toRead = min(chunkCount, chunkMapLength.toInt())
            if (toRead > 0) {
                val persistedStates = ByteArray(toRead)
                chunkMapRaf.readFully(persistedStates, 0, toRead)
                for (chunkIndex in 0 until toRead) {
                    chunkStates[chunkIndex] =
                        if (persistedStates[chunkIndex].toInt() != 0 && isChunkPersistedLocked(chunkIndex)) {
                            CHUNK_STATE_CACHED
                        } else {
                            CHUNK_STATE_UNCACHED
                        }
                }
            }
            for (chunkIndex in 0 until chunkCount) {
                if (chunkStates[chunkIndex] == CHUNK_STATE_CACHED) continue
                chunkStates[chunkIndex] = CHUNK_STATE_UNCACHED
            }
            sanitizePersistedChunkStateLocked()
        }

        if (
            chunkCount > 0 &&
            prefetchTransportFactory != null &&
            hasRemainingPrefetchWorkLocked()
        ) {
            startBackgroundPrefetch(prefetchTransportFactory)
        }
    }

    fun readAt(offset: Long, buffer: ByteArray, length: Int): Int {
        require(offset >= 0L) { "Progressive cache offset must be non-negative" }
        val clampedLength = length.coerceIn(0, buffer.size)
        if (clampedLength <= 0) return 0
        if (offset >= transport.sizeBytes) return 0

        var remaining = min(clampedLength.toLong(), transport.sizeBytes - offset).toInt()
        var outputOffset = 0
        var currentOffset = offset

        while (remaining > 0) {
            val chunkIndex = (currentOffset / chunkSizeBytes).toInt()
            if (chunkIndex !in 0 until chunkCount) break
            ensureChunkCached(chunkIndex, transport)

            val chunkStartOffset = chunkIndex.toLong() * chunkSizeBytes.toLong()
            val offsetInsideChunk = (currentOffset - chunkStartOffset).toInt()
            val bytesFromChunk = min(
                remaining,
                currentChunkSizeBytes(chunkIndex) - offsetInsideChunk
            )
            if (bytesFromChunk <= 0) {
                break
            }

            synchronized(lock) {
                check(!closed) { "Progressive cache is closed" }
                dataFileRaf.seek(currentOffset)
                dataFileRaf.readFully(buffer, outputOffset, bytesFromChunk)
            }

            outputOffset += bytesFromChunk
            currentOffset += bytesFromChunk
            remaining -= bytesFromChunk
        }

        return outputOffset
    }

    fun close() {
        var activePrefetchTransport: ProgressiveRandomAccessTransport? = null
        var activePrefetchJob: Job? = null
        val alreadyClosed = synchronized(lock) {
            if (closed) {
                true
            } else {
                closed = true
                activePrefetchTransport = prefetchTransport
                prefetchTransport = null
                activePrefetchJob = prefetchJob
                prefetchJob = null
                lock.notifyAll()
                false
            }
        }
        if (alreadyClosed) return
        runCatching { activePrefetchTransport?.close() }
        activePrefetchJob?.cancel()
        scope.cancel()
        synchronized(lock) {
            runCatching { dataFileRaf.close() }
            runCatching { chunkMapRaf.close() }
            runCatching { transport.close() }
        }
    }

    private fun startBackgroundPrefetch(
        backgroundTransportFactory: () -> ProgressiveRandomAccessTransport
    ) {
        prefetchJob = scope.launch {
            val backgroundTransport = try {
                backgroundTransportFactory()
            } catch (_: Throwable) {
                return@launch
            }
            synchronized(lock) {
                if (closed) {
                    runCatching { backgroundTransport.close() }
                    return@launch
                }
                prefetchTransport = backgroundTransport
            }
            try {
                if (backgroundTransport.sizeBytes != transport.sizeBytes) {
                    return@launch
                }
                val prefetchTargetBytes = prefetchTargetBytes()
                val rateLimitBytesPerSecond = prefetchConfig.rateLimitBytesPerSecond
                    ?.coerceAtLeast(1L)
                var backgroundBytesFetched = 0L
                val startedAtMs = SystemClock.elapsedRealtime()
                for (chunkIndex in 0 until chunkCount) {
                    ensureActive()
                    if (isClosed()) break
                    val chunkEndOffset = currentChunkEndOffset(chunkIndex)
                    if (chunkEndOffset > prefetchTargetBytes) {
                        break
                    }
                    try {
                        val fetchedByThisCall = ensureChunkCached(chunkIndex, backgroundTransport)
                        if (fetchedByThisCall && rateLimitBytesPerSecond != null) {
                            backgroundBytesFetched += currentChunkSizeBytes(chunkIndex).toLong()
                            val targetElapsedMs =
                                (backgroundBytesFetched * 1000L) / rateLimitBytesPerSecond
                            val actualElapsedMs =
                                (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
                            val delayMs = (targetElapsedMs - actualElapsedMs).coerceAtLeast(0L)
                            if (delayMs > 0L) {
                                delay(delayMs)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        break
                    }
                }
            } finally {
                synchronized(lock) {
                    if (prefetchTransport === backgroundTransport) {
                        prefetchTransport = null
                    }
                }
                runCatching { backgroundTransport.close() }
            }
        }
    }

    private fun currentChunkSizeBytes(chunkIndex: Int): Int {
        val chunkOffset = chunkIndex.toLong() * chunkSizeBytes.toLong()
        val remainingBytes = (transport.sizeBytes - chunkOffset).coerceAtLeast(0L)
        return min(chunkSizeBytes.toLong(), remainingBytes).toInt()
    }

    private fun currentChunkEndOffset(chunkIndex: Int): Long {
        return chunkIndex.toLong() * chunkSizeBytes.toLong() + currentChunkSizeBytes(chunkIndex)
    }

    private fun cachedPrefixBytesLocked(): Long {
        if (chunkCount <= 0) return 0L
        var prefixBytes = 0L
        for (chunkIndex in 0 until chunkCount) {
            if (chunkStates[chunkIndex] != CHUNK_STATE_CACHED) break
            prefixBytes = currentChunkEndOffset(chunkIndex)
        }
        return prefixBytes.coerceAtMost(transport.sizeBytes)
    }

    private fun prefetchTargetBytes(): Long {
        val requested = prefetchConfig.maxBytesToPrefetch?.coerceAtLeast(0L)
            ?: transport.sizeBytes
        return requested.coerceAtMost(transport.sizeBytes)
    }

    private fun hasRemainingPrefetchWorkLocked(): Boolean {
        val targetBytes = prefetchTargetBytes()
        if (targetBytes <= 0L) return false
        return cachedPrefixBytesLocked() < targetBytes
    }

    private fun isChunkPersistedLocked(chunkIndex: Int): Boolean {
        return currentChunkEndOffset(chunkIndex) <= dataFileRaf.length()
    }

    private fun sanitizePersistedChunkStateLocked() {
        for (chunkIndex in 0 until chunkCount) {
            if (chunkStates[chunkIndex] != CHUNK_STATE_CACHED) continue
            if (isChunkPersistedLocked(chunkIndex)) continue
            chunkStates[chunkIndex] = CHUNK_STATE_UNCACHED
            chunkMapRaf.seek(chunkIndex.toLong())
            chunkMapRaf.write(0)
        }
    }

    private fun ensureChunkCached(
        chunkIndex: Int,
        activeTransport: ProgressiveRandomAccessTransport
    ): Boolean {
        if (chunkIndex !in 0 until chunkCount) {
            throw IllegalArgumentException("Invalid progressive cache chunk index: $chunkIndex")
        }

        while (true) {
            synchronized(lock) {
                check(!closed) { "Progressive cache is closed" }
                when (chunkStates[chunkIndex]) {
                    CHUNK_STATE_CACHED -> return false
                    CHUNK_STATE_FETCHING -> {
                        lock.wait()
                        continue
                    }
                    else -> {
                        chunkStates[chunkIndex] = CHUNK_STATE_FETCHING
                    }
                }
            }

            try {
                val chunkData = readChunkData(chunkIndex, activeTransport)
                synchronized(lock) {
                    if (closed) {
                        chunkStates[chunkIndex] = CHUNK_STATE_UNCACHED
                        lock.notifyAll()
                        return false
                    }
                    val chunkOffset = chunkIndex.toLong() * chunkSizeBytes.toLong()
                    dataFileRaf.seek(chunkOffset)
                    dataFileRaf.write(chunkData)
                    chunkStates[chunkIndex] = CHUNK_STATE_CACHED
                    chunkMapRaf.seek(chunkIndex.toLong())
                    chunkMapRaf.write(1)
                    lock.notifyAll()
                    return true
                }
            } catch (t: Throwable) {
                synchronized(lock) {
                    if (chunkStates[chunkIndex] == CHUNK_STATE_FETCHING) {
                        chunkStates[chunkIndex] = CHUNK_STATE_UNCACHED
                    }
                    lock.notifyAll()
                }
                throw t
            }
        }
    }

    private fun readChunkData(
        chunkIndex: Int,
        activeTransport: ProgressiveRandomAccessTransport
    ): ByteArray {
        val chunkOffset = chunkIndex.toLong() * chunkSizeBytes.toLong()
        val chunkSize = currentChunkSizeBytes(chunkIndex)
        val chunkBuffer = ByteArray(chunkSize)
        var totalRead = 0
        while (totalRead < chunkSize) {
            val read = activeTransport.readAt(
                offset = chunkOffset + totalRead,
                buffer = chunkBuffer,
                bufferOffset = totalRead,
                length = chunkSize - totalRead
            )
            if (read <= 0) {
                break
            }
            totalRead += read
        }
        if (totalRead != chunkSize) {
            throw IllegalStateException(
                "Progressive cache short read for source=${transport.sourceId} chunk=$chunkIndex expected=$chunkSize actual=$totalRead"
            )
        }
        return chunkBuffer
    }

    private fun isClosed(): Boolean {
        synchronized(lock) {
            return closed
        }
    }
}
