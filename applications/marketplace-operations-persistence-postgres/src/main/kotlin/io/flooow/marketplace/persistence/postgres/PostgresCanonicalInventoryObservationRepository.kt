package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.inventory.mapping.InventoryItemId
import io.flooow.integration.inventory.mapping.InventoryLocationId
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.mapping.InventoryMappingTarget
import io.flooow.integration.inventory.mapping.InventoryUnitId
import io.flooow.integration.inventory.mapping.QuantityFactor
import io.flooow.integration.inventory.observation.*
import io.flooow.integration.inventory.source.SourceQuantity
import io.flooow.organization.OrganizationId
import java.math.BigInteger
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresCanonicalInventoryObservationRepository(
    private val configuration: PostgresConfiguration
) : CanonicalInventoryObservationRepository {
    override fun project(
        organizationId: OrganizationId,
        sourcePointer: CanonicalInventorySourcePointer,
        observationId: CanonicalInventoryObservationId,
        correlationId: CanonicalInventoryObservationCorrelationId
    ): CanonicalInventoryProjectionResult = try {
        transaction { connection ->
            val source = lockedSource(connection, organizationId, sourcePointer)
                ?: return@transaction CanonicalInventoryProjectionResult.SourceUnavailable
            if (!eligibleScope(connection, organizationId, sourcePointer)) {
                return@transaction CanonicalInventoryProjectionResult.SourceUnavailable
            }
            val mapping = activeMapping(connection, organizationId, sourcePointer, source)
                ?: return@transaction CanonicalInventoryProjectionResult.Unmapped
            if (!activeTargets(connection, organizationId, mapping)) {
                return@transaction CanonicalInventoryProjectionResult.TargetUnavailable
            }
            val measures = source.measures(mapping.factor)
            val history = historyIn(connection, organizationId, sourcePointer, true)
            history.firstOrNull { it.mappingDecisionId == mapping.decisionId }?.let { existing ->
                if (existing.mappingRevision != mapping.revision ||
                    existing.target != mapping.target || existing.measures != measures ||
                    existing.sourceUpdatedAt != source.sourceUpdatedAt ||
                    existing.sourceCommittedAt != source.sourceCommittedAt) {
                    return@transaction CanonicalInventoryProjectionResult.IntegrityFailure
                }
                return@transaction CanonicalInventoryProjectionResult.AlreadyProjected(
                    existing.id, existing.projectionRevision
                )
            }
            val prior = history.lastOrNull()
            if (prior != null && mapping.revision <= prior.mappingRevision) {
                return@transaction CanonicalInventoryProjectionResult.Conflict
            }
            val observation = CanonicalInventoryObservation(
                observationId, organizationId, sourcePointer,
                (prior?.projectionRevision ?: 0) + 1,
                mapping.decisionId, mapping.revision, mapping.target, measures,
                source.sourceUpdatedAt, source.sourceCommittedAt, transactionTime(connection),
                correlationId, prior?.id
            )
            insert(connection, observation)
            CanonicalInventoryProjectionResult.Projected(
                observation.id, observation.projectionRevision
            )
        }
    } catch (_: SQLException) {
        CanonicalInventoryProjectionResult.IntegrityFailure
    } catch (_: Exception) {
        CanonicalInventoryProjectionResult.IntegrityFailure
    }

    override fun find(
        organizationId: OrganizationId,
        observationId: CanonicalInventoryObservationId
    ): CanonicalInventoryObservation? = try {
        connection().use { connection ->
            connection.prepareStatement(
                "SELECT * FROM integration_inventory_canonical_observation " +
                    "WHERE organization_id=? AND observation_id=?"
            ).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setObject(2, observationId.valueForPersistence())
                statement.executeQuery().use { result ->
                    if (result.next()) readObservation(result) else null
                }
            }
        }
    } catch (_: SQLException) { null }

    override fun history(
        organizationId: OrganizationId,
        sourcePointer: CanonicalInventorySourcePointer
    ): List<CanonicalInventoryObservation> = try {
        connection().use { historyIn(it, organizationId, sourcePointer, false) }
    } catch (_: SQLException) { emptyList() }

    private fun lockedSource(
        connection: Connection,
        organizationId: OrganizationId,
        pointer: CanonicalInventorySourcePointer
    ): SourceRow? = connection.prepareStatement(
        "SELECT e.*,p.committed_at FROM integration_inventory_source_balance e " +
            "JOIN integration_connector_page_commit p ON p.organization_id=e.organization_id " +
            "AND p.connection_id=e.connection_id AND p.capability=e.capability " +
            "AND p.input_progress_version=e.input_progress_version " +
            "WHERE e.organization_id=? AND e.connection_id=? AND e.capability=? " +
            "AND e.input_progress_version=? AND e.record_ordinal=? FOR UPDATE OF e"
    ).use { statement ->
        bindPointer(statement, organizationId, pointer)
        statement.executeQuery().use { result -> if (result.next()) SourceRow(result) else null }
    }

    private fun eligibleScope(
        connection: Connection,
        organizationId: OrganizationId,
        pointer: CanonicalInventorySourcePointer
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_organization o JOIN integration_connection c " +
            "ON c.organization_id=o.organization_id WHERE o.organization_id=? " +
            "AND c.connection_id=? AND o.status='ACTIVE' " +
            "AND c.status IN ('ACTIVE','SUSPENDED') FOR SHARE OF o,c"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, pointer.connectionId.value)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun activeMapping(
        connection: Connection,
        organizationId: OrganizationId,
        pointer: CanonicalInventorySourcePointer,
        source: SourceRow
    ): MappingRow? = connection.prepareStatement(
        "SELECT * FROM integration_inventory_source_mapping WHERE organization_id=? " +
            "AND connection_id=? AND capability=? AND source_item_ref=? " +
            "AND source_location_ref IS NOT DISTINCT FROM ? " +
            "AND source_unit_code IS NOT DISTINCT FROM ? AND state='ACTIVE' FOR UPDATE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, pointer.connectionId.value)
        statement.setString(3, pointer.capability)
        statement.setString(4, source.itemReference)
        statement.setString(5, source.locationReference)
        statement.setString(6, source.unitCode)
        statement.executeQuery().use { result ->
            if (!result.next()) return@use null
            val row = MappingRow(result)
            if (result.next()) throw SQLException("mapping integrity")
            row
        }
    }

    private fun activeTargets(
        connection: Connection,
        organizationId: OrganizationId,
        mapping: MappingRow
    ): Boolean = identityActive(connection, "inventory_item_identity", organizationId, mapping.itemId) &&
        identityActive(connection, "inventory_unit_identity", organizationId, mapping.unitId) &&
        (mapping.locationId == null || identityActive(
            connection, "inventory_location_identity", organizationId, mapping.locationId
        ))

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

    private fun historyIn(
        connection: Connection,
        organizationId: OrganizationId,
        pointer: CanonicalInventorySourcePointer,
        lock: Boolean
    ): List<CanonicalInventoryObservation> = connection.prepareStatement(
        "SELECT * FROM integration_inventory_canonical_observation WHERE organization_id=? " +
            "AND connection_id=? AND capability=? AND input_progress_version=? " +
            "AND record_ordinal=? ORDER BY projection_revision" + if (lock) " FOR UPDATE" else ""
    ).use { statement ->
        bindPointer(statement, organizationId, pointer)
        statement.executeQuery().use { result ->
            buildList { while (result.next()) add(readObservation(result)) }
        }
    }

    private fun insert(connection: Connection, observation: CanonicalInventoryObservation) {
        connection.prepareStatement(
            "INSERT INTO integration_inventory_canonical_observation VALUES " +
                "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            var index = 1
            statement.setObject(index++, observation.organizationId.value)
            statement.setObject(index++, observation.id.valueForPersistence())
            statement.setObject(index++, observation.sourcePointer.connectionId.value)
            statement.setString(index++, observation.sourcePointer.capability)
            statement.setLong(index++, observation.sourcePointer.inputProgressVersion)
            statement.setInt(index++, observation.sourcePointer.recordOrdinal)
            statement.setInt(index++, observation.projectionRevision)
            statement.setObject(index++, observation.mappingDecisionId.valueForPersistence())
            statement.setInt(index++, observation.mappingRevision)
            statement.setObject(index++, observation.target.itemId.valueForPersistence())
            statement.setObject(index++, observation.target.locationId?.valueForPersistence())
            statement.setObject(index++, observation.target.unitId.valueForPersistence())
            statement.setLong(index++, observation.target.quantityFactor.numerator)
            statement.setLong(index++, observation.target.quantityFactor.denominator)
            index = bindQuantity(statement, index, observation.measures.availableToSell)
            index = bindQuantity(statement, index, observation.measures.onHand)
            index = bindQuantity(statement, index, observation.measures.reserved)
            index = bindQuantity(statement, index, observation.measures.pendingInbound)
            index = bindQuantity(statement, index, observation.measures.pendingOutbound)
            statement.setTimestamp(index++, observation.sourceUpdatedAt?.let(Timestamp::from))
            statement.setTimestamp(index++, Timestamp.from(observation.sourceCommittedAt))
            statement.setTimestamp(index++, Timestamp.from(observation.projectedAt))
            statement.setObject(index++, observation.correlationId.valueForPersistence())
            statement.setObject(index, observation.supersedesObservationId?.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
    }

    private fun readObservation(result: ResultSet): CanonicalInventoryObservation =
        CanonicalInventoryObservation(
            CanonicalInventoryObservationId.of(result.getObject("observation_id", UUID::class.java)),
            OrganizationId(result.getObject("organization_id", UUID::class.java)),
            CanonicalInventorySourcePointer(
                io.flooow.integration.control.IntegrationConnectionId(
                    result.getObject("connection_id", UUID::class.java)
                ), result.getString("capability"), result.getLong("input_progress_version"),
                result.getInt("record_ordinal")
            ),
            result.getInt("projection_revision"),
            InventoryMappingDecisionId.of(result.getObject("mapping_decision_id", UUID::class.java)),
            result.getInt("mapping_revision"),
            InventoryMappingTarget(
                InventoryItemId.of(result.getObject("target_item_id", UUID::class.java)),
                result.getObject("target_location_id", UUID::class.java)?.let(InventoryLocationId::of),
                InventoryUnitId.of(result.getObject("target_unit_id", UUID::class.java)),
                QuantityFactor.of(result.getLong("factor_numerator"), result.getLong("factor_denominator"))
            ),
            CanonicalInventoryMeasures(
                quantity(result, "available_to_sell"), quantity(result, "on_hand"),
                quantity(result, "reserved"), quantity(result, "pending_inbound"),
                quantity(result, "pending_outbound")
            ),
            result.getTimestamp("source_updated_at")?.toInstant(),
            result.getTimestamp("source_committed_at").toInstant(),
            result.getTimestamp("projected_at").toInstant(),
            CanonicalInventoryObservationCorrelationId.of(
                result.getObject("correlation_id", UUID::class.java)
            ),
            result.getObject("supersedes_observation_id", UUID::class.java)
                ?.let(CanonicalInventoryObservationId::of)
        )

    private fun quantity(result: ResultSet, prefix: String): ExactInventoryQuantity? =
        result.getBigDecimal("${prefix}_numerator")?.let {
            ExactInventoryQuantity.fromPersistence(it.toBigIntegerExact(), result.getLong("${prefix}_denominator"))
        }

    private fun bindQuantity(
        statement: java.sql.PreparedStatement,
        start: Int,
        value: ExactInventoryQuantity?
    ): Int {
        statement.setBigDecimal(start, value?.numeratorForPersistence()?.toBigDecimal())
        if (value == null) statement.setObject(start + 1, null)
        else statement.setLong(start + 1, value.denominatorForPersistence())
        return start + 2
    }

    private fun bindPointer(
        statement: java.sql.PreparedStatement,
        organizationId: OrganizationId,
        pointer: CanonicalInventorySourcePointer
    ) {
        statement.setObject(1, organizationId.value)
        statement.setObject(2, pointer.connectionId.value)
        statement.setString(3, pointer.capability)
        statement.setLong(4, pointer.inputProgressVersion)
        statement.setInt(5, pointer.recordOrdinal)
    }

    private fun transactionTime(connection: Connection): Instant =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT transaction_timestamp()").use { result ->
                result.next(); result.getTimestamp(1).toInstant()
            }
        }

    private fun connection() = DriverManager.getConnection(
        configuration.url, configuration.user, configuration.password
    )

    private fun <T> transaction(operation: (Connection) -> T): T = connection().use { connection ->
        connection.autoCommit = false
        try { operation(connection).also { connection.commit() } }
        catch (error: Exception) { connection.rollback(); throw error }
    }
}

private class SourceRow(result: ResultSet) {
    val itemReference: String = result.getString("source_item_ref")
    val locationReference: String? = result.getString("source_location_ref")
    val unitCode: String? = result.getString("source_unit_code")
    val sourceUpdatedAt: Instant? = result.getTimestamp("source_updated_at")?.toInstant()
    val sourceCommittedAt: Instant = result.getTimestamp("committed_at").toInstant()
    private val availableToSell = result.getBigDecimal("available_to_sell")
    private val onHand = result.getBigDecimal("on_hand")
    private val reserved = result.getBigDecimal("reserved")
    private val pendingInbound = result.getBigDecimal("pending_inbound")
    private val pendingOutbound = result.getBigDecimal("pending_outbound")

    fun measures(factor: QuantityFactor) = CanonicalInventoryMeasures(
        availableToSell?.let { ExactInventoryQuantity.from(SourceQuantity.parse(it.toPlainString()), factor) },
        onHand?.let { ExactInventoryQuantity.from(SourceQuantity.parse(it.toPlainString()), factor) },
        reserved?.let { ExactInventoryQuantity.from(SourceQuantity.parse(it.toPlainString()), factor) },
        pendingInbound?.let { ExactInventoryQuantity.from(SourceQuantity.parse(it.toPlainString()), factor) },
        pendingOutbound?.let { ExactInventoryQuantity.from(SourceQuantity.parse(it.toPlainString()), factor) }
    )
}

private class MappingRow(result: ResultSet) {
    val decisionId = InventoryMappingDecisionId.of(result.getObject("decision_id", UUID::class.java))
    val revision: Int = result.getInt("revision")
    val itemId: UUID = result.getObject("target_item_id", UUID::class.java)
    val locationId: UUID? = result.getObject("target_location_id", UUID::class.java)
    val unitId: UUID = result.getObject("target_unit_id", UUID::class.java)
    val factor = QuantityFactor.of(result.getLong("factor_numerator"), result.getLong("factor_denominator"))
    val target = InventoryMappingTarget(
        InventoryItemId.of(itemId), locationId?.let(InventoryLocationId::of),
        InventoryUnitId.of(unitId), factor
    )
}
