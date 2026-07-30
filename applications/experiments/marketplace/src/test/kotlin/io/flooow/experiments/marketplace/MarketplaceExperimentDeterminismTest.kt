package io.flooow.experiments.marketplace

import kotlin.test.Test
import kotlin.test.assertEquals

class MarketplaceExperimentDeterminismTest {

    @Test
    fun `evaluation remains deterministic`() {

        val evaluation =
            MarketplaceExperiment.orderApprovalEvaluation

        assertEquals(
            MarketplaceExperiment.orderApprovalHypothesis.id,
            evaluation.judgment.hypothesisId
        )

        assertEquals(
            MarketplaceExperiment.orderApprovalEvaluation,
            evaluation
        )
    }

    @Test
    fun `evidence references observation`() {

        val observation =
            MarketplaceExperiment.orderCreatedObservation

        val evidence =
            MarketplaceExperiment.orderCreatedEvidence

        assertEquals(
            setOf(observation.id),
            evidence.observationIds
        )
    }

    @Test
    fun `decision references evaluated evidence`() {

        val decision =
            MarketplaceExperiment.orderApprovalDecision

        val evidence =
            MarketplaceExperiment.orderCreatedEvidence

        assertEquals(
            setOf(evidence.id),
            decision.evidenceIds
        )
    }

    @Test
    fun `judgment references hypothesis`() {

        val hypothesis =
            MarketplaceExperiment.orderApprovalHypothesis

        val judgment =
            MarketplaceExperiment.orderApprovalEvaluation.judgment

        assertEquals(
            hypothesis.id,
            judgment.hypothesisId
        )
    }

    @Test
    fun `decision context preserves evaluation`() {

        val context =
            MarketplaceExperiment.orderApprovalDecisionContext

        val evaluation =
            MarketplaceExperiment.orderApprovalEvaluation

        assertEquals(
            evaluation.judgment,
            context.judgment
        )

        assertEquals(
            evaluation.evaluatedEvidence,
            context.evidenceSet
        )
    }

    @Test
    fun `complete reasoning pipeline remains coherent`() {

        val observation =
            MarketplaceExperiment.orderCreatedObservation

        val evidence =
            MarketplaceExperiment.orderCreatedEvidence

        val hypothesis =
            MarketplaceExperiment.orderApprovalHypothesis

        val evaluation =
            MarketplaceExperiment.orderApprovalEvaluation

        val context =
            MarketplaceExperiment.orderApprovalDecisionContext

        val decision =
            MarketplaceExperiment.orderApprovalDecision

        assertEquals(
            setOf(observation.id),
            evidence.observationIds
        )

        assertEquals(
            hypothesis.id,
            evaluation.judgment.hypothesisId
        )

        assertEquals(
            evaluation.judgment,
            context.judgment
        )

        assertEquals(
            evaluation.evaluatedEvidence,
            context.evidenceSet
        )

        assertEquals(
            setOf(evidence.id),
            decision.evidenceIds
        )
    }
}
