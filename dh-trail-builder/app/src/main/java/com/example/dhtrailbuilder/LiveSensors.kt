package com.example.dhtrailbuilder

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One shared, continuously updating view of the barometer and tilt sensor.
 * Hoisted to the app root so the whole UI reads live values without each widget
 * registering its own sensor listener.
 */
@Stable
class LiveSensorState {
    var pressureHpa: Float? by mutableStateOf(null)
        internal set
    var lengthTiltDeg: Float? by mutableStateOf(null)
        internal set
    var widthTiltDeg: Float? by mutableStateOf(null)
        internal set
    var barometerAvailable: Boolean by mutableStateOf(true)
        internal set
    var accelerometerAvailable: Boolean by mutableStateOf(true)
        internal set

    var seaLevelPressureHpa: Float by mutableStateOf(Physics.STANDARD_SEA_LEVEL_HPA)
        private set
    var isCalibrated: Boolean by mutableStateOf(false)
        private set

    /** Current altitude above sea level, using the calibrated reference when one is set. */
    val altitudeM: Float?
        get() = pressureHpa?.let { Physics.altitudeFrom(seaLevelPressureHpa, it) }

    /** Tell the app "I am standing at this altitude right now" and correct the reference. */
    fun calibrateTo(knownAltitudeM: Float): Boolean {
        val p = pressureHpa ?: return false
        seaLevelPressureHpa = Physics.seaLevelPressureFor(p, knownAltitudeM)
        isCalibrated = true
        return true
    }

    fun resetCalibration() {
        seaLevelPressureHpa = Physics.STANDARD_SEA_LEVEL_HPA
        isCalibrated = false
    }

    /** Restores a calibration saved from a previous session - see [rememberLiveSensors]. Unlike
     * [calibrateTo], this doesn't need a live pressure reading: it sets the reference directly. */
    internal fun restoreCalibration(seaLevelHpa: Float, calibrated: Boolean) {
        seaLevelPressureHpa = seaLevelHpa
        isCalibrated = calibrated
    }
}

@Composable
fun rememberLiveSensors(
    sensorRepository: SensorRepository,
    angleRepository: AngleRepository
): LiveSensorState {
    val context = LocalContext.current
    val state = remember {
        LiveSensorState().apply {
            val (seaLevelHpa, calibrated) = loadCalibration(context)
            restoreCalibration(seaLevelHpa, calibrated)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Calibrating used to reset itself every time the app was closed, so it needed redoing on
    // every launch - now it's remembered like the dark-mode preference, and just kept in sync
    // here whenever it changes (from the calibration dialog, or a manual reset).
    LaunchedEffect(state.seaLevelPressureHpa, state.isCalibrated) {
        saveCalibration(context, state.seaLevelPressureHpa, state.isCalibrated)
    }

    DisposableEffect(lifecycleOwner) {
        var pressureJob: Job? = null
        var angleJob: Job? = null

        val observer = LifecycleEventObserver { owner, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    pressureJob = owner.lifecycleScope.launch {
                        sensorRepository.pressureFlow().collect { reading ->
                            when (reading) {
                                is PressureReading.Value -> {
                                    state.pressureHpa = reading.hPa
                                    state.barometerAvailable = true
                                }
                                is PressureReading.Unavailable -> state.barometerAvailable = false
                            }
                        }
                    }
                    angleJob = owner.lifecycleScope.launch {
                        angleRepository.angleFlow().collect { reading ->
                            when (reading) {
                                is AngleReading.Value -> {
                                    state.lengthTiltDeg = reading.lengthTiltDeg
                                    state.widthTiltDeg = reading.widthTiltDeg
                                    state.accelerometerAvailable = true
                                }
                                is AngleReading.Unavailable -> state.accelerometerAvailable = false
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    pressureJob?.cancel()
                    angleJob?.cancel()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pressureJob?.cancel()
            angleJob?.cancel()
        }
    }

    return state
}

private const val CALIBRATION_PREFS_NAME = "dh_trail_builder_prefs"
private const val KEY_SEA_LEVEL_HPA = "sea_level_hpa"
private const val KEY_IS_CALIBRATED = "is_calibrated"

private fun loadCalibration(context: Context): Pair<Float, Boolean> {
    val prefs = context.getSharedPreferences(CALIBRATION_PREFS_NAME, Context.MODE_PRIVATE)
    val seaLevelHpa = prefs.getFloat(KEY_SEA_LEVEL_HPA, Physics.STANDARD_SEA_LEVEL_HPA)
    val calibrated = prefs.getBoolean(KEY_IS_CALIBRATED, false)
    return seaLevelHpa to calibrated
}

private fun saveCalibration(context: Context, seaLevelHpa: Float, calibrated: Boolean) {
    context.getSharedPreferences(CALIBRATION_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putFloat(KEY_SEA_LEVEL_HPA, seaLevelHpa)
        .putBoolean(KEY_IS_CALIBRATED, calibrated)
        .apply()
}
