package io.flooow.integration.inventory.selection

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.acceptance.CanonicalInventoryAcceptanceId
import io.flooow.integration.inventory.mapping.*
import io.flooow.integration.inventory.observation.*
import io.flooow.organization.OrganizationId
import java.math.BigInteger
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class CanonicalInventoryMeasureSelectionTest {
    @Test
    fun `identifiers principals and results remain bounded and redacted`() {
        val principal = InventoryMeasureSelectionPrincipalReference.of("operador-é")
        assertEquals("operador-é", principal.encodedForPersistence())
        assertEquals("[REDACTED]", principal.toString())
        assertFailsWith<IllegalArgumentException> {
            InventoryMeasureSelectionPrincipalReference.of(" operador ")
        }
        assertFailsWith<IllegalArgumentException> {
            InventoryMeasureSelectionPrincipalReference.of("x\n")
        }
        assertFailsWith<IllegalArgumentException> {
            InventoryMeasureSelectionPrincipalReference.of("x".repeat(129))
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventoryMeasureSelectionId.parse("550E8400-E29B-41D4-A716-446655440000")
        }
        val selected = CanonicalInventoryMeasureSelectionResult.Selected(
            CanonicalInventoryMeasureSelectionId.of(uuid(1)), 1
        )
        assertEquals("Selected([INTERNAL])", selected.toString())
    }

    @Test
    fun `selection enforces initial replacement and lifecycle shapes`() {
        val initial = decision(1, CanonicalInventoryMeasureSelectionReason.INITIAL_SELECTION)
        assertEquals(CanonicalInventoryMeasure.ON_HAND, initial.measure)
        assertEquals("CanonicalInventoryMeasureSelection([REDACTED])", initial.toString())
        val successor = decision(
            2, CanonicalInventoryMeasureSelectionReason.OPERATOR_CORRECTION,
            CanonicalInventoryMeasureSelectionId.of(uuid(99))
        )
        assertEquals(2, successor.revision)
        assertFailsWith<IllegalArgumentException> {
            decision(1, CanonicalInventoryMeasureSelectionReason.OPERATOR_CORRECTION)
        }
        assertFailsWith<IllegalArgumentException> {
            decision(
                2, CanonicalInventoryMeasureSelectionReason.OPERATOR_WITHDRAWAL,
                CanonicalInventoryMeasureSelectionId.of(uuid(99))
            )
        }
    }

    @Test
    fun `selected candidate preserves exact signed rational without business conversion`() {
        val connection = IntegrationConnectionId(uuid(3))
        val candidate = SelectedCanonicalInventoryMeasure(
            OrganizationId(uuid(1)), connection, "inventory.source-balance.read",
            InventoryMappingDecisionId.of(uuid(2)),
            CanonicalInventoryMeasureSelectionId.of(uuid(4)), 2,
            CanonicalInventoryAcceptanceId.of(uuid(5)), 3,
            CanonicalInventoryObservationId.of(uuid(6)),
            CanonicalInventorySourcePointer(connection, inputProgressVersion = 8, recordOrdinal = 0),
            4, InventoryMappingDecisionId.of(uuid(7)), 2,
            InventoryMappingTarget(
                InventoryItemId.of(uuid(8)), null, InventoryUnitId.of(uuid(9)),
                QuantityFactor.of(1, 3)
            ), CanonicalInventoryMeasure.RESERVED,
            ExactInventoryQuantity.fromPersistence(BigInteger.valueOf(-5), 6)
        )
        assertEquals(BigInteger.valueOf(-5), candidate.exactQuantity.numeratorForPersistence())
        assertEquals(6, candidate.exactQuantity.denominatorForPersistence())
        assertEquals("SelectedCanonicalInventoryMeasure([REDACTED])", candidate.toString())
    }

    @Test
    fun `service rejects reason categories before repository mutation`() {
        var mutated = false
        val repository = object : CanonicalInventoryMeasureSelectionRepository {
            override fun selectInitial(
                organizationId: OrganizationId,
                lineageRootDecisionId: InventoryMappingDecisionId,
                measure: CanonicalInventoryMeasure,
                selectionId: CanonicalInventoryMeasureSelectionId,
                principal: InventoryMeasureSelectionPrincipalReference,
                correlationId: CanonicalInventoryMeasureSelectionCorrelationId
            ) = CanonicalInventoryMeasureSelectionResult.IntegrityFailure

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
            ): CanonicalInventoryMeasureSelectionResult {
                mutated = true
                return CanonicalInventoryMeasureSelectionResult.IntegrityFailure
            }

            override fun withdraw(
                organizationId: OrganizationId,
                lineageRootDecisionId: InventoryMappingDecisionId,
                expectedSelectionId: CanonicalInventoryMeasureSelectionId,
                expectedRevision: Int,
                principal: InventoryMeasureSelectionPrincipalReference,
                reason: CanonicalInventoryMeasureSelectionReason,
                correlationId: CanonicalInventoryMeasureSelectionCorrelationId
            ): CanonicalInventoryMeasureSelectionResult {
                mutated = true
                return CanonicalInventoryMeasureSelectionResult.IntegrityFailure
            }

            override fun resolve(
                organizationId: OrganizationId,
                lineageRootDecisionId: InventoryMappingDecisionId
            ) = CanonicalInventoryMeasureResolutionResult.Unselected

            override fun head(
                organizationId: OrganizationId,
                lineageRootDecisionId: InventoryMappingDecisionId
            ) = null

            override fun history(
                organizationId: OrganizationId,
                lineageRootDecisionId: InventoryMappingDecisionId
            ) = emptyList<CanonicalInventoryMeasureSelection>()
        }
        val service = CanonicalInventoryMeasureSelectionService(repository)
        val organization = OrganizationId(uuid(1))
        val root = InventoryMappingDecisionId.of(uuid(2))
        val expected = CanonicalInventoryMeasureSelectionId.of(uuid(3))
        val principal = InventoryMeasureSelectionPrincipalReference.of("operator")
        assertFailsWith<IllegalArgumentException> {
            service.replace(
                organization, root, expected, 1, CanonicalInventoryMeasure.ON_HAND, principal,
                CanonicalInventoryMeasureSelectionReason.OPERATOR_WITHDRAWAL
            )
        }
        assertFailsWith<IllegalArgumentException> {
            service.withdraw(
                organization, root, expected, 1, principal,
                CanonicalInventoryMeasureSelectionReason.OPERATOR_CORRECTION
            )
        }
        assertFalse(mutated)
    }

    private fun decision(
        revision: Int,
        reason: CanonicalInventoryMeasureSelectionReason,
        predecessor: CanonicalInventoryMeasureSelectionId? = null
    ) = CanonicalInventoryMeasureSelection(
        CanonicalInventoryMeasureSelectionId.of(uuid(10 + revision.toLong())),
        OrganizationId(uuid(1)), IntegrationConnectionId(uuid(3)),
        lineageRootDecisionId = InventoryMappingDecisionId.of(uuid(2)), revision = revision,
        state = CanonicalInventoryMeasureSelectionState.ACTIVE,
        measure = CanonicalInventoryMeasure.ON_HAND,
        anchorAcceptanceId = CanonicalInventoryAcceptanceId.of(uuid(20)),
        anchorAcceptanceRevision = 2,
        anchorObservationId = CanonicalInventoryObservationId.of(uuid(21)),
        principalReference = InventoryMeasureSelectionPrincipalReference.of("operator"),
        reason = reason,
        correlationId = CanonicalInventoryMeasureSelectionCorrelationId.of(uuid(22)),
        selectedAt = Instant.parse("2026-08-13T10:00:00Z"),
        supersedesSelectionId = predecessor
    )

    private fun uuid(value: Long) = UUID(91, value)
}
