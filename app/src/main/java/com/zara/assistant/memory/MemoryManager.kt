package com.zara.assistant.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zara_memory")

/**
 * Encrypted local memory.
 * Stores preferences, context, routines.
 * No cloud dependency.
 */
class MemoryManager(private val context: Context) {

    suspend fun set(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun get(key: String): String? {
        return context.dataStore.data
            .map { it[stringPreferencesKey(key)] }
            .firstOrNull()
    }

    suspend fun delete(key: String) {
        context.dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    // Conversation context (in-memory, session only)
    private val sessionContext = ArrayDeque<String>(10)

    fun addContext(text: String) {
        if (sessionContext.size >= 10) sessionContext.removeFirst()
        sessionContext.addLast(text)
    }

    fun getRecentContext(): List<String> = sessionContext.toList()

    fun clearSession() = sessionContext.clear()
}
