package com.flopster101.siliconplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.PlaylistLibraryState
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.playlistEntryMatchesPlayback
import com.flopster101.siliconplayer.playlistTrackSubtitle
import com.flopster101.siliconplayer.samePath

private enum class PlaylistsSurfaceDestination {
    Library,
    Favorites
}

private const val PLAYLISTS_PAGE_NAV_DURATION_MS = 280

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistsScreen(
    libraryState: PlaylistLibraryState,
    activePlaylist: StoredPlaylist?,
    currentPlaybackSourceId: String?,
    currentSubtuneIndex: Int,
    bottomContentPadding: Dp,
    canAddCurrentTrackToFavorites: Boolean,
    canSaveActivePlaylist: Boolean,
    onBack: () -> Unit,
    onAddCurrentTrackToFavorites: () -> Unit,
    onSaveActivePlaylist: () -> Unit,
    onOpenFavorite: (PlaylistTrackEntry) -> Unit,
    onRemoveFavorite: (PlaylistTrackEntry) -> Unit,
    onOpenPlaylist: (StoredPlaylist) -> Unit,
    onRemovePlaylist: (StoredPlaylist) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val activePlaylistId = activePlaylist?.id
    var destination by rememberSaveable { mutableStateOf(PlaylistsSurfaceDestination.Library) }
    val showingFavoritesDetail = destination == PlaylistsSurfaceDestination.Favorites
    BackHandler(enabled = showingFavoritesDetail) {
        destination = PlaylistsSurfaceDestination.Library
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "Playlists")
                        if (showingFavoritesDetail) {
                            Text(
                                text = "Favorites",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (showingFavoritesDetail) {
                                destination = PlaylistsSurfaceDestination.Library
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                val forward = playlistsSurfaceDestinationOrder(targetState) >=
                    playlistsSurfaceDestinationOrder(initialState)
                val enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth / 4 },
                    animationSpec = tween(
                        durationMillis = PLAYLISTS_PAGE_NAV_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 210,
                        delayMillis = 40,
                        easing = LinearOutSlowInEasing
                    )
                )
                val exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 },
                    animationSpec = tween(
                        durationMillis = PLAYLISTS_PAGE_NAV_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 110,
                        easing = FastOutLinearInEasing
                    )
                )
                enter togetherWith exit
            },
            label = "playlistsSurfaceTransition",
            modifier = Modifier.fillMaxSize()
        ) { currentDestination ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentDestination == PlaylistsSurfaceDestination.Favorites) {
                    playlistDetailContent(
                        title = "Favorites",
                        entries = libraryState.favorites,
                        heroIcon = Icons.Default.Star,
                        emptyBody = "Your favorites will show up here.",
                        activeSourceId = currentPlaybackSourceId,
                        currentSubtuneIndex = currentSubtuneIndex,
                        onEntryClick = onOpenFavorite,
                        onEntryDelete = onRemoveFavorite
                    )
                } else {
                    item {
                        FavoritesCollectionRow(
                            favoriteCount = libraryState.favorites.size,
                            hasActiveFavorite = libraryState.favorites.any { entry ->
                                playlistEntryMatchesPlayback(
                                    entry = entry,
                                    activeSourceId = currentPlaybackSourceId,
                                    currentSubtuneIndex = currentSubtuneIndex
                                )
                            },
                            onClick = { destination = PlaylistsSurfaceDestination.Favorites }
                        )
                    }

                    activePlaylist?.takeIf { playlist ->
                        libraryState.playlists.none { it.id == playlist.id }
                    }?.let { sessionPlaylist ->
                        item(key = "active_session_playlist") {
                            SessionPlaylistCard(
                                playlist = sessionPlaylist,
                                onOpen = { onOpenPlaylist(sessionPlaylist) }
                            )
                        }
                    }
                    if (libraryState.playlists.isEmpty()) {
                        if (activePlaylist == null || libraryState.playlists.any { it.id == activePlaylist.id }) {
                            item {
                                EmptySectionCard(
                                    title = "No playlists yet",
                                    body = "More playlist options will show up here later."
                                )
                            }
                        }
                    } else {
                        items(
                            items = libraryState.playlists,
                            key = { it.id }
                        ) { playlist ->
                            PlaylistCollectionRow(
                                playlist = playlist,
                                isActive = playlist.id == activePlaylistId || (
                                    playlist.sourceIdHint != null &&
                                        activePlaylist?.sourceIdHint != null &&
                                        samePath(playlist.sourceIdHint, activePlaylist.sourceIdHint)
                                    ),
                                onClick = { onOpenPlaylist(playlist) },
                                onDelete = { onRemovePlaylist(playlist) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.playlistDetailContent(
    title: String,
    entries: List<PlaylistTrackEntry>,
    heroIcon: ImageVector,
    emptyBody: String,
    activeSourceId: String?,
    currentSubtuneIndex: Int,
    onEntryClick: (PlaylistTrackEntry) -> Unit,
    onEntryDelete: (PlaylistTrackEntry) -> Unit
) {
    item {
        PlaylistHeroCard(
            title = title,
            trackCountLabel = playlistTrackCountLabel(entries.size),
            heroIcon = heroIcon
        )
    }
    if (entries.isEmpty()) {
        item {
            EmptySectionCard(
                title = "Nothing here yet",
                body = emptyBody
            )
        }
    } else {
        items(
            items = entries,
            key = { it.id }
        ) { entry ->
            val isActive = playlistEntryMatchesPlayback(
                entry = entry,
                activeSourceId = activeSourceId,
                currentSubtuneIndex = currentSubtuneIndex
            )
            PlaylistTrackRow(
                entry = entry,
                isActive = isActive,
                onClick = { onEntryClick(entry) },
                onDelete = { onEntryDelete(entry) }
            )
        }
    }
}

@Composable
private fun PlaylistHeroCard(
    title: String,
    trackCountLabel: String,
    heroIcon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = heroIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = trackCountLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun FavoritesCollectionRow(
    favoriteCount: Int,
    hasActiveFavorite: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (hasActiveFavorite) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        if (hasActiveFavorite) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (hasActiveFavorite) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when (favoriteCount) {
                        0 -> "No tracks yet"
                        1 -> "1 track"
                        else -> "$favoriteCount tracks"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptySectionCard(
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun playlistsSurfaceDestinationOrder(destination: PlaylistsSurfaceDestination): Int =
    when (destination) {
        PlaylistsSurfaceDestination.Library -> 0
        PlaylistsSurfaceDestination.Favorites -> 1
    }

private fun playlistTrackCountLabel(trackCount: Int): String =
    when (trackCount) {
        0 -> "No tracks yet"
        1 -> "1 track"
        else -> "$trackCount tracks"
    }

@Composable
private fun PlaylistTrackRow(
    entry: PlaylistTrackEntry,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlistTrackSubtitle(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove favorite"
                )
            }
        }
    }
}

@Composable
private fun SessionPlaylistCard(
    playlist: StoredPlaylist,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Current playlist session",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${playlist.entries.size} entries • ${playlist.format.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun PlaylistCollectionRow(
    playlist: StoredPlaylist,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (isActive) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playlist.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AssistChip(
                        onClick = onClick,
                        label = { Text(playlist.format.label) }
                    )
                }
                Text(
                    text = "${playlist.entries.size} entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete playlist"
                )
            }
        }
    }
}
