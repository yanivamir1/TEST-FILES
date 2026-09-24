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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
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

@Composable
fun TrailRunScreen(
    sensorRepository: SensorRepository,
    locationRepository: LocationRepository,
    liveSensors: LiveSensorState,
    runStorage: RunStorage,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    predictedJump: JumpScreenResult? = null,
    rampAngleDeg: Float? = null
) {
    val scope = rememberCoroutineScope()
    val samples = remember { mutableStateListOf<RunSample>() }
    var mode by remember { mutableStateOf(RecordMode.Gps) }
    var isRecording by remember { mutableStateOf(false) }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var detectedJump by remember { mutableStateOf<JumpEvent?>(null) }
    var lastFix by remember { mutableStateOf<LocationSample?>(null) }
    var elapsedSec by remember { mutableStateOf(0) }
    var recordingStartedAtMs by remember { mutableStateOf(0L) }

    // The speedometer: live even before recording starts, independent of the saved run.
    var liveSpeedKmh by remember { mutableStateOf<Float?>(null) }
    var speedWindow by remember { mutableStateOf<List<Pair<Long, Float>>>(emptyList()) }

    // The approach check: the fastest speed since the last reset, and the altitude at that
    // exact instant, tracked automatically - see trackSpeed() below.
    var approachPeakSpeedKmh by remember { mutableStateOf<Float?>(null) }
    var approachPeakAltitudeM by remember { mutableStateOf<Float?>(null) }
    var approachLipAltitudeM by remember { mutableStateOf<Float?>(null) }
    var approachLandingDropM by remember { mutableStateOf<Float?>(null) }
    var approachRampAngleDeg by remember { mutableStateOf(rampAngleDeg) }

    fun resetApproachCheck() {
        approachPeakSpeedKmh = null
        approachPeakAltitudeM = null
        approachLipAltitudeM = null
        approachLandingDropM = null
    }

    fun trackSpeed(speedKmh: Float) {
        val now = System.currentTimeMillis()
        liveSpeedKmh = speedKmh
        speedWindow = (speedWindow + (now to speedKmh)).filter { now - it.first <= 30_000L }

        val peak = approachPeakSpeedKmh
        if (peak == null || speedKmh > peak) {
            approachPeakSpeedKmh = speedKmh
            approachPeakAltitudeM = liveSensors.altitudeM
        }
    }

    DisposableEffect(Unit) {
        onDispose { jobs.forEach { it.cancel() } }
    }

    // Ambient speed reading for the speedometer when nothing is being recorded - recording
    // itself feeds trackSpeed() from its own GPS collection below instead of running a second.
    LaunchedEffect(isRecording, hasLocationPermission) {
        if (isRecording || !hasLocationPermission) return@LaunchedEffect
        locationRepository.locationFlow()
            .catch { }
            .collect { fix -> fix.speedKmh?.let { trackSpeed(it) } }
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
        recordingStartedAtMs = startedAt

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
                    // The barometer is what the rest of the app measures and calibrates against -
                    // GPS altitude is a different reference and commonly tens of meters off.
                    // Fall back to it only when there is no barometer at all.
                    val altitude = liveSensors.altitudeM ?: fix.altitudeMeters
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
                    // A fix without a speed means standing still, not a useless fix.
                    val speed = fix.speedKmh ?: 0f
                    trackSpeed(speed)
                    samples.add(
                        RunSample(
                            timestampMs = now,
                            elapsedSec = (now - startedAt) / 1000f,
                            altitudeM = altitude,
                            speedKmh = speed,
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

        if (samples.size >= 2) {
            val run = SavedRun(
                id = UUID.randomUUID().toString(),
                startedAtMs = recordingStartedAtMs,
                mode = mode,
                samples = samples.toList(),
                detectedJump = detectedJump,
                predictedDistanceM = predictedJump?.distanceM
            )
            scope.launch { runStorage.saveRun(run) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LiveSpeedCard(
            currentKmh = liveSpeedKmh,
            maxKmh30s = speedWindow.maxOfOrNull { it.second } ?: 0f
        )

        if (!isRecording) {
            ApproachCheckCard(
                liveSensors = liveSensors,
                rampAngleDeg = approachRampAngleDeg,
                onRampAngleChange = { approachRampAngleDeg = it },
                peakSpeedKmh = approachPeakSpeedKmh,
                peakAltitudeM = approachPeakAltitudeM,
                lipAltitudeM = approachLipAltitudeM,
                onCaptureLip = { approachLipAltitudeM = liveSensors.altitudeM },
                landingDropM = approachLandingDropM,
                onLandingDropChange = { approachLandingDropM = it },
                onReset = { resetApproachCheck() }
            )
        }

        if (!hasLocationPermission && mode == RecordMode.Gps) {
            SectionCard(title = "Ride log", subtitle = "Checks the calculated jump against a real run") {
                Text(
                    "Location permission is required to record with GPS. You can still use " +
                        "Test (no GPS) to check the jump detection indoors.",
                    style = MaterialTheme.typography.bodyMedium
                )
                PrimaryActionButton(
                    text = "Grant permission",
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                )
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

            PrimaryActionButton(
                text = if (isRecording) "Stop recording" else "Start recording",
                onClick = { if (isRecording) stopRecording() else startRecording() },
                enabled = mode == RecordMode.NoGps || hasLocationPermission,
                modifier = Modifier.fillMaxWidth()
            )

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

            if (!isRecording && samples.size >= 2) {
                Text(
                    "Saved to History",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        RunResultsSection(
            samples = samples,
            mode = mode,
            detectedJump = detectedJump,
            predictedDistanceM = predictedJump?.distanceM,
            showComparison = !isRecording
        )
    }
}

/** Stats, chart and (optionally) the detected-jump comparison for a set of samples. Shared by
 * the live Ride Log view (after a recording stops) and the History detail view. */
@Composable
fun RunResultsSection(
    samples: List<RunSample>,
    mode: RecordMode,
    detectedJump: JumpEvent?,
    predictedDistanceM: Float?,
    modifier: Modifier = Modifier,
    showComparison: Boolean = true
) {
    if (samples.size < 2) return

    val useDistance = mode == RecordMode.Gps
    val points = samples.map {
        (if (useDistance) it.cumulativeDistanceM ?: 0f else it.elapsedSec) to it.altitudeM
    }
    val takeoffSample = detectedJump?.let { nearestSample(samples, it.takeoffAtMs) }
    val landingSample = detectedJump?.let { nearestSample(samples, it.landingAtMs) }

    var userScrubIndex by remember { mutableStateOf<Int?>(null) }
    val scrubIndex = (userScrubIndex ?: samples.lastIndex).coerceIn(0, samples.lastIndex)
    val scrubSample = samples[scrubIndex]

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                cursor = (if (useDistance) scrubSample.cumulativeDistanceM ?: 0f else scrubSample.elapsedSec) to
                    scrubSample.altitudeM,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            )
            AxisLabels(
                minValue = samples.minOf { it.altitudeM },
                maxValue = samples.maxOf { it.altitudeM },
                unit = "m"
            )

            ScrubReadout(sample = scrubSample, useDistance = useDistance)
            Slider(
                value = scrubIndex.toFloat(),
                onValueChange = { userScrubIndex = it.roundToInt() },
                valueRange = 0f..samples.lastIndex.toFloat().coerceAtLeast(0f),
                steps = (samples.size - 2).coerceAtLeast(0)
            )
        }

        if (showComparison) {
            JumpComparisonCard(
                takeoff = takeoffSample,
                landing = landingSample,
                detectedJump = detectedJump,
                predictedDistanceM = predictedDistanceM,
                useDistance = useDistance
            )
        }
    }
}

/** Live speed, always on regardless of recording: current reading and the fastest point in
 * the last 30 seconds. */
@Composable
private fun LiveSpeedCard(currentKmh: Float?, maxKmh30s: Float) {
    SectionCard(title = "Live speed", subtitle = "Works even before you start recording") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            ReadoutTile(
                label = "SPEED",
                value = currentKmh?.let { formatValue(it, 0) } ?: "—",
                unit = "km/h"
            )
            ReadoutTile(
                label = "MAX 30s",
                value = formatValue(maxKmh30s, 0),
                unit = "km/h"
            )
        }
    }
}

/**
 * The pre-jump test: ride the approach, slow to a stop instead of hitting the lip, and see
 * whether you'd have cleared it. The peak speed and the altitude at that instant are captured
 * automatically (see trackSpeed in the caller). Structured like the Jump tab - a pinned diagram,
 * then Approach / Takeoff / Landing - so the ramp angle can be (re)measured right here instead
 * of only ever coming from the Jump tab.
 */
@Composable
private fun ApproachCheckCard(
    liveSensors: LiveSensorState,
    rampAngleDeg: Float?,
    onRampAngleChange: (Float?) -> Unit,
    peakSpeedKmh: Float?,
    peakAltitudeM: Float?,
    lipAltitudeM: Float?,
    onCaptureLip: () -> Unit,
    landingDropM: Float?,
    onLandingDropChange: (Float?) -> Unit,
    onReset: () -> Unit
) {
    val dropToLip = if (peakAltitudeM != null && lipAltitudeM != null) {
        peakAltitudeM - lipAltitudeM
    } else {
        null
    }

    DiagramCard(
        instruction = "∠ at the lip, B where you'd touch down",
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        RampLandingProfile(
            rampAngleDeg = rampAngleDeg,
            landingDropM = landingDropM,
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
        )
    }

    SectionCard(
        title = "Approach",
        subtitle = "Ride down, slow to a stop before the lip - see if you'd have cleared it"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ReadoutTile(
                label = "PEAK SPEED",
                value = peakSpeedKmh?.let { formatValue(it, 0) } ?: "—",
                unit = "km/h"
            )
            ReadoutTile(
                label = "DROP TO LIP",
                value = dropToLip?.let { formatValue(it) } ?: "—",
                unit = "m"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryActionButton(
                text = "Capture at lip",
                onClick = onCaptureLip,
                enabled = liveSensors.altitudeM != null,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onReset) { Text("Reset") }
        }
    }

    SectionCard(title = "Takeoff") {
        AngleMeasureInput(
            label = "Ramp angle",
            hint = "Phone on the ramp face, length pointing down the slope",
            liveSensors = liveSensors,
            valueDeg = rampAngleDeg,
            onValueChange = onRampAngleChange
        )
    }

    SectionCard(title = "Landing") {
        ElevationDeltaInput(
            label = "Drop to the landing",
            hint = if (lipAltitudeM != null) {
                "The lip is already set from the capture above - just capture where you touch down."
            } else {
                "∠ at the lip, B where you touch down."
            },
            liveSensors = liveSensors,
            valueMeters = landingDropM,
            onValueChange = onLandingDropChange,
            presetPointA = lipAltitudeM,
            presetPointACaption = "lip, captured",
            pointALabel = "lip",
            pointBLabel = "landing",
            letterA = "∠",
            letterB = "B"
        )
    }

    val speed = peakSpeedKmh
    val angle = rampAngleDeg
    val drop = landingDropM

    when {
        speed == null -> NoticeCard(
            "Ride down and reach your approach speed - it's captured automatically."
        )
        angle == null -> NoticeCard(
            "Measure the ramp angle above to see the estimate."
        )
        drop == null -> NoticeCard(
            "Measure the landing drop above to see the estimate."
        )
        else -> {
            val speedMs = speed / 3.6f
            val naiveResult = Physics.computeJump(speedMs, angle, drop)

            Text(
                text = "IF YOU'D KEPT YOUR SPEED (${formatValue(speed, 0)} km/h)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (naiveResult) {
                is Physics.JumpResult.ShortOfLanding -> NoticeCard(
                    "You would not have cleared it at that speed alone.",
                    isError = true
                )
                is Physics.JumpResult.Landed -> ResultCard(
                    primaryLabel = "DISTANCE AT THAT SPEED",
                    primaryValue = formatValue(naiveResult.distanceM),
                    primaryUnit = "m",
                    secondary = listOf("Air time" to "${formatValue(naiveResult.airTimeSec, 2)} s")
                )
            }

            if (dropToLip != null) {
                val potentialLipSpeedMs = Physics.lipSpeed(speedMs, dropToLip)
                val potentialResult = Physics.computeJump(potentialLipSpeedMs, angle, drop)

                Text(
                    text = "IF YOU HAD CONTINUED TO THE LIP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when (potentialResult) {
                    is Physics.JumpResult.ShortOfLanding -> NoticeCard(
                        "Even continuing to the lip, you would not have cleared it.",
                        isError = true
                    )
                    is Physics.JumpResult.Landed -> ResultCard(
                        primaryLabel = "POTENTIAL DISTANCE",
                        primaryValue = formatValue(potentialResult.distanceM),
                        primaryUnit = "m",
                        secondary = listOf(
                            "Potential lip speed" to "${formatValue(potentialLipSpeedMs * 3.6f, 0)} km/h",
                            "Air time" to "${formatValue(potentialResult.airTimeSec, 2)} s"
                        )
                    )
                }
            } else {
                NoticeCard("Capture at the lip too, to see the potential-speed estimate.")
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
    predictedDistanceM: Float?,
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
    predictedDistanceM?.let { predicted ->
        val delta = actualDistance - predicted
        val sign = if (delta >= 0) "+" else ""
        secondary.add(0, "Predicted" to "${formatValue(predicted)} m")
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

/** The value the finger is currently over, shown above the slider. */
@Composable
private fun ScrubReadout(sample: RunSample, useDistance: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReadoutTile(label = "ALTITUDE", value = formatValue(sample.altitudeM), unit = "m")
        if (useDistance) {
            ReadoutTile(
                label = "SPEED",
                value = formatValue(sample.speedKmh ?: 0f),
                unit = "km/h"
            )
            ReadoutTile(
                label = "AT",
                value = formatValue(sample.cumulativeDistanceM ?: 0f, 0),
                unit = "m"
            )
        } else {
            ReadoutTile(label = "AT", value = formatValue(sample.elapsedSec, 0), unit = "s")
        }
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
