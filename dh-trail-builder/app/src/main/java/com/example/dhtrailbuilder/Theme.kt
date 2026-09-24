package com.example.dhtrailbuilder

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Dark mode: black canvas, off-white text, muted accents used only for meaning. ---
private val CanvasBlack = Color(0xFF0A0A0B)
private val DarkTextPrimary = Color(0xFFF7F5F1)
private val DarkTextSecondary = Color(0xFFC4C7CC)

private val CreamPrimary = Color(0xFFEDEBE6)
private val CreamOnPrimary = Color(0xFF1B1A17)

private val DarkGreen = Color(0xFF6FCB9F)
private val DarkRed = Color(0xFFE08A80)

private val DarkGlassSurface = Color(0xFF131315)
private val DarkGlassSurfaceLifted = Color(0xFF1B1B1E)
private val DarkHairline = Color(0x12FFFFFF)

private val DarkColors = darkColorScheme(
    primary = CreamPrimary,
    onPrimary = CreamOnPrimary,
    primaryContainer = Color(0xFF2A2823),
    onPrimaryContainer = CreamPrimary,
    secondary = DarkGreen,
    onSecondary = Color(0xFF0B2117),
    secondaryContainer = Color(0xFF15221B),
    onSecondaryContainer = DarkGreen,
    background = CanvasBlack,
    onBackground = DarkTextPrimary,
    surface = CanvasBlack,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkGlassSurface,
    onSurfaceVariant = DarkTextSecondary,
    inverseSurface = DarkGlassSurfaceLifted,
    outline = DarkHairline,
    outlineVariant = Color(0x0DFFFFFF),
    error = DarkRed,
    onError = Color(0xFF2A0E0B),
    errorContainer = Color(0xFF201412),
    onErrorContainer = DarkRed
)

// --- Light mode: warm ivory canvas, near-black ink, same principles inverted. ---
private val CanvasIvory = Color(0xFFFAF9F6)
private val LightTextPrimary = Color(0xFF111113)
private val LightTextSecondary = Color(0xFF45484E)

private val InkPrimary = Color(0xFF1C1C1E)
private val InkOnPrimary = Color(0xFFFAF9F6)

private val LightGreen = Color(0xFF2E9E6E)
private val LightRed = Color(0xFFC6584C)

private val LightGlassSurface = Color(0xFFF1F0EC)
private val LightGlassSurfaceLifted = Color(0xFFEDECE7)
private val LightHairline = Color(0x141C1C1E)

private val LightColors = lightColorScheme(
    primary = InkPrimary,
    onPrimary = InkOnPrimary,
    primaryContainer = Color(0xFFE7E6E1),
    onPrimaryContainer = InkPrimary,
    secondary = LightGreen,
    onSecondary = Color(0xFFF4FBF7),
    secondaryContainer = Color(0xFFDCF0E6),
    onSecondaryContainer = LightGreen,
    background = CanvasIvory,
    onBackground = LightTextPrimary,
    surface = CanvasIvory,
    onSurface = LightTextPrimary,
    surfaceVariant = LightGlassSurface,
    onSurfaceVariant = LightTextSecondary,
    inverseSurface = LightGlassSurfaceLifted,
    outline = LightHairline,
    outlineVariant = Color(0x0A1C1C1E),
    error = LightRed,
    onError = Color(0xFFFBF2F0),
    errorContainer = Color(0xFFF3E2DF),
    onErrorContainer = LightRed
)

/** The four-hue iridescent glow: mint, soft gold, periwinkle, orchid - Yaniv's signature ambient. */
internal data class GlowStop(val color: Color, val xFrac: Float, val yFrac: Float, val alpha: Float)

private val GlowMint = Color(140, 224, 196)
private val GlowGold = Color(232, 214, 158)
private val GlowBlue = Color(150, 176, 232)
private val GlowOrchid = Color(198, 150, 214)

internal val DarkGlowStops = listOf(
    GlowStop(GlowMint, 0.32f, 0.30f, 0.20f),
    GlowStop(GlowGold, 0.70f, 0.24f, 0.15f),
    GlowStop(GlowBlue, 0.66f, 0.68f, 0.19f),
    GlowStop(GlowOrchid, 0.28f, 0.70f, 0.15f)
)

internal val LightGlowStops = listOf(
    GlowStop(GlowMint, 0.32f, 0.30f, 0.14f),
    GlowStop(GlowGold, 0.70f, 0.24f, 0.13f),
    GlowStop(GlowBlue, 0.66f, 0.68f, 0.15f),
    GlowStop(GlowOrchid, 0.28f, 0.70f, 0.13f)
)

/** The light-mode-only glowing CTA gradient (dark mode's CTA stays a flat solid cream pill). */
internal val CtaGradientColors = listOf(
    Color(0xFF8FE3C0), Color(0xFFE8D69E), Color(0xFF96B0E8), Color(0xFFC29CD0)
)
internal val CtaOnGradient = Color(0xFF171512)

private val DhShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Sizes and weights bumped up from stock Material3 defaults so numbers and labels stay
// readable in direct sunlight on the trail, not just on a dim indoor screen - nothing below
// Medium weight, since thin strokes are the first thing glare washes out.
private val BaseTypography = Typography()
private val DhTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontSize = 42.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = BaseTypography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodySmall = BaseTypography.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelLarge = BaseTypography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    labelMedium = BaseTypography.labelMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp
    ),
    labelSmall = BaseTypography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp
    )
)

/** Whether the app is in dark mode right now, and how to flip it - read anywhere via `.current`. */
data class AppThemeState(val isDark: Boolean, val toggle: () -> Unit)

val LocalAppTheme = staticCompositionLocalOf { AppThemeState(isDark = true, toggle = {}) }

internal val GlowStops: List<GlowStop>
    @Composable get() = if (LocalAppTheme.current.isDark) DarkGlowStops else LightGlowStops

private const val PREFS_NAME = "dh_trail_builder_prefs"
private const val KEY_DARK_MODE = "dark_mode"

fun loadDarkModePreference(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_DARK_MODE, true)

fun saveDarkModePreference(context: Context, isDark: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_DARK_MODE, isDark)
        .apply()
}

/** Dark by default; light mode is a first-class alternative, toggled from the top bar. */
@Composable
fun DhTrailBuilderTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = DhShapes,
        typography = DhTypography,
        content = content
    )
}
