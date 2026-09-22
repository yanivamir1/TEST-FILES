package com.example.dhtrailbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * One thin line of live values, always on screen on every tab: altitude above sea level, the
 * phone's tilt along its length, and a persistent GPS status dot. Tapping the altitude opens
 * calibration.
 */
@Composable
fun LiveSensorBar(
    liveSensors: LiveSensorState,
    locationRepository: LocationRepository,
    hasLocationPermission: Boolean,
    modifier: Modifier = Modifier
) {
    var showCalibration by remember { mutableStateOf(false) }
    var gpsEnabled by remember { mutableStateOf(locationRepository.isGpsEnabled) }

    // Location can be toggled from outside the app (quick settings) at any time, so this is
    // polled rather than read once - cheap, since isGpsEnabled is just a settings lookup.
    LaunchedEffect(Unit) {
        while (true) {
            gpsEnabled = locationRepository.isGpsEnabled
            delay(3000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )
            .clickable(enabled = liveSensors.barometerAvailable) { showCalibration = true }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = altitudeText(liveSensors),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (liveSensors.isCalibrated) "CAL" else "~",
            style = MaterialTheme.typography.labelSmall,
            color = if (liveSensors.isCalibrated) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = tiltText(liveSensors),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GpsDot(
                enabled = hasLocationPermission &&
                    locationRepository.isGpsProviderAvailable &&
                    gpsEnabled
            )
            Text(
                text = "GPS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun GpsDot(enabled: Boolean) {
    val color = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color)
    )
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
