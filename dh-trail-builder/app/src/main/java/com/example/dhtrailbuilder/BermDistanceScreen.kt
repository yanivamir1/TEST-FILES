package com.example.dhtrailbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BermDistanceScreen(
    liveSensors: LiveSensorState,
    prefillLandingSpeedMs: Float?,
    landingAltitudeM: Float?,
    modifier: Modifier = Modifier
) {
    var landingSpeedText by remember(prefillLandingSpeedMs) {
        mutableStateOf(prefillLandingSpeedMs?.let { formatValue(it * 3.6f) } ?: "")
    }
    var dropToBerm by remember { mutableStateOf<Float?>(null) }
    var targetSpeedText by remember { mutableStateOf("") }
    var selectedFriction by remember { mutableStateOf(FrictionPreset.PACKED_TRAIL) }
    var braking by remember { mutableStateOf(true) }
    var outcome by remember { mutableStateOf<Physics.BermResult?>(null) }
    var inputProblem by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        DiagramCard(
            instruction = "Capture at the landing, then at the berm - how much further you drop",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            BermProfile(
                dropToBermM = dropToBerm,
                runOutM = (outcome as? Physics.BermResult.Distance)?.alongGroundM,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionCard(
                title = "Entry",
                subtitle = "How fast you are travelling when you touch down"
            ) {
                NumberField(
                    label = "Landing speed",
                    value = landingSpeedText,
                    onValueChange = { landingSpeedText = it },
                    unit = "km/h",
                    supportingText = if (prefillLandingSpeedMs != null) {
                        "Filled in from your calculated jump - change it if you want"
                    } else {
                        null
                    }
                )
                NumberField(
                    label = "Target berm entry speed",
                    value = targetSpeedText,
                    onValueChange = { targetSpeedText = it },
                    unit = "km/h",
                    supportingText = "The speed you want to be carrying into the turn"
                )
            }

            SectionCard(
                title = "Run-out",
                subtitle = "How much further you drop between the landing and the berm"
            ) {
                ElevationDeltaInput(
                    label = "Drop from landing to berm",
                    hint = if (landingAltitudeM != null) {
                        "The landing is already set from the Jump tab - just walk to the berm " +
                            "and capture B. Negative means the berm sits higher."
                    } else {
                        "A at the landing, B at the berm. Negative means the berm sits higher."
                    },
                    liveSensors = liveSensors,
                    valueMeters = dropToBerm,
                    onValueChange = { dropToBerm = it },
                    presetPointA = landingAltitudeM,
                    presetPointACaption = "from Jump",
                    pointALabel = "landing",
                    pointBLabel = "berm"
                )
            }

            SectionCard(
                title = "Surface",
                subtitle = "How much grip you have to scrub speed with"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = braking,
                        onClick = { braking = true },
                        label = { Text("On the brakes") }
                    )
                    FilterChip(
                        selected = !braking,
                        onClick = { braking = false },
                        label = { Text("Coasting") }
                    )
                }

                FrictionPreset.entries.forEach { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedFriction == preset,
                                onClick = { selectedFriction = preset }
                            )
                    ) {
                        RadioButton(
                            selected = selectedFriction == preset,
                            onClick = { selectedFriction = preset }
                        )
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(preset.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${preset.description} · µ ${preset.mu(braking)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val landingSpeed = landingSpeedText.toFloatOrNull()
                    val targetSpeed = targetSpeedText.toFloatOrNull()
                    val drop = dropToBerm

                    when {
                        landingSpeed == null -> {
                            inputProblem = "Enter the landing speed, or calculate a jump first."
                            outcome = null
                        }
                        targetSpeed == null -> {
                            inputProblem = "Enter the speed you want to enter the berm at."
                            outcome = null
                        }
                        drop == null -> {
                            inputProblem = "Measure or type the drop from the landing to the berm."
                            outcome = null
                        }
                        else -> {
                            inputProblem = null
                            outcome = Physics.bermRunOut(
                                landingSpeedMs = landingSpeed / 3.6f,
                                targetEntrySpeedMs = targetSpeed / 3.6f,
                                dropToBermM = drop,
                                mu = selectedFriction.mu(braking)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Calculate run-out") }

            inputProblem?.let { NoticeCard(it, isError = true) }

            when (val current = outcome) {
                null -> Unit
                is Physics.BermResult.NoRunOutNeeded -> NoticeCard(
                    "You arrive at or below your target speed already - no run-out needed to slow down."
                )
                is Physics.BermResult.Distance -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResultCard(
                        primaryLabel = "RUN-OUT NEEDED",
                        primaryValue = formatValue(current.alongGroundM),
                        primaryUnit = "m",
                        secondary = listOf(
                            "Flat distance" to "${formatValue(current.horizontalM)} m",
                            "Drop used" to "${formatValue(current.dropM)} m",
                            "Surface" to selectedFriction.label
                        )
                    )
                    Text(
                        text = "Measured along the ground. If the real gap to the berm is shorter " +
                            "than this, you arrive hot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
