package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.IdentifierFactory
import io.flooow.integration.control.IntegrationAuditEntryId
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationControlPlaneService
import io.flooow.integration.control.ProviderKey
import io.flooow.integration.control.SecretReference
import io.flooow.integration.control.SecretVault
import io.flooow.integration.inventory.mapping.*
import io.flooow.integration.inventory.source.SourceItemReference
import io.flooow.integration.inventory.source.SourceLocationReference
import io.flooow.integration.inventory.source.SourceUnitCode
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.testcontainers.postgresql.PostgreSQLContainer

class PostgresInventoryIdentityMappingRepositoryTest {
    private val now = Instant.parse("2026-08-12T14:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var control: IntegrationControlPlaneService
    private val identifiers = AtomicLong(1)

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(postgres.jdbcUrl, postgres.username, postgres.password)
        control = IntegrationControlPlaneService(
            PostgresIntegrationControlPlaneRepository.connect(configuration),
            MappingTestVault(),
            clock,
            organizationIds = IdentifierFactory { OrganizationId(uuid(1)) },
            connectionIds = IdentifierFactory { IntegrationConnectionId(uuid(2)) },
            auditIds = IdentifierFactory { IntegrationAuditEntryId(uuid(3)) },
            correlationIds = IdentifierFactory { uuid(4) }
        )
    }

    @AfterTest
    fun stopPostgres() = postgres.stop()

    @Test
    fun `exact evidence backed mapping resolves without null wildcard or organization leakage`() {
        val active = activeConnection()
        seedLedger(active, "source-item", "warehouse-a", "BOX")
        val service = mappingService()
        val item = createdItem(service, active.first)
        val location = createdLocation(service, active.first)
        val unit = createdUnit(service, active.first)
        val selector = selector(active.second, "source-item", "warehouse-a", "BOX")
        val decision = InventoryMappingDecisionId.of(uuid(20))
        val correlation = InventoryMappingCorrelationId.of(uuid(21))

        assertEquals(
            MappingWriteResult.APPLIED,
            service.activateInitial(
                active.first, selector,
                InventoryMappingTarget(item, location, unit, QuantityFactor.of(12, 1)),
                evidence(active.second), principal(), decision, correlation
            )
        )
        assertEquals(
            MappingWriteResult.ALREADY_APPLIED,
            service.activateInitial(
                active.first, selector,
                InventoryMappingTarget(item, location, unit, QuantityFactor.of(12, 1)),
                evidence(active.second), principal(), decision, correlation
            )
        )

        val resolved = assertIs<InventoryMappingResolution.Resolved>(
            service.resolve(active.first, selector)
        )
        assertEquals(item, resolved.target.itemId)
        assertEquals(location, resolved.target.locationId)
        assertEquals(QuantityFactor.of(12, 1), resolved.target.quantityFactor)
        assertEquals(1, resolved.revision)
        assertEquals(
            InventoryMappingResolution.Unmapped,
            service.resolve(
                active.first,
                selector(active.second, "source-item", null, "BOX")
            )
        )
        assertEquals(
            InventoryMappingResolution.Unmapped,
            service.resolve(
                OrganizationId(uuid(999)), selector
            )
        )
        assertFalse(resolved.toString().contains("source-item"))
    }

    @Test
    fun `replacement and retirement preserve ordered immutable history`() {
        val active = activeConnection()
        seedLedger(active, "replace-item", null, null)
        val service = mappingService()
        val firstItem = createdItem(service, active.first)
        val secondItem = createdItem(service, active.first)
        val unit = createdUnit(service, active.first)
        val selector = selector(active.second, "replace-item", null, null)
        assertEquals(
            MappingWriteResult.APPLIED,
            service.activateInitial(
                active.first, selector,
                InventoryMappingTarget(firstItem, null, unit, QuantityFactor.of(1, 1)),
                evidence(active.second), principal()
            )
        )
        val initial = service.history(active.first, selector).single()

        assertEquals(
            MappingWriteResult.APPLIED,
            service.replace(
                active.first, selector,
                InventoryMappingTarget(secondItem, null, unit, QuantityFactor.of(1, 2)),
                evidence(active.second), initial.id, initial.revision, principal(),
                InventoryMappingReason.IDENTITY_CORRECTION
            )
        )
        val history = service.history(active.first, selector)
        assertEquals(listOf(1, 2), history.map { it.revision })
        assertEquals(
            listOf(InventoryMappingState.RETIRED, InventoryMappingState.ACTIVE),
            history.map { it.state }
        )
        assertEquals(initial.id, history[1].supersedesDecisionId)
        assertEquals(1, count("integration_inventory_source_mapping_retirement"))

        assertEquals(
            MappingWriteResult.CONFLICT,
            service.replace(
                active.first, selector,
                InventoryMappingTarget(firstItem, null, unit, QuantityFactor.of(1, 1)),
                evidence(active.second), initial.id, 1, principal(),
                InventoryMappingReason.IDENTITY_CORRECTION
            )
        )
        val current = history.last()
        assertEquals(
            MappingWriteResult.APPLIED,
            service.retireMapping(
                active.first, selector, current.id, current.revision, principal(),
                InventoryMappingReason.SOURCE_MODEL_CHANGE
            )
        )
        assertEquals(
            InventoryMappingResolution.Unmapped,
            service.resolve(active.first, selector)
        )
        assertEquals(2, service.history(active.first, selector).size)
        assertEquals(2, count("integration_inventory_source_mapping_retirement"))
    }

    @Test
    fun `evidence target lifecycle and connection scope fail closed`() {
        val active = activeConnection()
        seedLedger(active, "guarded-item", null, "EA")
        val service = mappingService()
        val item = createdItem(service, active.first)
        val unit = createdUnit(service, active.first)
        val target = InventoryMappingTarget(item, null, unit, QuantityFactor.of(1, 1))

        assertEquals(
            MappingWriteResult.EVIDENCE_MISMATCH,
            service.activateInitial(
                active.first, selector(active.second, "other-item", null, "EA"), target,
                evidence(active.second), principal()
            )
        )
        service.retireUnit(active.first, unit)
        assertEquals(
            MappingWriteResult.TARGET_UNAVAILABLE,
            service.activateInitial(
                active.first, selector(active.second, "guarded-item", null, "EA"), target,
                evidence(active.second), principal()
            )
        )
        assertEquals(0, count("integration_inventory_source_mapping"))

        val suspended = activeConnection()
        seedLedger(suspended, "paused-item", null, null)
        val pausedItem = createdItem(service, suspended.first)
        val pausedUnit = createdUnit(service, suspended.first)
        control.suspendConnection(suspended.first, suspended.second)
        assertEquals(
            MappingWriteResult.APPLIED,
            service.activateInitial(
                suspended.first, selector(suspended.second, "paused-item", null, null),
                InventoryMappingTarget(
                    pausedItem, null, pausedUnit, QuantityFactor.of(1, 1)
                ),
                evidence(suspended.second), principal()
            )
        )
    }

    @Test
    fun `concurrent initial decisions produce one active revision`() {
        val active = activeConnection()
        seedLedger(active, "race-item", null, "EA")
        val firstService = mappingService()
        val secondService = mappingService()
        val item = createdItem(firstService, active.first)
        val unit = createdUnit(firstService, active.first)
        val selector = selector(active.second, "race-item", null, "EA")
        val target = InventoryMappingTarget(item, null, unit, QuantityFactor.of(1, 1))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(
                listOf(
                    Callable {
                        firstService.activateInitial(
                            active.first, selector, target, evidence(active.second), principal()
                        )
                    },
                    Callable {
                        secondService.activateInitial(
                            active.first, selector, target, evidence(active.second), principal()
                        )
                    }
                )
            ).map { it.get() }.toSet()
            assertEquals(setOf(MappingWriteResult.APPLIED, MappingWriteResult.CONFLICT), results)
            assertEquals(1, count("integration_inventory_source_mapping"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `replacement audit failure rolls back retirement and successor`() {
        val active = activeConnection()
        seedLedger(active, "rollback-map", null, null)
        val service = mappingService()
        val item = createdItem(service, active.first)
        val replacement = createdItem(service, active.first)
        val unit = createdUnit(service, active.first)
        val selector = selector(active.second, "rollback-map", null, null)
        service.activateInitial(
            active.first, selector,
            InventoryMappingTarget(item, null, unit, QuantityFactor.of(1, 1)),
            evidence(active.second), principal()
        )
        val initial = service.history(active.first, selector).single()
        execute(
            "CREATE FUNCTION reject_mapping_retirement() RETURNS trigger LANGUAGE plpgsql " +
                "AS 'BEGIN RAISE EXCEPTION ''injected mapping marker''; END'; " +
                "CREATE TRIGGER reject_mapping_retirement BEFORE INSERT ON " +
                "integration_inventory_source_mapping_retirement FOR EACH ROW " +
                "EXECUTE FUNCTION reject_mapping_retirement()"
        )

        val outcome = service.replace(
            active.first, selector,
            InventoryMappingTarget(replacement, null, unit, QuantityFactor.of(1, 1)),
            evidence(active.second), initial.id, 1, principal(),
            InventoryMappingReason.IDENTITY_CORRECTION
        )

        assertEquals(MappingWriteResult.INTEGRITY_FAILURE, outcome)
        assertEquals(1, service.history(active.first, selector).size)
        assertIs<InventoryMappingResolution.Resolved>(service.resolve(active.first, selector))
        assertEquals(0, count("integration_inventory_source_mapping_retirement"))
        assertFalse(outcome.toString().contains("injected mapping marker"))
    }

    private fun activeConnection(): Pair<OrganizationId, IntegrationConnectionId> {
        val organization = control.createOrganization()
        val connection = control.createConnection(
            organization.id, ProviderKey.of("test.mapping-provider"),
            CredentialKind.STATIC_API_CREDENTIAL
        )
        control.bindInitialCredential(
            organization.id, connection.id, "test-only-credential".toByteArray()
        )
        return organization.id to connection.id
    }

    private fun mappingService(): InventoryIdentityMappingService =
        InventoryIdentityMappingService(
            PostgresInventoryIdentityMappingRepository(configuration), clock,
            itemIds = MappingIdentifierFactory { InventoryItemId.of(uuid(10)) },
            locationIds = MappingIdentifierFactory { InventoryLocationId.of(uuid(11)) },
            unitIds = MappingIdentifierFactory { InventoryUnitId.of(uuid(12)) },
            decisionIds = MappingIdentifierFactory { InventoryMappingDecisionId.of(uuid(13)) },
            correlationIds = MappingIdentifierFactory {
                InventoryMappingCorrelationId.of(uuid(14))
            }
        )

    private fun createdItem(
        service: InventoryIdentityMappingService, organizationId: OrganizationId
    ): InventoryItemId = service.createItem(organizationId).also {
        assertEquals(IdentityWriteResult.APPLIED, it.first)
    }.second

    private fun createdLocation(
        service: InventoryIdentityMappingService, organizationId: OrganizationId
    ): InventoryLocationId = service.createLocation(organizationId).also {
        assertEquals(IdentityWriteResult.APPLIED, it.first)
    }.second

    private fun createdUnit(
        service: InventoryIdentityMappingService, organizationId: OrganizationId
    ): InventoryUnitId = service.createUnit(organizationId).also {
        assertEquals(IdentityWriteResult.APPLIED, it.first)
    }.second

    private fun selector(
        connectionId: IntegrationConnectionId,
        item: String,
        location: String?,
        unit: String?
    ) = InventorySourceSelector(
        connectionId,
        sourceItemReference = SourceItemReference.of(item),
        sourceLocationReference = location?.let(SourceLocationReference::of),
        sourceUnitCode = unit?.let(SourceUnitCode::of)
    )

    private fun evidence(connectionId: IntegrationConnectionId) =
        InventoryMappingEvidence(connectionId, inputProgressVersion = 0, recordOrdinal = 0)

    private fun principal() = InventoryMappingPrincipalReference.of("test-mapping-principal")

    private fun seedLedger(
        active: Pair<OrganizationId, IntegrationConnectionId>,
        item: String,
        location: String?,
        unit: String?
    ) = connection().use { connection ->
        connection.autoCommit = false
        connection.prepareStatement(
            "INSERT INTO integration_connector_progress VALUES " +
                "(?,?,?,1,NULL,true,?,?)"
        ).use { statement ->
            statement.setObject(1, active.first.value)
            statement.setObject(2, active.second.value)
            statement.setString(3, "inventory.source-balance.read")
            statement.setTimestamp(4, Timestamp.from(now))
            statement.setTimestamp(5, Timestamp.from(now))
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO integration_connector_page_commit VALUES (?,?,?,0,?,1,true,?,?)"
        ).use { statement ->
            statement.setObject(1, active.first.value)
            statement.setObject(2, active.second.value)
            statement.setString(3, "inventory.source-balance.read")
            statement.setBytes(4, ByteArray(32) { 7 })
            statement.setTimestamp(5, Timestamp.from(now))
            statement.setTimestamp(6, Timestamp.from(now))
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO integration_inventory_source_balance " +
                "(organization_id,connection_id,capability,input_progress_version," +
                "record_ordinal,source_item_ref,source_location_ref,source_sku," +
                "source_unit_code,source_updated_at,source_version,available_to_sell," +
                "on_hand,reserved,pending_inbound,pending_outbound) " +
                "VALUES (?,?,?,0,0,?,?,NULL,?,NULL,NULL,NULL,1,NULL,NULL,NULL)"
        ).use { statement ->
            statement.setObject(1, active.first.value)
            statement.setObject(2, active.second.value)
            statement.setString(3, "inventory.source-balance.read")
            statement.setString(4, item)
            statement.setString(5, location)
            statement.setString(6, unit)
            statement.executeUpdate()
        }
        connection.commit()
    }

    private fun count(table: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { result ->
                result.next()
                result.getInt(1)
            }
        }
    }

    private fun execute(sql: String) = connection().use { connection ->
        connection.createStatement().use { it.execute(sql) }
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url, configuration.user, configuration.password
    )

    private fun uuid(namespace: Long): UUID = UUID(namespace, identifiers.getAndIncrement())
}

private class MappingTestVault : SecretVault {
    private val values = mutableMapOf<SecretReference, ByteArray>()
    private var sequence = 0

    override fun store(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        credentialBytes: ByteArray
    ): SecretReference {
        val reference = SecretReference.of("mapping-test-secret-${++sequence}")
        values[reference] = credentialBytes.copyOf()
        return reference
    }

    override fun <T> withSecret(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference,
        operation: (ByteArray) -> T
    ): T {
        val scoped = requireNotNull(values[reference]).copyOf()
        return try { operation(scoped) } finally { scoped.fill(0) }
    }

    override fun revoke(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference
    ) {
        values.remove(reference)?.fill(0)
    }
}
