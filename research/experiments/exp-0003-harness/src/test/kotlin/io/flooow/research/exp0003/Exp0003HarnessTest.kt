package io.flooow.research.exp0003

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.model.Evidence
import io.flooow.kernel.reasoning.AggregatedEvidenceConfidencePolicy
import io.flooow.kernel.reasoning.DeterministicEvidenceAggregator
import io.flooow.kernel.reasoning.DeterministicHypothesisEvaluator
import io.flooow.kernel.reasoning.EvidenceSet
import io.flooow.kernel.reasoning.Hypothesis
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Exp0003HarnessTest {
    private val fixtures = FrozenFixtures.load()
    private val adapter = ExperimentalFixtureAdapter(fixtures)
    private val engine = ExperimentalReasoningEngine()

    @Test
    fun `complete experiment is observed before frozen oracles are asserted`() {
        val actual = linkedMapOf<String, String>()
        val diagnostics = mutableListOf<String>()

        observeCore(actual, diagnostics)
        observeIntegrity(actual, diagnostics)
        observeReduction(actual, diagnostics)
        observeAblations(actual, diagnostics)
        observeStructuralValidation(actual, diagnostics)

        // The complete primary observation is durable diagnostic output before any test assertion.
        writeObserved(actual, diagnostics)

        assertFixtureContract()
        val comparableExpected = fixtures.expectedLines.filterKeys {
            it.startsWith("core.") || it.startsWith("integrity.")
        }
        assertEquals(comparableExpected, actual.filterKeys(comparableExpected::containsKey))
        assertEquals("OBSERVE_PASS_OR_FAIL", fixtures.expected("reduction.oracle"))
        assertEquals("true", fixtures.expected("reduction.bothOutcomesAcceptedAsEvidence"))
        assertEquals("FAIL", actual.getValue("reduction.result"))
        for (ablation in fixtures.properties.required("ablation.ids").split(',')) {
            assertEquals(fixtures.expected("ablation.$ablation.expected"), actual.getValue("ablation.$ablation.actual"))
        }
        assertEquals("StructuralInputViolation", actual.getValue("structural.duplicateRelationshipId.errorType"))
        assertTrue(diagnostics.isNotEmpty())
    }

    private fun observeCore(actual: MutableMap<String, String>, diagnostics: MutableList<String>) {
        for (domain in listOf("M", "S")) for (scenario in (1..6).map { "C$it" }) {
            val case = fixtures.coreCase(domain, scenario)
            for ((short, policy) in listOf("P1" to "P1_STRICT_CONFLICT", "P2" to "P2_WEIGHTED_BALANCE")) {
                val key = "core.$domain.$scenario.$short"
                val outcome = engine.evaluate(policy, fixtures.request(case))
                actual[key] = when (outcome) {
                    is EvaluationOutcome.Success -> CanonicalTraceSerializer.serialize(outcome.judgment, case.evidenceById)
                    is EvaluationOutcome.Invalid -> "INVALID:${outcome.code}"
                }
                diagnostics += "$key.expected=${fixtures.expected(key)}"
                diagnostics += "$key.actual=${actual.getValue(key)}"
            }
        }
    }

    private fun observeIntegrity(actual: MutableMap<String, String>, diagnostics: MutableList<String>) {
        val base = fixtures.coreCase("M", "C1")
        val canonicalEvidence = base.evidenceById.getValue(Identifier("M-E-SUPPORT_080"))

        val i1Primary = adapter.adapt("M", "I1", "SUPPORT_080", "Carrier confirms replenishment before projected stockout.", "M1", RelationshipDirection.SUPPORTS)
        val i1Alternative = adapter.adapt("M", "I1", "SUPPORT_080", "Carrier confirms replenishment before projected stockout.", "M2", RelationshipDirection.CONTRADICTS)
        val primary = success("P1_STRICT_CONFLICT", request("M1", i1Primary))
        val alternative = success("P1_STRICT_CONFLICT", request("M2", i1Alternative))
        actual["integrity.I1.primary.direction"] = primary?.direction?.name ?: "NO_JUDGMENT"
        actual["integrity.I1.primary.relationshipDirection"] = i1Primary.relationships.single().direction.name
        actual["integrity.I1.alternative.direction"] = alternative?.direction?.name ?: "NO_JUDGMENT"
        actual["integrity.I1.alternative.relationshipDirection"] = i1Alternative.relationships.single().direction.name
        actual["integrity.I1.sameEvidenceUnchanged"] = (i1Primary.evidence == i1Alternative.evidence).toString()

        val mismatch = i1Alternative.relationships.single()
        recordInvalid(actual, "I2", request("M1", i1Alternative.copy(relationships = listOf(mismatch))))
        val absent = relationship("I3", 1, "M-E-ABSENT_EVIDENCE", "M1", RelationshipDirection.SUPPORTS)
        recordInvalid(actual, "I3", request("M1", mapOf(canonicalEvidence.id to canonicalEvidence), listOf(absent)))
        val dupSupport = relationship("I4", 1, canonicalEvidence.id.value, "M1", RelationshipDirection.SUPPORTS)
        val dupContradict = relationship("I4", 2, canonicalEvidence.id.value, "M1", RelationshipDirection.CONTRADICTS)
        recordInvalid(actual, "I4", request("M1", mapOf(canonicalEvidence.id to canonicalEvidence), listOf(dupSupport, dupContradict)))
        recordInvalid(actual, "I5", request("M1", emptyMap(), emptyList()))

        observeI6(actual, "I6a", listOf("SUPPORT_000" to RelationshipDirection.SUPPORTS))
        observeI6(actual, "I6b", listOf("SUPPORT_100" to RelationshipDirection.SUPPORTS))
        observeI6(actual, "I6c", listOf("CONTRADICT_000" to RelationshipDirection.CONTRADICTS))
        observeI6(actual, "I6d", listOf("SUPPORT_100" to RelationshipDirection.SUPPORTS, "CONTRADICT_000" to RelationshipDirection.CONTRADICTS))

        val originalPlan = adapter.adapt("M", "I7", "SUPPORT_080", fixtures.properties.required("domain.M.observation.support.description"), "M1", RelationshipDirection.SUPPORTS)
        val changedPlan = adapter.adapt("M", "I7", "SUPPORT_080", "Carrier status prose changed without changing the relationship.", "M1", RelationshipDirection.SUPPORTS)
        val originalResult = success("P1_STRICT_CONFLICT", request("M1", originalPlan))
        val changedResult = success("P1_STRICT_CONFLICT", request("M1", changedPlan))
        actual["integrity.I7.executableResultEqual"] = (originalPlan.observationDescription != changedPlan.observationDescription && originalResult == changedResult).toString()

        val c6 = fixtures.coreCase("M", "C6")
        val canonicalC6 = engine.evaluate("P2_WEIGHTED_BALANCE", fixtures.request(c6))
        actual["integrity.I8.everyPermutationEqual"] = c6.relationships.permutations()
            .all { engine.evaluate("P2_WEIGHTED_BALANCE", fixtures.request(c6, it)) == canonicalC6 }.toString()
        val successRepeats = (1..3).map { engine.evaluate("P1_STRICT_CONFLICT", fixtures.request(base)) }
        val invalidRepeats = (1..3).map { engine.evaluate("P1_STRICT_CONFLICT", request("M1", i1Alternative)) }
        actual["integrity.I9.everyRepeatEqual"] = (successRepeats.distinct().size == 1 && invalidRepeats.distinct().size == 1).toString()

        val alt2 = relationship("I10", 2, canonicalEvidence.id.value, "M2", RelationshipDirection.SUPPORTS)
        recordInvalid(actual, "I10", request("M1", mapOf(canonicalEvidence.id to canonicalEvidence), listOf(mismatch, alt2)))
        val identical1 = relationship("I11", 1, canonicalEvidence.id.value, "M1", RelationshipDirection.SUPPORTS)
        val identical2 = relationship("I11", 2, canonicalEvidence.id.value, "M1", RelationshipDirection.SUPPORTS)
        recordInvalid(actual, "I11", request("M1", mapOf(canonicalEvidence.id to canonicalEvidence), listOf(identical1, identical2)))

        val i12 = adapter.adaptEvidence("M", "SUPPORT_080", fixtures.properties.required("domain.M.observation.support.description"))
        actual["integrity.I12.canonicalEvidenceUnchanged"] = evidenceFieldsEqual(canonicalEvidence, i12.evidence).toString()
        actual["integrity.I12.defaultRelationshipCreated"] = i12.relationships.isNotEmpty().toString()

        fixtures.expectedLines.keys.filter { it.startsWith("integrity.") }.sorted().forEach { key ->
            diagnostics += "$key.expected=${fixtures.expected(key)}"
            diagnostics += "$key.actual=${actual[key] ?: "MISSING"}"
        }
    }

    private fun observeI6(actual: MutableMap<String, String>, scenario: String, specs: List<Pair<String, RelationshipDirection>>) {
        val evidence = specs.map { fixtures.evidence("M", it.first) }.associateBy { it.id }
        val relationships = specs.mapIndexed { index, spec -> relationship(scenario, index + 1, "M-E-${spec.first}", "M1", spec.second) }
        val request = request("M1", evidence, relationships)
        val p1 = success("P1_STRICT_CONFLICT", request)
        val p2 = success("P2_WEIGHTED_BALANCE", request)
        actual["integrity.$scenario.P1.direction"] = p1?.direction?.name ?: "NO_JUDGMENT"
        actual["integrity.$scenario.P1.reason"] = p1?.reason?.name ?: "NO_REASON"
        actual["integrity.$scenario.P2.direction"] = p2?.direction?.name ?: "NO_JUDGMENT"
        actual["integrity.$scenario.P2.reason"] = p2?.reason?.name ?: "NO_REASON"
        actual["integrity.$scenario.P2.supportTotal"] = p2?.supportTotal?.canonical() ?: "NOT_APPLICABLE"
        actual["integrity.$scenario.P2.contradictTotal"] = p2?.contradictTotal?.canonical() ?: "NOT_APPLICABLE"
    }

    private fun observeReduction(actual: MutableMap<String, String>, diagnostics: MutableList<String>) {
        val evaluator = DeterministicHypothesisEvaluator(
            DeterministicEvidenceAggregator(), AggregatedEvidenceConfidencePolicy(),
            Clock.fixed(Instant.parse("2026-08-04T18:45:00Z"), ZoneOffset.UTC)
        )
        val observations = mutableListOf<ExistingKernelReductionObservation>()
        for (number in 1..5) {
            val scenario = "C$number"
            val case = fixtures.coreCase("M", scenario)
            val hypothesis = Hypothesis(case.hypothesisId, "Frozen reduction hypothesis", Confidence.CERTAIN, fixtures.instant)
            val result = evaluator.evaluate(hypothesis, EvidenceSet(case.evidenceById.values.toSet()))
            val expectedP1 = traceDirection(fixtures.expectedCore("M", scenario, "P1"))
            val expectedP2 = traceDirection(fixtures.expectedCore("M", scenario, "P2"))
            val observation = ExistingKernelReductionObservation(
                scenario = scenario,
                projection = ExistingJudgmentProjection(
                    result.hypothesisId,
                    result.conclusion,
                    BigDecimal.valueOf(result.confidence.value).canonical(),
                    result.createdAt.toString()
                ),
                expectedP1Direction = expectedP1,
                expectedP2Direction = expectedP2
            )
            observations += observation
            actual["reduction.$scenario.observation"] = observation.serialize()
            diagnostics += "reduction.$scenario.expectedP1=${fixtures.expectedCore("M", scenario, "P1")}"
            diagnostics += "reduction.$scenario.expectedP2=${fixtures.expectedCore("M", scenario, "P2")}"
            diagnostics += "reduction.$scenario.actual=${observation.serialize()}"
        }
        val collisions = observations.groupBy { it.projection }.values.flatMap { sameProjection ->
            buildList {
                if (sameProjection.map { it.expectedP1Direction }.distinct().size > 1) add(ReductionCollision("P1", sameProjection))
                if (sameProjection.map { it.expectedP2Direction }.distinct().size > 1) add(ReductionCollision("P2", sameProjection))
            }
        }
        actual["reduction.collisions.count"] = collisions.size.toString()
        collisions.forEachIndexed { index, collision -> actual["reduction.collision.${index + 1}"] = collision.serialize() }
        actual["reduction.traceCharacterization"] = "Judgment projection exposes no evaluated relationship collection; EvaluationResult retains only the undirected EvidenceSet"
        actual["reduction.result"] = if (collisions.isEmpty()) "PASS" else "FAIL"
        diagnostics += "reduction.collisions=${collisions.joinToString(";") { it.serialize() }}"
    }

    private fun observeAblations(actual: MutableMap<String, String>, diagnostics: MutableList<String>) {
        val case = fixtures.coreCase("M", "C4")
        val baseline = success("P2_WEIGHTED_BALANCE", fixtures.request(case))!!

        val explorer = UndirectedRelationshipExplorer(engine)
        val ambiguous = mutableListOf<String>()
        for (scenario in 1..5) {
            val core = fixtures.coreCase("M", "C$scenario")
            for (policy in listOf("P1_STRICT_CONFLICT", "P2_WEIGHTED_BALANCE")) {
                val directions = explorer.possibleDirections(policy, core, fixtures)
                actual["ablation.REMOVE_RELATIONSHIP_DIRECTION.C$scenario.$policy.possibleDirections"] =
                    directions.sortedBy { it.name }.joinToString(",")
                if (directions.size != 1) ambiguous += "C$scenario/$policy:${directions.sortedBy { it.name }}"
            }
        }
        val relationshipAblation = AblationObservation(
            if (ambiguous.isEmpty()) "PASS" else "FAIL_REQUIRED_SEMANTICS",
            if (ambiguous.isEmpty()) "Every predeclared case had one direction" else "Multiple executable directions remain: ${ambiguous.joinToString(";")}"
        )
        actual["ablation.REMOVE_RELATIONSHIP_DIRECTION.actual"] = relationshipAblation.result
        actual["ablation.REMOVE_RELATIONSHIP_DIRECTION.cause"] = relationshipAblation.cause

        val c1 = success("P1_STRICT_CONFLICT", fixtures.request(fixtures.coreCase("M", "C1")))!!
        val c2 = success("P1_STRICT_CONFLICT", fixtures.request(fixtures.coreCase("M", "C2")))!!
        val consumer = StructuredDirectionConsumer()
        val c1Extraction = consumer.extract(DirectionlessJudgment(c1.hypothesisId, c1.evaluatedRelationships, c1.evaluatedAt))
        val c2Extraction = consumer.extract(DirectionlessJudgment(c2.hypothesisId, c2.evaluatedRelationships, c2.evaluatedAt))
        actual["ablation.REMOVE_JUDGMENT_DIRECTION.C1.extraction"] = c1Extraction.render()
        actual["ablation.REMOVE_JUDGMENT_DIRECTION.C2.extraction"] = c2Extraction.render()
        val directionsRepresented = listOf(c1Extraction, c2Extraction).filterIsInstance<DirectionExtraction.Represented>()
        val judgmentAblation = AblationObservation(
            if (directionsRepresented.size == 2 && directionsRepresented.map { it.direction }.toSet().size == 2) "PASS" else "FAIL_REQUIRED_SEMANTICS",
            "C1 extraction=${c1Extraction.render()}, C2 extraction=${c2Extraction.render()}; no alternative field was interpreted"
        )
        actual["ablation.REMOVE_JUDGMENT_DIRECTION.actual"] = judgmentAblation.result
        actual["ablation.REMOVE_JUDGMENT_DIRECTION.cause"] = judgmentAblation.cause

        val unretained = baseline.copy(evaluatedRelationships = emptyList())
        val auditComplete = unretained.evaluatedRelationships.containsAll(case.relationships)
        val retainedAblation = AblationObservation(
            if (auditComplete) "PASS" else "FAIL_INVARIANT",
            "Retained ${unretained.evaluatedRelationships.size} of ${case.relationships.size} evaluated relationships"
        )
        actual["ablation.REMOVE_RETAINED_RELATIONSHIPS.actual"] = retainedAblation.result
        actual["ablation.REMOVE_RETAINED_RELATIONSHIPS.cause"] = retainedAblation.cause

        fixtures.properties.required("ablation.ids").split(',').forEach { id ->
            diagnostics += "ablation.$id.expected=${fixtures.expected("ablation.$id.expected")}"
            diagnostics += "ablation.$id.actual=${actual.getValue("ablation.$id.actual")}"
            diagnostics += "ablation.$id.cause=${actual.getValue("ablation.$id.cause")}"
        }
    }

    private fun observeStructuralValidation(actual: MutableMap<String, String>, diagnostics: MutableList<String>) {
        val evidence = fixtures.evidence("M", "SUPPORT_080")
        val first = relationship("STRUCT", 1, evidence.id.value, "M1", RelationshipDirection.SUPPORTS)
        val second = first.copy(direction = RelationshipDirection.CONTRADICTS)
        val error = runCatching { engine.evaluate("P1_STRICT_CONFLICT", request("M1", mapOf(evidence.id to evidence), listOf(first, second))) }.exceptionOrNull()
        actual["structural.duplicateRelationshipId.errorType"] = error?.javaClass?.simpleName ?: "NONE"
        actual["structural.duplicateRelationshipId.message"] = error?.message ?: "NONE"
        diagnostics += "structural.duplicateRelationshipId=${actual["structural.duplicateRelationshipId.errorType"]}:${actual["structural.duplicateRelationshipId.message"]}"
    }

    private fun assertFixtureContract() {
        assertEquals("1.0", fixtures.properties.required("fixture.version"))
        assertEquals("2adf94cdb0e9120e259378cd41283e2877839b14", fixtures.properties.required("protocol.sourceCommit"))
        assertEquals(24, fixtures.expectedLines.keys.count { it.matches(Regex("core\\.[MS]\\.C[1-6]\\.P[12]")) })
        assertEquals(46, fixtures.expectedLines.keys.count { it.startsWith("integrity.") })
        val evidence = fixtures.evidence("M", "SUPPORT_080")
        val duplicate = relationship("ASSERT", 1, evidence.id.value, "M1", RelationshipDirection.SUPPORTS)
        assertFailsWith<StructuralInputViolation> {
            engine.evaluate("P1_STRICT_CONFLICT", request("M1", mapOf(evidence.id to evidence), listOf(duplicate, duplicate)))
        }
    }

    private fun recordInvalid(actual: MutableMap<String, String>, scenario: String, request: ExperimentalEvaluationRequest) {
        val outcomes = listOf("P1_STRICT_CONFLICT", "P2_WEIGHTED_BALANCE")
            .map { policy -> engine.evaluate(policy, request) }
        require(outcomes.distinct().size == 1) {
            "Validation must be policy-independent for $scenario: $outcomes"
        }
        val outcome = outcomes.first()
        actual["integrity.$scenario.validation"] = (outcome as? EvaluationOutcome.Invalid)?.code?.name ?: "VALID"
        actual["integrity.$scenario.judgmentProduced"] = (outcome is EvaluationOutcome.Success).toString()
    }

    private fun success(policy: String, request: ExperimentalEvaluationRequest): ExperimentalJudgment? =
        (engine.evaluate(policy, request) as? EvaluationOutcome.Success)?.judgment

    private fun request(hypothesisId: String, plan: AdaptedEvidencePlan) =
        request(hypothesisId, mapOf(plan.evidence.id to plan.evidence), plan.relationships)

    private fun request(hypothesisId: String, evidence: Map<Identifier, Evidence>, relationships: List<ExperimentalEvidenceRelationship>) =
        ExperimentalEvaluationRequest(Identifier(hypothesisId), evidence, relationships, fixtures.instant)

    private fun relationship(scenario: String, ordinal: Int, evidenceId: String, hypothesisId: String, direction: RelationshipDirection) =
        ExperimentalEvidenceRelationship(Identifier("M-R-$scenario-${ordinal.toString().padStart(2, '0')}"), Identifier(evidenceId), Identifier(hypothesisId), direction)

    private fun evidenceFieldsEqual(left: Evidence, right: Evidence) =
        left.id == right.id && left.observationIds == right.observationIds && left.confidence == right.confidence && left.recordedAt == right.recordedAt

    private fun traceDirection(trace: String): JudgmentDirection = JudgmentDirection.valueOf(trace.split('|')[1])

    private fun writeObserved(actual: Map<String, String>, diagnostics: List<String>) {
        val directory = Path.of("build", "exp-0003")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("complete-observed.snapshot"), actual.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
        Files.writeString(directory.resolve("complete-comparison.log"), diagnostics.joinToString("\n", postfix = "\n"))
    }
}

private data class ExistingKernelReductionObservation(
    val scenario: String,
    val projection: ExistingJudgmentProjection,
    val expectedP1Direction: JudgmentDirection,
    val expectedP2Direction: JudgmentDirection
) {
    fun serialize() = listOf(
        "scenario:$scenario", "projection:$projection",
        "expectedP1:$expectedP1Direction", "expectedP2:$expectedP2Direction"
    ).joinToString("|")
}

private data class ExistingJudgmentProjection(
    val hypothesisId: Identifier,
    val conclusion: String,
    val confidence: String,
    val createdAt: String
)

private data class ReductionCollision(
    val policy: String,
    val observations: List<ExistingKernelReductionObservation>
) {
    fun serialize(): String = "$policy:${observations.joinToString(",") { "${it.scenario}:${if (policy == "P1") it.expectedP1Direction else it.expectedP2Direction}" }}=>${observations.first().projection}"
}

private data class UndirectedRelationship(val id: Identifier, val evidenceId: Identifier, val hypothesisId: Identifier)

private class UndirectedRelationshipExplorer(private val engine: ExperimentalReasoningEngine) {
    fun possibleDirections(
        policy: String,
        case: FrozenCoreCase,
        fixtures: FrozenFixtures
    ): Set<JudgmentDirection> {
        val undirected = case.relationships.map { UndirectedRelationship(it.id, it.evidenceId, it.hypothesisId) }
        return assignments(undirected.size).mapNotNull { directions ->
            val relationships = undirected.zip(directions).map { (relation, direction) ->
                ExperimentalEvidenceRelationship(relation.id, relation.evidenceId, relation.hypothesisId, direction)
            }
            val request = ExperimentalEvaluationRequest(case.hypothesisId, case.evidenceById, relationships, fixtures.instant)
            (engine.evaluate(policy, request) as? EvaluationOutcome.Success)?.judgment?.direction
        }.toSet()
    }

    private fun assignments(size: Int): List<List<RelationshipDirection>> =
        if (size == 0) listOf(emptyList()) else assignments(size - 1).flatMap { prefix ->
            listOf(prefix + RelationshipDirection.SUPPORTS, prefix + RelationshipDirection.CONTRADICTS)
        }
}

private sealed interface DirectionExtraction {
    data class Represented(val direction: JudgmentDirection) : DirectionExtraction
    data object NotRepresentable : DirectionExtraction
}

private interface StructuredDirectionSource {
    fun extractStructuredDirection(): DirectionExtraction
}

private class StructuredDirectionConsumer {
    fun extract(source: StructuredDirectionSource): DirectionExtraction = source.extractStructuredDirection()
}

private data class DirectionlessJudgment(
    val hypothesisId: Identifier,
    val relationships: List<ExperimentalEvidenceRelationship>,
    val evaluatedAt: io.flooow.kernel.language.Timestamp
) : StructuredDirectionSource {
    override fun extractStructuredDirection(): DirectionExtraction = DirectionExtraction.NotRepresentable
}

private fun DirectionExtraction.render(): String = when (this) {
    is DirectionExtraction.Represented -> "REPRESENTED:$direction"
    DirectionExtraction.NotRepresentable -> "NOT_REPRESENTABLE"
}

private data class AblationObservation(val result: String, val cause: String)

private fun BigDecimal.canonical(): String {
    val text = stripTrailingZeros().toPlainString()
    return if ('.' in text) text else "$text.0"
}

private fun <T> List<T>.permutations(): List<List<T>> = when {
    size <= 1 -> listOf(this)
    else -> indices.flatMap { index ->
        val head = this[index]
        (take(index) + drop(index + 1)).permutations().map { listOf(head) + it }
    }
}
