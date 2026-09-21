package com.example.dhtrailbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class JumpScreenResult(val distanceM: Float, val landingSpeedMs: Float)

private sealed interface JumpOutcome {
    data class Computed(val result: Physics.JumpResult) : JumpOutcome
    data class Problem(val message: String) : JumpOutcome
}

@Composable
fun JumpCalculatorScreen(
    liveSensors: LiveSensorState,
    onResult: (JumpScreenResult) -> Unit,
    onLandingAltitudeCaptured: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var startSpeedText by remember { mutableStateOf("0") }
    var dropToLip by remember { mutableStateOf<Float?>(null) }
    var rampAngleDeg by remember { mutableStateOf<Float?>(null) }
    var landingDrop by remember { mutableStateOf<Float?>(null) }
    var activeStep by remember { mutableStateOf(TrailStep.RollIn) }
    var outcome by remember { mutableStateOf<JumpOutcome?>(null) }

    val landed = (outcome as? JumpOutcome.Computed)?.result as? Physics.JumpResult.Landed

    Column(modifier = modifier.fillMaxSize()) {
        DiagramCard(
            eyebrow = "MEASURING NOW",
            step = activeStep.stepLabel,
            title = activeStep.title,
            instruction = activeStep.instruction,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            TrailProfile(
                activeStep = activeStep,
                dropToLipM = dropToLip,
                rampAngleDeg = rampAngleDeg,
                landingDropM = landingDrop,
                jumpDistanceM = landed?.distanceM,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(
                title = "Approach",
                subtitle = "Speed built up on the way into the ramp",
                active = activeStep == TrailStep.RollIn,
                onActivate = { activeStep = TrailStep.RollIn }
            ) {
                NumberField(
                    label = "Starting speed",
                    value = startSpeedText,
                    onValueChange = { startSpeedText = it },
                    unit = "km/h",
                    supportingText = "Leave at 0 if you roll in from a standstill"
                )
                ElevationDeltaInput(
                    label = TrailStep.RollIn.title,
                    hint = TrailStep.RollIn.instruction,
                    liveSensors = liveSensors,
                    valueMeters = dropToLip,
                    onValueChange = { dropToLip = it },
                    onInteract = { activeStep = TrailStep.RollIn },
                    pointALabel = "start",
                    pointBLabel = "lip"
                )
            }

            SectionCard(
                title = "Takeoff",
                subtitle = "Launch angle of the ramp face",
                active = activeStep == TrailStep.Ramp,
                onActivate = { activeStep = TrailStep.Ramp }
            ) {
                AngleMeasureInput(
                    label = TrailStep.Ramp.title,
                    hint = TrailStep.Ramp.instruction,
                    liveSensors = liveSensors,
                    valueDeg = rampAngleDeg,
                    onValueChange = { rampAngleDeg = it },
                    onInteract = { activeStep = TrailStep.Ramp }
                )
            }

            SectionCard(
                title = "Landing",
                subtitle = "Where you touch down, below the lip or above it on a step-up",
                active = activeStep == TrailStep.Landing,
                onActivate = { activeStep = TrailStep.Landing }
            ) {
                ElevationDeltaInput(
                    label = TrailStep.Landing.title,
                    hint = "A back at the lip, B where you touch down. A negative value is a " +
                        "step-up: the landing sits above the lip.",
                    liveSensors = liveSensors,
                    valueMeters = landingDrop,
                    onValueChange = { landingDrop = it },
                    onInteract = { activeStep = TrailStep.Landing },
                    pointALabel = "lip",
                    pointBLabel = "landing",
                    onPointBCaptured = onLandingAltitudeCaptured
                )
            }

            Button(
                onClick = {
                    val startSpeedMs = (startSpeedText.toFloatOrNull() ?: 0f) / 3.6f
                    val drop1 = dropToLip
                    val angle = rampAngleDeg
                    val drop2 = landingDrop

                    outcome = if (drop1 == null || angle == null || drop2 == null) {
                        JumpOutcome.Problem("Fill in or measure every field to calculate.")
                    } else {
                        val result = Physics.computeJump(
                            Physics.lipSpeed(startSpeedMs, drop1),
                            angle,
                            drop2
                        )
                        if (result is Physics.JumpResult.Landed) {
                            onResult(JumpScreenResult(result.distanceM, result.landingSpeedMs))
                        }
                        JumpOutcome.Computed(result)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Calculate jump") }

            when (val current = outcome) {
                null -> Unit
                is JumpOutcome.Problem -> NoticeCard(current.message, isError = true)
                is JumpOutcome.Computed -> when (val result = current.result) {
                    is Physics.JumpResult.ShortOfLanding -> NoticeCard(
                        "You do not clear this step-up. The arc peaks " +
                            "${formatValue(result.peakAboveLipM)} m above the lip, but the landing " +
                            "is ${formatValue(result.neededAboveLipM)} m above it. More speed into " +
                            "the lip or a steeper ramp.",
                        isError = true
                    )

                    is Physics.JumpResult.Landed -> ResultCard(
                        primaryLabel = "JUMP DISTANCE",
                        primaryValue = formatValue(result.distanceM),
                        primaryUnit = "m",
                        secondary = listOf(
                            "Lip speed" to "${formatValue(result.lipSpeedMs * 3.6f)} km/h",
                            "Landing speed" to "${formatValue(result.landingSpeedMs * 3.6f)} km/h",
                            "Air time" to "${formatValue(result.airTimeSec, 2)} s",
                            "Peak above lip" to "${formatValue(result.peakAboveLipM)} m"
                        )
                    )
                }
            }
        }
    }
}
