package com.zara.assistant.identity

/**
 * Immutable identity record for a Zara installation.
 *
 * [zaraId]    — Public, user-facing ID. Safe to display and share.
 *               Format: ZR-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX
 *               Alphabet: ABCDEFGHJKLMNPQRSTVWXYZ23456789 (no O, 0, I, 1)
 *
 * [internalId] — Private system ID. 256-bit entropy, hex-encoded (64 chars).
 *                MUST NOT be returned to UI layers.
 *                MUST NOT be logged.
 *                MUST NOT be sent to cloud.
 *
 * [identityVersion] — Schema version. Initial value: 1.
 *                     Used by IdentityManager to detect and run migrations.
 */
data class ZaraIdentity internal constructor(
    val zaraId: String,
    internal val internalId: String,
    val identityVersion: Int
) {
    init {
        require(zaraId.matches(ZARA_ID_REGEX)) {
            "Invalid zaraId format"
        }
        require(internalId.matches(INTERNAL_ID_REGEX)) {
            "Invalid internalId format"
        }
        require(identityVersion >= 1) {
            "identityVersion must be >= 1"
        }
    }

    companion object {
        /** Regex validating ZR-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX format. */
        val ZARA_ID_REGEX = Regex("^ZR-[ABCDEFGHJKLMNPQRSTVWXYZ23456789]{5}-[ABCDEFGHJKLMNPQRSTVWXYZ23456789]{5}-[ABCDEFGHJKLMNPQRSTVWXYZ23456789]{5}-[ABCDEFGHJKLMNPQRSTVWXYZ23456789]{5}-[ABCDEFGHJKLMNPQRSTVWXYZ23456789]{5}$")
        /** Regex validating 64 lowercase hex characters. */
        val INTERNAL_ID_REGEX = Regex("^[0-9a-f]{64}$")
    }

    /**
     * Returns a safe string for debug logging.
     * internalId is never included.
     */
    override fun toString(): String = "ZaraIdentity(zaraId=$zaraId, version=$identityVersion)"
}
