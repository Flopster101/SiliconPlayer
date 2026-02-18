package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import kotlinx.coroutines.delay

@Composable
internal fun AppNavigationPlaybackEffects(
    context: Context,
    prefs: SharedPreferences,
    respondHeadphoneMediaButtons: Boolean,
    pauseOnHeadphoneDisconnect: Boolean,
    audioBackendPreference: AudioBackendPreference,
    audioPerformanceMode: AudioPerformanceMode,
    audioBufferPreset: AudioBufferPreset,
    audioResamplerPreference: AudioResamplerPreference,
    audioAllowBackendFallback: Boolean,
    pendingSoxExperimentalDialog: Boolean,
    onPendingSoxExperimentalDialogChanged: (Boolean) -> Unit,
    onShowSoxExperimentalDialogChanged: (Boolean) -> Unit,
    openPlayerFromNotification: Boolean,
    persistRepeatMode: Boolean,
    preferredRepeatMode: RepeatMode,
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    isPlaying: Boolean,
    metadataTitle: String,
    metadataArtist: String,
    duration: Double,
    notificationOpenSignal: Int,
    syncPlaybackService: () -> Unit,
    restorePlayerStateFromSessionAndNative: suspend (Boolean) -> Unit
) {
    LaunchedEffect(respondHeadphoneMediaButtons) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.RESPOND_HEADPHONE_MEDIA_BUTTONS, respondHeadphoneMediaButtons)
            .apply()
        PlaybackService.refreshSettings(context)
    }

    LaunchedEffect(pauseOnHeadphoneDisconnect) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.PAUSE_ON_HEADPHONE_DISCONNECT, pauseOnHeadphoneDisconnect)
            .apply()
        PlaybackService.refreshSettings(context)
    }

    LaunchedEffect(audioBackendPreference) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE,
                audioBackendPreference.storageValue
            )
            .apply()
    }

    LaunchedEffect(audioPerformanceMode) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_PERFORMANCE_MODE,
                audioPerformanceMode.storageValue
            )
            .apply()
    }

    LaunchedEffect(audioBufferPreset) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_BUFFER_PRESET,
                audioBufferPreset.storageValue
            )
            .apply()
    }

    LaunchedEffect(audioResamplerPreference) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.AUDIO_RESAMPLER_PREFERENCE,
                audioResamplerPreference.storageValue
            )
            .apply()
    }

    LaunchedEffect(pendingSoxExperimentalDialog) {
        if (!pendingSoxExperimentalDialog) return@LaunchedEffect
        delay(120)
        onShowSoxExperimentalDialogChanged(true)
        onPendingSoxExperimentalDialogChanged(false)
    }

    LaunchedEffect(audioAllowBackendFallback) {
        prefs.edit()
            .putBoolean(
                AppPreferenceKeys.AUDIO_ALLOW_BACKEND_FALLBACK,
                audioAllowBackendFallback
            )
            .apply()
    }

    LaunchedEffect(
        audioBackendPreference,
        audioPerformanceMode,
        audioBufferPreset,
        audioResamplerPreference,
        audioAllowBackendFallback
    ) {
        NativeBridge.setAudioPipelineConfig(
            backendPreference = audioBackendPreference.nativeValue,
            performanceMode = audioPerformanceMode.nativeValue,
            bufferPreset = audioBufferPreset.nativeValue,
            resamplerPreference = audioResamplerPreference.nativeValue,
            allowFallback = audioAllowBackendFallback
        )
    }

    LaunchedEffect(openPlayerFromNotification) {
        prefs.edit()
            .putBoolean(AppPreferenceKeys.OPEN_PLAYER_FROM_NOTIFICATION, openPlayerFromNotification)
            .apply()
    }

    LaunchedEffect(persistRepeatMode) {
        val editor = prefs.edit().putBoolean(AppPreferenceKeys.PERSIST_REPEAT_MODE, persistRepeatMode)
        if (!persistRepeatMode) {
            editor.remove(AppPreferenceKeys.PREFERRED_REPEAT_MODE)
        }
        editor.apply()
    }

    LaunchedEffect(preferredRepeatMode, persistRepeatMode) {
        if (persistRepeatMode) {
            prefs.edit()
                .putString(AppPreferenceKeys.PREFERRED_REPEAT_MODE, preferredRepeatMode.storageValue)
                .apply()
        }
    }

    LaunchedEffect(selectedFile, currentPlaybackSourceId, isPlaying, metadataTitle, metadataArtist, duration) {
        if (selectedFile != null) {
            syncPlaybackService()
        }
    }

    LaunchedEffect(Unit, notificationOpenSignal, openPlayerFromNotification) {
        val shouldOpenExpandedFromSignal = notificationOpenSignal > 0 && openPlayerFromNotification
        restorePlayerStateFromSessionAndNative(shouldOpenExpandedFromSignal)
    }

    LaunchedEffect(notificationOpenSignal, openPlayerFromNotification, selectedFile) {
        if (notificationOpenSignal <= 0) return@LaunchedEffect
        if (selectedFile == null) return@LaunchedEffect
        if (openPlayerFromNotification) {
            restorePlayerStateFromSessionAndNative(true)
        }
    }
}
