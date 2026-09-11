package io.flooow.research.exp0006

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.persistence.postgres.PostgresConfiguration
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID

class ExperimentalPostgresChangeFeed(
    private val configuration: PostgresConfiguration
) : ExperimentalMarketplaceEconomicEvidenceChangeFeed {

    override fun changesSince(
        organizationId: OrganizationId,
        checkpoint: ExperimentalChangeSequenceCheckpoint,
        limit: Int
    ): List<ExperimentalMarketplaceEconomicEvidenceChange> {
        requireLimit(limit)
        return connection().use { connection ->
            connection.prepareStatement(CHANGES_SINCE_SQL).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setLong(2, checkpoint.valueForPersistence())
                statement.setInt(3, limit)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.toChange())
                    }
                }
            }
        }
    }

    override fun organizationsWithPendingChanges(
        projectionName: ExperimentalProjectionName,
        limit: Int
    ): List<OrganizationId> {
        requireLimit(limit)
        return connection().use { connection ->
            connection.prepareStatement(PENDING_ORGANIZATIONS_SQL).use { statement ->
                statement.setString(1, projectionName.valueForPersistence())
                statement.setInt(2, limit)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(OrganizationId(result.getObject("organization_id", UUID::class.java)))
                        }
                    }
                }
            }
        }
    }

    override fun currentCheckpoint(
        organizationId: OrganizationId,
        projectionName: ExperimentalProjectionName
    ): ExperimentalChangeSequenceCheckpoint = connection().use { connection ->
        checkpoint(connection, organizationId, projectionName)
            ?: ExperimentalChangeSequenceCheckpoint.NONE
    }

    override fun advanceCheckpoint(
        organizationId: OrganizationId,
        projectionName: ExperimentalProjectionName,
        expected: ExperimentalChangeSequenceCheckpoint,
        next: ExperimentalChangeSequenceCheckpoint
    ): ExperimentalCheckpointAdvanceResult {
        if (next <= expected) return ExperimentalCheckpointAdvanceResult.Regression
        return transaction { connection ->
            if (updateCheckpoint(connection, organizationId, projectionName, expected, next)) {
                return@transaction ExperimentalCheckpointAdvanceResult.Advanced(next)
            }

            val actual = checkpoint(connection, organizationId, projectionName)
            if (actual != null) {
                return@transaction ExperimentalCheckpointAdvanceResult.Stale(actual)
            }
            if (expected != ExperimentalChangeSequenceCheckpoint.NONE) {
                return@transaction ExperimentalCheckpointAdvanceResult.Stale(
                    ExperimentalChangeSequenceCheckpoint.NONE
                )
            }

            if (insertCheckpoint(connection, organizationId, projectionName, next)) {
                ExperimentalCheckpointAdvanceResult.Advanced(next)
            } else {
                ExperimentalCheckpointAdvanceResult.Stale(
                    checkpoint(connection, organizationId, projectionName)
                        ?: ExperimentalChangeSequenceCheckpoint.NONE
                )
            }
        }
    }

    fun explainOrganizationsWithPendingChanges(
        projectionName: ExperimentalProjectionName,
        limit: Int
    ): List<String> {
        requireLimit(limit)
        return connection().use { connection ->
            connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS) $PENDING_ORGANIZATIONS_SQL").use { statement ->
                statement.setString(1, projectionName.valueForPersistence())
                statement.setInt(2, limit)
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(result.getString(1)) }
                }
            }
        }
    }

    private fun updateCheckpoint(
        connection: Connection,
        organizationId: OrganizationId,
        projectionName: ExperimentalProjectionName,
        expected: ExperimentalChangeSequenceCheckpoint,
        next: ExperimentalChangeSequenceCheckpoint
    ): Boolean = connection.prepareStatement(
        "UPDATE projection_checkpoint SET last_change_sequence=? " +
            "WHERE organization_id=? AND projection_name=? AND last_change_sequence=?"
    ).use { statement ->
        statement.setLong(1, next.valueForPersistence())
        statement.setObject(2, organizationId.value)
        statement.setString(3, projectionName.valueForPersistence())
        statement.setLong(4, expected.valueForPersistence())
        statement.executeUpdate() == 1
    }

    private fun insertCheckpoint(
        connection: Connection,
        organizationId: OrganizationId,
        projectionName: ExperimentalProjectionName,
        next: ExperimentalChangeSequenceCheckpoint
    ): Boolean = connection.prepareStatement(
        "INSERT INTO projection_checkpoint " +
            "(organization_id,projection_name,last_change_sequence,updated_at) " +
            "VALUES (?,?,?,transaction_timestamp()) ON CONFLICT DO NOTHING"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setString(2, projectionName.valueForPersistence())
        statement.setLong(3, next.valueForPersistence())
        statement.executeUpdate() == 1
    }

    private fun checkpoint(
        connection: Connection,
        organizationId: OrganizationId,
        projectionName: ExperimentalProjectionName
    ): ExperimentalChangeSequenceCheckpoint? = connection.prepareStatement(
        "SELECT last_change_sequence FROM projection_checkpoint " +
            "WHERE organization_id=? AND projection_name=?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setString(2, projectionName.valueForPersistence())
        statement.executeQuery().use { result ->
            if (result.next()) ExperimentalChangeSequenceCheckpoint(result.getLong(1)) else null
        }
    }

    private fun ResultSet.toChange(): ExperimentalMarketplaceEconomicEvidenceChange {
        val organizationId = OrganizationId(getObject("organization_id", UUID::class.java))
        val subject = MarketplaceEconomicEvidenceSubject(
            organizationId,
            MarketplaceOrderId(getObject("marketplace_order_id", UUID::class.java)),
            MarketplaceKey(getString("marketplace_key")),
            MarketplaceExternalOrderId(getString("external_order_id")),
            MarketplaceCurrency(getString("currency").trim())
        )
        return ExperimentalMarketplaceEconomicEvidenceChange(
            subject,
            MarketplaceEconomicEvidenceVersion(getLong("evidence_version")),
            ExperimentalChangeSequenceCheckpoint(getLong("change_sequence")),
            ExperimentalMarketplaceEconomicEvidenceChangeKind.valueOf(getString("change_kind"))
        )
    }

    private fun requireLimit(limit: Int) {
        require(limit in 1..MAX_LIMIT) { "Limit must be between 1 and $MAX_LIMIT" }
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

    companion object {
        private const val MAX_LIMIT = 1_000

        const val CHANGES_SINCE_SQL =
            "SELECT u.organization_id,u.marketplace_order_id,u.evidence_version," +
                "u.change_sequence,u.change_kind,s.marketplace_key,s.external_order_id,s.currency " +
                "FROM marketplace_economic_evidence_update u " +
                "JOIN marketplace_economic_evidence_subject s " +
                "ON s.organization_id=u.organization_id " +
                "AND s.marketplace_order_id=u.marketplace_order_id " +
                "WHERE u.organization_id=? AND u.change_sequence>? " +
                "ORDER BY u.change_sequence ASC LIMIT ?"

        const val PENDING_ORGANIZATIONS_SQL =
            "SELECT h.organization_id FROM (" +
                "SELECT organization_id,max(change_sequence) AS maximum_change_sequence " +
                "FROM marketplace_economic_evidence_update GROUP BY organization_id" +
                ") h LEFT JOIN projection_checkpoint c " +
                "ON c.organization_id=h.organization_id AND c.projection_name=? " +
                "WHERE h.maximum_change_sequence>COALESCE(c.last_change_sequence,0) " +
                "ORDER BY h.organization_id ASC LIMIT ?"

        fun installExperimentalSchema(configuration: PostgresConfiguration) {
            DriverManager.getConnection(configuration.url, configuration.user, configuration.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "CREATE TABLE projection_checkpoint (" +
                            "organization_id uuid NOT NULL REFERENCES integration_organization(organization_id)," +
                            "projection_name text NOT NULL CHECK " +
                            "(projection_name ~ '^[a-z0-9][a-z0-9-]{0,99}$')," +
                            "last_change_sequence bigint NOT NULL DEFAULT 0 CHECK (last_change_sequence>=0)," +
                            "updated_at timestamptz(6) NOT NULL," +
                            "PRIMARY KEY (organization_id,projection_name))"
                    )
                    statement.execute(
                        "CREATE FUNCTION stamp_projection_checkpoint() RETURNS trigger " +
                            "LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at := transaction_timestamp(); " +
                            "RETURN NEW; END; $$"
                    )
                    statement.execute(
                        "CREATE TRIGGER stamp_projection_checkpoint_before_write " +
                            "BEFORE INSERT OR UPDATE ON projection_checkpoint FOR EACH ROW " +
                            "EXECUTE FUNCTION stamp_projection_checkpoint()"
                    )
                }
            }
        }
    }
}
