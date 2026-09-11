package io.flooow.research.exp0013

import kotlin.math.max

class ConfidenceEngine {
    fun assess(inputs: ConfidenceInputs): ConfidenceAssessment {
        val base =
            (inputs.sourceReliability * 0.35) +
                (inputs.measurementQuality * 0.35) +
                (inputs.temporalQuality * 0.30)

        val depthPenalty = inputs.derivationDepth * 0.06
        val contradictionPenalty = inputs.contradictionCount * 0.18
        val unknownPenalty = inputs.unknownCount * 0.10

        val raw =
            base -
                depthPenalty -
                contradictionPenalty -
                unknownPenalty

        val score = max(0.0, raw).coerceAtMost(1.0)

        val reasons =
            buildList {
                add("base confidence derives from source, measurement and temporal quality")
                if (inputs.derivationDepth > 0) {
                    add("derivation depth reduced confidence")
                }
                if (inputs.contradictionCount > 0) {
                    add("contradictions reduced confidence")
                }
                if (inputs.unknownCount > 0) {
                    add("explicit unknowns reduced confidence")
                }
            }

        val decision =
            when {
                inputs.sourceReliability == 0.0 ||
                    inputs.measurementQuality == 0.0 ||
                    inputs.temporalQuality == 0.0 ->
                    ConfidenceDecision.INSUFFICIENT_INFORMATION

                score >= 0.80 -> ConfidenceDecision.HIGH_CONFIDENCE
                score >= 0.55 -> ConfidenceDecision.MEDIUM_CONFIDENCE
                else -> ConfidenceDecision.LOW_CONFIDENCE
            }

        return ConfidenceAssessment(
            score = score,
            decision = decision,
            reasons = reasons,
        )
    }
}
