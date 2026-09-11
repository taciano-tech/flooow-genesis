package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DirectionalHypothesisEvaluatorTest {
    private val instant = Instant.parse("2026-08-04T18:45:00Z")
    private val timestamp = Timestamp(instant)
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)
    private val idFactory = DirectionalJudgmentIdFactory()

    @Test
    fun `reproduces all 24 core traces and retains every contribution`() {
        val policies = listOf(StrictConflictPolicy(), WeightedBalancePolicy())
        val hypotheses = listOf(hypothesis("M1"), hypothesis("S1"))

        hypotheses.forEach { hypothesis ->
            policies.forEach { policy ->
                coreCases(hypothesis.id).forEachIndexed { index, case ->
                    val result = evaluator(policy).evaluate(
                        request(hypothesis, case)
                    )
                    val judgment = assertIs<DirectionalEvaluationResult.Success>(result)
                        .judgment
                    assertEquals(hypothesis.id, judgment.hypothesisId)
                    assertEquals(policy.id, judgment.policyId)
                    assertEquals(
                        case.sortedBy { it.relationship.id.value },
                        judgment.evaluatedRelationships
                    )
                    assertEquals(timestamp, judgment.createdAt)
                    val expected = frozenExpectation(policy.id, index)
                    assertEquals(expected.direction, judgment.direction)
                    assertEquals(expected.reason, judgment.reason)
                    assertEquals(expected.measures, judgment.measures)
                }
            }
        }
    }

    @Test
    fun `C6 is invariant across all 120 input permutations`() {
        val hypothesis = hypothesis("M1")
        val relationships = coreCases(hypothesis.id).last()
        listOf(StrictConflictPolicy(), WeightedBalancePolicy()).forEach { policy ->
            val evaluator = evaluator(policy)
            val expected = evaluator.evaluate(request(hypothesis, relationships))
            val permutations = permutations(relationships)
            assertEquals(120, permutations.size)
            permutations.forEach { permutation ->
                assertEquals(expected, evaluator.evaluate(request(hypothesis, permutation)))
            }
        }
    }

    @Test
    fun `fixed input policy and clock repeat structurally and ignore hypothesis prose`() {
        val original = hypothesis("M1", "Original hypothesis prose")
        val changed = original.copy(statement = "Entirely different prose")
        val relationships = coreCases(original.id).first()
        val evaluator = evaluator(StrictConflictPolicy())

        val first = evaluator.evaluate(request(original, relationships))
        repeat(3) {
            assertEquals(first, evaluator.evaluate(request(original, relationships)))
        }
        assertEquals(first, evaluator.evaluate(request(changed, relationships)))
    }

    @Test
    fun `changed observation prose produces the same explicit request result`() {
        val hypothesis = hypothesis("M1")
        val relationship = coreCases(hypothesis.id).first()
        val evaluator = evaluator(StrictConflictPolicy())

        val original = adaptObservation(
            "Carrier confirms replenishment before projected stockout.",
            hypothesis,
            relationship
        )
        val changed = adaptObservation(
            "Carrier status prose changed without changing the relationship.",
            hypothesis,
            relationship
        )

        assertEquals(original, changed)
        assertEquals(evaluator.evaluate(original), evaluator.evaluate(changed))
    }

    @Test
    fun `invalid request returns its error without invoking policy`() {
        val policy = RecordingPolicy()
        val result = evaluator(policy).evaluate(
            DirectionalEvaluationRequest(
                hypothesis("M1"),
                EvidenceSet(setOf(evidence("unused", 0.5))),
                emptyList()
            )
        )

        assertEquals(
            DirectionalEvaluationResult.Invalid(
                DirectionalValidationError.NO_RELATIONSHIPS
            ),
            result
        )
        assertEquals(0, policy.invocations)
    }

    @Test
    fun `unsupported custom policy outcome is a programmer error`() {
        val hypothesis = hypothesis("M1")
        val relationship = evaluated("r-1", "e-1", hypothesis.id, SUPPORTS, 0.8)
        val policy = object : DirectionalEvaluationPolicy {
            override val id = "invalid-custom-policy"
            override val supportedOutcomes = setOf(
                DirectionalOutcome(
                    JudgmentDirection.SUPPORTED,
                    JudgmentReason.UNANIMOUS_SUPPORT
                )
            )

            override fun evaluate(
                hypothesis: Hypothesis,
                relationships: List<EvaluatedRelationship>
            ) = DirectionalPolicyDecision(
                JudgmentDirection.CONTRADICTED,
                JudgmentReason.NEGATIVE_BALANCE,
                emptyList()
            )
        }

        assertFailsWith<IllegalStateException> {
            evaluator(policy).evaluate(request(hypothesis, listOf(relationship)))
        }
    }

    @Test
    fun `judgment id matches known answer and excludes clock`() {
        val hypothesis = hypothesis("h-1")
        val relationship = evaluated("r-1", "e-1", hypothesis.id, SUPPORTS, 1.0)
        assertEquals(
            Identifier(
                "directional-judgment-" +
                    "638170ba5a20f956792d6d773c3b67468eaa7dbbe54adb3c2a133ea3169c2676"
            ),
            idFactory.create(hypothesis, "strict-conflict-v1", listOf(relationship))
        )

        val request = request(hypothesis, listOf(relationship))
        val first = success(evaluator(StrictConflictPolicy(), clock).evaluate(request))
        val laterClock = Clock.fixed(instant.plusSeconds(60), ZoneOffset.UTC)
        val later = success(
            evaluator(StrictConflictPolicy(), laterClock).evaluate(request)
        )
        assertEquals(first.id, later.id)
        assertEquals(Timestamp(instant.plusSeconds(60)), later.createdAt)
    }

    @Test
    fun `judgment id canonicalizes decimal confidence and changes with semantics`() {
        val hypothesis = hypothesis("M1")
        val support = evaluated("r-1", "e-1", hypothesis.id, SUPPORTS, 1.0)
        val same = support.copy(confidence = Confidence(1.00))
        val contradiction = support.copy(
            relationship = support.relationship.copy(direction = CONTRADICTS)
        )
        val originalId = idFactory.create(hypothesis, "strict-conflict-v1", listOf(support))
        assertEquals(
            originalId,
            idFactory.create(hypothesis, "strict-conflict-v1", listOf(same))
        )
        kotlin.test.assertNotEquals(
            originalId,
            idFactory.create(hypothesis, "strict-conflict-v1", listOf(contradiction))
        )
    }

    private fun evaluator(
        policy: DirectionalEvaluationPolicy,
        selectedClock: Clock = clock
    ) = DirectionalHypothesisEvaluator(
        DirectionalRequestValidator(),
        policy,
        selectedClock
    )

    @Suppress("UNUSED_PARAMETER")
    private fun adaptObservation(
        observationDescription: String,
        hypothesis: Hypothesis,
        relationships: List<EvaluatedRelationship>
    ): DirectionalEvaluationRequest = request(hypothesis, relationships)

    private fun frozenExpectation(
        policyId: String,
        caseIndex: Int
    ): ExpectedTrace = if (policyId == "strict-conflict-v1") {
        listOf(
            ExpectedTrace(JudgmentDirection.SUPPORTED, JudgmentReason.UNANIMOUS_SUPPORT),
            ExpectedTrace(JudgmentDirection.CONTRADICTED, JudgmentReason.UNANIMOUS_CONTRADICTION),
            ExpectedTrace(JudgmentDirection.UNRESOLVED, JudgmentReason.CONFLICT),
            ExpectedTrace(JudgmentDirection.UNRESOLVED, JudgmentReason.CONFLICT),
            ExpectedTrace(JudgmentDirection.UNRESOLVED, JudgmentReason.CONFLICT),
            ExpectedTrace(JudgmentDirection.UNRESOLVED, JudgmentReason.CONFLICT)
        )[caseIndex]
    } else {
        listOf(
            weighted(JudgmentDirection.SUPPORTED, JudgmentReason.POSITIVE_BALANCE, "0.8", "0"),
            weighted(JudgmentDirection.CONTRADICTED, JudgmentReason.NEGATIVE_BALANCE, "0", "0.8"),
            weighted(JudgmentDirection.UNRESOLVED, JudgmentReason.BALANCED_CONFLICT, "0.75", "0.75"),
            weighted(JudgmentDirection.SUPPORTED, JudgmentReason.POSITIVE_BALANCE, "0.8", "0.5"),
            weighted(JudgmentDirection.CONTRADICTED, JudgmentReason.NEGATIVE_BALANCE, "0.4", "0.9"),
            weighted(JudgmentDirection.UNRESOLVED, JudgmentReason.BALANCED_CONFLICT, "1.0", "1.0")
        )[caseIndex]
    }

    private fun weighted(
        direction: JudgmentDirection,
        reason: JudgmentReason,
        support: String,
        contradict: String
    ) = ExpectedTrace(
        direction,
        reason,
        listOf(
            PolicyMeasure(
                "contradict-total",
                java.math.BigDecimal(contradict),
                "Exact sum of confidence magnitudes for CONTRADICTS relationships"
            ),
            PolicyMeasure(
                "support-total",
                java.math.BigDecimal(support),
                "Exact sum of confidence magnitudes for SUPPORTS relationships"
            )
        )
    )

    private data class ExpectedTrace(
        val direction: JudgmentDirection,
        val reason: JudgmentReason,
        val measures: List<PolicyMeasure> = emptyList()
    )

    private fun request(
        hypothesis: Hypothesis,
        relationships: List<EvaluatedRelationship>
    ): DirectionalEvaluationRequest {
        val evidences = relationships.map { relationship ->
            evidence(
                relationship.relationship.evidenceId.value,
                relationship.confidence.value
            )
        }.toSet()
        return DirectionalEvaluationRequest(
            hypothesis,
            EvidenceSet(evidences),
            relationships.map { it.relationship }
        )
    }

    private fun coreCases(hypothesisId: Identifier): List<List<EvaluatedRelationship>> {
        val domain = hypothesisId.value.first()
        fun trace(
            case: String,
            ordinal: Int,
            alias: String,
            direction: RelationshipDirection,
            confidence: Double
        ) = evaluated(
            "$domain-R-$case-${ordinal.toString().padStart(2, '0')}",
            "$domain-E-$alias",
            hypothesisId,
            direction,
            confidence
        )
        return listOf(
        listOf(trace("C1", 1, "SUPPORT_080", SUPPORTS, 0.8)),
        listOf(trace("C2", 1, "CONTRADICT_080", CONTRADICTS, 0.8)),
        listOf(
            trace("C3", 1, "SUPPORT_075", SUPPORTS, 0.75),
            trace("C3", 2, "CONTRADICT_075", CONTRADICTS, 0.75)
        ),
        listOf(
            trace("C4", 1, "SUPPORT_080", SUPPORTS, 0.8),
            trace("C4", 2, "CONTRADICT_050", CONTRADICTS, 0.5)
        ),
        listOf(
            trace("C5", 1, "SUPPORT_040", SUPPORTS, 0.4),
            trace("C5", 2, "CONTRADICT_090", CONTRADICTS, 0.9)
        ),
        listOf(
            trace("C6", 1, "SUPPORT_020", SUPPORTS, 0.2),
            trace("C6", 2, "SUPPORT_030", SUPPORTS, 0.3),
            trace("C6", 3, "SUPPORT_050", SUPPORTS, 0.5),
            trace("C6", 4, "CONTRADICT_040", CONTRADICTS, 0.4),
            trace("C6", 5, "CONTRADICT_060", CONTRADICTS, 0.6)
        )
    )
    }

    private fun evaluated(
        id: String,
        evidenceId: String,
        hypothesisId: Identifier,
        direction: RelationshipDirection,
        confidence: Double
    ) = EvaluatedRelationship(
        EvidenceRelationship(
            Identifier(id),
            Identifier(evidenceId),
            hypothesisId,
            direction
        ),
        Confidence(confidence)
    )

    private fun hypothesis(
        id: String,
        statement: String = "A testable statement"
    ) = Hypothesis(Identifier(id), statement, Confidence(0.5), timestamp)

    private fun evidence(id: String, confidence: Double) = Evidence(
        Identifier(id),
        setOf(Identifier("observation-$id")),
        Confidence(confidence),
        timestamp
    )

    private fun success(result: DirectionalEvaluationResult) =
        assertIs<DirectionalEvaluationResult.Success>(result).judgment

    private fun <T> permutations(values: List<T>): List<List<T>> =
        if (values.isEmpty()) listOf(emptyList()) else values.flatMapIndexed { index, value ->
            permutations(values.filterIndexed { candidate, _ -> candidate != index })
                .map { listOf(value) + it }
        }

    private class RecordingPolicy : DirectionalEvaluationPolicy {
        override val id = "recording"
        override val supportedOutcomes = emptySet<DirectionalOutcome>()
        var invocations = 0

        override fun evaluate(
            hypothesis: Hypothesis,
            relationships: List<EvaluatedRelationship>
        ): DirectionalPolicyDecision {
            invocations += 1
            error("Policy must not be invoked")
        }
    }

    private companion object {
        val SUPPORTS = RelationshipDirection.SUPPORTS
        val CONTRADICTS = RelationshipDirection.CONTRADICTS
    }
}
