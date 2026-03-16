package com.flopster101.siliconplayer.ui.screens

import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DragIndicator
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.PlaylistLibraryState
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.decodePercentEncodedForDisplay
import com.flopster101.siliconplayer.ensureRecentArtworkThumbnailCached
import com.flopster101.siliconplayer.parseHttpSourceSpecFromInput
import com.flopster101.siliconplayer.parseSmbSourceSpecFromInput
import com.flopster101.siliconplayer.playlistEntryMatchesPlayback
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.recentArtworkThumbnailFile
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.sourceLeafNameForDisplay
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PlaylistsSurfaceDestination {
    Library,
    Favorites,
    StoredPlaylist
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

internal enum class PlaylistEntrySortMode(
    val label: String
) {
    Custom("Custom"),
    Title("Title"),
    Artist("Artist"),
    Album("Album"),
    RecentlyAdded("Recently added")
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
    favoritesSortMode: PlaylistEntrySortMode,
    backHandlingEnabled: Boolean = true,
    onBack: () -> Unit,
    onFavoritesSortModeChange: (PlaylistEntrySortMode) -> Unit,
    onOpenFavorite: (PlaylistTrackEntry) -> Unit,
    onPlayStoredPlaylist: (StoredPlaylist) -> Unit,
    onShuffleStoredPlaylist: (StoredPlaylist) -> Unit,
    onOpenStoredPlaylistEntry: (PlaylistTrackEntry, StoredPlaylist) -> Unit,
    onPlayFavoritePlaylist: () -> Unit,
    onShuffleFavoritePlaylist: () -> Unit,
    onDeleteAllFavorites: () -> Unit,
    onDeleteFavoriteTrack: (PlaylistTrackEntry) -> Unit,
    onMoveFavoriteTrack: (PlaylistTrackEntry, Int) -> Unit,
    onPlayFavoriteTrackAsCached: (PlaylistTrackEntry) -> Unit,
    onOpenFavoriteTrackLocation: (PlaylistTrackEntry) -> Unit,
    onShareFavoriteTrack: (PlaylistTrackEntry) -> Unit,
    onCopyFavoriteTrackSource: (PlaylistTrackEntry) -> Unit,
    onOpenFavoriteTrackInfo: (PlaylistTrackEntry) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var destination by rememberSaveable { mutableStateOf(PlaylistsSurfaceDestination.Library) }
    var selectedStoredPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var albumCollectionLayout by rememberSaveable { mutableStateOf(AlbumCollectionLayout.Grid) }
    val libraryTabs = rememberLibraryTabs()
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { libraryTabs.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val showingFavoritesDetail = destination == PlaylistsSurfaceDestination.Favorites
    val selectedStoredPlaylist = selectedStoredPlaylistId?.let { playlistId ->
        libraryState.playlists.firstOrNull { playlist -> playlist.id == playlistId }
    }
    val showingStoredPlaylistDetail =
        destination == PlaylistsSurfaceDestination.StoredPlaylist && selectedStoredPlaylist != null
    val showingPlaylistDetail = showingFavoritesDetail || showingStoredPlaylistDetail
    var storedPlaylistSortMode by rememberSaveable(selectedStoredPlaylistId) {
        mutableStateOf(PlaylistEntrySortMode.Custom)
    }
    var favoritesEditModeEnabled by rememberSaveable { mutableStateOf(false) }
    var favoritesDraggingEntryId by remember { mutableStateOf<String?>(null) }
    var showDeleteAllFavoritesConfirm by rememberSaveable { mutableStateOf(false) }
    var trackInfoDialogState by remember {
        mutableStateOf<PlaylistTrackInfoDialogState?>(null)
    }
    val detailSubtitle = when {
        showingFavoritesDetail -> "Favorites"
        showingStoredPlaylistDetail -> selectedStoredPlaylist?.title
        else -> null
    }
    val detailCollapseFraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val showCollapsedDetailSubtitle = detailSubtitle != null &&
        scrollBehavior.state.collapsedFraction >= 0.999f
    LaunchedEffect(pagerState.currentPage) {
        if (selectedTabIndex != pagerState.currentPage) {
            selectedTabIndex = pagerState.currentPage
        }
    }
    LaunchedEffect(destination, selectedStoredPlaylistId, libraryState.playlists) {
        if (
            destination == PlaylistsSurfaceDestination.StoredPlaylist &&
            selectedStoredPlaylist == null
        ) {
            destination = PlaylistsSurfaceDestination.Library
            selectedStoredPlaylistId = null
        }
    }
    BackHandler(enabled = backHandlingEnabled && showingPlaylistDetail) {
        favoritesEditModeEnabled = false
        favoritesDraggingEntryId = null
        selectedStoredPlaylistId = null
        destination = PlaylistsSurfaceDestination.Library
    }
    LaunchedEffect(favoritesEditModeEnabled, favoritesSortMode, libraryState.favorites) {
        val isCustomSort = favoritesSortMode == PlaylistEntrySortMode.Custom
        val missingDraggedEntry = favoritesDraggingEntryId != null &&
            libraryState.favorites.none { it.id == favoritesDraggingEntryId }
        if (!favoritesEditModeEnabled || !isCustomSort || missingDraggedEntry) {
            favoritesDraggingEntryId = null
        }
    }
    val sortedFavoriteEntries = remember(libraryState.favorites, favoritesSortMode) {
        sortPlaylistEntries(
            entries = libraryState.favorites,
            sortMode = favoritesSortMode
        )
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        if (showingPlaylistDetail && detailSubtitle != null) {
                            val detailTitleAlpha = ((detailCollapseFraction - 0.58f) / 0.42f)
                                .coerceIn(0f, 1f)
                            val playlistsTitleAlpha = 1f - detailTitleAlpha
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Playlists",
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier.graphicsLayer(alpha = playlistsTitleAlpha)
                                )
                                Text(
                                    text = detailSubtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer(alpha = detailTitleAlpha)
                                        .basicMarquee()
                                )
                            }
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
                                        text = "Playlist",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Text(text = if (showingPlaylistDetail) "Playlists" else "Library")
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (showingPlaylistDetail) {
                                favoritesEditModeEnabled = false
                                favoritesDraggingEntryId = null
                                selectedStoredPlaylistId = null
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
                        entries = sortedFavoriteEntries,
                        heroIcon = Icons.Default.Star,
                        emptyBody = "Your favorites will show up here.",
                        selectedSortMode = favoritesSortMode,
                        onSortModeSelected = onFavoritesSortModeChange,
                        isEditMode = favoritesEditModeEnabled,
                        onEditModeChanged = { enabled ->
                            favoritesEditModeEnabled = enabled
                            if (!enabled) {
                                favoritesDraggingEntryId = null
                            }
                        },
                        canReorderEntries = favoritesSortMode == PlaylistEntrySortMode.Custom,
                        draggingEntryId = favoritesDraggingEntryId,
                        onDraggingEntryIdChange = { favoritesDraggingEntryId = it },
                        activeSourceId = currentPlaybackSourceId,
                        currentSubtuneIndex = currentSubtuneIndex,
                        onEntryClick = onOpenFavorite,
                        onPlayPlaylist = onPlayFavoritePlaylist,
                        onShufflePlaylist = onShuffleFavoritePlaylist,
                        onDeletePlaylist = {},
                        canDeletePlaylist = false,
                        onDeleteAllEntries = { showDeleteAllFavoritesConfirm = true },
                        onPlayEntry = onOpenFavorite,
                        onPlayEntryAsCached = onPlayFavoriteTrackAsCached,
                        onDeleteEntry = onDeleteFavoriteTrack,
                        onMoveEntry = onMoveFavoriteTrack,
                        onOpenEntryLocation = onOpenFavoriteTrackLocation,
                        onShareEntry = onShareFavoriteTrack,
                        onCopyEntrySource = onCopyFavoriteTrackSource,
                        onOpenEntryInfo = { entry ->
                            trackInfoDialogState = buildPlaylistTrackInfoDialogState(
                                playlistTitle = "Favorites",
                                entry = entry
                            )
                        }
                    )
                }
            } else if (
                currentDestination == PlaylistsSurfaceDestination.StoredPlaylist &&
                selectedStoredPlaylist != null
            ) {
                val sortedStoredPlaylistEntries = sortPlaylistEntries(
                    entries = selectedStoredPlaylist.entries,
                    sortMode = storedPlaylistSortMode
                )
                val sortedStoredPlaylist = selectedStoredPlaylist.copy(entries = sortedStoredPlaylistEntries)
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
                        title = selectedStoredPlaylist.title,
                        entries = sortedStoredPlaylistEntries,
                        heroIcon = null,
                        emptyBody = "This playlist has no tracks.",
                        selectedSortMode = storedPlaylistSortMode,
                        onSortModeSelected = { storedPlaylistSortMode = it },
                        isEditMode = false,
                        onEditModeChanged = {},
                        showAddAction = false,
                        showEditAction = false,
                        showDeleteAllEntriesAction = false,
                        canReorderEntries = false,
                        draggingEntryId = null,
                        onDraggingEntryIdChange = {},
                        activeSourceId = currentPlaybackSourceId,
                        currentSubtuneIndex = currentSubtuneIndex,
                        onEntryClick = { entry -> onOpenStoredPlaylistEntry(entry, sortedStoredPlaylist) },
                        onPlayPlaylist = { onPlayStoredPlaylist(sortedStoredPlaylist) },
                        onShufflePlaylist = { onShuffleStoredPlaylist(sortedStoredPlaylist) },
                        onDeletePlaylist = {},
                        canDeletePlaylist = false,
                        onDeleteAllEntries = {},
                        canDeleteEntries = false,
                        onPlayEntry = { entry -> onOpenStoredPlaylistEntry(entry, sortedStoredPlaylist) },
                        onPlayEntryAsCached = {},
                        onDeleteEntry = {},
                        onMoveEntry = { _, _ -> },
                        onOpenEntryLocation = {},
                        onShareEntry = {},
                        onCopyEntrySource = {},
                        onOpenEntryInfo = { entry ->
                            trackInfoDialogState = buildPlaylistTrackInfoDialogState(
                                playlistTitle = selectedStoredPlaylist.title,
                                entry = entry
                            )
                        },
                        showPlayAsCachedAction = false,
                        showLocationAction = false,
                        showShareAction = false,
                        showCopySourceAction = false,
                        showInfoAction = true
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
                                    onOpenPlaylist = { playlist ->
                                        selectedStoredPlaylistId = playlist.id
                                        destination = PlaylistsSurfaceDestination.StoredPlaylist
                                    }
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
    if (showDeleteAllFavoritesConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllFavoritesConfirm = false },
            title = { Text("Delete all favorites?") },
            text = { Text("This will remove every track from Favorites.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllFavoritesConfirm = false
                        onDeleteAllFavorites()
                    }
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllFavoritesConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    trackInfoDialogState?.let { dialogState ->
        BrowserInfoDialog(
            title = "Track and decoder info",
            fields = dialogState.fields,
            onDismiss = { trackInfoDialogState = null }
        )
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
    heroIcon: ImageVector?,
    emptyBody: String,
    selectedSortMode: PlaylistEntrySortMode,
    onSortModeSelected: (PlaylistEntrySortMode) -> Unit,
    isEditMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    showAddAction: Boolean = true,
    showEditAction: Boolean = true,
    showDeleteAllEntriesAction: Boolean = true,
    canReorderEntries: Boolean,
    draggingEntryId: String?,
    onDraggingEntryIdChange: (String?) -> Unit,
    activeSourceId: String?,
    currentSubtuneIndex: Int,
    onEntryClick: (PlaylistTrackEntry) -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    canDeletePlaylist: Boolean,
    onDeleteAllEntries: () -> Unit,
    canDeleteEntries: Boolean = true,
    onPlayEntry: (PlaylistTrackEntry) -> Unit,
    onPlayEntryAsCached: (PlaylistTrackEntry) -> Unit,
    onDeleteEntry: (PlaylistTrackEntry) -> Unit,
    onMoveEntry: (PlaylistTrackEntry, Int) -> Unit,
    onOpenEntryLocation: (PlaylistTrackEntry) -> Unit,
    onShareEntry: (PlaylistTrackEntry) -> Unit,
    onCopyEntrySource: (PlaylistTrackEntry) -> Unit,
    onOpenEntryInfo: (PlaylistTrackEntry) -> Unit,
    showPlayAsCachedAction: Boolean = true,
    showLocationAction: Boolean = true,
    showShareAction: Boolean = true,
    showCopySourceAction: Boolean = true,
    showInfoAction: Boolean = true
) {
    item {
        PlaylistHeroCard(
            title = title,
            trackCountLabel = playlistTrackCountLabel(entries.size),
            heroIcon = heroIcon,
            entries = entries,
            selectedSortMode = selectedSortMode,
            onSortModeSelected = onSortModeSelected,
            isEditMode = isEditMode,
            onEditModeChanged = onEditModeChanged,
            showAddAction = showAddAction,
            showEditAction = showEditAction,
            showDeleteAllEntriesAction = showDeleteAllEntriesAction,
            onPlayPlaylist = onPlayPlaylist,
            onShufflePlaylist = onShufflePlaylist,
            onDeletePlaylist = onDeletePlaylist,
            canDeletePlaylist = canDeletePlaylist,
            onDeleteAllEntries = onDeleteAllEntries
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
        itemsIndexed(
            items = entries,
            key = { _, entry -> entry.id }
        ) { index, entry ->
            val isActive = playlistEntryMatchesPlayback(
                entry = entry,
                activeSourceId = activeSourceId,
                currentSubtuneIndex = currentSubtuneIndex
            )
            val canMoveUp = index > 0
            val canMoveDown = index < entries.lastIndex
            PlaylistTrackRow(
                entry = entry,
                isActive = isActive,
                isDragged = draggingEntryId == entry.id,
                editModeEnabled = isEditMode,
                canReorder = canReorderEntries,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onClick = { onEntryClick(entry) },
                onPlay = { onPlayEntry(entry) },
                onPlayAsCached = { onPlayEntryAsCached(entry) },
                canDelete = canDeleteEntries,
                onDelete = { onDeleteEntry(entry) },
                onMoveUp = { onMoveEntry(entry, -1) },
                onMoveDown = { onMoveEntry(entry, 1) },
                onDragStart = {
                    if (canReorderEntries) {
                        onDraggingEntryIdChange(entry.id)
                    }
                },
                onDragStep = { direction ->
                    if (canReorderEntries) {
                        if (direction > 0) {
                            onMoveEntry(entry, 1)
                        } else if (direction < 0) {
                            onMoveEntry(entry, -1)
                        }
                    }
                },
                onDragEnd = {
                    onDraggingEntryIdChange(null)
                },
                onOpenLocation = { onOpenEntryLocation(entry) },
                onShare = { onShareEntry(entry) },
                onCopySource = { onCopyEntrySource(entry) },
                onOpenInfo = { onOpenEntryInfo(entry) },
                showPlayAsCachedAction = showPlayAsCachedAction,
                showLocationAction = showLocationAction,
                showShareAction = showShareAction,
                showCopySourceAction = showCopySourceAction,
                showInfoAction = showInfoAction
            )
        }
    }
}

@Composable
private fun PlaylistHeroCard(
    title: String,
    trackCountLabel: String,
    heroIcon: ImageVector?,
    entries: List<PlaylistTrackEntry>,
    selectedSortMode: PlaylistEntrySortMode,
    onSortModeSelected: (PlaylistEntrySortMode) -> Unit,
    isEditMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    showAddAction: Boolean,
    showEditAction: Boolean,
    showDeleteAllEntriesAction: Boolean,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    canDeletePlaylist: Boolean,
    onDeleteAllEntries: () -> Unit
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
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
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete playlist") },
                        enabled = canDeletePlaylist,
                        onClick = {
                            menuExpanded = false
                            onDeletePlaylist()
                        }
                    )
                    if (showDeleteAllEntriesAction) {
                        DropdownMenuItem(
                            text = { Text("Delete all entries") },
                            onClick = {
                                menuExpanded = false
                                onDeleteAllEntries()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onShufflePlaylist) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle"
                )
            }
            FilledIconButton(
                onClick = onPlayPlaylist,
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
            if (showAddAction) {
                PlaylistActionPill(
                    label = "Add",
                    icon = Icons.Default.Add,
                    onClick = {}
                )
            }
            if (showEditAction) {
                PlaylistActionPill(
                    label = if (isEditMode) "Done" else "Edit",
                    icon = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    onClick = { onEditModeChanged(!isEditMode) }
                )
            }
            Box {
                PlaylistActionPill(
                    label = "Sort",
                    icon = Icons.Default.SwapVert,
                    onClick = { sortMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    PlaylistEntrySortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            leadingIcon = {
                                if (mode == selectedSortMode) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                sortMenuExpanded = false
                                onSortModeSelected(mode)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier.clip(pillShape).clickable(onClick = onClick),
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

internal fun sortPlaylistEntries(
    entries: List<PlaylistTrackEntry>,
    sortMode: PlaylistEntrySortMode
): List<PlaylistTrackEntry> {
    if (entries.size <= 1 || sortMode == PlaylistEntrySortMode.Custom) return entries
    val indexedEntries = entries.withIndex()
    return when (sortMode) {
        PlaylistEntrySortMode.Custom -> entries
        PlaylistEntrySortMode.Title -> {
            indexedEntries
                .sortedWith(
                    compareBy<IndexedValue<PlaylistTrackEntry>> { it.value.title.lowercase(Locale.ROOT) }
                        .thenBy { sortablePlaylistText(it.value.artist) }
                        .thenBy { sortablePlaylistText(it.value.album) }
                        .thenBy { it.index }
                )
                .map { it.value }
        }

        PlaylistEntrySortMode.Artist -> {
            indexedEntries
                .sortedWith(
                    compareBy<IndexedValue<PlaylistTrackEntry>> { sortablePlaylistText(it.value.artist) }
                        .thenBy { it.value.title.lowercase(Locale.ROOT) }
                        .thenBy { sortablePlaylistText(it.value.album) }
                        .thenBy { it.index }
                )
                .map { it.value }
        }

        PlaylistEntrySortMode.Album -> {
            indexedEntries
                .sortedWith(
                    compareBy<IndexedValue<PlaylistTrackEntry>> { sortablePlaylistText(it.value.album) }
                        .thenBy { sortablePlaylistText(it.value.artist) }
                        .thenBy { it.value.title.lowercase(Locale.ROOT) }
                        .thenBy { it.index }
                )
                .map { it.value }
        }

        PlaylistEntrySortMode.RecentlyAdded -> {
            indexedEntries
                .sortedWith(
                    compareByDescending<IndexedValue<PlaylistTrackEntry>> { it.value.addedAtMs }
                        .thenBy { it.index }
                )
                .map { it.value }
        }
    }
}

private fun sortablePlaylistText(value: String?): String {
    val normalized = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return if (normalized.isBlank()) "\uFFFF" else normalized
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
        PlaylistsSurfaceDestination.StoredPlaylist -> 1
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
    isDragged: Boolean,
    editModeEnabled: Boolean,
    canReorder: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPlayAsCached: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onOpenLocation: () -> Unit,
    onShare: () -> Unit,
    onCopySource: () -> Unit,
    onOpenInfo: () -> Unit,
    showPlayAsCachedAction: Boolean,
    showLocationAction: Boolean,
    showShareAction: Boolean,
    showCopySourceAction: Boolean,
    showInfoAction: Boolean
) {
    var menuExpanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val isRemoteSource = remember(entry.source) { isRemotePlaylistSource(entry.source) }
    val localEntryFile = remember(entry.source) { resolvePlaylistEntryLocalFile(entry.source) }
    val canOpenLocalLocation = localEntryFile?.exists() == true
    val draggedHighlightColor by animateColorAsState(
        targetValue = if (isDragged) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
        label = "playlistRowDraggedHighlight"
    )
    val draggedScale by animateFloatAsState(
        targetValue = if (isDragged) 1.014f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "playlistRowDraggedScale"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PLAYLISTS_DETAIL_CONTENT_GUTTER)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = draggedScale
                    scaleY = draggedScale
                }
                .clip(RoundedCornerShape(14.dp))
                .background(draggedHighlightColor)
                .let { base ->
                    if (editModeEnabled) {
                        base
                    } else {
                        base.clickable(onClick = onClick)
                    }
                }
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
            if (editModeEnabled) {
                PlaylistTrackReorderHandle(
                    reorderEnabled = canReorder,
                    isDragged = isDragged,
                    onDragStart = onDragStart,
                    onDragStep = onDragStep,
                    onDragEnd = onDragEnd
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = { menuExpanded = true }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Track options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        onClick = {
                            menuExpanded = false
                            onPlay()
                        }
                    )
                    if (showPlayAsCachedAction && isRemoteSource) {
                        DropdownMenuItem(
                            text = { Text("Play as cached") },
                            onClick = {
                                menuExpanded = false
                                onPlayAsCached()
                            }
                        )
                    }
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                    if (showLocationAction && canOpenLocalLocation) {
                        DropdownMenuItem(
                            text = { Text("Open location") },
                            onClick = {
                                menuExpanded = false
                                onOpenLocation()
                            }
                        )
                    }
                    if (showCopySourceAction && isRemoteSource) {
                        DropdownMenuItem(
                            text = { Text("Copy URL") },
                            onClick = {
                                menuExpanded = false
                                onCopySource()
                            }
                        )
                    } else if (showShareAction && canOpenLocalLocation) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                    }
                    if (showInfoAction) {
                        DropdownMenuItem(
                            text = { Text("Info") },
                            onClick = {
                                menuExpanded = false
                                onOpenInfo()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        enabled = canReorder && canMoveUp,
                        onClick = {
                            menuExpanded = false
                            onMoveUp()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        enabled = canReorder && canMoveDown,
                        onClick = {
                            menuExpanded = false
                            onMoveDown()
                        }
                    )
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun PlaylistTrackReorderHandle(
    reorderEnabled: Boolean,
    isDragged: Boolean,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragEnd: () -> Unit
) {
    var dragSwapRemainderPx by remember { mutableFloatStateOf(0f) }
    val dragSwapThresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    val handleTint by animateColorAsState(
        targetValue = when {
            !reorderEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
            isDragged -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
        },
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "playlistDragHandleTint"
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .let { base ->
                if (reorderEnabled) {
                    base.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                dragSwapRemainderPx = 0f
                                onDragStart()
                            },
                            onDragEnd = {
                                dragSwapRemainderPx = 0f
                                onDragEnd()
                            },
                            onDragCancel = {
                                dragSwapRemainderPx = 0f
                                onDragEnd()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragSwapRemainderPx += dragAmount.y
                                while (dragSwapRemainderPx >= dragSwapThresholdPx) {
                                    onDragStep(1)
                                    dragSwapRemainderPx -= dragSwapThresholdPx
                                }
                                while (dragSwapRemainderPx <= -dragSwapThresholdPx) {
                                    onDragStep(-1)
                                    dragSwapRemainderPx += dragSwapThresholdPx
                                }
                            }
                        )
                    }
                } else {
                    base
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = "Drag to reorder",
            tint = handleTint,
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun isRemotePlaylistSource(sourceId: String): Boolean {
    val normalized = sourceId.trim()
    if (normalized.isEmpty()) return false
    val scheme = Uri.parse(normalized).scheme?.lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https" || scheme == "smb") return true
    if (scheme == "archive") {
        val parsed = parseArchiveSourceId(normalized) ?: return false
        return parseHttpSourceSpecFromInput(parsed.archivePath) != null ||
            parseSmbSourceSpecFromInput(parsed.archivePath) != null
    }
    return parseHttpSourceSpecFromInput(normalized) != null ||
        parseSmbSourceSpecFromInput(normalized) != null
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

private data class PlaylistTrackInfoDialogState(
    val fields: List<BrowserInfoField>
)

private fun buildPlaylistTrackInfoDialogState(
    playlistTitle: String,
    entry: PlaylistTrackEntry
): PlaylistTrackInfoDialogState {
    val sourceId = entry.source.trim()
    val localFile = resolvePlaylistEntryLocalFile(sourceId)?.takeIf { it.exists() && it.isFile }
    val archiveSource = parseArchiveSourceId(sourceId)
    val httpSource = parseHttpSourceSpecFromInput(sourceId)
    val smbSource = parseSmbSourceSpecFromInput(sourceId)
    val sourceFileName = localFile?.name
        ?: sourceLeafNameForDisplay(sourceId)
        ?: entry.title
    val sourceSizeBytes = localFile?.length()
    val storageOrHostLabel = when {
        httpSource != null || smbSource != null -> "Host"
        archiveSource != null -> "Archive"
        else -> "Storage"
    }
    val storageOrHostValue = when {
        httpSource != null -> httpSource.host
        smbSource != null -> smbSource.host
        archiveSource != null -> decodePercentEncodedForDisplay(archiveSource.archivePath) ?: archiveSource.archivePath
        localFile?.parentFile != null -> localFile.parentFile?.absolutePath.orEmpty()
        else -> sourceId
    }
    val fields = buildBrowserInfoFields(
        entries = listOf(
            BrowserInfoEntry(
                name = sourceFileName,
                isDirectory = false,
                sizeBytes = sourceSizeBytes
            )
        ),
        path = sourceId,
        storageOrHostLabel = storageOrHostLabel,
        storageOrHost = storageOrHostValue
    ).toMutableList()
    fields += BrowserInfoField("Playlist", playlistTitle)
    if (entry.title.isNotBlank()) {
        fields += BrowserInfoField("Track title", entry.title)
    }
    entry.artist?.trim()?.takeIf { it.isNotBlank() }?.let { artist ->
        fields += BrowserInfoField("Artist", artist)
    }
    entry.album?.trim()?.takeIf { it.isNotBlank() }?.let { album ->
        fields += BrowserInfoField("Album", album)
    }
    entry.subtuneIndex?.let { subtuneIndex ->
        fields += BrowserInfoField("Subtune", "${subtuneIndex + 1}")
    }
    entry.durationSecondsOverride
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { seconds ->
            fields += BrowserInfoField("Duration", formatPlaylistInfoDuration(seconds))
        }
    return PlaylistTrackInfoDialogState(fields = fields)
}

private fun formatPlaylistInfoDuration(seconds: Double): String {
    val totalSeconds = seconds.toInt().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}
