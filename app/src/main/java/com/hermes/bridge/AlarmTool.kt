package com.hermes.bridge

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.util.Calendar

class AlarmTool(private val context: Context) {

    private val alarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    /**
     * 设置闹钟
     * POST /api/alarm/set
     * Body: {"hour": 8, "minute": 30, "label": "起床", "repeat_daily": false}
     */
    suspend fun setAlarm(call: ApplicationCall): Map<String, Any> {
        return try {
            val bodyStr = call.receiveText()
            @Suppress("UNCHECKED_CAST")
            val body = com.google.gson.Gson().fromJson(bodyStr, Map::class.java) as Map<String, Any>

            val hour = (body["hour"] as? Number)?.toInt() ?: return errorResponse("hour is required (0-23)")
            val minute = (body["minute"] as? Number)?.toInt() ?: return errorResponse("minute is required (0-59)")
            val label = body["label"] as? String ?: "Hermes Alarm"
            val repeatDaily = body["repeat_daily"] as? Boolean ?: false

            if (hour !in 0..23 || minute !in 0..59) {
                return errorResponse("Invalid hour (0-23) or minute (0-59)")
            }

            // 检查精确闹钟权限 (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    return errorResponse("Exact alarm permission not granted. Please allow in Settings > Apps > Hermes Bridge > Alarms & reminders")
                }
            }

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // 如果时间已过，设为明天
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val alarmId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("label", label)
                putExtra("alarm_id", alarmId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (repeatDaily) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            mapOf(
                "success" to true,
                "alarm_id" to alarmId,
                "hour" to hour,
                "minute" to minute,
                "label" to label,
                "repeat_daily" to repeatDaily,
                "trigger_at_ms" to calendar.timeInMillis,
                "message" to "Alarm set successfully"
            )
        } catch (e: SecurityException) {
            errorResponse("Alarm permission not granted")
        } catch (e: Exception) {
            errorResponse(e.message ?: "Failed to set alarm")
        }
    }

    /**
     * 取消闹钟
     * POST /api/alarm/cancel
     * Body: {"alarm_id": 12345}
     */
    suspend fun cancelAlarm(call: ApplicationCall): Map<String, Any> {
        return try {
            val bodyStr = call.receiveText()
            @Suppress("UNCHECKED_CAST")
            val body = com.google.gson.Gson().fromJson(bodyStr, Map::class.java) as Map<String, Any>

            val alarmId = (body["alarm_id"] as? Number)?.toInt()
                ?: return errorResponse("alarm_id is required")

            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)

            mapOf(
                "success" to true,
                "alarm_id" to alarmId,
                "message" to "Alarm cancelled"
            )
        } catch (e: Exception) {
            errorResponse(e.message ?: "Failed to cancel alarm")
        }
    }

    private fun errorResponse(message: String): Map<String, Any> {
        return mapOf("success" to false, "error" to message)
    }
}
