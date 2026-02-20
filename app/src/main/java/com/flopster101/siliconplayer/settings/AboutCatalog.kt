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
            name = "FFmpeg",
            description = "General-purpose decoding backend used for mainstream audio containers and codecs.",
            author = "FFmpeg Project contributors",
            license = "LGPL-2.1+ (can become GPL when GPL components are enabled)",
            links = listOf(
                AboutEntityLink("Project", "https://ffmpeg.org/"),
                AboutEntityLink("Source", "https://git.ffmpeg.org/ffmpeg.git")
            ),
            integrationNotes = listOf(
                "Used as fallback/general decoder for broad format coverage.",
                "Integrated through libavformat/libavcodec/libswresample with native channel controls."
            )
        ),
        AboutEntity(
            id = "core.libopenmpt",
            kind = AboutEntityKind.Core,
            name = "LibOpenMPT",
            description = "Tracker module playback library for MOD/XM/S3M/IT and related formats.",
            author = "OpenMPT Project developers and contributors",
            license = "BSD-3-Clause",
            links = listOf(
                AboutEntityLink("Project", "https://lib.openmpt.org/"),
                AboutEntityLink("Source", "https://github.com/OpenMPT/openmpt.git")
            ),
            integrationNotes = listOf(
                "Primary module/tracker playback core in Silicon Player.",
                "Per-song channel naming is exposed for channel mute/solo controls."
            )
        ),
        AboutEntity(
            id = "core.vgmplay",
            kind = AboutEntityKind.Core,
            name = "VGMPlay",
            description = "Chip-focused VGM playback stack based on libvgm.",
            author = "Valley Bell and libvgm contributors",
            license = "Mixed per-chip licenses (BSD/LGPL/GPL and others; see upstream sources)",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/ValleyBell/libvgm.git")
            ),
            integrationNotes = listOf(
                "Integrated with per-chip/per-channel muting via libvgm device muting masks.",
                "Chip core selection and playback options are exposed in core settings."
            )
        ),
        AboutEntity(
            id = "core.gme",
            kind = AboutEntityKind.Core,
            name = "Game Music Emu",
            description = "Multi-system game music emulator for formats like NSF, SPC, GBS, HES, and others.",
            author = "Shay Green, libgme maintainers, and contributors",
            license = "LGPL-2.1+ (some optional emulation paths are GPL-2.0+)",
            links = listOf(
                AboutEntityLink("Source", "https://github.com/libgme/game-music-emu.git")
            ),
            integrationNotes = listOf(
                "Integrated with tempo, EQ, stereo controls, and per-voice channel muting."
            )
        ),
        AboutEntity(
            id = "core.libsidplayfp",
            kind = AboutEntityKind.Core,
            name = "LibSIDPlayFP",
            description = "Cycle-based Commodore 64 SID playback library with high quality SID emulation backends.",
            author = "Simon White, Antti Lankila, Leandro Nini, and contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://libsidplayfp.github.io/libsidplayfp/"),
                AboutEntityLink("Source", "https://github.com/libsidplayfp/libsidplayfp")
            ),
            integrationNotes = listOf(
                "Integrated with reSID/reSIDfp backend controls and per-voice mute toggles."
            )
        ),
        AboutEntity(
            id = "core.lazyusf2",
            kind = AboutEntityKind.Core,
            name = "LazyUSF2",
            description = "Nintendo 64 USF playback core derived from Mupen64plus-era audio emulation code.",
            author = "lazyusf2 and Mupen64plus contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://bitbucket.org/losnoco/lazyusf2/")
            ),
            integrationNotes = listOf(
                "Integrated with HLE audio and dynamic voice-channel mute controls.",
                "Silicon Player carries local patches for voice-mask APIs and channel availability reporting."
            )
        ),
        AboutEntity(
            id = "core.vio2sf",
            kind = AboutEntityKind.Core,
            name = "Vio2SF",
            description = "Nintendo DS 2SF playback core built around a DeSmuME-based audio state renderer.",
            author = "Christopher Snowhill and DeSmuME contributors",
            license = "GPL-2.0-or-later",
            links = listOf(
                AboutEntityLink("Source", "https://bitbucket.org/kode54/vio2sf")
            ),
            integrationNotes = listOf(
                "Uses PSFLib for 2SF/mini2SF container loading and library resolution.",
                "Integrated with DS voice channel controls and interpolation quality selection.",
                "Behavior and compatibility were cross-checked against existing 2SF player implementations."
            )
        ),
        AboutEntity(
            id = "core.sc68",
            kind = AboutEntityKind.Core,
            name = "SC68",
            description = "Atari ST/Amiga music playback core for SC68 and SNDH tracks.",
            author = "Benjamin Gerard and sc68 contributors",
            license = "GPL-3.0-or-later",
            links = listOf(
                AboutEntityLink("Project", "https://sourceforge.net/p/sc68/"),
                AboutEntityLink("Source", "https://sourceforge.net/p/sc68/code/HEAD/tree/")
            ),
            integrationNotes = listOf(
                "Integrated as a native decoder using libsc68 + file68 + unice68.",
                "Current baseline targets playback, seek, subtunes, and core metadata fields."
            )
        )
    )

    private val libraryEntries: List<AboutEntity> = listOf(
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
        )
    )

    private val pluginNameToCoreId: Map<String, String> = mapOf(
        "FFmpeg" to "core.ffmpeg",
        "LibOpenMPT" to "core.libopenmpt",
        "VGMPlay" to "core.vgmplay",
        "Game Music Emu" to "core.gme",
        "LibSIDPlayFP" to "core.libsidplayfp",
        "LazyUSF2" to "core.lazyusf2",
        "Vio2SF" to "core.vio2sf",
        "SC68" to "core.sc68"
    )

    private val entityById: Map<String, AboutEntity> = (coreEntries + libraryEntries).associateBy { it.id }

    val cores: List<AboutEntity>
        get() = coreEntries

    val libraries: List<AboutEntity>
        get() = libraryEntries

    fun resolveCoreForPlugin(pluginName: String): AboutEntity? {
        val id = pluginNameToCoreId[pluginName] ?: return null
        return entityById[id]
    }
}
