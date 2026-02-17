package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.IntChoice
import com.flopster101.siliconplayer.OpenMptChoiceSelectorCard
import com.flopster101.siliconplayer.PlayerSettingToggleCard

internal class SidPlayFpSettings(
    private val backend: Int,
    private val clockMode: Int,
    private val sidModelMode: Int,
    private val filter6581Enabled: Boolean,
    private val filter8580Enabled: Boolean,
    private val reSidFpFastSampling: Boolean,
    private val reSidFpCombinedWaveformsStrength: Int,
    private val onBackendChanged: (Int) -> Unit,
    private val onClockModeChanged: (Int) -> Unit,
    private val onSidModelModeChanged: (Int) -> Unit,
    private val onFilter6581EnabledChanged: (Boolean) -> Unit,
    private val onFilter8580EnabledChanged: (Boolean) -> Unit,
    private val onReSidFpFastSamplingChanged: (Boolean) -> Unit,
    private val onReSidFpCombinedWaveformsStrengthChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                OpenMptChoiceSelectorCard(
                    title = "Engine",
                    description = "Select SID emulation backend.",
                    selectedValue = backend,
                    options = listOf(
                        IntChoice(0, "ReSIDfp"),
                        IntChoice(1, "SIDLite")
                    ),
                    onSelected = onBackendChanged
                )
            }
            spacer()
            custom {
                OpenMptChoiceSelectorCard(
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
                OpenMptChoiceSelectorCard(
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
                    title = "Fast sampling (ReSIDfp)",
                    description = "Use lower-cost sampling path for ReSIDfp.",
                    checked = reSidFpFastSampling,
                    onCheckedChange = onReSidFpFastSamplingChanged
                )
            }
            spacer()
            custom {
                OpenMptChoiceSelectorCard(
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
