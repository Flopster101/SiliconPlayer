package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.IntChoice
import com.flopster101.siliconplayer.CoreChoiceSelectorCard

internal class Vio2sfSettings(
    private val interpolationQuality: Int,
    private val onInterpolationQualityChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                CoreChoiceSelectorCard(
                    title = "Interpolation quality",
                    description = "Internal DS SPU interpolation mode.",
                    selectedValue = interpolationQuality,
                    options = listOf(
                        IntChoice(0, "None"),
                        IntChoice(1, "Linear"),
                        IntChoice(2, "Cubic"),
                        IntChoice(3, "Sinc"),
                        IntChoice(4, "SNES style")
                    ),
                    onSelected = onInterpolationQualityChanged
                )
            }
        }
    }
}

