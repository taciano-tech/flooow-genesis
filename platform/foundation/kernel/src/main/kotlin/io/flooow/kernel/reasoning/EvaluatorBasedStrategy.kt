package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Timestamp
import java.time.Clock

/**
 * Evaluation strategy that delegates judgment production to a
 * [HypothesisEvaluator].
 */
class EvaluatorBasedStrategy(
    private val evaluator: HypothesisEvaluator,
    private val clock: Clock = Clock.systemUTC()
) : EvaluationStrategy {

    override fun supports(request: EvaluationRequest): Boolean =
        true

    override fun evaluate(request: EvaluationRequest): EvaluationResult {
        val judgment = evaluator.evaluate(
            hypothesis = request.hypothesis,
            evidenceSet = request.evidenceSet
        )

        return EvaluationResult(
            judgment = judgment,
            evaluatedEvidence = request.evidenceSet,
            evaluatedAt = Timestamp.now(clock)
        )
    }
}
