package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import java.math.BigDecimal

enum class RelationshipDirection { SUPPORTS, CONTRADICTS }

data class EvidenceRelationship(
    val id: Identifier,
    val evidenceId: Identifier,
    val hypothesisId: Identifier,
    val direction: RelationshipDirection
)

enum class JudgmentDirection { SUPPORTED, CONTRADICTED, UNRESOLVED }

enum class JudgmentReason {
    UNANIMOUS_SUPPORT,
    UNANIMOUS_CONTRADICTION,
    CONFLICT,
    POSITIVE_BALANCE,
    NEGATIVE_BALANCE,
    BALANCED_CONFLICT,
    INSUFFICIENT_WEIGHT
}

data class EvaluatedRelationship(
    val relationship: EvidenceRelationship,
    val confidence: Confidence
)

data class PolicyMeasure(
    val name: String,
    val value: BigDecimal,
    val interpretation: String
) {
    init {
        require(name.isNotBlank()) { "PolicyMeasure name must not be blank" }
        require(interpretation.isNotBlank()) {
            "PolicyMeasure interpretation must not be blank"
        }
    }
}

data class StructuredJudgment(
    val id: Identifier,
    val hypothesisId: Identifier,
    val direction: JudgmentDirection,
    val reason: JudgmentReason,
    val evaluatedRelationships: List<EvaluatedRelationship>,
    val policyId: String,
    val measures: List<PolicyMeasure>,
    val createdAt: Timestamp
) {
    init {
        require(policyId.isNotBlank()) { "policyId must not be blank" }
        require(evaluatedRelationships.isNotEmpty()) {
            "StructuredJudgment must retain evaluated relationships"
        }
        require(evaluatedRelationships == evaluatedRelationships.sortedBy {
            it.relationship.id.value
        }) { "Evaluated relationships must be ordered by relationship id" }
        require(evaluatedRelationships.all {
            it.relationship.hypothesisId == hypothesisId
        }) { "Every relationship must reference the judgment hypothesis" }
        require(evaluatedRelationships.map { it.relationship.id }.distinct().size ==
            evaluatedRelationships.size
        ) { "Relationship identifiers must be unique" }
        require(evaluatedRelationships.map {
            it.relationship.evidenceId to it.relationship.hypothesisId
        }.distinct().size == evaluatedRelationships.size) {
            "Evidence-hypothesis pairs must be unique"
        }
        require(measures == measures.sortedBy { it.name }) {
            "Measures must be ordered by name"
        }
        require(measures.map { it.name }.distinct().size == measures.size) {
            "Measure names must be unique"
        }
    }
}
