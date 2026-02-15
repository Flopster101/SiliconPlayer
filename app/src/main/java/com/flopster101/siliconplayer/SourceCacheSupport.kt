package com.flopster101.siliconplayer

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest

internal const val REMOTE_SOURCE_CACHE_DIR = "remote_sources"
internal const val SOURCE_CACHE_MAX_TRACKS_DEFAULT = 100
internal const val SOURCE_CACHE_MAX_BYTES_DEFAULT = 1024L * 1024L * 1024L

internal data class RemoteCachePruneResult(
    val deletedFiles: Int,
    val freedBytes: Long
)

internal data class RemoteCacheClearResult(
    val deletedFiles: Int,
    val skippedFiles: Int,
    val freedBytes: Long
)

internal fun sha1Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun sanitizeRemoteLeafName(raw: String?): String? {
    val trimmed = raw?.trim()?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: return null
    return trimmed.replace(Regex("""[\\/:*?"<>|]"""), "_")
}

internal fun stripUrlFragment(url: String): String {
    val parsed = Uri.parse(url)
    if (parsed.fragment.isNullOrBlank()) return url
    return parsed.buildUpon().fragment(null).build().toString()
}

internal fun remoteFilenameHintFromUri(uri: Uri): String? {
    val fragmentHint = sanitizeRemoteLeafName(uri.fragment)
        ?.takeIf { it.contains('.') }
    if (fragmentHint != null) return fragmentHint

    val queryHint = listOf("filename", "file", "name")
        .firstNotNullOfOrNull { key ->
            sanitizeRemoteLeafName(uri.getQueryParameter(key))
                ?.takeIf { it.contains('.') }
        }
    if (queryHint != null) return queryHint

    return sanitizeRemoteLeafName(uri.lastPathSegment)
}

internal fun filenameFromContentDisposition(headerValue: String?): String? {
    if (headerValue.isNullOrBlank()) return null
    val filenameStar = Regex("""filename\*\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
        .find(headerValue)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.trim('"')
        ?.let { value ->
            value.substringAfter("''", value)
        }
    if (!filenameStar.isNullOrBlank()) {
        return try {
            sanitizeRemoteLeafName(URLDecoder.decode(filenameStar, "UTF-8"))
        } catch (_: Throwable) {
            sanitizeRemoteLeafName(filenameStar)
        }
    }

    val filename = Regex("""filename\s*=\s*("?)([^";]+)\1""", RegexOption.IGNORE_CASE)
        .find(headerValue)
        ?.groupValues
        ?.getOrNull(2)
    return sanitizeRemoteLeafName(filename)
}

internal fun remoteCacheFileForSource(cacheRoot: File, url: String): File {
    if (!cacheRoot.exists()) {
        cacheRoot.mkdirs()
    }
    val uri = Uri.parse(url)
    val safeLeaf = remoteFilenameHintFromUri(uri)
        ?: sanitizeRemoteLeafName(uri.host)
        ?: "remote"
    return File(cacheRoot, "${sha1Hex(url)}_$safeLeaf")
}

internal fun findExistingCachedFileForSource(cacheRoot: File, url: String): File? {
    if (!cacheRoot.exists()) return null
    val prefix = "${sha1Hex(url)}_"
    return cacheRoot.listFiles().orEmpty()
        .firstOrNull { it.isFile && it.name.startsWith(prefix) && !it.name.endsWith(".part") && it.length() > 0L }
}

internal fun isRemoteSourceCached(context: Context, url: String): Boolean {
    val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
    return findExistingCachedFileForSource(cacheRoot, url) != null
}

internal fun enforceRemoteCacheLimits(
    cacheRoot: File,
    maxTracks: Int,
    maxBytes: Long,
    protectedPaths: Set<String> = emptySet()
): RemoteCachePruneResult {
    if (!cacheRoot.exists()) return RemoteCachePruneResult(0, 0L)

    val normalizedMaxTracks = maxTracks.coerceAtLeast(1)
    val normalizedMaxBytes = maxBytes.coerceAtLeast(1L)
    val protected = protectedPaths.filter { it.isNotBlank() }.toSet()

    val entries = cacheRoot.listFiles().orEmpty()
        .filter { it.isFile && !it.name.endsWith(".part", ignoreCase = true) }
        .toMutableList()
    if (entries.isEmpty()) return RemoteCachePruneResult(0, 0L)

    var totalBytes = entries.sumOf { it.length().coerceAtLeast(0L) }
    var totalCount = entries.size
    var deletedFiles = 0
    var freedBytes = 0L

    entries.sortBy { it.lastModified() }
    for (file in entries) {
        if (totalCount <= normalizedMaxTracks && totalBytes <= normalizedMaxBytes) break
        if (protected.contains(file.absolutePath)) continue
        val size = file.length().coerceAtLeast(0L)
        if (file.delete()) {
            deletedFiles++
            freedBytes += size
            totalCount--
            totalBytes = (totalBytes - size).coerceAtLeast(0L)
        } else {
            file.deleteOnExit()
        }
    }

    return RemoteCachePruneResult(deletedFiles, freedBytes)
}

internal fun clearRemoteCacheFiles(
    cacheRoot: File,
    protectedPaths: Set<String> = emptySet()
): RemoteCacheClearResult {
    if (!cacheRoot.exists()) return RemoteCacheClearResult(0, 0, 0L)
    val protected = protectedPaths.filter { it.isNotBlank() }.toSet()
    var deletedFiles = 0
    var skippedFiles = 0
    var freedBytes = 0L
    cacheRoot.listFiles().orEmpty().forEach { file ->
        if (!file.isFile) {
            file.deleteRecursively()
            return@forEach
        }
        if (protected.contains(file.absolutePath)) {
            skippedFiles++
            return@forEach
        }
        val size = file.length().coerceAtLeast(0L)
        if (file.delete()) {
            deletedFiles++
            freedBytes += size
        } else {
            file.deleteOnExit()
        }
    }
    return RemoteCacheClearResult(deletedFiles, skippedFiles, freedBytes)
}
