package com.hermes.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FileTool — 文件接收、存储与通知
 *
 * 提供 multipart file upload 支持，将 Hermes Agent 发送的文件保存到设备，
 * 并弹出通知让用户可直接用其他 APP 打开。
 *
 * 返回 Map<String, Any?>，由调用方 (HttpService.respondJson) 统一序列化。
 */
class FileTool(private val context: Context) {

    companion object {
        private const val TAG = "HermesBridge"
        private const val FILE_SUBDIR = "HermesBridge"
        const val FILE_CHANNEL_ID = "HermesBridgeFileReceive"
        private const val FILE_CHANNEL_NAME = "文件接收通知"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    private var notificationIdCounter = NOTIFICATION_ID_BASE

    init {
        createNotificationChannel()
    }

    /**
     * 创建文件接收通知渠道（高优先级，带提示音和震动）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 避免重复创建
            if (notificationManager.getNotificationChannel(FILE_CHANNEL_ID) != null) return

            val channel = NotificationChannel(
                FILE_CHANNEL_ID,
                FILE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Hermes Agent 发来的文件已接收，点击可打开"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 处理 multipart file upload 请求
     *
     * 请求格式: POST /api/file/upload (multipart/form-data)
     * 字段: file (FileItem) — 要上传的文件
     *
     * 返回: { "success": true, "path": "content://...", "name": "xxx.pptx", "size": 12345 }
     *       成功后还会弹出通知，点击可打开文件
     */
    suspend fun receiveFile(call: ApplicationCall): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val multipart = call.receiveMultipart()
            var savedUri: String? = null
            var savedName: String? = null
            var savedSize: Long = 0
            var savedFile: File? = null // 仅 API 26-28 需要

            multipart.forEachPart { part ->
                try {
                    when (part) {
                        is PartData.FileItem -> {
                            val fileName = part.originalFileName ?: "unnamed_${System.currentTimeMillis()}"
                            val bytes = part.streamProvider().readBytes()

                            if (bytes.isNotEmpty()) {
                                savedName = fileName
                                savedSize = bytes.size.toLong()
                                val result = saveFile(fileName, bytes)
                                savedUri = result.first
                                savedFile = result.second
                                Log.d(TAG, "File saved: $fileName -> $savedUri (${bytes.size} bytes)")
                            }
                        }
                        else -> { /* 忽略 form field 等其他 part */ }
                    }
                } finally {
                    part.dispose() // 必须释放，否则内存泄漏
                }
            }

            if (savedName != null && savedUri != null) {
                // 弹出通知，让用户可选择打开文件
                showFileNotification(savedName!!, savedSize, savedUri!!, savedFile)

                mapOf(
                    "success" to true,
                    "path" to savedUri,
                    "name" to savedName,
                    "size" to savedSize
                )
            } else {
                mapOf(
                    "success" to false,
                    "error" to "No file received"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "File upload failed", e)
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }

    /**
     * 弹出通知，点击后弹出 APP 选择器打开文件
     * 改为：上传中显示进度通知，完成后显示带操作按钮的通知
     */
    private fun showFileNotification(
        fileName: String,
        fileSize: Long,
        fileUri: String,
        file: File?
    ) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // ===== 第一步：显示上传进度通知（模拟 100% 完成） =====
            val progressIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("SHOW_FILE", fileName)
            }
            val progressFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val progressPendingIntent = PendingIntent.getActivity(
                context,
                notificationIdCounter,
                progressIntent,
                progressFlags
            )

            // 显示完成通知（带操作按钮）
            val openIntent = createOpenFileIntent(fileName, fileUri, file)
            val openPendingIntent = PendingIntent.getActivity(
                context,
                notificationIdCounter + 10,
                openIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val deleteIntent = Intent(context, FileReceiver::class.java).apply {
                action = "DELETE_FILE"
                putExtra("FILE_NAME", fileName)
                putExtra("FILE_URI", fileUri)
            }
            val deletePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationIdCounter + 20,
                deleteIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val sizeStr = formatFileSize(fileSize)

            val notification = NotificationCompat.Builder(context, FILE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("文件已接收")
                .setContentText("$fileName（$sizeStr）")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("收到文件: $fileName（$sizeStr）"))
                .setContentIntent(progressPendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(android.R.drawable.ic_menu_open, "打开", openPendingIntent)
                .addAction(android.R.drawable.ic_menu_delete, "删除", deletePendingIntent)
                .build()

            notificationManager.notify(notificationIdCounter, notification)
            notificationIdCounter += 100 // 避免与其他通知冲突

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show file notification", e)
        }
    }

    /**
     * 创建打开文件的 Intent
     */
    private fun createOpenFileIntent(
        fileName: String,
        fileUri: String,
        file: File?
    ): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            val uri = if (fileUri.startsWith("content://")) {
                android.net.Uri.parse(fileUri)
            } else if (file != null && file.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                android.net.Uri.parse(fileUri)
            }
            setDataAndType(uri, getMimeType(fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 保存文件到设备存储，返回 (uri字符串, 可选File对象)
     *
     * Android 10+ (API 29+): 使用 MediaStore.Downloads (scoped storage) → content:// URI
     * Android 9- (API 26-28): 写入公共 Downloads/HermesBridge/ 目录 → file:// URI + File 对象
     */
    private fun saveFile(fileName: String, bytes: ByteArray): Pair<String, File?> {
        val sanitizedName = sanitizeFileName(fileName)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — Scoped Storage via MediaStore
            Pair(saveViaMediaStore(sanitizedName, bytes), null)
        } else {
            // Android 9- — Direct file access
            saveToLegacyDirectory(sanitizedName, bytes)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyDirectory(fileName: String, bytes: ByteArray): Pair<String, File> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val bridgeDir = File(downloadsDir, FILE_SUBDIR)
        if (!bridgeDir.exists()) {
            bridgeDir.mkdirs()
        }
        val file = resolveConflict(bridgeDir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        return Pair(file.toURI().toString(), file)
    }

    /**
     * 处理文件名冲突：如果存在同名的，重命名已有的文件
     */
    private fun resolveConflict(dir: File, fileName: String): File {
        val file = File(dir, fileName)
        var counter = 1
        while (file.exists()) {
            val dotIndex = fileName.lastIndexOf('.')
            val base = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
            val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
            val renamed = "${base}_($counter)$ext"
            val newFile = File(dir, renamed)
            if (!newFile.exists()) {
                file.renameTo(newFile)
                break
            }
            counter++
        }
        return file
    }

    private fun saveViaMediaStore(fileName: String, bytes: ByteArray): String {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, getMimeType(fileName))
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(bytes)
        } ?: throw Exception("Failed to open output stream")

        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return uri.toString()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            "csv" -> "text/csv"
            "xml" -> "application/xml"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
