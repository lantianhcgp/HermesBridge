package com.hermes.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.withTimeoutOrNull

class LocationTool(private val context: Context) {

    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    /**
     * 获取当前位置
     * GET /api/location
     * GET /api/location?provider=gps (gps/network/auto)
     * GET /api/location?timeout=15 (seconds, default 10)
     */
    @SuppressLint("MissingPermission")
    suspend fun getLocation(call: io.ktor.server.application.ApplicationCall? = null): Map<String, Any> {
        return try {
            val provider = call?.request?.queryParameters["provider"] ?: "auto"
            val timeoutSec = call?.request?.queryParameters["timeout"]?.toLongOrNull() ?: 10L

            val location = withTimeoutOrNull(timeoutSec * 1000) {
                getCurrentLocation(provider)
            }

            if (location != null) {
                val age = (System.currentTimeMillis() - location.time) / 1000.0
                mapOf(
                    "success" to true,
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracy" to location.accuracy,
                    "altitude" to location.altitude,
                    "speed" to location.speed,
                    "bearing" to location.bearing,
                    "time" to location.time,
                    "age_seconds" to age,
                    "provider" to (location.provider ?: "unknown")
                )
            } else {
                mapOf(
                    "success" to false,
                    "error" to "Unable to get location within ${timeoutSec}s. GPS/Network may be disabled or no fix available."
                )
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Failed to get location"))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(provider: String): Location? {
        return kotlin.coroutines.suspendCoroutine { continuation ->
            var resumed = false

            val safeResume = { loc: Location? ->
                if (!resumed) {
                    resumed = true
                    continuation.resume(loc)
                }
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    safeResume(location)
                }
                override fun onStatusChanged(p: String?, s: Int, extras: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {
                    locationManager.removeUpdates(this)
                    safeResume(null)
                }
            }

            try {
                when (provider) {
                    "gps" -> {
                        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
                        } else {
                            safeResume(null)
                        }
                    }
                    "network" -> {
                        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
                        } else {
                            safeResume(null)
                        }
                    }
                    else -> { // auto — try GPS first, then network
                        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
                        } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
                        } else {
                            safeResume(null)
                        }
                    }
                }
            } catch (e: Exception) {
                safeResume(null)
            }
        }
    }
}

// Extension function for suspendCoroutine resume
private fun <T> kotlin.coroutines.Continuation<T>.resume(value: T) {
    (this as kotlin.coroutines.Continuation<T>).resumeWith(Result.success(value))
}
