package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun loadContextualPlayableFilesForManualSelection(
    repository: FileRepository,
    localFile: File
): List<File> {
    return withContext(Dispatchers.IO) {
        val parent = localFile.parentFile
        if (parent != null && parent.exists() && parent.isDirectory) {
            repository.getFiles(parent)
                .asSequence()
                .filterNot { it.isDirectory }
                .map { it.file }
                .toList()
        } else {
            listOf(localFile)
        }
    }
}
