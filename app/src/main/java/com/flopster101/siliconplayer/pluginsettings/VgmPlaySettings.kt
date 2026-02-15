package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.IntChoice
import com.flopster101.siliconplayer.OpenMptChoiceSelectorCard
import com.flopster101.siliconplayer.OpenMptDialogSliderCard
import com.flopster101.siliconplayer.PlayerSettingToggleCard
import com.flopster101.siliconplayer.SampleRateSelectorCard
import com.flopster101.siliconplayer.VgmChipCoreSpec
import com.flopster101.siliconplayer.VgmPlayConfig
import com.flopster101.siliconplayer.supportsCustomSampleRate

internal class VgmPlaySettings(
    private val sampleRateHz: Int,
    private val capabilities: Int,
    private val loopCount: Int,
    private val allowNonLoopingLoop: Boolean,
    private val vsyncRate: Int,
    private val resampleMode: Int,
    private val chipSampleMode: Int,
    private val chipSampleRate: Int,
    private val onSampleRateChanged: (Int) -> Unit,
    private val onLoopCountChanged: (Int) -> Unit,
    private val onAllowNonLoopingLoopChanged: (Boolean) -> Unit,
    private val onVsyncRateChanged: (Int) -> Unit,
    private val onResampleModeChanged: (Int) -> Unit,
    private val onChipSampleModeChanged: (Int) -> Unit,
    private val onChipSampleRateChanged: (Int) -> Unit,
    private val onOpenChipSettings: () -> Unit,
    private val includeSampleRateControl: Boolean = true
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                OpenMptDialogSliderCard(
                    title = "Loop count",
                    description = "How many loops to play for looped songs in non-loop-point modes.",
                    value = loopCount,
                    valueRange = 1..99,
                    step = 1,
                    valueLabel = { "$it" },
                    onValueChanged = onLoopCountChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Allow non-looping loop",
                    description = "Allow repeat-track style looping even when no loop point is present.",
                    checked = allowNonLoopingLoop,
                    onCheckedChange = onAllowNonLoopingLoopChanged
                )
            }
            spacer()
            custom {
                OpenMptChoiceSelectorCard(
                    title = "VSync mode",
                    description = "Force playback timing or keep the file's original timing.",
                    selectedValue = vsyncRate,
                    options = VgmPlayConfig.vsyncRateChoices,
                    onSelected = onVsyncRateChanged
                )
            }
            spacer()
            custom {
                OpenMptChoiceSelectorCard(
                    title = "Resampling mode",
                    description = "Select libvgm chip resampling quality mode.",
                    selectedValue = resampleMode,
                    options = VgmPlayConfig.resampleModeChoices,
                    onSelected = onResampleModeChanged
                )
            }
            spacer()
            custom {
                OpenMptChoiceSelectorCard(
                    title = "Chip sample mode",
                    description = "Choose how chip sample rates are resolved.",
                    selectedValue = chipSampleMode,
                    options = VgmPlayConfig.chipSampleModeChoices,
                    onSelected = onChipSampleModeChanged
                )
            }
            spacer()
            custom {
                OpenMptChoiceSelectorCard(
                    title = "Chip sample rate",
                    description = "Target chip sample rate for custom/highest sample modes.",
                    selectedValue = chipSampleRate,
                    options = VgmPlayConfig.chipSampleRateChoices,
                    onSelected = onChipSampleRateChanged
                )
            }
            spacer()
            custom {
                PluginSettingsNavigationCard(
                    title = "Chip settings",
                    description = "Choose emulator core per sound chip.",
                    onClick = onOpenChipSettings
                )
            }
        }

        if (includeSampleRateControl) {
            builder.genericOutputOptions {
                custom {
                    SampleRateSelectorCard(
                        title = "Render sample rate",
                        description = "Preferred internal render sample rate for this plugin. Audio is resampled to the active output stream rate.",
                        selectedHz = sampleRateHz,
                        enabled = supportsCustomSampleRate(capabilities),
                        onSelected = onSampleRateChanged
                    )
                }
            }
        }
    }
}

@Composable
internal fun VgmPlayChipSettingsScreen(
    chipCoreSpecs: List<VgmChipCoreSpec>,
    chipCoreSelections: Map<String, Int>,
    onChipCoreChanged: (String, Int) -> Unit
) {
    chipCoreSpecs.forEachIndexed { index, spec ->
        OpenMptChoiceSelectorCard(
            title = "${spec.title} emulator core",
            description = "Select the emulation core used for ${spec.title}.",
            selectedValue = chipCoreSelections[spec.key] ?: spec.defaultValue,
            options = spec.choices.map { IntChoice(it.value, it.label) },
            onSelected = { selected -> onChipCoreChanged(spec.key, selected) }
        )
        if (index < chipCoreSpecs.size - 1) {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PluginSettingsNavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
