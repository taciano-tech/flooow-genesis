package io.flooow.integration.inventory.mapping

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.source.SourceItemReference
import io.flooow.integration.inventory.source.SourceLocationReference
import io.flooow.integration.inventory.source.SourceUnitCode
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class InventoryIdentityMappingTest {
    private val organization = OrganizationId(UUID(0, 1))
    private val connection = IntegrationConnectionId(UUID(0, 2))
    private val now = Instant.parse("2026-08-12T13:00:00.123456Z")

    @Test
    fun `internal identifiers parse canonical uuid and redact text`() {
        val item = InventoryItemId.parse("00000000-0000-0000-0000-000000000003")
        assertEquals(UUID(0, 3), item.valueForPersistence())
        assertEquals("[INTERNAL]", item.toString())
        assertFailsWith<IllegalArgumentException> {
            InventoryItemId.parse("00000000-0000-0000-0000-00000000000A")
        }
    }

    @Test
    fun `principal and source selector preserve exact values without disclosure`() {
        val principal = InventoryMappingPrincipalReference.of("operator-e\u0301")
        assertEquals("operator-é", principal.encodedForPersistence())
        assertEquals("[REDACTED]", principal.toString())
        listOf("", " operator", "operator\n", "x".repeat(129)).forEach {
            assertFailsWith<IllegalArgumentException> {
                InventoryMappingPrincipalReference.of(it)
            }
        }
        val selector = selector(location = null, unit = null)
        assertNull(selector.sourceLocationReference)
        assertNull(selector.sourceUnitCode)
        assertFalse(selector.toString().contains("source-item"))
    }

    @Test
    fun `quantity factors reduce exactly and reject unsafe components`() {
        assertEquals(QuantityFactor.of(1, 12), QuantityFactor.of(10, 120))
        assertEquals(1, QuantityFactor.of(10, 120).numerator)
        assertEquals(12, QuantityFactor.of(10, 120).denominator)
        listOf(0L to 1L, -1L to 1L, 1L to 0L, 1_000_000_001L to 1L).forEach {
            assertFailsWith<IllegalArgumentException> { QuantityFactor.of(it.first, it.second) }
        }
    }

    @Test
    fun `decisions require evidence location and revision agreement`() {
        val selector = selector(
            location = SourceLocationReference.of("source-location"),
            unit = SourceUnitCode.of("BOX")
        )
        val target = InventoryMappingTarget(
            InventoryItemId.of(UUID(0, 10)),
            InventoryLocationId.of(UUID(0, 11)),
            InventoryUnitId.of(UUID(0, 12)),
            QuantityFactor.of(12, 1)
        )
        val decision = decision(selector, target)
        assertEquals(1, decision.revision)
        assertFalse(decision.toString().contains("source-location"))
        assertFailsWith<IllegalArgumentException> {
            decision(selector.copy(sourceLocationReference = null), target)
        }
        assertFailsWith<IllegalArgumentException> {
            decision.copy(
                revision = 2,
                supersedesDecisionId = null,
                reason = InventoryMappingReason.IDENTITY_CORRECTION
            )
        }
    }

    @Test
    fun `service creates scoped identities and deterministic initial decision`() {
        val repository = CapturingRepository()
        val service = InventoryIdentityMappingService(
            repository,
            Clock.fixed(now, ZoneOffset.UTC),
            itemIds = MappingIdentifierFactory { InventoryItemId.of(UUID(0, 20)) },
            locationIds = MappingIdentifierFactory { InventoryLocationId.of(UUID(0, 21)) },
            unitIds = MappingIdentifierFactory { InventoryUnitId.of(UUID(0, 22)) },
            decisionIds = MappingIdentifierFactory { InventoryMappingDecisionId.of(UUID(0, 23)) },
            correlationIds = MappingIdentifierFactory {
                InventoryMappingCorrelationId.of(UUID(0, 24))
            }
        )
        val item = service.createItem(organization)
        val unit = service.createUnit(organization)
        assertEquals(IdentityWriteResult.APPLIED, item.first)
        assertEquals(IdentityWriteResult.APPLIED, unit.first)

        val result = service.activateInitial(
            organization,
            selector(null, null),
            InventoryMappingTarget(item.second, null, unit.second, QuantityFactor.of(1, 1)),
            InventoryMappingEvidence(connection, inputProgressVersion = 0, recordOrdinal = 0),
            InventoryMappingPrincipalReference.of("test-principal")
        )

        assertEquals(MappingWriteResult.APPLIED, result)
        assertEquals(UUID(0, 23), repository.decision!!.id.valueForPersistence())
        assertEquals(now, repository.decision!!.decidedAt)
    }

    private fun selector(
        location: SourceLocationReference?,
        unit: SourceUnitCode?
    ) = InventorySourceSelector(
        connection,
        sourceItemReference = SourceItemReference.of("source-item"),
        sourceLocationReference = location,
        sourceUnitCode = unit
    )

    private fun decision(
        selector: InventorySourceSelector,
        target: InventoryMappingTarget
    ) = InventoryMappingDecision(
        InventoryMappingDecisionId.of(UUID(0, 30)), organization, selector, target,
        InventoryMappingEvidence(connection, inputProgressVersion = 0, recordOrdinal = 0),
        1, InventoryMappingState.ACTIVE,
        InventoryMappingPrincipalReference.of("test-principal"),
        InventoryMappingReason.INITIAL_ASSIGNMENT,
        InventoryMappingCorrelationId.of(UUID(0, 31)), now
    )
}

private class CapturingRepository : InventoryIdentityMappingRepository {
    var decision: InventoryMappingDecision? = null
    override fun createItem(identity: InventoryItemIdentity) = IdentityWriteResult.APPLIED
    override fun createLocation(identity: InventoryLocationIdentity) = IdentityWriteResult.APPLIED
    override fun createUnit(identity: InventoryUnitIdentity) = IdentityWriteResult.APPLIED
    override fun retireItem(organizationId: OrganizationId, id: InventoryItemId, retiredAt: Instant) =
        IdentityWriteResult.APPLIED
    override fun retireLocation(
        organizationId: OrganizationId, id: InventoryLocationId, retiredAt: Instant
    ) = IdentityWriteResult.APPLIED
    override fun retireUnit(organizationId: OrganizationId, id: InventoryUnitId, retiredAt: Instant) =
        IdentityWriteResult.APPLIED
    override fun activateInitial(decision: InventoryMappingDecision): MappingWriteResult {
        this.decision = decision
        return MappingWriteResult.APPLIED
    }
    override fun replace(
        expectedDecisionId: InventoryMappingDecisionId,
        expectedRevision: Int,
        decision: InventoryMappingDecision
    ) = MappingWriteResult.APPLIED
    override fun retireMapping(
        organizationId: OrganizationId,
        selector: InventorySourceSelector,
        expectedDecisionId: InventoryMappingDecisionId,
        expectedRevision: Int,
        principalReference: InventoryMappingPrincipalReference,
        reason: InventoryMappingReason,
        correlationId: InventoryMappingCorrelationId,
        retiredAt: Instant
    ) = MappingWriteResult.APPLIED
    override fun resolve(organizationId: OrganizationId, selector: InventorySourceSelector) =
        InventoryMappingResolution.Unmapped
    override fun history(organizationId: OrganizationId, selector: InventorySourceSelector) =
        emptyList<InventoryMappingDecision>()
}
