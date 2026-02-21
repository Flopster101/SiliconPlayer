package com.flopster101.siliconplayer

import android.os.Bundle
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import com.flopster101.siliconplayer.playback.loadPlayableSiblingFilesForExternalIntent
import com.flopster101.siliconplayer.playback.applyTrackSelectionAction
import com.flopster101.siliconplayer.session.exportCachedFilesToTree
import com.flopster101.siliconplayer.ui.visualization.rememberVisualizationModeCoordinator
import com.flopster101.siliconplayer.ui.theme.SiliconPlayerTheme
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.provider.Settings
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    external fun getCoreCapabilities(coreName: String): Int
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNavigation(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
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
    var forceMono by remember { mutableStateOf(false) }
    var showAudioEffectsDialog by remember { mutableStateOf(false) }

    // Temporary state for dialog (to support Cancel)
    var tempMasterVolumeDb by remember { mutableFloatStateOf(0f) }
    var tempPluginVolumeDb by remember { mutableFloatStateOf(0f) }
    var tempSongVolumeDb by remember { mutableFloatStateOf(0f) }
    var tempForceMono by remember { mutableStateOf(false) }

    var currentView by remember { mutableStateOf(MainView.Home) }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.Root) }
    var settingsRouteHistory by remember { mutableStateOf<List<SettingsRoute>>(emptyList()) }
    var selectedPluginName by remember { mutableStateOf<String?>(null) }
    var settingsLaunchedFromPlayer by remember { mutableStateOf(false) }
    var settingsReturnView by remember { mutableStateOf(MainView.Home) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var lastStoppedFile by remember { mutableStateOf<File?>(null) }
    var lastStoppedSourceId by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableDoubleStateOf(0.0) }
    var position by remember { mutableDoubleStateOf(0.0) }
    var isPlaying by remember { mutableStateOf(false) }
    var seekInProgress by remember { mutableStateOf(false) }
    var seekUiBusy by remember { mutableStateOf(false) }
    var playbackStartInProgress by remember { mutableStateOf(false) }
    var seekStartedAtMs by remember { mutableLongStateOf(0L) }
    var seekRequestedAtMs by remember { mutableLongStateOf(0L) }
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
    var subtuneCount by remember { mutableIntStateOf(0) }
    var currentSubtuneIndex by remember { mutableIntStateOf(0) }
    var subtuneEntries by remember { mutableStateOf<List<SubtuneEntry>>(emptyList()) }
    var showSubtuneSelectorDialog by remember { mutableStateOf(false) }
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
    var visiblePlayableFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var browserLaunchLocationId by remember { mutableStateOf<String?>(null) }
    var browserLaunchDirectoryPath by remember { mutableStateOf<String?>(null) }
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
    var visualizationMode by remember {
        mutableStateOf(
            VisualizationMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue)
            )
        )
    }
    var enabledVisualizationModes by remember {
        mutableStateOf(
            parseEnabledVisualizationModes(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES, null)
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
    var ffmpegCoreSampleRateHz by settingsStates.ffmpegCoreSampleRateHz
    var ffmpegGaplessRepeatTrack by settingsStates.ffmpegGaplessRepeatTrack
    var openMptCoreSampleRateHz by settingsStates.openMptCoreSampleRateHz
    var vgmPlayCoreSampleRateHz by settingsStates.vgmPlayCoreSampleRateHz
    var gmeCoreSampleRateHz by settingsStates.gmeCoreSampleRateHz
    var sidPlayFpCoreSampleRateHz by settingsStates.sidPlayFpCoreSampleRateHz
    var lazyUsf2CoreSampleRateHz by settingsStates.lazyUsf2CoreSampleRateHz
    var lazyUsf2UseHleAudio by settingsStates.lazyUsf2UseHleAudio
    var vio2sfInterpolationQuality by settingsStates.vio2sfInterpolationQuality
    var sc68SamplingRateHz by settingsStates.sc68SamplingRateHz
    var sc68Asid by settingsStates.sc68Asid
    var sc68DefaultTimeSeconds by settingsStates.sc68DefaultTimeSeconds
    var sc68YmEngine by settingsStates.sc68YmEngine
    var sc68YmVolModel by settingsStates.sc68YmVolModel
    var sc68AmigaFilter by settingsStates.sc68AmigaFilter
    var sc68AmigaBlend by settingsStates.sc68AmigaBlend
    var sc68AmigaClock by settingsStates.sc68AmigaClock
    var sidPlayFpBackend by settingsStates.sidPlayFpBackend
    var sidPlayFpClockMode by settingsStates.sidPlayFpClockMode
    var sidPlayFpSidModelMode by settingsStates.sidPlayFpSidModelMode
    var sidPlayFpFilter6581Enabled by settingsStates.sidPlayFpFilter6581Enabled
    var sidPlayFpFilter8580Enabled by settingsStates.sidPlayFpFilter8580Enabled
    var sidPlayFpDigiBoost8580 by settingsStates.sidPlayFpDigiBoost8580
    var sidPlayFpFilterCurve6581Percent by settingsStates.sidPlayFpFilterCurve6581Percent
    var sidPlayFpFilterRange6581Percent by settingsStates.sidPlayFpFilterRange6581Percent
    var sidPlayFpFilterCurve8580Percent by settingsStates.sidPlayFpFilterCurve8580Percent
    var sidPlayFpReSidFpFastSampling by settingsStates.sidPlayFpReSidFpFastSampling
    var sidPlayFpReSidFpCombinedWaveformsStrength by settingsStates.sidPlayFpReSidFpCombinedWaveformsStrength
    var gmeTempoPercent by settingsStates.gmeTempoPercent
    var gmeStereoSeparationPercent by settingsStates.gmeStereoSeparationPercent
    var gmeEchoEnabled by settingsStates.gmeEchoEnabled
    var gmeAccuracyEnabled by settingsStates.gmeAccuracyEnabled
    var gmeEqTrebleDecibel by settingsStates.gmeEqTrebleDecibel
    var gmeEqBassHz by settingsStates.gmeEqBassHz
    var gmeSpcUseBuiltInFade by settingsStates.gmeSpcUseBuiltInFade
    var gmeSpcInterpolation by settingsStates.gmeSpcInterpolation
    var gmeSpcUseNativeSampleRate by settingsStates.gmeSpcUseNativeSampleRate
    var vgmPlayLoopCount by settingsStates.vgmPlayLoopCount
    var vgmPlayAllowNonLoopingLoop by settingsStates.vgmPlayAllowNonLoopingLoop
    var vgmPlayVsyncRate by settingsStates.vgmPlayVsyncRate
    var vgmPlayResampleMode by settingsStates.vgmPlayResampleMode
    var vgmPlayChipSampleMode by settingsStates.vgmPlayChipSampleMode
    var vgmPlayChipSampleRate by settingsStates.vgmPlayChipSampleRate
    var vgmPlayChipCoreSelections by settingsStates.vgmPlayChipCoreSelections
    var openMptStereoSeparationPercent by settingsStates.openMptStereoSeparationPercent
    var openMptStereoSeparationAmigaPercent by settingsStates.openMptStereoSeparationAmigaPercent
    var openMptInterpolationFilterLength by settingsStates.openMptInterpolationFilterLength
    var openMptAmigaResamplerMode by settingsStates.openMptAmigaResamplerMode
    var openMptAmigaResamplerApplyAllModules by settingsStates.openMptAmigaResamplerApplyAllModules
    var openMptVolumeRampingStrength by settingsStates.openMptVolumeRampingStrength
    var openMptFt2XmVolumeRamping by settingsStates.openMptFt2XmVolumeRamping
    var openMptMasterGainMilliBel by settingsStates.openMptMasterGainMilliBel
    var openMptSurroundEnabled by settingsStates.openMptSurroundEnabled
    var respondHeadphoneMediaButtons by settingsStates.respondHeadphoneMediaButtons
    var pauseOnHeadphoneDisconnect by settingsStates.pauseOnHeadphoneDisconnect
    var audioBackendPreference by settingsStates.audioBackendPreference
    var audioPerformanceMode by settingsStates.audioPerformanceMode
    var audioBufferPreset by settingsStates.audioBufferPreset
    var audioResamplerPreference by settingsStates.audioResamplerPreference
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
    val currentTrackPathOrUrl = currentPlaybackSourceId ?: selectedFile?.absolutePath
    val playbackSourceLabel = remember(selectedFile, currentPlaybackSourceId) {
        resolvePlaybackSourceLabel(selectedFile, currentPlaybackSourceId)
    }

    // Get supported extensions from JNI
    val supportedExtensions = remember { NativeBridge.getSupportedExtensions().toSet() }
    val repository = remember(supportedExtensions, sortArchivesBeforeFiles, browserNameSortMode) {
        com.flopster101.siliconplayer.data.FileRepository(
            supportedExtensions = supportedExtensions,
            sortArchivesBeforeFiles = sortArchivesBeforeFiles,
            nameSortMode = browserNameSortMode
        )
    }

    // Handle pending file from intent
    var pendingFileToOpen by remember { mutableStateOf<File?>(initialFileToOpen) }
    var pendingFileFromExternalIntent by remember { mutableStateOf(initialFileFromExternalIntent) }

    val loadSongVolumeForFile = buildLoadSongVolumeForFileDelegate(
        volumeDatabase = volumeDatabase,
        onSongVolumeDbChanged = { songVolumeDb = it },
        onSongGainChanged = { NativeBridge.setSongGain(it) }
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
        metadataTitleProvider = { metadataTitle },
        metadataArtistProvider = { metadataArtist },
        durationProvider = { duration },
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

    LaunchedEffect(visualizationMode) {
        prefs.edit()
            .putString(AppPreferenceKeys.VISUALIZATION_MODE, visualizationMode.storageValue)
            .apply()
    }

    LaunchedEffect(enabledVisualizationModes) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.VISUALIZATION_ENABLED_MODES,
                serializeEnabledVisualizationModes(enabledVisualizationModes)
            )
            .apply()
    }

    LaunchedEffect(recentPlayedFiles, recentFilesLimit) {
        runtimeDelegates.scheduleRecentPlayedMetadataBackfill()
    }

    val playbackStateDelegates = AppNavigationPlaybackStateDelegates(
        context = context,
        prefs = prefs,
        selectedFileProvider = { selectedFile },
        onSelectedFileChanged = { selectedFile = it },
        currentPlaybackSourceIdProvider = { currentPlaybackSourceId },
        onCurrentPlaybackSourceIdChanged = { currentPlaybackSourceId = it },
        isPlayingProvider = { isPlaying },
        lastBrowserLocationIdProvider = { lastBrowserLocationId },
        isLocalPlayableFile = isLocalPlayableFile,
        metadataTitleProvider = { metadataTitle },
        metadataArtistProvider = { metadataArtist },
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        addRecentPlayedTrack = { path, locationId, title, artist ->
            runtimeDelegates.addRecentPlayedTrack(path, locationId, title, artist)
        },
        syncPlaybackService = { runtimeDelegates.syncPlaybackService() },
        readNativeTrackSnapshot = { readNativeTrackSnapshot() },
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
        onArtworkBitmapCleared = { artworkBitmap = null },
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
        cacheRootProvider = { File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR) },
        lastBrowserLocationIdProvider = { lastBrowserLocationId },
        onResetPlayback = { playbackStateDelegates.resetAndOptionallyKeepLastTrack(keepLastTrack = false) },
        onSelectedFileChanged = { selectedFile = it },
        onCurrentPlaybackSourceIdChanged = { currentPlaybackSourceId = it },
        onPlayerSurfaceVisibleChanged = { isPlayerSurfaceVisible = it },
        loadSongVolumeForFile = loadSongVolumeForFile,
        onSongVolumeDbChanged = { songVolumeDb = it },
        onSongGainChanged = { NativeBridge.setSongGain(it) },
        readNativeTrackSnapshot = { readNativeTrackSnapshot() },
        applyNativeTrackSnapshot = { snapshot -> playbackStateDelegates.applyNativeTrackSnapshot(snapshot) },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        onPositionChanged = { position = it },
        onArtworkBitmapCleared = { artworkBitmap = null },
        onIsPlayingChanged = { isPlaying = it },
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        onAddRecentPlayedTrack = { path, locationId, title, artist ->
            runtimeDelegates.addRecentPlayedTrack(path, locationId, title, artist)
        },
        metadataTitleProvider = { metadataTitle },
        metadataArtistProvider = { metadataArtist },
        onStartEngine = { NativeBridge.startEngine() },
        scheduleRecentTrackMetadataRefresh = { sourceId, locationId ->
            runtimeDelegates.scheduleRecentTrackMetadataRefresh(sourceId, locationId)
        },
        onPlayerExpandedChanged = { isPlayerExpanded = it },
        onPlaybackStartInProgressChanged = { playbackStartInProgress = it },
        syncPlaybackService = { runtimeDelegates.syncPlaybackService() }
    )

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
        onVisiblePlayableFilesChanged = { visiblePlayableFiles = it },
        onPlayerSurfaceVisibleChanged = { isPlayerSurfaceVisible = it },
        onSongVolumeDbChanged = { songVolumeDb = it },
        onSongGainChanged = { NativeBridge.setSongGain(it) },
        applyNativeTrackSnapshot = { snapshot -> playbackStateDelegates.applyNativeTrackSnapshot(snapshot) },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        onPositionChanged = { position = it },
        onArtworkBitmapCleared = { artworkBitmap = null },
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        onAddRecentPlayedTrack = { path, locationId, title, artist ->
            runtimeDelegates.addRecentPlayedTrack(path, locationId, title, artist)
        },
        metadataTitleProvider = { metadataTitle },
        metadataArtistProvider = { metadataArtist },
        applyRepeatModeToNative = { mode -> applyRepeatModeToNative(mode) },
        onStartEngine = { NativeBridge.startEngine() },
        onIsPlayingChanged = { isPlaying = it },
        scheduleRecentTrackMetadataRefresh = { sourceId, locationId ->
            runtimeDelegates.scheduleRecentTrackMetadataRefresh(sourceId, locationId)
        },
        onPlayerExpandedChanged = { isPlayerExpanded = it },
        syncPlaybackService = playbackSessionCoordinator.syncPlaybackService,
        onBrowserLaunchTargetChanged = { locationId, directoryPath ->
            browserLaunchLocationId = locationId
            browserLaunchDirectoryPath = directoryPath
        },
        onCurrentViewChanged = { currentView = it },
        onAddRecentFolder = { path, locationId ->
            runtimeDelegates.addRecentFolder(path, locationId)
        },
        onApplyTrackSelection = { file, autoStart, expandOverride, sourceIdOverride ->
            trackLoadDelegates.applyTrackSelection(
                file = file,
                autoStart = autoStart,
                expandOverride = expandOverride,
                sourceIdOverride = sourceIdOverride
            )
        }
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
        visiblePlayableFilesProvider = { visiblePlayableFiles },
        previousRestartsAfterThresholdProvider = { previousRestartsAfterThreshold },
        positionSecondsProvider = { position },
        onPositionChanged = { position = it },
        onSyncPlaybackService = playbackSessionCoordinator.syncPlaybackService,
        onApplyTrackSelection = { file, autoStart, expand ->
            trackLoadDelegates.applyTrackSelection(file = file, autoStart = autoStart, expandOverride = expand)
        },
        onApplyManualInputSelection = { rawInput, options, expandOverride ->
            manualOpenDelegates.applyManualInputSelection(rawInput, options, expandOverride)
        }
    )

    AppNavigationPlaybackPollEffects(
        selectedFile = selectedFile,
        isPlayingProvider = { isPlaying },
        selectedFileProvider = { selectedFile },
        seekInProgress = seekInProgress,
        seekStartedAtMs = seekStartedAtMs,
        seekRequestedAtMs = seekRequestedAtMs,
        seekUiBusyThresholdMs = seekUiBusyThresholdMs,
        duration = duration,
        subtuneCountProvider = { subtuneCount },
        currentSubtuneIndexProvider = { currentSubtuneIndex },
        currentPlaybackSourceIdProvider = { currentPlaybackSourceId },
        playbackWatchPath = playbackWatchPath,
        metadataTitleProvider = { metadataTitle },
        metadataArtistProvider = { metadataArtist },
        lastBrowserLocationId = lastBrowserLocationId,
        autoPlayNextTrackOnEnd = autoPlayNextTrackOnEnd,
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
            runtimeDelegates.addRecentPlayedTrack(
                path,
                locationId,
                title,
                artist
            )
        },
        onPlayAdjacentTrack = { trackNavDelegates.playAdjacentTrack(it) },
        isLocalPlayableFile = isLocalPlayableFile
    )

    AppNavigationTrackPreferenceEffects(
        context = context,
        prefs = prefs,
        selectedFile = selectedFile,
        currentPlaybackSourceId = currentPlaybackSourceId,
        preferredRepeatMode = preferredRepeatMode,
        isPlayerSurfaceVisible = isPlayerSurfaceVisible,
        autoPlayOnTrackSelect = autoPlayOnTrackSelect,
        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
        autoPlayNextTrackOnEnd = autoPlayNextTrackOnEnd,
        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
        fadePauseResume = fadePauseResume,
        rememberBrowserLocation = rememberBrowserLocation,
        sortArchivesBeforeFiles = sortArchivesBeforeFiles,
        browserNameSortMode = browserNameSortMode,
        onArtworkBitmapChanged = { artworkBitmap = it },
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        resetSubtuneUiState = {
            subtuneCount = 0
            currentSubtuneIndex = 0
            subtuneEntries = emptyList()
            showSubtuneSelectorDialog = false
        },
        onRememberBrowserLocationCleared = {
            lastBrowserLocationId = null
            lastBrowserDirectoryPath = null
        }
    )

    AppNavigationCoreEffects(
        prefs = prefs,
        ffmpegCoreSampleRateHz = ffmpegCoreSampleRateHz,
        ffmpegGaplessRepeatTrack = ffmpegGaplessRepeatTrack,
        openMptCoreSampleRateHz = openMptCoreSampleRateHz,
        vgmPlayCoreSampleRateHz = vgmPlayCoreSampleRateHz,
        gmeCoreSampleRateHz = gmeCoreSampleRateHz,
        sidPlayFpCoreSampleRateHz = sidPlayFpCoreSampleRateHz,
        lazyUsf2CoreSampleRateHz = lazyUsf2CoreSampleRateHz,
        lazyUsf2UseHleAudio = lazyUsf2UseHleAudio,
        vio2sfInterpolationQuality = vio2sfInterpolationQuality,
        sc68SamplingRateHz = sc68SamplingRateHz,
        sc68Asid = sc68Asid,
        sc68DefaultTimeSeconds = sc68DefaultTimeSeconds,
        sc68YmEngine = sc68YmEngine,
        sc68YmVolModel = sc68YmVolModel,
        sc68AmigaFilter = sc68AmigaFilter,
        sc68AmigaBlend = sc68AmigaBlend,
        sc68AmigaClock = sc68AmigaClock,
        sidPlayFpBackend = sidPlayFpBackend,
        sidPlayFpClockMode = sidPlayFpClockMode,
        sidPlayFpSidModelMode = sidPlayFpSidModelMode,
        sidPlayFpFilter6581Enabled = sidPlayFpFilter6581Enabled,
        sidPlayFpFilter8580Enabled = sidPlayFpFilter8580Enabled,
        sidPlayFpDigiBoost8580 = sidPlayFpDigiBoost8580,
        sidPlayFpFilterCurve6581Percent = sidPlayFpFilterCurve6581Percent,
        sidPlayFpFilterRange6581Percent = sidPlayFpFilterRange6581Percent,
        sidPlayFpFilterCurve8580Percent = sidPlayFpFilterCurve8580Percent,
        sidPlayFpReSidFpFastSampling = sidPlayFpReSidFpFastSampling,
        sidPlayFpReSidFpCombinedWaveformsStrength = sidPlayFpReSidFpCombinedWaveformsStrength,
        gmeTempoPercent = gmeTempoPercent,
        gmeStereoSeparationPercent = gmeStereoSeparationPercent,
        gmeEchoEnabled = gmeEchoEnabled,
        gmeAccuracyEnabled = gmeAccuracyEnabled,
        gmeEqTrebleDecibel = gmeEqTrebleDecibel,
        gmeEqBassHz = gmeEqBassHz,
        gmeSpcUseBuiltInFade = gmeSpcUseBuiltInFade,
        gmeSpcInterpolation = gmeSpcInterpolation,
        gmeSpcUseNativeSampleRate = gmeSpcUseNativeSampleRate,
        unknownTrackDurationSeconds = unknownTrackDurationSeconds,
        vgmPlayLoopCount = vgmPlayLoopCount,
        vgmPlayAllowNonLoopingLoop = vgmPlayAllowNonLoopingLoop,
        vgmPlayVsyncRate = vgmPlayVsyncRate,
        vgmPlayResampleMode = vgmPlayResampleMode,
        vgmPlayChipSampleMode = vgmPlayChipSampleMode,
        vgmPlayChipSampleRate = vgmPlayChipSampleRate,
        vgmPlayChipCoreSelections = vgmPlayChipCoreSelections,
        openMptStereoSeparationPercent = openMptStereoSeparationPercent,
        openMptStereoSeparationAmigaPercent = openMptStereoSeparationAmigaPercent,
        openMptInterpolationFilterLength = openMptInterpolationFilterLength,
        openMptAmigaResamplerMode = openMptAmigaResamplerMode,
        openMptAmigaResamplerApplyAllModules = openMptAmigaResamplerApplyAllModules,
        openMptVolumeRampingStrength = openMptVolumeRampingStrength,
        openMptFt2XmVolumeRamping = openMptFt2XmVolumeRamping,
        openMptMasterGainMilliBel = openMptMasterGainMilliBel,
        openMptSurroundEnabled = openMptSurroundEnabled,
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
        metadataTitle = metadataTitle,
        metadataArtist = metadataArtist,
        duration = duration,
        notificationOpenSignal = notificationOpenSignal,
        syncPlaybackService = playbackSessionCoordinator.syncPlaybackService,
        restorePlayerStateFromSessionAndNative = playbackSessionCoordinator.restorePlayerStateFromSessionAndNative
    )

    if (!storagePermissionState.hasPermission) {
        StoragePermissionRequiredScreen(
            onRequestPermission = storagePermissionState.requestPermission
        )
        return
    }

    val miniPlayerListInset = rememberMiniPlayerListInset(
        currentView = currentView,
        isPlayerSurfaceVisible = isPlayerSurfaceVisible
    )
    var miniExpandPreviewProgress by remember { mutableFloatStateOf(0f) }
    var expandFromMiniDrag by remember { mutableStateOf(false) }
    var collapseFromSwipe by remember { mutableStateOf(false) }
    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val miniPreviewLiftPx = with(LocalDensity.current) { 28.dp.toPx() }
    val stopAndEmptyTrackBase = buildStopAndEmptyTrackDelegate(
        context = context,
        playbackStateDelegates = playbackStateDelegates
    )
    val stopAndEmptyTrack: () -> Unit = {
        trackLoadDelegates.cancelPendingTrackSelection()
        stopAndEmptyTrackBase()
    }
    val activeCoreNameForUi = lastUsedCoreName
    val currentCorePluginName = pluginNameForCoreName(activeCoreNameForUi)
    val canOpenCurrentCoreSettings = currentCorePluginName != null
    val visualizationCoordinator = rememberVisualizationModeCoordinator(
        enabledModes = enabledVisualizationModes,
        activeCoreName = activeCoreNameForUi,
        currentMode = visualizationMode,
        onModeChanged = { visualizationMode = it },
        onEnabledModesChanged = { enabledVisualizationModes = it }
    )
    val availableVisualizationModes = visualizationCoordinator.availableModes
    val cycleVisualizationMode = visualizationCoordinator.onCycleMode
    val setVisualizationMode = visualizationCoordinator.onSelectMode
    val setEnabledVisualizationModes = visualizationCoordinator.onSetEnabledModes
    val settingsNavigationCoordinator = buildSettingsNavigationCoordinator(
        currentView = currentView,
        settingsRoute = settingsRoute,
        settingsRouteHistory = settingsRouteHistory,
        settingsLaunchedFromPlayer = settingsLaunchedFromPlayer,
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
        onMiniExpandPreviewProgressChanged = { miniExpandPreviewProgress = it },
        onExpandFromMiniDragChanged = { expandFromMiniDrag = it },
        onCollapseFromSwipeChanged = { collapseFromSwipe = it }
    )

    val hidePlayerSurface = buildHidePlayerSurfaceDelegate(
        onStopAndEmptyTrack = stopAndEmptyTrack,
        onPlayerExpandedChanged = { isPlayerExpanded = it },
        onPlayerSurfaceVisibleChanged = { isPlayerSurfaceVisible = it }
    )
    RegisterPlaybackBroadcastReceiver(
        context = context,
        onCleared = {
            trackLoadDelegates.cancelPendingTrackSelection()
            playbackStateDelegates.resetAndOptionallyKeepLastTrack(keepLastTrack = true)
        },
        onPreviousTrackRequested = { trackNavDelegates.handlePreviousTrackAction() },
        onNextTrackRequested = { trackNavDelegates.playAdjacentTrack(1) }
    )

    BackHandler(enabled = isPlayerExpanded || currentView == MainView.Settings) {
        handleAppNavigationBackAction(
            isPlayerExpanded = isPlayerExpanded,
            currentView = currentView,
            settingsLaunchedFromPlayer = settingsLaunchedFromPlayer,
            onPlayerExpandedChanged = { isPlayerExpanded = it },
            popSettingsRoute = popSettingsRoute,
            exitSettingsToReturnView = exitSettingsToReturnView
        )
    }

    @Composable
    fun BoxScope.PlayerOverlayAndDialogsSection() {
        val canResumeStoppedTrack = lastStoppedFile?.exists() == true || !lastStoppedSourceId.isNullOrBlank()
        val startPlaybackFromSurface = buildStartPlaybackFromSurfaceAction(
            selectedFileProvider = { selectedFile },
            currentPlaybackSourceIdProvider = { currentPlaybackSourceId },
            lastBrowserLocationIdProvider = { lastBrowserLocationId },
            metadataTitleProvider = { metadataTitle },
            metadataArtistProvider = { metadataArtist },
            activeRepeatModeProvider = { activeRepeatMode },
            isLocalPlayableFile = isLocalPlayableFile,
            addRecentPlayedTrack = { path, locationId, title, artist ->
                runtimeDelegates.addRecentPlayedTrack(path, locationId, title, artist)
            },
            applyRepeatModeToNative = { mode -> applyRepeatModeToNative(mode) },
            startEngine = { startEngineWithPauseResumeFade() },
            onPlayingStateChanged = { isPlaying = it },
            scheduleRecentTrackMetadataRefresh = { sourceId, locationId ->
                runtimeDelegates.scheduleRecentTrackMetadataRefresh(sourceId, locationId)
            },
            syncPlaybackService = playbackSessionCoordinator.syncPlaybackService,
            resumeLastStoppedTrack = { autoStart ->
                trackNavDelegates.resumeLastStoppedTrack(autoStart = autoStart)
            }
        )
        val openAudioEffectsDialog = buildOpenAudioEffectsDialogAction(
            masterVolumeDbProvider = { masterVolumeDb },
            pluginVolumeDbProvider = { pluginVolumeDb },
            songVolumeDbProvider = { songVolumeDb },
            forceMonoProvider = { forceMono },
            onTempMasterVolumeChanged = { tempMasterVolumeDb = it },
            onTempPluginVolumeChanged = { tempPluginVolumeDb = it },
            onTempSongVolumeChanged = { tempSongVolumeDb = it },
            onTempForceMonoChanged = { tempForceMono = it },
            onShowAudioEffectsDialogChanged = { showAudioEffectsDialog = it }
        )
        AppNavigationPlayerOverlaysSection(
            isPlayerSurfaceVisible = isPlayerSurfaceVisible,
            isPlayerExpanded = isPlayerExpanded,
            miniExpandPreviewProgress = miniExpandPreviewProgress,
            onMiniExpandPreviewProgressChanged = { miniExpandPreviewProgress = it },
            expandFromMiniDrag = expandFromMiniDrag,
            onExpandFromMiniDragChanged = { expandFromMiniDrag = it },
            collapseFromSwipe = collapseFromSwipe,
            onCollapseFromSwipeChanged = { collapseFromSwipe = it },
            onPlayerExpandedChanged = { isPlayerExpanded = it },
            screenHeightPx = screenHeightPx,
            miniPreviewLiftPx = miniPreviewLiftPx,
            selectedFile = selectedFile,
            visiblePlayableFiles = visiblePlayableFiles,
            isPlaying = isPlaying,
            playbackStartInProgress = playbackStartInProgress,
            seekUiBusy = seekUiBusy,
            durationSeconds = duration,
            positionSeconds = position,
            metadataTitle = metadataTitle,
            metadataArtist = metadataArtist,
            metadataSampleRate = metadataSampleRate,
            metadataChannelCount = metadataChannelCount,
            metadataBitDepthLabel = metadataBitDepthLabel,
            decoderName = activeCoreNameForUi,
            playbackSourceLabel = playbackSourceLabel,
            pathOrUrl = currentTrackPathOrUrl,
            artworkBitmap = artworkBitmap,
            activeRepeatMode = activeRepeatMode,
            playbackCapabilitiesFlags = playbackCapabilitiesFlags,
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
            canResumeStoppedTrack = canResumeStoppedTrack,
            onHidePlayerSurface = { hidePlayerSurface() },
            onPreviousTrack = { trackNavDelegates.handlePreviousTrackAction() },
            onNextTrack = { trackNavDelegates.playAdjacentTrack(1) },
            onPlayPause = {
                if (selectedFile == null) {
                    trackNavDelegates.resumeLastStoppedTrack(autoStart = true)
                } else if (isPlaying) {
                    pauseEngineWithPauseResumeFade {
                        isPlaying = false
                        playbackSessionCoordinator.syncPlaybackService()
                    }
                } else {
                    startPlaybackFromSurface()
                }
            },
            onPlay = startPlaybackFromSurface,
            onStopAndClear = stopAndEmptyTrack,
            onOpenAudioEffects = openAudioEffectsDialog,
            onPause = {
                pauseEngineWithPauseResumeFade {
                    isPlaying = false
                    playbackSessionCoordinator.syncPlaybackService()
                }
            },
            canPreviousTrack = currentTrackIndexForList(selectedFile, visiblePlayableFiles) > 0,
            canNextTrack = currentTrackIndexForList(selectedFile, visiblePlayableFiles) in 0 until (visiblePlayableFiles.size - 1),
            onSeek = { seconds ->
                if (!seekInProgress) {
                    NativeBridge.seekTo(seconds)
                    seekRequestedAtMs = SystemClock.elapsedRealtime()
                    seekUiBusy = false
                    position = seconds
                    playbackSessionCoordinator.syncPlaybackService()
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
            canPreviousSubtune = subtuneCount > 1 && currentSubtuneIndex > 0,
            canNextSubtune = subtuneCount > 1 && currentSubtuneIndex < (subtuneCount - 1),
            canOpenSubtuneSelector = subtuneCount > 1,
            currentSubtuneIndex = currentSubtuneIndex,
            subtuneCount = subtuneCount,
            onCycleRepeatMode = { runtimeDelegates.cycleRepeatMode() }
        )
        AppNavigationPlaybackDialogsSection(
            prefs = prefs,
            volumeDatabase = volumeDatabase,
            selectedFile = selectedFile,
            lastUsedCoreName = lastUsedCoreName,
            manualOpenDelegates = manualOpenDelegates,
            playbackStateDelegates = playbackStateDelegates,
            onCancelRemoteLoadJob = { remoteLoadJob?.cancel() },
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
            showAudioEffectsDialog = showAudioEffectsDialog,
            tempMasterVolumeDb = tempMasterVolumeDb,
            tempPluginVolumeDb = tempPluginVolumeDb,
            tempSongVolumeDb = tempSongVolumeDb,
            tempForceMono = tempForceMono,
            masterVolumeDb = masterVolumeDb,
            songVolumeDb = songVolumeDb,
            forceMono = forceMono,
            onTempMasterVolumeDbChanged = { tempMasterVolumeDb = it },
            onTempPluginVolumeDbChanged = { tempPluginVolumeDb = it },
            onTempSongVolumeDbChanged = { tempSongVolumeDb = it },
            onTempForceMonoChanged = { tempForceMono = it },
            onMasterVolumeDbChanged = { masterVolumeDb = it },
            onPluginVolumeDbChanged = { pluginVolumeDb = it },
            onSongVolumeDbChanged = { songVolumeDb = it },
            onForceMonoChanged = { forceMono = it },
            onShowAudioEffectsDialogChanged = { showAudioEffectsDialog = it }
        )
    }

    val settingsRouteContent: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit = { mainPadding ->
        AppNavigationSettingsRouteSection(mainPadding = mainPadding) {
            val settingsPluginCoreActions = SettingsPluginCoreActions(
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
                },
                onPluginPriorityChanged = { pluginName, priority ->
                    NativeBridge.setDecoderPriority(pluginName, priority)
                    normalizeDecoderPriorityValues()
                    persistAllPluginConfigurations(prefs)
                },
                onPluginPriorityOrderChanged = { orderedPluginNames ->
                    applyDecoderPriorityOrder(orderedPluginNames, prefs)
                },
                onPluginExtensionsChanged = { pluginName, extensions ->
                    NativeBridge.setDecoderEnabledExtensions(pluginName, extensions)
                    savePluginConfiguration(prefs, pluginName)
                },
                onFfmpegSampleRateChanged = { ffmpegCoreSampleRateHz = it },
                onFfmpegGaplessRepeatTrackChanged = { ffmpegGaplessRepeatTrack = it },
                onOpenMptSampleRateChanged = { openMptCoreSampleRateHz = it },
                onVgmPlaySampleRateChanged = { vgmPlayCoreSampleRateHz = it },
                onGmeSampleRateChanged = { gmeCoreSampleRateHz = it },
                onSidPlayFpSampleRateChanged = { sidPlayFpCoreSampleRateHz = it },
                onLazyUsf2SampleRateChanged = { lazyUsf2CoreSampleRateHz = it },
                onLazyUsf2UseHleAudioChanged = { lazyUsf2UseHleAudio = it },
                onVio2sfInterpolationQualityChanged = { vio2sfInterpolationQuality = it },
                onSc68SamplingRateHzChanged = { sc68SamplingRateHz = it },
                onSc68AsidChanged = { sc68Asid = it },
                onSc68DefaultTimeSecondsChanged = { sc68DefaultTimeSeconds = it },
                onSc68YmEngineChanged = { sc68YmEngine = it },
                onSc68YmVolModelChanged = { sc68YmVolModel = it },
                onSc68AmigaFilterChanged = { sc68AmigaFilter = it },
                onSc68AmigaBlendChanged = { sc68AmigaBlend = it },
                onSc68AmigaClockChanged = { sc68AmigaClock = it },
                onSidPlayFpBackendChanged = { sidPlayFpBackend = it },
                onSidPlayFpClockModeChanged = { sidPlayFpClockMode = it },
                onSidPlayFpSidModelModeChanged = { sidPlayFpSidModelMode = it },
                onSidPlayFpFilter6581EnabledChanged = { sidPlayFpFilter6581Enabled = it },
                onSidPlayFpFilter8580EnabledChanged = { sidPlayFpFilter8580Enabled = it },
                onSidPlayFpDigiBoost8580Changed = { sidPlayFpDigiBoost8580 = it },
                onSidPlayFpFilterCurve6581PercentChanged = { sidPlayFpFilterCurve6581Percent = it },
                onSidPlayFpFilterRange6581PercentChanged = { sidPlayFpFilterRange6581Percent = it },
                onSidPlayFpFilterCurve8580PercentChanged = { sidPlayFpFilterCurve8580Percent = it },
                onSidPlayFpReSidFpFastSamplingChanged = { sidPlayFpReSidFpFastSampling = it },
                onSidPlayFpReSidFpCombinedWaveformsStrengthChanged = {
                    sidPlayFpReSidFpCombinedWaveformsStrength = it
                },
                onGmeTempoPercentChanged = { gmeTempoPercent = it },
                onGmeStereoSeparationPercentChanged = { gmeStereoSeparationPercent = it },
                onGmeEchoEnabledChanged = { gmeEchoEnabled = it },
                onGmeAccuracyEnabledChanged = { gmeAccuracyEnabled = it },
                onGmeEqTrebleDecibelChanged = { gmeEqTrebleDecibel = it },
                onGmeEqBassHzChanged = { gmeEqBassHz = it },
                onGmeSpcUseBuiltInFadeChanged = { gmeSpcUseBuiltInFade = it },
                onGmeSpcInterpolationChanged = { gmeSpcInterpolation = it },
                onGmeSpcUseNativeSampleRateChanged = { gmeSpcUseNativeSampleRate = it },
                onVgmPlayLoopCountChanged = { vgmPlayLoopCount = it },
                onVgmPlayAllowNonLoopingLoopChanged = { vgmPlayAllowNonLoopingLoop = it },
                onVgmPlayVsyncRateChanged = { vgmPlayVsyncRate = it },
                onVgmPlayResampleModeChanged = { vgmPlayResampleMode = it },
                onVgmPlayChipSampleModeChanged = { vgmPlayChipSampleMode = it },
                onVgmPlayChipSampleRateChanged = { vgmPlayChipSampleRate = it },
                onVgmPlayChipCoreChanged = { chipKey, selectedValue ->
                    vgmPlayChipCoreSelections = vgmPlayChipCoreSelections + (chipKey to selectedValue)
                },
                onOpenMptStereoSeparationPercentChanged = { openMptStereoSeparationPercent = it },
                onOpenMptStereoSeparationAmigaPercentChanged = { openMptStereoSeparationAmigaPercent = it },
                onOpenMptInterpolationFilterLengthChanged = { openMptInterpolationFilterLength = it },
                onOpenMptAmigaResamplerModeChanged = { openMptAmigaResamplerMode = it },
                onOpenMptAmigaResamplerApplyAllModulesChanged = { openMptAmigaResamplerApplyAllModules = it },
                onOpenMptVolumeRampingStrengthChanged = { openMptVolumeRampingStrength = it },
                onOpenMptFt2XmVolumeRampingChanged = { openMptFt2XmVolumeRamping = it },
                onOpenMptMasterGainMilliBelChanged = { openMptMasterGainMilliBel = it },
                onOpenMptSurroundEnabledChanged = { openMptSurroundEnabled = it }
            )
            SettingsScreen(
                                route = settingsRoute,
                                bottomContentPadding = miniPlayerListInset,
                                state = buildSettingsScreenStateFromStateHolders(
                                    selectedPluginName = selectedPluginName,
                                    autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                                    openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                                    autoPlayNextTrackOnEnd = autoPlayNextTrackOnEnd,
                                    previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                                    fadePauseResume = fadePauseResume,
                                    audioFocusInterrupt = audioFocusInterrupt,
                                    audioDucking = audioDucking,
                                    persistRepeatMode = persistRepeatMode,
                                    themeMode = themeMode,
                                    rememberBrowserLocation = rememberBrowserLocation,
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
                                    onOpenAudioEffects = {
                            tempMasterVolumeDb = masterVolumeDb
                            tempPluginVolumeDb = pluginVolumeDb
                            tempSongVolumeDb = songVolumeDb
                            tempForceMono = forceMono
                            showAudioEffectsDialog = true
                        },
                                    onClearAllAudioParameters = {
                            clearAllAudioParametersAction(
                                context = context,
                                prefs = prefs,
                                volumeDatabase = volumeDatabase,
                                onMasterVolumeDbChanged = { masterVolumeDb = it },
                                onPluginVolumeDbChanged = { pluginVolumeDb = it },
                                onSongVolumeDbChanged = { songVolumeDb = it },
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
                                onSongVolumeDbChanged = { songVolumeDb = it }
                            )
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
                                    onAudioBackendPreferenceChanged = { audioBackendPreference = it },
                                    onAudioPerformanceModeChanged = { audioPerformanceMode = it },
                                    onAudioBufferPresetChanged = { audioBufferPreset = it },
                                    onAudioResamplerPreferenceChanged = {
                            audioResamplerPreference = it
                            if (it == AudioResamplerPreference.Sox) {
                                pendingSoxExperimentalDialog = true
                            }
                        },
                                    onAudioAllowBackendFallbackChanged = { audioAllowBackendFallback = it },
                                    onOpenPlayerFromNotificationChanged = { openPlayerFromNotification = it },
                                    onPersistRepeatModeChanged = { persistRepeatMode = it },
                                    onThemeModeChanged = onThemeModeChanged,
                                    onRememberBrowserLocationChanged = { rememberBrowserLocation = it },
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
                                    onClearAllSettings = {
                            clearAllSettingsUsingStateHolders(
                                context = context,
                                prefs = prefs,
                                defaultScopeTextSizeSp = defaultScopeTextSizeSp,
                                selectableVisualizationModes = selectableVisualizationModes,
                                onThemeModeChanged = onThemeModeChanged,
                                settingsStates = settingsStates,
                                onAutoPlayOnTrackSelectChanged = { autoPlayOnTrackSelect = it },
                                onOpenPlayerOnTrackSelectChanged = { openPlayerOnTrackSelect = it },
                                onAutoPlayNextTrackOnEndChanged = { autoPlayNextTrackOnEnd = it },
                                onPreviousRestartsAfterThresholdChanged = { previousRestartsAfterThreshold = it },
                                onFadePauseResumeChanged = { fadePauseResume = it },
                                onPersistRepeatModeChanged = { persistRepeatMode = it },
                                onPreferredRepeatModeChanged = { preferredRepeatMode = it },
                                onRememberBrowserLocationChanged = { rememberBrowserLocation = it },
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
                                onVisualizationModeChanged = { visualizationMode = it },
                                onEnabledVisualizationModesChanged = { enabledVisualizationModes = it },
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
                                onVisualizationVuRenderBackendChanged = { visualizationVuRenderBackend = it }
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
        AppNavigationMainScaffoldSection(
            currentView = currentView,
            onOpenPlayerSurface = {
                isPlayerSurfaceVisible = true
                isPlayerExpanded = true
                collapseFromSwipe = false
                expandFromMiniDrag = false
                miniExpandPreviewProgress = 0f
            },
            onHomeRequested = { currentView = MainView.Home },
            onSettingsRequested = {
                settingsLaunchedFromPlayer = false
                openSettingsRoute(SettingsRoute.Root, true)
                currentView = MainView.Settings
            },
            homeContent = { mainPadding ->
                AppNavigationHomeRouteSection(
                    mainPadding = mainPadding,
                    currentTrackPath = currentPlaybackSourceId ?: selectedFile?.absolutePath,
                    currentTrackTitle = metadataTitle,
                    currentTrackArtist = metadataArtist,
                    recentFolders = recentFolders,
                    recentPlayedFiles = recentPlayedFiles,
                    storagePresentationForEntry = { entry ->
                        storagePresentationForEntry(context, entry, storageDescriptors)
                    },
                    bottomContentPadding = miniPlayerListInset,
                    onOpenLibrary = {
                        browserLaunchLocationId = null
                        browserLaunchDirectoryPath = null
                        currentView = MainView.Browser
                    },
                    onOpenUrlOrPath = {
                        urlOrPathInput = ""
                        showUrlOrPathDialog = true
                    },
                    onOpenRecentFolder = { entry ->
                        browserLaunchLocationId = entry.locationId
                        browserLaunchDirectoryPath = entry.path
                        currentView = MainView.Browser
                    },
                    onPlayRecentFile = { entry ->
                        playRecentFileEntryAction(
                            cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                            entry = entry,
                            openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                            onApplyTrackSelection = { file, autoStart, expandOverride, sourceIdOverride, locationIdOverride, useSongVolumeLookup ->
                                trackLoadDelegates.applyTrackSelection(
                                    file,
                                    autoStart,
                                    expandOverride,
                                    sourceIdOverride,
                                    locationIdOverride,
                                    useSongVolumeLookup
                                )
                            },
                            onApplyManualInputSelection = { rawInput ->
                                manualOpenDelegates.applyManualInputSelection(rawInput)
                            }
                        )
                    },
                    onRecentFolderAction = { entry, action ->
                        applyRecentFolderAction(
                            context = context,
                            prefs = prefs,
                            entry = entry,
                            action = action,
                            recentFolders = recentFolders,
                            recentFoldersLimit = recentFoldersLimit,
                            onRecentFoldersChanged = { recentFolders = it }
                        )
                    },
                    onRecentFileAction = { entry, action ->
                        applyRecentSourceAction(
                            context = context,
                            prefs = prefs,
                            entry = entry,
                            action = action,
                            recentPlayedFiles = recentPlayedFiles,
                            recentFilesLimit = recentFilesLimit,
                            onRecentPlayedFilesChanged = { recentPlayedFiles = it },
                            resolveShareableFileForRecent = { recent ->
                                runtimeDelegates.resolveShareableFileForRecent(recent)
                            }
                        )
                    },
                    canShareRecentFile = { entry ->
                        runtimeDelegates.resolveShareableFileForRecent(entry) != null
                    }
                )
            },
            browserContent = { mainPadding ->
                AppNavigationBrowserRouteSection(
                    mainPadding = mainPadding,
                    repository = repository,
                    initialLocationId = browserLaunchLocationId
                        ?: if (rememberBrowserLocation) lastBrowserLocationId else null,
                    initialDirectoryPath = browserLaunchDirectoryPath
                        ?: if (rememberBrowserLocation) lastBrowserDirectoryPath else null,
                    bottomContentPadding = miniPlayerListInset,
                    backHandlingEnabled = !isPlayerExpanded,
                    playingFile = selectedFile,
                    onVisiblePlayableFilesChanged = { files -> visiblePlayableFiles = files },
                    onExitBrowser = { currentView = MainView.Home },
                    onBrowserLocationChanged = { locationId, directoryPath ->
                        if (locationId != null && directoryPath != null) {
                            runtimeDelegates.addRecentFolder(directoryPath, locationId)
                        }
                        if (browserLaunchLocationId != null || browserLaunchDirectoryPath != null) {
                            browserLaunchLocationId = null
                            browserLaunchDirectoryPath = null
                        }
                        if (!rememberBrowserLocation) return@AppNavigationBrowserRouteSection
                        lastBrowserLocationId = locationId
                        lastBrowserDirectoryPath = directoryPath
                        prefs.edit()
                            .putString(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID, locationId)
                            .putString(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH, directoryPath)
                            .apply()
                    },
                    onFileSelected = { file, sourceIdOverride ->
                        trackLoadDelegates.applyTrackSelection(
                            file = file,
                            autoStart = autoPlayOnTrackSelect,
                            expandOverride = openPlayerOnTrackSelect,
                            sourceIdOverride = sourceIdOverride
                        )
                    }
                )
            },
            settingsContent = settingsRouteContent
        )
        PlayerOverlayAndDialogsSection()
    }
}
