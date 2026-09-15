package com.hermes.bridge

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import io.ktor.server.application.*
import io.ktor.server.request.*

class SmsTool(context: Context) : BaseTool(context) {

    companion object {
        private const val TAG = "HermesBridge"
    }

    suspend fun sendSms(call: ApplicationCall): Map<String, Any> {
        return try {
            val body = parseBody(call)

            val to = body["to"] as? String ?: return error("to is required")
            val message = body["message"] as? String ?: return error("message is required")

            if (!isValidPhoneNumber(to)) {
                return error("Invalid phone number format")
            }

            Log.i(TAG, "SMS: sending to $to (${message.length} chars)")

            val smsManager = SmsManager.getDefault()
            if (message.length > 160) {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(to, null, message, null, null)
            }

            ok(
                "to" to to,
                "message_length" to message.length,
                "message" to "SMS sent successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed", e)
            error(e.message ?: "Failed to send SMS")
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        val cleaned = phone.replace(Regex("[\\s\\-\\+]"), "")
        return cleaned.all { it.isDigit() } && cleaned.length in 7..15
    }
}
