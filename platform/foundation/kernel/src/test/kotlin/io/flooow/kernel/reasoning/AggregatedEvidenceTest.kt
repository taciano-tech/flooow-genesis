package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import kotlin.test.Test
import kotlin.test.assertEquals

class AggregatedEvidenceTest {

    @Test
    fun `stores aggregated evidence`() {

        val timestamp =
            Timestamp.parse("2026-07-19T12:00:00Z")

        val evidenceSet =
            EvidenceSet(
                setOf(
                    Evidence(
                        id = Identifier("evidence-001"),
                        observationIds = setOf(
                            Identifier("observation-001")
                        ),
                        confidence = Confidence(0.90),
                        recordedAt = timestamp
                    )
                )
            )

        val aggregated =
            AggregatedEvidence(
                evidenceSet = evidenceSet,
                confidence = Confidence(0.90)
            )

        assertEquals(
            evidenceSet,
            aggregated.evidenceSet
        )

        assertEquals(
            Confidence(0.90),
            aggregated.confidence
        )
    }
}
