package com.flopster101.siliconplayer

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

internal const val PROGRESSIVE_REMOTE_SOURCE_CACHE_DIR = "progressive_remote_sources"
private const val PROGRESSIVE_REMOTE_SOURCE_CACHE_VERSION = 1
private const val DEFAULT_PROGRESSIVE_CACHE_CHUNK_SIZE_BYTES = 64 * 1024

internal interface ProgressiveRandomAccessTransport {
    val sourceId: String
    val sizeBytes: Long

    fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int

    fun close()
}

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
    private val chunkSizeBytes: Int = DEFAULT_PROGRESSIVE_CACHE_CHUNK_SIZE_BYTES
) {
    private val lock = Any()
    private val files = progressiveRandomAccessCacheFiles(context, transport.sourceId)
    private val chunkCount = when {
        transport.sizeBytes <= 0L -> 0
        else -> ((transport.sizeBytes + chunkSizeBytes - 1L) / chunkSizeBytes).toInt()
    }
    private val chunkScratchBuffer = ByteArray(chunkSizeBytes)
    private var closed = false

    private val dataFileRaf: RandomAccessFile
    private val chunkMapRaf: RandomAccessFile
    private val cachedChunks: ByteArray

    val sizeBytes: Long
        get() = transport.sizeBytes

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
        cachedChunks = ByteArray(chunkCount)
        if (chunkCount > 0) {
            val chunkMapLength = chunkMapRaf.length()
            if (chunkMapLength > chunkCount.toLong()) {
                chunkMapRaf.setLength(chunkCount.toLong())
            }
            chunkMapRaf.seek(0L)
            val toRead = min(chunkCount, chunkMapLength.toInt())
            if (toRead > 0) {
                chunkMapRaf.readFully(cachedChunks, 0, toRead)
            }
        }
    }

    fun readAt(offset: Long, buffer: ByteArray, length: Int): Int {
        require(offset >= 0L) { "Progressive cache offset must be non-negative" }
        val clampedLength = length.coerceIn(0, buffer.size)
        if (clampedLength <= 0) return 0
        if (offset >= transport.sizeBytes) return 0

        synchronized(lock) {
            check(!closed) { "Progressive cache is closed" }

            var remaining = min(clampedLength.toLong(), transport.sizeBytes - offset).toInt()
            var outputOffset = 0
            var currentOffset = offset

            while (remaining > 0) {
                val chunkIndex = (currentOffset / chunkSizeBytes).toInt()
                if (chunkIndex !in 0 until chunkCount) break
                ensureChunkCachedLocked(chunkIndex)

                val chunkStartOffset = chunkIndex.toLong() * chunkSizeBytes.toLong()
                val offsetInsideChunk = (currentOffset - chunkStartOffset).toInt()
                val bytesFromChunk = min(
                    remaining,
                    currentChunkSizeBytes(chunkIndex) - offsetInsideChunk
                )
                if (bytesFromChunk <= 0) {
                    break
                }

                dataFileRaf.seek(currentOffset)
                dataFileRaf.readFully(buffer, outputOffset, bytesFromChunk)

                outputOffset += bytesFromChunk
                currentOffset += bytesFromChunk
                remaining -= bytesFromChunk
            }

            return outputOffset
        }
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            runCatching { dataFileRaf.close() }
            runCatching { chunkMapRaf.close() }
            runCatching { transport.close() }
        }
    }

    private fun ensureChunkCachedLocked(chunkIndex: Int) {
        if (chunkIndex !in 0 until chunkCount) {
            throw IllegalArgumentException("Invalid progressive cache chunk index: $chunkIndex")
        }
        if (cachedChunks[chunkIndex].toInt() != 0) {
            return
        }

        val chunkOffset = chunkIndex.toLong() * chunkSizeBytes.toLong()
        val chunkSize = currentChunkSizeBytes(chunkIndex)
        var totalRead = 0
        while (totalRead < chunkSize) {
            val read = transport.readAt(
                offset = chunkOffset + totalRead,
                buffer = chunkScratchBuffer,
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

        dataFileRaf.seek(chunkOffset)
        dataFileRaf.write(chunkScratchBuffer, 0, chunkSize)
        cachedChunks[chunkIndex] = 1
        chunkMapRaf.seek(chunkIndex.toLong())
        chunkMapRaf.write(1)
    }

    private fun currentChunkSizeBytes(chunkIndex: Int): Int {
        val chunkOffset = chunkIndex.toLong() * chunkSizeBytes.toLong()
        val remainingBytes = (transport.sizeBytes - chunkOffset).coerceAtLeast(0L)
        return min(chunkSizeBytes.toLong(), remainingBytes).toInt()
    }
}
