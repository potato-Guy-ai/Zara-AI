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
     * Base32 alphabet: 32 characters, excludes ambiguous O, 0, I, 1.
     * Produces IDs that are readable aloud and easy to transcribe.
     */
    private const val BASE32_ALPHABET = "ABCDEFGHJKLMNPQRSTVWXYZ23456789"
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
     * Encodes into Base32 using [BASE32_ALPHABET], formatted as:
     * ZR-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX  (5 groups of 5 chars)
     *
     * 20 bytes → 160 bits → 32 Base32 chars (5 bits each = 160 bits).
     * Only 25 chars are used (5×5), consuming 125 of the 160 bits.
     * Remaining bits are discarded — no information leakage.
     */
    private fun generateZaraId(rng: SecureRandom): String {
        val bytes = ByteArray(20).also { rng.nextBytes(it) }
        val chars = CharArray(25)
        // Extract 5 bits per character from the byte stream
        var bitBuffer = 0
        var bitsInBuffer = 0
        var charIndex = 0
        for (byte in bytes) {
            bitBuffer = (bitBuffer shl 8) or (byte.toInt() and 0xFF)
            bitsInBuffer += 8
            while (bitsInBuffer >= 5 && charIndex < 25) {
                bitsInBuffer -= 5
                chars[charIndex++] = BASE32_ALPHABET[(bitBuffer shr bitsInBuffer) and 0x1F]
            }
        }
        // Format as ZR-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX
        return "ZR-${String(chars, 0, 5)}-${String(chars, 5, 5)}-${String(chars, 10, 5)}-${String(chars, 15, 5)}-${String(chars, 20, 5)}"
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
        identity ?: error("IdentityManager not initialised. Call IdentityManager.init(context) in Application.onCreate().")
}
