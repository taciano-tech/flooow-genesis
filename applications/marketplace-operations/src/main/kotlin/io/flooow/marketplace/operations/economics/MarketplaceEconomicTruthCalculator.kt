package io.flooow.marketplace.operations.economics

import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

enum class ContributionMarginUndefinedReason {
    NON_POSITIVE_GROSS_REVENUE
}

sealed interface ContributionMargin {
    class Defined internal constructor(val decimalValue: BigDecimal) : ContributionMargin {
        override fun equals(other: Any?): Boolean =
            other is Defined && decimalValue == other.decimalValue

        override fun hashCode(): Int = decimalValue.hashCode()

        override fun toString(): String = "[REDACTED]"
    }

    data class Undefined(
        val reason: ContributionMarginUndefinedReason
    ) : ContributionMargin {
        override fun toString(): String = "[REDACTED]"
    }
}

enum class MarketplaceEconomicTruthQuality {
    CONFIRMED,
    ESTIMATED
}

sealed interface MarketplaceEconomicTruthCalculationResult {
    @ConsistentCopyVisibility
    data class Complete internal constructor(val result: MarketplaceEconomicResult) :
        MarketplaceEconomicTruthCalculationResult {
        override fun toString(): String = "[REDACTED]"
    }

    @ConsistentCopyVisibility
    data class Incomplete internal constructor(
        val organizationId: OrganizationId,
        val orderId: MarketplaceOrderId,
        val missingTypes: List<EconomicComponentType>,
        val partialTypes: List<EconomicComponentType>,
        val suppliedComponents: List<EconomicComponent>,
        val calculationPolicyVersion: EconomicCalculationPolicyVersion
    ) : MarketplaceEconomicTruthCalculationResult {
        override fun toString(): String = "[REDACTED]"
    }
}

@ConsistentCopyVisibility
data class MarketplaceEconomicResult internal constructor(
    val organizationId: OrganizationId,
    val orderId: MarketplaceOrderId,
    val marketplace: MarketplaceKey,
    val externalOrderId: MarketplaceExternalOrderId,
    val orderOccurredAt: Instant,
    val currency: MarketplaceCurrency,
    val grossRevenue: MarketplaceMoney,
    val totalMarketplaceFees: MarketplaceMoney,
    val totalShipping: MarketplaceMoney,
    val totalAdvertising: MarketplaceMoney,
    val totalTaxes: MarketplaceMoney,
    val totalProductCost: MarketplaceMoney,
    val totalFinancialCost: MarketplaceMoney,
    val totalOtherAdjustments: MarketplaceMoney,
    val contribution: MarketplaceMoney,
    val contributionMargin: ContributionMargin,
    val truthQuality: MarketplaceEconomicTruthQuality,
    val calculationPolicyVersion: EconomicCalculationPolicyVersion,
    val components: List<EconomicComponent>
) {
    override fun toString(): String = "[REDACTED]"
}

object MarketplaceEconomicTruthCalculator {
    val POLICY_VERSION = EconomicCalculationPolicyVersion("marketplace-economic-truth/1")

    fun calculate(order: MarketplaceOrder): MarketplaceEconomicTruthCalculationResult {
        val missingTypes = typesWithCoverage(order, EconomicComponentCoverage.MISSING)
        val partialTypes = typesWithCoverage(order, EconomicComponentCoverage.PARTIAL)
        if (missingTypes.isNotEmpty() || partialTypes.isNotEmpty()) {
            return MarketplaceEconomicTruthCalculationResult.Incomplete(
                organizationId = order.organizationId,
                orderId = order.id,
                missingTypes = missingTypes,
                partialTypes = partialTypes,
                suppliedComponents = order.components,
                calculationPolicyVersion = POLICY_VERSION
            )
        }

        val grossRevenue = netAddition(order, EconomicComponentType.REVENUE)
        val totalMarketplaceFees =
            netDeduction(order, EconomicComponentType.MARKETPLACE_COMMISSION) +
                netDeduction(order, EconomicComponentType.MARKETPLACE_FEE)
        val totalShipping = netDeduction(order, EconomicComponentType.SHIPPING)
        val totalAdvertising = netDeduction(order, EconomicComponentType.ADVERTISING)
        val totalTaxes = netDeduction(order, EconomicComponentType.TAX)
        val totalProductCost = netDeduction(order, EconomicComponentType.PRODUCT_COST)
        val totalFinancialCost = netDeduction(order, EconomicComponentType.FINANCIAL_COST)
        val totalOtherAdjustments = netDeduction(order, EconomicComponentType.OTHER_ADJUSTMENT)

        val contribution = grossRevenue -
            totalMarketplaceFees -
            totalShipping -
            totalAdvertising -
            totalTaxes -
            totalProductCost -
            totalFinancialCost -
            totalOtherAdjustments

        val contributionMargin = if (grossRevenue.amount.signum() > 0) {
            ContributionMargin.Defined(
                contribution.amount.divide(grossRevenue.amount, 8, RoundingMode.HALF_EVEN)
            )
        } else {
            ContributionMargin.Undefined(
                ContributionMarginUndefinedReason.NON_POSITIVE_GROSS_REVENUE
            )
        }

        val truthQuality = if (
            order.components.any { it.quality == EconomicEvidenceQuality.ESTIMATED }
        ) {
            MarketplaceEconomicTruthQuality.ESTIMATED
        } else {
            MarketplaceEconomicTruthQuality.CONFIRMED
        }

        return MarketplaceEconomicTruthCalculationResult.Complete(
            MarketplaceEconomicResult(
                organizationId = order.organizationId,
                orderId = order.id,
                marketplace = order.marketplace,
                externalOrderId = order.externalOrderId,
                orderOccurredAt = order.occurredAt,
                currency = order.currency,
                grossRevenue = grossRevenue,
                totalMarketplaceFees = totalMarketplaceFees,
                totalShipping = totalShipping,
                totalAdvertising = totalAdvertising,
                totalTaxes = totalTaxes,
                totalProductCost = totalProductCost,
                totalFinancialCost = totalFinancialCost,
                totalOtherAdjustments = totalOtherAdjustments,
                contribution = contribution,
                contributionMargin = contributionMargin,
                truthQuality = truthQuality,
                calculationPolicyVersion = POLICY_VERSION,
                components = order.components
            )
        )
    }

    private fun typesWithCoverage(
        order: MarketplaceOrder,
        state: EconomicComponentCoverage
    ): List<EconomicComponentType> = EconomicComponentType.entries.filter {
        order.coverage.getValue(it) == state
    }

    private fun netAddition(
        order: MarketplaceOrder,
        type: EconomicComponentType
    ): MarketplaceMoney =
        sum(order, type, EconomicDirection.ADDITION) -
            sum(order, type, EconomicDirection.DEDUCTION)

    private fun netDeduction(
        order: MarketplaceOrder,
        type: EconomicComponentType
    ): MarketplaceMoney =
        sum(order, type, EconomicDirection.DEDUCTION) -
            sum(order, type, EconomicDirection.ADDITION)

    private fun sum(
        order: MarketplaceOrder,
        type: EconomicComponentType,
        direction: EconomicDirection
    ): MarketplaceMoney = order.components
        .asSequence()
        .filter { it.type == type && it.direction == direction }
        .fold(MarketplaceMoney.zero(order.currency)) { total, component ->
            total + component.magnitude
        }
}
