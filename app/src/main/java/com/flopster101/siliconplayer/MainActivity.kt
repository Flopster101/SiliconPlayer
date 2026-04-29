package com.flopster101.siliconplayer

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.* // Import for remember, mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.ExperimentalComposeUiApi
import com.flopster101.siliconplayer.playback.loadPlayableSiblingFilesForExternalIntent
import org.json.JSONArray
import com.flopster101.siliconplayer.playback.applyTrackSelectionAction
import com.flopster101.siliconplayer.session.exportCachedFilesToTree
import com.flopster101.siliconplayer.ui.screens.PlaylistEntrySortMode
import com.flopster101.siliconplayer.ui.screens.sortPlaylistEntries
import com.flopster101.siliconplayer.ui.visualization.rememberVisualizationUiState
import com.flopster101.siliconplayer.ui.theme.SiliconPlayerTheme
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.IntentFilter
import android.provider.Settings
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val PLAYER_ARTWORK_STOP_GRACE_MS = 180L

private data class LocalArtworkSwipeCacheEntry(
    val artwork: ImageBitmap?
)

@Composable
private fun rememberDisplayedPlayerArtwork(
    trackKey: String?,
    artwork: ImageBitmap?,
    resolvedTrackKey: String?
): ImageBitmap? {
    var displayedArtwork by remember { mutableStateOf<ImageBitmap?>(artwork) }
    val latestTrackKey by rememberUpdatedState(trackKey)
    val latestArtwork by rememberUpdatedState(artwork)
    val latestResolvedTrackKey by rememberUpdatedState(resolvedTrackKey)
    val artworkLookupPending = trackKey != null &&
        artwork == null &&
        resolvedTrackKey != trackKey

    LaunchedEffect(trackKey, artwork, resolvedTrackKey) {
        if (artwork != null) {
            displayedArtwork = artwork
            return@LaunchedEffect
        }
        if (artworkLookupPending) {
            return@LaunchedEffect
        }
        if (displayedArtwork == null) {
            return@LaunchedEffect
        }
        val expectedTrackKey = trackKey
        val expectedResolvedTrackKey = resolvedTrackKey
        val holdMs = if (trackKey == null) PLAYER_ARTWORK_STOP_GRACE_MS else 0L
        delay(holdMs)
        if (
            latestTrackKey == expectedTrackKey &&
            latestArtwork == null &&
            latestResolvedTrackKey == expectedResolvedTrackKey
        ) {
            displayedArtwork = null
        }
    }

    return displayedArtwork
}

@Composable
private fun rememberLocalArtworkSwipePreviewState(
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    visiblePlayableFiles: List<File>
): ArtworkSwipePreviewState {
    val artworkCache = remember { mutableStateMapOf<String, LocalArtworkSwipeCacheEntry>() }
    val isLocalSwipeEligible = remember(
        selectedFile?.absolutePath,
        currentPlaybackSourceId,
        visiblePlayableFiles
    ) {
        val localFile = selectedFile
        if (localFile == null || !localFile.exists() || !localFile.isFile) {
            return@remember false
        }
        if (currentTrackIndexForList(localFile, visiblePlayableFiles) < 0) {
            return@remember false
        }
        currentPlaybackSourceId.isNullOrBlank() || samePath(currentPlaybackSourceId, localFile.absolutePath)
    }
    val currentTrackKey = if (isLocalSwipeEligible) selectedFile?.absolutePath else null
    val previousFile = remember(
        isLocalSwipeEligible,
        selectedFile?.absolutePath,
        visiblePlayableFiles
    ) {
        if (!isLocalSwipeEligible) {
            null
        } else {
            adjacentTrackForOffset(
                selectedFile = selectedFile,
                visiblePlayableFiles = visiblePlayableFiles,
                offset = -1
            )
        }
    }
    val nextFile = remember(
        isLocalSwipeEligible,
        selectedFile?.absolutePath,
        visiblePlayableFiles
    ) {
        if (!isLocalSwipeEligible) {
            null
        } else {
            adjacentTrackForOffset(
                selectedFile = selectedFile,
                visiblePlayableFiles = visiblePlayableFiles,
                offset = 1
            )
        }
    }
    LaunchedEffect(isLocalSwipeEligible, previousFile?.absolutePath, nextFile?.absolutePath, selectedFile?.absolutePath) {
        listOfNotNull(
            if (isLocalSwipeEligible) selectedFile else null,
            previousFile,
            nextFile
        ).forEach { file ->
            val key = file.absolutePath
            if (artworkCache.containsKey(key)) return@forEach
            val artwork = withContext(Dispatchers.IO) {
                loadArtworkForFile(file)
            }
            artworkCache[key] = LocalArtworkSwipeCacheEntry(artwork = artwork)
        }
    }

    val currentArtworkEntry = currentTrackKey?.let { artworkCache[it] }
    val previousTrackKey = previousFile?.absolutePath
    val nextTrackKey = nextFile?.absolutePath
    val previousArtworkEntry = previousTrackKey?.let { artworkCache[it] }
    val nextArtworkEntry = nextTrackKey?.let { artworkCache[it] }
    val currentPlaceholderIcon = if (isLocalSwipeEligible) {
        placeholderArtworkIconForFile(
            file = selectedFile,
            decoderName = null,
            allowCurrentDecoderFallback = false
        )
    } else {
        Icons.Default.MusicNote
    }
    val previousPlaceholderIcon = placeholderArtworkIconForFile(
        file = previousFile,
        decoderName = null,
        allowCurrentDecoderFallback = false
    )
    val nextPlaceholderIcon = placeholderArtworkIconForFile(
        file = nextFile,
        decoderName = null,
        allowCurrentDecoderFallback = false
    )

    return remember(
        currentTrackKey,
        currentArtworkEntry,
        currentPlaceholderIcon,
        previousTrackKey,
        nextTrackKey,
        previousArtworkEntry,
        nextArtworkEntry,
        previousPlaceholderIcon,
        nextPlaceholderIcon
    ) {
        ArtworkSwipePreviewState(
            currentTrackKey = currentTrackKey,
            currentArtworkResolved = currentTrackKey != null && currentArtworkEntry != null,
            currentArtwork = currentArtworkEntry?.artwork,
            currentPlaceholderIcon = currentPlaceholderIcon,
            previousTrackKey = previousTrackKey,
            nextTrackKey = nextTrackKey,
            canSwipePrevious = previousTrackKey != null && previousArtworkEntry != null,
            canSwipeNext = nextTrackKey != null && nextArtworkEntry != null,
            previousArtwork = previousArtworkEntry?.artwork,
            nextArtwork = nextArtworkEntry?.artwork,
            previousPlaceholderIcon = previousPlaceholderIcon,
            nextPlaceholderIcon = nextPlaceholderIcon
        )
    }
}

class MainActivity : ComponentActivity() {
    private var initialFileToOpen: File? = null
    private var initialFileFromExternalIntent: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeBridge.installContext(applicationContext)
        applyRemoteSourceCachePolicyOnLaunch(this, cacheDir)
        applyArchiveMountCachePolicyOnLaunch(this, cacheDir)
        if (shouldOpenPlayerFromNotification(intent)) {
            notificationOpenPlayerSignal++
        }
        resolveInitialFileToOpen(contentResolver, intent)?.let { file ->
            initialFileToOpen = file
            initialFileFromExternalIntent = true
        }
        setContent {
            val prefs = remember {
                getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
            }
            var themeMode by remember {
                mutableStateOf(
                    ThemeMode.fromStorage(
                        prefs.getString(AppPreferenceKeys.THEME_MODE, ThemeMode.Auto.storageValue)
                    )
                )
            }
            val monetAvailable = remember { supportsMonetTheming() }
            var useMonet by remember {
                mutableStateOf(
                    monetAvailable && prefs.getBoolean(
                        AppPreferenceKeys.THEME_USE_MONET,
                        defaultUseMonetForCurrentApi()
                    )
                )
            }
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.Auto -> resolveAutoDarkThemePreference(this@MainActivity, systemDarkTheme)
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SiliconPlayerTheme(
                darkTheme = darkTheme,
                dynamicColor = monetAvailable && useMonet
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(
                        themeMode = themeMode,
                        useMonet = useMonet,
                        monetAvailable = monetAvailable,
                        onThemeModeChanged = { mode ->
                            themeMode = mode
                            prefs.edit()
                                .putString(AppPreferenceKeys.THEME_MODE, mode.storageValue)
                                .apply()
                        },
                        onUseMonetChanged = { enabled ->
                            val effectiveEnabled = monetAvailable && enabled
                            useMonet = effectiveEnabled
                            prefs.edit()
                                .putBoolean(AppPreferenceKeys.THEME_USE_MONET, effectiveEnabled)
                                .apply()
                        },
                        initialFileToOpen = initialFileToOpen,
                        initialFileFromExternalIntent = initialFileFromExternalIntent
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (shouldOpenPlayerFromNotification(intent)) {
            notificationOpenPlayerSignal++
        }
        resolveInitialFileToOpen(contentResolver, intent)?.let { file ->
            initialFileToOpen = file
            initialFileFromExternalIntent = true
        }
    }

    override fun onStart() {
        super.onStart()
        // Activity lifecycle is the source of truth for fg/bg state; service
        // liveness can lag behind it across pause+background cycles.
        NativeBridge.setBackgroundPlaybackMode(false)
    }

    override fun onStop() {
        NativeBridge.setBackgroundPlaybackMode(true)
        super.onStop()
    }

    external fun getStringFromJNI(): String
    external fun startEngine()
    external fun stopEngine()
    external fun isEnginePlaying(): Boolean
    external fun loadAudio(path: String)
    external fun getSupportedExtensions(): Array<String>
    external fun getDuration(): Double
    external fun getPosition(): Double
    external fun consumeNaturalEndEvent(): Boolean
    external fun seekTo(seconds: Double)
    external fun isSeekInProgress(): Boolean
    external fun setLooping(enabled: Boolean)
    external fun setRepeatMode(mode: Int)
    external fun getTrackTitle(): String
    external fun getTrackArtist(): String
    external fun getTrackSampleRate(): Int
    external fun getTrackChannelCount(): Int
    external fun getTrackBitDepth(): Int
    external fun getTrackBitDepthLabel(): String
    external fun getRepeatModeCapabilities(): Int
    external fun getPlaybackCapabilities(): Int
    external fun getTimelineMode(): Int
    external fun getCoreCapabilities(coreName: String): Int
    external fun getCoreRepeatModeCapabilities(coreName: String): Int
    external fun getCoreTimelineMode(coreName: String): Int
    external fun getCoreOptionApplyPolicy(coreName: String, optionName: String): Int
    external fun getCoreFixedSampleRateHz(coreName: String): Int
    external fun setCoreOutputSampleRate(coreName: String, sampleRateHz: Int)
    external fun setCoreOption(coreName: String, optionName: String, optionValue: String)
    external fun setAudioPipelineConfig(
        backendPreference: Int,
        performanceMode: Int,
        bufferPreset: Int,
        resamplerPreference: Int,
        allowFallback: Boolean
    )

    companion object {
        var notificationOpenPlayerSignal by mutableIntStateOf(0)

        init {
            System.loadLibrary("siliconplayer")
        }
    }
}

private fun resolveAutoDarkThemePreference(context: Context, systemDarkTheme: Boolean): Boolean {
    val isTvDevice = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return if (isTvDevice) true else systemDarkTheme
}

private const val HOME_BACK_EXIT_TIMEOUT_MS = 2_000L

private data class PendingPlaylistSubtuneSelection(
    val sourceId: String,
    val subtuneIndex: Int
)

private fun resolvePendingPlaylistEntry(
    activePlaylist: StoredPlaylist?,
    pendingPlaylistSubtuneSelection: PendingPlaylistSubtuneSelection?
): PlaylistTrackEntry? {
    val playlist = activePlaylist ?: return null
    val pendingSelection = pendingPlaylistSubtuneSelection ?: return null
    return playlist.entries.firstOrNull { entry ->
        samePath(entry.source, pendingSelection.sourceId) &&
            entry.subtuneIndex == pendingSelection.subtuneIndex
    }
}

private data class LastStoppedPlaylistResume(
    val playlist: StoredPlaylist,
    val entryId: String
)

private data class ExternalPlaylistRecentOverride(
    val playlistPath: String,
    val locationId: String?,
    val sourceHint: String?,
    val referencedSources: Set<String>
)

private fun buildFavoritesPlaybackPlaylist(entries: List<PlaylistTrackEntry>): StoredPlaylist {
    return StoredPlaylist(
        id = "__favorites__",
        title = "Favorites",
        format = PlaylistStoredFormat.Internal,
        entries = entries,
        updatedAtMs = 0L
    )
}

private fun parsePlaylistFileDocument(
    file: File,
    sourceIdHint: String?
): ParsedPlaylistDocument? {
    return runCatching {
        parsePlaylistDocument(
            file = file,
            sourceIdHint = sourceIdHint ?: file.absolutePath
        )
    }.getOrNull()
}

private fun openPlaylistEntry(
    context: Context,
    entry: PlaylistTrackEntry,
    playlist: StoredPlaylist?,
    trackLoadDelegates: AppNavigationTrackLoadDelegates,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    expandOverride: Boolean? = openPlayerOnTrackSelect,
    onActivePlaylistChanged: (StoredPlaylist?) -> Unit,
    onActivePlaylistEntryIdChanged: (String?) -> Unit,
    onPendingPlaylistSubtuneSelectionChanged: (PendingPlaylistSubtuneSelection?) -> Unit
) {
    onActivePlaylistChanged(playlist)
    onActivePlaylistEntryIdChanged(entry.id)
    onPendingPlaylistSubtuneSelectionChanged(
        entry.subtuneIndex?.let { PendingPlaylistSubtuneSelection(entry.source, it) }
    )
    val localFile = resolvePlaylistEntryLocalFile(entry.source)
    if (localFile != null) {
        if (!localFile.exists() || !localFile.isFile) {
            onPendingPlaylistSubtuneSelectionChanged(null)
            Toast.makeText(context, "Playlist entry is not available", Toast.LENGTH_SHORT).show()
            return
        }
        trackLoadDelegates.applyTrackSelection(
            file = localFile,
            autoStart = autoPlayOnTrackSelect,
            expandOverride = expandOverride,
            sourceIdOverride = localFile.absolutePath,
            initialSubtuneIndex = entry.subtuneIndex
        )
    } else {
        manualOpenDelegates.applyManualInputSelection(
            rawInput = sanitizePlaylistTrackRequestUrlHint(entry.source, entry.requestUrlHint)
                ?: entry.source,
            options = ManualSourceOpenOptions(initialSubtuneIndex = entry.subtuneIndex),
            expandOverride = expandOverride
        )
    }
}

private fun playAdjacentPlaylistEntry(
    context: Context,
    activePlaylist: StoredPlaylist?,
    currentEntryId: String?,
    offset: Int,
    wrapOverride: Boolean?,
    playlistWrapNavigation: Boolean,
    notifyWrap: Boolean,
    expandOverride: Boolean?,
    trackLoadDelegates: AppNavigationTrackLoadDelegates,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    onActivePlaylistChanged: (StoredPlaylist?) -> Unit,
    onActivePlaylistEntryIdChanged: (String?) -> Unit,
    onPendingPlaylistSubtuneSelectionChanged: (PendingPlaylistSubtuneSelection?) -> Unit
): Boolean {
    val playlist = activePlaylist ?: return false
    val entries = playlist.entries
    if (entries.isEmpty()) return false
    val currentIndex = currentEntryId
        ?.let { entryId -> entries.indexOfFirst { it.id == entryId } }
        ?: -1
    if (currentIndex !in entries.indices) return false
    val shouldWrap = wrapOverride ?: playlistWrapNavigation
    val rawTargetIndex = currentIndex + offset
    val targetIndex = if (shouldWrap) {
        val wrappedIndex = ((rawTargetIndex % entries.size) + entries.size) % entries.size
        if (wrappedIndex != rawTargetIndex && notifyWrap) {
            val message = if (offset < 0) {
                "Wrapped to last track"
            } else {
                "Wrapped to first track"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        wrappedIndex
    } else {
        if (rawTargetIndex !in entries.indices) return false
        rawTargetIndex
    }
    val targetEntry = entries.getOrNull(targetIndex) ?: return false
    openPlaylistEntry(
        context = context,
        entry = targetEntry,
        playlist = playlist,
        trackLoadDelegates = trackLoadDelegates,
        manualOpenDelegates = manualOpenDelegates,
        autoPlayOnTrackSelect = autoPlayOnTrackSelect,
        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
        expandOverride = expandOverride,
        onActivePlaylistChanged = onActivePlaylistChanged,
        onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
        onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged
    )
    return true
}

private fun openPlaylistDocument(
    context: Context,
    document: ParsedPlaylistDocument,
    trackLoadDelegates: AppNavigationTrackLoadDelegates,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    autoStart: Boolean = autoPlayOnTrackSelect,
    expandOverride: Boolean? = openPlayerOnTrackSelect,
    onActivePlaylistChanged: (StoredPlaylist?) -> Unit,
    onActivePlaylistEntryIdChanged: (String?) -> Unit,
    onShowPlaylistSelectorDialogChanged: (Boolean) -> Unit,
    onPendingPlaylistSubtuneSelectionChanged: (PendingPlaylistSubtuneSelection?) -> Unit,
    selectedEntryId: String? = null
) {
    if (document.entries.isEmpty()) {
        Toast.makeText(context, "Unable to open playlist", Toast.LENGTH_SHORT).show()
        return
    }
    val transientPlaylist = buildImportedPlaylist(document)
    onShowPlaylistSelectorDialogChanged(false)
    val entryToOpen = transientPlaylist.entries.firstOrNull { it.id == selectedEntryId }
        ?: transientPlaylist.entries.firstOrNull()
        ?: return
    openPlaylistEntry(
        context = context,
        entry = entryToOpen,
        playlist = transientPlaylist,
        trackLoadDelegates = trackLoadDelegates,
        manualOpenDelegates = manualOpenDelegates,
        autoPlayOnTrackSelect = autoStart,
        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
        expandOverride = expandOverride,
        onActivePlaylistChanged = onActivePlaylistChanged,
        onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
        onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged
    )
}

private fun playAdjacentBrowserFileFromAnchor(
    context: Context,
    anchorPath: String?,
    offset: Int,
    wrapOverride: Boolean?,
    playlistWrapNavigation: Boolean,
    notifyWrap: Boolean,
    activePlaylist: StoredPlaylist?,
    repository: com.flopster101.siliconplayer.data.FileRepository,
    visiblePlayableFiles: List<File>,
    playlistLibraryState: PlaylistLibraryState,
    trackLoadDelegates: AppNavigationTrackLoadDelegates,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    openPlayerOnTrackSelect: Boolean,
    expandOverride: Boolean?,
    onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit,
    onActivePlaylistChanged: (StoredPlaylist?) -> Unit,
    onActivePlaylistEntryIdChanged: (String?) -> Unit,
    onShowPlaylistSelectorDialogChanged: (Boolean) -> Unit,
    onPendingPlaylistSubtuneSelectionChanged: (PendingPlaylistSubtuneSelection?) -> Unit
): Boolean {
    val normalizedAnchorPath = anchorPath?.takeIf { it.isNotBlank() } ?: return false
    val referencedLocalSources = activePlaylist
        ?.entries
        ?.mapNotNull { entry -> resolvePlaylistEntryLocalFile(entry.source)?.absolutePath }
        .orEmpty()
    val anchorFile = File(normalizedAnchorPath)
    val parentDirectory = anchorFile.parentFile
    val browserContinuationFiles = if (parentDirectory != null && parentDirectory.exists() && parentDirectory.isDirectory) {
        repository.getFiles(parentDirectory)
            .asSequence()
            .filter { item ->
                !item.isDirectory &&
                    (repository.isPlayableFile(item.file) || isSupportedPlaylistFile(item.file))
            }
            .map { item -> item.file }
            .toList()
    } else {
        visiblePlayableFiles
    }
        .filter { file ->
            samePath(file.absolutePath, normalizedAnchorPath) ||
                referencedLocalSources.none { sourcePath ->
                    samePath(file.absolutePath, sourcePath)
                }
        }
    if (browserContinuationFiles.isEmpty()) return false
    val currentIndex = browserContinuationFiles.indexOfFirst { file ->
        samePath(file.absolutePath, normalizedAnchorPath)
    }
    if (currentIndex !in browserContinuationFiles.indices) return false
    val rawTargetIndex = currentIndex + offset
    val shouldWrap = wrapOverride ?: playlistWrapNavigation
    val targetIndex = if (shouldWrap) {
        val wrappedIndex =
            ((rawTargetIndex % browserContinuationFiles.size) + browserContinuationFiles.size) % browserContinuationFiles.size
        if (wrappedIndex != rawTargetIndex && notifyWrap) {
            val message = if (offset < 0) {
                "Wrapped to last track"
            } else {
                "Wrapped to first track"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        wrappedIndex
    } else {
        if (rawTargetIndex !in browserContinuationFiles.indices) return false
        rawTargetIndex
    }
    val targetFile = browserContinuationFiles.getOrNull(targetIndex) ?: return false
    if (isSupportedPlaylistFile(targetFile)) {
        val parsed = parsePlaylistFileDocument(targetFile, targetFile.absolutePath) ?: return false
        openPlaylistDocument(
            context = context,
            document = parsed,
            trackLoadDelegates = trackLoadDelegates,
            manualOpenDelegates = manualOpenDelegates,
            autoPlayOnTrackSelect = true,
            openPlayerOnTrackSelect = openPlayerOnTrackSelect,
            autoStart = true,
            expandOverride = expandOverride,
            onActivePlaylistChanged = onActivePlaylistChanged,
            onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
            onShowPlaylistSelectorDialogChanged = onShowPlaylistSelectorDialogChanged,
            onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged
        )
    } else {
        trackLoadDelegates.applyTrackSelection(
            file = targetFile,
            autoStart = true,
            expandOverride = expandOverride
        )
    }
    return true
}

private fun isRemoteQueuePlaybackSource(sourceId: String?): Boolean {
    val normalizedSourceId = normalizeSourceIdentity(sourceId) ?: return false
    return when (Uri.parse(normalizedSourceId).scheme?.lowercase()) {
        "http", "https", "smb" -> true
        else -> false
    }
}

private fun resolveActivePlaylistMetadataEntry(
    activePlaylist: StoredPlaylist?,
    activePlaylistEntryId: String?,
    activeSourceId: String?,
    currentSubtuneIndex: Int
): PlaylistTrackEntry? {
    val entry = activePlaylist
        ?.entries
        ?.firstOrNull { it.id == activePlaylistEntryId }
        ?: return null
    return entry.takeIf {
        playlistEntryMatchesPlayback(
            entry = it,
            activeSourceId = activeSourceId,
            currentSubtuneIndex = currentSubtuneIndex
        )
    }
}

private fun resolveFavoriteEntryForPlayback(
    playlistLibraryState: PlaylistLibraryState,
    activeSourceId: String?,
    currentSubtuneIndex: Int
): PlaylistTrackEntry? {
    if (activeSourceId.isNullOrBlank()) return null
    return playlistLibraryState.favorites.firstOrNull { entry ->
        samePath(entry.source, activeSourceId) && entry.subtuneIndex == currentSubtuneIndex
    } ?: playlistLibraryState.favorites.firstOrNull { entry ->
        samePath(entry.source, activeSourceId) && entry.subtuneIndex == null
    }
}

private fun sanitizePlaylistTrackRequestUrlHint(
    source: String,
    requestUrlHint: String?
): String? {
    val normalizedHint = requestUrlHint?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    return normalizedHint.takeUnless { it == source.trim() }
}

private fun buildFavoriteEntryForSource(
    context: Context,
    sourceId: String,
    requestUrlHint: String?,
    fallbackFile: File?,
    metadataTitle: String,
    metadataArtist: String,
    metadataAlbum: String,
    durationSecondsOverride: Double?,
    subtuneCount: Int,
    currentSubtuneIndex: Int
): PlaylistTrackEntry {
    val derivedTitle = metadataTitle.trim().ifBlank {
        fallbackFile
            ?.nameWithoutExtension
            ?.takeIf { it.isNotBlank() }
            ?: sourceId.substringAfterLast('/').ifBlank { "Track" }
    }
    return PlaylistTrackEntry(
        source = sourceId,
        requestUrlHint = sanitizePlaylistTrackRequestUrlHint(sourceId, requestUrlHint),
        title = derivedTitle,
        artist = metadataArtist.trim().takeIf { it.isNotBlank() },
        album = metadataAlbum.trim().takeIf { it.isNotBlank() },
        artworkThumbnailCacheKey = ensureRecentArtworkThumbnailCached(
            context = context,
            sourceId = sourceId,
            requestUrlHint = requestUrlHint
        ),
        durationSecondsOverride = durationSecondsOverride
            ?.takeIf { it.isFinite() && it > 0.0 },
        subtuneIndex = if (subtuneCount > 1) currentSubtuneIndex else null
    )
}

private fun toggleCurrentTrackFavorite(
    context: Context,
    playlistLibraryState: PlaylistLibraryState,
    currentPlaybackSourceId: String?,
    currentPlaybackRequestUrl: String?,
    selectedFile: File?,
    metadataTitle: String,
    metadataArtist: String,
    metadataAlbum: String,
    durationSecondsOverride: Double?,
    subtuneCount: Int,
    currentSubtuneIndex: Int,
    onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit
) {
    val sourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath
    if (sourceId.isNullOrBlank()) return
    val existingFavorite = resolveFavoriteEntryForPlayback(
        playlistLibraryState = playlistLibraryState,
        activeSourceId = sourceId,
        currentSubtuneIndex = currentSubtuneIndex
    )
    if (existingFavorite != null) {
        onPlaylistLibraryStateChanged(removeFavoriteTrack(playlistLibraryState, existingFavorite.id))
        Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
        return
    }
    onPlaylistLibraryStateChanged(
        upsertFavoriteTrack(
            state = playlistLibraryState,
            track = buildFavoriteEntryForSource(
                context = context,
                sourceId = sourceId,
                requestUrlHint = currentPlaybackRequestUrl,
                fallbackFile = selectedFile,
                metadataTitle = metadataTitle,
                metadataArtist = metadataArtist,
                metadataAlbum = metadataAlbum,
                durationSecondsOverride = durationSecondsOverride,
                subtuneCount = subtuneCount,
                currentSubtuneIndex = currentSubtuneIndex
            )
        )
    )
    Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
}

internal fun toggleFavoriteForFile(
    context: Context,
    playlistLibraryState: PlaylistLibraryState,
    file: File,
    onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit
) {
    val matchingFileLevelFavorites = playlistLibraryState.favorites.filter { entry ->
        samePath(entry.source, file.absolutePath) && entry.subtuneIndex == null
    }
    if (matchingFileLevelFavorites.isNotEmpty()) {
        onPlaylistLibraryStateChanged(
            playlistLibraryState.copy(
                favorites = playlistLibraryState.favorites.filterNot { entry ->
                    matchingFileLevelFavorites.any { existing -> existing.id == entry.id }
                }
            )
        )
        Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
        return
    }
    onPlaylistLibraryStateChanged(
        upsertFavoriteTrack(
            state = playlistLibraryState,
            track = PlaylistTrackEntry(
                source = file.absolutePath,
                title = inferredDisplayTitleForName(file.name),
                artist = null,
                album = null,
                artworkThumbnailCacheKey = ensureRecentArtworkThumbnailCached(
                    context = context,
                    sourceId = file.absolutePath
                ),
                subtuneIndex = null
            )
        )
    )
    Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
}

private fun mergeFavoritePlaybackMetadata(
    playlistLibraryState: PlaylistLibraryState,
    favoriteId: String,
    title: String,
    artist: String?,
    album: String?,
    artworkThumbnailCacheKey: String?,
    durationSecondsOverride: Double?,
    requestUrlHint: String?
): PlaylistLibraryState {
    val normalizedTitle = title.trim()
    val normalizedArtist = artist?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedAlbum = album?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedArtworkKey = artworkThumbnailCacheKey?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedDurationOverride = durationSecondsOverride?.takeIf { it.isFinite() && it > 0.0 }
    var changed = false
    val updatedFavorites = playlistLibraryState.favorites.map { entry ->
        if (entry.id != favoriteId) return@map entry
        val updatedEntry = entry.copy(
            title = normalizedTitle.ifBlank { entry.title },
            artist = normalizedArtist ?: entry.artist,
            album = normalizedAlbum ?: entry.album,
            artworkThumbnailCacheKey = normalizedArtworkKey ?: entry.artworkThumbnailCacheKey,
            durationSecondsOverride = normalizedDurationOverride ?: entry.durationSecondsOverride,
            requestUrlHint = sanitizePlaylistTrackRequestUrlHint(
                source = entry.source,
                requestUrlHint = requestUrlHint
            ) ?: entry.requestUrlHint
        )
        if (updatedEntry != entry) {
            changed = true
        }
        updatedEntry
    }
    return if (changed) {
        playlistLibraryState.copy(favorites = updatedFavorites)
    } else {
        playlistLibraryState
    }
}

@Composable
private fun AppNavigationPlaylistEffects(
    pendingFileToOpen: File?,
    pendingFileFromExternalIntent: Boolean,
    currentPlaybackSourceId: String?,
    selectedFile: File?,
    currentSubtuneIndex: Int,
    activePlaylist: StoredPlaylist?,
    activePlaylistEntryId: String?,
    pendingPlaylistSubtuneSelection: PendingPlaylistSubtuneSelection?,
    subtuneCount: Int,
    onPendingFileToOpenChanged: (File?) -> Unit,
    onPendingFileFromExternalIntentChanged: (Boolean) -> Unit,
    onActivePlaylistEntryIdChanged: (String?) -> Unit,
    onPendingPlaylistSubtuneSelectionChanged: (PendingPlaylistSubtuneSelection?) -> Unit,
    onHandlePlaylistFileSelection: (File, String?) -> Unit,
    onSelectSubtune: (Int) -> Unit
) {
    LaunchedEffect(pendingFileToOpen?.absolutePath, pendingFileFromExternalIntent) {
        val pendingFile = pendingFileToOpen ?: return@LaunchedEffect
        if (!isSupportedPlaylistFile(pendingFile)) return@LaunchedEffect
        onHandlePlaylistFileSelection(
            pendingFile,
            pendingFile.absolutePath
        )
        onPendingFileToOpenChanged(null)
        onPendingFileFromExternalIntentChanged(false)
    }

    LaunchedEffect(
        currentPlaybackSourceId,
        selectedFile?.absolutePath,
        currentSubtuneIndex,
        pendingPlaylistSubtuneSelection?.sourceId,
        pendingPlaylistSubtuneSelection?.subtuneIndex,
        activePlaylist?.id,
        activePlaylist?.sourceIdHint
    ) {
        val pendingEntry = resolvePendingPlaylistEntry(
            activePlaylist = activePlaylist,
            pendingPlaylistSubtuneSelection = pendingPlaylistSubtuneSelection
        )
        if (pendingEntry != null) {
            if (pendingEntry.id != activePlaylistEntryId) {
                onActivePlaylistEntryIdChanged(pendingEntry.id)
            }
            return@LaunchedEffect
        }
        val activeSourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath ?: return@LaunchedEffect
        val playbackMatchedEntry = activePlaylist
            ?.entries
            ?.firstOrNull { entry ->
                playlistEntryMatchesPlayback(
                    entry = entry,
                    activeSourceId = activeSourceId,
                    currentSubtuneIndex = currentSubtuneIndex
                )
            }
        if (playbackMatchedEntry != null && playbackMatchedEntry.id != activePlaylistEntryId) {
            onActivePlaylistEntryIdChanged(playbackMatchedEntry.id)
        }
    }

    LaunchedEffect(
        pendingPlaylistSubtuneSelection?.sourceId,
        pendingPlaylistSubtuneSelection?.subtuneIndex,
        currentPlaybackSourceId,
        selectedFile?.absolutePath,
        subtuneCount
    ) {
        val pendingSelection = pendingPlaylistSubtuneSelection ?: return@LaunchedEffect
        val activeSourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath ?: return@LaunchedEffect
        if (!samePath(pendingSelection.sourceId, activeSourceId)) return@LaunchedEffect
        if (subtuneCount <= 0) return@LaunchedEffect
        if (pendingSelection.subtuneIndex !in 0 until subtuneCount) {
            onPendingPlaylistSubtuneSelectionChanged(null)
            return@LaunchedEffect
        }
        if (currentSubtuneIndex != pendingSelection.subtuneIndex) {
            onSelectSubtune(pendingSelection.subtuneIndex)
        }
        onPendingPlaylistSubtuneSelectionChanged(null)
    }
}

@Composable
private fun AppNavigationBackHandlers(
    context: Context,
    currentView: MainView,
    isPlayerExpanded: Boolean,
    isPlayerSurfaceVisible: Boolean,
    settingsLaunchedFromPlayer: Boolean,
    showUrlOrPathDialog: Boolean,
    showMiniPlayerFocusHighlight: Boolean,
    onRestoreMiniPlayerFocusOnCollapseChanged: (Boolean) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    onCollapseDragInProgressChanged: (Boolean) -> Unit,
    onExpandedOverlaySettledVisibleChanged: (Boolean) -> Unit,
    popSettingsRoute: () -> Boolean,
    exitSettingsToReturnView: () -> Unit,
    onCurrentViewChanged: (MainView) -> Unit
) {
    BackHandler(enabled = isPlayerExpanded || currentView == MainView.Settings) {
        handleAppNavigationBackAction(
            isPlayerExpanded = isPlayerExpanded,
            currentView = currentView,
            settingsLaunchedFromPlayer = settingsLaunchedFromPlayer,
            onPlayerExpandedChanged = { expanded ->
                if (
                    isPlayerExpanded &&
                    !expanded &&
                    isPlayerSurfaceVisible &&
                    showMiniPlayerFocusHighlight
                ) {
                    onRestoreMiniPlayerFocusOnCollapseChanged(true)
                }
                onPlayerExpandedChanged(expanded)
                if (!expanded) {
                    onCollapseDragInProgressChanged(false)
                    onExpandedOverlaySettledVisibleChanged(false)
                }
            },
            popSettingsRoute = popSettingsRoute,
            exitSettingsToReturnView = exitSettingsToReturnView
        )
    }
    BackHandler(enabled = !isPlayerExpanded && currentView == MainView.Playlists) {
        onCurrentViewChanged(MainView.Home)
    }
    HomeExitBackHandler(
        context = context,
        currentView = currentView,
        isPlayerExpanded = isPlayerExpanded,
        showUrlOrPathDialog = showUrlOrPathDialog
    )
}

private fun openAudioEffectsDialogFromSettings(
    masterVolumeDb: Float,
    pluginVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    forceMono: Boolean,
    onTempMasterVolumeDbChanged: (Float) -> Unit,
    onTempPluginVolumeDbChanged: (Float) -> Unit,
    onTempSongVolumeDbChanged: (Float) -> Unit,
    onTempIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit,
    onTempForceMonoChanged: (Boolean) -> Unit,
    onShowAudioEffectsDialogChanged: (Boolean) -> Unit
) {
    onTempMasterVolumeDbChanged(masterVolumeDb)
    onTempPluginVolumeDbChanged(pluginVolumeDb)
    onTempSongVolumeDbChanged(songVolumeDb)
    onTempIgnoreCoreVolumeForSongChanged(ignoreCoreVolumeForSong)
    onTempForceMonoChanged(forceMono)
    onShowAudioEffectsDialogChanged(true)
}

private fun updateAudioBackendPreferenceSelection(
    prefs: android.content.SharedPreferences,
    selectedBackend: AudioBackendPreference,
    currentBackend: AudioBackendPreference,
    currentPerformanceMode: AudioPerformanceMode,
    currentBufferPreset: AudioBufferPreset,
    onAudioBackendPreferenceChanged: (AudioBackendPreference) -> Unit,
    onAudioPerformanceModeChanged: (AudioPerformanceMode) -> Unit,
    onAudioBufferPresetChanged: (AudioBufferPreset) -> Unit
) {
    if (selectedBackend == currentBackend) return

    prefs.edit()
        .putString(
            AppPreferenceKeys.audioPerformanceModeForBackend(currentBackend),
            currentPerformanceMode.storageValue
        )
        .putString(
            AppPreferenceKeys.audioBufferPresetForBackend(currentBackend),
            currentBufferPreset.storageValue
        )
        .apply()

    val performanceModeKey = AppPreferenceKeys.audioPerformanceModeForBackend(selectedBackend)
    val restoredPerformanceValue = when {
        prefs.contains(performanceModeKey) -> {
            prefs.getString(
                performanceModeKey,
                selectedBackend.defaultPerformanceMode().storageValue
            )
        }
        selectedBackend == AudioBackendPreference.AAudio -> {
            prefs.getString(
                AppPreferenceKeys.AUDIO_PERFORMANCE_MODE,
                selectedBackend.defaultPerformanceMode().storageValue
            )
        }
        else -> selectedBackend.defaultPerformanceMode().storageValue
    }
    val restoredPerformanceMode = AudioPerformanceMode.fromStorage(restoredPerformanceValue)

    val bufferPresetKey = AppPreferenceKeys.audioBufferPresetForBackend(selectedBackend)
    val restoredBufferValue = when {
        prefs.contains(bufferPresetKey) -> {
            prefs.getString(
                bufferPresetKey,
                selectedBackend.defaultBufferPreset().storageValue
            )
        }
        selectedBackend == AudioBackendPreference.AAudio -> {
            prefs.getString(
                AppPreferenceKeys.AUDIO_BUFFER_PRESET,
                selectedBackend.defaultBufferPreset().storageValue
            )
        }
        else -> selectedBackend.defaultBufferPreset().storageValue
    }
    val restoredBufferPreset = AudioBufferPreset.fromStorage(restoredBufferValue)

    onAudioBackendPreferenceChanged(selectedBackend)
    onAudioPerformanceModeChanged(restoredPerformanceMode)
    onAudioBufferPresetChanged(restoredBufferPreset)
}

private fun clearSavedNetworkSourcesFromSettings(
    context: Context,
    prefs: android.content.SharedPreferences,
    onNetworkNodesChanged: (List<NetworkNode>) -> Unit
) {
    onNetworkNodesChanged(emptyList())
    writeNetworkNodes(prefs, emptyList())
    Toast.makeText(context, "Saved network sources cleared", Toast.LENGTH_SHORT).show()
}

private fun clearAllSettingsAndUiState(
    context: Context,
    prefs: android.content.SharedPreferences,
    defaultScopeTextSizeSp: Int,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onUseMonetChanged: (Boolean) -> Unit,
    settingsStates: AppNavigationSettingsStates,
    onAutoPlayOnTrackSelectChanged: (Boolean) -> Unit,
    onOpenPlayerOnTrackSelectChanged: (Boolean) -> Unit,
    onAutoPlayNextTrackOnEndChanged: (Boolean) -> Unit,
    onPreloadNextCachedRemoteTrackChanged: (Boolean) -> Unit,
    onPlaylistWrapNavigationChanged: (Boolean) -> Unit,
    onPreviousRestartsAfterThresholdChanged: (Boolean) -> Unit,
    onFadePauseResumeChanged: (Boolean) -> Unit,
    onPersistRepeatModeChanged: (Boolean) -> Unit,
    onPreferredRepeatModeChanged: (RepeatMode) -> Unit,
    onRememberBrowserLocationChanged: (Boolean) -> Unit,
    onShowParentDirectoryEntryChanged: (Boolean) -> Unit,
    onShowFileIconChipBackgroundChanged: (Boolean) -> Unit,
    onBrowserNameSortModeChanged: (BrowserNameSortMode) -> Unit,
    onLastBrowserLocationIdChanged: (String?) -> Unit,
    onLastBrowserDirectoryPathChanged: (String?) -> Unit,
    onRecentFoldersLimitChanged: (Int) -> Unit,
    onRecentFilesLimitChanged: (Int) -> Unit,
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit,
    onRecentPlayedFilesChanged: (List<RecentPathEntry>) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onPlayerArtworkCornerRadiusDpChanged: (Int) -> Unit,
    onFilenameDisplayModeChanged: (FilenameDisplayMode) -> Unit,
    onFilenameOnlyWhenTitleMissingChanged: (Boolean) -> Unit,
    onUnknownTrackDurationSecondsChanged: (Int) -> Unit,
    onEndFadeApplyToAllTracksChanged: (Boolean) -> Unit,
    onEndFadeDurationMsChanged: (Int) -> Unit,
    onEndFadeCurveChanged: (EndFadeCurve) -> Unit,
    onVisualizationModeChanged: (VisualizationMode) -> Unit,
    onEnabledVisualizationModesChanged: (Set<VisualizationMode>) -> Unit,
    onVisualizationShowDebugInfoChanged: (Boolean) -> Unit,
    onVisualizationBarCountChanged: (Int) -> Unit,
    onVisualizationBarSmoothingPercentChanged: (Int) -> Unit,
    onVisualizationBarRoundnessDpChanged: (Int) -> Unit,
    onVisualizationBarOverlayArtworkChanged: (Boolean) -> Unit,
    onVisualizationBarUseThemeColorChanged: (Boolean) -> Unit,
    onVisualizationBarRenderBackendChanged: (VisualizationRenderBackend) -> Unit,
    onVisualizationOscStereoChanged: (Boolean) -> Unit,
    onVisualizationVuAnchorChanged: (VisualizationVuAnchor) -> Unit,
    onVisualizationVuUseThemeColorChanged: (Boolean) -> Unit,
    onVisualizationVuSmoothingPercentChanged: (Int) -> Unit,
    onVisualizationVuRenderBackendChanged: (VisualizationRenderBackend) -> Unit,
    onNetworkNodesChanged: (List<NetworkNode>) -> Unit,
    onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit,
    onActivePlaylistChanged: (StoredPlaylist?) -> Unit,
    onActivePlaylistEntryIdChanged: (String?) -> Unit,
    onShowPlaylistSelectorDialogChanged: (Boolean) -> Unit
) {
    clearAllSettingsUsingStateHolders(
        context = context,
        prefs = prefs,
        defaultScopeTextSizeSp = defaultScopeTextSizeSp,
        selectableVisualizationModes = selectableVisualizationModes,
        onThemeModeChanged = onThemeModeChanged,
        onUseMonetChanged = onUseMonetChanged,
        settingsStates = settingsStates,
        onAutoPlayOnTrackSelectChanged = onAutoPlayOnTrackSelectChanged,
        onOpenPlayerOnTrackSelectChanged = onOpenPlayerOnTrackSelectChanged,
        onAutoPlayNextTrackOnEndChanged = onAutoPlayNextTrackOnEndChanged,
        onPreloadNextCachedRemoteTrackChanged = onPreloadNextCachedRemoteTrackChanged,
        onPlaylistWrapNavigationChanged = onPlaylistWrapNavigationChanged,
        onPreviousRestartsAfterThresholdChanged = onPreviousRestartsAfterThresholdChanged,
        onFadePauseResumeChanged = onFadePauseResumeChanged,
        onPersistRepeatModeChanged = onPersistRepeatModeChanged,
        onPreferredRepeatModeChanged = onPreferredRepeatModeChanged,
        onRememberBrowserLocationChanged = onRememberBrowserLocationChanged,
        onShowParentDirectoryEntryChanged = onShowParentDirectoryEntryChanged,
        onShowFileIconChipBackgroundChanged = onShowFileIconChipBackgroundChanged,
        onBrowserNameSortModeChanged = onBrowserNameSortModeChanged,
        onLastBrowserLocationIdChanged = onLastBrowserLocationIdChanged,
        onLastBrowserDirectoryPathChanged = onLastBrowserDirectoryPathChanged,
        onRecentFoldersLimitChanged = onRecentFoldersLimitChanged,
        onRecentFilesLimitChanged = onRecentFilesLimitChanged,
        onRecentFoldersChanged = onRecentFoldersChanged,
        onRecentPlayedFilesChanged = onRecentPlayedFilesChanged,
        onKeepScreenOnChanged = onKeepScreenOnChanged,
        onPlayerArtworkCornerRadiusDpChanged = onPlayerArtworkCornerRadiusDpChanged,
        onFilenameDisplayModeChanged = onFilenameDisplayModeChanged,
        onFilenameOnlyWhenTitleMissingChanged = onFilenameOnlyWhenTitleMissingChanged,
        onUnknownTrackDurationSecondsChanged = onUnknownTrackDurationSecondsChanged,
        onEndFadeApplyToAllTracksChanged = onEndFadeApplyToAllTracksChanged,
        onEndFadeDurationMsChanged = onEndFadeDurationMsChanged,
        onEndFadeCurveChanged = onEndFadeCurveChanged,
        onVisualizationModeChanged = onVisualizationModeChanged,
        onEnabledVisualizationModesChanged = onEnabledVisualizationModesChanged,
        onVisualizationShowDebugInfoChanged = onVisualizationShowDebugInfoChanged,
        onVisualizationBarCountChanged = onVisualizationBarCountChanged,
        onVisualizationBarSmoothingPercentChanged = onVisualizationBarSmoothingPercentChanged,
        onVisualizationBarRoundnessDpChanged = onVisualizationBarRoundnessDpChanged,
        onVisualizationBarOverlayArtworkChanged = onVisualizationBarOverlayArtworkChanged,
        onVisualizationBarUseThemeColorChanged = onVisualizationBarUseThemeColorChanged,
        onVisualizationBarRenderBackendChanged = onVisualizationBarRenderBackendChanged,
        onVisualizationOscStereoChanged = onVisualizationOscStereoChanged,
        onVisualizationVuAnchorChanged = onVisualizationVuAnchorChanged,
        onVisualizationVuUseThemeColorChanged = onVisualizationVuUseThemeColorChanged,
        onVisualizationVuSmoothingPercentChanged = onVisualizationVuSmoothingPercentChanged,
        onVisualizationVuRenderBackendChanged = onVisualizationVuRenderBackendChanged
    )
    onNetworkNodesChanged(emptyList())
    writeNetworkNodes(prefs, emptyList())
    onPlaylistLibraryStateChanged(emptyPlaylistLibraryState())
    onActivePlaylistChanged(null)
    onActivePlaylistEntryIdChanged(null)
    onShowPlaylistSelectorDialogChanged(false)
}

@Composable
private fun HomeExitBackHandler(
    context: Context,
    currentView: MainView,
    isPlayerExpanded: Boolean,
    showUrlOrPathDialog: Boolean
) {
    val hostActivity = context as? Activity
    var lastHomeBackPressedAtMs by remember { mutableLongStateOf(0L) }

    BackHandler(
        enabled = currentView == MainView.Home &&
            !isPlayerExpanded &&
            !showUrlOrPathDialog
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHomeBackPressedAtMs <= HOME_BACK_EXIT_TIMEOUT_MS) {
            hostActivity?.finish()
        } else {
            lastHomeBackPressedAtMs = now
            Toast.makeText(context, "Press back again to exit app", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(currentView) {
        if (currentView != MainView.Home) {
            lastHomeBackPressedAtMs = 0L
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun AppNavigation(
    themeMode: ThemeMode,
    useMonet: Boolean,
    monetAvailable: Boolean,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onUseMonetChanged: (Boolean) -> Unit,
    initialFileToOpen: File?,
    initialFileFromExternalIntent: Boolean
) {
    val seekUiBusyThresholdMs = 500L
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val defaultScopeTextSizeSp = remember(context) { defaultChannelScopeTextSizeSp(context) }
    val volumeDatabase = remember(context) {
        VolumeDatabase.getInstance(context)
    }

    // Audio effects state
    var masterVolumeDb by remember { mutableFloatStateOf(0f) }
    var pluginVolumeDb by remember { mutableFloatStateOf(0f) }
    var songVolumeDb by remember { mutableFloatStateOf(0f) }
    var ignoreCoreVolumeForSong by remember { mutableStateOf(false) }
    var forceMono by remember { mutableStateOf(false) }
    var showAudioEffectsDialog by remember { mutableStateOf(false) }

    // Temporary state for dialog (to support Cancel)
    var tempMasterVolumeDb by remember { mutableFloatStateOf(0f) }
    var tempPluginVolumeDb by remember { mutableFloatStateOf(0f) }
    var tempSongVolumeDb by remember { mutableFloatStateOf(0f) }
    var tempIgnoreCoreVolumeForSong by remember { mutableStateOf(false) }
    var tempForceMono by remember { mutableStateOf(false) }

    var currentView by remember { mutableStateOf(MainView.Home) }
    val focusManager = LocalFocusManager.current
    var browserFocusRestoreRequestToken by remember { mutableIntStateOf(0) }
    var showMiniPlayerFocusHighlight by remember { mutableStateOf(false) }
    val miniPlayerFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val mainContentFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.Root) }
    var settingsRouteHistory by remember { mutableStateOf<List<SettingsRoute>>(emptyList()) }
    var selectedPluginName by remember { mutableStateOf<String?>(null) }
    var settingsLaunchedFromPlayer by remember { mutableStateOf(false) }
    var settingsReturnView by remember { mutableStateOf(MainView.Home) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var lastStoppedFile by remember { mutableStateOf<File?>(null) }
    var lastStoppedSourceId by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableDoubleStateOf(0.0) }
    // Pass to overlays as a stable State reference so AppNavigation's body
    // does not subscribe to the playback poll.
    val positionState = remember { mutableDoubleStateOf(0.0) }
    var position by positionState
    var deferredPlaybackSeek by remember { mutableStateOf<DeferredPlaybackSeek?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var seekInProgress by remember { mutableStateOf(false) }
    var seekUiBusy by remember { mutableStateOf(false) }
    var playbackStartInProgress by remember { mutableStateOf(false) }
    var seekStartedAtMs by remember { mutableLongStateOf(0L) }
    var seekRequestedAtMs by remember { mutableLongStateOf(0L) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    val playerTransition = remember { PlayerTransitionStateHolder() }
    playerTransition.DriveTapTransition(isPlayerExpanded = isPlayerExpanded)
    var isPlayerSurfaceVisible by remember { mutableStateOf(false) }
    var restoreMiniPlayerFocusOnCollapse by remember { mutableStateOf(false) }
    var preferredRepeatMode by remember {
        mutableStateOf(
            RepeatMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.PREFERRED_REPEAT_MODE,
                    prefs.getString(
                        AppPreferenceKeys.SESSION_CURRENT_REPEAT_MODE,
                        RepeatMode.None.storageValue
                    )
                )
            )
        )
    }
    var persistRepeatMode by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.PERSIST_REPEAT_MODE, true)
        )
    }
    var activeRepeatMode by remember { mutableStateOf(RepeatMode.None) }
    var metadataTitle by remember { mutableStateOf("") }
    var metadataArtist by remember { mutableStateOf("") }
    var metadataSampleRate by remember { mutableIntStateOf(0) }
    var metadataChannelCount by remember { mutableIntStateOf(0) }
    var metadataBitDepthLabel by remember { mutableStateOf("Unknown") }
    var subtuneCount by remember { mutableIntStateOf(0) }
    var currentSubtuneIndex by remember { mutableIntStateOf(0) }
    var subtuneEntries by remember { mutableStateOf<List<SubtuneEntry>>(emptyList()) }
    var showSubtuneSelectorDialog by remember { mutableStateOf(false) }
    var showPlaylistSelectorDialog by remember { mutableStateOf(false) }
    var showPlaylistOpenActionDialog by remember { mutableStateOf(false) }
    var showPlaylistPreviewDialog by remember { mutableStateOf(false) }
    var repeatModeCapabilitiesFlags by remember { mutableIntStateOf(REPEAT_CAP_ALL) }
    var playbackCapabilitiesFlags by remember {
        mutableIntStateOf(
            PLAYBACK_CAP_SEEK or
                PLAYBACK_CAP_RELIABLE_DURATION or
                PLAYBACK_CAP_LIVE_REPEAT_MODE
        )
    }
    var lastUsedCoreName by remember { mutableStateOf<String?>(null) }
    var artworkBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var artworkResolvedTrackKey by remember { mutableStateOf<String?>(null) }
    var artworkReloadToken by remember { mutableIntStateOf(0) }
    var visiblePlayableFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    val browserNavigator = remember { BrowserNavigatorState() }
    var networkCurrentFolderId by remember { mutableStateOf<Long?>(null) }
    val storageDescriptors = remember(context) { detectStorageDescriptors(context) }
    val appScope = rememberCoroutineScope()

    var ffmpegCapabilities by remember { mutableIntStateOf(0) }
    var openMptCapabilities by remember { mutableIntStateOf(0) }
    var vgmPlayCapabilities by remember { mutableIntStateOf(0) }

    var recentFoldersLimit by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.RECENT_FOLDERS_LIMIT, RECENT_FOLDERS_LIMIT_DEFAULT)
                .coerceIn(1, RECENTS_LIMIT_MAX)
        )
    }
    var recentFilesLimit by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.RECENT_PLAYED_FILES_LIMIT, RECENT_FILES_LIMIT_DEFAULT)
                .coerceIn(1, RECENTS_LIMIT_MAX)
        )
    }
    var recentFolders by remember {
        mutableStateOf(readRecentEntries(prefs, AppPreferenceKeys.RECENT_FOLDERS, recentFoldersLimit))
    }
    var recentPlayedFiles by remember {
        mutableStateOf(readRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, recentFilesLimit))
    }
    var playlistLibraryState by remember {
        mutableStateOf(readPlaylistLibraryState(prefs))
    }
    var favoritesSortMode by remember {
        mutableStateOf(PlaylistEntrySortMode.Custom)
    }
    var externalTrackInfoDialogRequestToken by remember { mutableIntStateOf(0) }
    var activePlaylist by remember { mutableStateOf<StoredPlaylist?>(null) }
    var activePlaylistEntryId by remember { mutableStateOf<String?>(null) }
    var activePlaylistShuffleActive by remember { mutableStateOf(false) }
    var lastStoppedPlaylistResume by remember { mutableStateOf<LastStoppedPlaylistResume?>(null) }
    var pendingPlaylistSubtuneSelection by remember { mutableStateOf<PendingPlaylistSubtuneSelection?>(null) }
    var pendingBrowserPlaylistDocument by remember { mutableStateOf<ParsedPlaylistDocument?>(null) }
    var networkNodes by remember {
        mutableStateOf(readNetworkNodes(prefs))
    }
    val onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit = { updated ->
        playlistLibraryState = updated
        writePlaylistLibraryState(prefs, updated)
    }
    val applyNetworkSourceMetadata: (String, String?, String?) -> Unit = { sourceId, title, artist ->
        val updated = mergeNetworkSourceMetadata(
            nodes = networkNodes,
            sourceId = sourceId,
            title = title,
            artist = artist
        )
        if (updated != networkNodes) {
            networkNodes = updated
            writeNetworkNodes(prefs, updated)
        }
    }
    val rememberNetworkSmbCredentials: (Long?, String, String?, String?) -> Unit = { sourceNodeId, _, username, password ->
        sourceNodeId
            ?.let { id -> networkNodes.firstOrNull { it.id == id } }
            ?.let(::resolveNetworkNodeSmbSpec)
            ?.let { spec ->
                NetworkCredentialStore.remember(spec, username, password)
            }
    }
    val rememberNetworkHttpCredentials: (Long?, String, String?, String?) -> Unit = { sourceNodeId, _, username, password ->
        sourceNodeId
            ?.let { id -> networkNodes.firstOrNull { it.id == id } }
            ?.let(::resolveNetworkNodeHttpSpec)
            ?.let { spec ->
                NetworkCredentialStore.remember(spec, username, password)
            }
    }
    AppNavigationStartupEffects(
        prefs = prefs,
        defaultScopeTextSizeSp = defaultScopeTextSizeSp,
        recentFoldersLimit = recentFoldersLimit,
        recentFilesLimit = recentFilesLimit,
        recentFolders = recentFolders,
        recentPlayedFiles = recentPlayedFiles,
        onFfmpegCapabilitiesChanged = { ffmpegCapabilities = it },
        onOpenMptCapabilitiesChanged = { openMptCapabilities = it },
        onVgmPlayCapabilitiesChanged = { vgmPlayCapabilities = it },
        onMasterVolumeDbChanged = { masterVolumeDb = it },
        onPluginVolumeDbChanged = { pluginVolumeDb = it },
        onForceMonoChanged = { forceMono = it },
        onRecentFoldersLimitChanged = { recentFoldersLimit = it },
        onRecentFilesLimitChanged = { recentFilesLimit = it },
        onRecentFoldersChanged = { recentFolders = it },
        onRecentPlayedFilesChanged = { recentPlayedFiles = it }
    )
    var autoPlayOnTrackSelect by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUTO_PLAY_ON_TRACK_SELECT, true)
        )
    }
    var openPlayerOnTrackSelect by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.OPEN_PLAYER_ON_TRACK_SELECT, true)
        )
    }
    var autoPlayNextTrackOnEnd by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUTO_PLAY_NEXT_TRACK_ON_END, true)
        )
    }
    var preloadNextCachedRemoteTrack by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.PRELOAD_NEXT_CACHED_REMOTE_TRACK,
                AppDefaults.Player.preloadNextCachedRemoteTrack
            )
        )
    }
    var playlistWrapNavigation by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.PLAYLIST_WRAP_NAVIGATION, true)
        )
    }
    var previousRestartsAfterThreshold by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.PREVIOUS_RESTART_AFTER_THRESHOLD, true)
        )
    }
    var fadePauseResume by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.FADE_PAUSE_RESUME, AppDefaults.Player.fadePauseResume)
        )
    }
    val startEngineWithPauseResumeFade = remember(fadePauseResume) {
        {
            val currentPositionSeconds = NativeBridge.getPosition()
            val shouldFade = fadePauseResume && currentPositionSeconds > 0.05
            if (!shouldFade) {
                NativeBridge.startEngine()
            } else {
                NativeBridge.startEngineWithPauseResumeFade()
            }
        }
    }
    val pauseEngineWithPauseResumeFade = remember(fadePauseResume) {
        { onPaused: () -> Unit ->
            val currentPositionSeconds = NativeBridge.getPosition()
            val shouldFade = fadePauseResume && currentPositionSeconds > 0.05
            if (!shouldFade) {
                NativeBridge.stopEngine()
                onPaused()
            } else {
                NativeBridge.stopEngineWithPauseResumeFade()
                // Keep UI responsive and update state immediately; native fade completes asynchronously.
                onPaused()
            }
        }
    }
    var rememberBrowserLocation by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, true)
        )
    }
    var showParentDirectoryEntry by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.BROWSER_SHOW_PARENT_DIRECTORY_ENTRY,
                AppDefaults.Browser.showParentDirectoryEntry
            )
        )
    }
    var showFileIconChipBackground by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.BROWSER_SHOW_FILE_ICON_CHIP_BACKGROUND,
                AppDefaults.Browser.showFileIconChipBackground
            )
        )
    }
    var sortArchivesBeforeFiles by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.BROWSER_SORT_ARCHIVES_BEFORE_FILES, false)
        )
    }
    var browserNameSortMode by remember {
        mutableStateOf(
            BrowserNameSortMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.BROWSER_NAME_SORT_MODE,
                    AppDefaults.Browser.nameSortMode.storageValue
                )
            )
        )
    }
    val persistedRememberedBrowserState = remember {
        readRememberedBrowserLaunchState(prefs)
    }
    var lastBrowserLocationId by remember {
        mutableStateOf(persistedRememberedBrowserState.locationId)
    }
    var lastBrowserDirectoryPath by remember {
        mutableStateOf(persistedRememberedBrowserState.directoryPath)
    }
    var keepScreenOn by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.KEEP_SCREEN_ON, AppDefaults.Player.keepScreenOn)
        )
    }
    var playerArtworkCornerRadiusDp by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.PLAYER_ARTWORK_CORNER_RADIUS_DP,
                AppDefaults.Player.artworkCornerRadiusDp
            ).coerceIn(0, 48)
        )
    }
    var audioFocusInterrupt by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_FOCUS_INTERRUPT, true)
        )
    }
    var audioDucking by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_DUCKING, true)
        )
    }
    var filenameDisplayMode by remember {
        mutableStateOf(
            FilenameDisplayMode.fromStorage(
                prefs.getString(AppPreferenceKeys.FILENAME_DISPLAY_MODE, FilenameDisplayMode.Always.storageValue)
            )
        )
    }
    var filenameOnlyWhenTitleMissing by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.FILENAME_ONLY_WHEN_TITLE_MISSING, false)
        )
    }
    var unknownTrackDurationSeconds by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS,
                GmeDefaults.unknownDurationSeconds
            ).coerceIn(1, 86400)
        )
    }
    var endFadeApplyToAllTracks by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.END_FADE_APPLY_TO_ALL_TRACKS,
                AppDefaults.Player.endFadeApplyToAllTracks
            )
        )
    }
    var endFadeDurationMs by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.END_FADE_DURATION_MS,
                AppDefaults.Player.endFadeDurationMs
            ).coerceIn(100, 120000)
        )
    }
    var endFadeCurve by remember {
        mutableStateOf(
            EndFadeCurve.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.END_FADE_CURVE,
                    AppDefaults.Player.endFadeCurve.storageValue
                )
            )
        )
    }
    var visualizationShowDebugInfo by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_SHOW_DEBUG_INFO,
                AppDefaults.Visualization.showDebugInfo
            )
        )
    }
    var visualizationBarCount by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.VISUALIZATION_BAR_COUNT,
                AppDefaults.Visualization.Bars.count
            ).coerceIn(AppDefaults.Visualization.Bars.countRange.first, AppDefaults.Visualization.Bars.countRange.last)
        )
    }
    var visualizationBarSmoothingPercent by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.VISUALIZATION_BAR_SMOOTHING_PERCENT,
                AppDefaults.Visualization.Bars.smoothingPercent
            ).coerceIn(
                AppDefaults.Visualization.Bars.smoothingRange.first,
                AppDefaults.Visualization.Bars.smoothingRange.last
            )
        )
    }
    var visualizationBarRoundnessDp by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.VISUALIZATION_BAR_ROUNDNESS_DP,
                AppDefaults.Visualization.Bars.roundnessDp
            ).coerceIn(
                AppDefaults.Visualization.Bars.roundnessRange.first,
                AppDefaults.Visualization.Bars.roundnessRange.last
            )
        )
    }
    var visualizationBarOverlayArtwork by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_BAR_OVERLAY_ARTWORK,
                AppDefaults.Visualization.Bars.overlayArtwork
            )
        )
    }
    var visualizationBarUseThemeColor by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_BAR_USE_THEME_COLOR,
                AppDefaults.Visualization.Bars.useThemeColor
            )
        )
    }
    var visualizationBarRenderBackend by remember {
        mutableStateOf(
            VisualizationRenderBackend.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_BAR_RENDER_BACKEND,
                    AppDefaults.Visualization.Bars.renderBackend.storageValue
                ),
                AppDefaults.Visualization.Bars.renderBackend
            )
        )
    }
    var visualizationOscStereo by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_OSC_STEREO,
                AppDefaults.Visualization.Oscilloscope.stereo
            )
        )
    }
    var visualizationVuAnchor by remember {
        mutableStateOf(
            VisualizationVuAnchor.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_VU_ANCHOR,
                    AppDefaults.Visualization.Vu.anchor.storageValue
                )
            )
        )
    }
    var visualizationVuUseThemeColor by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_VU_USE_THEME_COLOR,
                AppDefaults.Visualization.Vu.useThemeColor
            )
        )
    }
    var visualizationVuSmoothingPercent by remember {
        mutableIntStateOf(
            prefs.getInt(
                AppPreferenceKeys.VISUALIZATION_VU_SMOOTHING_PERCENT,
                AppDefaults.Visualization.Vu.smoothingPercent
            ).coerceIn(
                AppDefaults.Visualization.Vu.smoothingRange.first,
                AppDefaults.Visualization.Vu.smoothingRange.last
            )
        )
    }
    var visualizationVuRenderBackend by remember {
        mutableStateOf(
            VisualizationRenderBackend.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_VU_RENDER_BACKEND,
                    AppDefaults.Visualization.Vu.renderBackend.storageValue
                ),
                AppDefaults.Visualization.Vu.renderBackend
            )
        )
    }

    val settingsStates = rememberAppNavigationSettingsStates(prefs)
    var respondHeadphoneMediaButtons by settingsStates.respondHeadphoneMediaButtons
    var pauseOnHeadphoneDisconnect by settingsStates.pauseOnHeadphoneDisconnect
    var audioBackendPreference by settingsStates.audioBackendPreference
    var audioPerformanceMode by settingsStates.audioPerformanceMode
    var audioBufferPreset by settingsStates.audioBufferPreset
    var audioResamplerPreference by settingsStates.audioResamplerPreference
    var audioOutputLimiterEnabled by settingsStates.audioOutputLimiterEnabled
    var pendingSoxExperimentalDialog by settingsStates.pendingSoxExperimentalDialog
    var showSoxExperimentalDialog by settingsStates.showSoxExperimentalDialog
    var showUrlOrPathDialog by settingsStates.showUrlOrPathDialog
    var urlOrPathInput by settingsStates.urlOrPathInput
    var remoteLoadUiState by settingsStates.remoteLoadUiState
    var remoteLoadJob by settingsStates.remoteLoadJob
    var urlOrPathForceCaching by settingsStates.urlOrPathForceCaching
    var urlCacheClearOnLaunch by settingsStates.urlCacheClearOnLaunch
    var urlCacheMaxTracks by settingsStates.urlCacheMaxTracks
    var urlCacheMaxBytes by settingsStates.urlCacheMaxBytes
    var archiveCacheClearOnLaunch by settingsStates.archiveCacheClearOnLaunch
    var archiveCacheMaxMounts by settingsStates.archiveCacheMaxMounts
    var archiveCacheMaxBytes by settingsStates.archiveCacheMaxBytes
    var archiveCacheMaxAgeDays by settingsStates.archiveCacheMaxAgeDays
    var cachedSourceFiles by settingsStates.cachedSourceFiles
    var pendingCacheExportPaths by settingsStates.pendingCacheExportPaths
    var audioAllowBackendFallback by settingsStates.audioAllowBackendFallback
    var openPlayerFromNotification by settingsStates.openPlayerFromNotification
    var playbackWatchPath by settingsStates.playbackWatchPath
    var currentPlaybackSourceId by settingsStates.currentPlaybackSourceId
    var currentPlaybackRequestUrl by remember {
        mutableStateOf(prefs.getString(AppPreferenceKeys.SESSION_CURRENT_REQUEST_URL, null))
    }
    fun persistSessionRemotePlayableSourceIds(sourceIds: List<String>) {
        val normalized = sourceIds
            .mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() } }
            .distinct()
        if (normalized.isEmpty()) {
            prefs.edit().remove(AppPreferenceKeys.SESSION_REMOTE_PLAYABLE_SOURCE_IDS_JSON).apply()
            return
        }
        val json = JSONArray().apply {
            normalized.forEach { put(it) }
        }.toString()
        prefs.edit()
            .putString(AppPreferenceKeys.SESSION_REMOTE_PLAYABLE_SOURCE_IDS_JSON, json)
            .apply()
    }
    fun readSessionRemotePlayableSourceIds(): List<String> {
        val raw = prefs.getString(AppPreferenceKeys.SESSION_REMOTE_PLAYABLE_SOURCE_IDS_JSON, null)
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            buildList {
                repeat(json.length()) { index ->
                    json.optString(index)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
    fun updateCurrentPlaybackSource(sourceId: String?) {
        val preserveRequestUrl =
            !sourceId.isNullOrBlank() &&
                !currentPlaybackRequestUrl.isNullOrBlank() &&
                samePath(sourceId, currentPlaybackRequestUrl)
        currentPlaybackSourceId = sourceId
        if (!preserveRequestUrl) {
            currentPlaybackRequestUrl = null
        }
    }
    val currentTrackPathOrUrl = currentPlaybackSourceId ?: selectedFile?.absolutePath
    val pendingPlaylistEntry = resolvePendingPlaylistEntry(
        activePlaylist = activePlaylist,
        pendingPlaylistSubtuneSelection = pendingPlaylistSubtuneSelection
    )
    val activePlaylistMetadataEntry = resolveActivePlaylistMetadataEntry(
        activePlaylist = activePlaylist,
        activePlaylistEntryId = activePlaylistEntryId,
        activeSourceId = currentTrackPathOrUrl,
        currentSubtuneIndex = currentSubtuneIndex
    ) ?: pendingPlaylistEntry
    val currentFavoriteEntry = resolveFavoriteEntryForPlayback(
        playlistLibraryState = playlistLibraryState,
        activeSourceId = currentTrackPathOrUrl,
        currentSubtuneIndex = currentSubtuneIndex
    )
    val isCurrentTrackFavorited = currentFavoriteEntry != null
    val currentPlaylistNavigationEntryId = pendingPlaylistEntry
        ?.id
        ?: activePlaylist
        ?.entries
        ?.firstOrNull { entry ->
            playlistEntryMatchesPlayback(
                entry = entry,
                activeSourceId = currentTrackPathOrUrl,
                currentSubtuneIndex = currentSubtuneIndex
            )
        }
        ?.id
        ?: activePlaylistEntryId
    LaunchedEffect(
        activePlaylist,
        currentPlaylistNavigationEntryId,
        activePlaylistShuffleActive,
        currentTrackPathOrUrl
    ) {
        val sourceId = currentTrackPathOrUrl?.trim().takeUnless { it.isNullOrBlank() }
        val playlist = activePlaylist
        val entryId = currentPlaylistNavigationEntryId?.trim().takeUnless { it.isNullOrBlank() }
        if (
            sourceId != null &&
            playlist != null &&
            entryId != null &&
            playlist.entries.any { it.id == entryId }
        ) {
            prefs.edit()
                .putString(AppPreferenceKeys.SESSION_RESUME_ACTIVE_PLAYLIST_SOURCE_ID, sourceId)
                .putString(
                    AppPreferenceKeys.SESSION_RESUME_ACTIVE_PLAYLIST_JSON,
                    writeStoredPlaylistToJson(playlist)
                )
                .putString(AppPreferenceKeys.SESSION_RESUME_ACTIVE_PLAYLIST_ENTRY_ID, entryId)
                .putBoolean(
                    AppPreferenceKeys.SESSION_RESUME_ACTIVE_PLAYLIST_SHUFFLE_ACTIVE,
                    activePlaylistShuffleActive
                )
                .apply()
        } else if (
            sourceId != null ||
                playlist != null ||
                entryId != null
        ) {
            prefs.edit()
                .remove(AppPreferenceKeys.SESSION_RESUME_ACTIVE_PLAYLIST_SOURCE_ID)
                .remove(AppPreferenceKeys.SESSION_RESUME_ACTIVE_PLAYLIST_JSON)
                .remove(AppPreferenceKeys.SESSION_RESUME_ACTIVE_PLAYLIST_ENTRY_ID)
                .remove(AppPreferenceKeys.SESSION_RESUME_ACTIVE_PLAYLIST_SHUFFLE_ACTIVE)
                .apply()
        }
    }
    val effectiveMetadataTitle = activePlaylistMetadataEntry
        ?.title
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: metadataTitle
    val effectiveMetadataArtist = activePlaylistMetadataEntry
        ?.artist
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: metadataArtist
    val effectiveMetadataAlbum = activePlaylistMetadataEntry
        ?.album
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: NativeBridge.getTrackAlbum()
    val playlistDurationOverride = activePlaylistMetadataEntry
        ?.durationSecondsOverride
        ?.takeIf { it.isFinite() && it > 0.0 }
    val effectiveDuration = playlistDurationOverride ?: duration
    val pinnedPlaylistSubtune = activePlaylistMetadataEntry?.subtuneIndex != null
    val titleSubtuneCount = subtuneCount
    val titleCurrentSubtuneIndex = currentSubtuneIndex
    val subtuneTitleClickable = !pinnedPlaylistSubtune && subtuneCount > 1
    val transportSubtuneCount = if (pinnedPlaylistSubtune) 0 else subtuneCount
    val transportCurrentSubtuneIndex = if (pinnedPlaylistSubtune) 0 else currentSubtuneIndex
    val activePlaylistBrowserFile = activePlaylist
        ?.takeIf { activePlaylistMetadataEntry != null }
        ?.sourceIdHint
        ?.takeIf { it.isNotBlank() }
        ?.let(::resolvePlaylistEntryLocalFile)
        ?.takeIf { it.exists() && it.isFile && isSupportedPlaylistFile(it) }
    val activePlaylistInfo = activePlaylist.takeIf { activePlaylistMetadataEntry != null }
    val trackInfoPlaylistTitle = activePlaylistInfo
        ?.title
        ?.trim()
        ?.ifBlank { "Playlist" }
    val trackInfoPlaylistFormatLabel = activePlaylistInfo?.format?.label
    val trackInfoPlaylistTrackCount = activePlaylistInfo?.entries?.size ?: 0
    val trackInfoPlaylistPathOrUrl = activePlaylistInfo
        ?.takeIf { it.format != PlaylistStoredFormat.Internal }
        ?.sourceIdHint
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val usesSelfContainedPlaylistQueue = activePlaylistMetadataEntry != null && (
        activePlaylist?.format == PlaylistStoredFormat.Internal ||
            (activePlaylist?.entries?.size ?: 0) > 1 ||
            activePlaylistBrowserFile == null
    )
    val effectivePlaybackCapabilitiesFlags =
        if (playlistDurationOverride != null) {
            playbackCapabilitiesFlags or PLAYBACK_CAP_RELIABLE_DURATION
        } else {
            playbackCapabilitiesFlags
        }
    val playbackSourceLabel = remember(selectedFile, currentPlaybackSourceId) {
        resolvePlaybackSourceLabel(selectedFile, currentPlaybackSourceId)
    }

    LaunchedEffect(
        isPlaying,
        currentFavoriteEntry?.id,
        currentTrackPathOrUrl,
        effectiveMetadataTitle,
        effectiveMetadataArtist,
        effectiveMetadataAlbum
    ) {
        if (!isPlaying) return@LaunchedEffect
        val favoriteEntry = currentFavoriteEntry ?: return@LaunchedEffect
        val sourceId = currentTrackPathOrUrl ?: return@LaunchedEffect
        val resolvedArtist = effectiveMetadataArtist.trim().ifBlank {
            favoriteEntry.artist?.trim().takeUnless { it.isNullOrBlank() } ?: "Unknown artist"
        }
        val artworkCacheKey = if (favoriteEntry.artworkThumbnailCacheKey.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                ensureRecentArtworkThumbnailCached(
                    context = context,
                    sourceId = sourceId,
                    requestUrlHint = currentPlaybackRequestUrl
                )
            }
        } else {
            favoriteEntry.artworkThumbnailCacheKey
        }
        val updatedState = mergeFavoritePlaybackMetadata(
            playlistLibraryState = playlistLibraryState,
            favoriteId = favoriteEntry.id,
            title = effectiveMetadataTitle,
            artist = resolvedArtist,
            album = effectiveMetadataAlbum,
            artworkThumbnailCacheKey = artworkCacheKey,
            durationSecondsOverride = playlistDurationOverride,
            requestUrlHint = currentPlaybackRequestUrl
        )
        if (updatedState != playlistLibraryState) {
            onPlaylistLibraryStateChanged(updatedState)
        }
    }

    // Get supported extensions from JNI
    val supportedExtensions = remember { NativeBridge.getSupportedExtensions().toSet() }
    val repository = remember(supportedExtensions, sortArchivesBeforeFiles, browserNameSortMode) {
        com.flopster101.siliconplayer.data.FileRepository(
            supportedExtensions = supportedExtensions,
            prefs = prefs,
            sortArchivesBeforeFiles = sortArchivesBeforeFiles,
            nameSortMode = browserNameSortMode
        )
    }
    var decoderIconHintsVersion by remember { mutableIntStateOf(0) }
    val decoderExtensionArtworkHints by produceState<Map<String, DecoderArtworkHint>>(
        initialValue = emptyMap(),
        key1 = decoderIconHintsVersion
    ) {
        value = withContext(Dispatchers.Default) {
            buildDecoderExtensionArtworkHintMap()
        }
    }

    // Handle pending file from intent
    var pendingFileToOpen by remember { mutableStateOf<File?>(initialFileToOpen) }
    var pendingFileFromExternalIntent by remember { mutableStateOf(initialFileFromExternalIntent) }

    val loadSongVolumeForFile = buildLoadSongVolumeForFileDelegate(
        volumeDatabase = volumeDatabase,
        onSongVolumeDbChanged = { songVolumeDb = it },
        onSongGainChanged = { NativeBridge.setSongGain(it) },
        onIgnoreCoreVolumeForSongChanged = { ignoreCoreVolumeForSong = it }
    )

    val isLocalPlayableFile = buildIsLocalPlayableFileDelegate()

    val refreshCachedSourceFiles = buildRefreshCachedSourceFilesDelegate(
        appScope = appScope,
        cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
        onCachedSourceFilesChanged = { cachedSourceFiles = it }
    )

    val cacheExportDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val selectedPaths = pendingCacheExportPaths
        pendingCacheExportPaths = emptyList()
        if (treeUri == null || selectedPaths.isEmpty()) {
            if (selectedPaths.isNotEmpty()) {
                Toast.makeText(context, "Export canceled", Toast.LENGTH_SHORT).show()
            }
            return@rememberLauncherForActivityResult
        }
        appScope.launch(Dispatchers.IO) {
            val result = exportCachedFilesToTree(
                context = context,
                treeUri = treeUri,
                selectedPaths = selectedPaths
            )
            if (result.invalidDestination) {
                withContext(Dispatchers.Main.immediate) {
                    Toast.makeText(context, "Export failed: invalid destination", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                Toast.makeText(
                    context,
                    "Exported ${result.exportedCount} file(s)" +
                        if (result.failedCount > 0) " (${result.failedCount} failed)" else "",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    AppNavigationPendingOpenEffects(
        currentView = currentView,
        settingsRoute = settingsRoute,
        pendingFileToOpen = pendingFileToOpen,
        pendingFileFromExternalIntent = pendingFileFromExternalIntent,
        autoPlayOnTrackSelect = autoPlayOnTrackSelect,
        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
        supportedExtensions = supportedExtensions,
        onRefreshCachedSourceFiles = refreshCachedSourceFiles,
        onSelectedFileChanged = { selectedFile = it },
        onLoadSongVolumeForFile = loadSongVolumeForFile,
        onApplyRepeatModeToNative = { applyRepeatModeToNative(activeRepeatMode) },
        onStartEngine = { NativeBridge.startEngine() },
        onIsPlayingChanged = { isPlaying = it },
        onIsPlayerExpandedChanged = { isPlayerExpanded = it },
        onIsPlayerSurfaceVisibleChanged = { isPlayerSurfaceVisible = it },
        onVisiblePlayableFilesChanged = { visiblePlayableFiles = it },
        onPendingFileToOpenChanged = { pendingFileToOpen = it },
        onPendingFileFromExternalIntentChanged = { pendingFileFromExternalIntent = it },
        loadPlayableSiblingFiles = { file ->
            loadPlayableSiblingFilesForExternalIntent(repository = repository, file = file)
        }
    )

    val storagePermissionState = rememberStoragePermissionState(context)

    val runtimeDelegates = buildAppNavigationRuntimeDelegates(
        context = context,
        prefs = prefs,
        appScope = appScope,
        recentFoldersProvider = { recentFolders },
        recentFoldersLimitProvider = { recentFoldersLimit },
        onRecentFoldersChanged = { recentFolders = it },
        recentPlayedFilesProvider = { recentPlayedFiles },
        recentFilesLimitProvider = { recentFilesLimit },
        onRecentPlayedChanged = { recentPlayedFiles = it },
        selectedFileProvider = { selectedFile },
        currentPlaybackSourceIdProvider = { currentPlaybackSourceId },
        currentPlaybackRequestUrlProvider = { currentPlaybackRequestUrl },
        metadataTitleProvider = { effectiveMetadataTitle },
        metadataArtistProvider = { effectiveMetadataArtist },
        durationProvider = { effectiveDuration },
        positionProvider = { position },
        isPlayingProvider = { isPlaying },
        subtuneCountProvider = { subtuneCount },
        onSubtuneCountChanged = { subtuneCount = it },
        onCurrentSubtuneIndexChanged = { currentSubtuneIndex = it },
        onSubtuneEntriesChanged = { subtuneEntries = it },
        onShowSubtuneSelectorDialogChanged = { showSubtuneSelectorDialog = it },
        preferredRepeatModeProvider = { preferredRepeatMode },
        activeRepeatModeProvider = { activeRepeatMode },
        repeatModeCapabilitiesFlagsProvider = { repeatModeCapabilitiesFlags },
        playbackCapabilitiesFlagsProvider = { playbackCapabilitiesFlags },
        seekInProgressProvider = { seekInProgress },
        onPreferredRepeatModeChanged = { preferredRepeatMode = it },
        onActiveRepeatModeChanged = { activeRepeatMode = it },
        applyRepeatModeToNative = { mode -> applyRepeatModeToNative(mode) }
    )
    fun currentExternalPlaylistRecentOverride(): ExternalPlaylistRecentOverride? {
        val playlist = activePlaylist ?: return null
        if (playlist.format == PlaylistStoredFormat.Internal) return null
        val playlistPath = playlist.sourceIdHint?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val playlistEntry = playlist.entries.firstOrNull { it.id == activePlaylistEntryId } ?: return null
        val locationId = playlist.sourceIdHint
            ?.let(::resolvePlaylistEntryLocalFile)
            ?.takeIf { it.exists() && it.isFile && isSupportedPlaylistFile(it) }
            ?.let { lastBrowserLocationId }
        return ExternalPlaylistRecentOverride(
            playlistPath = playlistPath,
            locationId = locationId,
            sourceHint = playlistEntry.source.trim().takeIf { it.isNotBlank() },
            referencedSources = playlist.entries.map { it.source }.toSet()
        )
    }

    val addRecentPlayedTrackFromPlaybackContext: (String, String?, String?, String?) -> Unit =
        { path, locationId, title, artist ->
            val playlistOverride = currentExternalPlaylistRecentOverride()
            if (playlistOverride != null) {
                val updatedRecentPlayed = buildUpdatedRecentPlayedTracks(
                    current = recentPlayedFiles,
                    newPath = playlistOverride.playlistPath,
                    locationId = playlistOverride.locationId ?: locationId,
                    title = null,
                    artist = null,
                    decoderName = NativeBridge.getCurrentDecoderName().trim().takeIf { it.isNotEmpty() },
                    isPlaylist = true,
                    playlistSourceHint = playlistOverride.sourceHint,
                    limit = recentFilesLimit
                )
                    .filterNot { entry ->
                        !entry.isPlaylist &&
                            playlistOverride.referencedSources.any { referenced -> samePath(entry.path, referenced) }
                    }
                recentPlayedFiles = updatedRecentPlayed
                writeRecentEntries(
                    prefs,
                    AppPreferenceKeys.RECENT_PLAYED_FILES,
                    updatedRecentPlayed,
                    recentFilesLimit
                )
            } else {
                runtimeDelegates.addRecentPlayedTrack(
                    path,
                    locationId,
                    title,
                    artist,
                    false,
                    null
                )
            }
        }
    val scheduleRecentTrackMetadataRefreshFromPlaybackContext: (String, String?) -> Unit =
        { sourceId, locationId ->
            if (currentExternalPlaylistRecentOverride() == null) {
                runtimeDelegates.scheduleRecentTrackMetadataRefresh(sourceId, locationId)
            }
        }

    LaunchedEffect(recentPlayedFiles, recentFilesLimit) {
        runtimeDelegates.scheduleRecentPlayedMetadataBackfill()
    }

    LaunchedEffect(
        currentPlaybackSourceId,
        selectedFile?.absolutePath,
        metadataTitle,
        metadataArtist,
        lastBrowserLocationId,
        isPlaying
    ) {
        if (!isPlaying) return@LaunchedEffect
        val sourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath ?: return@LaunchedEffect
        val normalizedTitle = metadataTitle.trim()
        val normalizedArtist = metadataArtist.trim()
        val decoderName = NativeBridge.getCurrentDecoderName().trim().takeIf { it.isNotEmpty() }
        val playlistOverride = currentExternalPlaylistRecentOverride()
        val recentEntryPath = playlistOverride?.playlistPath ?: sourceId
        val recentEntryLocationId = if (playlistOverride != null) {
            playlistOverride.locationId
        } else if (isLocalPlayableFile(selectedFile)) {
            lastBrowserLocationId
        } else {
            null
        }
        var updatedRecentPlayed = buildUpdatedRecentPlayedTracks(
            current = recentPlayedFiles,
            newPath = recentEntryPath,
            locationId = recentEntryLocationId,
            title = if (playlistOverride != null) null else normalizedTitle,
            artist = if (playlistOverride != null) null else normalizedArtist,
            decoderName = decoderName,
            isPlaylist = playlistOverride != null,
            playlistSourceHint = playlistOverride?.sourceHint,
            clearBlankMetadataOnUpdate = true,
            limit = recentFilesLimit
        )
        if (playlistOverride != null && playlistOverride.referencedSources.isNotEmpty()) {
                updatedRecentPlayed = updatedRecentPlayed.filterNot { entry ->
                    !entry.isPlaylist &&
                        playlistOverride.referencedSources.any { referenced -> samePath(entry.path, referenced) }
                }
        }
        recentPlayedFiles = updatedRecentPlayed
        writeRecentEntries(
            prefs,
            AppPreferenceKeys.RECENT_PLAYED_FILES,
            updatedRecentPlayed,
            recentFilesLimit
        )
    }

    val playbackStateDelegates = AppNavigationPlaybackStateDelegates(
        context = context,
        prefs = prefs,
        selectedFileProvider = { selectedFile },
        onSelectedFileChanged = { selectedFile = it },
        currentPlaybackSourceIdProvider = { currentPlaybackSourceId },
        currentPlaybackRequestUrlProvider = { currentPlaybackRequestUrl },
        onCurrentPlaybackSourceIdChanged = { updateCurrentPlaybackSource(it) },
        isPlayingProvider = { isPlaying },
        lastBrowserLocationIdProvider = { lastBrowserLocationId },
        isLocalPlayableFile = isLocalPlayableFile,
        metadataTitleProvider = { effectiveMetadataTitle },
        metadataArtistProvider = { effectiveMetadataArtist },
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        addRecentPlayedTrack = { path, locationId, title, artist ->
            addRecentPlayedTrackFromPlaybackContext(path, locationId, title, artist)
        },
        syncPlaybackService = { runtimeDelegates.syncPlaybackService() },
        readNativeTrackSnapshot = { readNativeTrackSnapshot() },
        ignoreCoreVolumeForCurrentSongProvider = {
            if (showAudioEffectsDialog) tempIgnoreCoreVolumeForSong else ignoreCoreVolumeForSong
        },
        onLastUsedCoreNameChanged = { lastUsedCoreName = it },
        onPluginVolumeDbChanged = { pluginVolumeDb = it },
        onPluginGainChanged = { NativeBridge.setPluginGain(it) },
        onDurationChanged = { duration = it },
        onPositionChanged = { position = it },
        onIsPlayingChanged = { isPlaying = it },
        onSeekInProgressChanged = { seekInProgress = it },
        onSeekUiBusyChanged = { seekUiBusy = it },
        onSeekStartedAtMsChanged = { seekStartedAtMs = it },
        onSeekRequestedAtMsChanged = { seekRequestedAtMs = it },
        onMetadataTitleChanged = { metadataTitle = it },
        onMetadataArtistChanged = { metadataArtist = it },
        onMetadataSampleRateChanged = { metadataSampleRate = it },
        onMetadataChannelCountChanged = { metadataChannelCount = it },
        onMetadataBitDepthLabelChanged = { metadataBitDepthLabel = it },
        onSubtuneCountChanged = { subtuneCount = it },
        onCurrentSubtuneIndexChanged = { currentSubtuneIndex = it },
        onSubtuneEntriesCleared = { subtuneEntries = emptyList() },
        onShowSubtuneSelectorDialogChanged = { showSubtuneSelectorDialog = it },
        onRepeatModeCapabilitiesFlagsChanged = { repeatModeCapabilitiesFlags = it },
        onPlaybackCapabilitiesFlagsChanged = { playbackCapabilitiesFlags = it },
        onArtworkBitmapCleared = {
            artworkBitmap = null
            artworkResolvedTrackKey = null
            artworkReloadToken += 1
        },
        onIgnoreCoreVolumeForSongChanged = { ignoreCoreVolumeForSong = it },
        onLastStoppedChanged = { file, sourceId ->
            lastStoppedFile = file
            lastStoppedSourceId = sourceId
        },
        onStopEngine = { NativeBridge.stopEngine() }
    )

    val trackLoadDelegates = AppNavigationTrackLoadDelegates(
        appScope = appScope,
        context = context,
        prefs = prefs,
        repository = repository,
        cacheRootProvider = { File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR) },
        lastBrowserLocationIdProvider = { lastBrowserLocationId },
        isPlayingProvider = { isPlaying },
        onResetPlayback = {
            deferredPlaybackSeek = null
            playbackStateDelegates.resetAndOptionallyKeepLastTrack(keepLastTrack = false)
        },
        onSelectedFileChanged = { selectedFile = it },
        onCurrentPlaybackSourceIdChanged = { updateCurrentPlaybackSource(it) },
        onCurrentPlaybackRequestUrlChanged = { currentPlaybackRequestUrl = it },
        onActivePlaylistChanged = { activePlaylist = it },
        onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
        onActivePlaylistShuffleActiveChanged = { activePlaylistShuffleActive = it },
        onPendingPlaylistSubtuneSelectionChanged = { sourceId, subtuneIndex ->
            pendingPlaylistSubtuneSelection =
                if (!sourceId.isNullOrBlank() && subtuneIndex != null) {
                    PendingPlaylistSubtuneSelection(sourceId, subtuneIndex)
                } else {
                    null
                }
        },
        onVisiblePlayableFilesChanged = { visiblePlayableFiles = it },
        onPlayerSurfaceVisibleChanged = { isPlayerSurfaceVisible = it },
        loadSongVolumeForFile = loadSongVolumeForFile,
        onSongVolumeDbChanged = { songVolumeDb = it },
        onSongGainChanged = { NativeBridge.setSongGain(it) },
        onResolvedDecoderState = { decoderName ->
            playbackStateDelegates.applyResolvedDecoderState(decoderName)
        },
        readNativeTrackSnapshot = { readNativeTrackSnapshot() },
        applyNativeTrackSnapshot = { snapshot -> playbackStateDelegates.applyNativeTrackSnapshot(snapshot) },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        onPositionChanged = { position = it },
        onArtworkBitmapCleared = {
            artworkBitmap = null
            artworkResolvedTrackKey = null
            artworkReloadToken += 1
        },
        onIsPlayingChanged = { isPlaying = it },
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        onAddRecentPlayedTrack = { path, locationId, title, artist ->
            addRecentPlayedTrackFromPlaybackContext(path, locationId, title, artist)
        },
        metadataTitleProvider = { effectiveMetadataTitle },
        metadataArtistProvider = { effectiveMetadataArtist },
        onStartEngine = { NativeBridge.startEngine() },
        scheduleRecentTrackMetadataRefresh = { sourceId, locationId ->
            scheduleRecentTrackMetadataRefreshFromPlaybackContext(sourceId, locationId)
        },
        onPlayerExpandedChanged = { isPlayerExpanded = it },
        onPlaybackStartInProgressChanged = { playbackStartInProgress = it },
        syncPlaybackService = { runtimeDelegates.syncPlaybackService() },
        onDeferredPlaybackSeekChanged = { deferredPlaybackSeek = it }
    )

    LaunchedEffect(selectedFile?.absolutePath, currentPlaybackSourceId) {
        val activeSourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath
        if (deferredPlaybackSeek?.sourceId != activeSourceId) {
            deferredPlaybackSeek = null
        }
    }
    val displayedArtworkBitmap = rememberDisplayedPlayerArtwork(
        trackKey = currentPlaybackSourceId ?: selectedFile?.absolutePath,
        artwork = artworkBitmap,
        resolvedTrackKey = artworkResolvedTrackKey
    )
    val artworkSwipePreviewState = rememberLocalArtworkSwipePreviewState(
        selectedFile = selectedFile,
        currentPlaybackSourceId = currentPlaybackSourceId,
        visiblePlayableFiles = visiblePlayableFiles
    )
    LaunchedEffect(isPlayerExpanded, isPlayerSurfaceVisible, restoreMiniPlayerFocusOnCollapse) {
        if (!isPlayerExpanded && restoreMiniPlayerFocusOnCollapse && isPlayerSurfaceVisible) {
            withFrameNanos { }
            miniPlayerFocusRequester.requestFocus()
            restoreMiniPlayerFocusOnCollapse = false
        } else if (!isPlayerSurfaceVisible && restoreMiniPlayerFocusOnCollapse) {
            restoreMiniPlayerFocusOnCollapse = false
        }
    }

    LaunchedEffect(currentPlaybackSourceId, RemotePlayableSourceIdsHolder.current) {
        val activeSourceId = currentPlaybackSourceId?.trim().takeUnless { it.isNullOrBlank() }
            ?: return@LaunchedEffect
        val resolvedQueue = RemotePlayableSourceIdsHolder.resolvedCurrentOrLastForSource(activeSourceId)
        if (resolvedQueue.isNotEmpty()) {
            persistSessionRemotePlayableSourceIds(resolvedQueue)
        }
    }

    LaunchedEffect(currentPlaybackSourceId) {
        val activeSourceId = currentPlaybackSourceId?.trim().takeUnless { it.isNullOrBlank() }
            ?: return@LaunchedEffect
        if (RemotePlayableSourceIdsHolder.resolvedCurrentOrLastForSource(activeSourceId).isNotEmpty()) {
            return@LaunchedEffect
        }
        val restoredQueue = readSessionRemotePlayableSourceIds()
        if (restoredQueue.any { samePath(it, activeSourceId) }) {
            RemotePlayableSourceIdsHolder.current = restoredQueue
        }
    }

    val playbackSessionCoordinator = buildPlaybackSessionCoordinator(
        runtimeDelegates = runtimeDelegates,
        trackLoadDelegates = trackLoadDelegates
    )

    val manualOpenDelegates = AppNavigationManualOpenDelegates(
        context = context,
        appScope = appScope,
        repository = repository,
        storageDescriptors = storageDescriptors,
        openPlayerOnTrackSelectProvider = { openPlayerOnTrackSelect },
        isPlayerExpandedProvider = { isPlayerExpanded },
        activeRepeatModeProvider = { activeRepeatMode },
        selectedFileAbsolutePathProvider = { selectedFile?.absolutePath },
        urlCacheMaxTracksProvider = { urlCacheMaxTracks },
        urlCacheMaxBytesProvider = { urlCacheMaxBytes },
        currentRemoteLoadJobProvider = { remoteLoadJob },
        onRemoteLoadUiStateChanged = { remoteLoadUiState = it },
        onRemoteLoadJobChanged = { remoteLoadJob = it },
        onResetPlayback = { playbackStateDelegates.resetAndOptionallyKeepLastTrack(keepLastTrack = false) },
        onSelectedFileChanged = { selectedFile = it },
        onCurrentPlaybackSourceIdChanged = { currentPlaybackSourceId = it },
        onCurrentPlaybackRequestUrlChanged = { currentPlaybackRequestUrl = it },
        onVisiblePlayableFilesChanged = { visiblePlayableFiles = it },
        onPlayerSurfaceVisibleChanged = { isPlayerSurfaceVisible = it },
        onSongVolumeDbChanged = { songVolumeDb = it },
        onSongGainChanged = { NativeBridge.setSongGain(it) },
        onResolvedDecoderState = { decoderName ->
            playbackStateDelegates.applyResolvedDecoderState(decoderName)
        },
        applyNativeTrackSnapshot = { snapshot -> playbackStateDelegates.applyNativeTrackSnapshot(snapshot) },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        onPositionChanged = { position = it },
        onArtworkBitmapCleared = {
            artworkBitmap = null
            artworkResolvedTrackKey = null
            artworkReloadToken += 1
        },
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        onAddRecentPlayedTrack = { path, locationId, title, artist ->
            addRecentPlayedTrackFromPlaybackContext(path, locationId, title, artist)
        },
        metadataTitleProvider = { effectiveMetadataTitle },
        metadataArtistProvider = { effectiveMetadataArtist },
        applyRepeatModeToNative = { mode -> applyRepeatModeToNative(mode) },
        onStartEngine = { NativeBridge.startEngine() },
        onIsPlayingChanged = { isPlaying = it },
        scheduleRecentTrackMetadataRefresh = { sourceId, locationId ->
            scheduleRecentTrackMetadataRefreshFromPlaybackContext(sourceId, locationId)
        },
        onPlayerExpandedChanged = { isPlayerExpanded = it },
        syncPlaybackService = playbackSessionCoordinator.syncPlaybackService,
        onBrowserLaunchTargetChanged = { launchState ->
            var normalizedLaunchState = launchState
            val isArchiveLogicalLocation = resolveBrowserLocationModel(
                initialLocationId = launchState.locationId,
                initialDirectoryPath = launchState.directoryPath,
                initialSmbSourceNodeId = launchState.smbSourceNodeId,
                initialHttpSourceNodeId = launchState.httpSourceNodeId,
                initialHttpRootPath = launchState.httpRootPath
            ) is BrowserLocationModel.ArchiveLogical
            if (!isArchiveLogicalLocation) {
                normalizedLaunchState = normalizedLaunchState.copy(
                    smbSourceNodeId = null,
                    httpSourceNodeId = null,
                    httpRootPath = null
                )
            }
            browserNavigator.updateLaunchState(normalizedLaunchState)
        },
        onCurrentViewChanged = { currentView = it },
        onAddRecentFolder = { path, locationId, sourceNodeId ->
            runtimeDelegates.addRecentFolder(path, locationId, sourceNodeId)
        },
        onApplyTrackSelection = { file, autoStart, expandOverride, sourceIdOverride, initialSubtuneIndex ->
            trackLoadDelegates.applyTrackSelection(
                file = file,
                autoStart = autoStart,
                expandOverride = expandOverride,
                sourceIdOverride = sourceIdOverride,
                initialSubtuneIndex = initialSubtuneIndex
            )
        }
    )

    val openParsedPlaylistDocumentAction: (ParsedPlaylistDocument, String?) -> Unit = { document, entryId ->
        openPlaylistDocument(
            context = context,
            document = document,
            trackLoadDelegates = trackLoadDelegates,
            manualOpenDelegates = manualOpenDelegates,
            autoPlayOnTrackSelect = autoPlayOnTrackSelect,
            openPlayerOnTrackSelect = openPlayerOnTrackSelect,
            onActivePlaylistChanged = {
                activePlaylist = it
                activePlaylistShuffleActive = false
            },
            onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
            onShowPlaylistSelectorDialogChanged = { showPlaylistSelectorDialog = it },
            onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it },
            selectedEntryId = entryId
        )
    }
    val handlePlaylistFileSelectionAction: (File, String?) -> Unit = { file, sourceIdHint ->
        val parsed = parsePlaylistFileDocument(file, sourceIdHint)
        if (parsed == null || parsed.entries.isEmpty()) {
            Toast.makeText(context, "Unable to open playlist", Toast.LENGTH_SHORT).show()
        } else {
            pendingBrowserPlaylistDocument = parsed
            showPlaylistPreviewDialog = false
            showPlaylistOpenActionDialog = true
        }
    }
    val openPlaylistFileImmediatelyAction: (File, String?) -> Unit = { file, sourceIdHint ->
        val parsed = parsePlaylistFileDocument(file, sourceIdHint)
        if (parsed == null || parsed.entries.isEmpty()) {
            Toast.makeText(context, "Unable to open playlist", Toast.LENGTH_SHORT).show()
        } else {
            openParsedPlaylistDocumentAction(parsed, null)
            pendingBrowserPlaylistDocument = null
            showPlaylistOpenActionDialog = false
            showPlaylistPreviewDialog = false
        }
    }
    val playPendingBrowserPlaylistAction: () -> Unit = {
        pendingBrowserPlaylistDocument?.let { document ->
            openParsedPlaylistDocumentAction(document, null)
            pendingBrowserPlaylistDocument = null
            showPlaylistOpenActionDialog = false
            showPlaylistPreviewDialog = false
        }
    }
    val openPendingBrowserPlaylistEntryAction: (PlaylistTrackEntry) -> Unit = { entry ->
        pendingBrowserPlaylistDocument?.let { document ->
            openParsedPlaylistDocumentAction(document, entry.id)
            pendingBrowserPlaylistDocument = null
            showPlaylistOpenActionDialog = false
            showPlaylistPreviewDialog = false
        }
    }
    val dismissPendingBrowserPlaylistAction: () -> Unit = {
        pendingBrowserPlaylistDocument = null
        showPlaylistOpenActionDialog = false
        showPlaylistPreviewDialog = false
    }
    val browsePendingBrowserPlaylistAction: () -> Unit = {
        if (pendingBrowserPlaylistDocument == null) {
            showPlaylistPreviewDialog = false
        } else {
            showPlaylistPreviewDialog = true
        }
    }
    val playPlaylistEntryAction: (PlaylistTrackEntry, StoredPlaylist?) -> Unit = { entry, playlist ->
        openPlaylistEntry(
            context = context,
            entry = entry,
            playlist = playlist,
            trackLoadDelegates = trackLoadDelegates,
            manualOpenDelegates = manualOpenDelegates,
            autoPlayOnTrackSelect = autoPlayOnTrackSelect,
            openPlayerOnTrackSelect = openPlayerOnTrackSelect,
            onActivePlaylistChanged = { activePlaylist = it },
            onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
            onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
        )
    }
    val toggleCurrentTrackFavoriteAction: () -> Unit = {
        toggleCurrentTrackFavorite(
            context = context,
            playlistLibraryState = playlistLibraryState,
            currentPlaybackSourceId = currentPlaybackSourceId,
            currentPlaybackRequestUrl = currentPlaybackRequestUrl,
            selectedFile = selectedFile,
            metadataTitle = effectiveMetadataTitle,
            metadataArtist = effectiveMetadataArtist,
            metadataAlbum = effectiveMetadataAlbum,
            durationSecondsOverride = playlistDurationOverride,
            subtuneCount = subtuneCount,
            currentSubtuneIndex = currentSubtuneIndex,
            onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged
        )
    }
    AppNavigationPlaylistEffects(
        pendingFileToOpen = pendingFileToOpen,
        pendingFileFromExternalIntent = pendingFileFromExternalIntent,
        currentPlaybackSourceId = currentPlaybackSourceId,
        selectedFile = selectedFile,
        currentSubtuneIndex = currentSubtuneIndex,
        activePlaylist = activePlaylist,
        activePlaylistEntryId = activePlaylistEntryId,
        pendingPlaylistSubtuneSelection = pendingPlaylistSubtuneSelection,
        subtuneCount = subtuneCount,
        onPendingFileToOpenChanged = { pendingFileToOpen = it },
        onPendingFileFromExternalIntentChanged = { pendingFileFromExternalIntent = it },
        onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
        onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it },
        onHandlePlaylistFileSelection = openPlaylistFileImmediatelyAction,
        onSelectSubtune = { playbackStateDelegates.selectSubtune(it) }
    )

    val trackNavDelegates = com.flopster101.siliconplayer.playback.AppNavigationTrackNavDelegates(
        lastStoppedFileProvider = { lastStoppedFile },
        lastStoppedSourceIdProvider = { lastStoppedSourceId },
        onLastStoppedCleared = {
            lastStoppedFile = null
            lastStoppedSourceId = null
        },
        urlOrPathForceCachingProvider = { urlOrPathForceCaching },
        isPlayerExpandedProvider = { isPlayerExpanded },
        selectedFileProvider = { selectedFile },
        currentPlaybackSourceIdProvider = { currentPlaybackSourceId },
        visiblePlayableFilesProvider = { visiblePlayableFiles },
        visiblePlayableSourceIdsProvider = { RemotePlayableSourceIdsHolder.current },
        playlistWrapNavigationProvider = { playlistWrapNavigation },
        previousRestartsAfterThresholdProvider = { previousRestartsAfterThreshold },
        positionSecondsProvider = { position },
        onPositionChanged = { position = it },
        onSyncPlaybackService = playbackSessionCoordinator.syncPlaybackService,
        onPlaylistWrapped = { offset ->
            val message = if (offset < 0) {
                "Wrapped to last track"
            } else {
                "Wrapped to first track"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
        onApplyTrackSelection = { file, autoStart, expand ->
            trackLoadDelegates.applyTrackSelection(file = file, autoStart = autoStart, expandOverride = expand)
        },
        onApplyManualInputSelection = { rawInput, options, expandOverride ->
            manualOpenDelegates.applyManualInputSelection(rawInput, options, expandOverride)
        }
    )
    val playAdjacentActivePlaylistEntryAction: (Int, Boolean?, Boolean) -> Boolean =
        { offset, wrapOverride, notifyWrap ->
            val currentPlaylistEntryId = currentPlaylistNavigationEntryId
            if (
                activePlaylist?.entries?.isNotEmpty() == true &&
                    !currentPlaylistEntryId.isNullOrBlank()
            ) {
                if (usesSelfContainedPlaylistQueue) {
                    playAdjacentPlaylistEntry(
                        context = context,
                        activePlaylist = activePlaylist,
                        currentEntryId = currentPlaylistEntryId,
                        offset = offset,
                        wrapOverride = wrapOverride,
                        playlistWrapNavigation = playlistWrapNavigation,
                        notifyWrap = notifyWrap,
                        expandOverride = isPlayerExpanded,
                        trackLoadDelegates = trackLoadDelegates,
                        manualOpenDelegates = manualOpenDelegates,
                        autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                        onActivePlaylistChanged = { activePlaylist = it },
                        onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                        onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
                    )
                } else {
                    playAdjacentBrowserFileFromAnchor(
                        context = context,
                        anchorPath = activePlaylist?.sourceIdHint,
                        offset = offset,
                        wrapOverride = wrapOverride,
                        playlistWrapNavigation = playlistWrapNavigation,
                        notifyWrap = notifyWrap,
                        activePlaylist = activePlaylist,
                        repository = repository,
                        visiblePlayableFiles = visiblePlayableFiles,
                        playlistLibraryState = playlistLibraryState,
                        trackLoadDelegates = trackLoadDelegates,
                        manualOpenDelegates = manualOpenDelegates,
                        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                        expandOverride = isPlayerExpanded,
                        onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged,
                        onActivePlaylistChanged = { activePlaylist = it },
                        onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                        onShowPlaylistSelectorDialogChanged = { showPlaylistSelectorDialog = it },
                        onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
                    ) || trackNavDelegates.playAdjacentTrack(
                        offset = offset,
                        notifyWrap = notifyWrap,
                        wrapOverride = wrapOverride
                    )
                }
            } else {
                trackNavDelegates.playAdjacentTrack(
                    offset = offset,
                    notifyWrap = notifyWrap,
                    wrapOverride = wrapOverride
                )
            }
        }
    val playAdjacentTrackFromUiAction: (Int, Boolean) -> Boolean = { offset, stopAtBoundary ->
        val wrapAtBoundary = activeRepeatMode != RepeatMode.None
        val moved = if (
            activePlaylist?.entries?.isNotEmpty() == true &&
                !currentPlaylistNavigationEntryId.isNullOrBlank()
        ) {
            playAdjacentPlaylistEntry(
                context = context,
                activePlaylist = activePlaylist,
                currentEntryId = currentPlaylistNavigationEntryId,
                offset = offset,
                wrapOverride = false,
                playlistWrapNavigation = playlistWrapNavigation,
                notifyWrap = false,
                expandOverride = isPlayerExpanded,
                trackLoadDelegates = trackLoadDelegates,
                manualOpenDelegates = manualOpenDelegates,
                autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                onActivePlaylistChanged = { activePlaylist = it },
                onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
            ) || playAdjacentBrowserFileFromAnchor(
                context = context,
                anchorPath = activePlaylist?.sourceIdHint,
                offset = offset,
                wrapOverride = wrapAtBoundary,
                playlistWrapNavigation = playlistWrapNavigation,
                notifyWrap = true,
                activePlaylist = activePlaylist,
                repository = repository,
                visiblePlayableFiles = visiblePlayableFiles,
                playlistLibraryState = playlistLibraryState,
                trackLoadDelegates = trackLoadDelegates,
                manualOpenDelegates = manualOpenDelegates,
                openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                expandOverride = isPlayerExpanded,
                onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged,
                onActivePlaylistChanged = { activePlaylist = it },
                onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                onShowPlaylistSelectorDialogChanged = { showPlaylistSelectorDialog = it },
                onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
            )
        } else {
            val activeSourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath
            if (isRemoteQueuePlaybackSource(activeSourceId)) {
                false
            } else {
                val localAnchorPath = selectedFile?.absolutePath
                if (localAnchorPath != null) {
                    playAdjacentBrowserFileFromAnchor(
                        context = context,
                        anchorPath = localAnchorPath,
                        offset = offset,
                        wrapOverride = wrapAtBoundary,
                        playlistWrapNavigation = playlistWrapNavigation,
                        notifyWrap = true,
                        activePlaylist = null,
                        repository = repository,
                        visiblePlayableFiles = visiblePlayableFiles,
                        playlistLibraryState = playlistLibraryState,
                        trackLoadDelegates = trackLoadDelegates,
                        manualOpenDelegates = manualOpenDelegates,
                        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                        expandOverride = isPlayerExpanded,
                        onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged,
                        onActivePlaylistChanged = { activePlaylist = it },
                        onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                        onShowPlaylistSelectorDialogChanged = { showPlaylistSelectorDialog = it },
                        onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
                    )
                } else {
                    false
                }
            }
        } || trackNavDelegates.playAdjacentTrack(
            offset = offset,
            notifyWrap = true,
            wrapOverride = wrapAtBoundary
        )
        if (!moved && stopAtBoundary && offset > 0 && !wrapAtBoundary) {
            stopAndEmptyTrackAction(context, playbackStateDelegates)
            true
        } else {
            moved
        }
    }
    val playPreviousTrackFromUiAction: () -> Boolean = {
        val restartCurrentSelection = {
            NativeBridge.seekTo(0.0)
            position = 0.0
            playbackSessionCoordinator.syncPlaybackService()
        }
        val currentEntryId = currentPlaylistNavigationEntryId
        val playlistEntries = activePlaylist?.entries
        val usePlaylistNavigation = playlistEntries?.isNotEmpty() == true &&
            !currentEntryId.isNullOrBlank()
        if (!usePlaylistNavigation) {
            trackNavDelegates.handlePreviousTrackAction()
        } else {
            val currentIndex = playlistEntries
                ?.indexOfFirst { entry -> entry.id == currentEntryId }
                ?: -1
            val hasPreviousTrack = if (playlistWrapNavigation) {
                currentIndex >= 0 && playlistEntries.isNotEmpty()
            } else {
                currentIndex > 0 && playlistEntries.isNotEmpty()
            }
            when (
                resolvePreviousTrackAction(
                    previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                    hasTrackLoaded = selectedFile != null,
                    positionSeconds = position,
                    hasPreviousTrack = hasPreviousTrack
                )
            ) {
                PreviousTrackAction.RestartCurrent -> {
                    restartCurrentSelection()
                    true
                }

                PreviousTrackAction.PlayPreviousTrack -> {
                    val moved = playAdjacentTrackFromUiAction(-1, false)
                    if (moved) {
                        true
                    } else if (selectedFile != null) {
                        restartCurrentSelection()
                        true
                    } else {
                        false
                    }
                }

                PreviousTrackAction.NoAction -> {
                    false
                }
            }
        }
    }

    AppNavigationPlaybackPollEffects(
        selectedFile = selectedFile,
        isPlayingProvider = { isPlaying },
        selectedFileProvider = { selectedFile },
        isAnimatingProvider = { playerTransition.isAnyAnimating },
        deferredPlaybackSeekProvider = { deferredPlaybackSeek },
        seekInProgress = seekInProgress,
        seekStartedAtMs = seekStartedAtMs,
        seekRequestedAtMs = seekRequestedAtMs,
        seekUiBusyThresholdMs = seekUiBusyThresholdMs,
        duration = effectiveDuration,
        durationOverrideSeconds = playlistDurationOverride,
        subtuneCountProvider = { subtuneCount },
        currentSubtuneIndexProvider = { currentSubtuneIndex },
        activeRepeatModeProvider = { activeRepeatMode },
        currentPlaybackSourceIdProvider = { currentPlaybackSourceId },
        playbackWatchPath = playbackWatchPath,
        metadataTitleProvider = { metadataTitle },
        metadataArtistProvider = { metadataArtist },
        lastBrowserLocationId = lastBrowserLocationId,
        onSeekInProgressChanged = { seekInProgress = it },
        onSeekStartedAtMsChanged = { seekStartedAtMs = it },
        onSeekRequestedAtMsChanged = { seekRequestedAtMs = it },
        onSeekUiBusyChanged = { seekUiBusy = it },
        onDurationChanged = { duration = it },
        onPositionChanged = { position = it },
        onIsPlayingChanged = { isPlaying = it },
        onPlaybackWatchPathChanged = { playbackWatchPath = it },
        onMetadataTitleChanged = { metadataTitle = it },
        onMetadataArtistChanged = { metadataArtist = it },
        onSubtuneCursorChanged = { _ ->
            playbackStateDelegates.applyNativeTrackSnapshot(readNativeTrackSnapshot())
            runtimeDelegates.refreshSubtuneState()
            runtimeDelegates.refreshRepeatModeForTrack()
        },
        onAddRecentPlayedTrack = { path, locationId, title, artist ->
            addRecentPlayedTrackFromPlaybackContext(path, locationId, title, artist)
        },
        onPlayAdjacentTrack = { offset, wrapOverride, notifyWrap ->
            playAdjacentActivePlaylistEntryAction(offset, wrapOverride, notifyWrap)
        },
        onRestartCurrentTrack = {
            NativeBridge.seekTo(0.0)
            position = 0.0
            runtimeDelegates.syncPlaybackService()
        },
        onStopPlaybackAndUnload = {
            stopAndEmptyTrackAction(context, playbackStateDelegates)
        },
        isLocalPlayableFile = isLocalPlayableFile
    )

    AppNavigationTrackPreferenceEffects(
        context = context,
        prefs = prefs,
        selectedFile = selectedFile,
        currentPlaybackSourceId = currentPlaybackSourceId,
        currentPlaybackRequestUrl = currentPlaybackRequestUrl,
        artworkReloadToken = artworkReloadToken,
        preferredRepeatMode = preferredRepeatMode,
        isPlayerSurfaceVisible = isPlayerSurfaceVisible,
        autoPlayOnTrackSelect = autoPlayOnTrackSelect,
        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
        autoPlayNextTrackOnEnd = autoPlayNextTrackOnEnd,
        preloadNextCachedRemoteTrack = preloadNextCachedRemoteTrack,
        playlistWrapNavigation = playlistWrapNavigation,
        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
        fadePauseResume = fadePauseResume,
        rememberBrowserLocation = rememberBrowserLocation,
        showParentDirectoryEntry = showParentDirectoryEntry,
        showFileIconChipBackground = showFileIconChipBackground,
        sortArchivesBeforeFiles = sortArchivesBeforeFiles,
        browserNameSortMode = browserNameSortMode,
        onArtworkBitmapChanged = { artworkBitmap = it },
        onArtworkResolvedTrackKeyChanged = { artworkResolvedTrackKey = it },
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        resetSubtuneUiState = {
            subtuneCount = 0
            currentSubtuneIndex = 0
            subtuneEntries = emptyList()
            showSubtuneSelectorDialog = false
            showPlaylistSelectorDialog = false
            showPlaylistOpenActionDialog = false
            showPlaylistPreviewDialog = false
            pendingPlaylistSubtuneSelection = null
            pendingBrowserPlaylistDocument = null
        },
        onRememberBrowserLocationCleared = {
            lastBrowserLocationId = null
            lastBrowserDirectoryPath = null
        }
    )

    val cancelRemoteNextTrackPreload = rememberRemoteNextTrackPreloadCanceller(
        context = context,
        isPlaying,
        selectedFile = selectedFile,
        currentPlaybackSourceId = currentPlaybackSourceId,
        currentPlaybackRequestUrl = currentPlaybackRequestUrl,
        activeRepeatMode = activeRepeatMode,
        preloadNextCachedRemoteTrack = preloadNextCachedRemoteTrack,
        playlistWrapNavigation = playlistWrapNavigation,
        urlOrPathForceCaching = urlOrPathForceCaching,
        visiblePlayableFiles = visiblePlayableFiles,
        visiblePlayableSourceIds = RemotePlayableSourceIdsHolder.current
    )

    AppNavigationCoreEffectsFromSettingsStates(
        prefs = prefs,
        settingsStates = settingsStates,
        unknownTrackDurationSeconds = unknownTrackDurationSeconds,
        applyCoreOptionWithPolicyFn = { coreName, optionName, optionValue, policy, optionLabel ->
            playbackStateDelegates.applyCoreOptionWithPolicy(
                coreName = coreName,
                optionName = optionName,
                optionValue = optionValue,
                policy = policy,
                optionLabel = optionLabel
            )
        }
    )

    val notificationOpenSignal = MainActivity.notificationOpenPlayerSignal

    AppNavigationPlaybackEffects(
        context = context,
        prefs = prefs,
        respondHeadphoneMediaButtons = respondHeadphoneMediaButtons,
        pauseOnHeadphoneDisconnect = pauseOnHeadphoneDisconnect,
        audioBackendPreference = audioBackendPreference,
        audioPerformanceMode = audioPerformanceMode,
        audioBufferPreset = audioBufferPreset,
        audioResamplerPreference = audioResamplerPreference,
        audioOutputLimiterEnabled = audioOutputLimiterEnabled,
        audioAllowBackendFallback = audioAllowBackendFallback,
        pendingSoxExperimentalDialog = pendingSoxExperimentalDialog,
        onPendingSoxExperimentalDialogChanged = { pendingSoxExperimentalDialog = it },
        onShowSoxExperimentalDialogChanged = { showSoxExperimentalDialog = it },
        openPlayerFromNotification = openPlayerFromNotification,
        persistRepeatMode = persistRepeatMode,
        preferredRepeatMode = preferredRepeatMode,
        selectedFile = selectedFile,
        currentPlaybackSourceId = currentPlaybackSourceId,
        isPlaying = isPlaying,
        metadataTitle = effectiveMetadataTitle,
        metadataArtist = effectiveMetadataArtist,
        duration = effectiveDuration,
        notificationOpenSignal = notificationOpenSignal,
        syncPlaybackService = playbackSessionCoordinator.syncPlaybackService,
        restorePlayerStateFromSessionAndNative = playbackSessionCoordinator.restorePlayerStateFromSessionAndNative
    )
    LaunchedEffect(currentPlaybackSourceId, metadataTitle, metadataArtist) {
        val sourceId = currentPlaybackSourceId ?: return@LaunchedEffect
        applyNetworkSourceMetadata(sourceId, metadataTitle, metadataArtist)
    }

    if (!storagePermissionState.hasPermission) {
        StoragePermissionRequiredScreen(
            onRequestPermission = storagePermissionState.requestPermission
        )
        return
    }

    @Composable
    fun AppNavigationMainContent() {
    val miniPlayerListInset = rememberMiniPlayerListInset(
        currentView = currentView,
        isPlayerSurfaceVisible = isPlayerSurfaceVisible
    )
    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val miniPreviewLiftPx = with(LocalDensity.current) { 28.dp.toPx() }
    val stopAndEmptyTrackBase = buildStopAndEmptyTrackDelegate(
        context = context,
        playbackStateDelegates = playbackStateDelegates
    )
    val stopAndEmptyTrack: () -> Unit = {
        lastStoppedPlaylistResume = activePlaylistMetadataEntry
            ?.let { entry ->
                activePlaylist?.let { playlist ->
                    LastStoppedPlaylistResume(
                        playlist = playlist,
                        entryId = entry.id
                    )
                }
            }
        trackLoadDelegates.cancelPendingTrackSelection()
        manualOpenDelegates.cancelPendingManualInputSelection()
        deferredPlaybackSeek = null
        cancelRemoteNextTrackPreload()
        remoteLoadJob?.cancel()
        remoteLoadJob = null
        remoteLoadUiState = null
        stopAndEmptyTrackBase()
    }
    val activeCoreNameForUi = lastUsedCoreName
    val currentCorePluginName = pluginNameForCoreName(activeCoreNameForUi)
    val canOpenCurrentCoreSettings = currentCorePluginName != null
    val visualizationUiState = rememberVisualizationUiState(
        prefs = prefs,
        activeCoreName = activeCoreNameForUi,
        isPlayerSurfaceVisible = isPlayerSurfaceVisible
    )
    val visualizationMode = visualizationUiState.mode
    val enabledVisualizationModes = visualizationUiState.enabledModes
    val availableVisualizationModes = visualizationUiState.availableModes
    val cycleVisualizationMode = visualizationUiState.onCycleMode
    val setVisualizationMode = visualizationUiState.onSelectMode
    val setEnabledVisualizationModes = visualizationUiState.onSetEnabledModes
    val settingsNavigationCoordinator = buildSettingsNavigationCoordinator(
        currentView = currentView,
        settingsRoute = settingsRoute,
        settingsRouteHistory = settingsRouteHistory,
        settingsReturnView = settingsReturnView,
        lastUsedCoreName = lastUsedCoreName,
        setSettingsRoute = { settingsRoute = it },
        setSettingsRouteHistory = { settingsRouteHistory = it },
        setSettingsLaunchedFromPlayer = { settingsLaunchedFromPlayer = it },
        setSettingsReturnView = { settingsReturnView = it },
        setCurrentView = { currentView = it },
        setSelectedPluginName = { selectedPluginName = it },
        setPlayerExpanded = { isPlayerExpanded = it }
    )
    val openSettingsRoute = settingsNavigationCoordinator.openSettingsRoute
    val popSettingsRoute = settingsNavigationCoordinator.popSettingsRoute
    val exitSettingsToReturnView = settingsNavigationCoordinator.exitSettingsToReturnView
    val openCurrentCoreSettings = settingsNavigationCoordinator.openCurrentCoreSettings
    val openVisualizationSettings = settingsNavigationCoordinator.openVisualizationSettings
    val openSelectedVisualizationSettings: () -> Unit = {
        settingsNavigationCoordinator.openSelectedVisualizationSettings(visualizationMode)
    }

    AppNavigationUiEffects(
        context = context,
        prefs = prefs,
        keepScreenOn = keepScreenOn,
        isPlayerExpanded = isPlayerExpanded,
        playerArtworkCornerRadiusDp = playerArtworkCornerRadiusDp,
        onPlayerArtworkCornerRadiusChanged = { playerArtworkCornerRadiusDp = it },
        onMiniExpandPreviewProgressChanged = { playerTransition.miniExpandPreviewProgress = it },
        onExpandFromMiniDragChanged = { playerTransition.expandFromMiniDrag = it },
        onCollapseFromSwipeChanged = { playerTransition.collapseFromSwipe = it }
    )
    LaunchedEffect(
        playerTransition.expandedOverlaySettledVisible,
        isPlayerExpanded,
        playerTransition.expandFromMiniDrag
    ) {
        if (
            playerTransition.expandedOverlaySettledVisible &&
            isPlayerExpanded &&
            playerTransition.expandFromMiniDrag
        ) {
            playerTransition.expandFromMiniDrag = false
        }
    }

    val hidePlayerSurface = buildHidePlayerSurfaceDelegate(
        onPlayerExpandedChanged = { isPlayerExpanded = it },
        onPlayerSurfaceVisibleChanged = { isPlayerSurfaceVisible = it }
    )
    RegisterPlaybackBroadcastReceiver(
        context = context,
        onCleared = {
            trackLoadDelegates.cancelPendingTrackSelection()
            deferredPlaybackSeek = null
            playbackStateDelegates.resetAndOptionallyKeepLastTrack(keepLastTrack = true)
        },
        onPreviousTrackRequested = { playPreviousTrackFromUiAction() },
        onNextTrackRequested = { playAdjacentTrackFromUiAction(1, true) },
        onRepeatModeChanged = { mode ->
            preferredRepeatMode = mode
            activeRepeatMode = mode
        }
    )

    AppNavigationBackHandlers(
        context = context,
        currentView = currentView,
        isPlayerExpanded = isPlayerExpanded,
        isPlayerSurfaceVisible = isPlayerSurfaceVisible,
        settingsLaunchedFromPlayer = settingsLaunchedFromPlayer,
        showUrlOrPathDialog = showUrlOrPathDialog,
        showMiniPlayerFocusHighlight = showMiniPlayerFocusHighlight,
        onRestoreMiniPlayerFocusOnCollapseChanged = { restoreMiniPlayerFocusOnCollapse = it },
        onPlayerExpandedChanged = { isPlayerExpanded = it },
        onCollapseDragInProgressChanged = { playerTransition.collapseDragInProgress = it },
        onExpandedOverlaySettledVisibleChanged = { playerTransition.expandedOverlaySettledVisible = it },
        popSettingsRoute = popSettingsRoute,
        exitSettingsToReturnView = exitSettingsToReturnView,
        onCurrentViewChanged = { currentView = it }
    )

    @Composable
    fun BoxScope.PlayerOverlayAndDialogsSection() {
        val resumeLastStoppedPlayback: (Boolean) -> Boolean = { autoStart ->
            val playlistResume = lastStoppedPlaylistResume
            val resumedFromPlaylist = if (playlistResume != null) {
                val playlistEntry = playlistResume.playlist.entries.firstOrNull { it.id == playlistResume.entryId }
                val localFile = playlistEntry?.let { resolvePlaylistEntryLocalFile(it.source) }
                when {
                    playlistEntry == null -> {
                        lastStoppedPlaylistResume = null
                        false
                    }

                    localFile != null && (!localFile.exists() || !localFile.isFile) -> {
                        lastStoppedPlaylistResume = null
                        false
                    }

                    else -> {
                        openPlaylistEntry(
                            context = context,
                            entry = playlistEntry,
                            playlist = playlistResume.playlist,
                            trackLoadDelegates = trackLoadDelegates,
                            manualOpenDelegates = manualOpenDelegates,
                            autoPlayOnTrackSelect = autoStart,
                            openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                            expandOverride = isPlayerExpanded,
                            onActivePlaylistChanged = { activePlaylist = it },
                            onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                            onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
                        )
                        true
                    }
                }
            } else {
                false
            }
            if (resumedFromPlaylist) {
                true
            } else {
                trackNavDelegates.resumeLastStoppedTrack(autoStart = autoStart)
            }
        }
        val canResumeStoppedTrack = lastStoppedPlaylistResume != null ||
            lastStoppedFile?.exists() == true ||
            !lastStoppedSourceId.isNullOrBlank()
        val startPlaybackFromSurface = buildStartPlaybackFromSurfaceAction(
            selectedFileProvider = { selectedFile },
            currentPlaybackSourceIdProvider = { currentPlaybackSourceId },
            lastBrowserLocationIdProvider = { lastBrowserLocationId },
            metadataTitleProvider = { effectiveMetadataTitle },
            metadataArtistProvider = { effectiveMetadataArtist },
            activeRepeatModeProvider = { activeRepeatMode },
            isLocalPlayableFile = isLocalPlayableFile,
            addRecentPlayedTrack = { path, locationId, title, artist ->
                addRecentPlayedTrackFromPlaybackContext(path, locationId, title, artist)
            },
            applyRepeatModeToNative = { mode -> applyRepeatModeToNative(mode) },
            startEngine = { startEngineWithPauseResumeFade() },
            onPlayingStateChanged = { isPlaying = it },
            scheduleRecentTrackMetadataRefresh = { sourceId, locationId ->
                scheduleRecentTrackMetadataRefreshFromPlaybackContext(sourceId, locationId)
            },
            syncPlaybackService = playbackSessionCoordinator.syncPlaybackService,
            resumeLastStoppedTrack = { autoStart ->
                resumeLastStoppedPlayback(autoStart)
            }
        )
        val startPlaybackFromSurfaceWithDeferredSeek: () -> Unit = {
            val activeSelectedFile = selectedFile
            val activeSourceId = currentPlaybackSourceId ?: activeSelectedFile?.absolutePath
            val pendingSeek = deferredPlaybackSeek
            if (
                activeSelectedFile != null &&
                pendingSeek != null &&
                activeSourceId == pendingSeek.sourceId
            ) {
                val maxDuration = duration.coerceAtLeast(0.0)
                val clampedSeekSeconds = if (maxDuration > 0.0) {
                    pendingSeek.positionSeconds.coerceIn(0.0, maxDuration)
                } else {
                    pendingSeek.positionSeconds.coerceAtLeast(0.0)
                }
                deferredPlaybackSeek = null
                if (NativeBridge.getTrackSampleRate() <= 0) {
                    trackLoadDelegates.applyTrackSelection(
                        file = activeSelectedFile,
                        autoStart = true,
                        sourceIdOverride = activeSourceId,
                        initialSeekSeconds = clampedSeekSeconds
                    )
                } else {
                    if (clampedSeekSeconds > 0.0) {
                        NativeBridge.seekTo(clampedSeekSeconds)
                    }
                    position = clampedSeekSeconds
                    startPlaybackFromSurface()
                }
            } else {
                startPlaybackFromSurface()
            }
        }
        val openAudioEffectsDialog = buildOpenAudioEffectsDialogAction(
            masterVolumeDbProvider = { masterVolumeDb },
            pluginVolumeDbProvider = { pluginVolumeDb },
            songVolumeDbProvider = { songVolumeDb },
            ignoreCoreVolumeForSongProvider = { ignoreCoreVolumeForSong },
            forceMonoProvider = { forceMono },
            onTempMasterVolumeChanged = { tempMasterVolumeDb = it },
            onTempPluginVolumeChanged = { tempPluginVolumeDb = it },
            onTempSongVolumeChanged = { tempSongVolumeDb = it },
            onTempIgnoreCoreVolumeForSongChanged = { tempIgnoreCoreVolumeForSong = it },
            onTempForceMonoChanged = { tempForceMono = it },
            onShowAudioEffectsDialogChanged = { showAudioEffectsDialog = it }
        )
        AppNavigationPlayerOverlaysSection(
            miniPlayerFocusRequester = miniPlayerFocusRequester,
            isPlayerSurfaceVisible = isPlayerSurfaceVisible,
            isPlayerExpanded = isPlayerExpanded,
            miniExpandPreviewProgress = playerTransition.miniExpandPreviewProgress,
            onMiniExpandPreviewProgressChanged = { playerTransition.miniExpandPreviewProgress = it },
            expandFromMiniDrag = playerTransition.expandFromMiniDrag,
            onExpandFromMiniDragChanged = { playerTransition.expandFromMiniDrag = it },
            collapseFromSwipe = playerTransition.collapseFromSwipe,
            onCollapseFromSwipeChanged = { playerTransition.collapseFromSwipe = it },
            onCollapseDragProgressChanged = { playerTransition.collapseDragInProgress = it },
            onExpandedOverlayCurrentVisibleChanged = { playerTransition.expandedOverlayCurrentVisible = it },
            onExpandedOverlaySettledVisibleChanged = { playerTransition.expandedOverlaySettledVisible = it },
            onPlayerExpandedChanged = { expanded ->
                if (
                    isPlayerExpanded &&
                    !expanded &&
                    isPlayerSurfaceVisible &&
                    showMiniPlayerFocusHighlight
                ) {
                    restoreMiniPlayerFocusOnCollapse = true
                }
                isPlayerExpanded = expanded
                if (!expanded) {
                    playerTransition.collapseDragInProgress = false
                    playerTransition.expandedOverlaySettledVisible = false
                }
            },
            screenHeightPx = screenHeightPx,
            miniPreviewLiftPx = miniPreviewLiftPx,
            selectedFile = selectedFile,
            isPlaying = isPlaying,
            playbackStartInProgress = playbackStartInProgress,
            seekUiBusy = seekUiBusy,
            durationSeconds = effectiveDuration,
            positionSecondsState = positionState,
            metadataTitle = effectiveMetadataTitle,
            metadataArtist = effectiveMetadataArtist,
            metadataSampleRate = metadataSampleRate,
            metadataChannelCount = metadataChannelCount,
            metadataBitDepthLabel = metadataBitDepthLabel,
            decoderName = activeCoreNameForUi,
            playbackSourceLabel = playbackSourceLabel,
            pathOrUrl = currentTrackPathOrUrl,
            playlistTitle = trackInfoPlaylistTitle,
            playlistFormatLabel = trackInfoPlaylistFormatLabel,
            playlistTrackCount = trackInfoPlaylistTrackCount,
            playlistPathOrUrl = trackInfoPlaylistPathOrUrl,
            artworkBitmap = displayedArtworkBitmap,
            artworkSwipePreviewState = artworkSwipePreviewState,
            isCurrentTrackFavorited = isCurrentTrackFavorited,
            activeRepeatMode = activeRepeatMode,
            playbackCapabilitiesFlags = effectivePlaybackCapabilitiesFlags,
            canOpenCurrentCoreSettings = canOpenCurrentCoreSettings,
            openCurrentCoreSettings = openCurrentCoreSettings,
            visualizationMode = visualizationMode,
            availableVisualizationModes = availableVisualizationModes,
            cycleVisualizationMode = cycleVisualizationMode,
            setVisualizationMode = setVisualizationMode,
            openVisualizationSettings = openVisualizationSettings,
            openSelectedVisualizationSettings = openSelectedVisualizationSettings,
            visualizationBarCount = visualizationBarCount,
            visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
            visualizationBarRoundnessDp = visualizationBarRoundnessDp,
            visualizationBarOverlayArtwork = visualizationBarOverlayArtwork,
            visualizationBarUseThemeColor = visualizationBarUseThemeColor,
            visualizationBarRenderBackend = visualizationBarRenderBackend,
            visualizationOscStereo = visualizationOscStereo,
            visualizationVuAnchor = visualizationVuAnchor,
            visualizationVuUseThemeColor = visualizationVuUseThemeColor,
            visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
            visualizationVuRenderBackend = visualizationVuRenderBackend,
            visualizationShowDebugInfo = visualizationShowDebugInfo,
            playerArtworkCornerRadiusDp = playerArtworkCornerRadiusDp,
            filenameDisplayMode = filenameDisplayMode,
            filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
            externalTrackInfoDialogRequestToken = externalTrackInfoDialogRequestToken,
            showMiniPlayerFocusHighlight = showMiniPlayerFocusHighlight,
            onHardwareNavigationInput = { showMiniPlayerFocusHighlight = true },
            onTouchInteraction = {
                showMiniPlayerFocusHighlight = false
                focusManager.clearFocus(force = true)
            },
            onMiniPlayerNavigateUpRequested = {
                if (currentView == MainView.Browser) {
                    mainContentFocusRequester.requestFocus()
                    browserFocusRestoreRequestToken += 1
                    return@AppNavigationPlayerOverlaysSection
                }
                mainContentFocusRequester.requestFocus()
                val restored = mainContentFocusRequester.restoreFocusedChild()
                if (!restored) {
                    val moved = focusManager.moveFocus(FocusDirection.Up)
                    if (!moved) {
                        focusManager.moveFocus(FocusDirection.Up)
                    }
                }
            },
            onMiniPlayerExpandRequested = { restoreMiniPlayerFocusOnCollapse = true },
            canResumeStoppedTrack = canResumeStoppedTrack,
            onHidePlayerSurface = { hidePlayerSurface() },
            onPreviousTrack = { playPreviousTrackFromUiAction() },
            onForcePreviousTrack = { playAdjacentTrackFromUiAction(-1, false) },
            onNextTrack = { playAdjacentTrackFromUiAction(1, true) },
            onPlayPause = {
                if (selectedFile == null) {
                    resumeLastStoppedPlayback(true)
                } else if (isPlaying) {
                    pauseEngineWithPauseResumeFade {
                        isPlaying = false
                        playbackSessionCoordinator.syncPlaybackService()
                    }
                } else {
                    startPlaybackFromSurfaceWithDeferredSeek()
                }
            },
            onPlay = startPlaybackFromSurfaceWithDeferredSeek,
            onStopAndClear = stopAndEmptyTrack,
            onToggleFavoriteTrack = toggleCurrentTrackFavoriteAction,
            onOpenAudioEffects = openAudioEffectsDialog,
            onPause = {
                pauseEngineWithPauseResumeFade {
                    isPlaying = false
                    playbackSessionCoordinator.syncPlaybackService()
                }
            },
            canPreviousTrack = selectedFile != null,
            canNextTrack = selectedFile != null,
            previousRestartsAfterThreshold = previousRestartsAfterThreshold,
            onSeek = { seconds ->
                if (!seekInProgress) {
                    val activeSourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath
                    val pendingSeek = deferredPlaybackSeek
                    if (
                        pendingSeek != null &&
                        activeSourceId == pendingSeek.sourceId
                    ) {
                        val maxDuration = duration.coerceAtLeast(0.0)
                        val clamped = if (maxDuration > 0.0) {
                            seconds.coerceIn(0.0, maxDuration)
                        } else {
                            seconds.coerceAtLeast(0.0)
                        }
                        if (NativeBridge.getTrackSampleRate() <= 0) {
                            deferredPlaybackSeek = pendingSeek.copy(positionSeconds = clamped)
                            position = clamped
                            playbackSessionCoordinator.syncPlaybackService()
                        } else {
                            deferredPlaybackSeek = null
                            NativeBridge.seekTo(clamped)
                            seekRequestedAtMs = SystemClock.elapsedRealtime()
                            seekUiBusy = false
                            position = clamped
                            playbackSessionCoordinator.syncPlaybackService()
                        }
                    } else {
                        NativeBridge.seekTo(seconds)
                        seekRequestedAtMs = SystemClock.elapsedRealtime()
                        seekUiBusy = false
                        position = seconds
                        playbackSessionCoordinator.syncPlaybackService()
                    }
                }
            },
            onPreviousSubtune = {
                val target = (currentSubtuneIndex - 1).coerceAtLeast(0)
                if (target != currentSubtuneIndex) {
                    playbackStateDelegates.selectSubtune(target)
                }
            },
            onNextSubtune = {
                val maxIndex = (subtuneCount - 1).coerceAtLeast(0)
                val target = (currentSubtuneIndex + 1).coerceAtMost(maxIndex)
                if (target != currentSubtuneIndex) {
                    playbackStateDelegates.selectSubtune(target)
                }
            },
            onOpenSubtuneSelector = {
                if (subtuneCount > 1) {
                    runtimeDelegates.refreshSubtuneEntries()
                    showSubtuneSelectorDialog = true
                } else {
                    Toast.makeText(context, "No subtunes available", Toast.LENGTH_SHORT).show()
                }
            },
            canPreviousSubtune = transportSubtuneCount > 1 && transportCurrentSubtuneIndex > 0,
            canNextSubtune = transportSubtuneCount > 1 && transportCurrentSubtuneIndex < (transportSubtuneCount - 1),
            canOpenSubtuneSelector = transportSubtuneCount > 1,
            canOpenPlaylistSelector = activePlaylist?.entries?.isNotEmpty() == true,
            onOpenPlaylistSelector = { showPlaylistSelectorDialog = true },
            currentSubtuneIndex = transportCurrentSubtuneIndex,
            subtuneCount = transportSubtuneCount,
            titleCurrentSubtuneIndex = titleCurrentSubtuneIndex,
            titleSubtuneCount = titleSubtuneCount,
            subtuneTitleClickable = subtuneTitleClickable,
            onCycleRepeatMode = { runtimeDelegates.cycleRepeatMode() }
        )
        AppNavigationPlaybackDialogsSection(
            prefs = prefs,
            volumeDatabase = volumeDatabase,
            selectedFile = selectedFile,
            lastUsedCoreName = lastUsedCoreName,
            manualOpenDelegates = manualOpenDelegates,
            playbackStateDelegates = playbackStateDelegates,
            onCancelRemoteLoadJob = {
                manualOpenDelegates.cancelPendingManualInputSelection()
                remoteLoadJob?.cancel()
            },
            showUrlOrPathDialog = showUrlOrPathDialog,
            urlOrPathInput = urlOrPathInput,
            urlOrPathForceCaching = urlOrPathForceCaching,
            onUrlOrPathInputChanged = { urlOrPathInput = it },
            onUrlOrPathForceCachingChanged = { urlOrPathForceCaching = it },
            onShowUrlOrPathDialogChanged = { showUrlOrPathDialog = it },
            remoteLoadUiState = remoteLoadUiState,
            onRemoteLoadUiStateChanged = { remoteLoadUiState = it },
            showSoxExperimentalDialog = showSoxExperimentalDialog,
            onShowSoxExperimentalDialogChanged = { showSoxExperimentalDialog = it },
            showSubtuneSelectorDialog = showSubtuneSelectorDialog,
            subtuneEntries = subtuneEntries,
            currentSubtuneIndex = currentSubtuneIndex,
            onShowSubtuneSelectorDialogChanged = { showSubtuneSelectorDialog = it },
            showPlaylistSelectorDialog = showPlaylistSelectorDialog,
            playlistDialogTitle = "Playlist",
            playlistDialogSubtitle = activePlaylist?.title,
            playlistDialogShuffleActive = activePlaylistShuffleActive,
            playlistEntries = activePlaylist?.entries.orEmpty(),
            currentPlaylistEntryId = activePlaylistEntryId,
            onShowPlaylistSelectorDialogChanged = { showPlaylistSelectorDialog = it },
            onSelectPlaylistEntry = { playPlaylistEntryAction(it, activePlaylist) },
            showPlaylistOpenActionDialog = showPlaylistOpenActionDialog,
            playlistOpenActionTitle = pendingBrowserPlaylistDocument?.title ?: "Playlist",
            playlistOpenActionEntryCount = pendingBrowserPlaylistDocument?.entries?.size ?: 0,
            onShowPlaylistOpenActionDialogChanged = { showPlaylistOpenActionDialog = it },
            onDismissPlaylistOpenActionDialog = dismissPendingBrowserPlaylistAction,
            onPlayPlaylistFromFile = playPendingBrowserPlaylistAction,
            onBrowsePlaylistFromFile = browsePendingBrowserPlaylistAction,
            showPlaylistPreviewDialog = showPlaylistPreviewDialog,
            playlistPreviewTitle = "Playlist",
            playlistPreviewSubtitle = pendingBrowserPlaylistDocument?.title,
            playlistPreviewEntries = pendingBrowserPlaylistDocument?.entries.orEmpty(),
            onShowPlaylistPreviewDialogChanged = { showPlaylistPreviewDialog = it },
            onDismissPlaylistPreviewDialog = dismissPendingBrowserPlaylistAction,
            onSelectPlaylistPreviewEntry = openPendingBrowserPlaylistEntryAction,
            showAudioEffectsDialog = showAudioEffectsDialog,
            tempMasterVolumeDb = tempMasterVolumeDb,
            tempPluginVolumeDb = tempPluginVolumeDb,
            tempSongVolumeDb = tempSongVolumeDb,
            tempIgnoreCoreVolumeForSong = tempIgnoreCoreVolumeForSong,
            tempForceMono = tempForceMono,
            masterVolumeDb = masterVolumeDb,
            songVolumeDb = songVolumeDb,
            ignoreCoreVolumeForSong = ignoreCoreVolumeForSong,
            forceMono = forceMono,
            onTempMasterVolumeDbChanged = { tempMasterVolumeDb = it },
            onTempPluginVolumeDbChanged = { tempPluginVolumeDb = it },
            onTempSongVolumeDbChanged = { tempSongVolumeDb = it },
            onTempIgnoreCoreVolumeForSongChanged = { tempIgnoreCoreVolumeForSong = it },
            onTempForceMonoChanged = { tempForceMono = it },
            onMasterVolumeDbChanged = { masterVolumeDb = it },
            onPluginVolumeDbChanged = { pluginVolumeDb = it },
            onSongVolumeDbChanged = { songVolumeDb = it },
            onIgnoreCoreVolumeForSongChanged = { ignoreCoreVolumeForSong = it },
            onForceMonoChanged = { forceMono = it },
            onShowAudioEffectsDialogChanged = { showAudioEffectsDialog = it }
        )
    }

    val settingsRouteContent: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit = { mainPadding ->
        AppNavigationSettingsRouteSection(mainPadding = mainPadding) {
            val settingsPluginCoreActions = buildSettingsPluginCoreActionsFromStateHolders(
                settingsStates = settingsStates,
                onOpenVgmPlayChipSettings = {
                    openSettingsRoute(SettingsRoute.PluginVgmPlayChipSettings, false)
                },
                onPluginSelected = { pluginName ->
                    selectedPluginName = pluginName
                    openSettingsRoute(SettingsRoute.PluginDetail, false)
                },
                onPluginEnabledChanged = { pluginName, enabled ->
                    NativeBridge.setDecoderEnabled(pluginName, enabled)
                    savePluginConfiguration(prefs, pluginName)
                    decoderIconHintsVersion++
                },
                onPluginPriorityChanged = { pluginName, priority ->
                    NativeBridge.setDecoderPriority(pluginName, priority)
                    normalizeDecoderPriorityValues()
                    persistAllPluginConfigurations(prefs)
                    decoderIconHintsVersion++
                },
                onPluginPriorityOrderChanged = { orderedPluginNames ->
                    applyDecoderPriorityOrder(orderedPluginNames, prefs)
                    decoderIconHintsVersion++
                },
                onPluginExtensionsChanged = { pluginName, extensions ->
                    NativeBridge.setDecoderEnabledExtensions(pluginName, extensions)
                    savePluginConfiguration(prefs, pluginName)
                    decoderIconHintsVersion++
                }
            )
            SettingsScreen(
                                route = settingsRoute,
                                bottomContentPadding = miniPlayerListInset,
                                state = buildSettingsScreenStateFromStateHolders(
                                    selectedPluginName = selectedPluginName,
                                    autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                                    openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                                    autoPlayNextTrackOnEnd = autoPlayNextTrackOnEnd,
                                    preloadNextCachedRemoteTrack = preloadNextCachedRemoteTrack,
                                    playlistWrapNavigation = playlistWrapNavigation,
                                    previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                                    fadePauseResume = fadePauseResume,
                                    audioFocusInterrupt = audioFocusInterrupt,
                                    audioDucking = audioDucking,
                                    persistRepeatMode = persistRepeatMode,
                                    themeMode = themeMode,
                                    useMonet = useMonet,
                                    monetAvailable = monetAvailable,
                                    rememberBrowserLocation = rememberBrowserLocation,
                                    showParentDirectoryEntry = showParentDirectoryEntry,
                                    showFileIconChipBackground = showFileIconChipBackground,
                                    sortArchivesBeforeFiles = sortArchivesBeforeFiles,
                                    browserNameSortMode = browserNameSortMode,
                                    recentFoldersLimit = recentFoldersLimit,
                                    recentFilesLimit = recentFilesLimit,
                                    keepScreenOn = keepScreenOn,
                                    playerArtworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                                    filenameDisplayMode = filenameDisplayMode,
                                    filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                                    unknownTrackDurationSeconds = unknownTrackDurationSeconds,
                                    endFadeApplyToAllTracks = endFadeApplyToAllTracks,
                                    endFadeDurationMs = endFadeDurationMs,
                                    endFadeCurve = endFadeCurve,
                                    visualizationMode = visualizationMode,
                                    enabledVisualizationModes = enabledVisualizationModes,
                                    visualizationShowDebugInfo = visualizationShowDebugInfo,
                                    visualizationBarCount = visualizationBarCount,
                                    visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                                    visualizationBarRoundnessDp = visualizationBarRoundnessDp,
                                    visualizationBarOverlayArtwork = visualizationBarOverlayArtwork,
                                    visualizationBarUseThemeColor = visualizationBarUseThemeColor,
                                    visualizationBarRenderBackend = visualizationBarRenderBackend,
                                    visualizationOscStereo = visualizationOscStereo,
                                    visualizationVuAnchor = visualizationVuAnchor,
                                    visualizationVuUseThemeColor = visualizationVuUseThemeColor,
                                    visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                                    visualizationVuRenderBackend = visualizationVuRenderBackend,
                                    ffmpegCapabilities = ffmpegCapabilities,
                                    openMptCapabilities = openMptCapabilities,
                                    vgmPlayCapabilities = vgmPlayCapabilities,
                                    settingsStates = settingsStates
                                ),
                                actions = SettingsScreenActions(
                                    onBack = {
                            if (settingsLaunchedFromPlayer) {
                                exitSettingsToReturnView()
                                return@SettingsScreenActions
                            }
                            if (!popSettingsRoute()) {
                                exitSettingsToReturnView()
                            }
                        },
                                    onOpenAudioPlugins = { openSettingsRoute(SettingsRoute.AudioPlugins, false) },
                                    onOpenGeneralAudio = { openSettingsRoute(SettingsRoute.GeneralAudio, false) },
                                    onOpenHome = { openSettingsRoute(SettingsRoute.Home, false) },
                                    onOpenFileBrowser = { openSettingsRoute(SettingsRoute.FileBrowser, false) },
                                    onOpenNetwork = { openSettingsRoute(SettingsRoute.Network, false) },
                                    onOpenAudioEffects = {
                            openAudioEffectsDialogFromSettings(
                                masterVolumeDb = masterVolumeDb,
                                pluginVolumeDb = pluginVolumeDb,
                                songVolumeDb = songVolumeDb,
                                ignoreCoreVolumeForSong = ignoreCoreVolumeForSong,
                                forceMono = forceMono,
                                onTempMasterVolumeDbChanged = { tempMasterVolumeDb = it },
                                onTempPluginVolumeDbChanged = { tempPluginVolumeDb = it },
                                onTempSongVolumeDbChanged = { tempSongVolumeDb = it },
                                onTempIgnoreCoreVolumeForSongChanged = { tempIgnoreCoreVolumeForSong = it },
                                onTempForceMonoChanged = { tempForceMono = it },
                                onShowAudioEffectsDialogChanged = { showAudioEffectsDialog = it }
                            )
                        },
                                    onClearAllAudioParameters = {
                            clearAllAudioParametersAction(
                                context = context,
                                prefs = prefs,
                                volumeDatabase = volumeDatabase,
                                onMasterVolumeDbChanged = { masterVolumeDb = it },
                                onPluginVolumeDbChanged = { pluginVolumeDb = it },
                                onSongVolumeDbChanged = { songVolumeDb = it },
                                onIgnoreCoreVolumeForSongChanged = { ignoreCoreVolumeForSong = it },
                                onForceMonoChanged = { forceMono = it }
                            )
                        },
                                    onClearPluginAudioParameters = {
                            clearPluginAudioParametersAction(
                                context = context,
                                prefs = prefs,
                                onPluginVolumeDbChanged = { pluginVolumeDb = it }
                            )
                        },
                                    onClearSongAudioParameters = {
                            clearSongAudioParametersAction(
                                context = context,
                                volumeDatabase = volumeDatabase,
                                onSongVolumeDbChanged = { songVolumeDb = it },
                                onIgnoreCoreVolumeForSongChanged = { ignoreCoreVolumeForSong = it }
                            )
                            NativeBridge.setPluginGain(pluginVolumeDb)
                        },
                                    onOpenPlayer = { openSettingsRoute(SettingsRoute.Player, false) },
                                    onOpenVisualization = { openSettingsRoute(SettingsRoute.Visualization, false) },
                                    onOpenVisualizationBasic = { openSettingsRoute(SettingsRoute.VisualizationBasic, false) },
                                    onOpenVisualizationBasicBars = { openSettingsRoute(SettingsRoute.VisualizationBasicBars, false) },
                                    onOpenVisualizationBasicOscilloscope = { openSettingsRoute(SettingsRoute.VisualizationBasicOscilloscope, false) },
                                    onOpenVisualizationBasicVuMeters = { openSettingsRoute(SettingsRoute.VisualizationBasicVuMeters, false) },
                                    onOpenVisualizationAdvanced = { openSettingsRoute(SettingsRoute.VisualizationAdvanced, false) },
                                    onOpenVisualizationAdvancedChannelScope = {
                            openSettingsRoute(SettingsRoute.VisualizationAdvancedChannelScope, false)
                        },
                                    onOpenMisc = { openSettingsRoute(SettingsRoute.Misc, false) },
                                    onOpenUrlCache = { openSettingsRoute(SettingsRoute.UrlCache, false) },
                                    onOpenCacheManager = {
                            refreshCachedSourceFiles()
                            openSettingsRoute(SettingsRoute.CacheManager, false)
                        },
                                    onOpenUi = { openSettingsRoute(SettingsRoute.Ui, false) },
                                    onOpenAbout = { openSettingsRoute(SettingsRoute.About, false) },
                                    pluginCoreActions = settingsPluginCoreActions,
                                    onAutoPlayOnTrackSelectChanged = { autoPlayOnTrackSelect = it },
                                    onOpenPlayerOnTrackSelectChanged = { openPlayerOnTrackSelect = it },
                                    onAutoPlayNextTrackOnEndChanged = { autoPlayNextTrackOnEnd = it },
                                    onPreloadNextCachedRemoteTrackChanged = { preloadNextCachedRemoteTrack = it },
                                    onPlaylistWrapNavigationChanged = { playlistWrapNavigation = it },
                                    onPreviousRestartsAfterThresholdChanged = { previousRestartsAfterThreshold = it },
                                    onFadePauseResumeChanged = { fadePauseResume = it },
                                    onRespondHeadphoneMediaButtonsChanged = { respondHeadphoneMediaButtons = it },
                                    onPauseOnHeadphoneDisconnectChanged = { pauseOnHeadphoneDisconnect = it },
                                    onAudioFocusInterruptChanged = {
                            updateAudioFocusInterruptAction(
                                context = context,
                                prefs = prefs,
                                enabled = it,
                                onAudioFocusInterruptChanged = { audioFocusInterrupt = it }
                            )
                        },
                                    onAudioDuckingChanged = {
                            updateAudioDuckingAction(
                                context = context,
                                prefs = prefs,
                                enabled = it,
                                onAudioDuckingChanged = { audioDucking = it }
                            )
                        },
                                    onAudioBackendPreferenceChanged = { selectedBackend ->
                            updateAudioBackendPreferenceSelection(
                                prefs = prefs,
                                selectedBackend = selectedBackend,
                                currentBackend = audioBackendPreference,
                                currentPerformanceMode = audioPerformanceMode,
                                currentBufferPreset = audioBufferPreset,
                                onAudioBackendPreferenceChanged = { audioBackendPreference = it },
                                onAudioPerformanceModeChanged = { audioPerformanceMode = it },
                                onAudioBufferPresetChanged = { audioBufferPreset = it }
                            )
                        },
                                    onAudioPerformanceModeChanged = { audioPerformanceMode = it },
                                    onAudioBufferPresetChanged = { audioBufferPreset = it },
                                    onAudioResamplerPreferenceChanged = {
                            audioResamplerPreference = it
                            if (it == AudioResamplerPreference.Sox) {
                                pendingSoxExperimentalDialog = true
                            }
                        },
                                    onAudioOutputLimiterEnabledChanged = { audioOutputLimiterEnabled = it },
                                    onAudioAllowBackendFallbackChanged = { audioAllowBackendFallback = it },
                                    onOpenPlayerFromNotificationChanged = { openPlayerFromNotification = it },
                                    onPersistRepeatModeChanged = { persistRepeatMode = it },
                                    onThemeModeChanged = onThemeModeChanged,
                                    onUseMonetChanged = onUseMonetChanged,
                                    onRememberBrowserLocationChanged = { rememberBrowserLocation = it },
                                    onShowParentDirectoryEntryChanged = { showParentDirectoryEntry = it },
                                    onShowFileIconChipBackgroundChanged = { showFileIconChipBackground = it },
                                    onSortArchivesBeforeFilesChanged = { sortArchivesBeforeFiles = it },
                                    onBrowserNameSortModeChanged = { browserNameSortMode = it },
                                    onRecentFoldersLimitChanged = { recentFoldersLimit = it.coerceIn(1, RECENTS_LIMIT_MAX) },
                                    onRecentFilesLimitChanged = { recentFilesLimit = it.coerceIn(1, RECENTS_LIMIT_MAX) },
                                    onUrlCacheClearOnLaunchChanged = { enabled ->
                            updateUrlCacheClearOnLaunchAction(
                                prefs = prefs,
                                enabled = enabled,
                                onUrlCacheClearOnLaunchChanged = { urlCacheClearOnLaunch = it }
                            )
                        },
                                    onUrlCacheMaxTracksChanged = { value ->
                            updateUrlCacheMaxTracksAction(
                                value = value,
                                prefs = prefs,
                                appScope = appScope,
                                cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                                urlCacheMaxBytes = urlCacheMaxBytes,
                                onUrlCacheMaxTracksChanged = { urlCacheMaxTracks = it },
                                onRefreshCachedSourceFiles = refreshCachedSourceFiles
                            )
                        },
                                    onUrlCacheMaxBytesChanged = { value ->
                            updateUrlCacheMaxBytesAction(
                                value = value,
                                prefs = prefs,
                                appScope = appScope,
                                cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                                urlCacheMaxTracks = urlCacheMaxTracks,
                                onUrlCacheMaxBytesChanged = { urlCacheMaxBytes = it },
                                onRefreshCachedSourceFiles = refreshCachedSourceFiles
                            )
                        },
                                    onArchiveCacheClearOnLaunchChanged = { enabled ->
                            updateArchiveCacheClearOnLaunchAction(
                                prefs = prefs,
                                enabled = enabled,
                                onArchiveCacheClearOnLaunchChanged = { archiveCacheClearOnLaunch = it }
                            )
                        },
                                    onArchiveCacheMaxMountsChanged = { value ->
                            updateArchiveCacheMaxMountsAction(
                                value = value,
                                prefs = prefs,
                                appScope = appScope,
                                cacheDir = context.cacheDir,
                                archiveCacheMaxBytes = archiveCacheMaxBytes,
                                archiveCacheMaxAgeDays = archiveCacheMaxAgeDays,
                                onArchiveCacheMaxMountsChanged = { archiveCacheMaxMounts = it }
                            )
                        },
                                    onArchiveCacheMaxBytesChanged = { value ->
                            updateArchiveCacheMaxBytesAction(
                                value = value,
                                prefs = prefs,
                                appScope = appScope,
                                cacheDir = context.cacheDir,
                                archiveCacheMaxMounts = archiveCacheMaxMounts,
                                archiveCacheMaxAgeDays = archiveCacheMaxAgeDays,
                                onArchiveCacheMaxBytesChanged = { archiveCacheMaxBytes = it }
                            )
                        },
                                    onArchiveCacheMaxAgeDaysChanged = { value ->
                            updateArchiveCacheMaxAgeDaysAction(
                                value = value,
                                prefs = prefs,
                                appScope = appScope,
                                cacheDir = context.cacheDir,
                                archiveCacheMaxMounts = archiveCacheMaxMounts,
                                archiveCacheMaxBytes = archiveCacheMaxBytes,
                                onArchiveCacheMaxAgeDaysChanged = { archiveCacheMaxAgeDays = it }
                            )
                        },
                                    onClearUrlCacheNow = {
                            clearUrlCacheNowAction(
                                context = context,
                                prefs = prefs,
                                appScope = appScope,
                                cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                                selectedFile = selectedFile,
                                onRefreshCachedSourceFiles = refreshCachedSourceFiles
                            )
                        },
                                    onClearArchiveCacheNow = {
                            clearArchiveCacheNowAction(
                                context = context,
                                appScope = appScope,
                                cacheDir = context.cacheDir
                            )
                        },
                                    onRefreshCachedSourceFiles = refreshCachedSourceFiles,
                                    onDeleteCachedSourceFiles = { paths ->
                            deleteCachedSourceFilesAction(
                                context = context,
                                prefs = prefs,
                                appScope = appScope,
                                cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                                selectedFile = selectedFile,
                                absolutePaths = paths,
                                onRefreshCachedSourceFiles = refreshCachedSourceFiles
                            )
                        },
                                    onExportCachedSourceFiles = { paths ->
                            exportCachedSourceFilesAction(
                                context = context,
                                paths = paths,
                                onPendingCacheExportPathsChanged = { pendingCacheExportPaths = it },
                                launchDirectoryPicker = { cacheExportDirectoryLauncher.launch(null) }
                            )
                        },
                                    onKeepScreenOnChanged = { keepScreenOn = it },
                                    onPlayerArtworkCornerRadiusDpChanged = { value ->
                            playerArtworkCornerRadiusDp = value.coerceIn(0, 48)
                        },
                                    onFilenameDisplayModeChanged = { mode ->
                            filenameDisplayMode = mode
                            prefs.edit().putString(AppPreferenceKeys.FILENAME_DISPLAY_MODE, mode.storageValue).apply()
                        },
                                    onFilenameOnlyWhenTitleMissingChanged = { enabled ->
                            filenameOnlyWhenTitleMissing = enabled
                            prefs.edit().putBoolean(AppPreferenceKeys.FILENAME_ONLY_WHEN_TITLE_MISSING, enabled).apply()
                        },
                                    onUnknownTrackDurationSecondsChanged = { value ->
                            unknownTrackDurationSeconds = value
                        },
                                    onEndFadeApplyToAllTracksChanged = { enabled ->
                            endFadeApplyToAllTracks = enabled
                        },
                                    onEndFadeDurationMsChanged = { value ->
                            endFadeDurationMs = value
                        },
                                    onEndFadeCurveChanged = { curve ->
                            endFadeCurve = curve
                        },
                                    onVisualizationModeChanged = { mode ->
                            setVisualizationMode(mode)
                        },
                                    onEnabledVisualizationModesChanged = { modes ->
                            setEnabledVisualizationModes(modes)
                        },
                                    onVisualizationShowDebugInfoChanged = { enabled ->
                            visualizationShowDebugInfo = enabled
                        },
                                    onVisualizationBarCountChanged = { value ->
                            visualizationBarCount = value
                        },
                                    onVisualizationBarSmoothingPercentChanged = { value ->
                            visualizationBarSmoothingPercent = value
                        },
                                    onVisualizationBarRoundnessDpChanged = { value ->
                            visualizationBarRoundnessDp = value
                        },
                                    onVisualizationBarOverlayArtworkChanged = { enabled ->
                            visualizationBarOverlayArtwork = enabled
                        },
                                    onVisualizationBarUseThemeColorChanged = { enabled ->
                            visualizationBarUseThemeColor = enabled
                        },
                                    onVisualizationBarRenderBackendChanged = { backend ->
                            visualizationBarRenderBackend = backend
                        },
                                    onVisualizationOscStereoChanged = { enabled ->
                            visualizationOscStereo = enabled
                        },
                                    onVisualizationVuAnchorChanged = { anchor ->
                            visualizationVuAnchor = anchor
                        },
                                    onVisualizationVuUseThemeColorChanged = { enabled ->
                            visualizationVuUseThemeColor = enabled
                        },
                                    onVisualizationVuSmoothingPercentChanged = { value ->
                            visualizationVuSmoothingPercent = value
                        },
                                    onVisualizationVuRenderBackendChanged = { backend ->
                            visualizationVuRenderBackend = backend
                        },
                                    onResetVisualizationBarsSettings = {
                            resetVisualizationBarsSettingsAction(
                                prefs = prefs,
                                onBarCountChanged = { visualizationBarCount = it },
                                onBarSmoothingPercentChanged = { visualizationBarSmoothingPercent = it },
                                onBarRoundnessDpChanged = { visualizationBarRoundnessDp = it },
                                onBarOverlayArtworkChanged = { visualizationBarOverlayArtwork = it },
                                onBarUseThemeColorChanged = { visualizationBarUseThemeColor = it },
                                onBarRenderBackendChanged = { visualizationBarRenderBackend = it }
                            )
                        },
                                    onResetVisualizationOscilloscopeSettings = {
                            resetVisualizationOscilloscopeSettingsAction(
                                prefs = prefs,
                                onVisualizationOscStereoChanged = { visualizationOscStereo = it }
                            )
                        },
                                    onResetVisualizationVuSettings = {
                            resetVisualizationVuSettingsAction(
                                prefs = prefs,
                                onVisualizationVuAnchorChanged = { visualizationVuAnchor = it },
                                onVisualizationVuUseThemeColorChanged = { visualizationVuUseThemeColor = it },
                                onVisualizationVuSmoothingPercentChanged = { visualizationVuSmoothingPercent = it },
                                onVisualizationVuRenderBackendChanged = { visualizationVuRenderBackend = it }
                            )
                        },
                                    onResetVisualizationChannelScopeSettings = {
                            resetVisualizationChannelScopeSettingsAction(
                                prefs = prefs,
                                defaultScopeTextSizeSp = defaultScopeTextSizeSp
                            )
                        },
                                    onClearRecentHistory = {
                            clearRecentHistoryAction(
                                context = context,
                                prefs = prefs,
                                onRecentFoldersChanged = { recentFolders = it },
                                onRecentPlayedFilesChanged = { recentPlayedFiles = it }
                            )
                        },
                                    onClearSavedNetworkSources = {
                            clearSavedNetworkSourcesFromSettings(
                                context = context,
                                prefs = prefs,
                                onNetworkNodesChanged = { networkNodes = it }
                            )
                        },
                                    onClearAllSettings = {
                            clearAllSettingsAndUiState(
                                context = context,
                                prefs = prefs,
                                defaultScopeTextSizeSp = defaultScopeTextSizeSp,
                                onThemeModeChanged = onThemeModeChanged,
                                onUseMonetChanged = onUseMonetChanged,
                                settingsStates = settingsStates,
                                onAutoPlayOnTrackSelectChanged = { autoPlayOnTrackSelect = it },
                                onOpenPlayerOnTrackSelectChanged = { openPlayerOnTrackSelect = it },
                                onAutoPlayNextTrackOnEndChanged = { autoPlayNextTrackOnEnd = it },
                                onPreloadNextCachedRemoteTrackChanged = { preloadNextCachedRemoteTrack = it },
                                onPlaylistWrapNavigationChanged = { playlistWrapNavigation = it },
                                onPreviousRestartsAfterThresholdChanged = { previousRestartsAfterThreshold = it },
                                onFadePauseResumeChanged = { fadePauseResume = it },
                                onPersistRepeatModeChanged = { persistRepeatMode = it },
                                onPreferredRepeatModeChanged = { preferredRepeatMode = it },
                                onRememberBrowserLocationChanged = { rememberBrowserLocation = it },
                                onShowParentDirectoryEntryChanged = { showParentDirectoryEntry = it },
                                onShowFileIconChipBackgroundChanged = { showFileIconChipBackground = it },
                                onBrowserNameSortModeChanged = { browserNameSortMode = it },
                                onLastBrowserLocationIdChanged = { lastBrowserLocationId = it },
                                onLastBrowserDirectoryPathChanged = { lastBrowserDirectoryPath = it },
                                onRecentFoldersLimitChanged = { recentFoldersLimit = it },
                                onRecentFilesLimitChanged = { recentFilesLimit = it },
                                onRecentFoldersChanged = { recentFolders = it },
                                onRecentPlayedFilesChanged = { recentPlayedFiles = it },
                                onKeepScreenOnChanged = { keepScreenOn = it },
                                onPlayerArtworkCornerRadiusDpChanged = { playerArtworkCornerRadiusDp = it },
                                onFilenameDisplayModeChanged = { filenameDisplayMode = it },
                                onFilenameOnlyWhenTitleMissingChanged = { filenameOnlyWhenTitleMissing = it },
                                onUnknownTrackDurationSecondsChanged = { unknownTrackDurationSeconds = it },
                                onEndFadeApplyToAllTracksChanged = { endFadeApplyToAllTracks = it },
                                onEndFadeDurationMsChanged = { endFadeDurationMs = it },
                                onEndFadeCurveChanged = { endFadeCurve = it },
                                onVisualizationModeChanged = { setVisualizationMode(it) },
                                onEnabledVisualizationModesChanged = { setEnabledVisualizationModes(it) },
                                onVisualizationShowDebugInfoChanged = { visualizationShowDebugInfo = it },
                                onVisualizationBarCountChanged = { visualizationBarCount = it },
                                onVisualizationBarSmoothingPercentChanged = { visualizationBarSmoothingPercent = it },
                                onVisualizationBarRoundnessDpChanged = { visualizationBarRoundnessDp = it },
                                onVisualizationBarOverlayArtworkChanged = { visualizationBarOverlayArtwork = it },
                                onVisualizationBarUseThemeColorChanged = { visualizationBarUseThemeColor = it },
                                onVisualizationBarRenderBackendChanged = { visualizationBarRenderBackend = it },
                                onVisualizationOscStereoChanged = { visualizationOscStereo = it },
                                onVisualizationVuAnchorChanged = { visualizationVuAnchor = it },
                                onVisualizationVuUseThemeColorChanged = { visualizationVuUseThemeColor = it },
                                onVisualizationVuSmoothingPercentChanged = { visualizationVuSmoothingPercent = it },
                                onVisualizationVuRenderBackendChanged = { visualizationVuRenderBackend = it },
                                onNetworkNodesChanged = { networkNodes = it },
                                onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged,
                                onActivePlaylistChanged = { activePlaylist = it },
                                onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                                onShowPlaylistSelectorDialogChanged = { showPlaylistSelectorDialog = it }
                            )
                        },
                                    onClearAllPluginSettings = {
                            clearAllPluginSettingsUsingStateHolders(
                                context = context,
                                prefs = prefs,
                                settingsStates = settingsStates
                            )
                        },
                                    onResetPluginSettings = { pluginName ->
                            resetPluginSettingsUsingStateHolders(
                                context = context,
                                prefs = prefs,
                                pluginName = pluginName,
                                settingsStates = settingsStates
                            )
                        },
                                )
                            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val shouldComposeBackgroundContent =
            playerTransition.collapseDragInProgress ||
                !playerTransition.expandedOverlayCurrentVisible ||
                !playerTransition.expandedOverlaySettledVisible
        val backgroundContentStateHolder = rememberSaveableStateHolder()
        val openBrowser: (BrowserOpenRequest) -> Unit = { request ->
            browserNavigator.open(
                request = request,
                keepHistory = currentView == MainView.Browser
            )
        }
        val openPlayerSurfaceAction: () -> Unit = {
            isPlayerSurfaceVisible = true
            isPlayerExpanded = true
            playerTransition.collapseFromSwipe = false
            playerTransition.collapseDragInProgress = false
            playerTransition.expandedOverlayCurrentVisible = false
            playerTransition.expandedOverlaySettledVisible = false
            playerTransition.expandFromMiniDrag = false
            playerTransition.miniExpandPreviewProgress = 0f
        }
        val sortedFavoriteEntries = remember(playlistLibraryState.favorites, favoritesSortMode) {
            sortPlaylistEntries(
                entries = playlistLibraryState.favorites,
                sortMode = favoritesSortMode
            )
        }
        val favoritesPlaybackPlaylist = remember(sortedFavoriteEntries) {
            buildFavoritesPlaybackPlaylist(sortedFavoriteEntries)
        }
        val syncActiveFavoritesContextAfterMutation: (List<PlaylistTrackEntry>) -> Unit = { favorites ->
            val sortedFavorites = sortPlaylistEntries(
                entries = favorites,
                sortMode = favoritesSortMode
            )
            if (activePlaylist?.id == "__favorites__") {
                if (sortedFavorites.isEmpty()) {
                    activePlaylist = null
                    activePlaylistEntryId = null
                    activePlaylistShuffleActive = false
                    pendingPlaylistSubtuneSelection = null
                } else {
                    activePlaylist = buildFavoritesPlaybackPlaylist(sortedFavorites)
                    activePlaylistShuffleActive = false
                    val playbackMatchedEntryId = sortedFavorites.firstOrNull { entry ->
                        playlistEntryMatchesPlayback(
                            entry = entry,
                            activeSourceId = currentTrackPathOrUrl,
                            currentSubtuneIndex = currentSubtuneIndex
                        )
                    }?.id
                    val retainedEntryId = activePlaylistEntryId?.takeIf { currentId ->
                        sortedFavorites.any { it.id == currentId }
                    }
                    activePlaylistEntryId =
                        playbackMatchedEntryId ?: retainedEntryId ?: sortedFavorites.first().id
                }
            }
        }
        LaunchedEffect(favoritesSortMode, playlistLibraryState.favorites, activePlaylist?.id) {
            syncActiveFavoritesContextAfterMutation(playlistLibraryState.favorites)
        }
        if (shouldComposeBackgroundContent) {
            backgroundContentStateHolder.SaveableStateProvider("main_background_content") {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavigationMainContentHost(
                        currentView = currentView,
                        mainContentFocusRequester = mainContentFocusRequester,
                        canFocusMiniPlayer = isPlayerSurfaceVisible && !isPlayerExpanded,
                        requestMiniPlayerFocus = { miniPlayerFocusRequester.requestFocus() },
                        onHardwareNavigationInput = { showMiniPlayerFocusHighlight = true },
                        onTouchInteraction = { showMiniPlayerFocusHighlight = false },
                        onOpenPlayerSurface = openPlayerSurfaceAction,
                        onHomeRequested = { currentView = MainView.Home },
                        onSettingsRequested = {
                            settingsLaunchedFromPlayer = false
                            settingsReturnView =
                                (if (currentView == MainView.Settings) settingsReturnView else currentView)
                                    .takeUnless { it == MainView.Settings }
                                    ?: MainView.Home
                            openSettingsRoute(SettingsRoute.Root, true)
                            currentView = MainView.Settings
                        },
                        context = context,
                        prefs = prefs,
                        currentPlaybackSourceId = currentPlaybackSourceId,
                        currentPlaybackRequestUrl = currentPlaybackRequestUrl,
                        selectedFile = selectedFile,
                        playingPlaylistFile = activePlaylistBrowserFile,
                        metadataTitle = effectiveMetadataTitle,
                        metadataArtist = effectiveMetadataArtist,
                        isPlaying = isPlaying,
                        currentSubtuneIndex = currentSubtuneIndex,
                        recentFolders = recentFolders,
                        recentPlayedFiles = recentPlayedFiles,
                        recentFoldersLimit = recentFoldersLimit,
                        recentFilesLimit = recentFilesLimit,
                        playlistLibraryState = playlistLibraryState,
                        activePlaylist = activePlaylist,
                        favoritesSortMode = favoritesSortMode,
                        networkNodes = networkNodes,
                        storageDescriptors = storageDescriptors,
                        miniPlayerListInset = miniPlayerListInset,
                        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                        autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                        trackLoadDelegates = trackLoadDelegates,
                        manualOpenDelegates = manualOpenDelegates,
                        runtimeDelegates = runtimeDelegates,
                        onRecentFoldersChanged = { recentFolders = it },
                        onRecentPlayedFilesChanged = { recentPlayedFiles = it },
                        onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged,
                        onFavoritesSortModeChange = { favoritesSortMode = it },
                        onOpenFavorite = { entry ->
                            openPlaylistEntry(
                                context = context,
                                entry = entry,
                                playlist = favoritesPlaybackPlaylist,
                                trackLoadDelegates = trackLoadDelegates,
                                manualOpenDelegates = manualOpenDelegates,
                                autoPlayOnTrackSelect = true,
                                openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                                onActivePlaylistChanged = {
                                    activePlaylist = it
                                    activePlaylistShuffleActive = false
                                },
                                onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                                onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
                            )
                        },
                onPlayStoredPlaylist = { playlist ->
                    activePlaylist = playlist
                    activePlaylistEntryId = playlist.entries.firstOrNull()?.id
                    activePlaylistShuffleActive = false
                    playlist.entries.firstOrNull()?.let { entry ->
                        playPlaylistEntryAction(entry, playlist)
                    }
                },
                onShuffleStoredPlaylist = { playlist ->
                    val shuffledEntries = playlist.entries.shuffled()
                    val shuffledPlaylist = playlist.copy(entries = shuffledEntries)
                    activePlaylist = shuffledPlaylist
                    activePlaylistEntryId = shuffledPlaylist.entries.firstOrNull()?.id
                    activePlaylistShuffleActive = true
                    shuffledPlaylist.entries.firstOrNull()?.let { entry ->
                        playPlaylistEntryAction(entry, shuffledPlaylist)
                    }
                },
                onOpenStoredPlaylistEntry = { entry, playlist ->
                    activePlaylist = playlist
                    activePlaylistEntryId = entry.id
                    activePlaylistShuffleActive = false
                    playPlaylistEntryAction(entry, playlist)
                },
                onPlayFavoritePlaylist = {
                    val firstEntry = sortedFavoriteEntries.firstOrNull()
                    if (firstEntry == null) {
                        Toast.makeText(context, "Favorites is empty", Toast.LENGTH_SHORT).show()
                    } else {
                        openPlaylistEntry(
                            context = context,
                            entry = firstEntry,
                            playlist = favoritesPlaybackPlaylist,
                            trackLoadDelegates = trackLoadDelegates,
                            manualOpenDelegates = manualOpenDelegates,
                            autoPlayOnTrackSelect = true,
                            openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                            onActivePlaylistChanged = {
                                activePlaylist = it
                                activePlaylistShuffleActive = false
                            },
                            onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                            onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
                        )
                    }
                },
                onShuffleFavoritePlaylist = {
                    val shuffledEntries = sortedFavoriteEntries.shuffled()
                    val shuffledPlaylist = buildFavoritesPlaybackPlaylist(shuffledEntries)
                    activePlaylist = shuffledPlaylist
                    activePlaylistEntryId = shuffledPlaylist.entries.firstOrNull()?.id
                    activePlaylistShuffleActive = true
                    shuffledPlaylist.entries.firstOrNull()?.let { entry ->
                        openPlaylistEntry(
                            context = context,
                            entry = entry,
                            playlist = shuffledPlaylist,
                            trackLoadDelegates = trackLoadDelegates,
                            manualOpenDelegates = manualOpenDelegates,
                            autoPlayOnTrackSelect = true,
                            openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                            onActivePlaylistChanged = { activePlaylist = it },
                            onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                            onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
                        )
                    }
                },
                onDeleteAllFavorites = {
                    if (playlistLibraryState.favorites.isNotEmpty()) {
                        val updatedState = playlistLibraryState.copy(favorites = emptyList())
                        onPlaylistLibraryStateChanged(updatedState)
                        syncActiveFavoritesContextAfterMutation(updatedState.favorites)
                        Toast.makeText(context, "Favorites cleared", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteFavoriteTrack = { entry ->
                    val updatedState = removeFavoriteTrack(playlistLibraryState, entry.id)
                    if (updatedState != playlistLibraryState) {
                        onPlaylistLibraryStateChanged(updatedState)
                        syncActiveFavoritesContextAfterMutation(updatedState.favorites)
                        Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                    }
                },
                onMoveFavoriteTrack = { entry, offset ->
                    val updatedState = moveFavoriteTrack(playlistLibraryState, entry.id, offset)
                    if (updatedState != playlistLibraryState) {
                        onPlaylistLibraryStateChanged(updatedState)
                        syncActiveFavoritesContextAfterMutation(updatedState.favorites)
                    }
                },
                onPlayFavoriteTrackAsCached = { entry ->
                    val normalizedSource = normalizeSourceIdentity(entry.source) ?: entry.source
                    val cachedFile = findExistingCachedFileForSource(
                        cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                        url = normalizedSource
                    )?.takeIf { it.exists() && it.isFile }
                    if (cachedFile == null) {
                        Toast.makeText(context, "No cached file available", Toast.LENGTH_SHORT).show()
                    } else {
                        activePlaylist = favoritesPlaybackPlaylist
                        activePlaylistEntryId = entry.id
                        activePlaylistShuffleActive = false
                        pendingPlaylistSubtuneSelection =
                            entry.subtuneIndex?.let { PendingPlaylistSubtuneSelection(entry.source, it) }
                        trackLoadDelegates.applyTrackSelection(
                            file = cachedFile,
                            autoStart = true,
                            expandOverride = openPlayerOnTrackSelect,
                            sourceIdOverride = entry.source,
                            initialSubtuneIndex = entry.subtuneIndex
                        )
                    }
                },
                onOpenFavoriteTrackLocation = { entry ->
                    val localFile = resolvePlaylistEntryLocalFile(entry.source)
                        ?.takeIf { it.exists() && it.isFile }
                    if (localFile == null) {
                        Toast.makeText(context, "Location is only available for local files", Toast.LENGTH_SHORT).show()
                    } else {
                        val parentDirectory = localFile.parentFile
                        if (parentDirectory == null || !parentDirectory.exists()) {
                            Toast.makeText(context, "Unable to resolve file location", Toast.LENGTH_SHORT).show()
                        } else {
                            val locationId = resolveStorageLocationForPath(
                                path = parentDirectory.absolutePath,
                                descriptors = storageDescriptors
                            )
                            openBrowser(
                                browserOpenRequest(
                                    locationId = locationId,
                                    directoryPath = parentDirectory.absolutePath
                                )
                            )
                            currentView = MainView.Browser
                        }
                    }
                },
                onShareFavoriteTrack = { entry ->
                    val localFile = resolvePlaylistEntryLocalFile(entry.source)
                        ?.takeIf { it.exists() && it.isFile }
                    if (localFile == null) {
                        Toast.makeText(context, "Share is only available for local files", Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                localFile
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = guessMimeTypeFromFilename(localFile.name)
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newRawUri(localFile.name, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooser = Intent.createChooser(intent, "Share file").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(chooser)
                        } catch (_: Throwable) {
                            Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCopyFavoriteTrackSource = { entry ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL or path", entry.source))
                    Toast.makeText(context, "Copied URL/path", Toast.LENGTH_SHORT).show()
                },
                onOpenFavoriteTrackInfo = { entry ->
                    openPlayerSurfaceAction()
                    val playingSameEntry = playlistEntryMatchesPlayback(
                        entry = entry,
                        activeSourceId = currentTrackPathOrUrl,
                        currentSubtuneIndex = currentSubtuneIndex
                    )
                    if (playingSameEntry) {
                        externalTrackInfoDialogRequestToken += 1
                    } else {
                        openPlaylistEntry(
                            context = context,
                            entry = entry,
                            playlist = favoritesPlaybackPlaylist,
                            trackLoadDelegates = trackLoadDelegates,
                            manualOpenDelegates = manualOpenDelegates,
                            autoPlayOnTrackSelect = false,
                            openPlayerOnTrackSelect = true,
                            expandOverride = true,
                            onActivePlaylistChanged = {
                                activePlaylist = it
                                activePlaylistShuffleActive = false
                            },
                            onActivePlaylistEntryIdChanged = { activePlaylistEntryId = it },
                            onPendingPlaylistSubtuneSelectionChanged = { pendingPlaylistSubtuneSelection = it }
                        )
                        appScope.launch {
                            delay(140)
                            externalTrackInfoDialogRequestToken += 1
                        }
                    }
                },
                onOpenBrowser = openBrowser,
                onCurrentViewChanged = { currentView = it },
                onOpenUrlOrPathDialog = {
                    urlOrPathInput = ""
                    showUrlOrPathDialog = true
                },
                isPlayerExpanded = isPlayerExpanded,
                networkCurrentFolderId = networkCurrentFolderId,
                onNetworkCurrentFolderIdChanged = { networkCurrentFolderId = it },
                onNetworkNodesChanged = { updatedNodes ->
                    networkNodes = updatedNodes
                    writeNetworkNodes(prefs, updatedNodes)
                },
                onResolveRemoteSourceMetadata = { sourceId, onSettled ->
                    scheduleNetworkSourceMetadataBackfill(
                        scope = appScope,
                        context = context.applicationContext,
                        sourceId = sourceId,
                        onResolved = { resolvedSource, resolvedTitle, resolvedArtist ->
                            applyNetworkSourceMetadata(resolvedSource, resolvedTitle, resolvedArtist)
                        },
                        onSettled = { onSettled() }
                    )
                },
                onCancelPendingMetadataBackfill = {
                    cancelPendingNetworkSourceMetadataBackfillJobs()
                },
                repository = repository,
                decoderExtensionArtworkHints = decoderExtensionArtworkHints,
                rememberBrowserLocation = rememberBrowserLocation,
                lastBrowserLocationId = lastBrowserLocationId,
                lastBrowserDirectoryPath = lastBrowserDirectoryPath,
                browserLaunchState = browserNavigator.launchState,
                browserFocusRestoreRequestToken = browserFocusRestoreRequestToken,
                showParentDirectoryEntry = showParentDirectoryEntry,
                showFileIconChipBackground = showFileIconChipBackground,
                onVisiblePlayableFilesChanged = { files -> visiblePlayableFiles = files },
                onBrowserLaunchStateChanged = { browserNavigator.updateLaunchState(it) },
                onReturnToNetworkOnBrowserExitChanged = { browserNavigator.updateReturnTarget(it) },
                returnToNetworkOnBrowserExit = browserNavigator.returnToNetworkOnExit,
                onLastBrowserLocationIdChanged = { lastBrowserLocationId = it },
                onLastBrowserDirectoryPathChanged = { lastBrowserDirectoryPath = it },
                onBrowserLocationChanged = { launchState ->
                    val update = buildBrowserLocationChangedUpdate(
                        launchState = launchState,
                        rememberBrowserLocation = rememberBrowserLocation,
                        networkNodes = networkNodes
                    )
                    update.recentFolderUpdate?.let { recent ->
                        runtimeDelegates.addRecentFolderWithTitle(
                            recent.path,
                            recent.locationId,
                            recent.sourceNodeId,
                            recent.title
                        )
                    }
                    browserNavigator.updateLaunchState(
                        launchState.copy(
                            locationId = update.launchLocationId,
                            directoryPath = update.launchDirectoryPath
                        )
                    )
                    if (update.shouldPersistRememberedLocation) {
                        lastBrowserLocationId = update.rememberedLocationId
                        lastBrowserDirectoryPath = update.rememberedDirectoryPath
                        persistRememberedBrowserLaunchState(
                            prefs = prefs,
                            state = BrowserLaunchState(
                                locationId = update.rememberedLocationId,
                                directoryPath = update.rememberedDirectoryPath
                            )
                        )
                    }
                },
                onClearActivePlaylistContext = {
                    activePlaylist = null
                    activePlaylistEntryId = null
                    activePlaylistShuffleActive = false
                    lastStoppedPlaylistResume = null
                    pendingPlaylistSubtuneSelection = null
                    showPlaylistSelectorDialog = false
                },
                onPlaylistFileSelected = handlePlaylistFileSelectionAction,
                onRememberSmbCredentials = { sourceNodeId, sourceId, username, password ->
                    rememberNetworkSmbCredentials(sourceNodeId, sourceId, username, password)
                },
                onRememberHttpCredentials = { sourceNodeId, sourceId, username, password ->
                    rememberNetworkHttpCredentials(sourceNodeId, sourceId, username, password)
                },
                settingsContent = settingsRouteContent
                    )
                }
            }
        }
        PlayerOverlayAndDialogsSection()
    }
    }

    AppNavigationMainContent()
}
