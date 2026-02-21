package com.flopster101.siliconplayer

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun loadSongVolumeForFileAction(
    volumeDatabase: VolumeDatabase,
    filePath: String,
    onSongVolumeDbChanged: (Float) -> Unit,
    onSongGainChanged: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit
) {
    val value = volumeDatabase.getSongVolume(filePath) ?: 0f
    val ignoreCoreVolumeForSong = volumeDatabase.getSongIgnoreCoreVolume(filePath)
    onSongVolumeDbChanged(value)
    onSongGainChanged(value)
    onIgnoreCoreVolumeForSongChanged(ignoreCoreVolumeForSong)
}

internal fun isLocalPlayableFileAction(file: File?): Boolean {
    return file?.exists() == true && file.isFile
}

internal fun refreshCachedSourceFilesAction(
    appScope: CoroutineScope,
    cacheRoot: File,
    onCachedSourceFilesChanged: (List<CachedSourceFile>) -> Unit
) {
    appScope.launch(Dispatchers.IO) {
        val files = listCachedSourceFiles(cacheRoot)
        withContext(Dispatchers.Main.immediate) {
            onCachedSourceFilesChanged(files)
        }
    }
}
