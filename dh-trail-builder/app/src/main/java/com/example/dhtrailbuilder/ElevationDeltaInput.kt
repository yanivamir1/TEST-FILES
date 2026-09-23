package com.example.dhtrailbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Height-difference input driven by the live barometer readout, as one compact row: two
 * capture chips (A, B), the live altitude they read from, and the resulting difference -
 * still hand-editable - all on a single line.
 */
@Composable
fun ElevationDeltaInput(
    label: String,
    liveSensors: LiveSensorState,
    valueMeters: Float?,
    onValueChange: (Float?) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onInteract: () -> Unit = {},
    presetPointA: Float? = null,
    presetPointACaption: String? = null,
    pointALabel: String = "top",
    pointBLabel: String = "bottom",
    letterA: String = "A",
    letterB: String = "B",
    onPointBCaptured: (Float) -> Unit = {}
) {
    var pointA by remember(presetPointA) { mutableStateOf(presetPointA) }
    var pointB by remember { mutableStateOf<Float?>(null) }
    var usingPreset by remember(presetPointA) { mutableStateOf(presetPointA != null) }
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
                label = letterA,
                captured = pointA != null,
                enabled = liveSensors.altitudeM != null,
                onCapture = {
                    onInteract()
                    pointA = liveSensors.altitudeM
                    usingPreset = false
                    applyDelta()
                }
            )
            CaptureChip(
                label = letterB,
                captured = pointB != null,
                enabled = liveSensors.altitudeM != null,
                onCapture = {
                    onInteract()
                    val captured = liveSensors.altitudeM
                    pointB = captured
                    captured?.let(onPointBCaptured)
                    applyDelta()
                }
            )
            Text(
                text = liveSensors.altitudeM?.let { "${formatValue(it)} m live" } ?: "no reading",
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
                },
                unit = "m"
            )
        }
    }
}
