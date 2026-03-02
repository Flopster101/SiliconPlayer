package com.flopster101.siliconplayer

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
internal fun PreferenceChangeSyncEffect(
    prefs: SharedPreferences,
    watchedKeys: Set<String>,
    onRelevantChange: () -> Unit
) {
    DisposableEffect(prefs, watchedKeys) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key in watchedKeys) {
                onRelevantChange()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}
