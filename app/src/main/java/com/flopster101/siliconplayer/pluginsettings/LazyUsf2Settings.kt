package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.PlayerSettingToggleCard

internal class LazyUsf2Settings(
    private val useHleAudio: Boolean,
    private val onUseHleAudioChanged: (Boolean) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                PlayerSettingToggleCard(
                    title = "Use HLE audio",
                    description = "Use high-level audio emulation instead of RSP path.",
                    checked = useHleAudio,
                    onCheckedChange = onUseHleAudioChanged
                )
            }
        }
    }
}

