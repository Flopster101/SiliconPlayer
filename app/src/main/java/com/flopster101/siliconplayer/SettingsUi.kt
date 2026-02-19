package com.flopster101.siliconplayer

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.clickable
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

internal data class SettingsScreenState(
    val selectedPluginName: String?,
    val autoPlayOnTrackSelect: Boolean,
    val openPlayerOnTrackSelect: Boolean,
    val autoPlayNextTrackOnEnd: Boolean,
    val previousRestartsAfterThreshold: Boolean,
    val respondHeadphoneMediaButtons: Boolean,
    val pauseOnHeadphoneDisconnect: Boolean,
    val audioFocusInterrupt: Boolean,
    val audioDucking: Boolean,
    val audioBackendPreference: AudioBackendPreference,
    val audioPerformanceMode: AudioPerformanceMode,
    val audioBufferPreset: AudioBufferPreset,
    val audioResamplerPreference: AudioResamplerPreference,
    val audioAllowBackendFallback: Boolean,
    val openPlayerFromNotification: Boolean,
    val persistRepeatMode: Boolean,
    val themeMode: ThemeMode,
    val rememberBrowserLocation: Boolean,
    val recentFoldersLimit: Int,
    val recentFilesLimit: Int,
    val urlCacheClearOnLaunch: Boolean,
    val urlCacheMaxTracks: Int,
    val urlCacheMaxBytes: Long,
    val cachedSourceFiles: List<CachedSourceFile>,
    val keepScreenOn: Boolean,
    val playerArtworkCornerRadiusDp: Int,
    val filenameDisplayMode: FilenameDisplayMode,
    val filenameOnlyWhenTitleMissing: Boolean,
    val unknownTrackDurationSeconds: Int,
    val endFadeApplyToAllTracks: Boolean,
    val endFadeDurationMs: Int,
    val endFadeCurve: EndFadeCurve,
    val visualizationMode: VisualizationMode,
    val enabledVisualizationModes: Set<VisualizationMode>,
    val visualizationShowDebugInfo: Boolean,
    val visualizationBarCount: Int,
    val visualizationBarSmoothingPercent: Int,
    val visualizationBarRoundnessDp: Int,
    val visualizationBarOverlayArtwork: Boolean,
    val visualizationBarUseThemeColor: Boolean,
    val visualizationBarRenderBackend: VisualizationRenderBackend,
    val visualizationOscStereo: Boolean,
    val visualizationVuAnchor: VisualizationVuAnchor,
    val visualizationVuUseThemeColor: Boolean,
    val visualizationVuSmoothingPercent: Int,
    val visualizationVuRenderBackend: VisualizationRenderBackend,
    val ffmpegSampleRateHz: Int,
    val ffmpegCapabilities: Int,
    val openMptSampleRateHz: Int,
    val openMptCapabilities: Int,
    val vgmPlaySampleRateHz: Int,
    val vgmPlayCapabilities: Int,
    val gmeSampleRateHz: Int,
    val sidPlayFpSampleRateHz: Int,
    val lazyUsf2SampleRateHz: Int,
    val lazyUsf2UseHleAudio: Boolean,
    val vio2sfInterpolationQuality: Int,
    val sidPlayFpBackend: Int,
    val sidPlayFpClockMode: Int,
    val sidPlayFpSidModelMode: Int,
    val sidPlayFpFilter6581Enabled: Boolean,
    val sidPlayFpFilter8580Enabled: Boolean,
    val sidPlayFpDigiBoost8580: Boolean,
    val sidPlayFpFilterCurve6581Percent: Int,
    val sidPlayFpFilterRange6581Percent: Int,
    val sidPlayFpFilterCurve8580Percent: Int,
    val sidPlayFpReSidFpFastSampling: Boolean,
    val sidPlayFpReSidFpCombinedWaveformsStrength: Int,
    val gmeTempoPercent: Int,
    val gmeStereoSeparationPercent: Int,
    val gmeEchoEnabled: Boolean,
    val gmeAccuracyEnabled: Boolean,
    val gmeEqTrebleDecibel: Int,
    val gmeEqBassHz: Int,
    val gmeSpcUseBuiltInFade: Boolean,
    val gmeSpcInterpolation: Int,
    val gmeSpcUseNativeSampleRate: Boolean,
    val vgmPlayLoopCount: Int,
    val vgmPlayAllowNonLoopingLoop: Boolean,
    val vgmPlayVsyncRate: Int,
    val vgmPlayResampleMode: Int,
    val vgmPlayChipSampleMode: Int,
    val vgmPlayChipSampleRate: Int,
    val vgmPlayChipCoreSelections: Map<String, Int>,
    val openMptStereoSeparationPercent: Int,
    val openMptStereoSeparationAmigaPercent: Int,
    val openMptInterpolationFilterLength: Int,
    val openMptAmigaResamplerMode: Int,
    val openMptAmigaResamplerApplyAllModules: Boolean,
    val openMptVolumeRampingStrength: Int,
    val openMptFt2XmVolumeRamping: Boolean,
    val openMptMasterGainMilliBel: Int,
    val openMptSurroundEnabled: Boolean
)

internal data class SettingsScreenActions(
    val onBack: () -> Unit,
    val onOpenAudioPlugins: () -> Unit,
    val onOpenGeneralAudio: () -> Unit,
    val onOpenHome: () -> Unit,
    val onOpenAudioEffects: () -> Unit,
    val onClearAllAudioParameters: () -> Unit,
    val onClearPluginAudioParameters: () -> Unit,
    val onClearSongAudioParameters: () -> Unit,
    val onOpenPlayer: () -> Unit,
    val onOpenVisualization: () -> Unit,
    val onOpenVisualizationBasic: () -> Unit,
    val onOpenVisualizationBasicBars: () -> Unit,
    val onOpenVisualizationBasicOscilloscope: () -> Unit,
    val onOpenVisualizationBasicVuMeters: () -> Unit,
    val onOpenVisualizationAdvanced: () -> Unit,
    val onOpenVisualizationAdvancedChannelScope: () -> Unit,
    val onOpenMisc: () -> Unit,
    val onOpenUrlCache: () -> Unit,
    val onOpenCacheManager: () -> Unit,
    val onOpenUi: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenVgmPlayChipSettings: () -> Unit,
    val onPluginSelected: (String) -> Unit,
    val onPluginEnabledChanged: (String, Boolean) -> Unit,
    val onPluginPriorityChanged: (String, Int) -> Unit,
    val onPluginPriorityOrderChanged: (List<String>) -> Unit,
    val onPluginExtensionsChanged: (String, Array<String>) -> Unit,
    val onAutoPlayOnTrackSelectChanged: (Boolean) -> Unit,
    val onOpenPlayerOnTrackSelectChanged: (Boolean) -> Unit,
    val onAutoPlayNextTrackOnEndChanged: (Boolean) -> Unit,
    val onPreviousRestartsAfterThresholdChanged: (Boolean) -> Unit,
    val onRespondHeadphoneMediaButtonsChanged: (Boolean) -> Unit,
    val onPauseOnHeadphoneDisconnectChanged: (Boolean) -> Unit,
    val onAudioFocusInterruptChanged: (Boolean) -> Unit,
    val onAudioDuckingChanged: (Boolean) -> Unit,
    val onAudioBackendPreferenceChanged: (AudioBackendPreference) -> Unit,
    val onAudioPerformanceModeChanged: (AudioPerformanceMode) -> Unit,
    val onAudioBufferPresetChanged: (AudioBufferPreset) -> Unit,
    val onAudioResamplerPreferenceChanged: (AudioResamplerPreference) -> Unit,
    val onAudioAllowBackendFallbackChanged: (Boolean) -> Unit,
    val onOpenPlayerFromNotificationChanged: (Boolean) -> Unit,
    val onPersistRepeatModeChanged: (Boolean) -> Unit,
    val onThemeModeChanged: (ThemeMode) -> Unit,
    val onRememberBrowserLocationChanged: (Boolean) -> Unit,
    val onRecentFoldersLimitChanged: (Int) -> Unit,
    val onRecentFilesLimitChanged: (Int) -> Unit,
    val onUrlCacheClearOnLaunchChanged: (Boolean) -> Unit,
    val onUrlCacheMaxTracksChanged: (Int) -> Unit,
    val onUrlCacheMaxBytesChanged: (Long) -> Unit,
    val onClearUrlCacheNow: () -> Unit,
    val onRefreshCachedSourceFiles: () -> Unit,
    val onDeleteCachedSourceFiles: (List<String>) -> Unit,
    val onExportCachedSourceFiles: (List<String>) -> Unit,
    val onKeepScreenOnChanged: (Boolean) -> Unit,
    val onPlayerArtworkCornerRadiusDpChanged: (Int) -> Unit,
    val onFilenameDisplayModeChanged: (FilenameDisplayMode) -> Unit,
    val onFilenameOnlyWhenTitleMissingChanged: (Boolean) -> Unit,
    val onUnknownTrackDurationSecondsChanged: (Int) -> Unit,
    val onEndFadeApplyToAllTracksChanged: (Boolean) -> Unit,
    val onEndFadeDurationMsChanged: (Int) -> Unit,
    val onEndFadeCurveChanged: (EndFadeCurve) -> Unit,
    val onVisualizationModeChanged: (VisualizationMode) -> Unit,
    val onEnabledVisualizationModesChanged: (Set<VisualizationMode>) -> Unit,
    val onVisualizationShowDebugInfoChanged: (Boolean) -> Unit,
    val onVisualizationBarCountChanged: (Int) -> Unit,
    val onVisualizationBarSmoothingPercentChanged: (Int) -> Unit,
    val onVisualizationBarRoundnessDpChanged: (Int) -> Unit,
    val onVisualizationBarOverlayArtworkChanged: (Boolean) -> Unit,
    val onVisualizationBarUseThemeColorChanged: (Boolean) -> Unit,
    val onVisualizationBarRenderBackendChanged: (VisualizationRenderBackend) -> Unit,
    val onVisualizationOscStereoChanged: (Boolean) -> Unit,
    val onVisualizationVuAnchorChanged: (VisualizationVuAnchor) -> Unit,
    val onVisualizationVuUseThemeColorChanged: (Boolean) -> Unit,
    val onVisualizationVuSmoothingPercentChanged: (Int) -> Unit,
    val onVisualizationVuRenderBackendChanged: (VisualizationRenderBackend) -> Unit,
    val onResetVisualizationBarsSettings: () -> Unit,
    val onResetVisualizationOscilloscopeSettings: () -> Unit,
    val onResetVisualizationVuSettings: () -> Unit,
    val onResetVisualizationChannelScopeSettings: () -> Unit,
    val onFfmpegSampleRateChanged: (Int) -> Unit,
    val onOpenMptSampleRateChanged: (Int) -> Unit,
    val onVgmPlaySampleRateChanged: (Int) -> Unit,
    val onGmeSampleRateChanged: (Int) -> Unit,
    val onSidPlayFpSampleRateChanged: (Int) -> Unit,
    val onLazyUsf2SampleRateChanged: (Int) -> Unit,
    val onLazyUsf2UseHleAudioChanged: (Boolean) -> Unit,
    val onVio2sfInterpolationQualityChanged: (Int) -> Unit,
    val onSidPlayFpBackendChanged: (Int) -> Unit,
    val onSidPlayFpClockModeChanged: (Int) -> Unit,
    val onSidPlayFpSidModelModeChanged: (Int) -> Unit,
    val onSidPlayFpFilter6581EnabledChanged: (Boolean) -> Unit,
    val onSidPlayFpFilter8580EnabledChanged: (Boolean) -> Unit,
    val onSidPlayFpDigiBoost8580Changed: (Boolean) -> Unit,
    val onSidPlayFpFilterCurve6581PercentChanged: (Int) -> Unit,
    val onSidPlayFpFilterRange6581PercentChanged: (Int) -> Unit,
    val onSidPlayFpFilterCurve8580PercentChanged: (Int) -> Unit,
    val onSidPlayFpReSidFpFastSamplingChanged: (Boolean) -> Unit,
    val onSidPlayFpReSidFpCombinedWaveformsStrengthChanged: (Int) -> Unit,
    val onGmeTempoPercentChanged: (Int) -> Unit,
    val onGmeStereoSeparationPercentChanged: (Int) -> Unit,
    val onGmeEchoEnabledChanged: (Boolean) -> Unit,
    val onGmeAccuracyEnabledChanged: (Boolean) -> Unit,
    val onGmeEqTrebleDecibelChanged: (Int) -> Unit,
    val onGmeEqBassHzChanged: (Int) -> Unit,
    val onGmeSpcUseBuiltInFadeChanged: (Boolean) -> Unit,
    val onGmeSpcInterpolationChanged: (Int) -> Unit,
    val onGmeSpcUseNativeSampleRateChanged: (Boolean) -> Unit,
    val onVgmPlayLoopCountChanged: (Int) -> Unit,
    val onVgmPlayAllowNonLoopingLoopChanged: (Boolean) -> Unit,
    val onVgmPlayVsyncRateChanged: (Int) -> Unit,
    val onVgmPlayResampleModeChanged: (Int) -> Unit,
    val onVgmPlayChipSampleModeChanged: (Int) -> Unit,
    val onVgmPlayChipSampleRateChanged: (Int) -> Unit,
    val onVgmPlayChipCoreChanged: (String, Int) -> Unit,
    val onOpenMptStereoSeparationPercentChanged: (Int) -> Unit,
    val onOpenMptStereoSeparationAmigaPercentChanged: (Int) -> Unit,
    val onOpenMptInterpolationFilterLengthChanged: (Int) -> Unit,
    val onOpenMptAmigaResamplerModeChanged: (Int) -> Unit,
    val onOpenMptAmigaResamplerApplyAllModulesChanged: (Boolean) -> Unit,
    val onOpenMptVolumeRampingStrengthChanged: (Int) -> Unit,
    val onOpenMptFt2XmVolumeRampingChanged: (Boolean) -> Unit,
    val onOpenMptMasterGainMilliBelChanged: (Int) -> Unit,
    val onOpenMptSurroundEnabledChanged: (Boolean) -> Unit,
    val onClearRecentHistory: () -> Unit,
    val onClearAllSettings: () -> Unit,
    val onClearAllPluginSettings: () -> Unit,
    val onResetPluginSettings: (String) -> Unit
)

@Composable
internal fun SettingsScreen(
    route: SettingsRoute,
    bottomContentPadding: Dp = 0.dp,
    state: SettingsScreenState,
    actions: SettingsScreenActions
) {
    val onBack = actions.onBack
    val onOpenAudioPlugins = actions.onOpenAudioPlugins
    val onOpenGeneralAudio = actions.onOpenGeneralAudio
    val onOpenHome = actions.onOpenHome
    val onOpenAudioEffects = actions.onOpenAudioEffects
    val onClearAllAudioParameters = actions.onClearAllAudioParameters
    val onClearPluginAudioParameters = actions.onClearPluginAudioParameters
    val onClearSongAudioParameters = actions.onClearSongAudioParameters
    val onOpenPlayer = actions.onOpenPlayer
    val onOpenVisualization = actions.onOpenVisualization
    val onOpenVisualizationBasic = actions.onOpenVisualizationBasic
    val onOpenVisualizationBasicBars = actions.onOpenVisualizationBasicBars
    val onOpenVisualizationBasicOscilloscope = actions.onOpenVisualizationBasicOscilloscope
    val onOpenVisualizationBasicVuMeters = actions.onOpenVisualizationBasicVuMeters
    val onOpenVisualizationAdvanced = actions.onOpenVisualizationAdvanced
    val onOpenVisualizationAdvancedChannelScope = actions.onOpenVisualizationAdvancedChannelScope
    val onOpenMisc = actions.onOpenMisc
    val onOpenUrlCache = actions.onOpenUrlCache
    val onOpenCacheManager = actions.onOpenCacheManager
    val onOpenUi = actions.onOpenUi
    val onOpenAbout = actions.onOpenAbout
    val onOpenVgmPlayChipSettings = actions.onOpenVgmPlayChipSettings
    val selectedPluginName = state.selectedPluginName
    val onPluginSelected = actions.onPluginSelected
    val onPluginEnabledChanged = actions.onPluginEnabledChanged
    val onPluginPriorityChanged = actions.onPluginPriorityChanged
    val onPluginPriorityOrderChanged = actions.onPluginPriorityOrderChanged
    val onPluginExtensionsChanged = actions.onPluginExtensionsChanged
    val autoPlayOnTrackSelect = state.autoPlayOnTrackSelect
    val onAutoPlayOnTrackSelectChanged = actions.onAutoPlayOnTrackSelectChanged
    val openPlayerOnTrackSelect = state.openPlayerOnTrackSelect
    val onOpenPlayerOnTrackSelectChanged = actions.onOpenPlayerOnTrackSelectChanged
    val autoPlayNextTrackOnEnd = state.autoPlayNextTrackOnEnd
    val onAutoPlayNextTrackOnEndChanged = actions.onAutoPlayNextTrackOnEndChanged
    val previousRestartsAfterThreshold = state.previousRestartsAfterThreshold
    val onPreviousRestartsAfterThresholdChanged = actions.onPreviousRestartsAfterThresholdChanged
    val respondHeadphoneMediaButtons = state.respondHeadphoneMediaButtons
    val onRespondHeadphoneMediaButtonsChanged = actions.onRespondHeadphoneMediaButtonsChanged
    val pauseOnHeadphoneDisconnect = state.pauseOnHeadphoneDisconnect
    val onPauseOnHeadphoneDisconnectChanged = actions.onPauseOnHeadphoneDisconnectChanged
    val audioFocusInterrupt = state.audioFocusInterrupt
    val onAudioFocusInterruptChanged = actions.onAudioFocusInterruptChanged
    val audioDucking = state.audioDucking
    val onAudioDuckingChanged = actions.onAudioDuckingChanged
    val audioBackendPreference = state.audioBackendPreference
    val onAudioBackendPreferenceChanged = actions.onAudioBackendPreferenceChanged
    val audioPerformanceMode = state.audioPerformanceMode
    val onAudioPerformanceModeChanged = actions.onAudioPerformanceModeChanged
    val audioBufferPreset = state.audioBufferPreset
    val onAudioBufferPresetChanged = actions.onAudioBufferPresetChanged
    val audioResamplerPreference = state.audioResamplerPreference
    val onAudioResamplerPreferenceChanged = actions.onAudioResamplerPreferenceChanged
    val audioAllowBackendFallback = state.audioAllowBackendFallback
    val onAudioAllowBackendFallbackChanged = actions.onAudioAllowBackendFallbackChanged
    val openPlayerFromNotification = state.openPlayerFromNotification
    val onOpenPlayerFromNotificationChanged = actions.onOpenPlayerFromNotificationChanged
    val persistRepeatMode = state.persistRepeatMode
    val onPersistRepeatModeChanged = actions.onPersistRepeatModeChanged
    val themeMode = state.themeMode
    val onThemeModeChanged = actions.onThemeModeChanged
    val rememberBrowserLocation = state.rememberBrowserLocation
    val onRememberBrowserLocationChanged = actions.onRememberBrowserLocationChanged
    val recentFoldersLimit = state.recentFoldersLimit
    val onRecentFoldersLimitChanged = actions.onRecentFoldersLimitChanged
    val recentFilesLimit = state.recentFilesLimit
    val onRecentFilesLimitChanged = actions.onRecentFilesLimitChanged
    val urlCacheClearOnLaunch = state.urlCacheClearOnLaunch
    val onUrlCacheClearOnLaunchChanged = actions.onUrlCacheClearOnLaunchChanged
    val urlCacheMaxTracks = state.urlCacheMaxTracks
    val onUrlCacheMaxTracksChanged = actions.onUrlCacheMaxTracksChanged
    val urlCacheMaxBytes = state.urlCacheMaxBytes
    val onUrlCacheMaxBytesChanged = actions.onUrlCacheMaxBytesChanged
    val onClearUrlCacheNow = actions.onClearUrlCacheNow
    val cachedSourceFiles = state.cachedSourceFiles
    val onRefreshCachedSourceFiles = actions.onRefreshCachedSourceFiles
    val onDeleteCachedSourceFiles = actions.onDeleteCachedSourceFiles
    val onExportCachedSourceFiles = actions.onExportCachedSourceFiles
    val keepScreenOn = state.keepScreenOn
    val onKeepScreenOnChanged = actions.onKeepScreenOnChanged
    val playerArtworkCornerRadiusDp = state.playerArtworkCornerRadiusDp
    val onPlayerArtworkCornerRadiusDpChanged = actions.onPlayerArtworkCornerRadiusDpChanged
    val filenameDisplayMode = state.filenameDisplayMode
    val onFilenameDisplayModeChanged = actions.onFilenameDisplayModeChanged
    val filenameOnlyWhenTitleMissing = state.filenameOnlyWhenTitleMissing
    val onFilenameOnlyWhenTitleMissingChanged = actions.onFilenameOnlyWhenTitleMissingChanged
    val unknownTrackDurationSeconds = state.unknownTrackDurationSeconds
    val onUnknownTrackDurationSecondsChanged = actions.onUnknownTrackDurationSecondsChanged
    val endFadeApplyToAllTracks = state.endFadeApplyToAllTracks
    val onEndFadeApplyToAllTracksChanged = actions.onEndFadeApplyToAllTracksChanged
    val endFadeDurationMs = state.endFadeDurationMs
    val onEndFadeDurationMsChanged = actions.onEndFadeDurationMsChanged
    val endFadeCurve = state.endFadeCurve
    val onEndFadeCurveChanged = actions.onEndFadeCurveChanged
    val visualizationMode = state.visualizationMode
    val onVisualizationModeChanged = actions.onVisualizationModeChanged
    val enabledVisualizationModes = state.enabledVisualizationModes
    val onEnabledVisualizationModesChanged = actions.onEnabledVisualizationModesChanged
    val visualizationShowDebugInfo = state.visualizationShowDebugInfo
    val onVisualizationShowDebugInfoChanged = actions.onVisualizationShowDebugInfoChanged
    val visualizationBarCount = state.visualizationBarCount
    val onVisualizationBarCountChanged = actions.onVisualizationBarCountChanged
    val visualizationBarSmoothingPercent = state.visualizationBarSmoothingPercent
    val onVisualizationBarSmoothingPercentChanged = actions.onVisualizationBarSmoothingPercentChanged
    val visualizationBarRoundnessDp = state.visualizationBarRoundnessDp
    val onVisualizationBarRoundnessDpChanged = actions.onVisualizationBarRoundnessDpChanged
    val visualizationBarOverlayArtwork = state.visualizationBarOverlayArtwork
    val onVisualizationBarOverlayArtworkChanged = actions.onVisualizationBarOverlayArtworkChanged
    val visualizationBarUseThemeColor = state.visualizationBarUseThemeColor
    val onVisualizationBarUseThemeColorChanged = actions.onVisualizationBarUseThemeColorChanged
    val visualizationBarRenderBackend = state.visualizationBarRenderBackend
    val onVisualizationBarRenderBackendChanged = actions.onVisualizationBarRenderBackendChanged
    val visualizationOscStereo = state.visualizationOscStereo
    val onVisualizationOscStereoChanged = actions.onVisualizationOscStereoChanged
    val visualizationVuAnchor = state.visualizationVuAnchor
    val onVisualizationVuAnchorChanged = actions.onVisualizationVuAnchorChanged
    val visualizationVuUseThemeColor = state.visualizationVuUseThemeColor
    val onVisualizationVuUseThemeColorChanged = actions.onVisualizationVuUseThemeColorChanged
    val visualizationVuSmoothingPercent = state.visualizationVuSmoothingPercent
    val onVisualizationVuSmoothingPercentChanged = actions.onVisualizationVuSmoothingPercentChanged
    val visualizationVuRenderBackend = state.visualizationVuRenderBackend
    val onVisualizationVuRenderBackendChanged = actions.onVisualizationVuRenderBackendChanged
    val onResetVisualizationBarsSettings = actions.onResetVisualizationBarsSettings
    val onResetVisualizationOscilloscopeSettings = actions.onResetVisualizationOscilloscopeSettings
    val onResetVisualizationVuSettings = actions.onResetVisualizationVuSettings
    val onResetVisualizationChannelScopeSettings = actions.onResetVisualizationChannelScopeSettings
    val ffmpegSampleRateHz = state.ffmpegSampleRateHz
    val ffmpegCapabilities = state.ffmpegCapabilities
    val onFfmpegSampleRateChanged = actions.onFfmpegSampleRateChanged
    val openMptSampleRateHz = state.openMptSampleRateHz
    val openMptCapabilities = state.openMptCapabilities
    val onOpenMptSampleRateChanged = actions.onOpenMptSampleRateChanged
    val vgmPlaySampleRateHz = state.vgmPlaySampleRateHz
    val vgmPlayCapabilities = state.vgmPlayCapabilities
    val onVgmPlaySampleRateChanged = actions.onVgmPlaySampleRateChanged
    val gmeSampleRateHz = state.gmeSampleRateHz
    val onGmeSampleRateChanged = actions.onGmeSampleRateChanged
    val sidPlayFpSampleRateHz = state.sidPlayFpSampleRateHz
    val onSidPlayFpSampleRateChanged = actions.onSidPlayFpSampleRateChanged
    val lazyUsf2SampleRateHz = state.lazyUsf2SampleRateHz
    val onLazyUsf2SampleRateChanged = actions.onLazyUsf2SampleRateChanged
    val lazyUsf2UseHleAudio = state.lazyUsf2UseHleAudio
    val onLazyUsf2UseHleAudioChanged = actions.onLazyUsf2UseHleAudioChanged
    val vio2sfInterpolationQuality = state.vio2sfInterpolationQuality
    val onVio2sfInterpolationQualityChanged = actions.onVio2sfInterpolationQualityChanged
    val sidPlayFpBackend = state.sidPlayFpBackend
    val onSidPlayFpBackendChanged = actions.onSidPlayFpBackendChanged
    val sidPlayFpClockMode = state.sidPlayFpClockMode
    val onSidPlayFpClockModeChanged = actions.onSidPlayFpClockModeChanged
    val sidPlayFpSidModelMode = state.sidPlayFpSidModelMode
    val onSidPlayFpSidModelModeChanged = actions.onSidPlayFpSidModelModeChanged
    val sidPlayFpFilter6581Enabled = state.sidPlayFpFilter6581Enabled
    val onSidPlayFpFilter6581EnabledChanged = actions.onSidPlayFpFilter6581EnabledChanged
    val sidPlayFpFilter8580Enabled = state.sidPlayFpFilter8580Enabled
    val onSidPlayFpFilter8580EnabledChanged = actions.onSidPlayFpFilter8580EnabledChanged
    val sidPlayFpDigiBoost8580 = state.sidPlayFpDigiBoost8580
    val onSidPlayFpDigiBoost8580Changed = actions.onSidPlayFpDigiBoost8580Changed
    val sidPlayFpFilterCurve6581Percent = state.sidPlayFpFilterCurve6581Percent
    val onSidPlayFpFilterCurve6581PercentChanged = actions.onSidPlayFpFilterCurve6581PercentChanged
    val sidPlayFpFilterRange6581Percent = state.sidPlayFpFilterRange6581Percent
    val onSidPlayFpFilterRange6581PercentChanged = actions.onSidPlayFpFilterRange6581PercentChanged
    val sidPlayFpFilterCurve8580Percent = state.sidPlayFpFilterCurve8580Percent
    val onSidPlayFpFilterCurve8580PercentChanged = actions.onSidPlayFpFilterCurve8580PercentChanged
    val sidPlayFpReSidFpFastSampling = state.sidPlayFpReSidFpFastSampling
    val onSidPlayFpReSidFpFastSamplingChanged = actions.onSidPlayFpReSidFpFastSamplingChanged
    val sidPlayFpReSidFpCombinedWaveformsStrength = state.sidPlayFpReSidFpCombinedWaveformsStrength
    val onSidPlayFpReSidFpCombinedWaveformsStrengthChanged = actions.onSidPlayFpReSidFpCombinedWaveformsStrengthChanged
    val gmeTempoPercent = state.gmeTempoPercent
    val onGmeTempoPercentChanged = actions.onGmeTempoPercentChanged
    val gmeStereoSeparationPercent = state.gmeStereoSeparationPercent
    val onGmeStereoSeparationPercentChanged = actions.onGmeStereoSeparationPercentChanged
    val gmeEchoEnabled = state.gmeEchoEnabled
    val onGmeEchoEnabledChanged = actions.onGmeEchoEnabledChanged
    val gmeAccuracyEnabled = state.gmeAccuracyEnabled
    val onGmeAccuracyEnabledChanged = actions.onGmeAccuracyEnabledChanged
    val gmeEqTrebleDecibel = state.gmeEqTrebleDecibel
    val onGmeEqTrebleDecibelChanged = actions.onGmeEqTrebleDecibelChanged
    val gmeEqBassHz = state.gmeEqBassHz
    val onGmeEqBassHzChanged = actions.onGmeEqBassHzChanged
    val gmeSpcUseBuiltInFade = state.gmeSpcUseBuiltInFade
    val onGmeSpcUseBuiltInFadeChanged = actions.onGmeSpcUseBuiltInFadeChanged
    val gmeSpcInterpolation = state.gmeSpcInterpolation
    val onGmeSpcInterpolationChanged = actions.onGmeSpcInterpolationChanged
    val gmeSpcUseNativeSampleRate = state.gmeSpcUseNativeSampleRate
    val onGmeSpcUseNativeSampleRateChanged = actions.onGmeSpcUseNativeSampleRateChanged
    val vgmPlayLoopCount = state.vgmPlayLoopCount
    val onVgmPlayLoopCountChanged = actions.onVgmPlayLoopCountChanged
    val vgmPlayAllowNonLoopingLoop = state.vgmPlayAllowNonLoopingLoop
    val onVgmPlayAllowNonLoopingLoopChanged = actions.onVgmPlayAllowNonLoopingLoopChanged
    val vgmPlayVsyncRate = state.vgmPlayVsyncRate
    val onVgmPlayVsyncRateChanged = actions.onVgmPlayVsyncRateChanged
    val vgmPlayResampleMode = state.vgmPlayResampleMode
    val onVgmPlayResampleModeChanged = actions.onVgmPlayResampleModeChanged
    val vgmPlayChipSampleMode = state.vgmPlayChipSampleMode
    val onVgmPlayChipSampleModeChanged = actions.onVgmPlayChipSampleModeChanged
    val vgmPlayChipSampleRate = state.vgmPlayChipSampleRate
    val onVgmPlayChipSampleRateChanged = actions.onVgmPlayChipSampleRateChanged
    val vgmPlayChipCoreSelections = state.vgmPlayChipCoreSelections
    val onVgmPlayChipCoreChanged = actions.onVgmPlayChipCoreChanged
    val openMptStereoSeparationPercent = state.openMptStereoSeparationPercent
    val onOpenMptStereoSeparationPercentChanged = actions.onOpenMptStereoSeparationPercentChanged
    val openMptStereoSeparationAmigaPercent = state.openMptStereoSeparationAmigaPercent
    val onOpenMptStereoSeparationAmigaPercentChanged = actions.onOpenMptStereoSeparationAmigaPercentChanged
    val openMptInterpolationFilterLength = state.openMptInterpolationFilterLength
    val onOpenMptInterpolationFilterLengthChanged = actions.onOpenMptInterpolationFilterLengthChanged
    val openMptAmigaResamplerMode = state.openMptAmigaResamplerMode
    val onOpenMptAmigaResamplerModeChanged = actions.onOpenMptAmigaResamplerModeChanged
    val openMptAmigaResamplerApplyAllModules = state.openMptAmigaResamplerApplyAllModules
    val onOpenMptAmigaResamplerApplyAllModulesChanged = actions.onOpenMptAmigaResamplerApplyAllModulesChanged
    val openMptVolumeRampingStrength = state.openMptVolumeRampingStrength
    val onOpenMptVolumeRampingStrengthChanged = actions.onOpenMptVolumeRampingStrengthChanged
    val openMptFt2XmVolumeRamping = state.openMptFt2XmVolumeRamping
    val onOpenMptFt2XmVolumeRampingChanged = actions.onOpenMptFt2XmVolumeRampingChanged
    val openMptMasterGainMilliBel = state.openMptMasterGainMilliBel
    val onOpenMptMasterGainMilliBelChanged = actions.onOpenMptMasterGainMilliBelChanged
    val openMptSurroundEnabled = state.openMptSurroundEnabled
    val onOpenMptSurroundEnabledChanged = actions.onOpenMptSurroundEnabledChanged
    val onClearRecentHistory = actions.onClearRecentHistory
    val onClearAllSettings = actions.onClearAllSettings
    val onClearAllPluginSettings = actions.onClearAllPluginSettings
    val onResetPluginSettings = actions.onResetPluginSettings

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
        SettingsRoute.AudioPlugins -> "Audio cores"
        SettingsRoute.PluginDetail -> selectedPluginName?.let { "$it core settings" } ?: "Core settings"
        SettingsRoute.PluginVgmPlayChipSettings -> "VGMPlay chip settings"
        SettingsRoute.PluginFfmpeg -> "FFmpeg core settings"
        SettingsRoute.PluginOpenMpt -> "OpenMPT core settings"
        SettingsRoute.PluginVgmPlay -> "VGMPlay core settings"
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
                            actions = RootRouteActions(
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
                        )
                    }
                    SettingsRoute.AudioPlugins -> {
                        AudioPluginsRouteContent(
                            state = AudioPluginsRouteState(
                                pluginPriorityEditMode = pluginPriorityEditMode
                            ),
                            actions = AudioPluginsRouteActions(
                                onPluginSelected = onPluginSelected,
                                onPluginEnabledChanged = onPluginEnabledChanged,
                                onPluginPriorityOrderChanged = onPluginPriorityOrderChanged,
                                onRequestClearPluginSettings = {
                                    pendingResetAction = SettingsResetAction.ClearPluginSettings
                                }
                            )
                        )
                    }
                    SettingsRoute.PluginDetail -> {
                        PluginDetailRouteContent(
                            state = PluginDetailRouteState(
                                selectedPluginName = selectedPluginName,
                                ffmpegSampleRateHz = ffmpegSampleRateHz,
                                openMptSampleRateHz = openMptSampleRateHz,
                                openMptCapabilities = openMptCapabilities,
                                vgmPlaySampleRateHz = vgmPlaySampleRateHz,
                                vgmPlayCapabilities = vgmPlayCapabilities,
                                gmeSampleRateHz = gmeSampleRateHz,
                                sidPlayFpSampleRateHz = sidPlayFpSampleRateHz,
                                lazyUsf2SampleRateHz = lazyUsf2SampleRateHz,
                                openMptStereoSeparationPercent = openMptStereoSeparationPercent,
                                openMptStereoSeparationAmigaPercent = openMptStereoSeparationAmigaPercent,
                                openMptInterpolationFilterLength = openMptInterpolationFilterLength,
                                openMptAmigaResamplerMode = openMptAmigaResamplerMode,
                                openMptAmigaResamplerApplyAllModules = openMptAmigaResamplerApplyAllModules,
                                openMptVolumeRampingStrength = openMptVolumeRampingStrength,
                                openMptFt2XmVolumeRamping = openMptFt2XmVolumeRamping,
                                openMptMasterGainMilliBel = openMptMasterGainMilliBel,
                                openMptSurroundEnabled = openMptSurroundEnabled,
                                vgmPlayLoopCount = vgmPlayLoopCount,
                                vgmPlayAllowNonLoopingLoop = vgmPlayAllowNonLoopingLoop,
                                vgmPlayVsyncRate = vgmPlayVsyncRate,
                                vgmPlayResampleMode = vgmPlayResampleMode,
                                vgmPlayChipSampleMode = vgmPlayChipSampleMode,
                                vgmPlayChipSampleRate = vgmPlayChipSampleRate,
                                gmeTempoPercent = gmeTempoPercent,
                                gmeStereoSeparationPercent = gmeStereoSeparationPercent,
                                gmeEchoEnabled = gmeEchoEnabled,
                                gmeAccuracyEnabled = gmeAccuracyEnabled,
                                gmeEqTrebleDecibel = gmeEqTrebleDecibel,
                                gmeEqBassHz = gmeEqBassHz,
                                gmeSpcUseBuiltInFade = gmeSpcUseBuiltInFade,
                                gmeSpcInterpolation = gmeSpcInterpolation,
                                gmeSpcUseNativeSampleRate = gmeSpcUseNativeSampleRate,
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
                                lazyUsf2UseHleAudio = lazyUsf2UseHleAudio,
                                vio2sfInterpolationQuality = vio2sfInterpolationQuality
                            ),
                            actions = PluginDetailRouteActions(
                                onPluginPriorityChanged = onPluginPriorityChanged,
                                onPluginExtensionsChanged = onPluginExtensionsChanged,
                                onFfmpegSampleRateChanged = onFfmpegSampleRateChanged,
                                onOpenMptSampleRateChanged = onOpenMptSampleRateChanged,
                                onVgmPlaySampleRateChanged = onVgmPlaySampleRateChanged,
                                onGmeSampleRateChanged = onGmeSampleRateChanged,
                                onSidPlayFpSampleRateChanged = onSidPlayFpSampleRateChanged,
                                onLazyUsf2SampleRateChanged = onLazyUsf2SampleRateChanged,
                                onOpenMptStereoSeparationPercentChanged = onOpenMptStereoSeparationPercentChanged,
                                onOpenMptStereoSeparationAmigaPercentChanged = onOpenMptStereoSeparationAmigaPercentChanged,
                                onOpenMptInterpolationFilterLengthChanged = onOpenMptInterpolationFilterLengthChanged,
                                onOpenMptAmigaResamplerModeChanged = onOpenMptAmigaResamplerModeChanged,
                                onOpenMptAmigaResamplerApplyAllModulesChanged = onOpenMptAmigaResamplerApplyAllModulesChanged,
                                onOpenMptVolumeRampingStrengthChanged = onOpenMptVolumeRampingStrengthChanged,
                                onOpenMptFt2XmVolumeRampingChanged = onOpenMptFt2XmVolumeRampingChanged,
                                onOpenMptMasterGainMilliBelChanged = onOpenMptMasterGainMilliBelChanged,
                                onOpenMptSurroundEnabledChanged = onOpenMptSurroundEnabledChanged,
                                onVgmPlayLoopCountChanged = onVgmPlayLoopCountChanged,
                                onVgmPlayAllowNonLoopingLoopChanged = onVgmPlayAllowNonLoopingLoopChanged,
                                onVgmPlayVsyncRateChanged = onVgmPlayVsyncRateChanged,
                                onVgmPlayResampleModeChanged = onVgmPlayResampleModeChanged,
                                onVgmPlayChipSampleModeChanged = onVgmPlayChipSampleModeChanged,
                                onVgmPlayChipSampleRateChanged = onVgmPlayChipSampleRateChanged,
                                onOpenVgmPlayChipSettings = onOpenVgmPlayChipSettings,
                                onGmeTempoPercentChanged = onGmeTempoPercentChanged,
                                onGmeStereoSeparationPercentChanged = onGmeStereoSeparationPercentChanged,
                                onGmeEchoEnabledChanged = onGmeEchoEnabledChanged,
                                onGmeAccuracyEnabledChanged = onGmeAccuracyEnabledChanged,
                                onGmeEqTrebleDecibelChanged = onGmeEqTrebleDecibelChanged,
                                onGmeEqBassHzChanged = onGmeEqBassHzChanged,
                                onGmeSpcUseBuiltInFadeChanged = onGmeSpcUseBuiltInFadeChanged,
                                onGmeSpcInterpolationChanged = onGmeSpcInterpolationChanged,
                                onGmeSpcUseNativeSampleRateChanged = onGmeSpcUseNativeSampleRateChanged,
                                onSidPlayFpBackendChanged = onSidPlayFpBackendChanged,
                                onSidPlayFpClockModeChanged = onSidPlayFpClockModeChanged,
                                onSidPlayFpSidModelModeChanged = onSidPlayFpSidModelModeChanged,
                                onSidPlayFpFilter6581EnabledChanged = onSidPlayFpFilter6581EnabledChanged,
                                onSidPlayFpFilter8580EnabledChanged = onSidPlayFpFilter8580EnabledChanged,
                                onSidPlayFpDigiBoost8580Changed = onSidPlayFpDigiBoost8580Changed,
                                onSidPlayFpFilterCurve6581PercentChanged = onSidPlayFpFilterCurve6581PercentChanged,
                                onSidPlayFpFilterRange6581PercentChanged = onSidPlayFpFilterRange6581PercentChanged,
                                onSidPlayFpFilterCurve8580PercentChanged = onSidPlayFpFilterCurve8580PercentChanged,
                                onSidPlayFpReSidFpFastSamplingChanged = onSidPlayFpReSidFpFastSamplingChanged,
                                onSidPlayFpReSidFpCombinedWaveformsStrengthChanged = onSidPlayFpReSidFpCombinedWaveformsStrengthChanged,
                                onLazyUsf2UseHleAudioChanged = onLazyUsf2UseHleAudioChanged,
                                onVio2sfInterpolationQualityChanged = onVio2sfInterpolationQualityChanged
                            )
                        )
                    }
                    SettingsRoute.PluginVgmPlayChipSettings -> {
                        PluginVgmPlayChipSettingsRouteContent(
                            state = PluginVgmPlayChipSettingsRouteState(
                                vgmPlayChipCoreSelections = vgmPlayChipCoreSelections
                            ),
                            actions = PluginVgmPlayChipSettingsRouteActions(
                                onVgmPlayChipCoreChanged = onVgmPlayChipCoreChanged
                            )
                        )
                    }
                    SettingsRoute.PluginFfmpeg -> PluginSampleRateRouteContent(
                        state = PluginSampleRateRouteState(
                            title = "Render sample rate",
                            description = "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate.",
                            selectedHz = ffmpegSampleRateHz,
                            enabled = supportsCustomSampleRate(ffmpegCapabilities)
                        ),
                        actions = PluginSampleRateRouteActions(
                            onSelected = onFfmpegSampleRateChanged
                        )
                    )
                    SettingsRoute.PluginVgmPlay -> PluginSampleRateRouteContent(
                        state = PluginSampleRateRouteState(
                            title = "Render sample rate",
                            description = "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate.",
                            selectedHz = vgmPlaySampleRateHz,
                            enabled = supportsCustomSampleRate(vgmPlayCapabilities)
                        ),
                        actions = PluginSampleRateRouteActions(
                            onSelected = onVgmPlaySampleRateChanged
                        )
                    )
                    SettingsRoute.PluginOpenMpt -> {
                        PluginOpenMptRouteContent(
                            state = PluginOpenMptRouteState(
                                openMptSampleRateHz = openMptSampleRateHz,
                                openMptCapabilities = openMptCapabilities,
                                openMptStereoSeparationPercent = openMptStereoSeparationPercent,
                                openMptStereoSeparationAmigaPercent = openMptStereoSeparationAmigaPercent,
                                openMptInterpolationFilterLength = openMptInterpolationFilterLength,
                                openMptAmigaResamplerMode = openMptAmigaResamplerMode,
                                openMptAmigaResamplerApplyAllModules = openMptAmigaResamplerApplyAllModules,
                                openMptVolumeRampingStrength = openMptVolumeRampingStrength,
                                openMptFt2XmVolumeRamping = openMptFt2XmVolumeRamping,
                                openMptMasterGainMilliBel = openMptMasterGainMilliBel,
                                openMptSurroundEnabled = openMptSurroundEnabled
                            ),
                            actions = PluginOpenMptRouteActions(
                                onOpenMptStereoSeparationPercentChanged = onOpenMptStereoSeparationPercentChanged,
                                onOpenMptStereoSeparationAmigaPercentChanged = onOpenMptStereoSeparationAmigaPercentChanged,
                                onOpenMptInterpolationFilterLengthChanged = onOpenMptInterpolationFilterLengthChanged,
                                onOpenMptAmigaResamplerModeChanged = onOpenMptAmigaResamplerModeChanged,
                                onOpenMptAmigaResamplerApplyAllModulesChanged = onOpenMptAmigaResamplerApplyAllModulesChanged,
                                onOpenMptVolumeRampingStrengthChanged = onOpenMptVolumeRampingStrengthChanged,
                                onOpenMptFt2XmVolumeRampingChanged = onOpenMptFt2XmVolumeRampingChanged,
                                onOpenMptMasterGainMilliBelChanged = onOpenMptMasterGainMilliBelChanged,
                                onOpenMptSurroundEnabledChanged = onOpenMptSurroundEnabledChanged,
                                onOpenMptSampleRateChanged = onOpenMptSampleRateChanged
                            )
                        )
                    }
                    SettingsRoute.GeneralAudio -> {
                        GeneralAudioRouteContent(
                            state = GeneralAudioRouteState(
                                respondHeadphoneMediaButtons = respondHeadphoneMediaButtons,
                                pauseOnHeadphoneDisconnect = pauseOnHeadphoneDisconnect,
                                audioFocusInterrupt = audioFocusInterrupt,
                                audioDucking = audioDucking,
                                audioBackendPreference = audioBackendPreference,
                                audioPerformanceMode = audioPerformanceMode,
                                audioBufferPreset = audioBufferPreset,
                                audioResamplerPreference = audioResamplerPreference,
                                audioAllowBackendFallback = audioAllowBackendFallback
                            ),
                            actions = GeneralAudioRouteActions(
                                onRespondHeadphoneMediaButtonsChanged = onRespondHeadphoneMediaButtonsChanged,
                                onPauseOnHeadphoneDisconnectChanged = onPauseOnHeadphoneDisconnectChanged,
                                onAudioFocusInterruptChanged = onAudioFocusInterruptChanged,
                                onAudioDuckingChanged = onAudioDuckingChanged,
                                onOpenAudioEffects = onOpenAudioEffects,
                                onClearAllAudioParameters = onClearAllAudioParameters,
                                onClearPluginAudioParameters = onClearPluginAudioParameters,
                                onClearSongAudioParameters = onClearSongAudioParameters,
                                onAudioBackendPreferenceChanged = onAudioBackendPreferenceChanged,
                                onAudioPerformanceModeChanged = onAudioPerformanceModeChanged,
                                onAudioBufferPresetChanged = onAudioBufferPresetChanged,
                                onAudioResamplerPreferenceChanged = onAudioResamplerPreferenceChanged,
                                onAudioAllowBackendFallbackChanged = onAudioAllowBackendFallbackChanged
                            )
                        )
                    }
                    SettingsRoute.Home -> {
                        HomeRouteContent(
                            state = HomeRouteState(
                                recentFoldersLimit = recentFoldersLimit,
                                recentFilesLimit = recentFilesLimit
                            ),
                            actions = HomeRouteActions(
                                onRecentFoldersLimitChanged = onRecentFoldersLimitChanged,
                                onRecentFilesLimitChanged = onRecentFilesLimitChanged
                            )
                        )
                    }
                    SettingsRoute.Player -> {
                        PlayerRouteContent(
                            state = PlayerRouteState(
                                unknownTrackDurationSeconds = unknownTrackDurationSeconds,
                                endFadeDurationMs = endFadeDurationMs,
                                endFadeCurve = endFadeCurve,
                                endFadeApplyToAllTracks = endFadeApplyToAllTracks,
                                autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                                openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                                autoPlayNextTrackOnEnd = autoPlayNextTrackOnEnd,
                                previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                                openPlayerFromNotification = openPlayerFromNotification,
                                persistRepeatMode = persistRepeatMode,
                                keepScreenOn = keepScreenOn,
                                playerArtworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                                filenameDisplayMode = filenameDisplayMode,
                                filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing
                            ),
                            actions = PlayerRouteActions(
                                onUnknownTrackDurationSecondsChanged = onUnknownTrackDurationSecondsChanged,
                                onEndFadeDurationMsChanged = onEndFadeDurationMsChanged,
                                onEndFadeCurveChanged = onEndFadeCurveChanged,
                                onEndFadeApplyToAllTracksChanged = onEndFadeApplyToAllTracksChanged,
                                onAutoPlayOnTrackSelectChanged = onAutoPlayOnTrackSelectChanged,
                                onOpenPlayerOnTrackSelectChanged = onOpenPlayerOnTrackSelectChanged,
                                onAutoPlayNextTrackOnEndChanged = onAutoPlayNextTrackOnEndChanged,
                                onPreviousRestartsAfterThresholdChanged = onPreviousRestartsAfterThresholdChanged,
                                onOpenPlayerFromNotificationChanged = onOpenPlayerFromNotificationChanged,
                                onPersistRepeatModeChanged = onPersistRepeatModeChanged,
                                onKeepScreenOnChanged = onKeepScreenOnChanged,
                                onPlayerArtworkCornerRadiusDpChanged = onPlayerArtworkCornerRadiusDpChanged,
                                onFilenameDisplayModeChanged = onFilenameDisplayModeChanged,
                                onFilenameOnlyWhenTitleMissingChanged = onFilenameOnlyWhenTitleMissingChanged
                            )
                        )
                    }
                    SettingsRoute.Visualization -> {
                        VisualizationRouteContent(
                            state = VisualizationRouteState(
                                visualizationMode = visualizationMode,
                                enabledVisualizationModes = enabledVisualizationModes,
                                visualizationShowDebugInfo = visualizationShowDebugInfo
                            ),
                            actions = VisualizationRouteActions(
                                onVisualizationModeChanged = onVisualizationModeChanged,
                                onEnabledVisualizationModesChanged = onEnabledVisualizationModesChanged,
                                onVisualizationShowDebugInfoChanged = onVisualizationShowDebugInfoChanged,
                                onOpenVisualizationBasic = onOpenVisualizationBasic,
                                onOpenVisualizationAdvanced = onOpenVisualizationAdvanced
                            )
                        )
                    }
                    SettingsRoute.VisualizationBasic -> {
                        VisualizationBasicRouteContent(
                            actions = VisualizationBasicRouteActions(
                                onOpenVisualizationBasicBars = onOpenVisualizationBasicBars,
                                onOpenVisualizationBasicOscilloscope = onOpenVisualizationBasicOscilloscope,
                                onOpenVisualizationBasicVuMeters = onOpenVisualizationBasicVuMeters
                            )
                        )
                    }
                    SettingsRoute.VisualizationAdvanced -> {
                        VisualizationAdvancedRouteContent(
                            actions = VisualizationAdvancedRouteActions(
                                onOpenVisualizationAdvancedChannelScope = onOpenVisualizationAdvancedChannelScope
                            )
                        )
                    }
                    SettingsRoute.VisualizationBasicBars -> {
                        VisualizationBasicBarsRouteContent(
                            state = VisualizationBasicBarsRouteState(
                                visualizationBarCount = visualizationBarCount,
                                visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                                visualizationBarRoundnessDp = visualizationBarRoundnessDp,
                                visualizationBarOverlayArtwork = visualizationBarOverlayArtwork,
                                visualizationBarUseThemeColor = visualizationBarUseThemeColor,
                                visualizationBarRenderBackend = visualizationBarRenderBackend
                            ),
                            actions = VisualizationBasicBarsRouteActions(
                                onVisualizationBarCountChanged = onVisualizationBarCountChanged,
                                onVisualizationBarSmoothingPercentChanged = onVisualizationBarSmoothingPercentChanged,
                                onVisualizationBarRoundnessDpChanged = onVisualizationBarRoundnessDpChanged,
                                onVisualizationBarOverlayArtworkChanged = onVisualizationBarOverlayArtworkChanged,
                                onVisualizationBarUseThemeColorChanged = onVisualizationBarUseThemeColorChanged,
                                onVisualizationBarRenderBackendChanged = onVisualizationBarRenderBackendChanged
                            )
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
                            state = VisualizationBasicVuMetersRouteState(
                                visualizationVuAnchor = visualizationVuAnchor,
                                visualizationVuUseThemeColor = visualizationVuUseThemeColor,
                                visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                                visualizationVuRenderBackend = visualizationVuRenderBackend
                            ),
                            actions = VisualizationBasicVuMetersRouteActions(
                                onVisualizationVuAnchorChanged = onVisualizationVuAnchorChanged,
                                onVisualizationVuUseThemeColorChanged = onVisualizationVuUseThemeColorChanged,
                                onVisualizationVuSmoothingPercentChanged = onVisualizationVuSmoothingPercentChanged,
                                onVisualizationVuRenderBackendChanged = onVisualizationVuRenderBackendChanged
                            )
                        )
                    }
                    SettingsRoute.VisualizationAdvancedChannelScope -> {
                        VisualizationAdvancedChannelScopeRouteContent()
                    }
                    SettingsRoute.Misc -> {
                        MiscRouteContent(
                            state = MiscRouteState(
                                rememberBrowserLocation = rememberBrowserLocation
                            ),
                            actions = MiscRouteActions(
                                onRememberBrowserLocationChanged = onRememberBrowserLocationChanged,
                                onClearRecentHistory = onClearRecentHistory
                            )
                        )
                    }

                    SettingsRoute.UrlCache -> {
                        UrlCacheRouteContent(
                            state = UrlCacheRouteState(
                                urlCacheClearOnLaunch = urlCacheClearOnLaunch,
                                urlCacheMaxTracks = urlCacheMaxTracks,
                                urlCacheMaxBytes = urlCacheMaxBytes
                            ),
                            actions = UrlCacheRouteActions(
                                onUrlCacheClearOnLaunchChanged = onUrlCacheClearOnLaunchChanged,
                                onUrlCacheMaxTracksChanged = onUrlCacheMaxTracksChanged,
                                onUrlCacheMaxBytesChanged = onUrlCacheMaxBytesChanged,
                                onOpenCacheManager = onOpenCacheManager,
                                onClearUrlCacheNow = onClearUrlCacheNow
                            )
                        )
                    }
                    SettingsRoute.CacheManager -> {
                        CacheManagerSettingsRouteContent(
                            state = CacheManagerSettingsRouteState(
                                route = route,
                                cachedSourceFiles = cachedSourceFiles
                            ),
                            actions = CacheManagerSettingsRouteActions(
                                onRefreshCachedSourceFiles = onRefreshCachedSourceFiles,
                                onDeleteCachedSourceFiles = onDeleteCachedSourceFiles,
                                onExportCachedSourceFiles = onExportCachedSourceFiles
                            )
                        )
                    }
                    SettingsRoute.Ui -> UiRouteContent(
                        state = UiRouteState(themeMode = themeMode),
                        actions = UiRouteActions(onThemeModeChanged = onThemeModeChanged)
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
    val topBarVisibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    androidx.compose.material3.Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedVisibility(
                visibleState = topBarVisibleState,
                enter = fadeIn(animationSpec = tween(160)) + slideInVertically(
                    initialOffsetY = { fullHeight -> -fullHeight / 3 },
                    animationSpec = tween(220, easing = LinearOutSlowInEasing)
                ),
                exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(
                    targetOffsetY = { fullHeight -> -fullHeight / 4 },
                    animationSpec = tween(150, easing = FastOutLinearInEasing)
                )
            ) {
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
                                    contentDescription = if (pluginPriorityEditMode) "Finish reorder mode" else "Edit core order",
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
                                    contentDescription = "Reset core settings",
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
