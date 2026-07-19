package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluationStrategyTest {

    private val timestamp =
        Timestamp(Instant.parse("2026-07-19T00:00:00Z"))

    @Test
    fun `supports and evaluates a reasoning request`() {
        val hypothesis = Hypothesis(
            id = Identifier("hypothesis-001"),
            statement = "Demand will increase next quarter",
            confidence = Confidence(0.75),
            createdAt = timestamp
        )

        val evidence = Evidence(
            id = Identifier("evidence-001"),
            observationIds = setOf(Identifier("observation-001")),
            confidence = Confidence(0.90),
            recordedAt = timestamp
        )

        val evidenceSet = EvidenceSet(setOf(evidence))

        val judgment = Judgment(
            id = Identifier("judgment-001"),
            hypothesisId = hypothesis.id,
            conclusion = "Available evidence supports the hypothesis",
            confidence = Confidence(0.82),
            createdAt = timestamp
        )

        val request = EvaluationRequest(
            hypothesis = hypothesis,
            evidenceSet = evidenceSet
        )

        val expectedResult = EvaluationResult(
            judgment = judgment,
            evaluatedEvidence = evidenceSet,
            evaluatedAt = timestamp
        )

        val strategy = object : EvaluationStrategy {
            override fun supports(request: EvaluationRequest): Boolean =
                request.hypothesis.id == hypothesis.id

            override fun evaluate(request: EvaluationRequest): EvaluationResult =
                expectedResult
        }

        assertTrue(strategy.supports(request))
        assertEquals(expectedResult, strategy.evaluate(request))
    }
}
