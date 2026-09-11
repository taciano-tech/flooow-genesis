package io.flooow.research.exp0006

import io.flooow.marketplace.persistence.postgres.PostgresConfiguration
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExperimentalPendingDiscoveryComparisonTest {
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var feed: ExperimentalPostgresChangeFeed
    private lateinit var queries: ExperimentalPendingDiscoveryQueries
    private val organizations = mutableListOf<OrganizationId>()
    private val createdAt = Instant.parse("2026-09-02T15:00:00.123456Z")

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
        queries = ExperimentalPendingDiscoveryQueries(configuration)
    }

    @AfterAll
    fun stopPostgres() = postgres.stop()

    @Test
    fun `queries A B C remain equivalent across three scales and expose natural plans`() {
        val scales = listOf(
            Scale(number = 1, organizationCount = 40, minimumChanges = 25, spread = 51),
            Scale(number = 2, organizationCount = 80, minimumChanges = 800, spread = 401),
            Scale(number = 3, organizationCount = 120, minimumChanges = 2_000, spread = 1_001)
        )

        scales.forEach { scale ->
            addScale(scale)
            val projection = ExperimentalProjectionName("comparison-scale-${scale.number}")
            val secondaryProjection = ExperimentalProjectionName("secondary-scale-${scale.number}")
            val noCheckpointProjection = ExperimentalProjectionName("none-scale-${scale.number}")
            installMixedCheckpoints(projection, scale.number)
            installSecondaryCheckpoints(secondaryProjection)
            analyze()

            assertEquivalent(noCheckpointProjection, 10_000)
            assertEquivalent(projection, 10_000)
            assertEquivalent(secondaryProjection, 10_000)
            assertEquivalent(projection, 7)

            val firstBatch = queries.queryA(projection, 5)
            assertTrue(pendingCount(projection) > firstBatch.size)
            val repeatedBatches = List(4) { queries.queryA(projection, 5) }
            assertTrue(repeatedBatches.all { it == firstBatch })
            println("EXP-0006 SCALE ${scale.number} STARVATION OBSERVED")

            val metrics = metrics(projection, 1_000)
            println("EXP-0006 SCALE ${scale.number} METRICS $metrics")
            printPlan(scale.number, "A", queries.explainA(projection, 1_000))
            printPlan(scale.number, "B", queries.explainB(projection, 1_000))
            printPlan(scale.number, "C", queries.explainC(projection, 1_000))
        }
    }

    private fun assertEquivalent(projectionName: ExperimentalProjectionName, limit: Int) {
        val a = queries.queryA(projectionName, limit)
        val b = queries.queryB(projectionName, limit)
        val c = queries.queryC(projectionName, limit)
        assertEquals(a, b, "Query B diverged from Query A")
        assertEquals(a, c, "Query C diverged from Query A")
    }

    private fun installMixedCheckpoints(projectionName: ExperimentalProjectionName, scale: Int) {
        organizations.forEachIndexed { index, organization ->
            val maximum = maximumSequence(organization)
            when (index % 4) {
                0 -> advance(organization, projectionName, maximum)
                1 -> advance(organization, projectionName, maximum - 1)
            }
        }
        assertEquivalent(projectionName, 10_000)
        println("EXP-0006 SCALE $scale MIXED CHECKPOINTS INSTALLED")
    }

    private fun installSecondaryCheckpoints(projectionName: ExperimentalProjectionName) {
        organizations.forEachIndexed { index, organization ->
            if (index % 3 == 0) advance(organization, projectionName, maximumSequence(organization))
        }
    }

    private fun advance(
        organization: OrganizationId,
        projectionName: ExperimentalProjectionName,
        next: Long
    ) {
        val result = feed.advanceCheckpoint(
            organization,
            projectionName,
            ExperimentalChangeSequenceCheckpoint.NONE,
            ExperimentalChangeSequenceCheckpoint(next)
        )
        assertTrue(result is ExperimentalCheckpointAdvanceResult.Advanced)
    }

    private fun addScale(scale: Scale) {
        repeat(scale.organizationCount) { ordinal ->
            val organization = createOrganization()
            organizations += organization
            val changes = scale.minimumChanges + (ordinal % scale.spread)
            insertValidSingleVersionAggregates(organization, scale.number, changes)
        }
    }

    private fun insertValidSingleVersionAggregates(
        organization: OrganizationId,
        scale: Int,
        changes: Int
    ) = transaction { connection ->
        val organizationText = organization.value.toString()
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_subject " +
                "(organization_id,marketplace_order_id,marketplace_key,external_order_id,currency) " +
                "SELECT ?::uuid,md5(? || ':order:' || g::text)::uuid,'mercado-livre'," +
                "'exp-scale-$scale-' || g::text,'BRL' " +
                "FROM generate_series(1,?) g"
        ).use { statement ->
            statement.setString(1, organizationText)
            statement.setString(2, organizationText)
            statement.setInt(3, changes)
            assertEquals(changes, statement.executeUpdate())
        }
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_update " +
                "(organization_id,marketplace_order_id,evidence_version,update_id,change_kind) " +
                "SELECT ?::uuid,md5(? || ':order:' || g::text)::uuid,1," +
                "md5(? || ':update:' || g::text)::uuid,'ATTEMPT' " +
                "FROM generate_series(1,?) g"
        ).use { statement ->
            statement.setString(1, organizationText)
            statement.setString(2, organizationText)
            statement.setString(3, organizationText)
            statement.setInt(4, changes)
            assertEquals(changes, statement.executeUpdate())
        }
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_identifier " +
                "(organization_id,marketplace_order_id,observation_id,evidence_version,identifier_kind) " +
                "SELECT ?::uuid,md5(? || ':order:' || g::text)::uuid," +
                "md5(? || ':update:' || g::text)::uuid,1,'ATTEMPT' " +
                "FROM generate_series(1,?) g"
        ).use { statement ->
            statement.setString(1, organizationText)
            statement.setString(2, organizationText)
            statement.setString(3, organizationText)
            statement.setInt(4, changes)
            assertEquals(changes, statement.executeUpdate())
        }
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_collection_attempt " +
                "(organization_id,marketplace_order_id,attempt_id,evidence_version," +
                "family,source_system_key,outcome,attempted_at) " +
                "SELECT ?::uuid,md5(? || ':order:' || g::text)::uuid," +
                "md5(? || ':update:' || g::text)::uuid,1,'MARKETPLACE_ORDER'," +
                "'exp-0006','NO_EVIDENCE',? FROM generate_series(1,?) g"
        ).use { statement ->
            statement.setString(1, organizationText)
            statement.setString(2, organizationText)
            statement.setString(3, organizationText)
            statement.setTimestamp(4, Timestamp.from(createdAt))
            statement.setInt(5, changes)
            assertEquals(changes, statement.executeUpdate())
        }
        connection.prepareStatement(
            "UPDATE marketplace_economic_evidence_subject SET current_version=1 " +
                "WHERE organization_id=? AND current_version=0"
        ).use { statement ->
            statement.setObject(1, organization.value)
            assertEquals(changes, statement.executeUpdate())
        }
    }

    private fun metrics(projectionName: ExperimentalProjectionName, limit: Int): ScaleMetrics =
        connection().use { connection ->
            val distribution = connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT count(*) AS organizations,sum(changes) AS journal_rows," +
                        "min(changes) AS minimum_changes,avg(changes)::numeric(20,6) AS average_changes," +
                        "max(changes) AS maximum_changes FROM (" +
                        "SELECT organization_id,count(*) AS changes " +
                        "FROM marketplace_economic_evidence_update GROUP BY organization_id) d"
                ).use { result ->
                    result.next()
                    Distribution(
                        result.getInt("organizations"),
                        result.getLong("journal_rows"),
                        result.getLong("minimum_changes"),
                        result.getBigDecimal("average_changes").toPlainString(),
                        result.getLong("maximum_changes")
                    )
                }
            }
            val checkpointRows = scalar(
                connection,
                "SELECT count(*) FROM projection_checkpoint"
            )
            val projectionCheckpointRows = scalar(
                connection,
                "SELECT count(*) FROM projection_checkpoint WHERE projection_name=?",
                projectionName.valueForPersistence()
            )
            val pending = queries.queryA(projectionName, limit).size
            ScaleMetrics(
                distribution.journalRows,
                distribution.organizations,
                checkpointRows,
                projectionCheckpointRows,
                pending,
                distribution.organizations - pending,
                distribution.minimum,
                distribution.average,
                distribution.maximum,
                limit,
                projectionName.valueForPersistence()
            )
        }

    private fun pendingCount(projectionName: ExperimentalProjectionName): Int =
        queries.queryA(projectionName, 10_000).size

    private fun maximumSequence(organization: OrganizationId): Long = connection().use { connection ->
        connection.prepareStatement(
            "SELECT max(change_sequence) FROM marketplace_economic_evidence_update WHERE organization_id=?"
        ).use { statement ->
            statement.setObject(1, organization.value)
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }
    }

    private fun analyze() = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("ANALYZE marketplace_economic_evidence_update")
            statement.execute("ANALYZE projection_checkpoint")
            statement.execute("ANALYZE integration_organization")
        }
    }

    private fun printPlan(scale: Int, query: String, plan: List<String>) {
        println("EXP-0006 SCALE $scale QUERY $query EXPLAIN BEGIN")
        plan.forEach(::println)
        println("EXP-0006 SCALE $scale QUERY $query EXPLAIN END")
    }

    private fun createOrganization(): OrganizationId = OrganizationId(UUID.randomUUID()).also { organization ->
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO integration_organization " +
                    "(organization_id,status,created_at,updated_at) VALUES (?,'ACTIVE',?,?)"
            ).use { statement ->
                statement.setObject(1, organization.value)
                statement.setTimestamp(2, Timestamp.from(createdAt))
                statement.setTimestamp(3, Timestamp.from(createdAt))
                assertEquals(1, statement.executeUpdate())
            }
        }
    }

    private fun scalar(connection: Connection, sql: String, value: String? = null): Int =
        connection.prepareStatement(sql).use { statement ->
            if (value != null) statement.setString(1, value)
            statement.executeQuery().use { result -> result.next(); result.getInt(1) }
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

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private data class Scale(
        val number: Int,
        val organizationCount: Int,
        val minimumChanges: Int,
        val spread: Int
    )

    private data class Distribution(
        val organizations: Int,
        val journalRows: Long,
        val minimum: Long,
        val average: String,
        val maximum: Long
    )

    private data class ScaleMetrics(
        val journalRows: Long,
        val organizations: Int,
        val checkpointRows: Int,
        val projectionCheckpointRows: Int,
        val pending: Int,
        val nonPending: Int,
        val minimumChanges: Long,
        val averageChanges: String,
        val maximumChanges: Long,
        val limit: Int,
        val projectionName: String
    )
}
