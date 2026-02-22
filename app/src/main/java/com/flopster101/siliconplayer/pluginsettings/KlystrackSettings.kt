package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.KlystrackConfig

internal class KlystrackSettings(
    private val playerQuality: Int,
    private val onPlayerQualityChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                CoreChoiceSelectorCard(
                    title = "Replay quality",
                    description = "Klystron oversampling quality. Higher values improve high-frequency fidelity and use more CPU.",
                    selectedValue = playerQuality,
                    options = KlystrackConfig.playerQualityChoices,
                    onSelected = onPlayerQualityChanged
                )
            }
        }
    }
}
