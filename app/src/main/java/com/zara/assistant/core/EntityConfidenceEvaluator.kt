package com.zara.assistant.core

/**
 * Layer 5 Hardening — Entity Confidence Levels.
 */
enum class EntityConfidenceLevel { HIGH, MEDIUM, LOW }

object EntityConfidenceEvaluator {

    fun evaluate(candidateCount: Int, topScore: Int): EntityConfidenceLevel = when {
        candidateCount == 1 && topScore >= 80 -> EntityConfidenceLevel.HIGH
        candidateCount == 1                   -> EntityConfidenceLevel.MEDIUM
        candidateCount in 2..4 && topScore >= 80 -> EntityConfidenceLevel.MEDIUM
        candidateCount > 4                    -> EntityConfidenceLevel.LOW
        else                                  -> EntityConfidenceLevel.LOW
    }
}
