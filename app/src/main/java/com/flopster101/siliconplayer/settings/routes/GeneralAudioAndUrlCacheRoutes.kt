package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

internal data class GeneralAudioRouteState(
    val respondHeadphoneMediaButtons: Boolean,
    val pauseOnHeadphoneDisconnect: Boolean,
    val audioFocusInterrupt: Boolean,
    val audioDucking: Boolean,
    val audioBackendPreference: AudioBackendPreference,
    val audioPerformanceMode: AudioPerformanceMode,
    val audioBufferPreset: AudioBufferPreset,
    val audioResamplerPreference: AudioResamplerPreference,
    val audioAllowBackendFallback: Boolean
)

internal data class GeneralAudioRouteActions(
    val onRespondHeadphoneMediaButtonsChanged: (Boolean) -> Unit,
    val onPauseOnHeadphoneDisconnectChanged: (Boolean) -> Unit,
    val onAudioFocusInterruptChanged: (Boolean) -> Unit,
    val onAudioDuckingChanged: (Boolean) -> Unit,
    val onOpenAudioEffects: () -> Unit,
    val onClearAllAudioParameters: () -> Unit,
    val onClearPluginAudioParameters: () -> Unit,
    val onClearSongAudioParameters: () -> Unit,
    val onAudioBackendPreferenceChanged: (AudioBackendPreference) -> Unit,
    val onAudioPerformanceModeChanged: (AudioPerformanceMode) -> Unit,
    val onAudioBufferPresetChanged: (AudioBufferPreset) -> Unit,
    val onAudioResamplerPreferenceChanged: (AudioResamplerPreference) -> Unit,
    val onAudioAllowBackendFallbackChanged: (Boolean) -> Unit
)

internal data class UrlCacheRouteState(
    val urlCacheClearOnLaunch: Boolean,
    val urlCacheMaxTracks: Int,
    val urlCacheMaxBytes: Long
)

internal data class UrlCacheRouteActions(
    val onUrlCacheClearOnLaunchChanged: (Boolean) -> Unit,
    val onUrlCacheMaxTracksChanged: (Int) -> Unit,
    val onUrlCacheMaxBytesChanged: (Long) -> Unit,
    val onOpenCacheManager: () -> Unit,
    val onClearUrlCacheNow: () -> Unit
)

@Composable
internal fun GeneralAudioRouteContent(
    state: GeneralAudioRouteState,
    actions: GeneralAudioRouteActions
) {
    SettingsSectionLabel("Output behavior")
    PlayerSettingToggleCard(
        title = "Respond to headset media buttons",
        description = "Allow headphone/bluetooth media buttons to control playback.",
        checked = state.respondHeadphoneMediaButtons,
        onCheckedChange = actions.onRespondHeadphoneMediaButtonsChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Pause on output disconnect",
        description = "Pause playback when headphones/output device disconnects.",
        checked = state.pauseOnHeadphoneDisconnect,
        onCheckedChange = actions.onPauseOnHeadphoneDisconnectChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Allow interruption by other apps",
        description = "Pause playback when another app starts playing audio.",
        checked = state.audioFocusInterrupt,
        onCheckedChange = actions.onAudioFocusInterruptChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Duck audio instead of pausing",
        description = "Lower volume temporarily for brief interruptions (e.g., notifications) instead of pausing.",
        checked = state.audioDucking,
        onCheckedChange = actions.onAudioDuckingChanged
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Audio processing")
    SettingsItemCard(
        title = "Audio effects",
        description = "Volume controls and audio processing.",
        icon = Icons.Default.Tune,
        onClick = actions.onOpenAudioEffects
    )
    Spacer(modifier = Modifier.height(10.dp))
    ClearAudioParametersCard(
        onClearAll = actions.onClearAllAudioParameters,
        onClearPlugins = actions.onClearPluginAudioParameters,
        onClearSongs = actions.onClearSongAudioParameters
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Audio output pipeline")
    AudioBackendSelectorCard(
        selectedPreference = state.audioBackendPreference,
        onSelectedPreferenceChanged = actions.onAudioBackendPreferenceChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    AudioPerformanceModeSelectorCard(
        selectedMode = state.audioPerformanceMode,
        onSelectedModeChanged = actions.onAudioPerformanceModeChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    AudioBufferPresetSelectorCard(
        selectedPreset = state.audioBufferPreset,
        onSelectedPresetChanged = actions.onAudioBufferPresetChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    AudioResamplerSelectorCard(
        selectedPreference = state.audioResamplerPreference,
        onSelectedPreferenceChanged = actions.onAudioResamplerPreferenceChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Allow backend fallback",
        description = "If selected backend is unavailable, fall back automatically to a working output backend.",
        checked = state.audioAllowBackendFallback,
        onCheckedChange = actions.onAudioAllowBackendFallbackChanged
    )
}

@Composable
internal fun UrlCacheRouteContent(
    state: UrlCacheRouteState,
    actions: UrlCacheRouteActions
) {
    val urlCacheClearOnLaunch = state.urlCacheClearOnLaunch
    val onUrlCacheClearOnLaunchChanged = actions.onUrlCacheClearOnLaunchChanged
    val urlCacheMaxTracks = state.urlCacheMaxTracks
    val onUrlCacheMaxTracksChanged = actions.onUrlCacheMaxTracksChanged
    val urlCacheMaxBytes = state.urlCacheMaxBytes
    val onUrlCacheMaxBytesChanged = actions.onUrlCacheMaxBytesChanged
    val onOpenCacheManager = actions.onOpenCacheManager
    val onClearUrlCacheNow = actions.onClearUrlCacheNow

    var showCacheTrackLimitDialog by remember { mutableStateOf(false) }
    var showCacheSizeLimitDialog by remember { mutableStateOf(false) }
    var showClearCacheConfirmDialog by remember { mutableStateOf(false) }
    SettingsSectionLabel("Cache behavior")
    PlayerSettingToggleCard(
        title = "Clear cache on app launch",
        description = "Delete all cached files each time the app starts.",
        checked = urlCacheClearOnLaunch,
        onCheckedChange = onUrlCacheClearOnLaunchChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsSectionLabel("Limits")
    SettingsItemCard(
        title = "Cache song limit",
        description = "$urlCacheMaxTracks songs",
        icon = Icons.Default.MoreHoriz,
        onClick = { showCacheTrackLimitDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Cache size limit",
        description = String.format(Locale.US, "%.2f GB", urlCacheMaxBytes / (1024.0 * 1024.0 * 1024.0)),
        icon = Icons.Default.MoreHoriz,
        onClick = { showCacheSizeLimitDialog = true }
    )
    Spacer(modifier = Modifier.height(10.dp))
    SettingsItemCard(
        title = "Manage cached files",
        description = "Browse cached files, long-press multi-select, delete, and export.",
        icon = Icons.Default.MoreHoriz,
        onClick = onOpenCacheManager
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Danger zone")
    SettingsItemCard(
        title = "Clear cache now",
        description = "Delete all currently cached files immediately.",
        icon = Icons.Default.Delete,
        onClick = { showClearCacheConfirmDialog = true }
    )

    if (showCacheTrackLimitDialog) {
        SettingsTextInputDialog(
            title = "Cache song limit",
            fieldLabel = "Max songs",
            initialValue = urlCacheMaxTracks.toString(),
            placeholder = "100",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            sanitizer = { input -> input.filter { it.isDigit() }.take(6) },
            onDismiss = { showCacheTrackLimitDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onUrlCacheMaxTracksChanged(parsed)
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showCacheSizeLimitDialog) {
        CacheSizeLimitDialog(
            initialBytes = urlCacheMaxBytes,
            onDismiss = { showCacheSizeLimitDialog = false },
            onConfirmBytes = onUrlCacheMaxBytesChanged
        )
    }

    if (showClearCacheConfirmDialog) {
        SettingsConfirmDialog(
            title = "Clear cached files now?",
            message = "This will remove all cached files, except the currently active one if it is being played.",
            confirmLabel = "Clear cache",
            onDismiss = { showClearCacheConfirmDialog = false },
            onConfirm = onClearUrlCacheNow
        )
    }
}
