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

private data class OpenedSmbAvioFile(
    val connection: Connection,
    val session: Session,
    val share: DiskShare,
    val file: SmbFile,
    val sizeBytes: Long
)

private class SmbAvioHandle(
    val spec: SmbSourceSpec,
    val remotePath: String,
    openedFile: OpenedSmbAvioFile
) {
    val lock = Any()
    var connection: Connection = openedFile.connection
    var session: Session = openedFile.session
    var share: DiskShare = openedFile.share
    var file: SmbFile = openedFile.file
    var sizeBytes: Long = openedFile.sizeBytes

    fun replace(openedFile: OpenedSmbAvioFile) {
        connection = openedFile.connection
        session = openedFile.session
        share = openedFile.share
        file = openedFile.file
        sizeBytes = openedFile.sizeBytes
    }
}

private fun openSmbAvioFile(spec: SmbSourceSpec, remotePath: String): OpenedSmbAvioFile {
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
                return OpenedSmbAvioFile(
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

private fun closeSmbAvioHandleResources(handle: SmbAvioHandle) {
    runCatching { handle.file.close() }
    runCatching { handle.share.close() }
    runCatching { handle.session.close() }
    runCatching { handle.connection.close() }
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
            spec = spec,
            remotePath = remotePath,
            openedFile = openSmbAvioFile(spec, remotePath)
        )
        return handleId
    }

    fun readHandle(handleId: Long, offset: Long, buffer: ByteArray, length: Int): Int {
        val handle = activeHandles[handleId]
            ?: throw IllegalStateException("SMB AVIO handle is not open")
        if (offset < 0L) {
            throw IllegalArgumentException("SMB AVIO offset must be non-negative")
        }
        val clampedLength = length.coerceIn(0, buffer.size)
        if (clampedLength <= 0) {
            return 0
        }
        synchronized(handle.lock) {
            if (offset >= handle.sizeBytes) {
                return 0
            }
            try {
                return handle.file.read(buffer, offset, 0, clampedLength).coerceAtLeast(0)
            } catch (t: Throwable) {
                if (!isRetryableSmbTransportFailure(t)) {
                    activeHandles.remove(handleId, handle)
                    closeSmbAvioHandleResources(handle)
                    throw t
                }
                closeSmbAvioHandleResources(handle)
                val reopened = try {
                    openSmbAvioFile(handle.spec, handle.remotePath)
                } catch (reopenFailure: Throwable) {
                    activeHandles.remove(handleId, handle)
                    throw reopenFailure
                }
                handle.replace(reopened)
                return try {
                    handle.file.read(buffer, offset, 0, clampedLength).coerceAtLeast(0)
                } catch (retryFailure: Throwable) {
                    activeHandles.remove(handleId, handle)
                    closeSmbAvioHandleResources(handle)
                    throw retryFailure
                }
            }
        }
    }

    fun getHandleSize(handleId: Long): Long {
        return activeHandles[handleId]?.sizeBytes
            ?: throw IllegalStateException("SMB AVIO handle is not open")
    }

    fun closeHandle(handleId: Long) {
        val handle = activeHandles.remove(handleId) ?: return
        synchronized(handle.lock) {
            closeSmbAvioHandleResources(handle)
        }
    }
}
