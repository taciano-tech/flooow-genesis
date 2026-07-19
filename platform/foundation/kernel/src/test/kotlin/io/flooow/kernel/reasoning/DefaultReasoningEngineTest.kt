package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultReasoningEngineTest {

    private val timestamp =
        Timestamp(Instant.parse("2026-07-19T00:00:00Z"))

    private val hypothesis = Hypothesis(
        id = Identifier("hypothesis-001"),
        statement = "Demand will increase next quarter",
        confidence = Confidence(0.75),
        createdAt = timestamp
    )

    private val evidenceSet = EvidenceSet(
        setOf(
            Evidence(
                id = Identifier("evidence-001"),
                observationIds = setOf(Identifier("observation-001")),
                confidence = Confidence(0.90),
                recordedAt = timestamp
            )
        )
    )

    private val request = EvaluationRequest(
        hypothesis = hypothesis,
        evidenceSet = evidenceSet
    )

    private val expectedResult = EvaluationResult(
        judgment = Judgment(
            id = Identifier("judgment-001"),
            hypothesisId = hypothesis.id,
            conclusion = "Available evidence supports the hypothesis",
            confidence = Confidence(0.82),
            createdAt = timestamp
        ),
        evaluatedEvidence = evidenceSet,
        evaluatedAt = timestamp
    )

    @Test
    fun `delegates evaluation to the first supported strategy`() {
        var unsupportedStrategyEvaluated = false
        var supportedStrategyEvaluated = false

        val unsupportedStrategy = object : EvaluationStrategy {
            override fun supports(request: EvaluationRequest): Boolean =
                false

            override fun evaluate(request: EvaluationRequest): EvaluationResult {
                unsupportedStrategyEvaluated = true
                return expectedResult
            }
        }

        val supportedStrategy = object : EvaluationStrategy {
            override fun supports(request: EvaluationRequest): Boolean =
                true

            override fun evaluate(request: EvaluationRequest): EvaluationResult {
                supportedStrategyEvaluated = true
                return expectedResult
            }
        }

        val engine = DefaultReasoningEngine(
            listOf(unsupportedStrategy, supportedStrategy)
        )

        val result = engine.evaluate(request)

        assertEquals(expectedResult, result)
        assertFalse(unsupportedStrategyEvaluated)
        assertTrue(supportedStrategyEvaluated)
    }

    @Test
    fun `uses only the first supported strategy`() {
        var secondStrategyEvaluated = false

        val firstStrategy = object : EvaluationStrategy {
            override fun supports(request: EvaluationRequest): Boolean =
                true

            override fun evaluate(request: EvaluationRequest): EvaluationResult =
                expectedResult
        }

        val secondStrategy = object : EvaluationStrategy {
            override fun supports(request: EvaluationRequest): Boolean =
                true

            override fun evaluate(request: EvaluationRequest): EvaluationResult {
                secondStrategyEvaluated = true
                return expectedResult
            }
        }

        val engine = DefaultReasoningEngine(
            listOf(firstStrategy, secondStrategy)
        )

        engine.evaluate(request)

        assertFalse(secondStrategyEvaluated)
    }

    @Test
    fun `rejects an empty strategy collection`() {
        assertFailsWith<IllegalArgumentException> {
            DefaultReasoningEngine(emptyList())
        }
    }

    @Test
    fun `fails when no strategy supports the request`() {
        val unsupportedStrategy = object : EvaluationStrategy {
            override fun supports(request: EvaluationRequest): Boolean =
                false

            override fun evaluate(request: EvaluationRequest): EvaluationResult =
                expectedResult
        }

        val engine = DefaultReasoningEngine(
            listOf(unsupportedStrategy)
        )

        assertFailsWith<IllegalStateException> {
            engine.evaluate(request)
        }
    }
}
