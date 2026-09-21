package com.example.dhtrailbuilder

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed class AngleReading {
    /**
     * lengthTiltDeg: incline along the phone's length (top-to-bottom, the Y axis) - this is
     * the ramp/gradient angle when the phone is laid down pointing along the slope.
     * widthTiltDeg: side-to-side tilt (the X axis) - not used for ramp measurements.
     */
    data class Value(val lengthTiltDeg: Float, val widthTiltDeg: Float) : AngleReading()
    object Unavailable : AngleReading()
}

class AngleRepository(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun angleFlow(): Flow<AngleReading> = callbackFlow {
        val sensor = accelerometer
        if (sensor == null) {
            trySend(AngleReading.Unavailable)
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val lengthTilt = Math.toDegrees(
                    atan2(-y.toDouble(), sqrt((x * x + z * z).toDouble()))
                ).toFloat()
                val widthTilt = Math.toDegrees(
                    atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))
                ).toFloat()
                trySend(AngleReading.Value(lengthTiltDeg = lengthTilt, widthTiltDeg = widthTilt))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
