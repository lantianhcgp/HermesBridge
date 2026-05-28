package com.hermes.bridge

import android.content.Context
import android.provider.ContactsContract
import com.google.gson.Gson
import io.ktor.server.application.*
import io.ktor.server.request.*

class ContactsTool(private val context: Context) {

    private val gson = Gson()

    /**
     * 搜索联系人
     * GET /api/contacts/search?q=张三
     * GET /api/contacts/search?limit=20
     */
    suspend fun searchContacts(call: ApplicationCall): Map<String, Any> {
        return try {
            val query = call.request.queryParameters["q"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

            val contacts = mutableListOf<Map<String, Any>>()

            // 搜索姓名
            val selection = if (!query.isNullOrBlank()) {
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            } else null
            val selectionArgs = if (!query.isNullOrBlank()) arrayOf("%$query%") else null

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            )

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val contactId = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val number = cursor.getString(numberCol) ?: continue

                    // 去重：同一联系人可能有多条号码
                    val key = "$contactId|$number"
                    if (contacts.any { it["contact_id"] == contactId && it["number"] == number }) continue

                    val phoneType = cursor.getInt(typeCol)
                    val phoneTypeStr = when (phoneType) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
                        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "fax"
                        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "main"
                        else -> cursor.getString(labelCol) ?: "other"
                    }

                    contacts.add(mapOf(
                        "contact_id" to contactId,
                        "name" to name,
                        "number" to number,
                        "type" to phoneTypeStr
                    ))
                    count++
                }
            }

            mapOf(
                "success" to true,
                "count" to contacts.size,
                "contacts" to contacts
            )
        } catch (e: SecurityException) {
            errorResponse("Contacts permission not granted")
        } catch (e: Exception) {
            errorResponse(e.message ?: "Failed to search contacts")
        }
    }

    /**
     * 获取联系人详情
     * GET /api/contacts/{id}
     */
    suspend fun getContactDetail(call: ApplicationCall, contactId: Long): Map<String, Any> {
        return try {
            val phones = mutableListOf<String>()
            val emails = mutableListOf<String>()

            // 获取所有电话号码
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cursor ->
                val numCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    cursor.getString(numCol)?.let { phones.add(it) }
                }
            }

            // 获取所有邮箱
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cursor ->
                val emailCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (cursor.moveToNext()) {
                    cursor.getString(emailCol)?.let { emails.add(it) }
                }
            }

            // 获取显示名
            var name = ""
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: ""
                }
            }

            if (name.isEmpty()) {
                return errorResponse("Contact not found")
            }

            mapOf(
                "success" to true,
                "contact_id" to contactId,
                "name" to name,
                "phones" to phones,
                "emails" to emails
            )
        } catch (e: SecurityException) {
            errorResponse("Contacts permission not granted")
        } catch (e: Exception) {
            errorResponse(e.message ?: "Failed to get contact")
        }
    }

    private fun errorResponse(message: String): Map<String, Any> {
        return mapOf("success" to false, "error" to message)
    }
}
