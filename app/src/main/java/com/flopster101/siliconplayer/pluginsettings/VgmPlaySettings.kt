package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.IntChoice
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.CoreDialogSliderCard
import com.flopster101.siliconplayer.PlayerSettingToggleCard
import com.flopster101.siliconplayer.SampleRateSelectorCard
import com.flopster101.siliconplayer.SettingsRowSpacer
import com.flopster101.siliconplayer.SettingsValuePickerCard
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
                CoreDialogSliderCard(
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
                CoreChoiceSelectorCard(
                    title = "VSync mode",
                    description = "Force playback timing or keep the file's original timing.",
                    selectedValue = vsyncRate,
                    options = VgmPlayConfig.vsyncRateChoices,
                    onSelected = onVsyncRateChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Resampling mode",
                    description = "Select libvgm chip resampling quality mode.",
                    selectedValue = resampleMode,
                    options = VgmPlayConfig.resampleModeChoices,
                    onSelected = onResampleModeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Chip sample mode",
                    description = "Choose how chip sample rates are resolved.",
                    selectedValue = chipSampleMode,
                    options = VgmPlayConfig.chipSampleModeChoices,
                    onSelected = onChipSampleModeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
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
                        description = "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate.",
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
        CoreChoiceSelectorCard(
            title = "${spec.title} emulator core",
            description = "Select the emulation core used for ${spec.title}.",
            selectedValue = chipCoreSelections[spec.key] ?: spec.defaultValue,
            options = spec.choices.map { IntChoice(it.value, it.label) },
            onSelected = { selected -> onChipCoreChanged(spec.key, selected) }
        )
        if (index < chipCoreSpecs.size - 1) {
            SettingsRowSpacer()
        }
    }
}

@Composable
private fun PluginSettingsNavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    SettingsValuePickerCard(
        title = title,
        description = description,
        value = "Open",
        onClick = onClick
    )
}
