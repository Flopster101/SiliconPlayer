package com.flopster101.siliconplayer

val trackerModuleExtensions = setOf(
    "mod", "xm", "it", "s3m", "mptm", "mtm", "stm", "ult", "ptm", "okt",
    "amf", "ams", "dbm", "dmf", "dsm", "far", "gdm", "imf", "j2b", "med",
    "mdl", "mt2", "nst", "psm", "umx", "669", "mo3", "wow", "ice"
)

const val REPEAT_CAP_TRACK = 1 shl 0
const val REPEAT_CAP_LOOP_POINT = 1 shl 1
const val REPEAT_CAP_ALL = REPEAT_CAP_TRACK or REPEAT_CAP_LOOP_POINT

enum class RepeatMode(
    val storageValue: String,
    val label: String
) {
    None(
        storageValue = "none",
        label = "No repeat"
    ),
    Track(
        storageValue = "track",
        label = "Repeat track"
    ),
    Subtune(
        storageValue = "subtune",
        label = "Repeat subtune"
    ),
    Playlist(
        storageValue = "playlist",
        label = "Repeat playlist"
    ),
    LoopPoint(
        storageValue = "loop_point",
        label = "Repeat at loop point"
    );

    companion object {
        fun fromStorage(value: String?): RepeatMode {
            return entries.firstOrNull { it.storageValue == value } ?: None
        }
    }
}

data class RepeatCapabilities(
    val supportsLoopPointRepeat: Boolean
)

fun repeatCapabilitiesForExtension(extension: String?): RepeatCapabilities {
    val normalizedExtension = extension?.lowercase().orEmpty()
    return RepeatCapabilities(
        supportsLoopPointRepeat = normalizedExtension in trackerModuleExtensions
    )
}

fun availableRepeatModesForExtension(extension: String?): List<RepeatMode> {
    return availableRepeatModesForFlags(repeatModeCapabilitiesFlagsForExtensionFallback(extension))
}

fun repeatModeCapabilitiesFlagsForExtensionFallback(extension: String?): Int {
    val capabilities = repeatCapabilitiesForExtension(extension)
    var flags = REPEAT_CAP_TRACK
    if (capabilities.supportsLoopPointRepeat) flags = flags or REPEAT_CAP_LOOP_POINT
    return flags
}

fun availableRepeatModesForFlags(
    flags: Int,
    includeSubtuneRepeat: Boolean = false,
    includeTrackRepeat: Boolean = true
): List<RepeatMode> {
    val supportsLoopPointRepeat = (flags and REPEAT_CAP_LOOP_POINT) != 0
    return buildList {
        add(RepeatMode.None)
        if (includeTrackRepeat) {
            add(RepeatMode.Track)
        }
        if (includeSubtuneRepeat) {
            add(RepeatMode.Subtune)
        }
        add(RepeatMode.Playlist)
        if (supportsLoopPointRepeat) {
            add(RepeatMode.LoopPoint)
        }
    }
}

fun resolveRepeatModeForExtension(preferredMode: RepeatMode, extension: String?): RepeatMode {
    return resolveRepeatModeForFlags(preferredMode, repeatModeCapabilitiesFlagsForExtensionFallback(extension))
}

fun resolveRepeatModeForFlags(
    preferredMode: RepeatMode,
    flags: Int,
    includeSubtuneRepeat: Boolean = false,
    includeTrackRepeat: Boolean = true
): RepeatMode {
    val supportsLoopPointRepeat = (flags and REPEAT_CAP_LOOP_POINT) != 0
    return when (preferredMode) {
        RepeatMode.None -> RepeatMode.None
        RepeatMode.Track -> {
            if (includeTrackRepeat) {
                RepeatMode.Track
            } else {
                RepeatMode.Playlist
            }
        }
        RepeatMode.Subtune -> {
            if (includeSubtuneRepeat) {
                RepeatMode.Subtune
            } else if (includeTrackRepeat) {
                RepeatMode.Track
            } else {
                RepeatMode.Playlist
            }
        }
        RepeatMode.Playlist -> {
            RepeatMode.Playlist
        }
        RepeatMode.LoopPoint -> {
            if (supportsLoopPointRepeat) {
                RepeatMode.LoopPoint
            } else if (includeTrackRepeat) {
                RepeatMode.Track
            } else {
                RepeatMode.Playlist
            }
        }
    }
}
