package net.fenki.otp_sync.ui.theme

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

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8B4513),      // brown_500
    onPrimary = Color(0xFFffffff),    // black text on yellow
    primaryContainer = Color(0xFFFFF3B1), // yellow_200
    onPrimaryContainer = Color(0xFF000000),
    
    secondary = Color(0xFFFFE300),    // yellow_500
    onSecondary = Color(0xFF000000),  // white text on brown
    secondaryContainer = Color(0xFFBC8F6A), // brown_200
    onSecondaryContainer = Color(0xFF000000),
    
    background = Color(0xFFFFFBE6),   // very light yellow
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFE300),      // brown_200
    onPrimary = Color(0xFF000000),    // black text on yellow
    primaryContainer = Color(0xFFFFE300), // yellow_500
    onPrimaryContainer = Color(0xFF000000),
    
    secondary = Color(0xFFFFCC00),    // yellow_700
    onSecondary = Color(0xFF000000),  // black text on light brown
    secondaryContainer = Color(0xFF8B4513), // brown_500
    onSecondaryContainer = Color(0xFFFFFFFF),
    
    background = Color(0xFF000000),   // corrected to black
    onBackground = Color(0xFFFFFFFF),  // white text on black
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFFFFFFF)
)

@Composable
fun Otp_syncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}