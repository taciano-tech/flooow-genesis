package io.flooow.kernel.reasoning

data class EvaluationRequest(
    val hypothesis: Hypothesis,
    val evidenceSet: EvidenceSet
)
