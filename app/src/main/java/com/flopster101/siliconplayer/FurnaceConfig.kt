package com.flopster101.siliconplayer

internal object FurnaceOptionKeys {
    const val YM2612_CORE = "furnace.ym2612_core"
    const val SN_CORE = "furnace.sn_core"
    const val NES_CORE = "furnace.nes_core"
    const val C64_CORE = "furnace.c64_core"
    const val GB_QUALITY = "furnace.gb_quality"
    const val DSID_QUALITY = "furnace.dsid_quality"
    const val AY_CORE = "furnace.ay_core"
}

internal object FurnaceConfig {
    val ym2612CoreChoices = listOf(
        IntChoice(0, "Nuked-OPN2"),
        IntChoice(1, "ymfm"),
        IntChoice(2, "YMF276-LLE")
    )

    val snCoreChoices = listOf(
        IntChoice(0, "MAME"),
        IntChoice(1, "Nuked-PSG Mod")
    )

    val nesCoreChoices = listOf(
        IntChoice(0, "puNES"),
        IntChoice(1, "NSFplay")
    )

    val c64CoreChoices = listOf(
        IntChoice(0, "reSID"),
        IntChoice(1, "reSIDfp"),
        IntChoice(2, "dSID")
    )

    val ayCoreChoices = listOf(
        IntChoice(0, "MAME"),
        IntChoice(1, "AtomicSSG")
    )

    val qualityChoices = listOf(
        IntChoice(0, "Lower"),
        IntChoice(1, "Low"),
        IntChoice(2, "Medium"),
        IntChoice(3, "High"),
        IntChoice(4, "Ultra"),
        IntChoice(5, "Ultimate")
    )
}
