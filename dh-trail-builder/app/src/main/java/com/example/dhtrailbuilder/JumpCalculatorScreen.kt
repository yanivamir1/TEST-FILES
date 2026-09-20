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
    data class Success(val result: Physics.JumpResult) : JumpOutcome
    data class Problem(val message: String) : JumpOutcome
}

@Composable
fun JumpCalculatorScreen(
    liveSensors: LiveSensorState,
    onResult: (JumpScreenResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var startSpeedText by remember { mutableStateOf("0") }
    var dropToLip by remember { mutableStateOf<Float?>(null) }
    var rampAngleDeg by remember { mutableStateOf<Float?>(null) }
    var landingDrop by remember { mutableStateOf<Float?>(null) }
    var activeStep by remember { mutableStateOf(TrailStep.RollIn) }
    var outcome by remember { mutableStateOf<JumpOutcome?>(null) }

    val jumpDistance = (outcome as? JumpOutcome.Success)?.result?.distanceM

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepDiagramCard(
            activeStep = activeStep,
            dropToLipM = dropToLip,
            rampAngleDeg = rampAngleDeg,
            landingDropM = landingDrop,
            jumpDistanceM = jumpDistance
        )

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
                onInteract = { activeStep = TrailStep.RollIn }
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
            subtitle = "How far below the lip you touch down",
            active = activeStep == TrailStep.Landing,
            onActivate = { activeStep = TrailStep.Landing }
        ) {
            ElevationDeltaInput(
                label = TrailStep.Landing.title,
                hint = TrailStep.Landing.instruction,
                liveSensors = liveSensors,
                valueMeters = landingDrop,
                onValueChange = { landingDrop = it },
                onInteract = { activeStep = TrailStep.Landing }
            )
        }

        Button(
            onClick = {
                val startSpeedMs = (startSpeedText.toFloatOrNull() ?: 0f) / 3.6f
                val drop1 = dropToLip
                val angle = rampAngleDeg
                val drop2 = landingDrop

                outcome = when {
                    drop1 == null || angle == null || drop2 == null ->
                        JumpOutcome.Problem("Fill in or measure every field to calculate.")
                    else -> {
                        val jump = Physics.computeJump(
                            Physics.lipSpeed(startSpeedMs, drop1),
                            angle,
                            drop2
                        )
                        if (jump == null) {
                            JumpOutcome.Problem("The landing cannot sit above the ramp lip.")
                        } else {
                            onResult(JumpScreenResult(jump.distanceM, jump.landingSpeedMs))
                            JumpOutcome.Success(jump)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Calculate jump") }

        when (val current = outcome) {
            null -> Unit
            is JumpOutcome.Problem -> NoticeCard(current.message, isError = true)
            is JumpOutcome.Success -> ResultCard(
                primaryLabel = "JUMP DISTANCE",
                primaryValue = formatValue(current.result.distanceM),
                primaryUnit = "m",
                secondary = listOf(
                    "Lip speed" to "${formatValue(current.result.lipSpeedMs * 3.6f)} km/h",
                    "Landing speed" to "${formatValue(current.result.landingSpeedMs * 3.6f)} km/h",
                    "Air time" to "${formatValue(current.result.airTimeSec, 2)} s"
                )
            )
        }
    }
}

@Composable
private fun StepDiagramCard(
    activeStep: TrailStep,
    dropToLipM: Float?,
    rampAngleDeg: Float?,
    landingDropM: Float?,
    jumpDistanceM: Float?
) {
    DiagramCard(
        eyebrow = "MEASURING NOW",
        step = activeStep.stepLabel,
        title = activeStep.title,
        instruction = activeStep.instruction
    ) {
        TrailProfile(
            activeStep = activeStep,
            dropToLipM = dropToLipM,
            rampAngleDeg = rampAngleDeg,
            landingDropM = landingDropM,
            jumpDistanceM = jumpDistanceM,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        )
    }
}
