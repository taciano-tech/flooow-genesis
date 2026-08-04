package io.flooow.research.exp0003

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.reasoning.DirectionalEvaluationPolicy
import io.flooow.kernel.reasoning.DirectionalEvaluationRequest
import io.flooow.kernel.reasoning.DirectionalEvaluationResult
import io.flooow.kernel.reasoning.DirectionalHypothesisEvaluator
import io.flooow.kernel.reasoning.DirectionalRequestValidator
import io.flooow.kernel.reasoning.EvidenceRelationship
import io.flooow.kernel.reasoning.EvidenceSet
import io.flooow.kernel.reasoning.Hypothesis
import io.flooow.kernel.reasoning.RelationshipDirection
import io.flooow.kernel.reasoning.StrictConflictPolicy
import io.flooow.kernel.reasoning.StructuredJudgment
import io.flooow.kernel.reasoning.WeightedBalancePolicy
import java.math.BigDecimal
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProductionDirectionalRegressionConsumerTest {
    private val fixtures = FrozenFixtures.load()
    private val clock = Clock.fixed(fixtures.instant.value, ZoneOffset.UTC)
    private val policies = listOf(
        "P1" to StrictConflictPolicy(),
        "P2" to WeightedBalancePolicy()
    )

    @Test
    fun `controlled production consumer reproduces all committed core evidence`() {
        val migrated = linkedMapOf<String, String>()

        for (domain in listOf("M", "S")) {
            for (scenario in (1..6).map { "C$it" }) {
                val case = fixtures.coreCase(domain, scenario)
                policies.forEach { (shortName, policy) ->
                    val key = "core.$domain.$scenario.$shortName"
                    migrated[key] = evaluate(case, policy)
                }
            }
        }

        val committed = fixtures.expectedLines.filterKeys { it.startsWith("core.") }
        assertEquals(24, migrated.size)
        assertEquals(committed, migrated)
    }

    private fun evaluate(
        case: FrozenCoreCase,
        policy: DirectionalEvaluationPolicy
    ): String {
        val hypothesis = Hypothesis(
            case.hypothesisId,
            fixtures.properties.required(
                "domain.${case.domain}.hypothesis.primary.statement"
            ),
            Confidence.CERTAIN,
            fixtures.instant
        )
        val relationships = case.relationships.map { relationship ->
            EvidenceRelationship(
                relationship.id,
                relationship.evidenceId,
                relationship.hypothesisId,
                RelationshipDirection.valueOf(relationship.direction.name)
            )
        }
        val evaluator = DirectionalHypothesisEvaluator(
            DirectionalRequestValidator(),
            policy,
            clock
        )
        val result = evaluator.evaluate(
            DirectionalEvaluationRequest(
                hypothesis,
                EvidenceSet(case.evidenceById.values.toSet()),
                relationships
            )
        )
        return serialize(
            assertIs<DirectionalEvaluationResult.Success>(result).judgment
        )
    }

    private fun serialize(judgment: StructuredJudgment): String {
        val measures = judgment.measures.associate { it.name to it.value }
        val support = measures["support-total"]?.canonical() ?: "NOT_APPLICABLE"
        val contradict = measures["contradict-total"]?.canonical() ?: "NOT_APPLICABLE"
        val relationships = judgment.evaluatedRelationships.joinToString(",") {
            val relationship = it.relationship
            "${relationship.id}>${relationship.evidenceId}>" +
                "${relationship.hypothesisId}>${relationship.direction}>" +
                BigDecimal.valueOf(it.confidence.value).canonical()
        }
        return "${judgment.hypothesisId}|${judgment.direction}|${judgment.reason}|" +
            "$support|$contradict|$relationships|${judgment.createdAt}"
    }

    private fun BigDecimal.canonical(): String {
        val text = stripTrailingZeros().toPlainString()
        return if ('.' in text) text else "$text.0"
    }
}
