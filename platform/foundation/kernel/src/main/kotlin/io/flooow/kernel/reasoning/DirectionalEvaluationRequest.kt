package io.flooow.kernel.reasoning

data class DirectionalEvaluationRequest(
    val hypothesis: Hypothesis,
    val evidenceSet: EvidenceSet,
    val relationships: List<EvidenceRelationship>
)

enum class DirectionalValidationError {
    NO_RELATIONSHIPS,
    EVIDENCE_NOT_FOUND,
    HYPOTHESIS_MISMATCH,
    DUPLICATE_RELATIONSHIP_ID,
    IDENTICAL_DUPLICATE_RELATIONSHIP,
    CONTRADICTORY_DUPLICATE_RELATIONSHIP
}

data class ValidatedDirectionalEvaluationRequest internal constructor(
    val hypothesis: Hypothesis,
    val relationships: List<EvaluatedRelationship>
)

sealed interface DirectionalRequestValidation {
    data class Valid(
        val request: ValidatedDirectionalEvaluationRequest
    ) : DirectionalRequestValidation

    data class Invalid(
        val error: DirectionalValidationError
    ) : DirectionalRequestValidation
}

sealed interface DirectionalEvaluationResult {
    data class Success(val judgment: StructuredJudgment) :
        DirectionalEvaluationResult

    data class Invalid(val error: DirectionalValidationError) :
        DirectionalEvaluationResult
}
