package com.flopster101.siliconplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import java.io.File
import java.util.Locale

internal fun playRecentFileEntryAction(
    cacheRoot: File,
    entry: RecentPathEntry,
    openPlayerOnTrackSelect: Boolean,
    onApplyTrackSelection: (File, Boolean, Boolean?, String?, String?, Boolean) -> Unit,
    onApplyManualInputSelection: (String) -> Unit
) {
    val normalized = normalizeSourceIdentity(entry.path)
    val uri = normalized?.let { Uri.parse(it) }
    val scheme = uri?.scheme?.lowercase(Locale.ROOT)
    val isRemote = scheme == "http" || scheme == "https" || scheme == "smb"
    if (isRemote && !normalized.isNullOrBlank()) {
        val cached = findExistingCachedFileForSource(cacheRoot, normalized)
        if (cached != null) {
            onApplyTrackSelection(
                cached,
                true,
                openPlayerOnTrackSelect,
                normalized,
                null,
                false
            )
        } else {
            onApplyManualInputSelection(entry.path)
        }
    } else {
        onApplyManualInputSelection(entry.path)
    }
}

internal fun applyRecentFolderAction(
    context: Context,
    prefs: android.content.SharedPreferences,
    entry: RecentPathEntry,
    action: FolderEntryAction,
    recentFolders: List<RecentPathEntry>,
    recentFoldersLimit: Int,
    networkNodes: List<NetworkNode>,
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit,
    onOpenInBrowser: (locationId: String?, directoryPath: String, smbSourceNodeId: Long?) -> Unit
) {
    when (action) {
        FolderEntryAction.OpenInBrowser -> {
            val target = resolveBrowserParentForRecentFolder(entry, networkNodes)
            if (target == null) {
                Toast.makeText(context, "Unable to open folder in browser", Toast.LENGTH_SHORT).show()
            } else {
                onOpenInBrowser(target.locationId, target.directoryPath, target.smbSourceNodeId)
            }
        }

        FolderEntryAction.DeleteFromRecents -> {
            val updated = recentFolders.filterNot { samePath(it.path, entry.path) }
            onRecentFoldersChanged(updated)
            writeRecentEntries(
                prefs,
                AppPreferenceKeys.RECENT_FOLDERS,
                updated,
                recentFoldersLimit
            )
            Toast.makeText(context, "Removed from recents", Toast.LENGTH_SHORT).show()
        }

        FolderEntryAction.CopyPath -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Path", entry.path))
            Toast.makeText(context, "Copied path", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun applyRecentSourceAction(
    context: Context,
    prefs: android.content.SharedPreferences,
    entry: RecentPathEntry,
    action: SourceEntryAction,
    recentPlayedFiles: List<RecentPathEntry>,
    recentFilesLimit: Int,
    networkNodes: List<NetworkNode>,
    onRecentPlayedFilesChanged: (List<RecentPathEntry>) -> Unit,
    resolveShareableFileForRecent: (RecentPathEntry) -> File?,
    onOpenInBrowser: (locationId: String?, directoryPath: String, smbSourceNodeId: Long?) -> Unit
) {
    when (action) {
        SourceEntryAction.OpenInBrowser -> {
            val target = resolveBrowserFolderForRecentSource(entry, networkNodes)
            if (target == null) {
                Toast.makeText(context, "This source cannot be opened in file browser", Toast.LENGTH_SHORT).show()
            } else {
                onOpenInBrowser(target.locationId, target.directoryPath, target.smbSourceNodeId)
            }
        }

        SourceEntryAction.DeleteFromRecents -> {
            val updated = recentPlayedFiles.filterNot { samePath(it.path, entry.path) }
            onRecentPlayedFilesChanged(updated)
            writeRecentEntries(
                prefs,
                AppPreferenceKeys.RECENT_PLAYED_FILES,
                updated,
                recentFilesLimit
            )
            Toast.makeText(context, "Removed from recents", Toast.LENGTH_SHORT).show()
        }

        SourceEntryAction.ShareFile -> {
            val shareFile = resolveShareableFileForRecent(entry)
            if (shareFile == null) {
                Toast.makeText(
                    context,
                    "Share is only available for local or cached files",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = guessMimeTypeFromFilename(shareFile.name)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share file"))
            } catch (_: Throwable) {
                Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
            }
        }

        SourceEntryAction.CopySource -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("URL or path", entry.path))
            Toast.makeText(context, "Copied URL/path", Toast.LENGTH_SHORT).show()
        }
    }
}

private data class BrowserOpenTarget(
    val locationId: String?,
    val directoryPath: String,
    val smbSourceNodeId: Long?
)

internal data class SmbTargetResolution(
    val sourceNodeId: Long?,
    val requestUri: String
)

private fun resolveBrowserFolderForRecentSource(
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>
): BrowserOpenTarget? {
    parseArchiveSourceId(entry.path)?.let { archiveSource ->
        val parentInArchive = archiveSource.entryPath
            .replace('\\', '/')
            .substringBeforeLast('/', "")
            .trim('/')
        val logicalDirectory = if (parentInArchive.isBlank()) {
            archiveSource.archivePath
        } else {
            "${archiveSource.archivePath}/$parentInArchive"
        }
        return BrowserOpenTarget(
            locationId = entry.locationId,
            directoryPath = logicalDirectory,
            smbSourceNodeId = null
        )
    }

    val parsed = Uri.parse(entry.path)
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    if (scheme == "smb") {
        val smbSpec = parseSmbSourceSpecFromInput(entry.path) ?: return null
        if (smbSpec.share.isBlank()) return null
        val normalizedPath = smbSpec.path?.trim().orEmpty()
        val parentSpec = if (normalizedPath.isBlank()) {
            smbSpec.copy(path = null)
        } else {
            val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "")
                .trim()
                .ifBlank { null }
            smbSpec.copy(path = parentPath)
        }
        val smbTarget = resolveSmbRecentOpenTarget(
            targetSpec = parentSpec,
            networkNodes = networkNodes
        )
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = smbTarget.requestUri,
            smbSourceNodeId = smbTarget.sourceNodeId
        )
    }
    if (scheme == "http" || scheme == "https") return null

    val localPath = if (scheme == "file") {
        parsed.path
    } else {
        entry.path
    }?.trim().takeIf { !it.isNullOrBlank() } ?: return null

    val localFile = File(localPath)
    val directory = if (localFile.isDirectory) {
        localFile.absolutePath
    } else {
        localFile.parentFile?.absolutePath
    } ?: return null
    return BrowserOpenTarget(
        locationId = entry.locationId,
        directoryPath = directory,
        smbSourceNodeId = null
    )
}

private fun resolveBrowserParentForRecentFolder(
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>
): BrowserOpenTarget? {
    parseArchiveLogicalPath(entry.path)?.let { (archivePath, entryPath) ->
        if (entryPath.isNullOrBlank()) {
            val archiveParent = File(archivePath).parentFile?.absolutePath ?: return null
            return BrowserOpenTarget(
                locationId = entry.locationId,
                directoryPath = archiveParent,
                smbSourceNodeId = null
            )
        }
        val parentInArchive = entryPath
            .replace('\\', '/')
            .trim('/')
            .substringBeforeLast('/', "")
            .trim('/')
        val logicalParent = if (parentInArchive.isBlank()) {
            archivePath
        } else {
            "$archivePath/$parentInArchive"
        }
        return BrowserOpenTarget(
            locationId = entry.locationId,
            directoryPath = logicalParent,
            smbSourceNodeId = null
        )
    }

    val rawPath = entry.path.trim().takeIf { it.isNotBlank() } ?: return null
    val smbSpec = parseSmbSourceSpecFromInput(rawPath)
    if (smbSpec != null) {
        if (smbSpec.share.isBlank()) return null
        val normalizedPath = smbSpec.path?.trim().orEmpty()
        val parentSpec = if (normalizedPath.isBlank()) {
            smbSpec.copy(share = "", path = null)
        } else {
            val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "")
                .trim()
                .ifBlank { null }
            smbSpec.copy(path = parentPath)
        }
        val smbTarget = resolveSmbRecentOpenTarget(
            targetSpec = parentSpec,
            networkNodes = networkNodes
        )
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = smbTarget.requestUri,
            smbSourceNodeId = smbTarget.sourceNodeId
        )
    }

    val folder = File(rawPath)
    val parentPath = folder.parentFile?.absolutePath ?: return null
    return BrowserOpenTarget(
        locationId = entry.locationId,
        directoryPath = parentPath,
        smbSourceNodeId = null
    )
}

internal fun resolveSmbRecentOpenTarget(
    targetSpec: SmbSourceSpec,
    networkNodes: List<NetworkNode>
): SmbTargetResolution {
    if (!targetSpec.password.isNullOrBlank()) {
        return SmbTargetResolution(
            sourceNodeId = null,
            requestUri = buildSmbRequestUri(targetSpec)
        )
    }

    val normalizedTargetPath = normalizeSmbPathForShare(targetSpec.path).orEmpty()
    val normalizedTargetUser = targetSpec.username?.trim().orEmpty()
    val candidate = networkNodes
        .asSequence()
        .filter { node ->
            node.type == NetworkNodeType.RemoteSource && node.sourceKind == NetworkSourceKind.Smb
        }
        .mapNotNull { node ->
            val spec = resolveNetworkNodeSmbSpec(node) ?: return@mapNotNull null
            if (!spec.host.equals(targetSpec.host, ignoreCase = true)) return@mapNotNull null
            if (!spec.share.equals(targetSpec.share, ignoreCase = true)) return@mapNotNull null
            val normalizedNodePath = normalizeSmbPathForShare(spec.path).orEmpty()
            val normalizedNodeUser = spec.username?.trim().orEmpty()
            var score = 0
            if (normalizedNodeUser.isNotEmpty() && normalizedNodeUser.equals(normalizedTargetUser, ignoreCase = true)) {
                score += 200
            }
            if (normalizedNodePath.equals(normalizedTargetPath, ignoreCase = true)) {
                score += 400
            } else {
                if (normalizedTargetPath.startsWith("$normalizedNodePath/") && normalizedNodePath.isNotBlank()) {
                    score += 100 + normalizedNodePath.length
                }
                if (normalizedNodePath.startsWith("$normalizedTargetPath/") && normalizedTargetPath.isNotBlank()) {
                    score += 40 + normalizedTargetPath.length
                }
                if (normalizedNodePath.isBlank() && normalizedTargetPath.isNotBlank()) {
                    score += 30
                }
            }
            if (!spec.password.isNullOrBlank()) score += 10
            node.id to (score to spec)
        }
        .maxByOrNull { (_, pair) -> pair.first }

    val resolvedNodeId = candidate?.first
    val credentialSpec = candidate?.second?.second
    val finalSpec = if (credentialSpec == null) {
        targetSpec
    } else {
        targetSpec.copy(
            username = credentialSpec.username,
            password = credentialSpec.password
        )
    }
    return SmbTargetResolution(
        sourceNodeId = resolvedNodeId,
        requestUri = buildSmbRequestUri(finalSpec)
    )
}
