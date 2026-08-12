package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.*
import io.flooow.integration.inventory.acceptance.*
import io.flooow.integration.inventory.mapping.*
import io.flooow.integration.inventory.observation.*
import io.flooow.integration.inventory.source.SourceItemReference
import io.flooow.integration.inventory.source.SourceUnitCode
import io.flooow.organization.OrganizationId
import java.math.BigInteger
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*
import org.testcontainers.postgresql.PostgreSQLContainer

class PostgresCanonicalInventoryObservationRepositoryTest {
    private val now = Instant.parse("2026-08-12T18:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sequence = AtomicLong(1)
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var control: IntegrationControlPlaneService

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(postgres.jdbcUrl, postgres.username, postgres.password)
        control = IntegrationControlPlaneService(
            PostgresIntegrationControlPlaneRepository.connect(configuration), TestObservationVault(),
            clock,
            organizationIds = IdentifierFactory { OrganizationId(uuid()) },
            connectionIds = IdentifierFactory { IntegrationConnectionId(uuid()) },
            auditIds = IdentifierFactory { IntegrationAuditEntryId(uuid()) },
            correlationIds = IdentifierFactory { uuid() }
        )
    }

    @AfterTest
    fun stopPostgres() = postgres.stop()

    @Test
    fun `exact projection is scoped replayable and preserves independent measures`() {
        val scope = activeConnection()
        seedLedger(scope, "exact-item", "BOX", "1", "-2.500000", null, "0")
        val mapping = mappingService()
        val item = mapping.createItem(scope.first).second
        val unit = mapping.createUnit(scope.first).second
        val selector = selector(scope.second, "exact-item", "BOX")
        assertEquals(
            MappingWriteResult.APPLIED,
            mapping.activateInitial(
                scope.first, selector,
                InventoryMappingTarget(item, null, unit, QuantityFactor.of(1, 3)),
                InventoryMappingEvidence(scope.second, inputProgressVersion = 0, recordOrdinal = 0),
                InventoryMappingPrincipalReference.of("test-principal")
            )
        )
        val service = observationService()
        val pointer = pointer(scope.second)
        assertIs<CanonicalInventoryProjectionResult.Projected>(service.project(scope.first, pointer))
        val replay = assertIs<CanonicalInventoryProjectionResult.AlreadyProjected>(
            service.project(scope.first, pointer)
        )
        val observation = assertNotNull(service.find(scope.first, replay.observationId))
        assertEquals(BigInteger.ONE, observation.measures.availableToSell?.numeratorForPersistence())
        assertEquals(3, observation.measures.availableToSell?.denominatorForPersistence())
        assertEquals(BigInteger.valueOf(-5), observation.measures.onHand?.numeratorForPersistence())
        assertEquals(6, observation.measures.onHand?.denominatorForPersistence())
        assertNull(observation.measures.reserved)
        assertEquals(BigInteger.ZERO, observation.measures.pendingInbound?.numeratorForPersistence())
        assertNull(observation.sourceUpdatedAt)
        assertEquals(now, observation.sourceCommittedAt)
        assertEquals(1, service.history(scope.first, pointer).size)
        assertNull(service.find(OrganizationId(uuid()), replay.observationId))
    }

    @Test
    fun `mapping replacement appends immutable ordered interpretation`() {
        val scope = activeConnection()
        seedLedger(scope, "revision-item", "EA", "6", null, null, null)
        val mapping = mappingService()
        val firstItem = mapping.createItem(scope.first).second
        val secondItem = mapping.createItem(scope.first).second
        val unit = mapping.createUnit(scope.first).second
        val selector = selector(scope.second, "revision-item", "EA")
        mapping.activateInitial(
            scope.first, selector,
            InventoryMappingTarget(firstItem, null, unit, QuantityFactor.of(1, 1)),
            InventoryMappingEvidence(scope.second, inputProgressVersion = 0, recordOrdinal = 0),
            InventoryMappingPrincipalReference.of("test-principal")
        )
        val observations = observationService()
        val pointer = pointer(scope.second)
        val firstProjection = assertIs<CanonicalInventoryProjectionResult.Projected>(
            observations.project(scope.first, pointer)
        )
        val initial = mapping.history(scope.first, selector).single()
        val initialAcceptance = assertIs<CanonicalInventoryAcceptanceResult.Accepted>(
            acceptanceService().acceptInitial(
                scope.first, initial.id, firstProjection.observationId,
                InventoryAcceptancePrincipalReference.of("test-principal")
            )
        )
        assertEquals(
            MappingWriteResult.APPLIED,
            mapping.replace(
                scope.first, selector,
                InventoryMappingTarget(secondItem, null, unit, QuantityFactor.of(1, 2)),
                InventoryMappingEvidence(scope.second, inputProgressVersion = 0, recordOrdinal = 0),
                initial.id, initial.revision, InventoryMappingPrincipalReference.of("test-principal"),
                InventoryMappingReason.IDENTITY_CORRECTION
            )
        )
        val secondProjection = assertIs<CanonicalInventoryProjectionResult.Projected>(
            observations.project(scope.first, pointer)
        )
        val reinterpreted = assertIs<CanonicalInventoryAcceptanceResult.Accepted>(
            acceptanceService().replace(
                scope.first, initial.id, initialAcceptance.acceptanceId,
                initialAcceptance.revision, secondProjection.observationId,
                InventoryAcceptancePrincipalReference.of("test-principal"),
                CanonicalInventoryAcceptanceReason.MAPPING_REINTERPRETATION
            )
        )
        assertEquals(2, reinterpreted.revision)
        val history = observations.history(scope.first, pointer)
        assertEquals(listOf(1, 2), history.map { it.projectionRevision })
        assertEquals(listOf(1, 2), history.map { it.mappingRevision })
        assertEquals(history[0].id, history[1].supersedesObservationId)
        assertEquals(firstItem, history[0].target.itemId)
        assertEquals(secondItem, history[1].target.itemId)
        assertEquals(BigInteger.valueOf(3), history[1].measures.availableToSell?.numeratorForPersistence())
    }

    @Test
    fun `accepted mapping projects later evidence with the same exact selector`() {
        val scope = activeConnection()
        seedLedger(scope, "timeline-item", "EA", "4", null, null, null)
        val mapping = mappingService()
        val item = mapping.createItem(scope.first).second
        val unit = mapping.createUnit(scope.first).second
        val selector = selector(scope.second, "timeline-item", "EA")
        assertEquals(
            MappingWriteResult.APPLIED,
            mapping.activateInitial(
                scope.first, selector,
                InventoryMappingTarget(item, null, unit, QuantityFactor.of(1, 1)),
                InventoryMappingEvidence(scope.second, inputProgressVersion = 0, recordOrdinal = 0),
                InventoryMappingPrincipalReference.of("test-principal")
            )
        )
        val observations = observationService()
        assertIs<CanonicalInventoryProjectionResult.Projected>(
            observations.project(scope.first, pointer(scope.second, 0))
        )

        seedLaterEvidence(scope, "timeline-item", "EA", "7")

        assertIs<CanonicalInventoryProjectionResult.Projected>(
            observations.project(scope.first, pointer(scope.second, 1))
        )
        assertEquals(
            BigInteger.valueOf(7),
            observations.history(scope.first, pointer(scope.second, 1)).single()
                .measures.availableToSell?.numeratorForPersistence()
        )
        assertEquals(2, count("integration_inventory_canonical_observation"))
        assertEquals(1, count("integration_inventory_source_mapping"))
    }

    @Test
    fun `concurrent projection creates one observation and direct mutations are rejected`() {
        val scope = activeConnection()
        seedLedger(scope, "race-item", null, "9", null, null, null)
        val mapping = mappingService()
        val item = mapping.createItem(scope.first).second
        val unit = mapping.createUnit(scope.first).second
        mapping.activateInitial(
            scope.first, selector(scope.second, "race-item", null),
            InventoryMappingTarget(item, null, unit, QuantityFactor.of(1, 1)),
            InventoryMappingEvidence(scope.second, inputProgressVersion = 0, recordOrdinal = 0),
            InventoryMappingPrincipalReference.of("test-principal")
        )
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(
                listOf(Callable { observationService().project(scope.first, pointer(scope.second)) },
                    Callable { observationService().project(scope.first, pointer(scope.second)) })
            ).map { it.get() }
            assertEquals(1, results.count { it is CanonicalInventoryProjectionResult.Projected })
            assertEquals(1, count("integration_inventory_canonical_observation"))
        } finally { executor.shutdownNow() }

        assertFailsWith<SQLException> {
            execute("UPDATE integration_inventory_canonical_observation SET mapping_revision=2")
        }
        assertFailsWith<SQLException> {
            execute("DELETE FROM integration_inventory_canonical_observation")
        }
    }

    @Test
    fun `acceptance advances exact lineage withdraws head and preserves immutable history`() {
        val scope = activeConnection()
        seedLedger(scope, "accepted-item", "EA", "4", null, null, null)
        val mappings = mappingService()
        val item = mappings.createItem(scope.first).second
        val unit = mappings.createUnit(scope.first).second
        val selector = selector(scope.second, "accepted-item", "EA")
        assertEquals(
            MappingWriteResult.APPLIED,
            mappings.activateInitial(
                scope.first, selector,
                InventoryMappingTarget(item, null, unit, QuantityFactor.of(1, 1)),
                InventoryMappingEvidence(scope.second, inputProgressVersion = 0, recordOrdinal = 0),
                InventoryMappingPrincipalReference.of("test-principal")
            )
        )
        val root = mappings.history(scope.first, selector).single()
        val observations = observationService()
        val first = assertIs<CanonicalInventoryProjectionResult.Projected>(
            observations.project(scope.first, pointer(scope.second, 0))
        )
        val acceptances = acceptanceService()
        val accepted = assertIs<CanonicalInventoryAcceptanceResult.Accepted>(
            acceptances.acceptInitial(
                scope.first, root.id, first.observationId,
                InventoryAcceptancePrincipalReference.of("test-principal")
            )
        )
        assertEquals(1, accepted.revision)
        assertIs<CanonicalInventoryAcceptanceResult.AlreadyAccepted>(
            acceptances.acceptInitial(
                scope.first, root.id, first.observationId,
                InventoryAcceptancePrincipalReference.of("test-principal")
            )
        )

        seedLaterEvidence(scope, "accepted-item", "EA", "7")
        val second = assertIs<CanonicalInventoryProjectionResult.Projected>(
            observations.project(scope.first, pointer(scope.second, 1))
        )
        val replaced = assertIs<CanonicalInventoryAcceptanceResult.Accepted>(
            acceptances.replace(
                scope.first, root.id, accepted.acceptanceId, accepted.revision,
                second.observationId, InventoryAcceptancePrincipalReference.of("test-principal"),
                CanonicalInventoryAcceptanceReason.NEW_SOURCE_EVIDENCE
            )
        )
        assertEquals(2, replaced.revision)
        assertIs<CanonicalInventoryAcceptanceResult.Stale>(
            acceptances.replace(
                scope.first, root.id, replaced.acceptanceId, replaced.revision,
                first.observationId, InventoryAcceptancePrincipalReference.of("test-principal"),
                CanonicalInventoryAcceptanceReason.NEW_SOURCE_EVIDENCE
            )
        )
        assertEquals(1, acceptances.head(scope.first, root.id)
            ?.acceptedObservation?.sourcePointer?.inputProgressVersion)
        assertEquals(listOf(1, 2), acceptances.history(scope.first, root.id).map { it.revision })
        assertEquals(
            listOf(CanonicalInventoryAcceptanceState.RETIRED,
                CanonicalInventoryAcceptanceState.ACTIVE),
            acceptances.history(scope.first, root.id).map { it.state }
        )

        assertIs<CanonicalInventoryAcceptanceResult.Withdrawn>(
            acceptances.withdraw(
                scope.first, root.id, replaced.acceptanceId, replaced.revision,
                InventoryAcceptancePrincipalReference.of("test-principal"),
                CanonicalInventoryAcceptanceReason.OPERATOR_WITHDRAWAL
            )
        )
        assertNull(acceptances.head(scope.first, root.id))
        assertEquals(2, acceptances.history(scope.first, root.id).size)
        assertEquals(2, count("integration_inventory_source_acceptance_retirement"))
        assertFailsWith<SQLException> {
            execute("DELETE FROM integration_inventory_source_acceptance")
        }
    }

    @Test
    fun `competing acceptance replacements create one successor`() {
        val scope = activeConnection()
        seedLedger(scope, "acceptance-race", null, "3", null, null, null)
        val mappings = mappingService()
        val item = mappings.createItem(scope.first).second
        val unit = mappings.createUnit(scope.first).second
        val selector = selector(scope.second, "acceptance-race", null)
        mappings.activateInitial(
            scope.first, selector,
            InventoryMappingTarget(item, null, unit, QuantityFactor.of(1, 1)),
            InventoryMappingEvidence(scope.second, inputProgressVersion = 0, recordOrdinal = 0),
            InventoryMappingPrincipalReference.of("test-principal")
        )
        val root = mappings.history(scope.first, selector).single()
        val observations = observationService()
        val first = assertIs<CanonicalInventoryProjectionResult.Projected>(
            observations.project(scope.first, pointer(scope.second, 0))
        )
        val initial = assertIs<CanonicalInventoryAcceptanceResult.Accepted>(
            acceptanceService().acceptInitial(
                scope.first, root.id, first.observationId,
                InventoryAcceptancePrincipalReference.of("test-principal")
            )
        )
        seedLaterEvidence(scope, "acceptance-race", null, "8")
        val second = assertIs<CanonicalInventoryProjectionResult.Projected>(
            observations.project(scope.first, pointer(scope.second, 1))
        )
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(listOf(
                Callable {
                    acceptanceService().replace(
                        scope.first, root.id, initial.acceptanceId, initial.revision,
                        second.observationId,
                        InventoryAcceptancePrincipalReference.of("test-principal"),
                        CanonicalInventoryAcceptanceReason.NEW_SOURCE_EVIDENCE
                    )
                },
                Callable {
                    acceptanceService().replace(
                        scope.first, root.id, initial.acceptanceId, initial.revision,
                        second.observationId,
                        InventoryAcceptancePrincipalReference.of("test-principal"),
                        CanonicalInventoryAcceptanceReason.NEW_SOURCE_EVIDENCE
                    )
                }
            )).map { it.get() }
            assertEquals(1, results.count { it is CanonicalInventoryAcceptanceResult.Accepted })
            assertEquals(1, results.count { it is CanonicalInventoryAcceptanceResult.Conflict })
            assertEquals(2, acceptanceService().history(scope.first, root.id).size)
        } finally { executor.shutdownNow() }
    }

    private fun activeConnection(): Pair<OrganizationId, IntegrationConnectionId> {
        val organization = control.createOrganization()
        val connection = control.createConnection(
            organization.id, ProviderKey.of("test.observation-provider"),
            CredentialKind.STATIC_API_CREDENTIAL
        )
        control.bindInitialCredential(organization.id, connection.id, "test-secret".toByteArray())
        return organization.id to connection.id
    }

    private fun mappingService() = InventoryIdentityMappingService(
        PostgresInventoryIdentityMappingRepository(configuration), clock,
        itemIds = MappingIdentifierFactory { InventoryItemId.of(uuid()) },
        locationIds = MappingIdentifierFactory { InventoryLocationId.of(uuid()) },
        unitIds = MappingIdentifierFactory { InventoryUnitId.of(uuid()) },
        decisionIds = MappingIdentifierFactory { InventoryMappingDecisionId.of(uuid()) },
        correlationIds = MappingIdentifierFactory { InventoryMappingCorrelationId.of(uuid()) }
    )

    private fun observationService() = CanonicalInventoryObservationService(
        PostgresCanonicalInventoryObservationRepository(configuration),
        ObservationIdentifierFactory { CanonicalInventoryObservationId.of(uuid()) },
        ObservationIdentifierFactory { CanonicalInventoryObservationCorrelationId.of(uuid()) }
    )

    private fun acceptanceService() = CanonicalInventoryAcceptanceService(
        PostgresCanonicalInventoryAcceptanceRepository(configuration),
        AcceptanceIdentifierFactory { CanonicalInventoryAcceptanceId.of(uuid()) },
        AcceptanceIdentifierFactory { CanonicalInventoryAcceptanceCorrelationId.of(uuid()) }
    )

    private fun selector(connectionId: IntegrationConnectionId, item: String, unit: String?) =
        InventorySourceSelector(
            connectionId, sourceItemReference = SourceItemReference.of(item),
            sourceUnitCode = unit?.let(SourceUnitCode::of)
        )

    private fun pointer(connectionId: IntegrationConnectionId, version: Long = 0) =
        CanonicalInventorySourcePointer(
            connectionId, inputProgressVersion = version, recordOrdinal = 0
        )

    private fun seedLedger(
        scope: Pair<OrganizationId, IntegrationConnectionId>,
        item: String,
        unit: String?,
        available: String?,
        onHand: String?,
        reserved: String?,
        pendingInbound: String?
    ) = connection().use { connection ->
        connection.autoCommit = false
        connection.prepareStatement(
            "INSERT INTO integration_connector_progress VALUES (?,?,?,1,NULL,true,?,?)"
        ).use { statement ->
            statement.setObject(1, scope.first.value); statement.setObject(2, scope.second.value)
            statement.setString(3, "inventory.source-balance.read")
            statement.setTimestamp(4, Timestamp.from(now)); statement.setTimestamp(5, Timestamp.from(now))
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO integration_connector_page_commit VALUES (?,?,?,0,?,1,true,?,?)"
        ).use { statement ->
            statement.setObject(1, scope.first.value); statement.setObject(2, scope.second.value)
            statement.setString(3, "inventory.source-balance.read"); statement.setBytes(4, ByteArray(32) { 8 })
            statement.setTimestamp(5, Timestamp.from(now)); statement.setTimestamp(6, Timestamp.from(now))
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO integration_inventory_source_balance " +
                "(organization_id,connection_id,capability,input_progress_version,record_ordinal," +
                "source_item_ref,source_unit_code,available_to_sell,on_hand,reserved,pending_inbound) " +
                "VALUES (?,?,?,0,0,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, scope.first.value); statement.setObject(2, scope.second.value)
            statement.setString(3, "inventory.source-balance.read"); statement.setString(4, item)
            statement.setString(5, unit); statement.setBigDecimal(6, available?.toBigDecimal())
            statement.setBigDecimal(7, onHand?.toBigDecimal()); statement.setBigDecimal(8, reserved?.toBigDecimal())
            statement.setBigDecimal(9, pendingInbound?.toBigDecimal()); statement.executeUpdate()
        }
        connection.commit()
    }

    private fun seedLaterEvidence(
        scope: Pair<OrganizationId, IntegrationConnectionId>,
        item: String,
        unit: String?,
        available: String
    ) = connection().use { connection ->
        connection.autoCommit = false
        connection.prepareStatement(
            "UPDATE integration_connector_progress SET progress_version=2,updated_at=? " +
                "WHERE organization_id=? AND connection_id=? AND capability=?"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(now.plusSeconds(60)))
            statement.setObject(2, scope.first.value)
            statement.setObject(3, scope.second.value)
            statement.setString(4, "inventory.source-balance.read")
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO integration_connector_page_commit VALUES (?,?,?,1,?,1,true,?,?)"
        ).use { statement ->
            statement.setObject(1, scope.first.value)
            statement.setObject(2, scope.second.value)
            statement.setString(3, "inventory.source-balance.read")
            statement.setBytes(4, ByteArray(32) { 9 })
            statement.setTimestamp(5, Timestamp.from(now.plusSeconds(60)))
            statement.setTimestamp(6, Timestamp.from(now.plusSeconds(60)))
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO integration_inventory_source_balance " +
                "(organization_id,connection_id,capability,input_progress_version,record_ordinal," +
                "source_item_ref,source_unit_code,available_to_sell) VALUES (?,?,?,1,0,?,?,?)"
        ).use { statement ->
            statement.setObject(1, scope.first.value)
            statement.setObject(2, scope.second.value)
            statement.setString(3, "inventory.source-balance.read")
            statement.setString(4, item)
            statement.setString(5, unit)
            statement.setBigDecimal(6, available.toBigDecimal())
            statement.executeUpdate()
        }
        connection.commit()
    }

    private fun execute(sql: String) = connection().use { connection ->
        connection.createStatement().use { it.execute(sql) }
    }

    private fun count(table: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { result -> result.next(); result.getInt(1) }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url, configuration.user, configuration.password
    )

    private fun uuid(): UUID = UUID(77, sequence.getAndIncrement())
}

private class TestObservationVault : SecretVault {
    private val values = mutableMapOf<SecretReference, ByteArray>()
    private var sequence = 0
    override fun store(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId,
        credentialBytes: ByteArray
    ): SecretReference = SecretReference.of("observation-test-${++sequence}").also {
        values[it] = credentialBytes.copyOf()
    }
    override fun <T> withSecret(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId,
        reference: SecretReference, operation: (ByteArray) -> T
    ): T = operation(requireNotNull(values[reference]).copyOf())
    override fun revoke(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId,
        reference: SecretReference
    ) { values.remove(reference)?.fill(0) }
}
