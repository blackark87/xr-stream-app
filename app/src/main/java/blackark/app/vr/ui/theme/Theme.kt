package blackark.app.vr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

private val StreamingDarkColorScheme = darkColorScheme(
    primary = Color(0xFFC4B5FD),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE9FE),
    inversePrimary = Color(0xFF6D28D9),
    secondary = Color(0xFFD8B4FE),
    onSecondary = Color(0xFF3B0764),
    secondaryContainer = Color(0xFF581C87),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF2E1065),
    tertiaryContainer = Color(0xFF5B21B6),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = Color(0xFF09070D),
    onBackground = Color(0xFFF8F5FF),
    surface = Color(0xFF15111C),
    onSurface = Color(0xFFF8F5FF),
    surfaceVariant = Color(0xFF2A2234),
    onSurfaceVariant = Color(0xFFD4C9E0),
    surfaceTint = Color(0xFFC4B5FD),
    inverseSurface = Color(0xFFECE6F2),
    inverseOnSurface = Color(0xFF302A36),
    surfaceDim = Color(0xFF09070D),
    surfaceBright = Color(0xFF332B3F),
    surfaceContainerLowest = Color(0xFF070509),
    surfaceContainerLow = Color(0xFF100D15),
    surfaceContainer = Color(0xFF17121F),
    surfaceContainerHigh = Color(0xFF201928),
    surfaceContainerHighest = Color(0xFF2A2134),
    outline = Color(0xFF9A8DA8),
    outlineVariant = Color(0xFF4A4056),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

private val StreamingLightColorScheme = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    inversePrimary = Color(0xFFC4B5FD),
    secondary = Color(0xFF7E22CE),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF3B0764),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF2E1065),
    background = Color(0xFFF8F7FC),
    onBackground = Color(0xFF1D1824),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1D1824),
    surfaceVariant = Color(0xFFE8E1EF),
    onSurfaceVariant = Color(0xFF4A4452),
    surfaceTint = Color(0xFF6D28D9),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    surfaceDim = Color(0xFFDED8E4),
    surfaceBright = Color(0xFFFFFBFF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F1F8),
    surfaceContainer = Color(0xFFEFEAF3),
    surfaceContainerHigh = Color(0xFFE9E4ED),
    surfaceContainerHighest = Color(0xFFE3DEE7),
    outline = Color(0xFF7B7482),
    outlineVariant = Color(0xFFCCC4D0),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

@Composable
fun XRStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) StreamingDarkColorScheme else StreamingLightColorScheme

    SideEffect {
        StreamingThemeState.isDarkMode = darkTheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
