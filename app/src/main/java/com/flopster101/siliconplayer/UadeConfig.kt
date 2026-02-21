package com.flopster101.siliconplayer

internal object UadeOptionKeys {
    const val FILTER_ENABLED = "uade.filter_enabled"
    const val NTSC_MODE = "uade.ntsc_mode"
    const val PANNING_MODE = "uade.panning_mode"
    const val UNKNOWN_DURATION_SECONDS = "uade.unknown_duration_seconds"
}

internal object UadeConfig {
    val panningModeChoices = listOf(
        IntChoice(0, "Mono"),
        IntChoice(1, "Some"),
        IntChoice(2, "50/50"),
        IntChoice(3, "Lots"),
        IntChoice(4, "Full Stereo")
    )
}
