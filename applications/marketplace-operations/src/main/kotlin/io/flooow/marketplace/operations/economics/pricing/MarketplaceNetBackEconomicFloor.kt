package io.flooow.marketplace.operations.economics.pricing

import io.flooow.marketplace.operations.economics.EconomicComponentCoverage
import io.flooow.marketplace.operations.economics.EconomicComponentType
import io.flooow.marketplace.operations.economics.EconomicDirection
import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthQuality
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Collections
import java.util.EnumMap
import java.util.UUID

class NetBackPricingScenarioId private constructor(internal val value: UUID) {
    override fun equals(other: Any?): Boolean = other is NetBackPricingScenarioId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String) = NetBackPricingScenarioId(parseCanonicalUuid(value))
        fun of(value: UUID) = NetBackPricingScenarioId(value)
    }
}

class NetBackCostComponentId private constructor(internal val value: UUID) {
    override fun equals(other: Any?): Boolean = other is NetBackCostComponentId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String) = NetBackCostComponentId(parseCanonicalUuid(value))
        fun of(value: UUID) = NetBackCostComponentId(value)
    }
}

private fun parseCanonicalUuid(value: String): UUID {
    require(value.length == 36 && value == value.lowercase()) {
        "Identifier must be a canonical lowercase UUID"
    }
    val parsed = runCatching { UUID.fromString(value) }.getOrElse {
        throw IllegalArgumentException("Identifier must be a canonical lowercase UUID")
    }
    require(parsed.toString() == value) { "Identifier must be a canonical lowercase UUID" }
    return parsed
}

data class NetBackNormalizationPolicyVersion(val value: String) {
    init {
        require(POLICY_VERSION_PATTERN.matches(value)) {
            "Net-back normalization policy version is invalid"
        }
    }
    override fun toString(): String = "[REDACTED]"
}

data class NetBackCalculationPolicyVersion(val value: String) {
    init {
        require(POLICY_VERSION_PATTERN.matches(value)) {
            "Net-back calculation policy version is invalid"
        }
    }
    override fun toString(): String = "[REDACTED]"
}

class NetBackRate private constructor(decimalValue: BigDecimal) {
    val decimalValue: BigDecimal = normalize(decimalValue)

    init {
        require(this.decimalValue >= BigDecimal.ZERO && this.decimalValue <= BigDecimal.ONE) {
            "Net-back rate must be between zero and one"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is NetBackRate && decimalValue.compareTo(other.decimalValue) == 0
    override fun hashCode(): Int = decimalValue.stripTrailingZeros().hashCode()
    override fun toString(): String = "[REDACTED]"

    companion object {
        private val INPUT_PATTERN = Regex("(0(\\.[0-9]{1,8})?|1(\\.0{1,8})?)")
        fun parse(value: String): NetBackRate {
            require(INPUT_PATTERN.matches(value)) { "Net-back rate must be canonical decimal text" }
            return NetBackRate(BigDecimal(value))
        }
    }
}

class NetBackSignedRate internal constructor(decimalValue: BigDecimal) {
    val decimalValue: BigDecimal = normalize(decimalValue)
    override fun equals(other: Any?): Boolean =
        other is NetBackSignedRate && decimalValue.compareTo(other.decimalValue) == 0
    override fun hashCode(): Int = decimalValue.stripTrailingZeros().hashCode()
    override fun toString(): String = "[REDACTED]"
}

private fun normalize(value: BigDecimal): BigDecimal {
    require(value.scale() <= 8) { "Net-back rate scale must not exceed eight" }
    return if (value.signum() == 0) BigDecimal.ZERO else value.stripTrailingZeros()
}

sealed interface NetBackCostValue {
    data class FixedAmount(val magnitude: MarketplaceMoney) : NetBackCostValue {
        init {
            require(magnitude.amount.signum() >= 0) { "Net-back fixed amount must not be negative" }
        }
        override fun toString(): String = "[REDACTED]"
    }

    data class RevenueRate(val rate: NetBackRate) : NetBackCostValue {
        override fun toString(): String = "[REDACTED]"
    }
}

data class NetBackCostComponent(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val id: NetBackCostComponentId,
    val economicType: EconomicComponentType,
    val direction: EconomicDirection,
    val value: NetBackCostValue,
    val source: EconomicSource,
    val evidenceQuality: EconomicEvidenceQuality
) {
    init {
        require(economicType != EconomicComponentType.REVENUE) {
            "Revenue is not a net-back cost type"
        }
    }
    override fun toString(): String = "[REDACTED]"
}

sealed interface NetBackContributionTarget {
    data class AbsoluteAmount(val amount: MarketplaceMoney) : NetBackContributionTarget {
        init {
            require(amount.amount.signum() >= 0) { "Net-back contribution target must not be negative" }
        }
        override fun toString(): String = "[REDACTED]"
    }

    data class MarginRate(val rate: NetBackRate) : NetBackContributionTarget {
        init {
            require(rate.decimalValue < BigDecimal.ONE) {
                "Net-back contribution margin must be below one"
            }
        }
        override fun toString(): String = "[REDACTED]"
    }
}

class NetBackPricingProfile(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val marketplace: MarketplaceKey,
    val currency: MarketplaceCurrency,
    val priceQuantum: MarketplaceMoney,
    val normalizationPolicyVersion: NetBackNormalizationPolicyVersion,
    components: Collection<NetBackCostComponent>,
    coverage: Map<EconomicComponentType, EconomicComponentCoverage>,
    val target: NetBackContributionTarget
) {
    val components: List<NetBackCostComponent> = Collections.unmodifiableList(
        components.sortedWith(compareBy(COMPONENT_ID_COMPARATOR) { it.id })
    )
    val coverage: Map<EconomicComponentType, EconomicComponentCoverage> =
        Collections.unmodifiableMap(
            EnumMap<EconomicComponentType, EconomicComponentCoverage>(EconomicComponentType::class.java)
                .apply { putAll(coverage) }
        )

    init {
        require(priceQuantum.currency == currency && priceQuantum.amount.signum() > 0) {
            "Net-back price quantum must be positive and use profile currency"
        }
        require(components.all { it.organizationId == organizationId && it.scenarioId == scenarioId }) {
            "Net-back component must belong to profile ownership"
        }
        require(components.all {
            val value = it.value
            value !is NetBackCostValue.FixedAmount || value.magnitude.currency == currency
        }) { "Net-back fixed amount currency must match profile currency" }
        require(target !is NetBackContributionTarget.AbsoluteAmount || target.amount.currency == currency) {
            "Net-back target currency must match profile currency"
        }
        require(components.map { it.id }.toSet().size == components.size) {
            "Net-back component identifiers must be unique"
        }
        requirePresentSourceFactsUnique(components)
        validateCoverage(components, coverage)
    }

    override fun equals(other: Any?): Boolean =
        other is NetBackPricingProfile && organizationId == other.organizationId &&
            scenarioId == other.scenarioId && marketplace == other.marketplace &&
            currency == other.currency && priceQuantum == other.priceQuantum &&
            normalizationPolicyVersion == other.normalizationPolicyVersion &&
            components == other.components && coverage == other.coverage && target == other.target

    override fun hashCode(): Int {
        var result = organizationId.hashCode()
        result = 31 * result + scenarioId.hashCode()
        result = 31 * result + marketplace.hashCode()
        result = 31 * result + currency.hashCode()
        result = 31 * result + priceQuantum.hashCode()
        result = 31 * result + normalizationPolicyVersion.hashCode()
        result = 31 * result + components.hashCode()
        result = 31 * result + coverage.hashCode()
        result = 31 * result + target.hashCode()
        return result
    }
    override fun toString(): String = "[REDACTED]"
}

private fun requirePresentSourceFactsUnique(components: Collection<NetBackCostComponent>) {
    val keys = components.mapNotNull { component ->
        val state = component.source.externalReference
        if (state is EconomicExternalReferenceState.Present) {
            NetBackSourceFactKey(
                component.source.kind,
                component.source.systemKey,
                state.reference,
                component.economicType,
                if (component.value is NetBackCostValue.FixedAmount) "FIXED_AMOUNT" else "REVENUE_RATE"
            )
        } else null
    }
    require(keys.toSet().size == keys.size) { "Present net-back source facts must be unique" }
}

private fun validateCoverage(
    components: Collection<NetBackCostComponent>,
    coverage: Map<EconomicComponentType, EconomicComponentCoverage>
) {
    require(coverage.keys == NET_BACK_COST_TYPES.toSet()) {
        "Net-back coverage must classify every cost type exactly once"
    }
    NET_BACK_COST_TYPES.forEach { type ->
        val count = components.count { it.economicType == type }
        when (coverage.getValue(type)) {
            EconomicComponentCoverage.COMPLETE,
            EconomicComponentCoverage.PARTIAL -> require(count > 0) {
                "Present net-back coverage requires a component"
            }
            EconomicComponentCoverage.NOT_APPLICABLE,
            EconomicComponentCoverage.MISSING -> require(count == 0) {
                "Absent net-back coverage does not permit components"
            }
        }
    }
}

private data class NetBackSourceFactKey(
    val kind: io.flooow.marketplace.operations.economics.EconomicSourceKind,
    val systemKey: io.flooow.marketplace.operations.economics.EconomicSourceSystemKey,
    val reference: io.flooow.marketplace.operations.economics.EconomicExternalReference,
    val type: EconomicComponentType,
    val valueKind: String
)

enum class NetBackUnachievableReason {
    NON_POSITIVE_ABSOLUTE_DENOMINATOR,
    NON_POSITIVE_ECONOMIC_DENOMINATOR,
    FLOOR_OUT_OF_RANGE
}

sealed interface NetBackCalculationResult {
    data class Complete(val floor: NetBackEconomicFloor) : NetBackCalculationResult {
        override fun toString(): String = "[REDACTED]"
    }
    @ConsistentCopyVisibility
    data class Incomplete internal constructor(
        val missingTypes: List<EconomicComponentType>,
        val partialTypes: List<EconomicComponentType>,
        val suppliedComponents: List<NetBackCostComponent>,
        val normalizationPolicyVersion: NetBackNormalizationPolicyVersion,
        val calculationPolicyVersion: NetBackCalculationPolicyVersion
    ) : NetBackCalculationResult {
        override fun toString(): String = "[REDACTED]"
    }
    @ConsistentCopyVisibility
    data class Unachievable internal constructor(
        val reason: NetBackUnachievableReason,
        val normalizationPolicyVersion: NetBackNormalizationPolicyVersion,
        val calculationPolicyVersion: NetBackCalculationPolicyVersion
    ) : NetBackCalculationResult {
        override fun toString(): String = "[REDACTED]"
    }
}

@ConsistentCopyVisibility
data class NetBackEconomicFloor internal constructor(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val marketplace: MarketplaceKey,
    val currency: MarketplaceCurrency,
    val priceQuantum: MarketplaceMoney,
    val normalizationPolicyVersion: NetBackNormalizationPolicyVersion,
    val calculationPolicyVersion: NetBackCalculationPolicyVersion,
    val target: NetBackContributionTarget,
    val netFixedCost: MarketplaceMoney,
    val netVariableDeductionRate: NetBackSignedRate,
    val absoluteFloor: MarketplaceMoney,
    val economicFloor: MarketplaceMoney,
    val truthQuality: MarketplaceEconomicTruthQuality,
    val components: List<NetBackCostComponent>
) {
    init {
        require(economicFloor.amount >= absoluteFloor.amount) {
            "Net-back economic floor must not be below absolute floor"
        }
    }
    override fun toString(): String = "[REDACTED]"
}

object MarketplaceNetBackEconomicFloor {
    val POLICY_VERSION = NetBackCalculationPolicyVersion("marketplace-net-back-economic-floor/1")

    fun calculate(profile: NetBackPricingProfile): NetBackCalculationResult {
        val missing = NET_BACK_COST_TYPES.filter {
            profile.coverage.getValue(it) == EconomicComponentCoverage.MISSING
        }
        val partial = NET_BACK_COST_TYPES.filter {
            profile.coverage.getValue(it) == EconomicComponentCoverage.PARTIAL
        }
        if (missing.isNotEmpty() || partial.isNotEmpty()) {
            return NetBackCalculationResult.Incomplete(
                missing, partial, profile.components,
                profile.normalizationPolicyVersion, POLICY_VERSION
            )
        }

        val fixed = netFixed(profile.components)
        if (!isMoneyRepresentable(fixed)) return unachievable(profile, NetBackUnachievableReason.FLOOR_OUT_OF_RANGE)
        val variableRate = netRate(profile.components)
        val absoluteDenominator = BigDecimal.ONE.subtract(variableRate)
        if (absoluteDenominator.signum() <= 0) {
            return unachievable(profile, NetBackUnachievableReason.NON_POSITIVE_ABSOLUTE_DENOMINATOR)
        }
        val absoluteFloor = solveFloor(fixed, absoluteDenominator, profile.priceQuantum)
            ?: return unachievable(profile, NetBackUnachievableReason.FLOOR_OUT_OF_RANGE)

        val (economicNumerator, economicDenominator) = when (val target = profile.target) {
            is NetBackContributionTarget.AbsoluteAmount ->
                fixed.add(target.amount.amount) to absoluteDenominator
            is NetBackContributionTarget.MarginRate ->
                fixed to absoluteDenominator.subtract(target.rate.decimalValue)
        }
        if (economicDenominator.signum() <= 0) {
            return unachievable(profile, NetBackUnachievableReason.NON_POSITIVE_ECONOMIC_DENOMINATOR)
        }
        if (!isMoneyRepresentable(economicNumerator)) {
            return unachievable(profile, NetBackUnachievableReason.FLOOR_OUT_OF_RANGE)
        }
        val economicFloor = solveFloor(economicNumerator, economicDenominator, profile.priceQuantum)
            ?: return unachievable(profile, NetBackUnachievableReason.FLOOR_OUT_OF_RANGE)

        return NetBackCalculationResult.Complete(
            NetBackEconomicFloor(
                profile.organizationId, profile.scenarioId, profile.marketplace, profile.currency,
                profile.priceQuantum, profile.normalizationPolicyVersion, POLICY_VERSION,
                profile.target, MarketplaceMoney.calculated(profile.currency, fixed),
                NetBackSignedRate(variableRate), absoluteFloor, economicFloor,
                if (profile.components.any { it.evidenceQuality == EconomicEvidenceQuality.ESTIMATED }) {
                    MarketplaceEconomicTruthQuality.ESTIMATED
                } else MarketplaceEconomicTruthQuality.CONFIRMED,
                profile.components
            )
        )
    }

    private fun netFixed(components: List<NetBackCostComponent>): BigDecimal = components
        .mapNotNull { (it.value as? NetBackCostValue.FixedAmount)?.magnitude?.amount?.let { amount -> it.direction to amount } }
        .fold(BigDecimal.ZERO) { total, (direction, amount) ->
            if (direction == EconomicDirection.DEDUCTION) total.add(amount) else total.subtract(amount)
        }

    private fun netRate(components: List<NetBackCostComponent>): BigDecimal = components
        .mapNotNull { (it.value as? NetBackCostValue.RevenueRate)?.rate?.decimalValue?.let { rate -> it.direction to rate } }
        .fold(BigDecimal.ZERO) { total, (direction, rate) ->
            if (direction == EconomicDirection.DEDUCTION) total.add(rate) else total.subtract(rate)
        }

    private fun solveFloor(
        numerator: BigDecimal,
        denominator: BigDecimal,
        quantum: MarketplaceMoney
    ): MarketplaceMoney? {
        val nonNegative = numerator.max(BigDecimal.ZERO)
        val units = nonNegative.divide(denominator.multiply(quantum.amount), 0, RoundingMode.CEILING)
        val amount = units.multiply(quantum.amount)
        return if (isMoneyRepresentable(amount)) MarketplaceMoney.calculated(quantum.currency, amount) else null
    }

    private fun unachievable(profile: NetBackPricingProfile, reason: NetBackUnachievableReason) =
        NetBackCalculationResult.Unachievable(reason, profile.normalizationPolicyVersion, POLICY_VERSION)
}

private fun isMoneyRepresentable(value: BigDecimal): Boolean =
    value.scale() <= 6 && value.abs() < MONEY_EXCLUSIVE_LIMIT

private val NET_BACK_COST_TYPES = EconomicComponentType.entries.filter { it != EconomicComponentType.REVENUE }
private val COMPONENT_ID_COMPARATOR = Comparator<NetBackCostComponentId> { left, right ->
    val most = java.lang.Long.compareUnsigned(left.value.mostSignificantBits, right.value.mostSignificantBits)
    if (most != 0) most else java.lang.Long.compareUnsigned(left.value.leastSignificantBits, right.value.leastSignificantBits)
}
private val POLICY_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9./-]{0,99}")
private val MONEY_EXCLUSIVE_LIMIT = BigDecimal("1000000000000000000")
