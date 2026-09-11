package io.flooow.marketplace.operations.economics.pricing

@ConsistentCopyVisibility
data class NetBackComparablePriceEvidence internal constructor(
    val floorDelta: NetBackCostBasisFloorDelta,
    val sourceObservation: ObservedMarketplacePrice,
    val derivedObservation: ObservedMarketplacePrice,
    val sourceAssessment: EconomicPricePositionAssessment,
    val derivedAssessment: EconomicPricePositionAssessment
) {
    init {
        require(observationsRepresentSameFact(sourceObservation, derivedObservation)) {
            "Comparable price observations do not represent the same source fact"
        }
        require(
            MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.sourceFloor,
                sourceObservation
            ) == EconomicPricePositionResult.Assessed(sourceAssessment)
        ) { "Source assessment is inconsistent with the retained floor and observation" }
        require(
            MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
                derivedObservation
            ) == EconomicPricePositionResult.Assessed(derivedAssessment)
        ) { "Derived assessment is inconsistent with the retained floor and observation" }
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface NetBackComparablePriceEvidenceResult {
    data class Assessed(val evidence: NetBackComparablePriceEvidence) :
        NetBackComparablePriceEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object EvidenceMismatch : NetBackComparablePriceEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SourceOwnershipMismatch : NetBackComparablePriceEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object DerivedOwnershipMismatch : NetBackComparablePriceEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object CurrencyMismatch : NetBackComparablePriceEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object PriceQuantumMismatch : NetBackComparablePriceEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceNetBackComparablePriceEvidence {
    fun evaluate(
        floorDelta: NetBackCostBasisFloorDelta,
        sourceObservation: ObservedMarketplacePrice,
        derivedObservation: ObservedMarketplacePrice
    ): NetBackComparablePriceEvidenceResult {
        if (!observationsRepresentSameFact(sourceObservation, derivedObservation)) {
            return NetBackComparablePriceEvidenceResult.EvidenceMismatch
        }

        val sourceAssessment = when (
            val sourceResult = MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.sourceFloor,
                sourceObservation
            )
        ) {
            is EconomicPricePositionResult.Assessed -> sourceResult.assessment
            EconomicPricePositionResult.OwnershipMismatch ->
                return NetBackComparablePriceEvidenceResult.SourceOwnershipMismatch
            EconomicPricePositionResult.CurrencyMismatch ->
                return NetBackComparablePriceEvidenceResult.CurrencyMismatch
            EconomicPricePositionResult.PriceQuantumMismatch ->
                return NetBackComparablePriceEvidenceResult.PriceQuantumMismatch
        }

        val derivedAssessment = when (
            val derivedResult = MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
                derivedObservation
            )
        ) {
            is EconomicPricePositionResult.Assessed -> derivedResult.assessment
            EconomicPricePositionResult.OwnershipMismatch ->
                return NetBackComparablePriceEvidenceResult.DerivedOwnershipMismatch
            EconomicPricePositionResult.CurrencyMismatch ->
                return NetBackComparablePriceEvidenceResult.CurrencyMismatch
            EconomicPricePositionResult.PriceQuantumMismatch ->
                return NetBackComparablePriceEvidenceResult.PriceQuantumMismatch
        }

        return NetBackComparablePriceEvidenceResult.Assessed(
            NetBackComparablePriceEvidence(
                floorDelta,
                sourceObservation,
                derivedObservation,
                sourceAssessment,
                derivedAssessment
            )
        )
    }

    override fun toString(): String = "[REDACTED]"
}

private fun observationsRepresentSameFact(
    source: ObservedMarketplacePrice,
    derived: ObservedMarketplacePrice
): Boolean =
    source.scenarioId != derived.scenarioId &&
        source.organizationId == derived.organizationId &&
        source.id == derived.id &&
        source.grossPrice == derived.grossPrice &&
        source.source == derived.source &&
        source.occurredAt == derived.occurredAt &&
        source.evidenceQuality == derived.evidenceQuality
