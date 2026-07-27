package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ReasoningModuleTest {

    private val instant =
        Instant.parse("2026-07-19T12:00:00Z")

    private val clock =
        Clock.fixed(instant, ZoneOffset.UTC)

    private val createdAt =
        Timestamp.parse("2026-07-19T10:00:00Z")

    @Test
    fun `creates deterministic reasoning engine with default policy`() {
        val request =
            EvaluationRequest(
                hypothesis =
                    Hypothesis(
                        id = Identifier("hypothesis-001"),
                        statement = "Demand will increase",
                        confidence = Confidence(0.20),
                        createdAt = createdAt
                    ),
                evidenceSet =
                    EvidenceSet(
                        setOf(
                            Evidence(
                                id = Identifier("evidence-001"),
                                observationIds =
                                    setOf(
                                        Identifier("observation-001")
                                    ),
                                confidence = Confidence(0.60),
                                recordedAt = createdAt
                            ),
                            Evidence(
                                id = Identifier("evidence-002"),
                                observationIds =
                                    setOf(
                                        Identifier("observation-002")
                                    ),
                                confidence = Confidence(0.80),
                                recordedAt = createdAt
                            )
                        )
                    )
            )

        val engine =
            ReasoningModule.deterministic(
                clock = clock
            )

        val result =
            engine.evaluate(request)

        assertEquals(
            Confidence(0.70),
            result.judgment.confidence
        )

        assertEquals(
            Identifier("judgment-hypothesis-001"),
            result.judgment.id
        )

        assertEquals(
            request.hypothesis.id,
            result.judgment.hypothesisId
        )

        assertEquals(
            Timestamp(instant),
            result.judgment.createdAt
        )

        assertEquals(
            Timestamp(instant),
            result.evaluatedAt
        )

        assertEquals(
            request.evidenceSet,
            result.evaluatedEvidence
        )
    }

    @Test
    fun `creates deterministic reasoning engine with supplied policy`() {
        val request =
            EvaluationRequest(
                hypothesis =
                    Hypothesis(
                        id = Identifier("hypothesis-002"),
                        statement = "Supply risk will decrease",
                        confidence = Confidence(0.80),
                        createdAt = createdAt
                    ),
                evidenceSet =
                    EvidenceSet(
                        setOf(
                            Evidence(
                                id = Identifier("evidence-003"),
                                observationIds =
                                    setOf(
                                        Identifier("observation-003")
                                    ),
                                confidence = Confidence(0.60),
                                recordedAt = createdAt
                            )
                        )
                    )
            )

        val engine =
            ReasoningModule.deterministic(
                confidencePolicy =
                    WeightedConfidencePolicy(
                        hypothesisWeight = 0.25,
                        aggregatedEvidenceWeight = 0.75
                    ),
                clock = clock
            )

        val result =
            engine.evaluate(request)

        assertEquals(
            expected = 0.65,
            actual = result.judgment.confidence.value,
            absoluteTolerance = 1e-9
        )
    }
}
