package com.flopster101.siliconplayer

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

private const val PLAYLIST_LIBRARY_FAVORITES_KEY = "favorites"
private const val PLAYLIST_LIBRARY_PLAYLISTS_KEY = "playlists"
private const val PLAYLIST_ENTRY_ID_KEY = "id"
private const val PLAYLIST_ENTRY_SOURCE_KEY = "source"
private const val PLAYLIST_ENTRY_TITLE_KEY = "title"
private const val PLAYLIST_ENTRY_ARTIST_KEY = "artist"
private const val PLAYLIST_ENTRY_ALBUM_KEY = "album"
private const val PLAYLIST_ENTRY_ARTWORK_CACHE_KEY = "artworkThumbnailCacheKey"
private const val PLAYLIST_ENTRY_SUBTUNE_KEY = "subtune_index"
private const val PLAYLIST_ENTRY_DURATION_OVERRIDE_KEY = "duration_seconds_override"
private const val PLAYLIST_ENTRY_ADDED_AT_KEY = "added_at_ms"
private const val STORED_PLAYLIST_ID_KEY = "id"
private const val STORED_PLAYLIST_TITLE_KEY = "title"
private const val STORED_PLAYLIST_FORMAT_KEY = "format"
private const val STORED_PLAYLIST_SOURCE_HINT_KEY = "source_id_hint"
private const val STORED_PLAYLIST_UPDATED_AT_KEY = "updated_at_ms"
private const val STORED_PLAYLIST_ENTRIES_KEY = "entries"

internal fun readPlaylistLibraryState(prefs: SharedPreferences): PlaylistLibraryState {
    val raw = prefs.getString(AppPreferenceKeys.PLAYLIST_LIBRARY_JSON, null)
        ?.trim()
        .takeUnless { it.isNullOrBlank() }
        ?: return emptyPlaylistLibraryState()
    return runCatching {
        val root = JSONObject(raw)
        val favorites = root.optJSONArray(PLAYLIST_LIBRARY_FAVORITES_KEY)
            ?.let(::readPlaylistTrackEntries)
            .orEmpty()
        val playlists = root.optJSONArray(PLAYLIST_LIBRARY_PLAYLISTS_KEY)
            ?.let(::readStoredPlaylists)
            .orEmpty()
        PlaylistLibraryState(
            favorites = favorites,
            playlists = playlists
        )
    }.getOrElse {
        emptyPlaylistLibraryState()
    }
}

internal fun writePlaylistLibraryState(
    prefs: SharedPreferences,
    state: PlaylistLibraryState
) {
    val root = JSONObject()
        .put(
            PLAYLIST_LIBRARY_FAVORITES_KEY,
            JSONArray().apply {
                state.favorites.forEach { put(writePlaylistTrackEntry(it)) }
            }
        )
        .put(
            PLAYLIST_LIBRARY_PLAYLISTS_KEY,
            JSONArray().apply {
                state.playlists.forEach { put(writeStoredPlaylist(it)) }
            }
        )
    prefs.edit()
        .putString(AppPreferenceKeys.PLAYLIST_LIBRARY_JSON, root.toString())
        .apply()
}

internal fun upsertStoredPlaylist(
    state: PlaylistLibraryState,
    playlist: StoredPlaylist
): PlaylistLibraryState {
    val existingIndex = state.playlists.indexOfFirst { existing ->
        existing.sourceIdHint != null &&
            playlist.sourceIdHint != null &&
            samePath(existing.sourceIdHint, playlist.sourceIdHint)
    }
    val updatedPlaylist = if (existingIndex >= 0) {
        playlist.copy(
            id = state.playlists[existingIndex].id,
            updatedAtMs = System.currentTimeMillis()
        )
    } else {
        playlist.copy(updatedAtMs = System.currentTimeMillis())
    }
    val withoutExisting = if (existingIndex >= 0) {
        state.playlists.toMutableList().apply { removeAt(existingIndex) }
    } else {
        state.playlists.toMutableList()
    }
    withoutExisting.add(0, updatedPlaylist)
    return state.copy(playlists = withoutExisting)
}

internal fun removeStoredPlaylist(
    state: PlaylistLibraryState,
    playlistId: String
): PlaylistLibraryState {
    return state.copy(
        playlists = state.playlists.filterNot { it.id == playlistId }
    )
}

internal fun upsertFavoriteTrack(
    state: PlaylistLibraryState,
    track: PlaylistTrackEntry
): PlaylistLibraryState {
    val withoutExisting = state.favorites.filterNot { existing ->
        samePath(existing.source, track.source) &&
            (existing.subtuneIndex ?: -1) == (track.subtuneIndex ?: -1)
    }
    return state.copy(favorites = listOf(track) + withoutExisting)
}

internal fun removeFavoriteTrack(
    state: PlaylistLibraryState,
    favoriteId: String
): PlaylistLibraryState {
    return state.copy(
        favorites = state.favorites.filterNot { it.id == favoriteId }
    )
}

internal fun moveFavoriteTrack(
    state: PlaylistLibraryState,
    favoriteId: String,
    offset: Int
): PlaylistLibraryState {
    if (offset == 0 || state.favorites.size < 2) return state
    val currentIndex = state.favorites.indexOfFirst { it.id == favoriteId }
    if (currentIndex < 0) return state
    val targetIndex = (currentIndex + offset).coerceIn(0, state.favorites.lastIndex)
    if (targetIndex == currentIndex) return state
    val reorderedFavorites = state.favorites.toMutableList().apply {
        val entry = removeAt(currentIndex)
        add(targetIndex, entry)
    }
    return state.copy(favorites = reorderedFavorites)
}

internal fun readStoredPlaylistFromJson(raw: String?): StoredPlaylist? {
    val normalized = raw?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    return runCatching {
        readStoredPlaylist(JSONObject(normalized))
    }.getOrNull()
}

internal fun writeStoredPlaylistToJson(playlist: StoredPlaylist): String {
    return writeStoredPlaylist(playlist).toString()
}

private fun readStoredPlaylists(array: JSONArray): List<StoredPlaylist> {
    val playlists = mutableListOf<StoredPlaylist>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        readStoredPlaylist(item)?.let { playlists += it }
    }
    return playlists
}

private fun readStoredPlaylist(item: JSONObject): StoredPlaylist? {
    val title = item.optString(STORED_PLAYLIST_TITLE_KEY).trim()
    val entriesArray = item.optJSONArray(STORED_PLAYLIST_ENTRIES_KEY) ?: return null
    val entries = readPlaylistTrackEntries(entriesArray)
    if (title.isBlank() || entries.isEmpty()) return null
    return StoredPlaylist(
        id = item.optString(STORED_PLAYLIST_ID_KEY).trim().ifBlank { java.util.UUID.randomUUID().toString() },
        title = title,
        format = PlaylistStoredFormat.fromStorage(item.optString(STORED_PLAYLIST_FORMAT_KEY)),
        sourceIdHint = item.optString(STORED_PLAYLIST_SOURCE_HINT_KEY).trim().ifBlank { null },
        entries = entries,
        updatedAtMs = item.optLong(STORED_PLAYLIST_UPDATED_AT_KEY).takeIf { it > 0L }
            ?: System.currentTimeMillis()
    )
}

private fun readPlaylistTrackEntries(array: JSONArray): List<PlaylistTrackEntry> {
    val entries = mutableListOf<PlaylistTrackEntry>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val source = item.optString(PLAYLIST_ENTRY_SOURCE_KEY).trim()
        val title = item.optString(PLAYLIST_ENTRY_TITLE_KEY).trim()
        if (source.isBlank() || title.isBlank()) continue
        val fallbackAddedAt = (array.length() - index).toLong()
        entries += PlaylistTrackEntry(
            id = item.optString(PLAYLIST_ENTRY_ID_KEY).trim().ifBlank { java.util.UUID.randomUUID().toString() },
            source = source,
            title = title,
            artist = item.optString(PLAYLIST_ENTRY_ARTIST_KEY).trim().ifBlank { null },
            album = item.optString(PLAYLIST_ENTRY_ALBUM_KEY).trim().ifBlank { null },
            artworkThumbnailCacheKey = item.optString(PLAYLIST_ENTRY_ARTWORK_CACHE_KEY).trim().ifBlank { null },
            subtuneIndex = item.optInt(PLAYLIST_ENTRY_SUBTUNE_KEY, Int.MIN_VALUE)
                .takeUnless { it == Int.MIN_VALUE || it < 0 },
            durationSecondsOverride = item.optDouble(PLAYLIST_ENTRY_DURATION_OVERRIDE_KEY, Double.NaN)
                .takeIf { it.isFinite() && it > 0.0 },
            addedAtMs = item.optLong(PLAYLIST_ENTRY_ADDED_AT_KEY)
                .takeIf { it > 0L }
                ?: fallbackAddedAt
        )
    }
    return entries
}

private fun writeStoredPlaylist(playlist: StoredPlaylist): JSONObject {
    return JSONObject()
        .put(STORED_PLAYLIST_ID_KEY, playlist.id)
        .put(STORED_PLAYLIST_TITLE_KEY, playlist.title)
        .put(STORED_PLAYLIST_FORMAT_KEY, playlist.format.storageValue)
        .put(STORED_PLAYLIST_SOURCE_HINT_KEY, playlist.sourceIdHint ?: "")
        .put(STORED_PLAYLIST_UPDATED_AT_KEY, playlist.updatedAtMs)
        .put(
            STORED_PLAYLIST_ENTRIES_KEY,
            JSONArray().apply {
                playlist.entries.forEach { put(writePlaylistTrackEntry(it)) }
            }
        )
}

private fun writePlaylistTrackEntry(entry: PlaylistTrackEntry): JSONObject {
    return JSONObject()
        .put(PLAYLIST_ENTRY_ID_KEY, entry.id)
        .put(PLAYLIST_ENTRY_SOURCE_KEY, entry.source)
        .put(PLAYLIST_ENTRY_TITLE_KEY, entry.title)
        .put(PLAYLIST_ENTRY_ARTIST_KEY, entry.artist ?: "")
        .put(PLAYLIST_ENTRY_ALBUM_KEY, entry.album ?: "")
        .put(PLAYLIST_ENTRY_ARTWORK_CACHE_KEY, entry.artworkThumbnailCacheKey ?: "")
        .put(PLAYLIST_ENTRY_SUBTUNE_KEY, entry.subtuneIndex ?: -1)
        .put(PLAYLIST_ENTRY_ADDED_AT_KEY, entry.addedAtMs)
        .apply {
            entry.durationSecondsOverride
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.let { put(PLAYLIST_ENTRY_DURATION_OVERRIDE_KEY, it) }
        }
}
