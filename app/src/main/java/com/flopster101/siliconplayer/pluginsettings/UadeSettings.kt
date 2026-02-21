package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.PlayerSettingToggleCard
import com.flopster101.siliconplayer.UadeConfig

internal class UadeSettings(
    private val filterEnabled: Boolean,
    private val ntscMode: Boolean,
    private val panningMode: Int,
    private val onFilterEnabledChanged: (Boolean) -> Unit,
    private val onNtscModeChanged: (Boolean) -> Unit,
    private val onPanningModeChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                PlayerSettingToggleCard(
                    title = "Paula filter",
                    description = "Enable Paula low-pass filter emulation.",
                    checked = filterEnabled,
                    onCheckedChange = onFilterEnabledChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "NTSC mode",
                    description = "Use NTSC timing instead of PAL timing.",
                    checked = ntscMode,
                    onCheckedChange = onNtscModeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Panning",
                    description = "Stereo crossfeed profile for Paula voices.",
                    selectedValue = panningMode,
                    options = UadeConfig.panningModeChoices,
                    onSelected = onPanningModeChanged
                )
            }
        }
    }
}
