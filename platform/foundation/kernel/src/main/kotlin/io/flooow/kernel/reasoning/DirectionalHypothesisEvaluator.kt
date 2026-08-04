package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Timestamp
import java.time.Clock

class DirectionalHypothesisEvaluator(
    private val validator: DirectionalRequestValidator,
    private val policy: DirectionalEvaluationPolicy,
    private val clock: Clock
) {
    private val judgmentIdFactory = DirectionalJudgmentIdFactory()
    fun evaluate(
        request: DirectionalEvaluationRequest
    ): DirectionalEvaluationResult = when (val validation = validator.validate(request)) {
        is DirectionalRequestValidation.Invalid ->
            DirectionalEvaluationResult.Invalid(validation.error)
        is DirectionalRequestValidation.Valid -> evaluate(validation.request)
    }

    private fun evaluate(
        request: ValidatedDirectionalEvaluationRequest
    ): DirectionalEvaluationResult.Success {
        val decision = policy.evaluate(request.hypothesis, request.relationships)
        val outcome = DirectionalOutcome(decision.direction, decision.reason)
        check(outcome in policy.supportedOutcomes) {
            "Policy ${policy.id} returned unsupported outcome $outcome"
        }
        val judgment = StructuredJudgment(
            id = judgmentIdFactory.create(
                request.hypothesis,
                policy.id,
                request.relationships
            ),
            hypothesisId = request.hypothesis.id,
            direction = decision.direction,
            reason = decision.reason,
            evaluatedRelationships = request.relationships,
            policyId = policy.id,
            measures = decision.measures.sortedBy { it.name },
            createdAt = Timestamp.now(clock)
        )
        return DirectionalEvaluationResult.Success(judgment)
    }
}
