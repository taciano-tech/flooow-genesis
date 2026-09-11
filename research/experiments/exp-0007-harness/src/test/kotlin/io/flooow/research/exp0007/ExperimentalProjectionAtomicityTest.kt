package io.flooow.research.exp0007

import io.flooow.marketplace.operations.economics.*
import io.flooow.marketplace.operations.economics.evidence.*
import io.flooow.marketplace.persistence.postgres.PostgresConfiguration
import io.flooow.marketplace.persistence.postgres.PostgresMarketplaceEconomicEvidenceChangeFeed
import io.flooow.marketplace.persistence.postgres.PostgresMarketplaceIndependentEconomicEvidenceRepository
import io.flooow.organization.OrganizationId
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExperimentalProjectionAtomicityTest {

    private lateinit var postgres: PostgreSQLContainer
    private lateinit var store: ExperimentalProjectionStore
    private lateinit var configuration: PostgresConfiguration
    private lateinit var realFeed: PostgresMarketplaceEconomicEvidenceChangeFeed
    private lateinit var realWriter: PostgresMarketplaceIndependentEconomicEvidenceRepository

    @BeforeAll
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()

        configuration = PostgresConfiguration(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        )

        Flyway.configure()
            .dataSource(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password
            )
            .load()
            .migrate()

        store = ExperimentalProjectionStore(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        )

        store.installSchema()

        realFeed = PostgresMarketplaceEconomicEvidenceChangeFeed(configuration)
        realWriter = PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration)
    }

    @AfterAll
    fun stopPostgres() {
        postgres.stop()
    }

    @Test
    fun `experimental harness starts from canonical V016 database`() {
        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        ).use { connection ->
            connection.prepareStatement(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success = true
                ORDER BY installed_rank DESC
                LIMIT 1
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals("016", result.getString("version"))
                }
            }
        }
    }

    @Test
    fun `experiment strategies are explicitly bounded to A B and C`() {
        assertEquals(
            setOf(
                ExperimentalProjectionStrategy.ATOMIC_SAME_TRANSACTION,
                ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT,
                ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT
            ),
            ExperimentalProjectionStrategy.entries.toSet()
        )
    }

    @Test
    fun `strategy A crash before commit rolls back projection and checkpoint together`() {
        store.reset()

        val organizationId = UUID.randomUUID()
        val subjectId = UUID.randomUUID()
        val projectionName = "sales-intelligence"
        val crashInjector =
            ExperimentalCrashInjector.at(ExperimentalCrashPoint.BEFORE_COMMIT)

        store.createOrganization(organizationId)

        assertFailsWith<ExperimentalInjectedCrash> {
            store.transaction { connection ->
                crashInjector.hit(ExperimentalCrashPoint.BEFORE_PROJECTION_WRITE)

                store.applyProjectionMonotonically(
                    connection = connection,
                    organizationId = organizationId,
                    subjectId = subjectId,
                    projectedValue = 4200,
                    sourceEvidenceVersion = 1,
                    changeSequence = 10
                )

                crashInjector.hit(ExperimentalCrashPoint.AFTER_PROJECTION_WRITE)
                crashInjector.hit(ExperimentalCrashPoint.BEFORE_CHECKPOINT_ADVANCE)

                store.advanceCheckpoint(
                    connection = connection,
                    organizationId = organizationId,
                    projectionName = projectionName,
                    nextChangeSequence = 10
                )

                crashInjector.hit(ExperimentalCrashPoint.AFTER_CHECKPOINT_ADVANCE)
                crashInjector.hit(ExperimentalCrashPoint.BEFORE_COMMIT)
            }
        }

        assertNull(store.projection(organizationId, subjectId))
        assertNull(store.checkpoint(organizationId, projectionName))
    }

    @Test
    fun `strategy B survives crash after projection commit through monotonic replay`() {
        store.reset()

        val organizationId = UUID.randomUUID()
        val subjectId = UUID.randomUUID()
        val projectionName = "sales-intelligence"

        store.createOrganization(organizationId)

        val firstWriteCount = store.transaction { connection ->
            store.applyProjectionMonotonically(
                connection = connection,
                organizationId = organizationId,
                subjectId = subjectId,
                projectedValue = 4200,
                sourceEvidenceVersion = 1,
                changeSequence = 10
            )
        }

        assertEquals(1, firstWriteCount)

        val crashInjector =
            ExperimentalCrashInjector.at(
                ExperimentalCrashPoint.AFTER_PROJECTION_COMMIT
            )

        assertFailsWith<ExperimentalInjectedCrash> {
            crashInjector.hit(ExperimentalCrashPoint.AFTER_PROJECTION_COMMIT)

            store.transaction { connection ->
                store.advanceCheckpoint(
                    connection = connection,
                    organizationId = organizationId,
                    projectionName = projectionName,
                    nextChangeSequence = 10
                )
            }
        }

        val durableBeforeReplay =
            assertNotNull(store.projection(organizationId, subjectId))

        assertEquals(4200, durableBeforeReplay.projectedValue)
        assertEquals(1, durableBeforeReplay.sourceEvidenceVersion)
        assertEquals(10, durableBeforeReplay.lastAppliedChangeSequence)
        assertNull(store.checkpoint(organizationId, projectionName))

        val replayWriteCount = store.transaction { connection ->
            store.applyProjectionMonotonically(
                connection = connection,
                organizationId = organizationId,
                subjectId = subjectId,
                projectedValue = 4200,
                sourceEvidenceVersion = 1,
                changeSequence = 10
            )
        }

        assertEquals(
            0,
            replayWriteCount,
            "duplicate change must be a deterministic projection no-op"
        )

        val durableAfterReplay =
            assertNotNull(store.projection(organizationId, subjectId))

        assertEquals(durableBeforeReplay, durableAfterReplay)

        val checkpointWriteCount = store.transaction { connection ->
            store.advanceCheckpoint(
                connection = connection,
                organizationId = organizationId,
                projectionName = projectionName,
                nextChangeSequence = 10
            )
        }

        assertEquals(1, checkpointWriteCount)

        val checkpoint =
            assertNotNull(store.checkpoint(organizationId, projectionName))

        assertEquals(10, checkpoint.lastChangeSequence)

        val finalProjection =
            assertNotNull(store.projection(organizationId, subjectId))

        assertEquals(durableBeforeReplay, finalProjection)
    }
    @Test
    fun `older change can never overwrite newer projection state`() {
        store.reset()

        val organizationId = UUID.randomUUID()
        val subjectId = UUID.randomUUID()

        store.createOrganization(organizationId)

        val newerWriteCount = store.transaction { connection ->
            store.applyProjectionMonotonically(
                connection = connection,
                organizationId = organizationId,
                subjectId = subjectId,
                projectedValue = 9000,
                sourceEvidenceVersion = 20,
                changeSequence = 20
            )
        }

        assertEquals(1, newerWriteCount)

        val beforeStaleReplay =
            assertNotNull(store.projection(organizationId, subjectId))

        assertEquals(9000, beforeStaleReplay.projectedValue)
        assertEquals(20, beforeStaleReplay.sourceEvidenceVersion)
        assertEquals(20, beforeStaleReplay.lastAppliedChangeSequence)

        val staleWriteCount = store.transaction { connection ->
            store.applyProjectionMonotonically(
                connection = connection,
                organizationId = organizationId,
                subjectId = subjectId,
                projectedValue = 1000,
                sourceEvidenceVersion = 10,
                changeSequence = 10
            )
        }

        assertEquals(
            0,
            staleWriteCount,
            "older change must never overwrite newer materialized state"
        )

        val afterStaleReplay =
            assertNotNull(store.projection(organizationId, subjectId))

        assertEquals(beforeStaleReplay, afterStaleReplay)
    }
    @Test
    fun `concurrent older and newer changes converge to newest projection state`() {
        store.reset()

        val organizationId = UUID.randomUUID()
        val subjectId = UUID.randomUUID()

        store.createOrganization(organizationId)

        val start = CountDownLatch(1)
        val ready = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val older = executor.submit<Int> {
                ready.countDown()
                assertTrue(start.await(10, TimeUnit.SECONDS))

                store.transaction { connection ->
                    store.applyProjectionMonotonically(
                        connection = connection,
                        organizationId = organizationId,
                        subjectId = subjectId,
                        projectedValue = 1000,
                        sourceEvidenceVersion = 10,
                        changeSequence = 10
                    )
                }
            }

            val newer = executor.submit<Int> {
                ready.countDown()
                assertTrue(start.await(10, TimeUnit.SECONDS))

                store.transaction { connection ->
                    store.applyProjectionMonotonically(
                        connection = connection,
                        organizationId = organizationId,
                        subjectId = subjectId,
                        projectedValue = 2000,
                        sourceEvidenceVersion = 20,
                        changeSequence = 20
                    )
                }
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val writeCounts = listOf(
                older.get(30, TimeUnit.SECONDS),
                newer.get(30, TimeUnit.SECONDS)
            )

            assertTrue(writeCounts.all { it == 0 || it == 1 })
            assertTrue(writeCounts.sum() in 1..2)

            val finalProjection =
                assertNotNull(store.projection(organizationId, subjectId))

            assertEquals(2000, finalProjection.projectedValue)
            assertEquals(20, finalProjection.sourceEvidenceVersion)
            assertEquals(20, finalProjection.lastAppliedChangeSequence)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
    @Test
    fun `contention profile is measured for hot and broad subjects`() {
        val workerCounts = listOf(2, 4, 8)

        workerCounts.forEach { workers ->
            val hot = runContentionScenario(workers, true)
            val broad = runContentionScenario(workers, false)

            println(
                "EXP-0007 CONTENTION workers=$workers " +
                    "HOT durationMs=${hot.durationMs} " +
                    "applied=${hot.appliedWrites} " +
                    "noops=${hot.noOpWrites} " +
                    "errors=${hot.errors}"
            )

            println(
                "EXP-0007 CONTENTION workers=$workers " +
                    "BROAD durationMs=${broad.durationMs} " +
                    "applied=${broad.appliedWrites} " +
                    "noops=${broad.noOpWrites} " +
                    "errors=${broad.errors}"
            )

            assertEquals(0, hot.errors)
            assertEquals(0, broad.errors)
            assertEquals(workers, hot.appliedWrites + hot.noOpWrites)
            assertEquals(workers, broad.appliedWrites + broad.noOpWrites)
            assertEquals(workers, broad.appliedWrites)
        }
    }

    private fun runContentionScenario(
        workers: Int,
        hotSubject: Boolean
    ): ExperimentalContentionResult {
        store.reset()

        val organizationId = UUID.randomUUID()
        store.createOrganization(organizationId)

        val sharedSubjectId = UUID.randomUUID()
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workers)

        val appliedWrites = AtomicInteger()
        val noOpWrites = AtomicInteger()
        val errors = AtomicInteger()

        val futures = (1..workers).map { worker ->
            executor.submit {
                val subjectId =
                    if (hotSubject) sharedSubjectId else UUID.randomUUID()

                ready.countDown()

                if (!start.await(10, TimeUnit.SECONDS)) {
                    errors.incrementAndGet()
                    return@submit
                }

                try {
                    val affected = store.transaction { connection ->
                        store.applyProjectionMonotonically(
                            connection = connection,
                            organizationId = organizationId,
                            subjectId = subjectId,
                            projectedValue = worker.toLong(),
                            sourceEvidenceVersion = worker.toLong(),
                            changeSequence = worker.toLong()
                        )
                    }

                    when (affected) {
                        1 -> appliedWrites.incrementAndGet()
                        0 -> noOpWrites.incrementAndGet()
                        else -> errors.incrementAndGet()
                    }
                } catch (_: Throwable) {
                    errors.incrementAndGet()
                }
            }
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS))

        val startedAt = System.nanoTime()
        start.countDown()

        futures.forEach {
            it.get(30, TimeUnit.SECONDS)
        }

        val durationMs =
            TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
            )

        executor.shutdownNow()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        if (hotSubject) {
            val finalProjection =
                assertNotNull(
                    store.projection(
                        organizationId,
                        sharedSubjectId
                    )
                )

            assertEquals(
                workers.toLong(),
                finalProjection.lastAppliedChangeSequence
            )

            assertEquals(
                workers.toLong(),
                finalProjection.projectedValue
            )
        }

        return ExperimentalContentionResult(
            durationMs,
            appliedWrites.get(),
            noOpWrites.get(),
            errors.get()
        )
    }

    @Test
    fun `strategy B concurrent workers converge through projection and real checkpoint CAS`() {
        store.reset()

        val organizationId = OrganizationId(UUID.randomUUID())
        val subject = MarketplaceEconomicEvidenceSubject(
            organizationId = organizationId,
            orderId = MarketplaceOrderId(UUID.randomUUID()),
            marketplace = MarketplaceKey("mercado-livre"),
            externalOrderId = MarketplaceExternalOrderId("exp-0007-gate-7"),
            currency = MarketplaceCurrency("BRL")
        )
        val projectionName =
            ProjectionName("sales-intelligence-concurrent")

        store.createOrganization(organizationId.value)

        val attempt =
            MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
                MarketplaceEconomicEvidenceCollectionAttempt(
                    MarketplaceEconomicEvidenceObservationId.parse(
                        UUID.randomUUID().toString()
                    ),
                    subject,
                    MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                    EconomicSourceSystemKey("exp-0007"),
                    MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE,
                    Instant.parse("2026-09-04T12:30:00Z")
                )
            )

        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            realWriter.apply(
                MarketplaceEconomicEvidenceVersion.ZERO,
                attempt
            )
        )

        val feedResult = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                List<MarketplaceEconomicEvidenceChange>
            >
        >(
            realFeed.changesSince(
                organizationId,
                ChangeSequenceCheckpoint.NONE,
                100
            )
        )

        val change = feedResult.value.single()

        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = (1..2).map {
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))

                    val projectionWrite =
                        store.transaction { connection ->
                            store.applyProjectionMonotonically(
                                connection = connection,
                                organizationId = organizationId.value,
                                subjectId = subject.orderId.value,
                                projectedValue = 4200,
                                sourceEvidenceVersion =
                                    change.evidenceVersion.valueForPersistence(),
                                changeSequence =
                                    change.changeSequence.valueForPersistence()
                            )
                        }

                    val checkpointResult =
                        realFeed.advanceCheckpoint(
                            organizationId,
                            projectionName,
                            ChangeSequenceCheckpoint.NONE,
                            change.changeSequence
                        )

                    projectionWrite to checkpointResult
                })
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val results = futures.map {
                it.get(30, TimeUnit.SECONDS)
            }

            val projectionWrites =
                results.map { it.first }

            assertEquals(
                1,
                projectionWrites.count { it == 1 }
            )

            assertEquals(
                1,
                projectionWrites.count { it == 0 }
            )

            val checkpointValues =
                results.map { (_, result) ->
                    assertIs<
                        MarketplaceEconomicEvidenceChangeFeedResult.Success<
                            CheckpointAdvanceResult
                        >
                    >(result).value
                }

            assertEquals(
                1,
                checkpointValues.count {
                    it is CheckpointAdvanceResult.Advanced
                }
            )

            assertEquals(
                1,
                checkpointValues.count {
                    it is CheckpointAdvanceResult.Stale
                }
            )

            val finalProjection =
                assertNotNull(
                    store.projection(
                        organizationId.value,
                        subject.orderId.value
                    )
                )

            assertEquals(
                change.changeSequence.valueForPersistence(),
                finalProjection.lastAppliedChangeSequence
            )

            assertEquals(
                4200,
                finalProjection.projectedValue
            )

            val durableCheckpoint = assertIs<
                MarketplaceEconomicEvidenceChangeFeedResult.Success<
                    ChangeSequenceCheckpoint
                >
            >(
                realFeed.currentCheckpoint(
                    organizationId,
                    projectionName
                )
            )

            assertEquals(
                change.changeSequence,
                durableCheckpoint.value
            )
        } finally {
            executor.shutdownNow()
            assertTrue(
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                )
            )
        }
    }

    @Test
    fun `strategy B partial batch crash replays deterministically and advances only after recovery`() {
        store.reset()

        val organizationId = OrganizationId(UUID.randomUUID())
        val projectionName =
            ProjectionName("sales-intelligence-partial-batch")

        store.createOrganization(organizationId.value)

        val subjects = (1..3).map { index ->
            MarketplaceEconomicEvidenceSubject(
                organizationId = organizationId,
                orderId = MarketplaceOrderId(UUID.randomUUID()),
                marketplace = MarketplaceKey("mercado-livre"),
                externalOrderId =
                    MarketplaceExternalOrderId(
                        "exp-0007-gate-8-$index"
                    ),
                currency = MarketplaceCurrency("BRL")
            )
        }

        subjects.forEachIndexed { index, subject ->
            val attempt =
                MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
                    MarketplaceEconomicEvidenceCollectionAttempt(
                        MarketplaceEconomicEvidenceObservationId.parse(
                            UUID.randomUUID().toString()
                        ),
                        subject,
                        MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                        EconomicSourceSystemKey("exp-0007"),
                        MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE,
                        Instant.parse("2026-09-04T13:00:00Z")
                            .plusSeconds(index.toLong())
                    )
                )

            assertIs<
                MarketplaceIndependentEconomicEvidencePersistResult.Applied
            >(
                realWriter.apply(
                    MarketplaceEconomicEvidenceVersion.ZERO,
                    attempt
                )
            )
        }

        val initialFeed = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                List<MarketplaceEconomicEvidenceChange>
            >
        >(
            realFeed.changesSince(
                organizationId,
                ChangeSequenceCheckpoint.NONE,
                100
            )
        ).value

        assertEquals(3, initialFeed.size)

        initialFeed.take(2).forEachIndexed { index, change ->
            val affected = store.transaction { connection ->
                store.applyProjectionMonotonically(
                    connection = connection,
                    organizationId = organizationId.value,
                    subjectId = change.subject.orderId.value,
                    projectedValue = (index + 1).toLong() * 1000,
                    sourceEvidenceVersion =
                        change.evidenceVersion.valueForPersistence(),
                    changeSequence =
                        change.changeSequence.valueForPersistence()
                )
            }

            assertEquals(1, affected)
        }

        val checkpointBeforeCrash = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                ChangeSequenceCheckpoint
            >
        >(
            realFeed.currentCheckpoint(
                organizationId,
                projectionName
            )
        )

        assertEquals(
            ChangeSequenceCheckpoint.NONE,
            checkpointBeforeCrash.value
        )

        val crashInjector =
            ExperimentalCrashInjector.at(
                ExperimentalCrashPoint.AFTER_PROJECTION_COMMIT
            )

        assertFailsWith<ExperimentalInjectedCrash> {
            crashInjector.hit(
                ExperimentalCrashPoint.AFTER_PROJECTION_COMMIT
            )
        }

        val replayFeed = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                List<MarketplaceEconomicEvidenceChange>
            >
        >(
            realFeed.changesSince(
                organizationId,
                ChangeSequenceCheckpoint.NONE,
                100
            )
        ).value

        assertEquals(initialFeed, replayFeed)

        val replayWrites =
            replayFeed.mapIndexed { index, change ->
                store.transaction { connection ->
                    store.applyProjectionMonotonically(
                        connection = connection,
                        organizationId = organizationId.value,
                        subjectId = change.subject.orderId.value,
                        projectedValue = (index + 1).toLong() * 1000,
                        sourceEvidenceVersion =
                            change.evidenceVersion.valueForPersistence(),
                        changeSequence =
                            change.changeSequence.valueForPersistence()
                    )
                }
            }

        assertEquals(
            listOf(0, 0, 1),
            replayWrites
        )

        val lastChange = replayFeed.last()

        val checkpointAdvance = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                CheckpointAdvanceResult
            >
        >(
            realFeed.advanceCheckpoint(
                organizationId,
                projectionName,
                ChangeSequenceCheckpoint.NONE,
                lastChange.changeSequence
            )
        )

        val advanced =
            assertIs<CheckpointAdvanceResult.Advanced>(
                checkpointAdvance.value
            )

        assertEquals(
            lastChange.changeSequence,
            advanced.checkpoint
        )

        val durableCheckpoint = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                ChangeSequenceCheckpoint
            >
        >(
            realFeed.currentCheckpoint(
                organizationId,
                projectionName
            )
        )

        assertEquals(
            lastChange.changeSequence,
            durableCheckpoint.value
        )

        replayFeed.forEachIndexed { index, change ->
            val projection =
                assertNotNull(
                    store.projection(
                        organizationId.value,
                        change.subject.orderId.value
                    )
                )

            assertEquals(
                (index + 1).toLong() * 1000,
                projection.projectedValue
            )

            assertEquals(
                change.changeSequence.valueForPersistence(),
                projection.lastAppliedChangeSequence
            )
        }
    }

    @Test
    fun `current state refetch converges projection after newer evidence and stale invalidation cannot degrade it`() {
        store.reset()

        val organizationId = OrganizationId(UUID.randomUUID())
        val subject = MarketplaceEconomicEvidenceSubject(
            organizationId = organizationId,
            orderId = MarketplaceOrderId(UUID.randomUUID()),
            marketplace = MarketplaceKey("mercado-livre"),
            externalOrderId = MarketplaceExternalOrderId("exp-0007-gate-9"),
            currency = MarketplaceCurrency("BRL")
        )

        store.createOrganization(organizationId.value)

        val firstAttempt =
            MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
                MarketplaceEconomicEvidenceCollectionAttempt(
                    MarketplaceEconomicEvidenceObservationId.parse(
                        UUID.randomUUID().toString()
                    ),
                    subject,
                    MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                    EconomicSourceSystemKey("exp-0007"),
                    MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE,
                    Instant.parse("2026-09-04T14:00:00Z")
                )
            )

        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            realWriter.apply(
                MarketplaceEconomicEvidenceVersion.ZERO,
                firstAttempt
            )
        )

        val firstChange = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                List<MarketplaceEconomicEvidenceChange>
            >
        >(
            realFeed.changesSince(
                organizationId,
                ChangeSequenceCheckpoint.NONE,
                100
            )
        ).value.single()

        val firstCurrent = assertIs<
            MarketplaceIndependentEconomicEvidenceReadResult.Found
        >(
            realWriter.find(subject)
        )

        assertEquals(
            1L,
            firstCurrent.versionedEvidence.version.valueForPersistence()
        )

        val firstProjectionWrite =
            store.transaction { connection ->
                store.applyProjectionMonotonically(
                    connection = connection,
                    organizationId = organizationId.value,
                    subjectId = subject.orderId.value,
                    projectedValue =
                        firstCurrent.versionedEvidence.version
                            .valueForPersistence(),
                    sourceEvidenceVersion =
                        firstCurrent.versionedEvidence.version
                            .valueForPersistence(),
                    changeSequence =
                        firstChange.changeSequence.valueForPersistence()
                )
            }

        assertEquals(1, firstProjectionWrite)

        val secondAttempt =
            MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
                MarketplaceEconomicEvidenceCollectionAttempt(
                    MarketplaceEconomicEvidenceObservationId.parse(
                        UUID.randomUUID().toString()
                    ),
                    subject,
                    MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                    EconomicSourceSystemKey("exp-0007"),
                    MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE,
                    Instant.parse("2026-09-04T14:01:00Z")
                )
            )

        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            realWriter.apply(
                MarketplaceEconomicEvidenceVersion(1),
                secondAttempt
            )
        )

        val allChanges = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                List<MarketplaceEconomicEvidenceChange>
            >
        >(
            realFeed.changesSince(
                organizationId,
                ChangeSequenceCheckpoint.NONE,
                100
            )
        ).value

        assertEquals(2, allChanges.size)

        val secondChange = allChanges.last()

        val currentAfterSecond = assertIs<
            MarketplaceIndependentEconomicEvidenceReadResult.Found
        >(
            realWriter.find(subject)
        )

        assertEquals(
            2L,
            currentAfterSecond.versionedEvidence.version
                .valueForPersistence()
        )

        val secondProjectionWrite =
            store.transaction { connection ->
                store.applyProjectionMonotonically(
                    connection = connection,
                    organizationId = organizationId.value,
                    subjectId = subject.orderId.value,
                    projectedValue =
                        currentAfterSecond.versionedEvidence.version
                            .valueForPersistence(),
                    sourceEvidenceVersion =
                        currentAfterSecond.versionedEvidence.version
                            .valueForPersistence(),
                    changeSequence =
                        secondChange.changeSequence.valueForPersistence()
                )
            }

        assertEquals(1, secondProjectionWrite)

        val staleRefetch = assertIs<
            MarketplaceIndependentEconomicEvidenceReadResult.Found
        >(
            realWriter.find(firstChange.subject)
        )

        assertEquals(
            2L,
            staleRefetch.versionedEvidence.version
                .valueForPersistence()
        )

        val staleProjectionWrite =
            store.transaction { connection ->
                store.applyProjectionMonotonically(
                    connection = connection,
                    organizationId = organizationId.value,
                    subjectId = subject.orderId.value,
                    projectedValue =
                        staleRefetch.versionedEvidence.version
                            .valueForPersistence(),
                    sourceEvidenceVersion =
                        staleRefetch.versionedEvidence.version
                            .valueForPersistence(),
                    changeSequence =
                        firstChange.changeSequence.valueForPersistence()
                )
            }

        assertEquals(
            0,
            staleProjectionWrite,
            "older invalidation cannot overwrite projection built from newer current state"
        )

        val finalProjection =
            assertNotNull(
                store.projection(
                    organizationId.value,
                    subject.orderId.value
                )
            )

        assertEquals(
            2L,
            finalProjection.projectedValue
        )

        assertEquals(
            2L,
            finalProjection.sourceEvidenceVersion
        )

        assertEquals(
            secondChange.changeSequence.valueForPersistence(),
            finalProjection.lastAppliedChangeSequence
        )
    }

    @Test
    fun `rebuild from NONE converges from partially materialized projection`() {
        store.reset()

        val organizationId = OrganizationId(UUID.randomUUID())
        val projectionName =
            ProjectionName("sales-intelligence-rebuild")

        store.createOrganization(organizationId.value)

        val subjects = (1..3).map { index ->
            MarketplaceEconomicEvidenceSubject(
                organizationId = organizationId,
                orderId = MarketplaceOrderId(UUID.randomUUID()),
                marketplace = MarketplaceKey("mercado-livre"),
                externalOrderId =
                    MarketplaceExternalOrderId(
                        "exp-0007-gate-10-$index"
                    ),
                currency = MarketplaceCurrency("BRL")
            )
        }

        subjects.forEachIndexed { index, subject ->
            val attempt =
                MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
                    MarketplaceEconomicEvidenceCollectionAttempt(
                        MarketplaceEconomicEvidenceObservationId.parse(
                            UUID.randomUUID().toString()
                        ),
                        subject,
                        MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                        EconomicSourceSystemKey("exp-0007"),
                        MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE,
                        Instant.parse("2026-09-04T15:00:00Z")
                            .plusSeconds(index.toLong())
                    )
                )

            assertIs<
                MarketplaceIndependentEconomicEvidencePersistResult.Applied
            >(
                realWriter.apply(
                    MarketplaceEconomicEvidenceVersion.ZERO,
                    attempt
                )
            )
        }

        val changes = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                List<MarketplaceEconomicEvidenceChange>
            >
        >(
            realFeed.changesSince(
                organizationId,
                ChangeSequenceCheckpoint.NONE,
                100
            )
        ).value

        assertEquals(3, changes.size)

        listOf(0, 2).forEach { index ->
            val change = changes[index]

            val current = assertIs<
                MarketplaceIndependentEconomicEvidenceReadResult.Found
            >(
                realWriter.find(change.subject)
            )

            val affected =
                store.transaction { connection ->
                    store.applyProjectionMonotonically(
                        connection = connection,
                        organizationId = organizationId.value,
                        subjectId = change.subject.orderId.value,
                        projectedValue = (index + 1).toLong() * 1000,
                        sourceEvidenceVersion =
                            current.versionedEvidence.version
                                .valueForPersistence(),
                        changeSequence =
                            change.changeSequence.valueForPersistence()
                    )
                }

            assertEquals(1, affected)
        }

        assertNotNull(
            store.projection(
                organizationId.value,
                changes[0].subject.orderId.value
            )
        )

        assertEquals(
            null,
            store.projection(
                organizationId.value,
                changes[1].subject.orderId.value
            )
        )

        assertNotNull(
            store.projection(
                organizationId.value,
                changes[2].subject.orderId.value
            )
        )

        val checkpointBeforeRebuild = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                ChangeSequenceCheckpoint
            >
        >(
            realFeed.currentCheckpoint(
                organizationId,
                projectionName
            )
        )

        assertEquals(
            ChangeSequenceCheckpoint.NONE,
            checkpointBeforeRebuild.value
        )

        val rebuildFeed = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                List<MarketplaceEconomicEvidenceChange>
            >
        >(
            realFeed.changesSince(
                organizationId,
                ChangeSequenceCheckpoint.NONE,
                100
            )
        ).value

        assertEquals(changes, rebuildFeed)

        val rebuildWrites =
            rebuildFeed.mapIndexed { index, change ->
                val current = assertIs<
                    MarketplaceIndependentEconomicEvidenceReadResult.Found
                >(
                    realWriter.find(change.subject)
                )

                store.transaction { connection ->
                    store.applyProjectionMonotonically(
                        connection = connection,
                        organizationId = organizationId.value,
                        subjectId = change.subject.orderId.value,
                        projectedValue = (index + 1).toLong() * 1000,
                        sourceEvidenceVersion =
                            current.versionedEvidence.version
                                .valueForPersistence(),
                        changeSequence =
                            change.changeSequence.valueForPersistence()
                    )
                }
            }

        assertEquals(
            listOf(0, 1, 0),
            rebuildWrites
        )

        val lastChange = rebuildFeed.last()

        val checkpointAdvance = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                CheckpointAdvanceResult
            >
        >(
            realFeed.advanceCheckpoint(
                organizationId,
                projectionName,
                ChangeSequenceCheckpoint.NONE,
                lastChange.changeSequence
            )
        )

        val advanced =
            assertIs<CheckpointAdvanceResult.Advanced>(
                checkpointAdvance.value
            )

        assertEquals(
            lastChange.changeSequence,
            advanced.checkpoint
        )

        rebuildFeed.forEachIndexed { index, change ->
            val projection =
                assertNotNull(
                    store.projection(
                        organizationId.value,
                        change.subject.orderId.value
                    )
                )

            assertEquals(
                (index + 1).toLong() * 1000,
                projection.projectedValue
            )

            assertEquals(
                change.changeSequence.valueForPersistence(),
                projection.lastAppliedChangeSequence
            )
        }

        val durableCheckpoint = assertIs<
            MarketplaceEconomicEvidenceChangeFeedResult.Success<
                ChangeSequenceCheckpoint
            >
        >(
            realFeed.currentCheckpoint(
                organizationId,
                projectionName
            )
        )

        assertEquals(
            lastChange.changeSequence,
            durableCheckpoint.value
        )
    }

    @Test
    fun `strategy C adds explicit receipt write while preserving idempotent projection replay`() {
        store.reset()

        val organizationId = OrganizationId(UUID.randomUUID())
        val projectionName = "sales-intelligence-receipt"
        val subjectId = UUID.randomUUID()
        val changeSequence = 10L

        store.createOrganization(organizationId.value)

        val first = store.transaction { connection ->
            val receiptWrite =
                store.recordProcessedChange(
                    connection = connection,
                    organizationId = organizationId.value,
                    projectionName = projectionName,
                    changeSequence = changeSequence
                )

            val projectionWrite =
                store.applyProjectionMonotonically(
                    connection = connection,
                    organizationId = organizationId.value,
                    subjectId = subjectId,
                    projectedValue = 4200,
                    sourceEvidenceVersion = 1,
                    changeSequence = changeSequence
                )

            receiptWrite to projectionWrite
        }

        assertEquals(
            1 to 1,
            first
        )

        val replay = store.transaction { connection ->
            val receiptWrite =
                store.recordProcessedChange(
                    connection = connection,
                    organizationId = organizationId.value,
                    projectionName = projectionName,
                    changeSequence = changeSequence
                )

            val projectionWrite =
                store.applyProjectionMonotonically(
                    connection = connection,
                    organizationId = organizationId.value,
                    subjectId = subjectId,
                    projectedValue = 4200,
                    sourceEvidenceVersion = 1,
                    changeSequence = changeSequence
                )

            receiptWrite to projectionWrite
        }

        assertEquals(
            0 to 0,
            replay
        )

        val projection =
            assertNotNull(
                store.projection(
                    organizationId.value,
                    subjectId
                )
            )

        assertEquals(
            changeSequence,
            projection.lastAppliedChangeSequence
        )

        assertEquals(
            4200,
            projection.projectedValue
        )
    }

    @Test
    fun `strategy B and C write cost profile records controlled batch evidence`() {
        val batchSizes = listOf(1, 10, 100, 1000)
        val warmups = 2
        val repetitions = 5

        batchSizes.forEach { batchSize ->
            repeat(warmups) { warmup ->
                runStrategyWriteProfile(
                    strategy = ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT,
                    batchSize = batchSize,
                    runLabel = "warmup-$warmup"
                )

                runStrategyWriteProfile(
                    strategy = ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT,
                    batchSize = batchSize,
                    runLabel = "warmup-$warmup"
                )
            }

            val bResults =
                (1..repetitions).map { repetition ->
                    runStrategyWriteProfile(
                        strategy = ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT,
                        batchSize = batchSize,
                        runLabel = "measured-$repetition"
                    )
                }

            val cResults =
                (1..repetitions).map { repetition ->
                    runStrategyWriteProfile(
                        strategy = ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT,
                        batchSize = batchSize,
                        runLabel = "measured-$repetition"
                    )
                }

            val bSql = bResults.map { it.sqlDurationNanos }.sorted()
            val cSql = cResults.map { it.sqlDurationNanos }.sorted()
            val bTotal = bResults.map { it.totalDurationNanos }.sorted()
            val cTotal = cResults.map { it.totalDurationNanos }.sorted()

            val bProjectionWrites =
                bResults.sumOf { it.projectionWrites }

            val cProjectionWrites =
                cResults.sumOf { it.projectionWrites }

            val cReceiptWrites =
                cResults.sumOf { it.receiptWrites }

            assertEquals(
                batchSize * repetitions,
                bProjectionWrites
            )

            assertEquals(
                batchSize * repetitions,
                cProjectionWrites
            )

            assertEquals(
                batchSize * repetitions,
                cReceiptWrites
            )

            println(
                "EXP-0007 WRITE-COST " +
                    "batch=$batchSize " +
                    "B sqlMedianMs=${nanosToMillis(bSql[bSql.size / 2])} " +
                    "totalMedianMs=${nanosToMillis(bTotal[bTotal.size / 2])} " +
                    "projectionWrites=$bProjectionWrites " +
                    "receiptWrites=0"
            )

            println(
                "EXP-0007 WRITE-COST " +
                    "batch=$batchSize " +
                    "C sqlMedianMs=${nanosToMillis(cSql[cSql.size / 2])} " +
                    "totalMedianMs=${nanosToMillis(cTotal[cTotal.size / 2])} " +
                    "projectionWrites=$cProjectionWrites " +
                    "receiptWrites=$cReceiptWrites"
            )
        }
    }

    private fun runStrategyWriteProfile(
        strategy: ExperimentalProjectionStrategy,
        batchSize: Int,
        runLabel: String
    ): ExperimentalWriteProfileResult {
        store.reset()

        val organizationId = OrganizationId(UUID.randomUUID())
        store.createOrganization(organizationId.value)

        val strategyToken =
            when (strategy) {
                ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT ->
                    "idempotent"
                ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT ->
                    "receipt"
                ExperimentalProjectionStrategy.ATOMIC_SAME_TRANSACTION ->
                    error("Strategy A is outside the Gate 12 write-cost benchmark")
            }

        val projectionName =
            "sales-intelligence-$strategyToken-$runLabel"

        val subjects =
            List(batchSize) {
                UUID.randomUUID()
            }

        var sqlDurationNanos = 0L
        var projectionWrites = 0
        var receiptWrites = 0

        val totalStarted = System.nanoTime()

        store.transaction { connection ->
            val sqlStarted = System.nanoTime()

            subjects.forEachIndexed { index, subjectId ->
                val sequence = index.toLong() + 1

                if (strategy == ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT) {
                    receiptWrites +=
                        store.recordProcessedChange(
                            connection = connection,
                            organizationId = organizationId.value,
                            projectionName = projectionName,
                            changeSequence = sequence
                        )
                }

                projectionWrites +=
                    store.applyProjectionMonotonically(
                        connection = connection,
                        organizationId = organizationId.value,
                        subjectId = subjectId,
                        projectedValue = sequence,
                        sourceEvidenceVersion = 1,
                        changeSequence = sequence
                    )
            }

            sqlDurationNanos =
                System.nanoTime() - sqlStarted
        }

        val totalDurationNanos =
            System.nanoTime() - totalStarted

        return ExperimentalWriteProfileResult(
            sqlDurationNanos = sqlDurationNanos,
            totalDurationNanos = totalDurationNanos,
            projectionWrites = projectionWrites,
            receiptWrites = receiptWrites
        )
    }

    private fun nanosToMillis(value: Long): Double =
        value / 1_000_000.0



    @Test
    fun `strategy B and C duplicate replay cost profile records controlled batch evidence`() {
        val batchSizes = listOf(1, 10, 100, 1000)
        val warmups = 2
        val repetitions = 5

        batchSizes.forEach { batchSize ->
            repeat(warmups) { warmup ->
                runStrategyReplayProfile(
                    strategy = ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT,
                    batchSize = batchSize,
                    runLabel = "warmup-$warmup"
                )

                runStrategyReplayProfile(
                    strategy = ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT,
                    batchSize = batchSize,
                    runLabel = "warmup-$warmup"
                )
            }

            val bResults =
                (1..repetitions).map { repetition ->
                    runStrategyReplayProfile(
                        strategy = ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT,
                        batchSize = batchSize,
                        runLabel = "measured-$repetition"
                    )
                }

            val cResults =
                (1..repetitions).map { repetition ->
                    runStrategyReplayProfile(
                        strategy = ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT,
                        batchSize = batchSize,
                        runLabel = "measured-$repetition"
                    )
                }

            val bSql = bResults.map { it.sqlDurationNanos }.sorted()
            val cSql = cResults.map { it.sqlDurationNanos }.sorted()
            val bTotal = bResults.map { it.totalDurationNanos }.sorted()
            val cTotal = cResults.map { it.totalDurationNanos }.sorted()

            assertEquals(0, bResults.sumOf { it.projectionRowMutations })
            assertEquals(0, cResults.sumOf { it.projectionRowMutations })
            assertEquals(0, cResults.sumOf { it.receiptRowMutations })

            assertEquals(
                batchSize * repetitions,
                bResults.sumOf { it.projectionStatements }
            )

            assertEquals(
                0,
                bResults.sumOf { it.receiptStatements }
            )

            assertEquals(
                0,
                cResults.sumOf { it.projectionStatements }
            )

            assertEquals(
                batchSize * repetitions,
                cResults.sumOf { it.receiptStatements }
            )

            println(
                "EXP-0007 REPLAY-COST " +
                    "batch=$batchSize " +
                    "B sqlMedianMs=${nanosToMillis(bSql[bSql.size / 2])} " +
                    "totalMedianMs=${nanosToMillis(bTotal[bTotal.size / 2])} " +
                    "projectionStatements=${bResults.sumOf { it.projectionStatements }} " +
                    "receiptStatements=0 rowMutations=0"
            )

            println(
                "EXP-0007 REPLAY-COST " +
                    "batch=$batchSize " +
                    "C sqlMedianMs=${nanosToMillis(cSql[cSql.size / 2])} " +
                    "totalMedianMs=${nanosToMillis(cTotal[cTotal.size / 2])} " +
                    "projectionStatements=0 " +
                    "receiptStatements=${cResults.sumOf { it.receiptStatements }} " +
                    "rowMutations=0"
            )
        }
    }

    private fun runStrategyReplayProfile(
        strategy: ExperimentalProjectionStrategy,
        batchSize: Int,
        runLabel: String
    ): ExperimentalReplayProfileResult {
        store.reset()

        val organizationId = OrganizationId(UUID.randomUUID())
        store.createOrganization(organizationId.value)

        val strategyToken =
            when (strategy) {
                ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT ->
                    "idempotent"
                ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT ->
                    "receipt"
                ExperimentalProjectionStrategy.ATOMIC_SAME_TRANSACTION ->
                    error("Strategy A is outside the Gate 13 replay-cost benchmark")
            }

        val projectionName =
            "sales-intelligence-$strategyToken-$runLabel"

        val subjects = List(batchSize) { UUID.randomUUID() }

        store.transaction { connection ->
            subjects.forEachIndexed { index, subjectId ->
                val sequence = index.toLong() + 1

                if (strategy == ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT) {
                    assertEquals(
                        1,
                        store.recordProcessedChange(
                            connection = connection,
                            organizationId = organizationId.value,
                            projectionName = projectionName,
                            changeSequence = sequence
                        )
                    )
                }

                assertEquals(
                    1,
                    store.applyProjectionMonotonically(
                        connection = connection,
                        organizationId = organizationId.value,
                        subjectId = subjectId,
                        projectedValue = sequence,
                        sourceEvidenceVersion = 1,
                        changeSequence = sequence
                    )
                )
            }
        }

        var sqlDurationNanos = 0L
        var projectionStatements = 0
        var receiptStatements = 0
        var projectionRowMutations = 0
        var receiptRowMutations = 0

        val totalStarted = System.nanoTime()

        store.transaction { connection ->
            val sqlStarted = System.nanoTime()

            subjects.forEachIndexed { index, subjectId ->
                val sequence = index.toLong() + 1

                when (strategy) {
                    ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT -> {
                        projectionStatements += 1
                        projectionRowMutations +=
                            store.applyProjectionMonotonically(
                                connection = connection,
                                organizationId = organizationId.value,
                                subjectId = subjectId,
                                projectedValue = sequence,
                                sourceEvidenceVersion = 1,
                                changeSequence = sequence
                            )
                    }

                    ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT -> {
                        receiptStatements += 1

                        val receiptWrite =
                            store.recordProcessedChange(
                                connection = connection,
                                organizationId = organizationId.value,
                                projectionName = projectionName,
                                changeSequence = sequence
                            )

                        receiptRowMutations += receiptWrite

                        if (receiptWrite == 1) {
                            projectionStatements += 1
                            projectionRowMutations +=
                                store.applyProjectionMonotonically(
                                    connection = connection,
                                    organizationId = organizationId.value,
                                    subjectId = subjectId,
                                    projectedValue = sequence,
                                    sourceEvidenceVersion = 1,
                                    changeSequence = sequence
                                )
                        }
                    }

                    ExperimentalProjectionStrategy.ATOMIC_SAME_TRANSACTION ->
                        error("Strategy A is outside the Gate 13 replay-cost benchmark")
                }
            }

            sqlDurationNanos = System.nanoTime() - sqlStarted
        }

        val totalDurationNanos =
            System.nanoTime() - totalStarted

        return ExperimentalReplayProfileResult(
            sqlDurationNanos = sqlDurationNanos,
            totalDurationNanos = totalDurationNanos,
            projectionStatements = projectionStatements,
            receiptStatements = receiptStatements,
            projectionRowMutations = projectionRowMutations,
            receiptRowMutations = receiptRowMutations
        )
    }

    @Test
    fun `strategy B implicit sequence dedup competes with C receipt dedup on duplicate replay`() {
        val batchSizes = listOf(1, 10, 100, 1000)
        val warmups = 2
        val repetitions = 5

        batchSizes.forEach { batchSize ->
            repeat(warmups) { warmup ->
                runImplicitDedupReplayProfile(
                    strategy = ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT,
                    batchSize = batchSize,
                    runLabel = "warmup-$warmup"
                )

                runImplicitDedupReplayProfile(
                    strategy = ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT,
                    batchSize = batchSize,
                    runLabel = "warmup-$warmup"
                )
            }

            val bResults =
                (1..repetitions).map { repetition ->
                    runImplicitDedupReplayProfile(
                        strategy = ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT,
                        batchSize = batchSize,
                        runLabel = "measured-$repetition"
                    )
                }

            val cResults =
                (1..repetitions).map { repetition ->
                    runImplicitDedupReplayProfile(
                        strategy = ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT,
                        batchSize = batchSize,
                        runLabel = "measured-$repetition"
                    )
                }

            val bSql = bResults.map { it.sqlDurationNanos }.sorted()
            val cSql = cResults.map { it.sqlDurationNanos }.sorted()
            val bTotal = bResults.map { it.totalDurationNanos }.sorted()
            val cTotal = cResults.map { it.totalDurationNanos }.sorted()

            assertEquals(
                batchSize * repetitions,
                bResults.sumOf { it.lookupStatements }
            )

            assertEquals(
                batchSize * repetitions,
                cResults.sumOf { it.lookupStatements }
            )

            assertEquals(
                batchSize * repetitions,
                bResults.sumOf { it.skippedRefetches }
            )

            assertEquals(
                batchSize * repetitions,
                cResults.sumOf { it.skippedRefetches }
            )

            println(
                "EXP-0007 IMPLICIT-DEDUP " +
                    "batch=$batchSize " +
                    "B sqlMedianMs=${nanosToMillis(bSql[bSql.size / 2])} " +
                    "totalMedianMs=${nanosToMillis(bTotal[bTotal.size / 2])} " +
                    "lookupStatements=${bResults.sumOf { it.lookupStatements }} " +
                    "skippedRefetches=${bResults.sumOf { it.skippedRefetches }}"
            )

            println(
                "EXP-0007 IMPLICIT-DEDUP " +
                    "batch=$batchSize " +
                    "C sqlMedianMs=${nanosToMillis(cSql[cSql.size / 2])} " +
                    "totalMedianMs=${nanosToMillis(cTotal[cTotal.size / 2])} " +
                    "lookupStatements=${cResults.sumOf { it.lookupStatements }} " +
                    "skippedRefetches=${cResults.sumOf { it.skippedRefetches }}"
            )
        }
    }

    private fun runImplicitDedupReplayProfile(
        strategy: ExperimentalProjectionStrategy,
        batchSize: Int,
        runLabel: String
    ): ExperimentalImplicitDedupProfileResult {
        store.reset()

        val organizationId = OrganizationId(UUID.randomUUID())
        store.createOrganization(organizationId.value)

        val projectionName =
            when (strategy) {
                ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT ->
                    "sales-intelligence-idempotent-$runLabel"
                ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT ->
                    "sales-intelligence-receipt-$runLabel"
                ExperimentalProjectionStrategy.ATOMIC_SAME_TRANSACTION ->
                    error("Strategy A is outside the Gate 14 benchmark")
            }

        val subjects = List(batchSize) { UUID.randomUUID() }

        store.transaction { connection ->
            subjects.forEachIndexed { index, subjectId ->
                val sequence = index.toLong() + 1

                if (strategy == ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT) {
                    assertEquals(
                        1,
                        store.recordProcessedChange(
                            connection = connection,
                            organizationId = organizationId.value,
                            projectionName = projectionName,
                            changeSequence = sequence
                        )
                    )
                }

                assertEquals(
                    1,
                    store.applyProjectionMonotonically(
                        connection = connection,
                        organizationId = organizationId.value,
                        subjectId = subjectId,
                        projectedValue = sequence,
                        sourceEvidenceVersion = 1,
                        changeSequence = sequence
                    )
                )
            }
        }

        var lookupStatements = 0
        var skippedRefetches = 0

        val totalStarted = System.nanoTime()
        val sqlStarted = System.nanoTime()

        subjects.forEachIndexed { index, subjectId ->
            val sequence = index.toLong() + 1

            when (strategy) {
                ExperimentalProjectionStrategy.IDEMPOTENT_THEN_CHECKPOINT -> {
                    lookupStatements += 1

                    val durableSequence =
                        store.lastAppliedChangeSequence(
                            organizationId = organizationId.value,
                            subjectId = subjectId
                        )

                    if (durableSequence != null && durableSequence >= sequence) {
                        skippedRefetches += 1
                    }
                }

                ExperimentalProjectionStrategy.RECEIPT_THEN_CHECKPOINT -> {
                    lookupStatements += 1

                    val receiptWrite =
                        store.transaction { connection ->
                            store.recordProcessedChange(
                                connection = connection,
                                organizationId = organizationId.value,
                                projectionName = projectionName,
                                changeSequence = sequence
                            )
                        }

                    if (receiptWrite == 0) {
                        skippedRefetches += 1
                    }
                }

                ExperimentalProjectionStrategy.ATOMIC_SAME_TRANSACTION ->
                    error("Strategy A is outside the Gate 14 benchmark")
            }
        }

        val sqlDurationNanos =
            System.nanoTime() - sqlStarted

        val totalDurationNanos =
            System.nanoTime() - totalStarted

        return ExperimentalImplicitDedupProfileResult(
            sqlDurationNanos = sqlDurationNanos,
            totalDurationNanos = totalDurationNanos,
            lookupStatements = lookupStatements,
            skippedRefetches = skippedRefetches
        )
    }

}

internal data class ExperimentalImplicitDedupProfileResult(
    val sqlDurationNanos: Long,
    val totalDurationNanos: Long,
    val lookupStatements: Int,
    val skippedRefetches: Int
)

internal data class ExperimentalReplayProfileResult(
    val sqlDurationNanos: Long,
    val totalDurationNanos: Long,
    val projectionStatements: Int,
    val receiptStatements: Int,
    val projectionRowMutations: Int,
    val receiptRowMutations: Int
)

internal data class ExperimentalWriteProfileResult(
    val sqlDurationNanos: Long,
    val totalDurationNanos: Long,
    val projectionWrites: Int,
    val receiptWrites: Int
)

internal data class ExperimentalContentionResult(
    val durationMs: Long,
    val appliedWrites: Int,
    val noOpWrites: Int,
    val errors: Int
)

internal enum class ExperimentalProjectionStrategy {
    ATOMIC_SAME_TRANSACTION,
    IDEMPOTENT_THEN_CHECKPOINT,
    RECEIPT_THEN_CHECKPOINT
}
