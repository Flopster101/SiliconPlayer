package com.flopster101.siliconplayer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberMiniPlayerListInset(
    currentView: MainView,
    isPlayerSurfaceVisible: Boolean
): Dp {
    val target = when {
        currentView == MainView.Browser && isPlayerSurfaceVisible -> 108.dp
        currentView == MainView.Network && isPlayerSurfaceVisible -> 108.dp
        currentView == MainView.Home && isPlayerSurfaceVisible -> 108.dp
        currentView == MainView.Settings && isPlayerSurfaceVisible -> 108.dp
        else -> 0.dp
    }
    return animateDpAsState(
        targetValue = target,
        label = "miniPlayerListInset"
    ).value
}
