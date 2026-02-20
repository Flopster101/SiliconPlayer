package com.flopster101.siliconplayer.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

private const val ARCHIVE_MOUNT_ROOT_DIR = "archive_mounts"
private const val ARCHIVE_READY_MARKER = ".ready"
private const val MAX_ARCHIVE_ENTRIES = 20_000
private const val MAX_ARCHIVE_TOTAL_UNCOMPRESSED_BYTES = 1_000_000_000L // ~1 GB
private const val MAX_ARCHIVE_ENTRY_UNCOMPRESSED_BYTES = 256_000_000L // ~256 MB

internal fun isSupportedArchive(file: File): Boolean {
    return file.isFile && file.extension.lowercase(Locale.ROOT) == "zip"
}

internal fun ensureArchiveMounted(context: Context, archiveFile: File): File {
    require(isSupportedArchive(archiveFile)) { "Unsupported archive: ${archiveFile.absolutePath}" }
    val mountRoot = File(context.cacheDir, ARCHIVE_MOUNT_ROOT_DIR)
    if (!mountRoot.exists()) {
        mountRoot.mkdirs()
    }
    val sourceStamp = "${archiveFile.absolutePath}|${archiveFile.lastModified()}|${archiveFile.length()}"
    val mountDir = File(mountRoot, sha1Hex(sourceStamp))
    val readyMarker = File(mountDir, ARCHIVE_READY_MARKER)
    if (mountDir.exists() && readyMarker.exists()) {
        return mountDir
    }

    if (mountDir.exists()) {
        mountDir.deleteRecursively()
    }
    if (!mountDir.mkdirs()) {
        error("Failed to create archive mount directory: ${mountDir.absolutePath}")
    }

    val mountCanonicalPrefix = mountDir.canonicalPath + File.separator
    var totalUncompressedBytes = 0L
    var entriesSeen = 0

    try {
        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entriesSeen += 1
                if (entriesSeen > MAX_ARCHIVE_ENTRIES) {
                    error("Archive has too many entries")
                }

                val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                if (normalizedName.isBlank()) continue
                val outputFile = File(mountDir, normalizedName)
                val outputCanonical = outputFile.canonicalPath
                if (outputCanonical != mountDir.canonicalPath &&
                    !outputCanonical.startsWith(mountCanonicalPrefix)
                ) {
                    error("Archive entry escapes mount root: $normalizedName")
                }

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                    continue
                }

                outputFile.parentFile?.mkdirs()
                val declaredSize = entry.size
                if (declaredSize > MAX_ARCHIVE_ENTRY_UNCOMPRESSED_BYTES) {
                    error("Archive entry too large: $normalizedName")
                }

                zip.getInputStream(entry).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            entryBytes += read
                            if (entryBytes > MAX_ARCHIVE_ENTRY_UNCOMPRESSED_BYTES) {
                                error("Archive entry exceeded size limit: $normalizedName")
                            }
                            totalUncompressedBytes += read
                            if (totalUncompressedBytes > MAX_ARCHIVE_TOTAL_UNCOMPRESSED_BYTES) {
                                error("Archive exceeded total size limit")
                            }
                        }
                    }
                }
            }
        }
        readyMarker.writeText(sourceStamp)
        return mountDir
    } catch (t: Throwable) {
        mountDir.deleteRecursively()
        throw t
    }
}

private fun sha1Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
    return buildString(digest.size * 2) {
        for (b in digest) {
            append(((b.toInt() ushr 4) and 0xF).toString(16))
            append((b.toInt() and 0xF).toString(16))
        }
    }
}
