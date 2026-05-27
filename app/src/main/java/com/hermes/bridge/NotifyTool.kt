package com.hermes.bridge

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.ktor.server.application.*
import io.ktor.server.request.*

class NotifyTool(private val context: Context) {
    
    private val CHANNEL_ID = "HermesBridgeNotify"
    private var notificationId = 100
    
    suspend fun sendNotification(call: ApplicationCall): Map<String, Any> {
        return try {
            val body = call.receive<Map<String, Any>>()
            
            val title = body["title"] as? String ?: return errorResponse("title is required")
            val text = body["text"] as? String ?: return errorResponse("text is required")
            val priority = body["priority"] as? String ?: "default"
            
            // Create notification channel (required for Android 8+)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    CHANNEL_ID,
                    "Hermes Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }
            
            // Create intent to open app when notification is tapped
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build notification
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(
                    when (priority) {
                        "high" -> NotificationCompat.PRIORITY_HIGH
                        "low" -> NotificationCompat.PRIORITY_LOW
                        else -> NotificationCompat.PRIORITY_DEFAULT
                    }
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            
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
