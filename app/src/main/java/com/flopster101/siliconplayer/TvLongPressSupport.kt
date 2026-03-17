package com.flopster101.siliconplayer

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.onPreviewKeyEvent

fun Modifier.tvKeyLongPress(
    onLongClick: (() -> Unit)?
): Modifier = composed {
    if (onLongClick == null) {
        return@composed this
    }

    var longPressArmed by remember { mutableStateOf(false) }
    var activeKeyCode by remember { mutableIntStateOf(AndroidKeyEvent.KEYCODE_UNKNOWN) }

    fun isSupportedConfirmKey(keyCode: Int): Boolean {
        return when (keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_SPACE,
            AndroidKeyEvent.KEYCODE_BUTTON_A -> true
            else -> false
        }
    }

    this.onPreviewKeyEvent { keyEvent ->
        val nativeEvent = keyEvent.nativeKeyEvent
        if (!isSupportedConfirmKey(nativeEvent.keyCode)) {
            return@onPreviewKeyEvent false
        }

        when (nativeEvent.action) {
            AndroidKeyEvent.ACTION_DOWN -> {
                val longPressSignaled =
                    nativeEvent.repeatCount > 0 ||
                        (nativeEvent.flags and AndroidKeyEvent.FLAG_LONG_PRESS) != 0
                if (!longPressSignaled) {
                    activeKeyCode = nativeEvent.keyCode
                    longPressArmed = false
                    false
                } else {
                    if (!longPressArmed || activeKeyCode != nativeEvent.keyCode) {
                        activeKeyCode = nativeEvent.keyCode
                        longPressArmed = true
                    }
                    true
                }
            }

            AndroidKeyEvent.ACTION_UP -> {
                val shouldTriggerLongClick = longPressArmed && activeKeyCode == nativeEvent.keyCode
                if (activeKeyCode == nativeEvent.keyCode) {
                    activeKeyCode = AndroidKeyEvent.KEYCODE_UNKNOWN
                    longPressArmed = false
                }
                if (shouldTriggerLongClick) {
                    onLongClick()
                }
                shouldTriggerLongClick
            }

            else -> false
        }
    }
}
