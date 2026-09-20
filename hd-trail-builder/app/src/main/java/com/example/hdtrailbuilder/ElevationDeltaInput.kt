package com.example.hdtrailbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Height-delta input: tap "mark point A" at the top of the measurement, "mark point B" at the
 * bottom - each captures one barometer pressure reading. The field then fills in automatically
 * with altitude(A) - altitude(B), still editable by hand afterwards.
 */
@Composable
fun ElevationDeltaInput(
    label: String,
    sensorRepository: SensorRepository,
    valueMeters: Float?,
    onValueChange: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var pointAhPa by remember { mutableStateOf<Float?>(null) }
    var pointBhPa by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var textValue by remember { mutableStateOf(valueMeters?.let { String.format(Locale.US, "%.1f", it) } ?: "") }

    fun captureInto(onCaptured: (Float) -> Unit) {
        scope.launch {
            when (val reading = sensorRepository.pressureFlow().first()) {
                is PressureReading.Value -> onCaptured(reading.hPa)
                is PressureReading.Unavailable -> status = "אין חיישן ברומטר במכשיר זה"
            }
        }
    }

    fun recomputeDelta() {
        val a = pointAhPa
        val b = pointBhPa
        if (a != null && b != null) {
            val delta = sensorRepository.altitudeMeters(a) - sensorRepository.altitudeMeters(b)
            textValue = String.format(Locale.US, "%.1f", delta)
            onValueChange(delta)
            status = "חושב מהברומטר"
        }
    }

    Column(modifier = modifier) {
        Text(label, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                captureInto { hPa -> pointAhPa = hPa; recomputeDelta() }
            }) { Text(if (pointAhPa != null) "נק' A ✓" else "סמן נק' A") }

            OutlinedButton(onClick = {
                captureInto { hPa -> pointBhPa = hPa; recomputeDelta() }
            }) { Text(if (pointBhPa != null) "נק' B ✓" else "סמן נק' B") }
        }

        OutlinedTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                onValueChange(it.toFloatOrNull())
            },
            label = { Text("הפרש גובה במטרים") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        status?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
    }
}
