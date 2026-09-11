package io.flooow.marketplace.operations.economics.reconciliation

import io.flooow.organization.OrganizationId
import java.time.Instant

/** An explicit acceptance marker. Raw assessments never enter the case seam. */
class AcceptedFinancialReconciliationAssessment private constructor(
    val assessment: FinancialReconciliationAssessment,
    val acceptedAt: Instant
) {
    init {
        require(acceptedAt.nano % 1_000 == 0) { "Accepted assessment timestamp must have microsecond precision" }
    }

    companion object {
        fun accept(assessment: FinancialReconciliationAssessment, acceptedAt: Instant): AcceptedFinancialReconciliationAssessment =
            AcceptedFinancialReconciliationAssessment(assessment, acceptedAt)
    }
}

enum class ReconciliationCaseOrchestrationFailure {
    ORGANIZATION_MISMATCH,
    PERSISTENCE
}

sealed interface ReconciliationCaseOrchestrationResult {
    data class Created(val value: DurableReconciliationCase) : ReconciliationCaseOrchestrationResult
    data class Revised(val value: DurableReconciliationCase) : ReconciliationCaseOrchestrationResult
    data class Unchanged(val value: DurableReconciliationCase) : ReconciliationCaseOrchestrationResult
    data object NotEligible : ReconciliationCaseOrchestrationResult
    data class Rejected(val failure: ReconciliationCaseOrchestrationFailure) : ReconciliationCaseOrchestrationResult
    data class Failed(val failure: ReconciliationCaseOrchestrationFailure) : ReconciliationCaseOrchestrationResult
}

fun interface SystemicDivergenceAnalysisTrigger {
    fun analyze(organizationId: OrganizationId, evaluatedAt: Instant)
}

/**
 * The only productive seam from an accepted reconciliation assessment to a
 * durable case. It has no ledger/evidence/provider/recovery authority.
 */
class GovernedReconciliationCaseOrchestrator(
    private val repository: DurableReconciliationCaseRepository,
    private val systemicAnalysis: SystemicDivergenceAnalysisTrigger? = null
) {
    fun process(
        accepted: AcceptedFinancialReconciliationAssessment,
        organizationId: OrganizationId
    ): ReconciliationCaseOrchestrationResult {
        val assessment = accepted.assessment
        if (assessment.organizationId != organizationId) {
            return ReconciliationCaseOrchestrationResult.Rejected(
                ReconciliationCaseOrchestrationFailure.ORGANIZATION_MISMATCH
            )
        }
        if (assessment.status != FinancialReconciliationStatus.DIVERGENCE) {
            return ReconciliationCaseOrchestrationResult.NotEligible
        }

        val caseId = DurableReconciliationCase.deterministicId(assessment)
        return try {
            val existing = repository.find(organizationId, caseId)
            when (val decision = DurableReconciliationCaseProcessor.process(assessment, accepted.acceptedAt, existing)) {
                ReconciliationCaseDecision.NoCase -> ReconciliationCaseOrchestrationResult.NotEligible
                is ReconciliationCaseDecision.Open -> {
                    val value = decision.value
                    if (value === existing) {
                        ReconciliationCaseOrchestrationResult.Unchanged(value)
                    } else {
                        val saved = repository.save(value)
                        systemicAnalysis?.analyze(organizationId, accepted.acceptedAt)
                        if (existing == null) {
                            ReconciliationCaseOrchestrationResult.Created(saved)
                        } else {
                            ReconciliationCaseOrchestrationResult.Revised(saved)
                        }
                    }
                }
            }
        } catch (_: RuntimeException) {
            // Retry is safe: the deterministic identity and revision guard are
            // owned by the processor/repository. No lower-layer state is touched.
            ReconciliationCaseOrchestrationResult.Failed(ReconciliationCaseOrchestrationFailure.PERSISTENCE)
        }
    }
}
