package io.flooow.research.exp0006

import io.flooow.marketplace.operations.economics.EconomicComponent
import io.flooow.marketplace.operations.economics.EconomicComponentCoverage
import io.flooow.marketplace.operations.economics.EconomicComponentId
import io.flooow.marketplace.operations.economics.EconomicComponentType
import io.flooow.marketplace.operations.economics.EconomicDirection
import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import io.flooow.marketplace.operations.economics.EconomicExternalReference
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicComponentObservation
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceFamily
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceObservationId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidencePersistResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceUpdate
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicFact
import io.flooow.marketplace.persistence.postgres.PostgresConfiguration
import io.flooow.marketplace.persistence.postgres.PostgresMarketplaceIndependentEconomicEvidenceRepository
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExperimentalChangeFeedTest {
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var feed: ExperimentalPostgresChangeFeed
    private lateinit var writer: PostgresMarketplaceIndependentEconomicEvidenceRepository
    private val updateCounter = AtomicInteger()
    private val baseTime = Instant.parse("2026-09-02T12:00:00.123456Z")

    @BeforeAll
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure()
            .dataSource(configuration.url, configuration.user, configuration.password)
            .load()
            .migrate()
        ExperimentalPostgresChangeFeed.installExperimentalSchema(configuration)
        feed = ExperimentalPostgresChangeFeed(configuration)
        writer = PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration)
    }

    @AfterAll
    fun stopPostgres() = postgres.stop()

    @Test
    fun `value contracts reject invalid inputs and redact values`() {
        assertFailsWith<IllegalArgumentException> { ExperimentalChangeSequenceCheckpoint(-1) }
        assertFailsWith<IllegalArgumentException> { ExperimentalProjectionName("") }
        assertFailsWith<IllegalArgumentException> { ExperimentalProjectionName("Sales-Intelligence") }
        assertFailsWith<IllegalArgumentException> { ExperimentalProjectionName("a".repeat(101)) }
        assertEquals("[INTERNAL]", ExperimentalChangeSequenceCheckpoint.NONE.toString())
        assertEquals("[REDACTED]", ExperimentalProjectionName("sales-intelligence").toString())
    }

    @Test
    fun `bounded invalidation feed supports checkpoints stable pagination and complete subjects`() {
        val organization = createOrganization()
        val subject = subject(organization)
        repeat(5) { version -> writeFact(subject, version.toLong()) }

        val all = feed.changesSince(organization, ExperimentalChangeSequenceCheckpoint.NONE, 100)
        assertEquals(5, all.size)
        assertTrue(all.zipWithNext().all { (left, right) -> left.changeSequence < right.changeSequence })
        assertTrue(all.all { it.subject == subject })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), all.map { it.evidenceVersion.valueForPersistence() })

        val firstPage = feed.changesSince(organization, ExperimentalChangeSequenceCheckpoint.NONE, 2)
        val secondPage = feed.changesSince(organization, firstPage.last().changeSequence, 2)
        val thirdPage = feed.changesSince(organization, secondPage.last().changeSequence, 2)
        assertEquals(all, firstPage + secondPage + thirdPage)
        assertEquals(all.drop(2), feed.changesSince(organization, firstPage.last().changeSequence, 100))
        assertTrue(feed.changesSince(organization, all.last().changeSequence, 100).isEmpty())

        val repeated = feed.changesSince(organization, ExperimentalChangeSequenceCheckpoint.NONE, 100)
        assertEquals(all, repeated)
        val projection = ExperimentalProjectionName("sales-intelligence")
        assertEquals(ExperimentalChangeSequenceCheckpoint.NONE, feed.currentCheckpoint(organization, projection))
        assertIs<ExperimentalCheckpointAdvanceResult.Advanced>(
            feed.advanceCheckpoint(
                organization,
                projection,
                ExperimentalChangeSequenceCheckpoint.NONE,
                all.last().changeSequence
            )
        )
        assertTrue(feed.changesSince(organization, feed.currentCheckpoint(organization, projection), 100).isEmpty())
    }

    @Test
    fun `pending discovery includes absent checkpoints and isolates organizations and projections`() {
        val firstOrganization = createOrganization()
        val secondOrganization = createOrganization()
        val firstChanges = writeChanges(firstOrganization, 2)
        writeChanges(secondOrganization, 1)
        val sales = ExperimentalProjectionName("sales-intelligence")
        val audit = ExperimentalProjectionName("audit-projection")

        val initial = feed.organizationsWithPendingChanges(sales, 1_000)
        assertTrue(firstOrganization in initial)
        assertTrue(secondOrganization in initial)
        assertEquals(initial, feed.organizationsWithPendingChanges(sales, 1_000))

        assertIs<ExperimentalCheckpointAdvanceResult.Advanced>(
            feed.advanceCheckpoint(
                firstOrganization,
                sales,
                ExperimentalChangeSequenceCheckpoint.NONE,
                firstChanges.last().changeSequence
            )
        )
        assertTrue(firstOrganization !in feed.organizationsWithPendingChanges(sales, 1_000))
        assertTrue(firstOrganization in feed.organizationsWithPendingChanges(audit, 1_000))
        assertEquals(ExperimentalChangeSequenceCheckpoint.NONE, feed.currentCheckpoint(firstOrganization, audit))
    }

    @Test
    fun `checkpoint CAS rejects regression stale and invalid first creation`() {
        val organization = createOrganization()
        val projection = ExperimentalProjectionName("cas-projection")
        val one = ExperimentalChangeSequenceCheckpoint(1)
        val two = ExperimentalChangeSequenceCheckpoint(2)
        val three = ExperimentalChangeSequenceCheckpoint(3)

        assertIs<ExperimentalCheckpointAdvanceResult.Stale>(
            feed.advanceCheckpoint(organization, projection, one, two)
        )
        assertEquals(0, checkpointRows(organization, projection))
        assertIs<ExperimentalCheckpointAdvanceResult.Advanced>(
            feed.advanceCheckpoint(organization, projection, ExperimentalChangeSequenceCheckpoint.NONE, one)
        )
        assertEquals(one, feed.currentCheckpoint(organization, projection))
        assertIs<ExperimentalCheckpointAdvanceResult.Regression>(
            feed.advanceCheckpoint(organization, projection, one, one)
        )
        assertIs<ExperimentalCheckpointAdvanceResult.Regression>(
            feed.advanceCheckpoint(organization, projection, one, ExperimentalChangeSequenceCheckpoint.NONE)
        )
        assertEquals(one, feed.currentCheckpoint(organization, projection))
        assertIs<ExperimentalCheckpointAdvanceResult.Stale>(
            feed.advanceCheckpoint(organization, projection, ExperimentalChangeSequenceCheckpoint.NONE, three)
        )
        assertEquals(one, feed.currentCheckpoint(organization, projection))
    }

    @Test
    fun `concurrent first and existing checkpoint writers produce one Advanced and one Stale`() {
        val organization = createOrganization()
        val projection = ExperimentalProjectionName("concurrent-projection")
        val none = ExperimentalChangeSequenceCheckpoint.NONE
        val one = ExperimentalChangeSequenceCheckpoint(1)
        val two = ExperimentalChangeSequenceCheckpoint(2)

        val firstResults = concurrent(
            { feed.advanceCheckpoint(organization, projection, none, one) },
            { feed.advanceCheckpoint(organization, projection, none, one) }
        )
        assertEquals(1, firstResults.count { it is ExperimentalCheckpointAdvanceResult.Advanced })
        assertEquals(1, firstResults.count { it is ExperimentalCheckpointAdvanceResult.Stale })
        assertEquals(1, checkpointRows(organization, projection))

        val existingResults = concurrent(
            { feed.advanceCheckpoint(organization, projection, one, two) },
            { feed.advanceCheckpoint(organization, projection, one, two) }
        )
        assertEquals(1, existingResults.count { it is ExperimentalCheckpointAdvanceResult.Advanced })
        assertEquals(1, existingResults.count { it is ExperimentalCheckpointAdvanceResult.Stale })
        assertEquals(two, feed.currentCheckpoint(organization, projection))
    }

    @Test
    fun `feed remains organization ordered while real P0_2 writer commits`() {
        val organization = createOrganization()
        val subject = subject(organization)
        writeFact(subject, 0)
        val start = CountDownLatch(1)
        val writerFinished = AtomicBoolean(false)
        val observedSnapshots = ConcurrentLinkedQueue<List<ExperimentalMarketplaceEconomicEvidenceChange>>()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val writeFuture = executor.submit {
                check(start.await(10, TimeUnit.SECONDS))
                repeat(20) { offset -> writeFact(subject, offset.toLong() + 1) }
                writerFinished.set(true)
            }
            val readFuture = executor.submit {
                check(start.await(10, TimeUnit.SECONDS))
                while (!writerFinished.get()) {
                    observedSnapshots.add(
                        feed.changesSince(organization, ExperimentalChangeSequenceCheckpoint.NONE, 1_000)
                    )
                }
            }
            start.countDown()
            writeFuture.get(60, TimeUnit.SECONDS)
            readFuture.get(60, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        val finalChanges = feed.changesSince(organization, ExperimentalChangeSequenceCheckpoint.NONE, 1_000)
        assertEquals(21, finalChanges.size)
        assertTrue(finalChanges.zipWithNext().all { (left, right) -> left.changeSequence < right.changeSequence })
        assertTrue(
            observedSnapshots.all { snapshot ->
                snapshot.zipWithNext().all { (left, right) -> left.changeSequence < right.changeSequence }
            }
        )
    }

    @Test
    fun `pending discovery captures raw explain analyze buffers under volume`() {
        val organizations = createVolumeFixture(30, 200)
        val projection = ExperimentalProjectionName("performance-projection")
        organizations.take(10).forEach { organization ->
            val maximum = maximumSequence(organization)
            assertIs<ExperimentalCheckpointAdvanceResult.Advanced>(
                feed.advanceCheckpoint(
                    organization,
                    projection,
                    ExperimentalChangeSequenceCheckpoint.NONE,
                    ExperimentalChangeSequenceCheckpoint(maximum)
                )
            )
        }

        val pending = feed.organizationsWithPendingChanges(projection, 1_000)
        assertTrue(organizations.drop(10).all { it in pending })
        assertTrue(organizations.take(10).none { it in pending })

        val plan = feed.explainOrganizationsWithPendingChanges(projection, 1_000)
        println("EXP-0006 RAW EXPLAIN BEGIN")
        plan.forEach(::println)
        println("EXP-0006 RAW EXPLAIN END")
        assertTrue(plan.any { "actual" in it })
        assertTrue(plan.any { "Buffers:" in it })
        assertTrue(plan.any { "Planning Time:" in it })
        assertTrue(plan.any { "Execution Time:" in it })
    }

    private fun writeChanges(
        organization: OrganizationId,
        count: Int
    ): List<ExperimentalMarketplaceEconomicEvidenceChange> {
        val subject = subject(organization)
        repeat(count) { version -> writeFact(subject, version.toLong()) }
        return feed.changesSince(organization, ExperimentalChangeSequenceCheckpoint.NONE, 1_000)
    }

    private fun writeFact(subject: MarketplaceEconomicEvidenceSubject, currentVersion: Long) {
        val ordinal = updateCounter.incrementAndGet()
        val observedAt = baseTime.plusSeconds(ordinal.toLong())
        val update = MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(
            MarketplaceIndependentEconomicFact.Component(
                MarketplaceEconomicComponentObservation(
                    MarketplaceEconomicEvidenceObservationId.parse(UUID.randomUUID().toString()),
                    subject,
                    MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                    EconomicComponent(
                        subject.organizationId,
                        EconomicComponentId(UUID.randomUUID()),
                        subject.orderId,
                        EconomicComponentType.REVENUE,
                        EconomicDirection.ADDITION,
                        MarketplaceMoney.parse(subject.currency, BigDecimal(ordinal).toPlainString()),
                        EconomicSource(
                            EconomicSourceKind.MARKETPLACE,
                            EconomicSourceSystemKey("meli-br"),
                            EconomicExternalReferenceState.Present(
                                EconomicExternalReference("exp-0006-$ordinal")
                            )
                        ),
                        observedAt,
                        EconomicEvidenceQuality.CONFIRMED
                    ),
                    EconomicComponentCoverage.COMPLETE,
                    observedAt.plusNanos(1_000)
                )
            )
        )
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            writer.apply(MarketplaceEconomicEvidenceVersion(currentVersion), update)
        )
    }

    private fun subject(organizationId: OrganizationId): MarketplaceEconomicEvidenceSubject =
        MarketplaceEconomicEvidenceSubject(
            organizationId,
            MarketplaceOrderId(UUID.randomUUID()),
            MarketplaceKey("mercado-livre"),
            MarketplaceExternalOrderId("exp-${UUID.randomUUID()}"),
            MarketplaceCurrency("BRL")
        )

    private fun createOrganization(): OrganizationId = OrganizationId(UUID.randomUUID()).also { organization ->
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO integration_organization " +
                    "(organization_id,status,created_at,updated_at) VALUES (?,'ACTIVE',?,?)"
            ).use { statement ->
                statement.setObject(1, organization.value)
                statement.setTimestamp(2, Timestamp.from(baseTime))
                statement.setTimestamp(3, Timestamp.from(baseTime))
                assertEquals(1, statement.executeUpdate())
            }
        }
    }

    private fun checkpointRows(
        organizationId: OrganizationId,
        projectionName: ExperimentalProjectionName
    ): Int = connection().use { connection ->
        connection.prepareStatement(
            "SELECT count(*) FROM projection_checkpoint WHERE organization_id=? AND projection_name=?"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setString(2, projectionName.valueForPersistence())
            statement.executeQuery().use { result -> result.next(); result.getInt(1) }
        }
    }

    private fun maximumSequence(organizationId: OrganizationId): Long = connection().use { connection ->
        connection.prepareStatement(
            "SELECT max(change_sequence) FROM marketplace_economic_evidence_update WHERE organization_id=?"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }
    }

    private fun createVolumeFixture(organizationCount: Int, updatesPerOrganization: Int): List<OrganizationId> =
        connection().use { connection ->
            connection.autoCommit = false
            try {
                val organizations = List(organizationCount) { OrganizationId(UUID.randomUUID()) }
                val now = Timestamp.from(baseTime)
                connection.prepareStatement(
                    "INSERT INTO integration_organization " +
                        "(organization_id,status,created_at,updated_at) VALUES (?,'ACTIVE',?,?)"
                ).use { statement ->
                    organizations.forEach { organization ->
                        statement.setObject(1, organization.value)
                        statement.setTimestamp(2, now)
                        statement.setTimestamp(3, now)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.prepareStatement(
                    "INSERT INTO marketplace_economic_evidence_subject " +
                        "(organization_id,marketplace_order_id,marketplace_key,external_order_id,currency) " +
                        "VALUES (?,?,?,?,?)"
                ).use { subjectStatement ->
                    connection.prepareStatement(
                        "INSERT INTO marketplace_economic_evidence_update " +
                            "(organization_id,marketplace_order_id,evidence_version,update_id,change_kind) " +
                            "VALUES (?,?,?,?,?)"
                    ).use { updateStatement ->
                        connection.prepareStatement(
                            "INSERT INTO marketplace_economic_evidence_identifier " +
                                "(organization_id,marketplace_order_id,observation_id,evidence_version,identifier_kind) " +
                                "VALUES (?,?,?,?,?)"
                        ).use { identifierStatement ->
                            connection.prepareStatement(
                                "INSERT INTO marketplace_economic_evidence_collection_attempt " +
                                    "(organization_id,marketplace_order_id,attempt_id,evidence_version," +
                                    "family,source_system_key,outcome,attempted_at) VALUES (?,?,?,?,?,?,?,?)"
                            ).use { attemptStatement ->
                                connection.prepareStatement(
                                    "UPDATE marketplace_economic_evidence_subject SET current_version=? " +
                                        "WHERE organization_id=? AND marketplace_order_id=?"
                                ).use { rootStatement ->
                                    organizations.forEach { organization ->
                                        val orderId = UUID.randomUUID()
                                        subjectStatement.setObject(1, organization.value)
                                        subjectStatement.setObject(2, orderId)
                                        subjectStatement.setString(3, "mercado-livre")
                                        subjectStatement.setString(4, "volume-$orderId")
                                        subjectStatement.setString(5, "BRL")
                                        subjectStatement.executeUpdate()
                                        repeat(updatesPerOrganization) { index ->
                                            val version = index.toLong() + 1
                                            val updateId = UUID.randomUUID()
                                            updateStatement.setObject(1, organization.value)
                                            updateStatement.setObject(2, orderId)
                                            updateStatement.setLong(3, version)
                                            updateStatement.setObject(4, updateId)
                                            updateStatement.setString(5, "ATTEMPT")
                                            updateStatement.addBatch()

                                            identifierStatement.setObject(1, organization.value)
                                            identifierStatement.setObject(2, orderId)
                                            identifierStatement.setObject(3, updateId)
                                            identifierStatement.setLong(4, version)
                                            identifierStatement.setString(5, "ATTEMPT")
                                            identifierStatement.addBatch()

                                            attemptStatement.setObject(1, organization.value)
                                            attemptStatement.setObject(2, orderId)
                                            attemptStatement.setObject(3, updateId)
                                            attemptStatement.setLong(4, version)
                                            attemptStatement.setString(5, "MARKETPLACE_ORDER")
                                            attemptStatement.setString(6, "exp-0006")
                                            attemptStatement.setString(7, "NO_EVIDENCE")
                                            attemptStatement.setTimestamp(8, now)
                                            attemptStatement.addBatch()
                                        }
                                        updateStatement.executeBatch()
                                        identifierStatement.executeBatch()
                                        attemptStatement.executeBatch()
                                        repeat(updatesPerOrganization) { index ->
                                            rootStatement.setLong(1, index.toLong() + 1)
                                            rootStatement.setObject(2, organization.value)
                                            rootStatement.setObject(3, orderId)
                                            rootStatement.addBatch()
                                        }
                                        rootStatement.executeBatch()
                                    }
                                }
                            }
                        }
                    }
                }
                connection.commit()
                organizations
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }

    private fun <T> concurrent(first: () -> T, second: () -> T): List<T> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        return try {
            listOf(first, second).map { operation ->
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    operation()
                })
            }.also {
                check(ready.await(10, TimeUnit.SECONDS))
                start.countDown()
            }.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )
}
