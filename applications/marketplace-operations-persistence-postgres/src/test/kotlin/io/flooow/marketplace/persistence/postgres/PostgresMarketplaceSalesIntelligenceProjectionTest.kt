package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.economics.EconomicComponentCoverage
import io.flooow.marketplace.operations.economics.EconomicComponentType
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssembler
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssemblyNotReadyReason
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthCalculator
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceOrder
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.ChangeSequenceCheckpoint
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionPage
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionReadResult
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionRecord
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionWriteResult
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceState
import io.flooow.marketplace.operations.economics.sales.calculationPolicyVersionOf
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresMarketplaceSalesIntelligenceProjectionTest {
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var projection: PostgresMarketplaceSalesIntelligenceProjection
    private val baseTime = Instant.parse("2026-09-06T12:00:00.123456Z")

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
            .dataSource(configuration.url, configuration.user, configuration.password)
            .load()
            .migrate()
        projection = PostgresMarketplaceSalesIntelligenceProjection(configuration)
    }

    @AfterAll
    fun stopPostgres() = postgres.stop()

    @BeforeTest
    fun clearData() {
        connection().use { connection ->
            connection.createStatement().use {
                it.execute("TRUNCATE integration_organization CASCADE")
            }
        }
        projection = PostgresMarketplaceSalesIntelligenceProjection(configuration)
    }

    @Test
    fun `V001 through V018 migrate and projection schema freezes closed indexed shape`() {
        connection().use { connection ->
            val versions = connection.prepareStatement(
                "SELECT version FROM flyway_schema_history " +
                    "WHERE success=true AND version IS NOT NULL ORDER BY installed_rank"
            ).use { statement ->
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.getString(1))
                    }
                }
            }
            assertTrue(versions.contains("1") || versions.contains("001"))
            assertTrue(versions.contains("18") || versions.contains("018"))

            val precision = connection.prepareStatement(
                "SELECT datetime_precision FROM information_schema.columns " +
                    "WHERE table_schema='public' " +
                    "AND table_name='marketplace_sales_intelligence_projection' " +
                    "AND column_name='projected_at'"
            ).use { statement ->
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    result.getInt(1)
                }
            }
            assertEquals(6, precision)

            val index = connection.prepareStatement(
                "SELECT indexdef FROM pg_indexes " +
                    "WHERE tablename='marketplace_sales_intelligence_projection' " +
                    "AND indexname='marketplace_sales_intelligence_org_page_idx'"
            ).use { statement ->
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    result.getString(1)
                }
            }
            assertTrue(index.contains("organization_id"))
            assertTrue(index.contains("projected_at DESC"))
            assertTrue(index.contains("marketplace_order_id DESC"))
        }
    }

    @Test
    fun `schema constraints reject inconsistent state shape without durable residue`() {
        val organization = createOrganization(1)
        val order = createSubject(organization, 101)

        assertFailsWith<SQLException> {
            connection().use { connection ->
                connection.prepareStatement(
                    "INSERT INTO marketplace_sales_intelligence_projection (" +
                        "organization_id,marketplace_order_id,source_evidence_version," +
                        "state_kind,assembly_policy_version,calculation_policy_version," +
                        "calculation_kind,state_payload,last_applied_change_sequence,projected_at" +
                        ") VALUES (?,?,1,'UNRESOLVED','marketplace-economic-truth-assembly/1'," +
                        "'marketplace-economic-truth/1','INCOMPLETE','{}'::jsonb,1,?)"
                ).use { statement ->
                    statement.setObject(1, organization.value)
                    statement.setObject(2, order.value)
                    statement.setTimestamp(3, Timestamp.from(baseTime))
                    statement.executeUpdate()
                }
            }
        }

        assertEquals(0, projectionRows(organization, order))
    }

    @Test
    fun `unresolved state round trips with microsecond timestamp and restart equivalence`() {
        val organization = createOrganization(2)
        val order = createSubject(organization, 201)
        val expected = unresolved(organization, order, 5, baseTime)

        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.Applied>(
            projection.materializeIfNewer(expected)
        )

        val firstRead = detail(organization, order)
        assertEquals(expected, firstRead)

        projection = PostgresMarketplaceSalesIntelligenceProjection(configuration)
        val restartedRead = detail(organization, order)
        assertEquals(expected, restartedRead)
        assertEquals(123456000, restartedRead.projectedAt.nano)
    }

    @Test
    fun `calculated state round trips without semantic inference`() {
        val organization = createOrganization(3)
        val orderId = createSubject(organization, 301)
        val calculation = MarketplaceEconomicTruthCalculator.calculate(
            marketplaceOrder(organization, orderId, 301)
        )
        val record = MarketplaceSalesIntelligenceProjectionRecord(
            organizationId = organization,
            marketplaceOrderId = orderId,
            sourceEvidenceVersion = MarketplaceEconomicEvidenceVersion(4),
            state = MarketplaceSalesIntelligenceState.Calculated(
                assemblyPolicyVersion = MarketplaceEconomicTruthAssembler.POLICY_VERSION,
                calculationPolicyVersion = calculationPolicyVersionOf(calculation),
                calculationResult = calculation
            ),
            lastAppliedChangeSequence = ChangeSequenceCheckpoint(4),
            projectedAt = baseTime
        )

        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.Applied>(
            projection.materializeIfNewer(record)
        )

        val durable = detail(organization, orderId)
        val state = assertIs<MarketplaceSalesIntelligenceState.Calculated>(durable.state)
        assertEquals(record.sourceEvidenceVersion, durable.sourceEvidenceVersion)
        assertEquals(record.lastAppliedChangeSequence, durable.lastAppliedChangeSequence)
        assertEquals(record.state, state)
    }

    @Test
    fun `guarded writes apply only strictly newer sequence`() {
        val organization = createOrganization(4)
        val order = createSubject(organization, 401)
        val first = unresolved(organization, order, 5, baseTime)

        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.Applied>(
            projection.materializeIfNewer(first)
        )
        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.NoOpAlreadyCurrent>(
            projection.materializeIfNewer(first.copy(projectedAt = baseTime.plusSeconds(1)))
        )
        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.NoOpAlreadyCurrent>(
            projection.materializeIfNewer(
                first.copy(
                    lastAppliedChangeSequence = ChangeSequenceCheckpoint(4),
                    sourceEvidenceVersion = MarketplaceEconomicEvidenceVersion(4),
                    projectedAt = baseTime.plusSeconds(2)
                )
            )
        )

        val newer = unresolved(
            organization,
            order,
            6,
            baseTime.plusSeconds(3),
            MarketplaceEconomicTruthAssemblyNotReadyReason.ORDER_OCCURRED_AT_CONFLICT
        )
        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.Applied>(
            projection.materializeIfNewer(newer)
        )

        val durable = detail(organization, order)
        assertEquals(ChangeSequenceCheckpoint(6), durable.lastAppliedChangeSequence)
        assertEquals(baseTime.plusSeconds(3), durable.projectedAt)
        assertEquals(
            setOf(MarketplaceEconomicTruthAssemblyNotReadyReason.ORDER_OCCURRED_AT_CONFLICT),
            assertIs<MarketplaceSalesIntelligenceState.Unresolved>(durable.state).reasons
        )
    }

    @Test
    fun `organization scoped uniqueness permits same subject id in different organizations`() {
        val firstOrg = createOrganization(5)
        val secondOrg = createOrganization(6)
        val sharedOrder = MarketplaceOrderId(uuid(777))
        createSubject(firstOrg, sharedOrder, "first-777")
        createSubject(secondOrg, sharedOrder, "second-777")

        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.Applied>(
            projection.materializeIfNewer(
                unresolved(firstOrg, sharedOrder, 1, baseTime)
            )
        )
        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.Applied>(
            projection.materializeIfNewer(
                unresolved(secondOrg, sharedOrder, 1, baseTime.plusSeconds(1))
            )
        )

        assertEquals(firstOrg, detail(firstOrg, sharedOrder).organizationId)
        assertEquals(secondOrg, detail(secondOrg, sharedOrder).organizationId)
        assertEquals(2, totalProjectionRowsForOrder(sharedOrder))
    }

    @Test
    fun `failing foreign key write is atomic and fails closed`() {
        val organization = createOrganization(7)
        val missingOrder = MarketplaceOrderId(uuid(799))

        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.IntegrityFailure>(
            projection.materializeIfNewer(
                unresolved(organization, missingOrder, 1, baseTime)
            )
        )
        assertEquals(0, projectionRows(organization, missingOrder))
    }

    @Test
    fun `concurrent old and new writers converge to newest sequence`() {
        val organization = createOrganization(8)
        val order = createSubject(organization, 801)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val old = executor.submit<MarketplaceSalesIntelligenceProjectionWriteResult> {
                projection.materializeIfNewer(
                    unresolved(organization, order, 10, baseTime)
                )
            }
            val newer = executor.submit<MarketplaceSalesIntelligenceProjectionWriteResult> {
                projection.materializeIfNewer(
                    unresolved(organization, order, 11, baseTime.plusSeconds(1))
                )
            }
            old.get(30, TimeUnit.SECONDS)
            newer.get(30, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertEquals(
            ChangeSequenceCheckpoint(11),
            detail(organization, order).lastAppliedChangeSequence
        )
    }

    @Test
    fun `concurrent duplicate sequence produces at most one material transition`() {
        val organization = createOrganization(9)
        val order = createSubject(organization, 901)
        val record = unresolved(organization, order, 12, baseTime)
        val executor = Executors.newFixedThreadPool(2)
        val outcomes = try {
            listOf(
                executor.submit<MarketplaceSalesIntelligenceProjectionWriteResult> {
                    projection.materializeIfNewer(record)
                },
                executor.submit<MarketplaceSalesIntelligenceProjectionWriteResult> {
                    projection.materializeIfNewer(record)
                }
            ).map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(
            1,
            outcomes.count { it is MarketplaceSalesIntelligenceProjectionWriteResult.Applied }
        )
        assertEquals(
            1,
            outcomes.count {
                it is MarketplaceSalesIntelligenceProjectionWriteResult.NoOpAlreadyCurrent
            }
        )
        assertEquals(ChangeSequenceCheckpoint(12), detail(organization, order).lastAppliedChangeSequence)
    }

    @Test
    fun `malformed durable payload fails closed after restart`() {
        val organization = createOrganization(10)
        val order = createSubject(organization, 1001)
        projection.materializeIfNewer(unresolved(organization, order, 1, baseTime))

        connection().use { connection ->
            connection.prepareStatement(
                "UPDATE marketplace_sales_intelligence_projection " +
                    "SET state_payload='{}'::jsonb WHERE organization_id=? AND marketplace_order_id=?"
            ).use { statement ->
                statement.setObject(1, organization.value)
                statement.setObject(2, order.value)
                assertEquals(1, statement.executeUpdate())
            }
        }

        projection = PostgresMarketplaceSalesIntelligenceProjection(configuration)
        assertIs<MarketplaceSalesIntelligenceProjectionReadResult.IntegrityFailure>(
            projection.detailByOrganizationAndSubject(organization, order)
        )
    }

    @Test
    fun `detail isolation and keyset pagination preserve deterministic organization ordering`() {
        val firstOrg = createOrganization(11)
        val secondOrg = createOrganization(12)
        val firstOrders = (1L..7L).map { createSubject(firstOrg, 1100 + it) }
        val foreign = createSubject(secondOrg, 1299)

        firstOrders.forEachIndexed { index, order ->
            projection.materializeIfNewer(
                unresolved(
                    firstOrg,
                    order,
                    index.toLong() + 1,
                    baseTime.plusSeconds(index.toLong())
                )
            )
        }
        projection.materializeIfNewer(
            unresolved(secondOrg, foreign, 1, baseTime.plusSeconds(20))
        )

        val firstPage = page(firstOrg, null, 3)
        assertEquals(3, firstPage.records.size)
        assertNotNull(firstPage.nextCursor)
        assertTrue(firstPage.records.all { it.organizationId == firstOrg })

        val secondPage = page(firstOrg, firstPage.nextCursor, 3)
        assertEquals(3, secondPage.records.size)
        assertNotNull(secondPage.nextCursor)

        val lastPage = page(firstOrg, secondPage.nextCursor, 3)
        assertEquals(1, lastPage.records.size)
        assertNull(lastPage.nextCursor)

        val all = firstPage.records + secondPage.records + lastPage.records
        assertEquals(firstOrders.toSet(), all.map { it.marketplaceOrderId }.toSet())
        assertEquals(
            all.sortedWith(
                compareByDescending<MarketplaceSalesIntelligenceProjectionRecord> {
                    it.projectedAt
                }.thenByDescending { it.marketplaceOrderId.value }
            ),
            all
        )

        val foreignDetail = assertIs<
            MarketplaceSalesIntelligenceProjectionReadResult.Success<
                MarketplaceSalesIntelligenceProjectionRecord?
            >
        >(
            projection.detailByOrganizationAndSubject(firstOrg, foreign)
        ).value
        assertNull(foreignDetail)
    }

    @Test
    fun `representative volume exercises first middle and reduced last pages with indexed plans`() {
        val organization = createOrganization(13)
        repeat(203) { index ->
            val order = createSubject(organization, 13000L + index)
            projection.materializeIfNewer(
                unresolved(
                    organization,
                    order,
                    index.toLong() + 1,
                    baseTime.plusSeconds(index.toLong())
                )
            )
        }

        val first = page(organization, null, 50)
        assertEquals(50, first.records.size)
        val middle = page(organization, assertNotNull(first.nextCursor), 50)
        assertEquals(50, middle.records.size)

        var cursor = middle.nextCursor
        var last = middle
        while (cursor != null) {
            last = page(organization, cursor, 50)
            cursor = last.nextCursor
        }
        assertEquals(3, last.records.size)
        assertNull(last.nextCursor)

        val duplicate = first.records.first()
        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.NoOpAlreadyCurrent>(
            projection.materializeIfNewer(duplicate)
        )
        assertIs<MarketplaceSalesIntelligenceProjectionWriteResult.Applied>(
            projection.materializeIfNewer(
                duplicate.copy(
                    lastAppliedChangeSequence = ChangeSequenceCheckpoint(10_000),
                    sourceEvidenceVersion = MarketplaceEconomicEvidenceVersion(10_000),
                    projectedAt = baseTime.plusSeconds(10_000)
                )
            )
        )

        connection().use { connection ->
            connection.createStatement().use { it.execute("SET enable_seqscan=off") }

            val detailPlan = explain(
                connection,
                "EXPLAIN SELECT * FROM marketplace_sales_intelligence_projection " +
                    "WHERE organization_id=? AND marketplace_order_id=?",
                organization,
                first.records.first().marketplaceOrderId
            )
            assertTrue(detailPlan.contains("marketplace_sales_intelligence_projection_pkey"))

            val firstListPlan = explain(
                connection,
                "EXPLAIN SELECT * FROM marketplace_sales_intelligence_projection " +
                    "WHERE organization_id=? " +
                    "ORDER BY projected_at DESC, marketplace_order_id DESC LIMIT 50",
                organization,
                null
            )
            assertTrue(firstListPlan.contains("marketplace_sales_intelligence_org_page_idx"))

            val keysetCursor = assertNotNull(first.nextCursor)
            val keysetPlan = connection.prepareStatement(
                "EXPLAIN SELECT * FROM marketplace_sales_intelligence_projection " +
                    "WHERE organization_id=? AND " +
                    "(projected_at<? OR (projected_at=? AND marketplace_order_id<?)) " +
                    "ORDER BY projected_at DESC, marketplace_order_id DESC LIMIT 50"
            ).use { statement ->
                statement.setObject(1, organization.value)
                statement.setTimestamp(2, Timestamp.from(keysetCursor.projectedAt))
                statement.setTimestamp(3, Timestamp.from(keysetCursor.projectedAt))
                statement.setObject(4, keysetCursor.marketplaceOrderId.value)
                statement.executeQuery().use { result ->
                    buildString {
                        while (result.next()) appendLine(result.getString(1))
                    }
                }
            }
            assertTrue(keysetPlan.contains("marketplace_sales_intelligence_org_page_idx"))
        }
    }

    private fun unresolved(
        organizationId: OrganizationId,
        orderId: MarketplaceOrderId,
        sequence: Long,
        projectedAt: Instant,
        reason: MarketplaceEconomicTruthAssemblyNotReadyReason =
            MarketplaceEconomicTruthAssemblyNotReadyReason.ORDER_OCCURRED_AT_UNRESOLVED
    ) = MarketplaceSalesIntelligenceProjectionRecord(
        organizationId = organizationId,
        marketplaceOrderId = orderId,
        sourceEvidenceVersion = MarketplaceEconomicEvidenceVersion(sequence),
        state = MarketplaceSalesIntelligenceState.Unresolved(
            MarketplaceEconomicTruthAssembler.POLICY_VERSION,
            setOf(reason)
        ),
        lastAppliedChangeSequence = ChangeSequenceCheckpoint(sequence),
        projectedAt = projectedAt
    )

    private fun marketplaceOrder(
        organization: OrganizationId,
        orderId: MarketplaceOrderId,
        seed: Long
    ): MarketplaceOrder = MarketplaceOrder(
        organizationId = organization,
        id = orderId,
        marketplace = MarketplaceKey("mercado-livre"),
        externalOrderId = MarketplaceExternalOrderId("order-$seed"),
        occurredAt = baseTime,
        currency = MarketplaceCurrency("BRL"),
        components = emptyList(),
        coverage = EconomicComponentType.entries.associateWith {
            EconomicComponentCoverage.MISSING
        }
    )

    private fun detail(
        organization: OrganizationId,
        order: MarketplaceOrderId
    ): MarketplaceSalesIntelligenceProjectionRecord {
        val result = assertIs<
            MarketplaceSalesIntelligenceProjectionReadResult.Success<
                MarketplaceSalesIntelligenceProjectionRecord?
            >
        >(projection.detailByOrganizationAndSubject(organization, order))
        return assertNotNull(result.value)
    }

    private fun page(
        organization: OrganizationId,
        cursor: io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionCursor?,
        limit: Int
    ): MarketplaceSalesIntelligenceProjectionPage =
        assertIs<
            MarketplaceSalesIntelligenceProjectionReadResult.Success<
                MarketplaceSalesIntelligenceProjectionPage
            >
        >(projection.listByOrganization(organization, cursor, limit)).value

    private fun createOrganization(seed: Long): OrganizationId {
        val organization = OrganizationId(uuid(seed))
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO integration_organization " +
                    "(organization_id,status,created_at,updated_at) VALUES (?,'ACTIVE',?,?)"
            ).use { statement ->
                statement.setObject(1, organization.value)
                statement.setTimestamp(2, Timestamp.from(baseTime))
                statement.setTimestamp(3, Timestamp.from(baseTime))
                statement.executeUpdate()
            }
        }
        return organization
    }

    private fun createSubject(
        organization: OrganizationId,
        seed: Long
    ): MarketplaceOrderId {
        val order = MarketplaceOrderId(uuid(seed))
        createSubject(organization, order, "order-$seed")
        return order
    }

    private fun createSubject(
        organization: OrganizationId,
        order: MarketplaceOrderId,
        externalOrderId: String
    ) {
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO marketplace_economic_evidence_subject " +
                    "(organization_id,marketplace_order_id,marketplace_key,external_order_id,currency) " +
                    "VALUES (?,?,'mercado-livre',?,'BRL')"
            ).use { statement ->
                statement.setObject(1, organization.value)
                statement.setObject(2, order.value)
                statement.setString(3, externalOrderId)
                statement.executeUpdate()
            }
        }
    }

    private fun projectionRows(
        organization: OrganizationId,
        order: MarketplaceOrderId
    ): Int = connection().use { connection ->
        connection.prepareStatement(
            "SELECT count(*) FROM marketplace_sales_intelligence_projection " +
                "WHERE organization_id=? AND marketplace_order_id=?"
        ).use { statement ->
            statement.setObject(1, organization.value)
            statement.setObject(2, order.value)
            statement.executeQuery().use { result ->
                result.next()
                result.getInt(1)
            }
        }
    }

    private fun totalProjectionRowsForOrder(order: MarketplaceOrderId): Int =
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT count(*) FROM marketplace_sales_intelligence_projection " +
                    "WHERE marketplace_order_id=?"
            ).use { statement ->
                statement.setObject(1, order.value)
                statement.executeQuery().use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun explain(
        connection: Connection,
        sql: String,
        organization: OrganizationId,
        order: MarketplaceOrderId?
    ): String = connection.prepareStatement(sql).use { statement ->
        statement.setObject(1, organization.value)
        if (order != null) statement.setObject(2, order.value)
        statement.executeQuery().use { result ->
            buildString {
                while (result.next()) appendLine(result.getString(1))
            }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private fun uuid(value: Long): UUID = UUID(0L, value)
}
