package com.hermes.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.ktor.server.application.*
import io.ktor.server.request.*

class ClipboardTool(private val context: Context) {

    private val clipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    /**
     * 读取剪贴板内容
     * GET /api/clipboard
     */
    fun getClipboard(): Map<String, Any> {
        return try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                val text = item.text?.toString() ?: ""
                val label = clip.description?.label?.toString() ?: ""
                mapOf(
                    "success" to true,
                    "text" to text,
                    "label" to label,
                    "mime_types" to (0 until clip.description.mimeTypeCount).map {
                        clip.description.getMimeType(it)
                    }
                )
            } else {
                mapOf(
                    "success" to true,
                    "text" to "",
                    "label" to "",
                    "message" to "Clipboard is empty"
                )
            }
        } catch (e: Exception) {
            errorResponse(e.message ?: "Failed to read clipboard")
        }
    }

    /**
     * 设置剪贴板内容
     * POST /api/clipboard
     * Body: {"text": "内容", "label": "可选标签"}
     */
    suspend fun setClipboard(call: ApplicationCall): Map<String, Any> {
        return try {
            val bodyStr = call.receiveText()
            @Suppress("UNCHECKED_CAST")
            val body = com.google.gson.Gson().fromJson(bodyStr, Map::class.java) as Map<String, Any>

            val text = body["text"] as? String ?: return errorResponse("text is required")
            val label = body["label"] as? String ?: "HermesBridge"

            val clipData = ClipData.newPlainText(label, text)
            clipboardManager.setPrimaryClip(clipData)

            mapOf(
                "success" to true,
                "length" to text.length,
                "message" to "Clipboard set successfully"
            )
        } catch (e: Exception) {
            errorResponse(e.message ?: "Failed to set clipboard")
        }
    }

    /**
     * 清空剪贴板
     * POST /api/clipboard/clear
     */
    fun clearClipboard(): Map<String, Any> {
        return try {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
            mapOf(
                "success" to true,
                "message" to "Clipboard cleared"
            )
        } catch (e: Exception) {
            errorResponse(e.message ?: "Failed to clear clipboard")
        }
    }

    private fun errorResponse(message: String): Map<String, Any> {
        return mapOf("success" to false, "error" to message)
    }
}
