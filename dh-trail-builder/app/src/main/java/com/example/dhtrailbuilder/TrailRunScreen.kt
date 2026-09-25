package com.example.dhtrailbuilder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.catch
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
    rampAngleDeg: Float? = null,
    onRecordingChanged: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    var keepScreenOn by remember { mutableStateOf(loadKeepScreenOn(context)) }
    // Only asked so the "Recording" notification is visible - recording works either way.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // The mode selector, choosable only before Start is pressed.
    var selectedMode by remember { mutableStateOf(RecordMode.Gps) }

    // The recording itself lives in RecordingSession, not here - see its doc comment for why.
    // This screen only reads its state and starts/stops it; a config change, the tab switching
    // away, or this Composable being disposed for any other reason does not touch it.
    val isRecording = RecordingSession.isRecording.value
    val samples = RecordingSession.samples
    val detectedJump = RecordingSession.detectedJump.value
    val lastFix = RecordingSession.lastFix.value
    val elapsedSec = RecordingSession.elapsedSec.value
    // What the currently-displayed samples were actually recorded with, decoupled from
    // selectedMode so re-picking a chip after Stop can't relabel a finished run's chart.
    val recordedMode = RecordingSession.mode.value

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

    DisposableEffect(isRecording, keepScreenOn) {
        view.keepScreenOn = isRecording && keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Ambient speed reading for the speedometer when nothing is being recorded - recording
    // itself feeds trackSpeed() from RecordingSession's own GPS collection instead.
    LaunchedEffect(isRecording, hasLocationPermission) {
        if (isRecording || !hasLocationPermission) return@LaunchedEffect
        locationRepository.locationFlow()
            .catch { }
            .collect { fix -> fix.speedKmh?.let { trackSpeed(it) } }
    }

    // Lets the caller lock tab switching while recording - a stray touch through fabric
    // should not be able to navigate away while a run is in progress.
    LaunchedEffect(isRecording) { onRecordingChanged(isRecording) }

    // The hardware/gesture back action is another way a pocket touch could end the screen -
    // swallow it while recording instead of letting it navigate away.
    BackHandler(enabled = isRecording) { }

    fun startRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        RecordingSession.start(
            context = context,
            mode = selectedMode,
            sensorRepository = sensorRepository,
            locationRepository = locationRepository,
            liveSensors = liveSensors,
            runStorage = runStorage,
            hasLocationPermission = hasLocationPermission,
            onSpeedSample = ::trackSpeed
        )
    }

    fun stopRecording() {
        RecordingSession.stop(context)
        val finishedSamples = RecordingSession.samples
        if (finishedSamples.size >= 2) {
            val run = RecordingSession.snapshotForSave(
                rampAngleDeg = approachRampAngleDeg,
                landingDropM = approachLandingDropM,
                predictedDistanceM = predictedJump?.distanceM
            )
            scope.launch {
                runStorage.saveRun(run)
                runStorage.clearDraft()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!hasLocationPermission && selectedMode == RecordMode.Gps) {
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

        // Start/stop comes first - it is the one thing you always want without scrolling.
        SectionCard(title = "Record") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordMode.entries.forEach { option ->
                    FilterChip(
                        selected = selectedMode == option,
                        onClick = { if (!isRecording) selectedMode = option },
                        label = { Text(option.label) },
                        enabled = !isRecording
                    )
                }
            }

            if (isRecording) {
                // A tap can happen by accident through fabric in a pocket - stopping needs a
                // deliberate full-width slide instead.
                SlideToStopControl(
                    onConfirm = { stopRecording() },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PrimaryActionButton(
                    text = "Start recording",
                    onClick = { startRecording() },
                    enabled = selectedMode == RecordMode.NoGps || hasLocationPermission,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Keep screen on while recording",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = {
                        keepScreenOn = it
                        saveKeepScreenOn(context, it)
                    }
                )
            }

            RecordingStatus(
                isRecording = isRecording,
                mode = selectedMode,
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

        // Right under Start recording: the live estimate, so the number you care about most
        // never needs a scroll to reach.
        if (!isRecording) {
            ApproachEstimateCard(
                liveSpeedKmh = liveSpeedKmh,
                rampAngleDeg = approachRampAngleDeg,
                landingDropM = approachLandingDropM
            )
        }

        // Once a run is stopped, its profile (with the slider and markers) comes right after
        // the estimate - still above the fold on most phones. Hidden while still recording:
        // samples keeps growing live, so this used to put a second, unrelated slider (the
        // chart scrubber) on screen right next to Slide to stop - easy to grab by mistake, and
        // there is nothing useful to scrub through yet anyway.
        if (!isRecording) {
            RunResultsSection(
                samples = samples,
                mode = recordedMode,
                detectedJump = detectedJump,
                predictedDistanceM = predictedJump?.distanceM,
                showComparison = true,
                rampAngleDeg = approachRampAngleDeg,
                landingDropM = approachLandingDropM
            )
        }

        LiveSpeedCard(
            currentKmh = liveSpeedKmh,
            maxKmh30s = speedWindow.maxOfOrNull { it.second } ?: 0f,
            trace = speedWindow
        )

        // The full measurement UI (diagram, capture chips, +/- rows) stays lower - the compact
        // estimate above already shows the number while riding.
        if (!isRecording) {
            ApproachInputsCard(
                liveSensors = liveSensors,
                rampAngleDeg = approachRampAngleDeg,
                onRampAngleChange = { approachRampAngleDeg = it },
                landingDropM = approachLandingDropM,
                onLandingDropChange = { approachLandingDropM = it }
            )
        }
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

    // The chart sits first - the whole point is to see it (and the stats below it) without
    // scrolling right after stopping a recording.
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionCard(title = "Trail profile") {
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
                    .height(88.dp)
            )
            Slider(
                value = scrubIndex.toFloat(),
                onValueChange = { userScrubIndex = it.roundToInt() },
                valueRange = 0f..samples.lastIndex.toFloat().coerceAtLeast(0f),
                steps = (samples.size - 2).coerceAtLeast(0),
                modifier = Modifier.height(28.dp)
            )
            ScrubReadout(sample = scrubSample, useDistance = useDistance)
            if (peakSample != null) {
                MarkerLegend(showBraking = brakeSample != null)
            }
        }

        RunStats(samples, useDistance)

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

/**
 * "Slide to stop": the only way to end a recording, so a tap through fabric in a pocket can't
 * do it by accident. Drag the handle to the end of the track; letting go anywhere short of that
 * springs it back to the start with nothing triggered.
 */
@Composable
private fun SlideToStopControl(onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val handleSizeDp = 48.dp
    val density = LocalDensity.current
    val handleSizePx = with(density) { handleSizeDp.toPx() }
    var trackWidthPx by remember { mutableStateOf(0f) }
    val maxOffsetPx = (trackWidthPx - handleSizePx).coerceAtLeast(1f)

    // The handle's position while a finger is actively on it: plain, synchronous state. The
    // previous version routed every drag delta through `scope.launch { animatable.snapTo(...) }`
    // - a fresh coroutine per pixel of movement - so onDragStopped could run and read the
    // offset *before* the last few deltas had actually been applied, see the handle short of
    // the threshold even though the finger had reached the end, and never call onConfirm() at
    // all. That silently broke the only way to stop a recording. Reading a plain var here has
    // no such lag.
    var offsetPx by remember { mutableStateOf(0f) }
    var confirmed by remember { mutableStateOf(false) }

    // Only used to animate the handle to its resting position (back to the start, or on through
    // to the end on confirm) once the finger lifts - purely cosmetic, decoupled from the
    // confirm decision above so it can never delay or drop it.
    val settle = remember { Animatable(0f) }
    var settleTarget by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(settleTarget) {
        val target = settleTarget ?: return@LaunchedEffect
        settle.snapTo(offsetPx)
        settle.animateTo(target)
        offsetPx = target
        settleTarget = null
    }

    val displayOffset = settleTarget?.let { settle.value } ?: offsetPx
    val progress = (displayOffset / maxOffsetPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .onSizeChanged { trackWidthPx = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Slide to stop →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 1f - progress),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .padding(4.dp)
                .offset { IntOffset(displayOffset.roundToInt(), 0) }
                .size(handleSizeDp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !confirmed,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(0f, maxOffsetPx)
                    },
                    onDragStopped = {
                        if (offsetPx >= maxOffsetPx * 0.85f) {
                            confirmed = true
                            settleTarget = maxOffsetPx
                            onConfirm()
                        } else {
                            settleTarget = 0f
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "■",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onError
            )
        }
    }
}

/** Live speed, always on regardless of recording: current reading, the fastest point in the
 * last 30 seconds, and a trace of that window with the peak and the hardest braking marked -
 * literally where the speed was highest and where it dropped fastest. */
@Composable
private fun LiveSpeedCard(currentKmh: Float?, maxKmh30s: Float, trace: List<Pair<Long, Float>>) {
    SectionCard(title = "Live speed") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
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
                    .height(32.dp)
                    .padding(top = 2.dp)
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
 * Just the number: would you clear it right now, at the live GPS speed and the ramp geometry
 * already captured. No diagram, no inputs - those live in [ApproachInputsCard] further down -
 * so this sits right under Start recording without pushing it off screen.
 */
@Composable
private fun ApproachEstimateCard(
    liveSpeedKmh: Float?,
    rampAngleDeg: Float?,
    landingDropM: Float?
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

    when {
        speed == null -> NoticeCard("Waiting for a live GPS speed reading.")
        angle == null -> NoticeCard("Measure the ramp angle below to see the estimate.")
        drop == null -> NoticeCard("Measure the landing drop below to see the estimate.")
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
 * The diagram, capture chips and +/- rows behind the estimate above: B at the ramp, C where
 * you'd touch down. Structured like the Jump tab's own Landing and Takeoff sections, letters
 * carried over so the diagram and the capture chips always point at the same spot.
 */
@Composable
private fun ApproachInputsCard(
    liveSensors: LiveSensorState,
    rampAngleDeg: Float?,
    onRampAngleChange: (Float?) -> Unit,
    landingDropM: Float?,
    onLandingDropChange: (Float?) -> Unit
) {
    DiagramCard(
        instruction = "B at the ramp, C where you'd touch down",
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        // The distance span itself is shown by the compact estimate card above (it needs the
        // live speed); this diagram just anchors B and C.
        RampLandingProfile(
            rampAngleDeg = rampAngleDeg,
            landingDropM = landingDropM,
            jumpDistanceM = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
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

private const val PREFS_NAME = "dh_trail_builder_prefs"
private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

private fun loadKeepScreenOn(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_KEEP_SCREEN_ON, false)

private fun saveKeepScreenOn(context: Context, value: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_KEEP_SCREEN_ON, value)
        .apply()
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
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
