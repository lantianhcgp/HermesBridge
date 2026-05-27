package com.hermes.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HttpService : Service() {
    
    private val TAG = "HermesBridge"
    companion object {
        // 常驻服务通知通道 — IMPORTANCE_LOW = 不发声、不悬浮、状态栏小图标
        const val SERVICE_CHANNEL_ID = "HermesBridgeService"
        const val SERVICE_CHANNEL_NAME = "Hermes Bridge 服务"
        
        @Volatile
        var isRunning = false
            private set
        
        @Volatile
        var currentPort = 8889
            private set
    }
    
    private val NOTIFICATION_ID = 1
    private var engine: NettyApplicationEngine? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    
    // Tool instances
    private lateinit var calendarTool: CalendarTool
    private lateinit var notifyTool: NotifyTool
    private lateinit var smsTool: SmsTool
    private lateinit var locationTool: LocationTool
    private lateinit var deviceTool: DeviceTool
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initTools()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", 8889) ?: 8889
        currentPort = port
        
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true
        
        serviceScope.launch {
            try {
                startHttpServer(port)
                Log.d(TAG, "HTTP server started on port $port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HTTP server", e)
                isRunning = false
            }
        }
        
        return START_STICKY
    }
    
    private fun initTools() {
        calendarTool = CalendarTool(this)
        notifyTool = NotifyTool(this)
        smsTool = SmsTool(this)
        locationTool = LocationTool(this)
        deviceTool = DeviceTool(this)
    }
    
    private fun startHttpServer(port: Int) {
        engine = embeddedServer(Netty, port = port) {
            routing {
                // Health check
                get("/api/health") {
                    val response = mapOf(
                        "status" to "ok",
                        "service" to "HermesBridge",
                        "version" to "2.0"
                    )
                    call.respondText(gson.toJson(response), io.ktor.http.ContentType.Application.Json)
                }
                
                // Calendar routes
                route("/api/calendar") {
                    post("/create") {
                        val result = calendarTool.createEvent(call)
                        call.respondText(gson.toJson(result), io.ktor.http.ContentType.Application.Json)
                    }
                    get("/list") {
                        val result = calendarTool.listEvents(call)
                        call.respondText(gson.toJson(result), io.ktor.http.ContentType.Application.Json)
                    }
                    post("/delete") {
                        val result = calendarTool.deleteEvent(call)
                        call.respondText(gson.toJson(result), io.ktor.http.ContentType.Application.Json)
                    }
                    post("/update") {
                        val result = calendarTool.updateEvent(call)
                        call.respondText(gson.toJson(result), io.ktor.http.ContentType.Application.Json)
                    }
                }
                
                // Notification routes
                route("/api/notify") {
                    post("/send") {
                        val result = notifyTool.sendNotification(call)
                        call.respondText(gson.toJson(result), io.ktor.http.ContentType.Application.Json)
                    }
                }
                
                // SMS routes
                route("/api/sms") {
                    post("/send") {
                        val result = smsTool.sendSms(call)
                        call.respondText(gson.toJson(result), io.ktor.http.ContentType.Application.Json)
                    }
                }
                
                // Location routes
                route("/api/location") {
                    get("") {
                        val result = locationTool.getLocation()
                        call.respondText(gson.toJson(result), io.ktor.http.ContentType.Application.Json)
                    }
                }
                
                // Device routes
                route("/api/device") {
                    get("/info") {
                        val result = deviceTool.getDeviceInfo()
                        call.respondText(gson.toJson(result), io.ktor.http.ContentType.Application.Json)
                    }
                    get("/battery") {
                        val result = deviceTool.getBatteryStatus()
                        call.respondText(gson.toJson(result), io.ktor.http.ContentType.Application.Json)
                    }
                }
            }
        }
        
        engine?.start(wait = true)
    }
    
    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        // 通道 1: 常驻服务通知 — LOW importance，不发声不悬浮
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            SERVICE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Hermes Bridge 后台服务常驻通知（不可关闭）"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(serviceChannel)
        
        // 通道 2: 推送通知 — HIGH importance，悬浮+发声+振动
        val pushChannel = NotificationChannel(
            NotifyTool.PUSH_CHANNEL_ID,
            NotifyTool.PUSH_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Hermes 推送的通知，会在屏幕上悬浮显示"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 200, 250)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(pushChannel)
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("Hermes Bridge 运行中")
            .setContentText("端口: $currentPort | HTTP 服务器已启动")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        engine?.stop(1000, 5000)
        serviceScope.cancel()
    }
}
