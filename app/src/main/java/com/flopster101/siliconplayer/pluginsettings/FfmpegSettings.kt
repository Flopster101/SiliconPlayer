package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable

/**
 * Settings for the FFmpeg plugin.
 * FFmpeg has no core-specific options, only generic output options.
 */
class FfmpegSettings(
    private val sampleRateHz: Int,
    private val capabilities: Int,
    private val onSampleRateChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        // FFmpeg has no core-specific options

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
