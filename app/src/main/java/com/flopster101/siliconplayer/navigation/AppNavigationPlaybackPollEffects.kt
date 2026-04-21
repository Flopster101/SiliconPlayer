package com.flopster101.siliconplayer

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class PlaybackPollSnapshot(
    val seekInProgress: Boolean,
    val isPlaying: Boolean,
    val durationSeconds: Double,
    val positionSeconds: Double
)

private suspend fun readPlaybackPollSnapshot(
    localDuration: Double,
    durationRefreshCountdown: Int,
    activeSourceId: String?,
    deferredPlaybackSeek: DeferredPlaybackSeek?
): PlaybackPollSnapshot = withContext(Dispatchers.Default) {
    val nextSeekInProgress = NativeBridge.isSeekInProgress()
    val nextIsPlaying = NativeBridge.isEnginePlaying()
    val nextDuration = if (nextSeekInProgress) {
        localDuration
    } else if (
        durationRefreshCountdown <= 0 ||
        !(localDuration > 0.0) ||
        !localDuration.isFinite()
    ) {
        NativeBridge.getDuration()
    } else {
        localDuration
    }
    val nextPosition = if (
        deferredPlaybackSeek != null &&
        activeSourceId != null &&
        deferredPlaybackSeek.sourceId == activeSourceId
    ) {
        val maxDuration = nextDuration.coerceAtLeast(0.0)
        if (maxDuration > 0.0) {
            deferredPlaybackSeek.positionSeconds.coerceIn(0.0, maxDuration)
        } else {
            deferredPlaybackSeek.positionSeconds.coerceAtLeast(0.0)
        }
    } else {
        NativeBridge.getPosition()
    }
    PlaybackPollSnapshot(
        seekInProgress = nextSeekInProgress,
        isPlaying = nextIsPlaying,
        durationSeconds = nextDuration,
        positionSeconds = nextPosition
    )
}

@Composable
internal fun AppNavigationPlaybackPollEffects(
    selectedFile: File?,
    isPlayingProvider: () -> Boolean,
    selectedFileProvider: () -> File?,
    deferredPlaybackSeekProvider: () -> DeferredPlaybackSeek?,
    seekInProgress: Boolean,
    seekStartedAtMs: Long,
    seekRequestedAtMs: Long,
    seekUiBusyThresholdMs: Long,
    duration: Double,
    durationOverrideSeconds: Double?,
    subtuneCountProvider: () -> Int,
    currentSubtuneIndexProvider: () -> Int,
    activeRepeatModeProvider: () -> RepeatMode,
    currentPlaybackSourceIdProvider: () -> String?,
    playbackWatchPath: String?,
    metadataTitleProvider: () -> String,
    metadataArtistProvider: () -> String,
    lastBrowserLocationId: String?,
    onSeekInProgressChanged: (Boolean) -> Unit,
    onSeekStartedAtMsChanged: (Long) -> Unit,
    onSeekRequestedAtMsChanged: (Long) -> Unit,
    onSeekUiBusyChanged: (Boolean) -> Unit,
    onDurationChanged: (Double) -> Unit,
    onPositionChanged: (Double) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onPlaybackWatchPathChanged: (String?) -> Unit,
    onMetadataTitleChanged: (String) -> Unit,
    onMetadataArtistChanged: (String) -> Unit,
    onSubtuneCursorChanged: (File?) -> Unit,
    onAddRecentPlayedTrack: (path: String, locationId: String?, title: String?, artist: String?) -> Unit,
    onPlayAdjacentTrack: (offset: Int, wrapOverride: Boolean?, notifyWrap: Boolean) -> Boolean,
    onRestartCurrentTrack: () -> Unit,
    onStopPlaybackAndUnload: () -> Unit,
    isLocalPlayableFile: (File?) -> Boolean
) {
    val latestDurationOverrideSeconds = rememberUpdatedState(durationOverrideSeconds)
    val latestSeekRequestedAtMs = rememberUpdatedState(seekRequestedAtMs)

    LaunchedEffect(selectedFile) {
        var metadataPollElapsedMs = 0L
        var subtunePollElapsedMs = 0L
        var localSeekInProgress = seekInProgress
        var localSeekStartedAtMs = seekStartedAtMs
        var localSeekRequestedAtMs = seekRequestedAtMs
        var localDuration = duration
        var localPosition = 0.0
        var hasPublishedPosition = false
        var localIsPlaying = isPlayingProvider()
        var localPlaybackWatchPath = playbackWatchPath
        var lastObservedExplicitSeekRequestMs = seekRequestedAtMs
        var suppressTrackEndEventsUntilMs = 0L
        var durationRefreshCountdown = 0
        var lastPersistedRecentMetadata: Triple<String, String, String>? = null
        var suppressedSyntheticEndSourceId: String? = null

        while (selectedFileProvider() != null) {
            val currentFile = selectedFileProvider()
            val activeSourceId = currentPlaybackSourceIdProvider() ?: currentFile?.absolutePath
            val deferredPlaybackSeek = deferredPlaybackSeekProvider()
            val snapshot = readPlaybackPollSnapshot(
                localDuration = localDuration,
                durationRefreshCountdown = durationRefreshCountdown,
                activeSourceId = activeSourceId,
                deferredPlaybackSeek = deferredPlaybackSeek
            )
            val nextSeekInProgress = snapshot.seekInProgress
            val nextIsPlaying = snapshot.isPlaying
            val pollDelayMs = when {
                nextSeekInProgress -> 120L
                nextIsPlaying -> 180L
                else -> 320L
            }
            val nowMs = SystemClock.elapsedRealtime()
            val externalSeekRequestMs = latestSeekRequestedAtMs.value
            if (externalSeekRequestMs > lastObservedExplicitSeekRequestMs) {
                lastObservedExplicitSeekRequestMs = externalSeekRequestMs
                suppressTrackEndEventsUntilMs = maxOf(
                    suppressTrackEndEventsUntilMs,
                    externalSeekRequestMs + 1500L
                )
            }
            if (nextSeekInProgress) {
                if (!localSeekInProgress) {
                    localSeekStartedAtMs = if (localSeekRequestedAtMs > 0L) localSeekRequestedAtMs else nowMs
                    localSeekRequestedAtMs = 0L
                    onSeekRequestedAtMsChanged(localSeekRequestedAtMs)
                } else if (localSeekStartedAtMs <= 0L) {
                    localSeekStartedAtMs = nowMs
                }
                onSeekStartedAtMsChanged(localSeekStartedAtMs)
                onSeekUiBusyChanged(
                    localSeekStartedAtMs > 0L && (nowMs - localSeekStartedAtMs) >= seekUiBusyThresholdMs
                )
            } else {
                localSeekStartedAtMs = 0L
                localSeekRequestedAtMs = 0L
                onSeekStartedAtMsChanged(0L)
                onSeekRequestedAtMsChanged(0L)
                onSeekUiBusyChanged(false)
            }
            val nextDuration = snapshot.durationSeconds
            if (!nextSeekInProgress) {
                if (
                    durationRefreshCountdown <= 0 ||
                    !(localDuration > 0.0) ||
                    !localDuration.isFinite()
                ) {
                    durationRefreshCountdown = if (nextIsPlaying) 6 else 2
                } else {
                    durationRefreshCountdown -= 1
                }
            } else {
                durationRefreshCountdown = durationRefreshCountdown.coerceAtLeast(0)
            }
            val nextPosition = snapshot.positionSeconds
            val previousDuration = localDuration
            localSeekInProgress = nextSeekInProgress
            localDuration = nextDuration
            onSeekInProgressChanged(nextSeekInProgress)
            if (abs(nextDuration - previousDuration) > 0.0001) {
                onDurationChanged(nextDuration)
            }
            if (!hasPublishedPosition || abs(nextPosition - localPosition) > 0.001) {
                onPositionChanged(nextPosition)
                localPosition = nextPosition
                hasPublishedPosition = true
            }
            if (nextIsPlaying != localIsPlaying) {
                onIsPlayingChanged(nextIsPlaying)
                localIsPlaying = nextIsPlaying
            }

            if (!nextSeekInProgress) {
                val suppressTrackEndEvents = nowMs < suppressTrackEndEventsUntilMs
                val durationOverrideThreshold = latestDurationOverrideSeconds.value
                    ?.takeIf { it.isFinite() && it > 0.0 }
                val syntheticEndPositionResetThreshold = durationOverrideThreshold
                    ?.let { (it - 0.25).coerceAtLeast(0.0) }

                if (
                    durationOverrideThreshold != null &&
                    suppressedSyntheticEndSourceId != null &&
                    activeSourceId == suppressedSyntheticEndSourceId &&
                    nextPosition <= (syntheticEndPositionResetThreshold ?: 0.0)
                ) {
                    suppressedSyntheticEndSourceId = null
                }

                subtunePollElapsedMs += pollDelayMs
                val subtunePollIntervalMs = if (nextIsPlaying) 360L else 900L
                if (subtunePollElapsedMs >= subtunePollIntervalMs) {
                    subtunePollElapsedMs = 0L
                    val nativeSubtuneCursor = withContext(Dispatchers.Default) {
                        readNativeSubtuneCursor()
                    }
                    if (hasNativeSubtuneCursorChanged(
                            nativeSubtuneCursor,
                            subtuneCountProvider(),
                            currentSubtuneIndexProvider()
                        )
                    ) {
                        onSubtuneCursorChanged(currentFile)
                        val recentSourceId = currentPlaybackSourceIdProvider() ?: currentFile?.absolutePath
                        if (nextIsPlaying && recentSourceId != null) {
                            onAddRecentPlayedTrack(
                                recentSourceId,
                                if (isLocalPlayableFile(currentFile)) lastBrowserLocationId else null,
                                metadataTitleProvider(),
                                metadataArtistProvider()
                            )
                        }
                    }
                }

                val currentPath = currentPlaybackSourceIdProvider() ?: currentFile?.absolutePath
                if (currentPath != localPlaybackWatchPath) {
                    suppressedSyntheticEndSourceId = null
                    localPlaybackWatchPath = currentPath
                    onPlaybackWatchPathChanged(currentPath)
                } else {
                    val endedAtPlaylistDuration = durationOverrideThreshold != null &&
                        currentPath != null &&
                        nextIsPlaying &&
                        !suppressTrackEndEvents &&
                        suppressedSyntheticEndSourceId == null &&
                        nextPosition + 0.02 >= durationOverrideThreshold
                    if (endedAtPlaylistDuration) {
                        suppressedSyntheticEndSourceId = currentPath
                        val repeatMode = activeRepeatModeProvider()
                        when (repeatMode) {
                            RepeatMode.None -> {
                                val moved = onPlayAdjacentTrack(1, false, false)
                                if (!moved) {
                                    onStopPlaybackAndUnload()
                                }
                            }
                            RepeatMode.Playlist -> {
                                val moved = onPlayAdjacentTrack(1, true, false)
                                if (!moved) {
                                    onStopPlaybackAndUnload()
                                }
                            }
                            RepeatMode.LoopPoint -> {
                                // For playlist-backed tracks with a duration override, LoopPoint
                                // should still behave like the direct-file path: let the native
                                // loop-point repeat continue instead of downgrading to a full
                                // high-level restart at the override boundary.
                            }
                            else -> {
                                onRestartCurrentTrack()
                            }
                        }
                        continue
                    }
                    val endedNaturally = NativeBridge.consumeNaturalEndEvent()
                    if (endedNaturally && suppressTrackEndEvents) {
                        continue
                    }
                    if (endedNaturally) {
                        val repeatMode = activeRepeatModeProvider()
                        val moved = when (repeatMode) {
                            RepeatMode.None -> onPlayAdjacentTrack(1, false, false)
                            RepeatMode.Playlist -> onPlayAdjacentTrack(1, true, false)
                            else -> false
                        }
                        if (moved) {
                            continue
                        }
                        if (repeatMode == RepeatMode.Track || repeatMode == RepeatMode.Subtune) {
                            onRestartCurrentTrack()
                            continue
                        }
                        if (repeatMode == RepeatMode.None) {
                            onStopPlaybackAndUnload()
                        }
                    }
                }
                metadataPollElapsedMs += pollDelayMs
                val currentMetadataTitle = metadataTitleProvider()
                val currentMetadataArtist = metadataArtistProvider()
                if (shouldPollTrackMetadata(metadataPollElapsedMs, currentMetadataTitle, currentMetadataArtist)) {
                    metadataPollElapsedMs = 0L
                    val (nextTitle, nextArtist) = withContext(Dispatchers.Default) {
                        sanitizeRemoteCachedMetadataTitle(
                            rawTitle = NativeBridge.getTrackTitle(),
                            selectedFile = currentFile
                        ) to NativeBridge.getTrackArtist()
                    }
                    val titleChanged = nextTitle != currentMetadataTitle
                    val artistChanged = nextArtist != currentMetadataArtist
                    if (titleChanged) {
                        onMetadataTitleChanged(nextTitle)
                    }
                    if (artistChanged) {
                        onMetadataArtistChanged(nextArtist)
                    }
                    val recentSourceId = currentPlaybackSourceIdProvider() ?: currentFile?.absolutePath
                    if (nextIsPlaying && (titleChanged || artistChanged) && recentSourceId != null) {
                        onAddRecentPlayedTrack(
                            recentSourceId,
                            if (isLocalPlayableFile(currentFile)) lastBrowserLocationId else null,
                            nextTitle,
                            nextArtist
                        )
                    }
                    if (nextIsPlaying && recentSourceId != null) {
                        val normalizedTitle = nextTitle.trim()
                        val normalizedArtist = nextArtist.trim()
                        if (normalizedTitle.isNotBlank() || normalizedArtist.isNotBlank()) {
                            val metadataSignature =
                                Triple(recentSourceId, normalizedTitle, normalizedArtist)
                            if (metadataSignature != lastPersistedRecentMetadata) {
                                onAddRecentPlayedTrack(
                                    recentSourceId,
                                    if (isLocalPlayableFile(currentFile)) lastBrowserLocationId else null,
                                    nextTitle,
                                    nextArtist
                                )
                                lastPersistedRecentMetadata = metadataSignature
                            }
                        }
                    }
                }
            } else {
                metadataPollElapsedMs = 0L
                subtunePollElapsedMs = 0L
            }
            delay(pollDelayMs)
        }
    }
}
