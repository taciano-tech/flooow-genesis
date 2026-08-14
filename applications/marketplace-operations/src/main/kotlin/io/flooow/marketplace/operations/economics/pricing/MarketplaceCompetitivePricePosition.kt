package io.flooow.marketplace.operations.economics.pricing

import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import io.flooow.marketplace.operations.economics.EconomicExternalReference
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthQuality
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.organization.OrganizationId
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.UUID

class CompetitorPriceObservationId private constructor(internal val value: UUID) {
    override fun equals(other: Any?): Boolean =
        other is CompetitorPriceObservationId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String) = CompetitorPriceObservationId(parseCompetitorUuid(value))
        fun of(value: UUID) = CompetitorPriceObservationId(value)
    }
}

class CompetitiveProductMatchId private constructor(internal val value: UUID) {
    override fun equals(other: Any?): Boolean =
        other is CompetitiveProductMatchId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String) = CompetitiveProductMatchId(parseCompetitorUuid(value))
        fun of(value: UUID) = CompetitiveProductMatchId(value)
    }
}

private fun parseCompetitorUuid(value: String): UUID {
    require(value.length == 36 && value == value.lowercase()) {
        "Identifier must be a canonical lowercase UUID"
    }
    val parsed = runCatching { UUID.fromString(value) }.getOrElse {
        throw IllegalArgumentException("Identifier must be a canonical lowercase UUID")
    }
    require(parsed.toString() == value) { "Identifier must be a canonical lowercase UUID" }
    return parsed
}

class CompetitorSellerKey private constructor(internal val value: String) {
    init {
        require(SELLER_KEY_PATTERN.matches(value)) {
            "Competitor seller key must use 1-100 lowercase letters, digits, dots, or hyphens"
        }
    }

    override fun equals(other: Any?): Boolean = other is CompetitorSellerKey && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String) = CompetitorSellerKey(value)
    }
}

data class AvailableMatchedCompetitorPrice(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val marketplace: MarketplaceKey,
    val observationId: CompetitorPriceObservationId,
    val productMatchId: CompetitiveProductMatchId,
    val sellerKey: CompetitorSellerKey,
    val grossPrice: MarketplaceMoney,
    val source: EconomicSource,
    val occurredAt: Instant,
    val priceEvidenceQuality: EconomicEvidenceQuality,
    val matchEvidenceQuality: EconomicEvidenceQuality
) {
    init {
        require(grossPrice.amount.signum() >= 0) {
            "Available competitor gross price must not be negative"
        }
        require(
            source.kind == EconomicSourceKind.MARKETPLACE &&
                source.externalReference is EconomicExternalReferenceState.Present
        ) { "Available competitor price requires marketplace source reference" }
        require(occurredAt.nano % 1_000 == 0) {
            "Available competitor price time must use microsecond precision"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class CompetitivePriceComparisonPolicyVersion(val value: String) {
    init {
        require(COMPARISON_POLICY_VERSION_PATTERN.matches(value)) {
            "Competitive price comparison policy version is invalid"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class CompetitivePriceComparisonPolicy(
    val version: CompetitivePriceComparisonPolicyVersion,
    val maximumObservationAge: Duration
) {
    init {
        require(
            !maximumObservationAge.isZero &&
                !maximumObservationAge.isNegative &&
                maximumObservationAge <= MAXIMUM_COMPARISON_AGE
        ) { "Competitive price maximum observation age must be positive and at most 31 days" }
        require(maximumObservationAge.nano % 1_000 == 0) {
            "Competitive price maximum observation age must use microsecond precision"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

enum class CompetitivePricePosition {
    BELOW_LOWEST_COMPETITOR,
    TIED_LOWEST_COMPETITOR,
    ABOVE_LOWEST_COMPETITOR
}

@ConsistentCopyVisibility
data class NoComparableOfferEvidence internal constructor(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val ownObservationId: EconomicPriceObservationId,
    val marketplace: MarketplaceKey,
    val policyVersion: CompetitivePriceComparisonPolicyVersion,
    val evaluatedAt: Instant
) {
    override fun toString(): String = "[REDACTED]"
}

class CompetitivePricePositionAssessment internal constructor(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val ownObservationId: EconomicPriceObservationId,
    val marketplace: MarketplaceKey,
    val currency: MarketplaceCurrency,
    val priceQuantum: MarketplaceMoney,
    val ownObservedGrossPrice: MarketplaceMoney,
    val ownEconomicPosition: EconomicPricePosition,
    val ownEconomicQuality: MarketplaceEconomicTruthQuality,
    competitorObservations: Collection<AvailableMatchedCompetitorPrice>,
    val lowestCompetitorPrice: MarketplaceMoney,
    lowestCompetitorObservationIds: Collection<CompetitorPriceObservationId>,
    val gapToLowestCompetitor: MarketplaceMoney,
    val position: CompetitivePricePosition,
    val quality: MarketplaceEconomicTruthQuality,
    val policyVersion: CompetitivePriceComparisonPolicyVersion,
    val maximumObservationAge: Duration,
    val evaluatedAt: Instant
) {
    val competitorObservations: List<AvailableMatchedCompetitorPrice> =
        Collections.unmodifiableList(competitorObservations.toList())
    val lowestCompetitorObservationIds: List<CompetitorPriceObservationId> =
        Collections.unmodifiableList(lowestCompetitorObservationIds.toList())

    init {
        require(this.competitorObservations.isNotEmpty()) {
            "Competitive price assessment requires competitor observations"
        }
        require(priceQuantum.currency == currency && priceQuantum.amount.signum() > 0) {
            "Competitive price quantum must be positive and use assessment currency"
        }
        require(ownObservedGrossPrice.currency == currency) {
            "Own competitive price currency must match assessment currency"
        }
        require(
            !maximumObservationAge.isZero &&
                !maximumObservationAge.isNegative &&
                maximumObservationAge <= MAXIMUM_COMPARISON_AGE &&
                maximumObservationAge.nano % 1_000 == 0
        ) { "Competitive price assessment maximum age is invalid" }
        require(evaluatedAt.nano % 1_000 == 0) {
            "Competitive price assessment time must use microsecond precision"
        }
        require(this.competitorObservations.all {
            it.organizationId == organizationId &&
                it.scenarioId == scenarioId &&
                it.marketplace == marketplace &&
                it.grossPrice.currency == currency &&
                it.grossPrice.amount.remainder(priceQuantum.amount).signum() == 0 &&
                isWithinWindow(it.occurredAt, evaluatedAt, maximumObservationAge)
        }) { "Competitor evidence is inconsistent with assessment boundary" }
        require(this.competitorObservations == this.competitorObservations.sortedWith(OBSERVATION_COMPARATOR)) {
            "Competitor observations must use deterministic identity order"
        }
        require(!hasDuplicateEvidence(this.competitorObservations)) {
            "Competitor evidence must be unique"
        }
        val calculatedLowest = this.competitorObservations.minBy { it.grossPrice.amount }.grossPrice
        require(lowestCompetitorPrice == calculatedLowest) {
            "Lowest competitor price is inconsistent"
        }
        val calculatedLowestIds = this.competitorObservations
            .filter { it.grossPrice == calculatedLowest }
            .map { it.observationId }
            .sortedWith(OBSERVATION_ID_COMPARATOR)
        require(this.lowestCompetitorObservationIds == calculatedLowestIds) {
            "Lowest competitor observations are inconsistent"
        }
        require(gapToLowestCompetitor == ownObservedGrossPrice - lowestCompetitorPrice) {
            "Competitive price gap is inconsistent"
        }
        require(position == classifyCompetitivePosition(ownObservedGrossPrice, lowestCompetitorPrice)) {
            "Competitive price position is inconsistent"
        }
        require(quality == comparisonQuality(this.competitorObservations, ownEconomicQuality)) {
            "Competitive price quality is inconsistent"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is CompetitivePricePositionAssessment &&
            organizationId == other.organizationId &&
            scenarioId == other.scenarioId &&
            ownObservationId == other.ownObservationId &&
            marketplace == other.marketplace &&
            currency == other.currency &&
            priceQuantum == other.priceQuantum &&
            ownObservedGrossPrice == other.ownObservedGrossPrice &&
            ownEconomicPosition == other.ownEconomicPosition &&
            ownEconomicQuality == other.ownEconomicQuality &&
            competitorObservations == other.competitorObservations &&
            lowestCompetitorPrice == other.lowestCompetitorPrice &&
            lowestCompetitorObservationIds == other.lowestCompetitorObservationIds &&
            gapToLowestCompetitor == other.gapToLowestCompetitor &&
            position == other.position &&
            quality == other.quality &&
            policyVersion == other.policyVersion &&
            maximumObservationAge == other.maximumObservationAge &&
            evaluatedAt == other.evaluatedAt

    override fun hashCode(): Int {
        var result = organizationId.hashCode()
        result = 31 * result + scenarioId.hashCode()
        result = 31 * result + ownObservationId.hashCode()
        result = 31 * result + marketplace.hashCode()
        result = 31 * result + currency.hashCode()
        result = 31 * result + priceQuantum.hashCode()
        result = 31 * result + ownObservedGrossPrice.hashCode()
        result = 31 * result + ownEconomicPosition.hashCode()
        result = 31 * result + ownEconomicQuality.hashCode()
        result = 31 * result + competitorObservations.hashCode()
        result = 31 * result + lowestCompetitorPrice.hashCode()
        result = 31 * result + lowestCompetitorObservationIds.hashCode()
        result = 31 * result + gapToLowestCompetitor.hashCode()
        result = 31 * result + position.hashCode()
        result = 31 * result + quality.hashCode()
        result = 31 * result + policyVersion.hashCode()
        result = 31 * result + maximumObservationAge.hashCode()
        result = 31 * result + evaluatedAt.hashCode()
        return result
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface CompetitivePricePositionResult {
    data class Compared(val assessment: CompetitivePricePositionAssessment) :
        CompetitivePricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data class NoComparableOffers(val evidence: NoComparableOfferEvidence) :
        CompetitivePricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object OwnObservationOutsideWindow : CompetitivePricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object CompetitorObservationOutsideWindow : CompetitivePricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object OwnershipMismatch : CompetitivePricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object MarketplaceMismatch : CompetitivePricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object CurrencyMismatch : CompetitivePricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object PriceQuantumMismatch : CompetitivePricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object DuplicateEvidence : CompetitivePricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceCompetitivePricePosition {
    fun evaluate(
        ownAssessment: EconomicPricePositionAssessment,
        competitorObservations: Collection<AvailableMatchedCompetitorPrice>,
        policy: CompetitivePriceComparisonPolicy,
        evaluatedAt: Instant
    ): CompetitivePricePositionResult {
        require(evaluatedAt.nano % 1_000 == 0) {
            "Competitive price evaluation time must use microsecond precision"
        }
        if (!isWithinWindow(ownAssessment.observedAt, evaluatedAt, policy.maximumObservationAge)) {
            return CompetitivePricePositionResult.OwnObservationOutsideWindow
        }
        if (competitorObservations.any {
                !isWithinWindow(it.occurredAt, evaluatedAt, policy.maximumObservationAge)
            }
        ) {
            return CompetitivePricePositionResult.CompetitorObservationOutsideWindow
        }
        if (competitorObservations.any {
                it.organizationId != ownAssessment.organizationId ||
                    it.scenarioId != ownAssessment.scenarioId
            }
        ) {
            return CompetitivePricePositionResult.OwnershipMismatch
        }
        if (competitorObservations.any { it.marketplace != ownAssessment.marketplace }) {
            return CompetitivePricePositionResult.MarketplaceMismatch
        }
        if (competitorObservations.any { it.grossPrice.currency != ownAssessment.currency }) {
            return CompetitivePricePositionResult.CurrencyMismatch
        }
        if (competitorObservations.any {
                it.grossPrice.amount.remainder(ownAssessment.priceQuantum.amount).signum() != 0
            }
        ) {
            return CompetitivePricePositionResult.PriceQuantumMismatch
        }
        if (hasDuplicateEvidence(competitorObservations)) {
            return CompetitivePricePositionResult.DuplicateEvidence
        }
        if (competitorObservations.isEmpty()) {
            return CompetitivePricePositionResult.NoComparableOffers(
                NoComparableOfferEvidence(
                    ownAssessment.organizationId,
                    ownAssessment.scenarioId,
                    ownAssessment.observationId,
                    ownAssessment.marketplace,
                    policy.version,
                    evaluatedAt
                )
            )
        }

        val sorted = competitorObservations.sortedWith(OBSERVATION_COMPARATOR)
        val lowest = sorted.minBy { it.grossPrice.amount }.grossPrice
        val lowestIds = sorted.filter { it.grossPrice == lowest }.map { it.observationId }
        val quality = comparisonQuality(sorted, ownAssessment.quality)
        return CompetitivePricePositionResult.Compared(
            CompetitivePricePositionAssessment(
                ownAssessment.organizationId,
                ownAssessment.scenarioId,
                ownAssessment.observationId,
                ownAssessment.marketplace,
                ownAssessment.currency,
                ownAssessment.priceQuantum,
                ownAssessment.observedGrossPrice,
                ownAssessment.position,
                ownAssessment.quality,
                sorted,
                lowest,
                lowestIds,
                ownAssessment.observedGrossPrice - lowest,
                classifyCompetitivePosition(ownAssessment.observedGrossPrice, lowest),
                quality,
                policy.version,
                policy.maximumObservationAge,
                evaluatedAt
            )
        )
    }
}

private fun comparisonQuality(
    observations: Collection<AvailableMatchedCompetitorPrice>,
    qualityOfOwn: MarketplaceEconomicTruthQuality
): MarketplaceEconomicTruthQuality {
    val competitorConfirmed = observations.all {
        it.priceEvidenceQuality == EconomicEvidenceQuality.CONFIRMED &&
            it.matchEvidenceQuality == EconomicEvidenceQuality.CONFIRMED
    }
    return if (
        qualityOfOwn == MarketplaceEconomicTruthQuality.CONFIRMED && competitorConfirmed
    ) MarketplaceEconomicTruthQuality.CONFIRMED else MarketplaceEconomicTruthQuality.ESTIMATED
}

private fun classifyCompetitivePosition(
    ownPrice: MarketplaceMoney,
    lowestCompetitorPrice: MarketplaceMoney
): CompetitivePricePosition = when {
    ownPrice.amount < lowestCompetitorPrice.amount ->
        CompetitivePricePosition.BELOW_LOWEST_COMPETITOR
    ownPrice.amount.compareTo(lowestCompetitorPrice.amount) == 0 ->
        CompetitivePricePosition.TIED_LOWEST_COMPETITOR
    else -> CompetitivePricePosition.ABOVE_LOWEST_COMPETITOR
}

private fun isWithinWindow(observedAt: Instant, evaluatedAt: Instant, maximumAge: Duration): Boolean =
    runCatching {
        !observedAt.isAfter(evaluatedAt) && !observedAt.isBefore(evaluatedAt.minus(maximumAge))
    }.getOrDefault(false)

private fun hasDuplicateEvidence(observations: Collection<AvailableMatchedCompetitorPrice>): Boolean {
    if (observations.map { it.observationId }.toSet().size != observations.size) return true
    if (observations.map { it.productMatchId }.toSet().size != observations.size) return true
    val facts = observations.map {
        val reference = (it.source.externalReference as EconomicExternalReferenceState.Present).reference
        CompetitorSourceFact(it.sellerKey, it.source.systemKey, reference)
    }
    return facts.toSet().size != facts.size
}

private data class CompetitorSourceFact(
    val sellerKey: CompetitorSellerKey,
    val systemKey: EconomicSourceSystemKey,
    val reference: EconomicExternalReference
)

private val OBSERVATION_ID_COMPARATOR = Comparator<CompetitorPriceObservationId> { left, right ->
    compareUuidUnsigned(left.value, right.value)
}
private val OBSERVATION_COMPARATOR = Comparator<AvailableMatchedCompetitorPrice> { left, right ->
    OBSERVATION_ID_COMPARATOR.compare(left.observationId, right.observationId)
}

private fun compareUuidUnsigned(left: UUID, right: UUID): Int {
    val most = java.lang.Long.compareUnsigned(left.mostSignificantBits, right.mostSignificantBits)
    return if (most != 0) most else java.lang.Long.compareUnsigned(
        left.leastSignificantBits,
        right.leastSignificantBits
    )
}

private val SELLER_KEY_PATTERN = Regex("[a-z0-9][a-z0-9.-]{0,99}")
private val COMPARISON_POLICY_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9./-]{0,99}")
private val MAXIMUM_COMPARISON_AGE: Duration = Duration.ofDays(31)
