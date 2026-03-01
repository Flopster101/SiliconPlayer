package com.flopster101.siliconplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.ForegroundServiceStartNotAllowedException
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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
    private var audioFocusRequest: AudioFocusRequest? = null // For Android O+
    private var resumeOnFocusGain = false
    private var isDucked = false
    private var originalMasterVolume = 0f
    private var isForegroundNotificationShown = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (!prefs.getBoolean(PREF_AUDIO_FOCUS_INTERRUPT, true)) return@OnAudioFocusChangeListener
        val enableDucking = prefs.getBoolean(PREF_AUDIO_DUCKING, true)

        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                unduckAudio()
                pausePlayback(abandonFocus = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = true
                unduckAudio()
                pausePlayback(abandonFocus = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (enableDucking) {
                    // Duck audio instead of pausing
                    duckAudio()
                    resumeOnFocusGain = false
                } else {
                    // Pause as before
                    resumeOnFocusGain = true
                    pausePlayback(abandonFocus = false)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                unduckAudio()
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    playPlayback()
                }
            }
        }
    }

    private var currentPath: String? = null
    private var currentTitle: String = "No track selected"
    private var currentArtist: String = "Silicon Player"
    private var currentArtwork: Bitmap? = null
    private var currentArtworkPath: String? = null
    private var durationSeconds: Double = 0.0
    private var positionSeconds: Double = 0.0
    private var isPlaying: Boolean = false
    private var durationRefreshCountdown = 0
    private var lastPersistedResumeSourceId: String? = null
    private var lastPersistedResumePositionBucket: Int = Int.MIN_VALUE
    private var lastPersistedResumeDurationBucket: Int = Int.MIN_VALUE
    private var lastPersistedResumeIsPlaying: Boolean? = null
    private var lastPersistedResumeTitle: String? = null
    private var lastPersistedResumeArtist: String? = null
    private var lastPersistedResumePlaybackCaps: Int = Int.MIN_VALUE
    private var lastPersistedResumeRepeatCaps: Int = Int.MIN_VALUE
    private var hasPersistedResumeCheckpoint: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (currentPath != null) {
                val trackLoaded = NativeBridge.getTrackSampleRate() > 0
                val seekInProgress = trackLoaded && NativeBridge.isSeekInProgress()
                if (!seekInProgress && trackLoaded) {
                    positionSeconds = NativeBridge.getPosition()
                    if (durationRefreshCountdown <= 0) {
                        durationSeconds = NativeBridge.getDuration()
                        durationRefreshCountdown = if (isPlaying) 5 else 2
                    } else {
                        durationRefreshCountdown -= 1
                    }
                }
                isPlaying = if (trackLoaded) NativeBridge.isEnginePlaying() else false
                persistResumeCheckpointIfNeeded()
                updateMediaSessionState()
                pushNotification()
                val delayMs = when {
                    seekInProgress -> 180L
                    isPlaying -> 400L
                    else -> 900L
                }
                handler.postDelayed(this, delayMs)
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
        isServiceAlive = true
        createChannelIfNeeded()
        setupMediaSession()
        registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceAlive = false
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
            ACTION_PREVIOUS_TRACK -> requestAdjacentTrack(-1)
            ACTION_NEXT_TRACK -> requestAdjacentTrack(1)
            ACTION_SEEK_BACK -> seekBy(-10.0)
            ACTION_SEEK_FORWARD -> seekBy(10.0)
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
        durationRefreshCountdown = 0
        val wasPlaying = isPlaying
        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)

        // Request audio focus when transitioning from not playing to playing
        if (isPlaying && !wasPlaying && currentPath != null) {
            requestAudioFocus()
        } else if (!isPlaying && wasPlaying) {
            abandonAudioFocus()
            resumeOnFocusGain = false
        }
        persistResumeCheckpointIfNeeded(force = true)

        if (currentPath == null) {
            stopForegroundCompat(removeNotification = true)
            isForegroundNotificationShown = false
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
        requestAudioFocus()
        if (shouldApplyPauseResumeFade()) {
            NativeBridge.startEngineWithPauseResumeFade()
        } else {
            NativeBridge.startEngine()
        }
        isPlaying = true
        persistResumeCheckpointIfNeeded(force = true)
        updateMediaSessionState()
        pushNotification()
    }

    private fun pausePlayback(abandonFocus: Boolean = true) {
        if (abandonFocus) {
            abandonAudioFocus()
            resumeOnFocusGain = false
        }
        if (!shouldApplyPauseResumeFade() || !NativeBridge.isEnginePlaying()) {
            NativeBridge.stopEngine()
            isPlaying = false
            persistResumeCheckpointIfNeeded(force = true)
            updateMediaSessionState()
            pushNotification()
            return
        }
        NativeBridge.stopEngineWithPauseResumeFade()
        isPlaying = false
        persistResumeCheckpointIfNeeded(force = true)
        updateMediaSessionState()
        pushNotification()
    }

    private fun shouldApplyPauseResumeFade(): Boolean {
        if (!prefs.getBoolean(PREF_FADE_PAUSE_RESUME, true)) return false
        if (currentPath.isNullOrBlank()) return false
        // Apply only for mid-song pause/resume, not fresh starts / track changes.
        return positionSeconds > 0.05
    }

    private fun stopAndClear() {
        abandonAudioFocus()
        resumeOnFocusGain = false
        NativeBridge.stopEngine()
        isPlaying = false
        currentPath = null
        prefs.edit().remove(PREF_SESSION_CURRENT_PATH).apply()
        currentTitle = "No track selected"
        currentArtist = "Silicon Player"
        durationSeconds = 0.0
        positionSeconds = 0.0
        clearResumeCheckpoint()
        updateMediaSessionState()
        stopForegroundCompat(removeNotification = true)
        isForegroundNotificationShown = false
        notificationManager.cancel(NOTIFICATION_ID)
        sendBroadcast(Intent(ACTION_BROADCAST_CLEARED).setPackage(packageName))
        stopSelf()
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH
            )
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
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

            override fun onSeekTo(pos: Long) {
                if (!prefs.getBoolean(PREF_RESPOND_MEDIA_BUTTONS, true)) return
                seekToPosition(pos / 1000.0)
            }

            override fun onFastForward() {
                if (!prefs.getBoolean(PREF_RESPOND_MEDIA_BUTTONS, true)) return
                seekBy(10.0)
            }

            override fun onRewind() {
                if (!prefs.getBoolean(PREF_RESPOND_MEDIA_BUTTONS, true)) return
                seekBy(-10.0)
            }

            override fun onSkipToPrevious() {
                if (!prefs.getBoolean(PREF_RESPOND_MEDIA_BUTTONS, true)) return
                requestAdjacentTrack(-1)
            }

            override fun onSkipToNext() {
                if (!prefs.getBoolean(PREF_RESPOND_MEDIA_BUTTONS, true)) return
                requestAdjacentTrack(1)
            }
        })
        session.isActive = true
        mediaSession = session
    }

    private fun requestAdjacentTrack(offset: Int) {
        val action = if (offset < 0) {
            ACTION_BROADCAST_PREVIOUS_TRACK_REQUEST
        } else {
            ACTION_BROADCAST_NEXT_TRACK_REQUEST
        }
        sendBroadcast(Intent(action).setPackage(packageName))
    }

    private fun seekBy(deltaSeconds: Double) {
        if (currentPath == null) return
        seekToPosition(positionSeconds + deltaSeconds)
    }

    private fun seekToPosition(seconds: Double) {
        if (currentPath == null) return
        val maxDuration = durationSeconds.coerceAtLeast(0.0)
        val clamped = if (maxDuration > 0.0) {
            seconds.coerceIn(0.0, maxDuration)
        } else {
            seconds.coerceAtLeast(0.0)
        }
        NativeBridge.seekTo(clamped)
        positionSeconds = clamped
        persistResumeCheckpointIfNeeded(force = true)
        updateMediaSessionState()
        pushNotification()
    }

    private fun persistResumeCheckpointIfNeeded(force: Boolean = false) {
        val sourceId = currentPath?.takeIf { it.isNotBlank() }
        if (sourceId == null) {
            clearResumeCheckpoint()
            return
        }

        val trackLoaded = NativeBridge.getTrackSampleRate() > 0
        val storedSourceId = prefs.getString(AppPreferenceKeys.SESSION_RESUME_SOURCE_ID, null)
        val playbackCapabilities = if (trackLoaded) {
            NativeBridge.getPlaybackCapabilities()
        } else {
            if (storedSourceId != sourceId) {
                clearResumeCheckpoint()
                return
            }
            prefs.getInt(AppPreferenceKeys.SESSION_RESUME_PLAYBACK_CAPABILITIES, 0)
        }
        val repeatCapabilities = if (trackLoaded) {
            NativeBridge.getRepeatModeCapabilities()
        } else {
            prefs.getInt(AppPreferenceKeys.SESSION_RESUME_REPEAT_CAPABILITIES, REPEAT_CAP_ALL)
        }
        if (durationSeconds <= 0.0) {
            clearResumeCheckpoint()
            return
        }
        if (positionSeconds > durationSeconds + RESUME_POSITION_DURATION_EPSILON_SECONDS) {
            // Some looping cores can report elapsed time beyond declared duration.
            // Treat this as non-resumable timeline data.
            clearResumeCheckpoint()
            return
        }

        val clampedDuration = durationSeconds.coerceAtLeast(0.0)
        val clampedPosition = positionSeconds.coerceIn(0.0, clampedDuration)
        val positionBucket = (clampedPosition * 2.0).toInt()
        val durationBucket = (clampedDuration * 2.0).toInt()

        if (
            !force &&
            hasPersistedResumeCheckpoint &&
            sourceId == lastPersistedResumeSourceId &&
            positionBucket == lastPersistedResumePositionBucket &&
            durationBucket == lastPersistedResumeDurationBucket &&
            isPlaying == lastPersistedResumeIsPlaying &&
            currentTitle == lastPersistedResumeTitle &&
            currentArtist == lastPersistedResumeArtist &&
            playbackCapabilities == lastPersistedResumePlaybackCaps &&
            repeatCapabilities == lastPersistedResumeRepeatCaps
        ) {
            return
        }

        prefs.edit()
            .putString(AppPreferenceKeys.SESSION_RESUME_SOURCE_ID, sourceId)
            .putFloat(AppPreferenceKeys.SESSION_RESUME_POSITION_SECONDS, clampedPosition.toFloat())
            .putFloat(AppPreferenceKeys.SESSION_RESUME_DURATION_SECONDS, clampedDuration.toFloat())
            .putBoolean(AppPreferenceKeys.SESSION_RESUME_WAS_PLAYING, isPlaying)
            .putString(AppPreferenceKeys.SESSION_RESUME_TITLE, currentTitle)
            .putString(AppPreferenceKeys.SESSION_RESUME_ARTIST, currentArtist)
            .putInt(AppPreferenceKeys.SESSION_RESUME_PLAYBACK_CAPABILITIES, playbackCapabilities)
            .putInt(AppPreferenceKeys.SESSION_RESUME_REPEAT_CAPABILITIES, repeatCapabilities)
            .apply()

        hasPersistedResumeCheckpoint = true
        lastPersistedResumeSourceId = sourceId
        lastPersistedResumePositionBucket = positionBucket
        lastPersistedResumeDurationBucket = durationBucket
        lastPersistedResumeIsPlaying = isPlaying
        lastPersistedResumeTitle = currentTitle
        lastPersistedResumeArtist = currentArtist
        lastPersistedResumePlaybackCaps = playbackCapabilities
        lastPersistedResumeRepeatCaps = repeatCapabilities
    }

    private fun clearResumeCheckpoint() {
        if (!hasPersistedResumeCheckpoint && !prefs.contains(AppPreferenceKeys.SESSION_RESUME_SOURCE_ID)) return
        prefs.edit()
            .remove(AppPreferenceKeys.SESSION_RESUME_SOURCE_ID)
            .remove(AppPreferenceKeys.SESSION_RESUME_POSITION_SECONDS)
            .remove(AppPreferenceKeys.SESSION_RESUME_DURATION_SECONDS)
            .remove(AppPreferenceKeys.SESSION_RESUME_WAS_PLAYING)
            .remove(AppPreferenceKeys.SESSION_RESUME_TITLE)
            .remove(AppPreferenceKeys.SESSION_RESUME_ARTIST)
            .remove(AppPreferenceKeys.SESSION_RESUME_PLAYBACK_CAPABILITIES)
            .remove(AppPreferenceKeys.SESSION_RESUME_REPEAT_CAPABILITIES)
            .apply()
        hasPersistedResumeCheckpoint = false
        lastPersistedResumeSourceId = null
        lastPersistedResumePositionBucket = Int.MIN_VALUE
        lastPersistedResumeDurationBucket = Int.MIN_VALUE
        lastPersistedResumeIsPlaying = null
        lastPersistedResumeTitle = null
        lastPersistedResumeArtist = null
        lastPersistedResumePlaybackCaps = Int.MIN_VALUE
        lastPersistedResumeRepeatCaps = Int.MIN_VALUE
    }

    private fun updateMediaSessionState() {
        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_FAST_FORWARD or
                    PlaybackState.ACTION_REWIND or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SKIP_TO_NEXT
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
        if (!isPlaying) {
            if (isForegroundNotificationShown) {
                stopForegroundCompat(removeNotification = false)
                isForegroundNotificationShown = false
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
            return
        }
        try {
            startForeground(NOTIFICATION_ID, notification)
            isForegroundNotificationShown = true
        } catch (error: RuntimeException) {
            val blockedForegroundStart =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    error is ForegroundServiceStartNotAllowedException
            if (!blockedForegroundStart) throw error
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_PLAYER_FROM_NOTIFICATION, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            100,
            launchIntent,
            immutableUpdateCurrentPendingIntentFlags()
        )
        val stopIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP_CLEAR),
            immutableUpdateCurrentPendingIntentFlags()
        )
        val previousTrackIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, PlaybackService::class.java).setAction(ACTION_PREVIOUS_TRACK),
            immutableUpdateCurrentPendingIntentFlags()
        )
        val toggleIntent = PendingIntent.getService(
            this,
            103,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            immutableUpdateCurrentPendingIntentFlags()
        )
        val nextTrackIntent = PendingIntent.getService(
            this,
            104,
            Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT_TRACK),
            immutableUpdateCurrentPendingIntentFlags()
        )
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }
        notificationBuilder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(currentArtwork ?: fallbackIconBitmap())
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(launchPendingIntent)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(buildNotificationAction(android.R.drawable.ic_media_previous, "Previous track", previousTrackIntent))
            .addAction(
                buildNotificationAction(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    toggleIntent
                )
            )
            .addAction(buildNotificationAction(android.R.drawable.ic_media_next, "Next track", nextTrackIntent))
            .addAction(buildNotificationAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent))
        return notificationBuilder.build()
    }

    private fun immutableUpdateCurrentPendingIntentFlags(): Int {
        val baseFlags = PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            baseFlags or PendingIntent.FLAG_IMMUTABLE
        } else {
            baseFlags
        }
    }

    private fun buildNotificationAction(
        iconResId: Int,
        title: String,
        pendingIntent: PendingIntent
    ): Notification.Action {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, iconResId),
                title,
                pendingIntent
            ).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Action.Builder(iconResId, title, pendingIntent).build()
        }
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

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
                .build()
            audioFocusRequest = request
            val result = audioManager.requestAudioFocus(request)
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun duckAudio() {
        if (isDucked) return
        isDucked = true
        originalMasterVolume = NativeBridge.getMasterGain()
        // Duck to -12dB (about 25% volume)
        NativeBridge.setMasterGain(originalMasterVolume - 12f)
    }

    private fun unduckAudio() {
        if (!isDucked) return
        isDucked = false
        NativeBridge.setMasterGain(originalMasterVolume)
    }

    companion object {
        private const val CHANNEL_ID = "silicon_player_playback"
        private const val NOTIFICATION_ID = 1101

        private const val PREFS_NAME = "silicon_player_settings"
        private const val PREF_RESPOND_MEDIA_BUTTONS = "respond_headphone_media_buttons"
        private const val PREF_PAUSE_ON_DISCONNECT = "pause_on_headphone_disconnect"
        private const val PREF_FADE_PAUSE_RESUME = "fade_pause_resume"
        private const val PREF_AUDIO_FOCUS_INTERRUPT = "audio_focus_interrupt"
        private const val PREF_AUDIO_DUCKING = "audio_ducking"
        private const val PREF_SESSION_CURRENT_PATH = "session_current_path"
        private const val RESUME_POSITION_DURATION_EPSILON_SECONDS = 0.05
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
        const val ACTION_PREVIOUS_TRACK = "com.flopster101.siliconplayer.action.PREVIOUS_TRACK"
        const val ACTION_NEXT_TRACK = "com.flopster101.siliconplayer.action.NEXT_TRACK"
        const val ACTION_SEEK_BACK = "com.flopster101.siliconplayer.action.SEEK_BACK"
        const val ACTION_SEEK_FORWARD = "com.flopster101.siliconplayer.action.SEEK_FORWARD"
        const val ACTION_STOP_CLEAR = "com.flopster101.siliconplayer.action.STOP_CLEAR"
        const val ACTION_REFRESH_SETTINGS = "com.flopster101.siliconplayer.action.REFRESH_SETTINGS"
        const val ACTION_BROADCAST_CLEARED = "com.flopster101.siliconplayer.action.BROADCAST_CLEARED"
        const val ACTION_BROADCAST_PREVIOUS_TRACK_REQUEST = "com.flopster101.siliconplayer.action.BROADCAST_PREVIOUS_TRACK_REQUEST"
        const val ACTION_BROADCAST_NEXT_TRACK_REQUEST = "com.flopster101.siliconplayer.action.BROADCAST_NEXT_TRACK_REQUEST"
        const val EXTRA_OPEN_PLAYER_FROM_NOTIFICATION = "extra_open_player_from_notification"
        @Volatile
        private var isServiceAlive = false

        private fun startServiceSafely(context: Context, intent: Intent) {
            try {
                context.startService(intent)
            } catch (error: RuntimeException) {
                val blockedBackgroundStart =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        error is IllegalStateException
                if (!blockedBackgroundStart) throw error
            }
        }

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
            val shouldStartAsForeground = path != null && isPlaying && !isServiceAlive
            if (!shouldStartAsForeground) {
                startServiceSafely(context, intent)
                return
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: RuntimeException) {
                val blockedForegroundStart =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        error is ForegroundServiceStartNotAllowedException
                if (blockedForegroundStart) {
                    startServiceSafely(context, intent)
                    return
                }
                throw error
            }
        }

        fun refreshSettings(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_REFRESH_SETTINGS)
            context.startService(intent)
        }
    }
}
