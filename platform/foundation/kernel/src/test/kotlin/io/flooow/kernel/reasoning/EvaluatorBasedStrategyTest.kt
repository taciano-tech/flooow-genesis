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
import kotlin.test.assertTrue

class EvaluatorBasedStrategyTest {

    private val evaluationInstant =
        Instant.parse("2026-07-19T12:00:00Z")

    private val clock =
        Clock.fixed(evaluationInstant, ZoneOffset.UTC)

    private val createdAt =
        Timestamp.parse("2026-07-19T10:00:00Z")

    private val hypothesis = Hypothesis(
        id = Identifier("hypothesis-001"),
        statement = "Demand will increase next quarter",
        confidence = Confidence(0.75),
        createdAt = createdAt
    )

    private val evidenceSet = EvidenceSet(
        setOf(
            Evidence(
                id = Identifier("evidence-001"),
                observationIds = setOf(
                    Identifier("observation-001")
                ),
                confidence = Confidence(0.90),
                recordedAt = createdAt
            )
        )
    )

    private val request = EvaluationRequest(
        hypothesis = hypothesis,
        evidenceSet = evidenceSet
    )

    private val judgment = Judgment(
        id = Identifier("judgment-001"),
        hypothesisId = hypothesis.id,
        conclusion = "Available evidence supports the hypothesis",
        confidence = Confidence(0.82),
        createdAt = createdAt
    )

    @Test
    fun `supports evaluation requests`() {
        val strategy = EvaluatorBasedStrategy(
            evaluator = evaluatorReturning(judgment),
            clock = clock
        )

        assertTrue(strategy.supports(request))
    }

    @Test
    fun `delegates hypothesis evaluation to the evaluator`() {
        var evaluatedHypothesis: Hypothesis? = null
        var evaluatedEvidenceSet: EvidenceSet? = null

        val evaluator = object : HypothesisEvaluator {
            override fun evaluate(
                hypothesis: Hypothesis,
                evidenceSet: EvidenceSet
            ): Judgment {
                evaluatedHypothesis = hypothesis
                evaluatedEvidenceSet = evidenceSet
                return judgment
            }
        }

        val strategy = EvaluatorBasedStrategy(
            evaluator = evaluator,
            clock = clock
        )

        strategy.evaluate(request)

        assertEquals(hypothesis, evaluatedHypothesis)
        assertEquals(evidenceSet, evaluatedEvidenceSet)
    }

    @Test
    fun `produces an evaluation result from the evaluator judgment`() {
        val strategy = EvaluatorBasedStrategy(
            evaluator = evaluatorReturning(judgment),
            clock = clock
        )

        val result = strategy.evaluate(request)

        assertEquals(judgment, result.judgment)
        assertEquals(evidenceSet, result.evaluatedEvidence)
        assertEquals(
            Timestamp(evaluationInstant),
            result.evaluatedAt
        )
    }

    private fun evaluatorReturning(
        judgment: Judgment
    ): HypothesisEvaluator =
        object : HypothesisEvaluator {
            override fun evaluate(
                hypothesis: Hypothesis,
                evidenceSet: EvidenceSet
            ): Judgment =
                judgment
        }
}
