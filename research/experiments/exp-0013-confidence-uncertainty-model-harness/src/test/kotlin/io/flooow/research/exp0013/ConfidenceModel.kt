package io.flooow.research.exp0013

enum class ConfidenceDecision {
    HIGH_CONFIDENCE,
    MEDIUM_CONFIDENCE,
    LOW_CONFIDENCE,
    INSUFFICIENT_INFORMATION,
}

data class ConfidenceInputs(
    val sourceReliability: Double,
    val measurementQuality: Double,
    val temporalQuality: Double,
    val derivationDepth: Int,
    val contradictionCount: Int,
    val unknownCount: Int,
) {
    init {
        require(sourceReliability in 0.0..1.0)
        require(measurementQuality in 0.0..1.0)
        require(temporalQuality in 0.0..1.0)
        require(derivationDepth >= 0)
        require(contradictionCount >= 0)
        require(unknownCount >= 0)
    }
}

data class ConfidenceAssessment(
    val score: Double,
    val decision: ConfidenceDecision,
    val reasons: List<String>,
) {
    val canonicalTruth: Boolean = false
    val executable: Boolean = false
}
