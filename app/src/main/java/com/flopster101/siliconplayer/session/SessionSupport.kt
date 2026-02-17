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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

internal fun normalizeSourceIdentity(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val trimmed = path.trim()
    val uri = Uri.parse(trimmed)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    return when (scheme) {
        "http", "https" -> uri.normalizeScheme().toString()
        "file" -> {
            val localPath = uri.path?.takeIf { it.isNotBlank() } ?: return null
            try {
                File(localPath).canonicalFile.absolutePath
            } catch (_: Exception) {
                File(localPath).absoluteFile.normalize().path
            }
        }

        else -> {
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
    RemoteUrl
}

internal data class ManualSourceResolution(
    val type: ManualSourceType,
    val sourceId: String,
    val requestUrl: String,
    val localFile: File?,
    val directoryPath: String?,
    val displayFile: File?
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
            displayFile = File("/virtual/remote/$safeName")
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
                displayFile = file
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
    val artist: String? = null
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
    val parsed = Uri.parse(entry.path)
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https") {
        val hostLabel = parsed.host?.takeIf { it.isNotBlank() } ?: "unknown host"
        val protocolLabel = scheme.uppercase(Locale.ROOT)
        val qualifier = if (isRemoteSourceCached(context, entry.path)) "Cached" else null
        return StoragePresentation(
            label = "$protocolLabel ($hostLabel)",
            icon = Icons.Default.Public,
            qualifier = qualifier
        )
    }

    val pathForMatching = when (scheme) {
        "file" -> parsed.path?.takeIf { it.isNotBlank() } ?: entry.path
        else -> entry.path
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
        buildList {
            for (index in 0 until array.length()) {
                val objectValue = array.optJSONObject(index) ?: continue
                val path = objectValue.optString("path", "").trim()
                if (path.isBlank()) continue
                val locationId = objectValue.optString("locationId", "").ifBlank { null }
                val title = objectValue.optString("title", "").ifBlank { null }
                val artist = objectValue.optString("artist", "").ifBlank { null }
                add(
                    RecentPathEntry(
                        path = path,
                        locationId = locationId,
                        title = title,
                        artist = artist
                    )
                )
                if (size >= maxItems) break
            }
        }
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
    val trimmed = entries.take(maxItems)
    val array = JSONArray()
    trimmed.forEach { entry ->
        array.put(
            JSONObject()
                .put("path", entry.path)
                .put("locationId", entry.locationId ?: "")
                .put("title", entry.title ?: "")
                .put("artist", entry.artist ?: "")
        )
    }
    prefs.edit().putString(key, array.toString()).apply()
}
