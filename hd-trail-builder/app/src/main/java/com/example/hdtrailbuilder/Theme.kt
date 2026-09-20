package com.example.hdtrailbuilder

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Clay = Color(0xFFC2622C)
private val ClayLight = Color(0xFFFF9D5C)
private val Moss = Color(0xFF2E7D4F)
private val MossLight = Color(0xFF63D18C)

private val LightColors = lightColorScheme(
    primary = Clay,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC7),
    onPrimaryContainer = Color(0xFF3A1600),
    secondary = Moss,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F0D6),
    onSecondaryContainer = Color(0xFF07220F),
    background = Color(0xFFFAF8F6),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFAF8F6),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFEDE4DD),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85736A),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = ClayLight,
    onPrimary = Color(0xFF3A1600),
    primaryContainer = Color(0xFF8A4318),
    onPrimaryContainer = Color(0xFFFFDBC7),
    secondary = MossLight,
    onSecondary = Color(0xFF07220F),
    secondaryContainer = Color(0xFF1D5334),
    onSecondaryContainer = Color(0xFFC8F0D6),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFEBE1DA),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFEBE1DA),
    surfaceVariant = Color(0xFF3B322C),
    onSurfaceVariant = Color(0xFFD6C6BC),
    outline = Color(0xFF9E8D83),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

@Composable
fun HdTrailBuilderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
