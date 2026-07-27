package io.flooow.kernel.reasoning


/**
 * Provides official composition roots for reasoning pipelines.
 *
 * This object centralizes construction of concrete reasoning components
 * while exposing only stable kernel contracts to consumers.
 */
object ReasoningModule {

    /**
     * Creates the default deterministic reasoning engine.
     *
     * The default confidence policy derives judgment confidence directly
     * from aggregated evidence.
     */
    fun deterministic(
        configuration: ReasoningConfiguration =
            ReasoningConfiguration()
    ): ReasoningEngine {
        val evidenceAggregator =
            DeterministicEvidenceAggregator()

        val hypothesisEvaluator =
            DeterministicHypothesisEvaluator(
                evidenceAggregator = evidenceAggregator,
                confidencePolicy = configuration.confidencePolicy,
                clock = configuration.clock
            )

        val evaluationStrategy =
            EvaluatorBasedStrategy(
                evaluator = hypothesisEvaluator,
                clock = configuration.clock
            )

        return DefaultReasoningEngine(
            strategies = listOf(evaluationStrategy)
        )
    }
}
