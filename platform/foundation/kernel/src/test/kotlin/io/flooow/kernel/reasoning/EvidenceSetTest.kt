package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EvidenceSetTest {

    @Test
    fun `creates a valid evidence set`() {
        val evidence = Evidence(
            id = Identifier("evidence-001"),
            observationIds = setOf(Identifier("obs-001")),
            confidence = Confidence(0.90),
            recordedAt = Timestamp(Instant.parse("2026-07-18T13:00:00Z"))
        )

        val evidenceSet = EvidenceSet(setOf(evidence))

        assertEquals(1, evidenceSet.size())
    }

    @Test
    fun `rejects an empty evidence set`() {
        assertFailsWith<IllegalArgumentException> {
            EvidenceSet(emptySet())
        }
    }
}
