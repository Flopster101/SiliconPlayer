package com.flopster101.siliconplayer

import android.content.Context
import android.content.Intent

internal fun stopAndEmptyTrackAction(
    context: Context,
    playbackStateDelegates: AppNavigationPlaybackStateDelegates
) {
    playbackStateDelegates.resetAndOptionallyKeepLastTrack(keepLastTrack = true)
    context.startService(
        Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_STOP_CLEAR)
    )
}

internal fun hidePlayerSurfaceAction(
    onStopAndEmptyTrack: () -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    onPlayerSurfaceVisibleChanged: (Boolean) -> Unit
) {
    onStopAndEmptyTrack()
    onPlayerExpandedChanged(false)
    onPlayerSurfaceVisibleChanged(false)
}

internal fun handleAppNavigationBackAction(
    isPlayerExpanded: Boolean,
    currentView: MainView,
    settingsLaunchedFromPlayer: Boolean,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    popSettingsRoute: () -> Boolean,
    exitSettingsToReturnView: () -> Unit
) {
    when {
        isPlayerExpanded -> onPlayerExpandedChanged(false)
        currentView == MainView.Settings && settingsLaunchedFromPlayer -> exitSettingsToReturnView()
        currentView == MainView.Settings && popSettingsRoute() -> Unit
        currentView == MainView.Settings -> exitSettingsToReturnView()
    }
}
