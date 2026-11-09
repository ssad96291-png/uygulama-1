package dev.akiskontrol.app.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal val Mint = Color(0xFF55E1BC)
internal val Blue = Color(0xFF7EB7FF)
internal val Coral = Color(0xFFFF8D9B)
internal val Amber = Color(0xFFF3CB7B)

@Composable
internal fun AkisTheme(dark: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            window.isNavigationBarContrastEnforced = false
        }
    }
    val colors = if (dark) darkColorScheme(
        primary = Mint, onPrimary = Color(0xFF00382B), primaryContainer = Color(0xFF123E35),
        onPrimaryContainer = Color(0xFFB0F5DF), secondary = Blue,
        background = Color(0xFF0B121C), onBackground = Color(0xFFE8EEF5),
        surface = Color(0xFF101B29), onSurface = Color(0xFFE8EEF5),
        surfaceVariant = Color(0xFF1B2A3B), onSurfaceVariant = Color(0xFFA9B9CC),
        outline = Color(0xFF445366), error = Coral,
    ) else lightColorScheme(
        primary = Color(0xFF006E55), onPrimary = Color.White,
        primaryContainer = Color(0xFFD4F5E8), onPrimaryContainer = Color(0xFF00382B),
        secondary = Color(0xFF285E9C), background = Color(0xFFF2F6F9),
        onBackground = Color(0xFF122231), surface = Color.White,
        onSurface = Color(0xFF122231), surfaceVariant = Color(0xFFE6EDF3),
        onSurfaceVariant = Color(0xFF4C5D70), outline = Color(0xFFABB9C7),
        error = Color(0xFFAB2940),
    )
    MaterialTheme(colorScheme = colors, content = content)
}
