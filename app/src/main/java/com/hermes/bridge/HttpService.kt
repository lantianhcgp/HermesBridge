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
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HttpService : Service() {
    
    private val TAG = "HermesBridge"
    private val CHANNEL_ID = "HermesBridgeChannel"
    private val NOTIFICATION_ID = 1
    
    private var engine: NettyApplicationEngine? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
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
        createNotificationChannel()
        initTools()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", 8888) ?: 8888
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        serviceScope.launch {
            try {
                startHttpServer(port)
                Log.d(TAG, "HTTP server started on port $port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HTTP server", e)
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
            install(ContentNegotiation) {
                json()
            }
            
            routing {
                // Health check
                get("/api/health") {
                    call.respond(mapOf(
                        "status" to "ok",
                        "service" to "HermesBridge",
                        "version" to "1.0"
                    ))
                }
                
                // Calendar routes
                route("/api/calendar") {
                    post("/create") {
                        val result = calendarTool.createEvent(call)
                        call.respond(result)
                    }
                    get("/list") {
                        val result = calendarTool.listEvents(call)
                        call.respond(result)
                    }
                    post("/delete") {
                        val result = calendarTool.deleteEvent(call)
                        call.respond(result)
                    }
                    post("/update") {
                        val result = calendarTool.updateEvent(call)
                        call.respond(result)
                    }
                }
                
                // Notification routes
                route("/api/notify") {
                    post("/send") {
                        val result = notifyTool.sendNotification(call)
                        call.respond(result)
                    }
                }
                
                // SMS routes
                route("/api/sms") {
                    post("/send") {
                        val result = smsTool.sendSms(call)
                        call.respond(result)
                    }
                }
                
                // Location routes
                route("/api/location") {
                    get("") {
                        val result = locationTool.getLocation()
                        call.respond(result)
                    }
                }
                
                // Device routes
                route("/api/device") {
                    get("/info") {
                        val result = deviceTool.getDeviceInfo()
                        call.respond(result)
                    }
                    get("/battery") {
                        val result = deviceTool.getBatteryStatus()
                        call.respond(result)
                    }
                }
            }
        }
        
        engine?.start(wait = true)
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hermes Bridge Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持 Hermes Bridge 服务运行"
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Bridge 运行中")
            .setContentText("HTTP 服务器监听中...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        engine?.stop(1000, 5000)
        serviceScope.cancel()
    }
}
