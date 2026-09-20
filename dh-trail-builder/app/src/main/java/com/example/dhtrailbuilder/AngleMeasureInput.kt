package com.example.dhtrailbuilder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ramp-angle input driven by the live tilt sensor. The current angle is shown continuously
 * with a visual inclination indicator, so the phone can be aimed against the ramp face and
 * watched before the value is captured.
 */
@Composable
fun AngleMeasureInput(
    label: String,
    liveSensors: LiveSensorState,
    valueDeg: Float?,
    onValueChange: (Float?) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    var textValue by remember { mutableStateOf(valueDeg?.let { formatValue(it) } ?: "") }
    val liveAngle = liveSensors.pitchDeg?.let { abs(it) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReadoutTile(
                label = "LIVE ANGLE",
                value = liveAngle?.let { formatValue(it) } ?: "—",
                unit = "°",
                emphasis = true
            )
            InclinationIndicator(
                angleDeg = liveAngle ?: 0f,
                lineColor = MaterialTheme.colorScheme.primary,
                referenceColor = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(width = 96.dp, height = 56.dp)
            )
        }

        Button(
            onClick = {
                liveAngle?.let {
                    textValue = formatValue(it)
                    onValueChange(it)
                }
            },
            enabled = liveAngle != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Capture angle") }

        NumberField(
            label = "Ramp angle",
            value = textValue,
            onValueChange = {
                textValue = it
                onValueChange(it.toFloatOrNull())
            },
            unit = "°",
            supportingText = "Lay the phone on the ramp face, then capture - or type it in"
        )
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
            strokeWidth = 3f
        )

        val rad = Math.toRadians(angleDeg.toDouble())
        val end = Offset(
            x = pivot.x + (length * cos(rad)).toFloat(),
            y = pivot.y - (length * sin(rad)).toFloat()
        )
        drawLine(color = lineColor, start = pivot, end = end, strokeWidth = 7f)
    }
}
