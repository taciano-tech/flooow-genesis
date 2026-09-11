package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.connector.ConnectorPageCommitKey
import io.flooow.integration.connector.ConnectorPageCommitResult
import io.flooow.integration.connector.ConnectorPageCommitter
import io.flooow.integration.connector.ConnectorProgress
import io.flooow.integration.connector.ConnectorProgressProtectionContext
import io.flooow.integration.connector.ConnectorProgressProtector
import io.flooow.integration.connector.ConnectorRecord
import io.flooow.integration.connector.SealedConnectorProgress
import io.flooow.integration.connector.VersionedConnectorProgress
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.source.InventorySourceBalanceCapability
import io.flooow.integration.inventory.source.InventorySourceBalanceRecord
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import kotlin.reflect.KClass

class PostgresInventorySourceBalanceCommitter(
    private val configuration: PostgresConfiguration,
    private val protector: ConnectorProgressProtector,
    private val clock: Clock = Clock.systemUTC()
) : ConnectorPageCommitter {
    override val capability: ConnectorCapability = InventorySourceBalanceCapability.KEY
    override val recordType: KClass<out ConnectorRecord> = InventorySourceBalanceRecord::class

    override fun load(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability
    ): VersionedConnectorProgress {
        require(capability == this.capability) { "Connector capability unavailable" }
        connection().use { connection ->
            requireActive(connection, organizationId, connectionId)
            connection.prepareStatement(
                "SELECT progress_version, progress_envelope, exhausted, last_observed_at " +
                    "FROM integration_connector_progress WHERE organization_id=? " +
                    "AND connection_id=? AND capability=?"
            ).use { statement ->
                scope(statement, organizationId, connectionId, capability)
                statement.executeQuery().use { result ->
                    if (!result.next()) return VersionedConnectorProgress(0, null)
                    val version = result.getLong("progress_version")
                    val exhausted = result.getBoolean("exhausted")
                    val observedAt = result.getTimestamp("last_observed_at")?.toInstant()
                    val envelopeBytes = result.getBytes("progress_envelope")
                    val progress = envelopeBytes?.let {
                        val sealed = SealedConnectorProgress.take(it)
                        try {
                            val plaintext = protector.open(
                                context(organizationId, connectionId, capability, version),
                                sealed
                            )
                            ConnectorProgress.take(plaintext)
                        } finally {
                            sealed.close()
                        }
                    }
                    return VersionedConnectorProgress(version, progress, exhausted, observedAt)
                }
            }
        }
    }

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
        require(capability == this.capability) { "Connector capability unavailable" }
        require(expectedProgressVersion in 0 until Long.MAX_VALUE) {
            "Invalid connector progress version"
        }
        val typed = records.map {
            require(it is InventorySourceBalanceRecord) { "Connector record type unavailable" }
            it
        }
        require(typed.size <= 1_000) { "Connector page exceeds record limit" }
        require(exhausted == (nextProgress == null)) { "Invalid connector progress shape" }
        val nextVersion = expectedProgressVersion + 1
        val sealed = nextProgress?.useBytes { plaintext ->
            protector.seal(context(organizationId, connectionId, capability, nextVersion), plaintext)
        }
        return try {
            transaction { connection ->
                requireActive(connection, organizationId, connectionId)
                ensureProgressRow(connection, organizationId, connectionId, capability)
                val current = lockProgress(connection, organizationId, connectionId, capability)
                duplicateResult(
                    connection, organizationId, connectionId, capability, pageCommitKey,
                    expectedProgressVersion, typed.size, exhausted, observedAt
                )?.let { return@transaction it }
                if (current.first != expectedProgressVersion || current.second) {
                    return@transaction ConnectorPageCommitResult.STALE_PROGRESS
                }
                insertPage(
                    connection, organizationId, connectionId, capability, expectedProgressVersion,
                    pageCommitKey, typed.size, exhausted, observedAt
                )
                typed.forEachIndexed { index, record ->
                    insertRecord(
                        connection, organizationId, connectionId, capability,
                        expectedProgressVersion, index, record
                    )
                }
                updateProgress(
                    connection, organizationId, connectionId, capability, expectedProgressVersion,
                    nextVersion, sealed, exhausted, observedAt
                )
                ConnectorPageCommitResult.COMMITTED
            }
        } finally {
            sealed?.close()
        }
    }

    private fun requireActive(
        connection: Connection,
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId
    ) {
        connection.prepareStatement(
            "SELECT 1 FROM integration_organization o JOIN integration_connection c " +
                "ON c.organization_id=o.organization_id JOIN integration_credential_binding b " +
                "ON b.organization_id=c.organization_id AND b.connection_id=c.connection_id " +
                "AND b.revoked_at IS NULL WHERE o.organization_id=? AND c.connection_id=? " +
                "AND o.status='ACTIVE' AND c.status='ACTIVE' FOR SHARE OF o, c, b"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            require(statement.executeQuery().use(ResultSet::next)) { "Connection unavailable" }
        }
    }

    private fun ensureProgressRow(
        connection: Connection, organizationId: OrganizationId,
        connectionId: IntegrationConnectionId, capability: ConnectorCapability
    ) {
        connection.prepareStatement(
            "INSERT INTO integration_connector_progress " +
                "(organization_id,connection_id,capability,progress_version,progress_envelope," +
                "exhausted,last_observed_at,updated_at) VALUES (?,?,?,0,NULL,false,NULL,?) " +
                "ON CONFLICT DO NOTHING"
        ).use { statement ->
            scope(statement, organizationId, connectionId, capability)
            statement.setTimestamp(4, Timestamp.from(clock.instant()))
            statement.executeUpdate()
        }
    }

    private fun lockProgress(
        connection: Connection, organizationId: OrganizationId,
        connectionId: IntegrationConnectionId, capability: ConnectorCapability
    ): Pair<Long, Boolean> = connection.prepareStatement(
        "SELECT progress_version, exhausted FROM integration_connector_progress " +
            "WHERE organization_id=? AND connection_id=? AND capability=? FOR UPDATE"
    ).use { statement ->
        scope(statement, organizationId, connectionId, capability)
        statement.executeQuery().use { result ->
            check(result.next()) { "Connector progress unavailable" }
            result.getLong(1) to result.getBoolean(2)
        }
    }

    private fun duplicateResult(
        connection: Connection, organizationId: OrganizationId,
        connectionId: IntegrationConnectionId, capability: ConnectorCapability,
        key: ConnectorPageCommitKey, version: Long, count: Int,
        exhausted: Boolean, observedAt: Instant
    ): ConnectorPageCommitResult? = key.useBytes { bytes ->
        connection.prepareStatement(
            "SELECT input_progress_version,record_count,exhausted,observed_at " +
                "FROM integration_connector_page_commit WHERE organization_id=? " +
                "AND connection_id=? AND capability=? AND page_commit_key=?"
        ).use { statement ->
            scope(statement, organizationId, connectionId, capability)
            statement.setBytes(4, bytes)
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                check(
                    result.getLong(1) == version && result.getInt(2) == count &&
                        result.getBoolean(3) == exhausted &&
                        result.getTimestamp(4).toInstant() == observedAt
                ) { "Connector page integrity failure" }
                ConnectorPageCommitResult.ALREADY_COMMITTED
            }
        }
    }

    private fun insertPage(
        connection: Connection, organizationId: OrganizationId,
        connectionId: IntegrationConnectionId, capability: ConnectorCapability,
        version: Long, key: ConnectorPageCommitKey, count: Int,
        exhausted: Boolean, observedAt: Instant
    ) = key.useBytes { bytes ->
        connection.prepareStatement(
            "INSERT INTO integration_connector_page_commit VALUES (?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            scope(statement, organizationId, connectionId, capability)
            statement.setLong(4, version)
            statement.setBytes(5, bytes)
            statement.setInt(6, count)
            statement.setBoolean(7, exhausted)
            statement.setTimestamp(8, Timestamp.from(observedAt))
            statement.setTimestamp(9, Timestamp.from(clock.instant()))
            check(statement.executeUpdate() == 1)
        }
    }

    private fun insertRecord(
        connection: Connection, organizationId: OrganizationId,
        connectionId: IntegrationConnectionId, capability: ConnectorCapability,
        version: Long, ordinal: Int, record: InventorySourceBalanceRecord
    ) {
        connection.prepareStatement(
            "INSERT INTO integration_inventory_source_balance VALUES " +
                "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            scope(statement, organizationId, connectionId, capability)
            statement.setLong(4, version)
            statement.setInt(5, ordinal)
            statement.setString(6, record.sourceItemReference.encodedForPersistence())
            statement.setString(7, record.sourceLocationReference?.encodedForPersistence())
            statement.setString(8, record.sourceSku?.encodedForPersistence())
            statement.setString(9, record.sourceUnitCode?.encodedForPersistence())
            statement.setTimestamp(10, record.sourceUpdatedAt?.let(Timestamp::from))
            statement.setString(11, record.sourceVersion?.encodedForPersistence())
            statement.setBigDecimal(12, record.availableToSell?.valueForPersistence())
            statement.setBigDecimal(13, record.onHand?.valueForPersistence())
            statement.setBigDecimal(14, record.reserved?.valueForPersistence())
            statement.setBigDecimal(15, record.pendingInbound?.valueForPersistence())
            statement.setBigDecimal(16, record.pendingOutbound?.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
    }

    private fun updateProgress(
        connection: Connection, organizationId: OrganizationId,
        connectionId: IntegrationConnectionId, capability: ConnectorCapability,
        expected: Long, next: Long, sealed: SealedConnectorProgress?,
        exhausted: Boolean, observedAt: Instant
    ) {
        connection.prepareStatement(
            "UPDATE integration_connector_progress SET progress_version=?,progress_envelope=?," +
                "exhausted=?,last_observed_at=?,updated_at=? WHERE organization_id=? " +
                "AND connection_id=? AND capability=? AND progress_version=?"
        ).use { statement ->
            statement.setLong(1, next)
            sealed?.useBytes { statement.setBytes(2, it) } ?: statement.setBytes(2, null)
            statement.setBoolean(3, exhausted)
            statement.setTimestamp(4, Timestamp.from(observedAt))
            statement.setTimestamp(5, Timestamp.from(clock.instant()))
            statement.setObject(6, organizationId.value)
            statement.setObject(7, connectionId.value)
            statement.setString(8, capability.value)
            statement.setLong(9, expected)
            check(statement.executeUpdate() == 1) { "Connector progress conflict" }
        }
    }

    private fun context(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId,
        capability: ConnectorCapability, version: Long
    ) = ConnectorProgressProtectionContext(organizationId, connectionId, capability, version)

    private fun scope(
        statement: java.sql.PreparedStatement, organizationId: OrganizationId,
        connectionId: IntegrationConnectionId, capability: ConnectorCapability
    ) {
        statement.setObject(1, organizationId.value)
        statement.setObject(2, connectionId.value)
        statement.setString(3, capability.value)
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url, configuration.user, configuration.password
    )

    private fun <T> transaction(operation: (Connection) -> T): T = connection().use { connection ->
        connection.autoCommit = false
        try {
            operation(connection).also { connection.commit() }
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }
}
