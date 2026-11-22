package lk.ac.nibm.hdse.fixly_20.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = FixlyPrimary,
    onPrimary = FixlyOnPrimary,
    primaryContainer = FixlyPrimaryContainer,
    onPrimaryContainer = FixlyOnPrimaryContainer,
    secondary = FixlyNavy,
    onSecondary = FixlyOnNavy,
    secondaryContainer = FixlySurfaceVariantLight,
    onSecondaryContainer = FixlyNavyLight,
    tertiary = FixlySuccess,
    onTertiary = FixlyOnPrimary,
    tertiaryContainer = FixlySuccessContainer,
    onTertiaryContainer = FixlyOnSuccessContainer,
    error = FixlyEmergency,
    onError = FixlyOnPrimary,
    errorContainer = FixlyEmergencyContainer,
    onErrorContainer = FixlyOnEmergencyContainer,
    background = FixlyBackgroundLight,
    onBackground = FixlyOnSurfaceLight,
    surface = FixlySurfaceLight,
    onSurface = FixlyOnSurfaceLight,
    surfaceVariant = FixlySurfaceVariantLight,
    onSurfaceVariant = FixlyOnSurfaceVariantLight,
    outline = FixlyOutlineLight,
    outlineVariant = FixlyOutlineLight
)

private val DarkColorScheme = darkColorScheme(
    primary = FixlyPrimaryDark,
    onPrimary = FixlyOnPrimary,
    primaryContainer = FixlyPrimaryContainerDark,
    onPrimaryContainer = FixlyOnPrimaryContainerDark,
    secondary = FixlyNavyLight,
    onSecondary = FixlyOnNavy,
    secondaryContainer = FixlySurfaceVariantDark,
    onSecondaryContainer = FixlyOnSurfaceDark,
    tertiary = FixlySuccess,
    onTertiary = FixlyOnPrimary,
    tertiaryContainer = FixlySuccessContainer,
    onTertiaryContainer = FixlyOnSuccessContainer,
    error = FixlyEmergency,
    onError = FixlyOnPrimary,
    errorContainer = FixlyEmergencyContainer,
    onErrorContainer = FixlyOnEmergencyContainer,
    background = FixlyBackgroundDark,
    onBackground = FixlyOnSurfaceDark,
    surface = FixlySurfaceDark,
    onSurface = FixlyOnSurfaceDark,
    surfaceVariant = FixlySurfaceVariantDark,
    onSurfaceVariant = FixlyOnSurfaceVariantDark,
    outline = FixlyOutlineDark,
    outlineVariant = FixlyOutlineDark
)

@Composable
fun Fixly_20Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
