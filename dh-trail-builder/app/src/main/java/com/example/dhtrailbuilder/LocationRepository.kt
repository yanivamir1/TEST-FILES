package com.example.dhtrailbuilder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * speedKmh / altitudeMeters are null until the GPS provider delivers a fix carrying that value.
 * altitudeMeters is height above the WGS84 ellipsoid - fine for a relative speed/altitude graph,
 * not an absolute sea-level reference (use the barometer for that, see SensorRepository).
 * latitude / longitude are null only when there is no fix at all yet.
 */
data class LocationSample(
    val speedKmh: Float?,
    val altitudeMeters: Float?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null
)

class LocationRepository(context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val isGpsProviderAvailable: Boolean
        get() = locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)

    /** The provider existing is not the same as location being switched on. */
    val isGpsEnabled: Boolean
        get() = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun locationFlow(): Flow<LocationSample> = callbackFlow {
        if (!isGpsProviderAvailable) {
            trySend(LocationSample(speedKmh = null, altitudeMeters = null))
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val speed = if (location.hasSpeed()) location.speed * 3.6f else null
                val altitude = if (location.hasAltitude()) location.altitude.toFloat() else null
                trySend(
                    LocationSample(
                        speedKmh = speed,
                        altitudeMeters = altitude,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyM = if (location.hasAccuracy()) location.accuracy else null
                    )
                )
            }

            @Deprecated("Deprecated in Java, still called on older API levels")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                trySend(LocationSample(speedKmh = null, altitudeMeters = null))
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
