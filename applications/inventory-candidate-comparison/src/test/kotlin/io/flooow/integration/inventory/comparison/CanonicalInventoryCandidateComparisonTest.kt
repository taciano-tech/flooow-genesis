package io.flooow.integration.inventory.comparison

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.acceptance.CanonicalInventoryAcceptanceId
import io.flooow.integration.inventory.mapping.*
import io.flooow.integration.inventory.observation.*
import io.flooow.integration.inventory.selection.*
import io.flooow.integration.inventory.snapshot.*
import io.flooow.organization.OrganizationId
import java.math.BigInteger
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class CanonicalInventoryCandidateComparisonTest {
    @Test
    fun `single candidate is not agreement and remains redacted`() {
        val result = assertIs<CanonicalInventoryCandidateComparisonResult.SingleCandidate>(
            CanonicalInventoryCandidateComparator.compare(view(member(1, "-5", 6)))
        )
        assertEquals(BigInteger.valueOf(-5), result.exactQuantity.numeratorForPersistence())
        assertEquals("SingleCandidate([REDACTED])", result.toString())
    }

    @Test
    fun `measure mismatch precedes quantity comparison`() {
        val result = assertIs<CanonicalInventoryCandidateComparisonResult.MeasureMismatch>(
            CanonicalInventoryCandidateComparator.compare(
                view(
                    member(1, "2", 1, CanonicalInventoryMeasure.ON_HAND),
                    member(2, "2", 1, CanonicalInventoryMeasure.RESERVED)
                )
            )
        )
        assertEquals(2, result.distinctMeasureCount)
        assertFalse(result.toString().contains("ON_HAND"))
    }

    @Test
    fun `exact signed rationals agree or diverge without rounding`() {
        assertIs<CanonicalInventoryCandidateComparisonResult.ExactAgreement>(
            CanonicalInventoryCandidateComparator.compare(
                view(member(1, "-10", 12), member(2, "-5", 6))
            )
        )
        val divergent = assertIs<CanonicalInventoryCandidateComparisonResult.ExactDivergence>(
            CanonicalInventoryCandidateComparator.compare(
                view(member(1, "0", 9), member(2, "1", 1000), member(3, "0", 1))
            )
        )
        assertEquals(2, divergent.distinctQuantityCount)
    }

    private fun view(vararg members: CanonicalInventoryCandidateSnapshotMember):
        CanonicalInventoryCandidateSnapshotView {
        val snapshot = CanonicalInventoryCandidateSnapshot(
            snapshotId, organizationId, CanonicalInventoryCandidateSnapshotRequestId.of(uuid(80)),
            target, InventoryCandidateSnapshotPrincipalReference.of("operator"),
            CanonicalInventoryCandidateSnapshotCorrelationId.of(uuid(81)),
            Instant.parse("2026-08-13T18:00:00Z"), members.size
        )
        return CanonicalInventoryCandidateSnapshotView(snapshot, members.toList())
    }

    private fun member(
        index: Long,
        numerator: String,
        denominator: Long,
        measure: CanonicalInventoryMeasure = CanonicalInventoryMeasure.ON_HAND
    ): CanonicalInventoryCandidateSnapshotMember {
        val connection = IntegrationConnectionId(uuid(10 + index))
        return CanonicalInventoryCandidateSnapshotMember(
            organizationId, snapshotId, connection, "inventory.source-balance.read",
            InventoryMappingDecisionId.of(uuid(index)),
            CanonicalInventoryMeasureSelectionId.of(uuid(20 + index)), 1,
            CanonicalInventoryAcceptanceId.of(uuid(30 + index)), 1,
            CanonicalInventoryObservationId.of(uuid(40 + index)),
            CanonicalInventorySourcePointer(connection, inputProgressVersion = index, recordOrdinal = 0),
            1, InventoryMappingDecisionId.of(uuid(50 + index)), 1, target, measure,
            ExactInventoryQuantity.fromPersistence(BigInteger(numerator), denominator)
        )
    }

    private val organizationId = OrganizationId(uuid(90))
    private val snapshotId = CanonicalInventoryCandidateSnapshotId.of(uuid(91))
    private val target = CanonicalInventoryCandidateTarget(
        InventoryItemId.of(uuid(92)), null, InventoryUnitId.of(uuid(93))
    )

    private fun uuid(value: Long) = UUID(0, value)
}
