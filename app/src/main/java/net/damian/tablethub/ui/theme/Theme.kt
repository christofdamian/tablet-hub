package net.damian.tablethub.ui.theme

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// Night mode color scheme - red-shifted for night vision preservation
private val NightColorScheme = darkColorScheme(
    primary = NightRed,
    onPrimary = NightBackground,
    primaryContainer = NightRedDim,
    onPrimaryContainer = NightRed,
    secondary = NightRedDim,
    onSecondary = NightBackground,
    secondaryContainer = NightRedDim,
    onSecondaryContainer = NightRed,
    tertiary = NightRedDim,
    onTertiary = NightBackground,
    tertiaryContainer = NightRedDim,
    onTertiaryContainer = NightRed,
    background = NightBackground,
    onBackground = NightRed,
    surface = NightBackground,
    onSurface = NightRed,
    surfaceVariant = NightBackground,
    onSurfaceVariant = NightRedDim,
    error = Color(0xFF990000),
    onError = NightBackground,
    outline = NightRedDim,
    outlineVariant = NightRedDim
)

@Composable
fun TabletHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    nightMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Night mode takes priority over all other themes
        nightMode -> NightColorScheme
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme && !nightMode
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
