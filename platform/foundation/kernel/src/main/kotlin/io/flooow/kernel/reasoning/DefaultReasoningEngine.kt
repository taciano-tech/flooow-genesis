package io.flooow.kernel.reasoning

/**
 * Default reasoning engine that delegates evaluation to the first
 * compatible strategy.
 */
class DefaultReasoningEngine(
    strategies: List<EvaluationStrategy>
) : ReasoningEngine {

    private val strategies: List<EvaluationStrategy> = strategies.toList()

    init {
        require(this.strategies.isNotEmpty()) {
            "DefaultReasoningEngine requires at least one evaluation strategy"
        }
    }

    override fun evaluate(request: EvaluationRequest): EvaluationResult {
        val strategy = strategies.firstOrNull { it.supports(request) }
            ?: throw IllegalStateException(
                "No evaluation strategy supports the supplied request"
            )

        return strategy.evaluate(request)
    }
}
