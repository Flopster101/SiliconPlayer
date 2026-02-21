package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.IntChoice
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.CoreDialogSliderCard
import com.flopster101.siliconplayer.PlayerSettingToggleCard

internal class GmeSettings(
    private val tempoPercent: Int,
    private val stereoSeparationPercent: Int,
    private val echoEnabled: Boolean,
    private val accuracyEnabled: Boolean,
    private val eqTrebleDecibel: Int,
    private val eqBassHz: Int,
    private val spcUseBuiltInFade: Boolean,
    private val spcInterpolation: Int,
    private val spcUseNativeSampleRate: Boolean,
    private val onTempoPercentChanged: (Int) -> Unit,
    private val onStereoSeparationPercentChanged: (Int) -> Unit,
    private val onEchoEnabledChanged: (Boolean) -> Unit,
    private val onAccuracyEnabledChanged: (Boolean) -> Unit,
    private val onEqTrebleDecibelChanged: (Int) -> Unit,
    private val onEqBassHzChanged: (Int) -> Unit,
    private val onSpcUseBuiltInFadeChanged: (Boolean) -> Unit,
    private val onSpcInterpolationChanged: (Int) -> Unit,
    private val onSpcUseNativeSampleRateChanged: (Boolean) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                CoreDialogSliderCard(
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
                CoreDialogSliderCard(
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
                    description = "Enable libgme SPC DSP echo.",
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
            spacer()
            custom {
                CoreDialogSliderCard(
                    title = "EQ treble",
                    description = "libgme equalizer treble gain.",
                    value = eqTrebleDecibel,
                    valueRange = -50..5,
                    step = 1,
                    valueLabel = { "${it} dB" },
                    showNudgeButtons = true,
                    nudgeStep = 1,
                    onValueChanged = onEqTrebleDecibelChanged
                )
            }
            spacer()
            custom {
                CoreDialogSliderCard(
                    title = "EQ bass",
                    description = "libgme equalizer bass cutoff frequency.",
                    value = eqBassHz,
                    valueRange = 1..1000,
                    step = 1,
                    valueLabel = { "${it} Hz" },
                    showNudgeButtons = true,
                    nudgeStep = 1,
                    onValueChanged = onEqBassHzChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "SPC built-in fade",
                    description = "Use libgme SPC tagged fade behavior instead of app fade override.",
                    checked = spcUseBuiltInFade,
                    onCheckedChange = onSpcUseBuiltInFadeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "SPC interpolation",
                    description = "SPC interpolation mode.",
                    selectedValue = spcInterpolation,
                    options = listOf(
                        IntChoice(-2, "None"),
                        IntChoice(-1, "Linear"),
                        IntChoice(0, "Gaussian"),
                        IntChoice(1, "Cubic"),
                        IntChoice(2, "Sinc")
                    ),
                    onSelected = onSpcInterpolationChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Use native SPC sample rate",
                    description = "Force 32,000 Hz render rate for SPC files.",
                    checked = spcUseNativeSampleRate,
                    onCheckedChange = onSpcUseNativeSampleRateChanged
                )
            }
        }
    }
}
