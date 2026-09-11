package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorCapability
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
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicOrderSourceCapability
import io.flooow.marketplace.operations.economics.provider.MercadoLivreItemReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivreOrderItemSourceObservation
import io.flooow.marketplace.operations.economics.provider.MercadoLivreOrderReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivreOrderSourceRecord
import io.flooow.marketplace.operations.economics.provider.MercadoLivrePackReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivrePaymentReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivrePaymentSourceObservation
import io.flooow.marketplace.operations.economics.provider.MercadoLivreProviderStatus
import io.flooow.marketplace.operations.economics.provider.MercadoLivreShippingReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivreSourceCurrency
import io.flooow.marketplace.operations.economics.provider.ProviderSourceDecimal
import io.flooow.organization.OrganizationId
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.testcontainers.postgresql.PostgreSQLContainer

class PostgresMercadoLivreOrderSourceCommitterTest {
    private val now = Instant.parse("2026-09-06T20:15:30Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var repository: PostgresIntegrationControlPlaneRepository
    private lateinit var vault: MlOrderTestVault
    private lateinit var service: IntegrationControlPlaneService
    private lateinit var protector: MlOrderTestProtector
    private var identifier = 700L

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        )
        repository = PostgresIntegrationControlPlaneRepository.connect(configuration)
        vault = MlOrderTestVault()
        protector = MlOrderTestProtector()
        service = IntegrationControlPlaneService(
            repository,
            vault,
            clock,
            organizationIds = IdentifierFactory { OrganizationId(UUID(0, identifier++)) },
            connectionIds = IdentifierFactory {
                IntegrationConnectionId(UUID(1, identifier++))
            },
            auditIds = IdentifierFactory {
                IntegrationAuditEntryId(UUID(2, identifier++))
            },
            correlationIds = IdentifierFactory { UUID(3, identifier++) }
        )
    }

    @AfterTest
    fun stopPostgres() = postgres.stop()

    @Test
    fun `order item payment observations commit atomically with live progress and survive restart`() {
        val active = activeConnection()
        val connector = MlRecordConnector { _, _ ->
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(record("2000009713473608", "MLB333", "91776699099")),
                    ConnectorProgress.take(
                        "v1|hour=2026-09-06T20:00:00Z|offset=0".toByteArray()
                    ),
                    now,
                    exhausted = false,
                    responseBytes = 100
                )
            )
        }

        val runtime = runtime(active, committer(), connector)
        val outcome = assertIs<ConnectorExecutionOutcome.Success>(
            runtime.execute(invocation(active))
        )
        assertEquals(ConnectorSuccessKind.COMMITTED, outcome.kind)
        assertFalse(outcome.exhausted)

        assertEquals(1, count("integration_mercado_livre_order_source_observation"))
        assertEquals(1, count("integration_mercado_livre_order_item_source_observation"))
        assertEquals(1, count("integration_mercado_livre_payment_source_observation"))
        assertEquals(1, count("integration_connector_page_commit"))
        assertEquals(
            1,
            longValue("SELECT progress_version FROM integration_connector_progress")
        )

        val restarted = committer().load(
            active.first,
            active.second,
            MarketplaceEconomicOrderSourceCapability.KEY
        )
        assertFalse(restarted.exhausted)
        restarted.progress!!.use {
            assertEquals(
                "v1|hour=2026-09-06T20:00:00Z|offset=0",
                it.useBytes(ByteArray::decodeToString)
            )
        }

        assertEquals(
            "2000009713473608",
            textValue(
                "SELECT external_order_ref " +
                    "FROM integration_mercado_livre_order_source_observation"
            )
        )
        assertEquals(
            "125.92",
            decimalValue(
                "SELECT total_amount " +
                    "FROM integration_mercado_livre_order_source_observation"
            )
        )
        assertEquals(
            "11.07",
            decimalValue(
                "SELECT sale_fee " +
                    "FROM integration_mercado_livre_order_item_source_observation"
            )
        )
    }

    @Test
    fun `exact concurrent page replay converges and conflicting replay fails closed`() {
        val same = activeConnection()
        val sameBarrier = CyclicBarrier(2)
        val sameConnector = MlRecordConnector { _, _ ->
            sameBarrier.await()
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(record("same-order", "same-item", "same-payment")),
                    ConnectorProgress.take(
                        "v1|hour=2026-09-06T20:00:00Z|offset=0".toByteArray()
                    ),
                    now,
                    exhausted = false,
                    responseBytes = 50
                )
            )
        }

        val sameOutcomes = concurrentRuns(
            runtime(same, committer(), sameConnector),
            same
        )
        assertEquals(
            setOf(ConnectorSuccessKind.COMMITTED, ConnectorSuccessKind.ALREADY_COMMITTED),
            sameOutcomes.mapNotNull {
                (it as? ConnectorExecutionOutcome.Success)?.kind
            }.toSet()
        )

        val conflicting = activeConnection()
        val conflictBarrier = CyclicBarrier(2)
        val sequence = AtomicInteger()
        val conflictConnector = MlRecordConnector { _, _ ->
            val n = sequence.incrementAndGet()
            conflictBarrier.await()
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(record("order-$n", "item-$n", "payment-$n")),
                    ConnectorProgress.take(
                        "v1|hour=2026-09-06T20:00:00Z|offset=0".toByteArray()
                    ),
                    now,
                    exhausted = false,
                    responseBytes = 50
                )
            )
        }

        val conflictOutcomes = concurrentRuns(
            runtime(conflicting, committer(), conflictConnector),
            conflicting
        )

        assertEquals(
            1,
            conflictOutcomes.count { it is ConnectorExecutionOutcome.Success }
        )
        assertEquals(
            1,
            conflictOutcomes.count {
                it is ConnectorExecutionOutcome.Failure &&
                    it.kind == ConnectorExecutionFailureKind.INTERNAL
            }
        )
    }

    @Test
    fun `source persistence failure rolls back page and progress`() {
        val active = activeConnection()
        execute(
            "CREATE FUNCTION reject_ml_order() RETURNS trigger LANGUAGE plpgsql AS '" +
                "BEGIN RAISE EXCEPTION ''synthetic reject marker''; END'; " +
                "CREATE TRIGGER reject_ml_order BEFORE INSERT ON " +
                "integration_mercado_livre_order_source_observation FOR EACH ROW " +
                "EXECUTE FUNCTION reject_ml_order()"
        )

        val connector = MlRecordConnector { _, _ ->
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(record("rollback-order", "rollback-item", "rollback-payment")),
                    ConnectorProgress.take(
                        "v1|hour=2026-09-06T20:00:00Z|offset=0".toByteArray()
                    ),
                    now,
                    exhausted = false,
                    responseBytes = 20
                )
            )
        }

        val outcome = runtime(active, committer(), connector).execute(invocation(active))
        val failure = assertIs<ConnectorExecutionOutcome.Failure>(outcome)
        assertEquals(ConnectorExecutionFailureKind.INTERNAL, failure.kind)

        assertEquals(0, count("integration_connector_page_commit"))
        assertEquals(0, count("integration_mercado_livre_order_source_observation"))
        assertEquals(0, count("integration_mercado_livre_order_item_source_observation"))
        assertEquals(0, count("integration_mercado_livre_payment_source_observation"))
        assertEquals(0, count("integration_connector_progress"))
        assertFalse(outcome.toString().contains("synthetic reject marker"))
    }

    @Test
    fun `same provider order remains isolated by organization and connection`() {
        val first = activeConnection()
        val second = activeConnection()

        listOf(first, second).forEach { active ->
            val connector = MlRecordConnector { _, _ ->
                ConnectorReadResult.Page(
                    ConnectorPage(
                        listOf(record("same-external-order", "same-item", "same-payment")),
                        ConnectorProgress.take(
                            "v1|hour=2026-09-06T20:00:00Z|offset=0".toByteArray()
                        ),
                        now,
                        exhausted = false,
                        responseBytes = 20
                    )
                )
            }
            assertIs<ConnectorExecutionOutcome.Success>(
                runtime(active, committer(), connector).execute(invocation(active))
            )
        }

        assertEquals(2, count("integration_mercado_livre_order_source_observation"))
        assertEquals(1, countFor(first))
        assertEquals(1, countFor(second))
    }

    private fun concurrentRuns(
        runtime: ConnectorRuntime,
        active: Pair<OrganizationId, IntegrationConnectionId>
    ): List<ConnectorExecutionOutcome> {
        val executor = Executors.newFixedThreadPool(2)
        return try {
            executor.invokeAll(
                listOf(
                    Callable { runtime.execute(invocation(active)) },
                    Callable { runtime.execute(invocation(active)) }
                )
            ).map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun activeConnection(): Pair<OrganizationId, IntegrationConnectionId> {
        val organization = service.createOrganization()
        val connection = service.createConnection(
            organization.id,
            ProviderKey.of("br.com.mercadolivre"),
            CredentialKind.OAUTH2_AUTHORIZATION_CODE
        )

        service.bindInitialCredential(
            organization.id,
            connection.id,
            """
                {
                  "schemaVersion":1,
                  "clientId":"client-test",
                  "clientSecret":"secret-test",
                  "authorizedUserId":8035443,
                  "accessToken":"access-test",
                  "refreshToken":"refresh-test",
                  "accessTokenExpiresAt":"2026-09-06T21:15:30Z"
                }
            """.trimIndent().toByteArray()
        )

        return organization.id to connection.id
    }

    private fun committer() =
        PostgresMercadoLivreOrderSourceCommitter(configuration, protector, clock)

    private fun runtime(
        active: Pair<OrganizationId, IntegrationConnectionId>,
        committer: PostgresMercadoLivreOrderSourceCommitter,
        connector: MlRecordConnector
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
            MarketplaceEconomicOrderSourceCapability.KEY,
            ConnectorInvocationId(UUID.randomUUID()),
            ConnectorBudget(now.plusSeconds(30), 50, 100_000)
        )

    private fun record(order: String, item: String, payment: String) =
        MercadoLivreOrderSourceRecord(
            MercadoLivreOrderReference.of(order),
            MercadoLivreProviderStatus.of("paid"),
            Instant.parse("2026-09-06T19:05:00Z"),
            Instant.parse("2026-09-06T19:07:00Z"),
            Instant.parse("2026-09-06T19:06:00Z"),
            MercadoLivreSourceCurrency.of("BRL"),
            ProviderSourceDecimal.parse("125.92"),
            ProviderSourceDecimal.parse("125.92"),
            MercadoLivrePackReference.of("2000006556183755"),
            MercadoLivreShippingReference.of("46803546483"),
            listOf(
                MercadoLivreOrderItemSourceObservation(
                    MercadoLivreItemReference.of(item),
                    null,
                    ProviderSourceDecimal.parse("1"),
                    ProviderSourceDecimal.parse("62.96"),
                    MercadoLivreSourceCurrency.of("BRL"),
                    ProviderSourceDecimal.parse("11.07"),
                    ProviderSourceDecimal.parse("72.37")
                )
            ),
            listOf(
                MercadoLivrePaymentSourceObservation(
                    MercadoLivrePaymentReference.of(payment),
                    MercadoLivreProviderStatus.of("approved"),
                    ProviderSourceDecimal.parse("125.92"),
                    MercadoLivreSourceCurrency.of("BRL"),
                    Instant.parse("2026-09-06T19:05:30Z"),
                    Instant.parse("2026-09-06T19:06:30Z")
                )
            ),
            now
        )

    private fun count(table: String): Int {
        require(
            table in setOf(
                "integration_connector_progress",
                "integration_connector_page_commit",
                "integration_mercado_livre_order_source_observation",
                "integration_mercado_livre_order_item_source_observation",
                "integration_mercado_livre_payment_source_observation"
            )
        )
        return longValue("SELECT count(*) FROM $table").toInt()
    }

    private fun countFor(active: Pair<OrganizationId, IntegrationConnectionId>): Int =
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT count(*) FROM integration_mercado_livre_order_source_observation " +
                    "WHERE organization_id=? AND connection_id=?"
            ).use { statement ->
                statement.setObject(1, active.first.value)
                statement.setObject(2, active.second.value)
                statement.executeQuery().use {
                    it.next()
                    it.getInt(1)
                }
            }
        }

    private fun longValue(sql: String): Long = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use {
                it.next()
                it.getLong(1)
            }
        }
    }

    private fun textValue(sql: String): String = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use {
                it.next()
                it.getString(1)
            }
        }
    }

    private fun decimalValue(sql: String): String = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use {
                it.next()
                it.getBigDecimal(1).stripTrailingZeros().toPlainString()
            }
        }
    }

    private fun execute(sql: String) = connection().use { connection ->
        connection.createStatement().use { it.execute(sql) }
    }

    private fun connection() = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )
}

private class MlRecordConnector(
    private val reader: (Int, String?) -> ConnectorReadResult
) : PullConnector {
    override val descriptor = ConnectorDescriptor(
        ProviderKey.of("br.com.mercadolivre"),
        listOf(
            ConnectorRecordDefinition(
                MarketplaceEconomicOrderSourceCapability.KEY,
                MercadoLivreOrderSourceRecord::class
            )
        )
    )

    private val calls = AtomicInteger()

    override fun readPage(
        capability: ConnectorCapability,
        credentialBytes: ByteArray,
        currentProgress: ConnectorProgress?,
        budget: ConnectorBudget,
        cancellation: ConnectorCancellation
    ): ConnectorReadResult {
        val decoded = currentProgress?.useBytes(ByteArray::decodeToString)
        return reader(calls.incrementAndGet(), decoded)
    }
}

private class MlOrderTestVault : SecretVault {
    private val stored = mutableMapOf<SecretReference, ByteArray>()
    private var sequence = 0

    override fun store(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        credentialBytes: ByteArray
    ): SecretReference = try {
        SecretReference.of("test-vault://ml-order/${++sequence}").also {
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
        val scoped = requireNotNull(stored[reference]).copyOf()
        return try {
            operation(scoped)
        } finally {
            scoped.fill(0)
        }
    }

    override fun revoke(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference
    ) {
        stored.remove(reference)?.fill(0)
    }
}

private class MlOrderTestProtector : ConnectorProgressProtector {
    override fun seal(
        context: ConnectorProgressProtectionContext,
        plaintextBytes: ByteArray
    ): SealedConnectorProgress {
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
    ): ByteArray = sealedProgress.useBytes { envelope ->
        require(envelope.size >= 33) { "Protected progress unavailable" }
        val tag = envelope.copyOfRange(0, 32)
        val ciphertext = envelope.copyOfRange(32, envelope.size)
        val contextBytes = contextBytes(context)
        val pad = MessageDigest.getInstance("SHA-256").digest(contextBytes)
        val plaintext = ByteArray(ciphertext.size) { index ->
            (ciphertext[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        }
        val expected = MessageDigest.getInstance("SHA-256").digest(contextBytes + plaintext)
        contextBytes.fill(0)
        pad.fill(0)
        ciphertext.fill(0)
        val valid = MessageDigest.isEqual(tag, expected)
        tag.fill(0)
        expected.fill(0)
        require(valid) { "Protected progress unavailable" }
        plaintext
    }

    private fun contextBytes(context: ConnectorProgressProtectionContext): ByteArray =
        (
            context.organizationId.toString() + "|" +
                context.connectionId.value + "|" +
                context.capability.value + "|" +
                context.progressVersion
            ).toByteArray()
}