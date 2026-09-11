package io.flooow.marketplace.operations.live

import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorExecutionFailureKind
import io.flooow.integration.connector.ConnectorExecutionOutcome
import io.flooow.integration.connector.ConnectorInvocation
import io.flooow.integration.connector.ConnectorSuccessKind
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.ProviderKey
import io.flooow.marketplace.operations.economics.evidence.ChangeSequenceCheckpoint
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderRevenuePromotionBatchResult
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderRevenuePromotionBlockReason
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionBatchResult
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionBlockReason
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionProcessorResult
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarketplaceLivePipelineTest {
    private val now = Instant.parse("2026-09-06T23:40:00.000000Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val organization = OrganizationId(UUID(0, 801))
    private val connection = IntegrationConnectionId(UUID(0, 802))
    private val capability = MarketplaceLivePipelineContract.CAPABILITY
    private val provider = MarketplaceLivePipelineContract.PROVIDER

    @Test
    fun `happy path drains durable stages after retryable source stop`() {
        val source = FakeSourceRunner(
            listOf(
                success(ConnectorSuccessKind.COMMITTED, 2),
                success(ConnectorSuccessKind.ALREADY_COMMITTED, 1),
                failure(
                    ConnectorExecutionFailureKind.REMOTE_TEMPORARY,
                    Duration.ofMinutes(20)
                )
            )
        )
        val occurrence = FakeOccurrencePromoter(
            listOf(
                occurrenceCompleted(3, promoted = 2, duplicates = 1),
                occurrenceCompleted(0)
            )
        )
        val revenue = FakeRevenuePromoter(
            listOf(
                revenueCompleted(2, promoted = 2),
                revenueCompleted(0)
            )
        )
        val projection = FakeProjectionProcessor(
            listOf(
                MarketplaceSalesIntelligenceProjectionProcessorResult.Success(
                    processedChanges = 5,
                    checkpoint = ChangeSequenceCheckpoint(5)
                ),
                MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges
            )
        )

        val result = assertIs<MarketplaceLivePipelineResult.Completed>(
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        assertEquals(MarketplaceLivePipelineSourceStop.RETRYABLE_FAILURE, result.source.stop)
        assertEquals(ConnectorExecutionFailureKind.REMOTE_TEMPORARY, result.source.failureKind)
        assertEquals(Duration.ofMinutes(20), result.source.retryAfter)
        assertEquals(3, result.source.records)
        assertEquals(1, result.source.committedPages)
        assertEquals(1, result.source.alreadyCommittedPages)
        assertEquals(3, source.invocations.map { it.invocationId }.distinct().size)
        assertTrue(source.invocations.all { it.capability == capability })
        assertTrue(source.invocations.all { it.budget.deadline == now.plusSeconds(120) })

        assertTrue(result.occurrence.drained)
        assertEquals(3, result.occurrence.examined)
        assertTrue(result.revenue.drained)
        assertEquals(2, result.revenue.examined)
        assertTrue(result.projection.drained)
        assertEquals(5, result.projection.processedChanges)
    }

    @Test
    fun `non retryable source failure still drains durable backlog`() {
        val source = FakeSourceRunner(
            listOf(failure(ConnectorExecutionFailureKind.AUTHENTICATION_REQUIRED))
        )
        val occurrence = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val revenue = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val projection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges)
        )

        val result = assertIs<MarketplaceLivePipelineResult.Completed>(
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        assertEquals(
            MarketplaceLivePipelineSourceStop.NON_RETRYABLE_FAILURE,
            result.source.stop
        )
        assertEquals(1, occurrence.calls.get())
        assertEquals(1, revenue.calls.get())
        assertEquals(1, projection.calls.get())
    }

    @Test
    fun `exhausted source stops source loop and rate limit remains explicit`() {
        val exhaustedSource = FakeSourceRunner(
            listOf(
                ConnectorExecutionOutcome.Success(
                    kind = ConnectorSuccessKind.COMMITTED,
                    providerKey = provider,
                    capability = capability,
                    recordCount = 4,
                    exhausted = true,
                    observedAt = now
                ),
                success(ConnectorSuccessKind.COMMITTED, 99)
            )
        )
        val emptyOccurrence = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val emptyRevenue = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val emptyProjection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges)
        )

        val exhausted = assertIs<MarketplaceLivePipelineResult.Completed>(
            service(
                exhaustedSource,
                emptyOccurrence,
                emptyRevenue,
                emptyProjection
            ).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        assertEquals(MarketplaceLivePipelineSourceStop.EXHAUSTED, exhausted.source.stop)
        assertEquals(1, exhaustedSource.calls.get())

        val limitedSource = FakeSourceRunner(
            listOf(
                failure(
                    ConnectorExecutionFailureKind.RATE_LIMITED,
                    Duration.ofSeconds(45)
                )
            )
        )
        val occurrence2 = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val revenue2 = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val projection2 = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges)
        )

        val limited = assertIs<MarketplaceLivePipelineResult.Completed>(
            service(limitedSource, occurrence2, revenue2, projection2).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        assertEquals(
            MarketplaceLivePipelineSourceStop.RETRYABLE_FAILURE,
            limited.source.stop
        )
        assertEquals(ConnectorExecutionFailureKind.RATE_LIMITED, limited.source.failureKind)
        assertEquals(Duration.ofSeconds(45), limited.source.retryAfter)
    }

    @Test
    fun `wrong provider fails closed before downstream`() {
        val source = FakeSourceRunner(
            listOf(
                ConnectorExecutionOutcome.Success(
                    kind = ConnectorSuccessKind.COMMITTED,
                    providerKey = ProviderKey.of("omie"),
                    capability = capability,
                    recordCount = 1,
                    exhausted = false,
                    observedAt = now
                )
            )
        )
        val occurrence = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val revenue = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val projection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges)
        )

        val result = assertIs<MarketplaceLivePipelineResult.Blocked>(
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        val detail = assertIs<MarketplaceLivePipelineBlockDetail.IntegrityFailure>(
            result.detail
        )
        assertEquals(MarketplaceLivePipelineIntegrityStage.SOURCE, detail.stage)
        assertEquals(0, occurrence.calls.get())
        assertEquals(0, revenue.calls.get())
        assertEquals(0, projection.calls.get())
    }

    @Test
    fun `wrong source capability fails closed`() {
        val source = FakeSourceRunner(
            listOf(
                ConnectorExecutionOutcome.Failure(
                    kind = ConnectorExecutionFailureKind.CONNECTOR_UNAVAILABLE,
                    providerKey = provider,
                    capability = io.flooow.integration.connector.ConnectorCapability.of(
                        "marketplace-economic.other-source"
                    )
                )
            )
        )
        val occurrence = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val revenue = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val projection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges)
        )

        val result = assertIs<MarketplaceLivePipelineResult.Blocked>(
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        val detail = assertIs<MarketplaceLivePipelineBlockDetail.IntegrityFailure>(
            result.detail
        )
        assertEquals(MarketplaceLivePipelineIntegrityStage.SOURCE, detail.stage)
        assertEquals(0, occurrence.calls.get())
    }

    @Test
    fun `occurrence block stops revenue and projection`() {
        val source = FakeSourceRunner(
            listOf(failure(ConnectorExecutionFailureKind.REMOTE_PERMANENT))
        )
        val occurrence = FakeOccurrencePromoter(
            listOf(
                MarketplaceOrderSourcePromotionBatchResult.Blocked(
                    completedBeforeBlock = 0,
                    reason = MarketplaceOrderSourcePromotionBlockReason.INTEGRITY_FAILURE
                )
            )
        )
        val revenue = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val projection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges)
        )

        val result = assertIs<MarketplaceLivePipelineResult.Blocked>(
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        val detail = assertIs<MarketplaceLivePipelineBlockDetail.OccurrenceBlocked>(
            result.detail
        )
        assertEquals(MarketplaceOrderSourcePromotionBlockReason.INTEGRITY_FAILURE, detail.reason)
        assertEquals(0, revenue.calls.get())
        assertEquals(0, projection.calls.get())
    }

    @Test
    fun `revenue block stops projection`() {
        val source = FakeSourceRunner(
            listOf(failure(ConnectorExecutionFailureKind.REMOTE_PERMANENT))
        )
        val occurrence = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val revenue = FakeRevenuePromoter(
            listOf(
                MarketplaceOrderRevenuePromotionBatchResult.Blocked(
                    completedBeforeBlock = 0,
                    reason = MarketplaceOrderRevenuePromotionBlockReason.INTEGRITY_FAILURE
                )
            )
        )
        val projection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges)
        )

        val result = assertIs<MarketplaceLivePipelineResult.Blocked>(
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        val detail = assertIs<MarketplaceLivePipelineBlockDetail.RevenueBlocked>(
            result.detail
        )
        assertEquals(MarketplaceOrderRevenuePromotionBlockReason.INTEGRITY_FAILURE, detail.reason)
        assertEquals(0, projection.calls.get())
    }

    @Test
    fun `inconsistent revenue summary fails closed before projection`() {
        val source = FakeSourceRunner(
            listOf(failure(ConnectorExecutionFailureKind.REMOTE_PERMANENT))
        )
        val occurrence = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val revenue = FakeRevenuePromoter(
            listOf(
                MarketplaceOrderRevenuePromotionBatchResult.Completed(
                    examined = 2,
                    promoted = 1,
                    duplicates = 0,
                    identityConflicts = 0,
                    evidenceConflicts = 0
                )
            )
        )
        val projection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges)
        )

        val result = assertIs<MarketplaceLivePipelineResult.Blocked>(
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        val detail = assertIs<MarketplaceLivePipelineBlockDetail.IntegrityFailure>(
            result.detail
        )
        assertEquals(MarketplaceLivePipelineIntegrityStage.REVENUE, detail.stage)
        assertEquals(0, projection.calls.get())
    }

    @Test
    fun `projection zero-success and explicit integrity failure both block`() {
        val sourceA = FakeSourceRunner(
            listOf(failure(ConnectorExecutionFailureKind.REMOTE_PERMANENT))
        )
        val occurrenceA = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val revenueA = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val zeroProjection = FakeProjectionProcessor(
            listOf(
                MarketplaceSalesIntelligenceProjectionProcessorResult.Success(
                    processedChanges = 0,
                    checkpoint = ChangeSequenceCheckpoint.NONE
                )
            )
        )

        val zero = assertIs<MarketplaceLivePipelineResult.Blocked>(
            service(sourceA, occurrenceA, revenueA, zeroProjection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )
        val zeroDetail = assertIs<MarketplaceLivePipelineBlockDetail.IntegrityFailure>(
            zero.detail
        )
        assertEquals(MarketplaceLivePipelineIntegrityStage.PROJECTION, zeroDetail.stage)

        val sourceB = FakeSourceRunner(
            listOf(failure(ConnectorExecutionFailureKind.REMOTE_PERMANENT))
        )
        val occurrenceB = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val revenueB = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val integrityProjection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure)
        )

        val integrity = assertIs<MarketplaceLivePipelineResult.Blocked>(
            service(sourceB, occurrenceB, revenueB, integrityProjection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )
        assertEquals(
            MarketplaceLivePipelineBlockDetail.ProjectionIntegrityFailure,
            integrity.detail
        )
    }

    @Test
    fun `projection checkpoint conflict blocks`() {
        val source = FakeSourceRunner(
            listOf(failure(ConnectorExecutionFailureKind.REMOTE_PERMANENT))
        )
        val occurrence = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val revenue = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val projection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.CheckpointConflict)
        )

        val result = assertIs<MarketplaceLivePipelineResult.Blocked>(
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        assertEquals(
            MarketplaceLivePipelineBlockDetail.ProjectionCheckpointConflict,
            result.detail
        )
    }

    @Test
    fun `stage caps are bounded and surface drained false`() {
        val source = FakeSourceRunner(
            List(5) { success(ConnectorSuccessKind.COMMITTED, 1) }
        )
        val occurrence = FakeOccurrencePromoter(
            List(5) { occurrenceCompleted(1, promoted = 1) }
        )
        val revenue = FakeRevenuePromoter(
            List(5) { revenueCompleted(1, promoted = 1) }
        )
        val projection = FakeProjectionProcessor(
            List(5) {
                MarketplaceSalesIntelligenceProjectionProcessorResult.Success(
                    processedChanges = 1,
                    checkpoint = ChangeSequenceCheckpoint((it + 1).toLong())
                )
            }
        )

        val result = assertIs<MarketplaceLivePipelineResult.Completed>(
            service(source, occurrence, revenue, projection).run(
                organizationId = organization,
                connectionId = connection,
                deadline = now.plusSeconds(120),
                limits = MarketplaceLivePipelineLimits(
                    maxSourcePages = 2,
                    maxOccurrenceBatches = 2,
                    maxRevenueBatches = 2,
                    maxProjectionBatches = 2
                )
            )
        )

        assertEquals(MarketplaceLivePipelineSourceStop.PAGE_LIMIT, result.source.stop)
        assertEquals(2, source.calls.get())
        assertFalse(result.occurrence.drained)
        assertEquals(2, occurrence.calls.get())
        assertFalse(result.revenue.drained)
        assertEquals(2, revenue.calls.get())
        assertFalse(result.projection.drained)
        assertEquals(2, projection.calls.get())
    }

    @Test
    fun `inconsistent promotion summary fails closed`() {
        val source = FakeSourceRunner(
            listOf(failure(ConnectorExecutionFailureKind.REMOTE_PERMANENT))
        )
        val occurrence = FakeOccurrencePromoter(
            listOf(
                MarketplaceOrderSourcePromotionBatchResult.Completed(
                    examined = 2,
                    promoted = 1,
                    duplicates = 0,
                    identityConflicts = 0,
                    evidenceConflicts = 0
                )
            )
        )
        val revenue = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val projection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges)
        )

        val result = assertIs<MarketplaceLivePipelineResult.Blocked>(
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(120)
            )
        )

        val detail = assertIs<MarketplaceLivePipelineBlockDetail.IntegrityFailure>(
            result.detail
        )
        assertEquals(MarketplaceLivePipelineIntegrityStage.OCCURRENCE, detail.stage)
        assertEquals(0, revenue.calls.get())
        assertEquals(0, projection.calls.get())
    }

    @Test
    fun `cancellation and deadline boundaries are explicit`() {
        val source = FakeSourceRunner(listOf(success(ConnectorSuccessKind.COMMITTED, 1)))
        val occurrence = FakeOccurrencePromoter(listOf(occurrenceCompleted(0)))
        val revenue = FakeRevenuePromoter(listOf(revenueCompleted(0)))
        val projection = FakeProjectionProcessor(
            listOf(MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges)
        )

        val cancelled = assertIs<MarketplaceLivePipelineResult.Blocked>(
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(120),
                cancellation = ConnectorCancellation { true }
            )
        )
        assertEquals(MarketplaceLivePipelineBlockDetail.Cancelled, cancelled.detail)
        assertEquals(0, source.calls.get())
        assertEquals(0, occurrence.calls.get())
        assertEquals(0, revenue.calls.get())
        assertEquals(0, projection.calls.get())

        assertFailsWith<IllegalArgumentException> {
            service(source, occurrence, revenue, projection).run(
                organization,
                connection,
                now.plusSeconds(301)
            )
        }
    }

    private fun service(
        source: FakeSourceRunner,
        occurrence: FakeOccurrencePromoter,
        revenue: FakeRevenuePromoter,
        projection: FakeProjectionProcessor
    ) = MarketplaceLivePipelineService(
        source = source,
        occurrence = occurrence,
        revenue = revenue,
        projection = projection,
        clock = clock,
        invocationIds = MarketplaceLivePipelineInvocationIdFactory {
            io.flooow.integration.connector.ConnectorInvocationId(
                UUID(0, source.invocationIds.incrementAndGet().toLong())
            )
        }
    )

    private fun success(
        kind: ConnectorSuccessKind,
        records: Int
    ) = ConnectorExecutionOutcome.Success(
        kind = kind,
        providerKey = provider,
        capability = capability,
        recordCount = records,
        exhausted = false,
        observedAt = now
    )

    private fun failure(
        kind: ConnectorExecutionFailureKind,
        retryAfter: Duration? = null
    ) = ConnectorExecutionOutcome.Failure(
        kind = kind,
        providerKey = provider,
        capability = capability,
        retryAfter = retryAfter
    )

    private fun occurrenceCompleted(
        examined: Int,
        promoted: Int = 0,
        duplicates: Int = 0,
        identityConflicts: Int = 0,
        evidenceConflicts: Int = 0
    ) = MarketplaceOrderSourcePromotionBatchResult.Completed(
        examined,
        promoted,
        duplicates,
        identityConflicts,
        evidenceConflicts
    )

    private fun revenueCompleted(
        examined: Int,
        promoted: Int = 0,
        duplicates: Int = 0,
        identityConflicts: Int = 0,
        evidenceConflicts: Int = 0
    ) = MarketplaceOrderRevenuePromotionBatchResult.Completed(
        examined,
        promoted,
        duplicates,
        identityConflicts,
        evidenceConflicts
    )
}

private class FakeSourceRunner(
    outcomes: List<ConnectorExecutionOutcome>
) : MarketplaceLivePipelineSourceRunner {
    private val outcomes = ArrayDeque(outcomes)
    val calls = AtomicInteger(0)
    val invocationIds = AtomicInteger(0)
    val invocations = mutableListOf<ConnectorInvocation>()

    override fun execute(
        invocation: ConnectorInvocation,
        cancellation: ConnectorCancellation
    ): ConnectorExecutionOutcome {
        calls.incrementAndGet()
        invocations += invocation
        return outcomes.removeFirst()
    }
}

private class FakeOccurrencePromoter(
    results: List<MarketplaceOrderSourcePromotionBatchResult>
) : MarketplaceLivePipelineOccurrencePromoter {
    private val results = ArrayDeque(results)
    val calls = AtomicInteger(0)

    override fun promote(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderSourcePromotionBatchResult {
        calls.incrementAndGet()
        return results.removeFirst()
    }
}

private class FakeRevenuePromoter(
    results: List<MarketplaceOrderRevenuePromotionBatchResult>
) : MarketplaceLivePipelineRevenuePromoter {
    private val results = ArrayDeque(results)
    val calls = AtomicInteger(0)

    override fun promote(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderRevenuePromotionBatchResult {
        calls.incrementAndGet()
        return results.removeFirst()
    }
}

private class FakeProjectionProcessor(
    results: List<MarketplaceSalesIntelligenceProjectionProcessorResult>
) : MarketplaceLivePipelineProjectionProcessor {
    private val results = ArrayDeque(results)
    val calls = AtomicInteger(0)

    override fun process(
        organizationId: OrganizationId,
        limit: Int
    ): MarketplaceSalesIntelligenceProjectionProcessorResult {
        calls.incrementAndGet()
        return results.removeFirst()
    }
}