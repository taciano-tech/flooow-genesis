package io.flooow.research.exp0003

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import java.math.BigDecimal
import java.util.Properties

data class FrozenCoreCase(
    val domain: String,
    val scenario: String,
    val hypothesisId: Identifier,
    val evidenceById: Map<Identifier, Evidence>,
    val relationships: List<ExperimentalEvidenceRelationship>
)

data class AdaptedEvidencePlan(
    val observationDescription: String,
    val evidence: Evidence,
    val relationships: List<ExperimentalEvidenceRelationship>
)

class ExperimentalFixtureAdapter(private val fixtures: FrozenFixtures) {
    fun adaptEvidence(domain: String, alias: String, observationDescription: String): AdaptedEvidencePlan {
        require(observationDescription.isNotBlank())
        return AdaptedEvidencePlan(observationDescription, fixtures.evidence(domain, alias), emptyList())
    }

    fun adapt(
        domain: String,
        scenario: String,
        alias: String,
        observationDescription: String,
        hypothesisId: String,
        direction: RelationshipDirection
    ): AdaptedEvidencePlan {
        val evidencePlan = adaptEvidence(domain, alias, observationDescription)
        val evidence = evidencePlan.evidence
        val relationship = ExperimentalEvidenceRelationship(
            Identifier("$domain-R-$scenario-01"), evidence.id, Identifier(hypothesisId), direction
        )
        return evidencePlan.copy(relationships = listOf(relationship))
    }
}

class FrozenFixtures private constructor(
    val properties: Properties,
    val expectedLines: Map<String, String>,
    val instant: Timestamp
) {
    fun coreCase(domain: String, scenario: String): FrozenCoreCase {
        val hypothesisId = Identifier(properties.required("core.$domain.$scenario.hypothesisId"))
        val relationships = properties.required("core.$domain.$scenario.relationships")
            .split(',').map { encoded ->
                val parts = encoded.split('|')
                require(parts.size == 4) { "Invalid relationship: $encoded" }
                ExperimentalEvidenceRelationship(
                    Identifier(parts[0]), Identifier(parts[1]), Identifier(parts[2]),
                    RelationshipDirection.valueOf(parts[3])
                )
            }
        val evidence = relationships.map { relation -> evidence(domain, relation.evidenceId.value.substringAfter("-E-")) }
            .associateBy { it.id }
        return FrozenCoreCase(domain, scenario, hypothesisId, evidence, relationships)
    }

    fun evidence(domain: String, alias: String): Evidence = Evidence(
        id = Identifier("$domain-E-$alias"),
        observationIds = setOf(Identifier("$domain-O-$alias")),
        confidence = Confidence(properties.required("evidence.$alias.confidence").toDouble()),
        recordedAt = Timestamp.parse(properties.required("evidence.recordedAt"))
    )

    fun request(case: FrozenCoreCase, relationships: List<ExperimentalEvidenceRelationship> = case.relationships) =
        ExperimentalEvaluationRequest(case.hypothesisId, case.evidenceById, relationships, instant)

    fun expectedCore(domain: String, scenario: String, policyShort: String): String =
        expectedLines.getValue("core.$domain.$scenario.$policyShort")

    fun expected(key: String): String = expectedLines.getValue(key)

    companion object {
        fun load(): FrozenFixtures {
            val properties = Properties().apply {
                resource("/evidence-relationship-input.properties").openStream().use(::load)
            }
            val expected = linkedMapOf<String, String>()
            resource("/evidence-relationship-expected.snapshot").readText()
                .lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }
                .forEachIndexed { index, line ->
                    require('=' in line) { "Expected snapshot line ${index + 1} has no '='" }
                    val key = line.substringBefore('=')
                    require(key !in expected) { "Duplicate expected snapshot key: $key" }
                    expected[key] = line.substringAfter('=')
                }
            validateExpected(expected)
            return FrozenFixtures(properties, expected, Timestamp.parse(properties.required("evaluation.instant")))
        }

        private fun validateExpected(expected: Map<String, String>) {
            require(expected["status"] == "EXPECTED_NOT_OBSERVED")
            require(expected["fixture.version"] == "1.0")
            require(expected["domains"] == "M,S")
            require(expected["policies"] == "P1_STRICT_CONFLICT,P2_WEIGHTED_BALANCE")
            val core = expected.keys.filter { it.startsWith("core.") }
            require(core.toSet() == buildSet {
                for (domain in listOf("M", "S")) for (scenario in 1..6) for (policy in 1..2) {
                    add("core.$domain.C$scenario.P$policy")
                }
            }) { "Expected snapshot must contain exactly 24 core traces" }
            val requiredIntegrity = setOf(
                "integrity.I1.primary.direction", "integrity.I1.primary.relationshipDirection",
                "integrity.I1.alternative.direction", "integrity.I1.alternative.relationshipDirection",
                "integrity.I1.sameEvidenceUnchanged", "integrity.I2.validation", "integrity.I2.judgmentProduced",
                "integrity.I3.validation", "integrity.I3.judgmentProduced", "integrity.I4.validation",
                "integrity.I4.judgmentProduced", "integrity.I5.validation", "integrity.I5.judgmentProduced",
                "integrity.I6a.P1.direction", "integrity.I6a.P1.reason", "integrity.I6a.P2.direction",
                "integrity.I6a.P2.reason", "integrity.I6a.P2.supportTotal", "integrity.I6a.P2.contradictTotal",
                "integrity.I6b.P1.direction", "integrity.I6b.P1.reason", "integrity.I6b.P2.direction",
                "integrity.I6b.P2.reason", "integrity.I6b.P2.supportTotal", "integrity.I6b.P2.contradictTotal",
                "integrity.I6c.P1.direction", "integrity.I6c.P1.reason", "integrity.I6c.P2.direction",
                "integrity.I6c.P2.reason", "integrity.I6c.P2.supportTotal", "integrity.I6c.P2.contradictTotal",
                "integrity.I6d.P1.direction", "integrity.I6d.P1.reason", "integrity.I6d.P2.direction",
                "integrity.I6d.P2.reason", "integrity.I6d.P2.supportTotal", "integrity.I6d.P2.contradictTotal",
                "integrity.I7.executableResultEqual", "integrity.I8.everyPermutationEqual",
                "integrity.I9.everyRepeatEqual", "integrity.I10.validation", "integrity.I10.judgmentProduced",
                "integrity.I11.validation", "integrity.I11.judgmentProduced",
                "integrity.I12.canonicalEvidenceUnchanged", "integrity.I12.defaultRelationshipCreated"
            )
            require(expected.keys.filter { it.startsWith("integrity.") }.toSet() == requiredIntegrity)
            val requiredReduction = setOf(
                "reduction.oracle", "reduction.PASS.definition", "reduction.FAIL.definition",
                "reduction.bothOutcomesAcceptedAsEvidence"
            )
            require(expected.keys.filter { it.startsWith("reduction.") }.toSet() == requiredReduction)
            val requiredAblation = setOf(
                "ablation.REMOVE_RELATIONSHIP_DIRECTION.expected", "ablation.REMOVE_RELATIONSHIP_DIRECTION.oracle",
                "ablation.REMOVE_JUDGMENT_DIRECTION.expected", "ablation.REMOVE_JUDGMENT_DIRECTION.oracle",
                "ablation.REMOVE_RETAINED_RELATIONSHIPS.expected", "ablation.REMOVE_RETAINED_RELATIONSHIPS.oracle"
            )
            require(expected.keys.filter { it.startsWith("ablation.") }.toSet() == requiredAblation)
            val globals = setOf("fixture.version", "status", "domains", "policies", "kernel.productionChanged", "fixture.expectedOverwrittenByExecution")
            require(expected.keys.all { key -> key.substringBefore('.') in setOf("core", "integrity", "reduction", "ablation", "kernel", "fixture") || key in globals })
            require(globals.all(expected::containsKey))
        }

        private fun resource(name: String) = requireNotNull(FrozenFixtures::class.java.getResource(name)) {
            "Missing frozen EXP-0003 resource: $name"
        }
    }
}

fun Properties.required(key: String): String = requireNotNull(getProperty(key)) { "Missing property: $key" }

object CanonicalTraceSerializer {
    fun serialize(judgment: ExperimentalJudgment, evidenceById: Map<Identifier, Evidence>): String {
        val support = judgment.supportTotal?.plain() ?: "NOT_APPLICABLE"
        val contradict = judgment.contradictTotal?.plain() ?: "NOT_APPLICABLE"
        val relationships = judgment.evaluatedRelationships.sortedBy { it.id.value }.joinToString(",") { relation ->
            val confidence = BigDecimal.valueOf(evidenceById.getValue(relation.evidenceId).confidence.value).plain()
            "${relation.id}>${relation.evidenceId}>${relation.hypothesisId}>${relation.direction}>$confidence"
        }
        return "${judgment.hypothesisId}|${judgment.direction}|${judgment.reason}|$support|$contradict|$relationships|${judgment.evaluatedAt}"
    }

    private fun BigDecimal.plain(): String {
        val text = stripTrailingZeros().toPlainString()
        return if ('.' in text) text else "$text.0"
    }
}
