package com.flopster101.siliconplayer

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import java.io.IOException

private data class OpenedSmbProgressiveTransportFile(
    val connection: Connection,
    val session: Session,
    val share: DiskShare,
    val file: SmbFile,
    val sizeBytes: Long
)

private fun closeOpenedSmbProgressiveTransportFile(openedFile: OpenedSmbProgressiveTransportFile) {
    runCatching { openedFile.file.close() }
    runCatching { openedFile.share.close() }
    runCatching { openedFile.session.close() }
    runCatching { openedFile.connection.close() }
}

private fun openSmbProgressiveTransportFile(
    spec: SmbSourceSpec,
    remotePath: String
): OpenedSmbProgressiveTransportFile {
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
                return OpenedSmbProgressiveTransportFile(
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

internal class SmbProgressiveRandomAccessTransport(
    private val spec: SmbSourceSpec,
    private val remotePath: String
) : ProgressiveRandomAccessTransport {
    private val lock = Any()
    @Volatile
    private var cancelled = false
    @Volatile
    private var openedFile = openSmbProgressiveTransportFile(spec, remotePath)

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
            check(!cancelled) { "SMB AVIO transport cancelled" }
            if (offset >= openedFile.sizeBytes) {
                return 0
            }
            return try {
                openedFile.file.read(buffer, offset, bufferOffset, clampedLength).coerceAtLeast(0)
            } catch (t: Throwable) {
                if (cancelled) {
                    closeOpenedSmbProgressiveTransportFile(openedFile)
                    throw IOException("SMB AVIO transport cancelled", t)
                }
                if (!isRetryableSmbTransportFailure(t)) {
                    closeOpenedSmbProgressiveTransportFile(openedFile)
                    throw t
                }
                closeOpenedSmbProgressiveTransportFile(openedFile)
                if (cancelled) {
                    throw IOException("SMB AVIO transport cancelled", t)
                }
                val reopened = openSmbProgressiveTransportFile(spec, remotePath)
                if (cancelled) {
                    closeOpenedSmbProgressiveTransportFile(reopened)
                    throw IOException("SMB AVIO transport cancelled", t)
                }
                openedFile = reopened
                try {
                    reopened.file.read(buffer, offset, bufferOffset, clampedLength).coerceAtLeast(0)
                } catch (retryFailure: Throwable) {
                    closeOpenedSmbProgressiveTransportFile(reopened)
                    throw retryFailure
                }
            }
        }
    }

    override fun cancel() {
        cancelled = true
        closeOpenedSmbProgressiveTransportFile(openedFile)
    }

    override fun close() {
        cancel()
    }
}
