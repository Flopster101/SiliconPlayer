package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatModeTest {

    @Test
    fun `loop point repeat falls back to track when loop point is unsupported`() {
        val resolved = resolveRepeatModeForFlags(
            preferredMode = RepeatMode.LoopPoint,
            flags = REPEAT_CAP_TRACK,
            includeTrackRepeat = true
        )

        assertEquals(RepeatMode.Track, resolved)
    }

    @Test
    fun `loop point repeat falls back to playlist when neither loop point nor track repeat are available`() {
        val resolved = resolveRepeatModeForFlags(
            preferredMode = RepeatMode.LoopPoint,
            flags = 0,
            includeTrackRepeat = false
        )

        assertEquals(RepeatMode.Playlist, resolved)
    }

    @Test
    fun `loop point repeat is preserved when supported`() {
        val resolved = resolveRepeatModeForFlags(
            preferredMode = RepeatMode.LoopPoint,
            flags = REPEAT_CAP_ALL,
            includeTrackRepeat = true
        )

        assertEquals(RepeatMode.LoopPoint, resolved)
    }
}
