package com.flopster101.siliconplayer

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val SETTINGS_PAGE_NAV_DURATION_MS = 300

@Composable
internal fun SettingsRouteContentHost(
    route: SettingsRoute,
    bottomContentPadding: Dp,
    scaffoldPaddingValues: PaddingValues,
    state: SettingsScreenState,
    actions: SettingsScreenActions,
    pluginPriorityEditMode: Boolean,
    onTogglePluginPriorityEditMode: () -> Unit,
    onRequestClearAllSettings: () -> Unit,
    onRequestClearPluginSettings: () -> Unit,
    onRequestPluginReset: (String) -> Unit
) {
    val secondaryTitle = settingsSecondaryTitle(route, state.selectedPluginName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPaddingValues)
    ) {
        SettingsSecondaryHeader(
            secondaryTitle = secondaryTitle,
            route = route,
            pluginPriorityEditMode = pluginPriorityEditMode,
            onTogglePluginPriorityEditMode = onTogglePluginPriorityEditMode,
            selectedPluginName = state.selectedPluginName,
            onRequestPluginReset = onRequestPluginReset,
            onResetVisualizationBarsSettings = actions.onResetVisualizationBarsSettings,
            onResetVisualizationOscilloscopeSettings = actions.onResetVisualizationOscilloscopeSettings,
            onResetVisualizationVuSettings = actions.onResetVisualizationVuSettings,
            onResetVisualizationChannelScopeSettings = actions.onResetVisualizationChannelScopeSettings
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
                .padding(start = 16.dp, end = 16.dp)
        ) { currentRoute ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(state = rememberScrollState())
            ) {
                SettingsRowsHost {
                    when (currentRoute) {
                    SettingsRoute.Root -> {
                        RootRouteContent(
                            actions = RootRouteActions(
                                onOpenAudioPlugins = actions.onOpenAudioPlugins,
                                onOpenGeneralAudio = actions.onOpenGeneralAudio,
                                onOpenPlayer = actions.onOpenPlayer,
                                onOpenHome = actions.onOpenHome,
                                onOpenFileBrowser = actions.onOpenFileBrowser,
                                onOpenNetwork = actions.onOpenNetwork,
                                onOpenVisualization = actions.onOpenVisualization,
                                onOpenUrlCache = actions.onOpenUrlCache,
                                onOpenMisc = actions.onOpenMisc,
                                onOpenUi = actions.onOpenUi,
                                onOpenAbout = actions.onOpenAbout,
                                onRequestClearAllSettings = onRequestClearAllSettings
                            )
                        )
                    }

                    SettingsRoute.AudioPlugins -> {
                        AudioPluginsRouteContent(
                            state = AudioPluginsRouteState(
                                pluginPriorityEditMode = pluginPriorityEditMode
                            ),
                            actions = AudioPluginsRouteActions(
                                onPluginSelected = actions.pluginCoreActions.onPluginSelected,
                                onPluginEnabledChanged = actions.pluginCoreActions.onPluginEnabledChanged,
                                onPluginPriorityOrderChanged = actions.pluginCoreActions.onPluginPriorityOrderChanged,
                                onRequestClearPluginSettings = onRequestClearPluginSettings
                            )
                        )
                    }

                    SettingsRoute.PluginDetail -> {
                        PluginDetailRouteContent(
                            state = state.toPluginDetailRouteState(),
                            actions = actions.toPluginDetailRouteActions()
                        )
                    }

                    SettingsRoute.PluginVgmPlayChipSettings -> {
                        PluginVgmPlayChipSettingsRouteContent(
                            state = PluginVgmPlayChipSettingsRouteState(
                                vgmPlayChipCoreSelections = state.pluginCore.vgmPlayChipCoreSelections
                            ),
                            actions = PluginVgmPlayChipSettingsRouteActions(
                                onVgmPlayChipCoreChanged = actions.pluginCoreActions.onVgmPlayChipCoreChanged
                            )
                        )
                    }

                    SettingsRoute.PluginFfmpeg -> {
                        PluginSampleRateRouteContent(
                            state = PluginSampleRateRouteState(
                                title = "Render sample rate",
                                description = "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate.",
                                selectedHz = state.pluginCore.ffmpegSampleRateHz,
                                enabled = supportsCustomSampleRate(state.pluginCore.ffmpegCapabilities)
                            ),
                            actions = PluginSampleRateRouteActions(
                                onSelected = actions.pluginCoreActions.onFfmpegSampleRateChanged
                            )
                        )
                    }

                    SettingsRoute.PluginVgmPlay -> {
                        PluginSampleRateRouteContent(
                            state = PluginSampleRateRouteState(
                                title = "Render sample rate",
                                description = "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate.",
                                selectedHz = state.pluginCore.vgmPlaySampleRateHz,
                                enabled = supportsCustomSampleRate(state.pluginCore.vgmPlayCapabilities)
                            ),
                            actions = PluginSampleRateRouteActions(
                                onSelected = actions.pluginCoreActions.onVgmPlaySampleRateChanged
                            )
                        )
                    }

                    SettingsRoute.PluginOpenMpt -> {
                        PluginOpenMptRouteContent(
                            state = PluginOpenMptRouteState(
                                openMptSampleRateHz = state.pluginCore.openMptSampleRateHz,
                                openMptCapabilities = state.pluginCore.openMptCapabilities,
                                openMptStereoSeparationPercent = state.pluginCore.openMptStereoSeparationPercent,
                                openMptStereoSeparationAmigaPercent = state.pluginCore.openMptStereoSeparationAmigaPercent,
                                openMptInterpolationFilterLength = state.pluginCore.openMptInterpolationFilterLength,
                                openMptAmigaResamplerMode = state.pluginCore.openMptAmigaResamplerMode,
                                openMptAmigaResamplerApplyAllModules = state.pluginCore.openMptAmigaResamplerApplyAllModules,
                                openMptVolumeRampingStrength = state.pluginCore.openMptVolumeRampingStrength,
                                openMptFt2XmVolumeRamping = state.pluginCore.openMptFt2XmVolumeRamping,
                                openMptMasterGainMilliBel = state.pluginCore.openMptMasterGainMilliBel,
                                openMptSurroundEnabled = state.pluginCore.openMptSurroundEnabled
                            ),
                            actions = PluginOpenMptRouteActions(
                                onOpenMptStereoSeparationPercentChanged = actions.pluginCoreActions.onOpenMptStereoSeparationPercentChanged,
                                onOpenMptStereoSeparationAmigaPercentChanged = actions.pluginCoreActions.onOpenMptStereoSeparationAmigaPercentChanged,
                                onOpenMptInterpolationFilterLengthChanged = actions.pluginCoreActions.onOpenMptInterpolationFilterLengthChanged,
                                onOpenMptAmigaResamplerModeChanged = actions.pluginCoreActions.onOpenMptAmigaResamplerModeChanged,
                                onOpenMptAmigaResamplerApplyAllModulesChanged = actions.pluginCoreActions.onOpenMptAmigaResamplerApplyAllModulesChanged,
                                onOpenMptVolumeRampingStrengthChanged = actions.pluginCoreActions.onOpenMptVolumeRampingStrengthChanged,
                                onOpenMptFt2XmVolumeRampingChanged = actions.pluginCoreActions.onOpenMptFt2XmVolumeRampingChanged,
                                onOpenMptMasterGainMilliBelChanged = actions.pluginCoreActions.onOpenMptMasterGainMilliBelChanged,
                                onOpenMptSurroundEnabledChanged = actions.pluginCoreActions.onOpenMptSurroundEnabledChanged,
                                onOpenMptSampleRateChanged = actions.pluginCoreActions.onOpenMptSampleRateChanged
                            )
                        )
                    }

                    SettingsRoute.GeneralAudio -> {
                        GeneralAudioRouteContent(
                            state = GeneralAudioRouteState(
                                respondHeadphoneMediaButtons = state.respondHeadphoneMediaButtons,
                                pauseOnHeadphoneDisconnect = state.pauseOnHeadphoneDisconnect,
                                audioFocusInterrupt = state.audioFocusInterrupt,
                                audioDucking = state.audioDucking,
                                audioBackendPreference = state.audioBackendPreference,
                                audioPerformanceMode = state.audioPerformanceMode,
                                audioBufferPreset = state.audioBufferPreset,
                                audioResamplerPreference = state.audioResamplerPreference,
                                audioOutputLimiterEnabled = state.audioOutputLimiterEnabled,
                                audioAllowBackendFallback = state.audioAllowBackendFallback
                            ),
                            actions = GeneralAudioRouteActions(
                                onRespondHeadphoneMediaButtonsChanged = actions.onRespondHeadphoneMediaButtonsChanged,
                                onPauseOnHeadphoneDisconnectChanged = actions.onPauseOnHeadphoneDisconnectChanged,
                                onAudioFocusInterruptChanged = actions.onAudioFocusInterruptChanged,
                                onAudioDuckingChanged = actions.onAudioDuckingChanged,
                                onOpenAudioEffects = actions.onOpenAudioEffects,
                                onClearAllAudioParameters = actions.onClearAllAudioParameters,
                                onClearPluginAudioParameters = actions.onClearPluginAudioParameters,
                                onClearSongAudioParameters = actions.onClearSongAudioParameters,
                                onAudioBackendPreferenceChanged = actions.onAudioBackendPreferenceChanged,
                                onAudioPerformanceModeChanged = actions.onAudioPerformanceModeChanged,
                                onAudioBufferPresetChanged = actions.onAudioBufferPresetChanged,
                                onAudioResamplerPreferenceChanged = actions.onAudioResamplerPreferenceChanged,
                                onAudioOutputLimiterEnabledChanged = actions.onAudioOutputLimiterEnabledChanged,
                                onAudioAllowBackendFallbackChanged = actions.onAudioAllowBackendFallbackChanged
                            )
                        )
                    }

                    SettingsRoute.Home -> {
                        HomeRouteContent(
                            state = HomeRouteState(
                                recentFoldersLimit = state.recentFoldersLimit,
                                recentFilesLimit = state.recentFilesLimit
                            ),
                            actions = HomeRouteActions(
                                onRecentFoldersLimitChanged = actions.onRecentFoldersLimitChanged,
                                onRecentFilesLimitChanged = actions.onRecentFilesLimitChanged
                            )
                        )
                    }

                    SettingsRoute.FileBrowser -> {
                        FileBrowserRouteContent(
                            state = FileBrowserRouteState(
                                rememberBrowserLocation = state.rememberBrowserLocation,
                                showParentDirectoryEntry = state.showParentDirectoryEntry,
                                showFileIconChipBackground = state.showFileIconChipBackground,
                                sortArchivesBeforeFiles = state.sortArchivesBeforeFiles,
                                browserNameSortMode = state.browserNameSortMode
                            ),
                            actions = FileBrowserRouteActions(
                                onRememberBrowserLocationChanged = actions.onRememberBrowserLocationChanged,
                                onShowParentDirectoryEntryChanged = actions.onShowParentDirectoryEntryChanged,
                                onShowFileIconChipBackgroundChanged = actions.onShowFileIconChipBackgroundChanged,
                                onSortArchivesBeforeFilesChanged = actions.onSortArchivesBeforeFilesChanged,
                                onBrowserNameSortModeChanged = actions.onBrowserNameSortModeChanged
                            )
                        )
                    }

                    SettingsRoute.Network -> {
                        NetworkRouteContent(
                            actions = NetworkRouteActions(
                                onClearSavedNetworkSources = actions.onClearSavedNetworkSources
                            )
                        )
                    }

                    SettingsRoute.Player -> {
                        PlayerRouteContent(
                            state = PlayerRouteState(
                                unknownTrackDurationSeconds = state.unknownTrackDurationSeconds,
                                endFadeDurationMs = state.endFadeDurationMs,
                                endFadeCurve = state.endFadeCurve,
                                endFadeApplyToAllTracks = state.endFadeApplyToAllTracks,
                                autoPlayOnTrackSelect = state.autoPlayOnTrackSelect,
                                openPlayerOnTrackSelect = state.openPlayerOnTrackSelect,
                                autoPlayNextTrackOnEnd = state.autoPlayNextTrackOnEnd,
                                preloadNextCachedRemoteTrack = state.preloadNextCachedRemoteTrack,
                                playlistWrapNavigation = state.playlistWrapNavigation,
                                previousRestartsAfterThreshold = state.previousRestartsAfterThreshold,
                                fadePauseResume = state.fadePauseResume,
                                openPlayerFromNotification = state.openPlayerFromNotification,
                                persistRepeatMode = state.persistRepeatMode,
                                keepScreenOn = state.keepScreenOn,
                                playerArtworkCornerRadiusDp = state.playerArtworkCornerRadiusDp,
                                filenameDisplayMode = state.filenameDisplayMode,
                                filenameOnlyWhenTitleMissing = state.filenameOnlyWhenTitleMissing
                            ),
                            actions = PlayerRouteActions(
                                onUnknownTrackDurationSecondsChanged = actions.onUnknownTrackDurationSecondsChanged,
                                onEndFadeDurationMsChanged = actions.onEndFadeDurationMsChanged,
                                onEndFadeCurveChanged = actions.onEndFadeCurveChanged,
                                onEndFadeApplyToAllTracksChanged = actions.onEndFadeApplyToAllTracksChanged,
                                onAutoPlayOnTrackSelectChanged = actions.onAutoPlayOnTrackSelectChanged,
                                onOpenPlayerOnTrackSelectChanged = actions.onOpenPlayerOnTrackSelectChanged,
                                onAutoPlayNextTrackOnEndChanged = actions.onAutoPlayNextTrackOnEndChanged,
                                onPreloadNextCachedRemoteTrackChanged = actions.onPreloadNextCachedRemoteTrackChanged,
                                onPlaylistWrapNavigationChanged = actions.onPlaylistWrapNavigationChanged,
                                onPreviousRestartsAfterThresholdChanged = actions.onPreviousRestartsAfterThresholdChanged,
                                onFadePauseResumeChanged = actions.onFadePauseResumeChanged,
                                onOpenPlayerFromNotificationChanged = actions.onOpenPlayerFromNotificationChanged,
                                onPersistRepeatModeChanged = actions.onPersistRepeatModeChanged,
                                onKeepScreenOnChanged = actions.onKeepScreenOnChanged,
                                onPlayerArtworkCornerRadiusDpChanged = actions.onPlayerArtworkCornerRadiusDpChanged,
                                onFilenameDisplayModeChanged = actions.onFilenameDisplayModeChanged,
                                onFilenameOnlyWhenTitleMissingChanged = actions.onFilenameOnlyWhenTitleMissingChanged
                            )
                        )
                    }

                    SettingsRoute.Visualization -> {
                        VisualizationRouteContent(
                            state = VisualizationRouteState(
                                visualizationMode = state.visualizationMode,
                                enabledVisualizationModes = state.enabledVisualizationModes,
                                visualizationShowDebugInfo = state.visualizationShowDebugInfo
                            ),
                            actions = VisualizationRouteActions(
                                onVisualizationModeChanged = actions.onVisualizationModeChanged,
                                onEnabledVisualizationModesChanged = actions.onEnabledVisualizationModesChanged,
                                onVisualizationShowDebugInfoChanged = actions.onVisualizationShowDebugInfoChanged,
                                onOpenVisualizationBasic = actions.onOpenVisualizationBasic,
                                onOpenVisualizationAdvanced = actions.onOpenVisualizationAdvanced
                            )
                        )
                    }

                    SettingsRoute.VisualizationBasic -> {
                        VisualizationBasicRouteContent(
                            actions = VisualizationBasicRouteActions(
                                onOpenVisualizationBasicBars = actions.onOpenVisualizationBasicBars,
                                onOpenVisualizationBasicOscilloscope = actions.onOpenVisualizationBasicOscilloscope,
                                onOpenVisualizationBasicVuMeters = actions.onOpenVisualizationBasicVuMeters
                            )
                        )
                    }

                    SettingsRoute.VisualizationAdvanced -> {
                        VisualizationAdvancedRouteContent(
                            actions = VisualizationAdvancedRouteActions(
                                onOpenVisualizationAdvancedChannelScope = actions.onOpenVisualizationAdvancedChannelScope
                            )
                        )
                    }

                    SettingsRoute.VisualizationBasicBars -> {
                        VisualizationBasicBarsRouteContent(
                            state = VisualizationBasicBarsRouteState(
                                visualizationBarCount = state.visualizationBarCount,
                                visualizationBarSmoothingPercent = state.visualizationBarSmoothingPercent,
                                visualizationBarRoundnessDp = state.visualizationBarRoundnessDp,
                                visualizationBarOverlayArtwork = state.visualizationBarOverlayArtwork,
                                visualizationBarUseThemeColor = state.visualizationBarUseThemeColor,
                                visualizationBarRenderBackend = state.visualizationBarRenderBackend
                            ),
                            actions = VisualizationBasicBarsRouteActions(
                                onVisualizationBarCountChanged = actions.onVisualizationBarCountChanged,
                                onVisualizationBarSmoothingPercentChanged = actions.onVisualizationBarSmoothingPercentChanged,
                                onVisualizationBarRoundnessDpChanged = actions.onVisualizationBarRoundnessDpChanged,
                                onVisualizationBarOverlayArtworkChanged = actions.onVisualizationBarOverlayArtworkChanged,
                                onVisualizationBarUseThemeColorChanged = actions.onVisualizationBarUseThemeColorChanged,
                                onVisualizationBarRenderBackendChanged = actions.onVisualizationBarRenderBackendChanged
                            )
                        )
                    }

                    SettingsRoute.VisualizationBasicOscilloscope -> {
                        VisualizationBasicOscilloscopeRouteContent(
                            visualizationOscStereo = state.visualizationOscStereo,
                            onVisualizationOscStereoChanged = actions.onVisualizationOscStereoChanged
                        )
                    }

                    SettingsRoute.VisualizationBasicVuMeters -> {
                        VisualizationBasicVuMetersRouteContent(
                            state = VisualizationBasicVuMetersRouteState(
                                visualizationVuAnchor = state.visualizationVuAnchor,
                                visualizationVuUseThemeColor = state.visualizationVuUseThemeColor,
                                visualizationVuSmoothingPercent = state.visualizationVuSmoothingPercent,
                                visualizationVuRenderBackend = state.visualizationVuRenderBackend
                            ),
                            actions = VisualizationBasicVuMetersRouteActions(
                                onVisualizationVuAnchorChanged = actions.onVisualizationVuAnchorChanged,
                                onVisualizationVuUseThemeColorChanged = actions.onVisualizationVuUseThemeColorChanged,
                                onVisualizationVuSmoothingPercentChanged = actions.onVisualizationVuSmoothingPercentChanged,
                                onVisualizationVuRenderBackendChanged = actions.onVisualizationVuRenderBackendChanged
                            )
                        )
                    }

                    SettingsRoute.VisualizationAdvancedChannelScope -> {
                        VisualizationAdvancedChannelScopeRouteContent()
                    }

                    SettingsRoute.Misc -> {
                        MiscRouteContent(
                            actions = MiscRouteActions(
                                onClearRecentHistory = actions.onClearRecentHistory
                            )
                        )
                    }

                    SettingsRoute.UrlCache -> {
                        UrlCacheRouteContent(
                            state = UrlCacheRouteState(
                                urlCacheClearOnLaunch = state.urlCacheClearOnLaunch,
                                urlCacheMaxTracks = state.urlCacheMaxTracks,
                                urlCacheMaxBytes = state.urlCacheMaxBytes,
                                archiveCacheClearOnLaunch = state.archiveCacheClearOnLaunch,
                                archiveCacheMaxMounts = state.archiveCacheMaxMounts,
                                archiveCacheMaxBytes = state.archiveCacheMaxBytes,
                                archiveCacheMaxAgeDays = state.archiveCacheMaxAgeDays
                            ),
                            actions = UrlCacheRouteActions(
                                onUrlCacheClearOnLaunchChanged = actions.onUrlCacheClearOnLaunchChanged,
                                onUrlCacheMaxTracksChanged = actions.onUrlCacheMaxTracksChanged,
                                onUrlCacheMaxBytesChanged = actions.onUrlCacheMaxBytesChanged,
                                onOpenCacheManager = actions.onOpenCacheManager,
                                onArchiveCacheClearOnLaunchChanged = actions.onArchiveCacheClearOnLaunchChanged,
                                onArchiveCacheMaxMountsChanged = actions.onArchiveCacheMaxMountsChanged,
                                onArchiveCacheMaxBytesChanged = actions.onArchiveCacheMaxBytesChanged,
                                onArchiveCacheMaxAgeDaysChanged = actions.onArchiveCacheMaxAgeDaysChanged,
                                onClearUrlCacheNow = actions.onClearUrlCacheNow,
                                onClearArchiveCacheNow = actions.onClearArchiveCacheNow
                            )
                        )
                    }

                    SettingsRoute.CacheManager -> {
                        CacheManagerSettingsRouteContent(
                            state = CacheManagerSettingsRouteState(
                                route = currentRoute,
                                cachedSourceFiles = state.cachedSourceFiles
                            ),
                            actions = CacheManagerSettingsRouteActions(
                                onRefreshCachedSourceFiles = actions.onRefreshCachedSourceFiles,
                                onDeleteCachedSourceFiles = actions.onDeleteCachedSourceFiles,
                                onExportCachedSourceFiles = actions.onExportCachedSourceFiles
                            )
                        )
                    }

                    SettingsRoute.Ui -> {
                        UiRouteContent(
                            state = UiRouteState(themeMode = state.themeMode),
                            actions = UiRouteActions(onThemeModeChanged = actions.onThemeModeChanged)
                        )
                    }

                        SettingsRoute.About -> AboutSettingsBody()
                    }
                }

                if (bottomContentPadding > 0.dp) {
                    Spacer(modifier = Modifier.height(bottomContentPadding))
                }
            }
        }
    }
}

internal fun settingsSecondaryTitle(route: SettingsRoute, selectedPluginName: String?): String? {
    return when (route) {
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
        SettingsRoute.FileBrowser -> "File browser settings"
        SettingsRoute.Network -> "Network settings"
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
}
