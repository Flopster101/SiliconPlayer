package com.flopster101.siliconplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File

class PlaybackService : Service() {
    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val notificationManager by lazy {
        NotificationManagerCompat.from(this)
    }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var mediaSession: MediaSession? = null

    private var currentPath: String? = null
    private var currentTitle: String = "No track selected"
    private var currentArtist: String = "Silicon Player"
    private var currentArtwork: Bitmap? = null
    private var currentArtworkPath: String? = null
    private var durationSeconds: Double = 0.0
    private var positionSeconds: Double = 0.0
    private var isPlaying: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (currentPath != null) {
                positionSeconds = NativeBridge.getPosition()
                durationSeconds = NativeBridge.getDuration()
                isPlaying = NativeBridge.isEnginePlaying()
                updateMediaSessionState()
                pushNotification()
                handler.postDelayed(this, if (isPlaying) 400L else 900L)
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            if (!prefs.getBoolean(PREF_PAUSE_ON_DISCONNECT, true)) return
            if (!NativeBridge.isEnginePlaying()) return
            pausePlayback()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        setupMediaSession()
        registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
        unregisterReceiver(noisyReceiver)
        mediaSession?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC -> syncFromIntent(intent)
            ACTION_PLAY -> playPlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_TOGGLE -> if (NativeBridge.isEnginePlaying()) pausePlayback() else playPlayback()
            ACTION_STOP_CLEAR -> stopAndClear()
            ACTION_REFRESH_SETTINGS -> {
                // Settings are read lazily from SharedPreferences in callbacks.
                updateMediaSessionState()
                pushNotification()
            }
        }
        return START_STICKY
    }

    private fun syncFromIntent(intent: Intent) {
        val newPath = intent.getStringExtra(EXTRA_PATH)
        if (newPath != currentArtworkPath) {
            currentArtwork = loadArtworkForPath(newPath)
            currentArtworkPath = newPath
        }
        currentPath = newPath
        prefs.edit().putString(PREF_SESSION_CURRENT_PATH, currentPath).apply()
        currentTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Unknown Title" }
        currentArtist = intent.getStringExtra(EXTRA_ARTIST).orEmpty().ifBlank { "Unknown Artist" }
        durationSeconds = intent.getDoubleExtra(EXTRA_DURATION, 0.0)
        positionSeconds = intent.getDoubleExtra(EXTRA_POSITION, 0.0)
        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
        if (currentPath == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(NOTIFICATION_ID)
            stopSelf()
            return
        }
        updateMediaSessionState()
        pushNotification()
        handler.removeCallbacks(ticker)
        if (currentPath != null) {
            handler.post(ticker)
        }
    }

    private fun playPlayback() {
        if (currentPath == null) return
        NativeBridge.startEngine()
        isPlaying = true
        updateMediaSessionState()
        pushNotification()
    }

    private fun pausePlayback() {
        NativeBridge.stopEngine()
        isPlaying = false
        updateMediaSessionState()
        pushNotification()
    }

    private fun stopAndClear() {
        NativeBridge.stopEngine()
        isPlaying = false
        currentPath = null
        prefs.edit().remove(PREF_SESSION_CURRENT_PATH).apply()
        currentTitle = "No track selected"
        currentArtist = "Silicon Player"
        durationSeconds = 0.0
        positionSeconds = 0.0
        updateMediaSessionState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        sendBroadcast(Intent(ACTION_BROADCAST_CLEARED).setPackage(packageName))
        stopSelf()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playback controls and current track"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun setupMediaSession() {
        val session = MediaSession(this, "SiliconPlayerSession")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                if (!prefs.getBoolean(PREF_RESPOND_MEDIA_BUTTONS, true)) return
                playPlayback()
            }

            override fun onPause() {
                if (!prefs.getBoolean(PREF_RESPOND_MEDIA_BUTTONS, true)) return
                pausePlayback()
            }

            override fun onStop() {
                if (!prefs.getBoolean(PREF_RESPOND_MEDIA_BUTTONS, true)) return
                stopAndClear()
            }
        })
        session.isActive = true
        mediaSession = session
    }

    private fun updateMediaSessionState() {
        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_PLAY_PAUSE
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (positionSeconds * 1000.0).toLong(),
                if (isPlaying) 1f else 0f
            )
            .build()
        mediaSession?.setPlaybackState(state)
        mediaSession?.setMetadata(
            android.media.MediaMetadata.Builder().apply {
                putString(android.media.MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
                putLong(
                    android.media.MediaMetadata.METADATA_KEY_DURATION,
                    (durationSeconds * 1000.0).toLong()
                )
                val art = currentArtwork ?: fallbackIconBitmap()
                putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, art)
            }.build()
        )
    }

    private fun pushNotification() {
        if (currentPath == null) return
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_PLAYER_FROM_NOTIFICATION, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            100,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP_CLEAR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationBuilder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(currentArtwork ?: fallbackIconBitmap())
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(launchPendingIntent)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop",
                    stopIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this,
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    ),
                    if (isPlaying) "Pause" else "Play",
                    toggleIntent
                ).build()
            )
        return notificationBuilder.build()
    }

    private fun loadArtworkForPath(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        loadEmbeddedArtwork(file)?.let { return it }
        findFolderArtworkFile(file)?.let { return decodeScaledBitmapFromFile(it) }
        return null
    }

    private fun loadEmbeddedArtwork(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val embedded = retriever.embeddedPicture ?: return null
            decodeScaledBitmapFromBytes(embedded)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun findFolderArtworkFile(trackFile: File): File? {
        val parent = trackFile.parentFile ?: return null
        if (!parent.isDirectory) return null
        val allowedNames = setOf(
            "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
            "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
            "album.jpg", "album.jpeg", "album.png", "album.webp",
            "front.jpg", "front.jpeg", "front.png", "front.webp",
            "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
        )
        return parent.listFiles()?.firstOrNull { it.isFile && it.name.lowercase() in allowedNames }
    }

    private fun decodeScaledBitmapFromFile(file: File, maxDimension: Int = 512): Bitmap? {
        val optionsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, optionsBounds)
        if (optionsBounds.outWidth <= 0 || optionsBounds.outHeight <= 0) return null
        val sampleSize = computeInSampleSize(optionsBounds.outWidth, optionsBounds.outHeight, maxDimension)
        val optionsDecode = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, optionsDecode)
    }

    private fun decodeScaledBitmapFromBytes(bytes: ByteArray, maxDimension: Int = 512): Bitmap? {
        val optionsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, optionsBounds)
        if (optionsBounds.outWidth <= 0 || optionsBounds.outHeight <= 0) return null
        val sampleSize = computeInSampleSize(optionsBounds.outWidth, optionsBounds.outHeight, maxDimension)
        val optionsDecode = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, optionsDecode)
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var outWidth = width
        var outHeight = height
        while (outWidth > maxDimension || outHeight > maxDimension) {
            sampleSize *= 2
            outWidth /= 2
            outHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun fallbackIconBitmap(): Bitmap? {
        return drawableToBitmap(R.drawable.ic_placeholder_music_note)
    }

    private fun drawableToBitmap(drawableId: Int, sizePx: Int = 1024): Bitmap? {
        val drawable = ContextCompat.getDrawable(this, drawableId) ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private const val CHANNEL_ID = "silicon_player_playback"
        private const val NOTIFICATION_ID = 1101

        private const val PREFS_NAME = "silicon_player_settings"
        private const val PREF_RESPOND_MEDIA_BUTTONS = "respond_headphone_media_buttons"
        private const val PREF_PAUSE_ON_DISCONNECT = "pause_on_headphone_disconnect"
        private const val PREF_SESSION_CURRENT_PATH = "session_current_path"

        private const val EXTRA_PATH = "extra_path"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ARTIST = "extra_artist"
        private const val EXTRA_DURATION = "extra_duration"
        private const val EXTRA_POSITION = "extra_position"
        private const val EXTRA_IS_PLAYING = "extra_is_playing"

        const val ACTION_SYNC = "com.flopster101.siliconplayer.action.SYNC"
        const val ACTION_PLAY = "com.flopster101.siliconplayer.action.PLAY"
        const val ACTION_PAUSE = "com.flopster101.siliconplayer.action.PAUSE"
        const val ACTION_TOGGLE = "com.flopster101.siliconplayer.action.TOGGLE"
        const val ACTION_STOP_CLEAR = "com.flopster101.siliconplayer.action.STOP_CLEAR"
        const val ACTION_REFRESH_SETTINGS = "com.flopster101.siliconplayer.action.REFRESH_SETTINGS"
        const val ACTION_BROADCAST_CLEARED = "com.flopster101.siliconplayer.action.BROADCAST_CLEARED"
        const val EXTRA_OPEN_PLAYER_FROM_NOTIFICATION = "extra_open_player_from_notification"

        fun syncFromUi(
            context: Context,
            path: String?,
            title: String,
            artist: String,
            durationSeconds: Double,
            positionSeconds: Double,
            isPlaying: Boolean
        ) {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_SYNC)
                .putExtra(EXTRA_PATH, path)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
                .putExtra(EXTRA_DURATION, durationSeconds)
                .putExtra(EXTRA_POSITION, positionSeconds)
                .putExtra(EXTRA_IS_PLAYING, isPlaying)
            if (path != null && isPlaying) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun refreshSettings(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_REFRESH_SETTINGS)
            context.startService(intent)
        }
    }
}
