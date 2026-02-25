package com.flopster101.siliconplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal object RemotePlayableSourceIdsHolder {
    var current by mutableStateOf<List<String>>(emptyList())
}
