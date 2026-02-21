package com.flopster101.siliconplayer

internal object AdPlugOptionKeys {
    const val OPL_ENGINE = "adplug.opl_engine"
}

internal object AdPlugConfig {
    val oplEngineChoices = listOf(
        IntChoice(0, "DOSBox"),
        IntChoice(1, "Ken Silverman"),
        IntChoice(2, "MAME"),
        IntChoice(3, "Nuked")
    )
}
