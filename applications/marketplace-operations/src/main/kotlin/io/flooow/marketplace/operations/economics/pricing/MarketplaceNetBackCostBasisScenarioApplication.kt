package io.flooow.marketplace.operations.economics.pricing

import io.flooow.marketplace.operations.economics.EconomicComponentCoverage
import io.flooow.marketplace.operations.economics.EconomicComponentType
import io.flooow.marketplace.operations.economics.EconomicDirection
import java.time.Duration
import java.time.Instant

data class NetBackCostBasisApplicationPolicyVersion(val value: String) {
    init {
        require(APPLICATION_VERSION_PATTERN.matches(value)) {
            "Net-back cost basis application policy version is invalid"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class NetBackCostBasisApplicationPolicy(
    val version: NetBackCostBasisApplicationPolicyVersion,
    val maximumSelectionAge: Duration
) {
    init {
        require(isValidApplicationAge(maximumSelectionAge)) {
            "Net-back cost basis maximum selection age must be positive and at most 31 days"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

@ConsistentCopyVisibility
data class NetBackCostBasisAppliedScenario internal constructor(
    val sourceProfile: NetBackPricingProfile,
    val costSelection: PricingProductCostBasisSelection,
    val originalProductCostComponent: NetBackCostComponent,
    val appliedProductCostComponent: NetBackCostComponent,
    val targetScenarioId: NetBackPricingScenarioId,
    val derivedProfile: NetBackPricingProfile,
    val applicationPolicyVersion: NetBackCostBasisApplicationPolicyVersion,
    val maximumSelectionAge: Duration,
    val appliedAt: Instant
) {
    init {
        val policy = NetBackCostBasisApplicationPolicy(applicationPolicyVersion, maximumSelectionAge)
        val assessment = assessApplication(
            sourceProfile,
            costSelection,
            targetScenarioId,
            policy,
            appliedAt
        )
        require(assessment is InternalApplicationAssessment.Success) {
            "Applied net-back cost basis scenario is inconsistent"
        }
        require(originalProductCostComponent == assessment.parts.originalProductCostComponent) {
            "Original Product Cost component is inconsistent"
        }
        require(appliedProductCostComponent == assessment.parts.appliedProductCostComponent) {
            "Applied Product Cost component is inconsistent"
        }
        require(derivedProfile == assessment.parts.derivedProfile) {
            "Derived net-back pricing profile is inconsistent"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface NetBackCostBasisScenarioApplicationResult {
    data class Applied(val appliedScenario: NetBackCostBasisAppliedScenario) :
        NetBackCostBasisScenarioApplicationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object TargetScenarioReusesSource : NetBackCostBasisScenarioApplicationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object OwnershipMismatch : NetBackCostBasisScenarioApplicationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SourceScenarioMismatch : NetBackCostBasisScenarioApplicationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object MarketplaceMismatch : NetBackCostBasisScenarioApplicationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object CurrencyMismatch : NetBackCostBasisScenarioApplicationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object UnitMismatch : NetBackCostBasisScenarioApplicationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SelectionOutsideApplicationWindow : NetBackCostBasisScenarioApplicationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SelectionNoLongerApplicable : NetBackCostBasisScenarioApplicationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object UnsupportedProductCostShape : NetBackCostBasisScenarioApplicationResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceNetBackCostBasisScenarioApplication {
    fun apply(
        sourceProfile: NetBackPricingProfile,
        costSelection: PricingProductCostBasisSelection,
        targetScenarioId: NetBackPricingScenarioId,
        policy: NetBackCostBasisApplicationPolicy,
        appliedAt: Instant
    ): NetBackCostBasisScenarioApplicationResult {
        require(appliedAt.nano % 1_000 == 0) {
            "Net-back cost basis application time must use microsecond precision"
        }
        return when (
            val assessment = assessApplication(
                sourceProfile,
                costSelection,
                targetScenarioId,
                policy,
                appliedAt
            )
        ) {
            is InternalApplicationAssessment.Failure -> assessment.kind.toResult()
            is InternalApplicationAssessment.Success -> {
                val parts = assessment.parts
                NetBackCostBasisScenarioApplicationResult.Applied(
                    NetBackCostBasisAppliedScenario(
                        sourceProfile,
                        costSelection,
                        parts.originalProductCostComponent,
                        parts.appliedProductCostComponent,
                        targetScenarioId,
                        parts.derivedProfile,
                        policy.version,
                        policy.maximumSelectionAge,
                        appliedAt
                    )
                )
            }
        }
    }
}

private fun assessApplication(
    sourceProfile: NetBackPricingProfile,
    costSelection: PricingProductCostBasisSelection,
    targetScenarioId: NetBackPricingScenarioId,
    policy: NetBackCostBasisApplicationPolicy,
    appliedAt: Instant
): InternalApplicationAssessment {
    val assessment = costSelection.sourceAssessment
    if (targetScenarioId == sourceProfile.scenarioId) {
        return failure(InternalApplicationFailure.TARGET_SCENARIO_REUSES_SOURCE)
    }
    if (sourceProfile.organizationId != assessment.organizationId) {
        return failure(InternalApplicationFailure.OWNERSHIP_MISMATCH)
    }
    if (sourceProfile.scenarioId != assessment.scenarioId) {
        return failure(InternalApplicationFailure.SOURCE_SCENARIO_MISMATCH)
    }
    if (sourceProfile.marketplace != assessment.marketplace) {
        return failure(InternalApplicationFailure.MARKETPLACE_MISMATCH)
    }
    if (sourceProfile.currency != assessment.currency) {
        return failure(InternalApplicationFailure.CURRENCY_MISMATCH)
    }
    if (sourceProfile.unitKey != assessment.unitKey) {
        return failure(InternalApplicationFailure.UNIT_MISMATCH)
    }
    if (!isInsideApplicationWindow(costSelection.selectedAt, appliedAt, policy.maximumSelectionAge)) {
        return failure(InternalApplicationFailure.SELECTION_OUTSIDE_APPLICATION_WINDOW)
    }
    if (!selectionRemainsApplicable(costSelection, appliedAt)) {
        return failure(InternalApplicationFailure.SELECTION_NO_LONGER_APPLICABLE)
    }
    val original = sourceProfile.components.singleOrNull {
        it.economicType == EconomicComponentType.PRODUCT_COST
    }
    if (
        sourceProfile.coverage[EconomicComponentType.PRODUCT_COST] != EconomicComponentCoverage.COMPLETE ||
        original == null ||
        original.direction != EconomicDirection.DEDUCTION ||
        original.value !is NetBackCostValue.FixedAmount
    ) {
        return failure(InternalApplicationFailure.UNSUPPORTED_PRODUCT_COST_SHAPE)
    }
    return InternalApplicationAssessment.Success(
        deriveScenarioParts(sourceProfile, costSelection, targetScenarioId, original)
    )
}

private fun selectionRemainsApplicable(
    selection: PricingProductCostBasisSelection,
    appliedAt: Instant
): Boolean {
    val policy = PricingCostBasisSelectionPolicy(
        selection.selectionPolicyVersion,
        selection.selectedBasis,
        selection.maximumAssessmentAge
    )
    val reproduced = MarketplacePricingProductCostBasisSelection.select(
        selection.sourceAssessment,
        policy,
        appliedAt
    )
    return reproduced is PricingProductCostBasisSelectionResult.Selected &&
        reproduced.selection.selectedBasis == selection.selectedBasis &&
        reproduced.selection.selectedEvidence == selection.selectedEvidence &&
        reproduced.selection.sourceAssessment == selection.sourceAssessment
}

private fun deriveScenarioParts(
    sourceProfile: NetBackPricingProfile,
    costSelection: PricingProductCostBasisSelection,
    targetScenarioId: NetBackPricingScenarioId,
    originalProductCostComponent: NetBackCostComponent
): DerivedScenarioParts {
    val appliedProductCostComponent = originalProductCostComponent.copy(
        scenarioId = targetScenarioId,
        value = NetBackCostValue.FixedAmount(costSelection.selectedEvidence.unitCost),
        source = costSelection.selectedEvidence.source,
        evidenceQuality = costSelection.selectedEvidenceQuality
    )
    val components = sourceProfile.components.map { component ->
        if (component.id == originalProductCostComponent.id) {
            appliedProductCostComponent
        } else {
            component.copy(scenarioId = targetScenarioId)
        }
    }
    val derivedProfile = NetBackPricingProfile(
        sourceProfile.organizationId,
        targetScenarioId,
        sourceProfile.marketplace,
        sourceProfile.currency,
        sourceProfile.unitKey,
        sourceProfile.priceQuantum,
        sourceProfile.normalizationPolicyVersion,
        components,
        sourceProfile.coverage,
        sourceProfile.target
    )
    return DerivedScenarioParts(
        originalProductCostComponent,
        appliedProductCostComponent,
        derivedProfile
    )
}

private fun isInsideApplicationWindow(
    selectedAt: Instant,
    appliedAt: Instant,
    maximumAge: Duration
) = runCatching {
    !appliedAt.isBefore(selectedAt) && !appliedAt.isAfter(selectedAt.plus(maximumAge))
}.getOrDefault(false)

private fun isValidApplicationAge(value: Duration) =
    !value.isZero &&
        !value.isNegative &&
        value <= MAXIMUM_APPLICATION_AGE &&
        value.nano % 1_000 == 0

private fun failure(kind: InternalApplicationFailure) = InternalApplicationAssessment.Failure(kind)

private fun InternalApplicationFailure.toResult(): NetBackCostBasisScenarioApplicationResult = when (this) {
    InternalApplicationFailure.TARGET_SCENARIO_REUSES_SOURCE ->
        NetBackCostBasisScenarioApplicationResult.TargetScenarioReusesSource
    InternalApplicationFailure.OWNERSHIP_MISMATCH ->
        NetBackCostBasisScenarioApplicationResult.OwnershipMismatch
    InternalApplicationFailure.SOURCE_SCENARIO_MISMATCH ->
        NetBackCostBasisScenarioApplicationResult.SourceScenarioMismatch
    InternalApplicationFailure.MARKETPLACE_MISMATCH ->
        NetBackCostBasisScenarioApplicationResult.MarketplaceMismatch
    InternalApplicationFailure.CURRENCY_MISMATCH ->
        NetBackCostBasisScenarioApplicationResult.CurrencyMismatch
    InternalApplicationFailure.UNIT_MISMATCH ->
        NetBackCostBasisScenarioApplicationResult.UnitMismatch
    InternalApplicationFailure.SELECTION_OUTSIDE_APPLICATION_WINDOW ->
        NetBackCostBasisScenarioApplicationResult.SelectionOutsideApplicationWindow
    InternalApplicationFailure.SELECTION_NO_LONGER_APPLICABLE ->
        NetBackCostBasisScenarioApplicationResult.SelectionNoLongerApplicable
    InternalApplicationFailure.UNSUPPORTED_PRODUCT_COST_SHAPE ->
        NetBackCostBasisScenarioApplicationResult.UnsupportedProductCostShape
}

private sealed interface InternalApplicationAssessment {
    data class Success(val parts: DerivedScenarioParts) : InternalApplicationAssessment
    data class Failure(val kind: InternalApplicationFailure) : InternalApplicationAssessment
}

private enum class InternalApplicationFailure {
    TARGET_SCENARIO_REUSES_SOURCE,
    OWNERSHIP_MISMATCH,
    SOURCE_SCENARIO_MISMATCH,
    MARKETPLACE_MISMATCH,
    CURRENCY_MISMATCH,
    UNIT_MISMATCH,
    SELECTION_OUTSIDE_APPLICATION_WINDOW,
    SELECTION_NO_LONGER_APPLICABLE,
    UNSUPPORTED_PRODUCT_COST_SHAPE
}

private data class DerivedScenarioParts(
    val originalProductCostComponent: NetBackCostComponent,
    val appliedProductCostComponent: NetBackCostComponent,
    val derivedProfile: NetBackPricingProfile
)

private val APPLICATION_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9./-]{0,99}")
private val MAXIMUM_APPLICATION_AGE: Duration = Duration.ofDays(31)
