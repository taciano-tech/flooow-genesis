package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import java.time.Clock

/**
 * Produces deterministic judgments from aggregated evidence
 * and an explicit confidence policy.
 */
class DeterministicHypothesisEvaluator(
    private val evidenceAggregator: EvidenceAggregator,
    private val confidencePolicy: ConfidencePolicy,
    private val clock: Clock = Clock.systemUTC()
) : HypothesisEvaluator {

    override fun evaluate(
        hypothesis: Hypothesis,
        evidenceSet: EvidenceSet
    ): Judgment {
        val aggregatedEvidence =
            evidenceAggregator.aggregate(evidenceSet)

        val confidence =
            confidencePolicy.determine(
                hypothesis = hypothesis,
                aggregatedEvidence = aggregatedEvidence
            )

        return Judgment(
            id = Identifier("judgment-${hypothesis.id}"),
            hypothesisId = hypothesis.id,
            conclusion = "Evidence supports the hypothesis.",
            confidence = confidence,
            createdAt = Timestamp.now(clock)
        )
    }
}
