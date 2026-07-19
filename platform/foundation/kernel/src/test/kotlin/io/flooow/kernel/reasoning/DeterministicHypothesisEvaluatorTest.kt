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

class DeterministicHypothesisEvaluatorTest {

    private val instant =
        Instant.parse("2026-07-19T12:00:00Z")

    private val clock =
        Clock.fixed(instant, ZoneOffset.UTC)

    private val createdAt =
        Timestamp.parse("2026-07-19T10:00:00Z")

    @Test
    fun `creates a deterministic judgment`() {

        val hypothesis = Hypothesis(
            id = Identifier("hypothesis-001"),
            statement = "Demand will increase",
            confidence = Confidence(0.80),
            createdAt = createdAt
        )

        val evidenceSet = EvidenceSet(
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

        val evaluator =
            DeterministicHypothesisEvaluator(clock)

        val judgment =
            evaluator.evaluate(hypothesis, evidenceSet)

        assertEquals(
            Identifier("judgment-hypothesis-001"),
            judgment.id
        )

        assertEquals(
            hypothesis.id,
            judgment.hypothesisId
        )

        assertEquals(
            hypothesis.confidence,
            judgment.confidence
        )

        assertEquals(
            "Evidence supports the hypothesis.",
            judgment.conclusion
        )

        assertEquals(
            Timestamp(instant),
            judgment.createdAt
        )
    }
}
