package com.example.hdtrailbuilder

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

    val hasBarometer: Boolean get() = pressureSensor != null

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

    /** Approximate altitude in meters above sea level from a barometric pressure reading (hPa). */
    fun altitudeMeters(hPa: Float): Float =
        SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa)
}
