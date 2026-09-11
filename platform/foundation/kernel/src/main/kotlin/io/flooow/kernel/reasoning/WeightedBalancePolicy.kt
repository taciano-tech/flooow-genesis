package io.flooow.kernel.reasoning

import java.math.BigDecimal

class WeightedBalancePolicy : DirectionalEvaluationPolicy {
    override val id: String = "weighted-balance-v1"

    override val supportedOutcomes: Set<DirectionalOutcome> = setOf(
        DirectionalOutcome(
            JudgmentDirection.UNRESOLVED,
            JudgmentReason.INSUFFICIENT_WEIGHT
        ),
        DirectionalOutcome(
            JudgmentDirection.SUPPORTED,
            JudgmentReason.POSITIVE_BALANCE
        ),
        DirectionalOutcome(
            JudgmentDirection.CONTRADICTED,
            JudgmentReason.NEGATIVE_BALANCE
        ),
        DirectionalOutcome(
            JudgmentDirection.UNRESOLVED,
            JudgmentReason.BALANCED_CONFLICT
        )
    )

    override fun evaluate(
        hypothesis: Hypothesis,
        relationships: List<EvaluatedRelationship>
    ): DirectionalPolicyDecision {
        val contributions = relationships.sortedBy {
            it.relationship.evidenceId.value
        }.map { relationship ->
            relationship to BigDecimal.valueOf(relationship.confidence.value)
        }
        val support = contributions
            .filter { it.first.relationship.direction == RelationshipDirection.SUPPORTS }
            .fold(BigDecimal.ZERO) { total, contribution ->
                total + contribution.second
            }
        val contradict = contributions
            .filter {
                it.first.relationship.direction == RelationshipDirection.CONTRADICTS
            }
            .fold(BigDecimal.ZERO) { total, contribution ->
                total + contribution.second
            }
        val outcome = when {
            support.signum() == 0 && contradict.signum() == 0 ->
                DirectionalOutcome(
                    JudgmentDirection.UNRESOLVED,
                    JudgmentReason.INSUFFICIENT_WEIGHT
                )
            support > contradict -> DirectionalOutcome(
                JudgmentDirection.SUPPORTED,
                JudgmentReason.POSITIVE_BALANCE
            )
            contradict > support -> DirectionalOutcome(
                JudgmentDirection.CONTRADICTED,
                JudgmentReason.NEGATIVE_BALANCE
            )
            else -> DirectionalOutcome(
                JudgmentDirection.UNRESOLVED,
                JudgmentReason.BALANCED_CONFLICT
            )
        }
        return DirectionalPolicyDecision(
            outcome.direction,
            outcome.reason,
            listOf(
                PolicyMeasure(
                    "contradict-total",
                    contradict,
                    "Exact sum of confidence magnitudes for CONTRADICTS relationships"
                ),
                PolicyMeasure(
                    "support-total",
                    support,
                    "Exact sum of confidence magnitudes for SUPPORTS relationships"
                )
            )
        )
    }
}
