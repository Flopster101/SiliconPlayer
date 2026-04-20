package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.CrsidConfig

internal class CrsidSettings(
    private val clockMode: Int,
    private val sidModelMode: Int,
    private val qualityMode: Int,
    private val filter6581Preset: Int,
    private val onClockModeChanged: (Int) -> Unit,
    private val onSidModelModeChanged: (Int) -> Unit,
    private val onQualityModeChanged: (Int) -> Unit,
    private val onFilter6581PresetChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                CoreChoiceSelectorCard(
                    title = "Timing standard",
                    description = "Choose Auto or force a specific system clock for playback timing.",
                    selectedValue = clockMode,
                    options = CrsidConfig.clockModeChoices,
                    onSelected = onClockModeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "SID model",
                    description = "Choose Auto or force a specific SID chip model for playback.",
                    selectedValue = sidModelMode,
                    options = CrsidConfig.sidModelChoices,
                    onSelected = onSidModelModeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Emulation quality",
                    description = "High is the recommended default. Sinc uses the heavier resampler path, while Light uses the faster low-cost path.",
                    selectedValue = qualityMode,
                    options = CrsidConfig.qualityChoices,
                    onSelected = onQualityModeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "6581 filter preset",
                    description = "Choose the 6581 filter tuning. Stock keeps upstream cRSID behavior, while R4AR, R3, and R2 use revision-style presets.",
                    selectedValue = filter6581Preset,
                    options = CrsidConfig.filter6581PresetChoices,
                    onSelected = onFilter6581PresetChanged
                )
            }
        }
    }
}
