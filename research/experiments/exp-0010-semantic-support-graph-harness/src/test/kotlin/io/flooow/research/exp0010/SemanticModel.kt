package io.flooow.research.exp0010

data class Proposition(
    val factKey: String,
    val relation: Relation,
    val value: String,
)

enum class Relation {
    EQUALS,
}

data class EvidenceFact(
    val reference: String,
    val proposition: Proposition,
)

sealed interface SupportDeclaration {
    val evidenceReferences: Set<String>

    data class Direct(
        override val evidenceReferences: Set<String>,
    ) : SupportDeclaration

    data class Derived(
        val ruleId: String,
        override val evidenceReferences: Set<String>,
    ) : SupportDeclaration

    data object Hypothetical : SupportDeclaration {
        override val evidenceReferences: Set<String> = emptySet()
    }
}

data class DraftSemanticClaim(
    val id: String,
    val proposition: Proposition,
    val support: SupportDeclaration,
    val confidence: Double,
    val narrative: String,
)

enum class SupportDecision {
    DIRECTLY_SUPPORTED,
    DERIVED_SUPPORTED,
    HYPOTHETICAL,
    UNSUPPORTED,
}

data class SupportEdge(
    val from: String,
    val to: String,
    val kind: String,
)

data class SupportGraph(
    val claimId: String,
    val evidenceReferences: Set<String>,
    val ruleId: String?,
    val edges: List<SupportEdge>,
)

data class ValidatedSemanticClaim(
    val draft: DraftSemanticClaim,
    val decision: SupportDecision,
    val reasons: List<String>,
    val graph: SupportGraph,
) {
    val canonicalTruth: Boolean = false
    val executable: Boolean = false
}

class UnknownEvidenceReferenceException(
    val references: Set<String>,
) : IllegalArgumentException(
    "unknown evidence reference(s): ${references.sorted().joinToString(", ")}",
)

class UnknownSemanticRuleException(
    val ruleId: String,
) : IllegalArgumentException("unknown semantic rule: $ruleId")
