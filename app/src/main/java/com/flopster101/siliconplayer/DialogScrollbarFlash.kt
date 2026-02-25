package com.flopster101.siliconplayer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
internal fun rememberDialogScrollbarAlpha(
    enabled: Boolean,
    scrollState: ScrollState,
    label: String,
    flashDurationMs: Long = 900L,
    idleHideDelayMs: Long = 800L,
    fadeDurationMs: Int = 180
): Float {
    var visible by remember { mutableStateOf(false) }
    var flashedOnOpen by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, scrollState.maxValue) {
        if (!enabled || scrollState.maxValue <= 0) {
            visible = false
            flashedOnOpen = false
            return@LaunchedEffect
        }
        if (!flashedOnOpen) {
            flashedOnOpen = true
            visible = true
            delay(flashDurationMs)
            if (!scrollState.isScrollInProgress) {
                visible = false
            }
        }
    }

    LaunchedEffect(enabled, scrollState.isScrollInProgress, scrollState.maxValue) {
        if (!enabled || scrollState.maxValue <= 0) {
            visible = false
            return@LaunchedEffect
        }
        if (scrollState.isScrollInProgress) {
            visible = true
        } else if (flashedOnOpen) {
            delay(idleHideDelayMs)
            if (!scrollState.isScrollInProgress) {
                visible = false
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (enabled && scrollState.maxValue > 0 && visible) 1f else 0f,
        animationSpec = tween(durationMillis = fadeDurationMs),
        label = label
    )
    return alpha
}

@Composable
internal fun rememberDialogLazyListScrollbarAlpha(
    enabled: Boolean,
    listState: LazyListState,
    flashKey: Any?,
    label: String,
    flashDurationMs: Long = 900L,
    idleHideDelayMs: Long = 800L,
    fadeDurationMs: Int = 180
): Float {
    var visible by remember { mutableStateOf(false) }
    var lastFlashKey by remember { mutableStateOf<Any?>(null) }
    val isScrollable = listState.canScrollBackward || listState.canScrollForward

    LaunchedEffect(enabled, isScrollable, flashKey) {
        if (!enabled || !isScrollable) {
            visible = false
            lastFlashKey = null
            return@LaunchedEffect
        }
        if (flashKey != lastFlashKey) {
            lastFlashKey = flashKey
            visible = true
            delay(flashDurationMs)
            if (!listState.isScrollInProgress) {
                visible = false
            }
        }
    }

    LaunchedEffect(enabled, isScrollable, listState.isScrollInProgress) {
        if (!enabled || !isScrollable) {
            visible = false
            return@LaunchedEffect
        }
        if (listState.isScrollInProgress) {
            visible = true
        } else {
            delay(idleHideDelayMs)
            if (!listState.isScrollInProgress) {
                visible = false
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (enabled && isScrollable && visible) 1f else 0f,
        animationSpec = tween(durationMillis = fadeDurationMs),
        label = label
    )
    return alpha
}
