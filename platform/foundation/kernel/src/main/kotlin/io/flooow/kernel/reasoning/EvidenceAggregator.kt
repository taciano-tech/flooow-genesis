package io.flooow.kernel.reasoning

/**
 * Aggregates an evidence set into a single deterministic view.
 *
 * Implementations may use deterministic rules, weighted averages,
 * probabilistic models or future AI-based aggregation.
 */
interface EvidenceAggregator {

    fun aggregate(
        evidenceSet: EvidenceSet
    ): AggregatedEvidence
}
