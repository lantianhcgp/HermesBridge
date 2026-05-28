package com.hermes.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
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
        const val SERVICE_CHANNEL_ID = "HermesBridgeService"
        const val SERVICE_CHANNEL_NAME = "Hermes Bridge 服务"
        const val DEFAULT_PORT = 8889

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var currentPort = DEFAULT_PORT
            private set

        fun start(context: Context, port: Int = DEFAULT_PORT) {
            val intent = Intent(context, HttpService::class.java).apply {
                putExtra("port", port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HttpService::class.java))
        }
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
    private lateinit var contactsTool: ContactsTool
    private lateinit var clipboardTool: ClipboardTool
    private lateinit var alarmTool: AlarmTool
    private lateinit var wifiTool: WifiTool

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initTools()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", DEFAULT_PORT) ?: DEFAULT_PORT

        if (isRunning && engine != null) {
            Log.d(TAG, "Service already running on port $currentPort, ignoring start command")
            return START_STICKY
        }

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
        contactsTool = ContactsTool(this)
        clipboardTool = ClipboardTool(this)
        alarmTool = AlarmTool(this)
        wifiTool = WifiTool(this)
    }

    private fun startHttpServer(port: Int) {
        engine = embeddedServer(Netty, port = port) {
            routing {
                // ========== Service Control ==========
                get("/api/health") {
                    val response = mapOf(
                        "status" to "ok",
                        "service" to "HermesBridge",
                        "version" to "2.5.0",
                        "port" to currentPort
                    )
                    call.respondJson(response)
                }

                post("/api/service/stop") {
                    val response = mapOf(
                        "success" to true,
                        "message" to "Service stopping..."
                    )
                    call.respondJson(response)
                    // Delay stop to let response reach caller
                    kotlinx.coroutines.delay(500)
                    HttpService.stop(this@HttpService)
                }

                post("/api/service/restart") {
                    val response = mapOf(
                        "success" to true,
                        "message" to "Service restarting..."
                    )
                    call.respondJson(response)
                    kotlinx.coroutines.delay(500)
                    HttpService.stop(this@HttpService)
                    kotlinx.coroutines.delay(1000)
                    HttpService.start(this@HttpService, currentPort)
                }

                // ========== Calendar ==========
                route("/api/calendar") {
                    post("/create") { call.respondJson(calendarTool.createEvent(call)) }
                    get("/list") { call.respondJson(calendarTool.listEvents(call)) }
                    post("/delete") { call.respondJson(calendarTool.deleteEvent(call)) }
                    post("/update") { call.respondJson(calendarTool.updateEvent(call)) }
                }

                // ========== Notifications ==========
                route("/api/notify") {
                    post("/send") { call.respondJson(notifyTool.sendNotification(call)) }
                }

                // ========== SMS ==========
                route("/api/sms") {
                    post("/send") { call.respondJson(smsTool.sendSms(call)) }
                }

                // ========== Location ==========
                get("/api/location") { call.respondJson(locationTool.getLocation(call)) }

                // ========== Device ==========
                route("/api/device") {
                    get("/info") { call.respondJson(deviceTool.getDeviceInfo()) }
                    get("/battery") { call.respondJson(deviceTool.getBatteryStatus()) }
                }

                // ========== Contacts (NEW) ==========
                route("/api/contacts") {
                    get("/search") { call.respondJson(contactsTool.searchContacts(call)) }
                    get("/{id}") {
                        val id = call.parameters["id"]?.toLongOrNull()
                        if (id != null) {
                            call.respondJson(contactsTool.getContactDetail(call, id))
                        } else {
                            call.respondJson(mapOf("success" to false, "error" to "Invalid contact ID"))
                        }
                    }
                }

                // ========== Clipboard (NEW) ==========
                route("/api/clipboard") {
                    get("") { call.respondJson(clipboardTool.getClipboard()) }
                    post("") { call.respondJson(clipboardTool.setClipboard(call)) }
                    post("/clear") { call.respondJson(clipboardTool.clearClipboard()) }
                }

                // ========== Alarm (NEW) ==========
                route("/api/alarm") {
                    post("/set") { call.respondJson(alarmTool.setAlarm(call)) }
                    post("/cancel") { call.respondJson(alarmTool.cancelAlarm(call)) }
                }

                // ========== WiFi (NEW) ==========
                route("/api/wifi") {
                    get("/info") { call.respondJson(wifiTool.getWifiInfo()) }
                    get("/scan") { call.respondJson(wifiTool.scanWifi()) }
                }
            }
        }

        engine?.start(wait = true)
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Channel 1: Persistent service — LOW importance, silent
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

        // Channel 2: Push notifications — HIGH importance, heads-up
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
        engine = null
        serviceScope.cancel()
    }
}

/** Extension: respond with JSON using Gson */
private suspend fun ApplicationCall.respondJson(data: Any) {
    respondText(
        GsonBuilder().create().toJson(data),
        io.ktor.http.ContentType.Application.Json
    )
}
