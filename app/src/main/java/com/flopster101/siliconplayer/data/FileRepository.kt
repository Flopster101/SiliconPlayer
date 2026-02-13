package com.flopster101.siliconplayer.data

import java.io.File

class FileRepository(
    private val supportedExtensions: Set<String>
) {

    fun getFiles(directory: File): List<FileItem> {
        val files = directory.listFiles() ?: return emptyList()
        return files
            .filter { it.isDirectory || it.extension.lowercase() in supportedExtensions }
            .map { file ->
                FileItem(
                    file = file,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = if (file.isDirectory) 0 else file.length()
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun getRootDirectory(): File {
        return android.os.Environment.getExternalStorageDirectory()
    }
}
