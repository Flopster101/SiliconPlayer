package com.flopster101.siliconplayer

internal data class PlaybackSessionCoordinator(
    val syncPlaybackService: () -> Unit,
    val restorePlayerStateFromSessionAndNative: suspend (Boolean) -> Unit
)

internal fun buildPlaybackSessionCoordinator(
    runtimeDelegates: AppNavigationRuntimeDelegates,
    trackLoadDelegates: AppNavigationTrackLoadDelegates
): PlaybackSessionCoordinator {
    return PlaybackSessionCoordinator(
        syncPlaybackService = { runtimeDelegates.syncPlaybackService() },
        restorePlayerStateFromSessionAndNative = { openExpanded ->
            trackLoadDelegates.restorePlayerStateFromSessionAndNative(openExpanded = openExpanded)
        }
    )
}
