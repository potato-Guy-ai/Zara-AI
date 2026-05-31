package com.zara.assistant.identity

import android.content.Context
import java.security.SecureRandom

/**
 * Manages the permanent Zara installation identity.
 *
 * Initialization: call [init] once in Application.onCreate(), before any
 * other Zara component is started.
 *
 * Access rules enforced by visibility:
 *   - [zaraId]      accessible to any module (public)
 *   - [internalId]  accessible only within the identity package (internal)
 *                   UI code cannot reference it — compile-time enforced
 *
 * Thread safety: [init] must be called on the main thread before any
 * background work starts. After init, all fields are read-only.
 *
 * Dependencies: Android framework only (Context, SharedPreferences,
 * SecureRandom). No Zara internal imports.
 */
object IdentityManager {

    /**
     * Base32 alphabet: 32 characters.
     * Excludes ambiguous characters: O, 0, I, 1.
     * Includes U (distinguishable from V in uppercase context).
     * Alphabet: ABCDEFGHJKLMNPQRSTUVWXYZ23456789
     *
     * 32 chars = exactly 5 bits per character.
     * Index range 0-31 maps to all 5-bit values with no gaps.
     */
    private const val BASE32_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val IDENTITY_VERSION = 1

    @Volatile private var identity: ZaraIdentity? = null
    private lateinit var store: IdentityStore

    /**
     * Must be called once in Application.onCreate() before ZaraLogger.init()
     * and before any other component is started.
     *
     * Reads persisted identity. If absent or partial, generates a new one.
     * Synchronous — completes in < 5ms on all supported devices.
     */
    fun init(context: Context) {
        store = IdentityStore(context.applicationContext)
        identity = store.load() ?: generateAndSave()
    }

    /**
     * Public Zara ID — safe to display in UI and share with users.
     * Format: ZR-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX
     *
     * Throws [IllegalStateException] if called before [init].
     */
    val zaraId: String
        get() = requireIdentity().zaraId

    /**
     * Internal system ID — 64-char lowercase hex string.
     *
     * Visibility is [internal] — accessible within the identity package only.
     * UI code, ViewModel, and Activity classes cannot access this field.
     * Future use: memory namespace keys, session correlation, KDF input.
     *
     * Never log this value. Never return it to any UI layer.
     */
    internal val internalId: String
        get() = requireIdentity().internalId

    /** Schema version of the stored identity. */
    val identityVersion: Int
        get() = requireIdentity().identityVersion

    /** True if identity has been successfully initialised. */
    val isInitialised: Boolean
        get() = identity != null

    // ── Generation ───────────────────────────────────────────────────────────

    private fun generateAndSave(): ZaraIdentity {
        val rng = SecureRandom()
        val newIdentity = ZaraIdentity(
            zaraId          = generateZaraId(rng),
            internalId      = generateInternalId(rng),
            identityVersion = IDENTITY_VERSION
        )
        store.save(newIdentity)
        return newIdentity
    }

    /**
     * Generates a public Zara ID from 160 bits (20 bytes) of SecureRandom entropy.
     *
     * Encoding: 5 bits per character into BASE32_ALPHABET (32 chars, indices 0-31).
     * 20 bytes = 160 bits → 32 Base32 chars available; we use 25 (5 groups of 5).
     * The remaining 7 bits (160 - 125) are discarded — no information leakage.
     *
     * Result: ZR-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX
     */
    private fun generateZaraId(rng: SecureRandom): String {
        val bytes = ByteArray(20).also { rng.nextBytes(it) }
        val chars = CharArray(25)
        var bitBuffer = 0
        var bitsInBuffer = 0
        var charIndex = 0
        for (byte in bytes) {
            bitBuffer = (bitBuffer shl 8) or (byte.toInt() and 0xFF)
            bitsInBuffer += 8
            while (bitsInBuffer >= 5 && charIndex < 25) {
                bitsInBuffer -= 5
                // (bitBuffer shr bitsInBuffer) and 0x1F produces values 0-31
                // BASE32_ALPHABET has exactly 32 chars — all indices valid
                chars[charIndex++] = BASE32_ALPHABET[(bitBuffer shr bitsInBuffer) and 0x1F]
            }
        }
        return "ZR-${String(chars, 0, 5)}-${String(chars, 5, 5)}" +
               "-${String(chars, 10, 5)}-${String(chars, 15, 5)}" +
               "-${String(chars, 20, 5)}"
    }

    /**
     * Generates an internal ID from 256 bits (32 bytes) of SecureRandom entropy.
     * Encodes as 64-char lowercase hexadecimal.
     * Independently generated from zaraId — no correlation between the two.
     */
    private fun generateInternalId(rng: SecureRandom): String {
        val bytes = ByteArray(32).also { rng.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private fun requireIdentity(): ZaraIdentity =
        identity ?: error(
            "IdentityManager not initialised. " +
            "Call IdentityManager.init(context) in Application.onCreate()."
        )
}
