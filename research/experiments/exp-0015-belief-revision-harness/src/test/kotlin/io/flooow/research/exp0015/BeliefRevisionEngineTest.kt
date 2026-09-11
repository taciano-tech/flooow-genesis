package io.flooow.research.exp0015

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BeliefRevisionEngineTest {
    private val engine = BeliefRevisionEngine()

    private val t1 = Instant.parse("2026-09-07T10:00:00Z")
    private val t2 = Instant.parse("2026-09-07T12:00:00Z")
    private val t3 = Instant.parse("2026-09-07T14:00:00Z")

    @Test
    fun `supported valid belief can be established`() {
        val result =
            engine.establish(
                beliefId = "belief-1",
                proposition = margin("18.42"),
                evidence =
                    evidence(
                        confidence = ConfidenceBand.MEDIUM,
                        observedAt = t1,
                        supportingClaimIds = setOf("claim-1"),
                    ),
                recordedAt = t1,
            )

        assertEquals(BeliefState.ESTABLISHED, result.current.state)
        assertEquals(1, result.current.version)
        assertEquals(null, result.current.priorVersion)
        assertFalse(result.current.canonicalTruth)
        assertFalse(result.current.executable)
    }

    @Test
    fun `agreement reinforces without changing proposition`() {
        val current =
            established(
                confidence = ConfidenceBand.MEDIUM,
                observedAt = t1,
            )

        val result =
            engine.revise(
                current = current,
                candidateProposition = margin("18.42"),
                candidateEvidence =
                    evidence(
                        confidence = ConfidenceBand.HIGH,
                        observedAt = t2,
                        supportingClaimIds = setOf("claim-2"),
                    ),
                recordedAt = t2,
            )

        assertEquals(BeliefState.REINFORCED, result.current.state)
        assertEquals(current.proposition, result.current.proposition)
        assertEquals(setOf("claim-1", "claim-2"), result.current.evidence.supportingClaimIds)
        assertTrue(result.historyPreserved)
    }

    @Test
    fun `unsupported conflict cannot supersede`() {
        val current =
            established(
                confidence = ConfidenceBand.MEDIUM,
                observedAt = t1,
            )

        val result =
            engine.revise(
                current = current,
                candidateProposition = margin("25.00"),
                candidateEvidence =
                    evidence(
                        semanticSupport = SemanticSupport.UNSUPPORTED,
                        confidence = ConfidenceBand.HIGH,
                        observedAt = t2,
                        supportingClaimIds = setOf("claim-bad"),
                    ),
                recordedAt = t2,
            )

        assertEquals(BeliefState.SUSPENDED, result.current.state)
        assertEquals(current.proposition, result.current.proposition)
        assertTrue(
            RevisionReasonCode.CANDIDATE_UNSUPPORTED in
                result.current.revisionReasonCodes,
        )
    }

    @Test
    fun `temporally invalid conflict cannot supersede`() {
        val current =
            established(
                confidence = ConfidenceBand.MEDIUM,
                observedAt = t1,
            )

        val result =
            engine.revise(
                current = current,
                candidateProposition = margin("25.00"),
                candidateEvidence =
                    evidence(
                        temporalValidity = TemporalValidity.STALE,
                        confidence = ConfidenceBand.HIGH,
                        observedAt = t2,
                        supportingClaimIds = setOf("claim-stale"),
                    ),
                recordedAt = t2,
            )

        assertEquals(BeliefState.SUSPENDED, result.current.state)
        assertEquals(current.proposition, result.current.proposition)
    }

    @Test
    fun `unresolved contradiction blocks supersession`() {
        val current =
            established(
                confidence = ConfidenceBand.MEDIUM,
                observedAt = t1,
            )

        val result =
            engine.revise(
                current = current,
                candidateProposition = margin("25.00"),
                candidateEvidence =
                    evidence(
                        confidence = ConfidenceBand.HIGH,
                        contradictionState = ContradictionState.UNRESOLVED,
                        observedAt = t2,
                        supportingClaimIds = setOf("claim-conflict"),
                    ),
                recordedAt = t2,
            )

        assertEquals(BeliefState.CONTESTED, result.current.state)
        assertEquals(current.proposition, result.current.proposition)
        assertTrue(
            RevisionReasonCode.CANDIDATE_CONTRADICTED in
                result.current.revisionReasonCodes,
        )
    }

    @Test
    fun `weaker conflicting candidate cannot supersede stronger belief`() {
        val current =
            established(
                confidence = ConfidenceBand.HIGH,
                observedAt = t1,
            )

        val result =
            engine.revise(
                current = current,
                candidateProposition = margin("25.00"),
                candidateEvidence =
                    evidence(
                        confidence = ConfidenceBand.MEDIUM,
                        observedAt = t2,
                        supportingClaimIds = setOf("claim-weaker"),
                    ),
                recordedAt = t2,
            )

        assertEquals(BeliefState.CONTESTED, result.current.state)
        assertEquals(current.proposition, result.current.proposition)
        assertTrue(
            RevisionReasonCode.CANDIDATE_NOT_STRONGER in
                result.current.revisionReasonCodes,
        )
    }

    @Test
    fun `older conflicting candidate cannot supersede`() {
        val current =
            established(
                confidence = ConfidenceBand.MEDIUM,
                observedAt = t2,
            )

        val result =
            engine.revise(
                current = current,
                candidateProposition = margin("25.00"),
                candidateEvidence =
                    evidence(
                        confidence = ConfidenceBand.HIGH,
                        observedAt = t1,
                        supportingClaimIds = setOf("claim-older"),
                    ),
                recordedAt = t3,
            )

        assertEquals(BeliefState.CONTESTED, result.current.state)
        assertEquals(current.proposition, result.current.proposition)
        assertTrue(
            RevisionReasonCode.CANDIDATE_NOT_NEWER in
                result.current.revisionReasonCodes,
        )
    }

    @Test
    fun `stronger newer eligible candidate can supersede under policy`() {
        val current =
            established(
                confidence = ConfidenceBand.MEDIUM,
                observedAt = t1,
            )

        val result =
            engine.revise(
                current = current,
                candidateProposition = margin("25.00"),
                candidateEvidence =
                    evidence(
                        confidence = ConfidenceBand.HIGH,
                        observedAt = t2,
                        supportingClaimIds = setOf("claim-new"),
                    ),
                recordedAt = t2,
            )

        assertEquals(BeliefState.SUPERSEDED, result.current.state)
        assertEquals(margin("25.00"), result.current.proposition)
        assertEquals(current.version + 1, result.current.version)
        assertEquals(current.version, result.current.priorVersion)
        assertTrue(
            RevisionReasonCode.CANDIDATE_SUPERSEDES in
                result.current.revisionReasonCodes,
        )
        assertTrue(result.historyPreserved)
        assertFalse(result.current.canonicalTruth)
        assertFalse(result.current.executable)
    }

    @Test
    fun `missing observation time prevents automatic supersession`() {
        val current =
            established(
                confidence = ConfidenceBand.MEDIUM,
                observedAt = t1,
            )

        val result =
            engine.revise(
                current = current,
                candidateProposition = margin("25.00"),
                candidateEvidence =
                    evidence(
                        confidence = ConfidenceBand.HIGH,
                        observedAt = null,
                        supportingClaimIds = setOf("claim-no-time"),
                    ),
                recordedAt = t2,
            )

        assertEquals(BeliefState.CONTESTED, result.current.state)
        assertTrue(
            RevisionReasonCode.INSUFFICIENT_INFORMATION in
                result.current.revisionReasonCodes,
        )
    }

    @Test
    fun `revision cannot cross organizations`() {
        val current =
            established(
                confidence = ConfidenceBand.MEDIUM,
                observedAt = t1,
            )

        val candidate =
            margin("25.00").copy(
                organizationId = "org-2",
            )

        assertFailsWith<IllegalArgumentException> {
            engine.revise(
                current = current,
                candidateProposition = candidate,
                candidateEvidence =
                    evidence(
                        confidence = ConfidenceBand.HIGH,
                        observedAt = t2,
                    ),
                recordedAt = t2,
            )
        }
    }

    @Test
    fun `every revision preserves immutable lineage`() {
        val v1 =
            established(
                confidence = ConfidenceBand.LOW,
                observedAt = t1,
            )

        val v2 =
            engine.revise(
                current = v1,
                candidateProposition = margin("18.42"),
                candidateEvidence =
                    evidence(
                        confidence = ConfidenceBand.MEDIUM,
                        observedAt = t2,
                        supportingClaimIds = setOf("claim-2"),
                    ),
                recordedAt = t2,
            ).current

        val v3 =
            engine.revise(
                current = v2,
                candidateProposition = margin("22.00"),
                candidateEvidence =
                    evidence(
                        confidence = ConfidenceBand.HIGH,
                        observedAt = t3,
                        supportingClaimIds = setOf("claim-3"),
                    ),
                recordedAt = t3,
            ).current

        assertEquals(1, v1.version)
        assertEquals(2, v2.version)
        assertEquals(3, v3.version)
        assertEquals(null, v1.priorVersion)
        assertEquals(1, v2.priorVersion)
        assertEquals(2, v3.priorVersion)
        assertEquals(margin("18.42"), v1.proposition)
        assertEquals(margin("18.42"), v2.proposition)
        assertEquals(margin("22.00"), v3.proposition)
    }

    private fun established(
        confidence: ConfidenceBand,
        observedAt: Instant?,
    ): BeliefVersion =
        engine.establish(
            beliefId = "belief-1",
            proposition = margin("18.42"),
            evidence =
                evidence(
                    confidence = confidence,
                    observedAt = observedAt,
                    supportingClaimIds = setOf("claim-1"),
                ),
            recordedAt = t1,
        ).current

    private fun margin(value: String): BeliefProposition =
        BeliefProposition(
            organizationId = "org-1",
            subjectId = "sku-1",
            factKey = "contribution_margin_pct",
            scope = "marketplace-economic-intelligence",
            value =
                BeliefValue.DecimalValue(
                    BigDecimal(value),
                ),
        )

    private fun evidence(
        semanticSupport: SemanticSupport = SemanticSupport.SUPPORTED,
        temporalValidity: TemporalValidity = TemporalValidity.VALID,
        confidence: ConfidenceBand,
        contradictionState: ContradictionState = ContradictionState.NONE,
        observedAt: Instant?,
        supportingClaimIds: Set<String> = setOf("claim-candidate"),
        opposingClaimIds: Set<String> = emptySet(),
    ): BeliefEvidenceProfile =
        BeliefEvidenceProfile(
            semanticSupport = semanticSupport,
            temporalValidity = temporalValidity,
            confidence = confidence,
            contradictionState = contradictionState,
            supportingClaimIds = supportingClaimIds,
            opposingClaimIds = opposingClaimIds,
            observedAt = observedAt,
        )
}
