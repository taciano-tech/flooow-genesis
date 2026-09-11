package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.acceptance.CanonicalInventoryAcceptanceId
import io.flooow.integration.inventory.mapping.*
import io.flooow.integration.inventory.observation.*
import io.flooow.integration.inventory.selection.*
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresCanonicalInventoryMeasureSelectionRepository(
    private val configuration: PostgresConfiguration
) : CanonicalInventoryMeasureSelectionRepository {
    override fun selectInitial(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        measure: CanonicalInventoryMeasure,
        selectionId: CanonicalInventoryMeasureSelectionId,
        principal: InventoryMeasureSelectionPrincipalReference,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId
    ): CanonicalInventoryMeasureSelectionResult = mutate { connection ->
        val root = lockRoot(connection, organizationId, lineageRootDecisionId)
            ?: return@mutate CanonicalInventoryMeasureSelectionResult.LineageUnavailable
        val anchor = activeAnchor(connection, organizationId, lineageRootDecisionId)
            ?: return@mutate CanonicalInventoryMeasureSelectionResult.Unaccepted
        preflight(connection, root, anchor)?.let { return@mutate it }
        if (anchor.quantity(measure) == null) {
            return@mutate CanonicalInventoryMeasureSelectionResult.MeasureUnavailable
        }
        activeSelection(connection, organizationId, lineageRootDecisionId, true)?.let { current ->
            return@mutate if (current.measure == measure) {
                CanonicalInventoryMeasureSelectionResult.AlreadySelected(current.id, current.revision)
            } else CanonicalInventoryMeasureSelectionResult.Conflict
        }
        if (hasHistory(connection, organizationId, lineageRootDecisionId)) {
            return@mutate CanonicalInventoryMeasureSelectionResult.Conflict
        }
        val selection = selection(
            selectionId, organizationId, root, anchor, 1, measure, principal,
            CanonicalInventoryMeasureSelectionReason.INITIAL_SELECTION, correlationId,
            transactionTime(connection), null
        )
        insert(connection, selection)
        CanonicalInventoryMeasureSelectionResult.Selected(selection.id, selection.revision)
    }

    override fun replace(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedSelectionId: CanonicalInventoryMeasureSelectionId,
        expectedRevision: Int,
        measure: CanonicalInventoryMeasure,
        selectionId: CanonicalInventoryMeasureSelectionId,
        principal: InventoryMeasureSelectionPrincipalReference,
        reason: CanonicalInventoryMeasureSelectionReason,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId
    ): CanonicalInventoryMeasureSelectionResult = mutate { connection ->
        if (!reason.isReplacement()) {
            return@mutate CanonicalInventoryMeasureSelectionResult.IntegrityFailure
        }
        val root = lockRoot(connection, organizationId, lineageRootDecisionId)
            ?: return@mutate CanonicalInventoryMeasureSelectionResult.LineageUnavailable
        val anchor = activeAnchor(connection, organizationId, lineageRootDecisionId)
            ?: return@mutate CanonicalInventoryMeasureSelectionResult.Unaccepted
        preflight(connection, root, anchor)?.let { return@mutate it }
        val current = activeSelection(connection, organizationId, lineageRootDecisionId, true)
            ?: return@mutate CanonicalInventoryMeasureSelectionResult.Conflict
        if (current.id != expectedSelectionId || current.revision != expectedRevision) {
            return@mutate CanonicalInventoryMeasureSelectionResult.Conflict
        }
        if (current.measure == measure) {
            return@mutate CanonicalInventoryMeasureSelectionResult.AlreadySelected(
                current.id, current.revision
            )
        }
        if (anchor.quantity(measure) == null) {
            return@mutate CanonicalInventoryMeasureSelectionResult.MeasureUnavailable
        }
        val at = transactionTime(connection)
        retire(connection, current, principal, reason, correlationId, at)
        val successor = selection(
            selectionId, organizationId, root, anchor, current.revision + 1, measure,
            principal, reason, correlationId, at, current.id
        )
        insert(connection, successor)
        CanonicalInventoryMeasureSelectionResult.Selected(successor.id, successor.revision)
    }

    override fun withdraw(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedSelectionId: CanonicalInventoryMeasureSelectionId,
        expectedRevision: Int,
        principal: InventoryMeasureSelectionPrincipalReference,
        reason: CanonicalInventoryMeasureSelectionReason,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId
    ): CanonicalInventoryMeasureSelectionResult = mutate { connection ->
        if (!reason.isWithdrawal()) {
            return@mutate CanonicalInventoryMeasureSelectionResult.IntegrityFailure
        }
        val root = lockRoot(connection, organizationId, lineageRootDecisionId)
            ?: return@mutate CanonicalInventoryMeasureSelectionResult.LineageUnavailable
        val current = activeSelection(connection, organizationId, lineageRootDecisionId, true)
        if (current == null) {
            return@mutate if (withdrawalWasApplied(
                    connection, organizationId, expectedSelectionId, expectedRevision
                )) CanonicalInventoryMeasureSelectionResult.Withdrawn(expectedRevision)
            else CanonicalInventoryMeasureSelectionResult.Conflict
        }
        if (current.id != expectedSelectionId || current.revision != expectedRevision) {
            return@mutate CanonicalInventoryMeasureSelectionResult.Conflict
        }
        if (!eligibleScope(connection, organizationId, root.connectionId)) {
            return@mutate CanonicalInventoryMeasureSelectionResult.LineageUnavailable
        }
        if (activeAnchor(connection, organizationId, lineageRootDecisionId) == null) {
            return@mutate CanonicalInventoryMeasureSelectionResult.Unaccepted
        }
        retire(connection, current, principal, reason, correlationId, transactionTime(connection))
        CanonicalInventoryMeasureSelectionResult.Withdrawn(current.revision)
    }

    override fun resolve(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): CanonicalInventoryMeasureResolutionResult = try {
        connection().use { connection ->
            val root = readRoot(connection, organizationId, lineageRootDecisionId)
                ?: return CanonicalInventoryMeasureResolutionResult.IntegrityFailure
            val current = activeSelection(connection, organizationId, lineageRootDecisionId, false)
                ?: return CanonicalInventoryMeasureResolutionResult.Unselected
            val anchor = activeAnchor(connection, organizationId, lineageRootDecisionId)
                ?: return CanonicalInventoryMeasureResolutionResult.Unaccepted
            if (preflight(connection, root, anchor) != null) {
                return CanonicalInventoryMeasureResolutionResult.IntegrityFailure
            }
            val quantity = anchor.quantity(current.measure)
                ?: return CanonicalInventoryMeasureResolutionResult.MeasureUnavailable
            val pointer = CanonicalInventorySourcePointer(
                anchor.connectionId, anchor.capability, anchor.sourceProgressVersion,
                anchor.sourceRecordOrdinal
            )
            CanonicalInventoryMeasureResolutionResult.Resolved(
                SelectedCanonicalInventoryMeasure(
                    organizationId, anchor.connectionId, anchor.capability,
                    lineageRootDecisionId, current.id, current.revision,
                    anchor.acceptanceId, anchor.acceptanceRevision, anchor.observationId,
                    pointer, anchor.projectionRevision, anchor.mappingDecisionId,
                    anchor.mappingRevision, anchor.target, current.measure, quantity
                )
            )
        }
    } catch (_: Exception) { CanonicalInventoryMeasureResolutionResult.IntegrityFailure }

    override fun head(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): CanonicalInventoryMeasureSelection? = try {
        connection().use { activeSelection(it, organizationId, lineageRootDecisionId, false) }
    } catch (_: SQLException) { null }

    override fun history(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): List<CanonicalInventoryMeasureSelection> = try {
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT * FROM integration_inventory_measure_selection WHERE organization_id=? " +
                    "AND lineage_root_decision_id=? ORDER BY revision"
            ).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setObject(2, lineageRootDecisionId.valueForPersistence())
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(readSelection(result)) }
                }
            }
        }
    } catch (_: SQLException) { emptyList() }

    private fun preflight(
        connection: Connection,
        root: SelectionRoot,
        anchor: SelectionAnchor
    ): CanonicalInventoryMeasureSelectionResult? {
        if (!eligibleScope(connection, root.organizationId, root.connectionId)) {
            return CanonicalInventoryMeasureSelectionResult.LineageUnavailable
        }
        if (anchor.connectionId != root.connectionId || anchor.capability != root.capability) {
            return CanonicalInventoryMeasureSelectionResult.LineageUnavailable
        }
        if (!activeMappingMatches(connection, root, anchor)) {
            return CanonicalInventoryMeasureSelectionResult.LineageUnavailable
        }
        if (!targetsActive(connection, root.organizationId, anchor.target)) {
            return CanonicalInventoryMeasureSelectionResult.TargetUnavailable
        }
        return null
    }

    private fun activeMappingMatches(
        connection: Connection,
        root: SelectionRoot,
        anchor: SelectionAnchor
    ): Boolean = connection.prepareStatement(
        "WITH RECURSIVE lineage AS (" +
            "SELECT decision_id,supersedes_decision_id,revision,connection_id,capability," +
            "source_item_ref,source_location_ref,source_unit_code,state,target_item_id," +
            "target_location_id,target_unit_id,factor_numerator,factor_denominator " +
            "FROM integration_inventory_source_mapping WHERE organization_id=? AND decision_id=? " +
            "UNION ALL SELECT p.decision_id,p.supersedes_decision_id,p.revision,p.connection_id," +
            "p.capability,p.source_item_ref,p.source_location_ref,p.source_unit_code,p.state," +
            "p.target_item_id,p.target_location_id,p.target_unit_id,p.factor_numerator," +
            "p.factor_denominator FROM integration_inventory_source_mapping p JOIN lineage c " +
            "ON c.supersedes_decision_id=p.decision_id WHERE p.organization_id=? " +
            "AND p.revision=c.revision-1) SELECT 1 FROM lineage leaf WHERE leaf.decision_id=? " +
            "AND leaf.state='ACTIVE' AND leaf.connection_id=? AND leaf.capability=? " +
            "AND leaf.target_item_id=? AND leaf.target_location_id IS NOT DISTINCT FROM ? " +
            "AND leaf.target_unit_id=? AND leaf.factor_numerator=? AND leaf.factor_denominator=? " +
            "AND EXISTS (SELECT 1 FROM lineage r WHERE r.decision_id=? AND r.revision=1 " +
            "AND r.supersedes_decision_id IS NULL AND r.connection_id=leaf.connection_id " +
            "AND r.capability=leaf.capability AND r.source_item_ref=leaf.source_item_ref " +
            "AND r.source_location_ref IS NOT DISTINCT FROM leaf.source_location_ref " +
            "AND r.source_unit_code IS NOT DISTINCT FROM leaf.source_unit_code)"
    ).use { statement ->
        statement.setObject(1, root.organizationId.value)
        statement.setObject(2, anchor.mappingDecisionId.valueForPersistence())
        statement.setObject(3, root.organizationId.value)
        statement.setObject(4, anchor.mappingDecisionId.valueForPersistence())
        statement.setObject(5, root.connectionId.value)
        statement.setString(6, root.capability)
        statement.setObject(7, anchor.target.itemId.valueForPersistence())
        statement.setObject(8, anchor.target.locationId?.valueForPersistence())
        statement.setObject(9, anchor.target.unitId.valueForPersistence())
        statement.setLong(10, anchor.target.quantityFactor.numerator)
        statement.setLong(11, anchor.target.quantityFactor.denominator)
        statement.setObject(12, root.decisionId.valueForPersistence())
        statement.executeQuery().use(ResultSet::next)
    }

    private fun lockRoot(
        connection: Connection,
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): SelectionRoot? = readRoot(connection, organizationId, lineageRootDecisionId, true)

    private fun readRoot(
        connection: Connection,
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        lock: Boolean = false
    ): SelectionRoot? = connection.prepareStatement(
        "SELECT * FROM integration_inventory_source_mapping WHERE organization_id=? " +
            "AND decision_id=?" + if (lock) " FOR UPDATE" else ""
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, lineageRootDecisionId.valueForPersistence())
        statement.executeQuery().use { result ->
            if (!result.next()) null else SelectionRoot(result).takeIf {
                it.revision == 1 && it.supersedesDecisionId == null
            }
        }
    }

    private fun activeAnchor(
        connection: Connection,
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): SelectionAnchor? = connection.prepareStatement(
        "SELECT a.*,o.available_to_sell_numerator,o.available_to_sell_denominator," +
            "o.on_hand_numerator,o.on_hand_denominator,o.reserved_numerator," +
            "o.reserved_denominator,o.pending_inbound_numerator," +
            "o.pending_inbound_denominator,o.pending_outbound_numerator," +
            "o.pending_outbound_denominator FROM integration_inventory_source_acceptance a " +
            "JOIN integration_inventory_canonical_observation o ON o.organization_id=a.organization_id " +
            "AND o.observation_id=a.observation_id WHERE a.organization_id=? " +
            "AND a.lineage_root_decision_id=? AND a.state='ACTIVE' FOR SHARE OF a,o"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, lineageRootDecisionId.valueForPersistence())
        statement.executeQuery().use { result -> if (result.next()) SelectionAnchor(result) else null }
    }

    private fun activeSelection(
        connection: Connection,
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        lock: Boolean
    ): CanonicalInventoryMeasureSelection? = connection.prepareStatement(
        "SELECT * FROM integration_inventory_measure_selection WHERE organization_id=? " +
            "AND lineage_root_decision_id=? AND state='ACTIVE'" + if (lock) " FOR UPDATE" else ""
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, lineageRootDecisionId.valueForPersistence())
        statement.executeQuery().use { result -> if (result.next()) readSelection(result) else null }
    }

    private fun hasHistory(
        connection: Connection,
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_inventory_measure_selection WHERE organization_id=? " +
            "AND lineage_root_decision_id=? LIMIT 1"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, lineageRootDecisionId.valueForPersistence())
        statement.executeQuery().use(ResultSet::next)
    }

    private fun selection(
        id: CanonicalInventoryMeasureSelectionId,
        organizationId: OrganizationId,
        root: SelectionRoot,
        anchor: SelectionAnchor,
        revision: Int,
        measure: CanonicalInventoryMeasure,
        principal: InventoryMeasureSelectionPrincipalReference,
        reason: CanonicalInventoryMeasureSelectionReason,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId,
        at: Instant,
        supersedes: CanonicalInventoryMeasureSelectionId?
    ) = CanonicalInventoryMeasureSelection(
        id, organizationId, root.connectionId, root.capability, root.decisionId, revision,
        CanonicalInventoryMeasureSelectionState.ACTIVE, measure, anchor.acceptanceId,
        anchor.acceptanceRevision, anchor.observationId, principal, reason, correlationId, at,
        supersedesSelectionId = supersedes
    )

    private fun insert(connection: Connection, selection: CanonicalInventoryMeasureSelection) {
        connection.prepareStatement(
            "INSERT INTO integration_inventory_measure_selection (organization_id,selection_id," +
                "connection_id,capability,lineage_root_decision_id,revision,state,measure," +
                "anchor_acceptance_id,anchor_acceptance_revision,anchor_observation_id," +
                "principal_ref,reason,correlation_id,selected_at,retired_at," +
                "supersedes_selection_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, selection.organizationId.value)
            statement.setObject(2, selection.id.valueForPersistence())
            statement.setObject(3, selection.connectionId.value)
            statement.setString(4, selection.capability)
            statement.setObject(5, selection.lineageRootDecisionId.valueForPersistence())
            statement.setInt(6, selection.revision)
            statement.setString(7, selection.state.name)
            statement.setString(8, selection.measure.name)
            statement.setObject(9, selection.anchorAcceptanceId.valueForPersistence())
            statement.setInt(10, selection.anchorAcceptanceRevision)
            statement.setObject(11, selection.anchorObservationId.valueForPersistence())
            statement.setString(12, selection.principalReference.encodedForPersistence())
            statement.setString(13, selection.reason.name)
            statement.setObject(14, selection.correlationId.valueForPersistence())
            statement.setTimestamp(15, Timestamp.from(selection.selectedAt))
            statement.setTimestamp(16, null)
            statement.setObject(17, selection.supersedesSelectionId?.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
    }

    private fun retire(
        connection: Connection,
        current: CanonicalInventoryMeasureSelection,
        principal: InventoryMeasureSelectionPrincipalReference,
        reason: CanonicalInventoryMeasureSelectionReason,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId,
        at: Instant
    ) {
        connection.prepareStatement(
            "UPDATE integration_inventory_measure_selection SET state='RETIRED',retired_at=? " +
                "WHERE organization_id=? AND selection_id=? AND state='ACTIVE'"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(at))
            statement.setObject(2, current.organizationId.value)
            statement.setObject(3, current.id.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
        connection.prepareStatement(
            "INSERT INTO integration_inventory_measure_selection_retirement " +
                "(organization_id,selection_id,principal_ref,reason,correlation_id,retired_at) " +
                "VALUES (?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, current.organizationId.value)
            statement.setObject(2, current.id.valueForPersistence())
            statement.setString(3, principal.encodedForPersistence())
            statement.setString(4, reason.name)
            statement.setObject(5, correlationId.valueForPersistence())
            statement.setTimestamp(6, Timestamp.from(at))
            check(statement.executeUpdate() == 1)
        }
    }

    private fun withdrawalWasApplied(
        connection: Connection,
        organizationId: OrganizationId,
        selectionId: CanonicalInventoryMeasureSelectionId,
        revision: Int
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_inventory_measure_selection s JOIN " +
            "integration_inventory_measure_selection_retirement r ON " +
            "r.organization_id=s.organization_id AND r.selection_id=s.selection_id " +
            "WHERE s.organization_id=? AND s.selection_id=? AND s.revision=? " +
            "AND s.state='RETIRED' AND r.reason IN " +
            "('SOURCE_SEMANTICS_REVOKED','OPERATOR_WITHDRAWAL')"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, selectionId.valueForPersistence())
        statement.setInt(3, revision)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun readSelection(result: ResultSet) = CanonicalInventoryMeasureSelection(
        CanonicalInventoryMeasureSelectionId.of(result.getObject("selection_id", UUID::class.java)),
        OrganizationId(result.getObject("organization_id", UUID::class.java)),
        IntegrationConnectionId(result.getObject("connection_id", UUID::class.java)),
        result.getString("capability"),
        InventoryMappingDecisionId.of(
            result.getObject("lineage_root_decision_id", UUID::class.java)
        ), result.getInt("revision"),
        CanonicalInventoryMeasureSelectionState.valueOf(result.getString("state")),
        CanonicalInventoryMeasure.valueOf(result.getString("measure")),
        CanonicalInventoryAcceptanceId.of(
            result.getObject("anchor_acceptance_id", UUID::class.java)
        ), result.getInt("anchor_acceptance_revision"),
        CanonicalInventoryObservationId.of(
            result.getObject("anchor_observation_id", UUID::class.java)
        ), InventoryMeasureSelectionPrincipalReference.of(result.getString("principal_ref")),
        CanonicalInventoryMeasureSelectionReason.valueOf(result.getString("reason")),
        CanonicalInventoryMeasureSelectionCorrelationId.of(
            result.getObject("correlation_id", UUID::class.java)
        ), result.getTimestamp("selected_at").toInstant(),
        result.getTimestamp("retired_at")?.toInstant(),
        result.getObject("supersedes_selection_id", UUID::class.java)
            ?.let(CanonicalInventoryMeasureSelectionId::of)
    )

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

    private fun targetsActive(
        connection: Connection,
        organizationId: OrganizationId,
        target: InventoryMappingTarget
    ): Boolean = identityActive(
        connection, "inventory_item_identity", organizationId,
        target.itemId.valueForPersistence()
    ) && identityActive(
        connection, "inventory_unit_identity", organizationId,
        target.unitId.valueForPersistence()
    ) && target.locationId.let { location -> location == null || identityActive(
        connection, "inventory_location_identity", organizationId,
        location.valueForPersistence()
    ) }

    private fun identityActive(
        connection: Connection,
        table: String,
        organizationId: OrganizationId,
        identityId: UUID
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM $table WHERE organization_id=? AND identity_id=? " +
            "AND state='ACTIVE' FOR SHARE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, identityId)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun transactionTime(connection: Connection): Instant =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT transaction_timestamp()").use { result ->
                result.next(); result.getTimestamp(1).toInstant()
            }
        }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url, configuration.user, configuration.password
    )

    private fun mutate(
        operation: (Connection) -> CanonicalInventoryMeasureSelectionResult
    ): CanonicalInventoryMeasureSelectionResult = try {
        connection().use { connection ->
            connection.autoCommit = false
            try { operation(connection).also { connection.commit() } }
            catch (error: Exception) { connection.rollback(); throw error }
        }
    } catch (_: Exception) { CanonicalInventoryMeasureSelectionResult.IntegrityFailure }
}

private class SelectionRoot(result: ResultSet) {
    val organizationId = OrganizationId(result.getObject("organization_id", UUID::class.java))
    val decisionId = InventoryMappingDecisionId.of(result.getObject("decision_id", UUID::class.java))
    val connectionId = IntegrationConnectionId(result.getObject("connection_id", UUID::class.java))
    val capability: String = result.getString("capability")
    val revision: Int = result.getInt("revision")
    val supersedesDecisionId: UUID? = result.getObject("supersedes_decision_id", UUID::class.java)
}

private class SelectionAnchor(result: ResultSet) {
    val acceptanceId = CanonicalInventoryAcceptanceId.of(
        result.getObject("acceptance_id", UUID::class.java)
    )
    val acceptanceRevision: Int = result.getInt("revision")
    val observationId = CanonicalInventoryObservationId.of(
        result.getObject("observation_id", UUID::class.java)
    )
    val connectionId = IntegrationConnectionId(result.getObject("connection_id", UUID::class.java))
    val capability: String = result.getString("capability")
    val sourceProgressVersion: Long = result.getLong("source_progress_version")
    val sourceRecordOrdinal: Int = result.getInt("source_record_ordinal")
    val projectionRevision: Int = result.getInt("projection_revision")
    val mappingDecisionId = InventoryMappingDecisionId.of(
        result.getObject("mapping_decision_id", UUID::class.java)
    )
    val mappingRevision: Int = result.getInt("mapping_revision")
    val target = InventoryMappingTarget(
        InventoryItemId.of(result.getObject("target_item_id", UUID::class.java)),
        result.getObject("target_location_id", UUID::class.java)?.let(InventoryLocationId::of),
        InventoryUnitId.of(result.getObject("target_unit_id", UUID::class.java)),
        QuantityFactor.of(result.getLong("factor_numerator"), result.getLong("factor_denominator"))
    )
    private val quantities = CanonicalInventoryMeasure.entries.associateWith { measure ->
        val prefix = when (measure) {
            CanonicalInventoryMeasure.AVAILABLE_TO_SELL -> "available_to_sell"
            CanonicalInventoryMeasure.ON_HAND -> "on_hand"
            CanonicalInventoryMeasure.RESERVED -> "reserved"
            CanonicalInventoryMeasure.PENDING_INBOUND -> "pending_inbound"
            CanonicalInventoryMeasure.PENDING_OUTBOUND -> "pending_outbound"
        }
        result.getBigDecimal("${prefix}_numerator")?.let {
            ExactInventoryQuantity.fromPersistence(
                it.toBigIntegerExact(), result.getLong("${prefix}_denominator")
            )
        }
    }

    fun quantity(measure: CanonicalInventoryMeasure) = quantities[measure]
}
