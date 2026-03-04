package com.flopster101.siliconplayer.ui.screens

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.PlaylistLibraryState
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.ensureRecentArtworkThumbnailCached
import com.flopster101.siliconplayer.playlistEntryMatchesPlayback
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.recentArtworkThumbnailFile
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PlaylistsSurfaceDestination {
    Library,
    Favorites
}

private enum class LibrarySurfaceTab {
    Playlists,
    Albums,
    Artists
}

private enum class AlbumCollectionLayout {
    Grid,
    List
}

private const val PLAYLISTS_PAGE_NAV_DURATION_MS = 280
private val PLAYLISTS_DETAIL_CONTENT_GUTTER = 8.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistsScreen(
    libraryState: PlaylistLibraryState,
    activePlaylist: StoredPlaylist?,
    currentPlaybackSourceId: String?,
    currentSubtuneIndex: Int,
    bottomContentPadding: Dp,
    backHandlingEnabled: Boolean = true,
    onBack: () -> Unit,
    onOpenFavorite: (PlaylistTrackEntry) -> Unit,
    onOpenPlaylist: (StoredPlaylist) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var destination by rememberSaveable { mutableStateOf(PlaylistsSurfaceDestination.Library) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var albumCollectionLayout by rememberSaveable { mutableStateOf(AlbumCollectionLayout.Grid) }
    val libraryTabs = rememberLibraryTabs()
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { libraryTabs.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val showingFavoritesDetail = destination == PlaylistsSurfaceDestination.Favorites
    val showCollapsedDetailSubtitle = showingFavoritesDetail &&
        scrollBehavior.state.collapsedFraction >= 0.999f
    LaunchedEffect(pagerState.currentPage) {
        if (selectedTabIndex != pagerState.currentPage) {
            selectedTabIndex = pagerState.currentPage
        }
    }
    BackHandler(enabled = backHandlingEnabled && showingFavoritesDetail) {
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
                        Text(text = if (showingFavoritesDetail) "Playlists" else "Library")
                        if (showingFavoritesDetail) {
                            Box(
                                modifier = Modifier.height(18.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showCollapsedDetailSubtitle,
                                    enter = fadeIn(
                                        animationSpec = tween(
                                            durationMillis = 160,
                                            easing = LinearOutSlowInEasing
                                        )
                                    ),
                                    exit = fadeOut(
                                        animationSpec = tween(
                                            durationMillis = 90,
                                            easing = FastOutLinearInEasing
                                        )
                                    )
                                ) {
                                    Text(
                                        text = "Favorites",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
            if (currentDestination == PlaylistsSurfaceDestination.Favorites) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = bottomContentPadding + 16.dp
                    )
                ) {
                    playlistDetailContent(
                        title = "Favorites",
                        entries = libraryState.favorites,
                        heroIcon = Icons.Default.Star,
                        emptyBody = "Your favorites will show up here.",
                        activeSourceId = currentPlaybackSourceId,
                        currentSubtuneIndex = currentSubtuneIndex,
                        onEntryClick = onOpenFavorite
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    LibraryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        tabs = libraryTabs,
                        onTabSelected = { tabIndex ->
                            if (selectedTabIndex == tabIndex) return@LibraryTabRow
                            selectedTabIndex = tabIndex
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(tabIndex)
                            }
                        }
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (libraryTabs[page]) {
                            LibrarySurfaceTab.Playlists -> {
                                PlaylistsLibraryTabPage(
                                    libraryState = libraryState,
                                    bottomContentPadding = bottomContentPadding,
                                    onOpenFavorites = { destination = PlaylistsSurfaceDestination.Favorites },
                                    onOpenPlaylist = onOpenPlaylist
                                )
                            }
                            LibrarySurfaceTab.Albums -> {
                                AlbumsLibraryPlaceholderPage(
                                    bottomContentPadding = bottomContentPadding,
                                    layout = albumCollectionLayout,
                                    onLayoutChanged = { albumCollectionLayout = it }
                                )
                            }
                            LibrarySurfaceTab.Artists -> {
                                ArtistsLibraryPlaceholderPage(
                                    bottomContentPadding = bottomContentPadding
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsLibraryTabPage(
    libraryState: PlaylistLibraryState,
    bottomContentPadding: Dp,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (StoredPlaylist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = bottomContentPadding + 16.dp
        )
    ) {
        item {
            FavoritesCollectionRow(
                favoriteCount = libraryState.favorites.size,
                onClick = onOpenFavorites
            )
        }
        item {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 74.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        }
        if (libraryState.playlists.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                EmptySectionCard(
                    title = "No playlists yet",
                    body = "More playlist options will show up here later."
                )
            }
        } else {
            items(
                items = libraryState.playlists,
                key = { it.id }
            ) { playlist ->
                PlaylistCollectionRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist) }
                )
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(start = 74.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun LibraryCollectionPlaceholderPage(
    title: String,
    body: String,
    bottomContentPadding: Dp
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = bottomContentPadding + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        items(
            items = listOf(0, 1, 2, 3)
        ) {
            LibraryPlaceholderRow(
                title = "$title will appear here",
                body = "Add library folders later."
            )
        }
    }
}

@Composable
private fun AlbumsLibraryPlaceholderPage(
    bottomContentPadding: Dp,
    layout: AlbumCollectionLayout,
    onLayoutChanged: (AlbumCollectionLayout) -> Unit
) {
    val placeholderAlbums = listOf(
        "Album One",
        "Album Two",
        "Album Three",
        "Album Four",
        "Album Five",
        "Album Six"
    )
    AnimatedContent(
        targetState = layout,
        transitionSpec = {
            val enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 160,
                    easing = LinearOutSlowInEasing
                )
            )
            val exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 120,
                    easing = FastOutLinearInEasing
                )
            )
            enter togetherWith exit
        },
        label = "albumLayoutModeTransition",
        modifier = Modifier.fillMaxSize()
    ) { currentLayout ->
        if (currentLayout == AlbumCollectionLayout.List) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 10.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        AlbumLayoutToggleRow(
                            layout = currentLayout,
                            onLayoutChanged = onLayoutChanged
                        )
                    }
                    items(placeholderAlbums) { albumTitle ->
                        AlbumPlaceholderListRow(
                            title = albumTitle
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 10.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding + 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AlbumLayoutToggleRow(
                            layout = currentLayout,
                            onLayoutChanged = onLayoutChanged
                        )
                    }
                    gridItems(placeholderAlbums) { albumTitle ->
                        AlbumPlaceholderGridCard(
                            title = albumTitle
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsLibraryPlaceholderPage(
    bottomContentPadding: Dp
) {
    LibraryCollectionPlaceholderPage(
        title = "Artists",
        body = "Choose library folders later.",
        bottomContentPadding = bottomContentPadding
    )
}

@Composable
private fun AlbumLayoutToggleRow(
    layout: AlbumCollectionLayout,
    onLayoutChanged: (AlbumCollectionLayout) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            LibraryLayoutToggleButton(
                selected = layout == AlbumCollectionLayout.Grid,
                icon = Icons.Default.GridView,
                contentDescription = "Grid albums",
                onClick = { onLayoutChanged(AlbumCollectionLayout.Grid) }
            )
            LibraryLayoutToggleButton(
                selected = layout == AlbumCollectionLayout.List,
                icon = Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "List albums",
                onClick = { onLayoutChanged(AlbumCollectionLayout.List) }
            )
        }
    }
}

@Composable
private fun LibraryLayoutToggleButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    ) {
        Box(
            modifier = Modifier
                .size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(19.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun AlbumPlaceholderGridCard(
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Album placeholder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlbumPlaceholderListRow(
    title: String
) {
    LibraryPlaceholderRow(
        title = title,
        body = "Album placeholder"
    )
}

@Composable
private fun LibraryPlaceholderRow(
    title: String,
    body: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

@Composable
private fun LibraryTabRow(
    selectedTabIndex: Int,
    tabs: List<LibrarySurfaceTab>,
    onTabSelected: (Int) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = index == selectedTabIndex,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = when (tab) {
                            LibrarySurfaceTab.Playlists -> "Playlists"
                            LibrarySurfaceTab.Albums -> "Albums"
                            LibrarySurfaceTab.Artists -> "Artists"
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun rememberLibraryTabs(): List<LibrarySurfaceTab> {
    return listOf(
        LibrarySurfaceTab.Playlists,
        LibrarySurfaceTab.Albums,
        LibrarySurfaceTab.Artists
    )
}

private fun LazyListScope.playlistDetailContent(
    title: String,
    entries: List<PlaylistTrackEntry>,
    heroIcon: ImageVector,
    emptyBody: String,
    activeSourceId: String?,
    currentSubtuneIndex: Int,
    onEntryClick: (PlaylistTrackEntry) -> Unit
) {
    item {
        PlaylistHeroCard(
            title = title,
            trackCountLabel = playlistTrackCountLabel(entries.size),
            heroIcon = heroIcon,
            entries = entries
        )
    }
    if (entries.isEmpty()) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PLAYLISTS_DETAIL_CONTENT_GUTTER + 6.dp,
                        top = 8.dp,
                        end = PLAYLISTS_DETAIL_CONTENT_GUTTER + 6.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Nothing here yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = emptyBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                onClick = { onEntryClick(entry) }
            )
        }
    }
}

@Composable
private fun PlaylistHeroCard(
    title: String,
    trackCountLabel: String,
    heroIcon: ImageVector?,
    entries: List<PlaylistTrackEntry>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = PLAYLISTS_DETAIL_CONTENT_GUTTER,
                top = 8.dp,
                end = PLAYLISTS_DETAIL_CONTENT_GUTTER,
                bottom = 8.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PlaylistCoverArt(
            entries = entries,
            heroIcon = heroIcon,
            modifier = Modifier.size(220.dp),
            iconSize = 68.dp
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = trackCountLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More actions"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle"
                )
            }
            FilledIconButton(
                onClick = {},
                modifier = Modifier.size(64.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play playlist",
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaylistActionPill(
                label = "Add",
                icon = Icons.Default.Add
            )
            PlaylistActionPill(
                label = "Edit",
                icon = Icons.Default.Edit
            )
            PlaylistActionPill(
                label = "Sort",
                icon = Icons.Default.SwapVert
            )
        }
    }
}

@Composable
private fun PlaylistActionPill(
    label: String,
    icon: ImageVector
) {
    val pillShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier.clip(pillShape).clickable(onClick = {}),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = pillShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun PlaylistCoverArt(
    entries: List<PlaylistTrackEntry>,
    heroIcon: ImageVector?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        if (heroIcon != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(iconSize * 1.65f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = heroIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        } else {
            PlaylistIconGrid(entries = entries)
        }
    }
}

@Composable
private fun PlaylistIconGrid(
    entries: List<PlaylistTrackEntry>
) {
    val coverSources = playlistCoverSources(entries)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlaylistCoverCell(
                source = coverSources[0],
                modifier = Modifier.weight(1f)
            )
            PlaylistCoverCell(
                source = coverSources[1],
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlaylistCoverCell(
                source = coverSources[2],
                modifier = Modifier.weight(1f)
            )
            PlaylistCoverCell(
                source = coverSources[3],
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlaylistCoverCell(
    source: String?,
    modifier: Modifier = Modifier
) {
    val icon = placeholderArtworkIconForFile(
        file = source?.let(::File),
        decoderName = null,
        allowCurrentDecoderFallback = false
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun FavoritesCollectionRow(
    favoriteCount: Int,
    onClick: () -> Unit
) {
    PlaylistLibraryFlatRow(
        modifier = Modifier
            .fillMaxWidth(),
        title = "Favorites",
        subtitle = when (favoriteCount) {
            0 -> "No tracks yet"
            1 -> "1 track"
            else -> "$favoriteCount tracks"
        },
        icon = Icons.Default.Star,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onClick
    )
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

private fun playlistCoverSources(entries: List<PlaylistTrackEntry>): List<String?> {
    val distinctSources = entries
        .asSequence()
        .mapNotNull { entry -> entry.source.takeIf { it.isNotBlank() } }
        .distinctBy { source -> playlistCoverSourceKey(source) }
        .take(4)
        .toMutableList()
    if (distinctSources.isEmpty()) {
        distinctSources += ""
    }
    while (distinctSources.size < 4) {
        distinctSources += distinctSources.last()
    }
    return distinctSources
}

private fun playlistCoverSourceKey(source: String): String {
    val fileName = source.substringAfterLast('/').substringAfterLast('\\')
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
    return extension.ifBlank {
        fileName.lowercase(Locale.ROOT)
    }
}

@Composable
private fun PlaylistTrackRow(
    entry: PlaylistTrackEntry,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PLAYLISTS_DETAIL_CONTENT_GUTTER)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 6.dp, top = 10.dp, end = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistTrackArtworkChip(
                entry = entry,
                isActive = isActive
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlistPageTrackSubtitle(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Track options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun PlaylistTrackArtworkChip(
    entry: PlaylistTrackEntry,
    isActive: Boolean
) {
    val context = LocalContext.current
    val fallbackIcon = placeholderArtworkIconForFile(
        file = resolvePlaylistEntryLocalFile(entry.source),
        decoderName = null,
        allowCurrentDecoderFallback = false
    )
    val artworkThumbnailCacheKey = androidx.compose.runtime.produceState<String?>(
        initialValue = entry.artworkThumbnailCacheKey,
        key1 = entry.id,
        key2 = entry.source,
        key3 = entry.artworkThumbnailCacheKey
    ) {
        if (!entry.artworkThumbnailCacheKey.isNullOrBlank()) {
            value = entry.artworkThumbnailCacheKey
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            ensureRecentArtworkThumbnailCached(
                context = context,
                sourceId = entry.source
            )
        }
    }.value
    val artwork = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = artworkThumbnailCacheKey
    ) {
        value = withContext(Dispatchers.IO) {
            val artworkFile = recentArtworkThumbnailFile(context, artworkThumbnailCacheKey)
                ?: return@withContext null
            BitmapFactory.decodeFile(artworkFile.absolutePath)?.asImageBitmap()
        }
    }.value
    Surface(
        modifier = Modifier.size(46.dp),
        shape = MaterialTheme.shapes.large,
        color = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp)
            )
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PlaylistCollectionRow(
    playlist: StoredPlaylist,
    onClick: () -> Unit
) {
    PlaylistLibraryFlatRow(
        modifier = Modifier
            .fillMaxWidth(),
        title = playlist.title,
        subtitle = "${playlist.entries.size} tracks • ${playlist.format.label}",
        icon = Icons.Default.LibraryMusic,
        iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick
    )
}

@Composable
private fun PlaylistLibraryFlatRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(start = 6.dp, top = 10.dp, end = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.large,
            color = iconContainerColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun playlistPageTrackSubtitle(entry: PlaylistTrackEntry): String {
    return entry.artist?.trim()?.takeIf { it.isNotBlank() } ?: "No metadata yet"
}
