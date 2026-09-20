package com.example.hdtrailbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

data class JumpScreenResult(val distanceM: Float, val landingSpeedMs: Float)

@Composable
fun JumpCalculatorScreen(
    sensorRepository: SensorRepository,
    angleRepository: AngleRepository,
    onResult: (JumpScreenResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var startSpeedKmhText by remember { mutableStateOf("0") }
    var dropToLip by remember { mutableStateOf<Float?>(null) }
    var rampAngleDeg by remember { mutableStateOf<Float?>(null) }
    var landingDrop by remember { mutableStateOf<Float?>(null) }
    var resultText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("מחשבון מרחק קפיצה", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = startSpeedKmhText,
            onValueChange = { startSpeedKmhText = it },
            label = { Text("מהירות התחלה (קמ\"ש, ברירת מחדל 0)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        ElevationDeltaInput(
            label = "ירידת גובה מנק' ההתחלה עד שפת הרמפה",
            sensorRepository = sensorRepository,
            valueMeters = dropToLip,
            onValueChange = { dropToLip = it },
            modifier = Modifier.fillMaxWidth()
        )

        AngleMeasureInput(
            label = "זווית הרמפה",
            angleRepository = angleRepository,
            valueDeg = rampAngleDeg,
            onValueChange = { rampAngleDeg = it },
            modifier = Modifier.fillMaxWidth()
        )

        ElevationDeltaInput(
            label = "ירידת גובה משפת הרמפה עד נקודת הנחיתה",
            sensorRepository = sensorRepository,
            valueMeters = landingDrop,
            onValueChange = { landingDrop = it },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val startSpeedMs = (startSpeedKmhText.toFloatOrNull() ?: 0f) / 3.6f
                val drop1 = dropToLip
                val angle = rampAngleDeg
                val drop2 = landingDrop

                if (drop1 == null || angle == null || drop2 == null) {
                    resultText = "צריך למלא/למדוד את כל השדות קודם"
                    return@Button
                }

                val vLip = Physics.lipSpeed(startSpeedMs, drop1)
                val jump = Physics.computeJump(vLip, angle, drop2)

                if (jump == null) {
                    resultText = "נקודת הנחיתה לא יכולה להיות מעל שפת הרמפה"
                    return@Button
                }

                resultText = String.format(
                    Locale.US,
                    "מרחק קפיצה: %.1f מ'\nמהירות נחיתה: %.1f קמ\"ש\nזמן אוויר: %.2f שנ'",
                    jump.distanceM, jump.landingSpeedMs * 3.6f, jump.airTimeSec
                )
                onResult(JumpScreenResult(jump.distanceM, jump.landingSpeedMs))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("חשב קפיצה") }

        resultText?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
