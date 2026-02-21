package com.flopster101.siliconplayer.data

import com.flopster101.siliconplayer.BrowserNameSortMode
import com.flopster101.siliconplayer.fileMatchesSupportedExtensions
import java.io.File

class FileRepository(
    private val supportedExtensions: Set<String>,
    private val sortArchivesBeforeFiles: Boolean,
    private val nameSortMode: BrowserNameSortMode
) {

    fun getFiles(directory: File): List<FileItem> {
        val files = directory.listFiles() ?: return emptyList()
        return files
            .filter {
                it.isDirectory ||
                    fileMatchesSupportedExtensions(it, supportedExtensions) ||
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
            }.sortedWith { left, right ->
                val rankCompare = sortRank(left.kind).compareTo(sortRank(right.kind))
                if (rankCompare != 0) {
                    return@sortedWith rankCompare
                }
                val modeCompare = when (nameSortMode) {
                    BrowserNameSortMode.Natural -> compareNaturalName(left.name, right.name)
                    BrowserNameSortMode.Lexicographic -> left.name.lowercase().compareTo(right.name.lowercase())
                }
                if (modeCompare != 0) {
                    return@sortedWith modeCompare
                }
                left.name.compareTo(right.name)
            }
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

    private fun compareNaturalName(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0
        val leftLength = left.length
        val rightLength = right.length

        while (leftIndex < leftLength && rightIndex < rightLength) {
            val leftChar = left[leftIndex]
            val rightChar = right[rightIndex]
            val leftIsDigit = leftChar.isDigit()
            val rightIsDigit = rightChar.isDigit()

            if (leftIsDigit && rightIsDigit) {
                var leftNumEnd = leftIndex
                while (leftNumEnd < leftLength && left[leftNumEnd].isDigit()) leftNumEnd++
                var rightNumEnd = rightIndex
                while (rightNumEnd < rightLength && right[rightNumEnd].isDigit()) rightNumEnd++

                var leftTrimmedStart = leftIndex
                while (leftTrimmedStart < leftNumEnd && left[leftTrimmedStart] == '0') leftTrimmedStart++
                var rightTrimmedStart = rightIndex
                while (rightTrimmedStart < rightNumEnd && right[rightTrimmedStart] == '0') rightTrimmedStart++

                val leftDigitsLength = leftNumEnd - leftTrimmedStart
                val rightDigitsLength = rightNumEnd - rightTrimmedStart
                if (leftDigitsLength != rightDigitsLength) {
                    return leftDigitsLength.compareTo(rightDigitsLength)
                }
                if (leftDigitsLength > 0) {
                    var offset = 0
                    while (offset < leftDigitsLength) {
                        val digitCompare =
                            left[leftTrimmedStart + offset].compareTo(right[rightTrimmedStart + offset])
                        if (digitCompare != 0) return digitCompare
                        offset++
                    }
                }

                val leftRawLength = leftNumEnd - leftIndex
                val rightRawLength = rightNumEnd - rightIndex
                if (leftRawLength != rightRawLength) {
                    return leftRawLength.compareTo(rightRawLength)
                }

                leftIndex = leftNumEnd
                rightIndex = rightNumEnd
                continue
            }

            val charCompare = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
            if (charCompare != 0) return charCompare

            leftIndex++
            rightIndex++
        }

        return leftLength.compareTo(rightLength)
    }
}
