package io.flooow.marketplace.operations.economics.sales

import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssembler
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssemblyResult
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthCalculator
import io.flooow.marketplace.operations.economics.MarketplaceOrder
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthCalculationResult
import io.flooow.marketplace.operations.economics.evidence.ChangeSequenceCheckpoint
import io.flooow.marketplace.operations.economics.evidence.CheckpointAdvanceResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChange
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChangeFeed
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChangeFeedResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceReadResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceRepository
import io.flooow.marketplace.operations.economics.evidence.ProjectionName
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.temporal.ChronoUnit

sealed interface MarketplaceSalesIntelligenceProjectionProcessorResult {
    data class Success(
        val processedChanges: Int,
        val checkpoint: ChangeSequenceCheckpoint
    ) : MarketplaceSalesIntelligenceProjectionProcessorResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object NoChanges : MarketplaceSalesIntelligenceProjectionProcessorResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object CheckpointConflict : MarketplaceSalesIntelligenceProjectionProcessorResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object IntegrityFailure : MarketplaceSalesIntelligenceProjectionProcessorResult {
        override fun toString(): String = "[REDACTED]"
    }
}

class MarketplaceSalesIntelligenceProjectionProcessor(
    private val evidenceRepository: MarketplaceIndependentEconomicEvidenceRepository,
    private val changeFeed: MarketplaceEconomicEvidenceChangeFeed,
    private val projection: MarketplaceSalesIntelligenceProjection,
    private val clock: Clock = Clock.systemUTC(),
    private val calculator: (MarketplaceOrder) -> MarketplaceEconomicTruthCalculationResult = {
        order -> MarketplaceEconomicTruthCalculator.calculate(order)
    }
) {
    fun processBatch(
        organizationId: OrganizationId,
        limit: Int
    ): MarketplaceSalesIntelligenceProjectionProcessorResult {
        MarketplaceEconomicEvidenceChangeFeed.requireValidLimit(limit)

        val checkpoint = when (
            val result = changeFeed.currentCheckpoint(organizationId, PROJECTION_NAME)
        ) {
            is MarketplaceEconomicEvidenceChangeFeedResult.Success -> result.value
            MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure ->
                return MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure
        }

        val changes = when (
            val result = changeFeed.changesSince(organizationId, checkpoint, limit)
        ) {
            is MarketplaceEconomicEvidenceChangeFeedResult.Success -> result.value
            MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure ->
                return MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure
        }

        if (changes.isEmpty()) {
            return MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges
        }
        if (!isValidBatch(organizationId, checkpoint, changes)) {
            return MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure
        }

        for (change in changes) {
            when (materializeChange(organizationId, change)) {
                MaterializeOutcome.Complete -> Unit
                MaterializeOutcome.Failure ->
                    return MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure
            }
        }

        val destination = changes.last().changeSequence
        return when (
            val advance = changeFeed.advanceCheckpoint(
                organizationId,
                PROJECTION_NAME,
                checkpoint,
                destination
            )
        ) {
            MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure ->
                MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure

            is MarketplaceEconomicEvidenceChangeFeedResult.Success -> when (val value = advance.value) {
                is CheckpointAdvanceResult.Advanced ->
                    MarketplaceSalesIntelligenceProjectionProcessorResult.Success(
                        changes.size,
                        value.checkpoint
                    )

                is CheckpointAdvanceResult.Stale ->
                    if (value.currentCheckpoint >= destination) {
                        MarketplaceSalesIntelligenceProjectionProcessorResult.Success(
                            changes.size,
                            value.currentCheckpoint
                        )
                    } else {
                        MarketplaceSalesIntelligenceProjectionProcessorResult.CheckpointConflict
                    }

                CheckpointAdvanceResult.Regression ->
                    MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure
            }
        }
    }

    private fun materializeChange(
        organizationId: OrganizationId,
        change: MarketplaceEconomicEvidenceChange
    ): MaterializeOutcome {
        val current = when (
            val result = projection.currentBySubject(organizationId, change.subject.orderId)
        ) {
            is MarketplaceSalesIntelligenceProjectionReadResult.Success -> result.value
            MarketplaceSalesIntelligenceProjectionReadResult.IntegrityFailure ->
                return MaterializeOutcome.Failure
        }

        if (current != null &&
            current.lastAppliedChangeSequence >= change.changeSequence
        ) {
            return MaterializeOutcome.Complete
        }

        val versionedEvidence = when (val result = evidenceRepository.find(change.subject)) {
            is MarketplaceIndependentEconomicEvidenceReadResult.Found -> result.versionedEvidence
            MarketplaceIndependentEconomicEvidenceReadResult.NotFound,
            MarketplaceIndependentEconomicEvidenceReadResult.IntegrityFailure ->
                return MaterializeOutcome.Failure
        }

        val assembly = MarketplaceEconomicTruthAssembler.assemble(versionedEvidence.evidence)
        val state = when (assembly) {
            is MarketplaceEconomicTruthAssemblyResult.NotReady ->
                MarketplaceSalesIntelligenceState.Unresolved(
                    assemblyPolicyVersion = assembly.assemblyPolicyVersion,
                    reasons = assembly.reasons
                )

            is MarketplaceEconomicTruthAssemblyResult.Ready -> {
                val calculation = calculator(assembly.order)
                MarketplaceSalesIntelligenceState.Calculated(
                    assemblyPolicyVersion = assembly.assemblyPolicyVersion,
                    calculationPolicyVersion = calculationPolicyVersionOf(calculation),
                    calculationResult = calculation
                )
            }
        }

        val record = MarketplaceSalesIntelligenceProjectionRecord(
            organizationId = organizationId,
            marketplaceOrderId = change.subject.orderId,
            sourceEvidenceVersion = versionedEvidence.version,
            state = state,
            lastAppliedChangeSequence = change.changeSequence,
            projectedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
        )

        return when (projection.materializeIfNewer(record)) {
            MarketplaceSalesIntelligenceProjectionWriteResult.Applied,
            MarketplaceSalesIntelligenceProjectionWriteResult.NoOpAlreadyCurrent ->
                MaterializeOutcome.Complete
            MarketplaceSalesIntelligenceProjectionWriteResult.IntegrityFailure ->
                MaterializeOutcome.Failure
        }
    }

    private fun isValidBatch(
        organizationId: OrganizationId,
        checkpoint: ChangeSequenceCheckpoint,
        changes: List<MarketplaceEconomicEvidenceChange>
    ): Boolean {
        var previous = checkpoint
        for (change in changes) {
            if (change.subject.organizationId != organizationId ||
                change.changeSequence <= previous
            ) {
                return false
            }
            previous = change.changeSequence
        }
        return true
    }

    private enum class MaterializeOutcome {
        Complete,
        Failure
    }

    companion object {
        val PROJECTION_NAME: ProjectionName = ProjectionName("sales-intelligence")
    }
}
