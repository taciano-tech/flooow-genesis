package io.flooow.experiments.marketplace

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals

class MarketplaceExperimentJudgmentTest {

    @Test
    fun `judgment references the evaluated hypothesis`() {
        val evaluation = MarketplaceExperiment.orderApprovalEvaluation
        val hypothesis = MarketplaceExperiment.orderApprovalHypothesis

        assertEquals(
            hypothesis.id,
            evaluation.judgment.hypothesisId
        )
    }

    @Test
    fun `evaluation preserves the supplied evidence`() {
        val evaluation = MarketplaceExperiment.orderApprovalEvaluation
        val evidence = MarketplaceExperiment.orderCreatedEvidence

        assertEquals(
            setOf(evidence),
            evaluation.evaluatedEvidence.evidences
        )
    }

    @Test
    fun `judgment follows deterministic reasoning policy`() {
        val judgment =
            MarketplaceExperiment
                .orderApprovalEvaluation
                .judgment

        assertEquals(
            "Evidence supports the hypothesis.",
            judgment.conclusion
        )

        assertEquals(
            Confidence.CERTAIN,
            judgment.confidence
        )

        assertEquals(
            Timestamp.parse("2026-07-29T18:00:03Z"),
            judgment.createdAt
        )
    }

    @Test
    fun `evaluation uses the controlled reasoning clock`() {
        val evaluation = MarketplaceExperiment.orderApprovalEvaluation

        assertEquals(
            Timestamp.parse("2026-07-29T18:00:03Z"),
            evaluation.evaluatedAt
        )
    }
}
