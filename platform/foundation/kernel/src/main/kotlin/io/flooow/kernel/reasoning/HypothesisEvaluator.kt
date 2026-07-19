package io.flooow.kernel.reasoning

interface HypothesisEvaluator {

    fun evaluate(
        hypothesis: Hypothesis,
        evidenceSet: EvidenceSet
    ): Judgment
}
