package com.flopster101.siliconplayer.playback

import com.flopster101.siliconplayer.data.FileRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal suspend fun loadPlayableSiblingFilesForExternalIntent(
    repository: FileRepository,
    file: File,
    timeoutMs: Long = 15_000L
): List<File>? {
    return try {
        withTimeout(timeoutMs) {
            val parentDir = file.parentFile
            if (parentDir?.exists() != true || !parentDir.isDirectory) {
                return@withTimeout emptyList()
            }
            val loadedFiles = withContext(Dispatchers.IO) {
                repository.getFiles(parentDir)
            }
            loadedFiles
                .asSequence()
                .filter { !it.isDirectory }
                .map { it.file }
                .toList()
        }
    } catch (_: TimeoutCancellationException) {
        null
    } catch (_: Exception) {
        null
    }
}
