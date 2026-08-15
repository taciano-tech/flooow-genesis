package io.flooow.marketplace.operations.economics.pricing

import io.flooow.marketplace.operations.economics.MarketplaceMoney

@ConsistentCopyVisibility
data class NetBackCostBasisFloorDelta internal constructor(
    val sourceScenarioFloor: NetBackSourceScenarioFloor,
    val absoluteFloorDelta: MarketplaceMoney,
    val economicFloorDelta: MarketplaceMoney
) {
    init {
        val derivedFloor = sourceScenarioFloor.appliedScenarioFloor.floor
        val sourceFloor = sourceScenarioFloor.sourceFloor
        require(absoluteFloorDelta == derivedFloor.absoluteFloor - sourceFloor.absoluteFloor) {
            "Absolute floor delta is inconsistent with the retained floors"
        }
        require(economicFloorDelta == derivedFloor.economicFloor - sourceFloor.economicFloor) {
            "Economic floor delta is inconsistent with the retained floors"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

object MarketplaceNetBackCostBasisFloorDelta {
    fun calculate(sourceScenarioFloor: NetBackSourceScenarioFloor): NetBackCostBasisFloorDelta {
        val derivedFloor = sourceScenarioFloor.appliedScenarioFloor.floor
        val sourceFloor = sourceScenarioFloor.sourceFloor
        return NetBackCostBasisFloorDelta(
            sourceScenarioFloor,
            derivedFloor.absoluteFloor - sourceFloor.absoluteFloor,
            derivedFloor.economicFloor - sourceFloor.economicFloor
        )
    }

    override fun toString(): String = "[REDACTED]"
}
