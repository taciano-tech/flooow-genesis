package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence

/**
 * Determines the final confidence assigned to a judgment.
 *
 * Implementations may consider the hypothesis confidence,
 * aggregated evidence confidence or other deterministic rules.
 */
interface ConfidencePolicy {

    fun determine(
        hypothesis: Hypothesis,
        aggregatedEvidence: AggregatedEvidence
    ): Confidence
}
