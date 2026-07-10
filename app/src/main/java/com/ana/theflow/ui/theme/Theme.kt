package com.ana.theflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkNeonColorScheme = darkColorScheme(
    primary = NeonPurple,
    secondary = NeonPink,
    tertiary = NeonViolet,
    background = DeepNight,
    surface = SurfaceNight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = SoftLavender,
    onSurface = SoftLavender
)

@Composable
// Applies the app Compose theme.
fun THEFLOWTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkNeonColorScheme,
        typography = AppTypography,
        content = content
    )
}
