package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal data class AppNavigationRuntimeDelegates(
    val syncPlaybackService: () -> Unit,
    val refreshSubtuneState: () -> Unit,
    val refreshSubtuneEntries: () -> Unit,
    val resolveShareableFileForRecent: (RecentPathEntry) -> File?,
    val addRecentFolder: (String, String?) -> Unit,
    val addRecentPlayedTrack: (String, String?, String?, String?) -> Unit,
    val scheduleRecentTrackMetadataRefresh: (String, String?) -> Unit,
    val refreshRepeatModeForTrack: () -> Unit,
    val cycleRepeatMode: () -> Unit
)

internal fun buildAppNavigationRuntimeDelegates(
    context: Context,
    prefs: SharedPreferences,
    appScope: CoroutineScope,
    recentFoldersProvider: () -> List<RecentPathEntry>,
    recentFoldersLimitProvider: () -> Int,
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit,
    recentPlayedFilesProvider: () -> List<RecentPathEntry>,
    recentFilesLimitProvider: () -> Int,
    onRecentPlayedChanged: (List<RecentPathEntry>) -> Unit,
    selectedFileProvider: () -> File?,
    currentPlaybackSourceIdProvider: () -> String?,
    metadataTitleProvider: () -> String,
    metadataArtistProvider: () -> String,
    durationProvider: () -> Double,
    positionProvider: () -> Double,
    isPlayingProvider: () -> Boolean,
    subtuneCountProvider: () -> Int,
    onSubtuneCountChanged: (Int) -> Unit,
    onCurrentSubtuneIndexChanged: (Int) -> Unit,
    onSubtuneEntriesChanged: (List<SubtuneEntry>) -> Unit,
    onShowSubtuneSelectorDialogChanged: (Boolean) -> Unit,
    preferredRepeatModeProvider: () -> RepeatMode,
    activeRepeatModeProvider: () -> RepeatMode,
    repeatModeCapabilitiesFlagsProvider: () -> Int,
    playbackCapabilitiesFlagsProvider: () -> Int,
    seekInProgressProvider: () -> Boolean,
    onPreferredRepeatModeChanged: (RepeatMode) -> Unit,
    onActiveRepeatModeChanged: (RepeatMode) -> Unit,
    applyRepeatModeToNative: (RepeatMode) -> Unit
): AppNavigationRuntimeDelegates {
    val addRecentPlayedTrack: (String, String?, String?, String?) -> Unit = { path, locationId, title, artist ->
        addRecentPlayedTrackAction(
            current = recentPlayedFilesProvider(),
            path = path,
            locationId = locationId,
            title = title,
            artist = artist,
            limit = recentFilesLimitProvider(),
            onRecentPlayedChanged = onRecentPlayedChanged,
            prefs = prefs
        )
    }

    return AppNavigationRuntimeDelegates(
        syncPlaybackService = {
            syncPlaybackServiceAction(
                context = context,
                selectedFile = selectedFileProvider(),
                currentPlaybackSourceId = currentPlaybackSourceIdProvider(),
                metadataTitle = metadataTitleProvider(),
                metadataArtist = metadataArtistProvider(),
                durationSeconds = durationProvider(),
                positionSeconds = positionProvider(),
                isPlaying = isPlayingProvider()
            )
        },
        refreshSubtuneState = {
            refreshSubtuneStateAction(
                selectedFile = selectedFileProvider(),
                onSubtuneCountChanged = onSubtuneCountChanged,
                onCurrentSubtuneIndexChanged = onCurrentSubtuneIndexChanged,
                onSubtuneEntriesCleared = { onSubtuneEntriesChanged(emptyList()) },
                onShowSubtuneSelectorDialogChanged = onShowSubtuneSelectorDialogChanged
            )
        },
        refreshSubtuneEntries = {
            refreshSubtuneEntriesAction(
                subtuneCount = subtuneCountProvider(),
                onSubtuneEntriesChanged = onSubtuneEntriesChanged
            )
        },
        resolveShareableFileForRecent = { entry ->
            resolveShareableFileForRecentEntry(context, entry)
        },
        addRecentFolder = { path, locationId ->
            addRecentFolderAction(
                current = recentFoldersProvider(),
                path = path,
                locationId = locationId,
                limit = recentFoldersLimitProvider(),
                onRecentFoldersChanged = onRecentFoldersChanged,
                prefs = prefs
            )
        },
        addRecentPlayedTrack = addRecentPlayedTrack,
        scheduleRecentTrackMetadataRefresh = { sourceId, locationId ->
            scheduleRecentTrackMetadataRefreshAction(
                appScope = appScope,
                sourceId = sourceId,
                locationId = locationId,
                selectedFileProvider = selectedFileProvider,
                currentPlaybackSourceIdProvider = currentPlaybackSourceIdProvider,
                onAddRecentPlayedTrack = addRecentPlayedTrack
            )
        },
        refreshRepeatModeForTrack = {
            onActiveRepeatModeChanged(
                refreshRepeatModeForTrackAction(
                    selectedFile = selectedFileProvider(),
                    durationSeconds = durationProvider(),
                    subtuneCount = subtuneCountProvider(),
                    preferredRepeatMode = preferredRepeatModeProvider(),
                    repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlagsProvider(),
                    currentPlaybackSourceId = currentPlaybackSourceIdProvider(),
                    seekInProgress = seekInProgressProvider(),
                    applyRepeatMode = applyRepeatModeToNative
                )
            )
        },
        cycleRepeatMode = {
            val next = cycleRepeatModeAction(
                context = context,
                playbackCapabilitiesFlags = playbackCapabilitiesFlagsProvider(),
                seekInProgress = seekInProgressProvider(),
                selectedFile = selectedFileProvider(),
                durationSeconds = durationProvider(),
                subtuneCount = subtuneCountProvider(),
                activeRepeatMode = activeRepeatModeProvider(),
                repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlagsProvider(),
                applyRepeatMode = applyRepeatModeToNative
            )
            if (next != null) {
                onPreferredRepeatModeChanged(next)
                onActiveRepeatModeChanged(next)
            }
        }
    )
}
