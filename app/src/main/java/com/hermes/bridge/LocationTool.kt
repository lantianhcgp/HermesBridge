package com.hermes.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationTool(private val context: Context) {
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    @SuppressLint("MissingPermission")
    suspend fun getLocation(): Map<String, Any> {
        return try {
            val location = getCurrentLocation()
            
            if (location != null) {
                mapOf(
                    "success" to true,
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracy" to location.accuracy,
                    "altitude" to location.altitude,
                    "speed" to location.speed,
                    "bearing" to location.bearing,
                    "time" to location.time,
                    "provider" to (location.provider ?: "unknown")
                )
            } else {
                errorResponse("Unable to get location. GPS may be disabled.")
            }
        } catch (e: Exception) {
            errorResponse(e.message ?: "Failed to get location")
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? {
        return suspendCoroutine { continuation ->
            try {
                // Try GPS first
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestSingleUpdate(
                        LocationManager.GPS_PROVIDER,
                        object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                locationManager.removeUpdates(this)
                                continuation.resume(location)
                            }
                            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        },
                        Looper.getMainLooper()
                    )
                } 
                // Fallback to network provider
                else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestSingleUpdate(
                        LocationManager.NETWORK_PROVIDER,
                        object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                locationManager.removeUpdates(this)
                                continuation.resume(location)
                            }
                            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        },
                        Looper.getMainLooper()
                    )
                } else {
                    continuation.resume(null)
                }
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }
    }
    
    private fun errorResponse(message: String): Map<String, Any> {
        return mapOf(
            "success" to false,
            "error" to message
        )
    }
}
