package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import java.time.Clock

/**
 * Produces deterministic judgments using only the supplied hypothesis
 * and evidence set.
 */
class DeterministicHypothesisEvaluator(
    private val clock: Clock = Clock.systemUTC()
) : HypothesisEvaluator {

    override fun evaluate(
        hypothesis: Hypothesis,
        evidenceSet: EvidenceSet
    ): Judgment =
        Judgment(
            id = Identifier("judgment-${hypothesis.id}"),
            hypothesisId = hypothesis.id,
            conclusion = "Evidence supports the hypothesis.",
            confidence = hypothesis.confidence,
            createdAt = Timestamp.now(clock)
        )
}
