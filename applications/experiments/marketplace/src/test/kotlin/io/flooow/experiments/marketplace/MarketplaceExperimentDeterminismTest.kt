package io.flooow.experiments.marketplace

import kotlin.test.Test
import kotlin.test.assertEquals

class MarketplaceExperimentDeterminismTest {

    @Test
    fun `marketplace reasoning is deterministic`() {

        val firstEvaluation =
            MarketplaceExperiment.orderApprovalEvaluation

        val secondEvaluation =
            MarketplaceExperiment.orderApprovalEvaluation

        assertEquals(
            firstEvaluation,
            secondEvaluation
        )

        val firstDecision =
            MarketplaceExperiment.orderApprovalDecision

        val secondDecision =
            MarketplaceExperiment.orderApprovalDecision

        assertEquals(
            firstDecision,
            secondDecision
        )
    }

    @Test
    fun `decision trace remains coherent`() {

        val decision =
            MarketplaceExperiment.orderApprovalDecision

        val evidence =
            MarketplaceExperiment.orderCreatedEvidence

        val hypothesis =
            MarketplaceExperiment.orderApprovalHypothesis

        val judgment =
            MarketplaceExperiment.orderApprovalEvaluation.judgment

        assertEquals(
            setOf(evidence.id),
            decision.evidenceIds
        )

        assertEquals(
            hypothesis.id,
            judgment.hypothesisId
        )
    }
}
