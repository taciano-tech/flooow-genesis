package io.flooow.research.exp0012

import java.time.Duration
import java.time.Instant

class TemporalEvidenceValidator(
    private val policy: TemporalPolicy,
) {
    fun validate(
        now: Instant,
        window: DecisionWindow,
        evidence: List<TimedEvidenceFact>,
        semanticSupportValid: Boolean,
    ): TemporalValidationResult {
        if (!semanticSupportValid) {
            return TemporalValidationResult(
                decision = TemporalDecision.UNSUPPORTED,
                reasons = listOf("temporal validity cannot upgrade semantically unsupported evidence"),
                evidenceReferences = evidence.map { it.reference }.toSet(),
            )
        }

        if (evidence.isEmpty()) {
            return TemporalValidationResult(
                decision = TemporalDecision.UNSUPPORTED,
                reasons = listOf("temporal validation requires evidence"),
                evidenceReferences = emptySet(),
            )
        }

        val refs = evidence.map { it.reference }.toSet()

        val future = evidence.filter { it.observedAt.isAfter(now) }
        if (future.isNotEmpty()) {
            return TemporalValidationResult(
                decision = TemporalDecision.FUTURE_EVIDENCE,
                reasons = listOf("evidence cannot be observed after evaluation time"),
                evidenceReferences = refs,
            )
        }

        val outsideWindow = evidence.filterNot { window.contains(it.observedAt) }
        if (outsideWindow.isNotEmpty()) {
            return TemporalValidationResult(
                decision = TemporalDecision.OUTSIDE_WINDOW,
                reasons = listOf("one or more evidence facts fall outside the claim decision window"),
                evidenceReferences = refs,
            )
        }

        val stale =
            evidence.filter {
                Duration.between(it.observedAt, now) > policy.maxAge
            }

        if (stale.isNotEmpty()) {
            return TemporalValidationResult(
                decision = TemporalDecision.STALE,
                reasons = listOf("one or more evidence facts exceed the freshness policy"),
                evidenceReferences = refs,
            )
        }

        val observedTimes = evidence.map { it.observedAt }
        val earliest = observedTimes.minOrNull()!!
        val latest = observedTimes.maxOrNull()!!
        val spread = Duration.between(earliest, latest)

        if (spread > policy.maxPremiseSpread) {
            return TemporalValidationResult(
                decision = TemporalDecision.TEMPORAL_CONFLICT,
                reasons = listOf("premise timestamps are too far apart for one coherent support state"),
                evidenceReferences = refs,
            )
        }

        return TemporalValidationResult(
            decision = TemporalDecision.TEMPORALLY_VALID,
            reasons = listOf("all evidence satisfies freshness, decision-window and premise-spread policy"),
            evidenceReferences = refs,
        )
    }
}
