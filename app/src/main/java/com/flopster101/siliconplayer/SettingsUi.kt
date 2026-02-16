package com.flopster101.siliconplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import com.flopster101.siliconplayer.pluginsettings.OpenMptSettings
import com.flopster101.siliconplayer.pluginsettings.RenderPluginSettings
import com.flopster101.siliconplayer.pluginsettings.GmeSettings
import com.flopster101.siliconplayer.pluginsettings.VgmPlayChipSettingsScreen
import com.flopster101.siliconplayer.pluginsettings.VgmPlaySettings
import java.util.Locale
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler

private const val SETTINGS_PAGE_NAV_DURATION_MS = 300
private val SettingsCardShape = RoundedCornerShape(16.dp)

private enum class CacheSizeUnit(
    val label: String,
    val bytesPerUnit: Double
) {
    MB("MB", 1024.0 * 1024.0),
    GB("GB", 1024.0 * 1024.0 * 1024.0)
}

private fun formatCacheByteCount(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

private fun stripCachedFileHashPrefix(fileName: String): String {
    val hashPrefix = Regex("^[0-9a-fA-F]{40}_(.+)$")
    return hashPrefix.matchEntire(fileName)?.groupValues?.getOrNull(1) ?: fileName
}

private enum class VisualizationTier(val label: String) {
    Basic("Basic visualizations"),
    Advanced("Advanced visualizations"),
    Specialized("Specialized visualizations")
}

private data class VisualizationSettingsPageItem(
    val route: SettingsRoute,
    val mode: VisualizationMode,
    val title: String,
    val description: String
)

private fun basicVisualizationSettingsPages(): List<VisualizationSettingsPageItem> = listOf(
    VisualizationSettingsPageItem(
        route = SettingsRoute.VisualizationBasicBars,
        mode = VisualizationMode.Bars,
        title = "Bars",
        description = "Spectrum bars over artwork or blank background."
    ),
    VisualizationSettingsPageItem(
        route = SettingsRoute.VisualizationBasicOscilloscope,
        mode = VisualizationMode.Oscilloscope,
        title = "Oscilloscope",
        description = "Waveform scope in mono or stereo."
    ),
    VisualizationSettingsPageItem(
        route = SettingsRoute.VisualizationBasicVuMeters,
        mode = VisualizationMode.VuMeters,
        title = "VU meters",
        description = "Channel level meters with configurable anchor."
    )
)

private fun settingsRouteOrder(route: SettingsRoute): Int = when (route) {
    SettingsRoute.Root -> 0
    SettingsRoute.AudioPlugins -> 1
    SettingsRoute.PluginDetail -> 2
    SettingsRoute.PluginVgmPlayChipSettings -> 3
    SettingsRoute.PluginFfmpeg -> 2
    SettingsRoute.PluginOpenMpt -> 2
    SettingsRoute.PluginVgmPlay -> 2
    SettingsRoute.UrlCache -> 1
    SettingsRoute.CacheManager -> 2
    SettingsRoute.GeneralAudio -> 1
    SettingsRoute.Home -> 1
    SettingsRoute.Player -> 1
    SettingsRoute.Visualization -> 1
    SettingsRoute.VisualizationBasic -> 2
    SettingsRoute.VisualizationBasicBars -> 3
    SettingsRoute.VisualizationBasicOscilloscope -> 3
    SettingsRoute.VisualizationBasicVuMeters -> 3
    SettingsRoute.Misc -> 1
    SettingsRoute.Ui -> 1
    SettingsRoute.About -> 1
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SettingsScreen(
    route: SettingsRoute,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onOpenAudioPlugins: () -> Unit,
    onOpenGeneralAudio: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenAudioEffects: () -> Unit,
    onClearAllAudioParameters: () -> Unit,
    onClearPluginAudioParameters: () -> Unit,
    onClearSongAudioParameters: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenVisualization: () -> Unit,
    onOpenVisualizationBasic: () -> Unit,
    onOpenVisualizationBasicBars: () -> Unit,
    onOpenVisualizationBasicOscilloscope: () -> Unit,
    onOpenVisualizationBasicVuMeters: () -> Unit,
    onOpenMisc: () -> Unit,
    onOpenUrlCache: () -> Unit,
    onOpenCacheManager: () -> Unit,
    onOpenUi: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenVgmPlayChipSettings: () -> Unit,
    selectedPluginName: String?,
    onPluginSelected: (String) -> Unit,
    onPluginEnabledChanged: (String, Boolean) -> Unit,
    onPluginPriorityChanged: (String, Int) -> Unit,
    onPluginExtensionsChanged: (String, Array<String>) -> Unit,
    autoPlayOnTrackSelect: Boolean,
    onAutoPlayOnTrackSelectChanged: (Boolean) -> Unit,
    openPlayerOnTrackSelect: Boolean,
    onOpenPlayerOnTrackSelectChanged: (Boolean) -> Unit,
    autoPlayNextTrackOnEnd: Boolean,
    onAutoPlayNextTrackOnEndChanged: (Boolean) -> Unit,
    previousRestartsAfterThreshold: Boolean,
    onPreviousRestartsAfterThresholdChanged: (Boolean) -> Unit,
    respondHeadphoneMediaButtons: Boolean,
    onRespondHeadphoneMediaButtonsChanged: (Boolean) -> Unit,
    pauseOnHeadphoneDisconnect: Boolean,
    onPauseOnHeadphoneDisconnectChanged: (Boolean) -> Unit,
    audioFocusInterrupt: Boolean,
    onAudioFocusInterruptChanged: (Boolean) -> Unit,
    audioDucking: Boolean,
    onAudioDuckingChanged: (Boolean) -> Unit,
    audioBackendPreference: AudioBackendPreference,
    onAudioBackendPreferenceChanged: (AudioBackendPreference) -> Unit,
    audioPerformanceMode: AudioPerformanceMode,
    onAudioPerformanceModeChanged: (AudioPerformanceMode) -> Unit,
    audioBufferPreset: AudioBufferPreset,
    onAudioBufferPresetChanged: (AudioBufferPreset) -> Unit,
    audioResamplerPreference: AudioResamplerPreference,
    onAudioResamplerPreferenceChanged: (AudioResamplerPreference) -> Unit,
    audioAllowBackendFallback: Boolean,
    onAudioAllowBackendFallbackChanged: (Boolean) -> Unit,
    openPlayerFromNotification: Boolean,
    onOpenPlayerFromNotificationChanged: (Boolean) -> Unit,
    persistRepeatMode: Boolean,
    onPersistRepeatModeChanged: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    rememberBrowserLocation: Boolean,
    onRememberBrowserLocationChanged: (Boolean) -> Unit,
    recentFoldersLimit: Int,
    onRecentFoldersLimitChanged: (Int) -> Unit,
    recentFilesLimit: Int,
    onRecentFilesLimitChanged: (Int) -> Unit,
    urlCacheClearOnLaunch: Boolean,
    onUrlCacheClearOnLaunchChanged: (Boolean) -> Unit,
    urlCacheMaxTracks: Int,
    onUrlCacheMaxTracksChanged: (Int) -> Unit,
    urlCacheMaxBytes: Long,
    onUrlCacheMaxBytesChanged: (Long) -> Unit,
    onClearUrlCacheNow: () -> Unit,
    cachedSourceFiles: List<CachedSourceFile>,
    onRefreshCachedSourceFiles: () -> Unit,
    onDeleteCachedSourceFiles: (List<String>) -> Unit,
    onExportCachedSourceFiles: (List<String>) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    filenameDisplayMode: FilenameDisplayMode,
    onFilenameDisplayModeChanged: (FilenameDisplayMode) -> Unit,
    filenameOnlyWhenTitleMissing: Boolean,
    onFilenameOnlyWhenTitleMissingChanged: (Boolean) -> Unit,
    unknownTrackDurationSeconds: Int,
    onUnknownTrackDurationSecondsChanged: (Int) -> Unit,
    endFadeApplyToAllTracks: Boolean,
    onEndFadeApplyToAllTracksChanged: (Boolean) -> Unit,
    endFadeDurationMs: Int,
    onEndFadeDurationMsChanged: (Int) -> Unit,
    endFadeCurve: EndFadeCurve,
    onEndFadeCurveChanged: (EndFadeCurve) -> Unit,
    visualizationMode: VisualizationMode,
    onVisualizationModeChanged: (VisualizationMode) -> Unit,
    enabledVisualizationModes: Set<VisualizationMode>,
    onEnabledVisualizationModesChanged: (Set<VisualizationMode>) -> Unit,
    visualizationBarCount: Int,
    onVisualizationBarCountChanged: (Int) -> Unit,
    visualizationBarSmoothingPercent: Int,
    onVisualizationBarSmoothingPercentChanged: (Int) -> Unit,
    visualizationBarRoundnessDp: Int,
    onVisualizationBarRoundnessDpChanged: (Int) -> Unit,
    visualizationBarOverlayArtwork: Boolean,
    onVisualizationBarOverlayArtworkChanged: (Boolean) -> Unit,
    visualizationBarUseThemeColor: Boolean,
    onVisualizationBarUseThemeColorChanged: (Boolean) -> Unit,
    visualizationOscStereo: Boolean,
    onVisualizationOscStereoChanged: (Boolean) -> Unit,
    visualizationVuAnchor: VisualizationVuAnchor,
    onVisualizationVuAnchorChanged: (VisualizationVuAnchor) -> Unit,
    visualizationVuUseThemeColor: Boolean,
    onVisualizationVuUseThemeColorChanged: (Boolean) -> Unit,
    visualizationVuSmoothingPercent: Int,
    onVisualizationVuSmoothingPercentChanged: (Int) -> Unit,
    onResetVisualizationBarsSettings: () -> Unit,
    onResetVisualizationOscilloscopeSettings: () -> Unit,
    onResetVisualizationVuSettings: () -> Unit,
    ffmpegSampleRateHz: Int,
    ffmpegCapabilities: Int,
    onFfmpegSampleRateChanged: (Int) -> Unit,
    openMptSampleRateHz: Int,
    openMptCapabilities: Int,
    onOpenMptSampleRateChanged: (Int) -> Unit,
    vgmPlaySampleRateHz: Int,
    vgmPlayCapabilities: Int,
    onVgmPlaySampleRateChanged: (Int) -> Unit,
    gmeSampleRateHz: Int,
    onGmeSampleRateChanged: (Int) -> Unit,
    gmeTempoPercent: Int,
    onGmeTempoPercentChanged: (Int) -> Unit,
    gmeStereoSeparationPercent: Int,
    onGmeStereoSeparationPercentChanged: (Int) -> Unit,
    gmeEchoEnabled: Boolean,
    onGmeEchoEnabledChanged: (Boolean) -> Unit,
    gmeAccuracyEnabled: Boolean,
    onGmeAccuracyEnabledChanged: (Boolean) -> Unit,
    gmeEqTrebleDecibel: Int,
    onGmeEqTrebleDecibelChanged: (Int) -> Unit,
    gmeEqBassHz: Int,
    onGmeEqBassHzChanged: (Int) -> Unit,
    gmeSpcUseBuiltInFade: Boolean,
    onGmeSpcUseBuiltInFadeChanged: (Boolean) -> Unit,
    gmeSpcInterpolation: Int,
    onGmeSpcInterpolationChanged: (Int) -> Unit,
    gmeSpcUseNativeSampleRate: Boolean,
    onGmeSpcUseNativeSampleRateChanged: (Boolean) -> Unit,
    vgmPlayLoopCount: Int,
    onVgmPlayLoopCountChanged: (Int) -> Unit,
    vgmPlayAllowNonLoopingLoop: Boolean,
    onVgmPlayAllowNonLoopingLoopChanged: (Boolean) -> Unit,
    vgmPlayVsyncRate: Int,
    onVgmPlayVsyncRateChanged: (Int) -> Unit,
    vgmPlayResampleMode: Int,
    onVgmPlayResampleModeChanged: (Int) -> Unit,
    vgmPlayChipSampleMode: Int,
    onVgmPlayChipSampleModeChanged: (Int) -> Unit,
    vgmPlayChipSampleRate: Int,
    onVgmPlayChipSampleRateChanged: (Int) -> Unit,
    vgmPlayChipCoreSelections: Map<String, Int>,
    onVgmPlayChipCoreChanged: (String, Int) -> Unit,
    openMptStereoSeparationPercent: Int,
    onOpenMptStereoSeparationPercentChanged: (Int) -> Unit,
    openMptStereoSeparationAmigaPercent: Int,
    onOpenMptStereoSeparationAmigaPercentChanged: (Int) -> Unit,
    openMptInterpolationFilterLength: Int,
    onOpenMptInterpolationFilterLengthChanged: (Int) -> Unit,
    openMptAmigaResamplerMode: Int,
    onOpenMptAmigaResamplerModeChanged: (Int) -> Unit,
    openMptAmigaResamplerApplyAllModules: Boolean,
    onOpenMptAmigaResamplerApplyAllModulesChanged: (Boolean) -> Unit,
    openMptVolumeRampingStrength: Int,
    onOpenMptVolumeRampingStrengthChanged: (Int) -> Unit,
    openMptFt2XmVolumeRamping: Boolean,
    onOpenMptFt2XmVolumeRampingChanged: (Boolean) -> Unit,
    openMptMasterGainMilliBel: Int,
    onOpenMptMasterGainMilliBelChanged: (Int) -> Unit,
    openMptSurroundEnabled: Boolean,
    onOpenMptSurroundEnabledChanged: (Boolean) -> Unit,
    onClearRecentHistory: () -> Unit,
    onClearAllSettings: () -> Unit,
    onClearAllPluginSettings: () -> Unit,
    onResetPluginSettings: (String) -> Unit
) {
    var pendingResetAction by remember { mutableStateOf<SettingsResetAction?>(null) }
    var pendingPluginResetName by remember { mutableStateOf<String?>(null) }
    var pluginPriorityEditMode by remember { mutableStateOf(false) }
    LaunchedEffect(route) {
        if (route != SettingsRoute.AudioPlugins) {
            pluginPriorityEditMode = false
        }
    }
    val secondaryTitle = when (route) {
        SettingsRoute.Root -> null
        SettingsRoute.AudioPlugins -> "Audio plugins"
        SettingsRoute.PluginDetail -> selectedPluginName?.let { "$it plugin settings" } ?: "Plugin settings"
        SettingsRoute.PluginVgmPlayChipSettings -> "VGMPlay chip settings"
        SettingsRoute.PluginFfmpeg -> "FFmpeg plugin settings"
        SettingsRoute.PluginOpenMpt -> "OpenMPT plugin settings"
        SettingsRoute.PluginVgmPlay -> "VGMPlay plugin settings"
        SettingsRoute.UrlCache -> "Cache settings"
        SettingsRoute.CacheManager -> "Manage cached files"
        SettingsRoute.GeneralAudio -> "General audio"
        SettingsRoute.Home -> "Home settings"
        SettingsRoute.Player -> "Player settings"
        SettingsRoute.Visualization -> "Visualization settings"
        SettingsRoute.VisualizationBasic -> "Basic visualizations"
        SettingsRoute.VisualizationBasicBars -> "Bars settings"
        SettingsRoute.VisualizationBasicOscilloscope -> "Oscilloscope settings"
        SettingsRoute.VisualizationBasicVuMeters -> "VU meters settings"
        SettingsRoute.Misc -> "Misc settings"
        SettingsRoute.Ui -> "UI settings"
        SettingsRoute.About -> "About"
    }

    androidx.compose.material3.Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Settings") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(
                visible = secondaryTitle != null,
                enter = fadeIn(animationSpec = tween(160)) + expandVertically(
                    animationSpec = tween(180, easing = LinearOutSlowInEasing),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(
                    animationSpec = tween(150, easing = FastOutLinearInEasing),
                    shrinkTowards = Alignment.Top
                )
            ) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = secondaryTitle.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (route == SettingsRoute.AudioPlugins) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (pluginPriorityEditMode) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                ) {
                                    IconButton(
                                        onClick = { pluginPriorityEditMode = !pluginPriorityEditMode },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapVert,
                                            contentDescription = if (pluginPriorityEditMode) "Finish reorder mode" else "Edit plugin order",
                                            tint = if (pluginPriorityEditMode) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else if (route == SettingsRoute.PluginDetail && selectedPluginName != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent
                                ) {
                                    IconButton(
                                        onClick = { pendingPluginResetName = selectedPluginName },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = "Reset plugin settings",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else if (
                                route == SettingsRoute.VisualizationBasicBars ||
                                route == SettingsRoute.VisualizationBasicOscilloscope ||
                                route == SettingsRoute.VisualizationBasicVuMeters
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent
                                ) {
                                    IconButton(
                                        onClick = {
                                            when (route) {
                                                SettingsRoute.VisualizationBasicBars ->
                                                    onResetVisualizationBarsSettings()
                                                SettingsRoute.VisualizationBasicOscilloscope ->
                                                    onResetVisualizationOscilloscopeSettings()
                                                SettingsRoute.VisualizationBasicVuMeters ->
                                                    onResetVisualizationVuSettings()
                                                else -> Unit
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = "Reset visualization settings",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }

            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val forward = settingsRouteOrder(targetState) >= settingsRouteOrder(initialState)
                    val enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth / 4 },
                        animationSpec = tween(
                            durationMillis = SETTINGS_PAGE_NAV_DURATION_MS,
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
                            durationMillis = SETTINGS_PAGE_NAV_DURATION_MS,
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
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            state = rememberScrollState(),
                            enabled = !(route == SettingsRoute.AudioPlugins && pluginPriorityEditMode)
                        )
                ) {
                    when (it) {
                    SettingsRoute.Root -> {
                        SettingsSectionLabel("General")
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
                            title = "Home settings",
                            description = "Configure recents shown on the Home page.",
                            icon = Icons.Default.Home,
                            onClick = onOpenHome
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Visualization settings",
                            description = "Configure player visualizers and rendering style.",
                            icon = Icons.Default.GraphicEq,
                            onClick = onOpenVisualization
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Other")
                        SettingsItemCard(
                            title = "Cache settings",
                            description = "Cached file behavior, limits, and cleanup.",
                            icon = Icons.Default.Link,
                            onClick = onOpenUrlCache
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
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Danger zone")
                        SettingsItemCard(
                            title = "Clear all app settings",
                            description = "Reset app settings to defaults. Plugin/core settings are kept.",
                            icon = Icons.Default.MoreHoriz,
                            onClick = { pendingResetAction = SettingsResetAction.ClearAllSettings }
                        )
                    }
                    SettingsRoute.AudioPlugins -> {
                        val context = LocalContext.current
                        SettingsSectionLabel("Registered plugins")

                        val registeredPluginNames = remember { NativeBridge.getRegisteredDecoderNames().toList() }
                        val defaultPluginOrder = remember(registeredPluginNames) {
                            registeredPluginNames.sortedBy { pluginName ->
                                NativeBridge.getDecoderDefaultPriority(pluginName)
                            }
                        }
                        var orderedPluginNames by remember {
                            mutableStateOf(
                                registeredPluginNames.sortedBy { pluginName ->
                                    NativeBridge.getDecoderPriority(pluginName)
                                }
                            )
                        }
                        var draggingPluginName by remember { mutableStateOf<String?>(null) }
                        var dragVisualOffsetPx by remember { mutableFloatStateOf(0f) }
                        var dragSwapRemainderPx by remember { mutableFloatStateOf(0f) }
                        var dragOriginTopPx by remember { mutableFloatStateOf(0f) }
                        var dragStartIndex by remember { mutableIntStateOf(-1) }
                        var rowHeightPx by remember { mutableFloatStateOf(0f) }
                        var orderDirty by remember { mutableStateOf(false) }
                        val rowNudgeOffsetPx = remember { mutableStateMapOf<String, Float>() }
                        val rowNudgeNonce = remember { mutableStateMapOf<String, Int>() }
                        val rowTopPx = remember { mutableStateMapOf<String, Float>() }
                        val spacerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
                        val fallbackStepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 84.dp.toPx() }
                        val edgeOverscrollPx = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
                        val itemStepPx = if (rowHeightPx > 0f) rowHeightPx + spacerPx else fallbackStepPx
                        val swapThresholdPx = itemStepPx * 0.62f

                        fun persistPriorityOrder(order: List<String>) {
                            order.forEachIndexed { index, pluginName ->
                                val newPriority = index
                                onPluginPriorityChanged(pluginName, newPriority)
                            }
                        }

                        fun movePlugin(pluginName: String, direction: Int): Boolean {
                            val currentIndex = orderedPluginNames.indexOf(pluginName)
                            if (currentIndex < 0) return false
                            val targetIndex = (currentIndex + direction).coerceIn(0, orderedPluginNames.lastIndex)
                            if (targetIndex == currentIndex) return false
                            val mutable = orderedPluginNames.toMutableList()
                            val displacedPlugin = mutable[targetIndex]
                            val item = mutable.removeAt(currentIndex)
                            mutable.add(targetIndex, item)
                            // Displaced row starts from its old slot and eases into the new slot.
                            // Use a partial step so post-drop settling feels tighter and less bouncy.
                            val displacedStartOffset = if (direction > 0) itemStepPx * 0.42f else -itemStepPx * 0.42f
                            Snapshot.withMutableSnapshot {
                                rowNudgeOffsetPx[displacedPlugin] = displacedStartOffset
                                rowNudgeNonce[displacedPlugin] = (rowNudgeNonce[displacedPlugin] ?: 0) + 1
                                orderedPluginNames = mutable
                                orderDirty = true
                            }
                            return true
                        }

                        fun finishDragging(pluginName: String) {
                            if (draggingPluginName != pluginName) return
                            if (orderDirty) {
                                persistPriorityOrder(orderedPluginNames)
                            }
                            draggingPluginName = null
                            dragOriginTopPx = 0f
                            dragVisualOffsetPx = 0f
                            dragSwapRemainderPx = 0f
                            dragStartIndex = -1
                            orderDirty = false
                        }

                        if (pluginPriorityEditMode) {
                            Text(
                                text = "Drag plugins to set priority order. Top item has highest priority.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        LaunchedEffect(pluginPriorityEditMode) {
                            if (!pluginPriorityEditMode) {
                                draggingPluginName?.let { finishDragging(it) }
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                orderedPluginNames.forEachIndexed { index, pluginName ->
                                    key(pluginName) {
                                        val isEnabled = remember(pluginName) { NativeBridge.isDecoderEnabled(pluginName) }
                                        val priority = index
                                        val isDraggedRow = draggingPluginName == pluginName

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onGloballyPositioned { coords ->
                                                    rowTopPx[pluginName] = coords.positionInParent().y
                                                }
                                        ) {
                                            PluginListItemCard(
                                                pluginName = pluginName,
                                                priority = priority,
                                                enabled = isEnabled,
                                                onEnabledChanged = { enabled ->
                                                    onPluginEnabledChanged(pluginName, enabled)
                                                },
                                                onClick = {
                                                    if (!pluginPriorityEditMode) onPluginSelected(pluginName)
                                                },
                                                editMode = pluginPriorityEditMode,
                                                isDragging = false,
                                                dragOffsetPx = 0f,
                                                nudgeOffsetPx = if (isDraggedRow) 0f else (rowNudgeOffsetPx[pluginName] ?: 0f),
                                                nudgeNonce = if (isDraggedRow) 0 else (rowNudgeNonce[pluginName] ?: 0),
                                                contentAlpha = if (isDraggedRow) 0f else 1f,
                                                enableInteractions = (draggingPluginName == null || draggingPluginName == pluginName),
                                                switchEnabled = !isDraggedRow,
                                                onMeasuredHeight = { h ->
                                                    if (h > 0f) rowHeightPx = h
                                                },
                                                onDragStart = {
                                                    if (draggingPluginName == null || draggingPluginName == pluginName) {
                                                        draggingPluginName = pluginName
                                                        dragVisualOffsetPx = 0f
                                                        dragSwapRemainderPx = 0f
                                                        dragStartIndex = orderedPluginNames.indexOf(pluginName)
                                                        dragOriginTopPx = rowTopPx[pluginName] ?: 0f
                                                        orderDirty = false
                                                    }
                                                },
                                                onDragDelta = { deltaY ->
                                                    if (draggingPluginName != pluginName) return@PluginListItemCard
                                                    dragVisualOffsetPx += deltaY
                                                    dragSwapRemainderPx += deltaY
                                                    while (dragSwapRemainderPx >= swapThresholdPx) {
                                                        if (!movePlugin(pluginName, 1)) {
                                                            dragSwapRemainderPx = edgeOverscrollPx
                                                            break
                                                        }
                                                        dragSwapRemainderPx -= itemStepPx
                                                    }
                                                    while (dragSwapRemainderPx <= -swapThresholdPx) {
                                                        if (!movePlugin(pluginName, -1)) {
                                                            dragSwapRemainderPx = -edgeOverscrollPx
                                                            break
                                                        }
                                                        dragSwapRemainderPx += itemStepPx
                                                    }
                                                    val totalSteps = orderedPluginNames.lastIndex.coerceAtLeast(0)
                                                    val startIndex = dragStartIndex.coerceIn(0, totalSteps)
                                                    val minVisualOffset = (-startIndex * itemStepPx) - edgeOverscrollPx
                                                    val maxVisualOffset = ((totalSteps - startIndex) * itemStepPx) + edgeOverscrollPx
                                                    dragVisualOffsetPx = dragVisualOffsetPx.coerceIn(minVisualOffset, maxVisualOffset)
                                                },
                                                onDragEnd = {
                                                    finishDragging(pluginName)
                                                }
                                            )
                                        }

                                        if (index < orderedPluginNames.size - 1) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }
                                    }
                                }
                            }

                            val draggedName = draggingPluginName
                            if (draggedName != null) {
                                val draggedIndex = orderedPluginNames.indexOf(draggedName)
                                val draggedPriority = if (draggedIndex >= 0) {
                                    draggedIndex
                                } else {
                                    NativeBridge.getDecoderPriority(draggedName)
                                }
                                val draggedEnabled = NativeBridge.isDecoderEnabled(draggedName)
                                PluginListItemCard(
                                    pluginName = draggedName,
                                    priority = draggedPriority,
                                    enabled = draggedEnabled,
                                    onEnabledChanged = { },
                                    onClick = { },
                                    editMode = pluginPriorityEditMode,
                                    isDragging = true,
                                    dragOffsetPx = dragOriginTopPx + dragVisualOffsetPx,
                                    nudgeOffsetPx = 0f,
                                    nudgeNonce = 0,
                                    contentAlpha = 1f,
                                    enableInteractions = false,
                                    switchEnabled = false,
                                    onMeasuredHeight = { },
                                    onDragStart = { },
                                    onDragDelta = { },
                                    onDragEnd = { }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Danger zone")
                        SettingsItemCard(
                            title = "Clear all plugin settings",
                            description = "Reset all plugin/core settings to defaults without changing app settings.",
                            icon = Icons.Default.MoreHoriz,
                            onClick = { pendingResetAction = SettingsResetAction.ClearPluginSettings }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Reset plugin priority order",
                            description = "Restore plugin priority order to each plugin's built-in default priority.",
                            icon = Icons.Default.Tune,
                            onClick = {
                                orderedPluginNames = defaultPluginOrder
                                defaultPluginOrder.forEach { pluginName ->
                                    onPluginPriorityChanged(
                                        pluginName,
                                        NativeBridge.getDecoderDefaultPriority(pluginName)
                                    )
                                }
                                draggingPluginName = null
                                dragVisualOffsetPx = 0f
                                dragSwapRemainderPx = 0f
                                dragStartIndex = -1
                                orderDirty = false
                                Toast.makeText(context, "Plugin priority order reset", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    SettingsRoute.PluginDetail -> {
                        if (selectedPluginName != null) {
                            PluginDetailScreen(
                                pluginName = selectedPluginName,
                                onPriorityChanged = { priority ->
                                    onPluginPriorityChanged(selectedPluginName, priority)
                                },
                                onExtensionsChanged = { extensions ->
                                    onPluginExtensionsChanged(selectedPluginName, extensions)
                                }
                            )

                            val selectedCoreCapabilities = remember(selectedPluginName) {
                                NativeBridge.getCoreCapabilities(selectedPluginName)
                            }
                            val fixedSampleRateHz = remember(selectedPluginName) {
                                NativeBridge.getCoreFixedSampleRateHz(selectedPluginName)
                            }
                            val supportsConfigurableRate = supportsCustomSampleRate(selectedCoreCapabilities)
                            val supportsLiveRateChange = supportsLiveSampleRateChange(selectedCoreCapabilities)
                            val hasFixedRate = hasFixedSampleRate(selectedCoreCapabilities) && fixedSampleRateHz > 0
                            val selectedRateHz = when (selectedPluginName) {
                                "FFmpeg" -> ffmpegSampleRateHz
                                "LibOpenMPT" -> openMptSampleRateHz
                                "VGMPlay" -> vgmPlaySampleRateHz
                                "Game Music Emu" -> gmeSampleRateHz
                                else -> fixedSampleRateHz
                            }
                            val onSampleRateSelected: ((Int) -> Unit)? = when (selectedPluginName) {
                                "FFmpeg" -> onFfmpegSampleRateChanged
                                "LibOpenMPT" -> onOpenMptSampleRateChanged
                                "VGMPlay" -> onVgmPlaySampleRateChanged
                                "Game Music Emu" -> onGmeSampleRateChanged
                                else -> null
                            }
                            val fixedRateLabel = if (fixedSampleRateHz > 0) {
                                if (fixedSampleRateHz % 1000 == 0) {
                                    "${fixedSampleRateHz / 1000} kHz"
                                } else {
                                    String.format(Locale.US, "%.1f kHz", fixedSampleRateHz / 1000.0)
                                }
                            } else {
                                "unknown"
                            }
                            val sampleRateDescription = when {
                                hasFixedRate ->
                                    "Internal render rate for this core: $fixedRateLabel."
                                else ->
                                    "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate."
                            }
                            val sampleRateStatus = when {
                                hasFixedRate -> "Fixed rate"
                                supportsConfigurableRate && supportsLiveRateChange -> "Applies immediately"
                                supportsConfigurableRate -> "Playback restart required"
                                else -> "Not configurable"
                            }

                            val pluginSettings: com.flopster101.siliconplayer.pluginsettings.PluginSettings? = when (selectedPluginName) {
                                "LibOpenMPT" -> OpenMptSettings(
                                    sampleRateHz = openMptSampleRateHz,
                                    capabilities = openMptCapabilities,
                                    stereoSeparationPercent = openMptStereoSeparationPercent,
                                    stereoSeparationAmigaPercent = openMptStereoSeparationAmigaPercent,
                                    interpolationFilterLength = openMptInterpolationFilterLength,
                                    amigaResamplerMode = openMptAmigaResamplerMode,
                                    amigaResamplerApplyAllModules = openMptAmigaResamplerApplyAllModules,
                                    volumeRampingStrength = openMptVolumeRampingStrength,
                                    ft2XmVolumeRamping = openMptFt2XmVolumeRamping,
                                    masterGainMilliBel = openMptMasterGainMilliBel,
                                    surroundEnabled = openMptSurroundEnabled,
                                    onSampleRateChanged = onOpenMptSampleRateChanged,
                                    onStereoSeparationPercentChanged = onOpenMptStereoSeparationPercentChanged,
                                    onStereoSeparationAmigaPercentChanged = onOpenMptStereoSeparationAmigaPercentChanged,
                                    onInterpolationFilterLengthChanged = onOpenMptInterpolationFilterLengthChanged,
                                    onAmigaResamplerModeChanged = onOpenMptAmigaResamplerModeChanged,
                                    onAmigaResamplerApplyAllModulesChanged = onOpenMptAmigaResamplerApplyAllModulesChanged,
                                    onVolumeRampingStrengthChanged = onOpenMptVolumeRampingStrengthChanged,
                                    onFt2XmVolumeRampingChanged = onOpenMptFt2XmVolumeRampingChanged,
                                    onMasterGainMilliBelChanged = onOpenMptMasterGainMilliBelChanged,
                                    onSurroundEnabledChanged = onOpenMptSurroundEnabledChanged,
                                    includeSampleRateControl = false
                                )
                                "VGMPlay" -> VgmPlaySettings(
                                    sampleRateHz = vgmPlaySampleRateHz,
                                    capabilities = vgmPlayCapabilities,
                                    loopCount = vgmPlayLoopCount,
                                    allowNonLoopingLoop = vgmPlayAllowNonLoopingLoop,
                                    vsyncRate = vgmPlayVsyncRate,
                                    resampleMode = vgmPlayResampleMode,
                                    chipSampleMode = vgmPlayChipSampleMode,
                                    chipSampleRate = vgmPlayChipSampleRate,
                                    onSampleRateChanged = onVgmPlaySampleRateChanged,
                                    onLoopCountChanged = onVgmPlayLoopCountChanged,
                                    onAllowNonLoopingLoopChanged = onVgmPlayAllowNonLoopingLoopChanged,
                                    onVsyncRateChanged = onVgmPlayVsyncRateChanged,
                                    onResampleModeChanged = onVgmPlayResampleModeChanged,
                                    onChipSampleModeChanged = onVgmPlayChipSampleModeChanged,
                                    onChipSampleRateChanged = onVgmPlayChipSampleRateChanged,
                                    onOpenChipSettings = onOpenVgmPlayChipSettings,
                                    includeSampleRateControl = false
                                )
                                "Game Music Emu" -> GmeSettings(
                                    tempoPercent = gmeTempoPercent,
                                    stereoSeparationPercent = gmeStereoSeparationPercent,
                                    echoEnabled = gmeEchoEnabled,
                                    accuracyEnabled = gmeAccuracyEnabled,
                                    eqTrebleDecibel = gmeEqTrebleDecibel,
                                    eqBassHz = gmeEqBassHz,
                                    spcUseBuiltInFade = gmeSpcUseBuiltInFade,
                                    spcInterpolation = gmeSpcInterpolation,
                                    spcUseNativeSampleRate = gmeSpcUseNativeSampleRate,
                                    onTempoPercentChanged = onGmeTempoPercentChanged,
                                    onStereoSeparationPercentChanged = onGmeStereoSeparationPercentChanged,
                                    onEchoEnabledChanged = onGmeEchoEnabledChanged,
                                    onAccuracyEnabledChanged = onGmeAccuracyEnabledChanged,
                                    onEqTrebleDecibelChanged = onGmeEqTrebleDecibelChanged,
                                    onEqBassHzChanged = onGmeEqBassHzChanged,
                                    onSpcUseBuiltInFadeChanged = onGmeSpcUseBuiltInFadeChanged,
                                    onSpcInterpolationChanged = onGmeSpcInterpolationChanged,
                                    onSpcUseNativeSampleRateChanged = onGmeSpcUseNativeSampleRateChanged
                                )
                                else -> null
                            }

                            if (pluginSettings != null) {
                                RenderPluginSettings(
                                    pluginSettings = pluginSettings,
                                    settingsSectionLabel = { label -> SettingsSectionLabel(label) }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            SettingsSectionLabel("Generic output options")
                            SampleRateSelectorCard(
                                title = "Render sample rate",
                                description = sampleRateDescription,
                                selectedHz = if (hasFixedRate) fixedSampleRateHz else selectedRateHz,
                                statusText = sampleRateStatus,
                                enabled = supportsConfigurableRate && onSampleRateSelected != null,
                                onSelected = { hz -> onSampleRateSelected?.invoke(hz) }
                            )
                        }
                    }
                    SettingsRoute.PluginVgmPlayChipSettings -> {
                        SettingsSectionLabel("Chip settings")
                        VgmPlayChipSettingsScreen(
                            chipCoreSpecs = VgmPlayConfig.chipCoreSpecs,
                            chipCoreSelections = vgmPlayChipCoreSelections,
                            onChipCoreChanged = onVgmPlayChipCoreChanged
                        )
                    }
                    SettingsRoute.PluginFfmpeg -> SampleRateSelectorCard(
                        title = "Render sample rate",
                        description = "Preferred internal render sample rate for this plugin. Audio is resampled to the active output stream rate.",
                        selectedHz = ffmpegSampleRateHz,
                        enabled = supportsCustomSampleRate(ffmpegCapabilities),
                        onSelected = onFfmpegSampleRateChanged
                    )
                    SettingsRoute.PluginVgmPlay -> SampleRateSelectorCard(
                        title = "Render sample rate",
                        description = "Preferred internal render sample rate for this plugin. Audio is resampled to the active output stream rate.",
                        selectedHz = vgmPlaySampleRateHz,
                        enabled = supportsCustomSampleRate(vgmPlayCapabilities),
                        onSelected = onVgmPlaySampleRateChanged
                    )
                    SettingsRoute.PluginOpenMpt -> {
                        SettingsSectionLabel("Core options")
                        OpenMptDialogSliderCard(
                            title = "Stereo separation",
                            description = "Sets mixer stereo separation.",
                            value = openMptStereoSeparationPercent,
                            valueRange = 0..200,
                            step = 5,
                            valueLabel = { "$it%" },
                            onValueChanged = onOpenMptStereoSeparationPercentChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenMptDialogSliderCard(
                            title = "Amiga stereo separation",
                            description = "Stereo separation used specifically for Amiga modules.",
                            value = openMptStereoSeparationAmigaPercent,
                            valueRange = 0..200,
                            step = 5,
                            valueLabel = { "$it%" },
                            onValueChanged = onOpenMptStereoSeparationAmigaPercentChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenMptChoiceSelectorCard(
                            title = "Interpolation filter",
                            description = "Selects interpolation quality for module playback.",
                            selectedValue = openMptInterpolationFilterLength,
                            options = listOf(
                                IntChoice(0, "Auto"),
                                IntChoice(1, "None"),
                                IntChoice(2, "Linear"),
                                IntChoice(4, "Cubic"),
                                IntChoice(8, "Sinc (8-tap)")
                            ),
                            onSelected = onOpenMptInterpolationFilterLengthChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenMptChoiceSelectorCard(
                            title = "Amiga resampler",
                            description = "Choose Amiga resampler mode. None uses interpolation filter.",
                            selectedValue = openMptAmigaResamplerMode,
                            options = listOf(
                                IntChoice(0, "None"),
                                IntChoice(1, "Unfiltered"),
                                IntChoice(2, "Amiga 500"),
                                IntChoice(3, "Amiga 1200")
                            ),
                            onSelected = onOpenMptAmigaResamplerModeChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Apply Amiga resampler to all modules",
                            description = "When disabled, Amiga resampler is used only on Amiga module formats.",
                            checked = openMptAmigaResamplerApplyAllModules,
                            onCheckedChange = onOpenMptAmigaResamplerApplyAllModulesChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenMptVolumeRampingCard(
                            title = "Volume ramping strength",
                            description = "Controls smoothing strength for volume changes.",
                            value = openMptVolumeRampingStrength,
                            onValueChanged = onOpenMptVolumeRampingStrengthChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "FT2 5ms XM ramping",
                            description = "Apply classic FT2-style 5ms ramping for XM modules only.",
                            checked = openMptFt2XmVolumeRamping,
                            onCheckedChange = onOpenMptFt2XmVolumeRampingChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenMptDialogSliderCard(
                            title = "Master gain",
                            description = "Applies decoder gain before output.",
                            value = openMptMasterGainMilliBel,
                            valueRange = -1200..1200,
                            step = 100,
                            valueLabel = { formatMilliBelAsDbLabel(it) },
                            onValueChanged = onOpenMptMasterGainMilliBelChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Enable surround sound",
                            description = "Enable surround rendering mode when supported by the playback path.",
                            checked = openMptSurroundEnabled,
                            onCheckedChange = onOpenMptSurroundEnabledChanged
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Generic output options")
                        SampleRateSelectorCard(
                            title = "Render sample rate",
                            description = "Preferred internal render sample rate for this plugin. Audio is resampled to the active output stream rate.",
                            selectedHz = openMptSampleRateHz,
                            enabled = supportsCustomSampleRate(openMptCapabilities),
                            onSelected = onOpenMptSampleRateChanged
                        )
                    }
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
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Allow interruption by other apps",
                            description = "Pause playback when another app starts playing audio.",
                            checked = audioFocusInterrupt,
                            onCheckedChange = onAudioFocusInterruptChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Duck audio instead of pausing",
                            description = "Lower volume temporarily for brief interruptions (e.g., notifications) instead of pausing.",
                            checked = audioDucking,
                            onCheckedChange = onAudioDuckingChanged
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Audio processing")
                        SettingsItemCard(
                            title = "Audio effects",
                            description = "Volume controls and audio processing.",
                            icon = Icons.Default.Tune,
                            onClick = onOpenAudioEffects
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ClearAudioParametersCard(
                            onClearAll = onClearAllAudioParameters,
                            onClearPlugins = onClearPluginAudioParameters,
                            onClearSongs = onClearSongAudioParameters
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Audio output pipeline")
                        AudioBackendSelectorCard(
                            selectedPreference = audioBackendPreference,
                            onSelectedPreferenceChanged = onAudioBackendPreferenceChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AudioPerformanceModeSelectorCard(
                            selectedMode = audioPerformanceMode,
                            onSelectedModeChanged = onAudioPerformanceModeChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AudioBufferPresetSelectorCard(
                            selectedPreset = audioBufferPreset,
                            onSelectedPresetChanged = onAudioBufferPresetChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AudioResamplerSelectorCard(
                            selectedPreference = audioResamplerPreference,
                            onSelectedPreferenceChanged = onAudioResamplerPreferenceChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Allow backend fallback",
                            description = "If selected backend is unavailable, fall back automatically to a working output backend.",
                            checked = audioAllowBackendFallback,
                            onCheckedChange = onAudioAllowBackendFallbackChanged
                        )
                    }
                    SettingsRoute.Home -> {
                        var showFolderLimitDialog by remember { mutableStateOf(false) }
                        var showFileLimitDialog by remember { mutableStateOf(false) }

                        SettingsSectionLabel("Recents")
                        SettingsItemCard(
                            title = "Recent folders limit",
                            description = "$recentFoldersLimit folders",
                            icon = Icons.Default.Folder,
                            onClick = { showFolderLimitDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Recent files limit",
                            description = "$recentFilesLimit files",
                            icon = Icons.Default.MusicNote,
                            onClick = { showFileLimitDialog = true }
                        )

                        if (showFolderLimitDialog) {
                            var input by remember { mutableStateOf(recentFoldersLimit.toString()) }
                            AlertDialog(
                                onDismissRequest = { showFolderLimitDialog = false },
                                title = { Text("Recent folders limit") },
                                text = {
                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = { value ->
                                            input = value.filter { it.isDigit() }.take(2)
                                        },
                                        singleLine = true,
                                        label = { Text("Folders") },
                                        supportingText = { Text("1-50 (default 3)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                },
                                dismissButton = {
                                    TextButton(onClick = { showFolderLimitDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val parsed = input.trim().toIntOrNull()
                                        if (parsed != null && parsed in 1..50) {
                                            onRecentFoldersLimitChanged(parsed)
                                            showFolderLimitDialog = false
                                        }
                                    }) {
                                        Text("Save")
                                    }
                                }
                            )
                        }

                        if (showFileLimitDialog) {
                            var input by remember { mutableStateOf(recentFilesLimit.toString()) }
                            AlertDialog(
                                onDismissRequest = { showFileLimitDialog = false },
                                title = { Text("Recent files limit") },
                                text = {
                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = { value ->
                                            input = value.filter { it.isDigit() }.take(2)
                                        },
                                        singleLine = true,
                                        label = { Text("Files") },
                                        supportingText = { Text("1-50 (default 5)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                },
                                dismissButton = {
                                    TextButton(onClick = { showFileLimitDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val parsed = input.trim().toIntOrNull()
                                        if (parsed != null && parsed in 1..50) {
                                            onRecentFilesLimitChanged(parsed)
                                            showFileLimitDialog = false
                                        }
                                    }) {
                                        Text("Save")
                                    }
                                }
                            )
                        }
                    }
                    SettingsRoute.Player -> {
                        var showUnknownDurationDialog by remember { mutableStateOf(false) }
                        var showEndFadeDurationDialog by remember { mutableStateOf(false) }
                        var showEndFadeCurveDialog by remember { mutableStateOf(false) }
                        SettingsSectionLabel("Track duration fallback")
                        SettingsItemCard(
                            title = "Unknown track duration",
                            description = "${unknownTrackDurationSeconds}s (default 180s)",
                            icon = Icons.Default.MoreHoriz,
                            onClick = { showUnknownDurationDialog = true }
                        )
                        if (showUnknownDurationDialog) {
                            var input by remember { mutableStateOf(unknownTrackDurationSeconds.toString()) }
                            AlertDialog(
                                onDismissRequest = { showUnknownDurationDialog = false },
                                title = { Text("Unknown track duration") },
                                text = {
                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = { value ->
                                            input = value.filter { it.isDigit() }.take(5)
                                        },
                                        singleLine = true,
                                        label = { Text("Seconds") },
                                        supportingText = { Text("Used when a track has no real tagged duration.") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = MaterialTheme.shapes.extraLarge
                                    )
                                },
                                dismissButton = {
                                    TextButton(onClick = { showUnknownDurationDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val parsed = input.trim().toIntOrNull()
                                        if (parsed != null && parsed in 1..86400) {
                                            onUnknownTrackDurationSecondsChanged(parsed)
                                            showUnknownDurationDialog = false
                                        }
                                    }) {
                                        Text("Save")
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("End fadeout")
                        PlayerSettingToggleCard(
                            title = "Apply to tracks with known duration",
                            description = "When disabled, end fadeout only applies to unknown/unreliable-duration tracks.",
                            checked = endFadeApplyToAllTracks,
                            onCheckedChange = onEndFadeApplyToAllTracksChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Fade duration",
                            description = String.format(Locale.US, "%.1f seconds", endFadeDurationMs / 1000.0),
                            icon = Icons.Default.MoreHoriz,
                            onClick = { showEndFadeDurationDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Fade curve",
                            description = endFadeCurve.label,
                            icon = Icons.Default.MoreHoriz,
                            onClick = { showEndFadeCurveDialog = true }
                        )
                        if (showEndFadeDurationDialog) {
                            var input by remember {
                                mutableStateOf(
                                    if (endFadeDurationMs % 1000 == 0) {
                                        (endFadeDurationMs / 1000).toString()
                                    } else {
                                        String.format(Locale.US, "%.1f", endFadeDurationMs / 1000.0)
                                    }
                                )
                            }
                            AlertDialog(
                                onDismissRequest = { showEndFadeDurationDialog = false },
                                title = { Text("Fade duration") },
                                text = {
                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = { value ->
                                            input = value.filter { it.isDigit() || it == '.' }.take(6)
                                        },
                                        singleLine = true,
                                        label = { Text("Seconds") },
                                        supportingText = { Text("0.1s to 120s (default 10.0s)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = MaterialTheme.shapes.extraLarge
                                    )
                                },
                                dismissButton = {
                                    TextButton(onClick = { showEndFadeDurationDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val parsedSeconds = input.trim().toDoubleOrNull()
                                        if (parsedSeconds != null && parsedSeconds in 0.1..120.0) {
                                            onEndFadeDurationMsChanged((parsedSeconds * 1000.0).roundToInt())
                                            showEndFadeDurationDialog = false
                                        }
                                    }) {
                                        Text("Save")
                                    }
                                }
                            )
                        }
                        if (showEndFadeCurveDialog) {
                            AlertDialog(
                                onDismissRequest = { showEndFadeCurveDialog = false },
                                title = { Text("Fade curve") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        EndFadeCurve.entries.forEach { curve ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(MaterialTheme.shapes.medium)
                                                    .clickable {
                                                        onEndFadeCurveChanged(curve)
                                                        showEndFadeCurveDialog = false
                                                    }
                                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = curve == endFadeCurve,
                                                    onClick = {
                                                        onEndFadeCurveChanged(curve)
                                                        showEndFadeCurveDialog = false
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(curve.label)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showEndFadeCurveDialog = false }) {
                                        Text("Close")
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
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
                            title = "Previous track button behavior",
                            description = "If more than 3 seconds have elapsed, the Previous track button restarts the current track instead of moving to the previous track.",
                            checked = previousRestartsAfterThreshold,
                            onCheckedChange = onPreviousRestartsAfterThresholdChanged
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
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Keep screen on",
                            description = "Prevent screen from turning off when the player is expanded.",
                            checked = keepScreenOn,
                            onCheckedChange = onKeepScreenOnChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        var showFilenameDisplayDialog by remember { mutableStateOf(false) }
                        SettingsItemCard(
                            title = "Show filename",
                            description = when (filenameDisplayMode) {
                                FilenameDisplayMode.Always -> "Always show filename"
                                FilenameDisplayMode.Never -> "Never show filename"
                                FilenameDisplayMode.TrackerOnly -> "Show for tracker/chiptune formats only"
                            },
                            icon = Icons.Default.MoreHoriz,
                            onClick = { showFilenameDisplayDialog = true }
                        )
                        if (showFilenameDisplayDialog) {
                            FilenameDisplayModeDialog(
                                currentMode = filenameDisplayMode,
                                onModeSelected = { mode ->
                                    onFilenameDisplayModeChanged(mode)
                                    showFilenameDisplayDialog = false
                                },
                                onDismiss = { showFilenameDisplayDialog = false }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Show filename only when title missing",
                            description = "Only display filename when track has no title metadata.",
                            checked = filenameOnlyWhenTitleMissing,
                            onCheckedChange = onFilenameOnlyWhenTitleMissingChanged,
                            enabled = filenameDisplayMode != FilenameDisplayMode.Never
                        )
                    }
                    SettingsRoute.Visualization -> {
                        var showModeDialog by remember { mutableStateOf(false) }
                        var showEnabledDialog by remember { mutableStateOf(false) }
                        val basicPages = remember { basicVisualizationSettingsPages() }

                        SettingsSectionLabel("General")
                        SettingsItemCard(
                            title = "Current visualization",
                            description = visualizationMode.label,
                            icon = Icons.Default.GraphicEq,
                            onClick = { showModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Enabled visualizations",
                            description = "${enabledVisualizationModes.size}/${basicPages.size} basic modes enabled",
                            icon = Icons.Default.Tune,
                            onClick = { showEnabledDialog = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Basic visualizations")
                        Text(
                            text = "These visualizations work on all cores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Basic visualization settings",
                            description = "Configure Bars, Oscilloscope, and VU meters.",
                            icon = Icons.Default.GraphicEq,
                            onClick = onOpenVisualizationBasic
                        )

                        if (showModeDialog) {
                            val availableModes = listOf(VisualizationMode.Off) + basicPages
                                .map { it.mode }
                                .filter { enabledVisualizationModes.contains(it) }
                            AlertDialog(
                                onDismissRequest = { showModeDialog = false },
                                title = { Text("Visualization mode") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Available modes depend on enabled visualizations and current core/song.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        availableModes.forEach { mode ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onVisualizationModeChanged(mode)
                                                        showModeDialog = false
                                                    }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = mode == visualizationMode,
                                                    onClick = {
                                                        onVisualizationModeChanged(mode)
                                                        showModeDialog = false
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(mode.label)
                                            }
                                        }
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showModeDialog = false }) { Text("Close") }
                                },
                                confirmButton = {}
                            )
                        }

                        if (showEnabledDialog) {
                            var pendingEnabled by remember(showEnabledDialog) {
                                mutableStateOf(enabledVisualizationModes)
                            }
                            AlertDialog(
                                onDismissRequest = { showEnabledDialog = false },
                                title = { Text("Enabled visualizations") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(onClick = {
                                                pendingEnabled = basicPages.map { it.mode }.toSet()
                                            }) {
                                                Text("All")
                                            }
                                            OutlinedButton(onClick = { pendingEnabled = emptySet() }) {
                                                Text("None")
                                            }
                                        }
                                        VisualizationTier.entries.forEach { tier ->
                                            Text(
                                                text = tier.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val options = when (tier) {
                                                VisualizationTier.Basic -> basicPages.map { it.mode to it.title }
                                                VisualizationTier.Advanced -> emptyList()
                                                VisualizationTier.Specialized -> emptyList()
                                            }
                                            if (options.isEmpty()) {
                                                Text(
                                                    text = "No options yet.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                options.forEach { (mode, label) ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                pendingEnabled = if (pendingEnabled.contains(mode)) {
                                                                    pendingEnabled - mode
                                                                } else {
                                                                    pendingEnabled + mode
                                                                }
                                                            }
                                                            .padding(vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        androidx.compose.material3.Checkbox(
                                                            checked = pendingEnabled.contains(mode),
                                                            onCheckedChange = { checked ->
                                                                pendingEnabled = if (checked) {
                                                                    pendingEnabled + mode
                                                                } else {
                                                                    pendingEnabled - mode
                                                                }
                                                            }
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(label)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showEnabledDialog = false }) { Text("Cancel") }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        onEnabledVisualizationModesChanged(pendingEnabled)
                                        showEnabledDialog = false
                                    }) { Text("Apply") }
                                }
                            )
                        }
                    }
                    SettingsRoute.VisualizationBasic -> {
                        val basicPages = remember { basicVisualizationSettingsPages() }
                        SettingsSectionLabel("Basic visualizations")
                        Text(
                            text = "These visualizations work on all cores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        basicPages.forEachIndexed { index, page ->
                            SettingsItemCard(
                                title = page.title,
                                description = page.description,
                                icon = Icons.Default.GraphicEq,
                                onClick = {
                                    when (page.route) {
                                        SettingsRoute.VisualizationBasicBars -> onOpenVisualizationBasicBars()
                                        SettingsRoute.VisualizationBasicOscilloscope -> onOpenVisualizationBasicOscilloscope()
                                        SettingsRoute.VisualizationBasicVuMeters -> onOpenVisualizationBasicVuMeters()
                                        else -> Unit
                                    }
                                }
                            )
                            if (index < basicPages.lastIndex) {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                    SettingsRoute.VisualizationBasicBars -> {
                        val prefsName = "silicon_player_settings"
                        val barColorModeNoArtworkKey = "visualization_bar_color_mode_no_artwork"
                        val barColorModeWithArtworkKey = "visualization_bar_color_mode_with_artwork"
                        val barCustomColorKey = "visualization_bar_custom_color_argb"
                        val context = LocalContext.current
                        val prefs = remember(context) {
                            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                        }
                        var barColorModeNoArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        barColorModeNoArtworkKey,
                                        VisualizationOscColorMode.Monet.storageValue
                                    ),
                                    VisualizationOscColorMode.Monet
                                )
                            )
                        }
                        var barColorModeWithArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        barColorModeWithArtworkKey,
                                        VisualizationOscColorMode.Artwork.storageValue
                                    ),
                                    VisualizationOscColorMode.Artwork
                                )
                            )
                        }
                        var barCustomColorArgb by remember {
                            mutableIntStateOf(prefs.getInt(barCustomColorKey, 0xFF6BD8FF.toInt()))
                        }
                        var showBarCountDialog by remember { mutableStateOf(false) }
                        var showBarSmoothingDialog by remember { mutableStateOf(false) }
                        var showBarRoundnessDialog by remember { mutableStateOf(false) }
                        var showBarColorModeNoArtworkDialog by remember { mutableStateOf(false) }
                        var showBarColorModeWithArtworkDialog by remember { mutableStateOf(false) }
                        var showBarCustomColorDialog by remember { mutableStateOf(false) }

                        SettingsSectionLabel("Bars")
                        SettingsValuePickerCard(
                            title = "Bar count",
                            description = "Number of frequency bars shown in the spectrum.",
                            value = "$visualizationBarCount",
                            onClick = { showBarCountDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Smoothing",
                            description = "How quickly bars react to level changes.",
                            value = "$visualizationBarSmoothingPercent%",
                            onClick = { showBarSmoothingDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Roundness",
                            description = "Corner radius used for each bar.",
                            value = "${visualizationBarRoundnessDp}dp",
                            onClick = { showBarRoundnessDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Overlay on artwork",
                            description = "When disabled, bars are rendered over a blank background.",
                            checked = visualizationBarOverlayArtwork,
                            onCheckedChange = onVisualizationBarOverlayArtworkChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Use theme color",
                            description = "Use app theme color for bars instead of alternate accent.",
                            checked = visualizationBarUseThemeColor,
                            onCheckedChange = onVisualizationBarUseThemeColorChanged
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Colors (no artwork)")
                        SettingsValuePickerCard(
                            title = "Bar color",
                            description = "Color source used when no artwork is available.",
                            value = barColorModeNoArtwork.label,
                            onClick = { showBarColorModeNoArtworkDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsSectionLabel("Colors (with artwork)")
                        SettingsValuePickerCard(
                            title = "Bar color",
                            description = "Color source used when artwork is available.",
                            value = barColorModeWithArtwork.label,
                            onClick = { showBarColorModeWithArtworkDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsSectionLabel("Custom color")
                        SettingsValuePickerCard(
                            title = "Custom bar color",
                            description = "RGB color used when color mode is set to Custom.",
                            value = String.format(Locale.US, "#%06X", barCustomColorArgb and 0xFFFFFF),
                            onClick = { showBarCustomColorDialog = true }
                        )

                        if (showBarCountDialog) {
                            SteppedIntSliderDialog(
                                title = "Bar count",
                                unitLabel = "bars",
                                range = 8..96,
                                step = 1,
                                currentValue = visualizationBarCount,
                                onDismiss = { showBarCountDialog = false },
                                onConfirm = { value ->
                                    onVisualizationBarCountChanged(value)
                                    showBarCountDialog = false
                                }
                            )
                        }
                        if (showBarSmoothingDialog) {
                            SteppedIntSliderDialog(
                                title = "Bar smoothing",
                                unitLabel = "%",
                                range = 0..95,
                                step = 1,
                                currentValue = visualizationBarSmoothingPercent,
                                onDismiss = { showBarSmoothingDialog = false },
                                onConfirm = { value ->
                                    onVisualizationBarSmoothingPercentChanged(value)
                                    showBarSmoothingDialog = false
                                }
                            )
                        }
                        if (showBarRoundnessDialog) {
                            SteppedIntSliderDialog(
                                title = "Bar roundness",
                                unitLabel = "dp",
                                range = 0..24,
                                step = 1,
                                currentValue = visualizationBarRoundnessDp,
                                onDismiss = { showBarRoundnessDialog = false },
                                onConfirm = { value ->
                                    onVisualizationBarRoundnessDpChanged(value)
                                    showBarRoundnessDialog = false
                                }
                            )
                        }
                        if (showBarColorModeNoArtworkDialog) {
                            VisualizationOscColorModeDialog(
                                title = "Bar color (no artwork)",
                                options = listOf(
                                    VisualizationOscColorMode.Monet,
                                    VisualizationOscColorMode.White,
                                    VisualizationOscColorMode.Custom
                                ),
                                selectedMode = barColorModeNoArtwork,
                                onDismiss = { showBarColorModeNoArtworkDialog = false },
                                onSelect = { mode ->
                                    barColorModeNoArtwork = mode
                                    prefs.edit().putString(barColorModeNoArtworkKey, mode.storageValue).apply()
                                    showBarColorModeNoArtworkDialog = false
                                }
                            )
                        }
                        if (showBarColorModeWithArtworkDialog) {
                            VisualizationOscColorModeDialog(
                                title = "Bar color (with artwork)",
                                options = listOf(
                                    VisualizationOscColorMode.Artwork,
                                    VisualizationOscColorMode.Monet,
                                    VisualizationOscColorMode.White,
                                    VisualizationOscColorMode.Custom
                                ),
                                selectedMode = barColorModeWithArtwork,
                                onDismiss = { showBarColorModeWithArtworkDialog = false },
                                onSelect = { mode ->
                                    barColorModeWithArtwork = mode
                                    prefs.edit().putString(barColorModeWithArtworkKey, mode.storageValue).apply()
                                    showBarColorModeWithArtworkDialog = false
                                }
                            )
                        }
                        if (showBarCustomColorDialog) {
                            VisualizationRgbColorPickerDialog(
                                title = "Custom bar color",
                                initialArgb = barCustomColorArgb,
                                onDismiss = { showBarCustomColorDialog = false },
                                onConfirm = { argb ->
                                    barCustomColorArgb = argb
                                    prefs.edit().putInt(barCustomColorKey, argb).apply()
                                    showBarCustomColorDialog = false
                                }
                            )
                        }
                    }
                    SettingsRoute.VisualizationBasicOscilloscope -> {
                        val prefsName = "silicon_player_settings"
                        val oscWindowKey = "visualization_osc_window_ms"
                        val oscTriggerKey = "visualization_osc_trigger_mode"
                        val oscFpsModeKey = "visualization_osc_fps_mode"
                        val oscLineWidthKey = "visualization_osc_line_width_dp"
                        val oscGridWidthKey = "visualization_osc_grid_width_dp"
                        val oscVerticalGridEnabledKey = "visualization_osc_vertical_grid_enabled"
                        val oscCenterLineEnabledKey = "visualization_osc_center_line_enabled"
                        val oscLineNoArtworkColorModeKey = "visualization_osc_line_color_mode_no_artwork"
                        val oscGridNoArtworkColorModeKey = "visualization_osc_grid_color_mode_no_artwork"
                        val oscLineArtworkColorModeKey = "visualization_osc_line_color_mode_with_artwork"
                        val oscGridArtworkColorModeKey = "visualization_osc_grid_color_mode_with_artwork"
                        val oscCustomLineColorKey = "visualization_osc_custom_line_color_argb"
                        val oscCustomGridColorKey = "visualization_osc_custom_grid_color_argb"
                        val context = LocalContext.current
                        val prefs = remember(context) {
                            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                        }
                        var visualizationOscWindowMs by remember {
                            mutableIntStateOf(
                                prefs.getInt(oscWindowKey, 40).coerceIn(5, 200)
                            )
                        }
                        var visualizationOscTriggerMode by remember {
                            mutableStateOf(
                                VisualizationOscTriggerMode.fromStorage(
                                    prefs.getString(
                                        oscTriggerKey,
                                        VisualizationOscTriggerMode.Rising.storageValue
                                    )
                                )
                            )
                        }
                        var visualizationOscFpsMode by remember {
                            mutableStateOf(
                                VisualizationOscFpsMode.fromStorage(
                                    prefs.getString(
                                        oscFpsModeKey,
                                        VisualizationOscFpsMode.Default.storageValue
                                    )
                                )
                            )
                        }
                        var visualizationOscLineWidthDp by remember {
                            mutableIntStateOf(
                                prefs.getInt(oscLineWidthKey, 3).coerceIn(1, 12)
                            )
                        }
                        var visualizationOscGridWidthDp by remember {
                            mutableIntStateOf(
                                prefs.getInt(oscGridWidthKey, 2).coerceIn(1, 8)
                            )
                        }
                        var visualizationOscVerticalGridEnabled by remember {
                            mutableStateOf(prefs.getBoolean(oscVerticalGridEnabledKey, false))
                        }
                        var visualizationOscCenterLineEnabled by remember {
                            mutableStateOf(prefs.getBoolean(oscCenterLineEnabledKey, false))
                        }
                        var oscLineColorModeNoArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        oscLineNoArtworkColorModeKey,
                                        VisualizationOscColorMode.Monet.storageValue
                                    ),
                                    VisualizationOscColorMode.Monet
                                )
                            )
                        }
                        var oscGridColorModeNoArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        oscGridNoArtworkColorModeKey,
                                        VisualizationOscColorMode.Monet.storageValue
                                    ),
                                    VisualizationOscColorMode.Monet
                                )
                            )
                        }
                        var oscLineColorModeWithArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        oscLineArtworkColorModeKey,
                                        VisualizationOscColorMode.Artwork.storageValue
                                    ),
                                    VisualizationOscColorMode.Artwork
                                )
                            )
                        }
                        var oscGridColorModeWithArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        oscGridArtworkColorModeKey,
                                        VisualizationOscColorMode.Artwork.storageValue
                                    ),
                                    VisualizationOscColorMode.Artwork
                                )
                            )
                        }
                        var oscCustomLineColorArgb by remember {
                            mutableIntStateOf(prefs.getInt(oscCustomLineColorKey, 0xFF6BD8FF.toInt()))
                        }
                        var oscCustomGridColorArgb by remember {
                            mutableIntStateOf(prefs.getInt(oscCustomGridColorKey, 0x66FFFFFF))
                        }
                        var showWindowDialog by remember { mutableStateOf(false) }
                        var showTriggerDialog by remember { mutableStateOf(false) }
                        var showFpsModeDialog by remember { mutableStateOf(false) }
                        var showLineWidthDialog by remember { mutableStateOf(false) }
                        var showGridWidthDialog by remember { mutableStateOf(false) }
                        var showLineNoArtworkColorModeDialog by remember { mutableStateOf(false) }
                        var showGridNoArtworkColorModeDialog by remember { mutableStateOf(false) }
                        var showLineArtworkColorModeDialog by remember { mutableStateOf(false) }
                        var showGridArtworkColorModeDialog by remember { mutableStateOf(false) }
                        var showCustomLineColorDialog by remember { mutableStateOf(false) }
                        var showCustomGridColorDialog by remember { mutableStateOf(false) }
                        SettingsSectionLabel("Oscilloscope")
                        PlayerSettingToggleCard(
                            title = "Stereo mode",
                            description = "Render stereo waveform when channel layout supports it.",
                            checked = visualizationOscStereo,
                            onCheckedChange = onVisualizationOscStereoChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Visible window",
                            description = "Time span shown across the oscilloscope view.",
                            value = "${visualizationOscWindowMs} ms",
                            onClick = { showWindowDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Trigger",
                            description = "Sync mode used to stabilize waveform start point.",
                            value = visualizationOscTriggerMode.label,
                            onClick = { showTriggerDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Scope frame rate",
                            description = "Rendering rate for oscilloscope updates.",
                            value = visualizationOscFpsMode.label,
                            onClick = { showFpsModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Line width",
                            description = "Stroke width for waveform lines.",
                            value = "${visualizationOscLineWidthDp}dp",
                            onClick = { showLineWidthDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Grid width",
                            description = "Stroke width for grid lines.",
                            value = "${visualizationOscGridWidthDp}dp",
                            onClick = { showGridWidthDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Show vertical grid lines",
                            description = "Display vertical time divisions in the oscilloscope.",
                            checked = visualizationOscVerticalGridEnabled,
                            onCheckedChange = { enabled ->
                                visualizationOscVerticalGridEnabled = enabled
                                prefs.edit().putBoolean(oscVerticalGridEnabledKey, enabled).apply()
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Show centerline",
                            description = "Display the waveform center reference line.",
                            checked = visualizationOscCenterLineEnabled,
                            onCheckedChange = { enabled ->
                                visualizationOscCenterLineEnabled = enabled
                                prefs.edit().putBoolean(oscCenterLineEnabledKey, enabled).apply()
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Colors (no artwork)")
                        SettingsValuePickerCard(
                            title = "Line color",
                            description = "Color source used when no artwork is available.",
                            value = oscLineColorModeNoArtwork.label,
                            onClick = { showLineNoArtworkColorModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Grid color",
                            description = "Color source used when no artwork is available.",
                            value = oscGridColorModeNoArtwork.label,
                            onClick = { showGridNoArtworkColorModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Colors (with artwork)")
                        SettingsValuePickerCard(
                            title = "Line color",
                            description = "Color source used when artwork is available.",
                            value = oscLineColorModeWithArtwork.label,
                            onClick = { showLineArtworkColorModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Grid color",
                            description = "Color source used when artwork is available.",
                            value = oscGridColorModeWithArtwork.label,
                            onClick = { showGridArtworkColorModeDialog = true }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Custom colors")
                        SettingsValuePickerCard(
                            title = "Custom line color",
                            description = "RGB color used when line color mode is Custom.",
                            value = String.format(Locale.US, "#%06X", oscCustomLineColorArgb and 0xFFFFFF),
                            onClick = { showCustomLineColorDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Custom grid color",
                            description = "RGB color used when grid color mode is Custom.",
                            value = String.format(Locale.US, "#%06X", oscCustomGridColorArgb and 0xFFFFFF),
                            onClick = { showCustomGridColorDialog = true }
                        )
                        if (showWindowDialog) {
                            SteppedIntSliderDialog(
                                title = "Visible window",
                                unitLabel = "ms",
                                range = 5..200,
                                step = 1,
                                currentValue = visualizationOscWindowMs,
                                onDismiss = { showWindowDialog = false },
                                onConfirm = { value ->
                                    val clamped = value.coerceIn(5, 200)
                                    visualizationOscWindowMs = clamped
                                    prefs.edit()
                                        .putInt(oscWindowKey, clamped)
                                        .apply()
                                    showWindowDialog = false
                                }
                            )
                        }
                        if (showTriggerDialog) {
                            AlertDialog(
                                onDismissRequest = { showTriggerDialog = false },
                                title = { Text("Oscilloscope trigger") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        VisualizationOscTriggerMode.entries.forEach { mode ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        visualizationOscTriggerMode = mode
                                                        prefs.edit()
                                                            .putString(
                                                                oscTriggerKey,
                                                                mode.storageValue
                                                            )
                                                            .apply()
                                                        showTriggerDialog = false
                                                    }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = mode == visualizationOscTriggerMode,
                                                    onClick = {
                                                        visualizationOscTriggerMode = mode
                                                        prefs.edit()
                                                            .putString(
                                                                oscTriggerKey,
                                                                mode.storageValue
                                                            )
                                                            .apply()
                                                        showTriggerDialog = false
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(mode.label)
                                            }
                                        }
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showTriggerDialog = false }) {
                                        Text("Close")
                                    }
                                },
                                confirmButton = {}
                            )
                        }
                        if (showFpsModeDialog) {
                            AlertDialog(
                                onDismissRequest = { showFpsModeDialog = false },
                                title = { Text("Scope frame rate") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        VisualizationOscFpsMode.entries.forEach { mode ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        visualizationOscFpsMode = mode
                                                        prefs.edit()
                                                            .putString(oscFpsModeKey, mode.storageValue)
                                                            .apply()
                                                        showFpsModeDialog = false
                                                    }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = mode == visualizationOscFpsMode,
                                                    onClick = {
                                                        visualizationOscFpsMode = mode
                                                        prefs.edit()
                                                            .putString(oscFpsModeKey, mode.storageValue)
                                                            .apply()
                                                        showFpsModeDialog = false
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(mode.label)
                                            }
                                        }
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showFpsModeDialog = false }) {
                                        Text("Close")
                                    }
                                },
                                confirmButton = {}
                            )
                        }
                        if (showLineWidthDialog) {
                            SteppedIntSliderDialog(
                                title = "Scope line width",
                                unitLabel = "dp",
                                range = 1..12,
                                step = 1,
                                currentValue = visualizationOscLineWidthDp,
                                onDismiss = { showLineWidthDialog = false },
                                onConfirm = { value ->
                                    val clamped = value.coerceIn(1, 12)
                                    visualizationOscLineWidthDp = clamped
                                    prefs.edit().putInt(oscLineWidthKey, clamped).apply()
                                    showLineWidthDialog = false
                                }
                            )
                        }
                        if (showGridWidthDialog) {
                            SteppedIntSliderDialog(
                                title = "Grid line width",
                                unitLabel = "dp",
                                range = 1..8,
                                step = 1,
                                currentValue = visualizationOscGridWidthDp,
                                onDismiss = { showGridWidthDialog = false },
                                onConfirm = { value ->
                                    val clamped = value.coerceIn(1, 8)
                                    visualizationOscGridWidthDp = clamped
                                    prefs.edit().putInt(oscGridWidthKey, clamped).apply()
                                    showGridWidthDialog = false
                                }
                            )
                        }
                        if (showLineNoArtworkColorModeDialog) {
                            VisualizationOscColorModeDialog(
                                title = "Line color (no artwork)",
                                options = listOf(
                                    VisualizationOscColorMode.Monet,
                                    VisualizationOscColorMode.White,
                                    VisualizationOscColorMode.Custom
                                ),
                                selectedMode = oscLineColorModeNoArtwork,
                                onDismiss = { showLineNoArtworkColorModeDialog = false },
                                onSelect = { mode ->
                                    oscLineColorModeNoArtwork = mode
                                    prefs.edit()
                                        .putString(oscLineNoArtworkColorModeKey, mode.storageValue)
                                        .apply()
                                    showLineNoArtworkColorModeDialog = false
                                }
                            )
                        }
                        if (showGridNoArtworkColorModeDialog) {
                            VisualizationOscColorModeDialog(
                                title = "Grid color (no artwork)",
                                options = listOf(
                                    VisualizationOscColorMode.Monet,
                                    VisualizationOscColorMode.White,
                                    VisualizationOscColorMode.Custom
                                ),
                                selectedMode = oscGridColorModeNoArtwork,
                                onDismiss = { showGridNoArtworkColorModeDialog = false },
                                onSelect = { mode ->
                                    oscGridColorModeNoArtwork = mode
                                    prefs.edit()
                                        .putString(oscGridNoArtworkColorModeKey, mode.storageValue)
                                        .apply()
                                    showGridNoArtworkColorModeDialog = false
                                }
                            )
                        }
                        if (showLineArtworkColorModeDialog) {
                            VisualizationOscColorModeDialog(
                                title = "Line color (with artwork)",
                                options = listOf(
                                    VisualizationOscColorMode.Artwork,
                                    VisualizationOscColorMode.Monet,
                                    VisualizationOscColorMode.White,
                                    VisualizationOscColorMode.Custom
                                ),
                                selectedMode = oscLineColorModeWithArtwork,
                                onDismiss = { showLineArtworkColorModeDialog = false },
                                onSelect = { mode ->
                                    oscLineColorModeWithArtwork = mode
                                    prefs.edit()
                                        .putString(oscLineArtworkColorModeKey, mode.storageValue)
                                        .apply()
                                    showLineArtworkColorModeDialog = false
                                }
                            )
                        }
                        if (showGridArtworkColorModeDialog) {
                            VisualizationOscColorModeDialog(
                                title = "Grid color (with artwork)",
                                options = listOf(
                                    VisualizationOscColorMode.Artwork,
                                    VisualizationOscColorMode.Monet,
                                    VisualizationOscColorMode.White,
                                    VisualizationOscColorMode.Custom
                                ),
                                selectedMode = oscGridColorModeWithArtwork,
                                onDismiss = { showGridArtworkColorModeDialog = false },
                                onSelect = { mode ->
                                    oscGridColorModeWithArtwork = mode
                                    prefs.edit()
                                        .putString(oscGridArtworkColorModeKey, mode.storageValue)
                                        .apply()
                                    showGridArtworkColorModeDialog = false
                                }
                            )
                        }
                        if (showCustomLineColorDialog) {
                            VisualizationRgbColorPickerDialog(
                                title = "Custom line color",
                                initialArgb = oscCustomLineColorArgb,
                                onDismiss = { showCustomLineColorDialog = false },
                                onConfirm = { argb ->
                                    oscCustomLineColorArgb = argb
                                    prefs.edit().putInt(oscCustomLineColorKey, argb).apply()
                                    showCustomLineColorDialog = false
                                }
                            )
                        }
                        if (showCustomGridColorDialog) {
                            VisualizationRgbColorPickerDialog(
                                title = "Custom grid color",
                                initialArgb = oscCustomGridColorArgb,
                                onDismiss = { showCustomGridColorDialog = false },
                                onConfirm = { argb ->
                                    oscCustomGridColorArgb = argb
                                    prefs.edit().putInt(oscCustomGridColorKey, argb).apply()
                                    showCustomGridColorDialog = false
                                }
                            )
                        }
                    }
                    SettingsRoute.VisualizationBasicVuMeters -> {
                        val prefsName = "silicon_player_settings"
                        val vuColorModeNoArtworkKey = "visualization_vu_color_mode_no_artwork"
                        val vuColorModeWithArtworkKey = "visualization_vu_color_mode_with_artwork"
                        val vuCustomColorKey = "visualization_vu_custom_color_argb"
                        val context = LocalContext.current
                        val prefs = remember(context) {
                            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                        }
                        var vuColorModeNoArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        vuColorModeNoArtworkKey,
                                        VisualizationOscColorMode.Monet.storageValue
                                    ),
                                    VisualizationOscColorMode.Monet
                                )
                            )
                        }
                        var vuColorModeWithArtwork by remember {
                            mutableStateOf(
                                VisualizationOscColorMode.fromStorage(
                                    prefs.getString(
                                        vuColorModeWithArtworkKey,
                                        VisualizationOscColorMode.Artwork.storageValue
                                    ),
                                    VisualizationOscColorMode.Artwork
                                )
                            )
                        }
                        var vuCustomColorArgb by remember {
                            mutableIntStateOf(prefs.getInt(vuCustomColorKey, 0xFF6BD8FF.toInt()))
                        }
                        var showVuAnchorDialog by remember { mutableStateOf(false) }
                        var showVuSmoothingDialog by remember { mutableStateOf(false) }
                        var showVuColorModeNoArtworkDialog by remember { mutableStateOf(false) }
                        var showVuColorModeWithArtworkDialog by remember { mutableStateOf(false) }
                        var showVuCustomColorDialog by remember { mutableStateOf(false) }
                        SettingsSectionLabel("VU meters")
                        SettingsValuePickerCard(
                            title = "Anchor position",
                            description = "Where VU meter rows are aligned in the artwork area.",
                            value = visualizationVuAnchor.label,
                            onClick = { showVuAnchorDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsValuePickerCard(
                            title = "Smoothing",
                            description = "How quickly VU levels react to level changes.",
                            value = "$visualizationVuSmoothingPercent%",
                            onClick = { showVuSmoothingDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerSettingToggleCard(
                            title = "Use theme color",
                            description = "Use app theme color for VU bars instead of alternate accent.",
                            checked = visualizationVuUseThemeColor,
                            onCheckedChange = onVisualizationVuUseThemeColorChanged
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Colors (no artwork)")
                        SettingsValuePickerCard(
                            title = "VU color",
                            description = "Color source used when no artwork is available.",
                            value = vuColorModeNoArtwork.label,
                            onClick = { showVuColorModeNoArtworkDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsSectionLabel("Colors (with artwork)")
                        SettingsValuePickerCard(
                            title = "VU color",
                            description = "Color source used when artwork is available.",
                            value = vuColorModeWithArtwork.label,
                            onClick = { showVuColorModeWithArtworkDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsSectionLabel("Custom color")
                        SettingsValuePickerCard(
                            title = "Custom VU color",
                            description = "RGB color used when VU color mode is Custom.",
                            value = String.format(Locale.US, "#%06X", vuCustomColorArgb and 0xFFFFFF),
                            onClick = { showVuCustomColorDialog = true }
                        )
                        if (showVuSmoothingDialog) {
                            SteppedIntSliderDialog(
                                title = "VU smoothing",
                                unitLabel = "%",
                                range = 0..95,
                                step = 1,
                                currentValue = visualizationVuSmoothingPercent,
                                onDismiss = { showVuSmoothingDialog = false },
                                onConfirm = { value ->
                                    onVisualizationVuSmoothingPercentChanged(value)
                                    showVuSmoothingDialog = false
                                }
                            )
                        }
                        if (showVuAnchorDialog) {
                            AlertDialog(
                                onDismissRequest = { showVuAnchorDialog = false },
                                title = { Text("VU anchor position") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        VisualizationVuAnchor.entries.forEach { anchor ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onVisualizationVuAnchorChanged(anchor)
                                                        showVuAnchorDialog = false
                                                    }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = anchor == visualizationVuAnchor,
                                                    onClick = {
                                                        onVisualizationVuAnchorChanged(anchor)
                                                        showVuAnchorDialog = false
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(anchor.label)
                                            }
                                        }
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showVuAnchorDialog = false }) {
                                        Text("Close")
                                    }
                                },
                                confirmButton = {}
                            )
                        }
                        if (showVuColorModeNoArtworkDialog) {
                            VisualizationOscColorModeDialog(
                                title = "VU color (no artwork)",
                                options = listOf(
                                    VisualizationOscColorMode.Monet,
                                    VisualizationOscColorMode.White,
                                    VisualizationOscColorMode.Custom
                                ),
                                selectedMode = vuColorModeNoArtwork,
                                onDismiss = { showVuColorModeNoArtworkDialog = false },
                                onSelect = { mode ->
                                    vuColorModeNoArtwork = mode
                                    prefs.edit().putString(vuColorModeNoArtworkKey, mode.storageValue).apply()
                                    showVuColorModeNoArtworkDialog = false
                                }
                            )
                        }
                        if (showVuColorModeWithArtworkDialog) {
                            VisualizationOscColorModeDialog(
                                title = "VU color (with artwork)",
                                options = listOf(
                                    VisualizationOscColorMode.Artwork,
                                    VisualizationOscColorMode.Monet,
                                    VisualizationOscColorMode.White,
                                    VisualizationOscColorMode.Custom
                                ),
                                selectedMode = vuColorModeWithArtwork,
                                onDismiss = { showVuColorModeWithArtworkDialog = false },
                                onSelect = { mode ->
                                    vuColorModeWithArtwork = mode
                                    prefs.edit().putString(vuColorModeWithArtworkKey, mode.storageValue).apply()
                                    showVuColorModeWithArtworkDialog = false
                                }
                            )
                        }
                        if (showVuCustomColorDialog) {
                            VisualizationRgbColorPickerDialog(
                                title = "Custom VU color",
                                initialArgb = vuCustomColorArgb,
                                onDismiss = { showVuCustomColorDialog = false },
                                onConfirm = { argb ->
                                    vuCustomColorArgb = argb
                                    prefs.edit().putInt(vuCustomColorKey, argb).apply()
                                    showVuCustomColorDialog = false
                                }
                            )
                        }
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

                    SettingsRoute.UrlCache -> {
                        var showCacheTrackLimitDialog by remember { mutableStateOf(false) }
                        var showCacheSizeLimitDialog by remember { mutableStateOf(false) }
                        var showClearCacheConfirmDialog by remember { mutableStateOf(false) }
                        SettingsSectionLabel("Cache behavior")
                        PlayerSettingToggleCard(
                            title = "Clear cache on app launch",
                            description = "Delete all cached files each time the app starts.",
                            checked = urlCacheClearOnLaunch,
                            onCheckedChange = onUrlCacheClearOnLaunchChanged
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsSectionLabel("Limits")
                        SettingsItemCard(
                            title = "Cache song limit",
                            description = "$urlCacheMaxTracks songs",
                            icon = Icons.Default.MoreHoriz,
                            onClick = { showCacheTrackLimitDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Cache size limit",
                            description = String.format(Locale.US, "%.2f GB", urlCacheMaxBytes / (1024.0 * 1024.0 * 1024.0)),
                            icon = Icons.Default.MoreHoriz,
                            onClick = { showCacheSizeLimitDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsItemCard(
                            title = "Manage cached files",
                            description = "Browse cached files, long-press multi-select, delete, and export.",
                            icon = Icons.Default.MoreHoriz,
                            onClick = onOpenCacheManager
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Danger zone")
                        SettingsItemCard(
                            title = "Clear cache now",
                            description = "Delete all currently cached files immediately.",
                            icon = Icons.Default.Delete,
                            onClick = { showClearCacheConfirmDialog = true }
                        )

                        if (showCacheTrackLimitDialog) {
                            var input by remember { mutableStateOf(urlCacheMaxTracks.toString()) }
                            AlertDialog(
                                onDismissRequest = { showCacheTrackLimitDialog = false },
                                title = { Text("Cache song limit") },
                                text = {
                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = { input = it },
                                        singleLine = true,
                                        label = { Text("Max songs") },
                                        placeholder = { Text("100") }
                                    )
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCacheTrackLimitDialog = false }) { Text("Cancel") }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val parsed = input.trim().toIntOrNull()
                                        if (parsed != null && parsed > 0) {
                                            onUrlCacheMaxTracksChanged(parsed)
                                            showCacheTrackLimitDialog = false
                                        }
                                    }) {
                                        Text("Save")
                                    }
                                }
                            )
                        }

                        if (showCacheSizeLimitDialog) {
                            var unit by remember {
                                mutableStateOf(
                                    if (urlCacheMaxBytes < CacheSizeUnit.GB.bytesPerUnit.toLong()) CacheSizeUnit.MB else CacheSizeUnit.GB
                                )
                            }
                            var input by remember {
                                mutableStateOf(
                                    String.format(Locale.US, "%.2f", urlCacheMaxBytes / unit.bytesPerUnit)
                                )
                            }
                            AlertDialog(
                                onDismissRequest = { showCacheSizeLimitDialog = false },
                                title = { Text("Cache size limit") },
                                text = {
                                    Column {
                                        OutlinedTextField(
                                            value = input,
                                            onValueChange = { input = it },
                                            singleLine = true,
                                            label = { Text("Max size (${unit.label})") },
                                            placeholder = { Text("1.00") }
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CacheSizeUnit.values().forEach { candidate ->
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            if (candidate != unit) {
                                                                val parsed = input.trim().replace(',', '.').toDoubleOrNull()
                                                                val currentBytes = if (parsed != null && parsed > 0.0) {
                                                                    parsed * unit.bytesPerUnit
                                                                } else {
                                                                    urlCacheMaxBytes.toDouble()
                                                                }
                                                                unit = candidate
                                                                input = String.format(Locale.US, "%.2f", currentBytes / candidate.bytesPerUnit)
                                                            }
                                                        }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = unit == candidate,
                                                        onClick = {
                                                            if (candidate != unit) {
                                                                val parsed = input.trim().replace(',', '.').toDoubleOrNull()
                                                                val currentBytes = if (parsed != null && parsed > 0.0) {
                                                                    parsed * unit.bytesPerUnit
                                                                } else {
                                                                    urlCacheMaxBytes.toDouble()
                                                                }
                                                                unit = candidate
                                                                input = String.format(Locale.US, "%.2f", currentBytes / candidate.bytesPerUnit)
                                                            }
                                                        }
                                                    )
                                                    Text(
                                                        text = candidate.label,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCacheSizeLimitDialog = false }) { Text("Cancel") }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val parsed = input.trim().replace(',', '.').toDoubleOrNull()
                                        if (parsed != null && parsed > 0.0) {
                                            val bytes = (parsed * unit.bytesPerUnit).toLong().coerceAtLeast(1L)
                                            onUrlCacheMaxBytesChanged(bytes)
                                            showCacheSizeLimitDialog = false
                                        }
                                    }) {
                                        Text("Save")
                                    }
                                }
                            )
                        }

                        if (showClearCacheConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearCacheConfirmDialog = false },
                                title = { Text("Clear cached files now?") },
                                text = { Text("This will remove all cached files, except the currently active one if it is being played.") },
                                dismissButton = {
                                    TextButton(onClick = { showClearCacheConfirmDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        onClearUrlCacheNow()
                                        showClearCacheConfirmDialog = false
                                    }) {
                                        Text("Clear cache")
                                    }
                                }
                            )
                        }
                    }
                    SettingsRoute.CacheManager -> {
                        var selectedPaths by remember { mutableStateOf(setOf<String>()) }
                        var showDeleteConfirmDialog by remember { mutableStateOf(false) }
                        LaunchedEffect(cachedSourceFiles) {
                            val existing = cachedSourceFiles.map { it.absolutePath }.toSet()
                            selectedPaths = selectedPaths.filterTo(mutableSetOf()) { it in existing }
                        }
                        LaunchedEffect(route) {
                            onRefreshCachedSourceFiles()
                        }
                        val inSelectionMode = selectedPaths.isNotEmpty()
                        val totalBytes = cachedSourceFiles.sumOf { it.sizeBytes.coerceAtLeast(0L) }
                        BackHandler(enabled = inSelectionMode) {
                            selectedPaths = emptySet()
                        }

                        SettingsSectionLabel("Overview")
                        Text(
                            text = "${cachedSourceFiles.size} files • ${formatCacheByteCount(totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDeleteConfirmDialog = true },
                                enabled = inSelectionMode,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete (${selectedPaths.size})")
                            }
                            OutlinedButton(
                                onClick = { onExportCachedSourceFiles(selectedPaths.toList()) },
                                enabled = inSelectionMode,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export (${selectedPaths.size})")
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedPaths = cachedSourceFiles.map { it.absolutePath }.toSet()
                                },
                                enabled = cachedSourceFiles.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select all")
                            }
                            OutlinedButton(
                                onClick = { selectedPaths = emptySet() },
                                enabled = inSelectionMode,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Deselect all")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap files to select. Back clears selection first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (cachedSourceFiles.isEmpty()) {
                            Text(
                                text = "No cached files.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            SettingsSectionLabel("Files")
                            cachedSourceFiles.forEach { entry ->
                                val isSelected = selectedPaths.contains(entry.absolutePath)
                                val sourceLine = entry.sourceId ?: "Source: unknown"
                                Surface(
                                    shape = SettingsCardShape,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onLongClick = {
                                                selectedPaths = if (isSelected) {
                                                    selectedPaths - entry.absolutePath
                                                } else {
                                                    selectedPaths + entry.absolutePath
                                                }
                                            },
                                            onClick = {
                                                if (selectedPaths.isEmpty()) {
                                                    selectedPaths = setOf(entry.absolutePath)
                                                } else {
                                                    selectedPaths = if (isSelected) {
                                                        selectedPaths - entry.absolutePath
                                                    } else {
                                                        selectedPaths + entry.absolutePath
                                                    }
                                                }
                                            }
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        Text(
                                            text = stripCachedFileHashPrefix(entry.fileName),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "${formatCacheByteCount(entry.sizeBytes)} • $sourceLine",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 2
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        if (showDeleteConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirmDialog = false },
                                title = { Text("Delete selected cached files?") },
                                text = { Text("This removes ${selectedPaths.size} selected cached file(s).") },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        onDeleteCachedSourceFiles(selectedPaths.toList())
                                        selectedPaths = emptySet()
                                        showDeleteConfirmDialog = false
                                    }) {
                                        Text("Delete")
                                    }
                                }
                            )
                        }
                    }
                    SettingsRoute.Ui -> ThemeModeSelectorCard(
                        selectedMode = themeMode,
                        onSelectedModeChanged = onThemeModeChanged
                    )
                    SettingsRoute.About -> AboutSettingsBody()
                    }
                    if (bottomContentPadding > 0.dp) {
                        Spacer(modifier = Modifier.height(bottomContentPadding))
                    }
                }
            }
        }
    }

    pendingResetAction?.let { action ->
        val title: String
        val message: String
        val confirmText: String
        val onConfirm: () -> Unit
        when (action) {
            SettingsResetAction.ClearAllSettings -> {
                title = "Clear all app settings?"
                message = "This resets app settings to defaults and keeps plugin settings unchanged."
                confirmText = "Clear app settings"
                onConfirm = onClearAllSettings
            }
            SettingsResetAction.ClearPluginSettings -> {
                title = "Clear all plugin settings?"
                message = "This resets all plugin/core settings to defaults and keeps app settings unchanged."
                confirmText = "Clear plugin settings"
                onConfirm = onClearAllPluginSettings
            }
        }

        AlertDialog(
            onDismissRequest = { pendingResetAction = null },
            title = { Text(title) },
            text = { Text(message) },
            dismissButton = {
                TextButton(onClick = { pendingResetAction = null }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    pendingResetAction = null
                }) {
                    Text(confirmText)
                }
            }
        )
    }

    pendingPluginResetName?.let { pluginName ->
        AlertDialog(
            onDismissRequest = { pendingPluginResetName = null },
            title = { Text("Reset $pluginName settings?") },
            text = { Text("This resets only $pluginName plugin/core settings to defaults.") },
            dismissButton = {
                TextButton(onClick = { pendingPluginResetName = null }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onResetPluginSettings(pluginName)
                    pendingPluginResetName = null
                }) {
                    Text("Reset")
                }
            }
        )
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
private fun SettingsValuePickerCard(
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = { if (enabled) onClick() },
        enabled = enabled
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
        }
    }
}

@Composable
internal fun PlayerSettingToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    var localChecked by remember(title) { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        localChecked = checked
    }

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = {
            if (!enabled) return@ElevatedCard
            val toggled = !localChecked
            localChecked = toggled
            onCheckedChange(toggled)
        }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = localChecked,
                onCheckedChange = { newValue ->
                    if (!enabled) return@Switch
                    localChecked = newValue
                    onCheckedChange(newValue)
                },
                enabled = enabled
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

internal data class IntChoice(val value: Int, val label: String)

private data class EnumChoice<T>(val value: T, val label: String)

internal fun formatMilliBelAsDbLabel(milliBel: Int): String {
    val db = milliBel / 100.0
    return String.format(Locale.US, "%+.1f dB", db)
}

enum class CoreOptionApplyPolicy {
    Live,
    RequiresPlaybackRestart
}

private enum class SettingsResetAction {
    ClearAllSettings,
    ClearPluginSettings
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> SettingsEnumSelectorCard(
    title: String,
    description: String,
    selectedValue: T,
    options: List<EnumChoice<T>>,
    onSelected: (T) -> Unit
) {
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: options.firstOrNull()?.label.orEmpty()
    var dialogOpen by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current

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
                style = MaterialTheme.typography.titleMedium
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
                                        onSelected(option.value)
                                        dialogOpen = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = option.value == selectedValue,
                                    onClick = {
                                        onSelected(option.value)
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
private fun AudioBackendSelectorCard(
    selectedPreference: AudioBackendPreference,
    onSelectedPreferenceChanged: (AudioBackendPreference) -> Unit
) {
    SettingsEnumSelectorCard(
        title = "Audio output backend",
        description = "Preferred output backend implementation.",
        selectedValue = selectedPreference,
        options = listOf(
            EnumChoice(AudioBackendPreference.AAudio, AudioBackendPreference.AAudio.label),
            EnumChoice(AudioBackendPreference.OpenSLES, AudioBackendPreference.OpenSLES.label),
            EnumChoice(AudioBackendPreference.AudioTrack, AudioBackendPreference.AudioTrack.label)
        ),
        onSelected = onSelectedPreferenceChanged
    )
}

@Composable
private fun AudioPerformanceModeSelectorCard(
    selectedMode: AudioPerformanceMode,
    onSelectedModeChanged: (AudioPerformanceMode) -> Unit
) {
    SettingsEnumSelectorCard(
        title = "Audio performance mode",
        description = "Tune output stream behavior for latency vs efficiency.",
        selectedValue = selectedMode,
        options = listOf(
            EnumChoice(AudioPerformanceMode.LowLatency, AudioPerformanceMode.LowLatency.label),
            EnumChoice(AudioPerformanceMode.None, AudioPerformanceMode.None.label),
            EnumChoice(AudioPerformanceMode.PowerSaving, AudioPerformanceMode.PowerSaving.label)
        ),
        onSelected = onSelectedModeChanged
    )
}

@Composable
private fun AudioBufferPresetSelectorCard(
    selectedPreset: AudioBufferPreset,
    onSelectedPresetChanged: (AudioBufferPreset) -> Unit
) {
    SettingsEnumSelectorCard(
        title = "Audio buffer preset",
        description = "Choose output buffer sizing profile for stability vs latency.",
        selectedValue = selectedPreset,
        options = listOf(
            EnumChoice(AudioBufferPreset.Small, AudioBufferPreset.Small.label),
            EnumChoice(AudioBufferPreset.Medium, AudioBufferPreset.Medium.label),
            EnumChoice(AudioBufferPreset.Large, AudioBufferPreset.Large.label)
        ),
        onSelected = onSelectedPresetChanged
    )
}

@Composable
private fun AudioResamplerSelectorCard(
    selectedPreference: AudioResamplerPreference,
    onSelectedPreferenceChanged: (AudioResamplerPreference) -> Unit
) {
    SettingsEnumSelectorCard(
        title = "Output resampler",
        description = "Choose the output resampler. SoX is experimental and falls back to built-in for discontinuous timeline cores.",
        selectedValue = selectedPreference,
        options = listOf(
            EnumChoice(AudioResamplerPreference.BuiltIn, AudioResamplerPreference.BuiltIn.label),
            EnumChoice(AudioResamplerPreference.Sox, AudioResamplerPreference.Sox.label)
        ),
        onSelected = onSelectedPreferenceChanged
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SampleRateSelectorCard(
    title: String,
    description: String,
    selectedHz: Int,
    statusText: String? = null,
    enabled: Boolean = true,
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
    val selectedLabel = if (enabled) {
        options.firstOrNull { it.hz == selectedHz }?.label ?: "Auto"
    } else {
        if (selectedHz > 0) "${formatSampleRateLabel(selectedHz)} (Fixed)" else "Auto (Fixed)"
    }
    var dialogOpen by remember { mutableStateOf(false) }

    val contentAlpha = if (enabled) 1f else 0.38f

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = { if (enabled) dialogOpen = true },
        enabled = enabled
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (!statusText.isNullOrBlank()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun OpenMptChoiceSelectorCard(
    title: String,
    description: String,
    selectedValue: Int,
    options: List<IntChoice>,
    onSelected: (Int) -> Unit
) {
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: options.firstOrNull()?.label.orEmpty()
    var dialogOpen by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current

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
                style = MaterialTheme.typography.titleMedium
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
                                        onSelected(option.value)
                                        dialogOpen = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = option.value == selectedValue,
                                    onClick = {
                                        onSelected(option.value)
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
internal fun OpenMptDialogSliderCard(
    title: String,
    description: String,
    value: Int,
    valueRange: IntRange,
    step: Int,
    valueLabel: (Int) -> String,
    showNudgeButtons: Boolean = false,
    nudgeStep: Int = step,
    onValueChanged: (Int) -> Unit
) {
    val coercedValue = value.coerceIn(valueRange.first, valueRange.last)
    val sliderSteps = ((valueRange.last - valueRange.first) / step).coerceAtLeast(1) - 1
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
                text = valueLabel(coercedValue),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (dialogOpen) {
        var sliderValue by remember(value) { mutableIntStateOf(coercedValue) }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = valueLabel(sliderValue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CompositionLocalProvider(
                        androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showNudgeButtons) {
                                IconButton(
                                    onClick = {
                                        val snapped = sliderValue - nudgeStep
                                        sliderValue = snapped.coerceIn(valueRange.first, valueRange.last)
                                    },
                                    enabled = sliderValue > valueRange.first,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Slider(
                                value = sliderValue.toFloat(),
                                onValueChange = { raw ->
                                    val stepsFromStart = ((raw - valueRange.first.toFloat()) / step.toFloat()).roundToInt()
                                    val snapped = valueRange.first + (stepsFromStart * step)
                                    sliderValue = snapped.coerceIn(valueRange.first, valueRange.last)
                                },
                                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                                steps = sliderSteps,
                                modifier = Modifier.weight(1f)
                            )
                            if (showNudgeButtons) {
                                IconButton(
                                    onClick = {
                                        val snapped = sliderValue + nudgeStep
                                        sliderValue = snapped.coerceIn(valueRange.first, valueRange.last)
                                    },
                                    enabled = sliderValue < valueRange.last,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
            confirmButton = {
                TextButton(onClick = {
                    onValueChanged(sliderValue)
                    dialogOpen = false
                }) {
                    Text("Apply")
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun OpenMptVolumeRampingCard(
    title: String,
    description: String,
    value: Int,
    onValueChanged: (Int) -> Unit
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val autoEnabled = value < 0
    val displayedValue = if (autoEnabled) "Auto" else value.toString()

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
                text = displayedValue,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (dialogOpen) {
        var autoState by remember(value) { mutableStateOf(value < 0) }
        var sliderValue by remember(value) { mutableIntStateOf(value.coerceIn(0, 10)) }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = autoState,
                            onCheckedChange = { enabled ->
                                autoState = enabled
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (autoState) "Current: Auto" else "Current: $sliderValue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = sliderValue.toFloat(),
                        onValueChange = { raw ->
                            sliderValue = raw.roundToInt().coerceIn(0, 10)
                        },
                        valueRange = 0f..10f,
                        steps = 9,
                        enabled = !autoState
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChanged(if (autoState) -1 else sliderValue)
                    dialogOpen = false
                }) {
                    Text("Apply")
                }
            }
        )
    }
}


@Composable
private fun ClearAudioParametersCard(
    onClearAll: () -> Unit,
    onClearPlugins: () -> Unit,
    onClearSongs: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        onClick = { showDialog = true }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Clear saved parameters",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reset volume settings for all, plugins, or songs",
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

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Clear saved parameters") },
            text = {
                Column {
                    Text("Choose which audio parameters to reset:")
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            onClearAll()
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear all")
                    }
                    TextButton(
                        onClick = {
                            onClearPlugins()
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear plugin volumes")
                    }
                    TextButton(
                        onClick = {
                            onClearSongs()
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear song volumes")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
private fun FilenameDisplayModeDialog(
    currentMode: FilenameDisplayMode,
    onModeSelected: (FilenameDisplayMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show filename") },
        text = {
            Column {
                FilenameDisplayMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(mode.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ============================================================================
// Plugin Management Composables
// ============================================================================

@Composable
private fun PluginListItemCard(
    pluginName: String,
    priority: Int,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    editMode: Boolean,
    isDragging: Boolean,
    dragOffsetPx: Float,
    nudgeOffsetPx: Float,
    nudgeNonce: Int,
    contentAlpha: Float,
    enableInteractions: Boolean,
    switchEnabled: Boolean,
    onMeasuredHeight: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val rowNudgeAnim = remember(nudgeNonce) { Animatable(nudgeOffsetPx) }
    LaunchedEffect(nudgeNonce) {
        if (nudgeNonce > 0 && nudgeOffsetPx != 0f) {
            rowNudgeAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
            )
        }
    }
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = if (isDragging) dragOffsetPx else 0f,
        animationSpec = if (isDragging) snap() else spring(
            dampingRatio = 1.0f,
            stiffness = 520f
        ),
        label = "pluginCardDragOffset"
    )
    val displayOffsetY = animatedDragOffsetY + if (isDragging) 0f else rowNudgeAnim.value

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 100f else 0f)
            .alpha(contentAlpha)
            .clip(SettingsCardShape)
            .offset { IntOffset(x = 0, y = displayOffsetY.roundToInt()) }
            .let { base ->
                if (editMode && enableInteractions) {
                    base.pointerInput(pluginName, editMode) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            }
                        )
                    }
                } else if (!editMode && enableInteractions) {
                    base.clickable(onClick = onClick)
                } else {
                    base
                }
            },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = SettingsCardShape,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onMeasuredHeight(it.height.toFloat()) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pluginName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Priority: $priority",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val extensionCount = remember(pluginName) {
                    NativeBridge.getDecoderEnabledExtensions(pluginName).size
                }
                Text(
                    text = "$extensionCount extensions enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (editMode) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                    enabled = switchEnabled
                )
            }
        }
    }
}

@Composable
private fun PluginDetailScreen(
    pluginName: String,
    onPriorityChanged: (Int) -> Unit,
    onExtensionsChanged: (Array<String>) -> Unit
) {
    var isEnabled by remember { mutableStateOf(NativeBridge.isDecoderEnabled(pluginName)) }
    var priority by remember { mutableIntStateOf(NativeBridge.getDecoderPriority(pluginName)) }
    val supportedExtensions = remember { NativeBridge.getDecoderSupportedExtensions(pluginName) }
    var enabledExtensions by remember {
        mutableStateOf(NativeBridge.getDecoderEnabledExtensions(pluginName).toSet())
    }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showExtensionsDialog by remember { mutableStateOf(false) }

    Column {
        // Enable/Disable toggle
        SettingsSectionLabel("Plugin status")
        PlayerSettingToggleCard(
            title = "Enable plugin",
            description = "When disabled, this plugin will not be used for any files.",
            checked = isEnabled,
            onCheckedChange = { enabled ->
                isEnabled = enabled
                NativeBridge.setDecoderEnabled(pluginName, enabled)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Priority
        SettingsSectionLabel("Priority")
        SettingsItemCard(
            title = "Plugin priority: $priority",
            description = "Lower value means higher priority. Plugins are tried in ascending order for matching extensions. Click to change.",
            icon = Icons.Default.Tune,
            onClick = { showPriorityDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Extensions
        SettingsSectionLabel("Handled extensions")
        val enabledCount = enabledExtensions.size
        val totalCount = supportedExtensions.size
        SettingsItemCard(
            title = "$enabledCount of $totalCount extensions enabled",
            description = "Select which file extensions this plugin should handle.",
            icon = Icons.Default.MusicNote,
            onClick = { showExtensionsDialog = true }
        )
    }

    // Priority dialog
    if (showPriorityDialog) {
        PriorityPickerDialog(
            currentPriority = priority,
            onPrioritySelected = { newPriority ->
                priority = newPriority
                onPriorityChanged(newPriority)
                NativeBridge.setDecoderPriority(pluginName, newPriority)
                showPriorityDialog = false
            },
            onDismiss = { showPriorityDialog = false }
        )
    }

    // Extensions dialog
    if (showExtensionsDialog) {
        ExtensionSelectorDialog(
            supportedExtensions = supportedExtensions,
            enabledExtensions = enabledExtensions,
            onExtensionsChanged = { newEnabled ->
                enabledExtensions = newEnabled

                // Save to native
                val extensionsArray = if (newEnabled.size == supportedExtensions.size) {
                    // All enabled, save empty array (optimization)
                    emptyArray()
                } else {
                    newEnabled.toTypedArray()
                }
                onExtensionsChanged(extensionsArray)
                NativeBridge.setDecoderEnabledExtensions(pluginName, extensionsArray)
            },
            onDismiss = { showExtensionsDialog = false }
        )
    }
}

@Composable
private fun ExtensionSelectorDialog(
    supportedExtensions: Array<String>,
    enabledExtensions: Set<String>,
    onExtensionsChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var tempEnabledExtensions by remember { mutableStateOf(enabledExtensions) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Filter extensions based on search query
    val filteredExtensions = remember(searchQuery, supportedExtensions) {
        if (searchQuery.isBlank()) {
            supportedExtensions.toList()
        } else {
            supportedExtensions.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select extensions") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                // Search bar with rounded corners
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search extensions...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Select All / Deselect All buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        tempEnabledExtensions = supportedExtensions.toSet()
                    }) {
                        Text("Select All")
                    }
                    TextButton(onClick = {
                        tempEnabledExtensions = emptySet()
                    }) {
                        Text("Deselect All")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable list with scrollbar indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(
                            items = filteredExtensions,
                            key = { it }
                        ) { ext ->
                            val isEnabled = ext in tempEnabledExtensions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tempEnabledExtensions = if (isEnabled) {
                                            tempEnabledExtensions - ext
                                        } else {
                                            tempEnabledExtensions + ext
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        tempEnabledExtensions = if (checked) {
                                            tempEnabledExtensions + ext
                                        } else {
                                            tempEnabledExtensions - ext
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(ext.uppercase())
                            }
                        }
                    }

                    // Scrollbar indicator - only show when there are many items
                    if (filteredExtensions.size > 8) {
                        // Use derivedStateOf with actual layout info for accurate scrollbar
                        val scrollbarPosition by remember {
                            androidx.compose.runtime.derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                                val totalItemsHeight = layoutInfo.totalItemsCount *
                                    (layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 40)

                                if (viewportHeight <= 0 || totalItemsHeight <= viewportHeight) {
                                    return@derivedStateOf Triple(300f, 300f, 0f)
                                }

                                // Calculate thumb height based on viewport ratio
                                val thumbHeight = maxOf(
                                    20f,
                                    (viewportHeight.toFloat() / totalItemsHeight.toFloat()) * 300f
                                )

                                // Calculate scroll offset using layout info
                                val firstVisibleItemIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                                val firstVisibleItemOffset = layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0
                                val itemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 40

                                val scrollOffset = if (itemSize > 0) {
                                    firstVisibleItemIndex * itemSize - firstVisibleItemOffset
                                } else {
                                    0
                                }

                                val maxScroll = totalItemsHeight - viewportHeight
                                val scrollProgress = if (maxScroll > 0) {
                                    (scrollOffset.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }

                                val thumbOffset = (scrollProgress * (300f - thumbHeight)).coerceIn(0f, 300f - thumbHeight)

                                Triple(300f, thumbHeight, thumbOffset)
                            }
                        }

                        val (viewportHeight, thumbHeight, thumbOffset) = scrollbarPosition

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .width(6.dp)
                                .height(viewportHeight.dp)
                                .padding(start = 2.dp, end = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(thumbHeight.dp)
                                    .offset(y = thumbOffset.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onExtensionsChanged(tempEnabledExtensions)
                onDismiss()
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SteppedIntSliderDialog(
    title: String,
    unitLabel: String,
    range: IntRange,
    step: Int,
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val safeStep = step.coerceAtLeast(1)
    val min = range.first
    val max = range.last
    val stepCount = ((max - min) / safeStep).coerceAtLeast(1)
    var value by remember { mutableIntStateOf(currentValue.coerceIn(min, max)) }

    fun normalize(raw: Int): Int {
        val snapped = min + (((raw - min).toFloat() / safeStep).roundToInt() * safeStep)
        return snapped.coerceIn(min, max)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (unitLabel == "%") "$value$unitLabel" else "$value $unitLabel",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { value = normalize(value - safeStep) },
                        enabled = value > min,
                        modifier = Modifier.width(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease"
                        )
                    }
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { raw ->
                            value = normalize(raw.roundToInt())
                        },
                        valueRange = min.toFloat()..max.toFloat(),
                        steps = (stepCount - 1).coerceAtLeast(0),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { value = normalize(value + safeStep) },
                        enabled = value < max,
                        modifier = Modifier.width(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase"
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$min", style = MaterialTheme.typography.labelSmall)
                    Text("$max", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text("Save")
            }
        }
    )
}

@Composable
private fun VisualizationOscColorModeDialog(
    title: String,
    options: List<VisualizationOscColorMode>,
    selectedMode: VisualizationOscColorMode,
    onDismiss: () -> Unit,
    onSelect: (VisualizationOscColorMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == selectedMode,
                            onClick = { onSelect(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(mode.label)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun VisualizationRgbColorPickerDialog(
    title: String,
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var red by remember { mutableIntStateOf((initialArgb shr 16) and 0xFF) }
    var green by remember { mutableIntStateOf((initialArgb shr 8) and 0xFF) }
    var blue by remember { mutableIntStateOf(initialArgb and 0xFF) }

    fun asArgbInt(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    val previewColor = Color(asArgbInt(red, green, blue))
    val hex = String.format(Locale.US, "#%02X%02X%02X", red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(previewColor)
                )
                Text(text = hex, style = MaterialTheme.typography.titleMedium)

                Text("Red: $red", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = red.toFloat(),
                    onValueChange = { red = it.roundToInt().coerceIn(0, 255) },
                    valueRange = 0f..255f
                )
                Text("Green: $green", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = green.toFloat(),
                    onValueChange = { green = it.roundToInt().coerceIn(0, 255) },
                    valueRange = 0f..255f
                )
                Text("Blue: $blue", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = blue.toFloat(),
                    onValueChange = { blue = it.roundToInt().coerceIn(0, 255) },
                    valueRange = 0f..255f
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(asArgbInt(red, green, blue)) }) {
                Text("Save")
            }
        }
    )
}

@Composable
private fun PriorityPickerDialog(
    currentPriority: Int,
    onPrioritySelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var tempPriority by remember { mutableIntStateOf(currentPriority) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set plugin priority") },
        text = {
            Column {
                Text(
                    text = "Lower value means higher priority. Plugins are tried in ascending order when multiple plugins support the same extension.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Priority: $tempPriority",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = tempPriority.toFloat(),
                    onValueChange = { tempPriority = it.roundToInt() },
                    valueRange = 0f..100f,
                    steps = 19 // 0, 5, 10, ..., 100
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0 (Highest)", style = MaterialTheme.typography.labelSmall)
                    Text("100 (Lowest)", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPrioritySelected(tempPriority) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
