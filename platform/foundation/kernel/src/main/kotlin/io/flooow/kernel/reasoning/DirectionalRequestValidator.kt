package io.flooow.kernel.reasoning

class DirectionalRequestValidator {
    fun validate(
        request: DirectionalEvaluationRequest
    ): DirectionalRequestValidation {
        val relationships = request.relationships
        if (relationships.isEmpty()) return invalid(
            DirectionalValidationError.NO_RELATIONSHIPS
        )
        if (relationships.map { it.id }.distinct().size != relationships.size) {
            return invalid(DirectionalValidationError.DUPLICATE_RELATIONSHIP_ID)
        }
        if (relationships.any { relationship ->
                request.evidenceSet.evidences.none { it.id == relationship.evidenceId }
            }) {
            return invalid(DirectionalValidationError.EVIDENCE_NOT_FOUND)
        }
        if (relationships.any { it.hypothesisId != request.hypothesis.id }) {
            return invalid(DirectionalValidationError.HYPOTHESIS_MISMATCH)
        }

        val pairGroups = relationships.groupBy { it.evidenceId to it.hypothesisId }
        if (pairGroups.values.any { group ->
                group.size > 1 && group.map { it.direction }.distinct().size > 1
            }) {
            return invalid(
                DirectionalValidationError.CONTRADICTORY_DUPLICATE_RELATIONSHIP
            )
        }
        if (pairGroups.values.any { it.size > 1 }) {
            return invalid(DirectionalValidationError.IDENTICAL_DUPLICATE_RELATIONSHIP)
        }

        val evaluated = relationships.sortedBy { it.id.value }.map { relationship ->
            val evidence = request.evidenceSet.evidences.first {
                it.id == relationship.evidenceId
            }
            EvaluatedRelationship(relationship, evidence.confidence)
        }
        return DirectionalRequestValidation.Valid(
            ValidatedDirectionalEvaluationRequest(request.hypothesis, evaluated)
        )
    }

    private fun invalid(error: DirectionalValidationError) =
        DirectionalRequestValidation.Invalid(error)
}
