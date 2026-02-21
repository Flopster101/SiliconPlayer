package com.flopster101.siliconplayer

import java.io.File

internal fun startOrResumePlaybackFromPlayerSurface(
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    lastBrowserLocationId: String?,
    metadataTitle: String,
    metadataArtist: String,
    activeRepeatMode: RepeatMode,
    isLocalPlayableFile: (File?) -> Boolean,
    addRecentPlayedTrack: (path: String, locationId: String?, title: String, artist: String) -> Unit,
    applyRepeatModeToNative: (RepeatMode) -> Unit,
    startEngine: () -> Unit,
    onPlayingStateChanged: (Boolean) -> Unit,
    scheduleRecentTrackMetadataRefresh: (sourceId: String, locationId: String?) -> Unit,
    syncPlaybackService: () -> Unit,
    resumeLastStoppedTrack: (autoStart: Boolean) -> Unit
) {
    if (selectedFile == null) {
        resumeLastStoppedTrack(true)
        return
    }

    val sourceId = currentPlaybackSourceId ?: selectedFile.absolutePath
    addRecentPlayedTrack(
        sourceId,
        if (isLocalPlayableFile(selectedFile)) lastBrowserLocationId else null,
        metadataTitle,
        metadataArtist
    )
    applyRepeatModeToNative(activeRepeatMode)
    startEngine()
    onPlayingStateChanged(true)
    scheduleRecentTrackMetadataRefresh(
        sourceId,
        if (isLocalPlayableFile(selectedFile)) lastBrowserLocationId else null
    )
    syncPlaybackService()
}

internal fun primeAudioEffectsDialogState(
    masterVolumeDb: Float,
    pluginVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    forceMono: Boolean,
    onTempMasterVolumeChanged: (Float) -> Unit,
    onTempPluginVolumeChanged: (Float) -> Unit,
    onTempSongVolumeChanged: (Float) -> Unit,
    onTempIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit,
    onTempForceMonoChanged: (Boolean) -> Unit,
    onShowAudioEffectsDialog: (Boolean) -> Unit
) {
    onTempMasterVolumeChanged(masterVolumeDb)
    onTempPluginVolumeChanged(pluginVolumeDb)
    onTempSongVolumeChanged(songVolumeDb)
    onTempIgnoreCoreVolumeForSongChanged(ignoreCoreVolumeForSong)
    onTempForceMonoChanged(forceMono)
    onShowAudioEffectsDialog(true)
}

internal fun handleMiniPlayerPlayPauseAction(
    selectedFile: File?,
    isPlaying: Boolean,
    onResumeLastStoppedTrack: () -> Unit,
    onStopEngine: () -> Unit,
    onPlayingStateChanged: (Boolean) -> Unit,
    syncPlaybackService: () -> Unit,
    onStartOrResumePlayback: () -> Unit
) {
    if (selectedFile == null) {
        onResumeLastStoppedTrack()
        return
    }
    if (isPlaying) {
        onStopEngine()
        onPlayingStateChanged(false)
        syncPlaybackService()
        return
    }
    onStartOrResumePlayback()
}

internal fun buildStartPlaybackFromSurfaceAction(
    selectedFileProvider: () -> File?,
    currentPlaybackSourceIdProvider: () -> String?,
    lastBrowserLocationIdProvider: () -> String?,
    metadataTitleProvider: () -> String,
    metadataArtistProvider: () -> String,
    activeRepeatModeProvider: () -> RepeatMode,
    isLocalPlayableFile: (File?) -> Boolean,
    addRecentPlayedTrack: (String, String?, String, String) -> Unit,
    applyRepeatModeToNative: (RepeatMode) -> Unit,
    startEngine: () -> Unit,
    onPlayingStateChanged: (Boolean) -> Unit,
    scheduleRecentTrackMetadataRefresh: (String, String?) -> Unit,
    syncPlaybackService: () -> Unit,
    resumeLastStoppedTrack: (Boolean) -> Unit
): () -> Unit = {
    startOrResumePlaybackFromPlayerSurface(
        selectedFile = selectedFileProvider(),
        currentPlaybackSourceId = currentPlaybackSourceIdProvider(),
        lastBrowserLocationId = lastBrowserLocationIdProvider(),
        metadataTitle = metadataTitleProvider(),
        metadataArtist = metadataArtistProvider(),
        activeRepeatMode = activeRepeatModeProvider(),
        isLocalPlayableFile = isLocalPlayableFile,
        addRecentPlayedTrack = addRecentPlayedTrack,
        applyRepeatModeToNative = applyRepeatModeToNative,
        startEngine = startEngine,
        onPlayingStateChanged = onPlayingStateChanged,
        scheduleRecentTrackMetadataRefresh = scheduleRecentTrackMetadataRefresh,
        syncPlaybackService = syncPlaybackService,
        resumeLastStoppedTrack = resumeLastStoppedTrack
    )
}

internal fun buildOpenAudioEffectsDialogAction(
    masterVolumeDbProvider: () -> Float,
    pluginVolumeDbProvider: () -> Float,
    songVolumeDbProvider: () -> Float,
    ignoreCoreVolumeForSongProvider: () -> Boolean,
    forceMonoProvider: () -> Boolean,
    onTempMasterVolumeChanged: (Float) -> Unit,
    onTempPluginVolumeChanged: (Float) -> Unit,
    onTempSongVolumeChanged: (Float) -> Unit,
    onTempIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit,
    onTempForceMonoChanged: (Boolean) -> Unit,
    onShowAudioEffectsDialogChanged: (Boolean) -> Unit
): () -> Unit = {
    primeAudioEffectsDialogState(
        masterVolumeDb = masterVolumeDbProvider(),
        pluginVolumeDb = pluginVolumeDbProvider(),
        songVolumeDb = songVolumeDbProvider(),
        ignoreCoreVolumeForSong = ignoreCoreVolumeForSongProvider(),
        forceMono = forceMonoProvider(),
        onTempMasterVolumeChanged = onTempMasterVolumeChanged,
        onTempPluginVolumeChanged = onTempPluginVolumeChanged,
        onTempSongVolumeChanged = onTempSongVolumeChanged,
        onTempIgnoreCoreVolumeForSongChanged = onTempIgnoreCoreVolumeForSongChanged,
        onTempForceMonoChanged = onTempForceMonoChanged,
        onShowAudioEffectsDialog = onShowAudioEffectsDialogChanged
    )
}
