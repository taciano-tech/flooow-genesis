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
import kotlin.test.assertSame

class DeterministicHypothesisEvaluatorTest {

    private val instant =
        Instant.parse("2026-07-19T12:00:00Z")

    private val clock =
        Clock.fixed(instant, ZoneOffset.UTC)

    private val createdAt =
        Timestamp.parse("2026-07-19T10:00:00Z")

    @Test
    fun `creates a deterministic judgment using aggregated evidence`() {
        val hypothesis =
            Hypothesis(
                id = Identifier("hypothesis-001"),
                statement = "Demand will increase",
                confidence = Confidence(0.80),
                createdAt = createdAt
            )

        val evidenceSet =
            EvidenceSet(
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

        val aggregatedConfidence =
            Confidence(0.65)

        val evidenceAggregator =
            RecordingEvidenceAggregator(
                result = AggregatedEvidence(
                    evidenceSet = evidenceSet,
                    confidence = aggregatedConfidence
                )
            )

        val evaluator =
            DeterministicHypothesisEvaluator(
                evidenceAggregator = evidenceAggregator,
                clock = clock
            )

        val judgment =
            evaluator.evaluate(
                hypothesis = hypothesis,
                evidenceSet = evidenceSet
            )

        assertSame(
            evidenceSet,
            evidenceAggregator.receivedEvidenceSet
        )

        assertEquals(
            Identifier("judgment-hypothesis-001"),
            judgment.id
        )

        assertEquals(
            hypothesis.id,
            judgment.hypothesisId
        )

        assertEquals(
            aggregatedConfidence,
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

    private class RecordingEvidenceAggregator(
        private val result: AggregatedEvidence
    ) : EvidenceAggregator {

        var receivedEvidenceSet: EvidenceSet? = null
            private set

        override fun aggregate(
            evidenceSet: EvidenceSet
        ): AggregatedEvidence {
            receivedEvidenceSet = evidenceSet
            return result
        }
    }
}
