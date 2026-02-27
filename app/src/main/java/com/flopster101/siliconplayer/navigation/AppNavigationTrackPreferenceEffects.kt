package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun AppNavigationTrackPreferenceEffects(
    context: Context,
    prefs: SharedPreferences,
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    currentPlaybackRequestUrl: String?,
    preferredRepeatMode: RepeatMode,
    isPlayerSurfaceVisible: Boolean,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    autoPlayNextTrackOnEnd: Boolean,
    preloadNextCachedRemoteTrack: Boolean,
    playlistWrapNavigation: Boolean,
    previousRestartsAfterThreshold: Boolean,
    fadePauseResume: Boolean,
    rememberBrowserLocation: Boolean,
    showParentDirectoryEntry: Boolean,
    showFileIconChipBackground: Boolean,
    sortArchivesBeforeFiles: Boolean,
    browserNameSortMode: BrowserNameSortMode,
    onArtworkBitmapChanged: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit,
    refreshRepeatModeForTrack: () -> Unit,
    refreshSubtuneState: () -> Unit,
    resetSubtuneUiState: () -> Unit,
    onRememberBrowserLocationCleared: () -> Unit
) {
    LaunchedEffect(selectedFile, preferredRepeatMode) {
        refreshRepeatModeForTrack()
    }

    LaunchedEffect(selectedFile, currentPlaybackSourceId, currentPlaybackRequestUrl) {
        var resolvedArtwork: androidx.compose.ui.graphics.ImageBitmap? = null
        repeat(8) { attempt ->
            resolvedArtwork = withContext(Dispatchers.IO) {
                loadArtworkForSource(
                    context = context,
                    displayFile = selectedFile,
                    sourceId = currentPlaybackSourceId,
                    requestUrl = currentPlaybackRequestUrl
                )
            }
            if (resolvedArtwork != null) return@repeat
            if (attempt < 7) {
                delay(130L)
            }
        }
        onArtworkBitmapChanged(resolvedArtwork)
    }

    LaunchedEffect(selectedFile, currentPlaybackSourceId, isPlayerSurfaceVisible) {
        if (!isPlayerSurfaceVisible || selectedFile == null) {
            resetSubtuneUiState()
        } else {
            refreshSubtuneState()
        }
    }

    LaunchedEffect(autoPlayOnTrackSelect) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.AUTO_PLAY_ON_TRACK_SELECT, autoPlayOnTrackSelect)
            .apply()
    }

    LaunchedEffect(openPlayerOnTrackSelect) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.OPEN_PLAYER_ON_TRACK_SELECT, openPlayerOnTrackSelect)
            .apply()
    }

    LaunchedEffect(autoPlayNextTrackOnEnd) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.AUTO_PLAY_NEXT_TRACK_ON_END, autoPlayNextTrackOnEnd)
            .apply()
    }

    LaunchedEffect(preloadNextCachedRemoteTrack) {
        prefs.edit()
            .putBoolean(
                AppPreferenceKeys.PRELOAD_NEXT_CACHED_REMOTE_TRACK,
                preloadNextCachedRemoteTrack
            )
            .apply()
    }

    LaunchedEffect(playlistWrapNavigation) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.PLAYLIST_WRAP_NAVIGATION, playlistWrapNavigation)
            .apply()
    }

    LaunchedEffect(previousRestartsAfterThreshold) {
        prefs.edit()
            .putBoolean(
                AppPreferenceKeys.PREVIOUS_RESTART_AFTER_THRESHOLD,
                previousRestartsAfterThreshold
            )
            .apply()
    }

    LaunchedEffect(fadePauseResume) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.FADE_PAUSE_RESUME, fadePauseResume)
            .apply()
        PlaybackService.refreshSettings(context)
    }

    LaunchedEffect(rememberBrowserLocation) {
        val editor = prefs.edit()
            .putBoolean(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, rememberBrowserLocation)
        if (!rememberBrowserLocation) {
            onRememberBrowserLocationCleared()
            editor
                .remove(AppPreferenceKeys.BROWSER_LAST_LOCATION_STATE_JSON)
                .remove(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID)
                .remove(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH)
        }
        editor.apply()
    }

    LaunchedEffect(sortArchivesBeforeFiles) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.BROWSER_SORT_ARCHIVES_BEFORE_FILES, sortArchivesBeforeFiles)
            .apply()
    }

    LaunchedEffect(showParentDirectoryEntry) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.BROWSER_SHOW_PARENT_DIRECTORY_ENTRY, showParentDirectoryEntry)
            .apply()
    }

    LaunchedEffect(showFileIconChipBackground) {
        prefs.edit()
            .putBoolean(
                AppPreferenceKeys.BROWSER_SHOW_FILE_ICON_CHIP_BACKGROUND,
                showFileIconChipBackground
            )
            .apply()
    }

    LaunchedEffect(browserNameSortMode) {
        prefs.edit()
            .putString(AppPreferenceKeys.BROWSER_NAME_SORT_MODE, browserNameSortMode.storageValue)
            .apply()
    }
}
