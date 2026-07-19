package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence

/**
 * Determines judgment confidence directly from aggregated evidence.
 */
class AggregatedEvidenceConfidencePolicy : ConfidencePolicy {

    override fun determine(
        hypothesis: Hypothesis,
        aggregatedEvidence: AggregatedEvidence
    ): Confidence =
        aggregatedEvidence.confidence
}
