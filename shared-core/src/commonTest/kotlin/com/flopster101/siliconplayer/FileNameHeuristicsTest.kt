package com.flopster101.siliconplayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileNameHeuristicsTest {

    @Test
    fun extensionCandidatesIncludeLastMiddleAndPrefixTokens() {
        assertEquals(
            listOf("gz", "tar", "archive"),
            extensionCandidatesForName("archive.tar.gz")
        )
        assertEquals(
            listOf("title", "track", "mp3"),
            extensionCandidatesForName("mp3.track.title")
        )
    }

    @Test
    fun primaryExtensionUsesBestCandidate() {
        assertEquals("gz", inferredPrimaryExtensionForName("/music/archive.tar.gz"))
        assertNull(inferredPrimaryExtensionForName("title without extension"))
    }

    @Test
    fun supportedExtensionMatchingIsCaseInsensitive() {
        assertTrue(nameMatchesSupportedExtensions("Song.MP3", setOf("mp3")))
        assertTrue(nameMatchesSupportedExtensions("Song.mp3", setOf("MP3")))
        assertFalse(nameMatchesSupportedExtensions("Song.txt", setOf("mp3")))
    }

    @Test
    fun displayTitleRemovesExtensionAndRemoteCacheHash() {
        val hash = "0123456789abcdef0123456789abcdef01234567"

        assertEquals("Song Title", inferredDisplayTitleForName("Song Title.mp3"))
        assertEquals("Song Title", inferredDisplayTitleForName("$hash" + "_Song Title.mp3"))
    }

    @Test
    fun displayTitleHandlesExtensionPrefixedNames() {
        assertEquals("Song Title", inferredDisplayTitleForName("mp3.Song Title.mp3"))
    }
}
