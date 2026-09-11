package io.flooow.research.exp0003

import java.math.BigDecimal

class RequestValidator {
    fun validate(request: ExperimentalEvaluationRequest): Result<ValidatedRequest> {
        val duplicateRelationshipIds = request.relationships.groupBy { it.id }.filterValues { it.size > 1 }
        if (duplicateRelationshipIds.isNotEmpty()) {
            throw StructuralInputViolation("Relationship IDs must be unique: ${duplicateRelationshipIds.keys}")
        }
        if (request.relationships.isEmpty()) {
            return Result.failure(ValidationFailure(ValidationErrorCode.NO_RELATIONSHIPS))
        }
        if (request.relationships.any { it.evidenceId !in request.evidenceById }) {
            return Result.failure(ValidationFailure(ValidationErrorCode.EVIDENCE_NOT_FOUND))
        }
        if (request.relationships.any { it.hypothesisId != request.hypothesisId }) {
            return Result.failure(ValidationFailure(ValidationErrorCode.HYPOTHESIS_MISMATCH))
        }

        request.relationships.groupBy { it.evidenceId to it.hypothesisId }.values.forEach { samePair ->
            if (samePair.size > 1) {
                val code = if (samePair.map { it.direction }.distinct().size > 1) {
                    ValidationErrorCode.CONTRADICTORY_DUPLICATE_RELATIONSHIP
                } else {
                    ValidationErrorCode.IDENTICAL_DUPLICATE_RELATIONSHIP
                }
                return Result.failure(ValidationFailure(code))
            }
        }

        return Result.success(
            ValidatedRequest(
                request.hypothesisId,
                request.evidenceById,
                request.relationships.sortedBy { it.id.value },
                request.evaluatedAt
            )
        )
    }
}

class ValidationFailure(val code: ValidationErrorCode) : IllegalArgumentException(code.name)

class StructuralInputViolation(message: String) : IllegalArgumentException(message)

fun interface ExperimentalEvaluationPolicy {
    fun evaluate(request: ValidatedRequest): ExperimentalJudgment
}

class StrictConflictPolicy : ExperimentalEvaluationPolicy {
    override fun evaluate(request: ValidatedRequest): ExperimentalJudgment {
        val directions = request.relationships.map { it.direction }.toSet()
        val (direction, reason) = when (directions) {
            setOf(RelationshipDirection.SUPPORTS) -> JudgmentDirection.SUPPORTED to JudgmentReason.UNANIMOUS_SUPPORT
            setOf(RelationshipDirection.CONTRADICTS) -> JudgmentDirection.CONTRADICTED to JudgmentReason.UNANIMOUS_CONTRADICTION
            else -> JudgmentDirection.UNRESOLVED to JudgmentReason.CONFLICT
        }
        return request.judgment("P1_STRICT_CONFLICT", direction, reason, null, null)
    }
}

class WeightedBalancePolicy : ExperimentalEvaluationPolicy {
    override fun evaluate(request: ValidatedRequest): ExperimentalJudgment {
        val contributions = request.relationships.sortedBy { it.evidenceId.value }.map { relation ->
            relation to BigDecimal.valueOf(request.evidenceById.getValue(relation.evidenceId).confidence.value)
        }
        val support = contributions.filter { it.first.direction == RelationshipDirection.SUPPORTS }
            .fold(BigDecimal.ZERO) { total, item -> total + item.second }
        val contradict = contributions.filter { it.first.direction == RelationshipDirection.CONTRADICTS }
            .fold(BigDecimal.ZERO) { total, item -> total + item.second }
        val (direction, reason) = when {
            support.signum() == 0 && contradict.signum() == 0 ->
                JudgmentDirection.UNRESOLVED to JudgmentReason.INSUFFICIENT_WEIGHT
            support > contradict -> JudgmentDirection.SUPPORTED to JudgmentReason.POSITIVE_BALANCE
            contradict > support -> JudgmentDirection.CONTRADICTED to JudgmentReason.NEGATIVE_BALANCE
            else -> JudgmentDirection.UNRESOLVED to JudgmentReason.BALANCED_CONFLICT
        }
        return request.judgment("P2_WEIGHTED_BALANCE", direction, reason, support, contradict)
    }
}

private fun ValidatedRequest.judgment(
    policyId: String,
    direction: JudgmentDirection,
    reason: JudgmentReason,
    support: BigDecimal?,
    contradict: BigDecimal?
) = ExperimentalJudgment(
    hypothesisId,
    direction,
    reason,
    support,
    contradict,
    relationships,
    policyId,
    evaluatedAt
)

class ExperimentalReasoningEngine(
    private val validator: RequestValidator = RequestValidator(),
    private val policies: Map<String, ExperimentalEvaluationPolicy> = mapOf(
        "P1_STRICT_CONFLICT" to StrictConflictPolicy(),
        "P2_WEIGHTED_BALANCE" to WeightedBalancePolicy()
    )
) {
    fun evaluate(policyId: String, request: ExperimentalEvaluationRequest): EvaluationOutcome {
        val validated = validator.validate(request).getOrElse {
            return EvaluationOutcome.Invalid((it as ValidationFailure).code)
        }
        return EvaluationOutcome.Success(policies.getValue(policyId).evaluate(validated))
    }
}
