package io.flooow.marketplace.operations.live

import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.connector.ConnectorExecutionFailureKind
import io.flooow.integration.connector.ConnectorExecutionOutcome
import io.flooow.integration.connector.ConnectorInvocation
import io.flooow.integration.connector.ConnectorInvocationId
import io.flooow.integration.connector.ConnectorRuntime
import io.flooow.integration.connector.ConnectorSuccessKind
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.ProviderKey
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderRevenuePromotionBatchResult
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderRevenuePromotionBlockReason
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderRevenuePromotionService
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionBatchResult
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionBlockReason
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionContract
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionService
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionProcessor
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionProcessorResult
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

object MarketplaceLivePipelineContract {
    val PROVIDER: ProviderKey = ProviderKey.of("br.com.mercadolivre")
    val CAPABILITY: ConnectorCapability =
        ConnectorCapability.of(MarketplaceOrderSourcePromotionContract.CAPABILITY)
    val MAX_RUN_DURATION: Duration = Duration.ofMinutes(5)
}

data class MarketplaceLivePipelineLimits(
    val maxSourcePages: Int = 10,
    val sourceMaxRecordsPerPage: Int = 50,
    val sourceMaxResponseBytes: Long = 2L * 1024L * 1024L,
    val maxOccurrenceBatches: Int = 10,
    val maxRevenueBatches: Int = 10,
    val promotionBatchSize: Int = 500,
    val maxProjectionBatches: Int = 10,
    val projectionBatchSize: Int = 500
) {
    init {
        require(maxSourcePages in 1..100) { "Invalid live pipeline source page limit" }
        require(sourceMaxRecordsPerPage in 1..ConnectorBudget.MAX_RECORDS) {
            "Invalid live pipeline source record limit"
        }
        require(sourceMaxResponseBytes in 1..ConnectorBudget.MAX_RESPONSE_BYTES) {
            "Invalid live pipeline source response limit"
        }
        require(maxOccurrenceBatches in 1..100) {
            "Invalid live pipeline occurrence batch limit"
        }
        require(maxRevenueBatches in 1..100) {
            "Invalid live pipeline revenue batch limit"
        }
        require(promotionBatchSize in 1..1_000) {
            "Invalid live pipeline promotion batch size"
        }
        require(maxProjectionBatches in 1..100) {
            "Invalid live pipeline projection batch limit"
        }
        require(projectionBatchSize in 1..1_000) {
            "Invalid live pipeline projection batch size"
        }
    }
}

enum class MarketplaceLivePipelineSourceStop {
    EXHAUSTED,
    PAGE_LIMIT,
    RETRYABLE_FAILURE,
    NON_RETRYABLE_FAILURE
}

data class MarketplaceLivePipelineSourceSummary(
    val invocations: Int,
    val committedPages: Int,
    val alreadyCommittedPages: Int,
    val records: Long,
    val stop: MarketplaceLivePipelineSourceStop,
    val failureKind: ConnectorExecutionFailureKind? = null,
    val retryAfter: Duration? = null
) {
    init {
        require(invocations >= 0)
        require(committedPages >= 0)
        require(alreadyCommittedPages >= 0)
        require(records >= 0)
        require(committedPages + alreadyCommittedPages <= invocations)

        when (stop) {
            MarketplaceLivePipelineSourceStop.RETRYABLE_FAILURE ->
                require(
                    failureKind == ConnectorExecutionFailureKind.RATE_LIMITED ||
                        failureKind == ConnectorExecutionFailureKind.REMOTE_TEMPORARY
                )

            MarketplaceLivePipelineSourceStop.NON_RETRYABLE_FAILURE ->
                require(
                    failureKind != null &&
                        failureKind != ConnectorExecutionFailureKind.RATE_LIMITED &&
                        failureKind != ConnectorExecutionFailureKind.REMOTE_TEMPORARY &&
                        failureKind != ConnectorExecutionFailureKind.CANCELLED
                )

            MarketplaceLivePipelineSourceStop.EXHAUSTED,
            MarketplaceLivePipelineSourceStop.PAGE_LIMIT ->
                require(failureKind == null && retryAfter == null)
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class MarketplaceLivePipelinePromotionSummary(
    val batches: Int,
    val examined: Long,
    val promoted: Long,
    val duplicates: Long,
    val identityConflicts: Long,
    val evidenceConflicts: Long,
    val drained: Boolean
) {
    init {
        require(batches >= 0)
        require(examined >= 0)
        require(promoted >= 0)
        require(duplicates >= 0)
        require(identityConflicts >= 0)
        require(evidenceConflicts >= 0)
        require(examined == promoted + duplicates + identityConflicts + evidenceConflicts)
    }

    override fun toString(): String = "[REDACTED]"

    companion object {
        val EMPTY = MarketplaceLivePipelinePromotionSummary(
            batches = 0,
            examined = 0,
            promoted = 0,
            duplicates = 0,
            identityConflicts = 0,
            evidenceConflicts = 0,
            drained = false
        )
    }
}

data class MarketplaceLivePipelineProjectionSummary(
    val batches: Int,
    val processedChanges: Long,
    val drained: Boolean
) {
    init {
        require(batches >= 0)
        require(processedChanges >= 0)
    }

    override fun toString(): String = "[REDACTED]"

    companion object {
        val EMPTY = MarketplaceLivePipelineProjectionSummary(
            batches = 0,
            processedChanges = 0,
            drained = false
        )
    }
}

enum class MarketplaceLivePipelineIntegrityStage {
    SOURCE,
    OCCURRENCE,
    REVENUE,
    PROJECTION
}

sealed interface MarketplaceLivePipelineBlockDetail {
    data object Cancelled : MarketplaceLivePipelineBlockDetail {
        override fun toString(): String = "[REDACTED]"
    }

    data object DeadlineExceeded : MarketplaceLivePipelineBlockDetail {
        override fun toString(): String = "[REDACTED]"
    }

    data class IntegrityFailure(
        val stage: MarketplaceLivePipelineIntegrityStage
    ) : MarketplaceLivePipelineBlockDetail {
        override fun toString(): String = "[REDACTED]"
    }

    data class OccurrenceBlocked(
        val reason: MarketplaceOrderSourcePromotionBlockReason
    ) : MarketplaceLivePipelineBlockDetail {
        override fun toString(): String = "[REDACTED]"
    }

    data class RevenueBlocked(
        val reason: MarketplaceOrderRevenuePromotionBlockReason
    ) : MarketplaceLivePipelineBlockDetail {
        override fun toString(): String = "[REDACTED]"
    }

    data object ProjectionCheckpointConflict : MarketplaceLivePipelineBlockDetail {
        override fun toString(): String = "[REDACTED]"
    }

    data object ProjectionIntegrityFailure : MarketplaceLivePipelineBlockDetail {
        override fun toString(): String = "[REDACTED]"
    }
}

sealed interface MarketplaceLivePipelineResult {
    data class Completed(
        val source: MarketplaceLivePipelineSourceSummary,
        val occurrence: MarketplaceLivePipelinePromotionSummary,
        val revenue: MarketplaceLivePipelinePromotionSummary,
        val projection: MarketplaceLivePipelineProjectionSummary
    ) : MarketplaceLivePipelineResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class Blocked(
        val detail: MarketplaceLivePipelineBlockDetail,
        val source: MarketplaceLivePipelineSourceSummary? = null,
        val occurrence: MarketplaceLivePipelinePromotionSummary? = null,
        val revenue: MarketplaceLivePipelinePromotionSummary? = null,
        val projection: MarketplaceLivePipelineProjectionSummary? = null
    ) : MarketplaceLivePipelineResult {
        override fun toString(): String = "[REDACTED]"
    }
}

fun interface MarketplaceLivePipelineSourceRunner {
    fun execute(
        invocation: ConnectorInvocation,
        cancellation: ConnectorCancellation
    ): ConnectorExecutionOutcome
}

class ConnectorRuntimeMarketplaceLivePipelineSourceRunner(
    private val runtime: ConnectorRuntime
) : MarketplaceLivePipelineSourceRunner {
    override fun execute(
        invocation: ConnectorInvocation,
        cancellation: ConnectorCancellation
    ): ConnectorExecutionOutcome = runtime.execute(invocation, cancellation)
}

fun interface MarketplaceLivePipelineOccurrencePromoter {
    fun promote(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderSourcePromotionBatchResult
}

class MarketplaceOrderSourcePromotionLivePipelineAdapter(
    private val service: MarketplaceOrderSourcePromotionService
) : MarketplaceLivePipelineOccurrencePromoter {
    override fun promote(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderSourcePromotionBatchResult =
        service.promotePending(organizationId, connectionId, limit)
}

fun interface MarketplaceLivePipelineRevenuePromoter {
    fun promote(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderRevenuePromotionBatchResult
}

class MarketplaceOrderRevenuePromotionLivePipelineAdapter(
    private val service: MarketplaceOrderRevenuePromotionService
) : MarketplaceLivePipelineRevenuePromoter {
    override fun promote(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderRevenuePromotionBatchResult =
        service.promotePendingRevenue(organizationId, connectionId, limit)
}

fun interface MarketplaceLivePipelineProjectionProcessor {
    fun process(
        organizationId: OrganizationId,
        limit: Int
    ): MarketplaceSalesIntelligenceProjectionProcessorResult
}

class MarketplaceSalesIntelligenceLivePipelineAdapter(
    private val processor: MarketplaceSalesIntelligenceProjectionProcessor
) : MarketplaceLivePipelineProjectionProcessor {
    override fun process(
        organizationId: OrganizationId,
        limit: Int
    ): MarketplaceSalesIntelligenceProjectionProcessorResult =
        processor.processBatch(organizationId, limit)
}

fun interface MarketplaceLivePipelineInvocationIdFactory {
    fun create(): ConnectorInvocationId
}

class MarketplaceLivePipelineService(
    private val source: MarketplaceLivePipelineSourceRunner,
    private val occurrence: MarketplaceLivePipelineOccurrencePromoter,
    private val revenue: MarketplaceLivePipelineRevenuePromoter,
    private val projection: MarketplaceLivePipelineProjectionProcessor,
    private val sourceCapability: ConnectorCapability = ConnectorCapability.of("marketplace-economic.order-source"),
    private val clock: Clock = Clock.systemUTC(),
    private val invocationIds: MarketplaceLivePipelineInvocationIdFactory =
        MarketplaceLivePipelineInvocationIdFactory {
            ConnectorInvocationId(UUID.randomUUID())
        }
) {
    fun run(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        deadline: Instant,
        limits: MarketplaceLivePipelineLimits = MarketplaceLivePipelineLimits(),
        cancellation: ConnectorCancellation = ConnectorCancellation.NEVER,
        stopAfterSource: Boolean = false
    ): MarketplaceLivePipelineResult {
        val startedAt = clock.instant()
        require(deadline.isAfter(startedAt)) {
            "Live pipeline deadline must be in the future"
        }
        require(!deadline.isAfter(startedAt.plus(MarketplaceLivePipelineContract.MAX_RUN_DURATION))) {
            "Live pipeline deadline exceeds the maximum run duration"
        }

        gate(deadline, cancellation)?.let {
            return MarketplaceLivePipelineResult.Blocked(detail = it)
        }

        val sourcePhase = pullSource(
            organizationId,
            connectionId,
            deadline,
            limits,
            cancellation
        )
        when (sourcePhase) {
            is SourcePhase.Blocked ->
                return MarketplaceLivePipelineResult.Blocked(
                    detail = sourcePhase.detail,
                    source = sourcePhase.summary
                )

            is SourcePhase.Done -> Unit
        }
        val sourceSummary = (sourcePhase as SourcePhase.Done).summary

        if (stopAfterSource) {
            return MarketplaceLivePipelineResult.Completed(
                source = sourceSummary,
                occurrence = MarketplaceLivePipelinePromotionSummary.EMPTY,
                revenue = MarketplaceLivePipelinePromotionSummary.EMPTY,
                projection = MarketplaceLivePipelineProjectionSummary.EMPTY
            )
        }

        gate(deadline, cancellation)?.let {
            return MarketplaceLivePipelineResult.Blocked(
                detail = it,
                source = sourceSummary
            )
        }

        val occurrencePhase = drainOccurrence(
            organizationId,
            connectionId,
            deadline,
            limits,
            cancellation
        )
        when (occurrencePhase) {
            is PromotionPhase.Blocked ->
                return MarketplaceLivePipelineResult.Blocked(
                    detail = occurrencePhase.detail,
                    source = sourceSummary,
                    occurrence = occurrencePhase.summary
                )

            is PromotionPhase.Done -> Unit
        }
        val occurrenceSummary = (occurrencePhase as PromotionPhase.Done).summary

        gate(deadline, cancellation)?.let {
            return MarketplaceLivePipelineResult.Blocked(
                detail = it,
                source = sourceSummary,
                occurrence = occurrenceSummary
            )
        }

        val revenuePhase = drainRevenue(
            organizationId,
            connectionId,
            deadline,
            limits,
            cancellation
        )
        when (revenuePhase) {
            is PromotionPhase.Blocked ->
                return MarketplaceLivePipelineResult.Blocked(
                    detail = revenuePhase.detail,
                    source = sourceSummary,
                    occurrence = occurrenceSummary,
                    revenue = revenuePhase.summary
                )

            is PromotionPhase.Done -> Unit
        }
        val revenueSummary = (revenuePhase as PromotionPhase.Done).summary

        gate(deadline, cancellation)?.let {
            return MarketplaceLivePipelineResult.Blocked(
                detail = it,
                source = sourceSummary,
                occurrence = occurrenceSummary,
                revenue = revenueSummary
            )
        }

        val projectionPhase = drainProjection(
            organizationId,
            deadline,
            limits,
            cancellation
        )
        return when (projectionPhase) {
            is ProjectionPhase.Blocked ->
                MarketplaceLivePipelineResult.Blocked(
                    detail = projectionPhase.detail,
                    source = sourceSummary,
                    occurrence = occurrenceSummary,
                    revenue = revenueSummary,
                    projection = projectionPhase.summary
                )

            is ProjectionPhase.Done ->
                MarketplaceLivePipelineResult.Completed(
                    source = sourceSummary,
                    occurrence = occurrenceSummary,
                    revenue = revenueSummary,
                    projection = projectionPhase.summary
                )
        }
    }

    private fun pullSource(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        deadline: Instant,
        limits: MarketplaceLivePipelineLimits,
        cancellation: ConnectorCancellation
    ): SourcePhase {
        var invocations = 0
        var committed = 0
        var alreadyCommitted = 0
        var records = 0L

        repeat(limits.maxSourcePages) {
            gate(deadline, cancellation)?.let {
                return SourcePhase.Blocked(
                    it,
                    sourceSummary(
                        invocations,
                        committed,
                        alreadyCommitted,
                        records,
                        MarketplaceLivePipelineSourceStop.PAGE_LIMIT
                    )
                )
            }

            val invocation = ConnectorInvocation(
                organizationId = organizationId,
                connectionId = connectionId,
                capability = sourceCapability,
                invocationId = invocationIds.create(),
                budget = ConnectorBudget(
                    deadline = deadline,
                    maxRecords = limits.sourceMaxRecordsPerPage,
                    maxResponseBytes = limits.sourceMaxResponseBytes
                )
            )

            val outcome = source.execute(invocation, cancellation)
            invocations += 1

            when (outcome) {
                is ConnectorExecutionOutcome.Success -> {
                    if (
                        outcome.capability != sourceCapability ||
                        outcome.providerKey != MarketplaceLivePipelineContract.PROVIDER
                    ) {
                        return SourcePhase.Blocked(
                            MarketplaceLivePipelineBlockDetail.IntegrityFailure(
                                MarketplaceLivePipelineIntegrityStage.SOURCE
                            ),
                            null
                        )
                    }

                    records += outcome.recordCount.toLong()
                    when (outcome.kind) {
                        ConnectorSuccessKind.COMMITTED -> committed += 1
                        ConnectorSuccessKind.ALREADY_COMMITTED -> alreadyCommitted += 1
                    }

                    if (outcome.exhausted) {
                        return SourcePhase.Done(
                            sourceSummary(
                                invocations,
                                committed,
                                alreadyCommitted,
                                records,
                                MarketplaceLivePipelineSourceStop.EXHAUSTED
                            )
                        )
                    }
                }

                is ConnectorExecutionOutcome.Failure -> {
                    if (
                        outcome.capability != sourceCapability ||
                        (
                            outcome.providerKey != null &&
                                outcome.providerKey != MarketplaceLivePipelineContract.PROVIDER
                        )
                    ) {
                        return SourcePhase.Blocked(
                            MarketplaceLivePipelineBlockDetail.IntegrityFailure(
                                MarketplaceLivePipelineIntegrityStage.SOURCE
                            ),
                            null
                        )
                    }

                    if (outcome.kind == ConnectorExecutionFailureKind.CANCELLED) {
                        return SourcePhase.Blocked(
                            MarketplaceLivePipelineBlockDetail.Cancelled,
                            null
                        )
                    }

                    val retryable =
                        outcome.kind == ConnectorExecutionFailureKind.RATE_LIMITED ||
                            outcome.kind == ConnectorExecutionFailureKind.REMOTE_TEMPORARY

                    return SourcePhase.Done(
                        MarketplaceLivePipelineSourceSummary(
                            invocations = invocations,
                            committedPages = committed,
                            alreadyCommittedPages = alreadyCommitted,
                            records = records,
                            stop = if (retryable) {
                                MarketplaceLivePipelineSourceStop.RETRYABLE_FAILURE
                            } else {
                                MarketplaceLivePipelineSourceStop.NON_RETRYABLE_FAILURE
                            },
                            failureKind = outcome.kind,
                            retryAfter = outcome.retryAfter
                        )
                    )
                }
            }
        }

        return SourcePhase.Done(
            sourceSummary(
                invocations,
                committed,
                alreadyCommitted,
                records,
                MarketplaceLivePipelineSourceStop.PAGE_LIMIT
            )
        )
    }

    private fun drainOccurrence(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        deadline: Instant,
        limits: MarketplaceLivePipelineLimits,
        cancellation: ConnectorCancellation
    ): PromotionPhase {
        var summary = MarketplaceLivePipelinePromotionSummary.EMPTY

        repeat(limits.maxOccurrenceBatches) {
            gate(deadline, cancellation)?.let {
                return PromotionPhase.Blocked(it, summary)
            }

            when (
                val result = occurrence.promote(
                    organizationId,
                    connectionId,
                    limits.promotionBatchSize
                )
            ) {
                is MarketplaceOrderSourcePromotionBatchResult.Blocked ->
                    return PromotionPhase.Blocked(
                        MarketplaceLivePipelineBlockDetail.OccurrenceBlocked(result.reason),
                        summary
                    )

                is MarketplaceOrderSourcePromotionBatchResult.Completed -> {
                    if (!validPromotionCounts(result)) {
                        return PromotionPhase.Blocked(
                            MarketplaceLivePipelineBlockDetail.IntegrityFailure(
                                MarketplaceLivePipelineIntegrityStage.OCCURRENCE
                            ),
                            summary
                        )
                    }

                    summary = summary.plus(
                        examined = result.examined,
                        promoted = result.promoted,
                        duplicates = result.duplicates,
                        identityConflicts = result.identityConflicts,
                        evidenceConflicts = result.evidenceConflicts
                    )

                    if (result.examined == 0) {
                        return PromotionPhase.Done(summary.copy(drained = true))
                    }
                }
            }
        }

        return PromotionPhase.Done(summary.copy(drained = false))
    }

    private fun drainRevenue(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        deadline: Instant,
        limits: MarketplaceLivePipelineLimits,
        cancellation: ConnectorCancellation
    ): PromotionPhase {
        var summary = MarketplaceLivePipelinePromotionSummary.EMPTY

        repeat(limits.maxRevenueBatches) {
            gate(deadline, cancellation)?.let {
                return PromotionPhase.Blocked(it, summary)
            }

            when (
                val result = revenue.promote(
                    organizationId,
                    connectionId,
                    limits.promotionBatchSize
                )
            ) {
                is MarketplaceOrderRevenuePromotionBatchResult.Blocked ->
                    return PromotionPhase.Blocked(
                        MarketplaceLivePipelineBlockDetail.RevenueBlocked(result.reason),
                        summary
                    )

                is MarketplaceOrderRevenuePromotionBatchResult.Completed -> {
                    if (!validPromotionCounts(result)) {
                        return PromotionPhase.Blocked(
                            MarketplaceLivePipelineBlockDetail.IntegrityFailure(
                                MarketplaceLivePipelineIntegrityStage.REVENUE
                            ),
                            summary
                        )
                    }

                    summary = summary.plus(
                        examined = result.examined,
                        promoted = result.promoted,
                        duplicates = result.duplicates,
                        identityConflicts = result.identityConflicts,
                        evidenceConflicts = result.evidenceConflicts
                    )

                    if (result.examined == 0) {
                        return PromotionPhase.Done(summary.copy(drained = true))
                    }
                }
            }
        }

        return PromotionPhase.Done(summary.copy(drained = false))
    }

    private fun drainProjection(
        organizationId: OrganizationId,
        deadline: Instant,
        limits: MarketplaceLivePipelineLimits,
        cancellation: ConnectorCancellation
    ): ProjectionPhase {
        var summary = MarketplaceLivePipelineProjectionSummary.EMPTY

        repeat(limits.maxProjectionBatches) {
            gate(deadline, cancellation)?.let {
                return ProjectionPhase.Blocked(it, summary)
            }

            when (val result = projection.process(organizationId, limits.projectionBatchSize)) {
                MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges ->
                    return ProjectionPhase.Done(
                        summary.copy(
                            batches = summary.batches + 1,
                            drained = true
                        )
                    )

                MarketplaceSalesIntelligenceProjectionProcessorResult.CheckpointConflict ->
                    return ProjectionPhase.Blocked(
                        MarketplaceLivePipelineBlockDetail.ProjectionCheckpointConflict,
                        summary
                    )

                MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure ->
                    return ProjectionPhase.Blocked(
                        MarketplaceLivePipelineBlockDetail.ProjectionIntegrityFailure,
                        summary
                    )

                is MarketplaceSalesIntelligenceProjectionProcessorResult.Success -> {
                    if (result.processedChanges <= 0) {
                        return ProjectionPhase.Blocked(
                            MarketplaceLivePipelineBlockDetail.IntegrityFailure(
                                MarketplaceLivePipelineIntegrityStage.PROJECTION
                            ),
                            summary
                        )
                    }

                    summary = summary.copy(
                        batches = summary.batches + 1,
                        processedChanges =
                            summary.processedChanges + result.processedChanges.toLong()
                    )
                }
            }
        }

        return ProjectionPhase.Done(summary.copy(drained = false))
    }

    private fun validPromotionCounts(
        result: MarketplaceOrderSourcePromotionBatchResult.Completed
    ): Boolean =
        result.examined.toLong() ==
            result.promoted.toLong() +
            result.duplicates.toLong() +
            result.identityConflicts.toLong() +
            result.evidenceConflicts.toLong()

    private fun validPromotionCounts(
        result: MarketplaceOrderRevenuePromotionBatchResult.Completed
    ): Boolean =
        result.examined.toLong() ==
            result.promoted.toLong() +
            result.duplicates.toLong() +
            result.identityConflicts.toLong() +
            result.evidenceConflicts.toLong()

    private fun MarketplaceLivePipelinePromotionSummary.plus(
        examined: Int,
        promoted: Int,
        duplicates: Int,
        identityConflicts: Int,
        evidenceConflicts: Int
    ): MarketplaceLivePipelinePromotionSummary =
        MarketplaceLivePipelinePromotionSummary(
            batches = batches + 1,
            examined = this.examined + examined.toLong(),
            promoted = this.promoted + promoted.toLong(),
            duplicates = this.duplicates + duplicates.toLong(),
            identityConflicts = this.identityConflicts + identityConflicts.toLong(),
            evidenceConflicts = this.evidenceConflicts + evidenceConflicts.toLong(),
            drained = false
        )

    private fun gate(
        deadline: Instant,
        cancellation: ConnectorCancellation
    ): MarketplaceLivePipelineBlockDetail? = when {
        cancellation.isCancelled() -> MarketplaceLivePipelineBlockDetail.Cancelled
        !clock.instant().isBefore(deadline) ->
            MarketplaceLivePipelineBlockDetail.DeadlineExceeded
        else -> null
    }

    private fun sourceSummary(
        invocations: Int,
        committed: Int,
        alreadyCommitted: Int,
        records: Long,
        stop: MarketplaceLivePipelineSourceStop
    ) = MarketplaceLivePipelineSourceSummary(
        invocations = invocations,
        committedPages = committed,
        alreadyCommittedPages = alreadyCommitted,
        records = records,
        stop = stop
    )

    private sealed interface SourcePhase {
        data class Done(
            val summary: MarketplaceLivePipelineSourceSummary
        ) : SourcePhase

        data class Blocked(
            val detail: MarketplaceLivePipelineBlockDetail,
            val summary: MarketplaceLivePipelineSourceSummary?
        ) : SourcePhase
    }

    private sealed interface PromotionPhase {
        data class Done(
            val summary: MarketplaceLivePipelinePromotionSummary
        ) : PromotionPhase

        data class Blocked(
            val detail: MarketplaceLivePipelineBlockDetail,
            val summary: MarketplaceLivePipelinePromotionSummary
        ) : PromotionPhase
    }

    private sealed interface ProjectionPhase {
        data class Done(
            val summary: MarketplaceLivePipelineProjectionSummary
        ) : ProjectionPhase

        data class Blocked(
            val detail: MarketplaceLivePipelineBlockDetail,
            val summary: MarketplaceLivePipelineProjectionSummary
        ) : ProjectionPhase
    }
}
