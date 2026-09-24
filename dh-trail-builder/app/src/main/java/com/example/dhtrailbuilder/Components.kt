package com.example.dhtrailbuilder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun formatValue(value: Float, decimals: Int = 1): String =
    String.format(Locale.US, "%.${decimals}f", value)

/**
 * Canvas with a soft iridescent glow bleeding into it - four low-opacity radial blooms (mint,
 * gold, periwinkle, orchid) that melt into each other, never a flat colored wash. Every screen
 * sits inside this; the same recipe runs in both dark and light mode, just fainter in light.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val stops = GlowStops
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            stops.forEach { stop ->
                drawGlowOrb(
                    color = stop.color,
                    alpha = stop.alpha,
                    center = Offset(size.width * stop.xFrac, size.height * stop.yFrac),
                    radius = size.maxDimension * 0.42f
                )
            }
        }
        content()
    }
}

private fun DrawScope.drawGlowOrb(
    color: Color,
    alpha: Float,
    center: Offset,
    radius: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

internal val GlassBorder: Color
    @Composable get() = if (LocalAppTheme.current.isDark) Color(0x12FFFFFF) else Color(0x141C1C1E)

/**
 * Glass row group: near-black fill, hairline border, one small caps title. Deliberately thin -
 * a line or two of content, not a padded block - so a screen full of these still reads as one
 * clean list rather than a stack of cards.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    active: Boolean = false,
    onActivate: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .border(
            width = if (active) 1.5.dp else 1.dp,
            color = if (active) MaterialTheme.colorScheme.primary else GlassBorder,
            shape = MaterialTheme.shapes.medium
        )
        .then(if (onActivate != null) Modifier.clickable { onActivate() } else Modifier)

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

/**
 * The pinned diagram at the top of a screen. Deliberately minimal chrome - the diagram plus
 * one line saying what to capture - so the scrolling inputs below get the screen.
 */
@Composable
fun DiagramCard(
    instruction: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = GlassBorder, shape = MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            content()
            Text(
                text = instruction,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Large numeric readout: value in display type, unit small beside it. */
@Composable
fun ReadoutTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    valueColor: Color = Color.Unspecified
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                style = if (emphasis) MaterialTheme.typography.displaySmall
                else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                // Explicit fallback: tiles also sit in bare Rows (run stats), where the default
                // content color is black on the dark background.
                color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/** Result of a calculation: one headline figure plus supporting metrics. */
@Composable
fun ResultCard(
    primaryLabel: String,
    primaryValue: String,
    primaryUnit: String,
    modifier: Modifier = Modifier,
    secondary: List<Pair<String, String>> = emptyList()
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = GlassBorder, shape = MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReadoutTile(
                label = primaryLabel,
                value = primaryValue,
                unit = primaryUnit,
                emphasis = true,
                valueColor = MaterialTheme.colorScheme.secondary
            )
            if (secondary.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    secondary.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = label, style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Message shown in place of a result when inputs are incomplete or physically invalid. */
@Composable
fun NoticeCard(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.35f) else GlassBorder,
                shape = MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * A muted label, then either a compact label-left/value-right row (no `step`), or - when a
 * step is given - the label on its own line above a full-width row with a large +/- button
 * pinned to each edge and the value centered between them, as far apart as the row allows so
 * a miss-tap on one doesn't land on the other.
 */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    step: Float? = null,
    stepDecimals: Int = 1
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (step != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SteppedValueRow(
                value = value,
                onValueChange = onValueChange,
                unit = unit,
                isError = isError,
                step = step,
                decimals = stepDecimals,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                InlineValueField(
                    value = value,
                    onValueChange = onValueChange,
                    unit = unit,
                    isError = isError
                )
            }
        }
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Just the editable number + unit, for rows that already carry their own label (capture rows). */
@Composable
fun InlineValueField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    isError: Boolean = false,
    width: Dp = 80.dp
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = modifier.width(width)
        )
        unit?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A large +/- pair pinned to the edges of the row with the value centered between them - the
 * distance itself is the mis-tap guard, on top of each button's own 44dp size. Typing on the
 * keyboard still works; the buttons just nudge the value by `step` per press.
 */
@Composable
fun SteppedValueRow(
    value: String,
    onValueChange: (String) -> Unit,
    step: Float,
    modifier: Modifier = Modifier,
    unit: String? = null,
    isError: Boolean = false,
    decimals: Int = 1
) {
    fun nudge(sign: Float) {
        val current = value.toFloatOrNull() ?: 0f
        onValueChange(formatValue(current + sign * step, decimals))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StepButton(symbol = "−", onClick = { nudge(-1f) })
        InlineValueField(
            value = value,
            onValueChange = onValueChange,
            unit = unit,
            isError = isError
        )
        StepButton(symbol = "+", onClick = { nudge(1f) })
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(width = 1.dp, color = GlassBorder, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Small round A/B/∠-style capture button: outline when empty, solid cream once captured. */
@Composable
fun CaptureChip(
    label: String,
    captured: Boolean,
    enabled: Boolean,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fillColor = if (captured) MaterialTheme.colorScheme.primary else Color.Transparent
    val foreground = if (captured) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val borderColor = if (captured) MaterialTheme.colorScheme.primary else GlassBorder

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fillColor)
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .clickable(enabled = enabled) { onCapture() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) foreground else foreground.copy(alpha = 0.4f)
        )
    }
}

/**
 * Primary call-to-action. Dark mode: the flat solid cream pill. Light mode: the iridescent
 * gradient pill with a soft glowing halo behind it - Yaniv's Opal-style light-mode CTA.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (LocalAppTheme.current.isDark) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text)
        }
    } else {
        GlowGradientButton(text = text, onClick = onClick, enabled = enabled, modifier = modifier)
    }
}

@Composable
private fun GlowGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val shape = MaterialTheme.shapes.large

    Box(contentAlignment = Alignment.Center) {
        if (buttonSize != IntSize.Zero) {
            val haloWidth = with(density) { (buttonSize.width * 2.4f).toDp() }
            val haloHeight = with(density) { (buttonSize.height * 3.2f).toDp() }
            Canvas(modifier = Modifier.size(haloWidth, haloHeight)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                listOf(0.55f to 0.62f, 0.35f to 0.80f, 0.18f to 1.0f).forEach { (alpha, scale) ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = CtaGradientColors.map { it.copy(alpha = alpha) },
                            center = center,
                            radius = size.minDimension * scale / 2f
                        ),
                        radius = size.minDimension * scale / 2f,
                        center = center
                    )
                }
            }
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = CtaOnGradient,
                disabledContainerColor = Color.Transparent
            ),
            modifier = modifier
                .onSizeChanged { buttonSize = it }
                .background(brush = Brush.linearGradient(CtaGradientColors), shape = shape)
        ) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Small sun/moon pill that flips light/dark mode - lives in the persistent top bar. */
@Composable
fun ThemeToggleChip(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .border(width = 1.dp, color = GlassBorder, shape = CircleShape)
            .clickable { theme.toggle() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (theme.isDark) "☀" else "☽",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

