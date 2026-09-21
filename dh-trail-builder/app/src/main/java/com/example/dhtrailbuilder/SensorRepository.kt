package com.example.dhtrailbuilder

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed class PressureReading {
    data class Value(val hPa: Float) : PressureReading()
    object Unavailable : PressureReading()
}

class SensorRepository(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val hasBarometer: Boolean get() = pressureSensor != null
    val hasAccelerometer: Boolean get() = accelerometer != null

    fun pressureFlow(): Flow<PressureReading> = callbackFlow {
        val sensor = pressureSensor
        if (sensor == null) {
            trySend(PressureReading.Unavailable)
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(PressureReading.Value(event.values[0]))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Raw accelerometer magnitude (m/s²) at a fast, game-suitable rate, used for free-fall
     * detection during a ride - proper acceleration drops to near zero while airborne, versus
     * ~9.8 m/s² at rest or riding. Not the same as [pressureFlow]'s slower, UI-rate sibling.
     */
    fun accelerationMagnitudeFlow(): Flow<Float> = callbackFlow {
        val sensor = accelerometer
        if (sensor == null) {
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                trySend(sqrt(x * x + y * y + z * z))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
