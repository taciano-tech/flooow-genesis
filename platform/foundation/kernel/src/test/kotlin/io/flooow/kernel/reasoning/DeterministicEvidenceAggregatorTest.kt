package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import kotlin.test.Test
import kotlin.test.assertEquals

class DeterministicEvidenceAggregatorTest {

    private val timestamp =
        Timestamp.parse("2026-07-19T12:00:00Z")

    @Test
    fun `aggregates a single evidence confidence`() {
        val evidenceSet =
            EvidenceSet(
                setOf(
                    evidence(
                        id = "evidence-001",
                        confidence = 0.80
                    )
                )
            )

        val result =
            DeterministicEvidenceAggregator()
                .aggregate(evidenceSet)

        assertEquals(
            evidenceSet,
            result.evidenceSet
        )

        assertEquals(
            Confidence(0.80),
            result.confidence
        )
    }

    @Test
    fun `aggregates multiple evidence confidences using arithmetic mean`() {
        val evidenceSet =
            EvidenceSet(
                setOf(
                    evidence(
                        id = "evidence-001",
                        confidence = 0.60
                    ),
                    evidence(
                        id = "evidence-002",
                        confidence = 0.80
                    ),
                    evidence(
                        id = "evidence-003",
                        confidence = 1.00
                    )
                )
            )

        val result =
            DeterministicEvidenceAggregator()
                .aggregate(evidenceSet)

        assertEquals(
            expected = 0.80,
            actual = result.confidence.value,
            absoluteTolerance = 0.000000001
        )
    }

    @Test
    fun `aggregation is independent of evidence iteration order`() {
        val first =
            evidence(
                id = "evidence-001",
                confidence = 0.40
            )

        val second =
            evidence(
                id = "evidence-002",
                confidence = 0.80
            )

        val aggregator =
            DeterministicEvidenceAggregator()

        val firstResult =
            aggregator.aggregate(
                EvidenceSet(
                    linkedSetOf(first, second)
                )
            )

        val secondResult =
            aggregator.aggregate(
                EvidenceSet(
                    linkedSetOf(second, first)
                )
            )

        assertEquals(
            firstResult.confidence,
            secondResult.confidence
        )
    }

    private fun evidence(
        id: String,
        confidence: Double
    ): Evidence =
        Evidence(
            id = Identifier(id),
            observationIds = setOf(
                Identifier("observation-$id")
            ),
            confidence = Confidence(confidence),
            recordedAt = timestamp
        )
}
