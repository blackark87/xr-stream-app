package blackark.app.vr.ui.theme

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
import androidx.compose.ui.platform.LocalContext

private val StreamingDarkColorScheme = darkColorScheme(
    primary = Color(0xFFEE7B56),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C2818),
    onPrimaryContainer = Color(0xFFFFDACD),
    secondary = Color(0xFFFFB868),
    onSecondary = Color(0xFF3D2000),
    secondaryContainer = Color(0xFF5A3600),
    onSecondaryContainer = Color(0xFFFFE1BF),
    tertiary = Color(0xFF7BC690),
    onTertiary = Color(0xFF09301A),
    tertiaryContainer = Color(0xFF214B2E),
    onTertiaryContainer = Color(0xFFC9F2D5),
    background = Color(0xFF161311),
    onBackground = Color(0xFFF6F2EE),
    surface = Color(0xFF21201E),
    onSurface = Color(0xFFF6F2EE),
    surfaceVariant = Color(0xFF2B2926),
    onSurfaceVariant = Color(0xFFD8CFC7),
    surfaceTint = Color(0xFFEE7B56),
    outline = Color(0xFF7E746B),
    outlineVariant = Color(0xFF5F5750),
    error = Color(0xFFFF9A8C),
    onError = Color(0xFF5C0C05),
    errorContainer = Color(0xFF7A1A12),
    onErrorContainer = Color(0xFFFFDAD4),
    scrim = Color(0xFF100B09).copy(alpha = 0.86f),
)

private val StreamingLightColorScheme = lightColorScheme(
    primary = Color(0xFFD8502F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8C8),
    onPrimaryContainer = Color(0xFF3D1407),
    secondary = Color(0xFFF08A3C),
    onSecondary = Color(0xFF402103),
    secondaryContainer = Color(0xFFFFDDBA),
    onSecondaryContainer = Color(0xFF2D1600),
    tertiary = Color(0xFF2E7D4B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC5EFD0),
    onTertiaryContainer = Color(0xFF0E2B18),
    background = Color(0xFFF7F1E7),
    onBackground = Color(0xFF2A241E),
    surface = Color(0xFFF2EBDD),
    onSurface = Color(0xFF2A241E),
    surfaceVariant = Color(0xFFE9E0D1),
    onSurfaceVariant = Color(0xFF5D554C),
    outline = Color(0xFFA69888),
    outlineVariant = Color(0xFFD1C3B2),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)
@Composable
fun XRStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> StreamingDarkColorScheme
            else -> StreamingLightColorScheme
        }

    SideEffect {
        StreamingThemeState.isDarkMode = darkTheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
