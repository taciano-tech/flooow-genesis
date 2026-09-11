package io.flooow.research.exp0012

import java.time.Instant
import java.time.Duration

data class Proposition(
    val factKey: String,
    val value: String,
)

data class TimedEvidenceFact(
    val reference: String,
    val proposition: Proposition,
    val observedAt: Instant,
)

data class DecisionWindow(
    val startsAt: Instant,
    val endsAt: Instant,
) {
    init {
        require(!endsAt.isBefore(startsAt)) {
            "decision window end must be >= start"
        }
    }

    fun contains(instant: Instant): Boolean =
        !instant.isBefore(startsAt) && !instant.isAfter(endsAt)
}

enum class TemporalDecision {
    TEMPORALLY_VALID,
    STALE,
    FUTURE_EVIDENCE,
    OUTSIDE_WINDOW,
    TEMPORAL_CONFLICT,
    UNSUPPORTED,
}

data class TemporalValidationResult(
    val decision: TemporalDecision,
    val reasons: List<String>,
    val evidenceReferences: Set<String>,
) {
    val canonicalTruth: Boolean = false
    val executable: Boolean = false
}

data class TemporalPolicy(
    val maxAge: Duration,
    val maxPremiseSpread: Duration,
)
