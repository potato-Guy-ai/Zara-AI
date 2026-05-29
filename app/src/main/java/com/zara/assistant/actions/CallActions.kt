package com.zara.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.zara.assistant.utils.ZaraLogger

class CallActions(private val context: Context) {

    fun call(contact: String): String {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$contact")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Calling $contact"
        } catch (e: Exception) {
            ZaraLogger.e("Call error: ${e.message}")
            "Couldn't place the call. Check CALL_PHONE permission."
        }
    }

    fun answerCall(): String {
        // Requires ANSWER_PHONE_CALLS permission (API 26+)
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE)
                as? android.telecom.TelecomManager
            telecomManager?.acceptRingingCall()
            "Answering call"
        } catch (e: Exception) { "Couldn't answer the call" }
    }

    fun endCall(): String {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE)
                as? android.telecom.TelecomManager
            telecomManager?.endCall()
            "Call ended"
        } catch (e: Exception) { "Couldn't end the call" }
    }
}
