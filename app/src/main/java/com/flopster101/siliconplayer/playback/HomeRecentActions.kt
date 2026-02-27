package com.flopster101.siliconplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.flopster101.siliconplayer.data.buildArchiveDirectoryPath
import com.flopster101.siliconplayer.data.buildArchiveSourceId
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import com.flopster101.siliconplayer.data.resolveArchiveContainerParentLocation
import java.io.File
import java.util.Locale

internal fun playRecentFileEntryAction(
    cacheRoot: File,
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>,
    openPlayerOnTrackSelect: Boolean,
    onApplyTrackSelection: (File, Boolean, Boolean?, String?, String?, Boolean) -> Unit,
    onApplyManualInputSelection: (String) -> Unit
) {
    val playbackInput = resolveRecentPlaybackInput(entry, networkNodes)
    val normalized = normalizeSourceIdentity(playbackInput)
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
            onApplyManualInputSelection(playbackInput)
        }
    } else {
        onApplyManualInputSelection(playbackInput)
    }
}

private fun resolveRecentPlaybackInput(
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>
): String {
    val rawPath = entry.path.trim()
    if (rawPath.isBlank()) return entry.path

    parseArchiveSourceId(rawPath)?.let { archiveSource ->
        parseSmbSourceSpecFromInput(archiveSource.archivePath)?.let { archiveSmbSpec ->
            val smbTarget = resolveSmbRecentOpenTarget(
                targetSpec = archiveSmbSpec,
                networkNodes = networkNodes,
                preferredSourceNodeId = entry.sourceNodeId
            )
            return buildArchiveSourceId(smbTarget.requestUri, archiveSource.entryPath)
        }
        parseHttpSourceSpecFromInput(archiveSource.archivePath)?.let { archiveHttpSpec ->
            val httpTarget = resolveHttpRecentOpenTarget(
                targetSpec = archiveHttpSpec,
                networkNodes = networkNodes,
                preferredSourceNodeId = entry.sourceNodeId
            )
            return buildArchiveSourceId(httpTarget.requestUri, archiveSource.entryPath)
        }
        return rawPath
    }

    parseSmbSourceSpecFromInput(rawPath)?.let { smbSpec ->
        return resolveSmbRecentOpenTarget(
            targetSpec = smbSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        ).requestUri
    }

    parseHttpSourceSpecFromInput(rawPath)?.let { httpSpec ->
        return resolveHttpRecentOpenTarget(
            targetSpec = httpSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        ).requestUri
    }

    return rawPath
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
    onOpenInBrowser: (locationId: String?, directoryPath: String, smbSourceNodeId: Long?, httpSourceNodeId: Long?) -> Unit
) {
    when (action) {
        FolderEntryAction.OpenInBrowser -> {
            val target = resolveBrowserParentForRecentFolder(entry, networkNodes)
            if (target == null) {
                Toast.makeText(context, "Unable to open folder in browser", Toast.LENGTH_SHORT).show()
            } else {
                onOpenInBrowser(
                    target.locationId,
                    target.directoryPath,
                    target.smbSourceNodeId,
                    target.httpSourceNodeId
                )
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
    onOpenInBrowser: (locationId: String?, directoryPath: String, smbSourceNodeId: Long?, httpSourceNodeId: Long?) -> Unit
) {
    when (action) {
        SourceEntryAction.OpenInBrowser -> {
            val target = resolveBrowserFolderForRecentSource(entry, networkNodes)
            if (target == null) {
                Toast.makeText(context, "This source cannot be opened in file browser", Toast.LENGTH_SHORT).show()
            } else {
                onOpenInBrowser(
                    target.locationId,
                    target.directoryPath,
                    target.smbSourceNodeId,
                    target.httpSourceNodeId
                )
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

internal fun applyPinnedFolderAction(
    context: Context,
    entry: HomePinnedEntry,
    action: FolderEntryAction,
    pinnedEntries: List<HomePinnedEntry>,
    onPinnedEntriesChanged: (List<HomePinnedEntry>) -> Unit,
    onOpenInBrowser: (locationId: String?, directoryPath: String, smbSourceNodeId: Long?, httpSourceNodeId: Long?) -> Unit,
    networkNodes: List<NetworkNode>
) {
    val recentEntry = entry.asRecentPathEntry()
    when (action) {
        FolderEntryAction.OpenInBrowser -> {
            val target = resolveBrowserParentForRecentFolder(recentEntry, networkNodes)
            if (target == null) {
                Toast.makeText(context, "Unable to open folder in browser", Toast.LENGTH_SHORT).show()
            } else {
                onOpenInBrowser(
                    target.locationId,
                    target.directoryPath,
                    target.smbSourceNodeId,
                    target.httpSourceNodeId
                )
            }
        }

        FolderEntryAction.DeleteFromRecents -> {
            val updated = pinnedEntries.filterNot { pinned -> samePath(pinned.path, entry.path) }
            onPinnedEntriesChanged(updated)
            Toast.makeText(context, "Folder unpinned", Toast.LENGTH_SHORT).show()
        }

        FolderEntryAction.CopyPath -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Path", entry.path))
            Toast.makeText(context, "Copied path", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun applyPinnedSourceAction(
    context: Context,
    entry: HomePinnedEntry,
    action: SourceEntryAction,
    pinnedEntries: List<HomePinnedEntry>,
    onPinnedEntriesChanged: (List<HomePinnedEntry>) -> Unit,
    resolveShareableFileForRecent: (HomePinnedEntry) -> File?,
    onOpenInBrowser: (locationId: String?, directoryPath: String, smbSourceNodeId: Long?, httpSourceNodeId: Long?) -> Unit,
    networkNodes: List<NetworkNode>
) {
    val recentEntry = entry.asRecentPathEntry()
    when (action) {
        SourceEntryAction.OpenInBrowser -> {
            val target = resolveBrowserFolderForRecentSource(recentEntry, networkNodes)
            if (target == null) {
                Toast.makeText(context, "This source cannot be opened in file browser", Toast.LENGTH_SHORT).show()
            } else {
                onOpenInBrowser(
                    target.locationId,
                    target.directoryPath,
                    target.smbSourceNodeId,
                    target.httpSourceNodeId
                )
            }
        }

        SourceEntryAction.DeleteFromRecents -> {
            val updated = pinnedEntries.filterNot { pinned -> samePath(pinned.path, entry.path) }
            onPinnedEntriesChanged(updated)
            Toast.makeText(context, "File unpinned", Toast.LENGTH_SHORT).show()
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
    val smbSourceNodeId: Long?,
    val httpSourceNodeId: Long?
)

internal data class SmbTargetResolution(
    val sourceNodeId: Long?,
    val requestUri: String
)

internal data class HttpTargetResolution(
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
        val logicalDirectory = buildArchiveDirectoryPath(
            archivePath = archiveSource.archivePath,
            inArchiveDirectoryPath = parentInArchive.ifBlank { null }
        )
        val archiveSmb = parseSmbSourceSpecFromInput(archiveSource.archivePath)
        val archiveHttp = parseHttpSourceSpecFromInput(archiveSource.archivePath)
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = logicalDirectory,
            smbSourceNodeId = if (archiveSmb != null) entry.sourceNodeId else null,
            httpSourceNodeId = if (archiveHttp != null) entry.sourceNodeId else null
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
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = smbTarget.requestUri,
            smbSourceNodeId = smbTarget.sourceNodeId,
            httpSourceNodeId = null
        )
    }
    if (scheme == "http" || scheme == "https") {
        val httpSpec = parseHttpSourceSpecFromInput(entry.path) ?: return null
        val normalizedPath = normalizeHttpPath(httpSpec.path)
        val parentPath = normalizedPath
            .trimEnd('/')
            .substringBeforeLast('/', missingDelimiterValue = "")
            .trim()
        val parentSpec = httpSpec.copy(
            path = if (parentPath.isBlank()) "/" else "$parentPath/",
            query = null
        )
        val httpTarget = resolveHttpRecentOpenTarget(
            targetSpec = parentSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = httpTarget.requestUri,
            smbSourceNodeId = null,
            httpSourceNodeId = httpTarget.sourceNodeId
        )
    }

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
        smbSourceNodeId = null,
        httpSourceNodeId = null
    )
}

private fun resolveBrowserParentForRecentFolder(
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>
): BrowserOpenTarget? {
    parseArchiveLogicalPath(entry.path)?.let { (archivePath, entryPath) ->
        if (entryPath.isNullOrBlank()) {
            val archiveParent = resolveArchiveContainerParentLocation(archivePath) ?: return null
            val archiveSmb = parseSmbSourceSpecFromInput(archivePath)
            val archiveHttp = parseHttpSourceSpecFromInput(archivePath)
            val parentSmbSpec = parseSmbSourceSpecFromInput(archiveParent)
            val parentHttpSpec = parseHttpSourceSpecFromInput(archiveParent)
            val resolvedArchiveParent = when {
                parentSmbSpec != null -> resolveSmbRecentOpenTarget(
                    targetSpec = parentSmbSpec,
                    networkNodes = networkNodes,
                    preferredSourceNodeId = entry.sourceNodeId
                ).requestUri
                parentHttpSpec != null -> resolveHttpRecentOpenTarget(
                    targetSpec = parentHttpSpec,
                    networkNodes = networkNodes,
                    preferredSourceNodeId = entry.sourceNodeId
                ).requestUri
                else -> archiveParent
            }
            return BrowserOpenTarget(
                locationId = null,
                directoryPath = resolvedArchiveParent,
                smbSourceNodeId = if (archiveSmb != null) entry.sourceNodeId else null,
                httpSourceNodeId = if (archiveHttp != null) entry.sourceNodeId else null
            )
        }
        val parentInArchive = entryPath
            .replace('\\', '/')
            .trim('/')
            .substringBeforeLast('/', "")
            .trim('/')
        val logicalParent = buildArchiveDirectoryPath(
            archivePath = archivePath,
            inArchiveDirectoryPath = parentInArchive.ifBlank { null }
        )
        val archiveSmb = parseSmbSourceSpecFromInput(archivePath)
        val archiveHttp = parseHttpSourceSpecFromInput(archivePath)
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = logicalParent,
            smbSourceNodeId = if (archiveSmb != null) entry.sourceNodeId else null,
            httpSourceNodeId = if (archiveHttp != null) entry.sourceNodeId else null
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
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = smbTarget.requestUri,
            smbSourceNodeId = smbTarget.sourceNodeId,
            httpSourceNodeId = null
        )
    }
    val httpSpec = parseHttpSourceSpecFromInput(rawPath)
    if (httpSpec != null) {
        val normalizedPath = normalizeHttpPath(httpSpec.path)
        if (normalizedPath == "/") return null
        val parentPath = normalizedPath
            .trimEnd('/')
            .substringBeforeLast('/', missingDelimiterValue = "")
            .trim()
        val parentSpec = httpSpec.copy(
            path = if (parentPath.isBlank()) "/" else "$parentPath/",
            query = null
        )
        val httpTarget = resolveHttpRecentOpenTarget(
            targetSpec = parentSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = httpTarget.requestUri,
            smbSourceNodeId = null,
            httpSourceNodeId = httpTarget.sourceNodeId
        )
    }

    val folder = File(rawPath)
    val parentPath = folder.parentFile?.absolutePath ?: return null
    return BrowserOpenTarget(
        locationId = entry.locationId,
        directoryPath = parentPath,
        smbSourceNodeId = null,
        httpSourceNodeId = null
    )
}

internal fun resolveSmbRecentOpenTarget(
    targetSpec: SmbSourceSpec,
    networkNodes: List<NetworkNode>,
    preferredSourceNodeId: Long? = null
): SmbTargetResolution {
    if (!targetSpec.password.isNullOrBlank()) {
        return SmbTargetResolution(
            sourceNodeId = null,
            requestUri = buildSmbRequestUri(targetSpec)
        )
    }

    val preferredNodeSpec = preferredSourceNodeId
        ?.let { sourceNodeId ->
            networkNodes.firstOrNull { node ->
                node.id == sourceNodeId &&
                    node.type == NetworkNodeType.RemoteSource &&
                    node.sourceKind == NetworkSourceKind.Smb
            }
        }
        ?.let(::resolveNetworkNodeSmbSpec)

    if (preferredNodeSpec != null) {
        val exactSpec = targetSpec.copy(
            username = preferredNodeSpec.username,
            password = preferredNodeSpec.password
        )
        return SmbTargetResolution(
            sourceNodeId = preferredSourceNodeId,
            requestUri = buildSmbRequestUri(exactSpec)
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

internal fun resolveHttpRecentOpenTarget(
    targetSpec: HttpSourceSpec,
    networkNodes: List<NetworkNode>,
    preferredSourceNodeId: Long? = null
): HttpTargetResolution {
    if (!targetSpec.password.isNullOrBlank()) {
        return HttpTargetResolution(
            sourceNodeId = null,
            requestUri = buildHttpRequestUri(targetSpec)
        )
    }

    val preferredNodeSpec = preferredSourceNodeId
        ?.let { sourceNodeId ->
            networkNodes.firstOrNull { node ->
                node.id == sourceNodeId &&
                    node.type == NetworkNodeType.RemoteSource &&
                    node.sourceKind != NetworkSourceKind.Smb
            }
        }
        ?.let(::resolveNetworkNodeSourceId)
        ?.let(::parseHttpSourceSpecFromInput)

    if (preferredNodeSpec != null) {
        val exactSpec = targetSpec.copy(
            username = preferredNodeSpec.username ?: targetSpec.username,
            password = preferredNodeSpec.password ?: targetSpec.password
        )
        return HttpTargetResolution(
            sourceNodeId = preferredSourceNodeId,
            requestUri = buildHttpRequestUri(exactSpec)
        )
    }

    val normalizedTargetPath = normalizeHttpDirectoryPath(targetSpec.path)
    val normalizedTargetUser = targetSpec.username?.trim().orEmpty()
    val normalizedTargetPort = targetSpec.port ?: -1
    val candidate = networkNodes
        .asSequence()
        .filter { node ->
            node.type == NetworkNodeType.RemoteSource && node.sourceKind != NetworkSourceKind.Smb
        }
        .mapNotNull { node ->
            val spec = resolveNetworkNodeSourceId(node)
                ?.let(::parseHttpSourceSpecFromInput)
                ?: return@mapNotNull null
            if (!spec.scheme.equals(targetSpec.scheme, ignoreCase = true)) return@mapNotNull null
            if (!spec.host.equals(targetSpec.host, ignoreCase = true)) return@mapNotNull null
            val normalizedNodePort = spec.port ?: -1
            if (normalizedNodePort != normalizedTargetPort) return@mapNotNull null

            val normalizedNodePath = normalizeHttpDirectoryPath(spec.path)
            val normalizedNodeUser = spec.username?.trim().orEmpty()
            var score = 0
            if (
                normalizedNodeUser.isNotEmpty() &&
                normalizedNodeUser.equals(normalizedTargetUser, ignoreCase = true)
            ) {
                score += 200
            }
            if (normalizedNodePath.equals(normalizedTargetPath, ignoreCase = true)) {
                score += 400
            } else {
                if (normalizedTargetPath.startsWith(normalizedNodePath)) {
                    score += 100 + normalizedNodePath.length
                }
                if (normalizedNodePath.startsWith(normalizedTargetPath)) {
                    score += 40 + normalizedTargetPath.length
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
            username = credentialSpec.username ?: targetSpec.username,
            password = credentialSpec.password ?: targetSpec.password
        )
    }
    return HttpTargetResolution(
        sourceNodeId = resolvedNodeId,
        requestUri = buildHttpRequestUri(finalSpec)
    )
}
