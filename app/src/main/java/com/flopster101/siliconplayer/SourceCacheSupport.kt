package com.flopster101.siliconplayer

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest

internal const val REMOTE_SOURCE_CACHE_DIR = "remote_sources"
internal const val SOURCE_CACHE_MAX_TRACKS_DEFAULT = 100
internal const val SOURCE_CACHE_MAX_BYTES_DEFAULT = 1024L * 1024L * 1024L
private const val SOURCE_CACHE_INDEX_FILE = ".source_index.json"

internal data class RemoteCachePruneResult(
    val deletedFiles: Int,
    val freedBytes: Long
)

internal data class RemoteCacheClearResult(
    val deletedFiles: Int,
    val skippedFiles: Int,
    val freedBytes: Long
)

internal data class RemoteCacheDeleteResult(
    val deletedFiles: Int,
    val skippedFiles: Int,
    val missingFiles: Int,
    val freedBytes: Long
)

internal data class CachedSourceFile(
    val absolutePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val sourceId: String?
)

internal fun sha1Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun sanitizeRemoteLeafName(raw: String?): String? {
    val trimmed = raw?.trim()?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: return null
    return trimmed.replace(Regex("""[\\/:*?"<>|]"""), "_")
}

private val REMOTE_CACHE_HASH_PREFIX_REGEX = Regex("^[0-9a-fA-F]{40}_(.+)$")

internal fun stripRemoteCacheHashPrefix(rawName: String): String {
    val normalized = rawName.trim()
    if (normalized.isEmpty()) return rawName
    return REMOTE_CACHE_HASH_PREFIX_REGEX.matchEntire(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: normalized
}

internal fun sanitizeRemoteCachedMetadataTitle(
    rawTitle: String,
    selectedFile: File?
): String {
    val normalizedTitle = rawTitle.trim()
    if (normalizedTitle.isBlank()) return rawTitle
    val file = selectedFile ?: return rawTitle
    if (file.parentFile?.name != REMOTE_SOURCE_CACHE_DIR) return rawTitle

    val strippedHashPrefix = stripRemoteCacheHashPrefix(normalizedTitle)
    if (strippedHashPrefix == normalizedTitle) return rawTitle
    return inferredDisplayTitleForName(strippedHashPrefix)
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

private fun cacheIndexFile(cacheRoot: File): File = File(cacheRoot, SOURCE_CACHE_INDEX_FILE)

private fun loadSourceCacheIndex(cacheRoot: File): MutableMap<String, String> {
    val indexFile = cacheIndexFile(cacheRoot)
    if (!indexFile.exists() || !indexFile.isFile) return mutableMapOf()
    return try {
        val root = JSONObject(indexFile.readText())
        val out = mutableMapOf<String, String>()
        root.keys().forEach { key ->
            val value = root.optString(key, "").trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                out[key] = value
            }
        }
        out
    } catch (_: Throwable) {
        mutableMapOf()
    }
}

private fun saveSourceCacheIndex(cacheRoot: File, index: Map<String, String>) {
    try {
        if (!cacheRoot.exists()) cacheRoot.mkdirs()
        val root = JSONObject()
        index.entries
            .sortedBy { it.key }
            .forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    root.put(key, value)
                }
            }
        cacheIndexFile(cacheRoot).writeText(root.toString())
    } catch (_: Throwable) {
    }
}

internal fun rememberSourceForCachedFile(cacheRoot: File, fileName: String, sourceId: String) {
    if (fileName.isBlank() || sourceId.isBlank()) return
    val index = loadSourceCacheIndex(cacheRoot)
    index[fileName] = sourceId
    saveSourceCacheIndex(cacheRoot, index)
}

private fun removeSourceMappingsForFiles(cacheRoot: File, fileNames: Set<String>) {
    if (fileNames.isEmpty()) return
    val normalized = fileNames.filter { it.isNotBlank() }.toSet()
    if (normalized.isEmpty()) return
    val index = loadSourceCacheIndex(cacheRoot)
    val changed = index.keys.removeAll(normalized)
    if (changed) saveSourceCacheIndex(cacheRoot, index)
}

private fun pruneStaleSourceMappings(cacheRoot: File) {
    val index = loadSourceCacheIndex(cacheRoot)
    if (index.isEmpty()) return
    val existingNames = cacheRoot.listFiles().orEmpty()
        .filter { it.isFile && !it.name.endsWith(".part", ignoreCase = true) && it.name != SOURCE_CACHE_INDEX_FILE }
        .map { it.name }
        .toSet()
    val changed = index.keys.removeAll { it !in existingNames }
    if (changed) saveSourceCacheIndex(cacheRoot, index)
}

internal fun listCachedSourceFiles(cacheRoot: File): List<CachedSourceFile> {
    if (!cacheRoot.exists()) return emptyList()
    pruneStaleSourceMappings(cacheRoot)
    val index = loadSourceCacheIndex(cacheRoot)
    return cacheRoot.listFiles().orEmpty()
        .filter { it.isFile && !it.name.endsWith(".part", ignoreCase = true) && it.name != SOURCE_CACHE_INDEX_FILE }
        .sortedByDescending { it.lastModified() }
        .map { file ->
            CachedSourceFile(
                absolutePath = file.absolutePath,
                fileName = file.name,
                sizeBytes = file.length().coerceAtLeast(0L),
                lastModified = file.lastModified(),
                sourceId = index[file.name]
            )
        }
}

internal fun sourceIdForCachedFileName(cacheRoot: File, fileName: String): String? {
    if (fileName.isBlank()) return null
    return loadSourceCacheIndex(cacheRoot)[fileName]
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
    val deletedNames = mutableSetOf<String>()
    for (file in entries) {
        if (totalCount <= normalizedMaxTracks && totalBytes <= normalizedMaxBytes) break
        if (protected.contains(file.absolutePath)) continue
        val size = file.length().coerceAtLeast(0L)
        if (file.delete()) {
            deletedFiles++
            freedBytes += size
            totalCount--
            totalBytes = (totalBytes - size).coerceAtLeast(0L)
            deletedNames.add(file.name)
        } else {
            file.deleteOnExit()
        }
    }
    removeSourceMappingsForFiles(cacheRoot, deletedNames)

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
    val deletedNames = mutableSetOf<String>()
    cacheRoot.listFiles().orEmpty().forEach { file ->
        if (!file.isFile) {
            file.deleteRecursively()
            return@forEach
        }
        if (file.name == SOURCE_CACHE_INDEX_FILE) return@forEach
        if (protected.contains(file.absolutePath)) {
            skippedFiles++
            return@forEach
        }
        val size = file.length().coerceAtLeast(0L)
        if (file.delete()) {
            deletedFiles++
            freedBytes += size
            deletedNames.add(file.name)
        } else {
            file.deleteOnExit()
        }
    }
    removeSourceMappingsForFiles(cacheRoot, deletedNames)
    return RemoteCacheClearResult(deletedFiles, skippedFiles, freedBytes)
}

internal fun deleteSpecificRemoteCacheFiles(
    cacheRoot: File,
    absolutePaths: Set<String>,
    protectedPaths: Set<String> = emptySet()
): RemoteCacheDeleteResult {
    if (!cacheRoot.exists() || absolutePaths.isEmpty()) {
        return RemoteCacheDeleteResult(0, 0, absolutePaths.size, 0L)
    }
    val protected = protectedPaths.filter { it.isNotBlank() }.toSet()
    var deletedFiles = 0
    var skippedFiles = 0
    var missingFiles = 0
    var freedBytes = 0L
    val deletedNames = mutableSetOf<String>()
    absolutePaths.forEach { absolutePath ->
        val file = File(absolutePath)
        if (!file.exists() || !file.isFile || file.parentFile?.absolutePath != cacheRoot.absolutePath) {
            missingFiles++
            return@forEach
        }
        if (file.name == SOURCE_CACHE_INDEX_FILE) {
            missingFiles++
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
            deletedNames.add(file.name)
        } else {
            file.deleteOnExit()
            skippedFiles++
        }
    }
    removeSourceMappingsForFiles(cacheRoot, deletedNames)
    return RemoteCacheDeleteResult(deletedFiles, skippedFiles, missingFiles, freedBytes)
}
