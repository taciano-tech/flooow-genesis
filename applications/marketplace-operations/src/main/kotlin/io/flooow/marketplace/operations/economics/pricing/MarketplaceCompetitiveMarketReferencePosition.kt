package io.flooow.marketplace.operations.economics.pricing

import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthQuality
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.organization.OrganizationId
import java.time.Duration
import java.time.Instant

enum class CompetitiveMarketReferencePosition {
    BELOW_REFERENCE_BAND,
    WITHIN_REFERENCE_BAND,
    ABOVE_REFERENCE_BAND
}

@ConsistentCopyVisibility
data class CompetitiveMarketReferencePositionAssessment internal constructor(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val ownObservationId: EconomicPriceObservationId,
    val marketplace: MarketplaceKey,
    val currency: MarketplaceCurrency,
    val priceQuantum: MarketplaceMoney,
    val ownObservedGrossPrice: MarketplaceMoney,
    val lowerMedianPrice: MarketplaceMoney,
    val upperMedianPrice: MarketplaceMoney,
    val gapToLowerReference: MarketplaceMoney,
    val gapToUpperReference: MarketplaceMoney,
    val position: CompetitiveMarketReferencePosition,
    val ownEconomicQuality: MarketplaceEconomicTruthQuality,
    val marketEvidenceQuality: EconomicEvidenceQuality,
    val quality: MarketplaceEconomicTruthQuality,
    val comparisonPolicyVersion: CompetitivePriceComparisonPolicyVersion,
    val maximumObservationAge: Duration,
    val referencePolicyVersion: CompetitiveMarketReferencePolicyVersion,
    val minimumDistinctSellers: Int,
    val evaluatedAt: Instant
) {
    init {
        require(priceQuantum.currency == currency && priceQuantum.amount.signum() > 0)
        require(listOf(ownObservedGrossPrice, lowerMedianPrice, upperMedianPrice,
            gapToLowerReference, gapToUpperReference).all { it.currency == currency })
        require(listOf(ownObservedGrossPrice, lowerMedianPrice, upperMedianPrice).all {
            it.amount.remainder(priceQuantum.amount).signum() == 0
        })
        require(lowerMedianPrice.amount <= upperMedianPrice.amount)
        require(gapToLowerReference == ownObservedGrossPrice - lowerMedianPrice)
        require(gapToUpperReference == ownObservedGrossPrice - upperMedianPrice)
        require(position == classifyReferencePosition(ownObservedGrossPrice, lowerMedianPrice, upperMedianPrice))
        require(quality == combinedReferenceQuality(ownEconomicQuality, marketEvidenceQuality))
        require(minimumDistinctSellers in 2..100)
        require(evaluatedAt.nano % 1_000 == 0)
    }
    override fun toString(): String = "[REDACTED]"
}

sealed interface CompetitiveMarketReferencePositionResult {
    data class Assessed(val assessment: CompetitiveMarketReferencePositionAssessment) :
        CompetitiveMarketReferencePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object SourceAssessmentMismatch : CompetitiveMarketReferencePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceCompetitiveMarketReferencePosition {
    fun evaluate(
        competitiveAssessment: CompetitivePricePositionAssessment,
        marketReferenceAssessment: CompetitiveMarketReferenceAssessment
    ): CompetitiveMarketReferencePositionResult {
        val reproduced = MarketplaceCompetitiveMarketReference.evaluate(
            competitiveAssessment,
            CompetitiveMarketReferencePolicy(
                marketReferenceAssessment.referencePolicyVersion,
                marketReferenceAssessment.minimumDistinctSellers
            )
        )
        if (reproduced !is CompetitiveMarketReferenceResult.Referenced ||
            reproduced.assessment != marketReferenceAssessment
        ) return CompetitiveMarketReferencePositionResult.SourceAssessmentMismatch

        val own = competitiveAssessment.ownObservedGrossPrice
        val lower = marketReferenceAssessment.lowerMedianPrice
        val upper = marketReferenceAssessment.upperMedianPrice
        return CompetitiveMarketReferencePositionResult.Assessed(
            CompetitiveMarketReferencePositionAssessment(
                competitiveAssessment.organizationId, competitiveAssessment.scenarioId,
                competitiveAssessment.ownObservationId, competitiveAssessment.marketplace,
                competitiveAssessment.currency, competitiveAssessment.priceQuantum, own, lower, upper,
                own - lower, own - upper, classifyReferencePosition(own, lower, upper),
                competitiveAssessment.ownEconomicQuality, marketReferenceAssessment.marketEvidenceQuality,
                combinedReferenceQuality(competitiveAssessment.ownEconomicQuality,
                    marketReferenceAssessment.marketEvidenceQuality),
                competitiveAssessment.policyVersion, competitiveAssessment.maximumObservationAge,
                marketReferenceAssessment.referencePolicyVersion,
                marketReferenceAssessment.minimumDistinctSellers, competitiveAssessment.evaluatedAt
            )
        )
    }
}

private fun classifyReferencePosition(own: MarketplaceMoney, lower: MarketplaceMoney, upper: MarketplaceMoney) =
    when {
        own.amount < lower.amount -> CompetitiveMarketReferencePosition.BELOW_REFERENCE_BAND
        own.amount <= upper.amount -> CompetitiveMarketReferencePosition.WITHIN_REFERENCE_BAND
        else -> CompetitiveMarketReferencePosition.ABOVE_REFERENCE_BAND
    }

private fun combinedReferenceQuality(
    own: MarketplaceEconomicTruthQuality,
    market: EconomicEvidenceQuality
) = if (own == MarketplaceEconomicTruthQuality.CONFIRMED && market == EconomicEvidenceQuality.CONFIRMED)
    MarketplaceEconomicTruthQuality.CONFIRMED else MarketplaceEconomicTruthQuality.ESTIMATED
