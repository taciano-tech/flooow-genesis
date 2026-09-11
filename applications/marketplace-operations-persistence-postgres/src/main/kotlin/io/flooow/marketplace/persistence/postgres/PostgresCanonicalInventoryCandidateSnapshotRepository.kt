package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.acceptance.CanonicalInventoryAcceptanceId
import io.flooow.integration.inventory.mapping.InventoryItemId
import io.flooow.integration.inventory.mapping.InventoryLocationId
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.mapping.InventoryUnitId
import io.flooow.integration.inventory.observation.CanonicalInventoryObservationId
import io.flooow.integration.inventory.observation.CanonicalInventorySourcePointer
import io.flooow.integration.inventory.observation.ExactInventoryQuantity
import io.flooow.integration.inventory.selection.CanonicalInventoryMeasure
import io.flooow.integration.inventory.selection.CanonicalInventoryMeasureSelectionId
import io.flooow.integration.inventory.snapshot.*
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresCanonicalInventoryCandidateSnapshotRepository(
    private val configuration: PostgresConfiguration
) : CanonicalInventoryCandidateSnapshotRepository {
    override fun capture(
        command: CaptureCanonicalInventoryCandidates,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ): CanonicalInventoryCandidateSnapshotCaptureResult = try {
        connection().use { connection ->
            connection.autoCommit = false
            try {
                capture(connection, command, snapshotId).also { connection.commit() }
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    } catch (_: Exception) {
        replayAfterFailure(command)
            ?: CanonicalInventoryCandidateSnapshotCaptureResult.IntegrityFailure
    }

    override fun find(
        organizationId: OrganizationId,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ): CanonicalInventoryCandidateSnapshotReadResult = try {
        connection().use { connection ->
            find(connection, organizationId, snapshotId)
        }
    } catch (_: Exception) {
        CanonicalInventoryCandidateSnapshotReadResult.IntegrityFailure
    }

    internal fun find(
        connection: Connection,
        organizationId: OrganizationId,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ): CanonicalInventoryCandidateSnapshotReadResult {
        val header = readHeader(connection, organizationId, snapshotId)
            ?: return CanonicalInventoryCandidateSnapshotReadResult.NotFound
        val frozen = readFrozenMembers(connection, organizationId, snapshotId)
        if (frozen.size != header.memberCount) {
            return CanonicalInventoryCandidateSnapshotReadResult.IntegrityFailure
        }
        val members = frozen.map { member ->
            readHistoricalMember(connection, header, member)
                ?: return CanonicalInventoryCandidateSnapshotReadResult.IntegrityFailure
        }
        return CanonicalInventoryCandidateSnapshotReadResult.Found(
            CanonicalInventoryCandidateSnapshotView(header, members)
        )
    }

    private fun capture(
        connection: Connection,
        command: CaptureCanonicalInventoryCandidates,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ): CanonicalInventoryCandidateSnapshotCaptureResult {
        replay(connection, command)?.let { return it }
        if (!targetActive(connection, command.organizationId, command.target)) {
            return CanonicalInventoryCandidateSnapshotCaptureResult.TargetUnavailable
        }

        val roots = command.lineageRootDecisionIds.sortedWith(
            CanonicalInventoryCandidateLineageOrder
        )
        val lockedRoots = roots.map { rootId ->
            lockRoot(connection, command.organizationId, rootId)
                ?: return CanonicalInventoryCandidateSnapshotCaptureResult.CandidateUnavailable
        }
        replay(connection, command)?.let { return it }

        val candidates = roots.zip(lockedRoots).map { (rootId, root) ->
            if (!eligibleScope(connection, command.organizationId, root.connectionId)) {
                return CanonicalInventoryCandidateSnapshotCaptureResult.CandidateUnavailable
            }
            val candidate = readCurrentCandidate(connection, command.organizationId, rootId)
                ?: return CanonicalInventoryCandidateSnapshotCaptureResult.CandidateUnavailable
            if (!candidate.matchesCurrentRoot(
                    rootId, root.connectionId, root.capability
                ) ||
                !lineageMatches(connection, command.organizationId, rootId, candidate)
            ) {
                return CanonicalInventoryCandidateSnapshotCaptureResult.IntegrityFailure
            }
            if (candidate.target != command.target) {
                return CanonicalInventoryCandidateSnapshotCaptureResult.TargetMismatch
            }
            if (candidate.quantity() == null) {
                return CanonicalInventoryCandidateSnapshotCaptureResult.CandidateUnavailable
            }
            candidate
        }

        val capturedAt = transactionTime(connection)
        insertHeader(connection, command, snapshotId, capturedAt, candidates.size)
        candidates.forEach { insertMember(connection, command.organizationId, snapshotId, it) }
        return CanonicalInventoryCandidateSnapshotCaptureResult.Captured(snapshotId, candidates.size)
    }

    private fun replayAfterFailure(
        command: CaptureCanonicalInventoryCandidates
    ): CanonicalInventoryCandidateSnapshotCaptureResult? = try {
        connection().use { replay(it, command) }
    } catch (_: Exception) { null }

    private fun replay(
        connection: Connection,
        command: CaptureCanonicalInventoryCandidates
    ): CanonicalInventoryCandidateSnapshotCaptureResult? {
        val header = connection.prepareStatement(
            "SELECT snapshot_id,target_item_id,target_location_id,target_unit_id,member_count " +
                "FROM integration_inventory_candidate_snapshot WHERE organization_id=? " +
                "AND request_id=?"
        ).use { statement ->
            statement.setObject(1, command.organizationId.value)
            statement.setObject(2, command.requestId.valueForPersistence())
            statement.executeQuery().use { result ->
                if (!result.next()) null else ReplayHeader(
                    CanonicalInventoryCandidateSnapshotId.of(
                        result.getObject("snapshot_id", UUID::class.java)
                    ),
                    target(result),
                    result.getInt("member_count")
                )
            }
        } ?: return null
        val roots = connection.prepareStatement(
            "SELECT lineage_root_decision_id FROM integration_inventory_candidate_snapshot_member " +
                "WHERE organization_id=? AND snapshot_id=?"
        ).use { statement ->
            statement.setObject(1, command.organizationId.value)
            statement.setObject(2, header.id.valueForPersistence())
            statement.executeQuery().use { result ->
                buildSet {
                    while (result.next()) add(
                        InventoryMappingDecisionId.of(
                            result.getObject("lineage_root_decision_id", UUID::class.java)
                        )
                    )
                }
            }
        }
        if (roots.size != header.memberCount) {
            return CanonicalInventoryCandidateSnapshotCaptureResult.IntegrityFailure
        }
        return if (header.target == command.target && roots == command.lineageRootDecisionIds) {
            CanonicalInventoryCandidateSnapshotCaptureResult.AlreadyCaptured(
                header.id, header.memberCount
            )
        } else CanonicalInventoryCandidateSnapshotCaptureResult.Conflict
    }

    private fun lockRoot(
        connection: Connection,
        organizationId: OrganizationId,
        rootId: InventoryMappingDecisionId
    ): SnapshotRoot? = connection.prepareStatement(
        "SELECT connection_id,capability,revision,supersedes_decision_id " +
            "FROM integration_inventory_source_mapping WHERE organization_id=? " +
            "AND decision_id=? FOR UPDATE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, rootId.valueForPersistence())
        statement.executeQuery().use { result ->
            if (!result.next() || result.getInt("revision") != 1 ||
                result.getObject("supersedes_decision_id") != null
            ) null else SnapshotRoot(
                IntegrationConnectionId(result.getObject("connection_id", UUID::class.java)),
                result.getString("capability")
            )
        }
    }

    private fun readCurrentCandidate(
        connection: Connection,
        organizationId: OrganizationId,
        rootId: InventoryMappingDecisionId
    ): SnapshotCandidate? = connection.prepareStatement(
        CURRENT_CANDIDATE_SQL
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, rootId.valueForPersistence())
        statement.executeQuery().use { result ->
            if (!result.next()) null else SnapshotCandidate(result)
        }
    }

    private fun lineageMatches(
        connection: Connection,
        organizationId: OrganizationId,
        rootId: InventoryMappingDecisionId,
        candidate: SnapshotCandidate
    ): Boolean = connection.prepareStatement(
        "WITH RECURSIVE lineage AS (" +
            "SELECT decision_id,supersedes_decision_id,revision,connection_id,capability," +
            "source_item_ref,source_location_ref,source_unit_code FROM " +
            "integration_inventory_source_mapping WHERE organization_id=? AND decision_id=? " +
            "UNION ALL SELECT p.decision_id,p.supersedes_decision_id,p.revision,p.connection_id," +
            "p.capability,p.source_item_ref,p.source_location_ref,p.source_unit_code FROM " +
            "integration_inventory_source_mapping p JOIN lineage c ON " +
            "c.supersedes_decision_id=p.decision_id WHERE p.organization_id=? " +
            "AND p.revision=c.revision-1 AND p.connection_id=c.connection_id " +
            "AND p.capability=c.capability AND p.source_item_ref=c.source_item_ref " +
            "AND p.source_location_ref IS NOT DISTINCT FROM c.source_location_ref " +
            "AND p.source_unit_code IS NOT DISTINCT FROM c.source_unit_code) " +
            "SELECT 1 FROM lineage WHERE decision_id=? AND revision=1"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, candidate.mappingDecisionId.valueForPersistence())
        statement.setObject(3, organizationId.value)
        statement.setObject(4, rootId.valueForPersistence())
        statement.executeQuery().use(ResultSet::next)
    }

    private fun insertHeader(
        connection: Connection,
        command: CaptureCanonicalInventoryCandidates,
        snapshotId: CanonicalInventoryCandidateSnapshotId,
        capturedAt: Instant,
        memberCount: Int
    ) {
        connection.prepareStatement(
            "INSERT INTO integration_inventory_candidate_snapshot (organization_id,snapshot_id," +
                "request_id,target_item_id,target_location_id,target_unit_id,principal_ref," +
                "correlation_id,captured_at,member_count) VALUES (?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, command.organizationId.value)
            statement.setObject(2, snapshotId.valueForPersistence())
            statement.setObject(3, command.requestId.valueForPersistence())
            statement.setObject(4, command.target.itemId.valueForPersistence())
            statement.setObject(5, command.target.locationId?.valueForPersistence())
            statement.setObject(6, command.target.unitId.valueForPersistence())
            statement.setString(7, command.principalReference.encodedForPersistence())
            statement.setObject(8, command.correlationId.valueForPersistence())
            statement.setTimestamp(9, Timestamp.from(capturedAt))
            statement.setInt(10, memberCount)
            check(statement.executeUpdate() == 1)
        }
    }

    private fun insertMember(
        connection: Connection,
        organizationId: OrganizationId,
        snapshotId: CanonicalInventoryCandidateSnapshotId,
        candidate: SnapshotCandidate
    ) {
        connection.prepareStatement(
            "INSERT INTO integration_inventory_candidate_snapshot_member (organization_id," +
                "snapshot_id,connection_id,capability,lineage_root_decision_id,selection_id," +
                "selection_revision,acceptance_id,acceptance_revision,observation_id," +
                "projection_revision,mapping_decision_id,mapping_revision,target_item_id," +
                "target_location_id,target_unit_id,measure) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, snapshotId.valueForPersistence())
            statement.setObject(3, candidate.connectionId.value)
            statement.setString(4, candidate.capability)
            statement.setObject(5, candidate.lineageRootDecisionId.valueForPersistence())
            statement.setObject(6, candidate.selectionId.valueForPersistence())
            statement.setInt(7, candidate.selectionRevision)
            statement.setObject(8, candidate.acceptanceId.valueForPersistence())
            statement.setInt(9, candidate.acceptanceRevision)
            statement.setObject(10, candidate.observationId.valueForPersistence())
            statement.setInt(11, candidate.projectionRevision)
            statement.setObject(12, candidate.mappingDecisionId.valueForPersistence())
            statement.setInt(13, candidate.mappingRevision)
            statement.setObject(14, candidate.target.itemId.valueForPersistence())
            statement.setObject(15, candidate.target.locationId?.valueForPersistence())
            statement.setObject(16, candidate.target.unitId.valueForPersistence())
            statement.setString(17, candidate.measure.name)
            check(statement.executeUpdate() == 1)
        }
    }

    private fun readHeader(
        connection: Connection,
        organizationId: OrganizationId,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ): CanonicalInventoryCandidateSnapshot? = connection.prepareStatement(
        "SELECT * FROM integration_inventory_candidate_snapshot WHERE organization_id=? " +
            "AND snapshot_id=?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, snapshotId.valueForPersistence())
        statement.executeQuery().use { result ->
            if (!result.next()) null else CanonicalInventoryCandidateSnapshot(
                snapshotId,
                organizationId,
                CanonicalInventoryCandidateSnapshotRequestId.of(
                    result.getObject("request_id", UUID::class.java)
                ),
                target(result),
                InventoryCandidateSnapshotPrincipalReference.of(result.getString("principal_ref")),
                CanonicalInventoryCandidateSnapshotCorrelationId.of(
                    result.getObject("correlation_id", UUID::class.java)
                ),
                result.getTimestamp("captured_at").toInstant(),
                result.getInt("member_count")
            )
        }
    }

    private fun readFrozenMembers(
        connection: Connection,
        organizationId: OrganizationId,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ): List<FrozenMember> = connection.prepareStatement(
        "SELECT * FROM integration_inventory_candidate_snapshot_member WHERE organization_id=? " +
            "AND snapshot_id=? ORDER BY lineage_root_decision_id"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, snapshotId.valueForPersistence())
        statement.executeQuery().use { result ->
            buildList { while (result.next()) add(FrozenMember(result)) }
        }
    }

    private fun readHistoricalMember(
        connection: Connection,
        header: CanonicalInventoryCandidateSnapshot,
        frozen: FrozenMember
    ): CanonicalInventoryCandidateSnapshotMember? {
        val candidate = connection.prepareStatement(FROZEN_CANDIDATE_SQL).use { statement ->
            statement.setObject(1, header.organizationId.value)
            statement.setObject(2, frozen.selectionId.valueForPersistence())
            statement.setObject(3, frozen.acceptanceId.valueForPersistence())
            statement.setObject(4, frozen.observationId.valueForPersistence())
            statement.setObject(5, frozen.mappingDecisionId.valueForPersistence())
            statement.executeQuery().use { result ->
                if (!result.next()) null else SnapshotCandidate(result)
            }
        } ?: return null
        if (!candidate.matchesFrozen(frozen) ||
            !lineageMatches(
                connection, header.organizationId, frozen.lineageRootDecisionId, candidate
            ) || candidate.target != header.target
        ) return null
        val quantity = candidate.quantity() ?: return null
        return CanonicalInventoryCandidateSnapshotMember(
            header.organizationId, header.id, candidate.connectionId, candidate.capability,
            candidate.lineageRootDecisionId, candidate.selectionId, candidate.selectionRevision,
            candidate.acceptanceId, candidate.acceptanceRevision, candidate.observationId,
            CanonicalInventorySourcePointer(
                candidate.connectionId, candidate.capability,
                candidate.sourceProgressVersion, candidate.sourceRecordOrdinal
            ),
            candidate.projectionRevision, candidate.mappingDecisionId, candidate.mappingRevision,
            candidate.target, candidate.measure, quantity
        )
    }

    private fun eligibleScope(
        connection: Connection,
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_organization o JOIN integration_connection c " +
            "ON c.organization_id=o.organization_id WHERE o.organization_id=? " +
            "AND c.connection_id=? AND o.status='ACTIVE' " +
            "AND c.status IN ('ACTIVE','SUSPENDED') FOR SHARE OF o,c"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, connectionId.value)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun targetActive(
        connection: Connection,
        organizationId: OrganizationId,
        target: CanonicalInventoryCandidateTarget
    ): Boolean = identityActive(
        connection, "inventory_item_identity", organizationId,
        target.itemId.valueForPersistence()
    ) && identityActive(
        connection, "inventory_unit_identity", organizationId,
        target.unitId.valueForPersistence()
    ) && target.locationId.let { location ->
        location == null || identityActive(
            connection, "inventory_location_identity", organizationId,
            location.valueForPersistence()
        )
    }

    private fun identityActive(
        connection: Connection,
        table: String,
        organizationId: OrganizationId,
        id: UUID
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM $table WHERE organization_id=? AND identity_id=? " +
            "AND state='ACTIVE' FOR SHARE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, id)
        statement.executeQuery().use(ResultSet::next)
    }

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

    private data class ReplayHeader(
        val id: CanonicalInventoryCandidateSnapshotId,
        val target: CanonicalInventoryCandidateTarget,
        val memberCount: Int
    )

    private data class SnapshotRoot(
        val connectionId: IntegrationConnectionId,
        val capability: String
    )

    private companion object {
        const val CANDIDATE_COLUMNS =
            "s.connection_id,s.capability,s.lineage_root_decision_id,s.selection_id," +
                "s.revision AS selection_revision,s.measure,s.anchor_acceptance_id," +
                "s.anchor_acceptance_revision,s.anchor_observation_id," +
                "a.acceptance_id,a.revision AS acceptance_revision,a.observation_id," +
                "a.source_progress_version,a.source_record_ordinal,a.projection_revision," +
                "a.mapping_decision_id,a.mapping_revision,a.target_item_id,a.target_location_id," +
                "a.target_unit_id,a.factor_numerator,a.factor_denominator," +
                "o.connection_id AS observation_connection_id,o.capability AS observation_capability," +
                "o.input_progress_version,o.record_ordinal,o.projection_revision AS observed_projection_revision," +
                "o.mapping_decision_id AS observed_mapping_decision_id," +
                "o.mapping_revision AS observed_mapping_revision,o.target_item_id AS observed_target_item_id," +
                "o.target_location_id AS observed_target_location_id,o.target_unit_id AS observed_target_unit_id," +
                "o.factor_numerator AS observed_factor_numerator,o.factor_denominator AS observed_factor_denominator," +
                "o.available_to_sell_numerator,o.available_to_sell_denominator," +
                "o.on_hand_numerator,o.on_hand_denominator,o.reserved_numerator,o.reserved_denominator," +
                "o.pending_inbound_numerator,o.pending_inbound_denominator," +
                "o.pending_outbound_numerator,o.pending_outbound_denominator," +
                "m.connection_id AS mapping_connection_id,m.capability AS mapping_capability," +
                "m.revision AS persisted_mapping_revision,m.state AS mapping_state," +
                "m.target_item_id AS mapping_target_item_id," +
                "m.target_location_id AS mapping_target_location_id," +
                "m.target_unit_id AS mapping_target_unit_id," +
                "m.factor_numerator AS mapping_factor_numerator," +
                "m.factor_denominator AS mapping_factor_denominator"

        const val CANDIDATE_JOINS =
            " FROM integration_inventory_measure_selection s " +
                "JOIN integration_inventory_source_acceptance a ON " +
                "a.organization_id=s.organization_id AND " +
                "a.lineage_root_decision_id=s.lineage_root_decision_id " +
                "JOIN integration_inventory_canonical_observation o ON " +
                "o.organization_id=a.organization_id AND o.observation_id=a.observation_id " +
                "JOIN integration_inventory_source_mapping m ON " +
                "m.organization_id=a.organization_id AND m.decision_id=a.mapping_decision_id "

        const val CURRENT_CANDIDATE_SQL =
            "SELECT $CANDIDATE_COLUMNS$CANDIDATE_JOINS" +
                "WHERE s.organization_id=? AND s.lineage_root_decision_id=? " +
                "AND s.state='ACTIVE' AND a.state='ACTIVE' FOR SHARE OF s,a,o,m"

        const val FROZEN_CANDIDATE_SQL =
            "SELECT $CANDIDATE_COLUMNS$CANDIDATE_JOINS" +
                "WHERE s.organization_id=? AND s.selection_id=? AND a.acceptance_id=? " +
                "AND o.observation_id=? AND m.decision_id=?"
    }
}

private class SnapshotCandidate(result: ResultSet) {
    val connectionId = IntegrationConnectionId(result.getObject("connection_id", UUID::class.java))
    val capability: String = result.getString("capability")
    val lineageRootDecisionId = InventoryMappingDecisionId.of(
        result.getObject("lineage_root_decision_id", UUID::class.java)
    )
    val selectionId = CanonicalInventoryMeasureSelectionId.of(
        result.getObject("selection_id", UUID::class.java)
    )
    val selectionRevision: Int = result.getInt("selection_revision")
    val measure = CanonicalInventoryMeasure.valueOf(result.getString("measure"))
    private val anchorAcceptanceId = CanonicalInventoryAcceptanceId.of(
        result.getObject("anchor_acceptance_id", UUID::class.java)
    )
    private val anchorAcceptanceRevision: Int = result.getInt("anchor_acceptance_revision")
    private val anchorObservationId = CanonicalInventoryObservationId.of(
        result.getObject("anchor_observation_id", UUID::class.java)
    )
    val acceptanceId = CanonicalInventoryAcceptanceId.of(
        result.getObject("acceptance_id", UUID::class.java)
    )
    val acceptanceRevision: Int = result.getInt("acceptance_revision")
    val observationId = CanonicalInventoryObservationId.of(
        result.getObject("observation_id", UUID::class.java)
    )
    val sourceProgressVersion: Long = result.getLong("source_progress_version")
    val sourceRecordOrdinal: Int = result.getInt("source_record_ordinal")
    val projectionRevision: Int = result.getInt("projection_revision")
    val mappingDecisionId = InventoryMappingDecisionId.of(
        result.getObject("mapping_decision_id", UUID::class.java)
    )
    val mappingRevision: Int = result.getInt("mapping_revision")
    val target = CanonicalInventoryCandidateTarget(
        InventoryItemId.of(result.getObject("target_item_id", UUID::class.java)),
        result.getObject("target_location_id", UUID::class.java)?.let(InventoryLocationId::of),
        InventoryUnitId.of(result.getObject("target_unit_id", UUID::class.java))
    )
    private val factorNumerator: Long = result.getLong("factor_numerator")
    private val factorDenominator: Long = result.getLong("factor_denominator")
    private val observationConnectionId = IntegrationConnectionId(
        result.getObject("observation_connection_id", UUID::class.java)
    )
    private val observationCapability: String = result.getString("observation_capability")
    private val observedProgressVersion: Long = result.getLong("input_progress_version")
    private val observedRecordOrdinal: Int = result.getInt("record_ordinal")
    private val observedProjectionRevision: Int = result.getInt("observed_projection_revision")
    private val observedMappingDecisionId = InventoryMappingDecisionId.of(
        result.getObject("observed_mapping_decision_id", UUID::class.java)
    )
    private val observedMappingRevision: Int = result.getInt("observed_mapping_revision")
    private val observedTarget = CanonicalInventoryCandidateTarget(
        InventoryItemId.of(result.getObject("observed_target_item_id", UUID::class.java)),
        result.getObject("observed_target_location_id", UUID::class.java)?.let(InventoryLocationId::of),
        InventoryUnitId.of(result.getObject("observed_target_unit_id", UUID::class.java))
    )
    private val observedFactorNumerator: Long = result.getLong("observed_factor_numerator")
    private val observedFactorDenominator: Long = result.getLong("observed_factor_denominator")
    private val mappingConnectionId = IntegrationConnectionId(
        result.getObject("mapping_connection_id", UUID::class.java)
    )
    private val mappingCapability: String = result.getString("mapping_capability")
    private val persistedMappingRevision: Int = result.getInt("persisted_mapping_revision")
    private val mappingState: String = result.getString("mapping_state")
    private val mappingTarget = CanonicalInventoryCandidateTarget(
        InventoryItemId.of(result.getObject("mapping_target_item_id", UUID::class.java)),
        result.getObject("mapping_target_location_id", UUID::class.java)?.let(InventoryLocationId::of),
        InventoryUnitId.of(result.getObject("mapping_target_unit_id", UUID::class.java))
    )
    private val mappingFactorNumerator: Long = result.getLong("mapping_factor_numerator")
    private val mappingFactorDenominator: Long = result.getLong("mapping_factor_denominator")
    private val quantities = CanonicalInventoryMeasure.entries.associateWith { selected ->
        val prefix = when (selected) {
            CanonicalInventoryMeasure.AVAILABLE_TO_SELL -> "available_to_sell"
            CanonicalInventoryMeasure.ON_HAND -> "on_hand"
            CanonicalInventoryMeasure.RESERVED -> "reserved"
            CanonicalInventoryMeasure.PENDING_INBOUND -> "pending_inbound"
            CanonicalInventoryMeasure.PENDING_OUTBOUND -> "pending_outbound"
        }
        result.getBigDecimal("${prefix}_numerator")?.let { numerator ->
            ExactInventoryQuantity.fromPersistence(
                numerator.toBigIntegerExact(), result.getLong("${prefix}_denominator")
            )
        }
    }

    fun quantity(): ExactInventoryQuantity? = quantities[measure]

    fun matchesCurrentRoot(
        rootId: InventoryMappingDecisionId,
        rootConnectionId: IntegrationConnectionId,
        rootCapability: String
    ): Boolean = lineageRootDecisionId == rootId && connectionId == rootConnectionId &&
        capability == rootCapability && mappingState == "ACTIVE" && commonIntegrity()

    private fun commonIntegrity(): Boolean =
        anchorAcceptanceId == acceptanceId &&
            anchorAcceptanceRevision == acceptanceRevision &&
            anchorObservationId == observationId &&
            observationConnectionId == connectionId && observationCapability == capability &&
            observedProgressVersion == sourceProgressVersion &&
            observedRecordOrdinal == sourceRecordOrdinal &&
            observedProjectionRevision == projectionRevision &&
            observedMappingDecisionId == mappingDecisionId &&
            observedMappingRevision == mappingRevision && observedTarget == target &&
            observedFactorNumerator == factorNumerator &&
            observedFactorDenominator == factorDenominator &&
            mappingConnectionId == connectionId && mappingCapability == capability &&
            persistedMappingRevision == mappingRevision && mappingTarget == target &&
            mappingFactorNumerator == factorNumerator &&
            mappingFactorDenominator == factorDenominator

    fun matchesFrozen(frozen: FrozenMember): Boolean =
        connectionId == frozen.connectionId && capability == frozen.capability &&
            lineageRootDecisionId == frozen.lineageRootDecisionId &&
            selectionId == frozen.selectionId && selectionRevision == frozen.selectionRevision &&
            acceptanceId == frozen.acceptanceId && acceptanceRevision == frozen.acceptanceRevision &&
            observationId == frozen.observationId && projectionRevision == frozen.projectionRevision &&
            mappingDecisionId == frozen.mappingDecisionId && mappingRevision == frozen.mappingRevision &&
            target == frozen.target && measure == frozen.measure && commonIntegrity()
}

private class FrozenMember(result: ResultSet) {
    val connectionId = IntegrationConnectionId(result.getObject("connection_id", UUID::class.java))
    val capability: String = result.getString("capability")
    val lineageRootDecisionId = InventoryMappingDecisionId.of(
        result.getObject("lineage_root_decision_id", UUID::class.java)
    )
    val selectionId = CanonicalInventoryMeasureSelectionId.of(
        result.getObject("selection_id", UUID::class.java)
    )
    val selectionRevision: Int = result.getInt("selection_revision")
    val acceptanceId = CanonicalInventoryAcceptanceId.of(
        result.getObject("acceptance_id", UUID::class.java)
    )
    val acceptanceRevision: Int = result.getInt("acceptance_revision")
    val observationId = CanonicalInventoryObservationId.of(
        result.getObject("observation_id", UUID::class.java)
    )
    val projectionRevision: Int = result.getInt("projection_revision")
    val mappingDecisionId = InventoryMappingDecisionId.of(
        result.getObject("mapping_decision_id", UUID::class.java)
    )
    val mappingRevision: Int = result.getInt("mapping_revision")
    val target = CanonicalInventoryCandidateTarget(
        InventoryItemId.of(result.getObject("target_item_id", UUID::class.java)),
        result.getObject("target_location_id", UUID::class.java)?.let(InventoryLocationId::of),
        InventoryUnitId.of(result.getObject("target_unit_id", UUID::class.java))
    )
    val measure = CanonicalInventoryMeasure.valueOf(result.getString("measure"))
}

private fun target(result: ResultSet) = CanonicalInventoryCandidateTarget(
    InventoryItemId.of(result.getObject("target_item_id", UUID::class.java)),
    result.getObject("target_location_id", UUID::class.java)?.let(InventoryLocationId::of),
    InventoryUnitId.of(result.getObject("target_unit_id", UUID::class.java))
)
