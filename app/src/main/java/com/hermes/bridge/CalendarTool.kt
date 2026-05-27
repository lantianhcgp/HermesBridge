package com.hermes.bridge

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.util.TimeZone

class CalendarTool(private val context: Context) {
    
    data class CalendarEvent(
        val id: Long,
        val calendarId: Long,
        val title: String,
        val description: String,
        val location: String,
        val startMs: Long,
        val endMs: Long,
        val allDay: Boolean
    )
    
    suspend fun createEvent(call: ApplicationCall): Map<String, Any> {
        return try {
            val body = call.receive<Map<String, Any>>()
            
            val title = body["title"] as? String ?: return errorResponse("title is required")
            val startMs = body["start_ms"] as? Number ?: return errorResponse("start_ms is required")
            val endMs = body["end_ms"] as? Number ?: return errorResponse("end_ms is required")
            val description = body["description"] as? String ?: ""
            val location = body["location"] as? String ?: ""
            val allDay = body["all_day"] as? Boolean ?: false
            
            val calendarId = findWritableCalendarId()
                ?: return errorResponse("No writable calendar found")
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMs.toLong())
                put(CalendarContract.Events.DTEND, endMs.toLong())
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                if (description.isNotBlank()) {
                    put(CalendarContract.Events.DESCRIPTION, description)
                }
                if (location.isNotBlank()) {
                    put(CalendarContract.Events.EVENT_LOCATION, location)
                }
            }
            
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            
            if (uri != null) {
                val eventId = ContentUris.parseId(uri)
                mapOf(
                    "success" to true,
                    "event_id" to eventId,
                    "message" to "Calendar event created successfully"
                )
            } else {
                errorResponse("Failed to create calendar event")
            }
        } catch (e: Exception) {
            errorResponse(e.message ?: "Unknown error")
        }
    }
    
    suspend fun listEvents(call: ApplicationCall): Map<String, Any> {
        return try {
            val fromMs = call.request.queryParameters["from_ms"]?.toLongOrNull()
                ?: System.currentTimeMillis()
            val toMs = call.request.queryParameters["to_ms"]?.toLongOrNull()
                ?: (fromMs + 7L * 24L * 60L * 60L * 1000L)
            
            val events = mutableListOf<CalendarEvent>()
            
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, fromMs)
            ContentUris.appendId(builder, toMs)
            val uri = builder.build()
            
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
            )
            
            context.contentResolver.query(uri, projection, null, null, 
                "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val calCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val descCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                val locCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                
                while (cursor.moveToNext()) {
                    events.add(CalendarEvent(
                        id = cursor.getLong(0),
                        calendarId = cursor.getLong(1),
                        title = cursor.getString(2) ?: "",
                        description = cursor.getString(3) ?: "",
                        location = cursor.getString(4) ?: "",
                        startMs = cursor.getLong(5),
                        endMs = cursor.getLong(6),
                        allDay = cursor.getInt(7) == 1
                    ))
                }
            }
            
            mapOf(
                "success" to true,
                "count" to events.size,
                "events" to events.map { event ->
                    mapOf(
                        "id" to event.id,
                        "calendar_id" to event.calendarId,
                        "title" to event.title,
                        "description" to event.description,
                        "location" to event.location,
                        "start_ms" to event.startMs,
                        "end_ms" to event.endMs,
                        "all_day" to event.allDay
                    )
                }
            )
        } catch (e: Exception) {
            errorResponse(e.message ?: "Unknown error")
        }
    }
    
    suspend fun deleteEvent(call: ApplicationCall): Map<String, Any> {
        return try {
            val body = call.receive<Map<String, Any>>()
            val eventId = (body["event_id"] as? Number)?.toLong()
                ?: return errorResponse("event_id is required")
            
            val deleted = context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                null, null
            )
            
            if (deleted > 0) {
                mapOf(
                    "success" to true,
                    "message" to "Calendar event deleted successfully"
                )
            } else {
                errorResponse("Event not found or already deleted")
            }
        } catch (e: Exception) {
            errorResponse(e.message ?: "Unknown error")
        }
    }
    
    suspend fun updateEvent(call: ApplicationCall): Map<String, Any> {
        return try {
            val body = call.receive<Map<String, Any>>()
            val eventId = (body["event_id"] as? Number)?.toLong()
                ?: return errorResponse("event_id is required")
            
            val values = ContentValues()
            
            (body["title"] as? String)?.let { values.put(CalendarContract.Events.TITLE, it) }
            (body["description"] as? String)?.let { values.put(CalendarContract.Events.DESCRIPTION, it) }
            (body["location"] as? String)?.let { values.put(CalendarContract.Events.EVENT_LOCATION, it) }
            (body["start_ms"] as? Number)?.let { values.put(CalendarContract.Events.DTSTART, it.toLong()) }
            (body["end_ms"] as? Number)?.let { values.put(CalendarContract.Events.DTEND, it.toLong()) }
            (body["all_day"] as? Boolean)?.let { values.put(CalendarContract.Events.ALL_DAY, if (it) 1 else 0) }
            
            if (values.size() == 0) {
                return errorResponse("No update fields provided")
            }
            
            val updated = context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                values, null, null
            )
            
            if (updated > 0) {
                mapOf(
                    "success" to true,
                    "message" to "Calendar event updated successfully"
                )
            } else {
                errorResponse("Event not found")
            }
        } catch (e: Exception) {
            errorResponse(e.message ?: "Unknown error")
        }
    }
    
    private fun findWritableCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, selection, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }
    
    private fun errorResponse(message: String): Map<String, Any> {
        return mapOf(
            "success" to false,
            "error" to message
        )
    }
}
