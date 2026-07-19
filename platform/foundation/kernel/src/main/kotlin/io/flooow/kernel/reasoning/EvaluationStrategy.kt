package io.flooow.kernel.reasoning

/**
 * Defines an interchangeable approach for evaluating a reasoning request.
 *
 * Implementations may use deterministic rules, probabilistic methods,
 * artificial intelligence, or hybrid evaluation mechanisms.
 */
interface EvaluationStrategy {

    /**
     * Determines whether this strategy can evaluate the supplied request.
     */
    fun supports(request: EvaluationRequest): Boolean

    /**
     * Evaluates the supplied request and produces a complete result.
     */
    fun evaluate(request: EvaluationRequest): EvaluationResult
}
