package com.flopster101.siliconplayer.data

import android.net.Uri
import android.content.Context
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.buildHttpRequestUri
import com.flopster101.siliconplayer.buildSmbRequestUri
import com.flopster101.siliconplayer.findExistingCachedFileForSource
import com.flopster101.siliconplayer.normalizeHttpPath
import com.flopster101.siliconplayer.normalizeSourceIdentity
import com.flopster101.siliconplayer.normalizeSmbPathForShare
import com.flopster101.siliconplayer.parseHttpSourceSpecFromInput
import com.flopster101.siliconplayer.parseSmbSourceSpecFromInput
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
private const val ARCHIVE_DIRECTORY_SCHEME = "archive-dir"

internal const val ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT = 24
internal const val ARCHIVE_CACHE_MAX_BYTES_DEFAULT = 2L * 1024L * 1024L * 1024L // 2 GB
internal const val ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT = 14

internal data class ArchiveSourceRef(
    val archivePath: String,
    val entryPath: String
)

internal data class ArchiveMountedPathOrigin(
    val mountRootPath: String,
    val archivePath: String,
    val parentPath: String
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

    val mountCanonical = mountDir.canonicalPath
    val mountCanonicalPrefix = mountCanonical + File.separator
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
                if (!outputFile.exists()) {
                    outputFile.createNewFile()
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

internal fun buildArchiveDirectoryPath(
    archivePath: String,
    inArchiveDirectoryPath: String? = null
): String {
    val encodedArchive = Uri.encode(archivePath)
    val normalizedDirectory = inArchiveDirectoryPath
        ?.replace('\\', '/')
        ?.trim('/')
        ?.takeIf { it.isNotBlank() }
    return if (normalizedDirectory == null) {
        "$ARCHIVE_DIRECTORY_SCHEME://$encodedArchive"
    } else {
        "$ARCHIVE_DIRECTORY_SCHEME://$encodedArchive#${Uri.encode(normalizedDirectory)}"
    }
}

private fun parseArchiveDirectoryPath(path: String?): Pair<String, String?>? {
    val raw = path?.trim().orEmpty()
    if (!raw.startsWith("$ARCHIVE_DIRECTORY_SCHEME://")) return null
    val body = raw.removePrefix("$ARCHIVE_DIRECTORY_SCHEME://")
    if (body.isBlank()) return null
    val hashIndex = body.indexOf('#')
    val archiveEncoded = if (hashIndex >= 0) body.substring(0, hashIndex) else body
    val dirEncoded = if (hashIndex >= 0 && hashIndex < body.lastIndex) {
        body.substring(hashIndex + 1)
    } else {
        null
    }
    val archivePath = Uri.decode(archiveEncoded).trim().takeIf { it.isNotBlank() } ?: return null
    val directoryPath = dirEncoded
        ?.let(Uri::decode)
        ?.replace('\\', '/')
        ?.trim('/')
        ?.takeIf { it.isNotBlank() }
    return archivePath to directoryPath
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
    val archiveFile = resolveArchiveFileForLocation(context, parsed.archivePath) ?: return null
    if (!isSupportedArchive(archiveFile) || !archiveFile.exists() || !archiveFile.isFile) return null
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
    ensureArchiveEntryExtracted(
        archiveFile = archiveFile,
        mountDir = mountDir,
        entryPath = parsed.entryPath,
        outputFile = targetFile
    )
    return targetFile
}

internal fun resolveArchiveMountedCompanionPath(
    basePath: String?,
    requestedPath: String?
): String? {
    val base = basePath?.trim().orEmpty().takeIf { it.isNotBlank() } ?: return null
    val requestedRaw = requestedPath?.trim().orEmpty().takeIf { it.isNotBlank() } ?: return null
    val baseFile = File(base)
    val mountRoot = findArchiveMountRoot(baseFile) ?: return null
    val mountCanonical = mountRoot.canonicalPath
    val mountCanonicalPrefix = "$mountCanonical${File.separator}"

    val resolvedRequested = runCatching {
        val normalizedRequested = requestedRaw.replace('\\', '/')
        val requestedAsFile = File(normalizedRequested)
        val candidate = if (requestedAsFile.isAbsolute) {
            requestedAsFile
        } else {
            File(baseFile.parentFile ?: mountRoot, normalizedRequested)
        }
        candidate.canonicalFile
    }.getOrNull() ?: return null

    val requestedCanonical = resolvedRequested.canonicalPath
    if (requestedCanonical != mountCanonical && !requestedCanonical.startsWith(mountCanonicalPrefix)) {
        return null
    }
    if (resolvedRequested.isDirectory) {
        return null
    }

    val readyMarker = File(mountRoot, ARCHIVE_READY_MARKER)
    if (!readyMarker.exists() || !readyMarker.isFile) {
        return null
    }
    val archivePath = parseArchivePathFromReadyMarker(readyMarker) ?: return null
    val archiveFile = File(archivePath)
    if (!isSupportedArchive(archiveFile) || !archiveFile.exists() || !archiveFile.isFile) {
        return null
    }

    val relativeEntryPath = requestedCanonical
        .removePrefix(mountCanonical)
        .trimStart(File.separatorChar)
        .replace('\\', '/')
        .trimStart('/')
    if (relativeEntryPath.isBlank()) {
        return null
    }

    ensureArchiveEntryExtracted(
        archiveFile = archiveFile,
        mountDir = mountRoot,
        entryPath = relativeEntryPath,
        outputFile = resolvedRequested
    )
    touchArchiveMountMarker(readyMarker)
    return if (resolvedRequested.exists() && resolvedRequested.isFile) {
        resolvedRequested.absolutePath
    } else {
        null
    }
}

internal fun resolveArchiveMountedPathOrigin(path: File): ArchiveMountedPathOrigin? {
    val mountRoot = findArchiveMountRoot(path) ?: return null
    val readyMarker = File(mountRoot, ARCHIVE_READY_MARKER)
    if (!readyMarker.exists() || !readyMarker.isFile) return null
    val archivePath = parseArchivePathFromReadyMarker(readyMarker) ?: return null
    val parentPath = File(archivePath).parentFile?.absolutePath ?: return null
    return ArchiveMountedPathOrigin(
        mountRootPath = mountRoot.absolutePath,
        archivePath = archivePath,
        parentPath = parentPath
    )
}

internal fun parseArchiveLogicalPath(path: String?): Pair<String, String?>? {
    return parseArchiveDirectoryPath(path)
}

internal fun isArchiveLogicalFolderPath(path: String?): Boolean {
    return parseArchiveLogicalPath(path) != null
}

internal fun resolveArchiveLogicalDirectory(
    context: Context,
    logicalPath: String?
): ResolvedArchiveDirectory? {
    val parsed = parseArchiveLogicalPath(logicalPath) ?: return null
    val archiveLocation = parsed.first
    val archiveFile = resolveArchiveFileForLocation(context, archiveLocation) ?: return null
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
        archivePath = archiveLocation,
        parentPath = resolveArchiveContainerParentLocation(archiveLocation)
            ?: archiveFile.parentFile?.absolutePath
            ?: return null,
        mountDirectory = mountDir,
        targetDirectory = targetDirectory
    )
}

internal fun resolveArchiveContainerParentLocation(archiveLocation: String): String? {
    val trimmed = archiveLocation.trim()
    if (trimmed.isBlank()) return null
    parseSmbSourceSpecFromInput(trimmed)?.let { smbSpec ->
        if (smbSpec.share.isBlank()) return null
        val normalizedPath = normalizeSmbPathForShare(smbSpec.path).orEmpty()
        val parentPath = normalizedPath
            .substringBeforeLast('/', missingDelimiterValue = "")
            .trim()
            .ifBlank { null }
        return buildSmbRequestUri(smbSpec.copy(path = parentPath))
    }
    parseHttpSourceSpecFromInput(trimmed)?.let { httpSpec ->
        val normalizedPath = normalizeHttpPath(httpSpec.path)
        val parentPath = normalizedPath
            .trimEnd('/')
            .substringBeforeLast('/', missingDelimiterValue = "")
            .trim()
        val parentSpec = httpSpec.copy(
            path = if (parentPath.isBlank()) "/" else "$parentPath/",
            query = null
        )
        return buildHttpRequestUri(parentSpec)
    }
    val asFileUri = Uri.parse(trimmed).takeIf { it.scheme?.lowercase(Locale.ROOT) == "file" }?.path
    val localPath = asFileUri ?: trimmed
    return File(localPath).parentFile?.absolutePath
}

private fun resolveArchiveFileForLocation(context: Context, archiveLocation: String): File? {
    val trimmed = archiveLocation.trim()
    if (trimmed.isBlank()) return null
    val normalized = normalizeSourceIdentity(trimmed) ?: trimmed
    val normalizedUri = Uri.parse(normalized)
    val scheme = normalizedUri.scheme?.lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https" || scheme == "smb") {
        val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
        return findExistingCachedFileForSource(cacheRoot, normalized)
    }
    val path = if (scheme == "file") {
        normalizedUri.path?.trim()
    } else {
        normalized
    }
    val candidate = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
    return candidate.takeIf { it.exists() && it.isFile }
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

private fun findArchiveMountRoot(file: File): File? {
    var current: File? = runCatching { file.canonicalFile }.getOrNull()
    while (current != null) {
        val marker = File(current, ARCHIVE_READY_MARKER)
        if (marker.exists() && marker.isFile) {
            return current
        }
        current = current.parentFile
    }
    return null
}

private fun parseArchivePathFromReadyMarker(marker: File): String? {
    val stamp = runCatching { marker.readText() }.getOrNull()?.trim().orEmpty()
    if (stamp.isBlank()) return null
    val lastSep = stamp.lastIndexOf('|')
    if (lastSep <= 0) return null
    val secondLastSep = stamp.lastIndexOf('|', startIndex = lastSep - 1)
    if (secondLastSep <= 0) return null
    return stamp.substring(0, secondLastSep).trim().takeIf { it.isNotBlank() }
}

private fun ensureArchiveEntryExtracted(
    archiveFile: File,
    mountDir: File,
    entryPath: String,
    outputFile: File
) {
    // Placeholder files are zero-byte. Non-empty files are already extracted.
    if (outputFile.length() > 0L) return

    val normalizedEntryPath = entryPath.replace('\\', '/').trimStart('/')
    val mountCanonical = mountDir.canonicalPath
    val mountCanonicalPrefix = mountCanonical + File.separator
    val outputCanonical = outputFile.canonicalPath
    if (outputCanonical != mountCanonical && !outputCanonical.startsWith(mountCanonicalPrefix)) {
        error("Archive entry escapes mount root: $normalizedEntryPath")
    }

    ZipFile(archiveFile).use { zip ->
        val zipEntry = zip.getEntry(normalizedEntryPath)
            ?: error("Missing archive entry: $normalizedEntryPath")
        if (zipEntry.isDirectory) {
            error("Archive entry is a directory: $normalizedEntryPath")
        }
        val declaredSize = zipEntry.size
        if (declaredSize > MAX_ARCHIVE_ENTRY_UNCOMPRESSED_BYTES) {
            error("Archive entry too large: $normalizedEntryPath")
        }

        outputFile.parentFile?.mkdirs()
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.extracting")
        zip.getInputStream(zipEntry).use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var entryBytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    entryBytes += read
                    if (entryBytes > MAX_ARCHIVE_ENTRY_UNCOMPRESSED_BYTES) {
                        error("Archive entry exceeded size limit: $normalizedEntryPath")
                    }
                }
            }
        }
        if (outputFile.exists()) {
            outputFile.delete()
        }
        if (!tempFile.renameTo(outputFile)) {
            tempFile.delete()
            error("Failed to finalize extracted entry: $normalizedEntryPath")
        }
    }
}
