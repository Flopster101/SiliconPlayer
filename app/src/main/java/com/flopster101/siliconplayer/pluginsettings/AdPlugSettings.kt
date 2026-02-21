package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.AdPlugConfig
import com.flopster101.siliconplayer.OpenMptChoiceSelectorCard

internal class AdPlugSettings(
    private val oplEngine: Int,
    private val onOplEngineChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                OpenMptChoiceSelectorCard(
                    title = "Adlib core",
                    description = "OPL emulator backend used by AdPlug.",
                    selectedValue = oplEngine,
                    options = AdPlugConfig.oplEngineChoices,
                    onSelected = onOplEngineChanged
                )
            }
        }
    }
}
