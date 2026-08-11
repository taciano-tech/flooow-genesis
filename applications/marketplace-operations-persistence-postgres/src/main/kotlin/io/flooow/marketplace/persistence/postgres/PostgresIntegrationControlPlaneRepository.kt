package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.CredentialBinding
import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.IntegrationAuditAction
import io.flooow.integration.control.IntegrationAuditEntry
import io.flooow.integration.control.IntegrationAuditEntryId
import io.flooow.integration.control.IntegrationConnection
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationConnectionStatus
import io.flooow.integration.control.IntegrationControlPlaneRepository
import io.flooow.integration.control.IntegrationDestination
import io.flooow.integration.control.IntegrationDestinationId
import io.flooow.integration.control.IntegrationDestinationStatus
import io.flooow.integration.control.IntegrationOrganization
import io.flooow.integration.control.IntegrationOrganizationId
import io.flooow.integration.control.IntegrationOrganizationStatus
import io.flooow.integration.control.ProviderKey
import io.flooow.integration.control.SecretReference
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.flywaydb.core.Flyway

class PostgresIntegrationControlPlaneRepository private constructor(
    private val configuration: PostgresConfiguration
) : IntegrationControlPlaneRepository {
    override fun createOrganization(
        organization: IntegrationOrganization,
        audit: IntegrationAuditEntry
    ) = transaction { connection ->
        connection.prepareStatement(
            "INSERT INTO integration_organization VALUES (?, ?, ?, ?)"
        ).use { statement ->
            statement.setObject(1, organization.id.value)
            statement.setString(2, organization.status.name)
            statement.setTimestamp(3, Timestamp.from(organization.createdAt))
            statement.setTimestamp(4, Timestamp.from(organization.updatedAt))
            statement.executeUpdate()
        }
        insertAudit(connection, audit)
        Unit
    }

    override fun findOrganization(id: IntegrationOrganizationId): IntegrationOrganization? =
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT * FROM integration_organization WHERE organization_id = ?"
            ).use { statement ->
                statement.setObject(1, id.value)
                statement.executeQuery().use { result ->
                    if (result.next()) result.toOrganization() else null
                }
            }
        }

    override fun changeOrganizationStatus(
        id: IntegrationOrganizationId,
        expected: IntegrationOrganizationStatus,
        updated: IntegrationOrganization,
        audit: IntegrationAuditEntry
    ): Boolean = transaction { connection ->
        val changed = connection.prepareStatement(
            "UPDATE integration_organization SET status = ?, updated_at = ? " +
                "WHERE organization_id = ? AND status = ?"
        ).use { statement ->
            statement.setString(1, updated.status.name)
            statement.setTimestamp(2, Timestamp.from(updated.updatedAt))
            statement.setObject(3, id.value)
            statement.setString(4, expected.name)
            statement.executeUpdate() == 1
        }
        if (changed) insertAudit(connection, audit)
        changed
    }

    override fun createConnection(
        connection: IntegrationConnection,
        audit: IntegrationAuditEntry
    ) = transaction { databaseConnection ->
        require(activeOrganization(databaseConnection, connection.organizationId))
        databaseConnection.prepareStatement(
            "INSERT INTO integration_connection " +
                "(organization_id, connection_id, provider_key, credential_kind, status, " +
                "binding_version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setObject(1, connection.organizationId.value)
            statement.setObject(2, connection.id.value)
            statement.setString(3, connection.providerKey.value)
            statement.setString(4, connection.credentialKind.name)
            statement.setString(5, connection.status.name)
            statement.setObject(6, connection.bindingVersion)
            statement.setTimestamp(7, Timestamp.from(connection.createdAt))
            statement.setTimestamp(8, Timestamp.from(connection.updatedAt))
            statement.executeUpdate()
        }
        insertAudit(databaseConnection, audit)
        Unit
    }

    override fun findConnection(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId
    ): IntegrationConnection? = connection().use { connection ->
        connection.prepareStatement(
            "SELECT * FROM integration_connection WHERE organization_id = ? AND connection_id = ?"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            statement.executeQuery().use { result ->
                if (result.next()) result.toConnection() else null
            }
        }
    }

    override fun bindInitialCredential(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean = transaction { connection ->
        if (!activeOrganization(connection, organizationId)) return@transaction false
        val updated = connection.prepareStatement(
            "UPDATE integration_connection SET status = 'ACTIVE', binding_version = 1, " +
                "updated_at = ? WHERE organization_id = ? AND connection_id = ? " +
                "AND status = 'DRAFT' AND binding_version IS NULL"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(now))
            statement.setObject(2, organizationId.value)
            statement.setObject(3, connectionId.value)
            statement.executeUpdate() == 1
        }
        if (updated) {
            insertBinding(connection, organizationId, connectionId, 1, reference, now)
            insertAudit(connection, audit)
        }
        updated
    }

    override fun rotateCredential(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        expectedVersion: Int,
        newReference: SecretReference,
        now: Instant,
        audit: IntegrationAuditEntry
    ): SecretReference? = transaction { connection ->
        if (!activeOrganization(connection, organizationId)) return@transaction null
        val oldReference = connection.prepareStatement(
            "SELECT secret_ref FROM integration_credential_binding " +
                "WHERE organization_id = ? AND connection_id = ? AND binding_version = ? " +
                "AND revoked_at IS NULL FOR UPDATE"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            statement.setInt(3, expectedVersion)
            statement.executeQuery().use { result ->
                if (result.next()) SecretReference.of(result.getString(1)) else null
            }
        } ?: return@transaction null
        val updated = connection.prepareStatement(
            "UPDATE integration_connection SET binding_version = ?, updated_at = ? " +
                "WHERE organization_id = ? AND connection_id = ? AND status = 'ACTIVE' " +
                "AND binding_version = ?"
        ).use { statement ->
            statement.setInt(1, expectedVersion + 1)
            statement.setTimestamp(2, Timestamp.from(now))
            statement.setObject(3, organizationId.value)
            statement.setObject(4, connectionId.value)
            statement.setInt(5, expectedVersion)
            statement.executeUpdate() == 1
        }
        if (!updated) return@transaction null
        connection.prepareStatement(
            "UPDATE integration_credential_binding SET revoked_at = ? WHERE organization_id = ? " +
                "AND connection_id = ? AND binding_version = ?"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(now))
            statement.setObject(2, organizationId.value)
            statement.setObject(3, connectionId.value)
            statement.setInt(4, expectedVersion)
            statement.executeUpdate()
        }
        insertBinding(
            connection, organizationId, connectionId, expectedVersion + 1, newReference, now
        )
        insertAudit(connection, audit)
        oldReference
    }

    override fun currentBinding(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId
    ): CredentialBinding? = connection().use { connection ->
        connection.prepareStatement(
            "SELECT * FROM integration_credential_binding WHERE organization_id = ? " +
                "AND connection_id = ? AND revoked_at IS NULL"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            statement.executeQuery().use { result ->
                if (result.next()) result.toBinding() else null
            }
        }
    }

    override fun changeConnectionStatus(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        expected: IntegrationConnectionStatus,
        target: IntegrationConnectionStatus,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean = transaction { connection ->
        if (target == IntegrationConnectionStatus.ACTIVE &&
            !activeOrganization(connection, organizationId)) return@transaction false
        val changed = connection.prepareStatement(
            "UPDATE integration_connection SET status = ?, updated_at = ? WHERE " +
                "organization_id = ? AND connection_id = ? AND status = ?"
        ).use { statement ->
            statement.setString(1, target.name)
            statement.setTimestamp(2, Timestamp.from(now))
            statement.setObject(3, organizationId.value)
            statement.setObject(4, connectionId.value)
            statement.setString(5, expected.name)
            statement.executeUpdate() == 1
        }
        if (changed) insertAudit(connection, audit)
        changed
    }

    override fun revokeConnection(
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean = transaction { connection ->
        connection.prepareStatement(
            "UPDATE integration_credential_binding SET revoked_at = COALESCE(revoked_at, ?) " +
                "WHERE organization_id = ? AND connection_id = ?"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(now))
            statement.setObject(2, organizationId.value)
            statement.setObject(3, connectionId.value)
            statement.executeUpdate()
        }
        val changed = connection.prepareStatement(
            "UPDATE integration_connection SET status = 'REVOKED', updated_at = ? WHERE " +
                "organization_id = ? AND connection_id = ? AND status <> 'REVOKED'"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(now))
            statement.setObject(2, organizationId.value)
            statement.setObject(3, connectionId.value)
            statement.executeUpdate() == 1
        }
        if (changed) insertAudit(connection, audit)
        changed
    }

    override fun registerDestination(
        destination: IntegrationDestination,
        audit: IntegrationAuditEntry
    ) = transaction { connection ->
        require(activeOrganization(connection, destination.organizationId))
        require(
            connectionStatus(connection, destination.organizationId, destination.connectionId) ==
                IntegrationConnectionStatus.ACTIVE
        )
        connection.prepareStatement(
            "INSERT INTO integration_destination VALUES (?, ?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setObject(1, destination.organizationId.value)
            statement.setObject(2, destination.connectionId.value)
            statement.setString(3, destination.id.value)
            statement.setString(4, destination.status.name)
            statement.setTimestamp(5, Timestamp.from(destination.createdAt))
            statement.setTimestamp(6, Timestamp.from(destination.updatedAt))
            statement.executeUpdate()
        }
        insertAudit(connection, audit)
        Unit
    }

    override fun findDestination(
        organizationId: IntegrationOrganizationId,
        destinationId: IntegrationDestinationId
    ): IntegrationDestination? = connection().use { connection ->
        connection.prepareStatement(
            "SELECT * FROM integration_destination WHERE organization_id = ? AND destination_id = ?"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setString(2, destinationId.value)
            statement.executeQuery().use { result ->
                if (result.next()) result.toDestination() else null
            }
        }
    }

    override fun changeDestinationStatus(
        organizationId: IntegrationOrganizationId,
        destinationId: IntegrationDestinationId,
        expected: IntegrationDestinationStatus,
        target: IntegrationDestinationStatus,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean = transaction { connection ->
        val changed = connection.prepareStatement(
            "UPDATE integration_destination SET status = ?, updated_at = ? WHERE " +
                "organization_id = ? AND destination_id = ? AND status = ?"
        ).use { statement ->
            statement.setString(1, target.name)
            statement.setTimestamp(2, Timestamp.from(now))
            statement.setObject(3, organizationId.value)
            statement.setString(4, destinationId.value)
            statement.setString(5, expected.name)
            statement.executeUpdate() == 1
        }
        if (changed) insertAudit(connection, audit)
        changed
    }

    override fun auditEntries(
        organizationId: IntegrationOrganizationId
    ): List<IntegrationAuditEntry> = connection().use { connection ->
        connection.prepareStatement(
            "SELECT * FROM integration_control_audit WHERE organization_id = ? " +
                "ORDER BY occurred_at, audit_id"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(result.toAudit()) }
            }
        }
    }

    private fun activeOrganization(
        connection: Connection,
        id: IntegrationOrganizationId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_organization WHERE organization_id = ? AND status = 'ACTIVE'"
    ).use { statement ->
        statement.setObject(1, id.value)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun connectionStatus(
        connection: Connection,
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId
    ): IntegrationConnectionStatus? = connection.prepareStatement(
        "SELECT status FROM integration_connection WHERE organization_id = ? AND connection_id = ?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, connectionId.value)
        statement.executeQuery().use { result ->
            if (result.next()) IntegrationConnectionStatus.valueOf(result.getString(1)) else null
        }
    }

    private fun insertBinding(
        connection: Connection,
        organizationId: IntegrationOrganizationId,
        connectionId: IntegrationConnectionId,
        version: Int,
        reference: SecretReference,
        now: Instant
    ) = connection.prepareStatement(
        "INSERT INTO integration_credential_binding " +
            "(organization_id, connection_id, binding_version, secret_ref, bound_at) " +
            "VALUES (?, ?, ?, ?, ?)"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, connectionId.value)
        statement.setInt(3, version)
        statement.setString(4, reference.encodedForPersistence())
        statement.setTimestamp(5, Timestamp.from(now))
        statement.executeUpdate()
    }

    private fun insertAudit(connection: Connection, audit: IntegrationAuditEntry) =
        connection.prepareStatement(
            "INSERT INTO integration_control_audit VALUES (?, ?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setObject(1, audit.organizationId.value)
            statement.setObject(2, audit.id.value)
            statement.setObject(3, audit.connectionId?.value)
            statement.setString(4, audit.action.name)
            statement.setTimestamp(5, Timestamp.from(audit.occurredAt))
            statement.setObject(6, audit.correlationId)
            statement.executeUpdate()
        }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url, configuration.user, configuration.password
    )

    private fun <T> transaction(block: (Connection) -> T): T = connection().use { connection ->
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }

    companion object {
        fun connect(configuration: PostgresConfiguration): PostgresIntegrationControlPlaneRepository {
            Flyway.configure().dataSource(
                configuration.url, configuration.user, configuration.password
            ).load().migrate()
            return PostgresIntegrationControlPlaneRepository(configuration)
        }
    }
}

private fun ResultSet.toOrganization() = IntegrationOrganization(
    IntegrationOrganizationId(getObject("organization_id", UUID::class.java)),
    IntegrationOrganizationStatus.valueOf(getString("status")),
    getTimestamp("created_at").toInstant(),
    getTimestamp("updated_at").toInstant()
)

private fun ResultSet.toConnection() = IntegrationConnection(
    IntegrationOrganizationId(getObject("organization_id", UUID::class.java)),
    IntegrationConnectionId(getObject("connection_id", UUID::class.java)),
    ProviderKey.of(getString("provider_key")),
    CredentialKind.valueOf(getString("credential_kind")),
    IntegrationConnectionStatus.valueOf(getString("status")),
    getObject("binding_version")?.let { (it as Number).toInt() },
    getTimestamp("created_at").toInstant(),
    getTimestamp("updated_at").toInstant()
)

private fun ResultSet.toBinding() = CredentialBinding(
    IntegrationOrganizationId(getObject("organization_id", UUID::class.java)),
    IntegrationConnectionId(getObject("connection_id", UUID::class.java)),
    getInt("binding_version"),
    SecretReference.of(getString("secret_ref")),
    getTimestamp("bound_at").toInstant(),
    getTimestamp("revoked_at")?.toInstant()
)

private fun ResultSet.toDestination() = IntegrationDestination(
    IntegrationOrganizationId(getObject("organization_id", UUID::class.java)),
    IntegrationConnectionId(getObject("connection_id", UUID::class.java)),
    IntegrationDestinationId.of(getString("destination_id")),
    IntegrationDestinationStatus.valueOf(getString("status")),
    getTimestamp("created_at").toInstant(),
    getTimestamp("updated_at").toInstant()
)

private fun ResultSet.toAudit() = IntegrationAuditEntry(
    IntegrationAuditEntryId(getObject("audit_id", UUID::class.java)),
    IntegrationOrganizationId(getObject("organization_id", UUID::class.java)),
    getObject("connection_id", UUID::class.java)?.let(::IntegrationConnectionId),
    IntegrationAuditAction.valueOf(getString("action")),
    getTimestamp("occurred_at").toInstant(),
    getObject("correlation_id", UUID::class.java)
)
