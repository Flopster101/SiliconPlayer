package com.flopster101.siliconplayer

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.* // Import for remember, mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import com.flopster101.siliconplayer.ui.theme.SiliconPlayerTheme
import java.io.File
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SiliconPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }

    external fun getStringFromJNI(): String
    external fun startEngine()
    external fun stopEngine()
    external fun loadAudio(path: String)
    external fun getSupportedExtensions(): Array<String>
    external fun getDuration(): Double
    external fun getPosition(): Double
    external fun seekTo(seconds: Double)
    external fun setLooping(enabled: Boolean)
    external fun getTrackTitle(): String
    external fun getTrackArtist(): String
    external fun getTrackSampleRate(): Int
    external fun getTrackChannelCount(): Int
    external fun getTrackBitDepth(): Int
    external fun getTrackBitDepthLabel(): String

    companion object {
        init {
            System.loadLibrary("siliconplayer")
        }
    }
}

@Composable
fun AppNavigation() {
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var duration by remember { mutableDoubleStateOf(0.0) }
    var position by remember { mutableDoubleStateOf(0.0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var looping by remember { mutableStateOf(false) }
    var metadataTitle by remember { mutableStateOf("") }
    var metadataArtist by remember { mutableStateOf("") }
    var metadataSampleRate by remember { mutableIntStateOf(0) }
    var metadataChannelCount by remember { mutableIntStateOf(0) }
    var metadataBitDepthLabel by remember { mutableStateOf("Unknown") }
    var artworkBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as MainActivity

    // Get supported extensions from JNI
    val supportedExtensions = remember { activity.getSupportedExtensions().toSet() }
    val repository = remember { com.flopster101.siliconplayer.data.FileRepository(supportedExtensions) }

    // Permission handling
    var hasPermission by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // Function to check permissions
    fun checkPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    // Effect to check permission on start and resume
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = checkPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!checkPermission()) {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", context.packageName))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    context.startActivity(intent)
                }
            } else {
                launcher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    LaunchedEffect(selectedFile, isPlaying) {
        while (selectedFile != null) {
            duration = activity.getDuration()
            if (isPlaying) {
                position = activity.getPosition()
            }
            delay(250)
        }
    }

    LaunchedEffect(selectedFile) {
        artworkBitmap = withContext(Dispatchers.IO) {
            selectedFile?.let { loadArtworkForFile(it) }
        }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Please grant storage permissions to access audio files.")
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.addCategory("android.intent.category.DEFAULT")
                            intent.data = Uri.parse(String.format("package:%s", context.packageName))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent()
                            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                            context.startActivity(intent)
                        }
                    } else {
                        launcher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        com.flopster101.siliconplayer.ui.screens.FileBrowserScreen(
            repository = repository,
            onFileSelected = { file ->
                selectedFile = file
                activity.loadAudio(file.absolutePath)
                metadataTitle = activity.getTrackTitle()
                metadataArtist = activity.getTrackArtist()
                metadataSampleRate = activity.getTrackSampleRate()
                metadataChannelCount = activity.getTrackChannelCount()
                metadataBitDepthLabel = activity.getTrackBitDepthLabel()
                duration = activity.getDuration()
                position = 0.0
                isPlaying = false
                artworkBitmap = null
                isPlayerExpanded = true
            }
        )

        selectedFile?.let { file ->
            if (!isPlayerExpanded) {
                MiniPlayerBar(
                    file = file,
                    title = metadataTitle.ifBlank { file.nameWithoutExtension.ifBlank { file.name } },
                    artist = metadataArtist.ifBlank { "Unknown Artist" },
                    artwork = artworkBitmap,
                    noArtworkIcon = Icons.Default.MusicNote,
                    isPlaying = isPlaying,
                    positionSeconds = position,
                    durationSeconds = duration,
                    onExpand = { isPlayerExpanded = true },
                    onPlayStop = {
                        if (isPlaying) {
                            activity.stopEngine()
                            isPlaying = false
                        } else {
                            activity.setLooping(looping)
                            activity.startEngine()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            } else {
                com.flopster101.siliconplayer.ui.screens.PlayerScreen(
                    file = file,
                    onBack = { isPlayerExpanded = false },
                    isPlaying = isPlaying,
                    onPlay = {
                        activity.setLooping(looping)
                        activity.startEngine()
                        isPlaying = true
                    },
                    onStop = {
                        activity.stopEngine()
                        isPlaying = false
                    },
                    durationSeconds = duration,
                    positionSeconds = position,
                    title = metadataTitle,
                    artist = metadataArtist,
                    sampleRateHz = metadataSampleRate,
                    channelCount = metadataChannelCount,
                    bitDepthLabel = metadataBitDepthLabel,
                    artwork = artworkBitmap,
                    isLooping = looping,
                    onSeek = { seconds -> activity.seekTo(seconds) },
                    onLoopingChanged = { enabled ->
                        looping = enabled
                        activity.setLooping(enabled)
                    }
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    file: File,
    title: String,
    artist: String,
    artwork: ImageBitmap?,
    noArtworkIcon: ImageVector,
    isPlaying: Boolean,
    positionSeconds: Double,
    durationSeconds: Double,
    onExpand: () -> Unit,
    onPlayStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatLabel = file.extension.uppercase().ifBlank { "UNKNOWN" }
    val positionLabel = formatTimeForMini(positionSeconds)
    val durationLabel = formatTimeForMini(durationSeconds)
    val progress = if (durationSeconds > 0.0) {
        (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork,
                            contentDescription = "Mini player artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = noArtworkIcon,
                                contentDescription = "No artwork",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.size(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$formatLabel • $positionLabel / $durationLabel",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayStop) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play"
                        )
                    }
                    IconButton(onClick = onExpand) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Expand player"
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

private fun formatTimeForMini(seconds: Double): String {
    val safeSeconds = seconds.coerceAtLeast(0.0).toInt()
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun loadArtworkForFile(file: File): ImageBitmap? {
    loadEmbeddedArtwork(file)?.let { return it.asImageBitmap() }
    findFolderArtworkFile(file)?.let { folderImage ->
        decodeScaledBitmapFromFile(folderImage)?.let { return it.asImageBitmap() }
    }
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

    return parent.listFiles()
        ?.firstOrNull { it.isFile && allowedNames.contains(it.name.lowercase()) }
}

private fun decodeScaledBitmapFromBytes(data: ByteArray, maxSize: Int = 1024): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}

private fun decodeScaledBitmapFromFile(file: File, maxSize: Int = 1024): Bitmap? {
    val path = file.absolutePath
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, options)
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxSize || currentHeight > maxSize) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}
