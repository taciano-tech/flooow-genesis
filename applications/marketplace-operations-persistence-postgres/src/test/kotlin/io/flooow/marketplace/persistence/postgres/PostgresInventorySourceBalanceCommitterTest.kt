package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorDescriptor
import io.flooow.integration.connector.ConnectorExecutionFailureKind
import io.flooow.integration.connector.ConnectorExecutionOutcome
import io.flooow.integration.connector.ConnectorInvocation
import io.flooow.integration.connector.ConnectorInvocationId
import io.flooow.integration.connector.ConnectorPage
import io.flooow.integration.connector.ConnectorProgress
import io.flooow.integration.connector.ConnectorProgressProtectionContext
import io.flooow.integration.connector.ConnectorProgressProtector
import io.flooow.integration.connector.ConnectorReadResult
import io.flooow.integration.connector.ConnectorRecordDefinition
import io.flooow.integration.connector.ConnectorRuntime
import io.flooow.integration.connector.ConnectorSuccessKind
import io.flooow.integration.connector.IntegrationControlPlaneConnectorAccess
import io.flooow.integration.connector.PullConnector
import io.flooow.integration.connector.SealedConnectorProgress
import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.IdentifierFactory
import io.flooow.integration.control.IntegrationAuditEntryId
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationControlPlaneService
import io.flooow.integration.control.ProviderKey
import io.flooow.integration.control.SecretReference
import io.flooow.integration.control.SecretVault
import io.flooow.integration.inventory.source.InventorySourceBalanceCapability
import io.flooow.integration.inventory.source.InventorySourceBalanceRecord
import io.flooow.integration.inventory.source.SourceItemReference
import io.flooow.integration.inventory.source.SourceLocationReference
import io.flooow.integration.inventory.source.SourceQuantity
import io.flooow.integration.inventory.source.SourceSku
import io.flooow.integration.inventory.source.SourceUnitCode
import io.flooow.integration.inventory.source.SourceVersion
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresInventorySourceBalanceCommitterTest {
    private val now = Instant.parse("2026-08-12T04:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var repository: PostgresIntegrationControlPlaneRepository
    private lateinit var vault: LedgerTestVault
    private lateinit var service: IntegrationControlPlaneService
    private lateinit var protector: ContextBoundTestProtector
    private var identifier = 1L

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(postgres.jdbcUrl, postgres.username, postgres.password)
        repository = PostgresIntegrationControlPlaneRepository.connect(configuration)
        vault = LedgerTestVault()
        protector = ContextBoundTestProtector()
        service = IntegrationControlPlaneService(
            repository,
            vault,
            clock,
            organizationIds = IdentifierFactory { OrganizationId(UUID(0, identifier++)) },
            connectionIds = IdentifierFactory { IntegrationConnectionId(UUID(1, identifier++)) },
            auditIds = IdentifierFactory { IntegrationAuditEntryId(UUID(2, identifier++)) },
            correlationIds = IdentifierFactory { UUID(3, identifier++) }
        )
    }

    @AfterTest
    fun stopPostgres() = postgres.stop()

    @Test
    fun `pages persist signed source balances and sealed progress then become terminal`() {
        val active = activeConnection()
        val committer = committer()
        assertEquals(0, committer.load(active.first, active.second).version)
        assertEquals(0, count("integration_connector_progress"))

        val connector = LedgerTestConnector { call, progress ->
            if (call == 1) {
                assertNull(progress)
                ConnectorReadResult.Page(
                    ConnectorPage(
                        listOf(
                            balance("remote-item", "warehouse-a", "10.250000"),
                            balance("remote-item-2", null, "-2.500000")
                        ),
                        ConnectorProgress.take("plaintext-progress-marker".toByteArray()),
                        now,
                        exhausted = false,
                        responseBytes = 200
                    )
                )
            } else {
                assertEquals("plaintext-progress-marker", progress)
                ConnectorReadResult.Page(
                    ConnectorPage(
                        listOf(balance("remote-item-3", "warehouse-b", "0")),
                        null,
                        now.plusSeconds(1),
                        exhausted = true,
                        responseBytes = 100
                    )
                )
            }
        }
        val runtime = runtime(active, committer, connector)

        assertEquals(ConnectorSuccessKind.COMMITTED, success(runtime.execute(invocation(active))).kind)
        assertEquals(1, longValue("SELECT progress_version FROM integration_connector_progress"))
        assertFalse(databaseContains("plaintext-progress-marker"))
        assertEquals(2, count("integration_inventory_source_balance"))
        assertEquals("10.250000", decimalAt(0))
        assertEquals("-2.500000", decimalAt(1))
        val loaded = committer.load(active.first, active.second)
        loaded.progress!!.use { progress ->
            assertEquals("plaintext-progress-marker", progress.useBytes(ByteArray::decodeToString))
        }

        assertEquals(ConnectorSuccessKind.COMMITTED, success(runtime.execute(invocation(active))).kind)
        val vaultUsesAfterTerminal = vault.resolutions
        val opensAfterTerminal = protector.opens
        val repeated = success(runtime.execute(invocation(active)))

        assertEquals(ConnectorSuccessKind.ALREADY_COMMITTED, repeated.kind)
        assertTrue(repeated.exhausted)
        assertEquals(2, connector.calls.get())
        assertEquals(vaultUsesAfterTerminal, vault.resolutions)
        assertEquals(opensAfterTerminal, protector.opens)
        assertEquals(2, longValue("SELECT progress_version FROM integration_connector_progress"))
        assertTrue(booleanValue("SELECT exhausted FROM integration_connector_progress"))
        assertNull(bytesValue("SELECT progress_envelope FROM integration_connector_progress"))
        assertEquals(2, count("integration_connector_page_commit"))
        assertEquals(3, count("integration_inventory_source_balance"))
    }

    @Test
    fun `concurrent identical pages append records once`() {
        val active = activeConnection()
        val committer = committer()
        val barrier = CyclicBarrier(2)
        val connector = LedgerTestConnector { _, _ ->
            barrier.await()
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(balance("same-source-item", null, "7")),
                    null,
                    now,
                    exhausted = true,
                    responseBytes = 10
                )
            )
        }
        val runtime = runtime(active, committer, connector)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val outcomes = executor.invokeAll(
                listOf(
                    Callable { runtime.execute(invocation(active)) },
                    Callable { runtime.execute(invocation(active)) }
                )
            ).map { success(it.get()).kind }.toSet()

            assertEquals(
                setOf(ConnectorSuccessKind.COMMITTED, ConnectorSuccessKind.ALREADY_COMMITTED),
                outcomes
            )
            assertEquals(1, count("integration_connector_page_commit"))
            assertEquals(1, count("integration_inventory_source_balance"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `concurrent different pages fail closed on duplicate key metadata disagreement`() {
        val active = activeConnection()
        val committer = committer()
        val barrier = CyclicBarrier(2)
        val pageNumber = AtomicInteger()
        val connector = LedgerTestConnector { _, _ ->
            val current = pageNumber.incrementAndGet()
            barrier.await()
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(balance("different-source-$current", null, current.toString())),
                    null,
                    now.plusSeconds(current.toLong()),
                    exhausted = true,
                    responseBytes = 10
                )
            )
        }
        val runtime = runtime(active, committer, connector)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val outcomes = executor.invokeAll(
                listOf(
                    Callable { runtime.execute(invocation(active)) },
                    Callable { runtime.execute(invocation(active)) }
                )
            ).map { it.get() }

            assertEquals(1, outcomes.count { it is ConnectorExecutionOutcome.Success })
            assertEquals(
                listOf(ConnectorExecutionFailureKind.INTERNAL),
                outcomes.filterIsInstance<ConnectorExecutionOutcome.Failure>().map { it.kind }
            )
            assertEquals(1, count("integration_connector_page_commit"))
            assertEquals(1, count("integration_inventory_source_balance"))
            outcomes.forEach {
                assertFalse(it.toString().contains("different-source"))
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `lifecycle change between read and commit rolls back every effect`() {
        val active = activeConnection()
        val committer = committer()
        val connector = LedgerTestConnector { _, _ ->
            service.suspendConnection(active.first, active.second)
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(balance("blocked-item", null, "1")),
                    null,
                    now,
                    exhausted = true,
                    responseBytes = 1
                )
            )
        }

        val failure = assertIs<ConnectorExecutionOutcome.Failure>(
            runtime(active, committer, connector).execute(invocation(active))
        )

        assertEquals(ConnectorExecutionFailureKind.INTERNAL, failure.kind)
        assertEquals(0, count("integration_connector_progress"))
        assertEquals(0, count("integration_connector_page_commit"))
        assertEquals(0, count("integration_inventory_source_balance"))
        assertFalse(failure.toString().contains("blocked-item"))
    }

    @Test
    fun `database record failure rolls back page and progress`() {
        val active = activeConnection()
        execute(
            "CREATE FUNCTION reject_source_balance() RETURNS trigger LANGUAGE plpgsql AS '" +
                "BEGIN RAISE EXCEPTION ''injected database marker''; END'; " +
                "CREATE TRIGGER reject_source_balance BEFORE INSERT ON " +
                "integration_inventory_source_balance FOR EACH ROW EXECUTE FUNCTION " +
                "reject_source_balance()"
        )
        val connector = LedgerTestConnector { _, _ ->
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(balance("rollback-item", null, "4")),
                    null,
                    now,
                    exhausted = true,
                    responseBytes = 1
                )
            )
        }

        val outcome = runtime(active, committer(), connector).execute(invocation(active))

        assertEquals(
            ConnectorExecutionFailureKind.INTERNAL,
            assertIs<ConnectorExecutionOutcome.Failure>(outcome).kind
        )
        assertEquals(0, count("integration_connector_progress"))
        assertEquals(0, count("integration_connector_page_commit"))
        assertEquals(0, count("integration_inventory_source_balance"))
        assertFalse(outcome.toString().contains("injected database marker"))
    }

    @Test
    fun `source identifiers remain isolated by organization and connection`() {
        val first = activeConnection()
        val second = activeConnection()
        listOf(first, second).forEach { active ->
            val connector = LedgerTestConnector { _, _ ->
                ConnectorReadResult.Page(
                    ConnectorPage(
                        listOf(balance("reused-item", "reused-location", "1.5")),
                        null,
                        now,
                        exhausted = true,
                        responseBytes = 1
                    )
                )
            }
            success(runtime(active, committer(), connector).execute(invocation(active)))
        }

        assertEquals(2, count("integration_inventory_source_balance"))
        assertFails { committer().load(second.first, first.second) }
        assertEquals(1, countFor(first))
        assertEquals(1, countFor(second))
    }

    @Test
    fun `progress protection rejects wrong context and corruption without marker leakage`() {
        val active = activeConnection()
        val context = ConnectorProgressProtectionContext(
            active.first, active.second, InventorySourceBalanceCapability.KEY, 1
        )
        val plaintext = "protector-plaintext-marker".toByteArray()
        val sealed = protector.seal(context, plaintext)
        assertContentEquals("protector-plaintext-marker".toByteArray(), plaintext)
        val opened = protector.open(context, sealed)
        assertEquals("protector-plaintext-marker", opened.decodeToString())
        opened.fill(0)

        val wrong = context.copy(progressVersion = 2)
        val error = assertFails { protector.open(wrong, sealed) }
        assertFalse(error.message.orEmpty().contains("protector-plaintext-marker"))
        val corrupted = sealed.useBytes { it.copyOf().also { bytes -> bytes[0] = (bytes[0] + 1).toByte() } }
        val corruptEnvelope = SealedConnectorProgress.take(corrupted)
        assertFails { protector.open(context, corruptEnvelope) }
        sealed.close()
        corruptEnvelope.close()
        plaintext.fill(0)
    }

    private fun activeConnection(): Pair<OrganizationId, IntegrationConnectionId> {
        val organization = service.createOrganization()
        val connection = service.createConnection(
            organization.id,
            ProviderKey.of("test.inventory-provider"),
            CredentialKind.STATIC_API_CREDENTIAL
        )
        service.bindInitialCredential(
            organization.id,
            connection.id,
            "test-only-credential".toByteArray()
        )
        return organization.id to connection.id
    }

    private fun committer() = PostgresInventorySourceBalanceCommitter(
        configuration, protector, clock
    )

    private fun runtime(
        active: Pair<OrganizationId, IntegrationConnectionId>,
        committer: PostgresInventorySourceBalanceCommitter,
        connector: LedgerTestConnector
    ) = ConnectorRuntime(
        IntegrationControlPlaneConnectorAccess(service),
        listOf(connector),
        listOf(committer),
        clock
    )

    private fun invocation(active: Pair<OrganizationId, IntegrationConnectionId>) =
        ConnectorInvocation(
            active.first,
            active.second,
            InventorySourceBalanceCapability.KEY,
            ConnectorInvocationId(UUID.randomUUID()),
            ConnectorBudget(now.plusSeconds(30), 100, 10_000)
        )

    private fun balance(item: String, location: String?, onHand: String) =
        InventorySourceBalanceRecord(
            SourceItemReference.of(item),
            location?.let(SourceLocationReference::of),
            SourceSku.of("source-sku"),
            SourceUnitCode.of("UN"),
            now,
            SourceVersion.of("version-1"),
            onHand = SourceQuantity.parse(onHand),
            reserved = SourceQuantity.parse("0.125")
        )

    private fun success(outcome: ConnectorExecutionOutcome) =
        assertIs<ConnectorExecutionOutcome.Success>(outcome)

    private fun count(table: String): Int {
        require(table in setOf(
            "integration_connector_progress",
            "integration_connector_page_commit",
            "integration_inventory_source_balance"
        ))
        return longValue("SELECT count(*) FROM $table").toInt()
    }

    private fun countFor(active: Pair<OrganizationId, IntegrationConnectionId>): Int =
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT count(*) FROM integration_inventory_source_balance " +
                    "WHERE organization_id=? AND connection_id=?"
            ).use { statement ->
                statement.setObject(1, active.first.value)
                statement.setObject(2, active.second.value)
                statement.executeQuery().use { it.next(); it.getInt(1) }
            }
        }

    private fun decimalAt(ordinal: Int): String = connection().use { connection ->
        connection.prepareStatement(
            "SELECT on_hand FROM integration_inventory_source_balance WHERE record_ordinal=? " +
                "ORDER BY input_progress_version LIMIT 1"
        ).use { statement ->
            statement.setInt(1, ordinal)
            statement.executeQuery().use { it.next(); it.getBigDecimal(1).toPlainString() }
        }
    }

    private fun databaseContains(marker: String): Boolean = connection().use { connection ->
        connection.prepareStatement(
            "SELECT position(?::bytea in progress_envelope) > 0 " +
                "FROM integration_connector_progress"
        ).use { statement ->
            statement.setBytes(1, marker.toByteArray())
            statement.executeQuery().use { it.next() && it.getBoolean(1) }
        }
    }

    private fun longValue(sql: String): Long = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { it.next(); it.getLong(1) }
        }
    }

    private fun booleanValue(sql: String): Boolean = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { it.next(); it.getBoolean(1) }
        }
    }

    private fun bytesValue(sql: String): ByteArray? = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { it.next(); it.getBytes(1) }
        }
    }

    private fun execute(sql: String) = connection().use { connection ->
        connection.createStatement().use { it.execute(sql) }
    }

    private fun connection() = DriverManager.getConnection(
        configuration.url, configuration.user, configuration.password
    )
}

private class LedgerTestConnector(
    private val reader: (Int, String?) -> ConnectorReadResult
) : PullConnector {
    override val descriptor = ConnectorDescriptor(
        ProviderKey.of("test.inventory-provider"),
        listOf(
            ConnectorRecordDefinition(
                InventorySourceBalanceCapability.KEY,
                InventorySourceBalanceRecord::class
            )
        )
    )
    val calls = AtomicInteger()

    override fun readPage(
        capability: ConnectorCapability,
        credentialBytes: ByteArray,
        currentProgress: ConnectorProgress?,
        budget: ConnectorBudget,
        cancellation: ConnectorCancellation
    ): ConnectorReadResult {
        val progress = currentProgress?.useBytes(ByteArray::decodeToString)
        return reader(calls.incrementAndGet(), progress)
    }
}

private class LedgerTestVault : SecretVault {
    private val stored = mutableMapOf<SecretReference, ByteArray>()
    private var sequence = 0
    var resolutions = 0

    override fun store(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        credentialBytes: ByteArray
    ): SecretReference = try {
        SecretReference.of("test-vault://${++sequence}").also {
            stored[it] = credentialBytes.copyOf()
        }
    } finally {
        credentialBytes.fill(0)
    }

    override fun <T> withSecret(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference,
        operation: (ByteArray) -> T
    ): T {
        resolutions++
        val scoped = requireNotNull(stored[reference]).copyOf()
        return try { operation(scoped) } finally { scoped.fill(0) }
    }

    override fun revoke(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference
    ) {
        stored.remove(reference)?.fill(0)
    }
}

private class ContextBoundTestProtector : ConnectorProgressProtector {
    var seals = 0
    var opens = 0

    override fun seal(
        context: ConnectorProgressProtectionContext,
        plaintextBytes: ByteArray
    ): SealedConnectorProgress {
        seals++
        val contextBytes = contextBytes(context)
        val pad = MessageDigest.getInstance("SHA-256").digest(contextBytes)
        val ciphertext = ByteArray(plaintextBytes.size) { index ->
            (plaintextBytes[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        }
        val tag = MessageDigest.getInstance("SHA-256").digest(contextBytes + plaintextBytes)
        contextBytes.fill(0)
        pad.fill(0)
        val envelope = tag + ciphertext
        tag.fill(0)
        ciphertext.fill(0)
        return SealedConnectorProgress.take(envelope)
    }

    override fun open(
        context: ConnectorProgressProtectionContext,
        sealedProgress: SealedConnectorProgress
    ): ByteArray {
        opens++
        return sealedProgress.useBytes { envelope ->
            require(envelope.size >= 33) { "Protected progress unavailable" }
            val contextBytes = contextBytes(context)
            val pad = MessageDigest.getInstance("SHA-256").digest(contextBytes)
            val ciphertext = envelope.copyOfRange(32, envelope.size)
            val plaintext = ByteArray(ciphertext.size) { index ->
                (ciphertext[index].toInt() xor pad[index % pad.size].toInt()).toByte()
            }
            val expected = MessageDigest.getInstance("SHA-256").digest(contextBytes + plaintext)
            val actual = envelope.copyOfRange(0, 32)
            contextBytes.fill(0)
            pad.fill(0)
            ciphertext.fill(0)
            val valid = MessageDigest.isEqual(expected, actual)
            expected.fill(0)
            actual.fill(0)
            if (!valid) {
                plaintext.fill(0)
                error("Protected progress unavailable")
            }
            plaintext
        }
    }

    private fun contextBytes(context: ConnectorProgressProtectionContext): ByteArray =
        listOf(
            context.organizationId.toString(),
            context.connectionId.value.toString(),
            context.capability.value,
            context.progressVersion.toString()
        ).joinToString("\n").toByteArray(StandardCharsets.UTF_8)
}

private fun PostgresInventorySourceBalanceCommitter.load(
    organizationId: OrganizationId,
    connectionId: IntegrationConnectionId
) = load(organizationId, connectionId, InventorySourceBalanceCapability.KEY)
