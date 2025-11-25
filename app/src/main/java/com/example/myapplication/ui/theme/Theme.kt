package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val StreamingDarkColorScheme = darkColorScheme(
    primary = NetflixRed,
    secondary = AccentGold,
    tertiary = SuccessGreen,
    background = DarkBackground,
    surface = CardBackground,
    surfaceVariant = CardBackgroundHover,
    onPrimary = TextPrimary,
    onSecondary = StreamingBlack,
    onTertiary = StreamingBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = DividerGray,
    error = ErrorRed,
    onError = TextPrimary
)

// Light theme disabled for streaming app (always use dark theme for Netflix-like experience)
private val StreamingLightColorScheme = lightColorScheme(
    primary = NetflixDarkRed,
    secondary = AccentGold,
    tertiary = SuccessGreen
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Always use dark theme for streaming app
    // Dynamic color disabled for consistent Netflix-like experience
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Force dark theme for Netflix/Disney+ style
    val colorScheme = StreamingDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}