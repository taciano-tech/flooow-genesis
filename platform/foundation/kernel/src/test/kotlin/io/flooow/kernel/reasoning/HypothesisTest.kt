package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HypothesisTest {

    @Test
    fun `creates a valid hypothesis`() {
        val hypothesis = Hypothesis(
            id = Identifier("hypothesis-001"),
            statement = "Demand will increase next quarter",
            confidence = Confidence(0.75),
            createdAt = Timestamp(Instant.parse("2026-07-18T12:00:00Z"))
        )

        assertEquals("hypothesis-001", hypothesis.id.value)
        assertEquals("Demand will increase next quarter", hypothesis.statement)
        assertEquals(0.75, hypothesis.confidence.value)
    }

    @Test
    fun `rejects a blank statement`() {
        assertFailsWith<IllegalArgumentException> {
            Hypothesis(
                id = Identifier("hypothesis-002"),
                statement = " ",
                confidence = Confidence(0.50),
                createdAt = Timestamp(Instant.parse("2026-07-18T12:00:00Z"))
            )
        }
    }
}
