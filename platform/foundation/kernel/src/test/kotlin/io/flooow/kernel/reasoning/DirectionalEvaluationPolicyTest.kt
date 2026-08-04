package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DirectionalEvaluationPolicyTest {
    private val hypothesis = Hypothesis(
        Identifier("hypothesis-1"),
        "A testable statement",
        Confidence(0.5),
        Timestamp.parse("2026-08-04T12:00:00Z")
    )

    @Test
    fun `strict policy reproduces C1 through C6 without confidence polarity`() {
        val policy = StrictConflictPolicy()
        val expected = listOf(
            JudgmentDirection.SUPPORTED to JudgmentReason.UNANIMOUS_SUPPORT,
            JudgmentDirection.CONTRADICTED to
                JudgmentReason.UNANIMOUS_CONTRADICTION,
            JudgmentDirection.UNRESOLVED to JudgmentReason.CONFLICT,
            JudgmentDirection.UNRESOLVED to JudgmentReason.CONFLICT,
            JudgmentDirection.UNRESOLVED to JudgmentReason.CONFLICT,
            JudgmentDirection.UNRESOLVED to JudgmentReason.CONFLICT
        )

        coreCases().zip(expected).forEach { (relationships, outcome) ->
            val decision = policy.evaluate(hypothesis, relationships)
            assertEquals(outcome.first, decision.direction)
            assertEquals(outcome.second, decision.reason)
            assertEquals(emptyList(), decision.measures)
        }
        assertEquals("strict-conflict-v1", policy.id)
        assertEquals(expected.take(3).toSet(), policy.supportedOutcomes.map {
            it.direction to it.reason
        }.toSet())
    }

    @Test
    fun `weighted policy reproduces C1 through C6 with exact totals`() {
        val policy = WeightedBalancePolicy()
        val expected = listOf(
            expected(JudgmentDirection.SUPPORTED, JudgmentReason.POSITIVE_BALANCE, "0.8", "0"),
            expected(JudgmentDirection.CONTRADICTED, JudgmentReason.NEGATIVE_BALANCE, "0", "0.8"),
            expected(JudgmentDirection.UNRESOLVED, JudgmentReason.BALANCED_CONFLICT, "0.75", "0.75"),
            expected(JudgmentDirection.SUPPORTED, JudgmentReason.POSITIVE_BALANCE, "0.8", "0.5"),
            expected(JudgmentDirection.CONTRADICTED, JudgmentReason.NEGATIVE_BALANCE, "0.4", "0.9"),
            expected(JudgmentDirection.UNRESOLVED, JudgmentReason.BALANCED_CONFLICT, "1.0", "1.0")
        )

        coreCases().zip(expected).forEach { (relationships, expectedDecision) ->
            assertEquals(
                expectedDecision,
                policy.evaluate(hypothesis, relationships)
            )
        }
        assertEquals("weighted-balance-v1", policy.id)
        assertEquals(
            setOf(
                JudgmentDirection.UNRESOLVED to JudgmentReason.INSUFFICIENT_WEIGHT,
                JudgmentDirection.SUPPORTED to JudgmentReason.POSITIVE_BALANCE,
                JudgmentDirection.CONTRADICTED to JudgmentReason.NEGATIVE_BALANCE,
                JudgmentDirection.UNRESOLVED to JudgmentReason.BALANCED_CONFLICT
            ),
            policy.supportedOutcomes.map { it.direction to it.reason }.toSet()
        )
    }

    @Test
    fun `weighted policy handles zero and one confidence extremes`() {
        val policy = WeightedBalancePolicy()
        assertEquals(
            expected(
                JudgmentDirection.SUPPORTED,
                JudgmentReason.POSITIVE_BALANCE,
                "1.0",
                "0"
            ),
            policy.evaluate(hypothesis, listOf(evaluated("r-1", SUPPORTS, 1.0)))
        )
        assertEquals(
            expected(
                JudgmentDirection.UNRESOLVED,
                JudgmentReason.INSUFFICIENT_WEIGHT,
                "0.0",
                "0"
            ),
            policy.evaluate(hypothesis, listOf(evaluated("r-1", SUPPORTS, 0.0)))
        )
        assertEquals(
            expected(
                JudgmentDirection.UNRESOLVED,
                JudgmentReason.INSUFFICIENT_WEIGHT,
                "0",
                "0.0"
            ),
            policy.evaluate(hypothesis, listOf(evaluated("r-1", CONTRADICTS, 0.0)))
        )
        assertEquals(
            expected(
                JudgmentDirection.SUPPORTED,
                JudgmentReason.POSITIVE_BALANCE,
                "1.0",
                "0.0"
            ),
            policy.evaluate(
                hypothesis,
                listOf(
                    evaluated("r-1", SUPPORTS, 1.0),
                    evaluated("r-2", CONTRADICTS, 0.0)
                )
            )
        )
    }

    @Test
    fun `strict policy treats zero and one confidence as magnitude only`() {
        val policy = StrictConflictPolicy()
        val cases = listOf(
            listOf(evaluated("i6a-1", SUPPORTS, 0.0)) to
                (JudgmentDirection.SUPPORTED to JudgmentReason.UNANIMOUS_SUPPORT),
            listOf(evaluated("i6b-1", SUPPORTS, 1.0)) to
                (JudgmentDirection.SUPPORTED to JudgmentReason.UNANIMOUS_SUPPORT),
            listOf(evaluated("i6c-1", CONTRADICTS, 0.0)) to
                (JudgmentDirection.CONTRADICTED to
                    JudgmentReason.UNANIMOUS_CONTRADICTION),
            listOf(
                evaluated("i6d-1", SUPPORTS, 1.0),
                evaluated("i6d-2", CONTRADICTS, 0.0)
            ) to (JudgmentDirection.UNRESOLVED to JudgmentReason.CONFLICT)
        )

        cases.forEach { (relationships, expectedOutcome) ->
            val decision = policy.evaluate(hypothesis, relationships)
            assertEquals(expectedOutcome.first, decision.direction)
            assertEquals(expectedOutcome.second, decision.reason)
            assertEquals(emptyList(), decision.measures)
        }
    }

    @Test
    fun `policy decision rejects duplicate measure names`() {
        val measure = PolicyMeasure("total", BigDecimal.ONE, "Exact total")
        assertFailsWith<IllegalArgumentException> {
            DirectionalPolicyDecision(
                JudgmentDirection.SUPPORTED,
                JudgmentReason.POSITIVE_BALANCE,
                listOf(measure, measure.copy(value = BigDecimal.TEN))
            )
        }
    }

    private fun expected(
        direction: JudgmentDirection,
        reason: JudgmentReason,
        support: String,
        contradict: String
    ) = DirectionalPolicyDecision(
        direction,
        reason,
        listOf(
            PolicyMeasure(
                "contradict-total",
                BigDecimal(contradict),
                "Exact sum of confidence magnitudes for CONTRADICTS relationships"
            ),
            PolicyMeasure(
                "support-total",
                BigDecimal(support),
                "Exact sum of confidence magnitudes for SUPPORTS relationships"
            )
        )
    )

    private fun coreCases() = listOf(
        listOf(evaluated("c1-1", SUPPORTS, 0.8)),
        listOf(evaluated("c2-1", CONTRADICTS, 0.8)),
        listOf(
            evaluated("c3-1", SUPPORTS, 0.75),
            evaluated("c3-2", CONTRADICTS, 0.75)
        ),
        listOf(
            evaluated("c4-1", SUPPORTS, 0.8),
            evaluated("c4-2", CONTRADICTS, 0.5)
        ),
        listOf(
            evaluated("c5-1", SUPPORTS, 0.4),
            evaluated("c5-2", CONTRADICTS, 0.9)
        ),
        listOf(
            evaluated("c6-1", SUPPORTS, 0.2),
            evaluated("c6-2", SUPPORTS, 0.3),
            evaluated("c6-3", SUPPORTS, 0.5),
            evaluated("c6-4", CONTRADICTS, 0.4),
            evaluated("c6-5", CONTRADICTS, 0.6)
        )
    )

    private fun evaluated(
        id: String,
        direction: RelationshipDirection,
        confidence: Double
    ) = EvaluatedRelationship(
        EvidenceRelationship(
            Identifier(id),
            Identifier("evidence-$id"),
            hypothesis.id,
            direction
        ),
        Confidence(confidence)
    )

    private companion object {
        val SUPPORTS = RelationshipDirection.SUPPORTS
        val CONTRADICTS = RelationshipDirection.CONTRADICTS
    }
}
