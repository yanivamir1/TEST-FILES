package com.example.dhtrailbuilder

import android.location.Location
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

    // The approach check: landing drop and ramp angle, measured live against the current speed.
    var approachLandingDropM by remember { mutableStateOf<Float?>(null) }
    var approachRampAngleDeg by remember { mutableStateOf(rampAngleDeg) }

    fun trackSpeed(speedKmh: Float) {
        val now = System.currentTimeMillis()
        liveSpeedKmh = speedKmh
        speedWindow = (speedWindow + (now to speedKmh)).filter { now - it.first <= 30_000L }
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
                predictedDistanceM = predictedJump?.distanceM,
                rampAngleDeg = approachRampAngleDeg,
                landingDropM = approachLandingDropM
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
            maxKmh30s = speedWindow.maxOfOrNull { it.second } ?: 0f,
            trace = speedWindow
        )

        if (!isRecording) {
            ApproachCheckCard(
                liveSensors = liveSensors,
                liveSpeedKmh = liveSpeedKmh,
                rampAngleDeg = approachRampAngleDeg,
                onRampAngleChange = { approachRampAngleDeg = it },
                landingDropM = approachLandingDropM,
                onLandingDropChange = { approachLandingDropM = it }
            )
        }

        if (!hasLocationPermission && mode == RecordMode.Gps) {
            SectionCard(title = "Record", subtitle = "Checks the calculated jump against a real run") {
                Text(
                    "Location permission is required to record with GPS. You can still use " +
                        "Test (no GPS) to check the jump detection indoors.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                PrimaryActionButton(
                    text = "Grant permission",
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SectionCard(title = "Record", subtitle = "Checks the calculated jump against a real run") {
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
            showComparison = !isRecording,
            rampAngleDeg = approachRampAngleDeg,
            landingDropM = approachLandingDropM
        )
    }
}

/** Stats, chart and (optionally) the detected-jump comparison for a set of samples. Shared by
 * the live Run-up view (after a recording stops) and the History detail view. */
@Composable
fun RunResultsSection(
    samples: List<RunSample>,
    mode: RecordMode,
    detectedJump: JumpEvent?,
    predictedDistanceM: Float?,
    modifier: Modifier = Modifier,
    showComparison: Boolean = true,
    rampAngleDeg: Float? = null,
    landingDropM: Float? = null
) {
    if (samples.size < 2) return

    val useDistance = mode == RecordMode.Gps
    val points = samples.map {
        (if (useDistance) it.cumulativeDistanceM ?: 0f else it.elapsedSec) to it.altitudeM
    }
    val takeoffSample = detectedJump?.let { nearestSample(samples, it.takeoffAtMs) }
    val landingSample = detectedJump?.let { nearestSample(samples, it.landingAtMs) }
    val peakSample = if (useDistance) samples.maxByOrNull { it.speedKmh ?: 0f } else null
    val brakeSample = if (useDistance) brakingPoint(samples) else null
    fun xOf(sample: RunSample) = if (useDistance) sample.cumulativeDistanceM ?: 0f else sample.elapsedSec

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
                cursor = xOf(scrubSample) to scrubSample.altitudeM,
                peakSpeed = peakSample?.let { JumpMarker(xOf(it), it.altitudeM) },
                braking = brakeSample?.let { JumpMarker(xOf(it), it.altitudeM) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            )
            AxisLabels(
                minValue = samples.minOf { it.altitudeM },
                maxValue = samples.maxOf { it.altitudeM },
                unit = "m"
            )
            if (peakSample != null) {
                MarkerLegend(showBraking = brakeSample != null)
            }

            ScrubReadout(sample = scrubSample, useDistance = useDistance)
            Slider(
                value = scrubIndex.toFloat(),
                onValueChange = { userScrubIndex = it.roundToInt() },
                valueRange = 0f..samples.lastIndex.toFloat().coerceAtLeast(0f),
                steps = (samples.size - 2).coerceAtLeast(0)
            )
        }

        if (showComparison && useDistance) {
            PotentialJumpCard(
                peak = peakSample,
                braking = brakeSample,
                cursor = scrubSample,
                rampAngleDeg = rampAngleDeg,
                landingDropM = landingDropM
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

/** Live speed, always on regardless of recording: current reading, the fastest point in the
 * last 30 seconds, and a trace of that window with the peak and the hardest braking marked -
 * literally where the speed was highest and where it dropped fastest. */
@Composable
private fun LiveSpeedCard(currentKmh: Float?, maxKmh30s: Float, trace: List<Pair<Long, Float>>) {
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

        if (trace.size >= 2) {
            SpeedTraceChart(
                trace = trace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(top = 4.dp)
            )
        }
    }
}

/**
 * The last 30s of speed as a simple line, cream dot at the peak, red dot at the single
 * biggest drop between two consecutive readings - the moment braking bit hardest.
 */
@Composable
private fun SpeedTraceChart(trace: List<Pair<Long, Float>>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val peakColor = MaterialTheme.colorScheme.primary
    val brakeColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        val minT = trace.first().first
        val maxT = trace.last().first
        val spanT = (maxT - minT).coerceAtLeast(1L)
        val maxSpeed = trace.maxOf { it.second }.coerceAtLeast(1f)

        fun xOf(t: Long) = size.width * (t - minT).toFloat() / spanT
        fun yOf(v: Float) = size.height * (1f - (v / maxSpeed).coerceIn(0f, 1f))

        val path = Path()
        trace.forEachIndexed { index, (t, v) ->
            val x = xOf(t)
            val y = yOf(v)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))

        val peak = trace.maxByOrNull { it.second }
        peak?.let { (t, v) -> drawCircle(peakColor, 5f, Offset(xOf(t), yOf(v))) }

        var brakeIndex = -1
        var biggestDrop = 0f
        for (i in 1 until trace.size) {
            val drop = trace[i - 1].second - trace[i].second
            if (drop > biggestDrop) {
                biggestDrop = drop
                brakeIndex = i
            }
        }
        if (brakeIndex >= 0 && biggestDrop > 2f) {
            val (t, v) = trace[brakeIndex]
            drawCircle(brakeColor, 5f, Offset(xOf(t), yOf(v)))
        }
    }
}

/**
 * Would you clear it right now? A live distance estimate from the current GPS speed plus the
 * ramp geometry - no recording, no capture-and-stop test. Structured like the Jump tab's own
 * Landing and Takeoff sections, letters carried over (B = ramp, C = landing) so the diagram and
 * the capture chips always point at the same spot.
 */
@Composable
private fun ApproachCheckCard(
    liveSensors: LiveSensorState,
    liveSpeedKmh: Float?,
    rampAngleDeg: Float?,
    onRampAngleChange: (Float?) -> Unit,
    landingDropM: Float?,
    onLandingDropChange: (Float?) -> Unit
) {
    val speed = liveSpeedKmh
    val angle = rampAngleDeg
    val drop = landingDropM
    val result = if (speed != null && angle != null && drop != null) {
        Physics.computeJump(speed / 3.6f, angle, drop)
    } else {
        null
    }
    val landed = result as? Physics.JumpResult.Landed

    DiagramCard(
        instruction = "B at the ramp, C where you'd touch down",
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        RampLandingProfile(
            rampAngleDeg = rampAngleDeg,
            landingDropM = landingDropM,
            jumpDistanceM = landed?.distanceM,
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
        )
    }

    SectionCard(title = "Landing") {
        ElevationDeltaInput(
            label = "Drop to the landing",
            hint = "B at the ramp, C where you touch down.",
            liveSensors = liveSensors,
            valueMeters = landingDropM,
            onValueChange = onLandingDropChange,
            pointALabel = "ramp",
            pointBLabel = "landing",
            letterA = "B",
            letterB = "C"
        )
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

    when {
        speed == null -> NoticeCard("Waiting for a live GPS speed reading.")
        angle == null -> NoticeCard("Measure the ramp angle above to see the estimate.")
        drop == null -> NoticeCard("Measure the landing drop above to see the estimate.")
        result is Physics.JumpResult.ShortOfLanding -> NoticeCard(
            "You would not clear it at your current speed (${formatValue(speed, 0)} km/h).",
            isError = true
        )
        landed != null -> ResultCard(
            primaryLabel = "ESTIMATED DISTANCE",
            primaryValue = formatValue(landed.distanceM),
            primaryUnit = "m",
            secondary = listOf(
                "At current speed" to "${formatValue(speed, 0)} km/h",
                "Air time" to "${formatValue(landed.airTimeSec, 2)} s"
            )
        )
    }
}

/**
 * Looking back at a recorded run: how far the jump set up in Approach check (ramp angle B,
 * landing drop C) would have gone at the speeds actually ridden - the top speed, the speed
 * right before the hardest braking, and whatever point the slider is on. Works with the phone
 * in a pocket, since it only needs the GPS speed, not the free-fall detection.
 */
@Composable
private fun PotentialJumpCard(
    peak: RunSample?,
    braking: RunSample?,
    cursor: RunSample,
    rampAngleDeg: Float?,
    landingDropM: Float?
) {
    val peakSpeed = peak?.speedKmh
    if (rampAngleDeg == null || landingDropM == null) {
        NoticeCard(
            "Set the landing drop and ramp angle to see how far you could have jumped at the " +
                "speeds in this run."
        )
        return
    }
    if (peakSpeed == null || peakSpeed <= 0f) {
        NoticeCard("No GPS speed in this run, so there is nothing to estimate from.")
        return
    }

    fun estimate(speedKmh: Float): String =
        when (val result = Physics.computeJump(speedKmh / 3.6f, rampAngleDeg, landingDropM)) {
            is Physics.JumpResult.Landed -> "${formatValue(result.distanceM)} m"
            is Physics.JumpResult.ShortOfLanding -> "short"
        }

    val peakResult = Physics.computeJump(peakSpeed / 3.6f, rampAngleDeg, landingDropM)
    val secondary = mutableListOf("Top speed" to "${formatValue(peakSpeed, 0)} km/h")
    braking?.speedKmh?.let { speed ->
        secondary.add("Before braking ${formatValue(speed, 0)} km/h" to estimate(speed))
    }
    cursor.speedKmh?.let { speed ->
        secondary.add("At slider ${formatValue(speed, 0)} km/h" to estimate(speed))
    }

    when (peakResult) {
        is Physics.JumpResult.Landed -> ResultCard(
            primaryLabel = "YOU COULD HAVE JUMPED",
            primaryValue = formatValue(peakResult.distanceM),
            primaryUnit = "m",
            secondary = secondary + ("Air time" to "${formatValue(peakResult.airTimeSec, 2)} s")
        )
        is Physics.JumpResult.ShortOfLanding -> NoticeCard(
            "Even at your top speed (${formatValue(peakSpeed, 0)} km/h) you would not have " +
                "cleared this landing.",
            isError = true
        )
    }
}

/** Colour key for the V (top speed) and ! (braking) markers on the trail profile. */
@Composable
private fun MarkerLegend(showBraking: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "V top speed",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        if (showBraking) {
            Text(
                text = "! braking",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Where braking started: the sample with the biggest speed loss over the next ~3 readings
 * (GPS reports about once a second). Only counts a loss of more than 3 km/h, so a steady run
 * shows no braking point at all.
 */
private fun brakingPoint(samples: List<RunSample>): RunSample? {
    var best: RunSample? = null
    var biggestDrop = 3f
    for (i in 0 until samples.size - 1) {
        val speed = samples[i].speedKmh ?: continue
        val lowestAfter = samples.subList(i + 1, minOf(i + 4, samples.size))
            .mapNotNull { it.speedKmh }
            .minOrNull() ?: continue
        val drop = speed - lowestAfter
        if (drop > biggestDrop) {
            biggestDrop = drop
            best = samples[i]
        }
    }
    return best
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
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
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
            "No airborne moment detected - that needs the phone mounted on the bike, a pocket " +
                "picks up your body." +
                if (useDistance) " The estimate above comes from your speed and works either way." else ""
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
                modifier = Modifier.weight(1f),
                label = "TOP SPEED",
                value = formatValue(samples.maxOf { it.speedKmh ?: 0f }),
                unit = "km/h"
            )
            ReadoutTile(
                modifier = Modifier.weight(1f),
                label = "DISTANCE",
                value = formatValue(samples.last().cumulativeDistanceM ?: 0f, 0),
                unit = "m"
            )
        } else {
            ReadoutTile(
                modifier = Modifier.weight(1f),
                label = "DURATION",
                value = formatValue(samples.last().elapsedSec, 0),
                unit = "s"
            )
        }
        ReadoutTile(label = "HEIGHT", value = formatValue(gain), unit = "m", modifier = Modifier.weight(1f))
    }
}

/** The value the finger is currently over, shown above the slider. */
@Composable
private fun ScrubReadout(sample: RunSample, useDistance: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReadoutTile(label = "ALTITUDE", value = formatValue(sample.altitudeM), unit = "m", modifier = Modifier.weight(1f))
        if (useDistance) {
            ReadoutTile(
                modifier = Modifier.weight(1f),
                label = "SPEED",
                value = formatValue(sample.speedKmh ?: 0f),
                unit = "km/h"
            )
            ReadoutTile(
                modifier = Modifier.weight(1f),
                label = "AT",
                value = formatValue(sample.cumulativeDistanceM ?: 0f, 0),
                unit = "m"
            )
        } else {
            ReadoutTile(label = "AT", value = formatValue(sample.elapsedSec, 0), unit = "s", modifier = Modifier.weight(1f))
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
