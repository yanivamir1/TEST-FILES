package com.example.dhtrailbuilder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ramp-angle input driven by the live tilt sensor, as one compact row: a capture chip, the
 * live angle, a small inclination line, and the editable value.
 */
@Composable
fun AngleMeasureInput(
    label: String,
    liveSensors: LiveSensorState,
    valueDeg: Float?,
    onValueChange: (Float?) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onInteract: () -> Unit = {}
) {
    var textValue by remember { mutableStateOf(valueDeg?.let { formatValue(it) } ?: "") }
    var captured by remember { mutableStateOf(valueDeg != null) }
    val liveAngle = liveSensors.lengthTiltDeg?.let { abs(it) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CaptureChip(
                label = "∠",
                captured = captured,
                enabled = liveAngle != null,
                onCapture = {
                    onInteract()
                    liveAngle?.let {
                        textValue = formatValue(it)
                        onValueChange(it)
                        captured = true
                    }
                }
            )
            InclinationIndicator(
                angleDeg = liveAngle ?: 0f,
                lineColor = MaterialTheme.colorScheme.primary,
                referenceColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(width = 44.dp, height = 28.dp)
            )
            Text(
                text = liveAngle?.let { "${formatValue(it)}° live" } ?: "no reading",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            InlineValueField(
                value = textValue,
                onValueChange = {
                    onInteract()
                    textValue = it
                    onValueChange(it.toFloatOrNull())
                    captured = it.isNotBlank()
                },
                unit = "°"
            )
        }
    }
}

/** Small horizon-vs-slope drawing: a flat reference line and the current inclination. */
@Composable
private fun InclinationIndicator(
    angleDeg: Float,
    lineColor: Color,
    referenceColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val pivot = Offset(size.width * 0.12f, size.height * 0.82f)
        val length = size.width * 0.78f

        drawLine(
            color = referenceColor,
            start = pivot,
            end = Offset(pivot.x + length, pivot.y),
            strokeWidth = 2f
        )

        val rad = Math.toRadians(angleDeg.toDouble())
        val end = Offset(
            x = pivot.x + (length * cos(rad)).toFloat(),
            y = pivot.y - (length * sin(rad)).toFloat()
        )
        drawLine(color = lineColor, start = pivot, end = end, strokeWidth = 5f)
    }
}
