package io.flooow.marketplace.api

import io.flooow.integration.connector.*
import io.flooow.integration.control.*
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceCapability
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicProductCostCapability
import io.flooow.marketplace.operations.economics.provider.OmieProductCostSourceRecord
import io.flooow.organization.OrganizationId
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OmieStaticCredentialBootstrapTest {
    private val organization = OrganizationId.parse("11111111-1111-4111-8111-111111111111")

    @Test
    fun `happy path creates active Omie static credential and zeroizes envelope`() {
        val repository = MemoryRepository(organization)
        val vault = MemoryVault()
        val service = IntegrationControlPlaneService(repository, vault)
        val result = OmieStaticCredentialBootstrap(service)
            .bootstrap(organization, "app-key", "app-secret")

        assertEquals("omie", repository.connections.single().providerKey.value)
        assertEquals(CredentialKind.STATIC_API_CREDENTIAL, repository.connections.single().credentialKind)
        assertEquals(IntegrationConnectionStatus.ACTIVE, repository.connections.single().status)
        assertEquals(
            "{\"schemaVersion\":1,\"appKey\":\"app-key\",\"appSecret\":\"app-secret\"}",
            vault.storedBytes.decodeToString()
        )
        assertTrue(vault.lastInputBytes.all { it.toInt() == 0 })
        assertTrue(result.connectionId == repository.connections.single().id)
    }

    @Test
    fun `bind failure never produces ready and leaves connection inactive`() {
        val repository = MemoryRepository(organization, bindResult = false)
        val service = IntegrationControlPlaneService(repository, MemoryVault())

        assertFailsWith<IllegalStateException> {
            OmieStaticCredentialBootstrap(service).bootstrap(organization, "app-key", "app-secret")
        }
        assertEquals(IntegrationConnectionStatus.DRAFT, repository.connections.single().status)
    }

    @Test
    fun `inactive or different organization cannot receive a connection`() {
        val repository = MemoryRepository(organization)
        val service = IntegrationControlPlaneService(repository, MemoryVault())
        val otherOrganization = OrganizationId.parse("22222222-2222-4222-8222-222222222222")

        assertFailsWith<IllegalArgumentException> {
            OmieStaticCredentialBootstrap(service).bootstrap(otherOrganization, "app-key", "app-secret")
        }
        assertTrue(repository.connections.isEmpty())
    }

    @Test
    fun `request is authenticated and malformed credentials are rejected without disclosure`() =
        testApplication {
            application { configureForTest() }

            val unauthorized = client.post("/v1/integrations/omie/bootstrap") {
                contentType(ContentType.Application.Json)
                setBody("{\"appKey\":\"key\",\"appSecret\":\"secret\"}")
            }
            assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

            listOf(
                "{",
                "{\"appSecret\":\"secret\"}",
                "{\"appKey\":\"key\"}",
                "{\"appKey\":\" \",\"appSecret\":\"secret\"}",
                "{\"appKey\":\"key\",\"appSecret\":\"\"}",
                "{\"appKey\":1,\"appSecret\":\"secret\"}",
                "{\"appKey\":\"key\",\"appSecret\":\"secret\",\"organizationId\":\"other\"}"
            ).forEach { body ->
                val response = client.post("/v1/integrations/omie/bootstrap") {
                    bearerAuth(TEST_SERVICE_TOKEN)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertFalse(response.bodyAsText().contains("app-secret"))
            }
        }

    @Test
    fun `happy HTTP response contains only status and connection identity`() = testApplication {
        application { configureForTest() }
        val response = client.post("/v1/integrations/omie/bootstrap") {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Application.Json)
            setBody("{\"appKey\":\"app-key\",\"appSecret\":\"app-secret\"}")
        }
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.Created, response.status)
        assertContains(body, "\"status\":\"READY\"")
        assertContains(body, "\"connectionId\"")
        assertFalse(body.contains("app-key"))
        assertFalse(body.contains("app-secret"))
        assertFalse(body.contains("secretRef"))
    }

    @Test
    fun `HTTP bootstrap uses the authenticated principal organization`() = testApplication {
        val principalOrganization = OrganizationId.parse("33333333-3333-4333-8333-333333333333")
        val repository = MemoryRepository(principalOrganization)
        application {
            configureApi(
                serviceToken = ServiceToken.test(TEST_SERVICE_TOKEN),
                serviceOrganizationId = principalOrganization,
                record = { _, _ -> error("not used") },
                omieStaticCredentialBootstrap = OmieStaticCredentialBootstrap(
                    IntegrationControlPlaneService(repository, MemoryVault())
                )
            )
        }

        val response = client.post("/v1/integrations/omie/bootstrap") {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Application.Json)
            setBody("{\"appKey\":\"app-key\",\"appSecret\":\"app-secret\"}")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(principalOrganization, repository.connections.single().organizationId)
    }

    @Test
    fun `HTTP Omie refresh uses configured active connection and returns operational metadata`() = testApplication {
        val repository = MemoryRepository(organization)
        val vault = MemoryVault()
        val controlPlane = IntegrationControlPlaneService(repository, vault)
        val connection = OmieStaticCredentialBootstrap(controlPlane)
            .bootstrap(organization, "app-key", "app-secret")
        val committer = MemoryOmieCommitter()
        val runtime = ConnectorRuntime(
            IntegrationControlPlaneConnectorAccess(controlPlane),
            listOf(EmptyOmieConnector()),
            listOf(committer)
        )
        application {
            configureApi(
                serviceToken = ServiceToken.test(TEST_SERVICE_TOKEN),
                serviceOrganizationId = organization,
                record = { _, _ -> error("not used") },
                omieEvidenceRefreshApi = OmieEvidenceRefreshApi(controlPlane, runtime, connection.connectionId)
            )
        }

        val response = client.post(OMIE_EVIDENCE_REFRESH_PATH) {
            bearerAuth(TEST_SERVICE_TOKEN)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"status\":\"COMPLETED\"")
        assertContains(response.bodyAsText(), "\"committedPages\":1")
        assertFalse(response.bodyAsText().contains("app-secret"))
        assertEquals(1, committer.commits)
    }

    @Test
    fun `HTTP Omie product-cost refresh invokes registered capability with scoped credential`() =
        testApplication {
            val repository = MemoryRepository(organization)
            val vault = MemoryVault()
            val controlPlane = IntegrationControlPlaneService(repository, vault)
            val connection = OmieStaticCredentialBootstrap(controlPlane)
                .bootstrap(organization, "app-key", "app-secret")
            val connector = EmptyOmieProductCostConnector()
            val committer = MemoryOmieProductCostCommitter()
            val runtime = ConnectorRuntime(
                IntegrationControlPlaneConnectorAccess(controlPlane),
                listOf(connector),
                listOf(committer)
            )
            application {
                configureApi(
                    serviceToken = ServiceToken.test(TEST_SERVICE_TOKEN),
                    serviceOrganizationId = organization,
                    record = { _, _ -> error("not used") },
                    omieProductCostRefreshApi = OmieEvidenceRefreshApi(
                        controlPlane,
                        runtime,
                        connection.connectionId,
                        capability = MarketplaceEconomicProductCostCapability.KEY
                    )
                )
            }

            val response = client.post(OMIE_PRODUCT_COST_REFRESH_PATH) {
                bearerAuth(TEST_SERVICE_TOKEN)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(
                response.bodyAsText(),
                "\"acquisitionGeneration\":\"marketplace-economic.product-cost\""
            )
            assertEquals(1, connector.invocations)
            assertEquals(1, committer.commits)
            assertEquals("app-key", connector.observedAppKey)
            assertFalse(response.bodyAsText().contains("app-secret"))
        }

    @Test
    fun `refresh fails closed for missing configuration wrong provider and wrong organization`() {
        val repository = MemoryRepository(organization)
        val vault = MemoryVault()
        val controlPlane = IntegrationControlPlaneService(repository, vault)
        val omieConnection = OmieStaticCredentialBootstrap(controlPlane)
            .bootstrap(organization, "app-key", "app-secret")
        val otherOrganization = OrganizationId.parse("22222222-2222-4222-8222-222222222222")

        assertFailsWith<OmieEvidenceRefreshConfigurationUnavailableException> {
            OmieEvidenceRefreshApi(controlPlane, emptyRuntime(), null).refresh(organization)
        }
        assertFailsWith<OmieEvidenceRefreshConnectionUnavailableException> {
            OmieEvidenceRefreshApi(controlPlane, emptyRuntime(), omieConnection.connectionId)
                .refresh(otherOrganization)
        }

        val wrongProvider = controlPlane.createConnection(
            organization,
            ProviderKey.of("br.com.mercadolivre"),
            CredentialKind.STATIC_API_CREDENTIAL
        )
        controlPlane.bindInitialCredential(organization, wrongProvider.id, "{}".toByteArray())
        assertFailsWith<OmieEvidenceRefreshConnectionUnavailableException> {
            OmieEvidenceRefreshApi(controlPlane, emptyRuntime(), wrongProvider.id).refresh(organization)
        }
    }

    private fun Application.configureForTest() {
        val repository = MemoryRepository(TEST_ORGANIZATION_ID)
        configureApi(
            serviceToken = ServiceToken.test(TEST_SERVICE_TOKEN),
            serviceOrganizationId = TEST_ORGANIZATION_ID,
            record = { _, _ -> error("not used") },
            omieStaticCredentialBootstrap = OmieStaticCredentialBootstrap(
                IntegrationControlPlaneService(repository, MemoryVault())
            )
        )
    }

    private class MemoryVault : SecretVault {
        var storedBytes: ByteArray = ByteArray(0)
        var lastInputBytes: ByteArray = ByteArray(0)
        override fun store(organizationId: OrganizationId, connectionId: IntegrationConnectionId, credentialBytes: ByteArray): SecretReference {
            lastInputBytes = credentialBytes
            storedBytes = credentialBytes.copyOf()
            return SecretReference.of("memory-${connectionId.value}")
        }
        override fun <T> withSecret(organizationId: OrganizationId, connectionId: IntegrationConnectionId, reference: SecretReference, operation: (ByteArray) -> T): T = operation(storedBytes.copyOf())
        override fun revoke(organizationId: OrganizationId, connectionId: IntegrationConnectionId, reference: SecretReference) = Unit
    }

    private fun emptyRuntime() = ConnectorRuntime(
        object : ConnectorConnectionAccess {
            override fun activeProvider(organizationId: OrganizationId, connectionId: IntegrationConnectionId): ProviderKey? = null
            override fun <T> withActiveCredential(organizationId: OrganizationId, connectionId: IntegrationConnectionId, operation: (ByteArray) -> T): T = error("not used")
        },
        emptyList(),
        emptyList()
    )

    private class MemoryRepository(
        private val organization: OrganizationId,
        private val bindResult: Boolean = true
    ) : IntegrationControlPlaneRepository {
        val connections = mutableListOf<IntegrationConnection>()
        override fun createOrganization(organization: IntegrationOrganization, audit: IntegrationAuditEntry) = Unit
        override fun findOrganization(id: OrganizationId) = if (id == organization) IntegrationOrganization(id, IntegrationOrganizationStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH) else null
        override fun changeOrganizationStatus(id: OrganizationId, expected: IntegrationOrganizationStatus, updated: IntegrationOrganization, audit: IntegrationAuditEntry) = true
        override fun createConnection(connection: IntegrationConnection, audit: IntegrationAuditEntry) { connections += connection }
        override fun findConnection(organizationId: OrganizationId, connectionId: IntegrationConnectionId) = connections.find { it.organizationId == organizationId && it.id == connectionId }
        override fun bindInitialCredential(organizationId: OrganizationId, connectionId: IntegrationConnectionId, reference: SecretReference, now: Instant, audit: IntegrationAuditEntry): Boolean {
            if (!bindResult) return false
            val index = connections.indexOfFirst { it.organizationId == organizationId && it.id == connectionId }
            if (index < 0) return false
            connections[index] = connections[index].copy(status = IntegrationConnectionStatus.ACTIVE, bindingVersion = 1)
            return true
        }
        override fun rotateCredential(organizationId: OrganizationId, connectionId: IntegrationConnectionId, expectedVersion: Int, newReference: SecretReference, now: Instant, audit: IntegrationAuditEntry): SecretReference? = null
        override fun currentBinding(organizationId: OrganizationId, connectionId: IntegrationConnectionId): CredentialBinding? =
            findConnection(organizationId, connectionId)?.takeIf { it.status == IntegrationConnectionStatus.ACTIVE }
                ?.let { CredentialBinding(organizationId, connectionId, 1, SecretReference.of("memory-${connectionId.value}"), Instant.EPOCH, null) }
        override fun changeConnectionStatus(organizationId: OrganizationId, connectionId: IntegrationConnectionId, expected: IntegrationConnectionStatus, target: IntegrationConnectionStatus, now: Instant, audit: IntegrationAuditEntry) = true
        override fun revokeConnection(organizationId: OrganizationId, connectionId: IntegrationConnectionId, now: Instant, audit: IntegrationAuditEntry) = true
        override fun registerDestination(destination: IntegrationDestination, audit: IntegrationAuditEntry) = Unit
        override fun findDestination(organizationId: OrganizationId, destinationId: IntegrationDestinationId): IntegrationDestination? = null
        override fun changeDestinationStatus(organizationId: OrganizationId, destinationId: IntegrationDestinationId, expected: IntegrationDestinationStatus, target: IntegrationDestinationStatus, now: Instant, audit: IntegrationAuditEntry) = true
        override fun auditEntries(organizationId: OrganizationId): List<IntegrationAuditEntry> = emptyList()
    }

    private class EmptyOmieConnector : PullConnector {
        override val descriptor = ConnectorDescriptor(
            ProviderKey.of("omie"),
            listOf(ConnectorRecordDefinition(OmieTransactionEvidenceCapability.KEY, io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceRecord::class))
        )
        override fun readPage(capability: ConnectorCapability, credentialBytes: ByteArray, currentProgress: ConnectorProgress?, budget: ConnectorBudget, cancellation: ConnectorCancellation): ConnectorReadResult =
            ConnectorReadResult.Page(ConnectorPage(emptyList(), null, Instant.parse("2026-09-09T12:00:00Z"), true, 2))
    }

    private class MemoryOmieCommitter : ConnectorPageCommitter {
        override val capability = OmieTransactionEvidenceCapability.KEY
        override val recordType: KClass<out ConnectorRecord> = io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceRecord::class
        var commits = 0
        override fun load(organizationId: OrganizationId, connectionId: IntegrationConnectionId, capability: ConnectorCapability) = VersionedConnectorProgress(0, null, false)
        override fun commit(organizationId: OrganizationId, connectionId: IntegrationConnectionId, capability: ConnectorCapability, expectedProgressVersion: Long, pageCommitKey: ConnectorPageCommitKey, records: List<ConnectorRecord>, nextProgress: ConnectorProgress?, exhausted: Boolean, observedAt: Instant): ConnectorPageCommitResult {
            commits += 1
            return ConnectorPageCommitResult.COMMITTED
        }
    }

    private class EmptyOmieProductCostConnector : PullConnector {
        var invocations = 0
        var observedAppKey: String? = null
        override val descriptor = ConnectorDescriptor(
            ProviderKey.of("omie"),
            listOf(
                ConnectorRecordDefinition(
                    MarketplaceEconomicProductCostCapability.KEY,
                    OmieProductCostSourceRecord::class
                )
            )
        )
        override fun readPage(
            capability: ConnectorCapability,
            credentialBytes: ByteArray,
            currentProgress: ConnectorProgress?,
            budget: ConnectorBudget,
            cancellation: ConnectorCancellation
        ): ConnectorReadResult {
            invocations += 1
            observedAppKey = Regex("\\\"appKey\\\":\\\"([^\\\"]+)\\\"")
                .find(credentialBytes.decodeToString())?.groupValues?.get(1)
            return ConnectorReadResult.Page(
                ConnectorPage(emptyList(), null, Instant.parse("2026-09-11T12:00:00Z"), true, 2)
            )
        }
    }

    private class MemoryOmieProductCostCommitter : ConnectorPageCommitter {
        override val capability = MarketplaceEconomicProductCostCapability.KEY
        override val recordType: KClass<out ConnectorRecord> = OmieProductCostSourceRecord::class
        var commits = 0
        override fun load(
            organizationId: OrganizationId,
            connectionId: IntegrationConnectionId,
            capability: ConnectorCapability
        ) = VersionedConnectorProgress(0, null, false)
        override fun commit(
            organizationId: OrganizationId,
            connectionId: IntegrationConnectionId,
            capability: ConnectorCapability,
            expectedProgressVersion: Long,
            pageCommitKey: ConnectorPageCommitKey,
            records: List<ConnectorRecord>,
            nextProgress: ConnectorProgress?,
            exhausted: Boolean,
            observedAt: Instant
        ): ConnectorPageCommitResult {
            commits += 1
            return ConnectorPageCommitResult.COMMITTED
        }
    }
}
