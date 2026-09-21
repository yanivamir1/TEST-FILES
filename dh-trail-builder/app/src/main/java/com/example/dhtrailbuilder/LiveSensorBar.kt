package com.example.dhtrailbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import kotlin.math.abs

/**
 * One thin line of live sensor values, always on screen: altitude above sea level and the
 * phone's tilt along its length. Tapping the altitude opens calibration.
 */
@Composable
fun LiveSensorBar(
    liveSensors: LiveSensorState,
    modifier: Modifier = Modifier
) {
    var showCalibration by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = liveSensors.barometerAvailable) { showCalibration = true }
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = altitudeText(liveSensors),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (liveSensors.isCalibrated) "CALIBRATED" else "APPROX",
            style = MaterialTheme.typography.labelSmall,
            color = if (liveSensors.isCalibrated) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = tiltText(liveSensors),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (showCalibration) {
        AltitudeCalibrationDialog(
            liveSensors = liveSensors,
            onDismiss = { showCalibration = false }
        )
    }
}

private fun altitudeText(liveSensors: LiveSensorState): String = when {
    !liveSensors.barometerAvailable -> "no barometer"
    liveSensors.altitudeM == null -> "— m"
    else -> "${formatValue(liveSensors.altitudeM!!)} m"
}

private fun tiltText(liveSensors: LiveSensorState): String = when {
    !liveSensors.accelerometerAvailable -> "no tilt sensor"
    liveSensors.lengthTiltDeg == null -> "—°"
    else -> "${formatValue(abs(liveSensors.lengthTiltDeg!!))}°"
}

@Composable
private fun AltitudeCalibrationDialog(
    liveSensors: LiveSensorState,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(liveSensors.altitudeM?.let { formatValue(it, 0) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calibrate altitude") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Barometric altitude assumes standard sea-level pressure, which drifts with " +
                        "the weather. Enter your true altitude here and every reading is corrected " +
                        "from it. Height differences between two points are accurate either way.",
                    style = MaterialTheme.typography.bodySmall
                )
                NumberField(
                    label = "Known altitude",
                    value = input,
                    onValueChange = { input = it },
                    unit = "m"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    input.toFloatOrNull()?.let { liveSensors.calibrateTo(it) }
                    onDismiss()
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    liveSensors.resetCalibration()
                    onDismiss()
                }
            ) { Text("Reset to standard") }
        }
    )
}
