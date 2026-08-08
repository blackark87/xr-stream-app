package blackark.app.vr.ui.theme

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

private val VioletLightPalette = StreamingPalette(
    primary = Color(0xFF6D28D9),
    primaryPressed = Color(0xFF5521A7),
    contrast = Color(0xFF17121F),
    background = Color(0xFFF8F7FC),
    card = Color(0xFFFFFBFF),
    cardHover = Color(0xFFF0EBF6),
    divider = Color(0xFFD8D0E2),
    textPrimary = Color(0xFF1D1824),
    textSecondary = Color(0xFF5F5868),
    textTertiary = Color(0xFF7C7387),
    accent = Color(0xFF8B5CF6),
    success = Color(0xFF267A4B),
    error = Color(0xFFB3261E),
    gradientStart = Color(0xFFFBF9FF),
    gradientEnd = Color(0xFFEFEAF6),
)
private val VioletDarkPalette = StreamingPalette(
    primary = Color(0xFFC4B5FD),
    primaryPressed = Color(0xFFA78BFA),
    contrast = Color(0xFF09070D),
    background = Color(0xFF09070D),
    card = Color(0xFF15111C),
    cardHover = Color(0xFF231B2D),
    divider = Color(0xFF493C59),
    textPrimary = Color(0xFFF8F5FF),
    textSecondary = Color(0xFFD4C9E0),
    textTertiary = Color(0xFFA99CB7),
    accent = Color(0xFFA78BFA),
    success = Color(0xFF6ED59A),
    error = Color(0xFFFFB4AB),
    gradientStart = Color(0xFF09070D),
    gradientEnd = Color(0xFF191222),
)

private val activePalette: StreamingPalette
    get() = if (StreamingThemeState.isDarkMode) VioletDarkPalette else VioletLightPalette

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
