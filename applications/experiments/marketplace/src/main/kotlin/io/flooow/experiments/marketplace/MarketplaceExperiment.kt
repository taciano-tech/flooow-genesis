package io.flooow.experiments.marketplace

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Decision
import io.flooow.kernel.model.Evidence
import io.flooow.kernel.model.Observation
import io.flooow.kernel.reasoning.DecisionContext
import io.flooow.kernel.reasoning.EvaluationRequest
import io.flooow.kernel.reasoning.EvaluationResult
import io.flooow.kernel.reasoning.EvidenceSet
import io.flooow.kernel.reasoning.Hypothesis
import io.flooow.kernel.reasoning.ReasoningConfiguration
import io.flooow.kernel.reasoning.ReasoningModule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

object MarketplaceExperiment {

    val orderCreatedObservation: Observation =
        Observation(
            id = Identifier("observation-marketplace-order-created"),
            description = "A customer created a marketplace order.",
            observedAt = Timestamp.parse("2026-07-29T18:00:00Z")
        )

    val orderCreatedEvidence: Evidence =
        Evidence(
            id = Identifier("evidence-marketplace-order-created"),
            observationIds = setOf(orderCreatedObservation.id),
            confidence = Confidence.CERTAIN,
            recordedAt = Timestamp.parse("2026-07-29T18:00:01Z")
        )

    val orderApprovalHypothesis: Hypothesis =
        Hypothesis(
            id = Identifier("hypothesis-marketplace-order-approval"),
            statement = "The marketplace order can be approved.",
            confidence = Confidence.CERTAIN,
            createdAt = Timestamp.parse("2026-07-29T18:00:02Z")
        )

    private val reasoningClock: Clock =
        Clock.fixed(
            Instant.parse("2026-07-29T18:00:03Z"),
            ZoneOffset.UTC
        )

    private val reasoningEngine =
        ReasoningModule.deterministic(
            configuration =
                ReasoningConfiguration(
                    clock = reasoningClock
                )
        )

    val orderApprovalEvaluation: EvaluationResult =
        reasoningEngine.evaluate(
            EvaluationRequest(
                hypothesis = orderApprovalHypothesis,
                evidenceSet =
                    EvidenceSet(
                        evidences = setOf(orderCreatedEvidence)
                    )
            )
        )

    val orderApprovalDecisionContext: DecisionContext =
        DecisionContext(
            hypothesis = orderApprovalHypothesis,
            evidenceSet = orderApprovalEvaluation.evaluatedEvidence,
            judgment = orderApprovalEvaluation.judgment
        )

    val orderApprovalDecision: Decision =
        Decision(
            id = Identifier("decision-marketplace-order-approval"),
            statement = "Approve the marketplace order.",
            evidenceIds =
                orderApprovalDecisionContext
                    .evidenceSet
                    .evidences
                    .mapTo(mutableSetOf()) { evidence -> evidence.id },
            decidedAt = Timestamp.parse("2026-07-29T18:00:04Z")
        )
}
