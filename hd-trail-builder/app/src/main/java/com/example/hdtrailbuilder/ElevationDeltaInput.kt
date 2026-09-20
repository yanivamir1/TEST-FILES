package com.example.hdtrailbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Height-difference input driven by the live barometer readout.
 *
 * The current altitude is shown continuously, so the rider can watch it settle before
 * capturing. Point A is the upper spot, point B the lower one; each capture stores that
 * altitude and the difference (A - B) fills the field, which stays hand-editable.
 */
@Composable
fun ElevationDeltaInput(
    label: String,
    liveSensors: LiveSensorState,
    valueMeters: Float?,
    onValueChange: (Float?) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    var pointA by remember { mutableStateOf<Float?>(null) }
    var pointB by remember { mutableStateOf<Float?>(null) }
    var textValue by remember { mutableStateOf(valueMeters?.let { formatValue(it) } ?: "") }

    fun applyDelta() {
        val a = pointA
        val b = pointB
        if (a != null && b != null) {
            val delta = a - b
            textValue = formatValue(delta)
            onValueChange(delta)
        }
    }

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

        ReadoutTile(
            label = "LIVE ALTITUDE",
            value = liveSensors.altitudeM?.let { formatValue(it) } ?: "—",
            unit = "m"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CapturePointButton(
                name = "A",
                subtitle = "top",
                captured = pointA,
                enabled = liveSensors.altitudeM != null,
                onCapture = { pointA = liveSensors.altitudeM; applyDelta() },
                modifier = Modifier.weight(1f)
            )
            CapturePointButton(
                name = "B",
                subtitle = "bottom",
                captured = pointB,
                enabled = liveSensors.altitudeM != null,
                onCapture = { pointB = liveSensors.altitudeM; applyDelta() },
                modifier = Modifier.weight(1f)
            )
        }

        if (pointA != null || pointB != null) {
            TextButton(
                onClick = {
                    pointA = null
                    pointB = null
                }
            ) { Text("Clear captured points") }
        }

        Divider()

        NumberField(
            label = "Height difference",
            value = textValue,
            onValueChange = {
                textValue = it
                onValueChange(it.toFloatOrNull())
            },
            unit = "m",
            supportingText = "Captured from A − B, or type it in directly"
        )
    }
}

@Composable
private fun CapturePointButton(
    name: String,
    subtitle: String,
    captured: Float?,
    enabled: Boolean,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (captured != null) "${formatValue(captured)} m" else "Set point $name",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = if (captured != null) "point $name · $subtitle" else subtitle,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    if (captured != null) {
        Button(onClick = onCapture, enabled = enabled, modifier = modifier) { content() }
    } else {
        OutlinedButton(onClick = onCapture, enabled = enabled, modifier = modifier) { content() }
    }
}
