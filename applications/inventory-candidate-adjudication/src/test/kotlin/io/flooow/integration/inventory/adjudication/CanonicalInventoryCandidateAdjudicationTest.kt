package io.flooow.integration.inventory.adjudication

import io.flooow.integration.inventory.comparison.CanonicalInventoryCandidateComparisonResult
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.observation.ExactInventoryQuantity
import io.flooow.integration.inventory.selection.CanonicalInventoryMeasure
import io.flooow.integration.inventory.snapshot.CanonicalInventoryCandidateSnapshotId
import io.flooow.organization.OrganizationId
import java.util.UUID
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanonicalInventoryCandidateAdjudicationTest {
    private val snapshotId = CanonicalInventoryCandidateSnapshotId.of(uuid(1))

    @Test
    fun `canonical identifiers and principal are bounded and redacted`() {
        val id = CanonicalInventoryCandidateAdjudicationId.parse(
            "550e8400-e29b-41d4-a716-446655440000"
        )
        assertEquals("[INTERNAL]", id.toString())
        assertEquals("550e8400-e29b-41d4-a716-446655440000", id.valueForPersistence().toString())
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventoryCandidateAdjudicationId.parse(
                "550E8400-E29B-41D4-A716-446655440000"
            )
        }
        val principal = InventoryCandidateAdjudicationPrincipalReference.of("operador")
        assertEquals("[REDACTED]", principal.toString())
        assertFailsWith<IllegalArgumentException> {
            InventoryCandidateAdjudicationPrincipalReference.of(" operador ")
        }
        assertFailsWith<IllegalArgumentException> {
            InventoryCandidateAdjudicationPrincipalReference.of("x".repeat(129))
        }
    }

    @Test
    fun `controlled reasons match only their authorized comparison shapes`() {
        val single = CanonicalInventoryCandidateComparisonResult.SingleCandidate(
            snapshotId, CanonicalInventoryMeasure.ON_HAND,
            ExactInventoryQuantity.fromPersistence(BigInteger.ONE, 3)
        )
        val agreement = CanonicalInventoryCandidateComparisonResult.ExactAgreement(
            snapshotId, 2, CanonicalInventoryMeasure.ON_HAND,
            ExactInventoryQuantity.fromPersistence(BigInteger.ONE, 3)
        )
        val mismatch = CanonicalInventoryCandidateComparisonResult.MeasureMismatch(snapshotId, 2, 2)
        val divergence = CanonicalInventoryCandidateComparisonResult.ExactDivergence(
            snapshotId, 2, CanonicalInventoryMeasure.ON_HAND, 2
        )
        val reasons = CanonicalInventoryCandidateAdjudicationReason.entries
        assertEquals(
            setOf(CanonicalInventoryCandidateAdjudicationReason.SINGLE_CANDIDATE_CONFIRMATION),
            reasons.filter { it.matches(single) }.toSet()
        )
        assertEquals(
            setOf(CanonicalInventoryCandidateAdjudicationReason.EXACT_AGREEMENT_CONFIRMATION),
            reasons.filter { it.matches(agreement) }.toSet()
        )
        assertEquals(
            setOf(
                CanonicalInventoryCandidateAdjudicationReason.MEASURE_POLICY_REVIEW,
                CanonicalInventoryCandidateAdjudicationReason.CONTROLLED_EXCEPTION
            ),
            reasons.filter { it.matches(mismatch) }.toSet()
        )
        assertEquals(
            setOf(
                CanonicalInventoryCandidateAdjudicationReason.EVIDENCE_QUALITY_REVIEW,
                CanonicalInventoryCandidateAdjudicationReason.CONTROLLED_EXCEPTION
            ),
            reasons.filter { it.matches(divergence) }.toSet()
        )
        assertTrue(reasons.none {
            it.matches(CanonicalInventoryCandidateComparisonResult.IntegrityFailure)
        })
    }

    @Test
    fun `command contains only explicit references and renders no values`() {
        val command = AdjudicateCanonicalInventoryCandidate(
            OrganizationId(uuid(2)),
            CanonicalInventoryCandidateAdjudicationRequestId.of(uuid(3)),
            snapshotId,
            InventoryMappingDecisionId.of(uuid(4)),
            CanonicalInventoryCandidateAdjudicationReason.SINGLE_CANDIDATE_CONFIRMATION,
            InventoryCandidateAdjudicationPrincipalReference.of("operador"),
            CanonicalInventoryCandidateAdjudicationCorrelationId.of(uuid(5))
        )
        val rendered = command.toString()
        assertEquals("AdjudicateCanonicalInventoryCandidate([REDACTED])", rendered)
        listOf("operador", uuid(2).toString(), uuid(3).toString(), uuid(4).toString()).forEach {
            assertFalse(rendered.contains(it))
        }
    }

    @Test
    fun `controlled result renderings expose no identifiers`() {
        val id = CanonicalInventoryCandidateAdjudicationId.of(uuid(6))
        val values = listOf(
            CanonicalInventoryCandidateAdjudicationWriteResult.Adjudicated(id),
            CanonicalInventoryCandidateAdjudicationWriteResult.AlreadyAdjudicated(id),
            CanonicalInventoryCandidateAdjudicationWriteResult.SnapshotUnavailable,
            CanonicalInventoryCandidateAdjudicationWriteResult.CandidateUnavailable,
            CanonicalInventoryCandidateAdjudicationWriteResult.ReasonMismatch,
            CanonicalInventoryCandidateAdjudicationWriteResult.Conflict,
            CanonicalInventoryCandidateAdjudicationWriteResult.IntegrityFailure
        )
        assertTrue(values.none { it.toString().contains(uuid(6).toString()) })
    }

    private fun uuid(value: Long) = UUID(55, value)
}
