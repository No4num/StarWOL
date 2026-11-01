package com.starchen.wol.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 深色 + 安卓风：黑底、灰面、高亮用冷青色
val Accent = Color(0xFF7CE0FF)
val AccentDim = Color(0xFF1B3A47)
val Surface0 = Color(0xFF0B0C0E)
val Surface1 = Color(0xFF141518)
val Surface2 = Color(0xFF1D1F23)
val Surface3 = Color(0xFF2A2D32)
val TextPrimary = Color(0xFFF2F4F7)
val TextSecondary = Color(0xFF9AA1AC)
val TextTertiary = Color(0xFF5E646E)
val Danger = Color(0xFFFF6B6B)
val DangerDim = Color(0xFF3A1A1A)
val Success = Color(0xFF6BE39A)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00202A),
    primaryContainer = AccentDim,
    onPrimaryContainer = Accent,
    secondary = TextSecondary,
    onSecondary = Surface0,
    secondaryContainer = Surface2,
    onSecondaryContainer = TextPrimary,
    tertiary = Accent,
    background = Surface0,
    onBackground = TextPrimary,
    surface = Surface0,
    onSurface = TextPrimary,
    surfaceVariant = Surface1,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Color(0xFF3A3E45),
    outlineVariant = Color(0xFF262A30),
    error = Danger,
    onError = Color(0xFF2A0000),
    errorContainer = DangerDim,
    onErrorContainer = Danger,
    scrim = Color(0xCC000000)
)

@Composable
fun StarWolTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(colorScheme = DarkColors, content = content)
}
