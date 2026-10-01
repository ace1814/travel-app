package com.wanderpage.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// One palette on purpose: the app is paper, in light and dark system themes alike.
private val Colors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3D5C9),
    onPrimaryContainer = Color(0xFF4A1C10),
    secondary = Sepia,
    onSecondary = Color.White,
    secondaryContainer = PaperDeep,
    onSecondaryContainer = Ink,
    tertiary = Sage,
    background = Paper,
    onBackground = Ink,
    surface = PaperCard,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkSoft,
    surfaceContainer = PaperCard,
    surfaceContainerLow = PaperCard,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = PaperDeep,
    outline = Color(0xFFA89782),
    outlineVariant = Color(0xFFD6C7B0),
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = Color(0xFFF0B9A6),
)

@Composable
fun WanderpageTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        MaterialTheme(colorScheme = Colors, typography = WanderTypography, content = content)
    }
}
