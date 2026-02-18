package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
internal fun AppNavigationUiEffects(
    context: Context,
    prefs: SharedPreferences,
    keepScreenOn: Boolean,
    isPlayerExpanded: Boolean,
    playerArtworkCornerRadiusDp: Int,
    onPlayerArtworkCornerRadiusChanged: (Int) -> Unit,
    onMiniExpandPreviewProgressChanged: (Float) -> Unit,
    onExpandFromMiniDragChanged: (Boolean) -> Unit,
    onCollapseFromSwipeChanged: (Boolean) -> Unit
) {
    LaunchedEffect(keepScreenOn, isPlayerExpanded) {
        val window = (context as? ComponentActivity)?.window ?: return@LaunchedEffect
        if (keepScreenOn && isPlayerExpanded) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(keepScreenOn) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.KEEP_SCREEN_ON, keepScreenOn)
            .apply()
    }

    LaunchedEffect(playerArtworkCornerRadiusDp) {
        val normalized = playerArtworkCornerRadiusDp.coerceIn(0, 48)
        if (normalized != playerArtworkCornerRadiusDp) {
            onPlayerArtworkCornerRadiusChanged(normalized)
            return@LaunchedEffect
        }
        prefs.edit()
            .putInt(AppPreferenceKeys.PLAYER_ARTWORK_CORNER_RADIUS_DP, normalized)
            .apply()
    }

    LaunchedEffect(isPlayerExpanded) {
        if (!isPlayerExpanded) {
            onMiniExpandPreviewProgressChanged(0f)
            delay(160)
            onExpandFromMiniDragChanged(false)
            onCollapseFromSwipeChanged(false)
        }
    }
}
