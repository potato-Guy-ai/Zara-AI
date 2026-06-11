package com.zara.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.zara.assistant.core.ContactDeduplicationEngine
import com.zara.assistant.permissions.PermissionManager
import com.zara.assistant.utils.ZaraLogger

/**
 * Runtime dedup fix: ContactDeduplicationEngine.deduplicate() applied to resolveAll()
 * before size check. Prevents same contact synced to multiple accounts (Google, WhatsApp,
 * device) from generating a false AMBIGUOUS sentinel.
 */
class CallActions(private val context: Context) {

    companion object {
        const val AMBIGUOUS_PREFIX = "__AMBIGUOUS__|"
    }

    private val contactResolver = ContactResolver(context)

    suspend fun call(contact: String): String {
        val results = ContactDeduplicationEngine.deduplicate(contactResolver.resolveAll(contact))
        return when {
            results.isEmpty() -> openDiallerSearch(contact)
            results.size == 1 -> dialNumber(results[0].number, results[0].displayName)
            else -> {
                val parts = results.flatMap { listOf(it.displayName, it.number) }.joinToString("|")
                "$AMBIGUOUS_PREFIX$parts"
            }
        }
    }

    fun dialNumber(number: String, displayName: String): String {
        if (!PermissionManager.has(context, android.Manifest.permission.CALL_PHONE)) {
            return "I need the Call permission to place calls."
        }
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(number)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Calling $displayName."
        } catch (e: Exception) {
            ZaraLogger.e("dialNumber error: ${e.message}")
            "Couldn\'t place the call."
        }
    }

    private fun openDiallerSearch(contact: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "I couldn\'t find \'$contact\' in contacts. Opening dialler."
        } catch (e: Exception) {
            ZaraLogger.e("openDiallerSearch error: ${e.message}")
            "Couldn\'t open the dialler."
        }
    }

    fun answerCall(): String {
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            tm?.acceptRingingCall()
            "Answering call."
        } catch (e: Exception) {
            ZaraLogger.e("answerCall error: ${e.message}")
            "Couldn\'t answer the call."
        }
    }

    fun endCall(): String {
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            tm?.endCall()
            "Call ended."
        } catch (e: Exception) {
            ZaraLogger.e("endCall error: ${e.message}")
            "Couldn\'t end the call."
        }
    }
}
