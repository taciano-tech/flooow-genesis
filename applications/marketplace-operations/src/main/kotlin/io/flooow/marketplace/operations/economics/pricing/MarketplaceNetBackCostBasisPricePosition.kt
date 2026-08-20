package io.flooow.marketplace.operations.economics.pricing

@ConsistentCopyVisibility
data class NetBackCostBasisPricePosition internal constructor(
    val floorDelta: NetBackCostBasisFloorDelta,
    val observation: ObservedMarketplacePrice,
    val assessment: EconomicPricePositionAssessment
) {
    init {
        require(
            MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
                observation
            ) == EconomicPricePositionResult.Assessed(assessment)
        ) { "Price position is inconsistent with the retained derived floor and observation" }
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface NetBackCostBasisPricePositionResult {
    data class Assessed(val evaluation: NetBackCostBasisPricePosition) :
        NetBackCostBasisPricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object OwnershipMismatch : NetBackCostBasisPricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object CurrencyMismatch : NetBackCostBasisPricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object PriceQuantumMismatch : NetBackCostBasisPricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceNetBackCostBasisPricePosition {
    fun evaluate(
        floorDelta: NetBackCostBasisFloorDelta,
        observation: ObservedMarketplacePrice
    ): NetBackCostBasisPricePositionResult =
        when (
            val result = MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
                observation
            )
        ) {
            is EconomicPricePositionResult.Assessed ->
                NetBackCostBasisPricePositionResult.Assessed(
                    NetBackCostBasisPricePosition(floorDelta, observation, result.assessment)
                )
            EconomicPricePositionResult.OwnershipMismatch ->
                NetBackCostBasisPricePositionResult.OwnershipMismatch
            EconomicPricePositionResult.CurrencyMismatch ->
                NetBackCostBasisPricePositionResult.CurrencyMismatch
            EconomicPricePositionResult.PriceQuantumMismatch ->
                NetBackCostBasisPricePositionResult.PriceQuantumMismatch
        }

    override fun toString(): String = "[REDACTED]"
}
