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

enum class SettingsRoute {
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

enum class ThemeMode(val storageValue: String, val label: String) {
    Auto("auto", "Auto"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

enum class AudioBackendPreference(val storageValue: String, val label: String, val nativeValue: Int) {
    Auto("auto", "Auto", 0),
    AAudio("aaudio", "AAudio", 1),
    OpenSLES("opensl", "OpenSL ES", 2),
    AudioTrack("audiotrack", "AudioTrack", 3);

    companion object {
        fun fromStorage(value: String?): AudioBackendPreference {
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

enum class AudioPerformanceMode(val storageValue: String, val label: String, val nativeValue: Int) {
    Auto("auto", "Auto", 0),
    LowLatency("low_latency", "Low latency", 1),
    None("none", "None", 2),
    PowerSaving("power_saving", "Power saving", 3);

    companion object {
        fun fromStorage(value: String?): AudioPerformanceMode {
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

enum class AudioBufferPreset(val storageValue: String, val label: String, val nativeValue: Int) {
    Auto("auto", "Auto", 0),
    Small("small", "Small", 1),
    Medium("medium", "Medium", 2),
    Large("large", "Large", 3);

    companion object {
        fun fromStorage(value: String?): AudioBufferPreset {
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
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

internal fun samePath(a: String?, b: String?): Boolean {
    val left = normalizedPath(a) ?: return false
    val right = normalizedPath(b) ?: return false
    return left == right
}

internal data class RecentPathEntry(
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

internal data class StoragePresentation(
    val label: String,
    val icon: ImageVector
)

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
internal const val RECENTS_LIMIT = 3
internal const val PREVIOUS_RESTART_THRESHOLD_SECONDS = 3.0

private object AppPreferenceKeys {
    const val PREFS_NAME = "silicon_player_settings"
    const val AUTO_PLAY_ON_TRACK_SELECT = "auto_play_on_track_select"
    const val OPEN_PLAYER_ON_TRACK_SELECT = "open_player_on_track_select"
    const val AUTO_PLAY_NEXT_TRACK_ON_END = "auto_play_next_track_on_end"
    const val PREVIOUS_RESTART_AFTER_THRESHOLD = "previous_restart_after_threshold"
    const val REMEMBER_BROWSER_LOCATION = "remember_browser_location"
    const val BROWSER_LAST_LOCATION_ID = "browser_last_location_id"
    const val BROWSER_LAST_DIRECTORY_PATH = "browser_last_directory_path"
    const val RESPOND_HEADPHONE_MEDIA_BUTTONS = "respond_headphone_media_buttons"
    const val PAUSE_ON_HEADPHONE_DISCONNECT = "pause_on_headphone_disconnect"
    const val AUDIO_BACKEND_PREFERENCE = "audio_backend_preference"
    const val AUDIO_PERFORMANCE_MODE = "audio_performance_mode"
    const val AUDIO_BUFFER_PRESET = "audio_buffer_preset"
    const val AUDIO_ALLOW_BACKEND_FALLBACK = "audio_allow_backend_fallback"
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
    external fun setCoreOption(coreName: String, optionName: String, optionValue: String)
    external fun setAudioPipelineConfig(
        backendPreference: Int,
        performanceMode: Int,
        bufferPreset: Int,
        allowFallback: Boolean
    )

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
    var settingsLaunchedFromPlayer by remember { mutableStateOf(false) }
    var settingsReturnView by remember { mutableStateOf(MainView.Home) }
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
    var lastUsedCoreName by remember { mutableStateOf<String?>(null) }
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
    var previousRestartsAfterThreshold by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.PREVIOUS_RESTART_AFTER_THRESHOLD, true)
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
            prefs.getInt(CorePreferenceKeys.CORE_RATE_FFMPEG, FfmpegDefaults.coreSampleRateHz)
        )
    }
    var openMptCoreSampleRateHz by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.CORE_RATE_OPENMPT, OpenMptDefaults.coreSampleRateHz)
        )
    }
    var openMptStereoSeparationPercent by remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT,
                OpenMptDefaults.stereoSeparationPercent
            )
        )
    }
    var openMptStereoSeparationAmigaPercent by remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT,
                OpenMptDefaults.stereoSeparationAmigaPercent
            )
        )
    }
    var openMptInterpolationFilterLength by remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH,
                OpenMptDefaults.interpolationFilterLength
            )
        )
    }
    var openMptAmigaResamplerMode by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE, OpenMptDefaults.amigaResamplerMode)
        )
    }
    var openMptAmigaResamplerApplyAllModules by remember {
        mutableStateOf(
            prefs.getBoolean(
                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES,
                OpenMptDefaults.amigaResamplerApplyAllModules
            )
        )
    }
    var openMptVolumeRampingStrength by remember {
        mutableIntStateOf(
            prefs.getInt(
                CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH,
                OpenMptDefaults.volumeRampingStrength
            )
        )
    }
    var openMptFt2XmVolumeRamping by remember {
        mutableStateOf(
            prefs.getBoolean(
                CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING,
                OpenMptDefaults.ft2XmVolumeRamping
            )
        )
    }
    var openMptMasterGainMilliBel by remember {
        mutableIntStateOf(
            prefs.getInt(CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL, OpenMptDefaults.masterGainMilliBel)
        )
    }
    var openMptSurroundEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(CorePreferenceKeys.OPENMPT_SURROUND_ENABLED, OpenMptDefaults.surroundEnabled)
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
    var audioBackendPreference by remember {
        mutableStateOf(
            AudioBackendPreference.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE,
                    AudioBackendPreference.Auto.storageValue
                )
            )
        )
    }
    var audioPerformanceMode by remember {
        mutableStateOf(
            AudioPerformanceMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.AUDIO_PERFORMANCE_MODE,
                    AudioPerformanceMode.Auto.storageValue
                )
            )
        )
    }
    var audioBufferPreset by remember {
        mutableStateOf(
            AudioBufferPreset.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.AUDIO_BUFFER_PRESET,
                    AudioBufferPreset.Auto.storageValue
                )
            )
        )
    }
    var audioAllowBackendFallback by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_ALLOW_BACKEND_FALLBACK, true)
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
        syncPlaybackServiceForState(
            context = context,
            selectedFile = selectedFile,
            metadataTitle = metadataTitle,
            metadataArtist = metadataArtist,
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
        activeRepeatMode = resolveActiveRepeatMode(preferredRepeatMode, repeatModeCapabilitiesFlags)
        applyRepeatModeToNative(activeRepeatMode)
    }

    fun cycleRepeatMode() {
        val next = cycleRepeatModeValue(
            activeRepeatMode = activeRepeatMode,
            repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags
        ) ?: return
        preferredRepeatMode = next
        activeRepeatMode = next
        applyRepeatModeToNative(next)
        showRepeatModeToast(context, next)
    }

    fun applyCoreOptionWithPolicy(
        coreName: String,
        optionName: String,
        optionValue: String,
        policy: CoreOptionApplyPolicy,
        optionLabel: String? = null
    ) {
        NativeBridge.setCoreOption(coreName, optionName, optionValue)
        maybeShowCoreOptionRestartToast(
            context,
            coreName = coreName,
            selectedFile = selectedFile,
            isPlaying = isPlaying,
            policy = policy,
            optionLabel = optionLabel
        )
    }

    fun applyNativeTrackSnapshot(snapshot: NativeTrackSnapshot) {
        snapshot.decoderName?.let { lastUsedCoreName = it }
        metadataTitle = snapshot.title
        metadataArtist = snapshot.artist
        metadataSampleRate = snapshot.sampleRateHz
        metadataChannelCount = snapshot.channelCount
        metadataBitDepthLabel = snapshot.bitDepthLabel
        repeatModeCapabilitiesFlags = snapshot.repeatModeCapabilitiesFlags
        duration = snapshot.durationSeconds
    }

    fun clearPlaybackMetadataState() {
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
    }

    fun resetAndOptionallyKeepLastTrack(keepLastTrack: Boolean) {
        if (keepLastTrack && selectedFile != null) {
            lastStoppedFile = selectedFile
        }
        NativeBridge.stopEngine()
        clearPlaybackMetadataState()
    }

    fun applyTrackSelection(file: File, autoStart: Boolean, expandOverride: Boolean? = null) {
        resetAndOptionallyKeepLastTrack(keepLastTrack = false)
        selectedFile = file
        isPlayerSurfaceVisible = true
        NativeBridge.loadAudio(file.absolutePath)
        applyNativeTrackSnapshot(readNativeTrackSnapshot())
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
        return currentTrackIndexForList(
            selectedFile = selectedFile,
            visiblePlayableFiles = visiblePlayableFiles
        )
    }

    fun playAdjacentTrack(offset: Int): Boolean {
        val target = adjacentTrackForOffset(
            selectedFile = selectedFile,
            visiblePlayableFiles = visiblePlayableFiles,
            offset = offset
        ) ?: return false
        applyTrackSelection(
            file = target,
            autoStart = true,
            expandOverride = null
        )
        return true
    }

    fun handlePreviousTrackAction(): Boolean {
        val hasTrackLoaded = selectedFile != null
        val shouldRestartCurrent = shouldRestartCurrentTrackOnPrevious(
            previousRestartsAfterThreshold = previousRestartsAfterThreshold,
            hasTrackLoaded = hasTrackLoaded,
            positionSeconds = position
        )

        if (shouldRestartCurrent) {
            NativeBridge.seekTo(0.0)
            position = 0.0
            syncPlaybackService()
            return true
        }

        if (playAdjacentTrack(-1)) return true

        if (hasTrackLoaded) {
            NativeBridge.seekTo(0.0)
            position = 0.0
            syncPlaybackService()
            return true
        }
        return false
    }

    fun restorePlayerStateFromSessionAndNative(openExpanded: Boolean) {
        val path = prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null)
        val file = path?.let { File(it) }?.takeIf { it.exists() && it.isFile } ?: return
        selectedFile = file
        isPlayerSurfaceVisible = true
        isPlayerExpanded = openExpanded
        applyNativeTrackSnapshot(readNativeTrackSnapshot())
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

    LaunchedEffect(previousRestartsAfterThreshold) {
        prefs.edit()
            .putBoolean(
                AppPreferenceKeys.PREVIOUS_RESTART_AFTER_THRESHOLD,
                previousRestartsAfterThreshold
            )
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
            .putInt(CorePreferenceKeys.CORE_RATE_FFMPEG, ffmpegCoreSampleRateHz)
            .apply()
        NativeBridge.setCoreOutputSampleRate("FFmpeg", ffmpegCoreSampleRateHz)
    }

    LaunchedEffect(openMptCoreSampleRateHz) {
        prefs.edit()
            .putInt(CorePreferenceKeys.CORE_RATE_OPENMPT, openMptCoreSampleRateHz)
            .apply()
        NativeBridge.setCoreOutputSampleRate("LibOpenMPT", openMptCoreSampleRateHz)
    }

    LaunchedEffect(openMptStereoSeparationPercent) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT,
                openMptStereoSeparationPercent
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.stereo_separation_percent",
            openMptStereoSeparationPercent.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Stereo separation"
        )
    }

    LaunchedEffect(openMptStereoSeparationAmigaPercent) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT,
                openMptStereoSeparationAmigaPercent
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.stereo_separation_amiga_percent",
            openMptStereoSeparationAmigaPercent.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Amiga stereo separation"
        )
    }

    LaunchedEffect(openMptInterpolationFilterLength) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH,
                openMptInterpolationFilterLength
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.interpolation_filter_length",
            openMptInterpolationFilterLength.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Interpolation filter"
        )
    }

    LaunchedEffect(openMptAmigaResamplerMode) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE,
                openMptAmigaResamplerMode
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.amiga_resampler_mode",
            openMptAmigaResamplerMode.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Amiga resampler"
        )
    }

    LaunchedEffect(openMptAmigaResamplerApplyAllModules) {
        prefs.edit()
            .putBoolean(
                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES,
                openMptAmigaResamplerApplyAllModules
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.amiga_resampler_apply_all_modules",
            openMptAmigaResamplerApplyAllModules.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Apply Amiga resampler to all modules"
        )
    }

    LaunchedEffect(openMptVolumeRampingStrength) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH,
                openMptVolumeRampingStrength
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.volume_ramping_strength",
            openMptVolumeRampingStrength.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Volume ramping strength"
        )
    }

    LaunchedEffect(openMptFt2XmVolumeRamping) {
        prefs.edit()
            .putBoolean(
                CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING,
                openMptFt2XmVolumeRamping
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.ft2_xm_volume_ramping",
            openMptFt2XmVolumeRamping.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "FT2 5ms XM ramping"
        )
    }

    LaunchedEffect(openMptMasterGainMilliBel) {
        prefs.edit()
            .putInt(
                CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL,
                openMptMasterGainMilliBel
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.master_gain_millibel",
            openMptMasterGainMilliBel.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Master gain"
        )
    }

    LaunchedEffect(openMptSurroundEnabled) {
        prefs.edit()
            .putBoolean(
                CorePreferenceKeys.OPENMPT_SURROUND_ENABLED,
                openMptSurroundEnabled
            )
            .apply()
        applyCoreOptionWithPolicy(
            "LibOpenMPT",
            "openmpt.surround_enabled",
            openMptSurroundEnabled.toString(),
            policy = CoreOptionApplyPolicy.Live,
            optionLabel = "Enable surround sound"
        )
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

    LaunchedEffect(audioBackendPreference) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE,
                audioBackendPreference.storageValue
            )
            .apply()
    }

    LaunchedEffect(audioPerformanceMode) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_PERFORMANCE_MODE,
                audioPerformanceMode.storageValue
            )
            .apply()
    }

    LaunchedEffect(audioBufferPreset) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_BUFFER_PRESET,
                audioBufferPreset.storageValue
            )
            .apply()
    }

    LaunchedEffect(audioAllowBackendFallback) {
        prefs.edit()
            .putBoolean(
                AppPreferenceKeys.AUDIO_ALLOW_BACKEND_FALLBACK,
                audioAllowBackendFallback
            )
            .apply()
    }

    LaunchedEffect(
        audioBackendPreference,
        audioPerformanceMode,
        audioBufferPreset,
        audioAllowBackendFallback
    ) {
        NativeBridge.setAudioPipelineConfig(
            backendPreference = audioBackendPreference.nativeValue,
            performanceMode = audioPerformanceMode.nativeValue,
            bufferPreset = audioBufferPreset.nativeValue,
            allowFallback = audioAllowBackendFallback
        )
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
        currentView == MainView.Settings && isPlayerSurfaceVisible -> 108.dp
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
        resetAndOptionallyKeepLastTrack(keepLastTrack = true)
        context.startService(
            Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_STOP_CLEAR)
        )
    }
    val currentCoreSettingsRoute = settingsRouteForCoreName(lastUsedCoreName)
    val canOpenCurrentCoreSettings = currentCoreSettingsRoute != null
    val exitSettingsToReturnView: () -> Unit = {
        val target = if (settingsLaunchedFromPlayer) settingsReturnView else MainView.Home
        settingsLaunchedFromPlayer = false
        settingsRoute = SettingsRoute.Root
        currentView = target
    }
    val openCurrentCoreSettings: () -> Unit = {
        settingsRouteForCoreName(lastUsedCoreName)?.let { route ->
            settingsReturnView = if (currentView == MainView.Settings) MainView.Home else currentView
            settingsLaunchedFromPlayer = true
            settingsRoute = route
            currentView = MainView.Settings
            isPlayerExpanded = false
        }
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
                        resetAndOptionallyKeepLastTrack(keepLastTrack = true)
                    }
                    PlaybackService.ACTION_BROADCAST_PREVIOUS_TRACK_REQUEST -> {
                        handlePreviousTrackAction()
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
            currentView == MainView.Settings && settingsLaunchedFromPlayer -> exitSettingsToReturnView()
            currentView == MainView.Settings && settingsRoute != SettingsRoute.Root -> {
                settingsRoute = when (settingsRoute) {
                    SettingsRoute.PluginFfmpeg, SettingsRoute.PluginOpenMpt -> SettingsRoute.AudioPlugins
                    else -> SettingsRoute.Root
                }
            }
            currentView == MainView.Settings -> exitSettingsToReturnView()
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
                                    settingsLaunchedFromPlayer = false
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
                                    appScope.launch {
                                        val contextualPlayableFiles = withContext(Dispatchers.IO) {
                                            val parent = file.parentFile
                                            if (parent != null && parent.exists() && parent.isDirectory) {
                                                repository.getFiles(parent)
                                                    .asSequence()
                                                    .filterNot { it.isDirectory }
                                                    .map { it.file }
                                                    .toList()
                                            } else {
                                                listOf(file)
                                            }
                                        }
                                        visiblePlayableFiles = contextualPlayableFiles
                                        applyTrackSelection(
                                            file = file,
                                            autoStart = true,
                                            expandOverride = openPlayerOnTrackSelect
                                        )
                                    }
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
                        bottomContentPadding = miniPlayerListInset,
                        onBack = {
                            if (settingsLaunchedFromPlayer) {
                                exitSettingsToReturnView()
                                return@SettingsScreen
                            }
                            if (settingsRoute != SettingsRoute.Root) {
                                settingsRoute = when (settingsRoute) {
                                    SettingsRoute.PluginFfmpeg, SettingsRoute.PluginOpenMpt -> SettingsRoute.AudioPlugins
                                    else -> SettingsRoute.Root
                                }
                            } else {
                                exitSettingsToReturnView()
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
                        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                        onPreviousRestartsAfterThresholdChanged = { previousRestartsAfterThreshold = it },
                        respondHeadphoneMediaButtons = respondHeadphoneMediaButtons,
                        onRespondHeadphoneMediaButtonsChanged = { respondHeadphoneMediaButtons = it },
                        pauseOnHeadphoneDisconnect = pauseOnHeadphoneDisconnect,
                        onPauseOnHeadphoneDisconnectChanged = { pauseOnHeadphoneDisconnect = it },
                        audioBackendPreference = audioBackendPreference,
                        onAudioBackendPreferenceChanged = { audioBackendPreference = it },
                        audioPerformanceMode = audioPerformanceMode,
                        onAudioPerformanceModeChanged = { audioPerformanceMode = it },
                        audioBufferPreset = audioBufferPreset,
                        onAudioBufferPresetChanged = { audioBufferPreset = it },
                        audioAllowBackendFallback = audioAllowBackendFallback,
                        onAudioAllowBackendFallbackChanged = { audioAllowBackendFallback = it },
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
                        openMptStereoSeparationPercent = openMptStereoSeparationPercent,
                        onOpenMptStereoSeparationPercentChanged = { openMptStereoSeparationPercent = it },
                        openMptStereoSeparationAmigaPercent = openMptStereoSeparationAmigaPercent,
                        onOpenMptStereoSeparationAmigaPercentChanged = { openMptStereoSeparationAmigaPercent = it },
                        openMptInterpolationFilterLength = openMptInterpolationFilterLength,
                        onOpenMptInterpolationFilterLengthChanged = { openMptInterpolationFilterLength = it },
                        openMptAmigaResamplerMode = openMptAmigaResamplerMode,
                        onOpenMptAmigaResamplerModeChanged = { openMptAmigaResamplerMode = it },
                        openMptAmigaResamplerApplyAllModules = openMptAmigaResamplerApplyAllModules,
                        onOpenMptAmigaResamplerApplyAllModulesChanged = { openMptAmigaResamplerApplyAllModules = it },
                        openMptVolumeRampingStrength = openMptVolumeRampingStrength,
                        onOpenMptVolumeRampingStrengthChanged = { openMptVolumeRampingStrength = it },
                        openMptFt2XmVolumeRamping = openMptFt2XmVolumeRamping,
                        onOpenMptFt2XmVolumeRampingChanged = { openMptFt2XmVolumeRamping = it },
                        openMptMasterGainMilliBel = openMptMasterGainMilliBel,
                        onOpenMptMasterGainMilliBelChanged = { openMptMasterGainMilliBel = it },
                        openMptSurroundEnabled = openMptSurroundEnabled,
                        onOpenMptSurroundEnabledChanged = { openMptSurroundEnabled = it },
                        onClearRecentHistory = {
                            recentFolders = emptyList()
                            recentPlayedFiles = emptyList()
                            prefs.edit()
                                .remove(AppPreferenceKeys.RECENT_FOLDERS)
                                .remove(AppPreferenceKeys.RECENT_PLAYED_FILES)
                                .apply()
                            Toast.makeText(
                                context,
                                "Home recents cleared",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onClearAllSettings = {
                            val pluginSnapshot = mapOf(
                                CorePreferenceKeys.CORE_RATE_FFMPEG to ffmpegCoreSampleRateHz,
                                CorePreferenceKeys.CORE_RATE_OPENMPT to openMptCoreSampleRateHz,
                                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT to openMptStereoSeparationPercent,
                                CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT to openMptStereoSeparationAmigaPercent,
                                CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH to openMptInterpolationFilterLength,
                                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE to openMptAmigaResamplerMode,
                                CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH to openMptVolumeRampingStrength,
                                CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL to openMptMasterGainMilliBel
                            )
                            val pluginBooleanSnapshot = mapOf(
                                CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES to openMptAmigaResamplerApplyAllModules,
                                CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING to openMptFt2XmVolumeRamping,
                                CorePreferenceKeys.OPENMPT_SURROUND_ENABLED to openMptSurroundEnabled
                            )

                            prefs.edit().clear().apply()

                            val restoreEditor = prefs.edit()
                            pluginSnapshot.forEach { (key, value) -> restoreEditor.putInt(key, value) }
                            pluginBooleanSnapshot.forEach { (key, value) -> restoreEditor.putBoolean(key, value) }
                            restoreEditor.apply()

                            autoPlayOnTrackSelect = true
                            openPlayerOnTrackSelect = true
                            autoPlayNextTrackOnEnd = true
                            previousRestartsAfterThreshold = true
                            respondHeadphoneMediaButtons = true
                            pauseOnHeadphoneDisconnect = true
                            audioBackendPreference = AudioBackendPreference.Auto
                            audioPerformanceMode = AudioPerformanceMode.Auto
                            audioBufferPreset = AudioBufferPreset.Auto
                            audioAllowBackendFallback = true
                            openPlayerFromNotification = true
                            persistRepeatMode = true
                            preferredRepeatMode = RepeatMode.None
                            rememberBrowserLocation = true
                            lastBrowserLocationId = null
                            lastBrowserDirectoryPath = null
                            recentFolders = emptyList()
                            recentPlayedFiles = emptyList()
                            browserLaunchLocationId = null
                            browserLaunchDirectoryPath = null
                            onThemeModeChanged(ThemeMode.Auto)

                            Toast.makeText(
                                context,
                                "All app settings cleared",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onClearAllPluginSettings = {
                            ffmpegCoreSampleRateHz = 0
                            openMptCoreSampleRateHz = OpenMptDefaults.coreSampleRateHz
                            openMptStereoSeparationPercent = OpenMptDefaults.stereoSeparationPercent
                            openMptStereoSeparationAmigaPercent = OpenMptDefaults.stereoSeparationAmigaPercent
                            openMptInterpolationFilterLength = OpenMptDefaults.interpolationFilterLength
                            openMptAmigaResamplerMode = OpenMptDefaults.amigaResamplerMode
                            openMptAmigaResamplerApplyAllModules = OpenMptDefaults.amigaResamplerApplyAllModules
                            openMptVolumeRampingStrength = OpenMptDefaults.volumeRampingStrength
                            openMptFt2XmVolumeRamping = OpenMptDefaults.ft2XmVolumeRamping
                            openMptMasterGainMilliBel = OpenMptDefaults.masterGainMilliBel
                            openMptSurroundEnabled = OpenMptDefaults.surroundEnabled

                            prefs.edit()
                                .remove(CorePreferenceKeys.CORE_RATE_FFMPEG)
                                .remove(CorePreferenceKeys.CORE_RATE_OPENMPT)
                                .remove(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT)
                                .remove(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT)
                                .remove(CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH)
                                .remove(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE)
                                .remove(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES)
                                .remove(CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH)
                                .remove(CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING)
                                .remove(CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL)
                                .remove(CorePreferenceKeys.OPENMPT_SURROUND_ENABLED)
                                .apply()

                            Toast.makeText(
                                context,
                                "Plugin settings cleared",
                                Toast.LENGTH_SHORT
                            ).show()
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
                        onCycleRepeatMode = {},
                        canOpenCoreSettings = canOpenCurrentCoreSettings,
                        onOpenCoreSettings = openCurrentCoreSettings
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
                        onPreviousTrack = { handlePreviousTrackAction() },
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
                    onPreviousTrack = { handlePreviousTrackAction() },
                    onNextTrack = { playAdjacentTrack(1) },
                    onPreviousSubtune = {},
                    onNextSubtune = {},
                    onOpenSubtuneSelector = {},
                    onCycleRepeatMode = { cycleRepeatMode() },
                    canOpenCoreSettings = canOpenCurrentCoreSettings,
                    onOpenCoreSettings = openCurrentCoreSettings
                )
            }
        }
    }
}
