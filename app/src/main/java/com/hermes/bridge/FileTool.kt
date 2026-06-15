package com.hermes.bridge

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FileTool — 文件接收与存储
 *
 * 提供 multipart file upload 支持，将 Hermes Agent 发送的文件保存到设备。
 * 
 * 返回 Map<String, Any?>，由调用方 (HttpService.respondJson) 统一序列化。
 */
class FileTool(private val context: Context) {

    companion object {
        private const val TAG = "HermesBridge"
        private const val FILE_SUBDIR = "HermesBridge"
    }

    /**
     * 处理 multipart file upload 请求
     * 
     * 请求格式: POST /api/file/upload (multipart/form-data)
     * 字段: file (FileItem) — 要上传的文件
     * 
     * 返回: { "success": true, "path": "/storage/...", "name": "xxx.pptx", "size": 12345 }
     */
    suspend fun receiveFile(call: ApplicationCall): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val multipart = call.receiveMultipart()
            var savedPath: String? = null
            var savedName: String? = null
            var savedSize: Long = 0

            multipart.forEachPart { part ->
                try {
                    when (part) {
                        is PartData.FileItem -> {
                            val fileName = part.originalFileName ?: "unnamed_${System.currentTimeMillis()}"
                            val bytes = part.streamProvider().readBytes()
                            
                            if (bytes.isNotEmpty()) {
                                savedName = fileName
                                savedSize = bytes.size.toLong()
                                savedPath = saveFile(fileName, bytes)
                                Log.d(TAG, "File saved: $fileName -> $savedPath (${bytes.size} bytes)")
                            }
                        }
                        else -> { /* 忽略 form field 等其他 part */ }
                    }
                } finally {
                    part.dispose() // 必须释放，否则内存泄漏
                }
            }

            if (savedPath != null) {
                mapOf(
                    "success" to true,
                    "path" to savedPath,
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
     * 保存文件到设备存储
     * 
     * Android 10+ (API 29+): 使用 MediaStore.Downloads (scoped storage)
     * Android 9- (API 26-28): 写入公共 Downloads 目录
     */
    private fun saveFile(fileName: String, bytes: ByteArray): String {
        val sanitizedName = sanitizeFileName(fileName)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — Scoped Storage via MediaStore
            saveViaMediaStore(sanitizedName, bytes)
        } else {
            // Android 9- — Direct file access
            saveToLegacyDirectory(sanitizedName, bytes)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyDirectory(fileName: String, bytes: ByteArray): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val bridgeDir = File(downloadsDir, FILE_SUBDIR)
        if (!bridgeDir.exists()) {
            bridgeDir.mkdirs()
        }
        val file = File(bridgeDir, fileName)
        var counter = 1
        while (file.exists()) {
            val dotIndex = fileName.lastIndexOf('.')
            val base = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
            val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
            val renamed = "${base}_($counter)$ext"
            val newFile = File(bridgeDir, renamed)
            if (!newFile.exists()) {
                file.renameTo(newFile)
                break
            }
            counter++
        }
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
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

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "html" -> "text/html"
            else -> "application/octet-stream"
        }
    }
}
