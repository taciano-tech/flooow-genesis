package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence

/**
 * Aggregates evidence confidence using a deterministic arithmetic mean.
 */
class DeterministicEvidenceAggregator : EvidenceAggregator {

    override fun aggregate(
        evidenceSet: EvidenceSet
    ): AggregatedEvidence {
        val averageConfidence =
            evidenceSet.evidences
                .sortedBy { it.id.value }
                .map { it.confidence.value }
                .average()

        return AggregatedEvidence(
            evidenceSet = evidenceSet,
            confidence = Confidence(averageConfidence)
        )
    }
}
