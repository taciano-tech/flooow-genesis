package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.inventory.adjudication.*
import io.flooow.integration.inventory.comparison.CanonicalInventoryCandidateComparator
import io.flooow.integration.inventory.comparison.CanonicalInventoryCandidateComparisonResult
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.snapshot.CanonicalInventoryCandidateSnapshotId
import io.flooow.integration.inventory.snapshot.CanonicalInventoryCandidateSnapshotReadResult
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresCanonicalInventoryCandidateAdjudicationRepository(
    private val configuration: PostgresConfiguration
) : CanonicalInventoryCandidateAdjudicationRepository {
    private val snapshots = PostgresCanonicalInventoryCandidateSnapshotRepository(configuration)

    override fun adjudicate(
        command: AdjudicateCanonicalInventoryCandidate,
        adjudicationId: CanonicalInventoryCandidateAdjudicationId
    ): CanonicalInventoryCandidateAdjudicationWriteResult = try {
        connection().use { connection ->
            connection.autoCommit = false
            try {
                adjudicate(connection, command, adjudicationId).also { connection.commit() }
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    } catch (_: Exception) {
        replayAfterFailure(command)
            ?: CanonicalInventoryCandidateAdjudicationWriteResult.IntegrityFailure
    }

    override fun find(
        organizationId: OrganizationId,
        adjudicationId: CanonicalInventoryCandidateAdjudicationId
    ): CanonicalInventoryCandidateAdjudicationReadResult = try {
        connection().use { connection -> read(connection, organizationId, adjudicationId) }
    } catch (_: Exception) {
        CanonicalInventoryCandidateAdjudicationReadResult.IntegrityFailure
    }

    private fun adjudicate(
        connection: Connection,
        command: AdjudicateCanonicalInventoryCandidate,
        adjudicationId: CanonicalInventoryCandidateAdjudicationId
    ): CanonicalInventoryCandidateAdjudicationWriteResult {
        replay(connection, command)?.let { return it }
        if (!organizationActive(connection, command.organizationId)) {
            return CanonicalInventoryCandidateAdjudicationWriteResult.SnapshotUnavailable
        }
        if (!lockSnapshot(connection, command.organizationId, command.snapshotId)) {
            return CanonicalInventoryCandidateAdjudicationWriteResult.SnapshotUnavailable
        }
        replay(connection, command)?.let { return it }
        if (snapshotAlreadyAdjudicated(connection, command.organizationId, command.snapshotId)) {
            return CanonicalInventoryCandidateAdjudicationWriteResult.Conflict
        }
        if (!lockChosenMember(connection, command)) {
            return CanonicalInventoryCandidateAdjudicationWriteResult.CandidateUnavailable
        }

        val view = when (val found = snapshots.find(
            connection, command.organizationId, command.snapshotId
        )) {
            is CanonicalInventoryCandidateSnapshotReadResult.Found -> found.snapshot
            CanonicalInventoryCandidateSnapshotReadResult.NotFound ->
                return CanonicalInventoryCandidateAdjudicationWriteResult.SnapshotUnavailable
            CanonicalInventoryCandidateSnapshotReadResult.IntegrityFailure ->
                return CanonicalInventoryCandidateAdjudicationWriteResult.IntegrityFailure
        }
        if (view.members.count {
                it.lineageRootDecisionId == command.chosenLineageRootDecisionId
            } != 1
        ) return CanonicalInventoryCandidateAdjudicationWriteResult.CandidateUnavailable

        val comparison = CanonicalInventoryCandidateComparator.compare(view)
        if (comparison is CanonicalInventoryCandidateComparisonResult.IntegrityFailure) {
            return CanonicalInventoryCandidateAdjudicationWriteResult.IntegrityFailure
        }
        if (!command.reason.matches(comparison)) {
            return CanonicalInventoryCandidateAdjudicationWriteResult.ReasonMismatch
        }

        insert(connection, command, adjudicationId, transactionTime(connection))
        return CanonicalInventoryCandidateAdjudicationWriteResult.Adjudicated(adjudicationId)
    }

    private fun replayAfterFailure(
        command: AdjudicateCanonicalInventoryCandidate
    ): CanonicalInventoryCandidateAdjudicationWriteResult? = try {
        connection().use { replay(it, command) ?: if (
            snapshotAlreadyAdjudicated(it, command.organizationId, command.snapshotId)
        ) CanonicalInventoryCandidateAdjudicationWriteResult.Conflict else null }
    } catch (_: Exception) { null }

    private fun replay(
        connection: Connection,
        command: AdjudicateCanonicalInventoryCandidate
    ): CanonicalInventoryCandidateAdjudicationWriteResult? {
        val existing = connection.prepareStatement(
            "SELECT adjudication_id,snapshot_id,chosen_lineage_root_decision_id,reason " +
                "FROM integration_inventory_candidate_adjudication WHERE organization_id=? " +
                "AND request_id=?"
        ).use { statement ->
            statement.setObject(1, command.organizationId.value)
            statement.setObject(2, command.requestId.valueForPersistence())
            statement.executeQuery().use { result ->
                if (!result.next()) null else ExistingAdjudication(result)
            }
        } ?: return null
        return if (existing.snapshotId == command.snapshotId &&
            existing.chosenLineageRootDecisionId == command.chosenLineageRootDecisionId &&
            existing.reason == command.reason
        ) CanonicalInventoryCandidateAdjudicationWriteResult.AlreadyAdjudicated(existing.id)
        else CanonicalInventoryCandidateAdjudicationWriteResult.Conflict
    }

    private fun read(
        connection: Connection,
        organizationId: OrganizationId,
        adjudicationId: CanonicalInventoryCandidateAdjudicationId
    ): CanonicalInventoryCandidateAdjudicationReadResult {
        val adjudication = connection.prepareStatement(
            "SELECT * FROM integration_inventory_candidate_adjudication " +
                "WHERE organization_id=? AND adjudication_id=?"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, adjudicationId.valueForPersistence())
            statement.executeQuery().use { result ->
                if (!result.next()) null else decision(result, organizationId, adjudicationId)
            }
        } ?: return CanonicalInventoryCandidateAdjudicationReadResult.NotFound

        val view = when (val found = snapshots.find(
            connection, organizationId, adjudication.snapshotId
        )) {
            is CanonicalInventoryCandidateSnapshotReadResult.Found -> found.snapshot
            else -> return CanonicalInventoryCandidateAdjudicationReadResult.IntegrityFailure
        }
        val comparison = CanonicalInventoryCandidateComparator.compare(view)
        if (comparison is CanonicalInventoryCandidateComparisonResult.IntegrityFailure ||
            !adjudication.reason.matches(comparison)
        ) return CanonicalInventoryCandidateAdjudicationReadResult.IntegrityFailure
        val chosen = view.members.singleOrNull {
            it.lineageRootDecisionId == adjudication.chosenLineageRootDecisionId
        } ?: return CanonicalInventoryCandidateAdjudicationReadResult.IntegrityFailure
        return CanonicalInventoryCandidateAdjudicationReadResult.Found(
            AdjudicatedCanonicalInventoryCandidate(adjudication, comparison, chosen)
        )
    }

    private fun organizationActive(
        connection: Connection,
        organizationId: OrganizationId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_organization WHERE organization_id=? " +
            "AND status='ACTIVE' FOR SHARE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun lockSnapshot(
        connection: Connection,
        organizationId: OrganizationId,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_inventory_candidate_snapshot WHERE organization_id=? " +
            "AND snapshot_id=? FOR SHARE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, snapshotId.valueForPersistence())
        statement.executeQuery().use(ResultSet::next)
    }

    private fun lockChosenMember(
        connection: Connection,
        command: AdjudicateCanonicalInventoryCandidate
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_inventory_candidate_snapshot_member WHERE organization_id=? " +
            "AND snapshot_id=? AND lineage_root_decision_id=? FOR SHARE"
    ).use { statement ->
        statement.setObject(1, command.organizationId.value)
        statement.setObject(2, command.snapshotId.valueForPersistence())
        statement.setObject(3, command.chosenLineageRootDecisionId.valueForPersistence())
        statement.executeQuery().use(ResultSet::next)
    }

    private fun snapshotAlreadyAdjudicated(
        connection: Connection,
        organizationId: OrganizationId,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_inventory_candidate_adjudication WHERE organization_id=? " +
            "AND snapshot_id=?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, snapshotId.valueForPersistence())
        statement.executeQuery().use(ResultSet::next)
    }

    private fun insert(
        connection: Connection,
        command: AdjudicateCanonicalInventoryCandidate,
        adjudicationId: CanonicalInventoryCandidateAdjudicationId,
        decidedAt: Instant
    ) {
        connection.prepareStatement(
            "INSERT INTO integration_inventory_candidate_adjudication " +
                "(organization_id,adjudication_id,request_id,snapshot_id," +
                "chosen_lineage_root_decision_id,reason,principal_ref,correlation_id,decided_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, command.organizationId.value)
            statement.setObject(2, adjudicationId.valueForPersistence())
            statement.setObject(3, command.requestId.valueForPersistence())
            statement.setObject(4, command.snapshotId.valueForPersistence())
            statement.setObject(5, command.chosenLineageRootDecisionId.valueForPersistence())
            statement.setString(6, command.reason.name)
            statement.setString(7, command.principalReference.encodedForPersistence())
            statement.setObject(8, command.correlationId.valueForPersistence())
            statement.setTimestamp(9, Timestamp.from(decidedAt))
            check(statement.executeUpdate() == 1)
        }
    }

    private fun decision(
        result: ResultSet,
        organizationId: OrganizationId,
        id: CanonicalInventoryCandidateAdjudicationId
    ) = CanonicalInventoryCandidateAdjudication(
        id,
        organizationId,
        CanonicalInventoryCandidateAdjudicationRequestId.of(
            result.getObject("request_id", UUID::class.java)
        ),
        CanonicalInventoryCandidateSnapshotId.of(
            result.getObject("snapshot_id", UUID::class.java)
        ),
        InventoryMappingDecisionId.of(
            result.getObject("chosen_lineage_root_decision_id", UUID::class.java)
        ),
        CanonicalInventoryCandidateAdjudicationReason.valueOf(result.getString("reason")),
        InventoryCandidateAdjudicationPrincipalReference.of(result.getString("principal_ref")),
        CanonicalInventoryCandidateAdjudicationCorrelationId.of(
            result.getObject("correlation_id", UUID::class.java)
        ),
        result.getTimestamp("decided_at").toInstant()
    )

    private fun transactionTime(connection: Connection): Instant =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT transaction_timestamp()").use { result ->
                result.next()
                result.getTimestamp(1).toInstant()
            }
        }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url, configuration.user, configuration.password
    )

    private class ExistingAdjudication(result: ResultSet) {
        val id = CanonicalInventoryCandidateAdjudicationId.of(
            result.getObject("adjudication_id", UUID::class.java)
        )
        val snapshotId = CanonicalInventoryCandidateSnapshotId.of(
            result.getObject("snapshot_id", UUID::class.java)
        )
        val chosenLineageRootDecisionId = InventoryMappingDecisionId.of(
            result.getObject("chosen_lineage_root_decision_id", UUID::class.java)
        )
        val reason = CanonicalInventoryCandidateAdjudicationReason.valueOf(
            result.getString("reason")
        )
    }
}
