package com.flopster101.siliconplayer

import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape

private const val SETTINGS_PAGE_NAV_DURATION_MS = 300

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
    onOpenVisualizationAdvanced: () -> Unit,
    onOpenVisualizationAdvancedChannelScope: () -> Unit,
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
    onPluginPriorityOrderChanged: (List<String>) -> Unit,
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
    playerArtworkCornerRadiusDp: Int,
    onPlayerArtworkCornerRadiusDpChanged: (Int) -> Unit,
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
    visualizationShowDebugInfo: Boolean,
    onVisualizationShowDebugInfoChanged: (Boolean) -> Unit,
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
    onResetVisualizationChannelScopeSettings: () -> Unit,
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
    sidPlayFpSampleRateHz: Int,
    onSidPlayFpSampleRateChanged: (Int) -> Unit,
    lazyUsf2SampleRateHz: Int,
    onLazyUsf2SampleRateChanged: (Int) -> Unit,
    lazyUsf2UseHleAudio: Boolean,
    onLazyUsf2UseHleAudioChanged: (Boolean) -> Unit,
    sidPlayFpBackend: Int,
    onSidPlayFpBackendChanged: (Int) -> Unit,
    sidPlayFpClockMode: Int,
    onSidPlayFpClockModeChanged: (Int) -> Unit,
    sidPlayFpSidModelMode: Int,
    onSidPlayFpSidModelModeChanged: (Int) -> Unit,
    sidPlayFpFilter6581Enabled: Boolean,
    onSidPlayFpFilter6581EnabledChanged: (Boolean) -> Unit,
    sidPlayFpFilter8580Enabled: Boolean,
    onSidPlayFpFilter8580EnabledChanged: (Boolean) -> Unit,
    sidPlayFpDigiBoost8580: Boolean,
    onSidPlayFpDigiBoost8580Changed: (Boolean) -> Unit,
    sidPlayFpFilterCurve6581Percent: Int,
    onSidPlayFpFilterCurve6581PercentChanged: (Int) -> Unit,
    sidPlayFpFilterRange6581Percent: Int,
    onSidPlayFpFilterRange6581PercentChanged: (Int) -> Unit,
    sidPlayFpFilterCurve8580Percent: Int,
    onSidPlayFpFilterCurve8580PercentChanged: (Int) -> Unit,
    sidPlayFpReSidFpFastSampling: Boolean,
    onSidPlayFpReSidFpFastSamplingChanged: (Boolean) -> Unit,
    sidPlayFpReSidFpCombinedWaveformsStrength: Int,
    onSidPlayFpReSidFpCombinedWaveformsStrengthChanged: (Int) -> Unit,
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
        SettingsRoute.VisualizationAdvanced -> "Advanced visualizations"
        SettingsRoute.VisualizationAdvancedChannelScope -> "Channel scope settings"
        SettingsRoute.Misc -> "Misc settings"
        SettingsRoute.Ui -> "UI settings"
        SettingsRoute.About -> "About"
    }

    SettingsScaffoldShell(onBack = onBack) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SettingsSecondaryHeader(
                secondaryTitle = secondaryTitle,
                route = route,
                pluginPriorityEditMode = pluginPriorityEditMode,
                onTogglePluginPriorityEditMode = { pluginPriorityEditMode = !pluginPriorityEditMode },
                selectedPluginName = selectedPluginName,
                onRequestPluginReset = { pluginName -> pendingPluginResetName = pluginName },
                onResetVisualizationBarsSettings = onResetVisualizationBarsSettings,
                onResetVisualizationOscilloscopeSettings = onResetVisualizationOscilloscopeSettings,
                onResetVisualizationVuSettings = onResetVisualizationVuSettings,
                onResetVisualizationChannelScopeSettings = onResetVisualizationChannelScopeSettings
            )
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
                val routeContent: @Composable () -> Unit = {
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
                        RootRouteContent(
                            onOpenAudioPlugins = onOpenAudioPlugins,
                            onOpenGeneralAudio = onOpenGeneralAudio,
                            onOpenPlayer = onOpenPlayer,
                            onOpenHome = onOpenHome,
                            onOpenVisualization = onOpenVisualization,
                            onOpenUrlCache = onOpenUrlCache,
                            onOpenMisc = onOpenMisc,
                            onOpenUi = onOpenUi,
                            onOpenAbout = onOpenAbout,
                            onRequestClearAllSettings = {
                                pendingResetAction = SettingsResetAction.ClearAllSettings
                            }
                        )
                    }
                    SettingsRoute.AudioPlugins -> {
                        AudioPluginsRouteContent(
                            pluginPriorityEditMode = pluginPriorityEditMode,
                            onPluginSelected = onPluginSelected,
                            onPluginEnabledChanged = onPluginEnabledChanged,
                            onPluginPriorityOrderChanged = onPluginPriorityOrderChanged,
                            onRequestClearPluginSettings = {
                                pendingResetAction = SettingsResetAction.ClearPluginSettings
                            }
                        )
                    }
                    SettingsRoute.PluginDetail -> {
                        PluginDetailRouteContent(
                            selectedPluginName = selectedPluginName,
                            onPluginPriorityChanged = onPluginPriorityChanged,
                            onPluginExtensionsChanged = onPluginExtensionsChanged,
                            ffmpegSampleRateHz = ffmpegSampleRateHz,
                            onFfmpegSampleRateChanged = onFfmpegSampleRateChanged,
                            ffmpegCapabilities = ffmpegCapabilities,
                            openMptSampleRateHz = openMptSampleRateHz,
                            onOpenMptSampleRateChanged = onOpenMptSampleRateChanged,
                            openMptCapabilities = openMptCapabilities,
                            vgmPlaySampleRateHz = vgmPlaySampleRateHz,
                            onVgmPlaySampleRateChanged = onVgmPlaySampleRateChanged,
                            vgmPlayCapabilities = vgmPlayCapabilities,
                            gmeSampleRateHz = gmeSampleRateHz,
                            onGmeSampleRateChanged = onGmeSampleRateChanged,
                            sidPlayFpSampleRateHz = sidPlayFpSampleRateHz,
                            onSidPlayFpSampleRateChanged = onSidPlayFpSampleRateChanged,
                            lazyUsf2SampleRateHz = lazyUsf2SampleRateHz,
                            onLazyUsf2SampleRateChanged = onLazyUsf2SampleRateChanged,
                            openMptStereoSeparationPercent = openMptStereoSeparationPercent,
                            onOpenMptStereoSeparationPercentChanged = onOpenMptStereoSeparationPercentChanged,
                            openMptStereoSeparationAmigaPercent = openMptStereoSeparationAmigaPercent,
                            onOpenMptStereoSeparationAmigaPercentChanged = onOpenMptStereoSeparationAmigaPercentChanged,
                            openMptInterpolationFilterLength = openMptInterpolationFilterLength,
                            onOpenMptInterpolationFilterLengthChanged = onOpenMptInterpolationFilterLengthChanged,
                            openMptAmigaResamplerMode = openMptAmigaResamplerMode,
                            onOpenMptAmigaResamplerModeChanged = onOpenMptAmigaResamplerModeChanged,
                            openMptAmigaResamplerApplyAllModules = openMptAmigaResamplerApplyAllModules,
                            onOpenMptAmigaResamplerApplyAllModulesChanged = onOpenMptAmigaResamplerApplyAllModulesChanged,
                            openMptVolumeRampingStrength = openMptVolumeRampingStrength,
                            onOpenMptVolumeRampingStrengthChanged = onOpenMptVolumeRampingStrengthChanged,
                            openMptFt2XmVolumeRamping = openMptFt2XmVolumeRamping,
                            onOpenMptFt2XmVolumeRampingChanged = onOpenMptFt2XmVolumeRampingChanged,
                            openMptMasterGainMilliBel = openMptMasterGainMilliBel,
                            onOpenMptMasterGainMilliBelChanged = onOpenMptMasterGainMilliBelChanged,
                            openMptSurroundEnabled = openMptSurroundEnabled,
                            onOpenMptSurroundEnabledChanged = onOpenMptSurroundEnabledChanged,
                            vgmPlayLoopCount = vgmPlayLoopCount,
                            onVgmPlayLoopCountChanged = onVgmPlayLoopCountChanged,
                            vgmPlayAllowNonLoopingLoop = vgmPlayAllowNonLoopingLoop,
                            onVgmPlayAllowNonLoopingLoopChanged = onVgmPlayAllowNonLoopingLoopChanged,
                            vgmPlayVsyncRate = vgmPlayVsyncRate,
                            onVgmPlayVsyncRateChanged = onVgmPlayVsyncRateChanged,
                            vgmPlayResampleMode = vgmPlayResampleMode,
                            onVgmPlayResampleModeChanged = onVgmPlayResampleModeChanged,
                            vgmPlayChipSampleMode = vgmPlayChipSampleMode,
                            onVgmPlayChipSampleModeChanged = onVgmPlayChipSampleModeChanged,
                            vgmPlayChipSampleRate = vgmPlayChipSampleRate,
                            onVgmPlayChipSampleRateChanged = onVgmPlayChipSampleRateChanged,
                            onOpenVgmPlayChipSettings = onOpenVgmPlayChipSettings,
                            gmeTempoPercent = gmeTempoPercent,
                            onGmeTempoPercentChanged = onGmeTempoPercentChanged,
                            gmeStereoSeparationPercent = gmeStereoSeparationPercent,
                            onGmeStereoSeparationPercentChanged = onGmeStereoSeparationPercentChanged,
                            gmeEchoEnabled = gmeEchoEnabled,
                            onGmeEchoEnabledChanged = onGmeEchoEnabledChanged,
                            gmeAccuracyEnabled = gmeAccuracyEnabled,
                            onGmeAccuracyEnabledChanged = onGmeAccuracyEnabledChanged,
                            gmeEqTrebleDecibel = gmeEqTrebleDecibel,
                            onGmeEqTrebleDecibelChanged = onGmeEqTrebleDecibelChanged,
                            gmeEqBassHz = gmeEqBassHz,
                            onGmeEqBassHzChanged = onGmeEqBassHzChanged,
                            gmeSpcUseBuiltInFade = gmeSpcUseBuiltInFade,
                            onGmeSpcUseBuiltInFadeChanged = onGmeSpcUseBuiltInFadeChanged,
                            gmeSpcInterpolation = gmeSpcInterpolation,
                            onGmeSpcInterpolationChanged = onGmeSpcInterpolationChanged,
                            gmeSpcUseNativeSampleRate = gmeSpcUseNativeSampleRate,
                            onGmeSpcUseNativeSampleRateChanged = onGmeSpcUseNativeSampleRateChanged,
                            sidPlayFpBackend = sidPlayFpBackend,
                            onSidPlayFpBackendChanged = onSidPlayFpBackendChanged,
                            sidPlayFpClockMode = sidPlayFpClockMode,
                            onSidPlayFpClockModeChanged = onSidPlayFpClockModeChanged,
                            sidPlayFpSidModelMode = sidPlayFpSidModelMode,
                            onSidPlayFpSidModelModeChanged = onSidPlayFpSidModelModeChanged,
                            sidPlayFpFilter6581Enabled = sidPlayFpFilter6581Enabled,
                            onSidPlayFpFilter6581EnabledChanged = onSidPlayFpFilter6581EnabledChanged,
                            sidPlayFpFilter8580Enabled = sidPlayFpFilter8580Enabled,
                            onSidPlayFpFilter8580EnabledChanged = onSidPlayFpFilter8580EnabledChanged,
                            sidPlayFpDigiBoost8580 = sidPlayFpDigiBoost8580,
                            onSidPlayFpDigiBoost8580Changed = onSidPlayFpDigiBoost8580Changed,
                            sidPlayFpFilterCurve6581Percent = sidPlayFpFilterCurve6581Percent,
                            onSidPlayFpFilterCurve6581PercentChanged = onSidPlayFpFilterCurve6581PercentChanged,
                            sidPlayFpFilterRange6581Percent = sidPlayFpFilterRange6581Percent,
                            onSidPlayFpFilterRange6581PercentChanged = onSidPlayFpFilterRange6581PercentChanged,
                            sidPlayFpFilterCurve8580Percent = sidPlayFpFilterCurve8580Percent,
                            onSidPlayFpFilterCurve8580PercentChanged = onSidPlayFpFilterCurve8580PercentChanged,
                            sidPlayFpReSidFpFastSampling = sidPlayFpReSidFpFastSampling,
                            onSidPlayFpReSidFpFastSamplingChanged = onSidPlayFpReSidFpFastSamplingChanged,
                            sidPlayFpReSidFpCombinedWaveformsStrength = sidPlayFpReSidFpCombinedWaveformsStrength,
                            onSidPlayFpReSidFpCombinedWaveformsStrengthChanged = onSidPlayFpReSidFpCombinedWaveformsStrengthChanged,
                            lazyUsf2UseHleAudio = lazyUsf2UseHleAudio,
                            onLazyUsf2UseHleAudioChanged = onLazyUsf2UseHleAudioChanged
                        )
                    }
                    SettingsRoute.PluginVgmPlayChipSettings -> {
                        PluginVgmPlayChipSettingsRouteContent(
                            vgmPlayChipCoreSelections = vgmPlayChipCoreSelections,
                            onVgmPlayChipCoreChanged = onVgmPlayChipCoreChanged
                        )
                    }
                    SettingsRoute.PluginFfmpeg -> PluginSampleRateRouteContent(
                        title = "Render sample rate",
                        description = "Preferred internal render sample rate for this plugin. Audio is resampled to the active output stream rate.",
                        selectedHz = ffmpegSampleRateHz,
                        enabled = supportsCustomSampleRate(ffmpegCapabilities),
                        onSelected = onFfmpegSampleRateChanged
                    )
                    SettingsRoute.PluginVgmPlay -> PluginSampleRateRouteContent(
                        title = "Render sample rate",
                        description = "Preferred internal render sample rate for this plugin. Audio is resampled to the active output stream rate.",
                        selectedHz = vgmPlaySampleRateHz,
                        enabled = supportsCustomSampleRate(vgmPlayCapabilities),
                        onSelected = onVgmPlaySampleRateChanged
                    )
                    SettingsRoute.PluginOpenMpt -> {
                        PluginOpenMptRouteContent(
                            openMptSampleRateHz = openMptSampleRateHz,
                            openMptCapabilities = openMptCapabilities,
                            openMptStereoSeparationPercent = openMptStereoSeparationPercent,
                            onOpenMptStereoSeparationPercentChanged = onOpenMptStereoSeparationPercentChanged,
                            openMptStereoSeparationAmigaPercent = openMptStereoSeparationAmigaPercent,
                            onOpenMptStereoSeparationAmigaPercentChanged = onOpenMptStereoSeparationAmigaPercentChanged,
                            openMptInterpolationFilterLength = openMptInterpolationFilterLength,
                            onOpenMptInterpolationFilterLengthChanged = onOpenMptInterpolationFilterLengthChanged,
                            openMptAmigaResamplerMode = openMptAmigaResamplerMode,
                            onOpenMptAmigaResamplerModeChanged = onOpenMptAmigaResamplerModeChanged,
                            openMptAmigaResamplerApplyAllModules = openMptAmigaResamplerApplyAllModules,
                            onOpenMptAmigaResamplerApplyAllModulesChanged = onOpenMptAmigaResamplerApplyAllModulesChanged,
                            openMptVolumeRampingStrength = openMptVolumeRampingStrength,
                            onOpenMptVolumeRampingStrengthChanged = onOpenMptVolumeRampingStrengthChanged,
                            openMptFt2XmVolumeRamping = openMptFt2XmVolumeRamping,
                            onOpenMptFt2XmVolumeRampingChanged = onOpenMptFt2XmVolumeRampingChanged,
                            openMptMasterGainMilliBel = openMptMasterGainMilliBel,
                            onOpenMptMasterGainMilliBelChanged = onOpenMptMasterGainMilliBelChanged,
                            openMptSurroundEnabled = openMptSurroundEnabled,
                            onOpenMptSurroundEnabledChanged = onOpenMptSurroundEnabledChanged,
                            onOpenMptSampleRateChanged = onOpenMptSampleRateChanged
                        )
                    }
                    SettingsRoute.GeneralAudio -> {
                        GeneralAudioRouteContent(
                            respondHeadphoneMediaButtons = respondHeadphoneMediaButtons,
                            onRespondHeadphoneMediaButtonsChanged = onRespondHeadphoneMediaButtonsChanged,
                            pauseOnHeadphoneDisconnect = pauseOnHeadphoneDisconnect,
                            onPauseOnHeadphoneDisconnectChanged = onPauseOnHeadphoneDisconnectChanged,
                            audioFocusInterrupt = audioFocusInterrupt,
                            onAudioFocusInterruptChanged = onAudioFocusInterruptChanged,
                            audioDucking = audioDucking,
                            onAudioDuckingChanged = onAudioDuckingChanged,
                            onOpenAudioEffects = onOpenAudioEffects,
                            onClearAllAudioParameters = onClearAllAudioParameters,
                            onClearPluginAudioParameters = onClearPluginAudioParameters,
                            onClearSongAudioParameters = onClearSongAudioParameters,
                            audioBackendPreference = audioBackendPreference,
                            onAudioBackendPreferenceChanged = onAudioBackendPreferenceChanged,
                            audioPerformanceMode = audioPerformanceMode,
                            onAudioPerformanceModeChanged = onAudioPerformanceModeChanged,
                            audioBufferPreset = audioBufferPreset,
                            onAudioBufferPresetChanged = onAudioBufferPresetChanged,
                            audioResamplerPreference = audioResamplerPreference,
                            onAudioResamplerPreferenceChanged = onAudioResamplerPreferenceChanged,
                            audioAllowBackendFallback = audioAllowBackendFallback,
                            onAudioAllowBackendFallbackChanged = onAudioAllowBackendFallbackChanged
                        )
                    }
                    SettingsRoute.Home -> {
                        HomeRouteContent(
                            recentFoldersLimit = recentFoldersLimit,
                            onRecentFoldersLimitChanged = onRecentFoldersLimitChanged,
                            recentFilesLimit = recentFilesLimit,
                            onRecentFilesLimitChanged = onRecentFilesLimitChanged
                        )
                    }
                    SettingsRoute.Player -> {
                        PlayerRouteContent(
                            unknownTrackDurationSeconds = unknownTrackDurationSeconds,
                            onUnknownTrackDurationSecondsChanged = onUnknownTrackDurationSecondsChanged,
                            endFadeDurationMs = endFadeDurationMs,
                            onEndFadeDurationMsChanged = onEndFadeDurationMsChanged,
                            endFadeCurve = endFadeCurve,
                            onEndFadeCurveChanged = onEndFadeCurveChanged,
                            endFadeApplyToAllTracks = endFadeApplyToAllTracks,
                            onEndFadeApplyToAllTracksChanged = onEndFadeApplyToAllTracksChanged,
                            autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                            onAutoPlayOnTrackSelectChanged = onAutoPlayOnTrackSelectChanged,
                            openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                            onOpenPlayerOnTrackSelectChanged = onOpenPlayerOnTrackSelectChanged,
                            autoPlayNextTrackOnEnd = autoPlayNextTrackOnEnd,
                            onAutoPlayNextTrackOnEndChanged = onAutoPlayNextTrackOnEndChanged,
                            previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                            onPreviousRestartsAfterThresholdChanged = onPreviousRestartsAfterThresholdChanged,
                            openPlayerFromNotification = openPlayerFromNotification,
                            onOpenPlayerFromNotificationChanged = onOpenPlayerFromNotificationChanged,
                            persistRepeatMode = persistRepeatMode,
                            onPersistRepeatModeChanged = onPersistRepeatModeChanged,
                            keepScreenOn = keepScreenOn,
                            onKeepScreenOnChanged = onKeepScreenOnChanged,
                            playerArtworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                            onPlayerArtworkCornerRadiusDpChanged = onPlayerArtworkCornerRadiusDpChanged,
                            filenameDisplayMode = filenameDisplayMode,
                            onFilenameDisplayModeChanged = onFilenameDisplayModeChanged,
                            filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                            onFilenameOnlyWhenTitleMissingChanged = onFilenameOnlyWhenTitleMissingChanged
                        )
                    }
                    SettingsRoute.Visualization -> {
                        VisualizationRouteContent(
                            visualizationMode = visualizationMode,
                            onVisualizationModeChanged = onVisualizationModeChanged,
                            enabledVisualizationModes = enabledVisualizationModes,
                            onEnabledVisualizationModesChanged = onEnabledVisualizationModesChanged,
                            visualizationShowDebugInfo = visualizationShowDebugInfo,
                            onVisualizationShowDebugInfoChanged = onVisualizationShowDebugInfoChanged,
                            onOpenVisualizationBasic = onOpenVisualizationBasic,
                            onOpenVisualizationAdvanced = onOpenVisualizationAdvanced
                        )
                    }
                    SettingsRoute.VisualizationBasic -> {
                        VisualizationBasicRouteContent(
                            onOpenVisualizationBasicBars = onOpenVisualizationBasicBars,
                            onOpenVisualizationBasicOscilloscope = onOpenVisualizationBasicOscilloscope,
                            onOpenVisualizationBasicVuMeters = onOpenVisualizationBasicVuMeters
                        )
                    }
                    SettingsRoute.VisualizationAdvanced -> {
                        VisualizationAdvancedRouteContent(
                            onOpenVisualizationAdvancedChannelScope = onOpenVisualizationAdvancedChannelScope
                        )
                    }
                    SettingsRoute.VisualizationBasicBars -> {
                        VisualizationBasicBarsRouteContent(
                            visualizationBarCount = visualizationBarCount,
                            onVisualizationBarCountChanged = onVisualizationBarCountChanged,
                            visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                            onVisualizationBarSmoothingPercentChanged = onVisualizationBarSmoothingPercentChanged,
                            visualizationBarRoundnessDp = visualizationBarRoundnessDp,
                            onVisualizationBarRoundnessDpChanged = onVisualizationBarRoundnessDpChanged,
                            visualizationBarOverlayArtwork = visualizationBarOverlayArtwork,
                            onVisualizationBarOverlayArtworkChanged = onVisualizationBarOverlayArtworkChanged,
                            visualizationBarUseThemeColor = visualizationBarUseThemeColor,
                            onVisualizationBarUseThemeColorChanged = onVisualizationBarUseThemeColorChanged
                        )
                    }
                    SettingsRoute.VisualizationBasicOscilloscope -> {
                        VisualizationBasicOscilloscopeRouteContent(
                            visualizationOscStereo = visualizationOscStereo,
                            onVisualizationOscStereoChanged = onVisualizationOscStereoChanged
                        )
                    }
                    SettingsRoute.VisualizationBasicVuMeters -> {
                        VisualizationBasicVuMetersRouteContent(
                            visualizationVuAnchor = visualizationVuAnchor,
                            onVisualizationVuAnchorChanged = onVisualizationVuAnchorChanged,
                            visualizationVuUseThemeColor = visualizationVuUseThemeColor,
                            onVisualizationVuUseThemeColorChanged = onVisualizationVuUseThemeColorChanged,
                            visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                            onVisualizationVuSmoothingPercentChanged = onVisualizationVuSmoothingPercentChanged
                        )
                    }
                    SettingsRoute.VisualizationAdvancedChannelScope -> {
                        VisualizationAdvancedChannelScopeRouteContent()
                    }
                    SettingsRoute.Misc -> {
                        MiscRouteContent(
                            rememberBrowserLocation = rememberBrowserLocation,
                            onRememberBrowserLocationChanged = onRememberBrowserLocationChanged,
                            onClearRecentHistory = onClearRecentHistory
                        )
                    }

                    SettingsRoute.UrlCache -> {
                        UrlCacheRouteContent(
                            urlCacheClearOnLaunch = urlCacheClearOnLaunch,
                            onUrlCacheClearOnLaunchChanged = onUrlCacheClearOnLaunchChanged,
                            urlCacheMaxTracks = urlCacheMaxTracks,
                            onUrlCacheMaxTracksChanged = onUrlCacheMaxTracksChanged,
                            urlCacheMaxBytes = urlCacheMaxBytes,
                            onUrlCacheMaxBytesChanged = onUrlCacheMaxBytesChanged,
                            onOpenCacheManager = onOpenCacheManager,
                            onClearUrlCacheNow = onClearUrlCacheNow
                        )
                    }
                    SettingsRoute.CacheManager -> {
                        CacheManagerSettingsRouteContent(
                            route = route,
                            cachedSourceFiles = cachedSourceFiles,
                            onRefreshCachedSourceFiles = onRefreshCachedSourceFiles,
                            onDeleteCachedSourceFiles = onDeleteCachedSourceFiles,
                            onExportCachedSourceFiles = onExportCachedSourceFiles
                        )
                    }
                    SettingsRoute.Ui -> UiRouteContent(
                        themeMode = themeMode,
                        onThemeModeChanged = onThemeModeChanged
                    )
                        SettingsRoute.About -> AboutSettingsBody()
                        }
                        if (bottomContentPadding > 0.dp) {
                            Spacer(modifier = Modifier.height(bottomContentPadding))
                        }
                    }
                }
                routeContent()
            }
        }
    }
    SettingsResetDialogHost(
        pendingResetAction = pendingResetAction,
        onDismiss = { pendingResetAction = null },
        onConfirmClearAllSettings = onClearAllSettings,
        onConfirmClearAllPluginSettings = onClearAllPluginSettings
    )

    PluginResetDialogHost(
        pendingPluginResetName = pendingPluginResetName,
        onDismiss = { pendingPluginResetName = null },
        onConfirmResetPluginSettings = onResetPluginSettings
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsScaffoldShell(
    onBack: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
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
        },
        content = content
    )
}

@Composable
private fun SettingsSecondaryHeader(
    secondaryTitle: String?,
    route: SettingsRoute,
    pluginPriorityEditMode: Boolean,
    onTogglePluginPriorityEditMode: () -> Unit,
    selectedPluginName: String?,
    onRequestPluginReset: (String) -> Unit,
    onResetVisualizationBarsSettings: () -> Unit,
    onResetVisualizationOscilloscopeSettings: () -> Unit,
    onResetVisualizationVuSettings: () -> Unit,
    onResetVisualizationChannelScopeSettings: () -> Unit
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
                                onClick = onTogglePluginPriorityEditMode,
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
                                onClick = { onRequestPluginReset(selectedPluginName) },
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
                        route == SettingsRoute.VisualizationBasicVuMeters ||
                        route == SettingsRoute.VisualizationAdvancedChannelScope
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
                                        SettingsRoute.VisualizationAdvancedChannelScope ->
                                            onResetVisualizationChannelScopeSettings()
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
}
