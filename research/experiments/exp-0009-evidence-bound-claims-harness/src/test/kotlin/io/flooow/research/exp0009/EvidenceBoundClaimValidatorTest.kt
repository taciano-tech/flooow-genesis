package io.flooow.research.exp0009

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidenceBoundClaimValidatorTest {
    private val marginEvidence =
        EvidenceFact(
            reference = "ledger:contribution-margin:123",
            factKey = "contribution_margin_pct",
            value = "18.42",
            observedAt = Instant.parse("2026-09-07T12:00:00Z"),
            source = "flooow-economic-truth-projection",
        )

    private val catalog = EvidenceCatalog(listOf(marginEvidence))
    private val validator = EvidenceBoundClaimValidator(catalog)

    @Test
    fun `matching observation becomes evidence backed but never canonical truth`() {
        val report =
            validator.validate(
                listOf(
                    DraftClaim(
                        kind = ClaimKind.OBSERVATION,
                        factKey = "contribution_margin_pct",
                        predicate = "EQUALS",
                        value = "18.42",
                        evidenceReferences = setOf(marginEvidence.reference),
                        confidence = 1.0,
                        narrative = "Contribution margin is 18.42 percent.",
                    ),
                ),
            )

        val claim = report.claims.single()
        assertEquals(ClaimDecision.EVIDENCE_BACKED, claim.decision)
        assertFalse(claim.canonicalTruth)
        assertFalse(claim.executable)
        assertFalse(report.hasUnsupportedClaims)
    }

    @Test
    fun `invented industry average observation is rejected as unsupported`() {
        val report =
            validator.validate(
                listOf(
                    DraftClaim(
                        kind = ClaimKind.OBSERVATION,
                        factKey = "industry_average_contribution_margin_pct",
                        predicate = "EQUALS",
                        value = "12.00",
                        evidenceReferences = setOf(marginEvidence.reference),
                        confidence = 0.9,
                        narrative = "Industry average contribution margin is 12 percent.",
                    ),
                ),
            )

        val claim = report.claims.single()
        assertEquals(ClaimDecision.UNSUPPORTED, claim.decision)
        assertTrue(report.hasUnsupportedClaims)
        assertTrue(claim.reasons.single().contains("no referenced evidence supports"))
    }

    @Test
    fun `invented labor cost observation without evidence is rejected`() {
        val report =
            validator.validate(
                listOf(
                    DraftClaim(
                        kind = ClaimKind.OBSERVATION,
                        factKey = "labor_cost_pct",
                        predicate = "EQUALS",
                        value = "25.00",
                        evidenceReferences = emptySet(),
                        confidence = 0.7,
                        narrative = "Labor costs are 25 percent.",
                    ),
                ),
            )

        assertEquals(ClaimDecision.UNSUPPORTED, report.claims.single().decision)
    }

    @Test
    fun `unknown evidence reference fails deterministically`() {
        val error =
            assertFailsWith<UnknownEvidenceReferenceException> {
                validator.validate(
                    listOf(
                        DraftClaim(
                            kind = ClaimKind.INFERENCE,
                            factKey = "margin_pressure",
                            predicate = "EQUALS",
                            value = "possible",
                            evidenceReferences = setOf("invented:evidence:999"),
                            confidence = 0.5,
                            narrative = "Margin pressure may exist.",
                        ),
                    ),
                )
            }

        assertEquals(setOf("invented:evidence:999"), error.references)
    }

    @Test
    fun `hypothesis may be ungrounded but remains explicitly hypothetical`() {
        val report =
            validator.validate(
                listOf(
                    DraftClaim(
                        kind = ClaimKind.HYPOTHESIS,
                        factKey = "labor_cost_relationship",
                        predicate = "EQUALS",
                        value = "investigate",
                        evidenceReferences = emptySet(),
                        confidence = 0.3,
                        narrative = "Investigate whether labor costs influence margin.",
                    ),
                ),
            )

        val claim = report.claims.single()
        assertEquals(ClaimDecision.HYPOTHETICAL, claim.decision)
        assertFalse(claim.canonicalTruth)
        assertFalse(claim.executable)
    }

    @Test
    fun `inference requires evidence linkage`() {
        val report =
            validator.validate(
                listOf(
                    DraftClaim(
                        kind = ClaimKind.INFERENCE,
                        factKey = "margin_pressure",
                        predicate = "EQUALS",
                        value = "possible",
                        evidenceReferences = emptySet(),
                        confidence = 0.6,
                        narrative = "Margin pressure may exist.",
                    ),
                ),
            )

        assertEquals(ClaimDecision.UNSUPPORTED, report.claims.single().decision)
    }
}
