package com.flopster101.siliconplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.* // Import for remember, mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.ui.theme.SiliconPlayerTheme
import java.io.File
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Environment
import kotlinx.coroutines.delay

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

    companion object {
        init {
            System.loadLibrary("siliconplayer")
        }
    }
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.FileBrowser) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var duration by remember { mutableDoubleStateOf(0.0) }
    var position by remember { mutableDoubleStateOf(0.0) }
    var looping by remember { mutableStateOf(false) }
    var metadataTitle by remember { mutableStateOf("") }
    var metadataArtist by remember { mutableStateOf("") }
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

    LaunchedEffect(currentScreen, selectedFile) {
        while (currentScreen is Screen.Player && selectedFile != null) {
            duration = activity.getDuration()
            position = activity.getPosition()
            delay(250)
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

    when (currentScreen) {
        is Screen.FileBrowser -> {
            com.flopster101.siliconplayer.ui.screens.FileBrowserScreen(
                repository = repository,
                onFileSelected = { file ->
                    selectedFile = file
                    metadataTitle = ""
                    metadataArtist = ""
                    currentScreen = Screen.Player
                }
            )
        }
        is Screen.Player -> {
            selectedFile?.let { file ->
                com.flopster101.siliconplayer.ui.screens.PlayerScreen(
                    file = file,
                    onBack = { currentScreen = Screen.FileBrowser },
                    onPlay = {
                        activity.loadAudio(file.absolutePath)
                        metadataTitle = activity.getTrackTitle()
                        metadataArtist = activity.getTrackArtist()
                        activity.setLooping(looping)
                        activity.startEngine()
                    },
                    onStop = { activity.stopEngine() },
                    durationSeconds = duration,
                    positionSeconds = position,
                    title = metadataTitle,
                    artist = metadataArtist,
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

sealed class Screen {
    object FileBrowser : Screen()
    object Player : Screen()
}
