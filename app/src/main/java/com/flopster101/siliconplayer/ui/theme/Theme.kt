package com.flopster101.siliconplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private const val LEGACY_SYSTEM_BAR_DARK = 0xFF202020.toInt()

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun SiliconPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val supportsDarkStatusIcons = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            val supportsDarkNavIcons = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            val statusBarColor = if (!darkTheme && !supportsDarkStatusIcons) {
                LEGACY_SYSTEM_BAR_DARK
            } else {
                colorScheme.background.toArgb()
            }
            val navigationBarColor = if (!darkTheme && !supportsDarkNavIcons) {
                LEGACY_SYSTEM_BAR_DARK
            } else {
                colorScheme.surface.toArgb()
            }
            window.statusBarColor = statusBarColor
            window.navigationBarColor = navigationBarColor
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme && supportsDarkStatusIcons
                isAppearanceLightNavigationBars = !darkTheme && supportsDarkNavIcons
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
