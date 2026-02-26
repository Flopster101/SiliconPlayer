package com.flopster101.siliconplayer.data

import java.io.File

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val kind: Kind = if (isDirectory) Kind.Directory else Kind.AudioFile
) {
    enum class Kind {
        Directory,
        AudioFile,
        ArchiveZip,
        UnsupportedFile
    }

    val isArchive: Boolean
        get() = kind == Kind.ArchiveZip
}
