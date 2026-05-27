package com.zara.assistant.service.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.zara.assistant.utils.ZaraLogger

class MessagingAutomation(private val context: Context) {

    fun sendSMS(contact: String, message: String) {
        val number = resolveNumber(contact) ?: contact
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            ZaraLogger.d("SMS sent to $number")
        } catch (e: Exception) {
            ZaraLogger.e("SMS failed: ${e.message}")
        }
    }

    fun openWhatsApp(contact: String? = null) {
        val intent = if (contact != null) {
            val number = resolveNumber(contact) ?: contact
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number"))
        } else {
            context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://whatsapp.com"))
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun readRecentSMS(): List<Pair<String, String>> {
        val messages = mutableListOf<Pair<String, String>>()
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body"),
            null, null,
            "date DESC LIMIT 5"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val from = it.getString(0) ?: "Unknown"
                val body = it.getString(1) ?: ""
                messages.add(from to body)
            }
        }
        return messages
    }

    private fun resolveNumber(name: String): String? {
        val cursor = context.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )
        cursor?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }
}
