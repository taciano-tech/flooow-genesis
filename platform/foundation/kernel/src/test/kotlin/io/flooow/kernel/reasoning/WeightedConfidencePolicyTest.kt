package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WeightedConfidencePolicyTest {

    private val createdAt =
        Timestamp.parse("2026-07-19T10:00:00Z")

    @Test
    fun `combines hypothesis and aggregated evidence confidence`() {

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

        val result =
            WeightedConfidencePolicy(
                hypothesisWeight = 0.30,
                aggregatedEvidenceWeight = 0.70
            ).determine(
                hypothesis,
                aggregatedEvidence
            )

        assertEquals(
            expected = 0.66,
            actual = result.value,
            absoluteTolerance = 1e-9
        )
    }

    @Test
    fun `rejects invalid weights`() {

        assertFailsWith<IllegalArgumentException> {
            WeightedConfidencePolicy(
                hypothesisWeight = 0.40,
                aggregatedEvidenceWeight = 0.40
            )
        }
    }
}
