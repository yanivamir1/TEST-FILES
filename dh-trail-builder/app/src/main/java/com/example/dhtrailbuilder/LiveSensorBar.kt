package com.example.dhtrailbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Always-visible strip of live sensor values, so the rider can watch altitude and
 * phone angle settle before capturing anything.
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = liveSensors.barometerAvailable) { showCalibration = true }
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ALTITUDE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CalibrationBadge(liveSensors)
            }
            Text(
                text = liveAltitudeText(liveSensors),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "TILT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = liveTiltText(liveSensors),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    if (showCalibration) {
        AltitudeCalibrationDialog(
            liveSensors = liveSensors,
            onDismiss = { showCalibration = false }
        )
    }
}

private fun liveAltitudeText(liveSensors: LiveSensorState): String = when {
    !liveSensors.barometerAvailable -> "no barometer"
    liveSensors.altitudeM == null -> "—"
    else -> "${formatValue(liveSensors.altitudeM!!)} m"
}

private fun liveTiltText(liveSensors: LiveSensorState): String = when {
    !liveSensors.accelerometerAvailable -> "no sensor"
    liveSensors.lengthTiltDeg == null -> "—"
    else -> "${formatValue(kotlin.math.abs(liveSensors.lengthTiltDeg!!))}°"
}

@Composable
private fun CalibrationBadge(liveSensors: LiveSensorState) {
    if (!liveSensors.barometerAvailable) return
    val calibrated = liveSensors.isCalibrated
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (calibrated) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.outline
            )
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = if (calibrated) "CALIBRATED" else "APPROX",
            style = MaterialTheme.typography.labelSmall,
            color = if (calibrated) MaterialTheme.colorScheme.onSecondary
            else MaterialTheme.colorScheme.surface
        )
    }
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
