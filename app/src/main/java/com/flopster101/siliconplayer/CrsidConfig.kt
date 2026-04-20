package com.flopster101.siliconplayer

internal object CrsidOptionKeys {
    const val CLOCK_MODE = "crsid.clock_mode"
    const val SID_MODEL_MODE = "crsid.sid_model_mode"
    const val QUALITY_MODE = "crsid.quality_mode"
    const val UNKNOWN_DURATION_SECONDS = "crsid.unknown_duration_seconds"
}

internal object CrsidConfig {
    val clockModeChoices = listOf(
        IntChoice(0, "Auto"),
        IntChoice(1, "PAL"),
        IntChoice(2, "NTSC")
    )

    val sidModelChoices = listOf(
        IntChoice(0, "Auto"),
        IntChoice(1, "MOS6581"),
        IntChoice(2, "MOS8580")
    )

    val qualityChoices = listOf(
        IntChoice(1, "High"),
        IntChoice(2, "Sinc"),
        IntChoice(0, "Light")
    )
}
