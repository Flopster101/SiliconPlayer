package com.flopster101.siliconplayer.data

import java.io.File

class FileRepository(
    private val supportedExtensions: Set<String>,
    private val sortArchivesBeforeFiles: Boolean
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
                    size = when (kind) {
                        FileItem.Kind.Directory -> 0L
                        FileItem.Kind.ArchiveZip -> file.length()
                        FileItem.Kind.AudioFile -> file.length()
                    },
                    kind = kind
                )
            }.sortedWith(
                compareBy<FileItem>(
                    { sortRank(it.kind) },
                    { it.name.lowercase() }
                )
            )
    }

    private fun sortRank(kind: FileItem.Kind): Int {
        return when {
            kind == FileItem.Kind.Directory -> 0
            sortArchivesBeforeFiles && kind == FileItem.Kind.ArchiveZip -> 1
            sortArchivesBeforeFiles && kind == FileItem.Kind.AudioFile -> 2
            else -> 1
        }
    }

    fun getRootDirectory(): File {
        return android.os.Environment.getExternalStorageDirectory()
    }
}
