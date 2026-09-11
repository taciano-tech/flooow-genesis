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
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicProductCostCapability
import io.flooow.marketplace.operations.economics.provider.OmieDisplayedProductCode
import io.flooow.marketplace.operations.economics.provider.OmieIntegrationReference
import io.flooow.marketplace.operations.economics.provider.OmieLocationReference
import io.flooow.marketplace.operations.economics.provider.OmieProductCostSourceRecord
import io.flooow.marketplace.operations.economics.provider.OmieProductReference
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceCapability
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceRecord
import io.flooow.marketplace.operations.economics.provider.OmieTransactionProductIdentifier
import io.flooow.marketplace.operations.economics.provider.OmieTransactionProductIdentifierKind
import io.flooow.marketplace.operations.economics.provider.OmieTransactionProductObservation
import io.flooow.marketplace.operations.economics.provider.OmieOrderReference
import io.flooow.marketplace.operations.economics.provider.OmieCustomerOrderReference
import io.flooow.marketplace.operations.economics.provider.ProviderSourceDecimal
import io.flooow.marketplace.operations.identity.CommerceIdentityBridge
import io.flooow.marketplace.operations.identity.CommerceIdentityMatchState
import io.flooow.marketplace.operations.identity.CommerceIdentityPolicy
import io.flooow.marketplace.operations.identity.ExplicitMarketplaceOrderReferenceResolver
import io.flooow.marketplace.operations.identity.MercadoLivreTransactionEvidence
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PostgresOmieProductCostCommitterTest {
    private val now = Instant.parse("2026-09-06T14:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var repository: PostgresIntegrationControlPlaneRepository
    private lateinit var vault: OmieTestVault
    private lateinit var service: IntegrationControlPlaneService
    private lateinit var protector: OmieTestProtector
    private var identifier = 100L

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(postgres.jdbcUrl, postgres.username, postgres.password)
        repository = PostgresIntegrationControlPlaneRepository.connect(configuration)
        vault = OmieTestVault()
        protector = OmieTestProtector()
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
    fun `provider observations persist exactly before durable progress advances`() {
        val active = activeConnection()
        val committer = committer()
        val connector = OmieRecordConnector { call, progress ->
            if (call == 1) {
                assertNull(progress)
                ConnectorReadResult.Page(
                    ConnectorPage(
                        listOf(record("3415304571", "3415174133", "21.817094")),
                        ConnectorProgress.take("page=2".toByteArray()),
                        now,
                        exhausted = false,
                        responseBytes = 100
                    )
                )
            } else {
                assertEquals("page=2", progress)
                ConnectorReadResult.Page(
                    ConnectorPage(
                        listOf(record("3415304571", "3426146349", "22.092222")),
                        null,
                        now,
                        exhausted = true,
                        responseBytes = 100
                    )
                )
            }
        }

        val runtime = runtime(active, committer, connector)
        assertEquals(ConnectorSuccessKind.COMMITTED, success(runtime.execute(invocation(active))).kind)
        assertEquals(1, longValue("SELECT progress_version FROM integration_connector_progress"))
        assertEquals(1, count("integration_omie_product_cost_source_observation"))
        assertEquals("21.817094", decimalAt(0))
        assertFalse(databaseContains("page=2"))

        val loaded = committer.load(
            active.first,
            active.second,
            MarketplaceEconomicProductCostCapability.KEY
        )
        loaded.progress!!.use {
            assertEquals("page=2", it.useBytes(ByteArray::decodeToString))
        }

        assertEquals(ConnectorSuccessKind.COMMITTED, success(runtime.execute(invocation(active))).kind)
        val repeated = success(runtime.execute(invocation(active)))
        assertEquals(ConnectorSuccessKind.ALREADY_COMMITTED, repeated.kind)
        assertTrue(repeated.exhausted)
        assertEquals(2, count("integration_omie_product_cost_source_observation"))
        assertEquals(2, count("integration_connector_page_commit"))
        assertEquals(2, longValue("SELECT progress_version FROM integration_connector_progress"))
        assertEquals(2, connector.callCount)
    }

    @Test
    fun `catalog reader aggregates repeated locations and preserves identity contradictions`() {
        val active = activeConnection()
        val connector = OmieRecordConnector { _, _ ->
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(
                        record("product-1", "location-a", "10", integration = "INT-OLD"),
                        record("product-1", "location-b", "11", integration = "INT-NEW")
                    ),
                    null,
                    now,
                    exhausted = true,
                    responseBytes = 100
                )
            )
        }
        assertEquals(
            ConnectorSuccessKind.COMMITTED,
            success(runtime(active, committer(), connector).execute(invocation(active))).kind
        )

        val reader = PostgresOmieProductCatalogEvidenceReader(configuration, active.second)
        val read = reader.read(active.first, active.second.value.toString(), 100)

        assertEquals(2, read.persistedRows)
        assertEquals(1, read.products.size)
        assertEquals(setOf("INT-NEW", "INT-OLD"), read.products.single().integrationProductCodes)
        assertEquals(
            io.flooow.marketplace.operations.identity.OmieCatalogIdentityState.CONFLICT,
            read.products.single().identityState
        )
        assertFailsWith<IllegalArgumentException> {
            reader.read(active.first, UUID(9, 9).toString(), 100)
        }
    }

    @Test
    fun `synthetic durable Omie reader and transaction resolver path remains proven`() {
        val active = activeConnection()
        val source = OmieTransactionEvidenceRecord(
            OmieOrderReference.of("SYNTH-OMIE-ORDER"),
            null,
            OmieCustomerOrderReference.of("#SYNTH-ML-ORDER"),
            now,
            null,
            null,
            null,
            listOf(
                OmieTransactionProductObservation(
                    OmieTransactionProductIdentifier.of(
                        OmieTransactionProductIdentifierKind.INTEGRATION_PRODUCT_CODE,
                        "SYNTH-SKU"
                    ),
                    ProviderSourceDecimal.parse("1")
                )
            ),
            now,
            "synthetic-fingerprint"
        )
        val connector = SyntheticOmieTransactionConnector(source)
        val committer = PostgresOmieTransactionEvidenceCommitter(configuration, protector, clock)
        val runtime = ConnectorRuntime(
            IntegrationControlPlaneConnectorAccess(service), listOf(connector), listOf(committer), clock
        )
        val invocation = ConnectorInvocation(
            active.first, active.second, OmieTransactionEvidenceCapability.KEY,
            ConnectorInvocationId(UUID.randomUUID()), ConnectorBudget(now.plusSeconds(30), 100, 100_000)
        )
        assertEquals(ConnectorSuccessKind.COMMITTED, success(runtime.execute(invocation)).kind)

        val observed = PostgresOmieIdentityEvidenceReader(configuration, active.second)
            .read(active.first, 100).records.single()
        val ml = MercadoLivreTransactionEvidence(
            active.first, "SYNTH-ML-ORDER", null, null, emptySet(), setOf("SYNTH-SKU"),
            mapOf("SYNTH-SKU" to BigDecimal.ONE), null, now, setOf("synthetic:ml")
        )
        val declared = observed.copy(
            declaredMarketplaceOrderIds = ExplicitMarketplaceOrderReferenceResolver.resolve(
                setOf(ml.orderId), listOf(observed.integrationCode, observed.customerOrderNumber)
            )
        )

        assertEquals(
            CommerceIdentityMatchState.EXACT_CONFIRMED,
            CommerceIdentityBridge.assess(
                ml, listOf(declared), CommerceIdentityPolicy("synthetic-policy"), now
            ).transaction.state
        )
    }

    @Test
    fun `concurrent conflicting provider replay fails closed`() {
        val active = activeConnection()
        val barrier = CyclicBarrier(2)
        val sequence = AtomicInteger()
        val connector = OmieRecordConnector { _, _ ->
            val value = sequence.incrementAndGet()
            barrier.await()
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(record("product-$value", "location", "10")),
                    null,
                    now,
                    exhausted = true,
                    responseBytes = 10
                )
            )
        }

        val runtime = runtime(active, committer(), connector)
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
                1,
                outcomes.count {
                    it is ConnectorExecutionOutcome.Failure &&
                        it.kind == ConnectorExecutionFailureKind.INTERNAL
                }
            )
            assertEquals(1, count("integration_connector_page_commit"))
            assertEquals(1, count("integration_omie_product_cost_source_observation"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `source observation persistence failure rolls back page and progress`() {
        val active = activeConnection()
        execute(
            "CREATE FUNCTION reject_omie_cost() RETURNS trigger LANGUAGE plpgsql AS '" +
                "BEGIN RAISE EXCEPTION ''injected provider marker''; END'; " +
                "CREATE TRIGGER reject_omie_cost BEFORE INSERT ON " +
                "integration_omie_product_cost_source_observation FOR EACH ROW " +
                "EXECUTE FUNCTION reject_omie_cost()"
        )
        val connector = OmieRecordConnector { _, _ ->
            ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(record("rollback-product", "location", "12")),
                    null,
                    now,
                    exhausted = true,
                    responseBytes = 10
                )
            )
        }

        val outcome = runtime(active, committer(), connector).execute(invocation(active))
        val failure = assertIs<ConnectorExecutionOutcome.Failure>(outcome)
        assertEquals(ConnectorExecutionFailureKind.INTERNAL, failure.kind)
        assertEquals(0, count("integration_connector_progress"))
        assertEquals(0, count("integration_connector_page_commit"))
        assertEquals(0, count("integration_omie_product_cost_source_observation"))
        assertFalse(outcome.toString().contains("injected provider marker"))
    }

    @Test
    fun `same provider identifiers remain isolated by organization and connection`() {
        val first = activeConnection()
        val second = activeConnection()

        listOf(first, second).forEach { active ->
            val connector = OmieRecordConnector { _, _ ->
                ConnectorReadResult.Page(
                    ConnectorPage(
                        listOf(record("same-product", "same-location", "7.5")),
                        null,
                        now,
                        exhausted = true,
                        responseBytes = 10
                    )
                )
            }
            success(runtime(active, committer(), connector).execute(invocation(active)))
        }

        assertEquals(2, count("integration_omie_product_cost_source_observation"))
        assertEquals(1, countFor(first))
        assertEquals(1, countFor(second))
    }

    private fun activeConnection(): Pair<OrganizationId, IntegrationConnectionId> {
        val organization = service.createOrganization()
        val connection = service.createConnection(
            organization.id,
            ProviderKey.of("omie"),
            CredentialKind.STATIC_API_CREDENTIAL
        )
        service.bindInitialCredential(
            organization.id,
            connection.id,
            """{"schemaVersion":1,"appKey":"test","appSecret":"test"}""".toByteArray()
        )
        return organization.id to connection.id
    }

    private fun committer() =
        PostgresOmieProductCostCommitter(configuration, protector, clock)

    private fun runtime(
        active: Pair<OrganizationId, IntegrationConnectionId>,
        committer: PostgresOmieProductCostCommitter,
        connector: OmieRecordConnector
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
            MarketplaceEconomicProductCostCapability.KEY,
            ConnectorInvocationId(UUID.randomUUID()),
            ConnectorBudget(now.plusSeconds(30), 100, 100_000)
        )

    private fun record(
        product: String,
        location: String,
        cmc: String,
        integration: String = "integration-ref",
        display: String = "SKU"
    ) =
        OmieProductCostSourceRecord(
            OmieProductReference.of(product),
            OmieIntegrationReference.of(integration),
            OmieDisplayedProductCode.of(display),
            OmieLocationReference.of(location),
            ProviderSourceDecimal.parse(cmc),
            ProviderSourceDecimal.parse("13"),
            ProviderSourceDecimal.parse("13"),
            ProviderSourceDecimal.parse("0"),
            LocalDate.parse("2026-09-06"),
            now
        )

    private fun success(outcome: ConnectorExecutionOutcome) =
        assertIs<ConnectorExecutionOutcome.Success>(outcome)

    private fun count(table: String): Int {
        require(
            table in setOf(
                "integration_connector_progress",
                "integration_connector_page_commit",
                "integration_omie_product_cost_source_observation"
            )
        )
        return longValue("SELECT count(*) FROM $table").toInt()
    }

    private fun countFor(active: Pair<OrganizationId, IntegrationConnectionId>): Int =
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT count(*) FROM integration_omie_product_cost_source_observation " +
                    "WHERE organization_id=? AND connection_id=?"
            ).use { statement ->
                statement.setObject(1, active.first.value)
                statement.setObject(2, active.second.value)
                statement.executeQuery().use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun decimalAt(ordinal: Int): String = connection().use { connection ->
        connection.prepareStatement(
            "SELECT unit_cmc FROM integration_omie_product_cost_source_observation " +
                "WHERE record_ordinal=? ORDER BY input_progress_version LIMIT 1"
        ).use { statement ->
            statement.setInt(1, ordinal)
            statement.executeQuery().use {
                it.next()
                it.getBigDecimal(1).stripTrailingZeros().toPlainString()
            }
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
            statement.executeQuery(sql).use {
                it.next()
                it.getLong(1)
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

private class OmieRecordConnector(
    private val reader: (Int, String?) -> ConnectorReadResult
) : PullConnector {
    override val descriptor = ConnectorDescriptor(
        ProviderKey.of("omie"),
        listOf(
            ConnectorRecordDefinition(
                MarketplaceEconomicProductCostCapability.KEY,
                OmieProductCostSourceRecord::class
            )
        )
    )
    private val calls = AtomicInteger()
    val callCount: Int
        get() = calls.get()

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

private class OmieTestVault : SecretVault {
    private val stored = mutableMapOf<SecretReference, ByteArray>()
    private var sequence = 0

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

private class OmieTestProtector : ConnectorProgressProtector {
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
        val contextBytes = contextBytes(context)
        val pad = MessageDigest.getInstance("SHA-256").digest(contextBytes)
        val ciphertext = envelope.copyOfRange(32, envelope.size)
        val plaintext = ByteArray(ciphertext.size) { index ->
            (ciphertext[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        }
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(contextBytes + plaintext)
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

    private fun contextBytes(context: ConnectorProgressProtectionContext): ByteArray =
        listOf(
            context.organizationId.toString(),
            context.connectionId.value.toString(),
            context.capability.value,
            context.progressVersion.toString()
        ).joinToString("\n").toByteArray(StandardCharsets.UTF_8)
}

private class SyntheticOmieTransactionConnector(
    private val record: OmieTransactionEvidenceRecord
) : PullConnector {
    override val descriptor = ConnectorDescriptor(
        ProviderKey.of("omie"),
        listOf(ConnectorRecordDefinition(OmieTransactionEvidenceCapability.KEY, OmieTransactionEvidenceRecord::class))
    )

    override fun readPage(
        capability: ConnectorCapability,
        credentialBytes: ByteArray,
        currentProgress: ConnectorProgress?,
        budget: ConnectorBudget,
        cancellation: ConnectorCancellation
    ) = ConnectorReadResult.Page(
        ConnectorPage(listOf(record), null, record.observedAt, exhausted = true, responseBytes = 100)
    )
}
