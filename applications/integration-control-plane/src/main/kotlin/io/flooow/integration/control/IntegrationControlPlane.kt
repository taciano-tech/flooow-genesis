package io.flooow.integration.control

import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Instant
import java.util.UUID

typealias IntegrationOrganizationId = OrganizationId

@JvmInline
value class IntegrationConnectionId(val value: UUID)

@JvmInline
value class IntegrationAuditEntryId(val value: UUID)

@JvmInline
value class ProviderKey private constructor(val value: String) {
    companion object {
        private val pattern = Regex("[a-z0-9][a-z0-9.-]{0,99}")
        fun of(value: String): ProviderKey {
            require(pattern.matches(value)) { "Invalid provider key" }
            return ProviderKey(value)
        }
    }
}

@JvmInline
value class IntegrationDestinationId private constructor(val value: String) {
    companion object {
        private val pattern = Regex("[a-z0-9][a-z0-9._-]{0,99}")
        fun of(value: String): IntegrationDestinationId {
            require(pattern.matches(value)) { "Invalid integration destination identifier" }
            return IntegrationDestinationId(value)
        }

        fun forConnection(connectionId: IntegrationConnectionId): IntegrationDestinationId =
            of("connection.${connectionId.value}")
    }
}

class SecretReference private constructor(private val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 512) { "Invalid secret reference" }
    }

    fun encodedForPersistence(): String = value

    override fun equals(other: Any?): Boolean = other is SecretReference && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "[REDACTED]"

    companion object {
        fun of(value: String): SecretReference = SecretReference(value)
    }
}

enum class IntegrationOrganizationStatus { ACTIVE, SUSPENDED }
enum class IntegrationConnectionStatus { DRAFT, ACTIVE, SUSPENDED, REVOKED }
enum class IntegrationDestinationStatus { ACTIVE, SUSPENDED }
enum class CredentialKind { OAUTH2_AUTHORIZATION_CODE, STATIC_API_CREDENTIAL }

enum class IntegrationAuditAction {
    ORGANIZATION_CREATED,
    ORGANIZATION_SUSPENDED,
    ORGANIZATION_RESUMED,
    CONNECTION_CREATED,
    CONNECTION_ACTIVATED,
    CONNECTION_SUSPENDED,
    CONNECTION_RESUMED,
    CONNECTION_REVOKED,
    CREDENTIAL_ROTATED,
    DESTINATION_REGISTERED,
    DESTINATION_SUSPENDED,
    DESTINATION_RESUMED
}

data class IntegrationOrganization(
    val id: IntegrationOrganizationId,
    val status: IntegrationOrganizationStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class IntegrationConnection(
    val organizationId: IntegrationOrganizationId,
    val id: IntegrationConnectionId,
    val providerKey: ProviderKey,
    val credentialKind: CredentialKind,
    val status: IntegrationConnectionStatus,
    val bindingVersion: Int?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class IntegrationDestination(
    val organizationId: IntegrationOrganizationId,
    val connectionId: IntegrationConnectionId,
    val id: IntegrationDestinationId,
    val status: IntegrationDestinationStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class CredentialBinding(
    val organizationId: IntegrationOrganizationId,
    val connectionId: IntegrationConnectionId,
    val version: Int,
    val secretReference: SecretReference,
    val boundAt: Instant,
    val revokedAt: Instant?
)

data class ActiveCredentialContext(
    val providerKey: ProviderKey,
    val credentialKind: CredentialKind,
    val bindingVersion: Int
) {
    init { require(bindingVersion > 0) { "Invalid active credential binding version" } }
}

data class IntegrationAuditEntry(
    val id: IntegrationAuditEntryId,
    val organizationId: IntegrationOrganizationId,
    val connectionId: IntegrationConnectionId?,
    val action: IntegrationAuditAction,
    val occurredAt: Instant,
    val correlationId: UUID
)

fun interface IdentifierFactory<T> { fun create(): T }

interface SecretVault {
    fun store(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        credentialBytes: ByteArray
    ): SecretReference

    fun <T> withSecret(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference,
        operation: (ByteArray) -> T
    ): T

    fun revoke(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference
    )
}

interface IntegrationControlPlaneRepository {
    fun createOrganization(organization: IntegrationOrganization, audit: IntegrationAuditEntry)
    fun findOrganization(id: IntegrationOrganizationId): IntegrationOrganization?
    fun changeOrganizationStatus(
        id: IntegrationOrganizationId,
        expected: IntegrationOrganizationStatus,
        updated: IntegrationOrganization,
        audit: IntegrationAuditEntry
    ): Boolean

    fun createConnection(connection: IntegrationConnection, audit: IntegrationAuditEntry)
    fun findConnection(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId
    ): IntegrationConnection?

    fun bindInitialCredential(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean

    fun rotateCredential(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        expectedVersion: Int,
        newReference: SecretReference,
        now: Instant,
        audit: IntegrationAuditEntry
    ): SecretReference?

    fun currentBinding(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId
    ): CredentialBinding?

    fun changeConnectionStatus(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        expected: IntegrationConnectionStatus,
        target: IntegrationConnectionStatus,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean

    fun revokeConnection(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean

    fun registerDestination(destination: IntegrationDestination, audit: IntegrationAuditEntry)
    fun findDestination(
        organizationId: IntegrationOrganizationId,
        destinationId: IntegrationDestinationId
    ): IntegrationDestination?

    fun changeDestinationStatus(
        organizationId: IntegrationOrganizationId,
        destinationId: IntegrationDestinationId,
        expected: IntegrationDestinationStatus,
        target: IntegrationDestinationStatus,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean

    fun auditEntries(organizationId: IntegrationOrganizationId): List<IntegrationAuditEntry>
}

enum class CredentialRotationResult { ROTATED, ROTATED_CLEANUP_REQUIRED, STALE_VERSION }

class IntegrationControlPlaneService(
    private val repository: IntegrationControlPlaneRepository,
    private val vault: SecretVault,
    private val clock: Clock = Clock.systemUTC(),
    private val organizationIds: IdentifierFactory<IntegrationOrganizationId> =
        IdentifierFactory { IntegrationOrganizationId(UUID.randomUUID()) },
    private val connectionIds: IdentifierFactory<IntegrationConnectionId> =
        IdentifierFactory { IntegrationConnectionId(UUID.randomUUID()) },
    private val auditIds: IdentifierFactory<IntegrationAuditEntryId> =
        IdentifierFactory { IntegrationAuditEntryId(UUID.randomUUID()) },
    private val correlationIds: IdentifierFactory<UUID> = IdentifierFactory(UUID::randomUUID)
) {
    fun createOrganization(): IntegrationOrganization {
        val now = clock.instant()
        val organization = IntegrationOrganization(
            organizationIds.create(), IntegrationOrganizationStatus.ACTIVE, now, now
        )
        repository.createOrganization(
            organization,
            audit(organization.id, null, IntegrationAuditAction.ORGANIZATION_CREATED, now)
        )
        return organization
    }

    fun createConnection(
        organizationId: IntegrationOrganizationId,
        providerKey: ProviderKey,
        credentialKind: CredentialKind
    ): IntegrationConnection {
        require(repository.findOrganization(organizationId)?.status ==
            IntegrationOrganizationStatus.ACTIVE) { "Organization is not active" }
        val now = clock.instant()
        val connection = IntegrationConnection(
            organizationId, connectionIds.create(), providerKey, credentialKind,
            IntegrationConnectionStatus.DRAFT, null, now, now
        )
        repository.createConnection(
            connection,
            audit(organizationId, connection.id, IntegrationAuditAction.CONNECTION_CREATED, now)
        )
        return connection
    }

    fun bindInitialCredential(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        credentialBytes: ByteArray
    ) {
        val now = clock.instant()
        val reference = vault.store(organizationId, connectionId, credentialBytes)
        try {
            check(
                repository.bindInitialCredential(
                    organizationId,
                    connectionId,
                    reference,
                    now,
                    audit(
                        organizationId,
                        connectionId,
                        IntegrationAuditAction.CONNECTION_ACTIVATED,
                        now
                    )
                )
            ) { "Connection cannot be activated" }
        } catch (error: Exception) {
            vault.revoke(organizationId, connectionId, reference)
            throw error
        }
    }

    fun rotateCredential(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        expectedVersion: Int,
        credentialBytes: ByteArray
    ): CredentialRotationResult {
        val now = clock.instant()
        val newReference = vault.store(organizationId, connectionId, credentialBytes)
        val oldReference = try {
            repository.rotateCredential(
                organizationId,
                connectionId,
                expectedVersion,
                newReference,
                now,
                audit(
                    organizationId,
                    connectionId,
                    IntegrationAuditAction.CREDENTIAL_ROTATED,
                    now
                )
            )
        } catch (error: Exception) {
            vault.revoke(organizationId, connectionId, newReference)
            throw error
        }
        if (oldReference == null) {
            vault.revoke(organizationId, connectionId, newReference)
            return CredentialRotationResult.STALE_VERSION
        }
        return try {
            vault.revoke(organizationId, connectionId, oldReference)
            CredentialRotationResult.ROTATED
        } catch (_: Exception) {
            CredentialRotationResult.ROTATED_CLEANUP_REQUIRED
        }
    }

    fun activeCredentialContext(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId
    ): ActiveCredentialContext? {
        if (repository.findOrganization(organizationId)?.status !=
            IntegrationOrganizationStatus.ACTIVE) return null
        val connection = repository.findConnection(organizationId, connectionId) ?: return null
        if (connection.status != IntegrationConnectionStatus.ACTIVE) return null
        val binding = repository.currentBinding(organizationId, connectionId) ?: return null
        if (connection.bindingVersion != binding.version) return null
        return ActiveCredentialContext(connection.providerKey, connection.credentialKind, binding.version)
    }

    fun <T> withActiveCredentialContext(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        operation: (ActiveCredentialContext, ByteArray) -> T
    ): T {
        val context = requireNotNull(activeCredentialContext(organizationId, connectionId)) {
            "Connection has no active credential context"
        }
        val binding = requireNotNull(repository.currentBinding(organizationId, connectionId)) {
            "Connection has no active credential"
        }
        require(binding.version == context.bindingVersion) { "Active credential version changed" }
        return vault.withSecret(organizationId, connectionId, binding.secretReference) { bytes ->
            operation(context, bytes)
        }
    }

    fun <T> withActiveCredential(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        operation: (ByteArray) -> T
    ): T = withActiveCredentialContext(organizationId, connectionId) { _, bytes -> operation(bytes) }

    fun activeConnectionProvider(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId
    ): ProviderKey? {
        if (repository.findOrganization(organizationId)?.status !=
            IntegrationOrganizationStatus.ACTIVE) return null
        val connection = repository.findConnection(organizationId, connectionId)
            ?: return null
        if (connection.status != IntegrationConnectionStatus.ACTIVE) return null
        if (repository.currentBinding(organizationId, connectionId) == null) return null
        return connection.providerKey
    }

    fun registerDestination(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        destinationId: IntegrationDestinationId = IntegrationDestinationId.forConnection(connectionId)
    ): IntegrationDestination {
        require(repository.findOrganization(organizationId)?.status ==
            IntegrationOrganizationStatus.ACTIVE) { "Organization is not active" }
        require(repository.findConnection(organizationId, connectionId)?.status ==
            IntegrationConnectionStatus.ACTIVE) { "Connection is not active" }
        val now = clock.instant()
        val destination = IntegrationDestination(
            organizationId,
            connectionId,
            destinationId,
            IntegrationDestinationStatus.ACTIVE,
            now,
            now
        )
        repository.registerDestination(
            destination,
            audit(
                organizationId,
                connectionId,
                IntegrationAuditAction.DESTINATION_REGISTERED,
                now
            )
        )
        return destination
    }

    fun suspendOrganization(id: IntegrationOrganizationId) = changeOrganizationStatus(
        id,
        IntegrationOrganizationStatus.ACTIVE,
        IntegrationOrganizationStatus.SUSPENDED,
        IntegrationAuditAction.ORGANIZATION_SUSPENDED
    )

    fun resumeOrganization(id: IntegrationOrganizationId) = changeOrganizationStatus(
        id,
        IntegrationOrganizationStatus.SUSPENDED,
        IntegrationOrganizationStatus.ACTIVE,
        IntegrationAuditAction.ORGANIZATION_RESUMED
    )

    fun suspendConnection(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId
    ) = changeConnectionStatus(
        organizationId,
        connectionId,
        IntegrationConnectionStatus.ACTIVE,
        IntegrationConnectionStatus.SUSPENDED,
        IntegrationAuditAction.CONNECTION_SUSPENDED
    )

    fun resumeConnection(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId
    ) = changeConnectionStatus(
        organizationId,
        connectionId,
        IntegrationConnectionStatus.SUSPENDED,
        IntegrationConnectionStatus.ACTIVE,
        IntegrationAuditAction.CONNECTION_RESUMED
    )

    fun suspendDestination(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        destinationId: IntegrationDestinationId
    ) = changeDestinationStatus(
        organizationId,
        connectionId,
        destinationId,
        IntegrationDestinationStatus.ACTIVE,
        IntegrationDestinationStatus.SUSPENDED,
        IntegrationAuditAction.DESTINATION_SUSPENDED
    )

    fun resumeDestination(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        destinationId: IntegrationDestinationId
    ) = changeDestinationStatus(
        organizationId,
        connectionId,
        destinationId,
        IntegrationDestinationStatus.SUSPENDED,
        IntegrationDestinationStatus.ACTIVE,
        IntegrationAuditAction.DESTINATION_RESUMED
    )

    fun revokeConnection(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId
    ) {
        val binding = requireNotNull(repository.currentBinding(organizationId, connectionId)) {
            "Connection has no active credential"
        }
        vault.revoke(organizationId, connectionId, binding.secretReference)
        val now = clock.instant()
        check(
            repository.revokeConnection(
                organizationId,
                connectionId,
                now,
                audit(
                    organizationId,
                    connectionId,
                    IntegrationAuditAction.CONNECTION_REVOKED,
                    now
                )
            )
        ) { "Connection cannot be revoked" }
    }

    private fun changeOrganizationStatus(
        id: IntegrationOrganizationId,
        expected: IntegrationOrganizationStatus,
        target: IntegrationOrganizationStatus,
        action: IntegrationAuditAction
    ) {
        val current = requireNotNull(repository.findOrganization(id))
        val now = clock.instant()
        check(
            repository.changeOrganizationStatus(
                id,
                expected,
                current.copy(status = target, updatedAt = now),
                audit(id, null, action, now)
            )
        ) { "Organization status transition rejected" }
    }

    private fun changeConnectionStatus(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        expected: IntegrationConnectionStatus,
        target: IntegrationConnectionStatus,
        action: IntegrationAuditAction
    ) {
        val now = clock.instant()
        check(
            repository.changeConnectionStatus(
                organizationId,
                connectionId,
                expected,
                target,
                now,
                audit(organizationId, connectionId, action, now)
            )
        ) { "Connection status transition rejected" }
    }

    private fun changeDestinationStatus(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        destinationId: IntegrationDestinationId,
        expected: IntegrationDestinationStatus,
        target: IntegrationDestinationStatus,
        action: IntegrationAuditAction
    ) {
        if (target == IntegrationDestinationStatus.ACTIVE) {
            require(repository.findOrganization(organizationId)?.status ==
                IntegrationOrganizationStatus.ACTIVE) { "Organization is not active" }
            require(repository.findConnection(organizationId, connectionId)?.status ==
                IntegrationConnectionStatus.ACTIVE) { "Connection is not active" }
        }
        val now = clock.instant()
        check(
            repository.changeDestinationStatus(
                organizationId,
                destinationId,
                expected,
                target,
                now,
                audit(organizationId, connectionId, action, now)
            )
        ) { "Destination status transition rejected" }
    }

    private fun audit(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId?,
        action: IntegrationAuditAction,
        now: Instant
    ) = IntegrationAuditEntry(
        auditIds.create(), organizationId, connectionId, action, now, correlationIds.create()
    )
}
