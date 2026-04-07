package com.flopster101.siliconplayer

internal enum class AboutEntityKind {
    Core,
    Library
}

internal data class AboutEntityLink(
    val label: String,
    val url: String
)

internal data class AboutEntity(
    val id: String,
    val kind: AboutEntityKind,
    val name: String,
    val description: String,
    val author: String,
    val license: String,
    val links: List<AboutEntityLink> = emptyList(),
    val integrationNotes: List<String> = emptyList()
)

internal object AboutCatalog {
    private val coreEntries: List<AboutEntity> = listOf(
        AboutEntity(
            id = "core.ffmpeg",
            kind = AboutEntityKind.Core,
            name = DecoderNames.FFMPEG,
            description = "General-purpose decoding backend used for mainstream audio containers and codecs.",
            author = "FFmpeg Project contributors",
            license = "LGPL-2.1+ (can become GPL when GPL components are enabled)",
            links = listOf(
                AboutEntityLink("Project", "https://ffmpeg.org/"),
                AboutEntityLink("Source", "https://git.ffmpeg.org/ffmpeg.git")
            )
        ),
        AboutEntity(
            id = "core.libopenmpt",
            kind = AboutEntityKind.Core,
            name = DecoderNames.LIB_OPEN_MPT,
            description = "Tracker module playback library for MOD/XM/S3M/IT and related formats.",
            author = "OpenMPT Project developers and contributors",
            license = "BSD-3-Clause",
            links = listOf(
                AboutEntityLink("Project", "https://lib.openmpt.org/"),
                AboutEntityLink("Source", "https://github.com/OpenMPT/openmpt.git")
            )
        ),
        AboutEntity(
            id = "core.vgmplay",
            kind = AboutEntityKind.Core,
            name = DecoderNames.VGM_PLAY,
            description = "Chip-focused VGM playback stack based on libvgm.",
            author = "Valley Bell and libvgm contributors",
            license = "Mixed per-chip licenses (BSD/LGPL/GPL and others; see upstream sources)",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/ValleyBell/libvgm.git")
            )
        ),
        AboutEntity(
            id = "core.gme",
            kind = AboutEntityKind.Core,
            name = DecoderNames.GAME_MUSIC_EMU,
            description = "Multi-system game music emulator for formats like NSF, SPC, GBS, HES, and others.",
            author = "Shay Green, libgme maintainers, and contributors",
            license = "LGPL-2.1+ (some optional emulation paths are GPL-2.0+)",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/libgme/game-music-emu.git")
            )
        ),
        AboutEntity(
            id = "core.libsidplayfp",
            kind = AboutEntityKind.Core,
            name = DecoderNames.LIB_SID_PLAY_FP,
            description = "Cycle-based Commodore 64 SID playback library with high quality SID emulation backends.",
            author = "Simon White, Antti Lankila, Leandro Nini, and contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://libsidplayfp.github.io/libsidplayfp/"),
                AboutEntityLink("Source", "https://github.com/libsidplayfp/libsidplayfp")
            )
        ),
        AboutEntity(
            id = "core.lazyusf2",
            kind = AboutEntityKind.Core,
            name = DecoderNames.LAZY_USF2,
            description = "Nintendo 64 USF playback core derived from Mupen64plus-era audio emulation code.",
            author = "lazyusf2 and Mupen64plus contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://bitbucket.org/losnoco/lazyusf2/")
            )
        ),
        AboutEntity(
            id = "core.vio2sf",
            kind = AboutEntityKind.Core,
            name = DecoderNames.VIO2_SF,
            description = "Nintendo DS 2SF playback core built around a DeSmuME-based audio state renderer.",
            author = "Christopher Snowhill and DeSmuME contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://bitbucket.org/kode54/vio2sf")
            )
        ),
        AboutEntity(
            id = "core.sc68",
            kind = AboutEntityKind.Core,
            name = DecoderNames.SC68,
            description = "Atari ST/Amiga music playback core for SC68 and SNDH tracks.",
            author = "Benjamin Gerard and sc68 contributors",
            license = "GPL-3.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://sourceforge.net/p/sc68/"),
                AboutEntityLink("Source", "https://sourceforge.net/p/sc68/code/HEAD/tree/")
            )
        ),
        AboutEntity(
            id = "core.adplug",
            kind = AboutEntityKind.Core,
            name = DecoderNames.AD_PLUG,
            description = "OPL2/OPL3 replayer core for many DOS-era AdLib and OPL music formats.",
            author = "Simon Peter and AdPlug contributors",
            license = "LGPL-2.1-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://adplug.github.io/"),
                AboutEntityLink("Source", "https://github.com/adplug/adplug")
            )
        ),
        AboutEntity(
            id = "core.uade",
            kind = AboutEntityKind.Core,
            name = DecoderNames.UADE,
            description = "Amiga music playback core using Unix Amiga Delitracker Emulator format handlers.",
            author = "UADE contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://zakalwe.fi/uade/"),
                AboutEntityLink("Source", "https://github.com/viznut/uade")
            )
        ),
        AboutEntity(
            id = "core.hivelytracker",
            kind = AboutEntityKind.Core,
            name = DecoderNames.HIVELY_TRACKER,
            description = "AHX/HVL tracker replayer core for Amiga-style chiptune modules.",
            author = "Xeron, Xigh, and HivelyTracker contributors",
            license = "BSD-3-Clause",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/pete-gordon/hivelytracker")
            )
        ),
        AboutEntity(
            id = "core.klystrack",
            kind = AboutEntityKind.Core,
            name = DecoderNames.KLYSTRACK,
            description = "Klystrack-plus module replay core using the klystron audio engine.",
            author = "Georgy Saraykin (LTVA1) and Klystrack-plus contributors",
            license = "zlib License",
            links = listOf(
                AboutEntityLink("Project", "https://github.com/LTVA1/klystrack"),
                AboutEntityLink("Source", "https://github.com/LTVA1/klystrack")
            )
        ),
        AboutEntity(
            id = "core.furnace",
            kind = AboutEntityKind.Core,
            name = DecoderNames.FURNACE,
            description = "Furnace Tracker playback core for .fur/.dmf modules using the upstream headless engine.",
            author = "tildearrow and Furnace contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://tildearrow.org/furnace/"),
                AboutEntityLink("Source", "https://github.com/tildearrow/furnace")
            )
        )
    )

    private val libraryEntries: List<AboutEntity> = listOf(
        AboutEntity(
            id = "lib.openmpt_dsp_effects",
            kind = AboutEntityKind.Library,
            name = "OpenMPT DSP effects",
            description = "Audio DSP effect implementations adapted from OpenMPT sounddsp components (Bass Expansion, Reverb, Surround, BitCrush).",
            author = "OpenMPT Project developers and contributors",
            license = "BSD-3-Clause",
            links = listOf(
                AboutEntityLink("Project", "https://openmpt.org/"),
                AboutEntityLink("Source", "https://github.com/OpenMPT/openmpt.git")
            ),
            integrationNotes = listOf(
                "Integrated under app/src/main/cpp/effects/openmpt_dsp/ with local attribution and license copy.",
                "Silicon Player uses a native port of OpenMPT DSP routines in the app audio processing pipeline."
            )
        ),
        AboutEntity(
            id = "lib.psflib",
            kind = AboutEntityKind.Library,
            name = "PSFLib",
            description = "PSF/2SF container parsing and library dependency loading helper.",
            author = "Christopher Snowhill",
            license = "MIT",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/kode54/psflib")
            ),
            integrationNotes = listOf(
                "Used by Vio2SF to resolve mini2SF references and parse metadata tags."
            )
        ),
        AboutEntity(
            id = "lib.libsoxr",
            kind = AboutEntityKind.Library,
            name = "libsoxr",
            description = "High-quality sample-rate conversion library.",
            author = "Rob Sykes and contributors",
            license = "LGPL-2.1-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://git.code.sf.net/p/soxr/code")
            ),
            integrationNotes = listOf(
                "Available as an external resampling backend in the native pipeline."
            )
        ),
        AboutEntity(
            id = "lib.libresidfp",
            kind = AboutEntityKind.Library,
            name = "libresidfp",
            description = "Floating-point SID chip emulation backend used by libsidplayfp.",
            author = "libsidplayfp/libresidfp contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/libsidplayfp/libresidfp")
            ),
            integrationNotes = listOf(
                "Provides higher fidelity SID emulation paths for the SID core settings."
            )
        ),
        AboutEntity(
            id = "lib.resid",
            kind = AboutEntityKind.Library,
            name = "reSID",
            description = "Classic SID emulation backend used alongside reSIDfp in SID playback paths.",
            author = "Dag Lem and contributors",
            license = "GPL-3.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/libsidplayfp/resid")
            ),
            integrationNotes = listOf(
                "Bundled as part of SID backend support for libsidplayfp integration."
            )
        ),
        AboutEntity(
            id = "lib.openssl",
            kind = AboutEntityKind.Library,
            name = "OpenSSL",
            description = "General-purpose cryptography and TLS toolkit.",
            author = "The OpenSSL Project Authors",
            license = "Apache-2.0",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/openssl/openssl")
            )
        ),
        AboutEntity(
            id = "lib.libbinio",
            kind = AboutEntityKind.Library,
            name = "libbinio",
            description = "Binary I/O support library used by AdPlug loaders.",
            author = "Simon Peter and libbinio contributors",
            license = "LGPL-2.1-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://adplug.github.io/libbinio/"),
                AboutEntityLink("Source", "https://github.com/adplug/libbinio")
            ),
            integrationNotes = listOf(
                "Linked as a static dependency for the AdPlug core."
            )
        )
    )

    private val pluginNameToCoreId: Map<String, String> = mapOf(
        DecoderNames.FFMPEG to "core.ffmpeg",
        DecoderNames.LIB_OPEN_MPT to "core.libopenmpt",
        DecoderNames.VGM_PLAY to "core.vgmplay",
        DecoderNames.GAME_MUSIC_EMU to "core.gme",
        DecoderNames.LIB_SID_PLAY_FP to "core.libsidplayfp",
        DecoderNames.LAZY_USF2 to "core.lazyusf2",
        DecoderNames.VIO2_SF to "core.vio2sf",
        DecoderNames.SC68 to "core.sc68",
        DecoderNames.AD_PLUG to "core.adplug",
        DecoderNames.UADE to "core.uade",
        DecoderNames.HIVELY_TRACKER to "core.hivelytracker",
        DecoderNames.KLYSTRACK to "core.klystrack",
        DecoderNames.FURNACE to "core.furnace"
    )

    private val entityById: Map<String, AboutEntity> = (coreEntries + libraryEntries).associateBy { it.id }

    val cores: List<AboutEntity>
        get() = coreEntries

    val libraries: List<AboutEntity>
        get() = libraryEntries

    private val generatedVersionResolver: (String) -> String? = { entityId ->
        GeneratedAboutVersions.versionForId(entityId)
    }

    fun resolveVersion(entityId: String): String? {
        return generatedVersionResolver(entityId)
    }

    fun resolveCoreForPlugin(pluginName: String): AboutEntity? {
        val canonicalName = canonicalDecoderNameForAlias(pluginName) ?: pluginName
        val id = pluginNameToCoreId[canonicalName] ?: return null
        return entityById[id]
    }
}
