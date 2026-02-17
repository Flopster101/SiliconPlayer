package com.flopster101.siliconplayer

import android.content.ContentResolver
import android.content.Intent
import java.io.File

internal fun shouldOpenPlayerFromNotification(intent: Intent?): Boolean {
    return intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER_FROM_NOTIFICATION, false) == true
}

internal fun resolveInitialFileToOpen(contentResolver: ContentResolver, intent: Intent?): File? {
    return resolveFileFromViewIntent(contentResolver, intent)
}
