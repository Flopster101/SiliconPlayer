package com.flopster101.siliconplayer.session

import com.flopster101.siliconplayer.RecentPathEntry
import com.flopster101.siliconplayer.samePath
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.buildUpdatedRecentFolders
import com.flopster101.siliconplayer.buildUpdatedRecentPlayedTracks
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun addRecentFolderEntry(
    current: List<RecentPathEntry>,
    path: String,
    locationId: String?,
    limit: Int,
    update: (List<RecentPathEntry>) -> Unit,
    write: (List<RecentPathEntry>, Int) -> Unit
) {
    val next = buildUpdatedRecentFolders(
        current = current,
        newPath = path,
        locationId = locationId,
        limit = limit
    )
    update(next)
    write(next, limit)
}

internal fun addRecentPlayedTrackEntry(
    current: List<RecentPathEntry>,
    path: String,
    locationId: String?,
    title: String?,
    artist: String?,
    limit: Int,
    update: (List<RecentPathEntry>) -> Unit,
    write: (List<RecentPathEntry>, Int) -> Unit
) {
    val next = buildUpdatedRecentPlayedTracks(
        current = current,
        newPath = path,
        locationId = locationId,
        title = title,
        artist = artist,
        limit = limit
    )
    update(next)
    write(next, limit)
}

internal fun scheduleRecentTrackMetadataRefresh(
    scope: CoroutineScope,
    sourceId: String,
    locationId: String?,
    selectedFileProvider: () -> File?,
    currentPlaybackSourceIdProvider: () -> String?,
    onAddRecentPlayedTrack: (path: String, locationId: String?, title: String?, artist: String?) -> Unit
) {
    scope.launch {
        repeat(8) { attempt ->
            delay(if (attempt == 0) 120L else 220L)
            val current = selectedFileProvider() ?: return@launch
            val activeSource = currentPlaybackSourceIdProvider() ?: current.absolutePath
            if (!samePath(activeSource, sourceId)) return@launch
            val refreshedTitle = NativeBridge.getTrackTitle()
            val refreshedArtist = NativeBridge.getTrackArtist()
            onAddRecentPlayedTrack(
                sourceId,
                locationId,
                refreshedTitle,
                refreshedArtist
            )
            if (refreshedTitle.isNotBlank() || refreshedArtist.isNotBlank()) {
                return@launch
            }
        }
    }
}
