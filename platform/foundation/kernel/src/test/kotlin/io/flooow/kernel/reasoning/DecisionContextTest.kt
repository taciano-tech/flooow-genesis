package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DecisionContextTest {

    private val timestamp =
        Timestamp(Instant.parse("2026-07-18T14:00:00Z"))

    @Test
    fun `creates a coherent decision context`() {
        val hypothesis = Hypothesis(
            id = Identifier("hypothesis-001"),
            statement = "Demand will increase next quarter",
            confidence = Confidence(0.75),
            createdAt = timestamp
        )

        val evidence = Evidence(
            id = Identifier("evidence-001"),
            observationIds = setOf(Identifier("observation-001")),
            confidence = Confidence(0.90),
            recordedAt = timestamp
        )

        val judgment = Judgment(
            id = Identifier("judgment-001"),
            hypothesisId = hypothesis.id,
            conclusion = "Available evidence supports the hypothesis",
            confidence = Confidence(0.82),
            createdAt = timestamp
        )

        val context = DecisionContext(
            hypothesis = hypothesis,
            evidenceSet = EvidenceSet(setOf(evidence)),
            judgment = judgment
        )

        assertEquals(hypothesis, context.hypothesis)
        assertEquals(judgment, context.judgment)
        assertEquals(1, context.evidenceSet.size())
    }

    @Test
    fun `rejects judgment associated with another hypothesis`() {
        val hypothesis = Hypothesis(
            id = Identifier("hypothesis-001"),
            statement = "Demand will increase next quarter",
            confidence = Confidence(0.75),
            createdAt = timestamp
        )

        val evidence = Evidence(
            id = Identifier("evidence-001"),
            observationIds = setOf(Identifier("observation-001")),
            confidence = Confidence(0.90),
            recordedAt = timestamp
        )

        val judgment = Judgment(
            id = Identifier("judgment-001"),
            hypothesisId = Identifier("hypothesis-002"),
            conclusion = "Available evidence supports another hypothesis",
            confidence = Confidence(0.82),
            createdAt = timestamp
        )

        assertFailsWith<IllegalArgumentException> {
            DecisionContext(
                hypothesis = hypothesis,
                evidenceSet = EvidenceSet(setOf(evidence)),
                judgment = judgment
            )
        }
    }
}
