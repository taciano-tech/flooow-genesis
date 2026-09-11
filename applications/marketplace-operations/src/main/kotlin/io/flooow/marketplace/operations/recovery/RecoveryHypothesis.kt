package io.flooow.marketplace.operations.recovery

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCaseId
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignal
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalId
import io.flooow.marketplace.operations.identity.CommerceIdentityMatchState
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Collections
import java.util.UUID

sealed class RecoveryAmount private constructor(val value: MarketplaceMoney) {
    class ObservedDivergenceAmount(value: MarketplaceMoney) : RecoveryAmount(value)
    class PotentiallyRecoverableAmount(value: MarketplaceMoney) : RecoveryAmount(value)
    class ValidatedRecoverableAmount(value: MarketplaceMoney) : RecoveryAmount(value)
    class AuthorizedRecoveryAmount(value: MarketplaceMoney) : RecoveryAmount(value)
    class ExecutedRecoveryAmount(value: MarketplaceMoney) : RecoveryAmount(value)
    class ActualRecoveredAmount(value: MarketplaceMoney) : RecoveryAmount(value)
}

enum class RecoveryHypothesisStatus { OPEN, BLOCKED_IDENTITY, READY_FOR_VALIDATION, VALIDATED, REJECTED }
enum class RecoverabilityValidationStatus { INSUFFICIENT, BLOCKED, VALIDATED, REJECTED }

data class RecoverabilityValidation(
    val status: RecoverabilityValidationStatus,
    val policyVersion: String,
    val reasons: Set<String>,
    val validatedAmount: RecoveryAmount.ValidatedRecoverableAmount?,
    val evaluatedAt: Instant
) {
    init {
        require(policyVersion.isNotBlank())
        require(reasons.isNotEmpty())
        require(evaluatedAt.nano % 1_000 == 0)
        require(status == RecoverabilityValidationStatus.VALIDATED == (validatedAmount != null))
    }
    override fun toString() = "RecoverabilityValidation([REDACTED])"
}

class RecoveryHypothesis(
    val hypothesisId: RecoveryHypothesisId,
    val organizationId: OrganizationId,
    signalIds: Collection<SystemicDivergenceSignalId>,
    caseIds: Collection<ReconciliationCaseId>,
    evidenceReferences: Collection<String>,
    val policyVersion: String,
    val stage: FinancialLedgerStage,
    val currency: MarketplaceCurrency,
    val observedDivergenceAmount: RecoveryAmount.ObservedDivergenceAmount,
    val potentiallyRecoverableAmount: RecoveryAmount.PotentiallyRecoverableAmount?,
    val identityState: CommerceIdentityMatchState,
    val identityReference: String?,
    val validation: RecoverabilityValidation,
    val status: RecoveryHypothesisStatus,
    val createdAt: Instant,
    val lastEvaluatedAt: Instant,
    val revision: Long
) {
    val signalIds: List<SystemicDivergenceSignalId> = Collections.unmodifiableList(signalIds.distinct().sortedBy { it.value.toString() })
    val caseIds: List<ReconciliationCaseId> = Collections.unmodifiableList(caseIds.distinct().sortedBy { it.value.toString() })
    val evidenceReferences: List<String> = Collections.unmodifiableList(evidenceReferences.distinct().sorted())
    init {
        require(this.signalIds.isNotEmpty() && this.caseIds.isNotEmpty() && this.evidenceReferences.isNotEmpty())
        require(policyVersion.isNotBlank()); require(observedDivergenceAmount.value.currency == currency)
        require(potentiallyRecoverableAmount == null || potentiallyRecoverableAmount.value.currency == currency)
        require(identityState == CommerceIdentityMatchState.EXACT_CONFIRMED || identityReference == null || identityReference.isNotBlank())
        require(createdAt.nano % 1_000 == 0 && lastEvaluatedAt.nano % 1_000 == 0 && revision > 0)
        require(validation.policyVersion == policyVersion)
        require(status != RecoveryHypothesisStatus.VALIDATED || validation.status == RecoverabilityValidationStatus.VALIDATED)
        require(status != RecoveryHypothesisStatus.VALIDATED || identityState == CommerceIdentityMatchState.EXACT_CONFIRMED)
    }
    override fun toString() = "RecoveryHypothesis([REDACTED])"

    fun materiallyEquals(other: RecoveryHypothesis): Boolean =
        organizationId == other.organizationId && signalIds == other.signalIds && caseIds == other.caseIds &&
            evidenceReferences == other.evidenceReferences && policyVersion == other.policyVersion && stage == other.stage &&
            currency == other.currency && observedDivergenceAmount.value == other.observedDivergenceAmount.value &&
            potentiallyRecoverableAmount?.value == other.potentiallyRecoverableAmount?.value &&
            identityState == other.identityState && identityReference == other.identityReference && validation == other.validation

    companion object {
        fun deterministicId(organizationId: OrganizationId, signalIds: Collection<SystemicDivergenceSignalId>, policyVersion: String): RecoveryHypothesisId =
            RecoveryHypothesisId.of(UUID.nameUUIDFromBytes((organizationId.value.toString() + ":" + policyVersion + ":" + signalIds.map { it.value }.sortedBy { it.toString() }.joinToString(",")).toByteArray(StandardCharsets.UTF_8)))
    }
}

@JvmInline value class RecoveryHypothesisId(val value: UUID) {
    override fun toString() = "[INTERNAL]"
    companion object { fun of(value: UUID) = RecoveryHypothesisId(value) }
}

data class RecoverabilityPolicy(
    val version: String,
    val evidenceComplete: Boolean,
    val policyApplicable: Boolean,
    val withinEligibilityWindow: Boolean,
    val duplicateRecoveryAbsent: Boolean,
    val currencyConsistent: Boolean,
    val stageCategoryConsistent: Boolean,
    val explicitPotentialAmount: RecoveryAmount.PotentiallyRecoverableAmount?
) {
    init { require(version.isNotBlank()) }
}

object RecoverabilityValidator {
    fun validate(hypothesis: RecoveryHypothesis, policy: RecoverabilityPolicy, evaluatedAt: Instant): RecoverabilityValidation {
        require(policy.version == hypothesis.policyVersion) { "Recoverability policy version mismatch" }
        val blocked = linkedSetOf<String>()
        if (hypothesis.identityState != CommerceIdentityMatchState.EXACT_CONFIRMED) blocked += "IDENTITY_NOT_CONFIRMED"
        if (hypothesis.evidenceReferences.isEmpty()) blocked += "EVIDENCE_INCOMPLETE"
        if (!policy.evidenceComplete) blocked += "EVIDENCE_INCOMPLETE"
        if (!policy.policyApplicable) blocked += "POLICY_NOT_APPLICABLE"
        if (!policy.withinEligibilityWindow) blocked += "OUTSIDE_ELIGIBILITY_WINDOW"
        if (!policy.duplicateRecoveryAbsent) blocked += "DUPLICATE_RECOVERY_RISK"
        if (!policy.currencyConsistent) blocked += "CURRENCY_INCONSISTENT"
        if (!policy.stageCategoryConsistent) blocked += "STAGE_CATEGORY_INCONSISTENT"
        val potential = policy.explicitPotentialAmount
        if (blocked.isNotEmpty()) return RecoverabilityValidation(RecoverabilityValidationStatus.BLOCKED, policy.version, blocked, null, evaluatedAt)
        if (potential == null) return RecoverabilityValidation(RecoverabilityValidationStatus.INSUFFICIENT, policy.version, setOf("POTENTIAL_AMOUNT_MISSING"), null, evaluatedAt)
        require(potential.value.currency == hypothesis.currency)
        return RecoverabilityValidation(RecoverabilityValidationStatus.VALIDATED, policy.version, setOf("EXPLICIT_POLICY_AND_EVIDENCE_SATISFIED"), RecoveryAmount.ValidatedRecoverableAmount(potential.value), evaluatedAt)
    }
}

sealed interface RecoveryHypothesisProcessResult {
    data class Created(val value: RecoveryHypothesis) : RecoveryHypothesisProcessResult
    data class Revised(val value: RecoveryHypothesis) : RecoveryHypothesisProcessResult
    data class Unchanged(val value: RecoveryHypothesis) : RecoveryHypothesisProcessResult
}

object RecoveryHypothesisFactory {
    fun fromSignal(signal: SystemicDivergenceSignal, identityState: CommerceIdentityMatchState, identityReference: String?, policy: RecoverabilityPolicy, evaluatedAt: Instant): RecoveryHypothesis {
        val validation = RecoverabilityValidator.validatePlaceholder(signal, identityState, policy, evaluatedAt)
        val status = when {
            validation.status == RecoverabilityValidationStatus.VALIDATED -> RecoveryHypothesisStatus.VALIDATED
            identityState != CommerceIdentityMatchState.EXACT_CONFIRMED -> RecoveryHypothesisStatus.BLOCKED_IDENTITY
            validation.status == RecoverabilityValidationStatus.INSUFFICIENT -> RecoveryHypothesisStatus.READY_FOR_VALIDATION
            else -> RecoveryHypothesisStatus.REJECTED
        }
        return RecoveryHypothesis(RecoveryHypothesis.deterministicId(signal.organizationId, listOf(signal.signalId), signal.policyVersion), signal.organizationId, listOf(signal.signalId), signal.caseIds, signal.caseIds.map { "reconciliation-case:${it.value}" }, policy.version, signal.stage, signal.currency, RecoveryAmount.ObservedDivergenceAmount(signal.absoluteDifference), policy.explicitPotentialAmount, identityState, identityReference, validation, status, evaluatedAt, evaluatedAt, 1)
    }
}

private fun RecoverabilityValidator.validatePlaceholder(signal: SystemicDivergenceSignal, identityState: CommerceIdentityMatchState, policy: RecoverabilityPolicy, at: Instant): RecoverabilityValidation {
    val placeholder = RecoveryHypothesis(RecoveryHypothesisId.of(UUID.nameUUIDFromBytes("placeholder".toByteArray())), signal.organizationId, listOf(signal.signalId), signal.caseIds, signal.caseIds.map { "reconciliation-case:${it.value}" }, policy.version, signal.stage, signal.currency, RecoveryAmount.ObservedDivergenceAmount(signal.absoluteDifference), policy.explicitPotentialAmount, identityState, null, RecoverabilityValidation(RecoverabilityValidationStatus.INSUFFICIENT, policy.version, setOf("PENDING_HYPOTHESIS"), null, at), RecoveryHypothesisStatus.OPEN, at, at, 1)
    return validate(placeholder, policy, at)
}
