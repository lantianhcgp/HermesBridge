package com.hermes.bridge

import android.content.Context
import android.telephony.SmsManager
import io.ktor.server.application.*
import io.ktor.server.request.*

class SmsTool(private val context: Context) {
    
    suspend fun sendSms(call: ApplicationCall): Map<String, Any> {
        return try {
            val body = call.receive<Map<String, Any>>()
            
            val to = body["to"] as? String ?: return errorResponse("to is required")
            val message = body["message"] as? String ?: return errorResponse("message is required")
            
            // Validate phone number
            if (!isValidPhoneNumber(to)) {
                return errorResponse("Invalid phone number format")
            }
            
            // Get SmsManager
            val smsManager = SmsManager.getDefault()
            
            // Check if message is long
            if (message.length > 160) {
                // Split into multiple messages
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(to, null, message, null, null)
            }
            
            mapOf(
                "success" to true,
                "to" to to,
                "message_length" to message.length,
                "message" to "SMS sent successfully"
            )
        } catch (e: Exception) {
            errorResponse(e.message ?: "Failed to send SMS")
        }
    }
    
    private fun isValidPhoneNumber(phone: String): Boolean {
        // Basic validation: digits, +, -, spaces
        val cleaned = phone.replace(Regex("[\\s\\-\\+]"), "")
        return cleaned.all { it.isDigit() } && cleaned.length in 7..15
    }
    
    private fun errorResponse(message: String): Map<String, Any> {
        return mapOf(
            "success" to false,
            "error" to message
        )
    }
}
