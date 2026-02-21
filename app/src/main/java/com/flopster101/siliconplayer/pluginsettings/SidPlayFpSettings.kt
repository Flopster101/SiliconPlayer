package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.IntChoice
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.CoreDialogSliderCard
import com.flopster101.siliconplayer.PlayerSettingToggleCard

internal class SidPlayFpSettings(
    private val backend: Int,
    private val clockMode: Int,
    private val sidModelMode: Int,
    private val filter6581Enabled: Boolean,
    private val filter8580Enabled: Boolean,
    private val digiBoost8580: Boolean,
    private val filterCurve6581Percent: Int,
    private val filterRange6581Percent: Int,
    private val filterCurve8580Percent: Int,
    private val reSidFpFastSampling: Boolean,
    private val reSidFpCombinedWaveformsStrength: Int,
    private val onBackendChanged: (Int) -> Unit,
    private val onClockModeChanged: (Int) -> Unit,
    private val onSidModelModeChanged: (Int) -> Unit,
    private val onFilter6581EnabledChanged: (Boolean) -> Unit,
    private val onFilter8580EnabledChanged: (Boolean) -> Unit,
    private val onDigiBoost8580Changed: (Boolean) -> Unit,
    private val onFilterCurve6581PercentChanged: (Int) -> Unit,
    private val onFilterRange6581PercentChanged: (Int) -> Unit,
    private val onFilterCurve8580PercentChanged: (Int) -> Unit,
    private val onReSidFpFastSamplingChanged: (Boolean) -> Unit,
    private val onReSidFpCombinedWaveformsStrengthChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                CoreChoiceSelectorCard(
                    title = "Engine",
                    description = "Select SID emulation backend.",
                    selectedValue = backend,
                    options = listOf(
                        IntChoice(2, "ReSID"),
                        IntChoice(0, "ReSIDfp"),
                        IntChoice(1, "SIDLite")
                    ),
                    onSelected = onBackendChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Timing standard",
                    description = "Select C64 timing mode used for playback.",
                    selectedValue = clockMode,
                    options = listOf(
                        IntChoice(0, "Auto"),
                        IntChoice(1, "PAL"),
                        IntChoice(2, "NTSC")
                    ),
                    onSelected = onClockModeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "SID model",
                    description = "Choose Auto or force a specific SID model.",
                    selectedValue = sidModelMode,
                    options = listOf(
                        IntChoice(0, "Auto"),
                        IntChoice(1, "MOS6581"),
                        IntChoice(2, "MOS8580")
                    ),
                    onSelected = onSidModelModeChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Filter for MOS6581",
                    description = "Enable SID filter emulation for 6581 model chips.",
                    checked = filter6581Enabled,
                    onCheckedChange = onFilter6581EnabledChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Filter for MOS8580",
                    description = "Enable SID filter emulation for 8580 model chips.",
                    checked = filter8580Enabled,
                    onCheckedChange = onFilter8580EnabledChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Digi boost (8580)",
                    description = "Boosts 8580 volume-register digis.",
                    checked = digiBoost8580,
                    onCheckedChange = onDigiBoost8580Changed
                )
            }
            spacer()
            custom {
                CoreDialogSliderCard(
                    title = "Filter curve 6581",
                    description = "ReSIDfp 6581 filter curve.",
                    value = filterCurve6581Percent,
                    valueRange = 0..100,
                    step = 1,
                    valueLabel = { "${it}%" },
                    showNudgeButtons = true,
                    nudgeStep = 1,
                    onValueChanged = onFilterCurve6581PercentChanged
                )
            }
            spacer()
            custom {
                CoreDialogSliderCard(
                    title = "Filter range 6581",
                    description = "ReSIDfp 6581 filter range adjustment.",
                    value = filterRange6581Percent,
                    valueRange = 0..100,
                    step = 1,
                    valueLabel = { "${it}%" },
                    showNudgeButtons = true,
                    nudgeStep = 1,
                    onValueChanged = onFilterRange6581PercentChanged
                )
            }
            spacer()
            custom {
                CoreDialogSliderCard(
                    title = "Filter curve 8580",
                    description = "ReSIDfp 8580 filter curve.",
                    value = filterCurve8580Percent,
                    valueRange = 0..100,
                    step = 1,
                    valueLabel = { "${it}%" },
                    showNudgeButtons = true,
                    nudgeStep = 1,
                    onValueChanged = onFilterCurve8580PercentChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Fast sampling",
                    description = "Use lower-cost SID sampling path.",
                    checked = reSidFpFastSampling,
                    onCheckedChange = onReSidFpFastSamplingChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Combined waveforms (ReSIDfp)",
                    description = "Strength of combined waveform emulation.",
                    selectedValue = reSidFpCombinedWaveformsStrength,
                    options = listOf(
                        IntChoice(0, "Average"),
                        IntChoice(1, "Weak"),
                        IntChoice(2, "Strong")
                    ),
                    onSelected = onReSidFpCombinedWaveformsStrengthChanged
                )
            }
        }
    }
}
