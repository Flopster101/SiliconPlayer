package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AppNavigationTrackPreferenceEffects(
    context: Context,
    prefs: SharedPreferences,
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    preferredRepeatMode: RepeatMode,
    isPlayerSurfaceVisible: Boolean,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    autoPlayNextTrackOnEnd: Boolean,
    previousRestartsAfterThreshold: Boolean,
    fadePauseResume: Boolean,
    rememberBrowserLocation: Boolean,
    onArtworkBitmapChanged: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit,
    refreshRepeatModeForTrack: () -> Unit,
    refreshSubtuneState: () -> Unit,
    resetSubtuneUiState: () -> Unit,
    onRememberBrowserLocationCleared: () -> Unit
) {
    LaunchedEffect(selectedFile, preferredRepeatMode) {
        refreshRepeatModeForTrack()
    }

    LaunchedEffect(selectedFile, currentPlaybackSourceId) {
        onArtworkBitmapChanged(
            withContext(Dispatchers.IO) {
                loadArtworkForSource(
                    context = context,
                    displayFile = selectedFile,
                    sourceId = currentPlaybackSourceId
                )
            }
        )
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
                .remove(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID)
                .remove(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH)
        }
        editor.apply()
    }
}
