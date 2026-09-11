package io.flooow.research.exp0015

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeliefRevisionContradictionGuardTest {
    private val engine = BeliefRevisionEngine()

    @Test
    fun `same-value candidate with unresolved contradiction cannot reinforce belief`() {
        val currentObservedAt = Instant.parse("2026-09-07T10:00:00Z")
        val candidateObservedAt = Instant.parse("2026-09-07T12:00:00Z")

        val proposition =
            BeliefProposition(
                organizationId = "org-1",
                subjectId = "sku-1",
                factKey = "contribution_margin_pct",
                scope = "marketplace-economic-intelligence",
                value = BeliefValue.DecimalValue(BigDecimal("18.42")),
            )

        val current =
            engine.establish(
                beliefId = "belief-guard",
                proposition = proposition,
                evidence =
                    BeliefEvidenceProfile(
                        semanticSupport = SemanticSupport.SUPPORTED,
                        temporalValidity = TemporalValidity.VALID,
                        confidence = ConfidenceBand.MEDIUM,
                        contradictionState = ContradictionState.NONE,
                        supportingClaimIds = setOf("claim-current"),
                        observedAt = currentObservedAt,
                    ),
                recordedAt = currentObservedAt,
            ).current

        val result =
            engine.revise(
                current = current,
                candidateProposition = proposition,
                candidateEvidence =
                    BeliefEvidenceProfile(
                        semanticSupport = SemanticSupport.SUPPORTED,
                        temporalValidity = TemporalValidity.VALID,
                        confidence = ConfidenceBand.HIGH,
                        contradictionState = ContradictionState.UNRESOLVED,
                        supportingClaimIds = setOf("claim-candidate"),
                        opposingClaimIds = setOf("claim-opposing"),
                        observedAt = candidateObservedAt,
                    ),
                recordedAt = candidateObservedAt,
            )

        assertEquals(BeliefState.CONTESTED, result.current.state)
        assertEquals(current.proposition, result.current.proposition)
        assertTrue(
            RevisionReasonCode.CANDIDATE_CONTRADICTED in
                result.current.revisionReasonCodes,
        )
        assertFalse(
            RevisionReasonCode.AGREEMENT_REINFORCES in
                result.current.revisionReasonCodes,
        )
        assertEquals(current.version, result.current.priorVersion)
        assertTrue(result.historyPreserved)
        assertFalse(result.current.canonicalTruth)
        assertFalse(result.current.executable)
    }
}
