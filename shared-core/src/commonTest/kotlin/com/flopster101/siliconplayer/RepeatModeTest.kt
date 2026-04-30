package com.flopster101.siliconplayer

import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatModeTest {

    @Test
    fun loopPointRepeatFallsBackToTrackWhenLoopPointIsUnsupported() {
        val resolved = resolveRepeatModeForFlags(
            preferredMode = RepeatMode.LoopPoint,
            flags = REPEAT_CAP_TRACK,
            includeTrackRepeat = true
        )

        assertEquals(RepeatMode.Track, resolved)
    }

    @Test
    fun loopPointRepeatFallsBackToPlaylistWhenNeitherLoopPointNorTrackRepeatAreAvailable() {
        val resolved = resolveRepeatModeForFlags(
            preferredMode = RepeatMode.LoopPoint,
            flags = 0,
            includeTrackRepeat = false
        )

        assertEquals(RepeatMode.Playlist, resolved)
    }

    @Test
    fun loopPointRepeatIsPreservedWhenSupported() {
        val resolved = resolveRepeatModeForFlags(
            preferredMode = RepeatMode.LoopPoint,
            flags = REPEAT_CAP_ALL,
            includeTrackRepeat = true
        )

        assertEquals(RepeatMode.LoopPoint, resolved)
    }
}
