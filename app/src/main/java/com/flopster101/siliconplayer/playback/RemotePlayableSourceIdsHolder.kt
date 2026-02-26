package com.flopster101.siliconplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal object RemotePlayableSourceIdsHolder {
    private val currentState = mutableStateOf<List<String>>(emptyList())
    private val lastNonEmptyState = mutableStateOf<List<String>>(emptyList())

    var lastNonEmpty: List<String>
        get() = lastNonEmptyState.value
        private set(value) {
            lastNonEmptyState.value = value
        }

    var current: List<String>
        get() = currentState.value
        set(value) {
            currentState.value = value
            if (value.isNotEmpty()) {
                lastNonEmpty = value
            }
        }
}
