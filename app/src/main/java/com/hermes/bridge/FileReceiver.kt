package com.hermes.bridge

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

/**
 * 文件操作 BroadcastReceiver
 * 处理通知栏的"打开"和"删除"按钮点击
 */
class FileReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FileReceiver"
        private const val CHANNEL_ID = "file_notifications"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            "DELETE_FILE" -> handleDelete(context, intent)
            "OPEN_FILE" -> handleOpen(context, intent)
            else -> Log.w(TAG, "Unknown action: $action")
        }
    }

    private fun handleDelete(context: Context, intent: Intent) {
        val fileName = intent.getStringExtra("FILE_NAME")
        val fileUri = intent.getStringExtra("FILE_URI")

        Log.d(TAG, "Deleting file: $fileName ($fileUri)")

        // 删除文件
        var deleted = false
        if (fileUri != null && fileUri.startsWith("content://")) {
            // MediaStore 文件
            try {
                context.contentResolver.delete(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "_data=?",
                    arrayOf(fileUri)
                )
                deleted = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete via MediaStore", e)
            }
        } else if (fileName != null) {
            // 传统文件
            val bridgeDir = File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ),
                "HermesBridge"
            )
            val file = File(bridgeDir, fileName)
            if (file.exists()) {
                deleted = file.delete()
            }
        }

        // 取消通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        // 显示删除结果
        val msg = if (deleted) "文件已删除" else "删除失败"
        showResultNotification(context, msg, fileName)
    }

    private fun handleOpen(context: Context, intent: Intent) {
        val fileName = intent.getStringExtra("FILE_NAME")
        val fileUri = intent.getStringExtra("FILE_URI")

        if (fileUri != null) {
            val uri = android.net.Uri.parse(fileUri)
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(fileName ?: ""))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(openIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open file", e)
                showResultNotification(context, "打开失败", fileName)
            }
        }
    }

    private fun showResultNotification(context: Context, message: String, fileName: String?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(if (message.contains("失")) "错误" else "完成")
            .setContentText("$message${fileName?.let { " - $it" } ?: ""}")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // 创建 channel
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "文件操作结果",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "文件打开/删除操作结果通知"
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt() and 0xFFFF, builder.build())
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
