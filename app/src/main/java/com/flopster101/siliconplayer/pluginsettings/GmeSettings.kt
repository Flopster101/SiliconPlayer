package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.OpenMptDialogSliderCard
import com.flopster101.siliconplayer.PlayerSettingToggleCard

internal class GmeSettings(
    private val tempoPercent: Int,
    private val stereoSeparationPercent: Int,
    private val echoEnabled: Boolean,
    private val accuracyEnabled: Boolean,
    private val onTempoPercentChanged: (Int) -> Unit,
    private val onStereoSeparationPercentChanged: (Int) -> Unit,
    private val onEchoEnabledChanged: (Boolean) -> Unit,
    private val onAccuracyEnabledChanged: (Boolean) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                OpenMptDialogSliderCard(
                    title = "Tempo",
                    description = "Playback speed multiplier for GME tracks.",
                    value = tempoPercent,
                    valueRange = 50..200,
                    step = 5,
                    valueLabel = { "${it}%" },
                    onValueChanged = onTempoPercentChanged
                )
            }
            spacer()
            custom {
                OpenMptDialogSliderCard(
                    title = "Stereo separation",
                    description = "Stereo depth used by libgme (0% = mono mix, 100% = maximum depth).",
                    value = stereoSeparationPercent,
                    valueRange = 0..100,
                    step = 5,
                    valueLabel = { "${it}%" },
                    onValueChanged = onStereoSeparationPercentChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "SPC echo",
                    description = "Enable DSP echo for SPC playback.",
                    checked = echoEnabled,
                    onCheckedChange = onEchoEnabledChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "High accuracy emulation",
                    description = "Enable libgme high-accuracy emulation path.",
                    checked = accuracyEnabled,
                    onCheckedChange = onAccuracyEnabledChanged
                )
            }
        }
    }
}
