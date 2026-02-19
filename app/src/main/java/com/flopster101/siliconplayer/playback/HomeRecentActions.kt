package com.flopster101.siliconplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
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
    val isRemote = scheme == "http" || scheme == "https"
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
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit
) {
    when (action) {
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
    onRecentPlayedFilesChanged: (List<RecentPathEntry>) -> Unit,
    resolveShareableFileForRecent: (RecentPathEntry) -> File?
) {
    when (action) {
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
