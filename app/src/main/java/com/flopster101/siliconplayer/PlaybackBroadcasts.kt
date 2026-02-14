package com.flopster101.siliconplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat

@Composable
internal fun RegisterPlaybackBroadcastReceiver(
    context: Context,
    onCleared: () -> Unit,
    onPreviousTrackRequested: () -> Unit,
    onNextTrackRequested: () -> Unit
) {
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    PlaybackService.ACTION_BROADCAST_CLEARED -> onCleared()
                    PlaybackService.ACTION_BROADCAST_PREVIOUS_TRACK_REQUEST -> onPreviousTrackRequested()
                    PlaybackService.ACTION_BROADCAST_NEXT_TRACK_REQUEST -> onNextTrackRequested()
                }
            }
        }
        val playbackFilter = IntentFilter(PlaybackService.ACTION_BROADCAST_CLEARED).apply {
            addAction(PlaybackService.ACTION_BROADCAST_PREVIOUS_TRACK_REQUEST)
            addAction(PlaybackService.ACTION_BROADCAST_NEXT_TRACK_REQUEST)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            playbackFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}
