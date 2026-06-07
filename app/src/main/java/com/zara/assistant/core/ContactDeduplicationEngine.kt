package com.zara.assistant.core

import com.zara.assistant.actions.ContactResolver

/**
 * Layer 5 Hardening — Contact Deduplication Engine.
 * Deduplicates candidates by normalized phone and normalized name.
 * If deduplication leaves one candidate → auto-resolve (no clarification).
 */
object ContactDeduplicationEngine {

    fun deduplicate(candidates: List<ContactResolver.ContactResult>): List<ContactResolver.ContactResult> {
        val seenPhones = mutableSetOf<String>()
        val seenNames  = mutableSetOf<String>()
        val result     = mutableListOf<ContactResolver.ContactResult>()
        for (c in candidates) {
            val normPhone = ContactNormalizer.normalizePhone(c.number)
            val normName  = ContactNormalizer.normalize(c.displayName)
            // Deduplicate by phone first, then by name
            if (normPhone.isNotBlank() && seenPhones.contains(normPhone)) continue
            if (seenNames.contains(normName)) continue
            if (normPhone.isNotBlank()) seenPhones.add(normPhone)
            seenNames.add(normName)
            result.add(c)
        }
        return result
    }
}
