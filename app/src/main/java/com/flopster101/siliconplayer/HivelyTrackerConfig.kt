package com.flopster101.siliconplayer

internal object HivelyTrackerOptionKeys {
    const val PANNING_MODE = "hivelytracker.panning_mode"
    const val MIX_GAIN_PERCENT = "hivelytracker.mix_gain_percent"
}

internal object HivelyTrackerConfig {
    val panningModeChoices = listOf(
        IntChoice(-1, "Tune default"),
        IntChoice(0, "Mono"),
        IntChoice(1, "Narrow"),
        IntChoice(2, "Balanced"),
        IntChoice(3, "Wide"),
        IntChoice(4, "Full stereo")
    )

    val mixGainPercentChoices = listOf(
        IntChoice(-1, "Tune default"),
        IntChoice(50, "50%"),
        IntChoice(75, "75%"),
        IntChoice(100, "100%"),
        IntChoice(125, "125%"),
        IntChoice(150, "150%"),
        IntChoice(175, "175%"),
        IntChoice(200, "200%"),
        IntChoice(225, "225%"),
        IntChoice(250, "250%"),
        IntChoice(300, "300%")
    )
}
