package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.mapping.*
import io.flooow.integration.inventory.source.SourceItemReference
import io.flooow.integration.inventory.source.SourceLocationReference
import io.flooow.integration.inventory.source.SourceUnitCode
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresInventoryIdentityMappingRepository(
    private val configuration: PostgresConfiguration
) : InventoryIdentityMappingRepository {
    override fun createItem(identity: InventoryItemIdentity) = createIdentity(
        "inventory_item_identity", identity.organizationId,
        identity.id.valueForPersistence(), identity.createdAt
    )

    override fun createLocation(identity: InventoryLocationIdentity) = createIdentity(
        "inventory_location_identity", identity.organizationId,
        identity.id.valueForPersistence(), identity.createdAt
    )

    override fun createUnit(identity: InventoryUnitIdentity) = createIdentity(
        "inventory_unit_identity", identity.organizationId,
        identity.id.valueForPersistence(), identity.createdAt
    )

    override fun retireItem(
        organizationId: OrganizationId, id: InventoryItemId, retiredAt: Instant
    ) = retireIdentity(
        "inventory_item_identity", organizationId, id.valueForPersistence(), retiredAt
    )

    override fun retireLocation(
        organizationId: OrganizationId, id: InventoryLocationId, retiredAt: Instant
    ) = retireIdentity(
        "inventory_location_identity", organizationId, id.valueForPersistence(), retiredAt
    )

    override fun retireUnit(
        organizationId: OrganizationId, id: InventoryUnitId, retiredAt: Instant
    ) = retireIdentity(
        "inventory_unit_identity", organizationId, id.valueForPersistence(), retiredAt
    )

    override fun activateInitial(decision: InventoryMappingDecision): MappingWriteResult =
        mappingMutation(decision) { connection ->
            preflight(connection, decision)?.let { return@mappingMutation it }
            replay(connection, decision)?.let { return@mappingMutation it }
            if (activeDecision(connection, decision.organizationId, decision.selector, true) != null) {
                return@mappingMutation MappingWriteResult.CONFLICT
            }
            insertDecision(connection, decision)
            MappingWriteResult.APPLIED
        }

    override fun replace(
        expectedDecisionId: InventoryMappingDecisionId,
        expectedRevision: Int,
        decision: InventoryMappingDecision
    ): MappingWriteResult = mappingMutation(decision) { connection ->
        preflight(connection, decision)?.let { return@mappingMutation it }
        replay(connection, decision)?.let { return@mappingMutation it }
        val current = activeDecision(connection, decision.organizationId, decision.selector, true)
            ?: return@mappingMutation MappingWriteResult.CONFLICT
        if (current.id != expectedDecisionId || current.revision != expectedRevision ||
            decision.supersedesDecisionId != expectedDecisionId ||
            decision.revision != expectedRevision + 1) {
            return@mappingMutation MappingWriteResult.CONFLICT
        }
        retireDecision(
            connection, current, decision.principalReference, decision.reason,
            decision.correlationId, decision.decidedAt
        )
        insertDecision(connection, decision)
        MappingWriteResult.APPLIED
    }

    override fun retireMapping(
        organizationId: OrganizationId,
        selector: InventorySourceSelector,
        expectedDecisionId: InventoryMappingDecisionId,
        expectedRevision: Int,
        principalReference: InventoryMappingPrincipalReference,
        reason: InventoryMappingReason,
        correlationId: InventoryMappingCorrelationId,
        retiredAt: Instant
    ): MappingWriteResult = try {
        transaction { connection ->
            if (!eligibleConnection(connection, organizationId, selector.connectionId)) {
                return@transaction MappingWriteResult.UNAVAILABLE
            }
            val current = activeDecision(connection, organizationId, selector, true)
            if (current == null) {
                return@transaction retirementReplay(
                    connection, organizationId, expectedDecisionId, expectedRevision,
                    principalReference, reason, correlationId, retiredAt
                )
            }
            if (current.id != expectedDecisionId || current.revision != expectedRevision) {
                return@transaction MappingWriteResult.CONFLICT
            }
            retireDecision(
                connection, current, principalReference, reason, correlationId, retiredAt
            )
            MappingWriteResult.APPLIED
        }
    } catch (_: Exception) {
        MappingWriteResult.INTEGRITY_FAILURE
    }

    override fun resolve(
        organizationId: OrganizationId,
        selector: InventorySourceSelector
    ): InventoryMappingResolution = try {
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT m.* FROM integration_inventory_source_mapping m " +
                    "JOIN integration_organization o ON o.organization_id=m.organization_id " +
                    "JOIN integration_connection c ON c.organization_id=m.organization_id " +
                    "AND c.connection_id=m.connection_id " +
                    "JOIN inventory_item_identity i ON i.organization_id=m.organization_id " +
                    "AND i.identity_id=m.target_item_id " +
                    "JOIN inventory_unit_identity u ON u.organization_id=m.organization_id " +
                    "AND u.identity_id=m.target_unit_id " +
                    "LEFT JOIN inventory_location_identity l ON l.organization_id=m.organization_id " +
                    "AND l.identity_id=m.target_location_id WHERE " + selectorPredicate("m") +
                    " AND m.state='ACTIVE' AND o.status='ACTIVE' " +
                    "AND c.status IN ('ACTIVE','SUSPENDED') AND i.state='ACTIVE' " +
                    "AND u.state='ACTIVE' AND (m.target_location_id IS NULL OR l.state='ACTIVE')"
            ).use { statement ->
                bindSelector(statement, organizationId, selector)
                statement.executeQuery().use { result ->
                    if (!result.next()) return InventoryMappingResolution.Unmapped
                    val decision = readDecision(result)
                    if (result.next()) return InventoryMappingResolution.IntegrityFailure
                    InventoryMappingResolution.Resolved(
                        decision.target, decision.id, decision.revision
                    )
                }
            }
        }
    } catch (_: SQLException) {
        InventoryMappingResolution.IntegrityFailure
    }

    override fun history(
        organizationId: OrganizationId,
        selector: InventorySourceSelector
    ): List<InventoryMappingDecision> = try {
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT m.* FROM integration_inventory_source_mapping m WHERE " +
                    selectorPredicate("m") + " ORDER BY revision"
            ).use { statement ->
                bindSelector(statement, organizationId, selector)
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(readDecision(result)) }
                }
            }
        }
    } catch (_: SQLException) {
        emptyList()
    }

    private fun createIdentity(
        table: String, organizationId: OrganizationId, identityId: UUID, createdAt: Instant
    ): IdentityWriteResult = try {
        transaction { connection ->
            if (!activeOrganization(connection, organizationId)) {
                return@transaction IdentityWriteResult.UNAVAILABLE
            }
            connection.prepareStatement(
                "SELECT state FROM $table WHERE organization_id=? AND identity_id=? FOR UPDATE"
            ).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setObject(2, identityId)
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        return@transaction if (result.getString(1) == "ACTIVE") {
                            IdentityWriteResult.ALREADY_APPLIED
                        } else {
                            IdentityWriteResult.STALE
                        }
                    }
                }
            }
            connection.prepareStatement(
                "INSERT INTO $table (organization_id,identity_id,state,created_at,retired_at) " +
                    "VALUES (?,?,'ACTIVE',?,NULL)"
            ).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setObject(2, identityId)
                statement.setTimestamp(3, Timestamp.from(createdAt))
                check(statement.executeUpdate() == 1)
            }
            IdentityWriteResult.APPLIED
        }
    } catch (_: Exception) {
        IdentityWriteResult.UNAVAILABLE
    }

    private fun retireIdentity(
        table: String, organizationId: OrganizationId, identityId: UUID, retiredAt: Instant
    ): IdentityWriteResult = try {
        transaction { connection ->
            if (!activeOrganization(connection, organizationId)) {
                return@transaction IdentityWriteResult.UNAVAILABLE
            }
            val state = connection.prepareStatement(
                "SELECT state FROM $table WHERE organization_id=? AND identity_id=? FOR UPDATE"
            ).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setObject(2, identityId)
                statement.executeQuery().use { result ->
                    if (!result.next()) return@transaction IdentityWriteResult.UNAVAILABLE
                    result.getString(1)
                }
            }
            if (state == "RETIRED") return@transaction IdentityWriteResult.ALREADY_APPLIED
            connection.prepareStatement(
                "UPDATE $table SET state='RETIRED',retired_at=? " +
                    "WHERE organization_id=? AND identity_id=? AND state='ACTIVE'"
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.from(retiredAt))
                statement.setObject(2, organizationId.value)
                statement.setObject(3, identityId)
                if (statement.executeUpdate() != 1) return@transaction IdentityWriteResult.STALE
            }
            IdentityWriteResult.APPLIED
        }
    } catch (_: Exception) {
        IdentityWriteResult.UNAVAILABLE
    }

    private fun mappingMutation(
        decision: InventoryMappingDecision,
        operation: (Connection) -> MappingWriteResult
    ): MappingWriteResult = try {
        transaction(operation)
    } catch (error: Exception) {
        if (error is SQLException && error.sqlState == "23505") classifyCollision(decision)
        else MappingWriteResult.INTEGRITY_FAILURE
    }

    private fun preflight(
        connection: Connection, decision: InventoryMappingDecision
    ): MappingWriteResult? {
        if (!eligibleConnection(
                connection, decision.organizationId, decision.selector.connectionId
            )) return MappingWriteResult.UNAVAILABLE
        if (!targetsActive(connection, decision)) return MappingWriteResult.TARGET_UNAVAILABLE
        if (!evidenceMatches(connection, decision)) return MappingWriteResult.EVIDENCE_MISMATCH
        return null
    }

    private fun activeOrganization(
        connection: Connection, organizationId: OrganizationId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_organization WHERE organization_id=? " +
            "AND status='ACTIVE' FOR SHARE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun eligibleConnection(
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

    private fun targetsActive(connection: Connection, decision: InventoryMappingDecision): Boolean {
        if (!identityActive(
                connection, "inventory_item_identity", decision.organizationId,
                decision.target.itemId.valueForPersistence()
            )) return false
        if (!identityActive(
                connection, "inventory_unit_identity", decision.organizationId,
                decision.target.unitId.valueForPersistence()
            )) return false
        val location = decision.target.locationId ?: return true
        return identityActive(
            connection, "inventory_location_identity", decision.organizationId,
            location.valueForPersistence()
        )
    }

    private fun identityActive(
        connection: Connection, table: String,
        organizationId: OrganizationId, identityId: UUID
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM $table WHERE organization_id=? AND identity_id=? " +
            "AND state='ACTIVE' FOR SHARE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, identityId)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun evidenceMatches(
        connection: Connection, decision: InventoryMappingDecision
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_inventory_source_balance WHERE organization_id=? " +
            "AND connection_id=? AND capability=? AND input_progress_version=? " +
            "AND record_ordinal=? AND source_item_ref=? " +
            "AND source_location_ref IS NOT DISTINCT FROM ? " +
            "AND source_unit_code IS NOT DISTINCT FROM ?"
    ).use { statement ->
        statement.setObject(1, decision.organizationId.value)
        statement.setObject(2, decision.evidence.connectionId.value)
        statement.setString(3, decision.evidence.capability)
        statement.setLong(4, decision.evidence.inputProgressVersion)
        statement.setInt(5, decision.evidence.recordOrdinal)
        statement.setString(6, decision.selector.sourceItemReference.encodedForPersistence())
        statement.setString(7, decision.selector.sourceLocationReference?.encodedForPersistence())
        statement.setString(8, decision.selector.sourceUnitCode?.encodedForPersistence())
        statement.executeQuery().use(ResultSet::next)
    }

    private fun replay(
        connection: Connection, decision: InventoryMappingDecision
    ): MappingWriteResult? {
        val stored = decisionById(connection, decision.organizationId, decision.id, true)
            ?: return null
        return if (stored == decision) MappingWriteResult.ALREADY_APPLIED
        else MappingWriteResult.INTEGRITY_FAILURE
    }

    private fun retirementReplay(
        connection: Connection,
        organizationId: OrganizationId,
        expectedDecisionId: InventoryMappingDecisionId,
        expectedRevision: Int,
        principal: InventoryMappingPrincipalReference,
        reason: InventoryMappingReason,
        correlationId: InventoryMappingCorrelationId,
        retiredAt: Instant
    ): MappingWriteResult {
        val stored = decisionById(connection, organizationId, expectedDecisionId, false)
            ?: return MappingWriteResult.CONFLICT
        if (stored.revision != expectedRevision || stored.state != InventoryMappingState.RETIRED ||
            stored.retiredAt != retiredAt) return MappingWriteResult.CONFLICT
        val matches = connection.prepareStatement(
            "SELECT 1 FROM integration_inventory_source_mapping_retirement " +
                "WHERE organization_id=? AND decision_id=? AND principal_ref=? " +
                "AND reason=? AND correlation_id=? AND retired_at=?"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, expectedDecisionId.valueForPersistence())
            statement.setString(3, principal.encodedForPersistence())
            statement.setString(4, reason.name)
            statement.setObject(5, correlationId.valueForPersistence())
            statement.setTimestamp(6, Timestamp.from(retiredAt))
            statement.executeQuery().use(ResultSet::next)
        }
        return if (matches) MappingWriteResult.ALREADY_APPLIED
        else MappingWriteResult.INTEGRITY_FAILURE
    }

    private fun activeDecision(
        connection: Connection,
        organizationId: OrganizationId,
        selector: InventorySourceSelector,
        lock: Boolean
    ): InventoryMappingDecision? = connection.prepareStatement(
        "SELECT m.* FROM integration_inventory_source_mapping m WHERE " +
            selectorPredicate("m") + " AND state='ACTIVE'" + if (lock) " FOR UPDATE" else ""
    ).use { statement ->
        bindSelector(statement, organizationId, selector)
        statement.executeQuery().use { result ->
            if (result.next()) readDecision(result) else null
        }
    }

    private fun decisionById(
        connection: Connection,
        organizationId: OrganizationId,
        id: InventoryMappingDecisionId,
        lock: Boolean
    ): InventoryMappingDecision? = connection.prepareStatement(
        "SELECT * FROM integration_inventory_source_mapping WHERE organization_id=? " +
            "AND decision_id=?" + if (lock) " FOR UPDATE" else ""
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, id.valueForPersistence())
        statement.executeQuery().use { result -> if (result.next()) readDecision(result) else null }
    }

    private fun retireDecision(
        connection: Connection,
        current: InventoryMappingDecision,
        principal: InventoryMappingPrincipalReference,
        reason: InventoryMappingReason,
        correlationId: InventoryMappingCorrelationId,
        retiredAt: Instant
    ) {
        connection.prepareStatement(
            "UPDATE integration_inventory_source_mapping SET state='RETIRED',retired_at=? " +
                "WHERE organization_id=? AND decision_id=? AND state='ACTIVE'"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(retiredAt))
            statement.setObject(2, current.organizationId.value)
            statement.setObject(3, current.id.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
        connection.prepareStatement(
            "INSERT INTO integration_inventory_source_mapping_retirement " +
                "(organization_id,decision_id,principal_ref,reason,correlation_id,retired_at) " +
                "VALUES (?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, current.organizationId.value)
            statement.setObject(2, current.id.valueForPersistence())
            statement.setString(3, principal.encodedForPersistence())
            statement.setString(4, reason.name)
            statement.setObject(5, correlationId.valueForPersistence())
            statement.setTimestamp(6, Timestamp.from(retiredAt))
            check(statement.executeUpdate() == 1)
        }
    }

    private fun insertDecision(connection: Connection, decision: InventoryMappingDecision) {
        connection.prepareStatement(
            "INSERT INTO integration_inventory_source_mapping (organization_id,connection_id," +
                "capability,source_item_ref,source_location_ref,source_unit_code,decision_id," +
                "revision,state,target_item_id,target_location_id,target_unit_id,factor_numerator," +
                "factor_denominator,evidence_progress_version,evidence_record_ordinal," +
                "principal_ref,reason,correlation_id,decided_at,retired_at," +
                "supersedes_decision_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, decision.organizationId.value)
            statement.setObject(2, decision.selector.connectionId.value)
            statement.setString(3, decision.selector.capability)
            statement.setString(4, decision.selector.sourceItemReference.encodedForPersistence())
            statement.setString(5, decision.selector.sourceLocationReference?.encodedForPersistence())
            statement.setString(6, decision.selector.sourceUnitCode?.encodedForPersistence())
            statement.setObject(7, decision.id.valueForPersistence())
            statement.setInt(8, decision.revision)
            statement.setString(9, decision.state.name)
            statement.setObject(10, decision.target.itemId.valueForPersistence())
            statement.setObject(11, decision.target.locationId?.valueForPersistence())
            statement.setObject(12, decision.target.unitId.valueForPersistence())
            statement.setLong(13, decision.target.quantityFactor.numerator)
            statement.setLong(14, decision.target.quantityFactor.denominator)
            statement.setLong(15, decision.evidence.inputProgressVersion)
            statement.setInt(16, decision.evidence.recordOrdinal)
            statement.setString(17, decision.principalReference.encodedForPersistence())
            statement.setString(18, decision.reason.name)
            statement.setObject(19, decision.correlationId.valueForPersistence())
            statement.setTimestamp(20, Timestamp.from(decision.decidedAt))
            statement.setTimestamp(21, decision.retiredAt?.let(Timestamp::from))
            statement.setObject(22, decision.supersedesDecisionId?.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
    }

    private fun readDecision(result: ResultSet): InventoryMappingDecision {
        val organization = OrganizationId(result.getObject("organization_id", UUID::class.java))
        val selector = InventorySourceSelector(
            IntegrationConnectionId(result.getObject("connection_id", UUID::class.java)),
            result.getString("capability"),
            SourceItemReference.of(result.getString("source_item_ref")),
            result.getString("source_location_ref")?.let(SourceLocationReference::of),
            result.getString("source_unit_code")?.let(SourceUnitCode::of)
        )
        val target = InventoryMappingTarget(
            InventoryItemId.of(result.getObject("target_item_id", UUID::class.java)),
            result.getObject("target_location_id", UUID::class.java)?.let(InventoryLocationId::of),
            InventoryUnitId.of(result.getObject("target_unit_id", UUID::class.java)),
            QuantityFactor.of(
                result.getLong("factor_numerator"), result.getLong("factor_denominator")
            )
        )
        return InventoryMappingDecision(
            InventoryMappingDecisionId.of(result.getObject("decision_id", UUID::class.java)),
            organization, selector, target,
            InventoryMappingEvidence(
                selector.connectionId, selector.capability,
                result.getLong("evidence_progress_version"),
                result.getInt("evidence_record_ordinal")
            ),
            result.getInt("revision"),
            InventoryMappingState.valueOf(result.getString("state")),
            InventoryMappingPrincipalReference.of(result.getString("principal_ref")),
            InventoryMappingReason.valueOf(result.getString("reason")),
            InventoryMappingCorrelationId.of(result.getObject("correlation_id", UUID::class.java)),
            result.getTimestamp("decided_at").toInstant(),
            result.getTimestamp("retired_at")?.toInstant(),
            result.getObject("supersedes_decision_id", UUID::class.java)
                ?.let(InventoryMappingDecisionId::of)
        )
    }

    private fun classifyCollision(decision: InventoryMappingDecision): MappingWriteResult = try {
        connection().use { connection ->
            val existing = decisionById(connection, decision.organizationId, decision.id, false)
            when {
                existing == decision -> MappingWriteResult.ALREADY_APPLIED
                existing != null -> MappingWriteResult.INTEGRITY_FAILURE
                activeDecision(connection, decision.organizationId, decision.selector, false) != null ->
                    MappingWriteResult.CONFLICT
                else -> MappingWriteResult.INTEGRITY_FAILURE
            }
        }
    } catch (_: SQLException) {
        MappingWriteResult.INTEGRITY_FAILURE
    }

    private fun selectorPredicate(alias: String) =
        "$alias.organization_id=? AND $alias.connection_id=? AND $alias.capability=? " +
            "AND $alias.source_item_ref=? AND " +
            "$alias.source_location_ref IS NOT DISTINCT FROM ? AND " +
            "$alias.source_unit_code IS NOT DISTINCT FROM ?"

    private fun bindSelector(
        statement: PreparedStatement,
        organizationId: OrganizationId,
        selector: InventorySourceSelector
    ) {
        statement.setObject(1, organizationId.value)
        statement.setObject(2, selector.connectionId.value)
        statement.setString(3, selector.capability)
        statement.setString(4, selector.sourceItemReference.encodedForPersistence())
        statement.setString(5, selector.sourceLocationReference?.encodedForPersistence())
        statement.setString(6, selector.sourceUnitCode?.encodedForPersistence())
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
