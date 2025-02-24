package com.example.androiddiffusion.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),         // Deep Purple
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF), // Light Purple
    onPrimaryContainer = Color(0xFF21005E), // Dark Purple
    
    secondary = Color(0xFF03DAC6),       // Teal
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFCEFAF8), // Light Teal
    onSecondaryContainer = Color(0xFF001F1E), // Dark Teal
    
    tertiary = Color(0xFFFF4081),        // Pink
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E3), // Light Pink
    onTertiaryContainer = Color(0xFF3F0016), // Dark Pink
    
    error = Color(0xFFB00020),           // Error Red
    onError = Color.White,
    errorContainer = Color(0xFFFCD8DF),   // Light Error
    onErrorContainer = Color(0xFF370012), // Dark Error
    
    background = Color(0xFFF8F8F8),      // Light Grey
    onBackground = Color(0xFF1C1B1F),    // Dark Grey
    surface = Color(0xFFFFFBFF),         // White
    onSurface = Color(0xFF1C1B1F),       // Dark Grey
    surfaceVariant = Color(0xFFE7E0EB),  // Light Purple Grey
    onSurfaceVariant = Color(0xFF49454E), // Dark Purple Grey
    outline = Color(0xFF79747E)          // Medium Grey
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB388FF),         // Light Purple
    onPrimary = Color(0xFF311B92),       // Deep Purple
    primaryContainer = Color(0xFF4527A0), // Medium Purple
    onPrimaryContainer = Color(0xFFEDE7F6), // Very Light Purple
    
    secondary = Color(0xFF64FFDA),       // Light Teal
    onSecondary = Color(0xFF004D40),     // Dark Teal
    secondaryContainer = Color(0xFF00796B), // Medium Teal
    onSecondaryContainer = Color(0xFFB2DFDB), // Very Light Teal
    
    tertiary = Color(0xFFFFAB91),        // Light Orange
    onTertiary = Color(0xFF3E2723),      // Dark Brown
    tertiaryContainer = Color(0xFF5D4037), // Medium Brown
    onTertiaryContainer = Color(0xFFFFCCBC), // Very Light Orange
    
    error = Color(0xFFFF5252),           // Light Red
    onError = Color(0xFFB71C1C),         // Dark Red
    errorContainer = Color(0xFFC62828),   // Medium Red
    onErrorContainer = Color(0xFFFFEBEE), // Very Light Red
    
    background = Color(0xFF121212),      // Dark Background
    onBackground = Color(0xFFE0E0E0),    // Light Grey
    surface = Color(0xFF1E1E1E),         // Dark Surface
    onSurface = Color(0xFFE0E0E0),       // Light Grey
    surfaceVariant = Color(0xFF2D2D2D),  // Medium Dark Grey
    onSurfaceVariant = Color(0xFFBDBDBD), // Light Grey
    outline = Color(0xFF9E9E9E)          // Medium Grey
)

@Composable
fun AndroidDiffusionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
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
        typography = Typography,
        content = content
    )
} 