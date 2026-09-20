package com.example.hdtrailbuilder

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

private sealed interface BermOutcome {
    data class Success(val distanceM: Float) : BermOutcome
    data class Problem(val message: String, val isError: Boolean) : BermOutcome
}

@Composable
fun BermDistanceScreen(
    liveSensors: LiveSensorState,
    prefillLandingSpeedMs: Float?,
    modifier: Modifier = Modifier
) {
    var landingSpeedText by remember(prefillLandingSpeedMs) {
        mutableStateOf(prefillLandingSpeedMs?.let { formatValue(it * 3.6f) } ?: "")
    }
    var dropToBerm by remember { mutableStateOf<Float?>(null) }
    var targetSpeedText by remember { mutableStateOf("") }
    var selectedFriction by remember { mutableStateOf(FrictionPreset.PACKED_TRAIL) }
    var outcome by remember { mutableStateOf<BermOutcome?>(null) }

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
                    onClick = {
                        landingSpeedText = formatValue(prefillLandingSpeedMs * 3.6f)
                    },
                    label = { Text("Use ${formatValue(prefillLandingSpeedMs * 3.6f)} km/h from Jump") }
                )
            }
        }

        SectionCard(
            title = "Terrain",
            subtitle = "Elevation change and surface between landing and berm"
        ) {
            ElevationDeltaInput(
                label = "Drop from landing to berm",
                hint = "A at the landing spot, B at the berm. Negative means the berm is higher.",
                liveSensors = liveSensors,
                valueMeters = dropToBerm,
                onValueChange = { dropToBerm = it }
            )

            Text(
                text = "Surface",
                style = MaterialTheme.typography.titleSmall
            )
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
                            text = "${preset.description} · µ ${preset.mu}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SectionCard(
            title = "Target",
            subtitle = "Speed you want to be carrying into the berm"
        ) {
            NumberField(
                label = "Berm entry speed",
                value = targetSpeedText,
                onValueChange = { targetSpeedText = it },
                unit = "km/h"
            )
        }

        Button(
            onClick = {
                val landingSpeedMs = (landingSpeedText.toFloatOrNull() ?: 0f) / 3.6f
                val targetMs = (targetSpeedText.toFloatOrNull() ?: 0f) / 3.6f
                val drop = dropToBerm

                outcome = when {
                    landingSpeedText.toFloatOrNull() == null ->
                        BermOutcome.Problem("Enter the landing speed, or calculate a jump first.", true)
                    drop == null ->
                        BermOutcome.Problem("Measure or type the drop to the berm.", true)
                    else -> {
                        val distance = Physics.bermApproachDistance(
                            landingSpeedMs, drop, targetMs, selectedFriction.mu
                        )
                        if (distance == null) {
                            BermOutcome.Problem(
                                "You are already at or below the target speed - no run-out needed.",
                                false
                            )
                        } else {
                            BermOutcome.Success(distance)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Calculate distance") }

        when (val current = outcome) {
            null -> Unit
            is BermOutcome.Problem -> NoticeCard(current.message, isError = current.isError)
            is BermOutcome.Success -> ResultCard(
                primaryLabel = "DISTANCE TO BERM",
                primaryValue = formatValue(current.distanceM),
                primaryUnit = "m",
                secondary = listOf(
                    "Surface" to selectedFriction.label,
                    "From" to "${landingSpeedText.ifBlank { "0" }} km/h",
                    "To" to "${targetSpeedText.ifBlank { "0" }} km/h"
                )
            )
        }
    }
}
