package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.*

/**
 * Settings for the LibOpenMPT plugin.
 * OpenMPT has extensive core-specific options plus generic output options.
 */
class OpenMptSettings(
    private val sampleRateHz: Int,
    private val capabilities: Int,
    private val stereoSeparationPercent: Int,
    private val stereoSeparationAmigaPercent: Int,
    private val interpolationFilterLength: Int,
    private val amigaResamplerMode: Int,
    private val amigaResamplerApplyAllModules: Boolean,
    private val volumeRampingStrength: Int,
    private val ft2XmVolumeRamping: Boolean,
    private val masterGainMilliBel: Int,
    private val surroundEnabled: Boolean,
    private val onSampleRateChanged: (Int) -> Unit,
    private val onStereoSeparationPercentChanged: (Int) -> Unit,
    private val onStereoSeparationAmigaPercentChanged: (Int) -> Unit,
    private val onInterpolationFilterLengthChanged: (Int) -> Unit,
    private val onAmigaResamplerModeChanged: (Int) -> Unit,
    private val onAmigaResamplerApplyAllModulesChanged: (Boolean) -> Unit,
    private val onVolumeRampingStrengthChanged: (Int) -> Unit,
    private val onFt2XmVolumeRampingChanged: (Boolean) -> Unit,
    private val onMasterGainMilliBelChanged: (Int) -> Unit,
    private val onSurroundEnabledChanged: (Boolean) -> Unit,
    private val includeSampleRateControl: Boolean = true
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        // Core-specific options
        builder.coreOptions {
            custom {
                OpenMptDialogSliderCard(
                    title = "Stereo separation",
                    description = "Sets mixer stereo separation.",
                    value = stereoSeparationPercent,
                    valueRange = 0..200,
                    step = 5,
                    valueLabel = { "$it%" },
                    onValueChanged = onStereoSeparationPercentChanged
                )
            }
            spacer()
            custom {
                OpenMptDialogSliderCard(
                    title = "Amiga stereo separation",
                    description = "Stereo separation used specifically for Amiga modules.",
                    value = stereoSeparationAmigaPercent,
                    valueRange = 0..200,
                    step = 5,
                    valueLabel = { "$it%" },
                    onValueChanged = onStereoSeparationAmigaPercentChanged
                )
            }
            spacer()
            custom {
                OpenMptChoiceSelectorCard(
                    title = "Interpolation filter",
                    description = "Selects interpolation quality for module playback.",
                    selectedValue = interpolationFilterLength,
                    options = listOf(
                        IntChoice(0, "Auto"),
                        IntChoice(1, "None"),
                        IntChoice(2, "Linear"),
                        IntChoice(4, "Cubic"),
                        IntChoice(8, "Sinc (8-tap)")
                    ),
                    onSelected = onInterpolationFilterLengthChanged
                )
            }
            spacer()
            custom {
                OpenMptChoiceSelectorCard(
                    title = "Amiga resampler",
                    description = "Choose Amiga resampler mode. None uses interpolation filter.",
                    selectedValue = amigaResamplerMode,
                    options = listOf(
                        IntChoice(0, "None"),
                        IntChoice(1, "Unfiltered"),
                        IntChoice(2, "Amiga 500"),
                        IntChoice(3, "Amiga 1200")
                    ),
                    onSelected = onAmigaResamplerModeChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Apply Amiga resampler to all modules",
                    description = "When disabled, Amiga resampler is used only on Amiga module formats.",
                    checked = amigaResamplerApplyAllModules,
                    onCheckedChange = onAmigaResamplerApplyAllModulesChanged
                )
            }
            spacer()
            custom {
                OpenMptVolumeRampingCard(
                    title = "Volume ramping strength",
                    description = "Controls smoothing strength for volume changes.",
                    value = volumeRampingStrength,
                    onValueChanged = onVolumeRampingStrengthChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "FT2 5ms XM ramping",
                    description = "Apply classic FT2-style 5ms ramping for XM modules only.",
                    checked = ft2XmVolumeRamping,
                    onCheckedChange = onFt2XmVolumeRampingChanged
                )
            }
            spacer()
            custom {
                OpenMptDialogSliderCard(
                    title = "Master gain",
                    description = "Applies decoder gain before output.",
                    value = masterGainMilliBel,
                    valueRange = -1200..1200,
                    step = 100,
                    valueLabel = { formatMilliBelAsDbLabel(it) },
                    onValueChanged = onMasterGainMilliBelChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Enable surround sound",
                    description = "Enable surround rendering mode when supported by the playback path.",
                    checked = surroundEnabled,
                    onCheckedChange = onSurroundEnabledChanged
                )
            }
        }

        if (includeSampleRateControl) {
            // Generic output options
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
