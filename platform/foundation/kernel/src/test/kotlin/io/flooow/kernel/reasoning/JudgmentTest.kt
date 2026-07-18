package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JudgmentTest {

    @Test
    fun `creates a valid judgment`() {
        val judgment = Judgment(
            id = Identifier("judgment-001"),
            hypothesisId = Identifier("hypothesis-001"),
            conclusion = "The hypothesis is sufficiently supported",
            confidence = Confidence(0.82),
            createdAt = Timestamp(Instant.parse("2026-07-18T12:30:00Z"))
        )

        assertEquals("judgment-001", judgment.id.value)
        assertEquals("hypothesis-001", judgment.hypothesisId.value)
        assertEquals(
            "The hypothesis is sufficiently supported",
            judgment.conclusion
        )
        assertEquals(0.82, judgment.confidence.value)
    }

    @Test
    fun `rejects a blank conclusion`() {
        assertFailsWith<IllegalArgumentException> {
            Judgment(
                id = Identifier("judgment-002"),
                hypothesisId = Identifier("hypothesis-001"),
                conclusion = " ",
                confidence = Confidence(0.60),
                createdAt = Timestamp(Instant.parse("2026-07-18T12:30:00Z"))
            )
        }
    }
}
