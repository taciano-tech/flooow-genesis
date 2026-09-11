package io.flooow.marketplace.operations.economics.pricing

@ConsistentCopyVisibility
data class NetBackSourceScenarioFloor internal constructor(
    val appliedScenarioFloor: NetBackAppliedScenarioFloor,
    val sourceFloor: NetBackEconomicFloor
) {
    init {
        require(
            MarketplaceNetBackEconomicFloor.calculate(
                appliedScenarioFloor.appliedScenario.sourceProfile
            ) == NetBackCalculationResult.Complete(sourceFloor)
        ) { "Source scenario floor is inconsistent with the retained source profile" }
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface NetBackSourceScenarioFloorResult {
    data class Calculated(val evaluation: NetBackSourceScenarioFloor) :
        NetBackSourceScenarioFloorResult {
        override fun toString(): String = "[REDACTED]"
    }

    @ConsistentCopyVisibility
    data class Incomplete internal constructor(
        val appliedScenarioFloor: NetBackAppliedScenarioFloor,
        val calculation: NetBackCalculationResult.Incomplete
    ) : NetBackSourceScenarioFloorResult {
        init {
            require(
                MarketplaceNetBackEconomicFloor.calculate(
                    appliedScenarioFloor.appliedScenario.sourceProfile
                ) == calculation
            ) { "Incomplete source scenario calculation is inconsistent" }
        }

        override fun toString(): String = "[REDACTED]"
    }

    @ConsistentCopyVisibility
    data class Unachievable internal constructor(
        val appliedScenarioFloor: NetBackAppliedScenarioFloor,
        val calculation: NetBackCalculationResult.Unachievable
    ) : NetBackSourceScenarioFloorResult {
        init {
            require(
                MarketplaceNetBackEconomicFloor.calculate(
                    appliedScenarioFloor.appliedScenario.sourceProfile
                ) == calculation
            ) { "Unachievable source scenario calculation is inconsistent" }
        }

        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceNetBackSourceScenarioFloor {
    fun calculate(
        appliedScenarioFloor: NetBackAppliedScenarioFloor
    ): NetBackSourceScenarioFloorResult =
        when (
            val calculation = MarketplaceNetBackEconomicFloor.calculate(
                appliedScenarioFloor.appliedScenario.sourceProfile
            )
        ) {
            is NetBackCalculationResult.Complete ->
                NetBackSourceScenarioFloorResult.Calculated(
                    NetBackSourceScenarioFloor(appliedScenarioFloor, calculation.floor)
                )
            is NetBackCalculationResult.Incomplete ->
                NetBackSourceScenarioFloorResult.Incomplete(appliedScenarioFloor, calculation)
            is NetBackCalculationResult.Unachievable ->
                NetBackSourceScenarioFloorResult.Unachievable(appliedScenarioFloor, calculation)
        }
}
