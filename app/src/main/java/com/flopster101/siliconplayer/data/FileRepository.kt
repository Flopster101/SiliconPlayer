package com.flopster101.siliconplayer.data

import java.io.File

class FileRepository(
    private val supportedExtensions: Set<String>
) {

    fun getFiles(directory: File): List<FileItem> {
        val files = directory.listFiles() ?: return emptyList()
        return files
            .filter {
                it.isDirectory ||
                    it.extension.lowercase() in supportedExtensions ||
                    isSupportedArchive(it)
            }
            .map { file ->
                val isArchive = isSupportedArchive(file)
                val isDirectory = file.isDirectory || isArchive
                val kind = when {
                    file.isDirectory -> FileItem.Kind.Directory
                    isArchive -> FileItem.Kind.ArchiveZip
                    else -> FileItem.Kind.AudioFile
                }
                FileItem(
                    file = file,
                    name = file.name,
                    isDirectory = isDirectory,
                    size = if (isDirectory) 0 else file.length(),
                    kind = kind
                )
            }.sortedWith(
                compareBy<FileItem>(
                    { if (it.kind == FileItem.Kind.Directory) 0 else 1 },
                    { it.name.lowercase() }
                )
            )
    }

    fun getRootDirectory(): File {
        return android.os.Environment.getExternalStorageDirectory()
    }
}
