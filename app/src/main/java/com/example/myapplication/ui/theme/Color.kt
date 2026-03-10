package com.example.myapplication.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

internal object StreamingThemeState {
    var isDarkMode by mutableStateOf(false)
}

private data class StreamingPalette(
    val primary: Color,
    val primaryPressed: Color,
    val contrast: Color,
    val background: Color,
    val card: Color,
    val cardHover: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val success: Color,
    val error: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
)

private val WarmLightPalette = StreamingPalette(
    primary = Color(0xFFD8502F),
    primaryPressed = Color(0xFFB43D21),
    contrast = Color(0xFF2C2621),
    background = Color(0xFFF7F1E7),
    card = Color(0xFFF2EBDD),
    cardHover = Color(0xFFE9E0D1),
    divider = Color(0xFFD1C3B2),
    textPrimary = Color(0xFF2A241E),
    textSecondary = Color(0xFF5D554C),
    textTertiary = Color(0xFF7C7268),
    accent = Color(0xFFF08A3C),
    success = Color(0xFF2E7D4B),
    error = Color(0xFFC62828),
    gradientStart = Color(0xFFF8F3EA),
    gradientEnd = Color(0xFFF1E7D8),
)
private val WarmDarkPalette = StreamingPalette(
    primary = Color(0xFFEE7B56),
    primaryPressed = Color(0xFFCF5F3C),
    contrast = Color(0xFF181411),
    background = Color(0xFF161311),
    card = Color(0xFF21201E),
    cardHover = Color(0xFF2B2926),
    divider = Color(0xFF6E655D),
    textPrimary = Color(0xFFF6F2EE),
    textSecondary = Color(0xFFD8CFC7),
    textTertiary = Color(0xFFB6ACA3),
    accent = Color(0xFFFFB868),
    success = Color(0xFF7BC690),
    error = Color(0xFFFF9A8C),
    gradientStart = Color(0xFF1A1614),
    gradientEnd = Color(0xFF25211F),
)

private val activePalette: StreamingPalette
    get() = if (StreamingThemeState.isDarkMode) WarmDarkPalette else WarmLightPalette

val NetflixRed: Color
    get() = activePalette.primary
val NetflixDarkRed: Color
    get() = activePalette.primaryPressed
val StreamingBlack: Color
    get() = activePalette.contrast
val DarkBackground: Color
    get() = activePalette.background
val CardBackground: Color
    get() = activePalette.card
val CardBackgroundHover: Color
    get() = activePalette.cardHover
val DividerGray: Color
    get() = activePalette.divider
val TextPrimary: Color
    get() = activePalette.textPrimary
val TextSecondary: Color
    get() = activePalette.textSecondary
val TextTertiary: Color
    get() = activePalette.textTertiary
val AccentGold: Color
    get() = activePalette.accent
val SuccessGreen: Color
    get() = activePalette.success
val ErrorRed: Color
    get() = activePalette.error
val GradientStart: Color
    get() = activePalette.gradientStart
val GradientEnd: Color
    get() = activePalette.gradientEnd
