package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfidencePolicyTest {

    private val createdAt =
        Timestamp.parse("2026-07-19T10:00:00Z")

    @Test
    fun `determines confidence from hypothesis and aggregated evidence`() {
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
                        confidence = Confidence(0.60),
                        recordedAt = createdAt
                    )
                )
            )

        val aggregatedEvidence =
            AggregatedEvidence(
                evidenceSet = evidenceSet,
                confidence = Confidence(0.60)
            )

        val policy =
            AveragingTestConfidencePolicy()

        val confidence =
            policy.determine(
                hypothesis = hypothesis,
                aggregatedEvidence = aggregatedEvidence
            )

        assertEquals(
            expected = 0.70,
            actual = confidence.value,
            absoluteTolerance = 0.000000001
        )
    }

    private class AveragingTestConfidencePolicy :
        ConfidencePolicy {

        override fun determine(
            hypothesis: Hypothesis,
            aggregatedEvidence: AggregatedEvidence
        ): Confidence =
            Confidence(
                (
                    hypothesis.confidence.value +
                        aggregatedEvidence.confidence.value
                    ) / 2.0
            )
    }
}
