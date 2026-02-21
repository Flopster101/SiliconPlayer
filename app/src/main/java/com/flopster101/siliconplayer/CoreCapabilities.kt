package com.flopster101.siliconplayer

const val TIMELINE_MODE_UNKNOWN = 0
const val TIMELINE_MODE_CONTINUOUS_LINEAR = 1
const val TIMELINE_MODE_DISCONTINUOUS = 2

internal data class CoreCapabilityItem(
    val id: String,
    val description: String
)

internal data class CoreCapabilitySection(
    val title: String,
    val items: List<CoreCapabilityItem>
)

internal fun buildCoreCapabilitySections(
    playbackCapabilities: Int,
    repeatCapabilities: Int,
    timelineMode: Int
): List<CoreCapabilitySection> {
    val seekItems = buildList {
        if (canSeekPlayback(playbackCapabilities)) {
            add(
                CoreCapabilityItem(
                    id = "PLAYBACK_CAP_SEEK",
                    description = "This core supports timeline seeking."
                )
            )
        }
        if (supportsDirectSeek(playbackCapabilities)) {
            add(
                CoreCapabilityItem(
                    id = "PLAYBACK_CAP_DIRECT_SEEK",
                    description = "Seek requests can jump directly, so scrubbing is faster and more accurate."
                )
            )
        }
    }

    val repeatItems = buildList {
        if ((repeatCapabilities and REPEAT_CAP_TRACK) != 0) {
            add(
                CoreCapabilityItem(
                    id = "REPEAT_CAP_TRACK",
                    description = "Track repeat mode is supported."
                )
            )
        }
        if ((repeatCapabilities and REPEAT_CAP_LOOP_POINT) != 0) {
            add(
                CoreCapabilityItem(
                    id = "REPEAT_CAP_LOOP_POINT",
                    description = "Loop-point repeat mode is supported."
                )
            )
        }
        if (supportsLiveRepeatMode(playbackCapabilities)) {
            add(
                CoreCapabilityItem(
                    id = "PLAYBACK_CAP_LIVE_REPEAT_MODE",
                    description = "Repeat mode changes can be applied while playback is running."
                )
            )
        }
    }

    val timelineItems = buildList {
        val mode = when (timelineMode) {
            TIMELINE_MODE_CONTINUOUS_LINEAR -> CoreCapabilityItem(
                id = "TIMELINE_MODE_CONTINUOUS_LINEAR",
                description = "Reported playback position advances continuously."
            )
            TIMELINE_MODE_DISCONTINUOUS -> CoreCapabilityItem(
                id = "TIMELINE_MODE_DISCONTINUOUS",
                description = "Reported playback position may jump (for loops, patterns, or engine-driven resets)."
            )
            else -> CoreCapabilityItem(
                id = "TIMELINE_MODE_UNKNOWN",
                description = "Timeline behavior is not explicitly reported by this core."
            )
        }
        add(mode)
        if (hasReliableDuration(playbackCapabilities)) {
            add(
                CoreCapabilityItem(
                    id = "PLAYBACK_CAP_RELIABLE_DURATION",
                    description = "Duration is provided with reliable accuracy."
                )
            )
        }
    }

    val outputItems = buildList {
        if (supportsCustomSampleRate(playbackCapabilities)) {
            add(
                CoreCapabilityItem(
                    id = "PLAYBACK_CAP_CUSTOM_SAMPLE_RATE",
                    description = "This core accepts a configurable render sample rate."
                )
            )
        }
        if (supportsLiveSampleRateChange(playbackCapabilities)) {
            add(
                CoreCapabilityItem(
                    id = "PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE",
                    description = "Render sample rate changes can be applied live."
                )
            )
        }
        if (hasFixedSampleRate(playbackCapabilities)) {
            add(
                CoreCapabilityItem(
                    id = "PLAYBACK_CAP_FIXED_SAMPLE_RATE",
                    description = "This core renders at a fixed internal sample rate."
                )
            )
        }
    }

    return buildList {
        if (seekItems.isNotEmpty()) add(CoreCapabilitySection(title = "Seek", items = seekItems))
        if (repeatItems.isNotEmpty()) add(CoreCapabilitySection(title = "Repeat", items = repeatItems))
        if (timelineItems.isNotEmpty()) add(CoreCapabilitySection(title = "Timeline", items = timelineItems))
        if (outputItems.isNotEmpty()) add(CoreCapabilitySection(title = "Output", items = outputItems))
    }
}
