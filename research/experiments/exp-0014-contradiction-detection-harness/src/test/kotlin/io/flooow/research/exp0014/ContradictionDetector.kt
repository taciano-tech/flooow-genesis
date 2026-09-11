package io.flooow.research.exp0014

class ContradictionDetector {
    fun compare(
        left: GovernedClaim,
        right: GovernedClaim,
    ): ContradictionAssessment {
        val ids = setOf(left.claimId, right.claimId)

        if (left.organizationId != right.organizationId) {
            return incomparable(ids, "claims belong to different organizations")
        }

        if (left.subjectId != right.subjectId) {
            return incomparable(ids, "claims describe different subjects")
        }

        if (left.factKey != right.factKey) {
            return incomparable(ids, "claims describe different fact keys")
        }

        if (left.scope != right.scope) {
            return incomparable(ids, "claims belong to different governed scopes")
        }

        if (!left.validity.overlaps(right.validity)) {
            return incomparable(ids, "claim validity windows do not overlap")
        }

        if (left.value::class != right.value::class) {
            return incomparable(ids, "typed values use different value domains")
        }

        return if (left.value == right.value) {
            ContradictionAssessment(
                decision = ContradictionDecision.CONSISTENT,
                contradictionCount = 0,
                reasons = listOf("comparable typed claims agree"),
                comparedClaimIds = ids,
            )
        } else {
            ContradictionAssessment(
                decision = ContradictionDecision.CONTRADICTED,
                contradictionCount = 1,
                reasons = listOf(
                    "comparable typed claims assert different values",
                    "contradiction detection does not resolve which claim is true",
                ),
                comparedClaimIds = ids,
            )
        }
    }

    private fun incomparable(
        ids: Set<String>,
        reason: String,
    ): ContradictionAssessment =
        ContradictionAssessment(
            decision = ContradictionDecision.INCOMPARABLE,
            contradictionCount = 0,
            reasons = listOf(reason),
            comparedClaimIds = ids,
        )
}
