package com.flopster101.siliconplayer

internal object DecoderNames {
    const val FFMPEG = "FFmpeg"
    const val LIB_OPEN_MPT = "LibOpenMPT"
    const val VGM_PLAY = "VGMPlay"
    const val GAME_MUSIC_EMU = "Game Music Emu"
    const val LIB_SID_PLAY_FP = "LibSIDPlayFP"
    const val LAZY_USF2 = "LazyUSF2"
    const val VIO2_SF = "Vio2SF"
    const val SC68 = "SC68"
    const val AD_PLUG = "AdPlug"
    const val UADE = "UADE"
    const val HIVELY_TRACKER = "HivelyTracker"
    const val KLYSTRACK = "Klystrack"
    const val FURNACE = "Furnace"

    val trackedFileDecoders: Set<String> = setOf(
        LIB_OPEN_MPT,
        VGM_PLAY,
        LIB_SID_PLAY_FP,
        SC68,
        AD_PLUG,
        UADE,
        HIVELY_TRACKER,
        KLYSTRACK,
        FURNACE
    )

    val gameFileDecoders: Set<String> = setOf(
        GAME_MUSIC_EMU,
        LAZY_USF2,
        VIO2_SF
    )
}

internal fun canonicalDecoderNameForAlias(coreName: String?): String? {
    return when (coreName?.trim()?.lowercase()) {
        "ffmpeg" -> DecoderNames.FFMPEG
        "libopenmpt", "openmpt" -> DecoderNames.LIB_OPEN_MPT
        "vgmplay" -> DecoderNames.VGM_PLAY
        "game music emu", "libgme", "gme" -> DecoderNames.GAME_MUSIC_EMU
        "libsidplayfp", "sidplayfp", "sid" -> DecoderNames.LIB_SID_PLAY_FP
        "lazyusf2", "lazyusf", "usf" -> DecoderNames.LAZY_USF2
        "vio2sf", "2sf", "mini2sf" -> DecoderNames.VIO2_SF
        "sc68", "sndh" -> DecoderNames.SC68
        "adplug", "opl" -> DecoderNames.AD_PLUG
        "hivelytracker", "hively", "hvl", "ahx" -> DecoderNames.HIVELY_TRACKER
        "klystrack", "kly", "kt" -> DecoderNames.KLYSTRACK
        "furnace", "fur", "dmf" -> DecoderNames.FURNACE
        "uade", "amiga" -> DecoderNames.UADE
        else -> null
    }
}

internal fun String?.matchesDecoderName(canonicalName: String): Boolean {
    return this?.trim()?.equals(canonicalName, ignoreCase = true) == true
}
