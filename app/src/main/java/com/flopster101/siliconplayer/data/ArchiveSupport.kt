package com.flopster101.siliconplayer.data

import android.net.Uri
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max
import java.util.zip.ZipFile

private const val ARCHIVE_MOUNT_ROOT_DIR = "archive_mounts"
private const val ARCHIVE_READY_MARKER = ".ready"
private const val MAX_ARCHIVE_ENTRIES = 20_000
private const val MAX_ARCHIVE_TOTAL_UNCOMPRESSED_BYTES = 1_000_000_000L // ~1 GB
private const val MAX_ARCHIVE_ENTRY_UNCOMPRESSED_BYTES = 256_000_000L // ~256 MB
private const val ARCHIVE_SOURCE_SCHEME = "archive"

internal const val ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT = 24
internal const val ARCHIVE_CACHE_MAX_BYTES_DEFAULT = 2L * 1024L * 1024L * 1024L // 2 GB
internal const val ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT = 14

internal data class ArchiveSourceRef(
    val archivePath: String,
    val entryPath: String
)

internal data class ResolvedArchiveDirectory(
    val archivePath: String,
    val parentPath: String,
    val mountDirectory: File,
    val targetDirectory: File
)

internal data class ArchiveMountCacheEntry(
    val directory: File,
    val readyMarker: File,
    val sizeBytes: Long,
    val lastAccessTimeMs: Long
)

internal data class ArchiveMountCachePruneResult(
    val deletedMounts: Int,
    val freedBytes: Long
)

internal data class ArchiveMountCacheClearResult(
    val deletedMounts: Int,
    val freedBytes: Long
)

internal fun archiveMountRoot(cacheDir: File): File = File(cacheDir, ARCHIVE_MOUNT_ROOT_DIR)

internal fun isSupportedArchive(file: File): Boolean {
    return file.isFile && file.extension.lowercase(Locale.ROOT) == "zip"
}

internal fun ensureArchiveMounted(context: Context, archiveFile: File): File {
    require(isSupportedArchive(archiveFile)) { "Unsupported archive: ${archiveFile.absolutePath}" }
    val mountRoot = archiveMountRoot(context.cacheDir)
    if (!mountRoot.exists()) {
        mountRoot.mkdirs()
    }
    val sourceStamp = "${archiveFile.absolutePath}|${archiveFile.lastModified()}|${archiveFile.length()}"
    val mountDir = File(mountRoot, sha1Hex(sourceStamp))
    val readyMarker = File(mountDir, ARCHIVE_READY_MARKER)
    if (mountDir.exists() && readyMarker.exists()) {
        touchArchiveMountMarker(readyMarker)
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
        touchArchiveMountMarker(readyMarker)
        return mountDir
    } catch (t: Throwable) {
        mountDir.deleteRecursively()
        throw t
    }
}

internal fun buildArchiveSourceId(
    archivePath: String,
    entryRelativePath: String
): String {
    val encodedArchive = Uri.encode(archivePath)
    val normalizedEntry = entryRelativePath.replace('\\', '/').trimStart('/')
    val encodedEntry = Uri.encode(normalizedEntry)
    return "$ARCHIVE_SOURCE_SCHEME://$encodedArchive#$encodedEntry"
}

internal fun parseArchiveSourceId(sourceId: String?): ArchiveSourceRef? {
    if (sourceId.isNullOrBlank()) return null
    val trimmed = sourceId.trim()
    if (!trimmed.startsWith("$ARCHIVE_SOURCE_SCHEME://")) return null
    val body = trimmed.removePrefix("$ARCHIVE_SOURCE_SCHEME://")
    val hashIndex = body.indexOf('#')
    if (hashIndex <= 0 || hashIndex == body.lastIndex) return null
    val archiveEncoded = body.substring(0, hashIndex)
    val entryEncoded = body.substring(hashIndex + 1)
    val archivePath = Uri.decode(archiveEncoded).trim()
    val entryPath = Uri.decode(entryEncoded).replace('\\', '/').trimStart('/')
    if (archivePath.isBlank() || entryPath.isBlank()) return null
    return ArchiveSourceRef(
        archivePath = archivePath,
        entryPath = entryPath
    )
}

internal fun resolveArchiveSourceToMountedFile(
    context: Context,
    sourceId: String?
): File? {
    val parsed = parseArchiveSourceId(sourceId) ?: return null
    val archiveFile = File(parsed.archivePath)
    if (!isSupportedArchive(archiveFile)) return null
    if (!archiveFile.exists() || !archiveFile.isFile) return null
    val mountDir = ensureArchiveMounted(context, archiveFile)
    val targetFile = File(mountDir, parsed.entryPath)
    val mountCanonicalPrefix = mountDir.canonicalPath + File.separator
    val targetCanonical = targetFile.canonicalPath
    if (targetCanonical != mountDir.canonicalPath &&
        !targetCanonical.startsWith(mountCanonicalPrefix)
    ) {
        return null
    }
    if (!targetFile.exists() || !targetFile.isFile) return null
    return targetFile
}

internal fun parseArchiveLogicalPath(path: String?): Pair<String, String?>? {
    val raw = path?.trim().orEmpty()
    if (raw.isBlank()) return null
    val lower = raw.lowercase(Locale.ROOT)
    val zipIndex = lower.indexOf(".zip")
    if (zipIndex <= 0) return null
    val afterZip = zipIndex + 4
    if (afterZip < raw.length && raw[afterZip] != '/') return null
    val archivePath = raw.substring(0, afterZip)
    val archiveEntryPath = raw.substring(afterZip).trimStart('/').replace('\\', '/').trimStart('/')
        .takeIf { it.isNotBlank() }
    return archivePath to archiveEntryPath
}

internal fun isArchiveLogicalFolderPath(path: String?): Boolean {
    return parseArchiveLogicalPath(path) != null
}

internal fun resolveArchiveLogicalDirectory(
    context: Context,
    logicalPath: String?
): ResolvedArchiveDirectory? {
    val parsed = parseArchiveLogicalPath(logicalPath) ?: return null
    val archiveFile = File(parsed.first)
    if (!isSupportedArchive(archiveFile) || !archiveFile.exists()) return null
    val mountDir = ensureArchiveMounted(context, archiveFile)
    val targetDirectory = parsed.second?.let { entryPath ->
        File(mountDir, entryPath)
    } ?: mountDir

    val mountCanonicalPrefix = mountDir.canonicalPath + File.separator
    val targetCanonical = targetDirectory.canonicalPath
    if (targetCanonical != mountDir.canonicalPath &&
        !targetCanonical.startsWith(mountCanonicalPrefix)
    ) {
        return null
    }
    if (!targetDirectory.exists() || !targetDirectory.isDirectory) return null

    return ResolvedArchiveDirectory(
        archivePath = archiveFile.absolutePath,
        parentPath = archiveFile.parentFile?.absolutePath ?: return null,
        mountDirectory = mountDir,
        targetDirectory = targetDirectory
    )
}

internal fun clearArchiveMountCache(cacheDir: File): ArchiveMountCacheClearResult {
    val mountRoot = archiveMountRoot(cacheDir)
    if (!mountRoot.exists()) {
        return ArchiveMountCacheClearResult(
            deletedMounts = 0,
            freedBytes = 0L
        )
    }
    var deletedMounts = 0
    var freedBytes = 0L
    mountRoot.listFiles().orEmpty()
        .filter { it.isDirectory }
        .forEach { mountDir ->
            val bytes = directorySizeBytes(mountDir)
            if (mountDir.deleteRecursively()) {
                deletedMounts += 1
                freedBytes += bytes
            } else {
                mountDir.deleteOnExit()
            }
        }
    return ArchiveMountCacheClearResult(
        deletedMounts = deletedMounts,
        freedBytes = freedBytes
    )
}

internal fun enforceArchiveMountCacheLimits(
    cacheDir: File,
    maxMounts: Int,
    maxBytes: Long,
    maxAgeDays: Int
): ArchiveMountCachePruneResult {
    val mountRoot = archiveMountRoot(cacheDir)
    if (!mountRoot.exists()) {
        return ArchiveMountCachePruneResult(
            deletedMounts = 0,
            freedBytes = 0L
        )
    }
    val normalizedMaxMounts = max(maxMounts, 1)
    val normalizedMaxBytes = max(maxBytes, 1L)
    val normalizedMaxAgeDays = max(maxAgeDays, 1)

    val now = System.currentTimeMillis()
    val cutoff = now - (normalizedMaxAgeDays.toLong() * 24L * 60L * 60L * 1000L)
    val entries = listArchiveMountCacheEntries(mountRoot)
    if (entries.isEmpty()) {
        return ArchiveMountCachePruneResult(
            deletedMounts = 0,
            freedBytes = 0L
        )
    }

    var deletedMounts = 0
    var freedBytes = 0L
    val survivors = mutableListOf<ArchiveMountCacheEntry>()
    entries.forEach { entry ->
        if (entry.lastAccessTimeMs <= cutoff && entry.directory.deleteRecursively()) {
            deletedMounts += 1
            freedBytes += entry.sizeBytes
        } else {
            survivors.add(entry)
        }
    }

    var totalBytes = survivors.sumOf { it.sizeBytes }
    var totalMounts = survivors.size
    survivors.sortBy { it.lastAccessTimeMs }
    for (entry in survivors) {
        if (totalMounts <= normalizedMaxMounts && totalBytes <= normalizedMaxBytes) break
        if (entry.directory.deleteRecursively()) {
            deletedMounts += 1
            freedBytes += entry.sizeBytes
            totalMounts -= 1
            totalBytes = (totalBytes - entry.sizeBytes).coerceAtLeast(0L)
        } else {
            entry.directory.deleteOnExit()
        }
    }

    return ArchiveMountCachePruneResult(
        deletedMounts = deletedMounts,
        freedBytes = freedBytes
    )
}

private fun listArchiveMountCacheEntries(mountRoot: File): List<ArchiveMountCacheEntry> {
    return mountRoot.listFiles().orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { mountDir ->
            val readyMarker = File(mountDir, ARCHIVE_READY_MARKER)
            if (!readyMarker.exists() || !readyMarker.isFile) {
                mountDir.deleteRecursively()
                return@mapNotNull null
            }
            ArchiveMountCacheEntry(
                directory = mountDir,
                readyMarker = readyMarker,
                sizeBytes = directorySizeBytes(mountDir),
                lastAccessTimeMs = max(readyMarker.lastModified(), mountDir.lastModified())
            )
        }
}

private fun touchArchiveMountMarker(marker: File) {
    val now = System.currentTimeMillis()
    marker.setLastModified(now)
    marker.parentFile?.setLastModified(now)
}

private fun directorySizeBytes(directory: File): Long {
    if (!directory.exists()) return 0L
    return directory.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length().coerceAtLeast(0L) }
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
