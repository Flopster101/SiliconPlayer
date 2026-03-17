package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal fun Modifier.dialogScrollableContentNavigation(
    scrollState: ScrollState,
    focusRequester: FocusRequester,
    viewportHeightPx: Int,
    actionFocusRequester: FocusRequester? = null
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minimumScrollStepPx = with(density) { 96.dp.roundToPx() }

    fun scrollStepPx(): Int {
        return if (viewportHeightPx > 0) {
            maxOf((viewportHeightPx * 0.72f).toInt(), minimumScrollStepPx)
        } else {
            minimumScrollStepPx
        }
    }

    fun requestActionFocus(): Boolean {
        return if (actionFocusRequester != null) {
            actionFocusRequester.requestFocus()
            true
        } else {
            false
        }
    }

    fun animateTo(target: Int): Boolean {
        scope.launch { scrollState.animateScrollTo(target) }
        return true
    }

    this
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) {
                return@onPreviewKeyEvent false
            }
            when (keyEvent.key) {
                Key.DirectionDown, Key.PageDown -> {
                    if (scrollState.value < scrollState.maxValue) {
                        animateTo((scrollState.value + scrollStepPx()).coerceAtMost(scrollState.maxValue))
                    } else {
                        requestActionFocus()
                    }
                }
                Key.DirectionUp, Key.PageUp -> {
                    if (scrollState.value > 0) {
                        animateTo((scrollState.value - scrollStepPx()).coerceAtLeast(0))
                    } else {
                        false
                    }
                }
                Key.MoveHome -> {
                    if (scrollState.value > 0) {
                        animateTo(0)
                    } else {
                        false
                    }
                }
                Key.MoveEnd -> {
                    if (scrollState.value < scrollState.maxValue) {
                        animateTo(scrollState.maxValue)
                    } else {
                        requestActionFocus()
                    }
                }
                Key.DirectionLeft, Key.DirectionRight -> requestActionFocus()
                else -> false
            }
        }
}
