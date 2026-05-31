package com.zara.assistant.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.concurrent.CopyOnWriteArrayList

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zara_memory")

/**
 * Local persistent memory using DataStore (plaintext on-disk storage).
 *
 * C8 fix: removed false "Encrypted" claim from documentation.
 * DataStore does NOT encrypt data. Encryption (via EncryptedSharedPreferences or
 * a custom DataStore serializer with Tink) is a Phase 5 hardening task.
 * All data stored here must be treated as readable by any privileged process.
 *
 * M7 fix: sessionContext uses CopyOnWriteArrayList for thread-safe reads
 * across multiple coroutine dispatchers (Default + Main).
 */
class MemoryManager(private val context: Context) {

    // ── Persistent storage (DataStore, plaintext) ────────────────────────────

    suspend fun set(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun get(key: String): String? =
        context.dataStore.data
            .map { it[stringPreferencesKey(key)] }
            .firstOrNull()

    suspend fun delete(key: String) {
        context.dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    // ── Session context (in-memory only, cleared on process death) ───────────
    // CopyOnWriteArrayList: safe for concurrent reads from multiple dispatchers.
    // Writes are infrequent (one per utterance) so COW cost is acceptable.
    private val sessionContext = CopyOnWriteArrayList<String>()
    private val maxSessionEntries = 10

    fun addContext(text: String) {
        if (sessionContext.size >= maxSessionEntries) {
            sessionContext.removeAt(0)
        }
        sessionContext.add(text)
    }

    fun getRecentContext(): List<String> = sessionContext.toList()

    fun clearSession() = sessionContext.clear()
}
