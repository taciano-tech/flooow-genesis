package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence

/**
 * Represents the aggregated interpretation of an evidence set.
 */
data class AggregatedEvidence(
    val evidenceSet: EvidenceSet,
    val confidence: Confidence
)
