package com.zara.assistant.actions

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.zara.assistant.permissions.PermissionManager
import com.zara.assistant.utils.ZaraLogger

class CallActions(private val context: Context) {

    /**
     * C2 fix: resolve contact name to phone number before dialling.
     * If the input is already a digit string, use it directly.
     * If it is a name, look it up in the device contacts database.
     * If no match is found, open the dialler with a search query instead
     * of placing a call to a malformed URI.
     */
    fun call(contact: String): String {
        val number = resolveToNumber(contact)
        return if (number != null) {
            dialNumber(number, contact)
        } else {
            // Contact not found — open dialler pre-filled so user can confirm
            openDiallerSearch(contact)
        }
    }

    private fun dialNumber(number: String, displayName: String): String {
        if (!PermissionManager.has(context, android.Manifest.permission.CALL_PHONE)) {
            return "I need the Call permission to place calls."
        }
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(number)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Calling $displayName"
        } catch (e: Exception) {
            ZaraLogger.e("dialNumber error: ${e.message}")
            "Couldn't place the call."
        }
    }

    private fun openDiallerSearch(contact: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:")
                // Pre-populate search — user confirms before call is placed
                putExtra("android.intent.extra.PHONE_NUMBER", contact)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Couldn't find $contact in contacts. Opening dialler."
        } catch (e: Exception) {
            ZaraLogger.e("openDiallerSearch error: ${e.message}")
            "Couldn't open the dialler."
        }
    }

    /**
     * Returns a phone number string if [input] is already numeric,
     * or looks up the best matching contact by name.
     * Returns null if no contact is found.
     */
    private fun resolveToNumber(input: String): String? {
        val cleaned = input.filter { it.isDigit() || it == '+' }
        if (cleaned.length >= 7) return cleaned   // already a number

        if (!PermissionManager.has(context, android.Manifest.permission.READ_CONTACTS)) {
            return null
        }
        return queryContactNumber(input.trim())
    }

    private fun queryContactNumber(name: String): String? {
        val resolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
        )
        // Case-insensitive LIKE match
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$name%")
        val sort = "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"

        return resolver.query(uri, projection, selection, args, sort)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }

    fun answerCall(): String {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE)
                as? android.telecom.TelecomManager
            telecomManager?.acceptRingingCall()
            "Answering call"
        } catch (e: Exception) {
            ZaraLogger.e("answerCall error: ${e.message}")
            "Couldn't answer the call."
        }
    }

    fun endCall(): String {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE)
                as? android.telecom.TelecomManager
            telecomManager?.endCall()
            "Call ended"
        } catch (e: Exception) {
            ZaraLogger.e("endCall error: ${e.message}")
            "Couldn't end the call."
        }
    }
}
