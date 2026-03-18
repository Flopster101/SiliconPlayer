package com.flopster101.siliconplayer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote

internal data class ArtworkSwipePreviewState(
    val currentTrackKey: String? = null,
    val currentArtworkResolved: Boolean = false,
    val currentArtwork: ImageBitmap? = null,
    val currentPlaceholderIcon: ImageVector = Icons.Default.MusicNote,
    val previousTrackKey: String? = null,
    val nextTrackKey: String? = null,
    val canSwipePrevious: Boolean = false,
    val canSwipeNext: Boolean = false,
    val previousArtwork: ImageBitmap? = null,
    val nextArtwork: ImageBitmap? = null,
    val previousPlaceholderIcon: ImageVector = Icons.Default.MusicNote,
    val nextPlaceholderIcon: ImageVector = Icons.Default.MusicNote
)
