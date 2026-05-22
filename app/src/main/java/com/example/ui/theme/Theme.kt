package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryContainer,
    onPrimary = Color.White,
    primaryContainer = CobaltPrimary,
    onPrimaryContainer = CobaltOnPrimaryContainer,
    secondary = LimeSecondary,
    onSecondary = Color.White,
    secondaryContainer = LimeSecondaryContainer,
    onSecondaryContainer = LimeOnSecondaryContainer,
    background = OnSurfaceLight,
    surface = OnSurfaceLight,
    onBackground = SurfaceLight,
    onSurface = SurfaceLight,
    surfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltPrimaryContainer,
    onPrimaryContainer = CobaltOnPrimaryContainer,
    secondary = LimeSecondary,
    onSecondary = Color.White,
    secondaryContainer = LimeSecondaryContainer,
    onSecondaryContainer = LimeOnSecondaryContainer,
    background = SurfaceLight,
    surface = SurfaceLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerHighest,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorRed,
    errorContainer = ErrorRedContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Keep light theme focused as in high-contrast reference images
    dynamicColor: Boolean = false, // Set false to ensure our exact brand colors shine
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
