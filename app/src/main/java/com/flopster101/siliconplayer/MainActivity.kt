package com.flopster101.siliconplayer

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.* // Import for remember, mutableStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import com.flopster101.siliconplayer.ui.theme.SiliconPlayerTheme
import java.io.File
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private enum class MainView {
    Home,
    Browser,
    Settings
}

private enum class SettingsRoute {
    Root,
    AudioPlugins,
    PluginFfmpeg,
    PluginOpenMpt,
    GeneralAudio,
    Player,
    Misc,
    Ui,
    About
}

private enum class ThemeMode(val storageValue: String, val label: String) {
    Auto("auto", "Auto"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

@Composable
private fun placeholderArtworkIconForFile(file: File?): ImageVector {
    val extension = file?.extension?.lowercase()?.ifBlank { return Icons.Default.MusicNote }
        ?: return Icons.Default.MusicNote
    return if (extension in trackerModuleExtensions) {
        ImageVector.vectorResource(R.drawable.ic_placeholder_tracker_chip)
    } else {
        Icons.Default.MusicNote
    }
}

private fun normalizedPath(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return try {
        File(path).canonicalFile.absolutePath
    } catch (_: Exception) {
        File(path).absoluteFile.normalize().path
    }
}

private fun samePath(a: String?, b: String?): Boolean {
    val left = normalizedPath(a) ?: return false
    val right = normalizedPath(b) ?: return false
    return left == right
}

private data class RecentPathEntry(
    val path: String,
    val locationId: String?,
    val title: String? = null,
    val artist: String? = null
)

private data class StorageDescriptor(
    val rootPath: String,
    val label: String,
    val icon: ImageVector
)

private data class StoragePresentation(
    val label: String,
    val icon: ImageVector
)

private data class RecentTrackDisplay(
    val primaryText: String,
    val includeFilenameInSubtitle: Boolean
)

private fun buildRecentTrackDisplay(
    title: String,
    artist: String,
    fallback: String
): RecentTrackDisplay {
    return when {
        title.isNotBlank() && artist.isNotBlank() -> {
            RecentTrackDisplay(
                primaryText = "$artist - $title",
                includeFilenameInSubtitle = true
            )
        }
        title.isNotBlank() -> {
            RecentTrackDisplay(
                primaryText = "(unknown) - $title",
                includeFilenameInSubtitle = true
            )
        }
        artist.isNotBlank() -> {
            RecentTrackDisplay(
                primaryText = fallback,
                includeFilenameInSubtitle = false
            )
        }
        else -> {
            RecentTrackDisplay(
                primaryText = fallback,
                includeFilenameInSubtitle = false
            )
        }
    }
}

private fun applyRepeatModeToNative(mode: RepeatMode) {
    NativeBridge.setRepeatMode(
        when (mode) {
            RepeatMode.None -> 0
            RepeatMode.Track -> 1
            RepeatMode.LoopPoint -> 2
        }
    )
}

private fun showRepeatModeToast(context: Context, mode: RepeatMode) {
    Toast.makeText(context, mode.label, Toast.LENGTH_SHORT).show()
}

private fun resolveStorageRootFromAppDir(appSpecificDir: File): File? {
    val marker = "/Android/"
    val absolutePath = appSpecificDir.absolutePath
    val markerIndex = absolutePath.indexOf(marker)
    if (markerIndex <= 0) return null
    return File(absolutePath.substring(0, markerIndex))
}

private fun detectStorageDescriptors(context: Context): List<StorageDescriptor> {
    val descriptors = mutableListOf<StorageDescriptor>()
    val seen = mutableSetOf<String>()

    fun add(path: String, label: String, icon: ImageVector) {
        if (path in seen) return
        seen += path
        descriptors += StorageDescriptor(path, label, icon)
    }

    add("/", "Root (/)", Icons.Default.Folder)
    val internalRoot = Environment.getExternalStorageDirectory().absolutePath
    val internalIcon = if (context.resources.configuration.smallestScreenWidthDp >= 600) {
        Icons.Default.TabletAndroid
    } else {
        Icons.Default.PhoneAndroid
    }
    add(internalRoot, "Internal storage", internalIcon)

    context.getExternalFilesDirs(null)
        .orEmpty()
        .forEach { externalDir ->
            if (externalDir == null) return@forEach
            val volumeRoot = resolveStorageRootFromAppDir(externalDir) ?: return@forEach
            if (!Environment.isExternalStorageRemovable(externalDir)) return@forEach

            val lower = volumeRoot.absolutePath.lowercase()
            val isUsb = lower.contains("usb") || lower.contains("otg")
            val volumeName = volumeRoot.name.ifBlank { volumeRoot.absolutePath }
            val label = volumeName
            add(volumeRoot.absolutePath, label, if (isUsb) Icons.Default.Usb else Icons.Default.SdCard)
        }

    return descriptors
}

private fun storagePresentationForEntry(
    entry: RecentPathEntry,
    descriptors: List<StorageDescriptor>
): StoragePresentation {
    entry.locationId?.let { locationId ->
        descriptors.firstOrNull { it.rootPath == locationId }?.let {
            return StoragePresentation(label = it.label, icon = it.icon)
        }
    }
    val matching = descriptors
        .filter { entry.path == it.rootPath || entry.path.startsWith("${it.rootPath}/") }
        .maxByOrNull { it.rootPath.length }
    return if (matching != null) {
        StoragePresentation(label = matching.label, icon = matching.icon)
    } else {
        StoragePresentation(label = "Unknown storage", icon = Icons.Default.Folder)
    }
}

private fun readRecentEntries(
    prefs: android.content.SharedPreferences,
    key: String,
    maxItems: Int
): List<RecentPathEntry> {
    val raw = prefs.getString(key, null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val objectValue = array.optJSONObject(index) ?: continue
                val path = objectValue.optString("path", "").trim()
                if (path.isBlank()) continue
                val locationId = objectValue.optString("locationId", "").ifBlank { null }
                val title = objectValue.optString("title", "").ifBlank { null }
                val artist = objectValue.optString("artist", "").ifBlank { null }
                add(
                    RecentPathEntry(
                        path = path,
                        locationId = locationId,
                        title = title,
                        artist = artist
                    )
                )
                if (size >= maxItems) break
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun writeRecentEntries(
    prefs: android.content.SharedPreferences,
    key: String,
    entries: List<RecentPathEntry>,
    maxItems: Int
) {
    val trimmed = entries.take(maxItems)
    val array = JSONArray()
    trimmed.forEach { entry ->
        array.put(
            JSONObject()
                .put("path", entry.path)
                .put("locationId", entry.locationId ?: "")
                .put("title", entry.title ?: "")
                .put("artist", entry.artist ?: "")
        )
    }
    prefs.edit().putString(key, array.toString()).apply()
}

private fun mainViewOrder(view: MainView): Int = when (view) {
    MainView.Home -> 0
    MainView.Browser -> 1
    MainView.Settings -> 2
}

private fun settingsRouteOrder(route: SettingsRoute): Int = when (route) {
    SettingsRoute.Root -> 0
    SettingsRoute.AudioPlugins -> 1
    SettingsRoute.PluginFfmpeg -> 2
    SettingsRoute.PluginOpenMpt -> 2
    SettingsRoute.GeneralAudio -> 1
    SettingsRoute.Player -> 1
    SettingsRoute.Misc -> 1
    SettingsRoute.Ui -> 1
    SettingsRoute.About -> 1
}

private const val PAGE_NAV_DURATION_MS = 300
private const val RECENTS_LIMIT = 3
private val SettingsCardShape = RoundedCornerShape(16.dp)

private object AppPreferenceKeys {
    const val PREFS_NAME = "silicon_player_settings"
    const val AUTO_PLAY_ON_TRACK_SELECT = "auto_play_on_track_select"
    const val OPEN_PLAYER_ON_TRACK_SELECT = "open_player_on_track_select"
    const val AUTO_PLAY_NEXT_TRACK_ON_END = "auto_play_next_track_on_end"
    const val REMEMBER_BROWSER_LOCATION = "remember_browser_location"
    const val BROWSER_LAST_LOCATION_ID = "browser_last_location_id"
    const val BROWSER_LAST_DIRECTORY_PATH = "browser_last_directory_path"
    const val CORE_RATE_FFMPEG = "core_rate_ffmpeg"
    const val CORE_RATE_OPENMPT = "core_rate_openmpt"
    const val RESPOND_HEADPHONE_MEDIA_BUTTONS = "respond_headphone_media_buttons"
    const val PAUSE_ON_HEADPHONE_DISCONNECT = "pause_on_headphone_disconnect"
    const val OPEN_PLAYER_FROM_NOTIFICATION = "open_player_from_notification"
    const val PERSIST_REPEAT_MODE = "persist_repeat_mode"
    const val PREFERRED_REPEAT_MODE = "preferred_repeat_mode"
    const val SESSION_CURRENT_PATH = "session_current_path"
    const val THEME_MODE = "theme_mode"
    const val RECENT_FOLDERS = "recent_folders"
    const val RECENT_PLAYED_FILES = "recent_played_files"
}

class MainActivity : ComponentActivity() {
    private fun consumeNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER_FROM_NOTIFICATION, false) == true) {
            notificationOpenPlayerSignal++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeNotificationIntent(intent)
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
            val darkTheme = when (themeMode) {
                ThemeMode.Auto -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SiliconPlayerTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(
                        themeMode = themeMode,
                        onThemeModeChanged = { mode ->
                            themeMode = mode
                            prefs.edit()
                                .putString(AppPreferenceKeys.THEME_MODE, mode.storageValue)
                                .apply()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
    }

    external fun getStringFromJNI(): String
    external fun startEngine()
    external fun stopEngine()
    external fun isEnginePlaying(): Boolean
    external fun loadAudio(path: String)
    external fun getSupportedExtensions(): Array<String>
    external fun getDuration(): Double
    external fun getPosition(): Double
    external fun seekTo(seconds: Double)
    external fun setLooping(enabled: Boolean)
    external fun setRepeatMode(mode: Int)
    external fun getTrackTitle(): String
    external fun getTrackArtist(): String
    external fun getTrackSampleRate(): Int
    external fun getTrackChannelCount(): Int
    external fun getTrackBitDepth(): Int
    external fun getTrackBitDepthLabel(): String
    external fun getRepeatModeCapabilities(): Int
    external fun setCoreOutputSampleRate(coreName: String, sampleRateHz: Int)

    companion object {
        var notificationOpenPlayerSignal by mutableIntStateOf(0)

        init {
            System.loadLibrary("siliconplayer")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNavigation(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var currentView by remember { mutableStateOf(MainView.Home) }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.Root) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var lastStoppedFile by remember { mutableStateOf<File?>(null) }
    var duration by remember { mutableDoubleStateOf(0.0) }
    var position by remember { mutableDoubleStateOf(0.0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isPlayerSurfaceVisible by remember { mutableStateOf(false) }
    var preferredRepeatMode by remember {
        mutableStateOf(
            RepeatMode.fromStorage(
                prefs.getString(AppPreferenceKeys.PREFERRED_REPEAT_MODE, RepeatMode.None.storageValue)
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
    var repeatModeCapabilitiesFlags by remember { mutableIntStateOf(REPEAT_CAP_TRACK) }
    var artworkBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var visiblePlayableFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var browserLaunchLocationId by remember { mutableStateOf<String?>(null) }
    var browserLaunchDirectoryPath by remember { mutableStateOf<String?>(null) }
    val storageDescriptors = remember(context) { detectStorageDescriptors(context) }
    val appScope = rememberCoroutineScope()
    val recentLimit = RECENTS_LIMIT
    var recentFolders by remember {
        mutableStateOf(readRecentEntries(prefs, AppPreferenceKeys.RECENT_FOLDERS, recentLimit))
    }
    var recentPlayedFiles by remember {
        mutableStateOf(readRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, recentLimit))
    }
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
    var rememberBrowserLocation by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, true)
        )
    }
    var lastBrowserLocationId by remember {
        mutableStateOf(
            prefs.getString(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID, null)
        )
    }
    var lastBrowserDirectoryPath by remember {
        mutableStateOf(
            prefs.getString(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH, null)
        )
    }
    var ffmpegCoreSampleRateHz by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.CORE_RATE_FFMPEG, 0)
        )
    }
    var openMptCoreSampleRateHz by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.CORE_RATE_OPENMPT, 0)
        )
    }
    var respondHeadphoneMediaButtons by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.RESPOND_HEADPHONE_MEDIA_BUTTONS, true)
        )
    }
    var pauseOnHeadphoneDisconnect by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.PAUSE_ON_HEADPHONE_DISCONNECT, true)
        )
    }
    var openPlayerFromNotification by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.OPEN_PLAYER_FROM_NOTIFICATION, true)
        )
    }
    var playbackWatchPath by remember { mutableStateOf<String?>(null) }
    var playbackWatchWasPlaying by remember { mutableStateOf(false) }

    // Get supported extensions from JNI
    val supportedExtensions = remember { NativeBridge.getSupportedExtensions().toSet() }
    val repository = remember { com.flopster101.siliconplayer.data.FileRepository(supportedExtensions) }

    // Function to check permissions
    fun checkPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    // Permission handling
    var hasPermission by remember { mutableStateOf(checkPermission()) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // Effect to check permission on start and resume
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = checkPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    fun syncPlaybackService() {
        PlaybackService.syncFromUi(
            context = context,
            path = selectedFile?.absolutePath,
            title = metadataTitle.ifBlank { selectedFile?.nameWithoutExtension.orEmpty() },
            artist = metadataArtist.ifBlank { "Unknown Artist" },
            durationSeconds = duration,
            positionSeconds = position,
            isPlaying = isPlaying
        )
    }

    fun addRecentFolder(path: String, locationId: String?) {
        val normalized = normalizedPath(path) ?: path
        val updated = listOf(
            RecentPathEntry(path = normalized, locationId = locationId, title = null, artist = null)
        ) + recentFolders.filterNot { samePath(it.path, normalized) }
        recentFolders = updated.take(recentLimit)
        writeRecentEntries(prefs, AppPreferenceKeys.RECENT_FOLDERS, recentFolders, recentLimit)
    }

    fun addRecentPlayedTrack(path: String, locationId: String?, title: String? = null, artist: String? = null) {
        val normalized = normalizedPath(path) ?: path
        val existing = recentPlayedFiles.firstOrNull { samePath(it.path, normalized) }
        val resolvedTitle = title?.trim().takeUnless { it.isNullOrBlank() } ?: existing?.title
        val resolvedArtist = artist?.trim().takeUnless { it.isNullOrBlank() } ?: existing?.artist
        val updated = listOf(
            RecentPathEntry(
                path = normalized,
                locationId = locationId ?: existing?.locationId,
                title = resolvedTitle,
                artist = resolvedArtist
            )
        ) +
            recentPlayedFiles.filterNot { samePath(it.path, normalized) }
        recentPlayedFiles = updated.take(recentLimit)
        writeRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, recentPlayedFiles, recentLimit)
    }

    fun scheduleRecentTrackMetadataRefresh(path: String, locationId: String?) {
        appScope.launch {
            repeat(8) { attempt ->
                delay(if (attempt == 0) 120L else 220L)
                val current = selectedFile ?: return@launch
                if (current.absolutePath != path) return@launch
                val refreshedTitle = NativeBridge.getTrackTitle()
                val refreshedArtist = NativeBridge.getTrackArtist()
                addRecentPlayedTrack(
                    path = path,
                    locationId = locationId,
                    title = refreshedTitle,
                    artist = refreshedArtist
                )
                if (refreshedTitle.isNotBlank() || refreshedArtist.isNotBlank()) {
                    return@launch
                }
            }
        }
    }

    fun refreshRepeatModeForTrack() {
        activeRepeatMode = resolveRepeatModeForFlags(preferredRepeatMode, repeatModeCapabilitiesFlags)
        applyRepeatModeToNative(activeRepeatMode)
    }

    fun cycleRepeatMode() {
        val modes = availableRepeatModesForFlags(repeatModeCapabilitiesFlags)
        if (modes.isEmpty()) return
        val currentIndex = modes.indexOf(activeRepeatMode).let { if (it < 0) 0 else it }
        val next = modes[(currentIndex + 1) % modes.size]
        preferredRepeatMode = next
        activeRepeatMode = next
        applyRepeatModeToNative(next)
        showRepeatModeToast(context, next)
    }

    fun applyTrackSelection(file: File, autoStart: Boolean, expandOverride: Boolean? = null) {
        NativeBridge.stopEngine()
        isPlaying = false
        lastStoppedFile = null
        selectedFile = file
        isPlayerSurfaceVisible = true
        NativeBridge.loadAudio(file.absolutePath)
        metadataTitle = NativeBridge.getTrackTitle()
        metadataArtist = NativeBridge.getTrackArtist()
        metadataSampleRate = NativeBridge.getTrackSampleRate()
        metadataChannelCount = NativeBridge.getTrackChannelCount()
        metadataBitDepthLabel = NativeBridge.getTrackBitDepthLabel()
        repeatModeCapabilitiesFlags = NativeBridge.getRepeatModeCapabilities()
        duration = NativeBridge.getDuration()
        position = 0.0
        artworkBitmap = null
        refreshRepeatModeForTrack()
        if (autoStart) {
            addRecentPlayedTrack(
                path = file.absolutePath,
                locationId = lastBrowserLocationId,
                title = metadataTitle,
                artist = metadataArtist
            )
            NativeBridge.startEngine()
            isPlaying = true
            scheduleRecentTrackMetadataRefresh(file.absolutePath, lastBrowserLocationId)
        }
        expandOverride?.let { isPlayerExpanded = it }
        syncPlaybackService()
    }

    fun resumeLastStoppedTrack(autoStart: Boolean = true): Boolean {
        val resumable = lastStoppedFile?.takeIf { it.exists() && it.isFile } ?: run {
            lastStoppedFile = null
            return false
        }
        applyTrackSelection(
            file = resumable,
            autoStart = autoStart,
            expandOverride = null
        )
        return true
    }

    fun currentTrackIndex(): Int {
        val currentPath = selectedFile?.absolutePath ?: return -1
        return visiblePlayableFiles.indexOfFirst { it.absolutePath == currentPath }
    }

    fun playAdjacentTrack(offset: Int): Boolean {
        val index = currentTrackIndex()
        if (index < 0) return false
        val targetIndex = index + offset
        if (targetIndex !in visiblePlayableFiles.indices) return false
        applyTrackSelection(
            file = visiblePlayableFiles[targetIndex],
            autoStart = true,
            expandOverride = null
        )
        return true
    }

    fun restorePlayerStateFromSessionAndNative(openExpanded: Boolean) {
        val path = prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null)
        val file = path?.let { File(it) }?.takeIf { it.exists() && it.isFile } ?: return
        selectedFile = file
        isPlayerSurfaceVisible = true
        isPlayerExpanded = openExpanded
        metadataTitle = NativeBridge.getTrackTitle()
        metadataArtist = NativeBridge.getTrackArtist()
        metadataSampleRate = NativeBridge.getTrackSampleRate()
        metadataChannelCount = NativeBridge.getTrackChannelCount()
        metadataBitDepthLabel = NativeBridge.getTrackBitDepthLabel()
        repeatModeCapabilitiesFlags = NativeBridge.getRepeatModeCapabilities()
        duration = NativeBridge.getDuration()
        position = NativeBridge.getPosition()
        isPlaying = NativeBridge.isEnginePlaying()
        artworkBitmap = null
        refreshRepeatModeForTrack()
    }

    LaunchedEffect(Unit) {
        if (!checkPermission()) {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", context.packageName))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    context.startActivity(intent)
                }
            } else {
                launcher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(selectedFile) {
        while (selectedFile != null) {
            val currentFile = selectedFile
            val nextDuration = NativeBridge.getDuration()
            val nextPosition = NativeBridge.getPosition()
            val nextIsPlaying = NativeBridge.isEnginePlaying()
            duration = nextDuration
            position = nextPosition
            isPlaying = nextIsPlaying
            val currentPath = currentFile?.absolutePath
            if (currentPath != playbackWatchPath) {
                playbackWatchPath = currentPath
                playbackWatchWasPlaying = nextIsPlaying
            } else {
                val atTrackEnd = nextDuration > 0.0 && nextPosition >= (nextDuration - 0.2)
                val endedNaturally = playbackWatchWasPlaying && !nextIsPlaying && atTrackEnd
                playbackWatchWasPlaying = nextIsPlaying
                if (endedNaturally && autoPlayNextTrackOnEnd && playAdjacentTrack(1)) {
                    continue
                }
            }
            val nextTitle = NativeBridge.getTrackTitle()
            val nextArtist = NativeBridge.getTrackArtist()
            val titleChanged = nextTitle != metadataTitle
            val artistChanged = nextArtist != metadataArtist
            if (titleChanged) metadataTitle = nextTitle
            if (artistChanged) metadataArtist = nextArtist
            if ((titleChanged || artistChanged) && currentFile != null) {
                addRecentPlayedTrack(
                    path = currentFile.absolutePath,
                    locationId = lastBrowserLocationId,
                    title = nextTitle,
                    artist = nextArtist
                )
            }
            delay(if (isPlaying) 80 else 250)
        }
    }

    LaunchedEffect(selectedFile, preferredRepeatMode) {
        refreshRepeatModeForTrack()
    }

    LaunchedEffect(selectedFile) {
        artworkBitmap = withContext(Dispatchers.IO) {
            selectedFile?.let { loadArtworkForFile(it) }
        }
    }

    LaunchedEffect(autoPlayOnTrackSelect) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.AUTO_PLAY_ON_TRACK_SELECT, autoPlayOnTrackSelect)
            .apply()
    }

    LaunchedEffect(openPlayerOnTrackSelect) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.OPEN_PLAYER_ON_TRACK_SELECT, openPlayerOnTrackSelect)
            .apply()
    }

    LaunchedEffect(autoPlayNextTrackOnEnd) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.AUTO_PLAY_NEXT_TRACK_ON_END, autoPlayNextTrackOnEnd)
            .apply()
    }

    LaunchedEffect(rememberBrowserLocation) {
        val editor = prefs.edit()
            .putBoolean(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, rememberBrowserLocation)
        if (!rememberBrowserLocation) {
            lastBrowserLocationId = null
            lastBrowserDirectoryPath = null
            editor
                .remove(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID)
                .remove(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH)
        }
        editor.apply()
    }

    LaunchedEffect(ffmpegCoreSampleRateHz) {
        prefs.edit()
            .putInt(AppPreferenceKeys.CORE_RATE_FFMPEG, ffmpegCoreSampleRateHz)
            .apply()
        NativeBridge.setCoreOutputSampleRate("FFmpeg", ffmpegCoreSampleRateHz)
    }

    LaunchedEffect(openMptCoreSampleRateHz) {
        prefs.edit()
            .putInt(AppPreferenceKeys.CORE_RATE_OPENMPT, openMptCoreSampleRateHz)
            .apply()
        NativeBridge.setCoreOutputSampleRate("LibOpenMPT", openMptCoreSampleRateHz)
    }

    LaunchedEffect(respondHeadphoneMediaButtons) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.RESPOND_HEADPHONE_MEDIA_BUTTONS, respondHeadphoneMediaButtons)
            .apply()
        PlaybackService.refreshSettings(context)
    }

    LaunchedEffect(pauseOnHeadphoneDisconnect) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.PAUSE_ON_HEADPHONE_DISCONNECT, pauseOnHeadphoneDisconnect)
            .apply()
        PlaybackService.refreshSettings(context)
    }

    LaunchedEffect(openPlayerFromNotification) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.OPEN_PLAYER_FROM_NOTIFICATION, openPlayerFromNotification)
            .apply()
    }

    LaunchedEffect(persistRepeatMode) {
        val editor = prefs.edit().putBoolean(AppPreferenceKeys.PERSIST_REPEAT_MODE, persistRepeatMode)
        if (!persistRepeatMode) {
            editor.remove(AppPreferenceKeys.PREFERRED_REPEAT_MODE)
        }
        editor.apply()
    }

    LaunchedEffect(preferredRepeatMode, persistRepeatMode) {
        if (persistRepeatMode) {
            prefs.edit()
                .putString(AppPreferenceKeys.PREFERRED_REPEAT_MODE, preferredRepeatMode.storageValue)
                .apply()
        }
    }

    LaunchedEffect(selectedFile, isPlaying, metadataTitle, metadataArtist, duration) {
        if (selectedFile != null) {
            syncPlaybackService()
        }
    }

    val notificationOpenSignal = MainActivity.notificationOpenPlayerSignal
    LaunchedEffect(notificationOpenSignal, openPlayerFromNotification) {
        if (notificationOpenSignal <= 0) return@LaunchedEffect
        if (openPlayerFromNotification) {
            restorePlayerStateFromSessionAndNative(openExpanded = true)
        }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Please grant storage permissions to access audio files.")
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.addCategory("android.intent.category.DEFAULT")
                            intent.data = Uri.parse(String.format("package:%s", context.packageName))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent()
                            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                            context.startActivity(intent)
                        }
                    } else {
                        launcher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    val miniPlayerInsetTarget = when {
        currentView == MainView.Browser && isPlayerSurfaceVisible -> 108.dp
        currentView == MainView.Home && isPlayerSurfaceVisible -> 108.dp
        else -> 0.dp
    }
    val miniPlayerListInset by animateDpAsState(
        targetValue = miniPlayerInsetTarget,
        label = "miniPlayerListInset"
    )
    var miniExpandPreviewProgress by remember { mutableFloatStateOf(0f) }
    var expandFromMiniDrag by remember { mutableStateOf(false) }
    var collapseFromSwipe by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val miniPreviewLiftPx = with(LocalDensity.current) { 28.dp.toPx() }
    val stopAndEmptyTrack: () -> Unit = {
        if (selectedFile != null) {
            lastStoppedFile = selectedFile
        }
        NativeBridge.stopEngine()
        selectedFile = null
        duration = 0.0
        position = 0.0
        isPlaying = false
        metadataTitle = ""
        metadataArtist = ""
        metadataSampleRate = 0
        metadataChannelCount = 0
        metadataBitDepthLabel = "Unknown"
        repeatModeCapabilitiesFlags = REPEAT_CAP_TRACK
        artworkBitmap = null
        context.startService(
            Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_STOP_CLEAR)
        )
    }
    val hidePlayerSurface: () -> Unit = {
        stopAndEmptyTrack()
        isPlayerExpanded = false
        isPlayerSurfaceVisible = false
    }
    LaunchedEffect(isPlayerExpanded) {
        if (!isPlayerExpanded) {
            miniExpandPreviewProgress = 0f
            delay(160)
            expandFromMiniDrag = false
            collapseFromSwipe = false
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    PlaybackService.ACTION_BROADCAST_CLEARED -> {
                        if (selectedFile != null) {
                            lastStoppedFile = selectedFile
                        }
                        selectedFile = null
                        isPlaying = false
                        duration = 0.0
                        position = 0.0
                        metadataTitle = ""
                        metadataArtist = ""
                        metadataSampleRate = 0
                        metadataChannelCount = 0
                        metadataBitDepthLabel = "Unknown"
                        repeatModeCapabilitiesFlags = REPEAT_CAP_TRACK
                        artworkBitmap = null
                    }
                    PlaybackService.ACTION_BROADCAST_PREVIOUS_TRACK_REQUEST -> {
                        playAdjacentTrack(-1)
                    }
                    PlaybackService.ACTION_BROADCAST_NEXT_TRACK_REQUEST -> {
                        playAdjacentTrack(1)
                    }
                }
            }
        }
        val playbackFilter = IntentFilter(PlaybackService.ACTION_BROADCAST_CLEARED).apply {
            addAction(PlaybackService.ACTION_BROADCAST_PREVIOUS_TRACK_REQUEST)
            addAction(PlaybackService.ACTION_BROADCAST_NEXT_TRACK_REQUEST)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            playbackFilter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    BackHandler(enabled = isPlayerExpanded || currentView == MainView.Settings) {
        when {
            isPlayerExpanded -> isPlayerExpanded = false
            currentView == MainView.Settings && settingsRoute != SettingsRoute.Root -> {
                settingsRoute = when (settingsRoute) {
                    SettingsRoute.PluginFfmpeg, SettingsRoute.PluginOpenMpt -> SettingsRoute.AudioPlugins
                    else -> SettingsRoute.Root
                }
            }
            currentView == MainView.Settings -> currentView = MainView.Home
        }
    }

    val shouldShowBrowserHomeAction = currentView == MainView.Browser
    val shouldShowSettingsAction = currentView != MainView.Settings

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = currentView != MainView.Settings,
                    enter = fadeIn(animationSpec = tween(140)),
                    exit = fadeOut(animationSpec = tween(140))
                ) {
                    androidx.compose.material3.TopAppBar(
                        title = { Text("Silicon Player") },
                        actions = {
                            AnimatedVisibility(
                                visible = shouldShowBrowserHomeAction,
                                enter = fadeIn(animationSpec = tween(150)),
                                exit = fadeOut(animationSpec = tween(120))
                            ) {
                                IconButton(onClick = { currentView = MainView.Home }) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Go to app home"
                                    )
                                }
                            }
                            if (shouldShowSettingsAction) {
                                IconButton(onClick = {
                                    settingsRoute = SettingsRoute.Root
                                    currentView = MainView.Settings
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Open settings"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        ) { mainPadding ->
            AnimatedContent(
                targetState = currentView,
                transitionSpec = {
                    val forward = mainViewOrder(targetState) >= mainViewOrder(initialState)
                    val enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth / 4 },
                        animationSpec = tween(
                            durationMillis = PAGE_NAV_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 210,
                            delayMillis = 60,
                            easing = LinearOutSlowInEasing
                        )
                    )
                    val exit = slideOutHorizontally(
                        targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 },
                        animationSpec = tween(
                            durationMillis = PAGE_NAV_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 120,
                            easing = FastOutLinearInEasing
                        )
                    )
                    enter togetherWith exit
                },
                label = "mainViewTransition",
                modifier = Modifier
            ) { targetView ->
                when (targetView) {
                    MainView.Home -> Box(modifier = Modifier.padding(mainPadding)) {
                        HomeScreen(
                            currentTrackPath = selectedFile?.absolutePath,
                            currentTrackTitle = metadataTitle,
                            currentTrackArtist = metadataArtist,
                            recentFolders = recentFolders,
                            recentPlayedFiles = recentPlayedFiles,
                            storagePresentationForEntry = { entry ->
                                storagePresentationForEntry(entry, storageDescriptors)
                            },
                            bottomContentPadding = miniPlayerListInset,
                            onOpenLibrary = {
                                browserLaunchLocationId = null
                                browserLaunchDirectoryPath = null
                                currentView = MainView.Browser
                            },
                            onOpenRecentFolder = { entry ->
                                browserLaunchLocationId = entry.locationId
                                browserLaunchDirectoryPath = entry.path
                                currentView = MainView.Browser
                            },
                            onPlayRecentFile = { entry ->
                                val file = File(entry.path)
                                if (file.exists() && file.isFile) {
                                    applyTrackSelection(
                                        file = file,
                                        autoStart = true,
                                        expandOverride = openPlayerOnTrackSelect
                                    )
                                }
                            }
                        )
                    }
                    MainView.Browser -> Box(modifier = Modifier.padding(mainPadding)) {
                        com.flopster101.siliconplayer.ui.screens.FileBrowserScreen(
                            repository = repository,
                            initialLocationId = browserLaunchLocationId
                                ?: if (rememberBrowserLocation) lastBrowserLocationId else null,
                            initialDirectoryPath = browserLaunchDirectoryPath
                                ?: if (rememberBrowserLocation) lastBrowserDirectoryPath else null,
                            onVisiblePlayableFilesChanged = { files -> visiblePlayableFiles = files },
                            bottomContentPadding = miniPlayerListInset,
                            backHandlingEnabled = !isPlayerExpanded,
                            onExitBrowser = { currentView = MainView.Home },
                            onOpenSettings = null,
                            showPrimaryTopBar = false,
                            onBrowserLocationChanged = { locationId, directoryPath ->
                                if (locationId != null && directoryPath != null) {
                                    addRecentFolder(directoryPath, locationId)
                                }
                                if (browserLaunchLocationId != null || browserLaunchDirectoryPath != null) {
                                    browserLaunchLocationId = null
                                    browserLaunchDirectoryPath = null
                                }
                                if (!rememberBrowserLocation) return@FileBrowserScreen
                                lastBrowserLocationId = locationId
                                lastBrowserDirectoryPath = directoryPath
                                prefs.edit()
                                    .putString(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID, locationId)
                                    .putString(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH, directoryPath)
                                    .apply()
                            },
                            onFileSelected = { file ->
                                applyTrackSelection(
                                    file = file,
                                    autoStart = autoPlayOnTrackSelect,
                                    expandOverride = openPlayerOnTrackSelect
                                )
                            }
                        )
                    }
                    MainView.Settings -> SettingsScreen(
                        route = settingsRoute,
                        onBack = {
                            if (settingsRoute != SettingsRoute.Root) {
                                settingsRoute = when (settingsRoute) {
                                    SettingsRoute.PluginFfmpeg, SettingsRoute.PluginOpenMpt -> SettingsRoute.AudioPlugins
                                    else -> SettingsRoute.Root
                                }
                            } else {
                                currentView = MainView.Home
                            }
                        },
                        onOpenAudioPlugins = { settingsRoute = SettingsRoute.AudioPlugins },
                        onOpenGeneralAudio = { settingsRoute = SettingsRoute.GeneralAudio },
                        onOpenPlayer = { settingsRoute = SettingsRoute.Player },
                        onOpenMisc = { settingsRoute = SettingsRoute.Misc },
                        onOpenUi = { settingsRoute = SettingsRoute.Ui },
                        onOpenAbout = { settingsRoute = SettingsRoute.About },
                        onOpenFfmpeg = { settingsRoute = SettingsRoute.PluginFfmpeg },
                        onOpenOpenMpt = { settingsRoute = SettingsRoute.PluginOpenMpt },
                        autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                        onAutoPlayOnTrackSelectChanged = { autoPlayOnTrackSelect = it },
                        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                        onOpenPlayerOnTrackSelectChanged = { openPlayerOnTrackSelect = it },
                        autoPlayNextTrackOnEnd = autoPlayNextTrackOnEnd,
                        onAutoPlayNextTrackOnEndChanged = { autoPlayNextTrackOnEnd = it },
                        respondHeadphoneMediaButtons = respondHeadphoneMediaButtons,
                        onRespondHeadphoneMediaButtonsChanged = { respondHeadphoneMediaButtons = it },
                        pauseOnHeadphoneDisconnect = pauseOnHeadphoneDisconnect,
                        onPauseOnHeadphoneDisconnectChanged = { pauseOnHeadphoneDisconnect = it },
                        openPlayerFromNotification = openPlayerFromNotification,
                        onOpenPlayerFromNotificationChanged = { openPlayerFromNotification = it },
                        persistRepeatMode = persistRepeatMode,
                        onPersistRepeatModeChanged = { persistRepeatMode = it },
                        themeMode = themeMode,
                        onThemeModeChanged = onThemeModeChanged,
                        rememberBrowserLocation = rememberBrowserLocation,
                        onRememberBrowserLocationChanged = { rememberBrowserLocation = it },
                        ffmpegSampleRateHz = ffmpegCoreSampleRateHz,
                        onFfmpegSampleRateChanged = { ffmpegCoreSampleRateHz = it },
                        openMptSampleRateHz = openMptCoreSampleRateHz,
                        onOpenMptSampleRateChanged = { openMptCoreSampleRateHz = it },
                        onClearRecentHistory = {
                            recentFolders = emptyList()
                            recentPlayedFiles = emptyList()
                            prefs.edit()
                                .remove(AppPreferenceKeys.RECENT_FOLDERS)
                                .remove(AppPreferenceKeys.RECENT_PLAYED_FILES)
                                .apply()
                        }
                    )
                }
            }
        }

        run {
            if (isPlayerSurfaceVisible && !isPlayerExpanded && miniExpandPreviewProgress > 0f) {
                val previewProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
                val previewOffsetPx = (1f - previewProgress) * screenHeightPx
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            translationY = previewOffsetPx
                            alpha = (previewProgress * 0.98f).coerceIn(0f, 1f)
                        }
                ) {
                    com.flopster101.siliconplayer.ui.screens.PlayerScreen(
                        file = selectedFile,
                        onBack = {},
                        enableCollapseGesture = false,
                        isPlaying = isPlaying,
                        canResumeStoppedTrack = false,
                        onPlay = {},
                        onPause = {},
                        onStopAndClear = {},
                        durationSeconds = duration,
                        positionSeconds = position,
                        canPreviousTrack = currentTrackIndex() > 0,
                        canNextTrack = currentTrackIndex() in 0 until (visiblePlayableFiles.size - 1),
                        title = metadataTitle,
                        artist = metadataArtist,
                        sampleRateHz = metadataSampleRate,
                        channelCount = metadataChannelCount,
                        bitDepthLabel = metadataBitDepthLabel,
                        artwork = artworkBitmap,
                        noArtworkIcon = placeholderArtworkIconForFile(selectedFile),
                        repeatMode = activeRepeatMode,
                        onSeek = {},
                        onPreviousTrack = {},
                        onNextTrack = {},
                        onPreviousSubtune = {},
                        onNextSubtune = {},
                        onOpenSubtuneSelector = {},
                        onCycleRepeatMode = {}
                    )
                }
            }
            AnimatedVisibility(
                visible = isPlayerSurfaceVisible && !isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn() + scaleIn(initialScale = 0.96f),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val dismissState = rememberSwipeToDismissBoxState(
                    positionalThreshold = { totalDistance -> totalDistance * 0.6f },
                    confirmValueChange = { targetValue ->
                        val isDismiss = targetValue == SwipeToDismissBoxValue.StartToEnd ||
                            targetValue == SwipeToDismissBoxValue.EndToStart
                        if (isDismiss && !isPlaying) {
                            hidePlayerSurface()
                            true
                        } else {
                            false
                        }
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier
                        .graphicsLayer {
                            val dragProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
                            val hideMini = expandFromMiniDrag || isPlayerExpanded
                            val alphaFloor = if (hideMini) 0f else 0.16f
                            alpha = (1f - dragProgress).coerceIn(alphaFloor, 1f)
                            translationY = -miniPreviewLiftPx * dragProgress
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    backgroundContent = {},
                    enableDismissFromStartToEnd = !isPlaying,
                    enableDismissFromEndToStart = !isPlaying
                ) {
                    MiniPlayerBar(
                        file = selectedFile,
                        title = metadataTitle.ifBlank {
                            selectedFile?.nameWithoutExtension?.ifBlank { selectedFile?.name ?: "" }
                                ?: "No track selected"
                        },
                        artist = metadataArtist.ifBlank {
                            if (selectedFile != null) "Unknown Artist" else "Tap a file to play"
                        },
                        artwork = artworkBitmap,
                        noArtworkIcon = placeholderArtworkIconForFile(selectedFile),
                        isPlaying = isPlaying,
                        canResumeStoppedTrack = lastStoppedFile?.exists() == true,
                        positionSeconds = position,
                        durationSeconds = duration,
                        canPreviousTrack = currentTrackIndex() > 0,
                        canNextTrack = currentTrackIndex() in 0 until (visiblePlayableFiles.size - 1),
                        onExpand = {
                            collapseFromSwipe = false
                            expandFromMiniDrag = miniExpandPreviewProgress > 0f
                            miniExpandPreviewProgress = 0f
                            isPlayerExpanded = true
                        },
                        onExpandDragProgress = { progress ->
                            miniExpandPreviewProgress = progress
                        },
                        onExpandDragCommit = {
                            val start = miniExpandPreviewProgress.coerceIn(0f, 1f)
                            uiScope.launch {
                                expandFromMiniDrag = true
                                val anim = Animatable(start)
                                anim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
                                ) {
                                    miniExpandPreviewProgress = value
                                }
                                collapseFromSwipe = false
                                isPlayerExpanded = true
                            }
                        },
                        onPreviousTrack = { playAdjacentTrack(-1) },
                        onNextTrack = { playAdjacentTrack(1) },
                        onPlayPause = {
                            if (selectedFile == null) {
                                resumeLastStoppedTrack(autoStart = true)
                            } else {
                                if (isPlaying) {
                                    NativeBridge.stopEngine()
                                    isPlaying = false
                                } else {
                                    selectedFile?.let {
                                        addRecentPlayedTrack(
                                    path = it.absolutePath,
                                    locationId = lastBrowserLocationId,
                                    title = metadataTitle,
                                    artist = metadataArtist
                                )
                            }
                                    applyRepeatModeToNative(activeRepeatMode)
                                    NativeBridge.startEngine()
                                    isPlaying = true
                                    selectedFile?.let {
                                        scheduleRecentTrackMetadataRefresh(
                                            path = it.absolutePath,
                                            locationId = lastBrowserLocationId
                                        )
                                    }
                                }
                                syncPlaybackService()
                            }
                        },
                        onStopAndClear = stopAndEmptyTrack
                    )
                }
            }
            AnimatedVisibility(
                visible = isPlayerSurfaceVisible && isPlayerExpanded,
                enter = if (expandFromMiniDrag) {
                    EnterTransition.None
                } else {
                    slideInVertically(initialOffsetY = { it / 3 }) + fadeIn() + scaleIn(initialScale = 0.96f)
                },
                exit = if (collapseFromSwipe) {
                    fadeOut(animationSpec = tween(120))
                } else {
                    slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut()
                }
            ) {
                com.flopster101.siliconplayer.ui.screens.PlayerScreen(
                    file = selectedFile,
                    onBack = {
                        collapseFromSwipe = false
                        miniExpandPreviewProgress = 0f
                        isPlayerExpanded = false
                    },
                    onCollapseBySwipe = {
                        collapseFromSwipe = true
                        miniExpandPreviewProgress = 0f
                        isPlayerExpanded = false
                    },
                    isPlaying = isPlaying,
                    canResumeStoppedTrack = lastStoppedFile?.exists() == true,
                    onPlay = {
                        if (selectedFile == null) {
                            resumeLastStoppedTrack(autoStart = true)
                        } else {
                            selectedFile?.let {
                                addRecentPlayedTrack(
                                    path = it.absolutePath,
                                    locationId = lastBrowserLocationId,
                                    title = metadataTitle,
                                    artist = metadataArtist
                                )
                            }
                            applyRepeatModeToNative(activeRepeatMode)
                            NativeBridge.startEngine()
                            isPlaying = true
                            selectedFile?.let {
                                scheduleRecentTrackMetadataRefresh(
                                    path = it.absolutePath,
                                    locationId = lastBrowserLocationId
                                )
                            }
                            syncPlaybackService()
                        }
                    },
                    onPause = {
                        NativeBridge.stopEngine()
                        isPlaying = false
                        syncPlaybackService()
                    },
                    onStopAndClear = stopAndEmptyTrack,
                    durationSeconds = duration,
                    positionSeconds = position,
                    canPreviousTrack = currentTrackIndex() > 0,
                    canNextTrack = currentTrackIndex() in 0 until (visiblePlayableFiles.size - 1),
                    title = metadataTitle,
                    artist = metadataArtist,
                    sampleRateHz = metadataSampleRate,
                    channelCount = metadataChannelCount,
                    bitDepthLabel = metadataBitDepthLabel,
                    artwork = artworkBitmap,
                    noArtworkIcon = placeholderArtworkIconForFile(selectedFile),
                    repeatMode = activeRepeatMode,
                    onSeek = { seconds ->
                        NativeBridge.seekTo(seconds)
                        position = seconds
                        syncPlaybackService()
                    },
                    onPreviousTrack = { playAdjacentTrack(-1) },
                    onNextTrack = { playAdjacentTrack(1) },
                    onPreviousSubtune = {},
                    onNextSubtune = {},
                    onOpenSubtuneSelector = {},
                    onCycleRepeatMode = { cycleRepeatMode() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    currentTrackPath: String?,
    currentTrackTitle: String,
    currentTrackArtist: String,
    recentFolders: List<RecentPathEntry>,
    recentPlayedFiles: List<RecentPathEntry>,
    storagePresentationForEntry: (RecentPathEntry) -> StoragePresentation,
    bottomContentPadding: Dp = 0.dp,
    onOpenLibrary: () -> Unit,
    onOpenRecentFolder: (RecentPathEntry) -> Unit,
    onPlayRecentFile: (RecentPathEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = bottomContentPadding)
    ) {
        Text(
            text = "Local music library",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Browse files and play mainstream or tracker/module formats.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))
        androidx.compose.material3.ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            onClick = onOpenLibrary
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open Library Browser",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Text(
                        text = "Browse folders and choose a track",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (recentFolders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Recent folders",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            recentFolders.take(RECENTS_LIMIT).forEach { entry ->
                val folderFile = File(entry.path)
                val storagePresentation = storagePresentationForEntry(entry)
                androidx.compose.material3.ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsCardShape,
                    onClick = { onOpenRecentFolder(entry) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folderFile.name.ifBlank { entry.path },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = storagePresentation.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = storagePresentation.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (recentPlayedFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recently played",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            recentPlayedFiles.take(RECENTS_LIMIT).forEach { entry ->
                val trackFile = File(entry.path)
                val storagePresentation = storagePresentationForEntry(entry)
                val extensionLabel = trackFile.extension.uppercase().ifBlank { "UNKNOWN" }
                val useLiveMetadata = samePath(currentTrackPath, entry.path)
                androidx.compose.material3.ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsCardShape,
                    onClick = { onPlayRecentFile(entry) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            RecentTrackSummaryText(
                                file = trackFile,
                                cachedTitle = if (useLiveMetadata) currentTrackTitle else entry.title,
                                cachedArtist = if (useLiveMetadata) currentTrackArtist else entry.artist,
                                storagePresentation = storagePresentation,
                                extensionLabel = extensionLabel
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentTrackSummaryText(
    file: File,
    cachedTitle: String?,
    cachedArtist: String?,
    storagePresentation: StoragePresentation,
    extensionLabel: String
) {
    val fallback = file.nameWithoutExtension.ifBlank { file.name }
    val display by produceState(
        initialValue = RecentTrackDisplay(
            primaryText = fallback,
            includeFilenameInSubtitle = false
        ),
        key1 = file.absolutePath,
        key2 = cachedTitle,
        key3 = cachedArtist
    ) {
        value = withContext(Dispatchers.IO) {
            val cachedTitleValue = cachedTitle?.trim().orEmpty()
            val cachedArtistValue = cachedArtist?.trim().orEmpty()
            try {
                val result = if (cachedTitleValue.isNotBlank() || cachedArtistValue.isNotBlank()) {
                    buildRecentTrackDisplay(
                        title = cachedTitleValue,
                        artist = cachedArtistValue,
                        fallback = fallback
                    )
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val title = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            ?.trim()
                            .orEmpty()
                        val artist = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            ?.trim()
                            .orEmpty()
                        buildRecentTrackDisplay(
                            title = title,
                            artist = artist,
                            fallback = fallback
                        )
                    } finally {
                        retriever.release()
                    }
                }
                result
            } catch (_: Exception) {
                RecentTrackDisplay(
                    primaryText = fallback,
                    includeFilenameInSubtitle = false
                )
            }
        }
    }
    val shouldMarquee = display.primaryText.length > 28
    if (shouldMarquee) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
        ) {
            Text(
                text = display.primaryText,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 900
                )
            )
        }
    } else {
        Text(
            text = display.primaryText,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = storagePresentation.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = buildString {
                append(storagePresentation.label)
                append(" • ")
                append(extensionLabel)
                if (display.includeFilenameInSubtitle) {
                    append(" • ")
                    append(fallback)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    route: SettingsRoute,
    onBack: () -> Unit,
    onOpenAudioPlugins: () -> Unit,
    onOpenGeneralAudio: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenMisc: () -> Unit,
    onOpenUi: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFfmpeg: () -> Unit,
    onOpenOpenMpt: () -> Unit,
    autoPlayOnTrackSelect: Boolean,
    onAutoPlayOnTrackSelectChanged: (Boolean) -> Unit,
    openPlayerOnTrackSelect: Boolean,
    onOpenPlayerOnTrackSelectChanged: (Boolean) -> Unit,
    autoPlayNextTrackOnEnd: Boolean,
    onAutoPlayNextTrackOnEndChanged: (Boolean) -> Unit,
    respondHeadphoneMediaButtons: Boolean,
    onRespondHeadphoneMediaButtonsChanged: (Boolean) -> Unit,
    pauseOnHeadphoneDisconnect: Boolean,
    onPauseOnHeadphoneDisconnectChanged: (Boolean) -> Unit,
    openPlayerFromNotification: Boolean,
    onOpenPlayerFromNotificationChanged: (Boolean) -> Unit,
    persistRepeatMode: Boolean,
    onPersistRepeatModeChanged: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    rememberBrowserLocation: Boolean,
    onRememberBrowserLocationChanged: (Boolean) -> Unit,
    ffmpegSampleRateHz: Int,
    onFfmpegSampleRateChanged: (Int) -> Unit,
    openMptSampleRateHz: Int,
    onOpenMptSampleRateChanged: (Int) -> Unit,
    onClearRecentHistory: () -> Unit
) {
    val screenTitle = when (route) {
        SettingsRoute.Root -> "Settings"
        SettingsRoute.AudioPlugins -> "Audio plugins"
        SettingsRoute.PluginFfmpeg -> "FFmpeg"
        SettingsRoute.PluginOpenMpt -> "OpenMPT"
        SettingsRoute.GeneralAudio -> "General audio"
        SettingsRoute.Player -> "Player settings"
        SettingsRoute.Misc -> "Misc settings"
        SettingsRoute.Ui -> "UI settings"
        SettingsRoute.About -> "About"
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = settingsRouteOrder(targetState) >= settingsRouteOrder(initialState)
                val enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth / 4 },
                    animationSpec = tween(
                        durationMillis = PAGE_NAV_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 210,
                        delayMillis = 60,
                        easing = LinearOutSlowInEasing
                    )
                )
                val exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 },
                    animationSpec = tween(
                        durationMillis = PAGE_NAV_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 120,
                        easing = FastOutLinearInEasing
                    )
                )
                enter togetherWith exit
            },
            label = "settingsRouteTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (it) {
                    SettingsRoute.Root -> {
                        SettingsSectionLabel("Audio")
                        SettingsItemCard(
                            title = "Audio plugins",
                            description = "Configure each playback core/plugin.",
                            icon = Icons.Default.GraphicEq,
                            onClick = onOpenAudioPlugins
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "General audio settings",
                            description = "Global output and playback behavior.",
                            icon = Icons.Default.Tune,
                            onClick = onOpenGeneralAudio
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Player settings",
                            description = "Player behavior and interaction preferences.",
                            icon = Icons.Default.Slideshow,
                            onClick = onOpenPlayer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Misc settings",
                            description = "Other app-wide preferences and utilities.",
                            icon = Icons.Default.MoreHoriz,
                            onClick = onOpenMisc
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Interface")
                        SettingsItemCard(
                            title = "UI settings",
                            description = "Appearance and layout preferences.",
                            icon = Icons.Default.Palette,
                            onClick = onOpenUi
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Info")
                        SettingsItemCard(
                            title = "About",
                            description = "App information, versions, and credits.",
                            icon = Icons.Default.Info,
                            onClick = onOpenAbout
                        )
                    }
                    SettingsRoute.AudioPlugins -> {
                        SettingsSectionLabel("Plugin configuration")
                        Text(
                            text = "This section will also handle enabling/disabling plugins in the future.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "FFmpeg",
                            description = "Core options for mainstream audio codecs.",
                            icon = Icons.Default.GraphicEq,
                            onClick = onOpenFfmpeg
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "OpenMPT",
                            description = "Core options for tracker/module playback.",
                            icon = Icons.Default.GraphicEq,
                            onClick = onOpenOpenMpt
                        )
                    }
                    SettingsRoute.PluginFfmpeg -> SampleRateSelectorCard(
                        title = "Output sample rate",
                        description = "Preferred output sample rate for this plugin. Auto uses the device/output stream rate.",
                        selectedHz = ffmpegSampleRateHz,
                        onSelected = onFfmpegSampleRateChanged
                    )
                    SettingsRoute.PluginOpenMpt -> SampleRateSelectorCard(
                        title = "Output sample rate",
                        description = "Preferred output sample rate for this plugin. Auto uses the device/output stream rate.",
                        selectedHz = openMptSampleRateHz,
                        onSelected = onOpenMptSampleRateChanged
                    )
                    SettingsRoute.GeneralAudio -> {
                        SettingsSectionLabel("Output behavior")
                        PlayerSettingToggleCard(
                            title = "Respond to headset media buttons",
                            description = "Allow headphone/bluetooth media buttons to control playback.",
                            checked = respondHeadphoneMediaButtons,
                            onCheckedChange = onRespondHeadphoneMediaButtonsChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Pause on output disconnect",
                            description = "Pause playback when headphones/output device disconnects.",
                            checked = pauseOnHeadphoneDisconnect,
                            onCheckedChange = onPauseOnHeadphoneDisconnectChanged
                        )
                    }
                    SettingsRoute.Player -> {
                        SettingsSectionLabel("Track selection")
                        PlayerSettingToggleCard(
                            title = "Play on track select",
                            description = "Start playback immediately when a file is tapped.",
                            checked = autoPlayOnTrackSelect,
                            onCheckedChange = onAutoPlayOnTrackSelectChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Open player on track select",
                            description = "Open full player when selecting a file. Disable to keep mini-player only.",
                            checked = openPlayerOnTrackSelect,
                            onCheckedChange = onOpenPlayerOnTrackSelectChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Auto-play next track when current ends",
                            description = "Automatically start the next visible track after natural playback end.",
                            checked = autoPlayNextTrackOnEnd,
                            onCheckedChange = onAutoPlayNextTrackOnEndChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Open player from notification",
                            description = "When tapping playback notification, open the full player instead of normal app start destination.",
                            checked = openPlayerFromNotification,
                            onCheckedChange = onOpenPlayerFromNotificationChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Persist repeat mode",
                            description = "Keep selected repeat mode across app restarts.",
                            checked = persistRepeatMode,
                            onCheckedChange = onPersistRepeatModeChanged
                        )
                    }
                    SettingsRoute.Misc -> {
                        SettingsSectionLabel("Browser behavior")
                        PlayerSettingToggleCard(
                            title = "Remember browser location",
                            description = "Restore last storage and folder when reopening the library browser.",
                            checked = rememberBrowserLocation,
                            onCheckedChange = onRememberBrowserLocationChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Clear home recents",
                            description = "Remove recent folders and recently played shortcuts from the Home screen.",
                            icon = Icons.Default.MoreHoriz,
                            onClick = onClearRecentHistory
                        )
                    }
                    SettingsRoute.Ui -> ThemeModeSelectorCard(
                        selectedMode = themeMode,
                        onSelectedModeChanged = onThemeModeChanged
                    )
                    SettingsRoute.About -> AboutSettingsBody()
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsItemCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerSettingToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SettingsPlaceholderBody(
    title: String,
    description: String
) {
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutSettingsBody() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionLabel = "v${BuildConfig.VERSION_NAME}-${BuildConfig.GIT_SHA}"
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Silicon Player",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = versionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = SettingsCardShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Android music player focused on mainstream and tracker/module formats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "UI: Jetpack Compose Material 3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "License: GPL v3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/Flopster101/SiliconPlayer")
                        )
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Open GitHub Repository")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

private data class SampleRateChoice(val hz: Int, val label: String)

private data class ThemeModeChoice(val mode: ThemeMode, val label: String)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ThemeModeSelectorCard(
    selectedMode: ThemeMode,
    onSelectedModeChanged: (ThemeMode) -> Unit
) {
    val options = listOf(
        ThemeModeChoice(ThemeMode.Auto, "Auto"),
        ThemeModeChoice(ThemeMode.Light, "Light"),
        ThemeModeChoice(ThemeMode.Dark, "Dark")
    )
    var dialogOpen by remember { mutableStateOf(false) }

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = { dialogOpen = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "App theme",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose Auto, Light, or Dark theme behavior.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selectedMode.label,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("App theme") },
            text = {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        options.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clickable {
                                        onSelectedModeChanged(option.mode)
                                        dialogOpen = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = option.mode == selectedMode,
                                    onClick = {
                                        onSelectedModeChanged(option.mode)
                                        dialogOpen = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SampleRateSelectorCard(
    title: String,
    description: String,
    selectedHz: Int,
    onSelected: (Int) -> Unit
) {
    fun formatSampleRateLabel(hz: Int): String {
        return if (hz == 0) "Auto" else String.format(Locale.US, "%,d Hz", hz)
    }

    val configuration = LocalConfiguration.current
    val options = listOf(
        SampleRateChoice(0, formatSampleRateLabel(0)),
        SampleRateChoice(8000, formatSampleRateLabel(8000)),
        SampleRateChoice(11025, formatSampleRateLabel(11025)),
        SampleRateChoice(12000, formatSampleRateLabel(12000)),
        SampleRateChoice(16000, formatSampleRateLabel(16000)),
        SampleRateChoice(22050, formatSampleRateLabel(22050)),
        SampleRateChoice(24000, formatSampleRateLabel(24000)),
        SampleRateChoice(32000, formatSampleRateLabel(32000)),
        SampleRateChoice(44100, formatSampleRateLabel(44100)),
        SampleRateChoice(48000, formatSampleRateLabel(48000)),
        SampleRateChoice(88200, formatSampleRateLabel(88200)),
        SampleRateChoice(96000, formatSampleRateLabel(96000))
    )
    val selectedLabel = options.firstOrNull { it.hz == selectedHz }?.label ?: "Auto"
    var dialogOpen by remember { mutableStateOf(false) }

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = { dialogOpen = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                val maxDialogListHeight = configuration.screenHeightDp.dp * 0.62f
                CompositionLocalProvider(
                    androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxDialogListHeight)
                            .verticalScroll(rememberScrollState())
                    ) {
                        options.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clickable {
                                        onSelected(option.hz)
                                        dialogOpen = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = option.hz == selectedHz,
                                    onClick = {
                                        onSelected(option.hz)
                                        dialogOpen = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {}
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniPlayerBar(
    file: File?,
    title: String,
    artist: String,
    artwork: ImageBitmap?,
    noArtworkIcon: ImageVector,
    isPlaying: Boolean,
    canResumeStoppedTrack: Boolean,
    positionSeconds: Double,
    durationSeconds: Double,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    onExpand: () -> Unit,
    onExpandDragProgress: (Float) -> Unit,
    onExpandDragCommit: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPlayPause: () -> Unit,
    onStopAndClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasTrack = file != null
    val formatLabel = file?.extension?.uppercase()?.ifBlank { "UNKNOWN" } ?: "EMPTY"
    val positionLabel = formatTimeForMini(positionSeconds)
    val durationLabel = formatTimeForMini(durationSeconds)
    val progress = if (durationSeconds > 0.0) {
        (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
        label = "miniPlayerProgress"
    )
    val compactControls = LocalConfiguration.current.screenWidthDp <= 420
    val controlButtonSize = if (compactControls) 36.dp else 40.dp
    val controlIconSize = if (compactControls) 20.dp else 22.dp
    val density = LocalDensity.current
    val expandSwipeThresholdPx = with(density) { 112.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val previewDistancePx = screenHeightPx * 0.72f
    var upwardDragPx by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(expandSwipeThresholdPx) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                val next = (upwardDragPx - dragAmount).coerceAtLeast(0f)
                                if (next > 0f || upwardDragPx > 0f) {
                                    upwardDragPx = next
                                    onExpandDragProgress((upwardDragPx / previewDistancePx).coerceIn(0f, 1f))
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (upwardDragPx >= expandSwipeThresholdPx) {
                                    onExpandDragCommit()
                                    upwardDragPx = 0f
                                    return@detectVerticalDragGestures
                                }
                                upwardDragPx = 0f
                                onExpandDragProgress(0f)
                            },
                            onDragCancel = {
                                upwardDragPx = 0f
                                onExpandDragProgress(0f)
                            }
                        )
                    }
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork,
                            contentDescription = "Mini player artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = noArtworkIcon,
                                contentDescription = "No artwork",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.size(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 170, delayMillis = 35)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 110))
                        },
                        label = "miniTitleSwap"
                    ) { animatedTitle ->
                        val marquee = animatedTitle.length > 26
                        Text(
                            text = animatedTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = if (marquee) {
                                Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 900
                                )
                            } else {
                                Modifier
                            }
                        )
                    }
                    AnimatedContent(
                        targetState = artist,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 170, delayMillis = 25)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 100))
                        },
                        label = "miniArtistSwap"
                    ) { animatedArtist ->
                        val marquee = animatedArtist.length > 30
                        Text(
                            text = animatedArtist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (marquee) {
                                Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 1100
                                )
                            } else {
                                Modifier
                            }
                        )
                    }
                    Text(
                        text = "$formatLabel • $positionLabel / $durationLabel",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPreviousTrack,
                        enabled = hasTrack && canPreviousTrack,
                        modifier = Modifier.size(controlButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous track",
                            modifier = Modifier.size(controlIconSize)
                        )
                    }
                    IconButton(
                        onClick = onStopAndClear,
                        modifier = Modifier.size(controlButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(controlIconSize)
                        )
                    }
                    IconButton(
                        onClick = onPlayPause,
                        enabled = hasTrack || canResumeStoppedTrack,
                        modifier = Modifier.size(controlButtonSize)
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "miniPlayPauseIcon"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                modifier = Modifier.size(controlIconSize)
                            )
                        }
                    }
                    IconButton(
                        onClick = onNextTrack,
                        enabled = hasTrack && canNextTrack,
                        modifier = Modifier.size(controlButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next track",
                            modifier = Modifier.size(controlIconSize)
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

private fun formatTimeForMini(seconds: Double): String {
    val safeSeconds = seconds.coerceAtLeast(0.0).toInt()
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun loadArtworkForFile(file: File): ImageBitmap? {
    loadEmbeddedArtwork(file)?.let { return it.asImageBitmap() }
    findFolderArtworkFile(file)?.let { folderImage ->
        decodeScaledBitmapFromFile(folderImage)?.let { return it.asImageBitmap() }
    }
    return null
}

private fun loadEmbeddedArtwork(file: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val embedded = retriever.embeddedPicture ?: return null
        decodeScaledBitmapFromBytes(embedded)
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun findFolderArtworkFile(trackFile: File): File? {
    val parent = trackFile.parentFile ?: return null
    if (!parent.isDirectory) return null

    val allowedNames = setOf(
        "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
        "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
        "album.jpg", "album.jpeg", "album.png", "album.webp",
        "front.jpg", "front.jpeg", "front.png", "front.webp",
        "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
    )

    return parent.listFiles()
        ?.firstOrNull { it.isFile && allowedNames.contains(it.name.lowercase()) }
}

private fun decodeScaledBitmapFromBytes(data: ByteArray, maxSize: Int = 1024): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}

private fun decodeScaledBitmapFromFile(file: File, maxSize: Int = 1024): Bitmap? {
    val path = file.absolutePath
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, options)
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxSize || currentHeight > maxSize) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}
