package com.example.dhtrailbuilder

import android.content.Context
import android.location.Location
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns an in-progress recording's state and runs its GPS/barometer/accelerometer collection on
 * a coroutine scope that belongs to this singleton, not to any screen or Activity.
 *
 * The previous design ran these jobs on a Composable's `rememberCoroutineScope()`, so anything
 * that disposed that Composable - a config change, the OS backgrounding the app, even an
 * unrelated recomposition - cancelled the recording along with it. Locking the tab bar and the
 * back gesture (see TrailRunScreen) stops the *app* from doing that on its own, but a phone that
 * turns its own screen off and then aggressively kills the backgrounded app (common on MIUI,
 * One UI and similar) can still tear down anything tied to that screen. Living here instead of
 * in the Composable means only the process actually dying can end a recording that Start began -
 * and RecordingService's foreground notice + wake lock, plus the battery-optimization exemption
 * requested on start, both push back against that; [RunStorage.saveDraft] is the last line of
 * defence if the process is killed anyway.
 */
object RecordingSession {
    // Main, not Default: LocationManager.requestLocationUpdates() and SensorManager's listener
    // registration both need a thread with a prepared Looper when no Handler is passed
    // explicitly, and the main thread is the one that is guaranteed to have one for as long as
    // the process is alive - which is exactly the lifetime this scope needs.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var jobs: List<Job> = emptyList()

    val isRecording = mutableStateOf(false)
    val mode = mutableStateOf(RecordMode.Gps)
    val samples: SnapshotStateList<RunSample> = mutableStateListOf()
    val detectedJump = mutableStateOf<JumpEvent?>(null)
    val lastFix = mutableStateOf<LocationSample?>(null)
    val elapsedSec = mutableStateOf(0)
    val recordingStartedAtMs = mutableStateOf(0L)

    fun start(
        context: Context,
        mode: RecordMode,
        sensorRepository: SensorRepository,
        locationRepository: LocationRepository,
        liveSensors: LiveSensorState,
        runStorage: RunStorage,
        hasLocationPermission: Boolean,
        onSpeedSample: (Float) -> Unit
    ) {
        if (isRecording.value) return
        samples.clear()
        detectedJump.value = null
        lastFix.value = null
        this.mode.value = mode
        isRecording.value = true
        val startedAt = System.currentTimeMillis()
        recordingStartedAtMs.value = startedAt
        elapsedSec.value = 0

        // Keeps the CPU and GPS running with the screen off - see RecordingService.
        runCatching {
            RecordingService.start(context, useLocation = mode == RecordMode.Gps && hasLocationPermission)
        }
        requestBatteryOptimizationExemption(context)

        val timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                elapsedSec.value += 1
            }
        }

        // The app-wide barometer listener pauses when the app goes to the background (screen
        // off), so the recording keeps its own feeding the same shared state.
        val pressureJob = scope.launch {
            sensorRepository.pressureFlow().collect { reading ->
                if (reading is PressureReading.Value) liveSensors.pressureHpa = reading.hPa
            }
        }

        val detector = JumpDetector()
        val accelJob = scope.launch {
            sensorRepository.accelerationMagnitudeFlow().collect { magnitude ->
                val event = detector.onSample(magnitude, System.currentTimeMillis())
                if (event != null) detectedJump.value = event
            }
        }

        val recordJob = if (mode == RecordMode.Gps) {
            scope.launch {
                var lastLat: Double? = null
                var lastLon: Double? = null
                var cumulativeDistance = 0f

                locationRepository.locationFlow().collect { fix ->
                    lastFix.value = fix
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
                    onSpeedSample(speed)
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

        val draftJob = scope.launch {
            while (isActive) {
                delay(5_000)
                if (samples.size >= 2) {
                    runStorage.saveDraft(snapshotForSave(rampAngleDeg = null, landingDropM = null, predictedDistanceM = null))
                }
            }
        }

        jobs = listOf(timerJob, pressureJob, accelJob, recordJob, draftJob)
    }

    /** Ends the recording. Sample/jump state is left in place afterwards - call
     * [snapshotForSave] to build the run to persist. */
    fun stop(context: Context) {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        isRecording.value = false
        RecordingService.stop(context)
    }

    /** A [SavedRun] built from the current state - callers fill in the jump-setup fields that
     * only they know about (Run-up's live ramp angle / landing drop, the Jump tab's prediction). */
    fun snapshotForSave(rampAngleDeg: Float?, landingDropM: Float?, predictedDistanceM: Float?): SavedRun =
        SavedRun(
            id = java.util.UUID.randomUUID().toString(),
            startedAtMs = recordingStartedAtMs.value,
            mode = mode.value,
            samples = samples.toList(),
            detectedJump = detectedJump.value,
            predictedDistanceM = predictedDistanceM,
            rampAngleDeg = rampAngleDeg,
            landingDropM = landingDropM
        )

    private fun requestBatteryOptimizationExemption(context: Context) {
        runCatching {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                ?: return
            if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) return
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
}
