package com.flopster101.siliconplayer

import java.io.File
import kotlinx.coroutines.CoroutineScope

internal fun buildLoadSongVolumeForFileDelegate(
    volumeDatabase: VolumeDatabase,
    onSongVolumeDbChanged: (Float) -> Unit,
    onSongGainChanged: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit
): (String) -> Unit = { filePath ->
    loadSongVolumeForFileAction(
        volumeDatabase = volumeDatabase,
        filePath = filePath,
        onSongVolumeDbChanged = onSongVolumeDbChanged,
        onSongGainChanged = onSongGainChanged,
        onIgnoreCoreVolumeForSongChanged = onIgnoreCoreVolumeForSongChanged
    )
}

internal fun buildIsLocalPlayableFileDelegate(): (File?) -> Boolean = { file ->
    isLocalPlayableFileAction(file)
}

internal fun buildRefreshCachedSourceFilesDelegate(
    appScope: CoroutineScope,
    cacheRoot: File,
    onCachedSourceFilesChanged: (List<CachedSourceFile>) -> Unit
): () -> Unit = {
    refreshCachedSourceFilesAction(
        appScope = appScope,
        cacheRoot = cacheRoot,
        onCachedSourceFilesChanged = onCachedSourceFilesChanged
    )
}

internal fun buildStopAndEmptyTrackDelegate(
    context: android.content.Context,
    playbackStateDelegates: AppNavigationPlaybackStateDelegates
): () -> Unit = {
    stopAndEmptyTrackAction(
        context = context,
        playbackStateDelegates = playbackStateDelegates
    )
}

internal fun buildHidePlayerSurfaceDelegate(
    onStopAndEmptyTrack: () -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    onPlayerSurfaceVisibleChanged: (Boolean) -> Unit
): () -> Unit = {
    hidePlayerSurfaceAction(
        onStopAndEmptyTrack = onStopAndEmptyTrack,
        onPlayerExpandedChanged = onPlayerExpandedChanged,
        onPlayerSurfaceVisibleChanged = onPlayerSurfaceVisibleChanged
    )
}
