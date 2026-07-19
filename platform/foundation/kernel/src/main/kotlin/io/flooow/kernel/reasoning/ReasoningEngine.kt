package io.flooow.kernel.reasoning

interface ReasoningEngine {

    fun evaluate(
        request: EvaluationRequest
    ): EvaluationResult
}
