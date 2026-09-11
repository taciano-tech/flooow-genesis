package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceObservationId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceReadResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicFact
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderIdentityResolution
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderOccurrencePromotionOutcome
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderOccurrencePromotionWriteResult
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderPromotionIdentifierFactory
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourceKey
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePendingResult
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionBatchResult
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionCandidate
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionContract
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionService
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer

class PostgresMarketplaceOrderSourcePromotionRepositoryTest {
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var repository: PostgresMarketplaceOrderSourcePromotionRepository
    private val ids = AtomicLong(1)
    private val now = Instant.parse("2026-09-06T22:30:00.123456Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @BeforeTest
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
        repository = PostgresMarketplaceOrderSourcePromotionRepository(configuration)
    }

    @AfterTest
    fun stopPostgres() = postgres.stop()

    @Test
    fun `V022 is additive append only and contains no forbidden source payload`() {
        assertEquals(
            "022",
            text(
                "SELECT version FROM flyway_schema_history " +
                    "WHERE version='022' AND success"
            )
        )

        assertEquals(
            setOf(
                "marketplace_order_identity_registry",
                "marketplace_order_occurrence_source_promotion"
            ),
            strings(
                "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema='public' AND table_name IN (" +
                    "'marketplace_order_identity_registry'," +
                    "'marketplace_order_occurrence_source_promotion')"
            ).toSet()
        )

        val columns = strings(
            "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name IN (" +
                "'marketplace_order_identity_registry'," +
                "'marketplace_order_occurrence_source_promotion')"
        ).map(String::lowercase)

        listOf(
            "raw_json",
            "access_token",
            "refresh_token",
            "client_secret",
            "buyer",
            "email",
            "phone",
            "total_amount",
            "paid_amount",
            "sale_fee"
        ).forEach { forbidden ->
            assertFalse(columns.any { it.contains(forbidden) })
        }

        assertEquals(
            setOf(
                "marketplace_order_identity_registry",
                "marketplace_order_occurrence_source_promotion"
            ),
            strings(
                "SELECT event_object_table FROM information_schema.triggers " +
                    "WHERE trigger_name LIKE 'protect_marketplace_order_%_mutation' " +
                    "ORDER BY event_object_table"
            ).toSet()
        )
    }

    @Test
    fun `same external identity across connections converges and currency conflict is explicit`() {
        val org = organization()
        val connectionA = connection(org)
        val connectionB = connection(org)
        val first = source(org, connectionA, 0, "external-1", "BRL", now.minusSeconds(3600))
        val second = source(org, connectionB, 0, "external-1", "BRL", now.minusSeconds(3600))

        val winner = MarketplaceOrderId(uuid())
        val loser = MarketplaceOrderId(uuid())

        val firstResult = assertIs<MarketplaceOrderIdentityResolution.Resolved>(
            repository.resolveOrAllocate(first, winner, now)
        )
        val secondResult = assertIs<MarketplaceOrderIdentityResolution.Resolved>(
            repository.resolveOrAllocate(second, loser, now)
        )

        assertTrue(firstResult.allocatedNow)
        assertFalse(secondResult.allocatedNow)
        assertEquals(firstResult.orderId, secondResult.orderId)
        assertNotEquals(loser, secondResult.orderId)

        val conflictSource = source(
            org,
            connectionB,
            1,
            "external-1",
            "USD",
            now.minusSeconds(3600)
        )
        val conflict = assertIs<MarketplaceOrderIdentityResolution.Conflict>(
            repository.resolveOrAllocate(conflictSource, MarketplaceOrderId(uuid()), now)
        )
        assertEquals(firstResult.orderId, conflict.existingOrderId)

        assertEquals(
            MarketplaceOrderOccurrencePromotionWriteResult.APPLIED,
            repository.markTerminal(
                conflictSource,
                conflict.existingOrderId,
                MarketplaceOrderOccurrencePromotionOutcome.IDENTITY_CONFLICT,
                now
            )
        )
    }

    @Test
    fun `concurrent first allocation converges to one durable internal order id`() {
        val org = organization()
        val connection = connection(org)
        val candidate = source(
            org,
            connection,
            0,
            "concurrent-order",
            "BRL",
            now.minusSeconds(3600)
        )
        val proposedA = MarketplaceOrderId(uuid())
        val proposedB = MarketplaceOrderId(uuid())
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val results = try {
            val futureA = executor.submit(
                Callable {
                    check(start.await(5, TimeUnit.SECONDS)) {
                        "Concurrent identity allocation start barrier timed out"
                    }
                    repository.resolveOrAllocate(candidate, proposedA, now)
                }
            )
            val futureB = executor.submit(
                Callable {
                    check(start.await(5, TimeUnit.SECONDS)) {
                        "Concurrent identity allocation start barrier timed out"
                    }
                    repository.resolveOrAllocate(candidate, proposedB, now)
                }
            )

            start.countDown()

            listOf(
                futureA.get(30, TimeUnit.SECONDS),
                futureB.get(30, TimeUnit.SECONDS)
            )
        } catch (error: TimeoutException) {
            throw AssertionError(
                "Concurrent identity allocation did not converge within 30 seconds",
                error
            )
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS)) {
                "Concurrent identity allocation executor did not terminate"
            }
        }

        val resolved = results.map {
            assertIs<MarketplaceOrderIdentityResolution.Resolved>(it)
        }
        assertEquals(1, resolved.count { it.allocatedNow })
        assertEquals(1, resolved.map { it.orderId }.toSet().size)
    }

    @Test
    fun `pending source work set is ordered and terminal replay is fail closed`() {
        val org = organization()
        val connection = connection(org)
        val second = source(org, connection, 2, "order-2", "BRL", now.minusSeconds(3600))
        val first = source(org, connection, 1, "order-1", "BRL", now.minusSeconds(3700))

        val pending = assertIs<MarketplaceOrderSourcePendingResult.Available>(
            repository.pending(org, connection, 10)
        ).candidates
        assertEquals(listOf(1L, 2L), pending.map { it.sourceKey.inputProgressVersion })

        val identity = assertIs<MarketplaceOrderIdentityResolution.Resolved>(
            repository.resolveOrAllocate(first, MarketplaceOrderId(uuid()), now)
        )

        assertEquals(
            MarketplaceOrderOccurrencePromotionWriteResult.APPLIED,
            repository.markTerminal(
                first,
                identity.orderId,
                MarketplaceOrderOccurrencePromotionOutcome.PROMOTED,
                now
            )
        )
        assertEquals(
            MarketplaceOrderOccurrencePromotionWriteResult.ALREADY_APPLIED,
            repository.markTerminal(
                first,
                identity.orderId,
                MarketplaceOrderOccurrencePromotionOutcome.PROMOTED,
                now.plusSeconds(1)
            )
        )
        assertEquals(
            MarketplaceOrderOccurrencePromotionWriteResult.CONFLICT,
            repository.markTerminal(
                first,
                identity.orderId,
                MarketplaceOrderOccurrencePromotionOutcome.DUPLICATE,
                now.plusSeconds(2)
            )
        )

        val restarted = PostgresMarketplaceOrderSourcePromotionRepository(configuration)
        val remaining = assertIs<MarketplaceOrderSourcePendingResult.Available>(
            restarted.pending(org, connection, 10)
        ).candidates
        assertEquals(listOf(second.sourceKey), remaining.map { it.sourceKey })
    }

    @Test
    fun `real evidence repository promotes duplicate and conflict without financial components`() {
        val org = organization()
        val connection = connection(org)
        val first = source(org, connection, 0, "live-order", "BRL", now.minusSeconds(7200))

        val orderSequence = AtomicLong(1000)
        val observationSequence = AtomicLong(2000)
        val service = MarketplaceOrderSourcePromotionService(
            repository,
            PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration),
            clock,
            MarketplaceOrderPromotionIdentifierFactory {
                MarketplaceOrderId(UUID(0, orderSequence.getAndIncrement()))
            },
            MarketplaceOrderPromotionIdentifierFactory {
                MarketplaceEconomicEvidenceObservationId.parse(
                    UUID(0, observationSequence.getAndIncrement()).toString()
                )
            }
        )

        val promoted = assertIs<MarketplaceOrderSourcePromotionBatchResult.Completed>(
            service.promotePending(org, connection, 10)
        )
        assertEquals(1, promoted.promoted)

        val duplicateSource = source(
            org,
            connection,
            1,
            "live-order",
            "BRL",
            first.dateCreated
        )
        val duplicate = assertIs<MarketplaceOrderSourcePromotionBatchResult.Completed>(
            service.promotePending(org, connection, 10)
        )
        assertEquals(1, duplicate.duplicates)

        val conflictSource = source(
            org,
            connection,
            2,
            "live-order",
            "BRL",
            first.dateCreated.plusSeconds(60)
        )
        val conflict = assertIs<MarketplaceOrderSourcePromotionBatchResult.Completed>(
            service.promotePending(org, connection, 10)
        )
        assertEquals(1, conflict.evidenceConflicts)

        assertEquals(
            listOf("PROMOTED", "DUPLICATE", "EVIDENCE_CONFLICT"),
            strings(
                "SELECT outcome FROM marketplace_order_occurrence_source_promotion " +
                    "WHERE organization_id='${org.value}' " +
                    "ORDER BY source_input_progress_version"
            )
        )

        val orderId = MarketplaceOrderId(
            UUID.fromString(
                text(
                    "SELECT marketplace_order_id::text " +
                        "FROM marketplace_order_identity_registry " +
                        "WHERE organization_id='${org.value}'"
                )
            )
        )
        val subject = MarketplaceEconomicEvidenceSubject(
            org,
            orderId,
            MarketplaceOrderSourcePromotionContract.MARKETPLACE,
            MarketplaceExternalOrderId("live-order"),
            MarketplaceCurrency("BRL")
        )
        val evidence = assertIs<MarketplaceIndependentEconomicEvidenceReadResult.Found>(
            PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration)
                .find(subject)
        ).versionedEvidence.evidence

        assertEquals(1, evidence.activeFacts.size)
        val occurrence = assertIs<MarketplaceIndependentEconomicFact.OrderOccurrence>(
            evidence.activeFacts.single()
        ).observation
        assertEquals(first.dateCreated, occurrence.occurredAt)
        assertEquals(first.observedAt, occurrence.observedAt)
        assertEquals(EconomicSourceKind.MARKETPLACE, occurrence.source.kind)
        assertTrue(evidence.activeFacts.none { it is MarketplaceIndependentEconomicFact.Component })

        assertEquals(
            "live-order",
            text(
                "SELECT external_order_ref " +
                    "FROM integration_mercado_livre_order_source_observation " +
                    "WHERE organization_id='${org.value}' " +
                    "AND input_progress_version=0"
            )
        )

        assertEquals(
            duplicateSource.externalOrderId,
            MarketplaceExternalOrderId("live-order")
        )
        assertEquals(
            conflictSource.externalOrderId,
            MarketplaceExternalOrderId("live-order")
        )
    }

    @Test
    fun `identity and promotion are organization isolated and registry is immutable`() {
        val orgA = organization()
        val orgB = organization()
        val sourceA = source(
            orgA,
            connection(orgA),
            0,
            "shared-external",
            "BRL",
            now.minusSeconds(3600)
        )
        val sourceB = source(
            orgB,
            connection(orgB),
            0,
            "shared-external",
            "BRL",
            now.minusSeconds(3600)
        )

        val a = assertIs<MarketplaceOrderIdentityResolution.Resolved>(
            repository.resolveOrAllocate(sourceA, MarketplaceOrderId(uuid()), now)
        )
        val b = assertIs<MarketplaceOrderIdentityResolution.Resolved>(
            repository.resolveOrAllocate(sourceB, MarketplaceOrderId(uuid()), now)
        )
        assertNotEquals(a.orderId, b.orderId)

        assertFailsWith<SQLException> {
            execute(
                "UPDATE marketplace_order_identity_registry " +
                    "SET currency='USD' WHERE organization_id=?",
                orgA.value
            )
        }
    }

    private fun organization(): OrganizationId {
        val org = OrganizationId(uuid())
        execute(
            "INSERT INTO integration_organization " +
                "(organization_id,status,created_at,updated_at) " +
                "VALUES (?,'ACTIVE',?,?)",
            org.value,
            Timestamp.from(now),
            Timestamp.from(now)
        )
        return org
    }

    private fun connection(org: OrganizationId): IntegrationConnectionId {
        val connection = IntegrationConnectionId(uuid())
        execute(
            "INSERT INTO integration_connection (" +
                "organization_id,connection_id,provider_key,credential_kind,status," +
                "binding_version,created_at,updated_at" +
                ") VALUES (?,?,'br.com.mercadolivre','OAUTH2_AUTHORIZATION_CODE'," +
                "'ACTIVE',1,?,?)",
            org.value,
            connection.value,
            Timestamp.from(now),
            Timestamp.from(now)
        )

        execute(
            "INSERT INTO integration_connector_progress (" +
                "organization_id,connection_id,capability,progress_version," +
                "progress_envelope,exhausted,last_observed_at,updated_at" +
                ") VALUES (?,?,?,1,?,false,?,?)",
            org.value,
            connection.value,
            MarketplaceOrderSourcePromotionContract.CAPABILITY,
            byteArrayOf(1),
            Timestamp.from(now),
            Timestamp.from(now)
        )
        return connection
    }

    private fun source(
        org: OrganizationId,
        connection: IntegrationConnectionId,
        version: Long,
        external: String,
        currency: String,
        dateCreated: Instant
    ): MarketplaceOrderSourcePromotionCandidate {
        val observed = now.plusSeconds(version)
        val key = ByteArray(32) { index ->
            ((version + index + connection.value.leastSignificantBits) and 0xff).toByte()
        }

        execute(
            "INSERT INTO integration_connector_page_commit (" +
                "organization_id,connection_id,capability,input_progress_version," +
                "page_commit_key,record_count,exhausted,observed_at,committed_at" +
                ") VALUES (?,?,?,?,?,1,false,?,?)",
            org.value,
            connection.value,
            MarketplaceOrderSourcePromotionContract.CAPABILITY,
            version,
            key,
            Timestamp.from(observed),
            Timestamp.from(observed)
        )

        execute(
            "INSERT INTO integration_mercado_livre_order_source_observation (" +
                "organization_id,connection_id,capability,input_progress_version," +
                "record_ordinal,external_order_ref,provider_status,date_created," +
                "date_last_updated,date_closed,currency,total_amount,paid_amount," +
                "pack_ref,shipping_ref,observed_at" +
                ") VALUES (?,?,?,?,0,?,'paid',?,?,?, ?,100.00,100.00,NULL,NULL,?)",
            org.value,
            connection.value,
            MarketplaceOrderSourcePromotionContract.CAPABILITY,
            version,
            external,
            Timestamp.from(dateCreated),
            Timestamp.from(observed),
            Timestamp.from(observed),
            currency,
            Timestamp.from(observed)
        )

        return MarketplaceOrderSourcePromotionCandidate(
            MarketplaceOrderSourceKey(
                org,
                connection,
                MarketplaceOrderSourcePromotionContract.CAPABILITY,
                version,
                0
            ),
            MarketplaceExternalOrderId(external),
            MarketplaceCurrency(currency),
            dateCreated,
            observed
        )
    }

    private fun uuid(): UUID = UUID(0, ids.getAndIncrement())

    private fun execute(sql: String, vararg values: Any?) {
        connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                values.forEachIndexed { index, value ->
                    when (value) {
                        is ByteArray -> statement.setBytes(index + 1, value)
                        else -> statement.setObject(index + 1, value)
                    }
                }
                statement.executeUpdate()
            }
        }
    }

    private fun text(sql: String): String = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use {
                check(it.next())
                it.getString(1)
            }
        }
    }

    private fun strings(sql: String): List<String> = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                buildList {
                    while (result.next()) add(result.getString(1))
                }
            }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )
}
class PostgresMarketplaceOrderRevenuePromotionRepositoryTest {
    @kotlin.test.Test
    fun `revenue pending eligibility terminal replay conflict and append only persistence`() {
        val postgres = org.testcontainers.postgresql.PostgreSQLContainer("postgres:18.4")
        postgres.start()

        try {
            val configuration = PostgresConfiguration(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password
            )

            org.flywaydb.core.Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            seedRevenueFixtures(configuration)

            val repository = PostgresMarketplaceOrderSourcePromotionRepository(configuration)
            val organization =
                io.flooow.organization.OrganizationId(java.util.UUID(0, 701))
            val connection =
                io.flooow.integration.control.IntegrationConnectionId(java.util.UUID(0, 702))

            val pending =
                kotlin.test.assertIs<
                    io.flooow.marketplace.operations.economics.promotion
                        .MarketplaceOrderRevenuePendingResult.Available
                >(repository.pendingRevenue(organization, connection, 10))

            kotlin.test.assertEquals(2, pending.candidates.size)
            kotlin.test.assertEquals(
                listOf(1L, 2L),
                pending.candidates.map { it.sourceKey.inputProgressVersion }
            )

            val normal = pending.candidates[0]
            kotlin.test.assertEquals(
                io.flooow.marketplace.operations.economics.MarketplaceCurrency("BRL"),
                normal.sourceCurrency
            )
            kotlin.test.assertEquals(normal.sourceCurrency, normal.identityCurrency)
            kotlin.test.assertEquals(java.math.BigDecimal("123.450000"), normal.totalAmount)
            kotlin.test.assertEquals(
                java.time.Instant.parse("2026-09-06T20:30:00.123456Z"),
                normal.dateClosed
            )

            val mismatch = pending.candidates[1]
            kotlin.test.assertEquals(
                io.flooow.marketplace.operations.economics.MarketplaceCurrency("USD"),
                mismatch.sourceCurrency
            )
            kotlin.test.assertEquals(
                io.flooow.marketplace.operations.economics.MarketplaceCurrency("BRL"),
                mismatch.identityCurrency
            )
            kotlin.test.assertEquals(normal.orderId, mismatch.orderId)

            val promotedAt = java.time.Instant.parse("2026-09-06T22:30:00.000001Z")

            kotlin.test.assertEquals(
                io.flooow.marketplace.operations.economics.promotion
                    .MarketplaceOrderRevenuePromotionWriteResult.APPLIED,
                repository.markRevenueTerminal(
                    normal,
                    io.flooow.marketplace.operations.economics.promotion
                        .MarketplaceOrderRevenuePromotionOutcome.PROMOTED,
                    promotedAt
                )
            )

            kotlin.test.assertEquals(
                io.flooow.marketplace.operations.economics.promotion
                    .MarketplaceOrderRevenuePromotionWriteResult.ALREADY_APPLIED,
                repository.markRevenueTerminal(
                    normal,
                    io.flooow.marketplace.operations.economics.promotion
                        .MarketplaceOrderRevenuePromotionOutcome.PROMOTED,
                    promotedAt.plusSeconds(1)
                )
            )

            kotlin.test.assertEquals(
                io.flooow.marketplace.operations.economics.promotion
                    .MarketplaceOrderRevenuePromotionWriteResult.CONFLICT,
                repository.markRevenueTerminal(
                    normal,
                    io.flooow.marketplace.operations.economics.promotion
                        .MarketplaceOrderRevenuePromotionOutcome.DUPLICATE,
                    promotedAt.plusSeconds(2)
                )
            )

            kotlin.test.assertEquals(
                io.flooow.marketplace.operations.economics.promotion
                    .MarketplaceOrderRevenuePromotionWriteResult.APPLIED,
                repository.markRevenueTerminal(
                    mismatch,
                    io.flooow.marketplace.operations.economics.promotion
                        .MarketplaceOrderRevenuePromotionOutcome.IDENTITY_CONFLICT,
                    promotedAt.plusSeconds(3)
                )
            )

            val afterTerminal =
                kotlin.test.assertIs<
                    io.flooow.marketplace.operations.economics.promotion
                        .MarketplaceOrderRevenuePendingResult.Available
                >(repository.pendingRevenue(organization, connection, 10))
            kotlin.test.assertTrue(afterTerminal.candidates.isEmpty())

            kotlin.test.assertFailsWith<java.sql.SQLException> {
                java.sql.DriverManager.getConnection(
                    configuration.url,
                    configuration.user,
                    configuration.password
                ).use { sql ->
                    sql.createStatement().use {
                        it.executeUpdate(
                            "UPDATE marketplace_order_revenue_source_promotion " +
                                "SET outcome='DUPLICATE'"
                        )
                    }
                }
            }

            java.sql.DriverManager.getConnection(
                configuration.url,
                configuration.user,
                configuration.password
            ).use { sql ->
                sql.prepareStatement(
                    "SELECT COUNT(*) FROM integration_mercado_livre_order_source_observation " +
                        "WHERE external_order_ref='200000000155' AND date_closed IS NULL"
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        kotlin.test.assertTrue(result.next())
                        kotlin.test.assertEquals(1, result.getInt(1))
                    }
                }

                sql.prepareStatement(
                    "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_name='marketplace_order_revenue_source_promotion'"
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        val columns = mutableListOf<String>()
                        while (result.next()) columns += result.getString(1)
                        kotlin.test.assertTrue(
                            columns.none {
                                it.contains("json", ignoreCase = true) ||
                                    it.contains("token", ignoreCase = true) ||
                                    it.contains("secret", ignoreCase = true) ||
                                    it.contains("pii", ignoreCase = true)
                            }
                        )
                    }
                }
            }
        } finally {
            postgres.stop()
        }
    }

    private fun seedRevenueFixtures(configuration: PostgresConfiguration) {
        val organization = java.util.UUID(0, 701)
        val connection = java.util.UUID(0, 702)
        val orderA = java.util.UUID(0, 703)
        val orderB = java.util.UUID(0, 704)

        java.sql.DriverManager.getConnection(
            configuration.url,
            configuration.user,
            configuration.password
        ).use { sql ->
            sql.autoCommit = false
            try {
                fun execute(statement: String) {
                    sql.createStatement().use { it.executeUpdate(statement) }
                }

                execute(
                    "INSERT INTO integration_organization " +
                        "(organization_id,status,created_at,updated_at) VALUES " +
                        "('$organization','ACTIVE'," +
                        "'2026-09-06 18:00:00+00','2026-09-06 18:00:00+00')"
                )

                execute(
                    "INSERT INTO integration_connection " +
                        "(organization_id,connection_id,provider_key,credential_kind,status," +
                        "binding_version,created_at,updated_at) VALUES " +
                        "('$organization','$connection','br.com.mercadolivre'," +
                        "'OAUTH2_AUTHORIZATION_CODE','ACTIVE',1," +
                        "'2026-09-06 18:00:00+00','2026-09-06 18:00:00+00')"
                )

                execute(
                    "INSERT INTO integration_connector_progress " +
                        "(organization_id,connection_id,capability,progress_version," +
                        "progress_envelope,exhausted,last_observed_at,updated_at) VALUES " +
                        "('$organization','$connection','marketplace-economic.order-source',2," +
                        "decode('01','hex'),false," +
                        "'2026-09-06 22:00:00+00','2026-09-06 22:00:00+00')"
                )

                execute(
                    "INSERT INTO integration_connector_page_commit " +
                        "(organization_id,connection_id,capability,input_progress_version," +
                        "page_commit_key,record_count,exhausted,observed_at,committed_at) VALUES " +
                        "('$organization','$connection','marketplace-economic.order-source',1," +
                        "decode(repeat('11',32),'hex'),2,false," +
                        "'2026-09-06 21:00:00+00','2026-09-06 21:00:01+00')"
                )

                execute(
                    "INSERT INTO integration_connector_page_commit " +
                        "(organization_id,connection_id,capability,input_progress_version," +
                        "page_commit_key,record_count,exhausted,observed_at,committed_at) VALUES " +
                        "('$organization','$connection','marketplace-economic.order-source',2," +
                        "decode(repeat('22',32),'hex'),1,false," +
                        "'2026-09-06 22:00:00+00','2026-09-06 22:00:01+00')"
                )

                execute(
                    "INSERT INTO integration_mercado_livre_order_source_observation " +
                        "(organization_id,connection_id,capability,input_progress_version," +
                        "record_ordinal,external_order_ref,provider_status,date_created," +
                        "date_last_updated,date_closed,currency,total_amount,paid_amount," +
                        "pack_ref,shipping_ref,observed_at) VALUES " +
                        "('$organization','$connection','marketplace-economic.order-source',1,0," +
                        "'200000000154','paid','2026-09-06 20:00:00.000000+00'," +
                        "'2026-09-06 20:31:00.000000+00'," +
                        "'2026-09-06 20:30:00.123456+00','BRL',123.450000,123.450000," +
                        "NULL,NULL,'2026-09-06 21:00:00.123456+00')"
                )

                execute(
                    "INSERT INTO integration_mercado_livre_order_source_observation " +
                        "(organization_id,connection_id,capability,input_progress_version," +
                        "record_ordinal,external_order_ref,provider_status,date_created," +
                        "date_last_updated,date_closed,currency,total_amount,paid_amount," +
                        "pack_ref,shipping_ref,observed_at) VALUES " +
                        "('$organization','$connection','marketplace-economic.order-source',1,1," +
                        "'200000000155','confirmed','2026-09-06 20:05:00.000000+00'," +
                        "'2026-09-06 20:10:00.000000+00',NULL,'BRL',88.000000,NULL," +
                        "NULL,NULL,'2026-09-06 21:00:00.223456+00')"
                )

                execute(
                    "INSERT INTO integration_mercado_livre_order_source_observation " +
                        "(organization_id,connection_id,capability,input_progress_version," +
                        "record_ordinal,external_order_ref,provider_status,date_created," +
                        "date_last_updated,date_closed,currency,total_amount,paid_amount," +
                        "pack_ref,shipping_ref,observed_at) VALUES " +
                        "('$organization','$connection','marketplace-economic.order-source',2,0," +
                        "'200000000154','paid','2026-09-06 20:00:00.000000+00'," +
                        "'2026-09-06 21:31:00.000000+00'," +
                        "'2026-09-06 20:30:00.123456+00','USD',123.450000,123.450000," +
                        "NULL,NULL,'2026-09-06 22:00:00.123456+00')"
                )

                execute(
                    "INSERT INTO marketplace_order_identity_registry " +
                        "(organization_id,marketplace_key,external_order_id," +
                        "marketplace_order_id,currency,allocated_at," +
                        "first_source_connection_id,first_source_capability," +
                        "first_source_input_progress_version,first_source_record_ordinal) VALUES " +
                        "('$organization','mercado-livre','200000000154','$orderA','BRL'," +
                        "'2026-09-06 21:00:02+00','$connection'," +
                        "'marketplace-economic.order-source',1,0)"
                )

                execute(
                    "INSERT INTO marketplace_order_identity_registry " +
                        "(organization_id,marketplace_key,external_order_id," +
                        "marketplace_order_id,currency,allocated_at," +
                        "first_source_connection_id,first_source_capability," +
                        "first_source_input_progress_version,first_source_record_ordinal) VALUES " +
                        "('$organization','mercado-livre','200000000155','$orderB','BRL'," +
                        "'2026-09-06 21:00:03+00','$connection'," +
                        "'marketplace-economic.order-source',1,1)"
                )

                sql.commit()
            } catch (error: Exception) {
                sql.rollback()
                throw error
            }
        }
    }
}