package com.hermes.bridge

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class DeviceTool(private val context: Context) {
    
    fun getDeviceInfo(): Map<String, Any> {
        return mapOf(
            "success" to true,
            "device" to mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "device" to Build.DEVICE,
                "product" to Build.PRODUCT,
                "brand" to Build.BRAND,
                "android_version" to Build.VERSION.RELEASE,
                "sdk_version" to Build.VERSION.SDK_INT,
                "build_id" to Build.ID,
                "hardware" to Build.HARDWARE,
                "bootloader" to Build.BOOTLOADER,
                "board" to Build.BOARD
            ),
            "network" to getNetworkInfo(),
            "storage" to getStorageInfo()
        )
    }
    
    fun getBatteryStatus(): Map<String, Any> {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level != -1 && scale != -1) (level.toFloat() / scale.toFloat() * 100).toInt() else -1
        
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }
        
        val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        
        return mapOf(
            "success" to true,
            "battery" to mapOf(
                "percentage" to percentage,
                "status" to statusText,
                "temperature" to temperature,
                "voltage" to voltage,
                "temperature_celsius" to temperature / 10.0
            )
        )
    }
    
    private fun getNetworkInfo(): Map<String, Any> {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        
        val isConnected = capabilities != null
        val connectionType = when {
            capabilities == null -> "none"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
        
        return mapOf(
            "is_connected" to isConnected,
            "type" to connectionType
        )
    }
    
    private fun getStorageInfo(): Map<String, Any> {
        val stat = StatFs(Environment.getDataDirectory().path)
        
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = totalBytes - availableBytes
        
        return mapOf(
            "total_mb" to (totalBytes / (1024 * 1024)),
            "available_mb" to (availableBytes / (1024 * 1024)),
            "used_mb" to (usedBytes / (1024 * 1024)),
            "used_percentage" to if (totalBytes > 0) (usedBytes * 100 / totalBytes).toInt() else 0
        )
    }
}
