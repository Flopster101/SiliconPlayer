package com.flopster101.siliconplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun GeneralAudioRouteContent(
    respondHeadphoneMediaButtons: Boolean,
    onRespondHeadphoneMediaButtonsChanged: (Boolean) -> Unit,
    pauseOnHeadphoneDisconnect: Boolean,
    onPauseOnHeadphoneDisconnectChanged: (Boolean) -> Unit,
    audioFocusInterrupt: Boolean,
    onAudioFocusInterruptChanged: (Boolean) -> Unit,
    audioDucking: Boolean,
    onAudioDuckingChanged: (Boolean) -> Unit,
    onOpenAudioEffects: () -> Unit,
    onClearAllAudioParameters: () -> Unit,
    onClearPluginAudioParameters: () -> Unit,
    onClearSongAudioParameters: () -> Unit,
    audioBackendPreference: AudioBackendPreference,
    onAudioBackendPreferenceChanged: (AudioBackendPreference) -> Unit,
    audioPerformanceMode: AudioPerformanceMode,
    onAudioPerformanceModeChanged: (AudioPerformanceMode) -> Unit,
    audioBufferPreset: AudioBufferPreset,
    onAudioBufferPresetChanged: (AudioBufferPreset) -> Unit,
    audioResamplerPreference: AudioResamplerPreference,
    onAudioResamplerPreferenceChanged: (AudioResamplerPreference) -> Unit,
    audioAllowBackendFallback: Boolean,
    onAudioAllowBackendFallbackChanged: (Boolean) -> Unit
) {
    SettingsSectionLabel("Output behavior")
    PlayerSettingToggleCard(
        title = "Respond to headset media buttons",
        description = "Allow headphone/bluetooth media buttons to control playback.",
        checked = respondHeadphoneMediaButtons,
        onCheckedChange = onRespondHeadphoneMediaButtonsChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Pause on output disconnect",
        description = "Pause playback when headphones/output device disconnects.",
        checked = pauseOnHeadphoneDisconnect,
        onCheckedChange = onPauseOnHeadphoneDisconnectChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Allow interruption by other apps",
        description = "Pause playback when another app starts playing audio.",
        checked = audioFocusInterrupt,
        onCheckedChange = onAudioFocusInterruptChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Duck audio instead of pausing",
        description = "Lower volume temporarily for brief interruptions (e.g., notifications) instead of pausing.",
        checked = audioDucking,
        onCheckedChange = onAudioDuckingChanged
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Audio processing")
    SettingsItemCard(
        title = "Audio effects",
        description = "Volume controls and audio processing.",
        icon = Icons.Default.Tune,
        onClick = onOpenAudioEffects
    )
    Spacer(modifier = Modifier.height(10.dp))
    ClearAudioParametersCard(
        onClearAll = onClearAllAudioParameters,
        onClearPlugins = onClearPluginAudioParameters,
        onClearSongs = onClearSongAudioParameters
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Audio output pipeline")
    AudioBackendSelectorCard(
        selectedPreference = audioBackendPreference,
        onSelectedPreferenceChanged = onAudioBackendPreferenceChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    AudioPerformanceModeSelectorCard(
        selectedMode = audioPerformanceMode,
        onSelectedModeChanged = onAudioPerformanceModeChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    AudioBufferPresetSelectorCard(
        selectedPreset = audioBufferPreset,
        onSelectedPresetChanged = onAudioBufferPresetChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    AudioResamplerSelectorCard(
        selectedPreference = audioResamplerPreference,
        onSelectedPreferenceChanged = onAudioResamplerPreferenceChanged
    )
    Spacer(modifier = Modifier.height(10.dp))
    PlayerSettingToggleCard(
        title = "Allow backend fallback",
        description = "If selected backend is unavailable, fall back automatically to a working output backend.",
        checked = audioAllowBackendFallback,
        onCheckedChange = onAudioAllowBackendFallbackChanged
    )
}

@Composable
internal fun UrlCacheRouteContent(
    urlCacheClearOnLaunch: Boolean,
    onUrlCacheClearOnLaunchChanged: (Boolean) -> Unit,
    urlCacheMaxTracks: Int,
    onUrlCacheMaxTracksChanged: (Int) -> Unit,
    urlCacheMaxBytes: Long,
    onUrlCacheMaxBytesChanged: (Long) -> Unit,
    onOpenCacheManager: () -> Unit,
    onClearUrlCacheNow: () -> Unit
) {
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
        var input by remember { mutableStateOf(urlCacheMaxTracks.toString()) }
        AlertDialog(
            onDismissRequest = { showCacheTrackLimitDialog = false },
            title = { Text("Cache song limit") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("Max songs") },
                    placeholder = { Text("100") }
                )
            },
            dismissButton = {
                TextButton(onClick = { showCacheTrackLimitDialog = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = input.trim().toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        onUrlCacheMaxTracksChanged(parsed)
                        showCacheTrackLimitDialog = false
                    }
                }) {
                    Text("Save")
                }
            }
        )
    }

    if (showCacheSizeLimitDialog) {
        var unit by remember {
            mutableStateOf(
                if (urlCacheMaxBytes < CacheSizeUnit.GB.bytesPerUnit.toLong()) CacheSizeUnit.MB else CacheSizeUnit.GB
            )
        }
        var input by remember {
            mutableStateOf(
                String.format(Locale.US, "%.2f", urlCacheMaxBytes / unit.bytesPerUnit)
            )
        }
        AlertDialog(
            onDismissRequest = { showCacheSizeLimitDialog = false },
            title = { Text("Cache size limit") },
            text = {
                Column {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        label = { Text("Max size (${unit.label})") },
                        placeholder = { Text("1.00") }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CacheSizeUnit.values().forEach { candidate ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (candidate != unit) {
                                            val parsed = input.trim().replace(',', '.').toDoubleOrNull()
                                            val currentBytes = if (parsed != null && parsed > 0.0) {
                                                parsed * unit.bytesPerUnit
                                            } else {
                                                urlCacheMaxBytes.toDouble()
                                            }
                                            unit = candidate
                                            input = String.format(Locale.US, "%.2f", currentBytes / candidate.bytesPerUnit)
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = unit == candidate,
                                    onClick = {
                                        if (candidate != unit) {
                                            val parsed = input.trim().replace(',', '.').toDoubleOrNull()
                                            val currentBytes = if (parsed != null && parsed > 0.0) {
                                                parsed * unit.bytesPerUnit
                                            } else {
                                                urlCacheMaxBytes.toDouble()
                                            }
                                            unit = candidate
                                            input = String.format(Locale.US, "%.2f", currentBytes / candidate.bytesPerUnit)
                                        }
                                    }
                                )
                                Text(
                                    text = candidate.label,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCacheSizeLimitDialog = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = input.trim().replace(',', '.').toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) {
                        val bytes = (parsed * unit.bytesPerUnit).toLong().coerceAtLeast(1L)
                        onUrlCacheMaxBytesChanged(bytes)
                        showCacheSizeLimitDialog = false
                    }
                }) {
                    Text("Save")
                }
            }
        )
    }

    if (showClearCacheConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirmDialog = false },
            title = { Text("Clear cached files now?") },
            text = { Text("This will remove all cached files, except the currently active one if it is being played.") },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearUrlCacheNow()
                    showClearCacheConfirmDialog = false
                }) {
                    Text("Clear cache")
                }
            }
        )
    }
}
