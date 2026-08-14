package io.flooow.marketplace.operations.economics.pricing

import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import java.time.Duration
import java.time.Instant

data class PricingCostBasisSelectionPolicyVersion(val value: String) {
    init {
        require(SELECTION_VERSION_PATTERN.matches(value)) {
            "Pricing cost basis selection policy version is invalid"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class PricingCostBasisSelectionPolicy(
    val version: PricingCostBasisSelectionPolicyVersion,
    val selectedBasis: PricingProductCostBasis,
    val maximumAssessmentAge: Duration
) {
    init {
        require(isValidSelectionAge(maximumAssessmentAge)) {
            "Pricing cost basis maximum assessment age must be positive and at most 31 days"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

@ConsistentCopyVisibility
data class PricingProductCostBasisSelection internal constructor(
    val sourceAssessment: PricingProductCostBasisAssessment,
    val selectedBasis: PricingProductCostBasis,
    val selectedEvidence: PricingProductCostEvidence,
    val selectedEvidenceQuality: EconomicEvidenceQuality,
    val basisAssessmentQuality: EconomicEvidenceQuality,
    val selectionPolicyVersion: PricingCostBasisSelectionPolicyVersion,
    val maximumAssessmentAge: Duration,
    val selectedAt: Instant
) {
    init {
        require(isValidSelectionAge(maximumAssessmentAge)) {
            "Pricing cost basis selection maximum age is invalid"
        }
        require(selectedAt.nano % 1_000 == 0) {
            "Pricing cost basis selection time must use microsecond precision"
        }
        require(isAssessmentInsideSelectionWindow(sourceAssessment, selectedAt, maximumAssessmentAge)) {
            "Pricing cost basis source assessment is outside the selection window"
        }
        require(selectedEvidence == evidenceFor(sourceAssessment, selectedBasis)) {
            "Selected cost evidence is inconsistent with the selected basis"
        }
        require(selectedEvidenceQuality == selectedEvidence.quality) {
            "Selected cost evidence quality is inconsistent"
        }
        require(basisAssessmentQuality == sourceAssessment.quality) {
            "Cost basis assessment quality is inconsistent"
        }
        require(isSelectedEvidenceApplicable(sourceAssessment, selectedBasis, selectedAt)) {
            "Selected cost evidence is outside applicability at selection"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface PricingProductCostBasisSelectionResult {
    data class Selected(val selection: PricingProductCostBasisSelection) :
        PricingProductCostBasisSelectionResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object AssessmentOutsideSelectionWindow : PricingProductCostBasisSelectionResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SelectedEvidenceOutsideApplicability : PricingProductCostBasisSelectionResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplacePricingProductCostBasisSelection {
    fun select(
        assessment: PricingProductCostBasisAssessment,
        policy: PricingCostBasisSelectionPolicy,
        selectedAt: Instant
    ): PricingProductCostBasisSelectionResult {
        require(selectedAt.nano % 1_000 == 0) {
            "Pricing cost basis selection time must use microsecond precision"
        }
        if (!isAssessmentInsideSelectionWindow(assessment, selectedAt, policy.maximumAssessmentAge)) {
            return PricingProductCostBasisSelectionResult.AssessmentOutsideSelectionWindow
        }
        if (!isSelectedEvidenceApplicable(assessment, policy.selectedBasis, selectedAt)) {
            return PricingProductCostBasisSelectionResult.SelectedEvidenceOutsideApplicability
        }
        val selectedEvidence = evidenceFor(assessment, policy.selectedBasis)
        return PricingProductCostBasisSelectionResult.Selected(
            PricingProductCostBasisSelection(
                assessment,
                policy.selectedBasis,
                selectedEvidence,
                selectedEvidence.quality,
                assessment.quality,
                policy.version,
                policy.maximumAssessmentAge,
                selectedAt
            )
        )
    }
}

private fun evidenceFor(
    assessment: PricingProductCostBasisAssessment,
    basis: PricingProductCostBasis
) = when (basis) {
    PricingProductCostBasis.HISTORICAL_ACQUISITION -> assessment.historicalEvidence
    PricingProductCostBasis.CURRENT_REPLACEMENT -> assessment.currentReplacementEvidence
    PricingProductCostBasis.FORWARD_REPLACEMENT -> assessment.forwardReplacementEvidence
}

private fun isAssessmentInsideSelectionWindow(
    assessment: PricingProductCostBasisAssessment,
    selectedAt: Instant,
    maximumAge: Duration
) = runCatching {
    !selectedAt.isBefore(assessment.evaluatedAt) &&
        !selectedAt.isAfter(assessment.evaluatedAt.plus(maximumAge))
}.getOrDefault(false)

private fun isSelectedEvidenceApplicable(
    assessment: PricingProductCostBasisAssessment,
    basis: PricingProductCostBasis,
    selectedAt: Instant
) = when (basis) {
    PricingProductCostBasis.HISTORICAL_ACQUISITION -> true
    PricingProductCostBasis.CURRENT_REPLACEMENT -> {
        val evidence = assessment.currentReplacementEvidence
        isInsideSelectionPastWindow(
            evidence.occurredAt,
            selectedAt,
            assessment.maximumCurrentReplacementAge
        ) && isInsideSelectionPastWindow(
            evidence.applicableAt,
            selectedAt,
            assessment.maximumCurrentReplacementAge
        )
    }
    PricingProductCostBasis.FORWARD_REPLACEMENT ->
        assessment.forwardReplacementEvidence.applicableAt.isAfter(selectedAt)
}

private fun isInsideSelectionPastWindow(value: Instant, selectedAt: Instant, maximumAge: Duration) =
    runCatching {
        !value.isAfter(selectedAt) && !value.isBefore(selectedAt.minus(maximumAge))
    }.getOrDefault(false)

private fun isValidSelectionAge(value: Duration) =
    !value.isZero &&
        !value.isNegative &&
        value <= MAXIMUM_SELECTION_AGE &&
        value.nano % 1_000 == 0

private val SELECTION_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9./-]{0,99}")
private val MAXIMUM_SELECTION_AGE: Duration = Duration.ofDays(31)
