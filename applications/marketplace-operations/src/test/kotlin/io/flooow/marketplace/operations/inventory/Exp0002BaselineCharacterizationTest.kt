package io.flooow.marketplace.operations.inventory

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import io.flooow.kernel.model.Observation
import io.flooow.kernel.reasoning.DeterministicEvidenceAggregator
import io.flooow.kernel.reasoning.DeterministicHypothesisEvaluator
import io.flooow.kernel.reasoning.EvidenceSet
import io.flooow.kernel.reasoning.Hypothesis
import io.flooow.kernel.reasoning.Judgment
import io.flooow.kernel.reasoning.WeightedConfidencePolicy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class Exp0002BaselineCharacterizationTest {

    @Test
    fun `frozen Kernel behavior matches the committed characterization snapshot`() {
        val scenarios = loadScenarios("/exp-0002/conflicting-evidence-input.properties")
        val expected = resourceText("/exp-0002/conflicting-evidence-observed.snapshot")
        val observed = scenarios.map { scenario ->
            ScenarioResult(
                scenario = scenario,
                judgment = evaluator().evaluate(hypothesis(), evidenceSet(scenario))
            )
        }

        assertEquals(expected.trimEnd(), snapshotOf(observed).trimEnd())
    }

    private fun hypothesis(): Hypothesis = Hypothesis(
        id = Identifier("hypothesis-replenishment-before-stockout"),
        statement = "Replenishment will arrive before the projected stockout.",
        confidence = Confidence(0.7),
        createdAt = FIXED_TIMESTAMP
    )

    private fun evaluator(): DeterministicHypothesisEvaluator =
        DeterministicHypothesisEvaluator(
            evidenceAggregator = DeterministicEvidenceAggregator(),
            confidencePolicy = WeightedConfidencePolicy(
                hypothesisWeight = 0.5,
                aggregatedEvidenceWeight = 0.5
            ),
            clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
        )

    private fun evidenceSet(scenario: Scenario): EvidenceSet = EvidenceSet(
        scenario.evidences.mapIndexed { index, controlled ->
            val observationId = Identifier("observation-${scenario.id}-${index + 1}")
            Observation(
                id = observationId,
                description = controlled.description,
                observedAt = FIXED_TIMESTAMP
            )
            Evidence(
                id = Identifier("evidence-${scenario.id}-${index + 1}"),
                observationIds = setOf(observationId),
                confidence = Confidence(controlled.confidence),
                recordedAt = FIXED_TIMESTAMP
            )
        }.toSet()
    )

    private fun loadScenarios(resource: String): List<Scenario> {
        val properties = Properties().apply {
            Exp0002BaselineCharacterizationTest::class.java
                .getResourceAsStream(resource)
                .use { stream ->
                    requireNotNull(stream) { "Missing EXP-0002 fixture: $resource" }
                    load(stream)
                }
        }

        return (1..properties.required("scenario.count").toInt()).map { scenarioIndex ->
            val prefix = "scenario.$scenarioIndex"
            val evidenceCount = properties.required("$prefix.evidence.count").toInt()
            Scenario(
                id = properties.required("$prefix.id"),
                expectedDirection = SemanticDirection.valueOf(
                    properties.required("$prefix.expectedDirection")
                ),
                evidences = (1..evidenceCount).map { evidenceIndex ->
                    ControlledEvidence(
                        description = properties.required(
                            "$prefix.evidence.$evidenceIndex.description"
                        ),
                        confidence = properties.required(
                            "$prefix.evidence.$evidenceIndex.confidence"
                        ).toDouble()
                    )
                }
            )
        }
    }

    private fun snapshotOf(results: List<ScenarioResult>): String = buildList {
        add("kernel.evidence.directionField=ABSENT")
        add("kernel.judgment.directionField=ABSENT")
        add("kernel.conflictResolution=ABSENT")

        results.forEach { result ->
            val prefix = "scenario.${result.scenario.id}"
            val observedDirection = directionOf(result.judgment)
            add("$prefix.expected.direction=${result.scenario.expectedDirection}")
            add("$prefix.observed.conclusion=${result.judgment.conclusion}")
            add("$prefix.observed.confidence=${result.judgment.confidence.value}")
            add("$prefix.semantic.match=${result.scenario.expectedDirection == observedDirection}")
        }

        val equal = results.single { it.scenario.id == "equal-conflict" }.judgment
        val permuted = results.single {
            it.scenario.id == "equal-conflict-permuted"
        }.judgment
        add("permutation.equal.confidence=${equal.confidence == permuted.confidence}")
        add("permutation.equal.conclusion=${equal.conclusion == permuted.conclusion}")
        add("baseline.hypothesis.supported=false")
        add("baseline.nullHypothesis.supported=true")
    }.joinToString("\n")

    private fun directionOf(judgment: Judgment): SemanticDirection =
        when (judgment.conclusion) {
            "Evidence supports the hypothesis." -> SemanticDirection.SUPPORTED
            else -> error("Unclassified frozen Kernel conclusion: ${judgment.conclusion}")
        }

    private fun resourceText(resource: String): String =
        requireNotNull(Exp0002BaselineCharacterizationTest::class.java.getResource(resource)) {
            "Missing EXP-0002 snapshot: $resource"
        }.readText().replace("\r\n", "\n")

    private fun Properties.required(name: String): String =
        requireNotNull(getProperty(name)) { "Missing fixture property: $name" }

    private enum class SemanticDirection {
        SUPPORTED,
        CONTRADICTED,
        UNRESOLVED
    }

    private data class ControlledEvidence(
        val description: String,
        val confidence: Double
    )

    private data class Scenario(
        val id: String,
        val expectedDirection: SemanticDirection,
        val evidences: List<ControlledEvidence>
    )

    private data class ScenarioResult(
        val scenario: Scenario,
        val judgment: Judgment
    )

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-08-04T12:00:00Z")
        val FIXED_TIMESTAMP: Timestamp = Timestamp(FIXED_INSTANT)
    }
}
