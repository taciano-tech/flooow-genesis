package io.flooow.marketplace.operations.economics.pricing

@ConsistentCopyVisibility
data class NetBackAppliedScenarioFloor internal constructor(
    val appliedScenario: NetBackCostBasisAppliedScenario,
    val floor: NetBackEconomicFloor
) {
    init {
        require(
            MarketplaceNetBackEconomicFloor.calculate(appliedScenario.derivedProfile) ==
                NetBackCalculationResult.Complete(floor)
        ) { "Applied scenario floor is inconsistent with the derived profile" }
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface NetBackAppliedScenarioFloorResult {
    data class Calculated(val evaluation: NetBackAppliedScenarioFloor) :
        NetBackAppliedScenarioFloorResult {
        override fun toString(): String = "[REDACTED]"
    }

    @ConsistentCopyVisibility
    data class Incomplete internal constructor(
        val appliedScenario: NetBackCostBasisAppliedScenario,
        val calculation: NetBackCalculationResult.Incomplete
    ) : NetBackAppliedScenarioFloorResult {
        init {
            require(
                MarketplaceNetBackEconomicFloor.calculate(appliedScenario.derivedProfile) == calculation
            ) { "Incomplete applied scenario calculation is inconsistent" }
        }

        override fun toString(): String = "[REDACTED]"
    }

    @ConsistentCopyVisibility
    data class Unachievable internal constructor(
        val appliedScenario: NetBackCostBasisAppliedScenario,
        val calculation: NetBackCalculationResult.Unachievable
    ) : NetBackAppliedScenarioFloorResult {
        init {
            require(
                MarketplaceNetBackEconomicFloor.calculate(appliedScenario.derivedProfile) == calculation
            ) { "Unachievable applied scenario calculation is inconsistent" }
        }

        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceNetBackAppliedScenarioFloor {
    fun calculate(
        appliedScenario: NetBackCostBasisAppliedScenario
    ): NetBackAppliedScenarioFloorResult =
        when (
            val calculation = MarketplaceNetBackEconomicFloor.calculate(
                appliedScenario.derivedProfile
            )
        ) {
            is NetBackCalculationResult.Complete ->
                NetBackAppliedScenarioFloorResult.Calculated(
                    NetBackAppliedScenarioFloor(appliedScenario, calculation.floor)
                )
            is NetBackCalculationResult.Incomplete ->
                NetBackAppliedScenarioFloorResult.Incomplete(appliedScenario, calculation)
            is NetBackCalculationResult.Unachievable ->
                NetBackAppliedScenarioFloorResult.Unachievable(appliedScenario, calculation)
        }
}
