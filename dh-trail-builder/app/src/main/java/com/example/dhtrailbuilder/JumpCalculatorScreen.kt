package com.example.dhtrailbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class JumpScreenResult(val distanceM: Float, val landingSpeedMs: Float)

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
    var lipAltitudeM by remember { mutableStateOf<Float?>(null) }
    var activeStep by remember { mutableStateOf(TrailStep.RollIn) }

    // Recomputed on every recomposition, so as soon as the last field is filled the result
    // appears on its own, and any later edit updates it immediately - no calculate button.
    val drop1 = dropToLip
    val angle = rampAngleDeg
    val drop2 = landingDrop
    val result: Physics.JumpResult? = if (drop1 != null && angle != null && drop2 != null) {
        val startSpeedMs = (startSpeedText.toFloatOrNull() ?: 0f) / 3.6f
        Physics.computeJump(Physics.lipSpeed(startSpeedMs, drop1), angle, drop2)
    } else {
        null
    }
    val landed = result as? Physics.JumpResult.Landed

    LaunchedEffect(landed) {
        landed?.let { onResult(JumpScreenResult(it.distanceM, it.landingSpeedMs)) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        DiagramCard(
            instruction = activeStep.instruction,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            TrailProfile(
                activeStep = activeStep,
                dropToLipM = dropToLip,
                rampAngleDeg = rampAngleDeg,
                landingDropM = landingDrop,
                jumpDistanceM = landed?.distanceM,
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
                title = "Approach",
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
                    pointBLabel = "lip",
                    letterA = "A",
                    letterB = "B",
                    onPointBCaptured = { lipAltitudeM = it }
                )
            }

            SectionCard(
                title = "Takeoff",
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
                active = activeStep == TrailStep.Landing,
                onActivate = { activeStep = TrailStep.Landing }
            ) {
                ElevationDeltaInput(
                    label = TrailStep.Landing.title,
                    hint = if (lipAltitudeM != null) {
                        "The lip is already set from the first measurement - just capture where " +
                            "you touch down. A negative value is a step-up."
                    } else {
                        "A at the lip, B where you touch down. A negative value is a step-up."
                    },
                    liveSensors = liveSensors,
                    valueMeters = landingDrop,
                    onValueChange = { landingDrop = it },
                    onInteract = { activeStep = TrailStep.Landing },
                    presetPointA = lipAltitudeM,
                    presetPointACaption = "lip, set",
                    pointALabel = "lip",
                    pointBLabel = "landing",
                    letterA = "B",
                    letterB = "C",
                    onPointBCaptured = onLandingAltitudeCaptured
                )
            }

            when (result) {
                null -> Unit
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
                        "Air time" to "${formatValue(result.airTimeSec, 2)} s"
                    )
                )
            }
        }
    }
}
