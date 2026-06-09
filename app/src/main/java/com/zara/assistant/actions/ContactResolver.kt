package com.zara.assistant.actions

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.zara.assistant.core.ContactFuzzyMatcher
import com.zara.assistant.core.ContactNormalizer
import com.zara.assistant.permissions.PermissionManager
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Batch A1 — Fix 3 (Fuzzy fallback in ContactResolver)
 *
 * When the SQL LIKE query returns no results, loads all contacts and
 * delegates to ContactFuzzyMatcher for deterministic fuzzy matching.
 * No ML, no cloud.
 *
 * Resolution logic:
 * 1. If input is already a number (7+ digits), return as-is.
 * 2. SQL LIKE query on display name.
 * 3. If empty → fuzzy fallback: load all contacts, run ContactFuzzyMatcher.
 * 4. Return ranked results (ContactRankingEngine handles final sort upstream).
 */
class ContactResolver(private val context: Context) {

    data class ContactResult(
        val displayName: String,
        val number: String
    )

    suspend fun resolveNumber(input: String): String? {
        val cleaned = input.filter { it.isDigit() || it == '+' }
        if (cleaned.length >= 7) return cleaned

        if (!PermissionManager.has(context, android.Manifest.permission.READ_CONTACTS)) {
            ZaraLogger.d("ContactResolver: READ_CONTACTS not granted")
            return null
        }
        val results = resolveAll(input)
        return results.firstOrNull()?.number
    }

    suspend fun resolveAll(input: String): List<ContactResult> {
        val cleaned = input.filter { it.isDigit() || it == '+' }
        if (cleaned.length >= 7) return listOf(ContactResult(input, cleaned))
        if (!PermissionManager.has(context, android.Manifest.permission.READ_CONTACTS)) return emptyList()

        // Normalize input for SQL query — strip emoji/punct so DB query uses clean text
        val normalizedInput = ContactNormalizer.normalize(input)
        val results = queryContacts(normalizedInput)
        if (results.isNotEmpty()) return results

        // SQL LIKE returned nothing — try original raw input too (in case normalization over-stripped)
        val rawResults = if (normalizedInput != input.trim().lowercase()) queryContacts(input.trim()) else emptyList()
        if (rawResults.isNotEmpty()) return rawResults

        // Fuzzy fallback: load all contacts, run fuzzy matcher
        ZaraLogger.d("ContactResolver: no SQL results for '$normalizedInput', trying fuzzy fallback")
        return fuzzyFallback(normalizedInput)
    }

    private suspend fun fuzzyFallback(normalizedQuery: String): List<ContactResult> = withContext(Dispatchers.IO) {
        if (!PermissionManager.has(context, android.Manifest.permission.READ_CONTACTS)) return@withContext emptyList()
        val all = loadAllContacts()
        ZaraLogger.d("ContactResolver: fuzzy over ${all.size} contacts for '$normalizedQuery'")
        val fuzzy = ContactFuzzyMatcher.match(normalizedQuery, all)
        ZaraLogger.d("ContactResolver: fuzzy returned ${fuzzy.size} candidates")
        fuzzy
    }

    private suspend fun queryContacts(name: String): List<ContactResult> = withContext(Dispatchers.IO) {
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

        try {
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

    /**
     * Loads all contacts with phone numbers for fuzzy matching.
     * Called only when SQL LIKE returns empty — so rare path.
     */
    private suspend fun loadAllContacts(): List<ContactResult> = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
        )
        val sort = "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
        try {
            resolver.query(uri, projection, null, null, sort)?.use { cursor ->
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
            ZaraLogger.e("ContactResolver loadAll error: ${e.message}")
            emptyList()
        }
    }
}
