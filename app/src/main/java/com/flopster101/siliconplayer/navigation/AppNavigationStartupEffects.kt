package com.flopster101.siliconplayer

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AppNavigationStartupEffects(
    prefs: SharedPreferences,
    defaultScopeTextSizeSp: Int,
    recentFoldersLimit: Int,
    recentFilesLimit: Int,
    recentFolders: List<RecentPathEntry>,
    recentPlayedFiles: List<RecentPathEntry>,
    onFfmpegCapabilitiesChanged: (Int) -> Unit,
    onOpenMptCapabilitiesChanged: (Int) -> Unit,
    onVgmPlayCapabilitiesChanged: (Int) -> Unit,
    onMasterVolumeDbChanged: (Float) -> Unit,
    onPluginVolumeDbChanged: (Float) -> Unit,
    onForceMonoChanged: (Boolean) -> Unit,
    onRecentFoldersLimitChanged: (Int) -> Unit,
    onRecentFilesLimitChanged: (Int) -> Unit,
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit,
    onRecentPlayedFilesChanged: (List<RecentPathEntry>) -> Unit
) {
    LaunchedEffect(prefs, defaultScopeTextSizeSp) {
        if (!prefs.contains(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SIZE_SP)) {
            prefs.edit()
                .putInt(
                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SIZE_SP,
                    defaultScopeTextSizeSp
                )
                .apply()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            onFfmpegCapabilitiesChanged(NativeBridge.getCoreCapabilities("FFmpeg"))
            onOpenMptCapabilitiesChanged(NativeBridge.getCoreCapabilities("LibOpenMPT"))
            onVgmPlayCapabilitiesChanged(NativeBridge.getCoreCapabilities("VGMPlay"))
        }
    }

    LaunchedEffect(Unit) {
        val masterVolumeDb = prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f)
        val forceMono = prefs.getBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, false)

        onMasterVolumeDbChanged(masterVolumeDb)
        onPluginVolumeDbChanged(0f)
        onForceMonoChanged(forceMono)

        NativeBridge.setMasterGain(masterVolumeDb)
        NativeBridge.setPluginGain(0f)
        NativeBridge.setForceMono(forceMono)

        withContext(Dispatchers.IO) {
            loadPluginConfigurations(prefs)
        }
    }

    LaunchedEffect(recentFoldersLimit) {
        val clamped = recentFoldersLimit.coerceIn(1, RECENTS_LIMIT_MAX)
        if (clamped != recentFoldersLimit) {
            onRecentFoldersLimitChanged(clamped)
            return@LaunchedEffect
        }
        prefs.edit().putInt(AppPreferenceKeys.RECENT_FOLDERS_LIMIT, clamped).apply()
        val trimmed = recentFolders.take(clamped)
        if (trimmed.size != recentFolders.size) {
            onRecentFoldersChanged(trimmed)
        }
        writeRecentEntries(prefs, AppPreferenceKeys.RECENT_FOLDERS, trimmed, clamped)
    }

    LaunchedEffect(recentFilesLimit) {
        val clamped = recentFilesLimit.coerceIn(1, RECENTS_LIMIT_MAX)
        if (clamped != recentFilesLimit) {
            onRecentFilesLimitChanged(clamped)
            return@LaunchedEffect
        }
        prefs.edit().putInt(AppPreferenceKeys.RECENT_PLAYED_FILES_LIMIT, clamped).apply()
        val trimmed = recentPlayedFiles.take(clamped)
        if (trimmed.size != recentPlayedFiles.size) {
            onRecentPlayedFilesChanged(trimmed)
        }
        writeRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, trimmed, clamped)
    }
}
