package com.hermes.bridge

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import io.ktor.server.application.*
import io.ktor.server.request.*

class NotifyTool(private val context: Context) {
    
    companion object {
        // 推送通知通道 — IMPORTANCE_HIGH = 悬浮通知 (heads-up)
        const val PUSH_CHANNEL_ID = "HermesBridgePush"
        const val PUSH_CHANNEL_NAME = "Hermes 推送通知"
    }
    
    private var notificationId = 100
    private val gson = Gson()
    
    init {
        // 确保推送通知通道已创建
        createPushChannel()
    }
    
    private fun createPushChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channel = android.app.NotificationChannel(
                PUSH_CHANNEL_ID,
                PUSH_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH  // 悬浮通知！
            ).apply {
                description = "Hermes 推送的通知，会在屏幕上悬浮显示"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    suspend fun sendNotification(call: ApplicationCall): Map<String, Any> {
        return try {
            val bodyStr = call.receiveText()
            @Suppress("UNCHECKED_CAST")
            val body = gson.fromJson(bodyStr, Map::class.java) as Map<String, Any>
            
            val title = body["title"] as? String ?: return errorResponse("title is required")
            val text = body["text"] as? String ?: return errorResponse("text is required")
            val priority = body["priority"] as? String ?: "high"
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Create intent to open app when notification is tapped
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, System.currentTimeMillis().toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build heads-up notification on the push channel
            val builder = NotificationCompat.Builder(context, PUSH_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(
                    when (priority) {
                        "high" -> NotificationCompat.PRIORITY_HIGH
                        "low" -> NotificationCompat.PRIORITY_LOW
                        else -> NotificationCompat.PRIORITY_HIGH  // 默认就是悬浮
                    }
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
            
            // Show notification
            val id = notificationId++
            notificationManager.notify(id, builder.build())
            
            mapOf(
                "success" to true,
                "notification_id" to id,
                "message" to "Notification sent successfully"
            )
        } catch (e: Exception) {
            errorResponse(e.message ?: "Unknown error")
        }
    }
    
    private fun errorResponse(message: String): Map<String, Any> {
        return mapOf(
            "success" to false,
            "error" to message
        )
    }
}
