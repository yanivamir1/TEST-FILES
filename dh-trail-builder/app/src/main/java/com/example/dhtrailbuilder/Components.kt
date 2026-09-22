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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

fun formatValue(value: Float, decimals: Int = 1): String =
    String.format(Locale.US, "%.${decimals}f", value)

/**
 * Black canvas with a couple of large, heavily-softened glow orbs bleeding into the dark -
 * just a hint of muted color, never a colorful wash. Every screen sits inside this.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGlowOrb(
                color = Color(170, 112, 70),
                center = Offset(size.width * 0.12f, size.height * 0.06f),
                radius = size.maxDimension * 0.5f
            )
            drawGlowOrb(
                color = Color(92, 112, 142),
                center = Offset(size.width * 0.95f, size.height * 0.8f),
                radius = size.maxDimension * 0.55f
            )
        }
        content()
    }
}

private fun DrawScope.drawGlowOrb(
    color: Color,
    center: Offset,
    radius: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private val GlassBorder = Color(0x12FFFFFF)

/** Glass card: near-black fill, hairline border. Selected state gets a bright cream outline. */
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                color = valueColor
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

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    supportingText: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (unit != null) "$label ($unit)" else label) },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = GlassBorder,
            unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
            focusedContainerColor = Color.White.copy(alpha = 0.02f)
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth()
    )
}
