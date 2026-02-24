package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flopster101.siliconplayer.fileMatchesSupportedExtensions
import com.flopster101.siliconplayer.runWithNativeAudioSession
import java.io.File
import kotlinx.coroutines.launch

@Composable
internal fun AppNavigationPendingOpenEffects(
    currentView: MainView,
    settingsRoute: SettingsRoute,
    pendingFileToOpen: File?,
    pendingFileFromExternalIntent: Boolean,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    supportedExtensions: Set<String>,
    onRefreshCachedSourceFiles: () -> Unit,
    onSelectedFileChanged: (File?) -> Unit,
    onLoadSongVolumeForFile: (String) -> Unit,
    onApplyRepeatModeToNative: () -> Unit,
    onStartEngine: () -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onIsPlayerExpandedChanged: (Boolean) -> Unit,
    onIsPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onPendingFileToOpenChanged: (File?) -> Unit,
    onPendingFileFromExternalIntentChanged: (Boolean) -> Unit,
    loadPlayableSiblingFiles: suspend (File) -> List<File>?
) {
    LaunchedEffect(currentView, settingsRoute) {
        if (currentView == MainView.Settings &&
            (settingsRoute == SettingsRoute.UrlCache || settingsRoute == SettingsRoute.CacheManager)
        ) {
            onRefreshCachedSourceFiles()
        }
    }

    LaunchedEffect(pendingFileToOpen) {
        pendingFileToOpen?.let { file ->
            if (file.exists() && fileMatchesSupportedExtensions(file, supportedExtensions)) {
                onSelectedFileChanged(file)

                if (autoPlayOnTrackSelect) {
                    onLoadSongVolumeForFile(file.absolutePath)
                    runWithNativeAudioSession {
                        NativeBridge.loadAudio(file.absolutePath)
                    }
                    onApplyRepeatModeToNative()
                    onStartEngine()
                    onIsPlayingChanged(true)
                }
                if (openPlayerOnTrackSelect) {
                    onIsPlayerExpandedChanged(true)
                }
                onIsPlayerSurfaceVisibleChanged(true)

                if (pendingFileFromExternalIntent) {
                    launch {
                        val playableFiles = loadPlayableSiblingFiles(file)
                        if (playableFiles != null) {
                            onVisiblePlayableFilesChanged(playableFiles)
                        }
                    }
                }

                onPendingFileToOpenChanged(null)
                onPendingFileFromExternalIntentChanged(false)
            }
        }
    }
}
