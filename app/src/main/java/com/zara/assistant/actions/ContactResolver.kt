package com.zara.assistant.actions

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.zara.assistant.permissions.PermissionManager
import com.zara.assistant.utils.ZaraLogger

/**
 * Resolves a contact name or number string to a phone number.
 * Used by both CallActions and AppActions (SMS/WhatsApp).
 *
 * Resolution logic:
 * 1. If input is already a number (7+ digits), return as-is.
 * 2. Query ContactsContract with LIKE match on display name.
 * 3. Single match  → return number directly.
 * 4. Multiple matches → return first primary number (future: surface to user).
 * 5. No match → return null.
 */
class ContactResolver(private val context: Context) {

    data class ContactResult(
        val displayName: String,
        val number: String
    )

    /** Returns a phone number or null. */
    fun resolveNumber(input: String): String? {
        val cleaned = input.filter { it.isDigit() || it == '+' }
        if (cleaned.length >= 7) return cleaned

        if (!PermissionManager.has(context, android.Manifest.permission.READ_CONTACTS)) {
            ZaraLogger.d("ContactResolver: READ_CONTACTS not granted")
            return null
        }
        val results = queryContacts(input.trim())
        return results.firstOrNull()?.number
    }

    /**
     * Returns all matching contacts for disambiguation.
     * Callers can use this for multi-match UX in future phases.
     */
    fun resolveAll(input: String): List<ContactResult> {
        val cleaned = input.filter { it.isDigit() || it == '+' }
        if (cleaned.length >= 7) return listOf(ContactResult(input, cleaned))
        if (!PermissionManager.has(context, android.Manifest.permission.READ_CONTACTS)) return emptyList()
        return queryContacts(input.trim())
    }

    private fun queryContacts(name: String): List<ContactResult> {
        val resolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$name%")
        val sort = "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"

        return try {
            resolver.query(uri, projection, selection, args, sort)?.use { cursor ->
                val results = mutableListOf<ContactResult>()
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx  = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) else continue
                    val number      = if (numIdx >= 0) cursor.getString(numIdx) else continue
                    if (displayName != null && number != null) {
                        results.add(ContactResult(displayName, number))
                    }
                }
                results
            } ?: emptyList()
        } catch (e: Exception) {
            ZaraLogger.e("ContactResolver query error: ${e.message}")
            emptyList()
        }
    }
}
