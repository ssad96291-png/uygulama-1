package dev.akiskontrol.app.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Mint = Color(0xFF55E1BC)
internal val Blue = Color(0xFF7EB7FF)
internal val Coral = Color(0xFFFF8D9B)
internal val Amber = Color(0xFFF3CB7B)

private val AkisTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

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
        outline = Color(0xFF66766E), outlineVariant = Color(0xFF2C3C36), error = Coral,
    ) else lightColorScheme(
        primary = Color(0xFF00884F), onPrimary = Color.White,
        primaryContainer = Color(0xFFCDF5E2), onPrimaryContainer = Color(0xFF063B2B),
        secondary = Color(0xFF427563), secondaryContainer = Color(0xFFE5F6ED),
        onSecondaryContainer = Color(0xFF163D2E),
        background = Color(0xFFFCFBFA), onBackground = Color(0xFF10151F),
        surface = Color(0xFFFFFEFD), onSurface = Color(0xFF10151F),
        surfaceVariant = Color(0xFFF0F0ED), onSurfaceVariant = Color(0xFF565B63),
        outline = Color(0xFFADB3B5), outlineVariant = Color(0xFFE0E2DF),
        surfaceTint = Color(0xFF00884F), error = Color(0xFFB3263D),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = AkisTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(26.dp)),
        content = content,
    )
}
