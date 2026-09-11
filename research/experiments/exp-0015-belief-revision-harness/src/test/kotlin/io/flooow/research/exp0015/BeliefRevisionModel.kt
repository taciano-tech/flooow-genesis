package io.flooow.research.exp0015

import java.math.BigDecimal
import java.time.Instant

sealed interface BeliefValue {
    data class BooleanValue(
        val value: Boolean,
    ) : BeliefValue

    data class DecimalValue(
        val value: BigDecimal,
    ) : BeliefValue

    data class TextValue(
        val value: String,
    ) : BeliefValue
}

data class BeliefProposition(
    val organizationId: String,
    val subjectId: String,
    val factKey: String,
    val scope: String,
    val value: BeliefValue,
)

enum class SemanticSupport {
    SUPPORTED,
    UNSUPPORTED,
}

enum class TemporalValidity {
    VALID,
    STALE,
    FUTURE_EVIDENCE,
    OUTSIDE_WINDOW,
    TEMPORAL_CONFLICT,
}

enum class ConfidenceBand(
    val rank: Int,
) {
    INSUFFICIENT(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
}

enum class ContradictionState {
    NONE,
    UNRESOLVED,
}

enum class BeliefState {
    ESTABLISHED,
    REINFORCED,
    SUPERSEDED,
    CONTESTED,
    SUSPENDED,
    INSUFFICIENT_INFORMATION,
}

enum class RevisionReasonCode {
    INITIAL_ESTABLISHMENT,
    AGREEMENT_REINFORCES,
    CONFLICT_DETECTED,
    CANDIDATE_UNSUPPORTED,
    CANDIDATE_TEMPORALLY_INVALID,
    CANDIDATE_CONTRADICTED,
    CANDIDATE_NOT_STRONGER,
    CANDIDATE_NOT_NEWER,
    CANDIDATE_BELOW_MINIMUM_CONFIDENCE,
    CANDIDATE_SUPERSEDES,
    INSUFFICIENT_INFORMATION,
}

data class BeliefEvidenceProfile(
    val semanticSupport: SemanticSupport,
    val temporalValidity: TemporalValidity,
    val confidence: ConfidenceBand,
    val contradictionState: ContradictionState,
    val supportingClaimIds: Set<String>,
    val opposingClaimIds: Set<String> = emptySet(),
    val observedAt: Instant?,
)

data class BeliefVersion(
    val beliefId: String,
    val version: Int,
    val proposition: BeliefProposition,
    val state: BeliefState,
    val evidence: BeliefEvidenceProfile,
    val priorVersion: Int?,
    val revisionReasonCodes: Set<RevisionReasonCode>,
    val recordedAt: Instant,
) {
    val canonicalTruth: Boolean = false
    val executable: Boolean = false

    init {
        require(version >= 1)
        require(priorVersion == null || priorVersion < version)
    }
}

data class RevisionPolicy(
    val minimumSupersessionConfidence: ConfidenceBand = ConfidenceBand.MEDIUM,
)

data class BeliefRevisionResult(
    val previous: BeliefVersion?,
    val current: BeliefVersion,
) {
    val historyPreserved: Boolean =
        previous == null ||
            current.priorVersion == previous.version
}
