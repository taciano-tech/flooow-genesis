package io.flooow.marketplace.operations.economics.pricing

import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.organization.OrganizationId
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.UUID

class PricingProductCostEvidenceId private constructor(internal val value: UUID) {
    override fun equals(other: Any?): Boolean =
        other is PricingProductCostEvidenceId && value == other.value

    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String) = PricingProductCostEvidenceId(parseCostBasisUuid(value))
        fun of(value: UUID) = PricingProductCostEvidenceId(value)
    }
}

private fun parseCostBasisUuid(value: String): UUID {
    require(value.length == 36 && value == value.lowercase()) {
        "Identifier must be a canonical lowercase UUID"
    }
    val parsed = runCatching { UUID.fromString(value) }.getOrElse {
        throw IllegalArgumentException("Identifier must be a canonical lowercase UUID")
    }
    require(parsed.toString() == value) { "Identifier must be a canonical lowercase UUID" }
    return parsed
}

data class PricingCostUnitKey(val value: String) {
    init {
        require(COST_UNIT_KEY_PATTERN.matches(value)) {
            "Pricing cost unit key must use 1-100 lowercase letters, digits, dots, or hyphens"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class PricingCostAssumptionVersion(val value: String) {
    init {
        require(COST_VERSION_PATTERN.matches(value)) {
            "Pricing cost assumption version is invalid"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

enum class PricingProductCostBasis {
    HISTORICAL_ACQUISITION,
    CURRENT_REPLACEMENT,
    FORWARD_REPLACEMENT
}

data class PricingProductCostEvidence(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val marketplace: MarketplaceKey,
    val evidenceId: PricingProductCostEvidenceId,
    val unitKey: PricingCostUnitKey,
    val basis: PricingProductCostBasis,
    val unitCost: MarketplaceMoney,
    val source: EconomicSource,
    val occurredAt: Instant,
    val applicableAt: Instant,
    val quality: EconomicEvidenceQuality,
    val assumptionVersion: PricingCostAssumptionVersion
) {
    init {
        require(unitCost.amount.signum() >= 0) { "Pricing product unit cost must not be negative" }
        require(occurredAt.nano % 1_000 == 0) {
            "Pricing product cost source time must use microsecond precision"
        }
        require(applicableAt.nano % 1_000 == 0) {
            "Pricing product cost applicability time must use microsecond precision"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class PricingCostBasisPolicyVersion(val value: String) {
    init {
        require(COST_VERSION_PATTERN.matches(value)) {
            "Pricing cost basis policy version is invalid"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class PricingCostBasisPolicy(
    val version: PricingCostBasisPolicyVersion,
    val maximumCurrentReplacementAge: Duration,
    val maximumForwardHorizon: Duration
) {
    init {
        require(isValidCostDuration(maximumCurrentReplacementAge)) {
            "Maximum current replacement age must be positive and at most 730 days"
        }
        require(isValidCostDuration(maximumForwardHorizon)) {
            "Maximum forward horizon must be positive and at most 730 days"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

class MissingCostBasisEvidence internal constructor(missingBases: Collection<PricingProductCostBasis>) {
    val missingBases: List<PricingProductCostBasis> = Collections.unmodifiableList(
        missingBases.distinct().sortedBy { it.ordinal }
    )

    init {
        require(this.missingBases.isNotEmpty()) { "Missing cost basis evidence must not be empty" }
    }

    override fun equals(other: Any?): Boolean =
        other is MissingCostBasisEvidence && missingBases == other.missingBases

    override fun hashCode(): Int = missingBases.hashCode()
    override fun toString(): String = "[REDACTED]"
}

@ConsistentCopyVisibility
data class PricingProductCostBasisAssessment internal constructor(
    val organizationId: OrganizationId,
    val scenarioId: NetBackPricingScenarioId,
    val marketplace: MarketplaceKey,
    val currency: MarketplaceCurrency,
    val unitKey: PricingCostUnitKey,
    val historicalEvidence: PricingProductCostEvidence,
    val currentReplacementEvidence: PricingProductCostEvidence,
    val forwardReplacementEvidence: PricingProductCostEvidence,
    val currentChangeFromHistorical: MarketplaceMoney,
    val forwardChangeFromCurrent: MarketplaceMoney,
    val forwardChangeFromHistorical: MarketplaceMoney,
    val quality: EconomicEvidenceQuality,
    val policyVersion: PricingCostBasisPolicyVersion,
    val maximumCurrentReplacementAge: Duration,
    val maximumForwardHorizon: Duration,
    val evaluatedAt: Instant
) {
    init {
        val evidences = listOf(historicalEvidence, currentReplacementEvidence, forwardReplacementEvidence)
        require(historicalEvidence.basis == PricingProductCostBasis.HISTORICAL_ACQUISITION)
        require(currentReplacementEvidence.basis == PricingProductCostBasis.CURRENT_REPLACEMENT)
        require(forwardReplacementEvidence.basis == PricingProductCostBasis.FORWARD_REPLACEMENT)
        require(evidences.all {
            it.organizationId == organizationId &&
                it.scenarioId == scenarioId &&
                it.marketplace == marketplace &&
                it.unitKey == unitKey &&
                it.unitCost.currency == currency
        }) { "Cost basis evidence is inconsistent with assessment boundary" }
        require(!hasDuplicateCostEvidence(evidences)) {
            "Cost basis evidence identities and source facts must be unique"
        }
        require(isValidCostDuration(maximumCurrentReplacementAge))
        require(isValidCostDuration(maximumForwardHorizon))
        require(evaluatedAt.nano % 1_000 == 0) {
            "Pricing product cost evaluation time must use microsecond precision"
        }
        require(isCurrentWithinWindow(currentReplacementEvidence, evaluatedAt, maximumCurrentReplacementAge))
        require(!historicalEvidence.applicableAt.isAfter(currentReplacementEvidence.applicableAt))
        require(isForwardWithinWindow(forwardReplacementEvidence, evaluatedAt, maximumForwardHorizon))
        require(evidences.all { !it.occurredAt.isAfter(evaluatedAt) })
        require(currentChangeFromHistorical == currentReplacementEvidence.unitCost - historicalEvidence.unitCost)
        require(forwardChangeFromCurrent == forwardReplacementEvidence.unitCost - currentReplacementEvidence.unitCost)
        require(forwardChangeFromHistorical == forwardReplacementEvidence.unitCost - historicalEvidence.unitCost)
        require(quality == aggregateCostQuality(evidences))
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface PricingProductCostBasisResult {
    data class Assessed(val assessment: PricingProductCostBasisAssessment) : PricingProductCostBasisResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class MissingCostBasis(val evidence: MissingCostBasisEvidence) : PricingProductCostBasisResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object DuplicateCostBasis : PricingProductCostBasisResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object DuplicateEvidence : PricingProductCostBasisResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object OwnershipMismatch : PricingProductCostBasisResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object MarketplaceMismatch : PricingProductCostBasisResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object CurrencyMismatch : PricingProductCostBasisResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object UnitMismatch : PricingProductCostBasisResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SourceTimeViolation : PricingProductCostBasisResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object ApplicabilityViolation : PricingProductCostBasisResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplacePricingProductCostBasis {
    fun evaluate(
        evidences: Collection<PricingProductCostEvidence>,
        policy: PricingCostBasisPolicy,
        evaluatedAt: Instant
    ): PricingProductCostBasisResult {
        require(evaluatedAt.nano % 1_000 == 0) {
            "Pricing product cost evaluation time must use microsecond precision"
        }
        if (hasDuplicateCostEvidence(evidences)) {
            return PricingProductCostBasisResult.DuplicateEvidence
        }
        if (evidences.groupingBy { it.basis }.eachCount().any { it.value > 1 }) {
            return PricingProductCostBasisResult.DuplicateCostBasis
        }
        val missing = PricingProductCostBasis.entries.filter { basis -> evidences.none { it.basis == basis } }
        if (missing.isNotEmpty()) {
            return PricingProductCostBasisResult.MissingCostBasis(MissingCostBasisEvidence(missing))
        }

        val historical = evidences.single { it.basis == PricingProductCostBasis.HISTORICAL_ACQUISITION }
        val current = evidences.single { it.basis == PricingProductCostBasis.CURRENT_REPLACEMENT }
        val forward = evidences.single { it.basis == PricingProductCostBasis.FORWARD_REPLACEMENT }
        if (evidences.any {
                it.organizationId != historical.organizationId || it.scenarioId != historical.scenarioId
            }
        ) return PricingProductCostBasisResult.OwnershipMismatch
        if (evidences.any { it.marketplace != historical.marketplace }) {
            return PricingProductCostBasisResult.MarketplaceMismatch
        }
        if (evidences.any { it.unitCost.currency != historical.unitCost.currency }) {
            return PricingProductCostBasisResult.CurrencyMismatch
        }
        if (evidences.any { it.unitKey != historical.unitKey }) {
            return PricingProductCostBasisResult.UnitMismatch
        }
        if (evidences.any { it.occurredAt.isAfter(evaluatedAt) } ||
            !isCurrentSourceWithinWindow(current, evaluatedAt, policy.maximumCurrentReplacementAge)
        ) return PricingProductCostBasisResult.SourceTimeViolation
        if (!isCurrentApplicabilityWithinWindow(current, evaluatedAt, policy.maximumCurrentReplacementAge) ||
            historical.applicableAt.isAfter(current.applicableAt) ||
            !isForwardWithinWindow(forward, evaluatedAt, policy.maximumForwardHorizon)
        ) return PricingProductCostBasisResult.ApplicabilityViolation

        val all = listOf(historical, current, forward)
        return PricingProductCostBasisResult.Assessed(
            PricingProductCostBasisAssessment(
                historical.organizationId,
                historical.scenarioId,
                historical.marketplace,
                historical.unitCost.currency,
                historical.unitKey,
                historical,
                current,
                forward,
                current.unitCost - historical.unitCost,
                forward.unitCost - current.unitCost,
                forward.unitCost - historical.unitCost,
                aggregateCostQuality(all),
                policy.version,
                policy.maximumCurrentReplacementAge,
                policy.maximumForwardHorizon,
                evaluatedAt
            )
        )
    }
}

private fun hasDuplicateCostEvidence(evidences: Collection<PricingProductCostEvidence>): Boolean {
    if (evidences.map { it.evidenceId }.toSet().size != evidences.size) return true
    val facts = evidences.map {
        CostSourceFact(it.basis, it.source.kind, it.source.systemKey, it.source.externalReference)
    }
    return facts.toSet().size != facts.size
}

private data class CostSourceFact(
    val basis: PricingProductCostBasis,
    val kind: io.flooow.marketplace.operations.economics.EconomicSourceKind,
    val systemKey: io.flooow.marketplace.operations.economics.EconomicSourceSystemKey,
    val externalReference: EconomicExternalReferenceState
)

private fun aggregateCostQuality(evidences: Collection<PricingProductCostEvidence>) =
    if (evidences.all { it.quality == EconomicEvidenceQuality.CONFIRMED }) {
        EconomicEvidenceQuality.CONFIRMED
    } else {
        EconomicEvidenceQuality.ESTIMATED
    }

private fun isCurrentWithinWindow(
    evidence: PricingProductCostEvidence,
    evaluatedAt: Instant,
    maximumAge: Duration
) = isCurrentSourceWithinWindow(evidence, evaluatedAt, maximumAge) &&
    isCurrentApplicabilityWithinWindow(evidence, evaluatedAt, maximumAge)

private fun isCurrentSourceWithinWindow(
    evidence: PricingProductCostEvidence,
    evaluatedAt: Instant,
    maximumAge: Duration
) = isInsideInclusivePastWindow(evidence.occurredAt, evaluatedAt, maximumAge)

private fun isCurrentApplicabilityWithinWindow(
    evidence: PricingProductCostEvidence,
    evaluatedAt: Instant,
    maximumAge: Duration
) = isInsideInclusivePastWindow(evidence.applicableAt, evaluatedAt, maximumAge)

private fun isInsideInclusivePastWindow(value: Instant, evaluatedAt: Instant, maximumAge: Duration) =
    runCatching {
        !value.isAfter(evaluatedAt) && !value.isBefore(evaluatedAt.minus(maximumAge))
    }.getOrDefault(false)

private fun isForwardWithinWindow(
    evidence: PricingProductCostEvidence,
    evaluatedAt: Instant,
    maximumHorizon: Duration
) = runCatching {
    evidence.applicableAt.isAfter(evaluatedAt) &&
        !evidence.applicableAt.isAfter(evaluatedAt.plus(maximumHorizon))
}.getOrDefault(false)

private fun isValidCostDuration(value: Duration) =
    !value.isZero &&
        !value.isNegative &&
        value <= MAXIMUM_COST_BASIS_DURATION &&
        value.nano % 1_000 == 0

private val COST_UNIT_KEY_PATTERN = Regex("[a-z0-9][a-z0-9.-]{0,99}")
private val COST_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9./-]{0,99}")
private val MAXIMUM_COST_BASIS_DURATION: Duration = Duration.ofDays(730)
