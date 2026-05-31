package com.zara.assistant.identity

import android.content.Context
import android.content.SharedPreferences

/**
 * Storage abstraction for ZaraIdentity.
 *
 * Uses a dedicated SharedPreferences file ("zara_identity") completely
 * separate from MemoryManager's DataStore ("zara_memory").
 *
 * Isolation guarantees:
 *   - MemoryManager.clearAll() cannot affect identity storage.
 *   - Identity storage cannot affect MemoryManager data.
 *   - App updates do not clear SharedPreferences (app-data scoped).
 *   - android:allowBackup="false" in manifest prevents ADB backup exposure.
 *
 * Storage upgrade path: replace SharedPreferences calls here in Phase 5
 * without touching IdentityManager or ZaraIdentity.
 */
internal class IdentityStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        FILE_NAME, Context.MODE_PRIVATE
    )

    /**
     * Reads stored identity.
     * Returns null if any required field is absent or malformed.
     * Partial state is treated as no identity — triggers full regeneration.
     */
    fun load(): ZaraIdentity? {
        val zaraId      = prefs.getString(KEY_ZARA_ID, null)      ?: return null
        val internalId  = prefs.getString(KEY_INTERNAL_ID, null)  ?: return null
        val version     = prefs.getInt(KEY_VERSION, 0)

        if (version < 1) return null
        if (!zaraId.matches(ZaraIdentity.ZARA_ID_REGEX))     return null
        if (!internalId.matches(ZaraIdentity.INTERNAL_ID_REGEX)) return null

        return ZaraIdentity(
            zaraId          = zaraId,
            internalId      = internalId,
            identityVersion = version
        )
    }

    /**
     * Persists a complete identity atomically.
     * Uses commit() (synchronous) to guarantee the write completes before
     * the application continues — prevents partial state on process death.
     */
    fun save(identity: ZaraIdentity) {
        prefs.edit()
            .putString(KEY_ZARA_ID,     identity.zaraId)
            .putString(KEY_INTERNAL_ID, identity.internalId)
            .putInt(KEY_VERSION,        identity.identityVersion)
            .commit()  // synchronous — intentional
    }

    /** Wipes stored identity. Used only for testing and user-initiated reset. */
    internal fun clear() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val FILE_NAME       = "zara_identity"
        private const val KEY_ZARA_ID     = "key_zara_id"
        private const val KEY_INTERNAL_ID = "key_internal_id"
        private const val KEY_VERSION     = "key_identity_version"
    }
}
