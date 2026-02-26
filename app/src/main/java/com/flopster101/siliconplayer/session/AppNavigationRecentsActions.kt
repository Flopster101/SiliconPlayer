package com.flopster101.siliconplayer.session

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.flopster101.siliconplayer.RecentPathEntry
import com.flopster101.siliconplayer.samePath
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.buildUpdatedRecentFolders
import com.flopster101.siliconplayer.buildUpdatedRecentPlayedTracks
import com.flopster101.siliconplayer.ensureRecentArtworkThumbnailCached
import com.flopster101.siliconplayer.mergeRecentPlayedTrackMetadata
import com.flopster101.siliconplayer.mergeRecentPlayedTrackArtworkCacheKey
import com.flopster101.siliconplayer.normalizeSourceIdentity
import java.util.Locale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private var recentPlayedMetadataBackfillJob: Job? = null
private val recentArtworkCacheJobs = mutableMapOf<String, Job>()

internal fun addRecentFolderEntry(
    current: List<RecentPathEntry>,
    path: String,
    locationId: String?,
    sourceNodeId: Long?,
    limit: Int,
    update: (List<RecentPathEntry>) -> Unit,
    write: (List<RecentPathEntry>, Int) -> Unit
) {
    val next = buildUpdatedRecentFolders(
        current = current,
        newPath = path,
        locationId = locationId,
        sourceNodeId = sourceNodeId,
        limit = limit
    )
    update(next)
    write(next, limit)
}

internal fun addRecentPlayedTrackEntry(
    current: List<RecentPathEntry>,
    path: String,
    locationId: String?,
    sourceNodeId: Long? = null,
    title: String?,
    artist: String?,
    decoderName: String?,
    limit: Int,
    update: (List<RecentPathEntry>) -> Unit,
    write: (List<RecentPathEntry>, Int) -> Unit
) {
    val next = buildUpdatedRecentPlayedTracks(
        current = current,
        newPath = path,
        locationId = locationId,
        sourceNodeId = sourceNodeId,
        title = title,
        artist = artist,
        decoderName = decoderName,
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
            if (!NativeBridge.isEnginePlaying()) return@repeat
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

internal fun scheduleRecentPlayedMetadataBackfill(
    scope: CoroutineScope,
    currentProvider: () -> List<RecentPathEntry>,
    limitProvider: () -> Int,
    onRecentPlayedChanged: (List<RecentPathEntry>) -> Unit,
    writeRecentPlayed: (List<RecentPathEntry>, Int) -> Unit
) {
    recentPlayedMetadataBackfillJob?.cancel()
    recentPlayedMetadataBackfillJob = scope.launch(Dispatchers.IO) {
        delay(250L)
        val limit = limitProvider().coerceAtLeast(1)
        val snapshot = currentProvider().take(limit)
        if (snapshot.isEmpty()) return@launch

        var working = currentProvider()
        var changed = false
        snapshot.forEach { entry ->
            if (!entry.title.isNullOrBlank() || !entry.artist.isNullOrBlank()) return@forEach
            val metadata = readLocalTrackMetadata(entry.path) ?: return@forEach
            val merged = mergeRecentPlayedTrackMetadata(
                current = working,
                path = entry.path,
                title = metadata.first,
                artist = metadata.second
            )
            if (merged != working) {
                working = merged
                changed = true
            }
        }

        if (!changed) return@launch
        withContext(Dispatchers.Main.immediate) {
            onRecentPlayedChanged(working)
        }
        writeRecentPlayed(working, limit)
    }
}

internal fun scheduleRecentPlayedArtworkCacheBackfill(
    context: Context,
    scope: CoroutineScope,
    sourceId: String,
    requestUrlHint: String?,
    currentProvider: () -> List<RecentPathEntry>,
    limitProvider: () -> Int,
    onRecentPlayedChanged: (List<RecentPathEntry>) -> Unit,
    writeRecentPlayed: (List<RecentPathEntry>, Int) -> Unit
) {
    val normalizedSourceId = normalizeSourceIdentity(sourceId) ?: sourceId
    val existingJob = recentArtworkCacheJobs.remove(normalizedSourceId)
    existingJob?.cancel()
    recentArtworkCacheJobs[normalizedSourceId] = scope.launch(Dispatchers.IO) {
        try {
            val thumbnailCacheKey = ensureRecentArtworkThumbnailCached(
                context = context,
                sourceId = normalizedSourceId,
                requestUrlHint = requestUrlHint
            ) ?: return@launch
            val merged = mergeRecentPlayedTrackArtworkCacheKey(
                current = currentProvider(),
                path = normalizedSourceId,
                artworkThumbnailCacheKey = thumbnailCacheKey
            )
            if (merged == currentProvider()) return@launch
            val limit = limitProvider().coerceAtLeast(1)
            withContext(Dispatchers.Main.immediate) {
                onRecentPlayedChanged(merged)
            }
            writeRecentPlayed(merged, limit)
        } finally {
            recentArtworkCacheJobs.remove(normalizedSourceId)
        }
    }
}

private fun readLocalTrackMetadata(sourceId: String): Pair<String, String>? {
    val normalized = normalizeSourceIdentity(sourceId) ?: sourceId
    val uri = Uri.parse(normalized)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val localPath = when (scheme) {
        null -> normalized
        "file" -> uri.path ?: return null
        "http", "https", "smb" -> return null
        else -> normalized
    }
    val file = File(localPath)
    if (!file.exists() || !file.isFile) return null

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val title = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.trim()
            .orEmpty()
        val artist = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.trim()
            .orEmpty()
        if (title.isBlank() && artist.isBlank()) null else Pair(title, artist)
    } catch (_: Throwable) {
        null
    } finally {
        retriever.release()
    }
}
