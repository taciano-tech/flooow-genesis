package io.flooow.marketplace.operations.economics.pricing

enum class NetBackCostBasisPricePositionTransitionType {
    BELOW_ABSOLUTE_TO_BELOW_ABSOLUTE,
    BELOW_ABSOLUTE_TO_BELOW_ECONOMIC,
    BELOW_ABSOLUTE_TO_AT_ECONOMIC,
    BELOW_ABSOLUTE_TO_ABOVE_ECONOMIC,
    BELOW_ECONOMIC_TO_BELOW_ABSOLUTE,
    BELOW_ECONOMIC_TO_BELOW_ECONOMIC,
    BELOW_ECONOMIC_TO_AT_ECONOMIC,
    BELOW_ECONOMIC_TO_ABOVE_ECONOMIC,
    AT_ECONOMIC_TO_BELOW_ABSOLUTE,
    AT_ECONOMIC_TO_BELOW_ECONOMIC,
    AT_ECONOMIC_TO_AT_ECONOMIC,
    AT_ECONOMIC_TO_ABOVE_ECONOMIC,
    ABOVE_ECONOMIC_TO_BELOW_ABSOLUTE,
    ABOVE_ECONOMIC_TO_BELOW_ECONOMIC,
    ABOVE_ECONOMIC_TO_AT_ECONOMIC,
    ABOVE_ECONOMIC_TO_ABOVE_ECONOMIC
}

@ConsistentCopyVisibility
data class NetBackCostBasisPricePositionTransition internal constructor(
    val evidence: NetBackComparablePriceEvidence,
    val transition: NetBackCostBasisPricePositionTransitionType
) {
    init {
        require(
            transition == classifyTransition(
                evidence.sourceAssessment.position,
                evidence.derivedAssessment.position
            )
        ) { "Price position transition is inconsistent with the retained evidence" }
    }

    override fun toString(): String = "[REDACTED]"
}

object MarketplaceNetBackCostBasisPricePositionTransition {
    fun classify(
        evidence: NetBackComparablePriceEvidence
    ): NetBackCostBasisPricePositionTransition =
        NetBackCostBasisPricePositionTransition(
            evidence,
            classifyTransition(
                evidence.sourceAssessment.position,
                evidence.derivedAssessment.position
            )
        )

    override fun toString(): String = "[REDACTED]"
}

private fun classifyTransition(
    source: EconomicPricePosition,
    derived: EconomicPricePosition
): NetBackCostBasisPricePositionTransitionType =
    when (source) {
        EconomicPricePosition.BELOW_ABSOLUTE_FLOOR -> when (derived) {
            EconomicPricePosition.BELOW_ABSOLUTE_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.BELOW_ABSOLUTE_TO_BELOW_ABSOLUTE
            EconomicPricePosition.BELOW_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.BELOW_ABSOLUTE_TO_BELOW_ECONOMIC
            EconomicPricePosition.AT_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.BELOW_ABSOLUTE_TO_AT_ECONOMIC
            EconomicPricePosition.ABOVE_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.BELOW_ABSOLUTE_TO_ABOVE_ECONOMIC
        }
        EconomicPricePosition.BELOW_ECONOMIC_FLOOR -> when (derived) {
            EconomicPricePosition.BELOW_ABSOLUTE_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.BELOW_ECONOMIC_TO_BELOW_ABSOLUTE
            EconomicPricePosition.BELOW_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.BELOW_ECONOMIC_TO_BELOW_ECONOMIC
            EconomicPricePosition.AT_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.BELOW_ECONOMIC_TO_AT_ECONOMIC
            EconomicPricePosition.ABOVE_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.BELOW_ECONOMIC_TO_ABOVE_ECONOMIC
        }
        EconomicPricePosition.AT_ECONOMIC_FLOOR -> when (derived) {
            EconomicPricePosition.BELOW_ABSOLUTE_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.AT_ECONOMIC_TO_BELOW_ABSOLUTE
            EconomicPricePosition.BELOW_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.AT_ECONOMIC_TO_BELOW_ECONOMIC
            EconomicPricePosition.AT_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.AT_ECONOMIC_TO_AT_ECONOMIC
            EconomicPricePosition.ABOVE_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.AT_ECONOMIC_TO_ABOVE_ECONOMIC
        }
        EconomicPricePosition.ABOVE_ECONOMIC_FLOOR -> when (derived) {
            EconomicPricePosition.BELOW_ABSOLUTE_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.ABOVE_ECONOMIC_TO_BELOW_ABSOLUTE
            EconomicPricePosition.BELOW_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.ABOVE_ECONOMIC_TO_BELOW_ECONOMIC
            EconomicPricePosition.AT_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.ABOVE_ECONOMIC_TO_AT_ECONOMIC
            EconomicPricePosition.ABOVE_ECONOMIC_FLOOR ->
                NetBackCostBasisPricePositionTransitionType.ABOVE_ECONOMIC_TO_ABOVE_ECONOMIC
        }
    }
