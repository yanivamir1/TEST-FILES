package com.example.hdtrailbuilder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var pitchDeg: Float? by mutableStateOf(null)
        internal set
    var rollDeg: Float? by mutableStateOf(null)
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
}

@Composable
fun rememberLiveSensors(
    sensorRepository: SensorRepository,
    angleRepository: AngleRepository
): LiveSensorState {
    val state = remember { LiveSensorState() }
    val lifecycleOwner = LocalLifecycleOwner.current

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
                                    state.pitchDeg = reading.pitchDeg
                                    state.rollDeg = reading.rollDeg
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
