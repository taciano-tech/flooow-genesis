package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.CredentialRotationResult
import io.flooow.integration.control.IdentifierFactory
import io.flooow.integration.control.IntegrationAuditEntryId
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationConnectionStatus
import io.flooow.integration.control.IntegrationControlPlaneService
import io.flooow.integration.control.IntegrationDestination
import io.flooow.integration.control.IntegrationDestinationId
import io.flooow.integration.control.IntegrationDestinationStatus
import io.flooow.integration.control.IntegrationOrganizationId
import io.flooow.integration.control.IntegrationOrganizationStatus
import io.flooow.integration.control.ProviderKey
import io.flooow.integration.control.SecretReference
import io.flooow.integration.control.SecretVault
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.UUID
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresIntegrationControlPlaneRepositoryTest {
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var repository: PostgresIntegrationControlPlaneRepository
    private lateinit var vault: FakeSecretVault
    private lateinit var service: IntegrationControlPlaneService
    private val now = Instant.parse("2026-08-10T13:00:00Z")
    private val organizationIds = ArrayDeque(
        listOf(
            IntegrationOrganizationId(UUID.fromString("11111111-1111-4111-8111-111111111111")),
            IntegrationOrganizationId(UUID.fromString("22222222-2222-4222-8222-222222222222"))
        )
    )
    private val connectionIds = ArrayDeque(
        listOf(
            IntegrationConnectionId(UUID.fromString("33333333-3333-4333-8333-333333333333")),
            IntegrationConnectionId(UUID.fromString("44444444-4444-4444-8444-444444444444"))
        )
    )
    private var auditSequence = 1L

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(postgres.jdbcUrl, postgres.username, postgres.password)
        repository = PostgresIntegrationControlPlaneRepository.connect(configuration)
        vault = FakeSecretVault()
        service = IntegrationControlPlaneService(
            repository = repository,
            vault = vault,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            organizationIds = IdentifierFactory { organizationIds.removeFirst() },
            connectionIds = IdentifierFactory { connectionIds.removeFirst() },
            auditIds = IdentifierFactory {
                IntegrationAuditEntryId(UUID(0, auditSequence++))
            },
            correlationIds = IdentifierFactory { UUID(1, auditSequence++) }
        )
    }

    @AfterTest
    fun stopPostgres() {
        postgres.stop()
    }

    @Test
    fun `organization connection destination and credential lifecycle stay isolated`() {
        val firstOrganization = service.createOrganization()
        val secondOrganization = service.createOrganization()
        val connection = service.createConnection(
            firstOrganization.id,
            ProviderKey.of("br.com.mercadolivre"),
            CredentialKind.OAUTH2_AUTHORIZATION_CODE
        )
        assertNull(repository.findConnection(secondOrganization.id, connection.id))

        val firstSecret = "first-refresh-token".toByteArray()
        service.bindInitialCredential(firstOrganization.id, connection.id, firstSecret)
        assertTrue(firstSecret.all { it == 0.toByte() })
        assertEquals(
            IntegrationConnectionStatus.ACTIVE,
            repository.findConnection(firstOrganization.id, connection.id)?.status
        )

        val destination = service.registerDestination(firstOrganization.id, connection.id)
        assertNull(repository.findDestination(secondOrganization.id, destination.id))
        service.suspendDestination(firstOrganization.id, connection.id, destination.id)
        assertEquals(
            IntegrationDestinationStatus.SUSPENDED,
            repository.findDestination(firstOrganization.id, destination.id)?.status
        )
        service.resumeDestination(firstOrganization.id, connection.id, destination.id)

        val oldReference = repository.currentBinding(firstOrganization.id, connection.id)!!
            .secretReference
        val secondSecret = "second-refresh-token".toByteArray()
        assertEquals(
            CredentialRotationResult.ROTATED,
            service.rotateCredential(firstOrganization.id, connection.id, 1, secondSecret)
        )
        assertTrue(secondSecret.all { it == 0.toByte() })
        assertTrue(vault.isRevoked(oldReference))
        assertEquals(2, repository.currentBinding(firstOrganization.id, connection.id)?.version)

        lateinit var callbackBytes: ByteArray
        val resolved = service.withActiveCredential(
            firstOrganization.id,
            connection.id
        ) { bytes ->
            callbackBytes = bytes
            bytes.decodeToString()
        }
        assertEquals("second-refresh-token", resolved)
        assertTrue(callbackBytes.all { it == 0.toByte() })

        service.suspendConnection(firstOrganization.id, connection.id)
        assertFails {
            service.withActiveCredential(firstOrganization.id, connection.id) { it.size }
        }
        assertFails { service.registerDestination(firstOrganization.id, connection.id) }
        service.resumeConnection(firstOrganization.id, connection.id)
        service.suspendOrganization(firstOrganization.id)
        assertFails {
            service.withActiveCredential(firstOrganization.id, connection.id) { it.size }
        }
        assertFails {
            service.createConnection(
                firstOrganization.id,
                ProviderKey.of("br.com.omie"),
                CredentialKind.STATIC_API_CREDENTIAL
            )
        }
        service.resumeOrganization(firstOrganization.id)
        service.revokeConnection(firstOrganization.id, connection.id)
        assertEquals(
            IntegrationConnectionStatus.REVOKED,
            repository.findConnection(firstOrganization.id, connection.id)?.status
        )
        assertNull(repository.currentBinding(firstOrganization.id, connection.id))
        assertFails { service.resumeConnection(firstOrganization.id, connection.id) }
        assertTrue(repository.auditEntries(firstOrganization.id).size >= 10)
        assertDatabaseDoesNotContain("first-refresh-token", "second-refresh-token")
    }

    @Test
    fun `stale rotation and cleanup failure preserve the committed binding`() {
        val organization = service.createOrganization()
        val connection = service.createConnection(
            organization.id,
            ProviderKey.of("br.com.omie"),
            CredentialKind.STATIC_API_CREDENTIAL
        )
        service.bindInitialCredential(organization.id, connection.id, "initial-key".toByteArray())
        val oldReference = repository.currentBinding(organization.id, connection.id)!!.secretReference
        vault.failRevocation(oldReference)

        assertEquals(
            CredentialRotationResult.ROTATED_CLEANUP_REQUIRED,
            service.rotateCredential(organization.id, connection.id, 1, "rotated-key".toByteArray())
        )
        assertEquals(2, repository.currentBinding(organization.id, connection.id)?.version)

        val referencesBefore = vault.references().toSet()
        assertEquals(
            CredentialRotationResult.STALE_VERSION,
            service.rotateCredential(organization.id, connection.id, 1, "stale-key".toByteArray())
        )
        val staleReference = (vault.references().toSet() - referencesBefore).single()
        assertTrue(vault.isRevoked(staleReference))

        val active = repository.currentBinding(organization.id, connection.id)!!
        assertFails {
            repository.registerDestination(
                IntegrationDestination(
                    IntegrationOrganizationId(UUID.randomUUID()),
                    active.connectionId,
                    IntegrationDestinationId.of("cross-organization"),
                    IntegrationDestinationStatus.ACTIVE,
                    now,
                    now
                ),
                repository.auditEntries(organization.id).first()
            )
        }
    }

    private fun assertDatabaseDoesNotContain(vararg forbidden: String) {
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password)
            .use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT string_agg(value, ' ') FROM (" +
                            "SELECT secret_ref AS value FROM integration_credential_binding " +
                            "UNION ALL SELECT action FROM integration_control_audit) values"
                    ).use { result ->
                        result.next()
                        val persisted = result.getString(1).orEmpty()
                        forbidden.forEach { assertFalse(persisted.contains(it)) }
                    }
                }
            }
    }

    private class FakeSecretVault : SecretVault {
        private data class Stored(
            val organizationId: IntegrationOrganizationId,
            val connectionId: IntegrationConnectionId,
            val bytes: ByteArray,
            var revoked: Boolean = false
        )

        private val values = linkedMapOf<SecretReference, Stored>()
        private val failingRevocations = mutableSetOf<SecretReference>()

        override fun store(
            organizationId: IntegrationOrganizationId,
            connectionId: IntegrationConnectionId,
            credentialBytes: ByteArray
        ): SecretReference {
            try {
                val reference = SecretReference.of("vault-ref-${values.size + 1}-${UUID.randomUUID()}")
                values[reference] = Stored(organizationId, connectionId, credentialBytes.copyOf())
                return reference
            } finally {
                credentialBytes.fill(0)
            }
        }

        override fun <T> withSecret(
            organizationId: IntegrationOrganizationId,
            connectionId: IntegrationConnectionId,
            reference: SecretReference,
            operation: (ByteArray) -> T
        ): T {
            val stored = requireNotNull(values[reference])
            require(stored.organizationId == organizationId && stored.connectionId == connectionId)
            check(!stored.revoked)
            val copy = stored.bytes.copyOf()
            return try {
                operation(copy)
            } finally {
                copy.fill(0)
            }
        }

        override fun revoke(
            organizationId: IntegrationOrganizationId,
            connectionId: IntegrationConnectionId,
            reference: SecretReference
        ) {
            if (reference in failingRevocations) error("controlled vault failure")
            val stored = requireNotNull(values[reference])
            require(stored.organizationId == organizationId && stored.connectionId == connectionId)
            stored.bytes.fill(0)
            stored.revoked = true
        }

        fun failRevocation(reference: SecretReference) {
            failingRevocations += reference
        }

        fun isRevoked(reference: SecretReference): Boolean = values[reference]?.revoked == true
        fun references(): List<SecretReference> = values.keys.toList()
    }
}
