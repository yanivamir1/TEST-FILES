package com.example.dhtrailbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
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
    modifier: Modifier = Modifier
) {
    var landingSpeedText by remember(prefillLandingSpeedMs) {
        mutableStateOf(prefillLandingSpeedMs?.let { formatValue(it * 3.6f) } ?: "")
    }
    var gradientDeg by remember { mutableStateOf<Float?>(0f) }
    var targetSpeedText by remember { mutableStateOf("") }
    var selectedFriction by remember { mutableStateOf(FrictionPreset.PACKED_TRAIL) }
    var braking by remember { mutableStateOf(true) }
    var outcome by remember { mutableStateOf<Physics.BermResult?>(null) }
    var inputProblem by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(
            title = "Entry",
            subtitle = "How fast you are travelling when you touch down"
        ) {
            NumberField(
                label = "Landing speed",
                value = landingSpeedText,
                onValueChange = { landingSpeedText = it },
                unit = "km/h"
            )
            if (prefillLandingSpeedMs != null) {
                AssistChip(
                    onClick = { landingSpeedText = formatValue(prefillLandingSpeedMs * 3.6f) },
                    label = { Text("Use ${formatValue(prefillLandingSpeedMs * 3.6f)} km/h from Jump") }
                )
            }
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
            subtitle = "The ground between the landing and the berm"
        ) {
            AngleMeasureInput(
                label = "Run-out gradient",
                hint = "Lay the phone on the ground between landing and berm. " +
                    "0° is flat; steeper means you keep gaining speed.",
                liveSensors = liveSensors,
                valueDeg = gradientDeg,
                onValueChange = { gradientDeg = it }
            )

            Text(text = "Riding style", style = MaterialTheme.typography.titleSmall)
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

            Text(text = "Surface", style = MaterialTheme.typography.titleSmall)
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
                val gradient = gradientDeg

                when {
                    landingSpeed == null -> {
                        inputProblem = "Enter the landing speed, or calculate a jump first."
                        outcome = null
                    }
                    targetSpeed == null -> {
                        inputProblem = "Enter the speed you want to enter the berm at."
                        outcome = null
                    }
                    gradient == null -> {
                        inputProblem = "Measure or type the run-out gradient (0 if it is flat)."
                        outcome = null
                    }
                    else -> {
                        inputProblem = null
                        outcome = Physics.bermApproachDistance(
                            landingSpeedMs = landingSpeed / 3.6f,
                            targetEntrySpeedMs = targetSpeed / 3.6f,
                            gradientDeg = gradient,
                            mu = selectedFriction.mu(braking)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Calculate distance") }

        inputProblem?.let { NoticeCard(it, isError = true) }

        when (val current = outcome) {
            null -> Unit
            is Physics.BermResult.NoBrakingNeeded -> NoticeCard(
                "You land slower than your target speed - no run-out needed to slow down."
            )
            is Physics.BermResult.CannotSlow -> NoticeCard(
                "Too steep to scrub speed here. On ${selectedFriction.label.lowercase()} you " +
                    "stop gaining speed only below about ${formatValue(current.gradientLimitDeg)}° - " +
                    "on this gradient you keep accelerating no matter how much run-out there is.",
                isError = true
            )
            is Physics.BermResult.Distance -> ResultCard(
                primaryLabel = "RUN-OUT NEEDED",
                primaryValue = formatValue(current.distanceM),
                primaryUnit = "m",
                secondary = listOf(
                    "Deceleration" to "${formatValue(current.decelerationG, 2)} g",
                    "Height lost" to "${formatValue(current.elevationDropM)} m",
                    "Surface" to selectedFriction.label
                )
            )
        }
    }
}
