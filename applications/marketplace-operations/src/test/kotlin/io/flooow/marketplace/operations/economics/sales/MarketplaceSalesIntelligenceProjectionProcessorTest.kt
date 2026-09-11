package io.flooow.marketplace.operations.economics.sales

import io.flooow.marketplace.operations.economics.EconomicExternalReferenceAbsenceReason
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthCalculationResult
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceOrder
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.ChangeSequenceCheckpoint
import io.flooow.marketplace.operations.economics.evidence.CheckpointAdvanceResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChange
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChangeFeed
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChangeFeedResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChangeKind
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceObservationId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicOrderOccurrenceObservation
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidence
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceMerger
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidencePersistResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceReadResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceRepository
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceUpdate
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicFact
import io.flooow.marketplace.operations.economics.evidence.ProjectionName
import io.flooow.marketplace.operations.economics.evidence.VersionedMarketplaceIndependentEconomicEvidence
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarketplaceSalesIntelligenceProjectionProcessorTest {
    private val organization = OrganizationId(uuid(1))
    private val otherOrganization = OrganizationId(uuid(2))
    private val now = Instant.parse("2026-09-06T12:00:00.123456Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `empty batch leaves checkpoint untouched`() {
        val feed = FakeFeed()
        val projection = FakeProjection()
        val repository = MutableRepository()
        val processor = processor(feed, projection, repository)

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.NoChanges>(
            processor.processBatch(organization, 100)
        )
        assertEquals(ChangeSequenceCheckpoint.NONE, feed.checkpoint)
        assertTrue(projection.records.isEmpty())
    }

    @Test
    fun `changes are materialized in deterministic ascending sequence`() {
        val first = subject(10)
        val second = subject(11)
        val feed = FakeFeed(
            mutableListOf(
                change(first, 1, 2),
                change(second, 1, 5)
            )
        )
        val projection = FakeProjection()
        val repository = MutableRepository(
            mutableMapOf(
                first to versioned(empty(first), 1),
                second to versioned(empty(second), 1)
            )
        )

        val result = assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.Success>(
            processor(feed, projection, repository).processBatch(organization, 100)
        )

        assertEquals(listOf(2L, 5L), projection.writeSequences)
        assertEquals(ChangeSequenceCheckpoint(5), result.checkpoint)
    }

    @Test
    fun `not ready materializes unresolved without invoking calculator`() {
        val subject = subject(12)
        val feed = FakeFeed(mutableListOf(change(subject, 1, 1)))
        val projection = FakeProjection()
        val repository = MutableRepository(
            mutableMapOf(subject to versioned(empty(subject), 1))
        )
        var calculatorCalls = 0

        val processor = MarketplaceSalesIntelligenceProjectionProcessor(
            evidenceRepository = repository,
            changeFeed = feed,
            projection = projection,
            clock = clock,
            calculator = {
                calculatorCalls += 1
                error("calculator must not be called for NotReady")
            }
        )

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.Success>(
            processor.processBatch(organization, 100)
        )
        assertEquals(0, calculatorCalls)
        assertIs<MarketplaceSalesIntelligenceState.Unresolved>(
            projection.records.getValue(subject.orderId).state
        )
    }

    @Test
    fun `ready evidence invokes calculator and materializes calculated state`() {
        val subject = subject(13)
        val feed = FakeFeed(mutableListOf(change(subject, 1, 1)))
        val projection = FakeProjection()
        val repository = MutableRepository(
            mutableMapOf(subject to versioned(withOccurrence(subject), 1))
        )
        var calculatorCalls = 0

        val processor = MarketplaceSalesIntelligenceProjectionProcessor(
            evidenceRepository = repository,
            changeFeed = feed,
            projection = projection,
            clock = clock,
            calculator = { order: MarketplaceOrder ->
                calculatorCalls += 1
                io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthCalculator
                    .calculate(order)
            }
        )

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.Success>(
            processor.processBatch(organization, 100)
        )
        assertEquals(1, calculatorCalls)
        assertIs<MarketplaceSalesIntelligenceState.Calculated>(
            projection.records.getValue(subject.orderId).state
        )
    }

    @Test
    fun `newer not ready replaces calculated state with no stale calculated payload retained`() {
        val subject = subject(14)
        val feed = FakeFeed(mutableListOf(change(subject, 1, 1)))
        val projection = FakeProjection()
        val repository = MutableRepository(
            mutableMapOf(subject to versioned(withOccurrence(subject), 1))
        )
        val processor = processor(feed, projection, repository)

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.Success>(
            processor.processBatch(organization, 100)
        )
        assertIs<MarketplaceSalesIntelligenceState.Calculated>(
            projection.records.getValue(subject.orderId).state
        )

        repository.put(subject, versioned(empty(subject), 2))
        feed.append(change(subject, 2, 2))

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.Success>(
            processor.processBatch(organization, 100)
        )

        val current = projection.records.getValue(subject.orderId)
        assertEquals(MarketplaceEconomicEvidenceVersion(2), current.sourceEvidenceVersion)
        assertEquals(ChangeSequenceCheckpoint(2), current.lastAppliedChangeSequence)
        assertIs<MarketplaceSalesIntelligenceState.Unresolved>(current.state)
    }

    @Test
    fun `already current projection skips canonical refetch`() {
        val subject = subject(15)
        val projection = FakeProjection()
        projection.records[subject.orderId] = unresolvedRecord(subject, 3, 1)
        val feed = FakeFeed(mutableListOf(change(subject, 1, 3)))
        val repository = MutableRepository()

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.Success>(
            processor(feed, projection, repository).processBatch(organization, 100)
        )
        assertEquals(0, repository.findCalls)
        assertEquals(0, projection.appliedMutations)
    }

    @Test
    fun `projection failure blocks checkpoint advancement`() {
        val subject = subject(16)
        val feed = FakeFeed(mutableListOf(change(subject, 1, 1)))
        val projection = FakeProjection(failWrites = true)
        val repository = MutableRepository(
            mutableMapOf(subject to versioned(empty(subject), 1))
        )

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure>(
            processor(feed, projection, repository).processBatch(organization, 100)
        )
        assertEquals(ChangeSequenceCheckpoint.NONE, feed.checkpoint)
    }

    @Test
    fun `checkpoint failure after projection durability replays safely`() {
        val subject = subject(17)
        val feed = FakeFeed(
            mutableListOf(change(subject, 1, 1)),
            failNextAdvance = true
        )
        val projection = FakeProjection()
        val repository = MutableRepository(
            mutableMapOf(subject to versioned(empty(subject), 1))
        )
        val processor = processor(feed, projection, repository)

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure>(
            processor.processBatch(organization, 100)
        )
        assertEquals(1, projection.appliedMutations)
        assertEquals(ChangeSequenceCheckpoint.NONE, feed.checkpoint)

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.Success>(
            processor.processBatch(organization, 100)
        )
        assertEquals(1, projection.appliedMutations)
        assertEquals(ChangeSequenceCheckpoint(1), feed.checkpoint)
    }

    @Test
    fun `repeated same change is deterministic projection no op`() {
        val subject = subject(18)
        val feed = FakeFeed(mutableListOf(change(subject, 1, 1)))
        val projection = FakeProjection()
        val repository = MutableRepository(
            mutableMapOf(subject to versioned(empty(subject), 1))
        )
        val processor = processor(feed, projection, repository)

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.Success>(
            processor.processBatch(organization, 100)
        )
        assertEquals(1, projection.appliedMutations)

        feed.checkpoint = ChangeSequenceCheckpoint.NONE

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.Success>(
            processor.processBatch(organization, 100)
        )
        assertEquals(1, projection.appliedMutations)
    }

    @Test
    fun `current evidence may be newer than invalidation evidence version`() {
        val subject = subject(19)
        val feed = FakeFeed(mutableListOf(change(subject, 1, 1)))
        val projection = FakeProjection()
        val repository = MutableRepository(
            mutableMapOf(subject to versioned(withOccurrence(subject), 7))
        )

        processor(feed, projection, repository).processBatch(organization, 100)

        val current = projection.records.getValue(subject.orderId)
        assertEquals(MarketplaceEconomicEvidenceVersion(7), current.sourceEvidenceVersion)
        assertEquals(ChangeSequenceCheckpoint(1), current.lastAppliedChangeSequence)
    }

    @Test
    fun `processor rejects cross organization change and does not acknowledge batch`() {
        val foreign = subject(20, otherOrganization)
        val feed = FakeFeed(mutableListOf(change(foreign, 1, 1)))
        val projection = FakeProjection()
        val repository = MutableRepository(
            mutableMapOf(foreign to versioned(empty(foreign), 1))
        )

        assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.IntegrityFailure>(
            processor(feed, projection, repository).processBatch(organization, 100)
        )
        assertEquals(ChangeSequenceCheckpoint.NONE, feed.checkpoint)
        assertTrue(projection.records.isEmpty())
        assertEquals(0, repository.findCalls)
    }

    @Test
    fun `batch checkpoint destination is exactly final returned sequence`() {
        val first = subject(21)
        val second = subject(22)
        val feed = FakeFeed(
            mutableListOf(
                change(first, 1, 2),
                change(second, 1, 5)
            )
        )
        val projection = FakeProjection()
        val repository = MutableRepository(
            mutableMapOf(
                first to versioned(empty(first), 1),
                second to versioned(empty(second), 1)
            )
        )

        val result = assertIs<MarketplaceSalesIntelligenceProjectionProcessorResult.Success>(
            processor(feed, projection, repository).processBatch(organization, 100)
        )

        assertEquals(ChangeSequenceCheckpoint(5), result.checkpoint)
        assertEquals(ChangeSequenceCheckpoint(5), feed.lastAdvanceDestination)
    }

    private fun processor(
        feed: FakeFeed,
        projection: FakeProjection,
        repository: MutableRepository
    ) = MarketplaceSalesIntelligenceProjectionProcessor(
        evidenceRepository = repository,
        changeFeed = feed,
        projection = projection,
        clock = clock
    )

    private fun subject(
        seed: Long,
        organizationId: OrganizationId = organization
    ) = MarketplaceEconomicEvidenceSubject(
        organizationId = organizationId,
        orderId = MarketplaceOrderId(uuid(seed)),
        marketplace = MarketplaceKey("mercado-livre"),
        externalOrderId = MarketplaceExternalOrderId("order-$seed"),
        currency = MarketplaceCurrency("BRL")
    )

    private fun change(
        subject: MarketplaceEconomicEvidenceSubject,
        evidenceVersion: Long,
        sequence: Long
    ) = MarketplaceEconomicEvidenceChange(
        subject = subject,
        evidenceVersion = MarketplaceEconomicEvidenceVersion(evidenceVersion),
        changeSequence = ChangeSequenceCheckpoint(sequence),
        changeKind = MarketplaceEconomicEvidenceChangeKind.FACT
    )

    private fun empty(subject: MarketplaceEconomicEvidenceSubject) =
        MarketplaceIndependentEconomicEvidence.empty(subject)

    private fun withOccurrence(
        subject: MarketplaceEconomicEvidenceSubject
    ): MarketplaceIndependentEconomicEvidence {
        val occurrence = MarketplaceIndependentEconomicFact.OrderOccurrence(
            MarketplaceEconomicOrderOccurrenceObservation(
                id = MarketplaceEconomicEvidenceObservationId.parse(
                    uuid(subject.orderId.value.leastSignificantBits + 10_000).toString()
                ),
                subject = subject,
                source = EconomicSource(
                    kind = EconomicSourceKind.MANUAL,
                    systemKey = EconomicSourceSystemKey("operator"),
                    externalReference = EconomicExternalReferenceState.Absent(
                        EconomicExternalReferenceAbsenceReason.INTERNAL_ORIGIN
                    )
                ),
                occurredAt = now.minusSeconds(60),
                observedAt = now
            )
        )
        val applied = MarketplaceIndependentEconomicEvidenceMerger.apply(
            MarketplaceIndependentEconomicEvidence.empty(subject),
            MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(occurrence)
        )
        return assertIs<MarketplaceIndependentEconomicEvidenceResult.Applied>(applied).evidence
    }

    private fun versioned(
        evidence: MarketplaceIndependentEconomicEvidence,
        version: Long
    ) = VersionedMarketplaceIndependentEconomicEvidence(
        evidence = evidence,
        version = MarketplaceEconomicEvidenceVersion(version)
    )

    private fun unresolvedRecord(
        subject: MarketplaceEconomicEvidenceSubject,
        sequence: Long,
        evidenceVersion: Long
    ) = MarketplaceSalesIntelligenceProjectionRecord(
        organizationId = subject.organizationId,
        marketplaceOrderId = subject.orderId,
        sourceEvidenceVersion = MarketplaceEconomicEvidenceVersion(evidenceVersion),
        state = MarketplaceSalesIntelligenceState.Unresolved(
            io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssembler.POLICY_VERSION,
            setOf(
                io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssemblyNotReadyReason
                    .ORDER_OCCURRED_AT_UNRESOLVED
            )
        ),
        lastAppliedChangeSequence = ChangeSequenceCheckpoint(sequence),
        projectedAt = now
    )

    private fun uuid(value: Long): UUID = UUID(0L, value)

    private class MutableRepository(
        private val evidence: MutableMap<
            MarketplaceEconomicEvidenceSubject,
            VersionedMarketplaceIndependentEconomicEvidence
        > = mutableMapOf()
    ) : MarketplaceIndependentEconomicEvidenceRepository {
        var findCalls: Int = 0
            private set

        fun put(
            subject: MarketplaceEconomicEvidenceSubject,
            value: VersionedMarketplaceIndependentEconomicEvidence
        ) {
            evidence[subject] = value
        }

        override fun find(
            subject: MarketplaceEconomicEvidenceSubject
        ): MarketplaceIndependentEconomicEvidenceReadResult {
            findCalls += 1
            val value = evidence[subject]
            return if (value == null) {
                MarketplaceIndependentEconomicEvidenceReadResult.NotFound
            } else {
                MarketplaceIndependentEconomicEvidenceReadResult.Found(value)
            }
        }

        override fun apply(
            expectedVersion: MarketplaceEconomicEvidenceVersion,
            update: MarketplaceIndependentEconomicEvidenceUpdate
        ): MarketplaceIndependentEconomicEvidencePersistResult =
            error("not used")
    }

    private class FakeProjection(
        private val failWrites: Boolean = false
    ) : MarketplaceSalesIntelligenceProjection {
        val records =
            linkedMapOf<MarketplaceOrderId, MarketplaceSalesIntelligenceProjectionRecord>()
        val writeSequences = mutableListOf<Long>()
        var appliedMutations: Int = 0
            private set

        override fun currentBySubject(
            organizationId: OrganizationId,
            marketplaceOrderId: MarketplaceOrderId
        ) = MarketplaceSalesIntelligenceProjectionReadResult.Success(
            records[marketplaceOrderId]?.takeIf { it.organizationId == organizationId }
        )

        override fun materializeIfNewer(
            record: MarketplaceSalesIntelligenceProjectionRecord
        ): MarketplaceSalesIntelligenceProjectionWriteResult {
            if (failWrites) {
                return MarketplaceSalesIntelligenceProjectionWriteResult.IntegrityFailure
            }
            val current = records[record.marketplaceOrderId]
            if (current != null &&
                current.lastAppliedChangeSequence >= record.lastAppliedChangeSequence
            ) {
                return MarketplaceSalesIntelligenceProjectionWriteResult.NoOpAlreadyCurrent
            }
            records[record.marketplaceOrderId] = record
            writeSequences += record.lastAppliedChangeSequence.valueForPersistence()
            appliedMutations += 1
            return MarketplaceSalesIntelligenceProjectionWriteResult.Applied
        }

        override fun listByOrganization(
            organizationId: OrganizationId,
            cursor: MarketplaceSalesIntelligenceProjectionCursor?,
            limit: Int
        ) = MarketplaceSalesIntelligenceProjectionReadResult.Success(
            MarketplaceSalesIntelligenceProjectionPage(emptyList(), null)
        )

        override fun detailByOrganizationAndSubject(
            organizationId: OrganizationId,
            marketplaceOrderId: MarketplaceOrderId
        ) = currentBySubject(organizationId, marketplaceOrderId)
    }

    private class FakeFeed(
        private val changes: MutableList<MarketplaceEconomicEvidenceChange> = mutableListOf(),
        private var failNextAdvance: Boolean = false
    ) : MarketplaceEconomicEvidenceChangeFeed {
        var checkpoint: ChangeSequenceCheckpoint = ChangeSequenceCheckpoint.NONE
        var lastAdvanceDestination: ChangeSequenceCheckpoint? = null
            private set

        fun append(change: MarketplaceEconomicEvidenceChange) {
            changes += change
        }

        override fun changesSince(
            organizationId: OrganizationId,
            checkpoint: ChangeSequenceCheckpoint,
            limit: Int
        ) = MarketplaceEconomicEvidenceChangeFeedResult.Success(
            changes.filter { it.changeSequence > checkpoint }.take(limit)
        )

        override fun organizationsWithPendingChanges(
            projectionName: ProjectionName,
            limit: Int
        ) = MarketplaceEconomicEvidenceChangeFeedResult.Success(
            changes
                .filter { it.changeSequence > checkpoint }
                .map { it.subject.organizationId }
                .distinct()
                .take(limit)
        )

        override fun currentCheckpoint(
            organizationId: OrganizationId,
            projectionName: ProjectionName
        ) = MarketplaceEconomicEvidenceChangeFeedResult.Success(checkpoint)

        override fun advanceCheckpoint(
            organizationId: OrganizationId,
            projectionName: ProjectionName,
            expected: ChangeSequenceCheckpoint,
            next: ChangeSequenceCheckpoint
        ): MarketplaceEconomicEvidenceChangeFeedResult<CheckpointAdvanceResult> {
            lastAdvanceDestination = next
            if (failNextAdvance) {
                failNextAdvance = false
                return MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure
            }
            if (checkpoint != expected) {
                return MarketplaceEconomicEvidenceChangeFeedResult.Success(
                    CheckpointAdvanceResult.Stale(checkpoint)
                )
            }
            checkpoint = next
            return MarketplaceEconomicEvidenceChangeFeedResult.Success(
                CheckpointAdvanceResult.Advanced(next)
            )
        }
    }
}
