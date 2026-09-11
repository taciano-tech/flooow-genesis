package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.ChangeSequenceCheckpoint
import io.flooow.marketplace.operations.economics.evidence.CheckpointAdvanceResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChange
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChangeFeed
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChangeFeedResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceChangeKind
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.operations.economics.evidence.ProjectionName
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.Collections
import java.util.UUID

class PostgresMarketplaceEconomicEvidenceChangeFeed(
    private val configuration: PostgresConfiguration
) : MarketplaceEconomicEvidenceChangeFeed {

    override fun changesSince(
        organizationId: OrganizationId,
        checkpoint: ChangeSequenceCheckpoint,
        limit: Int
    ): MarketplaceEconomicEvidenceChangeFeedResult<List<MarketplaceEconomicEvidenceChange>> {
        val validatedLimit = MarketplaceEconomicEvidenceChangeFeed.requireValidLimit(limit)
        return try {
            connection().use { connection ->
                connection.prepareStatement(CHANGES_SINCE_SQL).use { statement ->
                    statement.setObject(1, organizationId.value)
                    statement.setLong(2, checkpoint.valueForPersistence())
                    statement.setInt(3, validatedLimit)
                    statement.executeQuery().use { result ->
                        val changes = mutableListOf<MarketplaceEconomicEvidenceChange>()
                        while (result.next()) changes += result.toChange()
                        MarketplaceEconomicEvidenceChangeFeedResult.Success(
                            Collections.unmodifiableList(changes)
                        )
                    }
                }
            }
        } catch (_: Exception) {
            MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure
        }
    }

    override fun organizationsWithPendingChanges(
        projectionName: ProjectionName,
        limit: Int
    ): MarketplaceEconomicEvidenceChangeFeedResult<List<OrganizationId>> {
        val validatedLimit = MarketplaceEconomicEvidenceChangeFeed.requireValidLimit(limit)
        return try {
            connection().use { connection ->
                connection.prepareStatement(PENDING_ORGANIZATIONS_SQL).use { statement ->
                    statement.setString(1, projectionName.valueForPersistence())
                    statement.setInt(2, validatedLimit)
                    statement.executeQuery().use { result ->
                        val organizations = mutableListOf<OrganizationId>()
                        while (result.next()) {
                            organizations += OrganizationId(
                                result.getObject("organization_id", UUID::class.java)
                            )
                        }
                        MarketplaceEconomicEvidenceChangeFeedResult.Success(
                            Collections.unmodifiableList(organizations)
                        )
                    }
                }
            }
        } catch (_: Exception) {
            MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure
        }
    }

    override fun currentCheckpoint(
        organizationId: OrganizationId,
        projectionName: ProjectionName
    ): MarketplaceEconomicEvidenceChangeFeedResult<ChangeSequenceCheckpoint> = try {
        connection().use { connection ->
            val checkpoint = readCheckpoint(connection, organizationId, projectionName)
                ?: ChangeSequenceCheckpoint.NONE
            MarketplaceEconomicEvidenceChangeFeedResult.Success(checkpoint)
        }
    } catch (_: Exception) {
        MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure
    }

    override fun advanceCheckpoint(
        organizationId: OrganizationId,
        projectionName: ProjectionName,
        expected: ChangeSequenceCheckpoint,
        next: ChangeSequenceCheckpoint
    ): MarketplaceEconomicEvidenceChangeFeedResult<CheckpointAdvanceResult> {
        if (next <= expected) {
            return MarketplaceEconomicEvidenceChangeFeedResult.Success(
                CheckpointAdvanceResult.Regression
            )
        }

        return try {
            val outcome = transaction { connection ->
                if (!organizationExists(connection, organizationId)) {
                    return@transaction AdvanceOutcome.IntegrityFailure
                }
                if (!destinationExists(connection, organizationId, next)) {
                    return@transaction AdvanceOutcome.InvalidDestination
                }

                val durable = lockCheckpoint(connection, organizationId, projectionName)
                if (durable == null) {
                    if (expected != ChangeSequenceCheckpoint.NONE) {
                        return@transaction AdvanceOutcome.Success(
                            CheckpointAdvanceResult.Stale(ChangeSequenceCheckpoint.NONE)
                        )
                    }
                    if (insertCheckpoint(connection, organizationId, projectionName, next)) {
                        return@transaction AdvanceOutcome.Success(
                            CheckpointAdvanceResult.Advanced(next)
                        )
                    }
                    val concurrent = lockCheckpoint(connection, organizationId, projectionName)
                        ?: return@transaction AdvanceOutcome.IntegrityFailure
                    return@transaction AdvanceOutcome.Success(
                        CheckpointAdvanceResult.Stale(concurrent)
                    )
                }

                if (durable != expected) {
                    return@transaction AdvanceOutcome.Success(
                        CheckpointAdvanceResult.Stale(durable)
                    )
                }

                if (!updateCheckpoint(connection, organizationId, projectionName, expected, next)) {
                    val concurrent = lockCheckpoint(connection, organizationId, projectionName)
                        ?: return@transaction AdvanceOutcome.IntegrityFailure
                    return@transaction AdvanceOutcome.Success(
                        CheckpointAdvanceResult.Stale(concurrent)
                    )
                }

                AdvanceOutcome.Success(CheckpointAdvanceResult.Advanced(next))
            }

            when (outcome) {
                AdvanceOutcome.IntegrityFailure ->
                    MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure
                AdvanceOutcome.InvalidDestination ->
                    throw InvalidCheckpointDestinationException()
                is AdvanceOutcome.Success ->
                    MarketplaceEconomicEvidenceChangeFeedResult.Success(outcome.value)
            }
        } catch (_: InvalidCheckpointDestinationException) {
            throw IllegalArgumentException("Checkpoint destination is invalid")
        } catch (_: Exception) {
            MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure
        }
    }

    private fun ResultSet.toChange(): MarketplaceEconomicEvidenceChange =
        MarketplaceEconomicEvidenceChange(
            subject = MarketplaceEconomicEvidenceSubject(
                organizationId = OrganizationId(
                    getObject("organization_id", UUID::class.java)
                ),
                orderId = MarketplaceOrderId(
                    getObject("marketplace_order_id", UUID::class.java)
                ),
                marketplace = MarketplaceKey(getString("marketplace_key")),
                externalOrderId = MarketplaceExternalOrderId(getString("external_order_id")),
                currency = MarketplaceCurrency(getString("currency").trim())
            ),
            evidenceVersion = MarketplaceEconomicEvidenceVersion(getLong("evidence_version")),
            changeSequence = ChangeSequenceCheckpoint(getLong("change_sequence")),
            changeKind = MarketplaceEconomicEvidenceChangeKind.valueOf(getString("change_kind"))
        )

    private fun organizationExists(
        connection: Connection,
        organizationId: OrganizationId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_organization WHERE organization_id=?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.executeQuery().use { result -> result.next() }
    }

    private fun destinationExists(
        connection: Connection,
        organizationId: OrganizationId,
        next: ChangeSequenceCheckpoint
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM marketplace_economic_evidence_update " +
            "WHERE organization_id=? AND change_sequence=?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setLong(2, next.valueForPersistence())
        statement.executeQuery().use { result -> result.next() }
    }

    private fun readCheckpoint(
        connection: Connection,
        organizationId: OrganizationId,
        projectionName: ProjectionName
    ): ChangeSequenceCheckpoint? = connection.prepareStatement(
        "SELECT last_change_sequence " +
            "FROM marketplace_economic_evidence_projection_checkpoint " +
            "WHERE organization_id=? AND projection_name=?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setString(2, projectionName.valueForPersistence())
        statement.executeQuery().use { result ->
            if (result.next()) ChangeSequenceCheckpoint(result.getLong(1)) else null
        }
    }

    private fun lockCheckpoint(
        connection: Connection,
        organizationId: OrganizationId,
        projectionName: ProjectionName
    ): ChangeSequenceCheckpoint? = connection.prepareStatement(
        "SELECT last_change_sequence " +
            "FROM marketplace_economic_evidence_projection_checkpoint " +
            "WHERE organization_id=? AND projection_name=? FOR UPDATE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setString(2, projectionName.valueForPersistence())
        statement.executeQuery().use { result ->
            if (result.next()) ChangeSequenceCheckpoint(result.getLong(1)) else null
        }
    }

    private fun insertCheckpoint(
        connection: Connection,
        organizationId: OrganizationId,
        projectionName: ProjectionName,
        next: ChangeSequenceCheckpoint
    ): Boolean = connection.prepareStatement(
        "INSERT INTO marketplace_economic_evidence_projection_checkpoint " +
            "(organization_id,projection_name,last_change_sequence,updated_at) " +
            "VALUES (?,?,?,transaction_timestamp()) ON CONFLICT DO NOTHING"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setString(2, projectionName.valueForPersistence())
        statement.setLong(3, next.valueForPersistence())
        statement.executeUpdate() == 1
    }

    private fun updateCheckpoint(
        connection: Connection,
        organizationId: OrganizationId,
        projectionName: ProjectionName,
        expected: ChangeSequenceCheckpoint,
        next: ChangeSequenceCheckpoint
    ): Boolean = connection.prepareStatement(
        "UPDATE marketplace_economic_evidence_projection_checkpoint " +
            "SET last_change_sequence=? " +
            "WHERE organization_id=? AND projection_name=? AND last_change_sequence=?"
    ).use { statement ->
        statement.setLong(1, next.valueForPersistence())
        statement.setObject(2, organizationId.value)
        statement.setString(3, projectionName.valueForPersistence())
        statement.setLong(4, expected.valueForPersistence())
        statement.executeUpdate() == 1
    }

    private fun <T> transaction(block: (Connection) -> T): T = connection().use { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private sealed interface AdvanceOutcome {
        data class Success(val value: CheckpointAdvanceResult) : AdvanceOutcome
        data object InvalidDestination : AdvanceOutcome
        data object IntegrityFailure : AdvanceOutcome
    }

    private class InvalidCheckpointDestinationException : RuntimeException()

    companion object {
        private const val CHANGES_SINCE_SQL: String =
            "SELECT s.organization_id,s.marketplace_order_id,s.marketplace_key," +
                "s.external_order_id,s.currency,u.evidence_version,u.change_sequence,u.change_kind " +
                "FROM marketplace_economic_evidence_update u " +
                "JOIN marketplace_economic_evidence_subject s " +
                "ON s.organization_id=u.organization_id " +
                "AND s.marketplace_order_id=u.marketplace_order_id " +
                "WHERE u.organization_id=? AND u.change_sequence>? " +
                "ORDER BY u.change_sequence ASC LIMIT ?"

        private const val PENDING_ORGANIZATIONS_SQL: String =
            "SELECT o.organization_id " +
                "FROM integration_organization o " +
                "LEFT JOIN marketplace_economic_evidence_projection_checkpoint c " +
                "ON c.organization_id=o.organization_id " +
                "AND c.projection_name=? " +
                "WHERE EXISTS (" +
                "SELECT 1 FROM marketplace_economic_evidence_update u " +
                "WHERE u.organization_id=o.organization_id " +
                "AND u.change_sequence>COALESCE(c.last_change_sequence,0)" +
                ") ORDER BY o.organization_id ASC LIMIT ?"
    }
}
