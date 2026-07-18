package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs

class ReasoningModelAlignmentTest {

    @Test
    fun `reasoning hypothesis implements organizational hypothesis contract`() {
        val hypothesis = Hypothesis(
            id = Identifier("hypothesis-1"),
            statement = "Customer retention improves with faster onboarding",
            confidence = Confidence(0.75),
            createdAt = Timestamp(Instant.ofEpochMilli(1L))
        )

        assertIs<io.flooow.kernel.model.Hypothesis>(hypothesis)
    }

    @Test
    fun `reasoning judgment implements organizational judgment contract`() {
        val judgment = Judgment(
            id = Identifier("judgment-1"),
            hypothesisId = Identifier("hypothesis-1"),
            conclusion = "Available evidence supports the hypothesis",
            confidence = Confidence(0.80),
            createdAt = Timestamp(Instant.ofEpochMilli(2L))
        )

        assertIs<io.flooow.kernel.model.Judgment>(judgment)
    }
}
