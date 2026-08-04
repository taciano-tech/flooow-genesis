package io.flooow.kernel.reasoning

class StrictConflictPolicy : DirectionalEvaluationPolicy {
    override val id: String = "strict-conflict-v1"

    override val supportedOutcomes: Set<DirectionalOutcome> = setOf(
        DirectionalOutcome(
            JudgmentDirection.SUPPORTED,
            JudgmentReason.UNANIMOUS_SUPPORT
        ),
        DirectionalOutcome(
            JudgmentDirection.CONTRADICTED,
            JudgmentReason.UNANIMOUS_CONTRADICTION
        ),
        DirectionalOutcome(
            JudgmentDirection.UNRESOLVED,
            JudgmentReason.CONFLICT
        )
    )

    override fun evaluate(
        hypothesis: Hypothesis,
        relationships: List<EvaluatedRelationship>
    ): DirectionalPolicyDecision {
        val directions = relationships.map { it.relationship.direction }.toSet()
        val outcome = when (directions) {
            setOf(RelationshipDirection.SUPPORTS) -> DirectionalOutcome(
                JudgmentDirection.SUPPORTED,
                JudgmentReason.UNANIMOUS_SUPPORT
            )
            setOf(RelationshipDirection.CONTRADICTS) -> DirectionalOutcome(
                JudgmentDirection.CONTRADICTED,
                JudgmentReason.UNANIMOUS_CONTRADICTION
            )
            else -> DirectionalOutcome(
                JudgmentDirection.UNRESOLVED,
                JudgmentReason.CONFLICT
            )
        }
        return DirectionalPolicyDecision(
            outcome.direction,
            outcome.reason,
            emptyList()
        )
    }
}
