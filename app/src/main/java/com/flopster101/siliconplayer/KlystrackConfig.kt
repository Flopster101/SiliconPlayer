package com.flopster101.siliconplayer

internal object KlystrackOptionKeys {
    const val PLAYER_QUALITY = "klystrack.player_quality"
}

internal object KlystrackConfig {
    val playerQualityChoices = listOf(
        IntChoice(0, "Off (1x)"),
        IntChoice(1, "Low (2x)"),
        IntChoice(2, "Medium (4x)"),
        IntChoice(3, "High (8x)"),
        IntChoice(4, "Very high (16x)")
    )
}
