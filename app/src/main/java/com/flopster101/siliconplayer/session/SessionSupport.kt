package com.flopster101.siliconplayer

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.ui.graphics.vector.ImageVector
import com.flopster101.siliconplayer.data.buildArchiveSourceId
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import com.flopster101.siliconplayer.ui.screens.NetworkIcons
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

private fun resolveCachedRemoteSourceId(localPath: String): String? {
    val candidate = File(localPath)
    val parent = candidate.parentFile ?: return null
    if (parent.name != REMOTE_SOURCE_CACHE_DIR) return null
    return sourceIdForCachedFileName(parent, candidate.name)
}

internal fun normalizeSourceIdentity(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val trimmed = path.trim()
    val uri = Uri.parse(trimmed)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    return when (scheme) {
        "http", "https" -> uri.normalizeScheme().toString()
        "archive" -> {
            val parsedArchive = parseArchiveSourceId(trimmed) ?: return trimmed
            val canonicalArchivePath = try {
                File(parsedArchive.archivePath).canonicalFile.absolutePath
            } catch (_: Exception) {
                File(parsedArchive.archivePath).absoluteFile.normalize().path
            }
            buildArchiveSourceId(canonicalArchivePath, parsedArchive.entryPath)
        }
        "file" -> {
            val localPath = uri.path?.takeIf { it.isNotBlank() } ?: return null
            resolveCachedRemoteSourceId(localPath)?.let { cachedSourceId ->
                return normalizeSourceIdentity(cachedSourceId) ?: cachedSourceId
            }
            try {
                File(localPath).canonicalFile.absolutePath
            } catch (_: Exception) {
                File(localPath).absoluteFile.normalize().path
            }
        }

        "smb" -> {
            val smbSpec = parseSmbSourceSpecFromInput(trimmed) ?: return null
            buildSmbSourceId(smbSpec)
        }

        else -> {
            resolveCachedRemoteSourceId(trimmed)?.let { cachedSourceId ->
                return normalizeSourceIdentity(cachedSourceId) ?: cachedSourceId
            }
            try {
                File(trimmed).canonicalFile.absolutePath
            } catch (_: Exception) {
                File(trimmed).absoluteFile.normalize().path
            }
        }
    }
}

internal fun samePath(a: String?, b: String?): Boolean {
    val left = normalizeSourceIdentity(a) ?: return false
    val right = normalizeSourceIdentity(b) ?: return false
    return left == right
}

internal enum class ManualSourceType {
    LocalFile,
    LocalDirectory,
    RemoteUrl,
    Smb
}

internal data class ManualSourceResolution(
    val type: ManualSourceType,
    val sourceId: String,
    val requestUrl: String,
    val localFile: File?,
    val directoryPath: String?,
    val displayFile: File?,
    val smbSpec: SmbSourceSpec? = null
)

internal data class ManualSourceOpenOptions(
    val forceCaching: Boolean = false
)

internal fun resolvePlaybackSourceLabel(
    selectedFile: File?,
    sourceId: String?
): String? {
    if (selectedFile == null) return null
    val normalizedSource = normalizeSourceIdentity(sourceId ?: selectedFile.absolutePath) ?: return "Local"
    val scheme = Uri.parse(normalizedSource).scheme?.lowercase(Locale.ROOT)
    val isRemote = scheme == "http" || scheme == "https"
    val isSmb = scheme == "smb"
    if (scheme == "archive") return "Archive"
    if (isSmb) {
        val smbSpec = parseSmbSourceSpecFromInput(normalizedSource)
        if (smbSpec != null) {
            val suffix = if (selectedFile.absolutePath.contains("/cache/remote_sources/")) {
                " (cached)"
            } else {
                ""
            }
            val smbTarget = if (smbSpec.share.isBlank()) {
                smbSpec.host
            } else {
                "${smbSpec.host}/${smbSpec.share}"
            }
            return "SMB ($smbTarget)$suffix"
        }
        return "SMB"
    }
    if (!isRemote) return "Local"
    return if (selectedFile.absolutePath.contains("/cache/remote_sources/")) {
        "Streamed (cached)"
    } else {
        "Streamed"
    }
}

internal fun resolveManualSourceInput(rawInput: String): ManualSourceResolution? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return null

    val uri = Uri.parse(trimmed)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https") {
        if (uri.host.isNullOrBlank()) return null
        val normalizedUrl = uri.normalizeScheme().toString()
        val requestUrl = stripUrlFragment(normalizedUrl)
        val safeName = remoteFilenameHintFromUri(uri) ?: sanitizeRemoteLeafName(uri.host) ?: "remote"
        return ManualSourceResolution(
            type = ManualSourceType.RemoteUrl,
            sourceId = normalizedUrl,
            requestUrl = requestUrl,
            localFile = null,
            directoryPath = null,
            displayFile = File("/virtual/remote/$safeName"),
            smbSpec = null
        )
    }

    if (scheme == "smb") {
        val smbSpec = parseSmbSourceSpecFromInput(trimmed) ?: return null
        val sourceId = buildSmbSourceId(smbSpec)
        val requestUri = buildSmbRequestUri(smbSpec)
        val safeName = sanitizeRemoteLeafName(smbSpec.path?.substringAfterLast('/'))
            ?: sanitizeRemoteLeafName(smbSpec.share)
            ?: "smb"
        return ManualSourceResolution(
            type = ManualSourceType.Smb,
            sourceId = sourceId,
            requestUrl = requestUri,
            localFile = null,
            directoryPath = null,
            displayFile = File("/virtual/remote/$safeName"),
            smbSpec = smbSpec
        )
    }

    fun resolveLocalPath(path: String, sourceIdOverride: String? = null): ManualSourceResolution? {
        val file = File(path).absoluteFile
        if (!file.exists()) return null
        if (file.isDirectory) {
            return ManualSourceResolution(
                type = ManualSourceType.LocalDirectory,
                sourceId = sourceIdOverride ?: file.absolutePath,
                requestUrl = sourceIdOverride ?: file.absolutePath,
                localFile = null,
                directoryPath = file.absolutePath,
                displayFile = null
            )
        }
        if (file.isFile) {
            return ManualSourceResolution(
                type = ManualSourceType.LocalFile,
                sourceId = sourceIdOverride ?: file.absolutePath,
                requestUrl = sourceIdOverride ?: file.absolutePath,
                localFile = file,
                directoryPath = null,
                displayFile = file,
                smbSpec = null
            )
        }
        return null
    }

    if (scheme == "file") {
        val localPath = uri.path?.takeIf { it.isNotBlank() } ?: return null
        return resolveLocalPath(localPath, sourceIdOverride = uri.normalizeScheme().toString())
    }

    val expandedPath = when {
        trimmed == "~" -> System.getProperty("user.home") ?: trimmed
        trimmed.startsWith("~/") -> {
            val home = System.getProperty("user.home") ?: return null
            home + trimmed.removePrefix("~")
        }

        else -> trimmed
    }
    return resolveLocalPath(expandedPath)
}

internal data class RecentPathEntry(
    val path: String,
    val locationId: String?,
    val title: String? = null,
    val artist: String? = null,
    val decoderName: String? = null
)

internal data class StorageDescriptor(
    val rootPath: String,
    val label: String,
    val icon: ImageVector
)

internal data class StoragePresentation(
    val label: String,
    val icon: ImageVector,
    val qualifier: String? = null
)

private fun resolveStorageRootFromAppDir(appSpecificDir: File): File? {
    val marker = "/Android/"
    val absolutePath = appSpecificDir.absolutePath
    val markerIndex = absolutePath.indexOf(marker)
    if (markerIndex <= 0) return null
    return File(absolutePath.substring(0, markerIndex))
}

internal fun detectStorageDescriptors(context: Context): List<StorageDescriptor> {
    val descriptors = mutableListOf<StorageDescriptor>()
    val seen = mutableSetOf<String>()

    fun add(path: String, label: String, icon: ImageVector) {
        if (path in seen) return
        seen += path
        descriptors += StorageDescriptor(path, label, icon)
    }

    add("/", "Root (/)", Icons.Default.Folder)
    val internalRoot = Environment.getExternalStorageDirectory().absolutePath
    val internalIcon = if (context.resources.configuration.smallestScreenWidthDp >= 600) {
        Icons.Default.TabletAndroid
    } else {
        Icons.Default.PhoneAndroid
    }
    add(internalRoot, "Internal storage", internalIcon)

    context.getExternalFilesDirs(null)
        .orEmpty()
        .forEach { externalDir ->
            if (externalDir == null) return@forEach
            val volumeRoot = resolveStorageRootFromAppDir(externalDir) ?: return@forEach
            if (!Environment.isExternalStorageRemovable(externalDir)) return@forEach

            val lower = volumeRoot.absolutePath.lowercase()
            val isUsb = lower.contains("usb") || lower.contains("otg")
            val volumeName = volumeRoot.name.ifBlank { volumeRoot.absolutePath }
            val label = volumeName
            add(volumeRoot.absolutePath, label, if (isUsb) Icons.Default.Usb else Icons.Default.SdCard)
        }

    return descriptors
}

internal fun storagePresentationForEntry(
    context: Context,
    entry: RecentPathEntry,
    descriptors: List<StorageDescriptor>
): StoragePresentation {
    val normalizedPath = normalizeSourceIdentity(entry.path) ?: entry.path
    val parsed = Uri.parse(normalizedPath)
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https") {
        val hostLabel = parsed.host?.takeIf { it.isNotBlank() } ?: "unknown host"
        val protocolLabel = scheme.uppercase(Locale.ROOT)
        val qualifier = if (isRemoteSourceCached(context, normalizedPath)) "Cached" else null
        return StoragePresentation(
            label = "$protocolLabel ($hostLabel)",
            icon = Icons.Default.Public,
            qualifier = qualifier
        )
    }
    if (scheme == "smb") {
        val smbSpec = parseSmbSourceSpecFromInput(normalizedPath)
        val qualifier = if (isRemoteSourceCached(context, normalizedPath)) "Cached" else null
        val smbLabel = if (smbSpec == null) {
            "SMB"
        } else {
            val smbTarget = if (smbSpec.share.isBlank()) {
                smbSpec.host
            } else {
                "${smbSpec.host}/${smbSpec.share}"
            }
            "SMB ($smbTarget)"
        }
        return StoragePresentation(
            label = smbLabel,
            icon = NetworkIcons.FolderData,
            qualifier = qualifier
        )
    }
    if (scheme == "archive") {
        val archiveName = parseArchiveSourceId(entry.path)
            ?.archivePath
            ?.let { File(it).name.ifBlank { it } }
            ?: "Archive"
        return StoragePresentation(
            label = archiveName,
            icon = Icons.Default.Folder
        )
    }

    val pathForMatching = when (scheme) {
        "file" -> parsed.path?.takeIf { it.isNotBlank() } ?: normalizedPath
        else -> normalizedPath
    }

    entry.locationId?.let { locationId ->
        descriptors.firstOrNull { it.rootPath == locationId }?.let {
            return StoragePresentation(label = it.label, icon = it.icon)
        }
    }
    val matching = descriptors
        .filter { pathForMatching == it.rootPath || pathForMatching.startsWith("${it.rootPath}/") }
        .maxByOrNull { it.rootPath.length }
    return if (matching != null) {
        StoragePresentation(label = matching.label, icon = matching.icon)
    } else {
        StoragePresentation(label = "Unknown storage", icon = Icons.Default.Folder)
    }
}

internal fun readRecentEntries(
    prefs: android.content.SharedPreferences,
    key: String,
    maxItems: Int
): List<RecentPathEntry> {
    val raw = prefs.getString(key, null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        val deduped = mutableListOf<RecentPathEntry>()
        for (index in 0 until array.length()) {
            val objectValue = array.optJSONObject(index) ?: continue
            val path = objectValue.optString("path", "").trim()
            if (path.isBlank()) continue
            val locationId = objectValue.optString("locationId", "").ifBlank { null }
            val title = objectValue.optString("title", "").ifBlank { null }
            val artist = objectValue.optString("artist", "").ifBlank { null }
            val decoderName = objectValue.optString("decoderName", "").ifBlank { null }
            val existingIndex = deduped.indexOfFirst { samePath(it.path, path) }
            if (existingIndex >= 0) {
                val existing = deduped[existingIndex]
                deduped[existingIndex] = existing.copy(
                    locationId = existing.locationId ?: locationId,
                    title = existing.title ?: title,
                    artist = existing.artist ?: artist,
                    decoderName = existing.decoderName ?: decoderName
                )
                continue
            }
            deduped += RecentPathEntry(
                path = path,
                locationId = locationId,
                title = title,
                artist = artist,
                decoderName = decoderName
            )
            if (deduped.size >= maxItems) break
        }
        deduped
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun writeRecentEntries(
    prefs: android.content.SharedPreferences,
    key: String,
    entries: List<RecentPathEntry>,
    maxItems: Int
) {
    val deduped = mutableListOf<RecentPathEntry>()
    entries.forEach { entry ->
        val existingIndex = deduped.indexOfFirst { samePath(it.path, entry.path) }
        if (existingIndex >= 0) {
            val existing = deduped[existingIndex]
            deduped[existingIndex] = existing.copy(
                locationId = existing.locationId ?: entry.locationId,
                title = existing.title ?: entry.title,
                artist = existing.artist ?: entry.artist,
                decoderName = existing.decoderName ?: entry.decoderName
            )
        } else {
            deduped += entry
        }
    }
    val trimmed = deduped.take(maxItems)
    val array = JSONArray()
    trimmed.forEach { entry ->
        array.put(
            JSONObject()
                .put("path", entry.path)
                .put("locationId", entry.locationId ?: "")
                .put("title", entry.title ?: "")
                .put("artist", entry.artist ?: "")
                .put("decoderName", entry.decoderName ?: "")
        )
    }
    prefs.edit().putString(key, array.toString()).apply()
}

internal fun buildUpdatedRecentFolders(
    current: List<RecentPathEntry>,
    newPath: String,
    locationId: String?,
    limit: Int
): List<RecentPathEntry> {
    val normalized = normalizeSourceIdentity(newPath) ?: newPath
    val updated = listOf(
        RecentPathEntry(path = normalized, locationId = locationId, title = null, artist = null)
    ) + current.filterNot { samePath(it.path, normalized) }
    return updated.take(limit)
}

internal fun buildUpdatedRecentPlayedTracks(
    current: List<RecentPathEntry>,
    newPath: String,
    locationId: String?,
    title: String? = null,
    artist: String? = null,
    decoderName: String? = null,
    clearBlankMetadataOnUpdate: Boolean = false,
    limit: Int
): List<RecentPathEntry> {
    val normalized = normalizeSourceIdentity(newPath) ?: newPath
    val existing = current.firstOrNull { samePath(it.path, normalized) }
    val trimmedTitle = title?.trim()
    val trimmedArtist = artist?.trim()
    val resolvedTitle = when {
        clearBlankMetadataOnUpdate && trimmedTitle != null -> trimmedTitle.ifBlank { null }
        else -> trimmedTitle.takeUnless { it.isNullOrBlank() } ?: existing?.title
    }
    val resolvedArtist = when {
        clearBlankMetadataOnUpdate && trimmedArtist != null -> trimmedArtist.ifBlank { null }
        else -> trimmedArtist.takeUnless { it.isNullOrBlank() } ?: existing?.artist
    }
    val resolvedDecoderName = decoderName?.trim().takeUnless { it.isNullOrBlank() } ?: existing?.decoderName
    val updated = listOf(
        RecentPathEntry(
            path = normalized,
            locationId = locationId ?: existing?.locationId,
            title = resolvedTitle,
            artist = resolvedArtist,
            decoderName = resolvedDecoderName
        )
    ) + current.filterNot { samePath(it.path, normalized) }
    return updated.take(limit)
}

internal fun mergeRecentPlayedTrackMetadata(
    current: List<RecentPathEntry>,
    path: String,
    title: String?,
    artist: String?
): List<RecentPathEntry> {
    val normalized = normalizeSourceIdentity(path) ?: path
    val normalizedTitle = title?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedArtist = artist?.trim().takeUnless { it.isNullOrBlank() }
    if (normalizedTitle == null && normalizedArtist == null) return current
    var changed = false
    val updated = current.map { entry ->
        if (!samePath(entry.path, normalized)) return@map entry
        val resolvedTitle = normalizedTitle ?: entry.title
        val resolvedArtist = normalizedArtist ?: entry.artist
        if (resolvedTitle == entry.title && resolvedArtist == entry.artist) {
            entry
        } else {
            changed = true
            entry.copy(title = resolvedTitle, artist = resolvedArtist)
        }
    }
    return if (changed) updated else current
}

internal fun resolveShareableFileForRecentEntry(
    context: Context,
    entry: RecentPathEntry
): File? {
    val normalized = normalizeSourceIdentity(entry.path) ?: return null
    val uri = Uri.parse(normalized)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    return when (scheme) {
        "http", "https", "smb" -> {
            findExistingCachedFileForSource(File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR), normalized)
                ?.takeIf { it.exists() && it.isFile }
        }

        "file" -> {
            uri.path?.let { File(it) }?.takeIf { it.exists() && it.isFile }
        }

        else -> {
            File(normalized).takeIf { it.exists() && it.isFile }
        }
    }
}

internal fun resolveStorageLocationForPath(
    path: String,
    descriptors: List<StorageDescriptor>
): String? {
    return descriptors
        .filter { path == it.rootPath || path.startsWith("${it.rootPath}/") }
        .maxByOrNull { it.rootPath.length }
        ?.rootPath
}
