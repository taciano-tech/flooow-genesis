package io.flooow.kernel.model

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrganizationalLanguageTest {

    private val timestamp =
        Timestamp.parse("2026-07-18T12:00:00Z")

    @Test
    fun `creates an observation`() {
        val observation = Observation(
            id = Identifier("observation-001"),
            description = "Customer demand increased during the quarter",
            observedAt = timestamp
        )

        assertEquals(
            "Customer demand increased during the quarter",
            observation.description
        )
    }

    @Test
    fun `rejects an observation without a description`() {
        assertFailsWith<IllegalArgumentException> {
            Observation(
                id = Identifier("observation-001"),
                description = " ",
                observedAt = timestamp
            )
        }
    }

    @Test
    fun `evidence must reference observations`() {
        assertFailsWith<IllegalArgumentException> {
            Evidence(
                id = Identifier("evidence-001"),
                observationIds = emptySet(),
                confidence = Confidence(0.8),
                recordedAt = timestamp
            )
        }
    }

    @Test
    fun `creates evidence from observations`() {
        val evidence = Evidence(
            id = Identifier("evidence-001"),
            observationIds = setOf(Identifier("observation-001")),
            confidence = Confidence(0.8),
            recordedAt = timestamp
        )

        assertEquals(1, evidence.observationIds.size)
        assertEquals(0.8, evidence.confidence.value)
    }

    @Test
    fun `decision must reference evidence`() {
        assertFailsWith<IllegalArgumentException> {
            Decision(
                id = Identifier("decision-001"),
                statement = "Increase production capacity",
                evidenceIds = emptySet(),
                decidedAt = timestamp
            )
        }
    }

    @Test
    fun `creates a decision grounded in evidence`() {
        val decision = Decision(
            id = Identifier("decision-001"),
            statement = "Increase production capacity",
            evidenceIds = setOf(Identifier("evidence-001")),
            decidedAt = timestamp
        )

        assertEquals(1, decision.evidenceIds.size)
        assertEquals(
            "Increase production capacity",
            decision.statement
        )
    }
}
