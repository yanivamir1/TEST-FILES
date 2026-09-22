package com.example.dhtrailbuilder

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Black canvas with off-white text and muted accents used only for meaning - Yaniv's
// signature dark style: smooth, low-fatigue, never a colorful wash.
private val CanvasBlack = Color(0xFF0A0A0B)
private val TextPrimary = Color(0xFFECEAE6)
private val TextSecondary = Color(0xFF9A9DA3)

private val CreamPrimary = Color(0xFFEDEBE6)
private val CreamOnPrimary = Color(0xFF1B1A17)

private val AccentGreen = Color(0xFF6FCB9F)
private val AccentRed = Color(0xFFE08A80)

private val GlassSurface = Color(0xFF131315)
private val GlassSurfaceLifted = Color(0xFF1B1B1E)
private val HairlineBorder = Color(0x12FFFFFF)

private val DhDarkColors = darkColorScheme(
    primary = CreamPrimary,
    onPrimary = CreamOnPrimary,
    primaryContainer = Color(0xFF2A2823),
    onPrimaryContainer = CreamPrimary,
    secondary = AccentGreen,
    onSecondary = Color(0xFF0B2117),
    secondaryContainer = Color(0xFF15221B),
    onSecondaryContainer = AccentGreen,
    background = CanvasBlack,
    onBackground = TextPrimary,
    surface = CanvasBlack,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = TextSecondary,
    inverseSurface = GlassSurfaceLifted,
    outline = HairlineBorder,
    outlineVariant = Color(0x0DFFFFFF),
    error = AccentRed,
    onError = Color(0xFF2A0E0B),
    errorContainer = Color(0xFF201412),
    onErrorContainer = AccentRed
)

private val DhShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val BaseTypography = Typography()
private val DhTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.Light),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Light),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Light),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = BaseTypography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.1.sp
    ),
    labelMedium = BaseTypography.labelMedium.copy(
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.9.sp
    )
)

/** Always dark - smooth and low-fatigue is the point, light mode is not offered. */
@Composable
fun DhTrailBuilderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DhDarkColors,
        shapes = DhShapes,
        typography = DhTypography,
        content = content
    )
}
