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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** speedKmh and cumulativeDistanceM are absent when recording without GPS. */
data class RunSample(
    val timestampMs: Long,
    val elapsedSec: Float,
    val altitudeM: Float,
    val speedKmh: Float? = null,
    val cumulativeDistanceM: Float? = null
)

private enum class RecordMode(val label: String) {
    Gps("Ride (GPS)"),
    NoGps("Test (no GPS)")
}

@Composable
fun TrailRunScreen(
    sensorRepository: SensorRepository,
    locationRepository: LocationRepository,
    liveSensors: LiveSensorState,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    predictedJump: JumpScreenResult? = null
) {
    val scope = rememberCoroutineScope()
    val samples = remember { mutableStateListOf<RunSample>() }
    var mode by remember { mutableStateOf(RecordMode.Gps) }
    var isRecording by remember { mutableStateOf(false) }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var detectedJump by remember { mutableStateOf<JumpEvent?>(null) }
    var lastFix by remember { mutableStateOf<LocationSample?>(null) }
    var elapsedSec by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { jobs.forEach { it.cancel() } }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        elapsedSec = 0
        while (isActive) {
            delay(1000)
            elapsedSec += 1
        }
    }

    fun startRecording() {
        samples.clear()
        detectedJump = null
        lastFix = null
        isRecording = true
        val startedAt = System.currentTimeMillis()

        val detector = JumpDetector()
        val accelJob = scope.launch {
            sensorRepository.accelerationMagnitudeFlow().collect { magnitude ->
                val event = detector.onSample(magnitude, System.currentTimeMillis())
                if (event != null) detectedJump = event
            }
        }

        val recordJob = if (mode == RecordMode.Gps) {
            scope.launch {
                var lastLat: Double? = null
                var lastLon: Double? = null
                var cumulativeDistance = 0f

                locationRepository.locationFlow().collect { fix ->
                    lastFix = fix
                    val lat = fix.latitude
                    val lon = fix.longitude
                    val altitude = fix.altitudeMeters
                    if (lat == null || lon == null || altitude == null) return@collect

                    val prevLat = lastLat
                    val prevLon = lastLon
                    if (prevLat != null && prevLon != null) {
                        val results = FloatArray(1)
                        Location.distanceBetween(prevLat, prevLon, lat, lon, results)
                        cumulativeDistance += results[0]
                    }
                    lastLat = lat
                    lastLon = lon

                    val now = System.currentTimeMillis()
                    samples.add(
                        RunSample(
                            timestampMs = now,
                            elapsedSec = (now - startedAt) / 1000f,
                            altitudeM = altitude,
                            // A fix without a speed means standing still, not a useless fix.
                            speedKmh = fix.speedKmh ?: 0f,
                            cumulativeDistanceM = cumulativeDistance
                        )
                    )
                }
            }
        } else {
            scope.launch {
                while (isActive) {
                    liveSensors.altitudeM?.let { altitude ->
                        val now = System.currentTimeMillis()
                        samples.add(
                            RunSample(
                                timestampMs = now,
                                elapsedSec = (now - startedAt) / 1000f,
                                altitudeM = altitude
                            )
                        )
                    }
                    delay(500)
                }
            }
        }

        jobs = listOf(accelJob, recordJob)
    }

    fun stopRecording() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        isRecording = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hasLocationPermission && mode == RecordMode.Gps) {
            SectionCard(title = "Ride log", subtitle = "Checks the calculated jump against a real run") {
                Text(
                    "Location permission is required to record with GPS. You can still use " +
                        "Test (no GPS) to check the jump detection indoors.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Grant permission")
                }
            }
        }

        SectionCard(title = "Ride log", subtitle = "Checks the calculated jump against a real run") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = { if (!isRecording) mode = option },
                        label = { Text(option.label) },
                        enabled = !isRecording
                    )
                }
            }

            Button(
                onClick = { if (isRecording) stopRecording() else startRecording() },
                enabled = mode == RecordMode.NoGps || hasLocationPermission,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isRecording) "Stop recording" else "Start recording") }

            RecordingStatus(
                isRecording = isRecording,
                mode = mode,
                elapsedSec = elapsedSec,
                sampleCount = samples.size,
                lastFix = lastFix,
                gpsEnabled = locationRepository.isGpsEnabled,
                gpsAvailable = locationRepository.isGpsProviderAvailable,
                hasBarometer = liveSensors.barometerAvailable
            )
        }

        if (samples.size >= 2) {
            val useDistance = mode == RecordMode.Gps
            val points = samples.map {
                (if (useDistance) it.cumulativeDistanceM ?: 0f else it.elapsedSec) to it.altitudeM
            }
            val takeoffSample = detectedJump?.let { nearestSample(samples, it.takeoffAtMs) }
            val landingSample = detectedJump?.let { nearestSample(samples, it.landingAtMs) }

            RunStats(samples, useDistance)

            SectionCard(
                title = "Trail profile",
                subtitle = if (useDistance) "Altitude over ground distance" else "Altitude over time"
            ) {
                RideProfileChart(
                    points = points,
                    takeoff = takeoffSample?.let {
                        JumpMarker(if (useDistance) it.cumulativeDistanceM ?: 0f else it.elapsedSec, it.altitudeM)
                    },
                    landing = landingSample?.let {
                        JumpMarker(if (useDistance) it.cumulativeDistanceM ?: 0f else it.elapsedSec, it.altitudeM)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
                AxisLabels(
                    minValue = samples.minOf { it.altitudeM },
                    maxValue = samples.maxOf { it.altitudeM },
                    unit = "m"
                )
            }

            if (!isRecording) {
                JumpComparisonCard(
                    takeoff = takeoffSample,
                    landing = landingSample,
                    detectedJump = detectedJump,
                    predictedJump = predictedJump,
                    useDistance = useDistance
                )
            }
        }
    }
}

private fun nearestSample(samples: List<RunSample>, atMs: Long): RunSample? =
    samples.minByOrNull { abs(it.timestampMs - atMs) }

private fun clockOf(seconds: Int): String =
    String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)

@Composable
private fun RecordingStatus(
    isRecording: Boolean,
    mode: RecordMode,
    elapsedSec: Int,
    sampleCount: Int,
    lastFix: LocationSample?,
    gpsEnabled: Boolean,
    gpsAvailable: Boolean,
    hasBarometer: Boolean
) {
    val headline: String
    val detail: String?

    when {
        !isRecording && sampleCount == 0 -> {
            headline = "Ready"
            detail = when (mode) {
                RecordMode.Gps -> "Ride the whole run-in, jump and landing with the phone on the bike."
                RecordMode.NoGps -> "No GPS needed - records altitude from the barometer and watches " +
                    "for the airborne moment. Good for testing indoors."
            }
        }

        !isRecording -> {
            headline = "Stopped · $sampleCount points"
            detail = null
        }

        mode == RecordMode.NoGps -> {
            headline = "Recording without GPS · ${clockOf(elapsedSec)} · $sampleCount points"
            detail = if (hasBarometer) null else "No barometer on this device - altitude will stay empty."
        }

        !gpsAvailable -> {
            headline = "No GPS provider on this device"
            detail = "Switch to Test (no GPS) to check the jump detection."
        }

        !gpsEnabled -> {
            headline = "Location is switched off"
            detail = "Turn location on in Settings, or switch to Test (no GPS)."
        }

        lastFix?.latitude == null -> {
            headline = "Searching for GPS… ${clockOf(elapsedSec)}"
            detail = "GPS needs a clear view of the sky - it will not get a fix indoors. " +
                "Go outside, or switch to Test (no GPS)."
        }

        else -> {
            headline = "Recording · ${clockOf(elapsedSec)} · $sampleCount points"
            detail = lastFix?.accuracyM?.let { "GPS accuracy ±${formatValue(it, 0)} m" }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun JumpComparisonCard(
    takeoff: RunSample?,
    landing: RunSample?,
    detectedJump: JumpEvent?,
    predictedJump: JumpScreenResult?,
    useDistance: Boolean
) {
    if (detectedJump == null || takeoff == null || landing == null) {
        NoticeCard(
            "No jump detected in this recording. The phone has to be mounted on the bike (a " +
                "pocket picks up your body, not the bike) and the hop has to be long enough to " +
                "register as free-fall."
        )
        return
    }

    val airTimeSec = (detectedJump.landingAtMs - detectedJump.takeoffAtMs) / 1000f

    if (!useDistance) {
        ResultCard(
            primaryLabel = "DETECTED JUMP",
            primaryValue = formatValue(airTimeSec, 2),
            primaryUnit = "s in the air",
            secondary = listOf(
                "Height change" to "${formatValue(landing.altitudeM - takeoff.altitudeM)} m"
            )
        )
        return
    }

    val actualDistance = (landing.cumulativeDistanceM ?: 0f) - (takeoff.cumulativeDistanceM ?: 0f)
    val secondary = mutableListOf(
        "Air time" to "${formatValue(airTimeSec, 2)} s",
        "Landing speed" to "${formatValue(landing.speedKmh ?: 0f)} km/h"
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
private fun RunStats(samples: List<RunSample>, useDistance: Boolean) {
    val gain = samples.maxOf { it.altitudeM } - samples.minOf { it.altitudeM }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (useDistance) {
            ReadoutTile(
                label = "TOP SPEED",
                value = formatValue(samples.maxOf { it.speedKmh ?: 0f }),
                unit = "km/h"
            )
            ReadoutTile(
                label = "DISTANCE",
                value = formatValue(samples.last().cumulativeDistanceM ?: 0f, 0),
                unit = "m"
            )
        } else {
            ReadoutTile(
                label = "DURATION",
                value = formatValue(samples.last().elapsedSec, 0),
                unit = "s"
            )
        }
        ReadoutTile(label = "ELEVATION RANGE", value = formatValue(gain), unit = "m")
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
