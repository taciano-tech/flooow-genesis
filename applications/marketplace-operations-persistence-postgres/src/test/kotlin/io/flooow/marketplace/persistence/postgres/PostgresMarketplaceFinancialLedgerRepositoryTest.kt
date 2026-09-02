package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.economics.EconomicDirection
import io.flooow.marketplace.operations.economics.EconomicExternalReference
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceAbsenceReason
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerAppendResult
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerAppendRequestId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerBasis
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerEntryDraft
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerEntryId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.ledger.FinancialTrace
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceId
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceOpenRequestId
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceOpenResult
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceReadResult
import io.flooow.marketplace.operations.economics.ledger.OpenFinancialTrace
import io.flooow.organization.OrganizationId
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer

class PostgresMarketplaceFinancialLedgerRepositoryTest {
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var repository: PostgresMarketplaceFinancialLedgerRepository
    private val sequence = AtomicLong(1)
    private val brl = MarketplaceCurrency("BRL")
    private val baseTime = Instant.parse("2026-08-13T18:00:00Z")

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure()
            .dataSource(configuration.url, configuration.user, configuration.password)
            .load()
            .migrate()
        repository = PostgresMarketplaceFinancialLedgerRepository(configuration)
    }

    @AfterTest
    fun stopPostgres() = postgres.stop()

    @Test
    fun `trace open is replayable order unique and organization isolated`() {
        val organization = createOrganization()
        val otherOrganization = createOrganization()
        val traceId = traceId()
        val command = openCommand(organization)

        assertEquals(FinancialTraceOpenResult.Opened(traceId), repository.open(command, traceId))
        assertEquals(
            FinancialTraceOpenResult.AlreadyOpen(traceId),
            repository.open(command, FinancialTraceId.of(uuid()))
        )
        assertEquals(
            FinancialTraceOpenResult.Conflict,
            repository.open(command.copy(currency = MarketplaceCurrency("USD")), traceId())
        )
        assertEquals(
            FinancialTraceOpenResult.OrderAlreadyTraced(traceId),
            repository.open(command.copy(requestId = openRequestId()), traceId())
        )

        val otherTrace = traceId()
        assertEquals(
            FinancialTraceOpenResult.Opened(otherTrace),
            repository.open(
                command.copy(organizationId = otherOrganization, requestId = openRequestId()),
                otherTrace
            )
        )
        assertIs<FinancialTraceReadResult.NotFound>(repository.find(otherOrganization, traceId))
        val found = assertIs<FinancialTraceReadResult.Found>(
            repository.findByOrder(organization, command.orderId)
        ).trace
        assertEquals(traceId, found.id)
        assertTrue(found.entries.isEmpty())
    }

    @Test
    fun `acceptance trace preserves expected actual settlement account and bank facts`() {
        val organization = createOrganization()
        val traceId = openedTrace(organization)
        val facts = listOf(
            fact(FinancialLedgerStage.SALE, FinancialLedgerBasis.EXPECTED, "299.90", 1,
                EconomicDirection.ADDITION),
            fact(FinancialLedgerStage.MARKETPLACE_COMMISSION, FinancialLedgerBasis.EXPECTED,
                "41.99", 2),
            fact(FinancialLedgerStage.SHIPPING, FinancialLedgerBasis.EXPECTED, "18.40", 3),
            fact(FinancialLedgerStage.ADVERTISING, FinancialLedgerBasis.EXPECTED, "7.20", 4),
            fact(FinancialLedgerStage.TAX, FinancialLedgerBasis.EXPECTED, "24.30", 5),
            fact(FinancialLedgerStage.PRODUCT_COST, FinancialLedgerBasis.EXPECTED, "143.20", 6),
            fact(FinancialLedgerStage.SETTLEMENT, FinancialLedgerBasis.ACTUAL, "65.31", 7,
                EconomicDirection.ADDITION),
            fact(FinancialLedgerStage.PAYMENT_ACCOUNT, FinancialLedgerBasis.ACTUAL, "65.31", 8,
                EconomicDirection.ADDITION),
            fact(FinancialLedgerStage.BANK, FinancialLedgerBasis.ACTUAL, "65.31", 9,
                EconomicDirection.ADDITION)
        )

        facts.forEach { fixture ->
            assertIs<FinancialLedgerAppendResult.Appended>(
                repository.append(fixture.draft(organization, traceId), fixture.entryId)
            )
        }

        val trace = foundTrace(organization, traceId)
        assertEquals(9, trace.entries.size)
        assertEquals(
            setOf(
                FinancialLedgerStage.SETTLEMENT,
                FinancialLedgerStage.PAYMENT_ACCOUNT,
                FinancialLedgerStage.BANK
            ),
            trace.entries.filter { it.basis == FinancialLedgerBasis.ACTUAL }
                .map { it.stage }.toSet()
        )
        assertEquals(
            listOf("65.31", "65.31", "65.31"),
            trace.entries.filter { it.basis == FinancialLedgerBasis.ACTUAL }
                .map { it.magnitude.amount.toPlainString() }
        )
        assertTrue(trace.entries.all { it.recordedAt.nano % 1_000 == 0 })
    }

    @Test
    fun `append request and external source replay are exact while internal origins coexist`() {
        val organization = createOrganization()
        val traceId = openedTrace(organization)
        val first = fact(FinancialLedgerStage.SHIPPING, FinancialLedgerBasis.ACTUAL, "18.40", 1)
        val draft = first.draft(organization, traceId)

        assertEquals(
            FinancialLedgerAppendResult.Appended(first.entryId),
            repository.append(draft, first.entryId)
        )
        assertEquals(
            FinancialLedgerAppendResult.AlreadyAppended(first.entryId),
            repository.append(draft, FinancialLedgerEntryId.of(uuid()))
        )
        assertEquals(
            FinancialLedgerAppendResult.Conflict,
            repository.append(draft.copy(magnitude = money("18.41")), FinancialLedgerEntryId.of(uuid()))
        )

        val sameSourceNewRequest = draft.copy(requestId = appendRequestId())
        assertEquals(
            FinancialLedgerAppendResult.AlreadyAppended(first.entryId),
            repository.append(sameSourceNewRequest, FinancialLedgerEntryId.of(uuid()))
        )
        assertEquals(
            FinancialLedgerAppendResult.Conflict,
            repository.append(
                sameSourceNewRequest.copy(
                    requestId = appendRequestId(),
                    occurredAt = baseTime.plusSeconds(99)
                ),
                FinancialLedgerEntryId.of(uuid())
            )
        )

        val internalSource = EconomicSource(
            EconomicSourceKind.MANUAL,
            EconomicSourceSystemKey("operations-desk"),
            EconomicExternalReferenceState.Absent(
                EconomicExternalReferenceAbsenceReason.INTERNAL_ORIGIN
            )
        )
        repeat(2) { index ->
            assertIs<FinancialLedgerAppendResult.Appended>(
                repository.append(
                    draft.copy(
                        requestId = appendRequestId(),
                        source = internalSource,
                        occurredAt = baseTime.plusSeconds((200 + index).toLong())
                    ),
                    FinancialLedgerEntryId.of(uuid())
                )
            )
        }
        assertEquals(3, foundTrace(organization, traceId).entries.size)
    }

    @Test
    fun `source facts cannot cross traces but remain isolated by organization`() {
        val organization = createOrganization()
        val firstTrace = openedTrace(organization)
        val secondTrace = openedTrace(organization, orderId = MarketplaceOrderId(uuid()))
        val sourceFact = fact(FinancialLedgerStage.SETTLEMENT, FinancialLedgerBasis.ACTUAL, "65.31", 1)
        assertIs<FinancialLedgerAppendResult.Appended>(
            repository.append(sourceFact.draft(organization, firstTrace), sourceFact.entryId)
        )
        assertEquals(
            FinancialLedgerAppendResult.Conflict,
            repository.append(
                sourceFact.draft(organization, secondTrace).copy(requestId = appendRequestId()),
                FinancialLedgerEntryId.of(uuid())
            )
        )

        val otherOrganization = createOrganization()
        val otherTrace = openedTrace(otherOrganization)
        assertIs<FinancialLedgerAppendResult.Appended>(
            repository.append(
                sourceFact.draft(otherOrganization, otherTrace).copy(requestId = appendRequestId()),
                FinancialLedgerEntryId.of(uuid())
            )
        )
    }

    @Test
    fun `corrections are same trace stage basis linear and preserve complete history`() {
        val organization = createOrganization()
        val traceId = openedTrace(organization)
        val original = fact(FinancialLedgerStage.SHIPPING, FinancialLedgerBasis.ACTUAL, "18.40", 1)
        repository.append(original.draft(organization, traceId), original.entryId)

        val correctionId = FinancialLedgerEntryId.of(uuid())
        val correction = original.draft(organization, traceId).copy(
            requestId = appendRequestId(),
            magnitude = money("18.90"),
            source = source("correction-2"),
            occurredAt = baseTime.plusSeconds(2),
            correctsEntryId = original.entryId
        )
        assertEquals(
            FinancialLedgerAppendResult.Appended(correctionId),
            repository.append(correction, correctionId)
        )
        assertEquals(
            FinancialLedgerAppendResult.Conflict,
            repository.append(
                correction.copy(requestId = appendRequestId(), source = source("correction-3")),
                FinancialLedgerEntryId.of(uuid())
            )
        )

        assertEquals(
            FinancialLedgerAppendResult.CorrectionTargetUnavailable,
            repository.append(
                correction.copy(
                    requestId = appendRequestId(),
                    source = source("correction-4"),
                    correctsEntryId = FinancialLedgerEntryId.of(uuid())
                ),
                FinancialLedgerEntryId.of(uuid())
            )
        )
        assertEquals(
            FinancialLedgerAppendResult.CorrectionTargetUnavailable,
            repository.append(
                correction.copy(
                    requestId = appendRequestId(),
                    source = source("correction-5"),
                    stage = FinancialLedgerStage.TAX
                ),
                FinancialLedgerEntryId.of(uuid())
            )
        )

        val secondCorrectionId = FinancialLedgerEntryId.of(uuid())
        assertEquals(
            FinancialLedgerAppendResult.Appended(secondCorrectionId),
            repository.append(
                correction.copy(
                    requestId = appendRequestId(),
                    source = source("correction-6"),
                    magnitude = money("18.70"),
                    correctsEntryId = correctionId
                ),
                secondCorrectionId
            )
        )
        val entries = foundTrace(organization, traceId).entries
        assertEquals(3, entries.size)
        assertEquals(setOf(original.entryId, correctionId, secondCorrectionId), entries.map { it.id }.toSet())
    }

    @Test
    fun `lifecycle gates writes while historical reads and request replay remain available`() {
        val organization = createOrganization()
        val orderId = MarketplaceOrderId(uuid())
        val traceId = traceId()
        val openCommand = openCommand(organization, orderId)
        assertIs<FinancialTraceOpenResult.Opened>(repository.open(openCommand, traceId))
        val fixture = fact(FinancialLedgerStage.SALE, FinancialLedgerBasis.EXPECTED, "299.90", 1,
            EconomicDirection.ADDITION)
        repository.append(fixture.draft(organization, traceId), fixture.entryId)
        suspendOrganization(organization)

        assertIs<FinancialTraceReadResult.Found>(repository.find(organization, traceId))
        assertEquals(
            FinancialTraceOpenResult.AlreadyOpen(traceId),
            repository.open(openCommand, traceId)
        )
        assertEquals(
            FinancialLedgerAppendResult.AlreadyAppended(fixture.entryId),
            repository.append(fixture.draft(organization, traceId), fixture.entryId)
        )
        assertEquals(
            FinancialLedgerAppendResult.OrganizationUnavailable,
            repository.append(
                fixture.draft(organization, traceId).copy(
                    requestId = appendRequestId(),
                    source = source("new-after-suspension")
                ),
                FinancialLedgerEntryId.of(uuid())
            )
        )
        assertEquals(
            FinancialTraceOpenResult.OrganizationUnavailable,
            repository.open(openCommand(organization, orderId = MarketplaceOrderId(uuid())), traceId())
        )
    }

    @Test
    fun `concurrent open append and direct correction accept exactly one write`() {
        val organization = createOrganization()
        val command = openCommand(organization)
        val traceId = traceId()
        val openResults = concurrent(
            { repository.open(command, traceId) },
            { repository.open(command, traceId) }
        )
        assertEquals(1, openResults.count { it is FinancialTraceOpenResult.Opened })
        assertEquals(1, openResults.count { it is FinancialTraceOpenResult.AlreadyOpen })

        val fixture = fact(FinancialLedgerStage.TAX, FinancialLedgerBasis.ACTUAL, "24.30", 1)
        val draft = fixture.draft(organization, traceId)
        val appendResults = concurrent(
            { repository.append(draft, fixture.entryId) },
            { repository.append(draft, fixture.entryId) }
        )
        assertEquals(1, appendResults.count { it is FinancialLedgerAppendResult.Appended })
        assertEquals(1, appendResults.count { it is FinancialLedgerAppendResult.AlreadyAppended })

        val correctionOne = draft.copy(
            requestId = appendRequestId(),
            source = source("concurrent-correction-1"),
            correctsEntryId = fixture.entryId
        )
        val correctionTwo = correctionOne.copy(
            requestId = appendRequestId(),
            source = source("concurrent-correction-2")
        )
        val correctionResults = concurrent(
            { repository.append(correctionOne, FinancialLedgerEntryId.of(uuid())) },
            { repository.append(correctionTwo, FinancialLedgerEntryId.of(uuid())) }
        )
        assertEquals(1, correctionResults.count { it is FinancialLedgerAppendResult.Appended })
        assertEquals(1, correctionResults.count { it is FinancialLedgerAppendResult.Conflict })
        assertEquals(2, foundTrace(organization, traceId).entries.size)
    }

    @Test
    fun `database ledger is immutable and malformed persisted data fails closed`() {
        val organization = createOrganization()
        val traceId = openedTrace(organization)
        val fixture = fact(FinancialLedgerStage.BANK, FinancialLedgerBasis.ACTUAL, "65.31", 1,
            EconomicDirection.ADDITION)
        repository.append(fixture.draft(organization, traceId), fixture.entryId)
        val tax = fact(FinancialLedgerStage.TAX, FinancialLedgerBasis.ACTUAL, "24.30", 2)
        repository.append(tax.draft(organization, traceId), tax.entryId)

        assertFailsWith<SQLException> {
            sql("UPDATE marketplace_financial_trace SET currency='USD' WHERE organization_id=?") {
                setObject(1, organization.value)
            }
        }
        assertFailsWith<SQLException> {
            sql("DELETE FROM marketplace_financial_ledger_entry WHERE organization_id=?") {
                setObject(1, organization.value)
            }
        }

        DriverManager.getConnection(configuration.url, configuration.user, configuration.password).use { connection ->
            connection.createStatement().use {
                it.execute("ALTER TABLE marketplace_financial_ledger_entry DISABLE TRIGGER USER")
            }
            connection.prepareStatement(
                "UPDATE marketplace_financial_ledger_entry SET corrects_entry_id=? " +
                    "WHERE organization_id=? AND entry_id=?"
            ).use {
                it.setObject(1, tax.entryId.valueForPersistence())
                it.setObject(2, organization.value)
                it.setObject(3, fixture.entryId.valueForPersistence())
                assertEquals(1, it.executeUpdate())
            }
            connection.createStatement().use {
                it.execute("ALTER TABLE marketplace_financial_ledger_entry ENABLE TRIGGER USER")
            }
        }
        assertIs<FinancialTraceReadResult.IntegrityFailure>(repository.find(organization, traceId))
    }

    @Test
    fun `V014 is applied and rejected writes leave no partial rows`() {
        val migrations = queryStrings(
            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"
        )
        assertTrue(migrations.contains("014"))

        val organization = createOrganization()
        val traceId = openedTrace(organization)
        val incompatible = fact(FinancialLedgerStage.SALE, FinancialLedgerBasis.ACTUAL, "10", 1)
            .draft(organization, traceId)
            .copy(magnitude = MarketplaceMoney.parse(MarketplaceCurrency("USD"), "10"))
        assertEquals(
            FinancialLedgerAppendResult.Conflict,
            repository.append(incompatible, FinancialLedgerEntryId.of(uuid()))
        )
        assertEquals(0, count("marketplace_financial_ledger_entry", organization))
    }

    private fun openedTrace(
        organizationId: OrganizationId,
        orderId: MarketplaceOrderId = MarketplaceOrderId(uuid())
    ): FinancialTraceId = traceId().also { id ->
        assertIs<FinancialTraceOpenResult.Opened>(repository.open(openCommand(organizationId, orderId), id))
    }

    private fun openCommand(
        organizationId: OrganizationId,
        orderId: MarketplaceOrderId = MarketplaceOrderId(uuid())
    ) = OpenFinancialTrace(
        organizationId,
        openRequestId(),
        orderId,
        MarketplaceKey("mercado-livre"),
        MarketplaceExternalOrderId("order-${sequence.getAndIncrement()}"),
        brl
    )

    private fun fact(
        stage: FinancialLedgerStage,
        basis: FinancialLedgerBasis,
        amount: String,
        ordinal: Int,
        direction: EconomicDirection = EconomicDirection.DEDUCTION
    ) = FactFixture(
        stage,
        basis,
        direction,
        money(amount),
        source("fact-$ordinal-${sequence.getAndIncrement()}"),
        baseTime.plusSeconds(ordinal.toLong()),
        FinancialLedgerEntryId.of(uuid())
    )

    private fun foundTrace(
        organizationId: OrganizationId,
        traceId: FinancialTraceId
    ): FinancialTrace = assertIs<FinancialTraceReadResult.Found>(
        repository.find(organizationId, traceId)
    ).trace

    private fun source(reference: String) = EconomicSource(
        EconomicSourceKind.MARKETPLACE,
        EconomicSourceSystemKey("meli-br"),
        EconomicExternalReferenceState.Present(EconomicExternalReference(reference))
    )

    private fun money(amount: String) = MarketplaceMoney.parse(brl, amount)

    private fun createOrganization(status: String = "ACTIVE"): OrganizationId =
        OrganizationId(uuid()).also { id ->
            val now = Timestamp.from(baseTime)
            sql(
                "INSERT INTO integration_organization " +
                    "(organization_id,status,created_at,updated_at) VALUES (?,?,?,?)"
            ) {
                setObject(1, id.value)
                setString(2, status)
                setTimestamp(3, now)
                setTimestamp(4, now)
            }
        }

    private fun suspendOrganization(organizationId: OrganizationId) {
        sql("UPDATE integration_organization SET status='SUSPENDED' WHERE organization_id=?") {
            setObject(1, organizationId.value)
        }
    }

    private fun count(table: String, organizationId: OrganizationId): Int =
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password).use { connection ->
            connection.prepareStatement("SELECT count(*) FROM $table WHERE organization_id=?").use {
                it.setObject(1, organizationId.value)
                it.executeQuery().use { result -> result.next(); result.getInt(1) }
            }
        }

    private fun queryStrings(sql: String): List<String> =
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    buildList { while (result.next()) add(result.getString(1)) }
                }
            }
        }

    private fun sql(
        statementSql: String,
        bind: java.sql.PreparedStatement.() -> Unit
    ): Int = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    ).use { connection ->
        connection.prepareStatement(statementSql).use { statement ->
            statement.bind()
            statement.executeUpdate()
        }
    }

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

    private fun uuid(): UUID = UUID(9, sequence.getAndIncrement())
    private fun traceId() = FinancialTraceId.of(uuid())
    private fun openRequestId() = FinancialTraceOpenRequestId.of(uuid())
    private fun appendRequestId() = FinancialLedgerAppendRequestId.of(uuid())

    private data class FactFixture(
        val stage: FinancialLedgerStage,
        val basis: FinancialLedgerBasis,
        val direction: EconomicDirection,
        val magnitude: MarketplaceMoney,
        val source: EconomicSource,
        val occurredAt: Instant,
        val entryId: FinancialLedgerEntryId
    ) {
        fun draft(organizationId: OrganizationId, traceId: FinancialTraceId) =
            FinancialLedgerEntryDraft(
                organizationId,
                FinancialLedgerAppendRequestId.of(UUID(8, entryId.value.leastSignificantBits)),
                traceId,
                stage,
                basis,
                direction,
                magnitude,
                source,
                occurredAt
            )
    }
}
