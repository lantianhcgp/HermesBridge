package com.hermes.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

class WifiTool(private val context: Context) {

    /**
     * 获取 WiFi 详细信息
     * GET /api/wifi/info
     */
    fun getWifiInfo(): Map<String, Any> {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            val isConnected = isNetworkConnected()
            val connectionType = getConnectionType()

            val result = mutableMapOf<String, Any>(
                "success" to true,
                "is_connected" to isConnected,
                "connection_type" to connectionType,
                "ip_address" to getLocalIpAddress()
            )

            if (wifiManager != null && connectionType == "wifi") {
                val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ 需要新 API
                    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
                    // WiFi info 从 NetworkCapabilities 获取有限信息
                    null
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager.connectionInfo
                }

                @Suppress("DEPRECATION")
                if (wifiInfo != null) {
                    val ssid = wifiInfo.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: "unknown"
                    val bssid = wifiInfo.bssid ?: "unknown"
                    val rssi = wifiInfo.rssi
                    val linkSpeed = wifiInfo.linkSpeed
                    val freq = wifiInfo.frequency

                    result["wifi"] = mapOf(
                        "ssid" to ssid,
                        "bssid" to bssid,
                        "rssi" to rssi,
                        "rssi_level" to calculateSignalLevel(rssi),
                        "link_speed_mbps" to linkSpeed,
                        "frequency_mhz" to freq,
                        "channel" to frequencyToChannel(freq)
                    )
                }

                // WiFi 开关状态
                result["wifi_enabled"] = wifiManager.isWifiEnabled
            }

            result.toMap()
        } catch (e: SecurityException) {
            mapOf("success" to false, "error" to "Location permission required for WiFi info")
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Failed to get WiFi info"))
        }
    }

    /**
     * 扫描可用 WiFi 网络
     * GET /api/wifi/scan
     */
    fun scanWifi(): Map<String, Any> {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            if (wifiManager == null) {
                return mapOf("success" to false, "error" to "WiFi service not available")
            }

            val scanResults = wifiManager.scanResults ?: emptyList()
            val networks = scanResults.map { result ->
                mapOf(
                    "ssid" to (result.SSID ?: ""),
                    "bssid" to (result.BSSID ?: ""),
                    "rssi" to result.level,
                    "rssi_level" to calculateSignalLevel(result.level),
                    "frequency" to result.frequency,
                    "channel" to frequencyToChannel(result.frequency),
                    "capabilities" to (result.capabilities ?: "")
                )
            }.filter { (it["ssid"] as String).isNotEmpty() } // 过滤隐藏网络

            mapOf(
                "success" to true,
                "count" to networks.size,
                "networks" to networks
            )
        } catch (e: SecurityException) {
            mapOf("success" to false, "error" to "Location permission required for WiFi scan")
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Failed to scan WiFi"))
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getConnectionType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "unknown"
                    }
                }
            }
        } catch (_: Exception) {}
        return "unknown"
    }

    private fun calculateSignalLevel(rssi: Int): String {
        return when {
            rssi >= -50 -> "excellent"
            rssi >= -65 -> "good"
            rssi >= -75 -> "fair"
            rssi >= -85 -> "poor"
            else -> "very_poor"
        }
    }

    private fun frequencyToChannel(freq: Int): Int {
        return when {
            freq in 2412..2484 -> (freq - 2407) / 5
            freq in 5170..5825 -> (freq - 5000) / 5
            freq in 5955..7115 -> (freq - 5950) / 5
            else -> 0
        }
    }
}
