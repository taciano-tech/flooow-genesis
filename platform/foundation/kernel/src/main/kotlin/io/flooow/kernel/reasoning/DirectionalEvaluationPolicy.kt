package io.flooow.kernel.reasoning

data class DirectionalOutcome(
    val direction: JudgmentDirection,
    val reason: JudgmentReason
)

data class DirectionalPolicyDecision(
    val direction: JudgmentDirection,
    val reason: JudgmentReason,
    val measures: List<PolicyMeasure>
) {
    init {
        require(measures.map { it.name }.distinct().size == measures.size) {
            "Policy measure names must be unique"
        }
    }
}

interface DirectionalEvaluationPolicy {
    val id: String
    val supportedOutcomes: Set<DirectionalOutcome>

    fun evaluate(
        hypothesis: Hypothesis,
        relationships: List<EvaluatedRelationship>
    ): DirectionalPolicyDecision
}
