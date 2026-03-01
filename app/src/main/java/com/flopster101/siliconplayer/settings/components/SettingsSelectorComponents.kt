package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.os.Build
import java.util.Locale

private data class SampleRateChoice(val hz: Int, val label: String)
private data class ThemeModeChoice(val mode: ThemeMode, val label: String)
@Immutable
private data class EnumChoice<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> SettingsEnumSelectorCard(
    title: String,
    description: String,
    selectedValue: T,
    options: List<EnumChoice<T>>,
    highlightedDescription: String? = null,
    onSelected: (T) -> Unit
) {
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: options.firstOrNull()?.label.orEmpty()
    var dialogOpen by remember { mutableStateOf(false) }

    SettingsRowContainer(onClick = { dialogOpen = true }) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!highlightedDescription.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = highlightedDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.titleMedium
        )
    }

    if (dialogOpen) {
        SettingsSingleChoiceDialog(
            title = title,
            selectedValue = selectedValue,
            options = options.map {
                ChoiceDialogOption(value = it.value, label = it.label, enabled = it.enabled)
            },
            onSelected = onSelected,
            onDismiss = { dialogOpen = false }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ThemeModeSelectorCard(
    selectedMode: ThemeMode,
    onSelectedModeChanged: (ThemeMode) -> Unit
) {
    val options = listOf(
        ThemeModeChoice(ThemeMode.Auto, "Auto"),
        ThemeModeChoice(ThemeMode.Light, "Light"),
        ThemeModeChoice(ThemeMode.Dark, "Dark")
    )
    var dialogOpen by remember { mutableStateOf(false) }

    SettingsRowContainer(onClick = { dialogOpen = true }) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "App theme",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Choose Auto, Light, or Dark theme behavior.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = selectedMode.label,
            style = MaterialTheme.typography.titleMedium
        )
    }

    if (dialogOpen) {
        SettingsSingleChoiceDialog(
            title = "App theme",
            selectedValue = selectedMode,
            options = options.map { ChoiceDialogOption(value = it.mode, label = it.label) },
            onSelected = onSelectedModeChanged,
            onDismiss = { dialogOpen = false }
        )
    }
}

@Composable
internal fun BrowserNameSortModeSelectorCard(
    selectedMode: BrowserNameSortMode,
    onSelectedModeChanged: (BrowserNameSortMode) -> Unit
) {
    SettingsEnumSelectorCard(
        title = "File name sort mode",
        description = "Choose how names with numbers are ordered in browser lists and track navigation.",
        selectedValue = selectedMode,
        options = listOf(
            EnumChoice(BrowserNameSortMode.Natural, BrowserNameSortMode.Natural.label),
            EnumChoice(BrowserNameSortMode.Lexicographic, BrowserNameSortMode.Lexicographic.label)
        ),
        onSelected = onSelectedModeChanged
    )
}

@Composable
internal fun AudioBackendSelectorCard(
    selectedPreference: AudioBackendPreference,
    onSelectedPreferenceChanged: (AudioBackendPreference) -> Unit
) {
    val isAaudioAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    SettingsEnumSelectorCard(
        title = "Audio output backend",
        description = "Preferred output backend implementation.",
        highlightedDescription = if (!isAaudioAvailable) {
            "AAudio is only available on Android 8.0 (API 26) and up."
        } else {
            null
        },
        selectedValue = selectedPreference,
        options = listOf(
            EnumChoice(
                AudioBackendPreference.AAudio,
                AudioBackendPreference.AAudio.label,
                enabled = isAaudioAvailable
            ),
            EnumChoice(AudioBackendPreference.OpenSLES, AudioBackendPreference.OpenSLES.label),
            EnumChoice(AudioBackendPreference.AudioTrack, AudioBackendPreference.AudioTrack.label)
        ),
        onSelected = onSelectedPreferenceChanged
    )
}

@Composable
internal fun AudioPerformanceModeSelectorCard(
    selectedMode: AudioPerformanceMode,
    onSelectedModeChanged: (AudioPerformanceMode) -> Unit,
    title: String = "Audio performance mode",
    description: String = "Tune output stream behavior for latency vs efficiency."
) {
    SettingsEnumSelectorCard(
        title = title,
        description = description,
        selectedValue = selectedMode,
        options = listOf(
            EnumChoice(AudioPerformanceMode.LowLatency, AudioPerformanceMode.LowLatency.label),
            EnumChoice(AudioPerformanceMode.None, AudioPerformanceMode.None.label),
            EnumChoice(AudioPerformanceMode.PowerSaving, AudioPerformanceMode.PowerSaving.label)
        ),
        onSelected = onSelectedModeChanged
    )
}

@Composable
internal fun AudioBufferPresetSelectorCard(
    selectedPreset: AudioBufferPreset,
    onSelectedPresetChanged: (AudioBufferPreset) -> Unit,
    title: String = "Audio buffer preset",
    description: String = "Choose output buffer sizing profile for stability vs latency."
) {
    SettingsEnumSelectorCard(
        title = title,
        description = description,
        selectedValue = selectedPreset,
        options = listOf(
            EnumChoice(AudioBufferPreset.VerySmall, AudioBufferPreset.VerySmall.label),
            EnumChoice(AudioBufferPreset.Small, AudioBufferPreset.Small.label),
            EnumChoice(AudioBufferPreset.Medium, AudioBufferPreset.Medium.label),
            EnumChoice(AudioBufferPreset.Large, AudioBufferPreset.Large.label)
        ),
        onSelected = onSelectedPresetChanged
    )
}

@Composable
internal fun AudioResamplerSelectorCard(
    selectedPreference: AudioResamplerPreference,
    onSelectedPreferenceChanged: (AudioResamplerPreference) -> Unit,
    description: String = "Choose the output resampler. SoX is experimental and falls back to built-in for discontinuous timeline cores."
) {
    SettingsEnumSelectorCard(
        title = "Output resampler",
        description = description,
        selectedValue = selectedPreference,
        options = listOf(
            EnumChoice(AudioResamplerPreference.BuiltIn, AudioResamplerPreference.BuiltIn.label),
            EnumChoice(AudioResamplerPreference.Sox, AudioResamplerPreference.Sox.label)
        ),
        onSelected = onSelectedPreferenceChanged
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SampleRateSelectorCard(
    title: String,
    description: String,
    selectedHz: Int,
    statusText: String? = null,
    enabled: Boolean = true,
    onSelected: (Int) -> Unit
) {
    fun formatSampleRateLabel(hz: Int): String {
        return if (hz == 0) "Auto" else String.format(Locale.US, "%,d Hz", hz)
    }

    val options = listOf(
        SampleRateChoice(0, formatSampleRateLabel(0)),
        SampleRateChoice(8000, formatSampleRateLabel(8000)),
        SampleRateChoice(11025, formatSampleRateLabel(11025)),
        SampleRateChoice(12000, formatSampleRateLabel(12000)),
        SampleRateChoice(16000, formatSampleRateLabel(16000)),
        SampleRateChoice(22050, formatSampleRateLabel(22050)),
        SampleRateChoice(24000, formatSampleRateLabel(24000)),
        SampleRateChoice(32000, formatSampleRateLabel(32000)),
        SampleRateChoice(44100, formatSampleRateLabel(44100)),
        SampleRateChoice(48000, formatSampleRateLabel(48000)),
        SampleRateChoice(88200, formatSampleRateLabel(88200)),
        SampleRateChoice(96000, formatSampleRateLabel(96000))
    )
    val selectedLabel = if (enabled) {
        options.firstOrNull { it.hz == selectedHz }?.label ?: "Auto"
    } else {
        if (selectedHz > 0) "${formatSampleRateLabel(selectedHz)} (Fixed)" else "Auto (Fixed)"
    }
    var dialogOpen by remember { mutableStateOf(false) }
    val contentAlpha = if (enabled) 1f else 0.38f

    SettingsRowContainer(
        onClick = { dialogOpen = true },
        enabled = enabled
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (!statusText.isNullOrBlank()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                )
                Spacer(modifier = Modifier.height(3.dp))
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
        )
    }

    if (dialogOpen) {
        SettingsSingleChoiceDialog(
            title = title,
            selectedValue = selectedHz,
            options = options.map { ChoiceDialogOption(value = it.hz, label = it.label) },
            onSelected = onSelected,
            onDismiss = { dialogOpen = false }
        )
    }
}
