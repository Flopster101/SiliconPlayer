package com.flopster101.siliconplayer

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

internal suspend fun downloadSmbSourceToCache(
    context: Context,
    sourceId: String,
    spec: SmbSourceSpec,
    onStatus: suspend (RemoteLoadUiState) -> Unit
): RemoteDownloadResult = withContext(Dispatchers.IO) {
    val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
    val target = remoteCacheFileForSource(cacheRoot, sourceId)
    val temp = File(target.absolutePath + ".part")

    suspend fun emitStatus(state: RemoteLoadUiState) {
        withContext(Dispatchers.Main.immediate) {
            onStatus(state)
        }
    }

    findExistingCachedFileForSource(cacheRoot, sourceId)?.let { existing ->
        existing.setLastModified(System.currentTimeMillis())
        rememberSourceForCachedFile(cacheRoot, existing.name, sourceId)
        return@withContext RemoteDownloadResult(file = existing)
    }

    val remotePath = spec.path?.trim().orEmpty()
    if (remotePath.isBlank()) {
        return@withContext RemoteDownloadResult(
            file = null,
            errorMessage = "SMB share must point to a file path inside the share"
        )
    }

    emitStatus(
        RemoteLoadUiState(
            sourceId = sourceId,
            phase = RemoteLoadPhase.Connecting,
            indeterminate = true
        )
    )

    return@withContext try {
        SMBClient().use { smbClient ->
            smbClient.connect(spec.host).use { connection ->
                authenticateSmbSession(connection, spec).use { session ->
                    val share = session.connectShare(spec.share)
                    if (share !is DiskShare) {
                        share.close()
                        return@withContext RemoteDownloadResult(
                            file = null,
                            errorMessage = "SMB share is not a disk share"
                        )
                    }
                    share.use { diskShare ->
                        diskShare.openFile(
                            remotePath,
                            setOf(AccessMask.GENERIC_READ),
                            null,
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OPEN,
                            null
                        ).use { smbFile ->
                            val expectedBytes = runCatching {
                                smbFile.getFileInformation(FileStandardInformation::class.java)
                                    .getEndOfFile()
                                    .coerceAtLeast(0L)
                            }.getOrNull()?.takeIf { it > 0L }
                            BufferedInputStream(smbFile.inputStream).use { input ->
                                FileOutputStream(temp).use { output ->
                                    val buffer = ByteArray(16 * 1024)
                                    var totalBytes = 0L
                                    var latestBytesPerSecond: Long? = null
                                    val startedAtMs = System.currentTimeMillis()
                                    var lastSpeedSampleBytes = 0L
                                    var lastSpeedSampleMs = startedAtMs
                                    var lastUiUpdateMs = 0L

                                    suspend fun publishDownloadStatus(force: Boolean = false) {
                                        val now = System.currentTimeMillis()
                                        if (!force && now - lastUiUpdateMs < 120L) return
                                        lastUiUpdateMs = now
                                        val percentValue = expectedBytes?.let { expected ->
                                            ((totalBytes * 100L) / expected).toInt().coerceIn(0, 100)
                                        }
                                        emitStatus(
                                            RemoteLoadUiState(
                                                sourceId = sourceId,
                                                phase = RemoteLoadPhase.Downloading,
                                                downloadedBytes = totalBytes,
                                                totalBytes = expectedBytes,
                                                bytesPerSecond = latestBytesPerSecond,
                                                percent = percentValue,
                                                indeterminate = expectedBytes == null
                                            )
                                        )
                                    }

                                    publishDownloadStatus(force = true)
                                    while (true) {
                                        kotlin.coroutines.coroutineContext.ensureActive()
                                        val read = input.read(buffer)
                                        if (read <= 0) break
                                        output.write(buffer, 0, read)
                                        totalBytes += read
                                        val now = System.currentTimeMillis()
                                        val deltaMs = now - lastSpeedSampleMs
                                        if (deltaMs >= 350L) {
                                            val deltaBytes = totalBytes - lastSpeedSampleBytes
                                            latestBytesPerSecond =
                                                ((deltaBytes * 1000L) / deltaMs).coerceAtLeast(0L)
                                            lastSpeedSampleBytes = totalBytes
                                            lastSpeedSampleMs = now
                                        }
                                        publishDownloadStatus()
                                    }
                                    output.flush()
                                    publishDownloadStatus(force = true)
                                    val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                                    val avgSpeed = (totalBytes * 1000L) / elapsedMs
                                    emitStatus(
                                        RemoteLoadUiState(
                                            sourceId = sourceId,
                                            phase = RemoteLoadPhase.Downloading,
                                            downloadedBytes = totalBytes,
                                            totalBytes = expectedBytes,
                                            bytesPerSecond = latestBytesPerSecond ?: avgSpeed,
                                            percent = expectedBytes?.let { 100 },
                                            indeterminate = expectedBytes == null
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (temp.length() <= 0L) {
            temp.delete()
            return@withContext RemoteDownloadResult(
                file = null,
                errorMessage = "Downloaded 0 bytes from SMB share"
            )
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        rememberSourceForCachedFile(cacheRoot, target.name, sourceId)
        RemoteDownloadResult(file = target)
    } catch (_: CancellationException) {
        RemoteDownloadResult(file = null, errorMessage = "Cancelled", cancelled = true)
    } catch (t: Throwable) {
        RemoteDownloadResult(
            file = null,
            errorMessage = "${t::class.java.simpleName}: ${t.message ?: "unknown error"}"
        )
    } finally {
        if (temp.exists() && target.length() <= 0L) {
            temp.delete()
        }
    }
}
