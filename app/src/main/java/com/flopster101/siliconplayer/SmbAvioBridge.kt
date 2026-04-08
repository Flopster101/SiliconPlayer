package com.flopster101.siliconplayer

import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private data class OpenedSmbAvioTransportFile(
    val connection: Connection,
    val session: Session,
    val share: DiskShare,
    val file: SmbFile,
    val sizeBytes: Long
)

private fun closeOpenedSmbAvioTransportFile(openedFile: OpenedSmbAvioTransportFile) {
    runCatching { openedFile.file.close() }
    runCatching { openedFile.share.close() }
    runCatching { openedFile.session.close() }
    runCatching { openedFile.connection.close() }
}

private fun openSmbAvioTransportFile(
    spec: SmbSourceSpec,
    remotePath: String
): OpenedSmbAvioTransportFile {
    var shouldRetry = true
    while (true) {
        val openedSession = openDedicatedSmbSession(spec)
        try {
            val share = openedSession.session.connectShare(spec.share)
            if (share !is DiskShare) {
                runCatching { share.close() }
                throw IllegalStateException("SMB share is not a disk share")
            }

            try {
                val smbFile = share.openFile(
                    remotePath,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                val sizeBytes = try {
                    smbFile.getFileInformation(FileStandardInformation::class.java)
                        .getEndOfFile()
                        .coerceAtLeast(0L)
                } catch (t: Throwable) {
                    runCatching { smbFile.close() }
                    runCatching { share.close() }
                    throw t
                }
                return OpenedSmbAvioTransportFile(
                    connection = openedSession.connection,
                    session = openedSession.session,
                    share = share,
                    file = smbFile,
                    sizeBytes = sizeBytes
                )
            } catch (t: Throwable) {
                runCatching { share.close() }
                throw t
            }
        } catch (t: Throwable) {
            runCatching { openedSession.session.close() }
            runCatching { openedSession.connection.close() }
            if (!shouldRetry || !isRetryableSmbTransportFailure(t)) {
                throw t
            }
            shouldRetry = false
        }
    }
}

private class SmbProgressiveRandomAccessTransport(
    private val spec: SmbSourceSpec,
    private val remotePath: String
) : ProgressiveRandomAccessTransport {
    private val lock = Any()
    private var openedFile = openSmbAvioTransportFile(spec, remotePath)

    override val sourceId: String = buildSmbSourceId(spec)

    override val sizeBytes: Long
        get() = synchronized(lock) { openedFile.sizeBytes }

    override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
        if (offset < 0L) {
            throw IllegalArgumentException("SMB AVIO offset must be non-negative")
        }
        val clampedLength = length.coerceIn(0, buffer.size - bufferOffset)
        if (clampedLength <= 0) return 0

        synchronized(lock) {
            if (offset >= openedFile.sizeBytes) {
                return 0
            }
            return try {
                openedFile.file.read(buffer, offset, bufferOffset, clampedLength).coerceAtLeast(0)
            } catch (t: Throwable) {
                if (!isRetryableSmbTransportFailure(t)) {
                    closeOpenedSmbAvioTransportFile(openedFile)
                    throw t
                }
                closeOpenedSmbAvioTransportFile(openedFile)
                val reopened = openSmbAvioTransportFile(spec, remotePath)
                openedFile = reopened
                try {
                    reopened.file.read(buffer, offset, bufferOffset, clampedLength).coerceAtLeast(0)
                } catch (retryFailure: Throwable) {
                    closeOpenedSmbAvioTransportFile(reopened)
                    throw retryFailure
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            closeOpenedSmbAvioTransportFile(openedFile)
        }
    }
}

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
                )
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
