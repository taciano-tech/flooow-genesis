package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.ChangeSequenceCheckpoint
import io.flooow.marketplace.operations.economics.evidence.CheckpointAdvanceResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChange
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChangeFeedResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChangeKind
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceAttemptOutcome
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceCollectionAttempt
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceFamily
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceObservationId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidencePersistResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceUpdate
import io.flooow.marketplace.operations.economics.evidence.ProjectionName
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.organization.OrganizationId
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresMarketplaceEconomicEvidenceChangeFeedTest {
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var feed: PostgresMarketplaceEconomicEvidenceChangeFeed
    private lateinit var writer: PostgresMarketplaceIndependentEconomicEvidenceRepository
    private val identifiers = AtomicLong(1)
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
        feed = PostgresMarketplaceEconomicEvidenceChangeFeed(configuration)
        writer = PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration)
    }

    @AfterAll
    fun stopPostgres() = postgres.stop()

    @BeforeTest
    fun clearDurableData() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE integration_organization CASCADE")
            }
        }
    }

    @AfterTest
    fun restoreConnectionState() {
        feed = PostgresMarketplaceEconomicEvidenceChangeFeed(configuration)
    }

    @Test
    fun `unknown organization and known organization without changes return empty without writes`() {
        val unknown = organization()
        val known = createOrganization()

        assertTrue(changes(unknown, ChangeSequenceCheckpoint.NONE, 100).isEmpty())
        assertTrue(changes(known, ChangeSequenceCheckpoint.NONE, 100).isEmpty())
        assertEquals(0, checkpointRows())
        assertEquals(0, outboxRows())
    }

    @Test
    fun `exclusive bounded feed reconstructs subject deterministically and accepts physical gaps`() {
        val first = createOrganization(uuid(101))
        val other = createOrganization(uuid(102))
        val subject = subject(first, uuid(201), "order-first")
        val firstChange = appendAttempt(subject)
        appendAttempt(subject(other, uuid(202), "order-other"))
        val secondChange = appendAttempt(subject)

        val all = changes(first, ChangeSequenceCheckpoint.NONE, 1_000)
        assertEquals(2, all.size)
        assertEquals(subject, all[0].subject)
        assertEquals(subject, all[1].subject)
        assertEquals(listOf(1L, 2L), all.map { it.evidenceVersion.valueForPersistence() })
        assertTrue(all[0].changeSequence < all[1].changeSequence)
        assertTrue(
            all[1].changeSequence.valueForPersistence() -
                all[0].changeSequence.valueForPersistence() > 1
        )
        assertEquals(
            listOf(secondChange.sequence),
            changes(first, firstChange.sequence, 1_000).map { it.changeSequence }
        )
        assertTrue(changes(first, secondChange.sequence, 1_000).isEmpty())
        assertEquals(all, changes(first, ChangeSequenceCheckpoint.NONE, 1_000))
        val firstPage = changes(first, ChangeSequenceCheckpoint.NONE, 1)
        val secondPage = changes(first, firstPage.single().changeSequence, 1)
        assertEquals(all, firstPage + secondPage)
        assertEquals(0, checkpointRows())
    }

    @Test
    fun `real P0_2 writer changes are visible through feed`() {
        val organization = createOrganization()
        val subject = subject(organization)
        val applied = writer.apply(
            MarketplaceEconomicEvidenceVersion.ZERO,
            p0_2Attempt(subject, 1)
        )
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(applied)

        val actual = changes(organization, ChangeSequenceCheckpoint.NONE, 100)
        assertEquals(1, actual.size)
        assertEquals(subject, actual.single().subject)
        assertEquals(MarketplaceEconomicEvidenceChangeKind.ATTEMPT, actual.single().changeKind)
        assertEquals(1L, actual.single().evidenceVersion.valueForPersistence())
    }

    @Test
    fun `concurrent P0_2 writer and feed reader preserve increasing per organization order`() {
        val organization = createOrganization()
        val subject = subject(organization)
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            writer.apply(MarketplaceEconomicEvidenceVersion.ZERO, p0_2Attempt(subject, 0))
        )
        val start = CountDownLatch(1)
        val writerFinished = AtomicBoolean(false)
        val snapshots = ConcurrentLinkedQueue<List<MarketplaceEconomicEvidenceChange>>()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val writeFuture = executor.submit {
                check(start.await(10, TimeUnit.SECONDS))
                repeat(12) { offset ->
                    assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
                        writer.apply(
                            MarketplaceEconomicEvidenceVersion(offset.toLong() + 1),
                            p0_2Attempt(subject, offset + 1)
                        )
                    )
                }
                writerFinished.set(true)
            }
            val readFuture = executor.submit {
                check(start.await(10, TimeUnit.SECONDS))
                while (!writerFinished.get()) {
                    snapshots += changes(organization, ChangeSequenceCheckpoint.NONE, 1_000)
                }
            }
            start.countDown()
            writeFuture.get(30, TimeUnit.SECONDS)
            readFuture.get(30, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        val final = changes(organization, ChangeSequenceCheckpoint.NONE, 1_000)
        assertEquals(13, final.size)
        assertTrue(final.isStrictlyIncreasing())
        assertTrue(snapshots.all { it.isStrictlyIncreasing() })
    }

    @Test
    fun `feed limits reject invalid values before persistence and accept both boundaries`() {
        val inaccessible = PostgresMarketplaceEconomicEvidenceChangeFeed(unreachableConfiguration())
        listOf(Int.MIN_VALUE, -1, 0, 1_001, Int.MAX_VALUE).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> {
                inaccessible.changesSince(organization(), ChangeSequenceCheckpoint.NONE, invalid)
            }
            assertFailsWith<IllegalArgumentException> {
                inaccessible.organizationsWithPendingChanges(ProjectionName("bounded"), invalid)
            }
        }

        val known = createOrganization()
        assertTrue(changes(known, ChangeSequenceCheckpoint.NONE, 1).isEmpty())
        assertTrue(changes(known, ChangeSequenceCheckpoint.NONE, 1_000).isEmpty())
        assertTrue(pending(ProjectionName("bounded"), 1).isEmpty())
        assertTrue(pending(ProjectionName("bounded"), 1_000).isEmpty())
    }

    @Test
    fun `journal kinds map exactly to closed change kinds`() {
        val organization = createOrganization()
        val subject = subject(organization)
        val fact = appendFact(subject)
        appendAttempt(subject)
        appendCorrection(subject, fact.updateId)

        val actual = changes(organization, ChangeSequenceCheckpoint.NONE, 100)
        assertEquals(
            listOf(
                MarketplaceEconomicEvidenceChangeKind.FACT,
                MarketplaceEconomicEvidenceChangeKind.ATTEMPT,
                MarketplaceEconomicEvidenceChangeKind.CORRECTION
            ),
            actual.map { it.changeKind }
        )
        assertEquals(listOf(1L, 2L, 3L), actual.map { it.evidenceVersion.valueForPersistence() })
    }

    @Test
    fun `current checkpoint handles missing known unknown durable and reconstructed adapter states`() {
        val known = createOrganization()
        val unknown = organization()
        val projection = ProjectionName("sales-intelligence")
        val change = appendAttempt(subject(known))

        assertEquals(ChangeSequenceCheckpoint.NONE, checkpoint(known, projection))
        assertEquals(ChangeSequenceCheckpoint.NONE, checkpoint(unknown, projection))
        assertEquals(0, checkpointRows())
        assertIs<CheckpointAdvanceResult.Advanced>(
            advance(known, projection, ChangeSequenceCheckpoint.NONE, change.sequence)
        )
        assertEquals(change.sequence, checkpoint(known, projection))

        feed = PostgresMarketplaceEconomicEvidenceChangeFeed(configuration)
        assertEquals(change.sequence, checkpoint(known, projection))
    }

    @Test
    fun `advance validates order regression stale valid and relational destination semantics`() {
        val organization = createOrganization()
        val other = createOrganization()
        val projection = ProjectionName("projection-a")
        val first = appendAttempt(subject(organization))
        val otherChange = appendAttempt(subject(other))
        val second = appendAttempt(subject(organization))

        val inaccessible = PostgresMarketplaceEconomicEvidenceChangeFeed(unreachableConfiguration())
        listOf(ChangeSequenceCheckpoint(10), ChangeSequenceCheckpoint(9)).forEach { next ->
            val regression = inaccessible.advanceCheckpoint(
                organization(),
                projection,
                ChangeSequenceCheckpoint(10),
                next
            )
            assertIs<MarketplaceEconomicEvidenceChangeFeedResult.Success<CheckpointAdvanceResult>>(regression)
            assertIs<CheckpointAdvanceResult.Regression>(regression.value)
        }

        val missingExpected = advance(organization, projection, first.sequence, second.sequence)
        assertEquals(
            ChangeSequenceCheckpoint.NONE,
            assertIs<CheckpointAdvanceResult.Stale>(missingExpected).currentCheckpoint
        )
        assertEquals(0, checkpointRows(organization, projection))

        val invalidOther = assertFailsWith<IllegalArgumentException> {
            feed.advanceCheckpoint(organization, projection, ChangeSequenceCheckpoint.NONE, otherChange.sequence)
        }
        assertEquals("Checkpoint destination is invalid", invalidOther.message)
        assertEquals(0, checkpointRows(organization, projection))

        val gap = ChangeSequenceCheckpoint(nextSequenceGap())
        val invalidGap = assertFailsWith<IllegalArgumentException> {
            feed.advanceCheckpoint(organization, projection, ChangeSequenceCheckpoint.NONE, gap)
        }
        assertEquals("Checkpoint destination is invalid", invalidGap.message)
        assertEquals(0, checkpointRows(organization, projection))

        assertIs<CheckpointAdvanceResult.Advanced>(
            advance(organization, projection, ChangeSequenceCheckpoint.NONE, first.sequence)
        )
        assertEquals(first.sequence, checkpoint(organization, projection))

        val stale = advance(organization, projection, ChangeSequenceCheckpoint.NONE, second.sequence)
        assertEquals(first.sequence, assertIs<CheckpointAdvanceResult.Stale>(stale).currentCheckpoint)
        assertEquals(first.sequence, checkpoint(organization, projection))

        assertIs<CheckpointAdvanceResult.Advanced>(
            advance(organization, projection, first.sequence, second.sequence)
        )
        assertEquals(second.sequence, checkpoint(organization, projection))

        assertIs<CheckpointAdvanceResult.Regression>(
            advance(organization, projection, second.sequence, second.sequence)
        )
        assertIs<CheckpointAdvanceResult.Regression>(
            advance(organization, projection, second.sequence, first.sequence)
        )
        assertEquals(second.sequence, checkpoint(organization, projection))
    }

    @Test
    fun `advance for unknown organization and infrastructure validation failure fail closed without rows`() {
        val unknown = organization()
        val result = feed.advanceCheckpoint(
            unknown,
            ProjectionName("unknown-projection"),
            ChangeSequenceCheckpoint.NONE,
            ChangeSequenceCheckpoint(1)
        )
        assertIs<MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure>(result)
        assertEquals("[REDACTED]", result.toString())
        assertEquals(0, checkpointRows())

        val inaccessible = PostgresMarketplaceEconomicEvidenceChangeFeed(unreachableConfiguration())
        val failed = inaccessible.advanceCheckpoint(
            unknown,
            ProjectionName("private-projection"),
            ChangeSequenceCheckpoint.NONE,
            ChangeSequenceCheckpoint(1)
        )
        assertIs<MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure>(failed)
        assertEquals("[REDACTED]", failed.toString())
        assertFalse(failed.toString().contains("SQL", ignoreCase = true))
        assertEquals(0, checkpointRows())
    }

    @Test
    fun `destination validation query failure is fail closed and rolls back without checkpoint mutation`() {
        val organization = OrganizationId(uuid(801))
        val projection = ProjectionName("destination-failure")
        val fixture = installDestinationValidationFailureSchema(organization)
        val isolatedFeed = PostgresMarketplaceEconomicEvidenceChangeFeed(fixture.configuration)

        assertEquals(1, schemaTableRows(fixture.schema, "integration_organization"))
        connection(fixture.configuration).use { connection ->
            connection.prepareStatement(
                "SELECT 1 FROM integration_organization WHERE organization_id=?"
            ).use { statement ->
                statement.setObject(1, organization.value)
                statement.executeQuery().use { result -> assertTrue(result.next()) }
            }
            val destinationFailure = assertFailsWith<SQLException> {
                connection.prepareStatement(
                    "SELECT 1 FROM marketplace_economic_evidence_update " +
                        "WHERE organization_id=? AND change_sequence=?"
                ).use { statement ->
                    statement.setObject(1, organization.value)
                    statement.setLong(2, 1)
                    statement.executeQuery()
                }
            }
            assertEquals("42703", destinationFailure.sqlState)
        }

        val result = isolatedFeed.advanceCheckpoint(
            organization,
            projection,
            ChangeSequenceCheckpoint.NONE,
            ChangeSequenceCheckpoint(1)
        )

        assertSanitizedIntegrityFailure(
            result,
            fixture.schema,
            "marketplace_economic_evidence_update",
            organization.value.toString(),
            projection.valueForPersistence(),
            "42703"
        )
        assertEquals(
            0,
            schemaTableRows(fixture.schema, "marketplace_economic_evidence_projection_checkpoint")
        )
    }

    @Test
    fun `deferred commit failure is fail closed and rolls back checkpoint write`() {
        val organization = OrganizationId(uuid(802))
        val projection = ProjectionName("commit-failure")
        val fixture = installDeferredCommitFailureSchema(organization, 1)
        val isolatedFeed = PostgresMarketplaceEconomicEvidenceChangeFeed(fixture.configuration)

        connection(fixture.configuration).use { connection ->
            connection.autoCommit = false
            try {
                assertEquals(
                    1,
                    execute(
                        connection,
                        "INSERT INTO marketplace_economic_evidence_projection_checkpoint " +
                            "(organization_id,projection_name,last_change_sequence,updated_at) " +
                            "VALUES (?,?,?,transaction_timestamp()) ON CONFLICT DO NOTHING",
                        organization.value,
                        projection.valueForPersistence(),
                        1L
                    )
                )
                assertEquals(
                    1L,
                    queryLong(
                        connection,
                        "SELECT count(*) FROM marketplace_economic_evidence_projection_checkpoint"
                    )
                )
                val commitFailure = assertFailsWith<SQLException> { connection.commit() }
                assertEquals("23503", commitFailure.sqlState)
            } finally {
                connection.rollback()
            }
        }
        assertEquals(
            0,
            schemaTableRows(fixture.schema, "marketplace_economic_evidence_projection_checkpoint")
        )

        val result = isolatedFeed.advanceCheckpoint(
            organization,
            projection,
            ChangeSequenceCheckpoint.NONE,
            ChangeSequenceCheckpoint(1)
        )

        assertSanitizedIntegrityFailure(
            result,
            fixture.schema,
            "marketplace_economic_evidence_projection_checkpoint",
            "checkpoint_commit_guard_fk",
            organization.value.toString(),
            projection.valueForPersistence(),
            "23503"
        )
        assertEquals(
            0,
            schemaTableRows(fixture.schema, "marketplace_economic_evidence_projection_checkpoint")
        )
    }

    @Test
    fun `concurrent first checkpoint writers yield one advanced and one stale`() {
        val organization = createOrganization()
        val projection = ProjectionName("concurrent-first")
        val next = appendAttempt(subject(organization)).sequence

        val results = concurrent(
            { feed.advanceCheckpoint(organization, projection, ChangeSequenceCheckpoint.NONE, next) },
            { feed.advanceCheckpoint(organization, projection, ChangeSequenceCheckpoint.NONE, next) }
        )

        assertConcurrencyResult(results, next)
        assertEquals(1, checkpointRows(organization, projection))
    }

    @Test
    fun `concurrent existing checkpoint writers yield one advanced and one stale`() {
        val organization = createOrganization()
        val projection = ProjectionName("concurrent-existing")
        val subject = subject(organization)
        val current = appendAttempt(subject).sequence
        val next = appendAttempt(subject).sequence
        assertIs<CheckpointAdvanceResult.Advanced>(
            advance(organization, projection, ChangeSequenceCheckpoint.NONE, current)
        )

        val results = concurrent(
            { feed.advanceCheckpoint(organization, projection, current, next) },
            { feed.advanceCheckpoint(organization, projection, current, next) }
        )

        assertConcurrencyResult(results, next)
        assertEquals(next, checkpoint(organization, projection))
    }

    @Test
    fun `checkpoint state is isolated by organization and projection and reads do not mutate`() {
        val first = createOrganization()
        val second = createOrganization()
        val firstChange = appendAttempt(subject(first))
        val secondChange = appendAttempt(subject(second))
        val sales = ProjectionName("sales")
        val audit = ProjectionName("audit")

        assertIs<CheckpointAdvanceResult.Advanced>(
            advance(first, sales, ChangeSequenceCheckpoint.NONE, firstChange.sequence)
        )
        assertIs<CheckpointAdvanceResult.Advanced>(
            advance(second, sales, ChangeSequenceCheckpoint.NONE, secondChange.sequence)
        )
        assertEquals(ChangeSequenceCheckpoint.NONE, checkpoint(first, audit))
        assertEquals(firstChange.sequence, checkpoint(first, sales))
        assertEquals(secondChange.sequence, checkpoint(second, sales))

        val before = checkpointSnapshot()
        changes(first, ChangeSequenceCheckpoint.NONE, 100)
        pending(sales, 100)
        checkpoint(first, sales)
        assertEquals(before, checkpointSnapshot())
        assertEquals(0, outboxRows())
    }

    @Test
    fun `pending discovery is projection isolated mixed bounded deterministic and cannot invent organizations`() {
        val first = createOrganization(uuid(1))
        val second = createOrganization(uuid(2))
        val thirdWithoutChanges = createOrganization(uuid(3))
        val firstChange = appendAttempt(subject(first))
        appendAttempt(subject(second))
        val sales = ProjectionName("sales")
        val audit = ProjectionName("audit")

        assertEquals(listOf(first), pending(sales, 1))
        assertEquals(listOf(first, second), pending(sales, 1_000))
        assertEquals(listOf(first, second), pending(sales, 1_000))
        assertFalse(thirdWithoutChanges in pending(sales, 1_000))

        assertIs<CheckpointAdvanceResult.Advanced>(
            advance(first, sales, ChangeSequenceCheckpoint.NONE, firstChange.sequence)
        )
        assertEquals(listOf(second), pending(sales, 1_000))
        assertEquals(listOf(first, second), pending(audit, 1_000))
        assertTrue(pending(sales, 1_000).all { it == first || it == second })
    }

    @Test
    fun `bounded fixed ordering demonstrates known starvation without fairness state`() {
        val first = createOrganization(uuid(11))
        val second = createOrganization(uuid(12))
        val third = createOrganization(uuid(13))
        listOf(first, second, third).forEach { appendAttempt(subject(it)) }
        val projection = ProjectionName("starvation")

        repeat(4) {
            assertEquals(listOf(first), pending(projection, 1))
        }
        assertFalse(second in pending(projection, 1))
        assertFalse(third in pending(projection, 1))
        assertEquals(0, checkpointRows())
        assertNoForbiddenCoordinationObjects()
    }

    @Test
    fun `adapter SQL has canonical Query B indexed EXISTS shape without forbidden alternatives`() {
        val sql = productionSql("PENDING_ORGANIZATIONS_SQL")
            .replace(Regex("\\s+"), " ")
            .lowercase()
        assertContains(sql, "from integration_organization o")
        assertContains(sql, "left join marketplace_economic_evidence_projection_checkpoint c")
        assertContains(sql, "where exists")
        assertContains(sql, "from marketplace_economic_evidence_update u")
        assertContains(sql, "u.organization_id=o.organization_id")
        assertContains(sql, "u.change_sequence>coalesce(c.last_change_sequence,0)")
        assertContains(sql, "order by o.organization_id asc limit ?")
        assertFalse(sql.contains("group by"))
        assertFalse(sql.contains("max("))
        assertFalse(sql.contains("order by u.change_sequence desc"))
    }

    @Test
    fun `controlled Query B explain records expected indexed EXISTS path`() {
        createVolumeFixture(80, 40)
        val plan = explainPending(ProjectionName("explain-projection"), 1_000)
        println("TASK-0145 QUERY B EXPLAIN BEGIN")
        plan.forEach(::println)
        println("TASK-0145 QUERY B EXPLAIN END")

        assertTrue(plan.any { "marketplace_economic_evidence_update" in it })
        assertTrue(plan.any { "Index" in it && "change_sequence" in it })
        assertTrue(plan.any { "actual" in it })
        assertTrue(plan.any { "Buffers:" in it })
        assertTrue(plan.any { "Planning Time:" in it })
        assertTrue(plan.any { "Execution Time:" in it })
    }

    @Test
    fun `V016 migration enforces identity checks foreign keys timestamp and exact metadata`() {
        val organization = createOrganization()
        val other = createOrganization()
        val first = appendAttempt(subject(organization))
        val otherChange = appendAttempt(subject(other))
        val second = appendAttempt(subject(organization))

        assertSqlState("23503") {
            insertCheckpoint(organization(), "unknown", first.sequence.valueForPersistence())
        }
        insertCheckpoint(organization, "valid-name", first.sequence.valueForPersistence())
        assertSqlState("23505") {
            insertCheckpoint(organization, "valid-name", first.sequence.valueForPersistence())
        }
        listOf("", "Upper", "bad_name", "a".repeat(101)).forEach { invalid ->
            assertSqlState("23514") {
                insertCheckpoint(organization, invalid, second.sequence.valueForPersistence())
            }
        }
        assertSqlState("23514") {
            insertCheckpoint(organization, "negative", -1)
        }
        assertSqlState("23503") {
            insertCheckpoint(organization, "invented", Long.MAX_VALUE)
        }
        assertSqlState("23503") {
            insertCheckpoint(organization, "other-sequence", otherChange.sequence.valueForPersistence())
        }
        insertCheckpoint(organization, "same-org", second.sequence.valueForPersistence())

        val stamped = checkpointTimestamp(organization, "valid-name")
        assertNotEquals(Instant.parse("2000-01-01T00:00:00Z"), stamped)
        updateCheckpointDirect(organization, "valid-name", second.sequence.valueForPersistence())
        val updated = checkpointTimestamp(organization, "valid-name")
        assertTrue(updated >= stamped)
        assertNotEquals(Instant.parse("2001-01-01T00:00:00Z"), updated)

        val definition = queryString(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint " +
                "WHERE conname='marketplace_economic_evidence_projection_checkpoint_change_fk'"
        )
        assertEquals(
            "FOREIGN KEY (organization_id, last_change_sequence) REFERENCES " +
                "marketplace_economic_evidence_update(organization_id, change_sequence)",
            definition
        )
        assertEquals("016", queryString("SELECT version FROM flyway_schema_history WHERE version='016' AND success"))
    }

    @Test
    fun `V015 and all prior migrations are byte unchanged and V016 adds no journal index`() {
        EXPECTED_MIGRATION_SHA256.forEach { (name, expected) ->
            assertEquals(expected, resourceSha256("db/migration/$name"), name)
        }
        assertEquals(
            setOf(
                "marketplace_economic_evidence_update_pkey",
                "marketplace_economic_evidence_organization_id_marketplace__key1",
                "marketplace_economic_evidence_organization_id_change_sequen_key"
            ),
            queryStrings(
                "SELECT indexname FROM pg_indexes " +
                    "WHERE schemaname='public' AND tablename='marketplace_economic_evidence_update'"
            ).toSet()
        )
        val v016 = resourceText(
            "db/migration/V016__create_marketplace_economic_evidence_projection_checkpoint.sql"
        )
        assertFalse(v016.contains("CREATE INDEX", ignoreCase = true))
        assertEquals(1, Regex("CREATE TABLE", RegexOption.IGNORE_CASE).findAll(v016).count())
        assertEquals(1, Regex("CREATE FUNCTION", RegexOption.IGNORE_CASE).findAll(v016).count())
        assertEquals(1, Regex("CREATE TRIGGER", RegexOption.IGNORE_CASE).findAll(v016).count())
        assertContains(v016, "marketplace_economic_evidence_projection_checkpoint")
        assertFalse(v016.contains("CREATE TABLE marketplace_economic_evidence_update", ignoreCase = true))
    }

    @Test
    fun `connection query mapping transaction and malformed failures are fail closed without leakage`() {
        val inaccessible = PostgresMarketplaceEconomicEvidenceChangeFeed(unreachableConfiguration())
        val organization = organization()
        val projection = ProjectionName("secret-projection")
        val failures = listOf(
            inaccessible.changesSince(organization, ChangeSequenceCheckpoint.NONE, 10),
            inaccessible.organizationsWithPendingChanges(projection, 10),
            inaccessible.currentCheckpoint(organization, projection),
            inaccessible.advanceCheckpoint(
                organization,
                projection,
                ChangeSequenceCheckpoint.NONE,
                ChangeSequenceCheckpoint(1)
            )
        )
        failures.forEach { failure ->
            assertIs<MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure>(failure)
            assertEquals("[REDACTED]", failure.toString())
            listOf("sql", "jdbc", "constraint", "projection", "sequence", organization.value.toString())
                .forEach { forbidden ->
                    assertFalse(failure.toString().contains(forbidden, ignoreCase = true))
                }
        }

        val malformedConfiguration = installMalformedMappingSchema()
        val malformedFeed = PostgresMarketplaceEconomicEvidenceChangeFeed(malformedConfiguration)
        val mapped = malformedFeed.changesSince(
            OrganizationId(uuid(700)),
            ChangeSequenceCheckpoint.NONE,
            10
        )
        assertIs<MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure>(mapped)
        assertEquals("[REDACTED]", mapped.toString())
        assertEquals(0, checkpointRows())
    }

    @Test
    fun `checkpoint operations create no outbox projection scheduler or provider side effect`() {
        val organization = createOrganization()
        val change = appendAttempt(subject(organization))
        val projection = ProjectionName("side-effects")
        val beforeOutbox = outboxRows()

        changes(organization, ChangeSequenceCheckpoint.NONE, 100)
        pending(projection, 100)
        checkpoint(organization, projection)
        assertIs<CheckpointAdvanceResult.Advanced>(
            advance(organization, projection, ChangeSequenceCheckpoint.NONE, change.sequence)
        )

        assertEquals(beforeOutbox, outboxRows())
        assertEquals(1, checkpointRows(organization, projection))
        assertNoForbiddenCoordinationObjects()
    }

    private fun changes(
        organizationId: OrganizationId,
        checkpoint: ChangeSequenceCheckpoint,
        limit: Int
    ): List<MarketplaceEconomicEvidenceChange> = assertIs<
        MarketplaceEconomicEvidenceChangeFeedResult.Success<List<MarketplaceEconomicEvidenceChange>>
        >(feed.changesSince(organizationId, checkpoint, limit)).value

    private fun pending(projectionName: ProjectionName, limit: Int): List<OrganizationId> =
        assertIs<MarketplaceEconomicEvidenceChangeFeedResult.Success<List<OrganizationId>>>(
            feed.organizationsWithPendingChanges(projectionName, limit)
        ).value

    private fun checkpoint(
        organizationId: OrganizationId,
        projectionName: ProjectionName
    ): ChangeSequenceCheckpoint =
        assertIs<MarketplaceEconomicEvidenceChangeFeedResult.Success<ChangeSequenceCheckpoint>>(
            feed.currentCheckpoint(organizationId, projectionName)
        ).value

    private fun advance(
        organizationId: OrganizationId,
        projectionName: ProjectionName,
        expected: ChangeSequenceCheckpoint,
        next: ChangeSequenceCheckpoint
    ): CheckpointAdvanceResult =
        assertIs<MarketplaceEconomicEvidenceChangeFeedResult.Success<CheckpointAdvanceResult>>(
            feed.advanceCheckpoint(organizationId, projectionName, expected, next)
        ).value

    private fun assertConcurrencyResult(
        results: List<MarketplaceEconomicEvidenceChangeFeedResult<CheckpointAdvanceResult>>,
        expectedCheckpoint: ChangeSequenceCheckpoint
    ) {
        assertEquals(1, results.count { result ->
            result is MarketplaceEconomicEvidenceChangeFeedResult.Success &&
                result.value is CheckpointAdvanceResult.Advanced
        })
        assertEquals(1, results.count { result ->
            result is MarketplaceEconomicEvidenceChangeFeedResult.Success &&
                result.value is CheckpointAdvanceResult.Stale
        })
        results.forEach { assertIs<MarketplaceEconomicEvidenceChangeFeedResult.Success<*>>(it) }
        assertEquals(expectedCheckpoint, checkpointValue(results, CheckpointAdvanceResult.Advanced::class.java))
        assertEquals(expectedCheckpoint, checkpointValue(results, CheckpointAdvanceResult.Stale::class.java))
    }

    private fun checkpointValue(
        results: List<MarketplaceEconomicEvidenceChangeFeedResult<CheckpointAdvanceResult>>,
        kind: Class<out CheckpointAdvanceResult>
    ): ChangeSequenceCheckpoint {
        val result = results.mapNotNull { it as? MarketplaceEconomicEvidenceChangeFeedResult.Success }
            .map { it.value }
            .single { kind.isInstance(it) }
        return when (result) {
            is CheckpointAdvanceResult.Advanced -> result.checkpoint
            is CheckpointAdvanceResult.Stale -> result.currentCheckpoint
            CheckpointAdvanceResult.Regression -> error("Unexpected regression")
        }
    }

    private fun appendAttempt(subject: MarketplaceEconomicEvidenceSubject): AppliedChange =
        appendChange(subject, "ATTEMPT") { connection, version, updateId ->
            insertIdentifier(connection, subject, version, updateId, "ATTEMPT")
            execute(
                connection,
                "INSERT INTO marketplace_economic_evidence_collection_attempt " +
                    "(organization_id,marketplace_order_id,attempt_id,evidence_version," +
                    "family,source_system_key,outcome,attempted_at) VALUES (?,?,?,?,?,?,?,?)",
                subject.organizationId.value,
                subject.orderId.value,
                updateId,
                version,
                "MARKETPLACE_ORDER",
                "mgi-test",
                "NO_EVIDENCE",
                Timestamp.from(baseTime.plusSeconds(version))
            )
        }

    private fun p0_2Attempt(
        subject: MarketplaceEconomicEvidenceSubject,
        offset: Int
    ): MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt =
        MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
            MarketplaceEconomicEvidenceCollectionAttempt(
                MarketplaceEconomicEvidenceObservationId.parse(UUID.randomUUID().toString()),
                subject,
                MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                EconomicSourceSystemKey("mgi-test"),
                MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE,
                baseTime.plusSeconds(offset.toLong())
            )
        )

    private fun List<MarketplaceEconomicEvidenceChange>.isStrictlyIncreasing(): Boolean =
        zipWithNext().all { (left, right) -> left.changeSequence < right.changeSequence }

    private fun appendFact(subject: MarketplaceEconomicEvidenceSubject): AppliedChange =
        appendChange(subject, "FACT") { connection, version, updateId ->
            insertComponentFact(connection, subject, version, updateId)
        }

    private fun appendCorrection(
        subject: MarketplaceEconomicEvidenceSubject,
        supersededFactId: UUID
    ): AppliedChange = appendChange(subject, "CORRECTION") { connection, version, updateId ->
        val replacementId = UUID.randomUUID()
        insertIdentifier(connection, subject, version, updateId, "CORRECTION")
        insertComponentFact(connection, subject, version, replacementId)
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_correction " +
                "(organization_id,marketplace_order_id,correction_id,evidence_version," +
                "superseded_fact_id,replacement_fact_id,reason,observed_at) VALUES (?,?,?,?,?,?,?,?)",
            subject.organizationId.value,
            subject.orderId.value,
            updateId,
            version,
            supersededFactId,
            replacementId,
            "SOURCE_CORRECTION",
            Timestamp.from(baseTime.plusSeconds(version))
        )
    }

    private fun appendChange(
        subject: MarketplaceEconomicEvidenceSubject,
        kind: String,
        subtype: (Connection, Long, UUID) -> Unit
    ): AppliedChange = transaction { connection ->
        ensureSubject(connection, subject)
        val version = queryLong(
            connection,
            "SELECT current_version + 1 FROM marketplace_economic_evidence_subject " +
                "WHERE organization_id=? AND marketplace_order_id=? FOR UPDATE",
            subject.organizationId.value,
            subject.orderId.value
        )
        val updateId = UUID.randomUUID()
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_update " +
                "(organization_id,marketplace_order_id,evidence_version,update_id,change_kind) " +
                "VALUES (?,?,?,?,?)",
            subject.organizationId.value,
            subject.orderId.value,
            version,
            updateId,
            kind
        )
        subtype(connection, version, updateId)
        execute(
            connection,
            "UPDATE marketplace_economic_evidence_subject SET current_version=? " +
                "WHERE organization_id=? AND marketplace_order_id=?",
            version,
            subject.organizationId.value,
            subject.orderId.value
        )
        val sequence = queryLong(
            connection,
            "SELECT change_sequence FROM marketplace_economic_evidence_update " +
                "WHERE organization_id=? AND marketplace_order_id=? AND evidence_version=?",
            subject.organizationId.value,
            subject.orderId.value,
            version
        )
        AppliedChange(updateId, ChangeSequenceCheckpoint(sequence))
    }

    private fun insertComponentFact(
        connection: Connection,
        subject: MarketplaceEconomicEvidenceSubject,
        version: Long,
        factId: UUID
    ) {
        insertIdentifier(connection, subject, version, factId, "FACT")
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_fact " +
                "(organization_id,marketplace_order_id,fact_id,evidence_version,fact_kind,family,observed_at) " +
                "VALUES (?,?,?,?,?,?,?)",
            subject.organizationId.value,
            subject.orderId.value,
            factId,
            version,
            "COMPONENT",
            "MARKETPLACE_ORDER",
            Timestamp.from(baseTime.plusSeconds(version))
        )
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_component_fact " +
                "(organization_id,marketplace_order_id,fact_id,evidence_version,family,component_id," +
                "component_type,direction,magnitude,currency,source_kind,source_system_key," +
                "source_external_reference,occurred_at,quality,coverage) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            subject.organizationId.value,
            subject.orderId.value,
            factId,
            version,
            "MARKETPLACE_ORDER",
            UUID.randomUUID(),
            "REVENUE",
            "ADDITION",
            10.toBigDecimal(),
            subject.currency.code,
            "MARKETPLACE",
            "mgi-test",
            "source-$factId",
            Timestamp.from(baseTime.plusSeconds(version)),
            "CONFIRMED",
            "COMPLETE"
        )
    }

    private fun insertIdentifier(
        connection: Connection,
        subject: MarketplaceEconomicEvidenceSubject,
        version: Long,
        identifier: UUID,
        kind: String
    ) {
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_identifier " +
                "(organization_id,marketplace_order_id,observation_id,evidence_version,identifier_kind) " +
                "VALUES (?,?,?,?,?)",
            subject.organizationId.value,
            subject.orderId.value,
            identifier,
            version,
            kind
        )
    }

    private fun ensureSubject(connection: Connection, subject: MarketplaceEconomicEvidenceSubject) {
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_subject " +
                "(organization_id,marketplace_order_id,marketplace_key,external_order_id,currency) " +
                "VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
            subject.organizationId.value,
            subject.orderId.value,
            subject.marketplace.value,
            subject.externalOrderId.value,
            subject.currency.code
        )
    }

    private fun createVolumeFixture(organizationCount: Int, updatesPerOrganization: Int) {
        repeat(organizationCount) { organizationIndex ->
            val organization = createOrganization(uuid(10_000L + organizationIndex))
            val subject = subject(organization, uuid(20_000L + organizationIndex), "volume-$organizationIndex")
            repeat(updatesPerOrganization) { appendAttempt(subject) }
        }
    }

    private fun explainPending(projectionName: ProjectionName, limit: Int): List<String> =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("ANALYZE integration_organization")
                statement.execute("ANALYZE marketplace_economic_evidence_update")
                statement.execute("ANALYZE marketplace_economic_evidence_projection_checkpoint")
                statement.execute("SET enable_seqscan = off")
            }
            connection.prepareStatement(
                "EXPLAIN (ANALYZE, BUFFERS) " +
                    productionSql("PENDING_ORGANIZATIONS_SQL")
            ).use { statement ->
                statement.setString(1, projectionName.valueForPersistence())
                statement.setInt(2, limit)
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(result.getString(1)) }
                }
            }
        }

    private fun installMalformedMappingSchema(): PostgresConfiguration {
        val schema = "malformed_${identifiers.incrementAndGet()}"
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE SCHEMA $schema")
                statement.execute(
                    "CREATE TABLE $schema.marketplace_economic_evidence_update (" +
                        "organization_id uuid,marketplace_order_id uuid,evidence_version bigint," +
                        "change_sequence bigint,change_kind text)"
                )
                statement.execute(
                    "CREATE TABLE $schema.marketplace_economic_evidence_subject (" +
                        "organization_id uuid,marketplace_order_id uuid,marketplace_key text," +
                        "external_order_id text,currency char(3))"
                )
                statement.execute(
                    "INSERT INTO $schema.marketplace_economic_evidence_subject VALUES " +
                        "('${uuid(700)}','${uuid(701)}','mercado-livre','malformed','BRL')"
                )
                statement.execute(
                    "INSERT INTO $schema.marketplace_economic_evidence_update VALUES " +
                        "('${uuid(700)}','${uuid(701)}',1,1,'UNKNOWN')"
                )
            }
        }
        val separator = if (configuration.url.contains('?')) "&" else "?"
        return PostgresConfiguration(
            configuration.url + separator + "currentSchema=$schema,public",
            configuration.user,
            configuration.password
        )
    }

    private fun installDestinationValidationFailureSchema(
        organizationId: OrganizationId
    ): TestSchemaFixture {
        val schema = "destination_failure_${identifiers.incrementAndGet()}"
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE SCHEMA $schema")
                statement.execute(
                    "CREATE TABLE $schema.integration_organization (organization_id uuid PRIMARY KEY)"
                )
                statement.execute(
                    "CREATE TABLE $schema.marketplace_economic_evidence_update (organization_id uuid NOT NULL)"
                )
                statement.execute(
                    "CREATE TABLE $schema.marketplace_economic_evidence_projection_checkpoint (" +
                        "organization_id uuid NOT NULL,projection_name text NOT NULL," +
                        "last_change_sequence bigint NOT NULL,updated_at timestamptz NOT NULL," +
                        "PRIMARY KEY (organization_id,projection_name))"
                )
            }
        }
        execute(
            "INSERT INTO $schema.integration_organization (organization_id) VALUES (?)",
            organizationId.value
        )
        return TestSchemaFixture(schema, configurationForSchema(schema))
    }

    private fun installDeferredCommitFailureSchema(
        organizationId: OrganizationId,
        changeSequence: Long
    ): TestSchemaFixture {
        val schema = "commit_failure_${identifiers.incrementAndGet()}"
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE SCHEMA $schema")
                statement.execute(
                    "CREATE TABLE $schema.integration_organization (organization_id uuid PRIMARY KEY)"
                )
                statement.execute(
                    "CREATE TABLE $schema.marketplace_economic_evidence_update (" +
                        "organization_id uuid NOT NULL,change_sequence bigint NOT NULL," +
                        "PRIMARY KEY (organization_id,change_sequence))"
                )
                statement.execute(
                    "CREATE TABLE $schema.checkpoint_commit_guard (guard_id integer PRIMARY KEY)"
                )
                statement.execute(
                    "CREATE TABLE $schema.marketplace_economic_evidence_projection_checkpoint (" +
                        "organization_id uuid NOT NULL,projection_name text NOT NULL," +
                        "last_change_sequence bigint NOT NULL,updated_at timestamptz NOT NULL," +
                        "guard_id integer NOT NULL DEFAULT 1," +
                        "PRIMARY KEY (organization_id,projection_name)," +
                        "CONSTRAINT checkpoint_commit_guard_fk FOREIGN KEY (guard_id) " +
                        "REFERENCES $schema.checkpoint_commit_guard(guard_id) " +
                        "DEFERRABLE INITIALLY DEFERRED)"
                )
            }
        }
        execute(
            "INSERT INTO $schema.integration_organization (organization_id) VALUES (?)",
            organizationId.value
        )
        execute(
            "INSERT INTO $schema.marketplace_economic_evidence_update " +
                "(organization_id,change_sequence) VALUES (?,?)",
            organizationId.value,
            changeSequence
        )
        return TestSchemaFixture(schema, configurationForSchema(schema))
    }

    private fun configurationForSchema(schema: String): PostgresConfiguration {
        val separator = if (configuration.url.contains('?')) "&" else "?"
        return PostgresConfiguration(
            configuration.url + separator + "currentSchema=$schema,public",
            configuration.user,
            configuration.password
        )
    }

    private fun schemaTableRows(schema: String, table: String): Int =
        queryString("SELECT count(*)::text FROM $schema.$table").toInt()

    private fun assertSanitizedIntegrityFailure(
        result: MarketplaceEconomicEvidenceChangeFeedResult<CheckpointAdvanceResult>,
        vararg forbiddenValues: String
    ) {
        assertIs<MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure>(result)
        val rendered = result.toString()
        assertEquals("[REDACTED]", rendered)
        (listOf("sql", "jdbc", "constraint", "table", "schema") + forbiddenValues).forEach { forbidden ->
            assertFalse(rendered.contains(forbidden, ignoreCase = true))
        }
    }

    private fun insertCheckpoint(organizationId: OrganizationId, projection: String, sequence: Long) {
        execute(
            "INSERT INTO marketplace_economic_evidence_projection_checkpoint " +
                "(organization_id,projection_name,last_change_sequence,updated_at) " +
                "VALUES (?,?,?,?::timestamptz)",
            organizationId.value,
            projection,
            sequence,
            "2000-01-01T00:00:00Z"
        )
    }

    private fun updateCheckpointDirect(organizationId: OrganizationId, projection: String, sequence: Long) {
        execute(
            "UPDATE marketplace_economic_evidence_projection_checkpoint " +
                "SET last_change_sequence=?,updated_at=?::timestamptz " +
                "WHERE organization_id=? AND projection_name=?",
            sequence,
            "2001-01-01T00:00:00Z",
            organizationId.value,
            projection
        )
    }

    private fun checkpointTimestamp(organizationId: OrganizationId, projection: String): Instant =
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT updated_at FROM marketplace_economic_evidence_projection_checkpoint " +
                    "WHERE organization_id=? AND projection_name=?"
            ).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setString(2, projection)
                statement.executeQuery().use { result -> result.next(); result.getTimestamp(1).toInstant() }
            }
        }

    private fun checkpointRows(
        organizationId: OrganizationId? = null,
        projectionName: ProjectionName? = null
    ): Int = connection().use { connection ->
        val sql = if (organizationId == null) {
            "SELECT count(*) FROM marketplace_economic_evidence_projection_checkpoint"
        } else {
            "SELECT count(*) FROM marketplace_economic_evidence_projection_checkpoint " +
                "WHERE organization_id=? AND projection_name=?"
        }
        connection.prepareStatement(sql).use { statement ->
            if (organizationId != null) {
                statement.setObject(1, organizationId.value)
                statement.setString(2, assertNotNull(projectionName).valueForPersistence())
            }
            statement.executeQuery().use { result -> result.next(); result.getInt(1) }
        }
    }

    private fun checkpointSnapshot(): List<String> = queryStrings(
        "SELECT organization_id::text || ':' || projection_name || ':' || last_change_sequence::text " +
            "FROM marketplace_economic_evidence_projection_checkpoint ORDER BY 1"
    )

    private fun outboxRows(): Int = queryString("SELECT count(*)::text FROM integration_event_outbox").toInt()

    private fun assertNoForbiddenCoordinationObjects() {
        val names = queryStrings(
            "SELECT table_name FROM information_schema.tables WHERE table_schema='public'"
        ).map(String::lowercase)
        listOf("lease", "claim", "scheduler", "round_robin", "last_served").forEach { forbidden ->
            assertTrue(names.none { forbidden in it })
        }
    }

    private fun assertSqlState(expected: String, operation: () -> Unit) {
        val failure = assertFailsWith<SQLException> { operation() }
        assertEquals(expected, failure.sqlState)
    }

    private fun nextSequenceGap(): Long = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT nextval('marketplace_economic_evidence_change_sequence')").use { result ->
                result.next()
                result.getLong(1)
            }
        }
    }

    private fun createOrganization(value: UUID = UUID.randomUUID()): OrganizationId =
        OrganizationId(value).also { organization ->
            execute(
                "INSERT INTO integration_organization " +
                    "(organization_id,status,created_at,updated_at) VALUES (?,'ACTIVE',?,?)",
                organization.value,
                Timestamp.from(baseTime),
                Timestamp.from(baseTime)
            )
        }

    private fun subject(
        organizationId: OrganizationId,
        orderId: UUID = UUID.randomUUID(),
        externalOrderId: String = "order-${identifiers.incrementAndGet()}"
    ): MarketplaceEconomicEvidenceSubject = MarketplaceEconomicEvidenceSubject(
        organizationId,
        MarketplaceOrderId(orderId),
        MarketplaceKey("mercado-livre"),
        MarketplaceExternalOrderId(externalOrderId),
        MarketplaceCurrency("BRL")
    )

    private fun organization(): OrganizationId = OrganizationId(UUID.randomUUID())

    private fun uuid(value: Long): UUID = UUID(0, value)

    private fun unreachableConfiguration(): PostgresConfiguration = PostgresConfiguration(
        "jdbc:postgresql://127.0.0.1:1/unreachable?connectTimeout=1",
        "unavailable",
        "unavailable"
    )

    private fun <T> concurrent(first: () -> T, second: () -> T): List<T> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        return try {
            val futures = listOf(first, second).map { operation ->
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    operation()
                })
            }
            check(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun <T> transaction(block: (Connection) -> T): T = connection().use { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        }
    }

    private fun execute(sql: String, vararg values: Any?): Int = connection().use { connection ->
        execute(connection, sql, *values)
    }

    private fun execute(connection: Connection, sql: String, vararg values: Any?): Int =
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }

    private fun queryLong(connection: Connection, sql: String, vararg values: Any?): Long =
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }

    private fun queryString(sql: String): String = queryStrings(sql).single()

    private fun queryStrings(sql: String): List<String> = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                buildList { while (result.next()) add(result.getString(1)) }
            }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private fun connection(configuration: PostgresConfiguration): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private fun resourceText(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "Missing resource" }
            .bufferedReader()
            .use { it.readText() }

    private fun productionSql(fieldName: String): String {
        val field = PostgresMarketplaceEconomicEvidenceChangeFeed::class.java
            .getDeclaredField(fieldName)
        field.trySetAccessible()
        return field.get(null) as String
    }

    private fun resourceSha256(name: String): String {
        val bytes = checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "Missing resource" }
            .use { it.readBytes() }
        val canonicalBytes = bytes
            .toString(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\n", "\r\n")
            .toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(canonicalBytes)
            .joinToString("") { "%02x".format(it) }
    }

    private data class AppliedChange(
        val updateId: UUID,
        val sequence: ChangeSequenceCheckpoint
    )

    private data class TestSchemaFixture(
        val schema: String,
        val configuration: PostgresConfiguration
    )

    private companion object {
        val EXPECTED_MIGRATION_SHA256 = linkedMapOf(
            "V001__create_inventory_risk_assessment_journal.sql" to "715416067810e703397021cecb2455fe6bbf186b394fadd151394ec39cf0272b",
            "V002__create_integration_event_outbox.sql" to "ae8079965963ddaef352750659aa400bad6ca034169840480d4707b033c9cd08",
            "V003__create_integration_event_delivery.sql" to "49776a5d16f872e7fffd3b3f26c02422bd320137463da41863bbb792ecfd5cdd",
            "V004__create_integration_control_plane.sql" to "46e33bd8d17c94e4562aabc7cc96ba5eb104d73ad515df9fd977caac987cb9f0",
            "V005__propagate_organization_context.sql" to "64136b33e65b479deaac7fa3a4b5c25b4328bc5d3b5af01ed070447c4f5b801c",
            "V006__create_inventory_source_ledger.sql" to "5c56486690f7645ed7af4787720ca1bd533aeb9a0feddddfbc19103dab71e2b0",
            "V007__create_inventory_identity_mapping.sql" to "22ae6c3d26a947b1cbe674ed35549db00ea5476110330c3d86f9a2817297abb1",
            "V008__create_canonical_inventory_observation.sql" to "edb723bf3a3d0666ca1542473ea2f82ce8fc9f8b9492ff8f7a2bf190a98f3a3f",
            "V009__allow_mapping_across_matching_inventory_evidence.sql" to "48ecb7e1ff6e2ee27dbc14962990bb6bde278503828e0bdad8d2d8377103d94e",
            "V010__create_canonical_inventory_source_acceptance.sql" to "31766093f68a70cbd3e401da73942e610e7942f01dccbe50d942b94d07a45ccf",
            "V011__create_canonical_inventory_measure_selection.sql" to "0a318eebf139dbb4928a0e42890b705883b14c129ac3b35b2874fbf8a8bad3cb",
            "V012__create_canonical_inventory_candidate_snapshot.sql" to "1f61fffd234c0ce33281a4c690d93723c248516ae389176ae548fd99e98f2714",
            "V013__create_canonical_inventory_candidate_adjudication.sql" to "64373ef69a862d8bc79a54d4f82b46382d499e879de89f6b33be00db78e36422",
            "V014__create_marketplace_financial_ledger.sql" to "4efffeff39a032a31284b8c7f80fd14b995be69d7ee31f607a02d705c6bfbc27",
            "V015__create_independent_marketplace_economic_evidence.sql" to "671bd0625241e0682d749818a4bbcd56c64e059fa8fb34defa3b605b12f910c2"
        )
    }
}
