package com.flopster101.siliconplayer

internal data class VgmChipCoreChoice(
    val value: Int,
    val label: String
)

internal data class VgmChipCoreSpec(
    val key: String,
    val title: String,
    val defaultValue: Int,
    val choices: List<VgmChipCoreChoice>
)

internal object VgmPlayOptionKeys {
    const val LOOP_COUNT = "vgmplay.loop_count"
    const val ALLOW_NON_LOOPING_LOOP = "vgmplay.allow_non_looping_loop"
    const val VSYNC_RATE_HZ = "vgmplay.vsync_rate_hz"
    const val RESAMPLE_MODE = "vgmplay.resample_mode"
    const val CHIP_SAMPLE_MODE = "vgmplay.chip_sample_mode"
    const val CHIP_SAMPLE_RATE_HZ = "vgmplay.chip_sample_rate_hz"
    const val CHIP_CORE_PREFIX = "vgmplay.chip_core."
}

internal object VgmPlayConfig {
    val vsyncRateChoices = listOf(
        IntChoice(0, "Auto"),
        IntChoice(50, "PAL 50Hz"),
        IntChoice(60, "NTSC 60Hz")
    )

    val resampleModeChoices = listOf(
        IntChoice(0, "High Quality"),
        IntChoice(1, "Low Quality"),
        IntChoice(2, "LQ Down, HQ Up")
    )

    val chipSampleModeChoices = listOf(
        IntChoice(0, "Native"),
        IntChoice(1, "Selected rate"),
        IntChoice(2, "Highest")
    )

    val chipSampleRateChoices = listOf(
        IntChoice(48000, "48000"),
        IntChoice(44100, "44100"),
        IntChoice(32000, "32000"),
        IntChoice(24000, "24000"),
        IntChoice(22050, "22050"),
        IntChoice(16000, "16000"),
        IntChoice(11025, "11025"),
        IntChoice(8000, "8000")
    )

    val chipCoreSpecs = listOf(
        VgmChipCoreSpec(
            key = "SN76496",
            title = "SN76496",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "MAME"),
                VgmChipCoreChoice(1, "Maxim")
            )
        ),
        VgmChipCoreSpec(
            key = "YM2151",
            title = "YM2151",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "MAME"),
                VgmChipCoreChoice(1, "Nuked OPM")
            )
        ),
        VgmChipCoreSpec(
            key = "YM2413",
            title = "YM2413",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "EMU2413"),
                VgmChipCoreChoice(1, "MAME"),
                VgmChipCoreChoice(2, "Nuked")
            )
        ),
        VgmChipCoreSpec(
            key = "YM2612",
            title = "YM2612",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "Genesis Plus GX"),
                VgmChipCoreChoice(1, "Nuked OPN2"),
                VgmChipCoreChoice(2, "Gens")
            )
        ),
        VgmChipCoreSpec(
            key = "YM2203",
            title = "YM2203",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "EMU2149"),
                VgmChipCoreChoice(1, "MAME")
            )
        ),
        VgmChipCoreSpec(
            key = "YM2608",
            title = "YM2608",
            defaultValue = 1,
            choices = listOf(
                VgmChipCoreChoice(0, "EMU2149"),
                VgmChipCoreChoice(1, "MAME")
            )
        ),
        VgmChipCoreSpec(
            key = "YM2610",
            title = "YM2610",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "EMU2149"),
                VgmChipCoreChoice(1, "MAME")
            )
        ),
        VgmChipCoreSpec(
            key = "YM3812",
            title = "YM3812",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "DOSBox"),
                VgmChipCoreChoice(1, "MAME"),
                VgmChipCoreChoice(2, "Nuked")
            )
        ),
        VgmChipCoreSpec(
            key = "YMF262",
            title = "YMF262",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "DOSBox"),
                VgmChipCoreChoice(1, "MAME"),
                VgmChipCoreChoice(2, "Nuked")
            )
        ),
        VgmChipCoreSpec(
            key = "AY8910",
            title = "AY8910",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "EMU2149"),
                VgmChipCoreChoice(1, "MAME")
            )
        ),
        VgmChipCoreSpec(
            key = "NES_APU",
            title = "NES APU",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "NSFPlay"),
                VgmChipCoreChoice(1, "MAME")
            )
        ),
        VgmChipCoreSpec(
            key = "qsound",
            title = "QSound",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "CTR"),
                VgmChipCoreChoice(1, "MAME")
            )
        ),
        VgmChipCoreSpec(
            key = "saa1099",
            title = "SAA1099",
            defaultValue = 0,
            choices = listOf(
                VgmChipCoreChoice(0, "Valley Bell"),
                VgmChipCoreChoice(1, "MAME")
            )
        ),
        VgmChipCoreSpec(
            key = "c6280",
            title = "HuC6280",
            defaultValue = 1,
            choices = listOf(
                VgmChipCoreChoice(0, "Ootake"),
                VgmChipCoreChoice(1, "MAME")
            )
        )
    )

    fun defaultChipCoreSelections(): Map<String, Int> {
        return chipCoreSpecs.associate { it.key to it.defaultValue }
    }
}
