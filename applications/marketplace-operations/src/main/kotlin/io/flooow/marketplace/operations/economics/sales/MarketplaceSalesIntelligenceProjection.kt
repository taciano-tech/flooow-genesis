package io.flooow.marketplace.operations.economics.sales

import io.flooow.marketplace.operations.economics.ContributionMargin
import io.flooow.marketplace.operations.economics.ContributionMarginUndefinedReason
import io.flooow.marketplace.operations.economics.EconomicCalculationPolicyVersion
import io.flooow.marketplace.operations.economics.EconomicComponent
import io.flooow.marketplace.operations.economics.EconomicComponentType
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceEconomicResult
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthCalculationResult
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthQuality
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssemblyNotReadyReason
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssemblyPolicyVersion
import io.flooow.marketplace.operations.economics.evidence.ChangeSequenceCheckpoint
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.time.Instant
import java.util.Collections

sealed interface MarketplaceSalesIntelligenceState {
    val assemblyPolicyVersion: MarketplaceEconomicTruthAssemblyPolicyVersion

    class Unresolved(
        override val assemblyPolicyVersion: MarketplaceEconomicTruthAssemblyPolicyVersion,
        reasons: Set<MarketplaceEconomicTruthAssemblyNotReadyReason>
    ) : MarketplaceSalesIntelligenceState {
        val reasons: Set<MarketplaceEconomicTruthAssemblyNotReadyReason> =
            Collections.unmodifiableSet(reasons.toSet())

        init {
            require(reasons.isNotEmpty()) {
                "Sales Intelligence unresolved state requires at least one assembly reason"
            }
        }

        override fun equals(other: Any?): Boolean =
            other is Unresolved &&
                assemblyPolicyVersion == other.assemblyPolicyVersion &&
                reasons == other.reasons

        override fun hashCode(): Int =
            31 * assemblyPolicyVersion.hashCode() + reasons.hashCode()

        override fun toString(): String = "[REDACTED]"
    }

    data class Calculated(
        override val assemblyPolicyVersion: MarketplaceEconomicTruthAssemblyPolicyVersion,
        val calculationPolicyVersion: EconomicCalculationPolicyVersion,
        val calculationResult: MarketplaceEconomicTruthCalculationResult
    ) : MarketplaceSalesIntelligenceState {
        init {
            require(calculationPolicyVersionOf(calculationResult) == calculationPolicyVersion) {
                "Sales Intelligence calculation policy version is inconsistent"
            }
        }

        override fun toString(): String = "[REDACTED]"
    }
}

data class MarketplaceSalesIntelligenceProjectionRecord(
    val organizationId: OrganizationId,
    val marketplaceOrderId: MarketplaceOrderId,
    val sourceEvidenceVersion: MarketplaceEconomicEvidenceVersion,
    val state: MarketplaceSalesIntelligenceState,
    val lastAppliedChangeSequence: ChangeSequenceCheckpoint,
    val projectedAt: Instant
) {
    init {
        require(lastAppliedChangeSequence > ChangeSequenceCheckpoint.NONE) {
            "Sales Intelligence projection requires a positive change sequence"
        }
        require(projectedAt.nano % 1_000 == 0) {
            "Sales Intelligence projectedAt must use whole-microsecond precision"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class MarketplaceSalesIntelligenceProjectionCursor(
    val projectedAt: Instant,
    val marketplaceOrderId: MarketplaceOrderId
) {
    init {
        require(projectedAt.nano % 1_000 == 0) {
            "Sales Intelligence cursor time must use whole-microsecond precision"
        }
    }

    override fun toString(): String = "[INTERNAL]"
}

data class MarketplaceSalesIntelligenceProjectionPage(
    val records: List<MarketplaceSalesIntelligenceProjectionRecord>,
    val nextCursor: MarketplaceSalesIntelligenceProjectionCursor?
) {
    override fun toString(): String = "[REDACTED]"
}

sealed interface MarketplaceSalesIntelligenceProjectionReadResult<out T> {
    data class Success<T>(val value: T) : MarketplaceSalesIntelligenceProjectionReadResult<T> {
        override fun toString(): String = "[REDACTED]"
    }

    data object IntegrityFailure : MarketplaceSalesIntelligenceProjectionReadResult<Nothing> {
        override fun toString(): String = "[REDACTED]"
    }
}

sealed interface MarketplaceSalesIntelligenceProjectionWriteResult {
    data object Applied : MarketplaceSalesIntelligenceProjectionWriteResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object NoOpAlreadyCurrent : MarketplaceSalesIntelligenceProjectionWriteResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object IntegrityFailure : MarketplaceSalesIntelligenceProjectionWriteResult {
        override fun toString(): String = "[REDACTED]"
    }
}

interface MarketplaceSalesIntelligenceProjection {
    fun currentBySubject(
        organizationId: OrganizationId,
        marketplaceOrderId: MarketplaceOrderId
    ): MarketplaceSalesIntelligenceProjectionReadResult<MarketplaceSalesIntelligenceProjectionRecord?>

    fun materializeIfNewer(
        record: MarketplaceSalesIntelligenceProjectionRecord
    ): MarketplaceSalesIntelligenceProjectionWriteResult

    fun listByOrganization(
        organizationId: OrganizationId,
        cursor: MarketplaceSalesIntelligenceProjectionCursor?,
        limit: Int
    ): MarketplaceSalesIntelligenceProjectionReadResult<MarketplaceSalesIntelligenceProjectionPage>

    fun detailByOrganizationAndSubject(
        organizationId: OrganizationId,
        marketplaceOrderId: MarketplaceOrderId
    ): MarketplaceSalesIntelligenceProjectionReadResult<MarketplaceSalesIntelligenceProjectionRecord?>

    companion object {
        const val MIN_PAGE_SIZE: Int = 1
        const val MAX_PAGE_SIZE: Int = 200

        fun requireValidPageSize(limit: Int): Int {
            require(limit in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
                "Sales Intelligence page size must be from 1 through 200"
            }
            return limit
        }
    }
}

sealed interface MarketplaceSalesIntelligenceCalculationSnapshot {
    fun toCalculationResult(
        organizationId: OrganizationId,
        marketplaceOrderId: MarketplaceOrderId,
        calculationPolicyVersion: EconomicCalculationPolicyVersion
    ): MarketplaceEconomicTruthCalculationResult

    data class Complete(
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
        val contributionMargin: MarketplaceSalesIntelligenceContributionMarginSnapshot,
        val truthQuality: MarketplaceEconomicTruthQuality,
        val components: List<EconomicComponent>
    ) : MarketplaceSalesIntelligenceCalculationSnapshot {
        override fun toCalculationResult(
            organizationId: OrganizationId,
            marketplaceOrderId: MarketplaceOrderId,
            calculationPolicyVersion: EconomicCalculationPolicyVersion
        ): MarketplaceEconomicTruthCalculationResult =
            MarketplaceEconomicTruthCalculationResult.Complete(
                MarketplaceEconomicResult(
                    organizationId = organizationId,
                    orderId = marketplaceOrderId,
                    marketplace = marketplace,
                    externalOrderId = externalOrderId,
                    orderOccurredAt = orderOccurredAt,
                    currency = currency,
                    grossRevenue = grossRevenue,
                    totalMarketplaceFees = totalMarketplaceFees,
                    totalShipping = totalShipping,
                    totalAdvertising = totalAdvertising,
                    totalTaxes = totalTaxes,
                    totalProductCost = totalProductCost,
                    totalFinancialCost = totalFinancialCost,
                    totalOtherAdjustments = totalOtherAdjustments,
                    contribution = contribution,
                    contributionMargin = contributionMargin.toDomain(),
                    truthQuality = truthQuality,
                    calculationPolicyVersion = calculationPolicyVersion,
                    components = components
                )
            )

        override fun toString(): String = "[REDACTED]"
    }

    data class Incomplete(
        val missingTypes: List<EconomicComponentType>,
        val partialTypes: List<EconomicComponentType>,
        val suppliedComponents: List<EconomicComponent>
    ) : MarketplaceSalesIntelligenceCalculationSnapshot {
        override fun toCalculationResult(
            organizationId: OrganizationId,
            marketplaceOrderId: MarketplaceOrderId,
            calculationPolicyVersion: EconomicCalculationPolicyVersion
        ): MarketplaceEconomicTruthCalculationResult =
            MarketplaceEconomicTruthCalculationResult.Incomplete(
                organizationId = organizationId,
                orderId = marketplaceOrderId,
                missingTypes = missingTypes,
                partialTypes = partialTypes,
                suppliedComponents = suppliedComponents,
                calculationPolicyVersion = calculationPolicyVersion
            )

        override fun toString(): String = "[REDACTED]"
    }

    companion object {
        fun from(
            result: MarketplaceEconomicTruthCalculationResult
        ): MarketplaceSalesIntelligenceCalculationSnapshot = when (result) {
            is MarketplaceEconomicTruthCalculationResult.Complete -> {
                val value = result.result
                Complete(
                    marketplace = value.marketplace,
                    externalOrderId = value.externalOrderId,
                    orderOccurredAt = value.orderOccurredAt,
                    currency = value.currency,
                    grossRevenue = value.grossRevenue,
                    totalMarketplaceFees = value.totalMarketplaceFees,
                    totalShipping = value.totalShipping,
                    totalAdvertising = value.totalAdvertising,
                    totalTaxes = value.totalTaxes,
                    totalProductCost = value.totalProductCost,
                    totalFinancialCost = value.totalFinancialCost,
                    totalOtherAdjustments = value.totalOtherAdjustments,
                    contribution = value.contribution,
                    contributionMargin =
                        MarketplaceSalesIntelligenceContributionMarginSnapshot.from(
                            value.contributionMargin
                        ),
                    truthQuality = value.truthQuality,
                    components = value.components
                )
            }

            is MarketplaceEconomicTruthCalculationResult.Incomplete ->
                Incomplete(
                    missingTypes = result.missingTypes,
                    partialTypes = result.partialTypes,
                    suppliedComponents = result.suppliedComponents
                )
        }
    }
}

sealed interface MarketplaceSalesIntelligenceContributionMarginSnapshot {
    fun toDomain(): ContributionMargin

    data class Defined(val decimalValue: BigDecimal) :
        MarketplaceSalesIntelligenceContributionMarginSnapshot {
        override fun toDomain(): ContributionMargin = ContributionMargin.Defined(decimalValue)
        override fun toString(): String = "[REDACTED]"
    }

    data class Undefined(val reason: ContributionMarginUndefinedReason) :
        MarketplaceSalesIntelligenceContributionMarginSnapshot {
        override fun toDomain(): ContributionMargin = ContributionMargin.Undefined(reason)
        override fun toString(): String = "[REDACTED]"
    }

    companion object {
        fun from(value: ContributionMargin): MarketplaceSalesIntelligenceContributionMarginSnapshot =
            when (value) {
                is ContributionMargin.Defined -> Defined(value.decimalValue)
                is ContributionMargin.Undefined -> Undefined(value.reason)
            }
    }
}

fun calculationPolicyVersionOf(
    result: MarketplaceEconomicTruthCalculationResult
): EconomicCalculationPolicyVersion = when (result) {
    is MarketplaceEconomicTruthCalculationResult.Complete ->
        result.result.calculationPolicyVersion
    is MarketplaceEconomicTruthCalculationResult.Incomplete ->
        result.calculationPolicyVersion
}
