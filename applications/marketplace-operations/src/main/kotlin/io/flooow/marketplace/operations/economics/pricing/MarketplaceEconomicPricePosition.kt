package io.flooow.marketplace.operations.economics.pricing

import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthQuality
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.organization.OrganizationId
import java.time.Instant
import java.util.UUID

class EconomicPriceObservationId private constructor(internal val value: UUID) {
    override fun equals(other: Any?): Boolean = other is EconomicPriceObservationId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String) = EconomicPriceObservationId(parseObservationUuid(value))
        fun of(value: UUID) = EconomicPriceObservationId(value)
    }
}

private fun parseObservationUuid(value: String): UUID {
    require(value.length == 36 && value == value.lowercase()) {
        "Identifier must be a canonical lowercase UUID"
    }
    val parsed = runCatching { UUID.fromString(value) }.getOrElse {
        throw IllegalArgumentException("Identifier must be a canonical lowercase UUID")
    }
    require(parsed.toString() == value) { "Identifier must be a canonical lowercase UUID" }
    return parsed
}

data class ObservedMarketplacePrice(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val id: EconomicPriceObservationId,
    val grossPrice: MarketplaceMoney,
    val source: EconomicSource,
    val occurredAt: Instant,
    val evidenceQuality: EconomicEvidenceQuality
) {
    init {
        require(grossPrice.amount.signum() >= 0) {
            "Observed marketplace price must not be negative"
        }
        require(occurredAt.nano % 1_000 == 0) {
            "Observed marketplace price time must use microsecond precision"
        }
    }
    override fun toString(): String = "[REDACTED]"
}

enum class EconomicPricePosition {
    BELOW_ABSOLUTE_FLOOR,
    BELOW_ECONOMIC_FLOOR,
    AT_ECONOMIC_FLOOR,
    ABOVE_ECONOMIC_FLOOR
}

@ConsistentCopyVisibility
data class EconomicPricePositionAssessment internal constructor(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val observationId: EconomicPriceObservationId,
    val marketplace: MarketplaceKey,
    val currency: MarketplaceCurrency,
    val observedGrossPrice: MarketplaceMoney,
    val absoluteFloor: MarketplaceMoney,
    val economicFloor: MarketplaceMoney,
    val absoluteFloorGap: MarketplaceMoney,
    val economicFloorGap: MarketplaceMoney,
    val position: EconomicPricePosition,
    val quality: MarketplaceEconomicTruthQuality,
    val floorNormalizationPolicyVersion: NetBackNormalizationPolicyVersion,
    val floorCalculationPolicyVersion: NetBackCalculationPolicyVersion,
    val source: EconomicSource,
    val observedAt: Instant
) {
    init {
        require(
            listOf(
                observedGrossPrice,
                absoluteFloor,
                economicFloor,
                absoluteFloorGap,
                economicFloorGap
            ).all { it.currency == currency }
        ) { "Economic price position currencies must match" }
        require(economicFloor.amount >= absoluteFloor.amount) {
            "Economic price floor must not be below absolute floor"
        }
        require(absoluteFloorGap == observedGrossPrice - absoluteFloor) {
            "Absolute floor gap is inconsistent"
        }
        require(economicFloorGap == observedGrossPrice - economicFloor) {
            "Economic floor gap is inconsistent"
        }
        require(position == classify(observedGrossPrice, absoluteFloor, economicFloor)) {
            "Economic price position is inconsistent"
        }
    }
    override fun toString(): String = "[REDACTED]"
}

sealed interface EconomicPricePositionResult {
    data class Assessed(val assessment: EconomicPricePositionAssessment) :
        EconomicPricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object OwnershipMismatch : EconomicPricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object CurrencyMismatch : EconomicPricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
    data object PriceQuantumMismatch : EconomicPricePositionResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceEconomicPricePosition {
    fun evaluate(
        floor: NetBackEconomicFloor,
        observation: ObservedMarketplacePrice
    ): EconomicPricePositionResult {
        if (
            floor.organizationId != observation.organizationId ||
            floor.scenarioId != observation.scenarioId
        ) {
            return EconomicPricePositionResult.OwnershipMismatch
        }
        if (floor.currency != observation.grossPrice.currency) {
            return EconomicPricePositionResult.CurrencyMismatch
        }
        if (observation.grossPrice.amount.remainder(floor.priceQuantum.amount).signum() != 0) {
            return EconomicPricePositionResult.PriceQuantumMismatch
        }

        val quality = if (
            floor.truthQuality == MarketplaceEconomicTruthQuality.CONFIRMED &&
            observation.evidenceQuality == EconomicEvidenceQuality.CONFIRMED
        ) MarketplaceEconomicTruthQuality.CONFIRMED else MarketplaceEconomicTruthQuality.ESTIMATED

        return EconomicPricePositionResult.Assessed(
            EconomicPricePositionAssessment(
                organizationId = floor.organizationId,
                scenarioId = floor.scenarioId,
                observationId = observation.id,
                marketplace = floor.marketplace,
                currency = floor.currency,
                observedGrossPrice = observation.grossPrice,
                absoluteFloor = floor.absoluteFloor,
                economicFloor = floor.economicFloor,
                absoluteFloorGap = observation.grossPrice - floor.absoluteFloor,
                economicFloorGap = observation.grossPrice - floor.economicFloor,
                position = classify(
                    observation.grossPrice,
                    floor.absoluteFloor,
                    floor.economicFloor
                ),
                quality = quality,
                floorNormalizationPolicyVersion = floor.normalizationPolicyVersion,
                floorCalculationPolicyVersion = floor.calculationPolicyVersion,
                source = observation.source,
                observedAt = observation.occurredAt
            )
        )
    }
}

private fun classify(
    observed: MarketplaceMoney,
    absoluteFloor: MarketplaceMoney,
    economicFloor: MarketplaceMoney
): EconomicPricePosition = when {
    observed.amount < absoluteFloor.amount -> EconomicPricePosition.BELOW_ABSOLUTE_FLOOR
    observed.amount < economicFloor.amount -> EconomicPricePosition.BELOW_ECONOMIC_FLOOR
    observed.amount.compareTo(economicFloor.amount) == 0 -> EconomicPricePosition.AT_ECONOMIC_FLOOR
    else -> EconomicPricePosition.ABOVE_ECONOMIC_FLOOR
}
