package io.flooow.integration.inventory.snapshot

import io.flooow.integration.inventory.mapping.InventoryItemId
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.mapping.InventoryUnitId
import io.flooow.organization.OrganizationId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanonicalInventoryCandidateSnapshotTest {
    @Test
    fun `identifiers principal and commands are bounded and redacted`() {
        val principal = InventoryCandidateSnapshotPrincipalReference.of("operador")
        assertEquals("operador", principal.encodedForPersistence())
        assertEquals("[REDACTED]", principal.toString())
        assertFailsWith<IllegalArgumentException> {
            InventoryCandidateSnapshotPrincipalReference.of(" operador ")
        }
        assertFailsWith<IllegalArgumentException> {
            InventoryCandidateSnapshotPrincipalReference.of("x\n")
        }
        assertFailsWith<IllegalArgumentException> {
            InventoryCandidateSnapshotPrincipalReference.of("x".repeat(129))
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventoryCandidateSnapshotId.parse("550E8400-E29B-41D4-A716-446655440000")
        }

        val root = InventoryMappingDecisionId.of(uuid(4))
        assertFailsWith<IllegalArgumentException> { command(emptyList()) }
        assertFailsWith<IllegalArgumentException> { command(listOf(root, root)) }
        assertEquals("CaptureCanonicalInventoryCandidates([REDACTED])", command(listOf(root)).toString())
    }

    @Test
    fun `lineage order matches unsigned uuid byte order`() {
        val highUnsigned = InventoryMappingDecisionId.of(UUID(-1, 0))
        val lowUnsigned = InventoryMappingDecisionId.of(UUID(0, -1))
        assertEquals(
            listOf(lowUnsigned, highUnsigned),
            listOf(highUnsigned, lowUnsigned).sortedWith(CanonicalInventoryCandidateLineageOrder)
        )
    }

    @Test
    fun `service generates identifiers without adding candidate claims`() {
        var captured: CaptureCanonicalInventoryCandidates? = null
        val expectedId = CanonicalInventoryCandidateSnapshotId.of(uuid(20))
        val repository = object : CanonicalInventoryCandidateSnapshotRepository {
            override fun capture(
                command: CaptureCanonicalInventoryCandidates,
                snapshotId: CanonicalInventoryCandidateSnapshotId
            ): CanonicalInventoryCandidateSnapshotCaptureResult {
                captured = command
                assertEquals(expectedId, snapshotId)
                return CanonicalInventoryCandidateSnapshotCaptureResult.Captured(snapshotId, 1)
            }

            override fun find(
                organizationId: OrganizationId,
                snapshotId: CanonicalInventoryCandidateSnapshotId
            ) = CanonicalInventoryCandidateSnapshotReadResult.NotFound
        }
        val service = CanonicalInventoryCandidateSnapshotService(
            repository,
            CandidateSnapshotIdentifierFactory { expectedId },
            CandidateSnapshotIdentifierFactory {
                CanonicalInventoryCandidateSnapshotCorrelationId.of(uuid(21))
            }
        )
        val result = service.capture(
            OrganizationId(uuid(1)),
            CanonicalInventoryCandidateSnapshotRequestId.of(uuid(2)),
            target(),
            listOf(InventoryMappingDecisionId.of(uuid(4))),
            InventoryCandidateSnapshotPrincipalReference.of("operator")
        )
        assertEquals(1, (result as CanonicalInventoryCandidateSnapshotCaptureResult.Captured).memberCount)
        assertEquals(target(), captured?.target)
    }

    private fun command(roots: Collection<InventoryMappingDecisionId>) =
        CaptureCanonicalInventoryCandidates(
            OrganizationId(uuid(1)),
            CanonicalInventoryCandidateSnapshotRequestId.of(uuid(2)),
            target(), roots,
            InventoryCandidateSnapshotPrincipalReference.of("operator"),
            CanonicalInventoryCandidateSnapshotCorrelationId.of(uuid(3))
        )

    private fun target() = CanonicalInventoryCandidateTarget(
        InventoryItemId.of(uuid(5)), null, InventoryUnitId.of(uuid(6))
    )

    private fun uuid(value: Long) = UUID(101, value)
}
