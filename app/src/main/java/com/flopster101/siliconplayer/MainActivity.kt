package com.flopster101.siliconplayer

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.runtime.* // Import for remember, mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

private enum class MainView {
    Home,
    Browser,
    Settings
}

private enum class SettingsRoute {
    Root,
    AudioPlugins,
    PluginFfmpeg,
    PluginOpenMpt,
    GeneralAudio,
    Player,
    Misc,
    Ui,
    About
}

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
    external fun isEnginePlaying(): Boolean
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    var currentView by remember { mutableStateOf(MainView.Home) }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.Root) }
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

    LaunchedEffect(selectedFile) {
        while (selectedFile != null) {
            duration = activity.getDuration()
            position = activity.getPosition()
            isPlaying = activity.isEnginePlaying()
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

    val isMiniPlayerVisible = selectedFile != null && !isPlayerExpanded
    val miniPlayerListInset = if (isMiniPlayerVisible && currentView == MainView.Browser) 108.dp else 0.dp
    val clearCurrentTrack: () -> Unit = {
        activity.stopEngine()
        selectedFile = null
        duration = 0.0
        position = 0.0
        isPlaying = false
        isPlayerExpanded = false
        metadataTitle = ""
        metadataArtist = ""
        metadataSampleRate = 0
        metadataChannelCount = 0
        metadataBitDepthLabel = "Unknown"
        artworkBitmap = null
    }

    BackHandler(enabled = isPlayerExpanded || currentView == MainView.Settings) {
        when {
            isPlayerExpanded -> isPlayerExpanded = false
            currentView == MainView.Settings && settingsRoute != SettingsRoute.Root -> {
                settingsRoute = when (settingsRoute) {
                    SettingsRoute.PluginFfmpeg, SettingsRoute.PluginOpenMpt -> SettingsRoute.AudioPlugins
                    else -> SettingsRoute.Root
                }
            }
            currentView == MainView.Settings -> currentView = MainView.Home
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentView) {
            MainView.Home -> HomeScreen(
                selectedTrack = selectedFile,
                onOpenLibrary = { currentView = MainView.Browser },
                onOpenSettings = {
                    settingsRoute = SettingsRoute.Root
                    currentView = MainView.Settings
                }
            )
            MainView.Browser -> com.flopster101.siliconplayer.ui.screens.FileBrowserScreen(
                repository = repository,
                bottomContentPadding = miniPlayerListInset,
                backHandlingEnabled = !isPlayerExpanded,
                onExitBrowser = { currentView = MainView.Home },
                onOpenSettings = {
                    settingsRoute = SettingsRoute.Root
                    currentView = MainView.Settings
                },
                onFileSelected = { file ->
                    activity.stopEngine()
                    isPlaying = false
                    selectedFile = file
                    activity.loadAudio(file.absolutePath)
                    metadataTitle = activity.getTrackTitle()
                    metadataArtist = activity.getTrackArtist()
                    metadataSampleRate = activity.getTrackSampleRate()
                    metadataChannelCount = activity.getTrackChannelCount()
                    metadataBitDepthLabel = activity.getTrackBitDepthLabel()
                    duration = activity.getDuration()
                    position = 0.0
                    artworkBitmap = null
                    isPlayerExpanded = true
                }
            )
            MainView.Settings -> SettingsScreen(
                route = settingsRoute,
                onBack = {
                    if (settingsRoute != SettingsRoute.Root) {
                        settingsRoute = when (settingsRoute) {
                            SettingsRoute.PluginFfmpeg, SettingsRoute.PluginOpenMpt -> SettingsRoute.AudioPlugins
                            else -> SettingsRoute.Root
                        }
                    } else {
                        currentView = MainView.Home
                    }
                },
                onOpenAudioPlugins = { settingsRoute = SettingsRoute.AudioPlugins },
                onOpenGeneralAudio = { settingsRoute = SettingsRoute.GeneralAudio },
                onOpenPlayer = { settingsRoute = SettingsRoute.Player },
                onOpenMisc = { settingsRoute = SettingsRoute.Misc },
                onOpenUi = { settingsRoute = SettingsRoute.Ui },
                onOpenAbout = { settingsRoute = SettingsRoute.About },
                onOpenFfmpeg = { settingsRoute = SettingsRoute.PluginFfmpeg },
                onOpenOpenMpt = { settingsRoute = SettingsRoute.PluginOpenMpt }
            )
        }

        selectedFile?.let { file ->
            if (!isPlayerExpanded) {
                val dismissState = rememberSwipeToDismissBoxState(
                    positionalThreshold = { totalDistance -> totalDistance * 0.6f },
                    confirmValueChange = { targetValue ->
                        val isDismiss = targetValue == SwipeToDismissBoxValue.StartToEnd ||
                            targetValue == SwipeToDismissBoxValue.EndToStart
                        if (isDismiss && !isPlaying) {
                            clearCurrentTrack()
                            true
                        } else {
                            false
                        }
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    backgroundContent = {},
                    enableDismissFromStartToEnd = !isPlaying,
                    enableDismissFromEndToStart = !isPlaying
                ) {
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
                        }
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    selectedTrack: File?,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Silicon Player") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open settings"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Local music library",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Browse files and play mainstream or tracker/module formats.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            androidx.compose.material3.ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                onClick = onOpenLibrary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Open Library Browser",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "Browse folders and choose a track",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            selectedTrack?.let { track ->
                Spacer(modifier = Modifier.height(14.dp))
                androidx.compose.material3.OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Current track",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = track.nameWithoutExtension.ifBlank { track.name },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    route: SettingsRoute,
    onBack: () -> Unit,
    onOpenAudioPlugins: () -> Unit,
    onOpenGeneralAudio: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenMisc: () -> Unit,
    onOpenUi: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFfmpeg: () -> Unit,
    onOpenOpenMpt: () -> Unit
) {
    val screenTitle = when (route) {
        SettingsRoute.Root -> "Settings"
        SettingsRoute.AudioPlugins -> "Audio plugins"
        SettingsRoute.PluginFfmpeg -> "FFmpeg"
        SettingsRoute.PluginOpenMpt -> "OpenMPT"
        SettingsRoute.GeneralAudio -> "General audio"
        SettingsRoute.Player -> "Player settings"
        SettingsRoute.Misc -> "Misc settings"
        SettingsRoute.Ui -> "UI settings"
        SettingsRoute.About -> "About"
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to home"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (route) {
                SettingsRoute.Root -> {
                    SettingsSectionLabel("Audio")
                    SettingsItemCard(
                        title = "Audio plugins",
                        description = "Configure each playback core/plugin.",
                        icon = Icons.Default.GraphicEq,
                        onClick = onOpenAudioPlugins
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsItemCard(
                        title = "General audio settings",
                        description = "Global output and playback behavior.",
                        icon = Icons.Default.Tune,
                        onClick = onOpenGeneralAudio
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsItemCard(
                        title = "Player settings",
                        description = "Player behavior and interaction preferences.",
                        icon = Icons.Default.Slideshow,
                        onClick = onOpenPlayer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsItemCard(
                        title = "Misc settings",
                        description = "Other app-wide preferences and utilities.",
                        icon = Icons.Default.MoreHoriz,
                        onClick = onOpenMisc
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsSectionLabel("Interface")
                    SettingsItemCard(
                        title = "UI settings",
                        description = "Appearance and layout preferences.",
                        icon = Icons.Default.Palette,
                        onClick = onOpenUi
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsSectionLabel("Info")
                    SettingsItemCard(
                        title = "About",
                        description = "App information, versions, and credits.",
                        icon = Icons.Default.Info,
                        onClick = onOpenAbout
                    )
                }
                SettingsRoute.AudioPlugins -> {
                    SettingsSectionLabel("Plugin configuration")
                    Text(
                        text = "This section will also handle enabling/disabling plugins in the future.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsItemCard(
                        title = "FFmpeg",
                        description = "Core options for mainstream audio codecs.",
                        icon = Icons.Default.GraphicEq,
                        onClick = onOpenFfmpeg
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsItemCard(
                        title = "OpenMPT",
                        description = "Core options for tracker/module playback.",
                        icon = Icons.Default.GraphicEq,
                        onClick = onOpenOpenMpt
                    )
                }
                SettingsRoute.PluginFfmpeg -> SettingsPlaceholderBody(
                    title = "FFmpeg plugin options",
                    description = "FFmpeg-specific settings will appear here."
                )
                SettingsRoute.PluginOpenMpt -> SettingsPlaceholderBody(
                    title = "OpenMPT plugin options",
                    description = "OpenMPT-specific settings will appear here."
                )
                SettingsRoute.GeneralAudio -> SettingsPlaceholderBody(
                    title = "General audio settings",
                    description = "Global audio behavior options will appear here."
                )
                SettingsRoute.Player -> SettingsPlaceholderBody(
                    title = "Player settings",
                    description = "Player controls and behavior options will appear here."
                )
                SettingsRoute.Misc -> SettingsPlaceholderBody(
                    title = "Misc settings",
                    description = "Additional app options will appear here."
                )
                SettingsRoute.Ui -> SettingsPlaceholderBody(
                    title = "UI settings",
                    description = "Theme and layout options will appear here."
                )
                SettingsRoute.About -> SettingsPlaceholderBody(
                    title = "About Silicon Player",
                    description = "Version, credits, and technical details will appear here."
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsItemCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    androidx.compose.material3.OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsPlaceholderBody(
    title: String,
    description: String
) {
    androidx.compose.material3.OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
