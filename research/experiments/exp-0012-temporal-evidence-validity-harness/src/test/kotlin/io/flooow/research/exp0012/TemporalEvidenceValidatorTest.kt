package io.flooow.research.exp0012

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TemporalEvidenceValidatorTest {
    private val now = Instant.parse("2026-09-07T18:00:00Z")

    private val policy =
        TemporalPolicy(
            maxAge = Duration.ofHours(24),
            maxPremiseSpread = Duration.ofHours(6),
        )

    private val validator = TemporalEvidenceValidator(policy)

    private val window =
        DecisionWindow(
            startsAt = Instant.parse("2026-09-06T18:00:00Z"),
            endsAt = now,
        )

    @Test
    fun `fresh evidence inside window is temporally valid`() {
        val result =
            validator.validate(
                now = now,
                window = window,
                evidence =
                    listOf(
                        evidence(
                            "ledger:margin:123",
                            "2026-09-07T16:00:00Z",
                        ),
                    ),
                semanticSupportValid = true,
            )

        assertEquals(TemporalDecision.TEMPORALLY_VALID, result.decision)
        assertFalse(result.canonicalTruth)
        assertFalse(result.executable)
    }

    @Test
    fun `future dated evidence is rejected`() {
        val result =
            validator.validate(
                now = now,
                window =
                    DecisionWindow(
                        startsAt = Instant.parse("2026-09-06T18:00:00Z"),
                        endsAt = Instant.parse("2026-09-08T18:00:00Z"),
                    ),
                evidence =
                    listOf(
                        evidence(
                            "ledger:future:1",
                            "2026-09-07T19:00:00Z",
                        ),
                    ),
                semanticSupportValid = true,
            )

        assertEquals(TemporalDecision.FUTURE_EVIDENCE, result.decision)
    }

    @Test
    fun `evidence outside decision window is rejected`() {
        val result =
            validator.validate(
                now = now,
                window = window,
                evidence =
                    listOf(
                        evidence(
                            "ledger:old-window:1",
                            "2026-09-06T10:00:00Z",
                        ),
                    ),
                semanticSupportValid = true,
            )

        assertEquals(TemporalDecision.OUTSIDE_WINDOW, result.decision)
    }

    @Test
    fun `stale evidence is rejected even when semantically supported`() {
        val wideWindow =
            DecisionWindow(
                startsAt = Instant.parse("2026-09-01T00:00:00Z"),
                endsAt = now,
            )

        val result =
            validator.validate(
                now = now,
                window = wideWindow,
                evidence =
                    listOf(
                        evidence(
                            "ledger:stale:1",
                            "2026-09-05T12:00:00Z",
                        ),
                    ),
                semanticSupportValid = true,
            )

        assertEquals(TemporalDecision.STALE, result.decision)
    }

    @Test
    fun `premises too far apart are temporally conflicting`() {
        val result =
            validator.validate(
                now = now,
                window = window,
                evidence =
                    listOf(
                        evidence(
                            "ledger:a",
                            "2026-09-07T08:00:00Z",
                        ),
                        evidence(
                            "ledger:b",
                            "2026-09-07T16:00:00Z",
                        ),
                    ),
                semanticSupportValid = true,
            )

        assertEquals(TemporalDecision.TEMPORAL_CONFLICT, result.decision)
    }

    @Test
    fun `temporal validity never upgrades unsupported semantics`() {
        val result =
            validator.validate(
                now = now,
                window = window,
                evidence =
                    listOf(
                        evidence(
                            "ledger:margin:123",
                            "2026-09-07T16:00:00Z",
                        ),
                    ),
                semanticSupportValid = false,
            )

        assertEquals(TemporalDecision.UNSUPPORTED, result.decision)
    }

    private fun evidence(
        reference: String,
        observedAt: String,
    ): TimedEvidenceFact =
        TimedEvidenceFact(
            reference = reference,
            proposition = Proposition("contribution_margin_pct", "18.42"),
            observedAt = Instant.parse(observedAt),
        )
}
