package io.flooow.research.exp0003

import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import java.math.BigDecimal

enum class RelationshipDirection { SUPPORTS, CONTRADICTS }

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

enum class ValidationErrorCode {
    NO_RELATIONSHIPS,
    HYPOTHESIS_MISMATCH,
    EVIDENCE_NOT_FOUND,
    CONTRADICTORY_DUPLICATE_RELATIONSHIP,
    IDENTICAL_DUPLICATE_RELATIONSHIP
}

data class ExperimentalEvidenceRelationship(
    val id: Identifier,
    val evidenceId: Identifier,
    val hypothesisId: Identifier,
    val direction: RelationshipDirection
)

data class ExperimentalEvaluationRequest(
    val hypothesisId: Identifier,
    val evidenceById: Map<Identifier, Evidence>,
    val relationships: List<ExperimentalEvidenceRelationship>,
    val evaluatedAt: Timestamp
)

data class ExperimentalJudgment(
    val hypothesisId: Identifier,
    val direction: JudgmentDirection,
    val reason: JudgmentReason,
    val supportTotal: BigDecimal?,
    val contradictTotal: BigDecimal?,
    val evaluatedRelationships: List<ExperimentalEvidenceRelationship>,
    val policyId: String,
    val evaluatedAt: Timestamp
)

sealed interface EvaluationOutcome {
    data class Success(val judgment: ExperimentalJudgment) : EvaluationOutcome
    data class Invalid(val code: ValidationErrorCode) : EvaluationOutcome
}

data class ValidatedRequest(
    val hypothesisId: Identifier,
    val evidenceById: Map<Identifier, Evidence>,
    val relationships: List<ExperimentalEvidenceRelationship>,
    val evaluatedAt: Timestamp
)
