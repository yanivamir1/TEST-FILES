package com.example.hdtrailbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun BermDistanceScreen(
    sensorRepository: SensorRepository,
    prefillLandingSpeedMs: Float?,
    modifier: Modifier = Modifier
) {
    var landingSpeedKmhText by remember(prefillLandingSpeedMs) {
        mutableStateOf(prefillLandingSpeedMs?.let { String.format(Locale.US, "%.1f", it * 3.6f) } ?: "")
    }
    var dropToBerm by remember { mutableStateOf<Float?>(null) }
    var targetSpeedKmhText by remember { mutableStateOf("") }
    var selectedFriction by remember { mutableStateOf(FrictionPreset.PACKED_TRAIL) }
    var resultText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("מרחק עד הברם", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = landingSpeedKmhText,
            onValueChange = { landingSpeedKmhText = it },
            label = { Text("מהירות בנחיתה (קמ\"ש - מתמלא אוטומטית מחישוב הקפיצה)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "* אין צורך למדוד שוב - חישוב הקפיצה כבר הזין כאן ערך אם השתמשת בו קודם",
            fontSize = 12.sp
        )

        ElevationDeltaInput(
            label = "הפרש גובה מנקודת הנחיתה עד הברם (שלילי = הברם גבוה יותר)",
            sensorRepository = sensorRepository,
            valueMeters = dropToBerm,
            onValueChange = { dropToBerm = it },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = targetSpeedKmhText,
            onValueChange = { targetSpeedKmhText = it },
            label = { Text("מהירות יעד בכניסה לברם (קמ\"ש)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Column {
            Text("טיב שטח (מהטוב לגרוע)", fontSize = 14.sp)
            FrictionPreset.entries.forEach { preset ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedFriction == preset,
                            onClick = { selectedFriction = preset }
                        )
                ) {
                    RadioButton(selected = selectedFriction == preset, onClick = { selectedFriction = preset })
                    Text(preset.label, fontSize = 14.sp)
                }
            }
        }

        Button(
            onClick = {
                val landingSpeedMs = (landingSpeedKmhText.toFloatOrNull() ?: 0f) / 3.6f
                val drop = dropToBerm
                val targetMs = (targetSpeedKmhText.toFloatOrNull() ?: 0f) / 3.6f

                if (drop == null) {
                    resultText = "צריך למלא/למדוד את הפרש הגובה עד הברם קודם"
                    return@Button
                }

                val distance = Physics.bermApproachDistance(landingSpeedMs, drop, targetMs, selectedFriction.mu)
                resultText = if (distance == null) {
                    "כבר בקצב מספיק - אין צורך במרחק בלימה נוסף"
                } else {
                    String.format(Locale.US, "מרחק דרוש עד הברם: %.1f מ'", distance)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("חשב מרחק לברם") }

        resultText?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
