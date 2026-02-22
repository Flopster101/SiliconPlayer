package com.flopster101.siliconplayer

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun adaptiveDialogModifier(): Modifier {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (!isLandscape) {
        // Portrait baseline matches Core capabilities dialog width.
        return Modifier.fillMaxWidth(0.85f)
    }

    val landscapeWidthFraction = when {
        configuration.screenWidthDp >= 1400 -> 0.50f
        configuration.screenWidthDp >= 1100 -> 0.56f
        configuration.screenWidthDp >= 840 -> 0.62f
        else -> 0.68f
    }
    val landscapeMaxWidth = when {
        configuration.screenWidthDp >= 1400 -> 900.dp
        configuration.screenWidthDp >= 1100 -> 860.dp
        else -> 800.dp
    }

    return Modifier
        .fillMaxWidth(landscapeWidthFraction)
        .widthIn(max = landscapeMaxWidth)
}

internal fun adaptiveDialogProperties(): DialogProperties {
    return DialogProperties(usePlatformDefaultWidth = false)
}
