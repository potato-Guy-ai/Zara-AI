package com.zara.assistant.service.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import com.zara.assistant.utils.ZaraLogger

class PhoneAutomation(private val context: Context) {

    fun call(contact: String) {
        val number = resolveNumber(contact) ?: contact
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
            ZaraLogger.d("Calling: $number")
        } catch (e: Exception) {
            ZaraLogger.e("Call failed: ${e.message}")
        }
    }

    fun answerCall() {
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.acceptRingingCall()
        } catch (e: Exception) {
            ZaraLogger.e("Answer call error: ${e.message}")
        }
    }

    fun endCall() {
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.endCall()
        } catch (e: Exception) {
            ZaraLogger.e("End call error: ${e.message}")
        }
    }

    private fun resolveNumber(name: String): String? {
        val cursor = context.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }
}
