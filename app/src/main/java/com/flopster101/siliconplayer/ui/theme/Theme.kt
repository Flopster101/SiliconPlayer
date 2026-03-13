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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private const val LEGACY_SYSTEM_BAR_DARK = 0xFF202020.toInt()

private val IconDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBAC4FF),
    onPrimary = Color(0xFF11267D),
    primaryContainer = IconBlueDark,
    onPrimaryContainer = IconBlueLight,
    secondary = Color(0xFFE1B8F0),
    onSecondary = Color(0xFF45255A),
    secondaryContainer = IconLavenderDark,
    onSecondaryContainer = IconLavenderLight,
    tertiary = Color(0xFFFFB68B),
    onTertiary = Color(0xFF552009),
    tertiaryContainer = IconPeachDark,
    onTertiaryContainer = IconPeachLight,
    background = IconNight,
    onBackground = Color(0xFFE5E1F2),
    surface = IconNight,
    onSurface = Color(0xFFE5E1F2),
    surfaceVariant = Color(0xFF44485D),
    onSurfaceVariant = Color(0xFFC6C5D6),
    outline = Color(0xFF9090A2)
)

private val IconLightColorScheme = lightColorScheme(
    primary = Color(0xFF4A5FBE),
    onPrimary = Color.White,
    primaryContainer = IconBlueLight,
    onPrimaryContainer = Color(0xFF08174A),
    secondary = Color(0xFF745089),
    onSecondary = Color.White,
    secondaryContainer = IconLavenderLight,
    onSecondaryContainer = Color(0xFF2D123D),
    tertiary = Color(0xFF92522E),
    onTertiary = Color.White,
    tertiaryContainer = IconPeachLight,
    onTertiaryContainer = Color(0xFF351000),
    background = Color(0xFFF8F8FF),
    onBackground = Color(0xFF181C2C),
    surface = Color(0xFFF8F8FF),
    onSurface = Color(0xFF181C2C),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF464654),
    outline = Color(0xFF767685)
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
        darkTheme -> IconDarkColorScheme
        else -> IconLightColorScheme
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
