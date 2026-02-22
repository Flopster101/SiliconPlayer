package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.HivelyTrackerConfig

internal class HivelyTrackerSettings(
    private val panningMode: Int,
    private val mixGainPercent: Int,
    private val onPanningModeChanged: (Int) -> Unit,
    private val onMixGainPercentChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                CoreChoiceSelectorCard(
                    title = "Stereo panning",
                    description = "Channel stereo spread profile used by HivelyTracker playback.",
                    selectedValue = panningMode,
                    options = HivelyTrackerConfig.panningModeChoices,
                    onSelected = onPanningModeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Replay mix gain",
                    description = "Output gain inside HivelyTracker before the app-level volume chain.",
                    selectedValue = mixGainPercent,
                    options = HivelyTrackerConfig.mixGainPercentChoices,
                    onSelected = onMixGainPercentChanged
                )
            }
        }
    }
}
