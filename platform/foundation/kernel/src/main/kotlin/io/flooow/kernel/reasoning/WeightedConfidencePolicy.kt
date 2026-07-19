package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import kotlin.math.abs

/**
 * Determines judgment confidence using a weighted combination
 * of hypothesis confidence and aggregated evidence confidence.
 */
class WeightedConfidencePolicy(
    private val hypothesisWeight: Double,
    private val aggregatedEvidenceWeight: Double
) : ConfidencePolicy {

    init {
        require(hypothesisWeight >= 0.0) {
            "Hypothesis weight must be non-negative"
        }

        require(aggregatedEvidenceWeight >= 0.0) {
            "Aggregated evidence weight must be non-negative"
        }

        require(
            abs(
                hypothesisWeight +
                    aggregatedEvidenceWeight -
                    1.0
            ) < 1e-9
        ) {
            "Weights must sum to 1.0"
        }
    }

    override fun determine(
        hypothesis: Hypothesis,
        aggregatedEvidence: AggregatedEvidence
    ): Confidence =
        Confidence(
            hypothesis.confidence.value * hypothesisWeight +
                aggregatedEvidence.confidence.value * aggregatedEvidenceWeight
        )
}
