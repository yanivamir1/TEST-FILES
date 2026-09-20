package com.example.barometergps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** speedKmh is null until the GPS provider delivers a fix with a valid speed. */
data class SpeedReading(val speedKmh: Float?)

class LocationRepository(context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val isGpsProviderAvailable: Boolean
        get() = locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)

    @SuppressLint("MissingPermission")
    fun speedFlow(): Flow<SpeedReading> = callbackFlow {
        if (!isGpsProviderAvailable) {
            trySend(SpeedReading(speedKmh = null))
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val speed = if (location.hasSpeed()) location.speed * 3.6f else null
                trySend(SpeedReading(speedKmh = speed))
            }

            @Deprecated("Deprecated in Java, still called on older API levels")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                trySend(SpeedReading(speedKmh = null))
            }
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L,
            1f,
            listener
        )
        awaitClose { locationManager.removeUpdates(listener) }
    }
}
