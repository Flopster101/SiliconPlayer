package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable

/**
 * Settings for the FFmpeg plugin.
 * FFmpeg has a small set of core-specific playback options plus generic output options.
 */
class FfmpegSettings(
    private val sampleRateHz: Int,
    private val capabilities: Int,
    private val gaplessRepeatTrack: Boolean,
    private val onSampleRateChanged: (Int) -> Unit,
    private val onGaplessRepeatTrackChanged: (Boolean) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                com.flopster101.siliconplayer.PlayerSettingToggleCard(
                    title = "Gapless repeat track",
                    description = "When Repeat Track is active, restart instantly at the beginning without a transition gap.",
                    checked = gaplessRepeatTrack,
                    onCheckedChange = onGaplessRepeatTrackChanged
                )
            }
        }

        // Generic output options
        builder.genericOutputOptions {
            custom {
                com.flopster101.siliconplayer.SampleRateSelectorCard(
                    title = "Render sample rate",
                    description = "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate.",
                    selectedHz = sampleRateHz,
                    enabled = com.flopster101.siliconplayer.supportsCustomSampleRate(capabilities),
                    onSelected = onSampleRateChanged
                )
            }
        }
    }
}
