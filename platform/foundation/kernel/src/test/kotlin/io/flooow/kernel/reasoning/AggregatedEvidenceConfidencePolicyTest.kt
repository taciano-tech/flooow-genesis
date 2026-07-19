package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import kotlin.test.Test
import kotlin.test.assertEquals

class AggregatedEvidenceConfidencePolicyTest {

    private val createdAt =
        Timestamp.parse("2026-07-19T10:00:00Z")

    @Test
    fun `returns confidence from aggregated evidence`() {
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
                        confidence = Confidence(0.65),
                        recordedAt = createdAt
                    )
                )
            )

        val aggregatedEvidence =
            AggregatedEvidence(
                evidenceSet = evidenceSet,
                confidence = Confidence(0.65)
            )

        val result =
            AggregatedEvidenceConfidencePolicy()
                .determine(
                    hypothesis = hypothesis,
                    aggregatedEvidence = aggregatedEvidence
                )

        assertEquals(
            aggregatedEvidence.confidence,
            result
        )
    }

    @Test
    fun `ignores hypothesis confidence`() {
        val hypothesis =
            Hypothesis(
                id = Identifier("hypothesis-001"),
                statement = "Demand will increase",
                confidence = Confidence(0.20),
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

        val aggregatedEvidence =
            AggregatedEvidence(
                evidenceSet = evidenceSet,
                confidence = Confidence(0.90)
            )

        val result =
            AggregatedEvidenceConfidencePolicy()
                .determine(
                    hypothesis = hypothesis,
                    aggregatedEvidence = aggregatedEvidence
                )

        assertEquals(
            Confidence(0.90),
            result
        )
    }
}
