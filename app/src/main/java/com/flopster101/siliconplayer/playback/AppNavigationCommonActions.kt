package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import com.flopster101.siliconplayer.playback.cycleRepeatModeWithCapabilities
import com.flopster101.siliconplayer.playback.resolveAndMaybeApplyRepeatMode
import com.flopster101.siliconplayer.playback.resolveSubtuneUiState
import com.flopster101.siliconplayer.playback.syncPlaybackServiceFromUiState
import com.flopster101.siliconplayer.session.addRecentFolderEntry
import com.flopster101.siliconplayer.session.addRecentPlayedTrackEntry
import com.flopster101.siliconplayer.session.scheduleRecentPlayedArtworkCacheBackfill as scheduleRecentPlayedArtworkCacheBackfillInSession
import com.flopster101.siliconplayer.session.scheduleRecentPlayedMetadataBackfill as scheduleRecentPlayedMetadataBackfillInSession
import com.flopster101.siliconplayer.session.scheduleRecentTrackMetadataRefresh as scheduleRecentTrackMetadataRefreshInSession
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal fun syncPlaybackServiceAction(
    context: Context,
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    metadataTitle: String,
    metadataArtist: String,
    durationSeconds: Double,
    positionSeconds: Double,
    isPlaying: Boolean
) {
    syncPlaybackServiceFromUiState(
        context = context,
        selectedFile = selectedFile,
        currentPlaybackSourceId = currentPlaybackSourceId,
        metadataTitle = metadataTitle,
        metadataArtist = metadataArtist,
        durationSeconds = durationSeconds,
        positionSeconds = positionSeconds,
        isPlaying = isPlaying
    )
}

internal fun refreshSubtuneStateAction(
    selectedFile: File?,
    onSubtuneCountChanged: (Int) -> Unit,
    onCurrentSubtuneIndexChanged: (Int) -> Unit,
    onSubtuneEntriesCleared: () -> Unit,
    onShowSubtuneSelectorDialogChanged: (Boolean) -> Unit
) {
    val state = resolveSubtuneUiState(selectedFile)
    if (!state.shouldShowSelector) {
        onSubtuneCountChanged(0)
        onCurrentSubtuneIndexChanged(0)
        onSubtuneEntriesCleared()
        onShowSubtuneSelectorDialogChanged(false)
        return
    }
    onSubtuneCountChanged(state.count)
    onCurrentSubtuneIndexChanged(state.currentIndex)
}

internal fun refreshSubtuneEntriesAction(
    subtuneCount: Int,
    onSubtuneEntriesChanged: (List<SubtuneEntry>) -> Unit
) {
    onSubtuneEntriesChanged(readSubtuneEntries(subtuneCount))
}

internal fun addRecentFolderAction(
    current: List<RecentPathEntry>,
    path: String,
    locationId: String?,
    sourceNodeId: Long?,
    title: String?,
    limit: Int,
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit,
    prefs: SharedPreferences
) {
    addRecentFolderEntry(
        current = current,
        path = path,
        locationId = locationId,
        sourceNodeId = sourceNodeId,
        title = title,
        limit = limit,
        update = onRecentFoldersChanged,
        write = { entries, max ->
            writeRecentEntries(prefs, AppPreferenceKeys.RECENT_FOLDERS, entries, max)
        }
    )
}

internal fun addRecentPlayedTrackAction(
    context: Context,
    appScope: CoroutineScope,
    currentProvider: () -> List<RecentPathEntry>,
    path: String,
    requestUrl: String? = null,
    locationId: String?,
    sourceNodeId: Long? = null,
    title: String?,
    artist: String?,
    decoderName: String?,
    limitProvider: () -> Int,
    onRecentPlayedChanged: (List<RecentPathEntry>) -> Unit,
    prefs: SharedPreferences
) {
    val current = currentProvider()
    val limit = limitProvider()
    addRecentPlayedTrackEntry(
        current = current,
        path = path,
        locationId = locationId,
        sourceNodeId = sourceNodeId,
        title = title,
        artist = artist,
        decoderName = decoderName,
        limit = limit,
        update = onRecentPlayedChanged,
        write = { entries, max ->
            writeRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, entries, max)
        }
    )
    scheduleRecentPlayedArtworkCacheBackfillInSession(
        context = context,
        scope = appScope,
        sourceId = path,
        requestUrlHint = requestUrl,
        currentProvider = currentProvider,
        limitProvider = limitProvider,
        onRecentPlayedChanged = onRecentPlayedChanged,
        writeRecentPlayed = { entries, max ->
            writeRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, entries, max)
        }
    )
}

internal fun scheduleRecentTrackMetadataRefreshAction(
    appScope: CoroutineScope,
    sourceId: String,
    locationId: String?,
    selectedFileProvider: () -> File?,
    currentPlaybackSourceIdProvider: () -> String?,
    onAddRecentPlayedTrack: (path: String, locationId: String?, title: String?, artist: String?) -> Unit
) {
    scheduleRecentTrackMetadataRefreshInSession(
        scope = appScope,
        sourceId = sourceId,
        locationId = locationId,
        selectedFileProvider = selectedFileProvider,
        currentPlaybackSourceIdProvider = currentPlaybackSourceIdProvider,
        onAddRecentPlayedTrack = onAddRecentPlayedTrack
    )
}

internal fun scheduleRecentPlayedMetadataBackfillAction(
    appScope: CoroutineScope,
    recentPlayedProvider: () -> List<RecentPathEntry>,
    recentFilesLimitProvider: () -> Int,
    onRecentPlayedChanged: (List<RecentPathEntry>) -> Unit,
    prefs: SharedPreferences
) {
    scheduleRecentPlayedMetadataBackfillInSession(
        scope = appScope,
        currentProvider = recentPlayedProvider,
        limitProvider = recentFilesLimitProvider,
        onRecentPlayedChanged = onRecentPlayedChanged,
        writeRecentPlayed = { entries, max ->
            writeRecentEntries(prefs, AppPreferenceKeys.RECENT_PLAYED_FILES, entries, max)
        }
    )
}

internal fun refreshRepeatModeForTrackAction(
    selectedFile: File?,
    durationSeconds: Double,
    subtuneCount: Int,
    preferredRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int,
    currentPlaybackSourceId: String?,
    seekInProgress: Boolean,
    applyRepeatMode: (RepeatMode) -> Unit
): RepeatMode {
    return resolveAndMaybeApplyRepeatMode(
        selectedFile = selectedFile,
        durationSeconds = durationSeconds,
        subtuneCount = subtuneCount,
        preferredRepeatMode = preferredRepeatMode,
        repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags,
        currentPlaybackSourceId = currentPlaybackSourceId,
        seekInProgress = seekInProgress,
        applyRepeatMode = applyRepeatMode
    )
}

internal fun cycleRepeatModeAction(
    context: Context,
    playbackCapabilitiesFlags: Int,
    seekInProgress: Boolean,
    selectedFile: File?,
    durationSeconds: Double,
    subtuneCount: Int,
    activeRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int,
    applyRepeatMode: (RepeatMode) -> Unit
): RepeatMode? {
    return cycleRepeatModeWithCapabilities(
        context = context,
        playbackCapabilitiesFlags = playbackCapabilitiesFlags,
        seekInProgress = seekInProgress,
        selectedFile = selectedFile,
        durationSeconds = durationSeconds,
        subtuneCount = subtuneCount,
        activeRepeatMode = activeRepeatMode,
        repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags,
        applyRepeatMode = applyRepeatMode
    )
}

internal fun applyCoreOptionWithPolicyAction(
    context: Context,
    coreName: String,
    optionName: String,
    optionValue: String,
    policy: CoreOptionApplyPolicy,
    optionLabel: String?,
    selectedFile: File?,
    isPlaying: Boolean
) {
    NativeBridge.setCoreOption(coreName, optionName, optionValue)
    val resolvedPolicy = try {
        when (NativeBridge.getCoreOptionApplyPolicy(coreName, optionName)) {
            1 -> CoreOptionApplyPolicy.RequiresPlaybackRestart
            else -> CoreOptionApplyPolicy.Live
        }
    } catch (_: Throwable) {
        policy
    }
    maybeShowCoreOptionRestartToast(
        context,
        coreName = coreName,
        selectedFile = selectedFile,
        isPlaying = isPlaying,
        policy = resolvedPolicy,
        optionLabel = optionLabel
    )
}
