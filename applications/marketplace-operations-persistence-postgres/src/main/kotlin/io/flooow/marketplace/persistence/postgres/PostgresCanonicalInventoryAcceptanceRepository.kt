package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.acceptance.*
import io.flooow.integration.inventory.mapping.*
import io.flooow.integration.inventory.observation.CanonicalInventoryObservationId
import io.flooow.integration.inventory.observation.CanonicalInventorySourcePointer
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresCanonicalInventoryAcceptanceRepository(
    private val configuration: PostgresConfiguration
) : CanonicalInventoryAcceptanceRepository {
    override fun acceptInitial(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        candidateObservationId: CanonicalInventoryObservationId,
        acceptanceId: CanonicalInventoryAcceptanceId,
        principal: InventoryAcceptancePrincipalReference,
        correlationId: CanonicalInventoryAcceptanceCorrelationId
    ): CanonicalInventoryAcceptanceResult = mutate {
        val root = lockRoot(it, organizationId, lineageRootDecisionId)
            ?: return@mutate CanonicalInventoryAcceptanceResult.LineageUnavailable
        val candidate = candidate(it, organizationId, candidateObservationId)
            ?: return@mutate CanonicalInventoryAcceptanceResult.CandidateUnavailable
        validateCandidate(it, root, candidate)?.let { failure -> return@mutate failure }
        activeHead(it, organizationId, lineageRootDecisionId, true)?.let { current ->
            return@mutate if (current.acceptedObservation.observationId == candidate.observationId) {
                CanonicalInventoryAcceptanceResult.AlreadyAccepted(current.id, current.revision)
            } else CanonicalInventoryAcceptanceResult.Conflict
        }
        if (hasHistory(it, organizationId, lineageRootDecisionId)) {
            return@mutate CanonicalInventoryAcceptanceResult.Conflict
        }
        val decision = acceptance(
            acceptanceId, organizationId, root, candidate, 1, principal,
            CanonicalInventoryAcceptanceReason.INITIAL_ACCEPTANCE, correlationId,
            transactionTime(it), null
        )
        insert(it, decision)
        CanonicalInventoryAcceptanceResult.Accepted(decision.id, decision.revision)
    }

    override fun replace(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedAcceptanceId: CanonicalInventoryAcceptanceId,
        expectedRevision: Int,
        candidateObservationId: CanonicalInventoryObservationId,
        acceptanceId: CanonicalInventoryAcceptanceId,
        principal: InventoryAcceptancePrincipalReference,
        reason: CanonicalInventoryAcceptanceReason,
        correlationId: CanonicalInventoryAcceptanceCorrelationId
    ): CanonicalInventoryAcceptanceResult = mutate {
        if (!reason.isReplacement()) return@mutate CanonicalInventoryAcceptanceResult.IntegrityFailure
        val root = lockRoot(it, organizationId, lineageRootDecisionId)
            ?: return@mutate CanonicalInventoryAcceptanceResult.LineageUnavailable
        val candidate = candidate(it, organizationId, candidateObservationId)
            ?: return@mutate CanonicalInventoryAcceptanceResult.CandidateUnavailable
        validateCandidate(it, root, candidate)?.let { failure -> return@mutate failure }
        val current = activeHead(it, organizationId, lineageRootDecisionId, true)
            ?: return@mutate CanonicalInventoryAcceptanceResult.Unaccepted
        if (current.id != expectedAcceptanceId || current.revision != expectedRevision) {
            return@mutate CanonicalInventoryAcceptanceResult.Conflict
        }
        if (current.acceptedObservation.observationId == candidate.observationId) {
            return@mutate CanonicalInventoryAcceptanceResult.AlreadyAccepted(
                current.id, current.revision
            )
        }
        classify(current.acceptedObservation, candidate, reason)?.let { return@mutate it }
        val at = transactionTime(it)
        retire(it, current, principal, reason, correlationId, at)
        val successor = acceptance(
            acceptanceId, organizationId, root, candidate, current.revision + 1,
            principal, reason, correlationId, at, current.id
        )
        insert(it, successor)
        CanonicalInventoryAcceptanceResult.Accepted(successor.id, successor.revision)
    }

    override fun withdraw(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedAcceptanceId: CanonicalInventoryAcceptanceId,
        expectedRevision: Int,
        principal: InventoryAcceptancePrincipalReference,
        reason: CanonicalInventoryAcceptanceReason,
        correlationId: CanonicalInventoryAcceptanceCorrelationId
    ): CanonicalInventoryAcceptanceResult = mutate {
        if (!reason.isWithdrawal()) return@mutate CanonicalInventoryAcceptanceResult.IntegrityFailure
        lockRoot(it, organizationId, lineageRootDecisionId)
            ?: return@mutate CanonicalInventoryAcceptanceResult.LineageUnavailable
        val current = activeHead(it, organizationId, lineageRootDecisionId, true)
        if (current == null) {
            return@mutate if (withdrawalWasApplied(
                    it, organizationId, expectedAcceptanceId, expectedRevision
                )) CanonicalInventoryAcceptanceResult.Withdrawn(expectedRevision)
            else CanonicalInventoryAcceptanceResult.Unaccepted
        }
        if (current.id != expectedAcceptanceId || current.revision != expectedRevision) {
            return@mutate CanonicalInventoryAcceptanceResult.Conflict
        }
        if (!eligibleScope(it, organizationId, current.connectionId)) {
            return@mutate CanonicalInventoryAcceptanceResult.LineageUnavailable
        }
        retire(it, current, principal, reason, correlationId, transactionTime(it))
        CanonicalInventoryAcceptanceResult.Withdrawn(current.revision)
    }

    override fun head(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): CanonicalInventoryAcceptance? = try {
        connection().use { activeHead(it, organizationId, lineageRootDecisionId, false) }
    } catch (_: SQLException) { null }

    override fun history(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): List<CanonicalInventoryAcceptance> = try {
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT * FROM integration_inventory_source_acceptance " +
                    "WHERE organization_id=? AND lineage_root_decision_id=? ORDER BY revision"
            ).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setObject(2, lineageRootDecisionId.valueForPersistence())
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(readAcceptance(result)) }
                }
            }
        }
    } catch (_: SQLException) { emptyList() }

    private fun classify(
        current: AcceptedCanonicalInventoryObservation,
        candidate: AcceptanceCandidate,
        reason: CanonicalInventoryAcceptanceReason
    ): CanonicalInventoryAcceptanceResult? {
        val old = current.sourcePointer
        val next = candidate.pointer
        if (next.inputProgressVersion < old.inputProgressVersion) {
            return CanonicalInventoryAcceptanceResult.Stale
        }
        if (next.inputProgressVersion > old.inputProgressVersion) {
            return if (reason in setOf(
                    CanonicalInventoryAcceptanceReason.NEW_SOURCE_EVIDENCE,
                    CanonicalInventoryAcceptanceReason.OPERATOR_CORRECTION
                )) null else CanonicalInventoryAcceptanceResult.Conflict
        }
        if (next.recordOrdinal != old.recordOrdinal) {
            return CanonicalInventoryAcceptanceResult.Conflict
        }
        val advances = candidate.projectionRevision > current.projectionRevision &&
            candidate.mappingRevision > current.mappingRevision
        if (!advances) return CanonicalInventoryAcceptanceResult.Stale
        return if (reason in setOf(
                CanonicalInventoryAcceptanceReason.MAPPING_REINTERPRETATION,
                CanonicalInventoryAcceptanceReason.OPERATOR_CORRECTION
            )) null else CanonicalInventoryAcceptanceResult.Conflict
    }

    private fun validateCandidate(
        connection: Connection,
        root: MappingRoot,
        candidate: AcceptanceCandidate
    ): CanonicalInventoryAcceptanceResult? {
        if (!eligibleScope(connection, root.organizationId, candidate.pointer.connectionId)) {
            return CanonicalInventoryAcceptanceResult.LineageUnavailable
        }
        val mapping = connection.prepareStatement(
            "SELECT m.*,e.source_item_ref AS evidence_item,e.source_location_ref AS evidence_location," +
                "e.source_unit_code AS evidence_unit FROM integration_inventory_source_mapping m " +
                "JOIN integration_inventory_source_balance e ON e.organization_id=m.organization_id " +
                "AND e.connection_id=? AND e.capability=? AND e.input_progress_version=? " +
                "AND e.record_ordinal=? WHERE m.organization_id=? AND m.decision_id=?"
        ).use { statement ->
            statement.setObject(1, candidate.pointer.connectionId.value)
            statement.setString(2, candidate.pointer.capability)
            statement.setLong(3, candidate.pointer.inputProgressVersion)
            statement.setInt(4, candidate.pointer.recordOrdinal)
            statement.setObject(5, root.organizationId.value)
            statement.setObject(6, candidate.mappingDecisionId.valueForPersistence())
            statement.executeQuery().use { result -> if (result.next()) CandidateMapping(result) else null }
        } ?: return CanonicalInventoryAcceptanceResult.LineageUnavailable
        if (!mapping.matches(root, candidate)) {
            return CanonicalInventoryAcceptanceResult.LineageUnavailable
        }
        if (!lineageReachesRoot(connection, root, candidate)) {
            return CanonicalInventoryAcceptanceResult.LineageUnavailable
        }
        if (!targetsActive(connection, root.organizationId, candidate.target)) {
            return CanonicalInventoryAcceptanceResult.TargetUnavailable
        }
        return null
    }

    private fun lineageReachesRoot(
        connection: Connection,
        root: MappingRoot,
        candidate: AcceptanceCandidate
    ): Boolean = connection.prepareStatement(
        "WITH RECURSIVE lineage AS (" +
            "SELECT decision_id,supersedes_decision_id,revision,source_item_ref," +
            "source_location_ref,source_unit_code FROM integration_inventory_source_mapping " +
            "WHERE organization_id=? AND decision_id=? UNION ALL " +
            "SELECT p.decision_id,p.supersedes_decision_id,p.revision,p.source_item_ref," +
            "p.source_location_ref,p.source_unit_code FROM integration_inventory_source_mapping p " +
            "JOIN lineage c ON c.supersedes_decision_id=p.decision_id " +
            "WHERE p.organization_id=? AND p.connection_id=? AND p.capability=? " +
            "AND p.source_item_ref=? AND p.source_location_ref IS NOT DISTINCT FROM ? " +
            "AND p.source_unit_code IS NOT DISTINCT FROM ? AND p.revision=c.revision-1) " +
            "SELECT 1 FROM lineage WHERE decision_id=? AND revision=1 AND supersedes_decision_id IS NULL"
    ).use { statement ->
        statement.setObject(1, root.organizationId.value)
        statement.setObject(2, candidate.mappingDecisionId.valueForPersistence())
        statement.setObject(3, root.organizationId.value)
        statement.setObject(4, root.connectionId.value)
        statement.setString(5, root.capability)
        statement.setString(6, root.itemReference)
        statement.setString(7, root.locationReference)
        statement.setString(8, root.unitCode)
        statement.setObject(9, root.decisionId.valueForPersistence())
        statement.executeQuery().use(ResultSet::next)
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

    private fun lockRoot(
        connection: Connection,
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): MappingRoot? = connection.prepareStatement(
        "SELECT * FROM integration_inventory_source_mapping WHERE organization_id=? " +
            "AND decision_id=? FOR UPDATE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, lineageRootDecisionId.valueForPersistence())
        statement.executeQuery().use { result ->
            if (!result.next()) null else MappingRoot(result).takeIf {
                it.revision == 1 && it.supersedesDecisionId == null
            }
        }
    }

    private fun candidate(
        connection: Connection,
        organizationId: OrganizationId,
        observationId: CanonicalInventoryObservationId
    ): AcceptanceCandidate? = connection.prepareStatement(
        "SELECT * FROM integration_inventory_canonical_observation " +
            "WHERE organization_id=? AND observation_id=? FOR SHARE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, observationId.valueForPersistence())
        statement.executeQuery().use { result ->
            if (result.next()) AcceptanceCandidate(result) else null
        }
    }

    private fun activeHead(
        connection: Connection,
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        lock: Boolean
    ): CanonicalInventoryAcceptance? = connection.prepareStatement(
        "SELECT * FROM integration_inventory_source_acceptance WHERE organization_id=? " +
            "AND lineage_root_decision_id=? AND state='ACTIVE'" + if (lock) " FOR UPDATE" else ""
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, lineageRootDecisionId.valueForPersistence())
        statement.executeQuery().use { result -> if (result.next()) readAcceptance(result) else null }
    }

    private fun hasHistory(
        connection: Connection,
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_inventory_source_acceptance " +
            "WHERE organization_id=? AND lineage_root_decision_id=? LIMIT 1"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, lineageRootDecisionId.valueForPersistence())
        statement.executeQuery().use(ResultSet::next)
    }

    private fun withdrawalWasApplied(
        connection: Connection,
        organizationId: OrganizationId,
        acceptanceId: CanonicalInventoryAcceptanceId,
        revision: Int
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_inventory_source_acceptance a " +
            "JOIN integration_inventory_source_acceptance_retirement r " +
            "ON r.organization_id=a.organization_id AND r.acceptance_id=a.acceptance_id " +
            "WHERE a.organization_id=? AND a.acceptance_id=? AND a.revision=? " +
            "AND a.state='RETIRED' AND r.reason IN " +
            "('SOURCE_REVOKED','EVIDENCE_INVALIDATED','OPERATOR_WITHDRAWAL')"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, acceptanceId.valueForPersistence())
        statement.setInt(3, revision)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun acceptance(
        id: CanonicalInventoryAcceptanceId,
        organizationId: OrganizationId,
        root: MappingRoot,
        candidate: AcceptanceCandidate,
        revision: Int,
        principal: InventoryAcceptancePrincipalReference,
        reason: CanonicalInventoryAcceptanceReason,
        correlationId: CanonicalInventoryAcceptanceCorrelationId,
        acceptedAt: Instant,
        supersedes: CanonicalInventoryAcceptanceId?
    ) = CanonicalInventoryAcceptance(
        id, organizationId, candidate.pointer.connectionId, candidate.pointer.capability,
        root.decisionId, revision, CanonicalInventoryAcceptanceState.ACTIVE,
        candidate.reference(), principal, reason, correlationId, acceptedAt,
        supersedesAcceptanceId = supersedes
    )

    private fun insert(connection: Connection, acceptance: CanonicalInventoryAcceptance) {
        connection.prepareStatement(
            "INSERT INTO integration_inventory_source_acceptance (organization_id,acceptance_id," +
                "connection_id,capability,lineage_root_decision_id,revision,state,observation_id," +
                "source_progress_version,source_record_ordinal,projection_revision," +
                "mapping_decision_id,mapping_revision,target_item_id,target_location_id," +
                "target_unit_id,factor_numerator,factor_denominator,principal_ref,reason," +
                "correlation_id,accepted_at,retired_at,supersedes_acceptance_id) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            var i = 1
            statement.setObject(i++, acceptance.organizationId.value)
            statement.setObject(i++, acceptance.id.valueForPersistence())
            statement.setObject(i++, acceptance.connectionId.value)
            statement.setString(i++, acceptance.capability)
            statement.setObject(i++, acceptance.lineageRootDecisionId.valueForPersistence())
            statement.setInt(i++, acceptance.revision)
            statement.setString(i++, acceptance.state.name)
            val candidate = acceptance.acceptedObservation
            statement.setObject(i++, candidate.observationId.valueForPersistence())
            statement.setLong(i++, candidate.sourcePointer.inputProgressVersion)
            statement.setInt(i++, candidate.sourcePointer.recordOrdinal)
            statement.setInt(i++, candidate.projectionRevision)
            statement.setObject(i++, candidate.mappingDecisionId.valueForPersistence())
            statement.setInt(i++, candidate.mappingRevision)
            statement.setObject(i++, candidate.target.itemId.valueForPersistence())
            statement.setObject(i++, candidate.target.locationId?.valueForPersistence())
            statement.setObject(i++, candidate.target.unitId.valueForPersistence())
            statement.setLong(i++, candidate.target.quantityFactor.numerator)
            statement.setLong(i++, candidate.target.quantityFactor.denominator)
            statement.setString(i++, acceptance.principalReference.encodedForPersistence())
            statement.setString(i++, acceptance.reason.name)
            statement.setObject(i++, acceptance.correlationId.valueForPersistence())
            statement.setTimestamp(i++, Timestamp.from(acceptance.acceptedAt))
            statement.setTimestamp(i++, null)
            statement.setObject(i, acceptance.supersedesAcceptanceId?.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
    }

    private fun retire(
        connection: Connection,
        current: CanonicalInventoryAcceptance,
        principal: InventoryAcceptancePrincipalReference,
        reason: CanonicalInventoryAcceptanceReason,
        correlationId: CanonicalInventoryAcceptanceCorrelationId,
        at: Instant
    ) {
        connection.prepareStatement(
            "UPDATE integration_inventory_source_acceptance SET state='RETIRED',retired_at=? " +
                "WHERE organization_id=? AND acceptance_id=? AND state='ACTIVE'"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(at))
            statement.setObject(2, current.organizationId.value)
            statement.setObject(3, current.id.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
        connection.prepareStatement(
            "INSERT INTO integration_inventory_source_acceptance_retirement " +
                "(organization_id,acceptance_id,principal_ref,reason,correlation_id,retired_at) " +
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

    private fun readAcceptance(result: ResultSet): CanonicalInventoryAcceptance {
        val pointer = CanonicalInventorySourcePointer(
            IntegrationConnectionId(result.getObject("connection_id", UUID::class.java)),
            result.getString("capability"), result.getLong("source_progress_version"),
            result.getInt("source_record_ordinal")
        )
        val target = InventoryMappingTarget(
            InventoryItemId.of(result.getObject("target_item_id", UUID::class.java)),
            result.getObject("target_location_id", UUID::class.java)?.let(InventoryLocationId::of),
            InventoryUnitId.of(result.getObject("target_unit_id", UUID::class.java)),
            QuantityFactor.of(
                result.getLong("factor_numerator"), result.getLong("factor_denominator")
            )
        )
        return CanonicalInventoryAcceptance(
            CanonicalInventoryAcceptanceId.of(
                result.getObject("acceptance_id", UUID::class.java)
            ),
            OrganizationId(result.getObject("organization_id", UUID::class.java)),
            pointer.connectionId, pointer.capability,
            InventoryMappingDecisionId.of(
                result.getObject("lineage_root_decision_id", UUID::class.java)
            ),
            result.getInt("revision"),
            CanonicalInventoryAcceptanceState.valueOf(result.getString("state")),
            AcceptedCanonicalInventoryObservation(
                CanonicalInventoryObservationId.of(
                    result.getObject("observation_id", UUID::class.java)
                ), pointer, result.getInt("projection_revision"),
                InventoryMappingDecisionId.of(
                    result.getObject("mapping_decision_id", UUID::class.java)
                ), result.getInt("mapping_revision"), target
            ),
            InventoryAcceptancePrincipalReference.of(result.getString("principal_ref")),
            CanonicalInventoryAcceptanceReason.valueOf(result.getString("reason")),
            CanonicalInventoryAcceptanceCorrelationId.of(
                result.getObject("correlation_id", UUID::class.java)
            ), result.getTimestamp("accepted_at").toInstant(),
            result.getTimestamp("retired_at")?.toInstant(),
            result.getObject("supersedes_acceptance_id", UUID::class.java)
                ?.let(CanonicalInventoryAcceptanceId::of)
        )
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
        operation: (Connection) -> CanonicalInventoryAcceptanceResult
    ): CanonicalInventoryAcceptanceResult = try {
        connection().use { connection ->
            connection.autoCommit = false
            try { operation(connection).also { connection.commit() } }
            catch (error: Exception) { connection.rollback(); throw error }
        }
    } catch (_: SQLException) {
        CanonicalInventoryAcceptanceResult.IntegrityFailure
    } catch (_: Exception) {
        CanonicalInventoryAcceptanceResult.IntegrityFailure
    }
}

private class MappingRoot(result: ResultSet) {
    val organizationId = OrganizationId(result.getObject("organization_id", UUID::class.java))
    val decisionId = InventoryMappingDecisionId.of(result.getObject("decision_id", UUID::class.java))
    val connectionId = IntegrationConnectionId(result.getObject("connection_id", UUID::class.java))
    val capability: String = result.getString("capability")
    val itemReference: String = result.getString("source_item_ref")
    val locationReference: String? = result.getString("source_location_ref")
    val unitCode: String? = result.getString("source_unit_code")
    val revision: Int = result.getInt("revision")
    val supersedesDecisionId: UUID? = result.getObject("supersedes_decision_id", UUID::class.java)
}

private class AcceptanceCandidate(result: ResultSet) {
    val observationId = CanonicalInventoryObservationId.of(
        result.getObject("observation_id", UUID::class.java)
    )
    val pointer = CanonicalInventorySourcePointer(
        IntegrationConnectionId(result.getObject("connection_id", UUID::class.java)),
        result.getString("capability"), result.getLong("input_progress_version"),
        result.getInt("record_ordinal")
    )
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

    fun reference() = AcceptedCanonicalInventoryObservation(
        observationId, pointer, projectionRevision, mappingDecisionId, mappingRevision, target
    )
}

private class CandidateMapping(result: ResultSet) {
    private val state: String = result.getString("state")
    private val connectionId = IntegrationConnectionId(
        result.getObject("connection_id", UUID::class.java)
    )
    private val capability: String = result.getString("capability")
    private val itemReference: String = result.getString("source_item_ref")
    private val locationReference: String? = result.getString("source_location_ref")
    private val unitCode: String? = result.getString("source_unit_code")
    private val evidenceItem: String = result.getString("evidence_item")
    private val evidenceLocation: String? = result.getString("evidence_location")
    private val evidenceUnit: String? = result.getString("evidence_unit")
    private val revision: Int = result.getInt("revision")
    private val itemId: UUID = result.getObject("target_item_id", UUID::class.java)
    private val locationId: UUID? = result.getObject("target_location_id", UUID::class.java)
    private val unitId: UUID = result.getObject("target_unit_id", UUID::class.java)
    private val numerator: Long = result.getLong("factor_numerator")
    private val denominator: Long = result.getLong("factor_denominator")

    fun matches(root: MappingRoot, candidate: AcceptanceCandidate) = state == "ACTIVE" &&
        connectionId == root.connectionId && capability == root.capability &&
        itemReference == root.itemReference && locationReference == root.locationReference &&
        unitCode == root.unitCode && itemReference == evidenceItem &&
        locationReference == evidenceLocation && unitCode == evidenceUnit &&
        revision == candidate.mappingRevision &&
        itemId == candidate.target.itemId.valueForPersistence() &&
        locationId == candidate.target.locationId?.valueForPersistence() &&
        unitId == candidate.target.unitId.valueForPersistence() &&
        numerator == candidate.target.quantityFactor.numerator &&
        denominator == candidate.target.quantityFactor.denominator
}
