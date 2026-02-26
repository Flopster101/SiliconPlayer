package com.flopster101.siliconplayer

import java.io.File

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

fun repeatCapabilitiesForFile(file: File?): RepeatCapabilities {
    val extension = file?.extension?.lowercase().orEmpty()
    val trackerFamily = extension in trackerModuleExtensions
    return RepeatCapabilities(
        supportsLoopPointRepeat = trackerFamily
    )
}

fun availableRepeatModesForFile(file: File?): List<RepeatMode> {
    return availableRepeatModesForFlags(repeatModeCapabilitiesFlagsForFileFallback(file))
}

fun repeatModeCapabilitiesFlagsForFileFallback(file: File?): Int {
    val capabilities = repeatCapabilitiesForFile(file)
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

fun resolveRepeatModeForFile(preferredMode: RepeatMode, file: File?): RepeatMode {
    return resolveRepeatModeForFlags(preferredMode, repeatModeCapabilitiesFlagsForFileFallback(file))
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
            } else {
                RepeatMode.Playlist
            }
        }
    }
}
