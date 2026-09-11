package io.flooow.marketplace.api

import io.flooow.integration.control.*
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MercadoLivreOAuthBootstrapTest {
    private val organization = OrganizationId.parse("11111111-1111-4111-8111-111111111111")
    private val clock = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `start creates encoded state and S256 challenge without exposing secret`() {
        val repository = MemoryRepository(organization)
        val service = IntegrationControlPlaneService(repository, MemoryVault(), clock)
        val bootstrap = MercadoLivreOAuthBootstrap(
            service,
            organization,
            MercadoLivreOAuthBootstrapConfiguration("client-123", "secret-never-returned", "http://localhost:8080/v1/integrations/mercadolivre/oauth/callback", pkceEnabled = true),
            clock = clock
        )

        val url = bootstrap.start().authorizationUrl
        assertTrue(url.startsWith("https://auth.mercadolivre.com.br/authorization?"))
        assertContains(url, "code_challenge_method=S256")
        assertTrue("secret-never-returned" !in url)
        assertTrue(repository.connections.single().status == IntegrationConnectionStatus.DRAFT)
    }

    @Test
    fun `incomplete environment is rejected and state replay fails closed`() {
        assertFailsWith<IllegalArgumentException> {
            MercadoLivreOAuthBootstrapConfiguration.fromEnvironment(
                mapOf("FLOOOW_MERCADO_LIVRE_CLIENT_ID" to "client")
            )
        }
        val repository = MemoryRepository(organization)
        val service = IntegrationControlPlaneService(repository, MemoryVault(), clock)
        val bootstrap = MercadoLivreOAuthBootstrap(
            service,
            organization,
            MercadoLivreOAuthBootstrapConfiguration("client", "secret", "http://localhost:8080/callback"),
            transport = object : MercadoLivreOAuthBootstrapTransport {
                override fun exchangeCode(form: String, timeout: java.time.Duration) = "{}".encodeToByteArray()
                override fun fetchUser(accessToken: String, timeout: java.time.Duration) = "{}".encodeToByteArray()
            },
            clock = clock
        )
        val state = bootstrap.start().authorizationUrl.substringAfter("state=").substringBefore('&')
        assertFailsWith<IllegalStateException> { bootstrap.callback("code", state) }
        assertFailsWith<IllegalStateException> { bootstrap.callback("code", state) }
    }

    @Test
    fun `callback exchanges code validates identity and activates connection`() {
        val repository = MemoryRepository(organization)
        val vault = MemoryVault()
        val service = IntegrationControlPlaneService(repository, vault, clock)
        val bootstrap = MercadoLivreOAuthBootstrap(
            service,
            organization,
            MercadoLivreOAuthBootstrapConfiguration("client", "secret", "http://localhost:8080/callback"),
            transport = object : MercadoLivreOAuthBootstrapTransport {
                override fun exchangeCode(form: String, timeout: java.time.Duration) =
                    "{\"token_type\":\"Bearer\",\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"user_id\":42,\"expires_in\":3600}".encodeToByteArray()
                override fun fetchUser(accessToken: String, timeout: java.time.Duration) = "{\"id\":42}".encodeToByteArray()
            },
            clock = clock
        )
        val state = bootstrap.start().authorizationUrl.substringAfter("state=").substringBefore('&')

        val result = bootstrap.callback("authorization-code", state)

        assertTrue(result.authorizedUserId == 42L)
        assertTrue(repository.connections.single().status == IntegrationConnectionStatus.ACTIVE)
        assertTrue(vault.storedBytes.isNotEmpty())
    }

    private class MemoryVault : SecretVault {
        var storedBytes: ByteArray = ByteArray(0)
        override fun store(organizationId: OrganizationId, connectionId: IntegrationConnectionId, credentialBytes: ByteArray): SecretReference {
            storedBytes = credentialBytes.copyOf()
            return SecretReference.of("memory-${connectionId.value}")
        }
        override fun <T> withSecret(organizationId: OrganizationId, connectionId: IntegrationConnectionId, reference: SecretReference, operation: (ByteArray) -> T): T = operation(ByteArray(0))
        override fun revoke(organizationId: OrganizationId, connectionId: IntegrationConnectionId, reference: SecretReference) = Unit
    }

    private class MemoryRepository(private val organization: OrganizationId) : IntegrationControlPlaneRepository {
        val connections = mutableListOf<IntegrationConnection>()
        override fun createOrganization(organization: IntegrationOrganization, audit: IntegrationAuditEntry) = Unit
        override fun findOrganization(id: OrganizationId) = if (id == organization) IntegrationOrganization(id, IntegrationOrganizationStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH) else null
        override fun changeOrganizationStatus(id: OrganizationId, expected: IntegrationOrganizationStatus, updated: IntegrationOrganization, audit: IntegrationAuditEntry) = true
        override fun createConnection(connection: IntegrationConnection, audit: IntegrationAuditEntry) { connections += connection }
        override fun findConnection(organizationId: OrganizationId, connectionId: IntegrationConnectionId) = connections.find { it.organizationId == organizationId && it.id == connectionId }
        override fun bindInitialCredential(organizationId: OrganizationId, connectionId: IntegrationConnectionId, reference: SecretReference, now: Instant, audit: IntegrationAuditEntry): Boolean {
            val index = connections.indexOfFirst { it.organizationId == organizationId && it.id == connectionId }
            if (index < 0) return false
            connections[index] = connections[index].copy(status = IntegrationConnectionStatus.ACTIVE, bindingVersion = 1)
            return true
        }
        override fun rotateCredential(organizationId: OrganizationId, connectionId: IntegrationConnectionId, expectedVersion: Int, newReference: SecretReference, now: Instant, audit: IntegrationAuditEntry): SecretReference? = null
        override fun currentBinding(organizationId: OrganizationId, connectionId: IntegrationConnectionId): CredentialBinding? = null
        override fun changeConnectionStatus(organizationId: OrganizationId, connectionId: IntegrationConnectionId, expected: IntegrationConnectionStatus, target: IntegrationConnectionStatus, now: Instant, audit: IntegrationAuditEntry) = true
        override fun revokeConnection(organizationId: OrganizationId, connectionId: IntegrationConnectionId, now: Instant, audit: IntegrationAuditEntry) = true
        override fun registerDestination(destination: IntegrationDestination, audit: IntegrationAuditEntry) = Unit
        override fun findDestination(organizationId: OrganizationId, destinationId: IntegrationDestinationId): IntegrationDestination? = null
        override fun changeDestinationStatus(organizationId: OrganizationId, destinationId: IntegrationDestinationId, expected: IntegrationDestinationStatus, target: IntegrationDestinationStatus, now: Instant, audit: IntegrationAuditEntry) = true
        override fun auditEntries(organizationId: OrganizationId): List<IntegrationAuditEntry> = emptyList()
    }
}
