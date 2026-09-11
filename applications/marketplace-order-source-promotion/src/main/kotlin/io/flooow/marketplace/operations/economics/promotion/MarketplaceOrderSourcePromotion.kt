package io.flooow.marketplace.operations.economics.promotion

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.economics.EconomicExternalReference
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceObservationId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicOrderOccurrenceObservation
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidencePersistResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceReadResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceRepository
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceUpdate
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicFact
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

object MarketplaceOrderSourcePromotionContract {
    const val CAPABILITY = "marketplace-economic.order-source"
    val MARKETPLACE = MarketplaceKey("mercado-livre")
    val SOURCE_SYSTEM_KEY = EconomicSourceSystemKey("br.com.mercadolivre")
}

data class MarketplaceOrderSourceKey(
    val organizationId: OrganizationId,
    val connectionId: IntegrationConnectionId,
    val capability: String,
    val inputProgressVersion: Long,
    val recordOrdinal: Int
) {
    init {
        require(capability == MarketplaceOrderSourcePromotionContract.CAPABILITY) {
            "Marketplace order source capability unavailable"
        }
        require(inputProgressVersion >= 0) {
            "Marketplace order source progress version is invalid"
        }
        require(recordOrdinal in 0..999) {
            "Marketplace order source ordinal is invalid"
        }
    }

    override fun toString(): String = "[INTERNAL]"
}

data class MarketplaceOrderSourcePromotionCandidate(
    val sourceKey: MarketplaceOrderSourceKey,
    val externalOrderId: MarketplaceExternalOrderId,
    val currency: MarketplaceCurrency,
    val dateCreated: Instant,
    val observedAt: Instant
) {
    init {
        requireMicrosecond(dateCreated, "Marketplace order source creation time")
        requireMicrosecond(observedAt, "Marketplace order source observation time")
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface MarketplaceOrderSourcePendingResult {
    data class Available(
        val candidates: List<MarketplaceOrderSourcePromotionCandidate>
    ) : MarketplaceOrderSourcePendingResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object Unavailable : MarketplaceOrderSourcePendingResult {
        override fun toString(): String = "[REDACTED]"
    }
}

sealed interface MarketplaceOrderIdentityResolution {
    data class Resolved(
        val orderId: MarketplaceOrderId,
        val allocatedNow: Boolean
    ) : MarketplaceOrderIdentityResolution {
        override fun toString(): String = "[INTERNAL]"
    }

    data class Conflict(
        val existingOrderId: MarketplaceOrderId
    ) : MarketplaceOrderIdentityResolution {
        override fun toString(): String = "[INTERNAL]"
    }

    data object Unavailable : MarketplaceOrderIdentityResolution {
        override fun toString(): String = "[REDACTED]"
    }
}

enum class MarketplaceOrderOccurrencePromotionOutcome {
    PROMOTED,
    DUPLICATE,
    IDENTITY_CONFLICT,
    EVIDENCE_CONFLICT
}

enum class MarketplaceOrderOccurrencePromotionWriteResult {
    APPLIED,
    ALREADY_APPLIED,
    CONFLICT,
    UNAVAILABLE
}

interface MarketplaceOrderSourcePromotionRepository {
    fun pending(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderSourcePendingResult

    fun resolveOrAllocate(
        candidate: MarketplaceOrderSourcePromotionCandidate,
        proposedOrderId: MarketplaceOrderId,
        allocatedAt: Instant
    ): MarketplaceOrderIdentityResolution

    fun markTerminal(
        candidate: MarketplaceOrderSourcePromotionCandidate,
        orderId: MarketplaceOrderId,
        outcome: MarketplaceOrderOccurrencePromotionOutcome,
        promotedAt: Instant
    ): MarketplaceOrderOccurrencePromotionWriteResult
}

fun interface MarketplaceOrderPromotionIdentifierFactory<T> {
    fun create(): T
}

enum class MarketplaceOrderSourcePromotionBlockReason {
    SOURCE_UNAVAILABLE,
    IDENTITY_UNAVAILABLE,
    EVIDENCE_UNAVAILABLE,
    INTEGRITY_FAILURE,
    TERMINAL_WRITE_UNAVAILABLE,
    TERMINAL_CONFLICT
}

sealed interface MarketplaceOrderSourcePromotionBatchResult {
    data class Completed(
        val examined: Int,
        val promoted: Int,
        val duplicates: Int,
        val identityConflicts: Int,
        val evidenceConflicts: Int
    ) : MarketplaceOrderSourcePromotionBatchResult

    data class Blocked(
        val completedBeforeBlock: Int,
        val reason: MarketplaceOrderSourcePromotionBlockReason
    ) : MarketplaceOrderSourcePromotionBatchResult
}

class MarketplaceOrderSourcePromotionService(
    private val sourceRepository: MarketplaceOrderSourcePromotionRepository,
    private val evidenceRepository: MarketplaceIndependentEconomicEvidenceRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val orderIds: MarketplaceOrderPromotionIdentifierFactory<MarketplaceOrderId> =
        MarketplaceOrderPromotionIdentifierFactory {
            MarketplaceOrderId(UUID.randomUUID())
        },
    private val observationIds:
        MarketplaceOrderPromotionIdentifierFactory<MarketplaceEconomicEvidenceObservationId> =
        MarketplaceOrderPromotionIdentifierFactory {
            MarketplaceEconomicEvidenceObservationId.parse(UUID.randomUUID().toString())
        }
) {
    fun promotePending(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderSourcePromotionBatchResult {
        require(limit in 1..1_000) { "Marketplace order promotion limit is invalid" }

        val pending = when (
            val result = sourceRepository.pending(organizationId, connectionId, limit)
        ) {
            is MarketplaceOrderSourcePendingResult.Available -> result.candidates
            MarketplaceOrderSourcePendingResult.Unavailable ->
                return MarketplaceOrderSourcePromotionBatchResult.Blocked(
                    0,
                    MarketplaceOrderSourcePromotionBlockReason.SOURCE_UNAVAILABLE
                )
        }

        var completed = 0
        var promoted = 0
        var duplicates = 0
        var identityConflicts = 0
        var evidenceConflicts = 0

        for (candidate in pending) {
            if (
                candidate.sourceKey.organizationId != organizationId ||
                candidate.sourceKey.connectionId != connectionId
            ) {
                return MarketplaceOrderSourcePromotionBatchResult.Blocked(
                    completed,
                    MarketplaceOrderSourcePromotionBlockReason.INTEGRITY_FAILURE
                )
            }

            val identity = sourceRepository.resolveOrAllocate(
                candidate,
                orderIds.create(),
                now()
            )

            when (identity) {
                MarketplaceOrderIdentityResolution.Unavailable ->
                    return MarketplaceOrderSourcePromotionBatchResult.Blocked(
                        completed,
                        MarketplaceOrderSourcePromotionBlockReason.IDENTITY_UNAVAILABLE
                    )

                is MarketplaceOrderIdentityResolution.Conflict -> {
                    terminalBlock(
                        candidate,
                        identity.existingOrderId,
                        MarketplaceOrderOccurrencePromotionOutcome.IDENTITY_CONFLICT
                    )?.let {
                        return MarketplaceOrderSourcePromotionBatchResult.Blocked(completed, it)
                    }
                    completed += 1
                    identityConflicts += 1
                }

                is MarketplaceOrderIdentityResolution.Resolved -> {
                    when (val evidence = promoteOccurrence(candidate, identity.orderId)) {
                        is EvidencePromotion.Terminal -> {
                            terminalBlock(
                                candidate,
                                identity.orderId,
                                evidence.outcome
                            )?.let {
                                return MarketplaceOrderSourcePromotionBatchResult.Blocked(
                                    completed,
                                    it
                                )
                            }
                            completed += 1
                            when (evidence.outcome) {
                                MarketplaceOrderOccurrencePromotionOutcome.PROMOTED ->
                                    promoted += 1
                                MarketplaceOrderOccurrencePromotionOutcome.DUPLICATE ->
                                    duplicates += 1
                                MarketplaceOrderOccurrencePromotionOutcome.EVIDENCE_CONFLICT ->
                                    evidenceConflicts += 1
                                MarketplaceOrderOccurrencePromotionOutcome.IDENTITY_CONFLICT ->
                                    error("Identity conflict is handled before evidence promotion")
                            }
                        }

                        is EvidencePromotion.Blocked ->
                            return MarketplaceOrderSourcePromotionBatchResult.Blocked(
                                completed,
                                evidence.reason
                            )
                    }
                }
            }
        }

        return MarketplaceOrderSourcePromotionBatchResult.Completed(
            examined = completed,
            promoted = promoted,
            duplicates = duplicates,
            identityConflicts = identityConflicts,
            evidenceConflicts = evidenceConflicts
        )
    }

    private fun promoteOccurrence(
        candidate: MarketplaceOrderSourcePromotionCandidate,
        orderId: MarketplaceOrderId
    ): EvidencePromotion {
        val subject = MarketplaceEconomicEvidenceSubject(
            organizationId = candidate.sourceKey.organizationId,
            orderId = orderId,
            marketplace = MarketplaceOrderSourcePromotionContract.MARKETPLACE,
            externalOrderId = candidate.externalOrderId,
            currency = candidate.currency
        )

        val source = EconomicSource(
            kind = EconomicSourceKind.MARKETPLACE,
            systemKey = MarketplaceOrderSourcePromotionContract.SOURCE_SYSTEM_KEY,
            externalReference = EconomicExternalReferenceState.Present(
                EconomicExternalReference(candidate.externalOrderId.value)
            )
        )

        val fact = MarketplaceIndependentEconomicFact.OrderOccurrence(
            MarketplaceEconomicOrderOccurrenceObservation(
                id = observationIds.create(),
                subject = subject,
                source = source,
                occurredAt = candidate.dateCreated,
                observedAt = candidate.observedAt
            )
        )

        repeat(MAX_EVIDENCE_ATTEMPTS) {
            val expectedVersion = when (val current = evidenceRepository.find(subject)) {
                MarketplaceIndependentEconomicEvidenceReadResult.NotFound ->
                    MarketplaceEconomicEvidenceVersion.ZERO

                MarketplaceIndependentEconomicEvidenceReadResult.IntegrityFailure ->
                    return EvidencePromotion.Blocked(
                        MarketplaceOrderSourcePromotionBlockReason.INTEGRITY_FAILURE
                    )

                is MarketplaceIndependentEconomicEvidenceReadResult.Found ->
                    current.versionedEvidence.version
            }

            when (
                evidenceRepository.apply(
                    expectedVersion,
                    MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(fact)
                )
            ) {
                is MarketplaceIndependentEconomicEvidencePersistResult.Applied ->
                    return EvidencePromotion.Terminal(
                        MarketplaceOrderOccurrencePromotionOutcome.PROMOTED
                    )

                is MarketplaceIndependentEconomicEvidencePersistResult.Duplicate ->
                    return EvidencePromotion.Terminal(
                        MarketplaceOrderOccurrencePromotionOutcome.DUPLICATE
                    )

                MarketplaceIndependentEconomicEvidencePersistResult.SourceFactConflict ->
                    return EvidencePromotion.Terminal(
                        MarketplaceOrderOccurrencePromotionOutcome.EVIDENCE_CONFLICT
                    )

                is MarketplaceIndependentEconomicEvidencePersistResult.StaleVersion -> Unit

                MarketplaceIndependentEconomicEvidencePersistResult.OrganizationUnavailable ->
                    return EvidencePromotion.Blocked(
                        MarketplaceOrderSourcePromotionBlockReason.EVIDENCE_UNAVAILABLE
                    )

                MarketplaceIndependentEconomicEvidencePersistResult.IntegrityFailure,
                MarketplaceIndependentEconomicEvidencePersistResult.SubjectMismatch,
                MarketplaceIndependentEconomicEvidencePersistResult.IdentifierConflict,
                MarketplaceIndependentEconomicEvidencePersistResult.SupersededFactNotFound,
                MarketplaceIndependentEconomicEvidencePersistResult.SupersededTargetNotFact,
                MarketplaceIndependentEconomicEvidencePersistResult.FactAlreadySuperseded,
                MarketplaceIndependentEconomicEvidencePersistResult.ReplacementIdentifierConflict,
                MarketplaceIndependentEconomicEvidencePersistResult.ReplacementSourceFactConflict ->
                    return EvidencePromotion.Blocked(
                        MarketplaceOrderSourcePromotionBlockReason.INTEGRITY_FAILURE
                    )
            }
        }

        return EvidencePromotion.Blocked(
            MarketplaceOrderSourcePromotionBlockReason.EVIDENCE_UNAVAILABLE
        )
    }

    private fun terminalBlock(
        candidate: MarketplaceOrderSourcePromotionCandidate,
        orderId: MarketplaceOrderId,
        outcome: MarketplaceOrderOccurrencePromotionOutcome
    ): MarketplaceOrderSourcePromotionBlockReason? =
        when (sourceRepository.markTerminal(candidate, orderId, outcome, now())) {
            MarketplaceOrderOccurrencePromotionWriteResult.APPLIED,
            MarketplaceOrderOccurrencePromotionWriteResult.ALREADY_APPLIED -> null

            MarketplaceOrderOccurrencePromotionWriteResult.CONFLICT ->
                MarketplaceOrderSourcePromotionBlockReason.TERMINAL_CONFLICT

            MarketplaceOrderOccurrencePromotionWriteResult.UNAVAILABLE ->
                MarketplaceOrderSourcePromotionBlockReason.TERMINAL_WRITE_UNAVAILABLE
        }

    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MICROS)

    private sealed interface EvidencePromotion {
        data class Terminal(
            val outcome: MarketplaceOrderOccurrencePromotionOutcome
        ) : EvidencePromotion

        data class Blocked(
            val reason: MarketplaceOrderSourcePromotionBlockReason
        ) : EvidencePromotion
    }

    companion object {
        private const val MAX_EVIDENCE_ATTEMPTS = 3
    }
}

private fun requireMicrosecond(value: Instant, label: String) {
    require(value.nano % 1_000 == 0) { "$label must use at most microsecond precision" }
}
data class MarketplaceOrderRevenuePromotionCandidate(
    val sourceKey: MarketplaceOrderSourceKey,
    val externalOrderId: io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId,
    val sourceCurrency: io.flooow.marketplace.operations.economics.MarketplaceCurrency,
    val identityCurrency: io.flooow.marketplace.operations.economics.MarketplaceCurrency,
    val orderId: io.flooow.marketplace.operations.economics.MarketplaceOrderId,
    val totalAmount: java.math.BigDecimal,
    val dateClosed: java.time.Instant,
    val observedAt: java.time.Instant
) {
    init {
        require(totalAmount.signum() >= 0) {
            "Marketplace order revenue amount must be nonnegative"
        }
        require(totalAmount.scale() <= 6) {
            "Marketplace order revenue amount scale must not exceed six"
        }
        require(totalAmount.abs() < java.math.BigDecimal("1000000000000000000")) {
            "Marketplace order revenue amount exceeds the supported bound"
        }
        requireMicrosecond(dateClosed, "Marketplace order source close time")
        requireMicrosecond(observedAt, "Marketplace order source observation time")
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface MarketplaceOrderRevenuePendingResult {
    data class Available(
        val candidates: List<MarketplaceOrderRevenuePromotionCandidate>
    ) : MarketplaceOrderRevenuePendingResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object Unavailable : MarketplaceOrderRevenuePendingResult {
        override fun toString(): String = "[REDACTED]"
    }
}

enum class MarketplaceOrderRevenuePromotionOutcome {
    PROMOTED,
    DUPLICATE,
    IDENTITY_CONFLICT,
    EVIDENCE_CONFLICT
}

enum class MarketplaceOrderRevenuePromotionWriteResult {
    APPLIED,
    ALREADY_APPLIED,
    CONFLICT,
    UNAVAILABLE
}

interface MarketplaceOrderRevenuePromotionRepository {
    fun pendingRevenue(
        organizationId: io.flooow.organization.OrganizationId,
        connectionId: io.flooow.integration.control.IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderRevenuePendingResult

    fun markRevenueTerminal(
        candidate: MarketplaceOrderRevenuePromotionCandidate,
        outcome: MarketplaceOrderRevenuePromotionOutcome,
        promotedAt: java.time.Instant
    ): MarketplaceOrderRevenuePromotionWriteResult
}

enum class MarketplaceOrderRevenuePromotionBlockReason {
    SOURCE_UNAVAILABLE,
    EVIDENCE_UNAVAILABLE,
    INTEGRITY_FAILURE,
    TERMINAL_WRITE_UNAVAILABLE,
    TERMINAL_CONFLICT
}

sealed interface MarketplaceOrderRevenuePromotionBatchResult {
    data class Completed(
        val examined: Int,
        val promoted: Int,
        val duplicates: Int,
        val identityConflicts: Int,
        val evidenceConflicts: Int
    ) : MarketplaceOrderRevenuePromotionBatchResult

    data class Blocked(
        val completedBeforeBlock: Int,
        val reason: MarketplaceOrderRevenuePromotionBlockReason
    ) : MarketplaceOrderRevenuePromotionBatchResult
}

class MarketplaceOrderRevenuePromotionService(
    private val sourceRepository: MarketplaceOrderRevenuePromotionRepository,
    private val evidenceRepository:
        io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceRepository,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
    private val componentIds:
        MarketplaceOrderPromotionIdentifierFactory<
            io.flooow.marketplace.operations.economics.EconomicComponentId
        > = MarketplaceOrderPromotionIdentifierFactory {
            io.flooow.marketplace.operations.economics.EconomicComponentId(
                java.util.UUID.randomUUID()
            )
        },
    private val observationIds:
        MarketplaceOrderPromotionIdentifierFactory<
            io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceObservationId
        > = MarketplaceOrderPromotionIdentifierFactory {
            io.flooow.marketplace.operations.economics.evidence
                .MarketplaceEconomicEvidenceObservationId.parse(
                    java.util.UUID.randomUUID().toString()
                )
        }
) {
    fun promotePendingRevenue(
        organizationId: io.flooow.organization.OrganizationId,
        connectionId: io.flooow.integration.control.IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderRevenuePromotionBatchResult {
        require(limit in 1..1_000) { "Marketplace order revenue promotion limit is invalid" }

        val pending = when (
            val result = sourceRepository.pendingRevenue(organizationId, connectionId, limit)
        ) {
            is MarketplaceOrderRevenuePendingResult.Available -> result.candidates
            MarketplaceOrderRevenuePendingResult.Unavailable ->
                return MarketplaceOrderRevenuePromotionBatchResult.Blocked(
                    0,
                    MarketplaceOrderRevenuePromotionBlockReason.SOURCE_UNAVAILABLE
                )
        }

        var completed = 0
        var promoted = 0
        var duplicates = 0
        var identityConflicts = 0
        var evidenceConflicts = 0

        for (candidate in pending) {
            if (
                candidate.sourceKey.organizationId != organizationId ||
                candidate.sourceKey.connectionId != connectionId
            ) {
                return MarketplaceOrderRevenuePromotionBatchResult.Blocked(
                    completed,
                    MarketplaceOrderRevenuePromotionBlockReason.INTEGRITY_FAILURE
                )
            }

            if (candidate.sourceCurrency != candidate.identityCurrency) {
                terminalBlock(
                    candidate,
                    MarketplaceOrderRevenuePromotionOutcome.IDENTITY_CONFLICT
                )?.let {
                    return MarketplaceOrderRevenuePromotionBatchResult.Blocked(completed, it)
                }
                completed += 1
                identityConflicts += 1
                continue
            }

            when (val evidence = promoteRevenue(candidate)) {
                is RevenueEvidencePromotion.Terminal -> {
                    terminalBlock(candidate, evidence.outcome)?.let {
                        return MarketplaceOrderRevenuePromotionBatchResult.Blocked(completed, it)
                    }
                    completed += 1
                    when (evidence.outcome) {
                        MarketplaceOrderRevenuePromotionOutcome.PROMOTED -> promoted += 1
                        MarketplaceOrderRevenuePromotionOutcome.DUPLICATE -> duplicates += 1
                        MarketplaceOrderRevenuePromotionOutcome.EVIDENCE_CONFLICT ->
                            evidenceConflicts += 1
                        MarketplaceOrderRevenuePromotionOutcome.IDENTITY_CONFLICT ->
                            error("Identity conflict is handled before revenue evidence promotion")
                    }
                }

                is RevenueEvidencePromotion.Blocked ->
                    return MarketplaceOrderRevenuePromotionBatchResult.Blocked(
                        completed,
                        evidence.reason
                    )
            }
        }

        return MarketplaceOrderRevenuePromotionBatchResult.Completed(
            examined = completed,
            promoted = promoted,
            duplicates = duplicates,
            identityConflicts = identityConflicts,
            evidenceConflicts = evidenceConflicts
        )
    }

    private fun promoteRevenue(
        candidate: MarketplaceOrderRevenuePromotionCandidate
    ): RevenueEvidencePromotion {
        val subject =
            io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject(
                organizationId = candidate.sourceKey.organizationId,
                orderId = candidate.orderId,
                marketplace = MarketplaceOrderSourcePromotionContract.MARKETPLACE,
                externalOrderId = candidate.externalOrderId,
                currency = candidate.identityCurrency
            )

        val source = io.flooow.marketplace.operations.economics.EconomicSource(
            kind = io.flooow.marketplace.operations.economics.EconomicSourceKind.MARKETPLACE,
            systemKey = MarketplaceOrderSourcePromotionContract.SOURCE_SYSTEM_KEY,
            externalReference =
                io.flooow.marketplace.operations.economics.EconomicExternalReferenceState.Present(
                    io.flooow.marketplace.operations.economics.EconomicExternalReference(
                        candidate.externalOrderId.value
                    )
                )
        )

        val component = io.flooow.marketplace.operations.economics.EconomicComponent(
            organizationId = candidate.sourceKey.organizationId,
            id = componentIds.create(),
            orderId = candidate.orderId,
            type = io.flooow.marketplace.operations.economics.EconomicComponentType.REVENUE,
            direction = io.flooow.marketplace.operations.economics.EconomicDirection.ADDITION,
            magnitude = io.flooow.marketplace.operations.economics.MarketplaceMoney.parse(
                candidate.sourceCurrency,
                canonicalRevenueAmount(candidate.totalAmount)
            ),
            source = source,
            occurredAt = candidate.dateClosed,
            quality =
                io.flooow.marketplace.operations.economics.EconomicEvidenceQuality.CONFIRMED
        )

        val fact =
            io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicFact
                .Component(
                    io.flooow.marketplace.operations.economics.evidence
                        .MarketplaceEconomicComponentObservation(
                            id = observationIds.create(),
                            subject = subject,
                            family =
                                io.flooow.marketplace.operations.economics.evidence
                                    .MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                            component = component,
                            coverageClaim =
                                io.flooow.marketplace.operations.economics
                                    .EconomicComponentCoverage.PARTIAL,
                            observedAt = candidate.observedAt
                        )
                )

        repeat(MAX_REVENUE_EVIDENCE_ATTEMPTS) {
            val expectedVersion = when (val current = evidenceRepository.find(subject)) {
                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidenceReadResult.NotFound ->
                    io.flooow.marketplace.operations.economics.evidence
                        .MarketplaceEconomicEvidenceVersion.ZERO

                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidenceReadResult.IntegrityFailure ->
                    return RevenueEvidencePromotion.Blocked(
                        MarketplaceOrderRevenuePromotionBlockReason.INTEGRITY_FAILURE
                    )

                is io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidenceReadResult.Found ->
                    current.versionedEvidence.version
            }

            when (
                evidenceRepository.apply(
                    expectedVersion,
                    io.flooow.marketplace.operations.economics.evidence
                        .MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(fact)
                )
            ) {
                is io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.Applied ->
                    return RevenueEvidencePromotion.Terminal(
                        MarketplaceOrderRevenuePromotionOutcome.PROMOTED
                    )

                is io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.Duplicate ->
                    return RevenueEvidencePromotion.Terminal(
                        MarketplaceOrderRevenuePromotionOutcome.DUPLICATE
                    )

                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.SourceFactConflict ->
                    return RevenueEvidencePromotion.Terminal(
                        MarketplaceOrderRevenuePromotionOutcome.EVIDENCE_CONFLICT
                    )

                is io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.StaleVersion -> Unit

                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.OrganizationUnavailable ->
                    return RevenueEvidencePromotion.Blocked(
                        MarketplaceOrderRevenuePromotionBlockReason.EVIDENCE_UNAVAILABLE
                    )

                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.IntegrityFailure,
                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.SubjectMismatch,
                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.IdentifierConflict,
                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.SupersededFactNotFound,
                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.SupersededTargetNotFact,
                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult.FactAlreadySuperseded,
                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult
                    .ReplacementIdentifierConflict,
                io.flooow.marketplace.operations.economics.evidence
                    .MarketplaceIndependentEconomicEvidencePersistResult
                    .ReplacementSourceFactConflict ->
                    return RevenueEvidencePromotion.Blocked(
                        MarketplaceOrderRevenuePromotionBlockReason.INTEGRITY_FAILURE
                    )
            }
        }

        return RevenueEvidencePromotion.Blocked(
            MarketplaceOrderRevenuePromotionBlockReason.EVIDENCE_UNAVAILABLE
        )
    }

    private fun terminalBlock(
        candidate: MarketplaceOrderRevenuePromotionCandidate,
        outcome: MarketplaceOrderRevenuePromotionOutcome
    ): MarketplaceOrderRevenuePromotionBlockReason? =
        when (
            sourceRepository.markRevenueTerminal(
                candidate,
                outcome,
                clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
            )
        ) {
            MarketplaceOrderRevenuePromotionWriteResult.APPLIED,
            MarketplaceOrderRevenuePromotionWriteResult.ALREADY_APPLIED -> null

            MarketplaceOrderRevenuePromotionWriteResult.CONFLICT ->
                MarketplaceOrderRevenuePromotionBlockReason.TERMINAL_CONFLICT

            MarketplaceOrderRevenuePromotionWriteResult.UNAVAILABLE ->
                MarketplaceOrderRevenuePromotionBlockReason.TERMINAL_WRITE_UNAVAILABLE
        }

    private sealed interface RevenueEvidencePromotion {
        data class Terminal(
            val outcome: MarketplaceOrderRevenuePromotionOutcome
        ) : RevenueEvidencePromotion

        data class Blocked(
            val reason: MarketplaceOrderRevenuePromotionBlockReason
        ) : RevenueEvidencePromotion
    }

    companion object {
        private const val MAX_REVENUE_EVIDENCE_ATTEMPTS = 3
    }
}

private fun canonicalRevenueAmount(value: java.math.BigDecimal): String =
    if (value.signum() == 0) "0" else value.stripTrailingZeros().toPlainString()