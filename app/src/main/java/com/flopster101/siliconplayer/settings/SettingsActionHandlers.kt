package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_BYTES_DEFAULT
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT
import com.flopster101.siliconplayer.data.clearArchiveMountCache
import com.flopster101.siliconplayer.data.enforceArchiveMountCacheLimits
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun clearAllAudioParametersAction(
    context: Context,
    prefs: SharedPreferences,
    volumeDatabase: VolumeDatabase,
    onMasterVolumeDbChanged: (Float) -> Unit,
    onPluginVolumeDbChanged: (Float) -> Unit,
    onSongVolumeDbChanged: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit,
    onForceMonoChanged: (Boolean) -> Unit
) {
    prefs.edit().apply {
        remove(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB)
        remove(AppPreferenceKeys.AUDIO_FORCE_MONO)
        remove(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED)
        remove(AppPreferenceKeys.AUDIO_DSP_BASS_DEPTH)
        remove(AppPreferenceKeys.AUDIO_DSP_BASS_RANGE)
        remove(AppPreferenceKeys.AUDIO_DSP_SURROUND_ENABLED)
        remove(AppPreferenceKeys.AUDIO_DSP_SURROUND_DEPTH)
        remove(AppPreferenceKeys.AUDIO_DSP_SURROUND_DELAY_MS)
        remove(AppPreferenceKeys.AUDIO_DSP_REVERB_ENABLED)
        remove(AppPreferenceKeys.AUDIO_DSP_REVERB_DEPTH)
        remove(AppPreferenceKeys.AUDIO_DSP_REVERB_PRESET)
        remove(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_ENABLED)
        remove(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_BITS)
        apply()
    }
    clearAllDecoderPluginVolumes(prefs)
    volumeDatabase.resetAllSongVolumes()
    onMasterVolumeDbChanged(0f)
    onPluginVolumeDbChanged(0f)
    onSongVolumeDbChanged(0f)
    onIgnoreCoreVolumeForSongChanged(false)
    onForceMonoChanged(false)
    NativeBridge.setMasterGain(0f)
    NativeBridge.setPluginGain(0f)
    NativeBridge.setSongGain(0f)
    NativeBridge.setForceMono(false)
    NativeBridge.setDspBassEnabled(false)
    NativeBridge.setDspBassDepth(AppDefaults.AudioProcessing.Dsp.bassDepth)
    NativeBridge.setDspBassRange(AppDefaults.AudioProcessing.Dsp.bassRange)
    NativeBridge.setDspSurroundEnabled(false)
    NativeBridge.setDspSurroundDepth(AppDefaults.AudioProcessing.Dsp.surroundDepth)
    NativeBridge.setDspSurroundDelayMs(AppDefaults.AudioProcessing.Dsp.surroundDelayMs)
    NativeBridge.setDspReverbEnabled(false)
    NativeBridge.setDspReverbDepth(AppDefaults.AudioProcessing.Dsp.reverbDepth)
    NativeBridge.setDspReverbPreset(AppDefaults.AudioProcessing.Dsp.reverbPreset)
    NativeBridge.setDspBitCrushEnabled(false)
    NativeBridge.setDspBitCrushBits(AppDefaults.AudioProcessing.Dsp.bitCrushBits)
    Toast.makeText(context, "All audio parameters cleared", Toast.LENGTH_SHORT).show()
}

internal fun clearPluginAudioParametersAction(
    context: Context,
    prefs: SharedPreferences,
    onPluginVolumeDbChanged: (Float) -> Unit
) {
    clearAllDecoderPluginVolumes(prefs)
    onPluginVolumeDbChanged(0f)
    NativeBridge.setPluginGain(0f)
    Toast.makeText(context, "Core volume cleared", Toast.LENGTH_SHORT).show()
}

internal fun clearSongAudioParametersAction(
    context: Context,
    volumeDatabase: VolumeDatabase,
    onSongVolumeDbChanged: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit
) {
    volumeDatabase.resetAllSongVolumes()
    onSongVolumeDbChanged(0f)
    onIgnoreCoreVolumeForSongChanged(false)
    NativeBridge.setSongGain(0f)
    Toast.makeText(context, "All song volumes cleared", Toast.LENGTH_SHORT).show()
}

internal fun updateUrlCacheMaxTracksAction(
    value: Int,
    prefs: SharedPreferences,
    appScope: CoroutineScope,
    cacheRoot: File,
    urlCacheMaxBytes: Long,
    onUrlCacheMaxTracksChanged: (Int) -> Unit,
    onRefreshCachedSourceFiles: () -> Unit
) {
    val maxTracks = value.coerceAtLeast(1)
    onUrlCacheMaxTracksChanged(maxTracks)
    prefs.edit().putInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, maxTracks).apply()
    appScope.launch(Dispatchers.IO) {
        enforceRemoteCacheLimits(
            cacheRoot = cacheRoot,
            maxTracks = maxTracks,
            maxBytes = urlCacheMaxBytes
        )
        withContext(Dispatchers.Main.immediate) {
            onRefreshCachedSourceFiles()
        }
    }
}

internal fun updateUrlCacheMaxBytesAction(
    value: Long,
    prefs: SharedPreferences,
    appScope: CoroutineScope,
    cacheRoot: File,
    urlCacheMaxTracks: Int,
    onUrlCacheMaxBytesChanged: (Long) -> Unit,
    onRefreshCachedSourceFiles: () -> Unit
) {
    val maxBytes = value.coerceAtLeast(1L)
    onUrlCacheMaxBytesChanged(maxBytes)
    prefs.edit().putLong(AppPreferenceKeys.URL_CACHE_MAX_BYTES, maxBytes).apply()
    appScope.launch(Dispatchers.IO) {
        enforceRemoteCacheLimits(
            cacheRoot = cacheRoot,
            maxTracks = urlCacheMaxTracks,
            maxBytes = maxBytes
        )
        withContext(Dispatchers.Main.immediate) {
            onRefreshCachedSourceFiles()
        }
    }
}

internal fun updateAudioFocusInterruptAction(
    context: Context,
    prefs: SharedPreferences,
    enabled: Boolean,
    onAudioFocusInterruptChanged: (Boolean) -> Unit
) {
    onAudioFocusInterruptChanged(enabled)
    prefs.edit().putBoolean(AppPreferenceKeys.AUDIO_FOCUS_INTERRUPT, enabled).apply()
    PlaybackService.refreshSettings(context)
}

internal fun updateAudioDuckingAction(
    context: Context,
    prefs: SharedPreferences,
    enabled: Boolean,
    onAudioDuckingChanged: (Boolean) -> Unit
) {
    onAudioDuckingChanged(enabled)
    prefs.edit().putBoolean(AppPreferenceKeys.AUDIO_DUCKING, enabled).apply()
    PlaybackService.refreshSettings(context)
}

internal fun updateUrlCacheClearOnLaunchAction(
    prefs: SharedPreferences,
    enabled: Boolean,
    onUrlCacheClearOnLaunchChanged: (Boolean) -> Unit
) {
    onUrlCacheClearOnLaunchChanged(enabled)
    prefs.edit().putBoolean(AppPreferenceKeys.URL_CACHE_CLEAR_ON_LAUNCH, enabled).apply()
}

internal fun updateArchiveCacheClearOnLaunchAction(
    prefs: SharedPreferences,
    enabled: Boolean,
    onArchiveCacheClearOnLaunchChanged: (Boolean) -> Unit
) {
    onArchiveCacheClearOnLaunchChanged(enabled)
    prefs.edit().putBoolean(AppPreferenceKeys.ARCHIVE_CACHE_CLEAR_ON_LAUNCH, enabled).apply()
}

internal fun updateArchiveCacheMaxMountsAction(
    value: Int,
    prefs: SharedPreferences,
    appScope: CoroutineScope,
    cacheDir: File,
    archiveCacheMaxBytes: Long,
    archiveCacheMaxAgeDays: Int,
    onArchiveCacheMaxMountsChanged: (Int) -> Unit
) {
    val maxMounts = value.coerceAtLeast(1)
    onArchiveCacheMaxMountsChanged(maxMounts)
    prefs.edit().putInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_MOUNTS, maxMounts).apply()
    appScope.launch(Dispatchers.IO) {
        enforceArchiveMountCacheLimits(
            cacheDir = cacheDir,
            maxMounts = maxMounts,
            maxBytes = archiveCacheMaxBytes,
            maxAgeDays = archiveCacheMaxAgeDays
        )
    }
}

internal fun updateArchiveCacheMaxBytesAction(
    value: Long,
    prefs: SharedPreferences,
    appScope: CoroutineScope,
    cacheDir: File,
    archiveCacheMaxMounts: Int,
    archiveCacheMaxAgeDays: Int,
    onArchiveCacheMaxBytesChanged: (Long) -> Unit
) {
    val maxBytes = value.coerceAtLeast(1L)
    onArchiveCacheMaxBytesChanged(maxBytes)
    prefs.edit().putLong(AppPreferenceKeys.ARCHIVE_CACHE_MAX_BYTES, maxBytes).apply()
    appScope.launch(Dispatchers.IO) {
        enforceArchiveMountCacheLimits(
            cacheDir = cacheDir,
            maxMounts = archiveCacheMaxMounts,
            maxBytes = maxBytes,
            maxAgeDays = archiveCacheMaxAgeDays
        )
    }
}

internal fun updateArchiveCacheMaxAgeDaysAction(
    value: Int,
    prefs: SharedPreferences,
    appScope: CoroutineScope,
    cacheDir: File,
    archiveCacheMaxMounts: Int,
    archiveCacheMaxBytes: Long,
    onArchiveCacheMaxAgeDaysChanged: (Int) -> Unit
) {
    val maxAgeDays = value.coerceAtLeast(1)
    onArchiveCacheMaxAgeDaysChanged(maxAgeDays)
    prefs.edit().putInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_AGE_DAYS, maxAgeDays).apply()
    appScope.launch(Dispatchers.IO) {
        enforceArchiveMountCacheLimits(
            cacheDir = cacheDir,
            maxMounts = archiveCacheMaxMounts,
            maxBytes = archiveCacheMaxBytes,
            maxAgeDays = maxAgeDays
        )
    }
}

internal fun clearArchiveCacheNowAction(
    context: Context,
    appScope: CoroutineScope,
    cacheDir: File
) {
    appScope.launch(Dispatchers.IO) {
        val result = clearArchiveMountCache(cacheDir)
        withContext(Dispatchers.Main.immediate) {
            Toast.makeText(
                context,
                "Archive cache cleared (${result.deletedMounts} mounts)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

internal fun clearUrlCacheNowAction(
    context: Context,
    prefs: SharedPreferences,
    appScope: CoroutineScope,
    cacheRoot: File,
    selectedFile: File?,
    onRefreshCachedSourceFiles: () -> Unit
) {
    appScope.launch(Dispatchers.IO) {
        val sessionPath = prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null)
        val protectedPaths = buildSet {
            selectedFile?.absolutePath
                ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
                ?.let { add(it) }
            sessionPath
                ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
                ?.let { add(it) }
        }
        val result = clearRemoteCacheFiles(
            cacheRoot = cacheRoot,
            protectedPaths = protectedPaths
        )
        withContext(Dispatchers.Main) {
            val suffix = if (result.skippedFiles > 0) " (kept current track)" else ""
            Toast.makeText(
                context,
                "Cache cleared (${result.deletedFiles} files)$suffix",
                Toast.LENGTH_SHORT
            ).show()
            onRefreshCachedSourceFiles()
        }
    }
}

internal fun deleteCachedSourceFilesAction(
    context: Context,
    prefs: SharedPreferences,
    appScope: CoroutineScope,
    cacheRoot: File,
    selectedFile: File?,
    absolutePaths: List<String>,
    onRefreshCachedSourceFiles: () -> Unit
) {
    appScope.launch(Dispatchers.IO) {
        val sessionPath = prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null)
        val protectedPaths = buildSet {
            selectedFile?.absolutePath
                ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
                ?.let { add(it) }
            sessionPath
                ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
                ?.let { add(it) }
        }
        val result = deleteSpecificRemoteCacheFiles(
            cacheRoot = cacheRoot,
            absolutePaths = absolutePaths.toSet(),
            protectedPaths = protectedPaths
        )
        withContext(Dispatchers.Main.immediate) {
            val suffix = if (result.skippedFiles > 0) " (${result.skippedFiles} protected)" else ""
            Toast.makeText(
                context,
                "Deleted ${result.deletedFiles} file(s)$suffix",
                Toast.LENGTH_SHORT
            ).show()
            onRefreshCachedSourceFiles()
        }
    }
}

internal fun exportCachedSourceFilesAction(
    context: Context,
    paths: List<String>,
    onPendingCacheExportPathsChanged: (List<String>) -> Unit,
    launchDirectoryPicker: () -> Unit
) {
    if (paths.isEmpty()) {
        Toast.makeText(context, "No files selected", Toast.LENGTH_SHORT).show()
        return
    }
    onPendingCacheExportPathsChanged(paths)
    launchDirectoryPicker()
}

internal fun clearRecentHistoryAction(
    context: Context,
    prefs: SharedPreferences,
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit,
    onRecentPlayedFilesChanged: (List<RecentPathEntry>) -> Unit
) {
    onRecentFoldersChanged(emptyList())
    onRecentPlayedFilesChanged(emptyList())
    prefs.edit()
        .remove(AppPreferenceKeys.RECENT_FOLDERS)
        .remove(AppPreferenceKeys.RECENT_PLAYED_FILES)
        .apply()
    Toast.makeText(context, "Home recents cleared", Toast.LENGTH_SHORT).show()
}

internal fun resetVisualizationBarsSettingsAction(
    prefs: SharedPreferences,
    onBarCountChanged: (Int) -> Unit,
    onBarSmoothingPercentChanged: (Int) -> Unit,
    onBarRoundnessDpChanged: (Int) -> Unit,
    onBarOverlayArtworkChanged: (Boolean) -> Unit,
    onBarUseThemeColorChanged: (Boolean) -> Unit,
    onBarRenderBackendChanged: (VisualizationRenderBackend) -> Unit
) {
    onBarCountChanged(AppDefaults.Visualization.Bars.count)
    onBarSmoothingPercentChanged(AppDefaults.Visualization.Bars.smoothingPercent)
    onBarRoundnessDpChanged(AppDefaults.Visualization.Bars.roundnessDp)
    onBarOverlayArtworkChanged(AppDefaults.Visualization.Bars.overlayArtwork)
    onBarUseThemeColorChanged(AppDefaults.Visualization.Bars.useThemeColor)
    onBarRenderBackendChanged(AppDefaults.Visualization.Bars.renderBackend)
    prefs.edit()
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_BAR_FREQUENCY_GRID_ENABLED,
            AppDefaults.Visualization.Bars.frequencyGridEnabled
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_BAR_FPS_MODE,
            AppDefaults.Visualization.Bars.fpsMode.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_BAR_RENDER_BACKEND,
            AppDefaults.Visualization.Bars.renderBackend.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_BAR_COLOR_MODE_NO_ARTWORK,
            AppDefaults.Visualization.Bars.colorModeNoArtwork.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_BAR_COLOR_MODE_WITH_ARTWORK,
            AppDefaults.Visualization.Bars.colorModeWithArtwork.storageValue
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_BAR_CUSTOM_COLOR_ARGB,
            AppDefaults.Visualization.Bars.customColorArgb
        )
        .apply()
}

internal fun resetVisualizationOscilloscopeSettingsAction(
    prefs: SharedPreferences,
    onVisualizationOscStereoChanged: (Boolean) -> Unit
) {
    onVisualizationOscStereoChanged(AppDefaults.Visualization.Oscilloscope.stereo)
    prefs.edit()
        .putInt(
            AppPreferenceKeys.VISUALIZATION_OSC_WINDOW_MS,
            AppDefaults.Visualization.Oscilloscope.windowMs
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_OSC_TRIGGER_MODE,
            AppDefaults.Visualization.Oscilloscope.triggerMode.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_OSC_FPS_MODE,
            AppDefaults.Visualization.Oscilloscope.fpsMode.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_OSC_RENDER_BACKEND,
            AppDefaults.Visualization.Oscilloscope.renderBackend.storageValue
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_OSC_LINE_WIDTH_DP,
            AppDefaults.Visualization.Oscilloscope.lineWidthDp
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_OSC_GRID_WIDTH_DP,
            AppDefaults.Visualization.Oscilloscope.gridWidthDp
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_OSC_VERTICAL_GRID_ENABLED,
            AppDefaults.Visualization.Oscilloscope.verticalGridEnabled
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_OSC_CENTER_LINE_ENABLED,
            AppDefaults.Visualization.Oscilloscope.centerLineEnabled
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_OSC_LINE_COLOR_MODE_NO_ARTWORK,
            AppDefaults.Visualization.Oscilloscope.lineColorModeNoArtwork.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_OSC_GRID_COLOR_MODE_NO_ARTWORK,
            AppDefaults.Visualization.Oscilloscope.gridColorModeNoArtwork.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_OSC_LINE_COLOR_MODE_WITH_ARTWORK,
            AppDefaults.Visualization.Oscilloscope.lineColorModeWithArtwork.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_OSC_GRID_COLOR_MODE_WITH_ARTWORK,
            AppDefaults.Visualization.Oscilloscope.gridColorModeWithArtwork.storageValue
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_OSC_CUSTOM_LINE_COLOR_ARGB,
            AppDefaults.Visualization.Oscilloscope.customLineColorArgb
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_OSC_CUSTOM_GRID_COLOR_ARGB,
            AppDefaults.Visualization.Oscilloscope.customGridColorArgb
        )
        .apply()
}

internal fun resetVisualizationVuSettingsAction(
    prefs: SharedPreferences,
    onVisualizationVuAnchorChanged: (VisualizationVuAnchor) -> Unit,
    onVisualizationVuUseThemeColorChanged: (Boolean) -> Unit,
    onVisualizationVuSmoothingPercentChanged: (Int) -> Unit,
    onVisualizationVuRenderBackendChanged: (VisualizationRenderBackend) -> Unit
) {
    onVisualizationVuAnchorChanged(AppDefaults.Visualization.Vu.anchor)
    onVisualizationVuUseThemeColorChanged(AppDefaults.Visualization.Vu.useThemeColor)
    onVisualizationVuSmoothingPercentChanged(AppDefaults.Visualization.Vu.smoothingPercent)
    onVisualizationVuRenderBackendChanged(AppDefaults.Visualization.Vu.renderBackend)
    prefs.edit()
        .putString(
            AppPreferenceKeys.VISUALIZATION_VU_FPS_MODE,
            AppDefaults.Visualization.Vu.fpsMode.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_VU_RENDER_BACKEND,
            AppDefaults.Visualization.Vu.renderBackend.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_VU_COLOR_MODE_NO_ARTWORK,
            AppDefaults.Visualization.Vu.colorModeNoArtwork.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_VU_COLOR_MODE_WITH_ARTWORK,
            AppDefaults.Visualization.Vu.colorModeWithArtwork.storageValue
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_VU_CUSTOM_COLOR_ARGB,
            AppDefaults.Visualization.Vu.customColorArgb
        )
        .apply()
}

internal fun resetVisualizationChannelScopeSettingsAction(
    prefs: SharedPreferences,
    defaultScopeTextSizeSp: Int
) {
    prefs.edit()
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WINDOW_MS,
            AppDefaults.Visualization.ChannelScope.windowMs
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_RENDER_BACKEND,
            AppDefaults.Visualization.ChannelScope.renderBackend.storageValue
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_DC_REMOVAL_ENABLED,
            AppDefaults.Visualization.ChannelScope.dcRemovalEnabled
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GAIN_PERCENT,
            AppDefaults.Visualization.ChannelScope.gainPercent
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRIGGER_MODE,
            AppDefaults.Visualization.ChannelScope.triggerMode.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_FPS_MODE,
            AppDefaults.Visualization.ChannelScope.fpsMode.storageValue
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_WIDTH_DP,
            AppDefaults.Visualization.ChannelScope.lineWidthDp
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_WIDTH_DP,
            AppDefaults.Visualization.ChannelScope.gridWidthDp
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_VERTICAL_GRID_ENABLED,
            AppDefaults.Visualization.ChannelScope.verticalGridEnabled
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CENTER_LINE_ENABLED,
            AppDefaults.Visualization.ChannelScope.centerLineEnabled
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_SHOW_ARTWORK_BACKGROUND,
            AppDefaults.Visualization.ChannelScope.showArtworkBackground
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_BACKGROUND_MODE,
            AppDefaults.Visualization.ChannelScope.backgroundMode.storageValue
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_BACKGROUND_COLOR_ARGB,
            AppDefaults.Visualization.ChannelScope.customBackgroundColorArgb
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LAYOUT,
            AppDefaults.Visualization.ChannelScope.layout.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_NO_ARTWORK,
            AppDefaults.Visualization.ChannelScope.lineColorModeNoArtwork.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_NO_ARTWORK,
            AppDefaults.Visualization.ChannelScope.gridColorModeNoArtwork.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_WITH_ARTWORK,
            AppDefaults.Visualization.ChannelScope.lineColorModeWithArtwork.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_WITH_ARTWORK,
            AppDefaults.Visualization.ChannelScope.gridColorModeWithArtwork.storageValue
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_LINE_COLOR_ARGB,
            AppDefaults.Visualization.ChannelScope.customLineColorArgb
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_GRID_COLOR_ARGB,
            AppDefaults.Visualization.ChannelScope.customGridColorArgb
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_ENABLED,
            AppDefaults.Visualization.ChannelScope.textEnabled
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_ANCHOR,
            AppDefaults.Visualization.ChannelScope.textAnchor.storageValue
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_PADDING_DP,
            AppDefaults.Visualization.ChannelScope.textPaddingDp
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SIZE_SP,
            defaultScopeTextSizeSp
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_HIDE_WHEN_OVERFLOW,
            AppDefaults.Visualization.ChannelScope.textHideWhenOverflow
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHADOW_ENABLED,
            AppDefaults.Visualization.ChannelScope.textShadowEnabled
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_FONT,
            AppDefaults.Visualization.ChannelScope.textFont.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_COLOR_MODE,
            AppDefaults.Visualization.ChannelScope.textColorMode.storageValue
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_TEXT_COLOR_ARGB,
            AppDefaults.Visualization.ChannelScope.customTextColorArgb
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_NOTE_FORMAT,
            AppDefaults.Visualization.ChannelScope.textNoteFormat.storageValue
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_CHANNEL,
            AppDefaults.Visualization.ChannelScope.textShowChannel
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_NOTE,
            AppDefaults.Visualization.ChannelScope.textShowNote
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_VOLUME,
            AppDefaults.Visualization.ChannelScope.textShowVolume
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_EFFECT,
            AppDefaults.Visualization.ChannelScope.textShowEffect
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_INSTRUMENT_SAMPLE,
            AppDefaults.Visualization.ChannelScope.textShowInstrumentSample
        )
        .putBoolean(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_ENABLED,
            AppDefaults.Visualization.ChannelScope.textVuEnabled
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_ANCHOR,
            AppDefaults.Visualization.ChannelScope.textVuAnchor.storageValue
        )
        .putString(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_COLOR_MODE,
            AppDefaults.Visualization.ChannelScope.textVuColorMode.storageValue
        )
        .putInt(
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_CUSTOM_COLOR_ARGB,
            AppDefaults.Visualization.ChannelScope.textVuCustomColorArgb
        )
        .apply()
}

internal fun clearAllSettingsAction(
    context: Context,
    prefs: SharedPreferences,
    defaultScopeTextSizeSp: Int,
    selectableVisualizationModes: List<VisualizationMode>,
    onThemeModeChanged: (ThemeMode) -> Unit,
    ffmpegCoreSampleRateHz: Int,
    openMptCoreSampleRateHz: Int,
    vgmPlayCoreSampleRateHz: Int,
    gmeCoreSampleRateHz: Int,
    sidPlayFpCoreSampleRateHz: Int,
    lazyUsf2CoreSampleRateHz: Int,
    adPlugCoreSampleRateHz: Int,
    hivelyTrackerCoreSampleRateHz: Int,
    klystrackCoreSampleRateHz: Int,
    furnaceCoreSampleRateHz: Int,
    uadeCoreSampleRateHz: Int,
    adPlugOplEngine: Int,
    vio2sfInterpolationQuality: Int,
    sc68SamplingRateHz: Int,
    sc68Asid: Int,
    sc68DefaultTimeSeconds: Int,
    sc68YmEngine: Int,
    sc68YmVolModel: Int,
    vgmPlayLoopCount: Int,
    vgmPlayVsyncRate: Int,
    vgmPlayResampleMode: Int,
    vgmPlayChipSampleMode: Int,
    vgmPlayChipSampleRate: Int,
    gmeTempoPercent: Int,
    gmeStereoSeparationPercent: Int,
    gmeEqTrebleDecibel: Int,
    gmeEqBassHz: Int,
    gmeSpcInterpolation: Int,
    sidPlayFpBackend: Int,
    sidPlayFpClockMode: Int,
    sidPlayFpSidModelMode: Int,
    sidPlayFpFilterCurve6581Percent: Int,
    sidPlayFpFilterRange6581Percent: Int,
    sidPlayFpFilterCurve8580Percent: Int,
    sidPlayFpReSidFpCombinedWaveformsStrength: Int,
    openMptStereoSeparationPercent: Int,
    openMptStereoSeparationAmigaPercent: Int,
    openMptInterpolationFilterLength: Int,
    openMptAmigaResamplerMode: Int,
    openMptVolumeRampingStrength: Int,
    openMptMasterGainMilliBel: Int,
    vgmPlayAllowNonLoopingLoop: Boolean,
    gmeEchoEnabled: Boolean,
    gmeAccuracyEnabled: Boolean,
    gmeSpcUseBuiltInFade: Boolean,
    gmeSpcUseNativeSampleRate: Boolean,
    lazyUsf2UseHleAudio: Boolean,
    sc68AmigaFilter: Boolean,
    uadeFilterEnabled: Boolean,
    uadeNtscMode: Boolean,
    sidPlayFpFilter6581Enabled: Boolean,
    sidPlayFpFilter8580Enabled: Boolean,
    sidPlayFpDigiBoost8580: Boolean,
    sidPlayFpReSidFpFastSampling: Boolean,
    openMptAmigaResamplerApplyAllModules: Boolean,
    openMptFt2XmVolumeRamping: Boolean,
    openMptSurroundEnabled: Boolean,
    sc68AmigaBlend: Int,
    sc68AmigaClock: Int,
    uadePanningMode: Int,
    hivelyTrackerPanningMode: Int,
    hivelyTrackerMixGainPercent: Int,
    klystrackPlayerQuality: Int,
    furnaceYm2612Core: Int,
    furnaceSnCore: Int,
    furnaceNesCore: Int,
    furnaceC64Core: Int,
    furnaceGbQuality: Int,
    furnaceDsidQuality: Int,
    furnaceAyCore: Int,
    vgmPlayChipCoreSelections: Map<String, Int>,
    onAutoPlayOnTrackSelectChanged: (Boolean) -> Unit,
    onOpenPlayerOnTrackSelectChanged: (Boolean) -> Unit,
    onAutoPlayNextTrackOnEndChanged: (Boolean) -> Unit,
    onPlaylistWrapNavigationChanged: (Boolean) -> Unit,
    onPreviousRestartsAfterThresholdChanged: (Boolean) -> Unit,
    onFadePauseResumeChanged: (Boolean) -> Unit,
    onRespondHeadphoneMediaButtonsChanged: (Boolean) -> Unit,
    onPauseOnHeadphoneDisconnectChanged: (Boolean) -> Unit,
    onAudioBackendPreferenceChanged: (AudioBackendPreference) -> Unit,
    onAudioPerformanceModeChanged: (AudioPerformanceMode) -> Unit,
    onAudioBufferPresetChanged: (AudioBufferPreset) -> Unit,
    onAudioResamplerPreferenceChanged: (AudioResamplerPreference) -> Unit,
    onAudioOutputLimiterEnabledChanged: (Boolean) -> Unit,
    onAudioAllowBackendFallbackChanged: (Boolean) -> Unit,
    onOpenPlayerFromNotificationChanged: (Boolean) -> Unit,
    onPersistRepeatModeChanged: (Boolean) -> Unit,
    onPreferredRepeatModeChanged: (RepeatMode) -> Unit,
    onRememberBrowserLocationChanged: (Boolean) -> Unit,
    onShowParentDirectoryEntryChanged: (Boolean) -> Unit,
    onShowFileIconChipBackgroundChanged: (Boolean) -> Unit,
    onBrowserNameSortModeChanged: (BrowserNameSortMode) -> Unit,
    onUrlCacheClearOnLaunchChanged: (Boolean) -> Unit,
    onUrlCacheMaxTracksChanged: (Int) -> Unit,
    onUrlCacheMaxBytesChanged: (Long) -> Unit,
    onArchiveCacheClearOnLaunchChanged: (Boolean) -> Unit,
    onArchiveCacheMaxMountsChanged: (Int) -> Unit,
    onArchiveCacheMaxBytesChanged: (Long) -> Unit,
    onArchiveCacheMaxAgeDaysChanged: (Int) -> Unit,
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
    onSc68SamplingRateHzChanged: (Int) -> Unit,
    onSc68AsidChanged: (Int) -> Unit,
    onSc68DefaultTimeSecondsChanged: (Int) -> Unit,
    onSc68YmEngineChanged: (Int) -> Unit,
    onSc68YmVolModelChanged: (Int) -> Unit,
    onSc68AmigaFilterChanged: (Boolean) -> Unit,
    onSc68AmigaBlendChanged: (Int) -> Unit,
    onSc68AmigaClockChanged: (Int) -> Unit,
    onUadeCoreSampleRateHzChanged: (Int) -> Unit,
    onUadeFilterEnabledChanged: (Boolean) -> Unit,
    onUadeNtscModeChanged: (Boolean) -> Unit,
    onUadePanningModeChanged: (Int) -> Unit,
    onHivelyTrackerCoreSampleRateHzChanged: (Int) -> Unit,
    onKlystrackCoreSampleRateHzChanged: (Int) -> Unit,
    onFurnaceCoreSampleRateHzChanged: (Int) -> Unit,
    onHivelyTrackerPanningModeChanged: (Int) -> Unit,
    onHivelyTrackerMixGainPercentChanged: (Int) -> Unit,
    onKlystrackPlayerQualityChanged: (Int) -> Unit,
    onFurnaceYm2612CoreChanged: (Int) -> Unit,
    onFurnaceSnCoreChanged: (Int) -> Unit,
    onFurnaceNesCoreChanged: (Int) -> Unit,
    onFurnaceC64CoreChanged: (Int) -> Unit,
    onFurnaceGbQualityChanged: (Int) -> Unit,
    onFurnaceDsidQualityChanged: (Int) -> Unit,
    onFurnaceAyCoreChanged: (Int) -> Unit,
    onAdPlugCoreSampleRateHzChanged: (Int) -> Unit,
    onAdPlugOplEngineChanged: (Int) -> Unit,
) {
    val pluginSnapshot = mapOf(
        CorePreferenceKeys.CORE_RATE_FFMPEG to ffmpegCoreSampleRateHz,
        CorePreferenceKeys.CORE_RATE_OPENMPT to openMptCoreSampleRateHz,
        CorePreferenceKeys.CORE_RATE_VGMPLAY to vgmPlayCoreSampleRateHz,
        CorePreferenceKeys.CORE_RATE_GME to gmeCoreSampleRateHz,
        CorePreferenceKeys.CORE_RATE_SIDPLAYFP to sidPlayFpCoreSampleRateHz,
        CorePreferenceKeys.CORE_RATE_LAZYUSF2 to lazyUsf2CoreSampleRateHz,
        CorePreferenceKeys.CORE_RATE_ADPLUG to adPlugCoreSampleRateHz,
        CorePreferenceKeys.CORE_RATE_HIVELYTRACKER to hivelyTrackerCoreSampleRateHz,
        CorePreferenceKeys.CORE_RATE_KLYSTRACK to klystrackCoreSampleRateHz,
        CorePreferenceKeys.CORE_RATE_FURNACE to furnaceCoreSampleRateHz,
        CorePreferenceKeys.CORE_RATE_UADE to uadeCoreSampleRateHz,
        CorePreferenceKeys.VIO2SF_INTERPOLATION_QUALITY to vio2sfInterpolationQuality,
        CorePreferenceKeys.ADPLUG_OPL_ENGINE to adPlugOplEngine,
        CorePreferenceKeys.CORE_RATE_SC68 to sc68SamplingRateHz,
        CorePreferenceKeys.SC68_ASID to sc68Asid,
        CorePreferenceKeys.SC68_DEFAULT_TIME_SECONDS to sc68DefaultTimeSeconds,
        CorePreferenceKeys.SC68_YM_ENGINE to sc68YmEngine,
        CorePreferenceKeys.SC68_YM_VOLMODEL to sc68YmVolModel,
        CorePreferenceKeys.SC68_AMIGA_BLEND to sc68AmigaBlend,
        CorePreferenceKeys.SC68_AMIGA_CLOCK to sc68AmigaClock,
        CorePreferenceKeys.UADE_PANNING_MODE to uadePanningMode,
        CorePreferenceKeys.HIVELYTRACKER_PANNING_MODE to hivelyTrackerPanningMode,
        CorePreferenceKeys.HIVELYTRACKER_MIX_GAIN_PERCENT to hivelyTrackerMixGainPercent,
        CorePreferenceKeys.KLYSTRACK_PLAYER_QUALITY to klystrackPlayerQuality,
        CorePreferenceKeys.FURNACE_YM2612_CORE to furnaceYm2612Core,
        CorePreferenceKeys.FURNACE_SN_CORE to furnaceSnCore,
        CorePreferenceKeys.FURNACE_NES_CORE to furnaceNesCore,
        CorePreferenceKeys.FURNACE_C64_CORE to furnaceC64Core,
        CorePreferenceKeys.FURNACE_GB_QUALITY to furnaceGbQuality,
        CorePreferenceKeys.FURNACE_DSID_QUALITY to furnaceDsidQuality,
        CorePreferenceKeys.FURNACE_AY_CORE to furnaceAyCore,
        CorePreferenceKeys.VGMPLAY_LOOP_COUNT to vgmPlayLoopCount,
        CorePreferenceKeys.VGMPLAY_VSYNC_RATE to vgmPlayVsyncRate,
        CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE to vgmPlayResampleMode,
        CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE to vgmPlayChipSampleMode,
        CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE to vgmPlayChipSampleRate,
        CorePreferenceKeys.GME_TEMPO_PERCENT to gmeTempoPercent,
        CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT to gmeStereoSeparationPercent,
        CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL to gmeEqTrebleDecibel,
        CorePreferenceKeys.GME_EQ_BASS_HZ to gmeEqBassHz,
        CorePreferenceKeys.GME_SPC_INTERPOLATION to gmeSpcInterpolation,
        CorePreferenceKeys.SIDPLAYFP_BACKEND to sidPlayFpBackend,
        CorePreferenceKeys.SIDPLAYFP_CLOCK_MODE to sidPlayFpClockMode,
        CorePreferenceKeys.SIDPLAYFP_SID_MODEL_MODE to sidPlayFpSidModelMode,
        CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_6581 to sidPlayFpFilterCurve6581Percent,
        CorePreferenceKeys.SIDPLAYFP_FILTER_RANGE_6581 to sidPlayFpFilterRange6581Percent,
        CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_8580 to sidPlayFpFilterCurve8580Percent,
        CorePreferenceKeys.SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH to sidPlayFpReSidFpCombinedWaveformsStrength,
        CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT to openMptStereoSeparationPercent,
        CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT to openMptStereoSeparationAmigaPercent,
        CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH to openMptInterpolationFilterLength,
        CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE to openMptAmigaResamplerMode,
        CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH to openMptVolumeRampingStrength,
        CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL to openMptMasterGainMilliBel
    )
    val pluginBooleanSnapshot = mapOf(
        CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP to vgmPlayAllowNonLoopingLoop,
        CorePreferenceKeys.GME_ECHO_ENABLED to gmeEchoEnabled,
        CorePreferenceKeys.GME_ACCURACY_ENABLED to gmeAccuracyEnabled,
        CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE to gmeSpcUseBuiltInFade,
        CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE to gmeSpcUseNativeSampleRate,
        CorePreferenceKeys.LAZYUSF2_USE_HLE_AUDIO to lazyUsf2UseHleAudio,
        CorePreferenceKeys.SC68_AMIGA_FILTER to sc68AmigaFilter,
        CorePreferenceKeys.UADE_FILTER_ENABLED to uadeFilterEnabled,
        CorePreferenceKeys.UADE_NTSC_MODE to uadeNtscMode,
        CorePreferenceKeys.SIDPLAYFP_FILTER_6581_ENABLED to sidPlayFpFilter6581Enabled,
        CorePreferenceKeys.SIDPLAYFP_FILTER_8580_ENABLED to sidPlayFpFilter8580Enabled,
        CorePreferenceKeys.SIDPLAYFP_DIGI_BOOST_8580 to sidPlayFpDigiBoost8580,
        CorePreferenceKeys.SIDPLAYFP_RESIDFP_FAST_SAMPLING to sidPlayFpReSidFpFastSampling,
        CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES to openMptAmigaResamplerApplyAllModules,
        CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING to openMptFt2XmVolumeRamping,
        CorePreferenceKeys.OPENMPT_SURROUND_ENABLED to openMptSurroundEnabled
    )
    val vgmChipCoreSnapshot = vgmPlayChipCoreSelections

    prefs.edit().clear().apply()

    val restoreEditor = prefs.edit()
    pluginSnapshot.forEach { (key, value) -> restoreEditor.putInt(key, value) }
    pluginBooleanSnapshot.forEach { (key, value) -> restoreEditor.putBoolean(key, value) }
    vgmChipCoreSnapshot.forEach { (chipKey, value) ->
        restoreEditor.putInt(CorePreferenceKeys.vgmPlayChipCoreKey(chipKey), value)
    }
    restoreEditor.apply()

    onAutoPlayOnTrackSelectChanged(true)
    onOpenPlayerOnTrackSelectChanged(true)
    onAutoPlayNextTrackOnEndChanged(true)
    onPlaylistWrapNavigationChanged(true)
    onPreviousRestartsAfterThresholdChanged(true)
    onFadePauseResumeChanged(AppDefaults.Player.fadePauseResume)
    onRespondHeadphoneMediaButtonsChanged(true)
    onPauseOnHeadphoneDisconnectChanged(true)
    onAudioBackendPreferenceChanged(AudioBackendPreference.AAudio)
    onAudioPerformanceModeChanged(AudioBackendPreference.AAudio.defaultPerformanceMode())
    onAudioBufferPresetChanged(AudioBackendPreference.AAudio.defaultBufferPreset())
    onAudioResamplerPreferenceChanged(AudioResamplerPreference.BuiltIn)
    onAudioOutputLimiterEnabledChanged(AppDefaults.AudioProcessing.outputLimiterEnabled)
    onAudioAllowBackendFallbackChanged(true)
    onOpenPlayerFromNotificationChanged(true)
    onPersistRepeatModeChanged(true)
    onPreferredRepeatModeChanged(RepeatMode.None)
    onRememberBrowserLocationChanged(true)
    onShowParentDirectoryEntryChanged(AppDefaults.Browser.showParentDirectoryEntry)
    onShowFileIconChipBackgroundChanged(AppDefaults.Browser.showFileIconChipBackground)
    onBrowserNameSortModeChanged(AppDefaults.Browser.nameSortMode)
    onUrlCacheClearOnLaunchChanged(false)
    onUrlCacheMaxTracksChanged(SOURCE_CACHE_MAX_TRACKS_DEFAULT)
    onUrlCacheMaxBytesChanged(SOURCE_CACHE_MAX_BYTES_DEFAULT)
    onArchiveCacheClearOnLaunchChanged(false)
    onArchiveCacheMaxMountsChanged(ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT)
    onArchiveCacheMaxBytesChanged(ARCHIVE_CACHE_MAX_BYTES_DEFAULT)
    onArchiveCacheMaxAgeDaysChanged(ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT)
    onLastBrowserLocationIdChanged(null)
    onLastBrowserDirectoryPathChanged(null)
    onRecentFoldersLimitChanged(RECENT_FOLDERS_LIMIT_DEFAULT)
    onRecentFilesLimitChanged(RECENT_FILES_LIMIT_DEFAULT)
    onRecentFoldersChanged(emptyList())
    onRecentPlayedFilesChanged(emptyList())
    onKeepScreenOnChanged(AppDefaults.Player.keepScreenOn)
    onPlayerArtworkCornerRadiusDpChanged(AppDefaults.Player.artworkCornerRadiusDp)
    onFilenameDisplayModeChanged(FilenameDisplayMode.Always)
    onFilenameOnlyWhenTitleMissingChanged(false)
    onUnknownTrackDurationSecondsChanged(GmeDefaults.unknownDurationSeconds)
    onAdPlugCoreSampleRateHzChanged(AdPlugDefaults.coreSampleRateHz)
    onAdPlugOplEngineChanged(AdPlugDefaults.oplEngine)
    onSc68SamplingRateHzChanged(Sc68Defaults.coreSampleRateHz)
    onSc68AsidChanged(Sc68Defaults.asid)
    onSc68DefaultTimeSecondsChanged(Sc68Defaults.defaultTimeSeconds)
    onSc68YmEngineChanged(Sc68Defaults.ymEngine)
    onSc68YmVolModelChanged(Sc68Defaults.ymVolModel)
    onSc68AmigaFilterChanged(Sc68Defaults.amigaFilter)
    onSc68AmigaBlendChanged(Sc68Defaults.amigaBlend)
    onSc68AmigaClockChanged(Sc68Defaults.amigaClock)
    onUadeCoreSampleRateHzChanged(UadeDefaults.coreSampleRateHz)
    onUadeFilterEnabledChanged(UadeDefaults.filterEnabled)
    onUadeNtscModeChanged(UadeDefaults.ntscMode)
    onUadePanningModeChanged(UadeDefaults.panningMode)
    onHivelyTrackerCoreSampleRateHzChanged(HivelyTrackerDefaults.coreSampleRateHz)
    onKlystrackCoreSampleRateHzChanged(KlystrackDefaults.coreSampleRateHz)
    onFurnaceCoreSampleRateHzChanged(FurnaceDefaults.coreSampleRateHz)
    onHivelyTrackerPanningModeChanged(HivelyTrackerDefaults.panningMode)
    onHivelyTrackerMixGainPercentChanged(HivelyTrackerDefaults.mixGainPercent)
    onKlystrackPlayerQualityChanged(KlystrackDefaults.playerQuality)
    onFurnaceYm2612CoreChanged(FurnaceDefaults.ym2612Core)
    onFurnaceSnCoreChanged(FurnaceDefaults.snCore)
    onFurnaceNesCoreChanged(FurnaceDefaults.nesCore)
    onFurnaceC64CoreChanged(FurnaceDefaults.c64Core)
    onFurnaceGbQualityChanged(FurnaceDefaults.gbQuality)
    onFurnaceDsidQualityChanged(FurnaceDefaults.dsidQuality)
    onFurnaceAyCoreChanged(FurnaceDefaults.ayCore)
    onEndFadeApplyToAllTracksChanged(AppDefaults.Player.endFadeApplyToAllTracks)
    onEndFadeDurationMsChanged(AppDefaults.Player.endFadeDurationMs)
    onEndFadeCurveChanged(AppDefaults.Player.endFadeCurve)
    onVisualizationModeChanged(VisualizationMode.Off)
    onEnabledVisualizationModesChanged(selectableVisualizationModes.toSet())
    onVisualizationShowDebugInfoChanged(AppDefaults.Visualization.showDebugInfo)

    resetVisualizationBarsSettingsAction(
        prefs = prefs,
        onBarCountChanged = onVisualizationBarCountChanged,
        onBarSmoothingPercentChanged = onVisualizationBarSmoothingPercentChanged,
        onBarRoundnessDpChanged = onVisualizationBarRoundnessDpChanged,
        onBarOverlayArtworkChanged = onVisualizationBarOverlayArtworkChanged,
        onBarUseThemeColorChanged = onVisualizationBarUseThemeColorChanged,
        onBarRenderBackendChanged = onVisualizationBarRenderBackendChanged
    )
    resetVisualizationOscilloscopeSettingsAction(
        prefs = prefs,
        onVisualizationOscStereoChanged = onVisualizationOscStereoChanged
    )
    resetVisualizationVuSettingsAction(
        prefs = prefs,
        onVisualizationVuAnchorChanged = onVisualizationVuAnchorChanged,
        onVisualizationVuUseThemeColorChanged = onVisualizationVuUseThemeColorChanged,
        onVisualizationVuSmoothingPercentChanged = onVisualizationVuSmoothingPercentChanged,
        onVisualizationVuRenderBackendChanged = onVisualizationVuRenderBackendChanged
    )
    resetVisualizationChannelScopeSettingsAction(
        prefs = prefs,
        defaultScopeTextSizeSp = defaultScopeTextSizeSp
    )

    onThemeModeChanged(ThemeMode.Auto)
    Toast.makeText(context, "All app settings cleared", Toast.LENGTH_SHORT).show()
}

internal fun clearAllPluginSettingsAction(
    context: Context,
    prefs: SharedPreferences,
    onFfmpegCoreSampleRateHzChanged: (Int) -> Unit,
    onFfmpegGaplessRepeatTrackChanged: (Boolean) -> Unit,
    onOpenMptCoreSampleRateHzChanged: (Int) -> Unit,
    onVgmPlayCoreSampleRateHzChanged: (Int) -> Unit,
    onGmeCoreSampleRateHzChanged: (Int) -> Unit,
    onSidPlayFpCoreSampleRateHzChanged: (Int) -> Unit,
    onLazyUsf2CoreSampleRateHzChanged: (Int) -> Unit,
    onAdPlugCoreSampleRateHzChanged: (Int) -> Unit,
    onHivelyTrackerCoreSampleRateHzChanged: (Int) -> Unit,
    onKlystrackCoreSampleRateHzChanged: (Int) -> Unit,
    onFurnaceCoreSampleRateHzChanged: (Int) -> Unit,
    onUadeCoreSampleRateHzChanged: (Int) -> Unit,
    onAdPlugOplEngineChanged: (Int) -> Unit,
    onLazyUsf2UseHleAudioChanged: (Boolean) -> Unit,
    onVio2sfInterpolationQualityChanged: (Int) -> Unit,
    onSc68SamplingRateHzChanged: (Int) -> Unit,
    onSc68AsidChanged: (Int) -> Unit,
    onSc68DefaultTimeSecondsChanged: (Int) -> Unit,
    onSc68YmEngineChanged: (Int) -> Unit,
    onSc68YmVolModelChanged: (Int) -> Unit,
    onSc68AmigaFilterChanged: (Boolean) -> Unit,
    onSc68AmigaBlendChanged: (Int) -> Unit,
    onSc68AmigaClockChanged: (Int) -> Unit,
    onUadeFilterEnabledChanged: (Boolean) -> Unit,
    onUadeNtscModeChanged: (Boolean) -> Unit,
    onUadePanningModeChanged: (Int) -> Unit,
    onHivelyTrackerPanningModeChanged: (Int) -> Unit,
    onHivelyTrackerMixGainPercentChanged: (Int) -> Unit,
    onKlystrackPlayerQualityChanged: (Int) -> Unit,
    onFurnaceYm2612CoreChanged: (Int) -> Unit,
    onFurnaceSnCoreChanged: (Int) -> Unit,
    onFurnaceNesCoreChanged: (Int) -> Unit,
    onFurnaceC64CoreChanged: (Int) -> Unit,
    onFurnaceGbQualityChanged: (Int) -> Unit,
    onFurnaceDsidQualityChanged: (Int) -> Unit,
    onFurnaceAyCoreChanged: (Int) -> Unit,
    onSidPlayFpBackendChanged: (Int) -> Unit,
    onSidPlayFpClockModeChanged: (Int) -> Unit,
    onSidPlayFpSidModelModeChanged: (Int) -> Unit,
    onSidPlayFpFilter6581EnabledChanged: (Boolean) -> Unit,
    onSidPlayFpFilter8580EnabledChanged: (Boolean) -> Unit,
    onSidPlayFpDigiBoost8580Changed: (Boolean) -> Unit,
    onSidPlayFpFilterCurve6581PercentChanged: (Int) -> Unit,
    onSidPlayFpFilterRange6581PercentChanged: (Int) -> Unit,
    onSidPlayFpFilterCurve8580PercentChanged: (Int) -> Unit,
    onSidPlayFpReSidFpFastSamplingChanged: (Boolean) -> Unit,
    onSidPlayFpReSidFpCombinedWaveformsStrengthChanged: (Int) -> Unit,
    onGmeTempoPercentChanged: (Int) -> Unit,
    onGmeStereoSeparationPercentChanged: (Int) -> Unit,
    onGmeEchoEnabledChanged: (Boolean) -> Unit,
    onGmeAccuracyEnabledChanged: (Boolean) -> Unit,
    onGmeEqTrebleDecibelChanged: (Int) -> Unit,
    onGmeEqBassHzChanged: (Int) -> Unit,
    onGmeSpcUseBuiltInFadeChanged: (Boolean) -> Unit,
    onGmeSpcInterpolationChanged: (Int) -> Unit,
    onGmeSpcUseNativeSampleRateChanged: (Boolean) -> Unit,
    onVgmPlayLoopCountChanged: (Int) -> Unit,
    onVgmPlayAllowNonLoopingLoopChanged: (Boolean) -> Unit,
    onVgmPlayVsyncRateChanged: (Int) -> Unit,
    onVgmPlayResampleModeChanged: (Int) -> Unit,
    onVgmPlayChipSampleModeChanged: (Int) -> Unit,
    onVgmPlayChipSampleRateChanged: (Int) -> Unit,
    onVgmPlayChipCoreSelectionsChanged: (Map<String, Int>) -> Unit,
    onOpenMptStereoSeparationPercentChanged: (Int) -> Unit,
    onOpenMptStereoSeparationAmigaPercentChanged: (Int) -> Unit,
    onOpenMptInterpolationFilterLengthChanged: (Int) -> Unit,
    onOpenMptAmigaResamplerModeChanged: (Int) -> Unit,
    onOpenMptAmigaResamplerApplyAllModulesChanged: (Boolean) -> Unit,
    onOpenMptVolumeRampingStrengthChanged: (Int) -> Unit,
    onOpenMptFt2XmVolumeRampingChanged: (Boolean) -> Unit,
    onOpenMptMasterGainMilliBelChanged: (Int) -> Unit,
    onOpenMptSurroundEnabledChanged: (Boolean) -> Unit
) {
    onFfmpegCoreSampleRateHzChanged(0)
    onFfmpegGaplessRepeatTrackChanged(FfmpegDefaults.gaplessRepeatTrack)
    onOpenMptCoreSampleRateHzChanged(OpenMptDefaults.coreSampleRateHz)
    onVgmPlayCoreSampleRateHzChanged(VgmPlayDefaults.coreSampleRateHz)
    onGmeCoreSampleRateHzChanged(GmeDefaults.coreSampleRateHz)
    onSidPlayFpCoreSampleRateHzChanged(SidPlayFpDefaults.coreSampleRateHz)
    onLazyUsf2CoreSampleRateHzChanged(LazyUsf2Defaults.coreSampleRateHz)
    onAdPlugCoreSampleRateHzChanged(AdPlugDefaults.coreSampleRateHz)
    onHivelyTrackerCoreSampleRateHzChanged(HivelyTrackerDefaults.coreSampleRateHz)
    onKlystrackCoreSampleRateHzChanged(KlystrackDefaults.coreSampleRateHz)
    onFurnaceCoreSampleRateHzChanged(FurnaceDefaults.coreSampleRateHz)
    onUadeCoreSampleRateHzChanged(UadeDefaults.coreSampleRateHz)
    onAdPlugOplEngineChanged(AdPlugDefaults.oplEngine)
    onLazyUsf2UseHleAudioChanged(LazyUsf2Defaults.useHleAudio)
    onVio2sfInterpolationQualityChanged(Vio2sfDefaults.interpolationQuality)
    onSc68SamplingRateHzChanged(Sc68Defaults.coreSampleRateHz)
    onSc68AsidChanged(Sc68Defaults.asid)
    onSc68DefaultTimeSecondsChanged(Sc68Defaults.defaultTimeSeconds)
    onSc68YmEngineChanged(Sc68Defaults.ymEngine)
    onSc68YmVolModelChanged(Sc68Defaults.ymVolModel)
    onSc68AmigaFilterChanged(Sc68Defaults.amigaFilter)
    onSc68AmigaBlendChanged(Sc68Defaults.amigaBlend)
    onSc68AmigaClockChanged(Sc68Defaults.amigaClock)
    onUadeFilterEnabledChanged(UadeDefaults.filterEnabled)
    onUadeNtscModeChanged(UadeDefaults.ntscMode)
    onUadePanningModeChanged(UadeDefaults.panningMode)
    onHivelyTrackerPanningModeChanged(HivelyTrackerDefaults.panningMode)
    onHivelyTrackerMixGainPercentChanged(HivelyTrackerDefaults.mixGainPercent)
    onKlystrackPlayerQualityChanged(KlystrackDefaults.playerQuality)
    onFurnaceYm2612CoreChanged(FurnaceDefaults.ym2612Core)
    onFurnaceSnCoreChanged(FurnaceDefaults.snCore)
    onFurnaceNesCoreChanged(FurnaceDefaults.nesCore)
    onFurnaceC64CoreChanged(FurnaceDefaults.c64Core)
    onFurnaceGbQualityChanged(FurnaceDefaults.gbQuality)
    onFurnaceDsidQualityChanged(FurnaceDefaults.dsidQuality)
    onFurnaceAyCoreChanged(FurnaceDefaults.ayCore)
    onSidPlayFpBackendChanged(SidPlayFpDefaults.backend)
    onSidPlayFpClockModeChanged(SidPlayFpDefaults.clockMode)
    onSidPlayFpSidModelModeChanged(SidPlayFpDefaults.sidModelMode)
    onSidPlayFpFilter6581EnabledChanged(SidPlayFpDefaults.filter6581Enabled)
    onSidPlayFpFilter8580EnabledChanged(SidPlayFpDefaults.filter8580Enabled)
    onSidPlayFpDigiBoost8580Changed(SidPlayFpDefaults.digiBoost8580)
    onSidPlayFpFilterCurve6581PercentChanged(SidPlayFpDefaults.filterCurve6581Percent)
    onSidPlayFpFilterRange6581PercentChanged(SidPlayFpDefaults.filterRange6581Percent)
    onSidPlayFpFilterCurve8580PercentChanged(SidPlayFpDefaults.filterCurve8580Percent)
    onSidPlayFpReSidFpFastSamplingChanged(SidPlayFpDefaults.reSidFpFastSampling)
    onSidPlayFpReSidFpCombinedWaveformsStrengthChanged(SidPlayFpDefaults.reSidFpCombinedWaveformsStrength)
    onGmeTempoPercentChanged(GmeDefaults.tempoPercent)
    onGmeStereoSeparationPercentChanged(GmeDefaults.stereoSeparationPercent)
    onGmeEchoEnabledChanged(GmeDefaults.echoEnabled)
    onGmeAccuracyEnabledChanged(GmeDefaults.accuracyEnabled)
    onGmeEqTrebleDecibelChanged(GmeDefaults.eqTrebleDecibel)
    onGmeEqBassHzChanged(GmeDefaults.eqBassHz)
    onGmeSpcUseBuiltInFadeChanged(GmeDefaults.spcUseBuiltInFade)
    onGmeSpcInterpolationChanged(GmeDefaults.spcInterpolation)
    onGmeSpcUseNativeSampleRateChanged(GmeDefaults.spcUseNativeSampleRate)
    onVgmPlayLoopCountChanged(VgmPlayDefaults.loopCount)
    onVgmPlayAllowNonLoopingLoopChanged(VgmPlayDefaults.allowNonLoopingLoop)
    onVgmPlayVsyncRateChanged(VgmPlayDefaults.vsyncRate)
    onVgmPlayResampleModeChanged(VgmPlayDefaults.resampleMode)
    onVgmPlayChipSampleModeChanged(VgmPlayDefaults.chipSampleMode)
    onVgmPlayChipSampleRateChanged(VgmPlayDefaults.chipSampleRate)
    onVgmPlayChipCoreSelectionsChanged(VgmPlayConfig.defaultChipCoreSelections())
    onOpenMptStereoSeparationPercentChanged(OpenMptDefaults.stereoSeparationPercent)
    onOpenMptStereoSeparationAmigaPercentChanged(OpenMptDefaults.stereoSeparationAmigaPercent)
    onOpenMptInterpolationFilterLengthChanged(OpenMptDefaults.interpolationFilterLength)
    onOpenMptAmigaResamplerModeChanged(OpenMptDefaults.amigaResamplerMode)
    onOpenMptAmigaResamplerApplyAllModulesChanged(OpenMptDefaults.amigaResamplerApplyAllModules)
    onOpenMptVolumeRampingStrengthChanged(OpenMptDefaults.volumeRampingStrength)
    onOpenMptFt2XmVolumeRampingChanged(OpenMptDefaults.ft2XmVolumeRamping)
    onOpenMptMasterGainMilliBelChanged(OpenMptDefaults.masterGainMilliBel)
    onOpenMptSurroundEnabledChanged(OpenMptDefaults.surroundEnabled)

    prefs.edit().apply {
        remove(CorePreferenceKeys.CORE_RATE_FFMPEG)
        remove(CorePreferenceKeys.FFMPEG_GAPLESS_REPEAT_TRACK)
        remove(CorePreferenceKeys.CORE_RATE_OPENMPT)
        remove(CorePreferenceKeys.CORE_RATE_VGMPLAY)
        remove(CorePreferenceKeys.CORE_RATE_GME)
        remove(CorePreferenceKeys.CORE_RATE_SIDPLAYFP)
        remove(CorePreferenceKeys.CORE_RATE_LAZYUSF2)
        remove(CorePreferenceKeys.CORE_RATE_ADPLUG)
        remove(CorePreferenceKeys.CORE_RATE_HIVELYTRACKER)
        remove(CorePreferenceKeys.CORE_RATE_KLYSTRACK)
        remove(CorePreferenceKeys.CORE_RATE_FURNACE)
        remove(CorePreferenceKeys.CORE_RATE_UADE)
        remove(CorePreferenceKeys.VIO2SF_INTERPOLATION_QUALITY)
        remove(CorePreferenceKeys.LAZYUSF2_USE_HLE_AUDIO)
        remove(CorePreferenceKeys.ADPLUG_OPL_ENGINE)
        remove(CorePreferenceKeys.CORE_RATE_SC68)
        remove(CorePreferenceKeys.SC68_ASID)
        remove(CorePreferenceKeys.SC68_DEFAULT_TIME_SECONDS)
        remove(CorePreferenceKeys.SC68_YM_ENGINE)
        remove(CorePreferenceKeys.SC68_YM_VOLMODEL)
        remove(CorePreferenceKeys.SC68_AMIGA_FILTER)
        remove(CorePreferenceKeys.SC68_AMIGA_BLEND)
        remove(CorePreferenceKeys.SC68_AMIGA_CLOCK)
        remove(CorePreferenceKeys.UADE_FILTER_ENABLED)
        remove(CorePreferenceKeys.UADE_NTSC_MODE)
        remove(CorePreferenceKeys.UADE_PANNING_MODE)
        remove(CorePreferenceKeys.HIVELYTRACKER_PANNING_MODE)
        remove(CorePreferenceKeys.HIVELYTRACKER_MIX_GAIN_PERCENT)
        remove(CorePreferenceKeys.KLYSTRACK_PLAYER_QUALITY)
        remove(CorePreferenceKeys.FURNACE_YM2612_CORE)
        remove(CorePreferenceKeys.FURNACE_SN_CORE)
        remove(CorePreferenceKeys.FURNACE_NES_CORE)
        remove(CorePreferenceKeys.FURNACE_C64_CORE)
        remove(CorePreferenceKeys.FURNACE_GB_QUALITY)
        remove(CorePreferenceKeys.FURNACE_DSID_QUALITY)
        remove(CorePreferenceKeys.FURNACE_AY_CORE)
        remove(CorePreferenceKeys.SIDPLAYFP_BACKEND)
        remove(CorePreferenceKeys.SIDPLAYFP_CLOCK_MODE)
        remove(CorePreferenceKeys.SIDPLAYFP_SID_MODEL_MODE)
        remove(CorePreferenceKeys.SIDPLAYFP_FILTER_6581_ENABLED)
        remove(CorePreferenceKeys.SIDPLAYFP_FILTER_8580_ENABLED)
        remove(CorePreferenceKeys.SIDPLAYFP_DIGI_BOOST_8580)
        remove(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_6581)
        remove(CorePreferenceKeys.SIDPLAYFP_FILTER_RANGE_6581)
        remove(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_8580)
        remove(CorePreferenceKeys.SIDPLAYFP_RESIDFP_FAST_SAMPLING)
        remove(CorePreferenceKeys.SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH)
        remove(CorePreferenceKeys.GME_TEMPO_PERCENT)
        remove(CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT)
        remove(CorePreferenceKeys.GME_ECHO_ENABLED)
        remove(CorePreferenceKeys.GME_ACCURACY_ENABLED)
        remove(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL)
        remove(CorePreferenceKeys.GME_EQ_BASS_HZ)
        remove(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE)
        remove(CorePreferenceKeys.GME_SPC_INTERPOLATION)
        remove(CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE)
        remove(CorePreferenceKeys.VGMPLAY_LOOP_COUNT)
        remove(CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP)
        remove(CorePreferenceKeys.VGMPLAY_VSYNC_RATE)
        remove(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE)
        remove(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE)
        remove(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE)
        remove(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT)
        remove(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT)
        remove(CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH)
        remove(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE)
        remove(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES)
        remove(CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH)
        remove(CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING)
        remove(CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL)
        remove(CorePreferenceKeys.OPENMPT_SURROUND_ENABLED)
        VgmPlayConfig.chipCoreSpecs.forEach { spec ->
            remove(CorePreferenceKeys.vgmPlayChipCoreKey(spec.key))
        }
        apply()
    }

    Toast.makeText(context, "Core settings cleared", Toast.LENGTH_SHORT).show()
}

internal fun resetPluginSettingsAction(
    context: Context,
    prefs: SharedPreferences,
    pluginName: String,
    onFfmpegCoreSampleRateHzChanged: (Int) -> Unit,
    onFfmpegGaplessRepeatTrackChanged: (Boolean) -> Unit,
    onOpenMptCoreSampleRateHzChanged: (Int) -> Unit,
    onOpenMptStereoSeparationPercentChanged: (Int) -> Unit,
    onOpenMptStereoSeparationAmigaPercentChanged: (Int) -> Unit,
    onOpenMptInterpolationFilterLengthChanged: (Int) -> Unit,
    onOpenMptAmigaResamplerModeChanged: (Int) -> Unit,
    onOpenMptAmigaResamplerApplyAllModulesChanged: (Boolean) -> Unit,
    onOpenMptVolumeRampingStrengthChanged: (Int) -> Unit,
    onOpenMptFt2XmVolumeRampingChanged: (Boolean) -> Unit,
    onOpenMptMasterGainMilliBelChanged: (Int) -> Unit,
    onOpenMptSurroundEnabledChanged: (Boolean) -> Unit,
    onVgmPlayCoreSampleRateHzChanged: (Int) -> Unit,
    onVgmPlayLoopCountChanged: (Int) -> Unit,
    onVgmPlayAllowNonLoopingLoopChanged: (Boolean) -> Unit,
    onVgmPlayVsyncRateChanged: (Int) -> Unit,
    onVgmPlayResampleModeChanged: (Int) -> Unit,
    onVgmPlayChipSampleModeChanged: (Int) -> Unit,
    onVgmPlayChipSampleRateChanged: (Int) -> Unit,
    onVgmPlayChipCoreSelectionsChanged: (Map<String, Int>) -> Unit,
    onGmeCoreSampleRateHzChanged: (Int) -> Unit,
    onGmeTempoPercentChanged: (Int) -> Unit,
    onGmeStereoSeparationPercentChanged: (Int) -> Unit,
    onGmeEchoEnabledChanged: (Boolean) -> Unit,
    onGmeAccuracyEnabledChanged: (Boolean) -> Unit,
    onGmeEqTrebleDecibelChanged: (Int) -> Unit,
    onGmeEqBassHzChanged: (Int) -> Unit,
    onGmeSpcUseBuiltInFadeChanged: (Boolean) -> Unit,
    onGmeSpcInterpolationChanged: (Int) -> Unit,
    onGmeSpcUseNativeSampleRateChanged: (Boolean) -> Unit,
    onLazyUsf2CoreSampleRateHzChanged: (Int) -> Unit,
    onAdPlugCoreSampleRateHzChanged: (Int) -> Unit,
    onHivelyTrackerCoreSampleRateHzChanged: (Int) -> Unit,
    onKlystrackCoreSampleRateHzChanged: (Int) -> Unit,
    onFurnaceCoreSampleRateHzChanged: (Int) -> Unit,
    onUadeCoreSampleRateHzChanged: (Int) -> Unit,
    onAdPlugOplEngineChanged: (Int) -> Unit,
    onLazyUsf2UseHleAudioChanged: (Boolean) -> Unit,
    onVio2sfInterpolationQualityChanged: (Int) -> Unit,
    onSc68SamplingRateHzChanged: (Int) -> Unit,
    onSc68AsidChanged: (Int) -> Unit,
    onSc68DefaultTimeSecondsChanged: (Int) -> Unit,
    onSc68YmEngineChanged: (Int) -> Unit,
    onSc68YmVolModelChanged: (Int) -> Unit,
    onSc68AmigaFilterChanged: (Boolean) -> Unit,
    onSc68AmigaBlendChanged: (Int) -> Unit,
    onSc68AmigaClockChanged: (Int) -> Unit,
    onUadeFilterEnabledChanged: (Boolean) -> Unit,
    onUadeNtscModeChanged: (Boolean) -> Unit,
    onUadePanningModeChanged: (Int) -> Unit,
    onHivelyTrackerPanningModeChanged: (Int) -> Unit,
    onHivelyTrackerMixGainPercentChanged: (Int) -> Unit,
    onKlystrackPlayerQualityChanged: (Int) -> Unit,
    onFurnaceYm2612CoreChanged: (Int) -> Unit,
    onFurnaceSnCoreChanged: (Int) -> Unit,
    onFurnaceNesCoreChanged: (Int) -> Unit,
    onFurnaceC64CoreChanged: (Int) -> Unit,
    onFurnaceGbQualityChanged: (Int) -> Unit,
    onFurnaceDsidQualityChanged: (Int) -> Unit,
    onFurnaceAyCoreChanged: (Int) -> Unit,
    onSidPlayFpCoreSampleRateHzChanged: (Int) -> Unit,
    onSidPlayFpBackendChanged: (Int) -> Unit,
    onSidPlayFpClockModeChanged: (Int) -> Unit,
    onSidPlayFpSidModelModeChanged: (Int) -> Unit,
    onSidPlayFpFilter6581EnabledChanged: (Boolean) -> Unit,
    onSidPlayFpFilter8580EnabledChanged: (Boolean) -> Unit,
    onSidPlayFpDigiBoost8580Changed: (Boolean) -> Unit,
    onSidPlayFpFilterCurve6581PercentChanged: (Int) -> Unit,
    onSidPlayFpFilterRange6581PercentChanged: (Int) -> Unit,
    onSidPlayFpFilterCurve8580PercentChanged: (Int) -> Unit,
    onSidPlayFpReSidFpFastSamplingChanged: (Boolean) -> Unit,
    onSidPlayFpReSidFpCombinedWaveformsStrengthChanged: (Int) -> Unit
) {
    val optionNamesForReset = when (pluginName) {
        DecoderNames.FFMPEG -> listOf(
            FfmpegOptionKeys.GAPLESS_REPEAT_TRACK
        )

        DecoderNames.LIB_OPEN_MPT -> listOf(
            "openmpt.stereo_separation_percent",
            "openmpt.stereo_separation_amiga_percent",
            "openmpt.interpolation_filter_length",
            "openmpt.amiga_resampler_mode",
            "openmpt.amiga_resampler_apply_all_modules",
            "openmpt.volume_ramping_strength",
            "openmpt.ft2_xm_volume_ramping",
            "openmpt.master_gain_millibel",
            "openmpt.surround_enabled"
        )

        DecoderNames.VGM_PLAY -> buildList {
            add(VgmPlayOptionKeys.LOOP_COUNT)
            add(VgmPlayOptionKeys.ALLOW_NON_LOOPING_LOOP)
            add(VgmPlayOptionKeys.VSYNC_RATE_HZ)
            add(VgmPlayOptionKeys.RESAMPLE_MODE)
            add(VgmPlayOptionKeys.CHIP_SAMPLE_MODE)
            add(VgmPlayOptionKeys.CHIP_SAMPLE_RATE_HZ)
            VgmPlayConfig.chipCoreSpecs.forEach { spec ->
                add("${VgmPlayOptionKeys.CHIP_CORE_PREFIX}${spec.key}")
            }
        }

        DecoderNames.GAME_MUSIC_EMU -> listOf(
            GmeOptionKeys.TEMPO,
            GmeOptionKeys.STEREO_SEPARATION,
            GmeOptionKeys.ECHO_ENABLED,
            GmeOptionKeys.ACCURACY_ENABLED,
            GmeOptionKeys.EQ_TREBLE_DB,
            GmeOptionKeys.EQ_BASS_HZ,
            GmeOptionKeys.SPC_USE_BUILTIN_FADE,
            GmeOptionKeys.SPC_INTERPOLATION,
            GmeOptionKeys.SPC_USE_NATIVE_SAMPLE_RATE
        )

        DecoderNames.LIB_SID_PLAY_FP -> listOf(
            SidPlayFpOptionKeys.BACKEND,
            SidPlayFpOptionKeys.CLOCK_MODE,
            SidPlayFpOptionKeys.SID_MODEL_MODE,
            SidPlayFpOptionKeys.FILTER_6581_ENABLED,
            SidPlayFpOptionKeys.FILTER_8580_ENABLED,
            SidPlayFpOptionKeys.RESIDFP_FAST_SAMPLING,
            SidPlayFpOptionKeys.RESIDFP_COMBINED_WAVEFORMS_STRENGTH
        )

        DecoderNames.LAZY_USF2 -> listOf(
            LazyUsf2OptionKeys.USE_HLE_AUDIO
        )

        DecoderNames.AD_PLUG -> listOf(
            AdPlugOptionKeys.OPL_ENGINE
        )

        DecoderNames.VIO2_SF -> listOf(
            Vio2sfOptionKeys.INTERPOLATION_QUALITY
        )

        DecoderNames.SC68 -> listOf(
            Sc68OptionKeys.ASID,
            Sc68OptionKeys.DEFAULT_TIME_SECONDS,
            Sc68OptionKeys.YM_ENGINE,
            Sc68OptionKeys.YM_VOLMODEL,
            Sc68OptionKeys.AMIGA_FILTER,
            Sc68OptionKeys.AMIGA_BLEND,
            Sc68OptionKeys.AMIGA_CLOCK
        )

        DecoderNames.UADE -> listOf(
            UadeOptionKeys.FILTER_ENABLED,
            UadeOptionKeys.NTSC_MODE,
            UadeOptionKeys.PANNING_MODE
        )

        DecoderNames.HIVELY_TRACKER -> listOf(
            HivelyTrackerOptionKeys.PANNING_MODE,
            HivelyTrackerOptionKeys.MIX_GAIN_PERCENT
        )

        DecoderNames.KLYSTRACK -> listOf(
            KlystrackOptionKeys.PLAYER_QUALITY
        )

        DecoderNames.FURNACE -> listOf(
            FurnaceOptionKeys.YM2612_CORE,
            FurnaceOptionKeys.SN_CORE,
            FurnaceOptionKeys.NES_CORE,
            FurnaceOptionKeys.C64_CORE,
            FurnaceOptionKeys.GB_QUALITY,
            FurnaceOptionKeys.DSID_QUALITY,
            FurnaceOptionKeys.AY_CORE
        )

        else -> emptyList()
    }
    val requiresPlaybackRestart = optionNamesForReset.any { optionName ->
        try {
            NativeBridge.getCoreOptionApplyPolicy(pluginName, optionName) == 1
        } catch (_: Throwable) {
            false
        }
    }
    when (pluginName) {
        DecoderNames.FFMPEG -> {
            onFfmpegCoreSampleRateHzChanged(FfmpegDefaults.coreSampleRateHz)
            onFfmpegGaplessRepeatTrackChanged(FfmpegDefaults.gaplessRepeatTrack)
            prefs.edit()
                .remove(CorePreferenceKeys.CORE_RATE_FFMPEG)
                .remove(CorePreferenceKeys.FFMPEG_GAPLESS_REPEAT_TRACK)
                .apply()
        }

        DecoderNames.LIB_OPEN_MPT -> {
            onOpenMptCoreSampleRateHzChanged(OpenMptDefaults.coreSampleRateHz)
            onOpenMptStereoSeparationPercentChanged(OpenMptDefaults.stereoSeparationPercent)
            onOpenMptStereoSeparationAmigaPercentChanged(OpenMptDefaults.stereoSeparationAmigaPercent)
            onOpenMptInterpolationFilterLengthChanged(OpenMptDefaults.interpolationFilterLength)
            onOpenMptAmigaResamplerModeChanged(OpenMptDefaults.amigaResamplerMode)
            onOpenMptAmigaResamplerApplyAllModulesChanged(OpenMptDefaults.amigaResamplerApplyAllModules)
            onOpenMptVolumeRampingStrengthChanged(OpenMptDefaults.volumeRampingStrength)
            onOpenMptFt2XmVolumeRampingChanged(OpenMptDefaults.ft2XmVolumeRamping)
            onOpenMptMasterGainMilliBelChanged(OpenMptDefaults.masterGainMilliBel)
            onOpenMptSurroundEnabledChanged(OpenMptDefaults.surroundEnabled)
            prefs.edit().apply {
                remove(CorePreferenceKeys.CORE_RATE_OPENMPT)
                remove(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT)
                remove(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT)
                remove(CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH)
                remove(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE)
                remove(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES)
                remove(CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH)
                remove(CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING)
                remove(CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL)
                remove(CorePreferenceKeys.OPENMPT_SURROUND_ENABLED)
                apply()
            }
        }

        DecoderNames.VGM_PLAY -> {
            onVgmPlayCoreSampleRateHzChanged(VgmPlayDefaults.coreSampleRateHz)
            onVgmPlayLoopCountChanged(VgmPlayDefaults.loopCount)
            onVgmPlayAllowNonLoopingLoopChanged(VgmPlayDefaults.allowNonLoopingLoop)
            onVgmPlayVsyncRateChanged(VgmPlayDefaults.vsyncRate)
            onVgmPlayResampleModeChanged(VgmPlayDefaults.resampleMode)
            onVgmPlayChipSampleModeChanged(VgmPlayDefaults.chipSampleMode)
            onVgmPlayChipSampleRateChanged(VgmPlayDefaults.chipSampleRate)
            onVgmPlayChipCoreSelectionsChanged(VgmPlayConfig.defaultChipCoreSelections())
            prefs.edit().apply {
                remove(CorePreferenceKeys.CORE_RATE_VGMPLAY)
                remove(CorePreferenceKeys.VGMPLAY_LOOP_COUNT)
                remove(CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP)
                remove(CorePreferenceKeys.VGMPLAY_VSYNC_RATE)
                remove(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE)
                remove(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE)
                remove(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE)
                VgmPlayConfig.chipCoreSpecs.forEach { spec ->
                    remove(CorePreferenceKeys.vgmPlayChipCoreKey(spec.key))
                }
                apply()
            }
        }

        DecoderNames.GAME_MUSIC_EMU -> {
            onGmeCoreSampleRateHzChanged(GmeDefaults.coreSampleRateHz)
            onGmeTempoPercentChanged(GmeDefaults.tempoPercent)
            onGmeStereoSeparationPercentChanged(GmeDefaults.stereoSeparationPercent)
            onGmeEchoEnabledChanged(GmeDefaults.echoEnabled)
            onGmeAccuracyEnabledChanged(GmeDefaults.accuracyEnabled)
            onGmeEqTrebleDecibelChanged(GmeDefaults.eqTrebleDecibel)
            onGmeEqBassHzChanged(GmeDefaults.eqBassHz)
            onGmeSpcUseBuiltInFadeChanged(GmeDefaults.spcUseBuiltInFade)
            onGmeSpcInterpolationChanged(GmeDefaults.spcInterpolation)
            onGmeSpcUseNativeSampleRateChanged(GmeDefaults.spcUseNativeSampleRate)
            prefs.edit().apply {
                remove(CorePreferenceKeys.CORE_RATE_GME)
                remove(CorePreferenceKeys.GME_TEMPO_PERCENT)
                remove(CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT)
                remove(CorePreferenceKeys.GME_ECHO_ENABLED)
                remove(CorePreferenceKeys.GME_ACCURACY_ENABLED)
                remove(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL)
                remove(CorePreferenceKeys.GME_EQ_BASS_HZ)
                remove(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE)
                remove(CorePreferenceKeys.GME_SPC_INTERPOLATION)
                remove(CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE)
                apply()
            }
        }

        DecoderNames.LAZY_USF2 -> {
            onLazyUsf2CoreSampleRateHzChanged(LazyUsf2Defaults.coreSampleRateHz)
            onLazyUsf2UseHleAudioChanged(LazyUsf2Defaults.useHleAudio)
            prefs.edit()
                .remove(CorePreferenceKeys.CORE_RATE_LAZYUSF2)
                .remove(CorePreferenceKeys.LAZYUSF2_USE_HLE_AUDIO)
                .apply()
        }

        DecoderNames.AD_PLUG -> {
            onAdPlugCoreSampleRateHzChanged(AdPlugDefaults.coreSampleRateHz)
            onAdPlugOplEngineChanged(AdPlugDefaults.oplEngine)
            prefs.edit()
                .remove(CorePreferenceKeys.CORE_RATE_ADPLUG)
                .remove(CorePreferenceKeys.ADPLUG_OPL_ENGINE)
                .apply()
        }

        DecoderNames.HIVELY_TRACKER -> {
            onHivelyTrackerCoreSampleRateHzChanged(HivelyTrackerDefaults.coreSampleRateHz)
            onHivelyTrackerPanningModeChanged(HivelyTrackerDefaults.panningMode)
            onHivelyTrackerMixGainPercentChanged(HivelyTrackerDefaults.mixGainPercent)
            prefs.edit()
                .remove(CorePreferenceKeys.CORE_RATE_HIVELYTRACKER)
                .remove(CorePreferenceKeys.HIVELYTRACKER_PANNING_MODE)
                .remove(CorePreferenceKeys.HIVELYTRACKER_MIX_GAIN_PERCENT)
                .apply()
        }

        DecoderNames.KLYSTRACK -> {
            onKlystrackCoreSampleRateHzChanged(KlystrackDefaults.coreSampleRateHz)
            onKlystrackPlayerQualityChanged(KlystrackDefaults.playerQuality)
            prefs.edit()
                .remove(CorePreferenceKeys.CORE_RATE_KLYSTRACK)
                .remove(CorePreferenceKeys.KLYSTRACK_PLAYER_QUALITY)
                .apply()
        }

        DecoderNames.FURNACE -> {
            onFurnaceCoreSampleRateHzChanged(FurnaceDefaults.coreSampleRateHz)
            onFurnaceYm2612CoreChanged(FurnaceDefaults.ym2612Core)
            onFurnaceSnCoreChanged(FurnaceDefaults.snCore)
            onFurnaceNesCoreChanged(FurnaceDefaults.nesCore)
            onFurnaceC64CoreChanged(FurnaceDefaults.c64Core)
            onFurnaceGbQualityChanged(FurnaceDefaults.gbQuality)
            onFurnaceDsidQualityChanged(FurnaceDefaults.dsidQuality)
            onFurnaceAyCoreChanged(FurnaceDefaults.ayCore)
            prefs.edit().apply {
                remove(CorePreferenceKeys.CORE_RATE_FURNACE)
                remove(CorePreferenceKeys.FURNACE_YM2612_CORE)
                remove(CorePreferenceKeys.FURNACE_SN_CORE)
                remove(CorePreferenceKeys.FURNACE_NES_CORE)
                remove(CorePreferenceKeys.FURNACE_C64_CORE)
                remove(CorePreferenceKeys.FURNACE_GB_QUALITY)
                remove(CorePreferenceKeys.FURNACE_DSID_QUALITY)
                remove(CorePreferenceKeys.FURNACE_AY_CORE)
                apply()
            }
        }

        DecoderNames.UADE -> {
            onUadeCoreSampleRateHzChanged(UadeDefaults.coreSampleRateHz)
            onUadeFilterEnabledChanged(UadeDefaults.filterEnabled)
            onUadeNtscModeChanged(UadeDefaults.ntscMode)
            onUadePanningModeChanged(UadeDefaults.panningMode)
            prefs.edit()
                .remove(CorePreferenceKeys.CORE_RATE_UADE)
                .remove(CorePreferenceKeys.UADE_FILTER_ENABLED)
                .remove(CorePreferenceKeys.UADE_NTSC_MODE)
                .remove(CorePreferenceKeys.UADE_PANNING_MODE)
                .apply()
        }

        DecoderNames.VIO2_SF -> {
            onVio2sfInterpolationQualityChanged(Vio2sfDefaults.interpolationQuality)
            prefs.edit()
                .remove(CorePreferenceKeys.VIO2SF_INTERPOLATION_QUALITY)
                .apply()
        }

        DecoderNames.SC68 -> {
            onSc68SamplingRateHzChanged(Sc68Defaults.coreSampleRateHz)
            onSc68AsidChanged(Sc68Defaults.asid)
            onSc68DefaultTimeSecondsChanged(Sc68Defaults.defaultTimeSeconds)
            onSc68YmEngineChanged(Sc68Defaults.ymEngine)
            onSc68YmVolModelChanged(Sc68Defaults.ymVolModel)
            onSc68AmigaFilterChanged(Sc68Defaults.amigaFilter)
            onSc68AmigaBlendChanged(Sc68Defaults.amigaBlend)
            onSc68AmigaClockChanged(Sc68Defaults.amigaClock)
            prefs.edit().apply {
                remove(CorePreferenceKeys.CORE_RATE_SC68)
                remove(CorePreferenceKeys.SC68_ASID)
                remove(CorePreferenceKeys.SC68_DEFAULT_TIME_SECONDS)
                remove(CorePreferenceKeys.SC68_YM_ENGINE)
                remove(CorePreferenceKeys.SC68_YM_VOLMODEL)
                remove(CorePreferenceKeys.SC68_AMIGA_FILTER)
                remove(CorePreferenceKeys.SC68_AMIGA_BLEND)
                remove(CorePreferenceKeys.SC68_AMIGA_CLOCK)
                apply()
            }
        }

        DecoderNames.LIB_SID_PLAY_FP -> {
            onSidPlayFpCoreSampleRateHzChanged(SidPlayFpDefaults.coreSampleRateHz)
            onSidPlayFpBackendChanged(SidPlayFpDefaults.backend)
            onSidPlayFpClockModeChanged(SidPlayFpDefaults.clockMode)
            onSidPlayFpSidModelModeChanged(SidPlayFpDefaults.sidModelMode)
            onSidPlayFpFilter6581EnabledChanged(SidPlayFpDefaults.filter6581Enabled)
            onSidPlayFpFilter8580EnabledChanged(SidPlayFpDefaults.filter8580Enabled)
            onSidPlayFpDigiBoost8580Changed(SidPlayFpDefaults.digiBoost8580)
            onSidPlayFpFilterCurve6581PercentChanged(SidPlayFpDefaults.filterCurve6581Percent)
            onSidPlayFpFilterRange6581PercentChanged(SidPlayFpDefaults.filterRange6581Percent)
            onSidPlayFpFilterCurve8580PercentChanged(SidPlayFpDefaults.filterCurve8580Percent)
            onSidPlayFpReSidFpFastSamplingChanged(SidPlayFpDefaults.reSidFpFastSampling)
            onSidPlayFpReSidFpCombinedWaveformsStrengthChanged(SidPlayFpDefaults.reSidFpCombinedWaveformsStrength)
            prefs.edit().apply {
                remove(CorePreferenceKeys.CORE_RATE_SIDPLAYFP)
                remove(CorePreferenceKeys.SIDPLAYFP_BACKEND)
                remove(CorePreferenceKeys.SIDPLAYFP_CLOCK_MODE)
                remove(CorePreferenceKeys.SIDPLAYFP_SID_MODEL_MODE)
                remove(CorePreferenceKeys.SIDPLAYFP_FILTER_6581_ENABLED)
                remove(CorePreferenceKeys.SIDPLAYFP_FILTER_8580_ENABLED)
                remove(CorePreferenceKeys.SIDPLAYFP_DIGI_BOOST_8580)
                remove(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_6581)
                remove(CorePreferenceKeys.SIDPLAYFP_FILTER_RANGE_6581)
                remove(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_8580)
                remove(CorePreferenceKeys.SIDPLAYFP_RESIDFP_FAST_SAMPLING)
                remove(CorePreferenceKeys.SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH)
                apply()
            }
        }
    }
    Toast.makeText(
        context,
        if (requiresPlaybackRestart) {
            "Settings reset. Playback restart needed for some changes."
        } else {
            "$pluginName core settings reset"
        },
        Toast.LENGTH_SHORT
    ).show()
}
