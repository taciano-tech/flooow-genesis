package io.flooow.kernel.reasoning

data class DecisionContext(
    val hypothesis: Hypothesis,
    val evidenceSet: EvidenceSet,
    val judgment: Judgment
) {
    init {
        require(judgment.hypothesisId == hypothesis.id) {
            "Judgment must reference the hypothesis in the decision context"
        }
    }
}
