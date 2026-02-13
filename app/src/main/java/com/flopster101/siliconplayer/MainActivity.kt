package com.flopster101.siliconplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.flopster101.siliconplayer.ui.theme.SiliconPlayerTheme
import java.io.File

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
    val repository = remember { com.flopster101.siliconplayer.data.FileRepository() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as MainActivity

    // Permission handling
    var hasPermission by remember { mutableStateOf(false) }
    val permissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
        arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            hasPermission = true
        } else {
            launcher.launch(permissions)
        }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Please grant storage permissions to access audio files.")
        }
        return
    }

    when (val screen = currentScreen) {
        is Screen.FileBrowser -> {
            com.flopster101.siliconplayer.ui.screens.FileBrowserScreen(
                repository = repository,
                onFileSelected = { file ->
                    selectedFile = file
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
                        activity.startEngine()
                    },
                    onStop = { activity.stopEngine() }
                )
            }
        }
    }
}

sealed class Screen {
    object FileBrowser : Screen()
    object Player : Screen()
}
