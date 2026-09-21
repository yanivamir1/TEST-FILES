package com.example.dhtrailbuilder

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class RunSample(
    val timestampMs: Long,
    val cumulativeDistanceM: Float,
    val speedKmh: Float,
    val altitudeM: Float
)

@Composable
fun TrailRunScreen(
    sensorRepository: SensorRepository,
    locationRepository: LocationRepository,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    predictedJump: JumpScreenResult? = null
) {
    val scope = rememberCoroutineScope()
    val samples = remember { mutableStateListOf<RunSample>() }
    var isRecording by remember { mutableStateOf(false) }
    var locationJob by remember { mutableStateOf<Job?>(null) }
    var accelJob by remember { mutableStateOf<Job?>(null) }
    var detectedJump by remember { mutableStateOf<JumpEvent?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            locationJob?.cancel()
            accelJob?.cancel()
        }
    }

    fun startRecording() {
        samples.clear()
        detectedJump = null
        isRecording = true

        var lastLat: Double? = null
        var lastLon: Double? = null
        var cumulativeDistance = 0f

        locationJob = scope.launch {
            locationRepository.locationFlow().collect { sample ->
                val speed = sample.speedKmh
                val altitude = sample.altitudeMeters
                val lat = sample.latitude
                val lon = sample.longitude
                if (speed != null && altitude != null && lat != null && lon != null) {
                    val prevLat = lastLat
                    val prevLon = lastLon
                    if (prevLat != null && prevLon != null) {
                        val results = FloatArray(1)
                        Location.distanceBetween(prevLat, prevLon, lat, lon, results)
                        cumulativeDistance += results[0]
                    }
                    lastLat = lat
                    lastLon = lon
                    samples.add(
                        RunSample(
                            timestampMs = System.currentTimeMillis(),
                            cumulativeDistanceM = cumulativeDistance,
                            speedKmh = speed,
                            altitudeM = altitude
                        )
                    )
                }
            }
        }

        val detector = JumpDetector()
        accelJob = scope.launch {
            sensorRepository.accelerationMagnitudeFlow().collect { magnitude ->
                val event = detector.onSample(magnitude, System.currentTimeMillis())
                if (event != null) detectedJump = event
            }
        }
    }

    fun stopRecording() {
        locationJob?.cancel()
        accelJob?.cancel()
        isRecording = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            !hasLocationPermission -> SectionCard(
                title = "Ride log",
                subtitle = "Checks the calculated jump against a real run"
            ) {
                Text(
                    "Location permission is required to record a run.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Grant permission")
                }
            }

            !locationRepository.isGpsProviderAvailable -> NoticeCard(
                "No GPS provider available on this device.",
                isError = true
            )

            else -> {
                SectionCard(
                    title = "Ride log",
                    subtitle = "Checks the calculated jump against a real run"
                ) {
                    Button(
                        onClick = { if (isRecording) stopRecording() else startRecording() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isRecording) "Stop recording" else "Start recording") }

                    Text(
                        text = when {
                            isRecording -> "Recording · ${samples.size} points"
                            samples.isEmpty() ->
                                "Waiting to start. Keep the phone on the bike for the whole run-in, jump and landing."
                            else -> "Stopped · ${samples.size} points"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (samples.isNotEmpty()) {
                    RunStats(samples)

                    val (takeoffMarker, landingMarker) = detectedJump?.let { event ->
                        val takeoffSample = samples.minByOrNull { kotlin.math.abs(it.timestampMs - event.takeoffAtMs) }
                        val landingSample = samples.minByOrNull { kotlin.math.abs(it.timestampMs - event.landingAtMs) }
                        if (takeoffSample != null && landingSample != null) {
                            JumpMarker(takeoffSample.cumulativeDistanceM, takeoffSample.altitudeM) to
                                JumpMarker(landingSample.cumulativeDistanceM, landingSample.altitudeM)
                        } else null to null
                    } ?: (null to null)

                    SectionCard(
                        title = "Trail profile",
                        subtitle = "Altitude over ground distance covered"
                    ) {
                        RideProfileChart(
                            samples = samples,
                            takeoff = takeoffMarker,
                            landing = landingMarker,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                        AxisLabels(
                            minValue = samples.minOf { it.altitudeM },
                            maxValue = samples.maxOf { it.altitudeM },
                            unit = "m"
                        )
                    }

                    if (!isRecording) {
                        JumpComparisonCard(
                            samples = samples,
                            detectedJump = detectedJump,
                            predictedJump = predictedJump
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpComparisonCard(
    samples: List<RunSample>,
    detectedJump: JumpEvent?,
    predictedJump: JumpScreenResult?
) {
    if (detectedJump == null) {
        NoticeCard(
            "No jump detected this run. Ride the whole run-in, jump and landing with the " +
                "phone mounted on the bike for it to catch the airborne moment."
        )
        return
    }

    val takeoffSample = samples.minByOrNull { kotlin.math.abs(it.timestampMs - detectedJump.takeoffAtMs) }
    val landingSample = samples.minByOrNull { kotlin.math.abs(it.timestampMs - detectedJump.landingAtMs) }
    if (takeoffSample == null || landingSample == null) return

    val actualDistance = landingSample.cumulativeDistanceM - takeoffSample.cumulativeDistanceM
    val airTimeSec = (detectedJump.landingAtMs - detectedJump.takeoffAtMs) / 1000f
    val landingSpeed = landingSample.speedKmh

    val secondary = mutableListOf(
        "Air time" to "${formatValue(airTimeSec, 2)} s",
        "Landing speed" to "${formatValue(landingSpeed)} km/h"
    )

    predictedJump?.let { predicted ->
        val delta = actualDistance - predicted.distanceM
        val sign = if (delta >= 0) "+" else ""
        secondary.add(0, "Predicted" to "${formatValue(predicted.distanceM)} m")
        secondary.add(1, "Difference" to "$sign${formatValue(delta)} m")
    }

    ResultCard(
        primaryLabel = "DETECTED JUMP",
        primaryValue = formatValue(actualDistance),
        primaryUnit = "m",
        secondary = secondary
    )
}

@Composable
private fun RunStats(samples: List<RunSample>) {
    val maxSpeed = samples.maxOf { it.speedKmh }
    val gain = samples.maxOf { it.altitudeM } - samples.minOf { it.altitudeM }
    val distance = samples.last().cumulativeDistanceM

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReadoutTile(label = "TOP SPEED", value = formatValue(maxSpeed), unit = "km/h")
        ReadoutTile(label = "ELEVATION RANGE", value = formatValue(gain), unit = "m")
        ReadoutTile(label = "DISTANCE", value = formatValue(distance, 0), unit = "m")
    }
}

@Composable
private fun AxisLabels(minValue: Float, maxValue: Float, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "min ${formatValue(minValue)} $unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "max ${formatValue(maxValue)} $unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
