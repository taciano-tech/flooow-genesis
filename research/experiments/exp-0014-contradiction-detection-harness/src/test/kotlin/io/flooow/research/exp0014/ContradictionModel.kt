package io.flooow.research.exp0014

import java.math.BigDecimal
import java.time.Instant

sealed interface ClaimValue {
    data class BooleanValue(
        val value: Boolean,
    ) : ClaimValue

    data class DecimalValue(
        val value: BigDecimal,
    ) : ClaimValue

    data class TextValue(
        val value: String,
    ) : ClaimValue
}

data class ValidityWindow(
    val startsAt: Instant,
    val endsAt: Instant,
) {
    init {
        require(!endsAt.isBefore(startsAt)) {
            "validity window end must be >= start"
        }
    }

    fun overlaps(other: ValidityWindow): Boolean =
        !endsAt.isBefore(other.startsAt) &&
            !other.endsAt.isBefore(startsAt)
}

data class GovernedClaim(
    val claimId: String,
    val organizationId: String,
    val subjectId: String,
    val factKey: String,
    val scope: String,
    val value: ClaimValue,
    val validity: ValidityWindow,
    val evidenceReferences: Set<String>,
    val narrative: String? = null,
)

enum class ContradictionDecision {
    CONSISTENT,
    CONTRADICTED,
    INCOMPARABLE,
}

data class ContradictionAssessment(
    val decision: ContradictionDecision,
    val contradictionCount: Int,
    val reasons: List<String>,
    val comparedClaimIds: Set<String>,
) {
    val canonicalTruth: Boolean = false
    val executable: Boolean = false
}
