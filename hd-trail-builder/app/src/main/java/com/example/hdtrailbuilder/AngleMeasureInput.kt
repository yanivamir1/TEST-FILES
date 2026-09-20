package com.example.hdtrailbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/** Angle input: lay the phone flat on the surface and tap "measure angle" to read the tilt. */
@Composable
fun AngleMeasureInput(
    label: String,
    angleRepository: AngleRepository,
    valueDeg: Float?,
    onValueChange: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var textValue by remember { mutableStateOf(valueDeg?.let { String.format(Locale.US, "%.1f", it) } ?: "") }

    Column(modifier = modifier) {
        Text(label, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    when (val reading = angleRepository.angleFlow().first()) {
                        is AngleReading.Value -> {
                            val deg = abs(reading.pitchDeg)
                            textValue = String.format(Locale.US, "%.1f", deg)
                            onValueChange(deg)
                            status = "נמדד מהאיצן"
                        }
                        is AngleReading.Unavailable -> status = "אין חיישן תאוצה במכשיר זה"
                    }
                }
            }) { Text("מדוד זווית") }

            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onValueChange(it.toFloatOrNull())
                },
                label = { Text("מעלות") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        status?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
    }
}
