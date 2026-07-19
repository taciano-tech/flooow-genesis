package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Timestamp

data class EvaluationResult(
    val judgment: Judgment,
    val evaluatedEvidence: EvidenceSet,
    val evaluatedAt: Timestamp
)
