package io.flooow.research.exp0009

enum class ClaimKind {
    OBSERVATION,
    INFERENCE,
    HYPOTHESIS,
    ASSUMPTION,
}

enum class ClaimDecision {
    EVIDENCE_BACKED,
    INFERRED,
    HYPOTHETICAL,
    ASSUMED,
    UNSUPPORTED,
}

data class DraftClaim(
    val kind: ClaimKind,
    val factKey: String,
    val predicate: String,
    val value: String,
    val evidenceReferences: Set<String>,
    val confidence: Double,
    val narrative: String,
)

data class ValidatedClaim(
    val draft: DraftClaim,
    val decision: ClaimDecision,
    val reasons: List<String>,
) {
    val canonicalTruth: Boolean = false
    val executable: Boolean = false
}

data class ClaimValidationReport(
    val claims: List<ValidatedClaim>,
) {
    val hasUnsupportedClaims: Boolean =
        claims.any { it.decision == ClaimDecision.UNSUPPORTED }
}

class UnknownEvidenceReferenceException(
    val references: Set<String>,
) : IllegalArgumentException(
        "unknown evidence reference(s): ${references.sorted().joinToString(", ")}",
    )
