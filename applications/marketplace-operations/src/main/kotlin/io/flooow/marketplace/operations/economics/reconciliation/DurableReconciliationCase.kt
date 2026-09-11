package io.flooow.marketplace.operations.economics.reconciliation

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerEntryId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceId
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Collections
import java.util.UUID

@JvmInline
value class ReconciliationCaseId(val value: UUID) {
    fun valueForPersistence(): UUID = value
    override fun toString(): String = "[INTERNAL]"
    companion object { fun of(value: UUID) = ReconciliationCaseId(value) }
}

enum class ReconciliationCaseStatus { OPEN, ACKNOWLEDGED, RESOLVED }

class ReconciliationCaseStageDifference(
    val stage: FinancialLedgerStage,
    val expected: MarketplaceMoney?,
    val actual: MarketplaceMoney?,
    val signedDifference: MarketplaceMoney?,
    val absoluteDifference: MarketplaceMoney?,
    val tolerance: MarketplaceMoney,
    expectedEntryIds: Collection<FinancialLedgerEntryId>,
    actualEntryIds: Collection<FinancialLedgerEntryId>
) {
    val expectedEntryIds: List<FinancialLedgerEntryId> = Collections.unmodifiableList(expectedEntryIds.toList())
    val actualEntryIds: List<FinancialLedgerEntryId> = Collections.unmodifiableList(actualEntryIds.toList())
    init {
        require(expected == null || expected.currency == tolerance.currency)
        require(actual == null || actual.currency == tolerance.currency)
        require(signedDifference == null || signedDifference.currency == tolerance.currency)
        require(absoluteDifference == null || absoluteDifference.currency == tolerance.currency)
    }
}

class DurableReconciliationCase(
    val caseId: ReconciliationCaseId,
    val organizationId: OrganizationId,
    val orderId: MarketplaceOrderId,
    val traceId: FinancialTraceId,
    val policyVersion: FinancialReconciliationPolicyVersion,
    val currency: MarketplaceCurrency,
    val status: ReconciliationCaseStatus,
    val openedAt: Instant,
    val lastObservedAt: Instant,
    val resolvedAt: Instant?,
    val revision: Long,
    val absoluteDifferenceSummary: MarketplaceMoney,
    stages: Collection<ReconciliationCaseStageDifference>,
    evidenceEntryIds: Collection<FinancialLedgerEntryId>
) {
    val stages: List<ReconciliationCaseStageDifference> = Collections.unmodifiableList(stages.sortedBy { it.stage.ordinal })
    val evidenceEntryIds: List<FinancialLedgerEntryId> = Collections.unmodifiableList(evidenceEntryIds.toList())
    init {
        require(revision > 0)
        require(openedAt.nano % 1_000 == 0 && lastObservedAt.nano % 1_000 == 0)
        require(resolvedAt == null || resolvedAt.nano % 1_000 == 0)
        require(absoluteDifferenceSummary.currency == currency)
        require(stages.isNotEmpty())
    }

    fun withObservation(assessment: FinancialReconciliationAssessment, observedAt: Instant): DurableReconciliationCase {
        require(assessment.organizationId == organizationId && assessment.traceId == traceId)
        val next = fromAssessment(caseId, assessment, openedAt, observedAt, revision + 1, status, resolvedAt)
        return next
    }

    internal fun hasSameObservation(candidate: DurableReconciliationCase): Boolean =
        policyVersion == candidate.policyVersion && currency == candidate.currency &&
            absoluteDifferenceSummary == candidate.absoluteDifferenceSummary && stagesEquivalent(candidate.stages)

    private fun stagesEquivalent(other: List<ReconciliationCaseStageDifference>): Boolean =
        stages.size == other.size && stages.zip(other).all { (left, right) ->
            left.stage == right.stage && left.expected == right.expected && left.actual == right.actual &&
                left.signedDifference == right.signedDifference && left.absoluteDifference == right.absoluteDifference &&
                left.tolerance == right.tolerance && left.expectedEntryIds == right.expectedEntryIds &&
                left.actualEntryIds == right.actualEntryIds
        }

    companion object {
        fun fromAssessment(
            caseId: ReconciliationCaseId,
            assessment: FinancialReconciliationAssessment,
            openedAt: Instant,
            observedAt: Instant,
            revision: Long = 1,
            status: ReconciliationCaseStatus = ReconciliationCaseStatus.OPEN,
            resolvedAt: Instant? = null
        ): DurableReconciliationCase {
            require(assessment.status == FinancialReconciliationStatus.DIVERGENCE)
            require(openedAt.nano % 1_000 == 0 && observedAt.nano % 1_000 == 0)
            val stages = assessment.lines.filter { it.status == FinancialReconciliationStatus.DIVERGENCE }.map { line ->
                val expected = (line.expected as? FinancialReconciliationSide.Observed)
                val actual = (line.actual as? FinancialReconciliationSide.Observed)
                val difference = (line.difference as? FinancialReconciliationDifference.Compared)
                ReconciliationCaseStageDifference(
                    line.stage,
                    expected?.netAmount,
                    actual?.netAmount,
                    difference?.signedDifference,
                    difference?.absoluteDifference,
                    difference?.tolerance ?: MarketplaceMoney.zero(assessment.currency),
                    expected?.effectiveEntryIds.orEmpty(),
                    actual?.effectiveEntryIds.orEmpty()
                )
            }
            val total = stages.fold(MarketplaceMoney.zero(assessment.currency)) { sum, stage ->
                sum + (stage.absoluteDifference ?: MarketplaceMoney.zero(assessment.currency))
            }
            val refs = stages.flatMap { it.expectedEntryIds + it.actualEntryIds }.distinct()
            return DurableReconciliationCase(
                caseId, assessment.organizationId, assessment.orderId, assessment.traceId,
                assessment.policyVersion, assessment.currency, status, openedAt, observedAt,
                resolvedAt, revision, total, stages, refs
            )
        }

        fun deterministicId(assessment: FinancialReconciliationAssessment): ReconciliationCaseId =
            ReconciliationCaseId.of(UUID.nameUUIDFromBytes(
                "${assessment.organizationId.value}:${assessment.traceId.value}:${assessment.policyVersion.value}".toByteArray(StandardCharsets.UTF_8)
            ))
    }
}

sealed interface ReconciliationCaseDecision {
    data object NoCase : ReconciliationCaseDecision
    data class Open(val value: DurableReconciliationCase) : ReconciliationCaseDecision
}

object DurableReconciliationCaseProcessor {
    fun process(
        assessment: FinancialReconciliationAssessment,
        observedAt: Instant,
        existing: DurableReconciliationCase? = null
    ): ReconciliationCaseDecision {
        if (assessment.status != FinancialReconciliationStatus.DIVERGENCE) return ReconciliationCaseDecision.NoCase
        val id = existing?.caseId ?: DurableReconciliationCase.deterministicId(assessment)
        if (existing == null) return ReconciliationCaseDecision.Open(
            DurableReconciliationCase.fromAssessment(id, assessment, observedAt, observedAt)
        )
        val candidate = DurableReconciliationCase.fromAssessment(
            id, assessment, existing.openedAt, observedAt, existing.revision, existing.status, existing.resolvedAt
        )
        return ReconciliationCaseDecision.Open(
            if (existing.hasSameObservation(candidate)) existing else existing.withObservation(assessment, observedAt)
        )
    }
}

data class ReconciliationCaseCursor(val observedAt: Instant, val caseId: ReconciliationCaseId)
data class ReconciliationCasePage(val cases: List<DurableReconciliationCase>, val nextCursor: ReconciliationCaseCursor?)

interface DurableReconciliationCaseRepository {
    fun save(value: DurableReconciliationCase): DurableReconciliationCase
    fun find(organizationId: OrganizationId, caseId: ReconciliationCaseId): DurableReconciliationCase?
    fun list(organizationId: OrganizationId, cursor: ReconciliationCaseCursor?, limit: Int): ReconciliationCasePage
}
