package io.flooow.marketplace.operations.economics.pricing

import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.organization.OrganizationId
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.UUID

data class CompetitiveMarketReferencePolicyVersion(val value: String) {
    init {
        require(MARKET_REFERENCE_POLICY_VERSION_PATTERN.matches(value)) {
            "Competitive market reference policy version is invalid"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class CompetitiveMarketReferencePolicy(
    val version: CompetitiveMarketReferencePolicyVersion,
    val minimumDistinctSellers: Int
) {
    init {
        require(minimumDistinctSellers in MINIMUM_DISTINCT_SELLERS..MAXIMUM_DISTINCT_SELLERS) {
            "Competitive market reference minimum sellers must be between 2 and 100"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

class SellerCompetitivePriceReference internal constructor(
    val sellerKey: CompetitorSellerKey,
    val grossPrice: MarketplaceMoney,
    supportingObservationIds: Collection<CompetitorPriceObservationId>
) {
    val supportingObservationIds: List<CompetitorPriceObservationId> =
        Collections.unmodifiableList(supportingObservationIds.toList())

    init {
        require(grossPrice.amount.signum() >= 0) {
            "Seller competitive price reference must not be negative"
        }
        require(this.supportingObservationIds.isNotEmpty()) {
            "Seller competitive price reference requires supporting evidence"
        }
        require(this.supportingObservationIds.toSet().size == this.supportingObservationIds.size) {
            "Seller competitive price reference evidence must be unique"
        }
        require(
            this.supportingObservationIds ==
                this.supportingObservationIds.sortedWith(MARKET_REFERENCE_OBSERVATION_ID_COMPARATOR)
        ) { "Seller competitive price reference evidence must use deterministic identity order" }
    }

    override fun equals(other: Any?): Boolean =
        other is SellerCompetitivePriceReference &&
            sellerKey == other.sellerKey &&
            grossPrice == other.grossPrice &&
            supportingObservationIds == other.supportingObservationIds

    override fun hashCode(): Int {
        var result = sellerKey.hashCode()
        result = 31 * result + grossPrice.hashCode()
        result = 31 * result + supportingObservationIds.hashCode()
        return result
    }

    override fun toString(): String = "[REDACTED]"
}

@ConsistentCopyVisibility
data class InsufficientSellerDiversityEvidence internal constructor(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val ownObservationId: EconomicPriceObservationId,
    val marketplace: MarketplaceKey,
    val observedOfferCount: Int,
    val observedSellerCount: Int,
    val requiredSellerCount: Int,
    val comparisonPolicyVersion: CompetitivePriceComparisonPolicyVersion,
    val referencePolicyVersion: CompetitiveMarketReferencePolicyVersion,
    val evaluatedAt: Instant
) {
    init {
        require(observedOfferCount > 0) { "Observed offer count must be positive" }
        require(observedSellerCount > 0) { "Observed seller count must be positive" }
        require(observedSellerCount <= observedOfferCount) {
            "Observed seller count must not exceed offer count"
        }
        require(requiredSellerCount in MINIMUM_DISTINCT_SELLERS..MAXIMUM_DISTINCT_SELLERS) {
            "Required seller count is invalid"
        }
        require(observedSellerCount < requiredSellerCount) {
            "Insufficient seller evidence requires a seller shortfall"
        }
        require(evaluatedAt.nano % 1_000 == 0) {
            "Competitive market reference time must use microsecond precision"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

class CompetitiveMarketReferenceAssessment internal constructor(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val ownObservationId: EconomicPriceObservationId,
    val marketplace: MarketplaceKey,
    val currency: MarketplaceCurrency,
    val priceQuantum: MarketplaceMoney,
    sellerReferences: Collection<SellerCompetitivePriceReference>,
    val observedOfferCount: Int,
    val observedSellerCount: Int,
    val lowerMedianPrice: MarketplaceMoney,
    val upperMedianPrice: MarketplaceMoney,
    val marketEvidenceQuality: EconomicEvidenceQuality,
    val comparisonPolicyVersion: CompetitivePriceComparisonPolicyVersion,
    val maximumObservationAge: Duration,
    val referencePolicyVersion: CompetitiveMarketReferencePolicyVersion,
    val minimumDistinctSellers: Int,
    val earliestOccurredAt: Instant,
    val latestOccurredAt: Instant,
    val evaluatedAt: Instant
) {
    val sellerReferences: List<SellerCompetitivePriceReference> =
        Collections.unmodifiableList(sellerReferences.toList())

    init {
        require(priceQuantum.currency == currency && priceQuantum.amount.signum() > 0) {
            "Competitive market reference quantum must be positive and use assessment currency"
        }
        require(this.sellerReferences.isNotEmpty()) {
            "Competitive market reference requires seller evidence"
        }
        require(observedOfferCount >= this.sellerReferences.size) {
            "Observed offer count is inconsistent"
        }
        require(observedSellerCount == this.sellerReferences.size) {
            "Observed seller count is inconsistent"
        }
        require(minimumDistinctSellers in MINIMUM_DISTINCT_SELLERS..MAXIMUM_DISTINCT_SELLERS) {
            "Competitive market reference minimum sellers is invalid"
        }
        require(observedSellerCount >= minimumDistinctSellers) {
            "Competitive market reference requires sufficient seller diversity"
        }
        require(this.sellerReferences.map { it.sellerKey }.toSet().size == observedSellerCount) {
            "Competitive market reference sellers must be unique"
        }
        require(this.sellerReferences == this.sellerReferences.sortedWith(SELLER_REFERENCE_COMPARATOR)) {
            "Competitive market references must use deterministic market order"
        }
        require(this.sellerReferences.all {
            it.grossPrice.currency == currency &&
                it.grossPrice.amount.remainder(priceQuantum.amount).signum() == 0
        }) { "Competitive market reference prices must share currency and quantum" }
        val lowerIndex = (observedSellerCount - 1) / 2
        val upperIndex = observedSellerCount / 2
        require(lowerMedianPrice == this.sellerReferences[lowerIndex].grossPrice) {
            "Competitive market lower median is inconsistent"
        }
        require(upperMedianPrice == this.sellerReferences[upperIndex].grossPrice) {
            "Competitive market upper median is inconsistent"
        }
        require(lowerMedianPrice.amount <= upperMedianPrice.amount) {
            "Competitive market median band is invalid"
        }
        require(
            !maximumObservationAge.isZero &&
                !maximumObservationAge.isNegative &&
                maximumObservationAge.nano % 1_000 == 0
        ) { "Competitive market reference source window is invalid" }
        require(earliestOccurredAt <= latestOccurredAt) {
            "Competitive market reference occurrence interval is invalid"
        }
        require(!earliestOccurredAt.isBefore(evaluatedAt.minus(maximumObservationAge))) {
            "Competitive market reference occurrence is outside the source window"
        }
        require(!latestOccurredAt.isAfter(evaluatedAt)) {
            "Competitive market reference occurrence is after evaluation"
        }
        require(
            earliestOccurredAt.nano % 1_000 == 0 &&
                latestOccurredAt.nano % 1_000 == 0 &&
                evaluatedAt.nano % 1_000 == 0
        ) { "Competitive market reference times must use microsecond precision" }
    }

    override fun equals(other: Any?): Boolean =
        other is CompetitiveMarketReferenceAssessment &&
            organizationId == other.organizationId &&
            scenarioId == other.scenarioId &&
            ownObservationId == other.ownObservationId &&
            marketplace == other.marketplace &&
            currency == other.currency &&
            priceQuantum == other.priceQuantum &&
            sellerReferences == other.sellerReferences &&
            observedOfferCount == other.observedOfferCount &&
            observedSellerCount == other.observedSellerCount &&
            lowerMedianPrice == other.lowerMedianPrice &&
            upperMedianPrice == other.upperMedianPrice &&
            marketEvidenceQuality == other.marketEvidenceQuality &&
            comparisonPolicyVersion == other.comparisonPolicyVersion &&
            maximumObservationAge == other.maximumObservationAge &&
            referencePolicyVersion == other.referencePolicyVersion &&
            minimumDistinctSellers == other.minimumDistinctSellers &&
            earliestOccurredAt == other.earliestOccurredAt &&
            latestOccurredAt == other.latestOccurredAt &&
            evaluatedAt == other.evaluatedAt

    override fun hashCode(): Int {
        var result = organizationId.hashCode()
        result = 31 * result + scenarioId.hashCode()
        result = 31 * result + ownObservationId.hashCode()
        result = 31 * result + marketplace.hashCode()
        result = 31 * result + currency.hashCode()
        result = 31 * result + priceQuantum.hashCode()
        result = 31 * result + sellerReferences.hashCode()
        result = 31 * result + observedOfferCount
        result = 31 * result + observedSellerCount
        result = 31 * result + lowerMedianPrice.hashCode()
        result = 31 * result + upperMedianPrice.hashCode()
        result = 31 * result + marketEvidenceQuality.hashCode()
        result = 31 * result + comparisonPolicyVersion.hashCode()
        result = 31 * result + maximumObservationAge.hashCode()
        result = 31 * result + referencePolicyVersion.hashCode()
        result = 31 * result + minimumDistinctSellers
        result = 31 * result + earliestOccurredAt.hashCode()
        result = 31 * result + latestOccurredAt.hashCode()
        result = 31 * result + evaluatedAt.hashCode()
        return result
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface CompetitiveMarketReferenceResult {
    data class Referenced(val assessment: CompetitiveMarketReferenceAssessment) :
        CompetitiveMarketReferenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class InsufficientSellerDiversity(val evidence: InsufficientSellerDiversityEvidence) :
        CompetitiveMarketReferenceResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceCompetitiveMarketReference {
    fun evaluate(
        competitiveAssessment: CompetitivePricePositionAssessment,
        policy: CompetitiveMarketReferencePolicy
    ): CompetitiveMarketReferenceResult {
        val sellerReferences = competitiveAssessment.competitorObservations
            .groupBy { it.sellerKey }
            .map { (sellerKey, observations) -> sellerReference(sellerKey, observations) }
            .sortedWith(SELLER_REFERENCE_COMPARATOR)

        if (sellerReferences.size < policy.minimumDistinctSellers) {
            return CompetitiveMarketReferenceResult.InsufficientSellerDiversity(
                InsufficientSellerDiversityEvidence(
                    competitiveAssessment.organizationId,
                    competitiveAssessment.scenarioId,
                    competitiveAssessment.ownObservationId,
                    competitiveAssessment.marketplace,
                    competitiveAssessment.competitorObservations.size,
                    sellerReferences.size,
                    policy.minimumDistinctSellers,
                    competitiveAssessment.policyVersion,
                    policy.version,
                    competitiveAssessment.evaluatedAt
                )
            )
        }

        val lowerMedian = sellerReferences[(sellerReferences.size - 1) / 2].grossPrice
        val upperMedian = sellerReferences[sellerReferences.size / 2].grossPrice
        val observations = competitiveAssessment.competitorObservations
        return CompetitiveMarketReferenceResult.Referenced(
            CompetitiveMarketReferenceAssessment(
                competitiveAssessment.organizationId,
                competitiveAssessment.scenarioId,
                competitiveAssessment.ownObservationId,
                competitiveAssessment.marketplace,
                competitiveAssessment.currency,
                competitiveAssessment.priceQuantum,
                sellerReferences,
                observations.size,
                sellerReferences.size,
                lowerMedian,
                upperMedian,
                marketReferenceQuality(observations),
                competitiveAssessment.policyVersion,
                competitiveAssessment.maximumObservationAge,
                policy.version,
                policy.minimumDistinctSellers,
                observations.minOf { it.occurredAt },
                observations.maxOf { it.occurredAt },
                competitiveAssessment.evaluatedAt
            )
        )
    }
}

private fun sellerReference(
    sellerKey: CompetitorSellerKey,
    observations: Collection<AvailableMatchedCompetitorPrice>
): SellerCompetitivePriceReference {
    val lowestPrice = observations.minBy { it.grossPrice.amount }.grossPrice
    val supportingIds = observations
        .filter { it.grossPrice == lowestPrice }
        .map { it.observationId }
        .sortedWith(MARKET_REFERENCE_OBSERVATION_ID_COMPARATOR)
    return SellerCompetitivePriceReference(sellerKey, lowestPrice, supportingIds)
}

private fun marketReferenceQuality(
    observations: Collection<AvailableMatchedCompetitorPrice>
): EconomicEvidenceQuality = if (
    observations.all {
        it.priceEvidenceQuality == EconomicEvidenceQuality.CONFIRMED &&
            it.matchEvidenceQuality == EconomicEvidenceQuality.CONFIRMED
    }
) EconomicEvidenceQuality.CONFIRMED else EconomicEvidenceQuality.ESTIMATED

private val SELLER_REFERENCE_COMPARATOR =
    compareBy<SellerCompetitivePriceReference> { it.grossPrice.amount }
        .thenBy { it.sellerKey.value }

private val MARKET_REFERENCE_OBSERVATION_ID_COMPARATOR =
    Comparator<CompetitorPriceObservationId> { left, right ->
        compareMarketReferenceUuidUnsigned(left.value, right.value)
    }

private fun compareMarketReferenceUuidUnsigned(left: UUID, right: UUID): Int {
    val most = java.lang.Long.compareUnsigned(left.mostSignificantBits, right.mostSignificantBits)
    return if (most != 0) most else java.lang.Long.compareUnsigned(
        left.leastSignificantBits,
        right.leastSignificantBits
    )
}

private val MARKET_REFERENCE_POLICY_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9./-]{0,99}")
private const val MINIMUM_DISTINCT_SELLERS = 2
private const val MAXIMUM_DISTINCT_SELLERS = 100
