package io.flooow.research.exp0014

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContradictionDetectorTest {
    private val detector = ContradictionDetector()

    private val currentWindow =
        ValidityWindow(
            startsAt = Instant.parse("2026-09-07T00:00:00Z"),
            endsAt = Instant.parse("2026-09-08T00:00:00Z"),
        )

    @Test
    fun `identical comparable claims are consistent`() {
        val left =
            claim(
                id = "c1",
                value = ClaimValue.DecimalValue(BigDecimal("18.42")),
            )

        val right =
            claim(
                id = "c2",
                value = ClaimValue.DecimalValue(BigDecimal("18.42")),
            )

        val result = detector.compare(left, right)

        assertEquals(ContradictionDecision.CONSISTENT, result.decision)
        assertEquals(0, result.contradictionCount)
        assertFalse(result.canonicalTruth)
        assertFalse(result.executable)
    }

    @Test
    fun `different comparable decimal claims are contradicted`() {
        val left =
            claim(
                id = "c1",
                value = ClaimValue.DecimalValue(BigDecimal("18.42")),
            )

        val right =
            claim(
                id = "c2",
                value = ClaimValue.DecimalValue(BigDecimal("21.10")),
            )

        val result = detector.compare(left, right)

        assertEquals(ContradictionDecision.CONTRADICTED, result.decision)
        assertEquals(1, result.contradictionCount)
        assertTrue(
            result.reasons.any {
                it.contains("does not resolve which claim is true")
            },
        )
    }

    @Test
    fun `opposite boolean claims are contradicted`() {
        val left =
            claim(
                id = "c1",
                factKey = "margin_below_20",
                value = ClaimValue.BooleanValue(true),
            )

        val right =
            claim(
                id = "c2",
                factKey = "margin_below_20",
                value = ClaimValue.BooleanValue(false),
            )

        val result = detector.compare(left, right)

        assertEquals(ContradictionDecision.CONTRADICTED, result.decision)
    }

    @Test
    fun `different subjects are incomparable`() {
        val left =
            claim(
                id = "c1",
                subjectId = "sku-1",
                value = ClaimValue.DecimalValue(BigDecimal("18.42")),
            )

        val right =
            claim(
                id = "c2",
                subjectId = "sku-2",
                value = ClaimValue.DecimalValue(BigDecimal("21.10")),
            )

        val result = detector.compare(left, right)

        assertEquals(ContradictionDecision.INCOMPARABLE, result.decision)
        assertEquals(0, result.contradictionCount)
    }

    @Test
    fun `non overlapping validity windows are incomparable`() {
        val historical =
            ValidityWindow(
                startsAt = Instant.parse("2026-09-01T00:00:00Z"),
                endsAt = Instant.parse("2026-09-02T00:00:00Z"),
            )

        val current =
            ValidityWindow(
                startsAt = Instant.parse("2026-09-07T00:00:00Z"),
                endsAt = Instant.parse("2026-09-08T00:00:00Z"),
            )

        val left =
            claim(
                id = "c1",
                value = ClaimValue.DecimalValue(BigDecimal("18.42")),
                validity = historical,
            )

        val right =
            claim(
                id = "c2",
                value = ClaimValue.DecimalValue(BigDecimal("21.10")),
                validity = current,
            )

        val result = detector.compare(left, right)

        assertEquals(ContradictionDecision.INCOMPARABLE, result.decision)
    }

    @Test
    fun `narrative cannot override typed contradiction`() {
        val left =
            claim(
                id = "c1",
                value = ClaimValue.BooleanValue(true),
                factKey = "attention_required",
                narrative = "Everything looks fine.",
            )

        val right =
            claim(
                id = "c2",
                value = ClaimValue.BooleanValue(false),
                factKey = "attention_required",
                narrative = "Urgent attention is definitely required.",
            )

        val result = detector.compare(left, right)

        assertEquals(ContradictionDecision.CONTRADICTED, result.decision)
    }

    @Test
    fun `different organizations are incomparable`() {
        val left =
            claim(
                id = "c1",
                organizationId = "org-a",
                value = ClaimValue.TextValue("ACTIVE"),
                factKey = "listing_status",
            )

        val right =
            claim(
                id = "c2",
                organizationId = "org-b",
                value = ClaimValue.TextValue("PAUSED"),
                factKey = "listing_status",
            )

        val result = detector.compare(left, right)

        assertEquals(ContradictionDecision.INCOMPARABLE, result.decision)
    }

    private fun claim(
        id: String,
        organizationId: String = "org-1",
        subjectId: String = "sku-1",
        factKey: String = "contribution_margin_pct",
        scope: String = "marketplace-economic-truth",
        value: ClaimValue,
        validity: ValidityWindow = currentWindow,
        narrative: String? = null,
    ): GovernedClaim =
        GovernedClaim(
            claimId = id,
            organizationId = organizationId,
            subjectId = subjectId,
            factKey = factKey,
            scope = scope,
            value = value,
            validity = validity,
            evidenceReferences = setOf("evidence:$id"),
            narrative = narrative,
        )
}
