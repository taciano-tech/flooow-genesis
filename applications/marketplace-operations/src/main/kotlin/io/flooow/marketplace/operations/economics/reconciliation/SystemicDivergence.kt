package io.flooow.marketplace.operations.economics.reconciliation

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.UUID

data class SystemicDivergencePolicy(
    val version: String,
    val window: Duration,
    val minimumCases: Int,
    val minimumAbsoluteDifference: MarketplaceMoney
) {
    init {
        require(version.matches(Regex("[a-z0-9][a-z0-9./-]{0,99}")))
        require(!window.isZero && !window.isNegative)
        require(minimumCases >= 2)
        require(minimumAbsoluteDifference.amount.signum() >= 0)
    }
}

object SystemicDivergencePolicies {
    /** Explicit governed baseline; changing it requires a new policy version. */
    val current = SystemicDivergencePolicy("systemic/1", Duration.ofDays(7), 2, MarketplaceMoney.zero(MarketplaceCurrency("BRL")))
}

enum class SystemicDivergenceSignalStatus { ACTIVE }

class SystemicDivergenceSignal(
    val signalId: SystemicDivergenceSignalId,
    val organizationId: OrganizationId,
    val stage: FinancialLedgerStage,
    val currency: MarketplaceCurrency,
    val policyVersion: String,
    val window: Duration,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val occurrenceCount: Int,
    val absoluteDifference: MarketplaceMoney,
    val status: SystemicDivergenceSignalStatus,
    caseIds: Collection<ReconciliationCaseId>,
    val revision: Long
) {
    val caseIds: List<ReconciliationCaseId> = Collections.unmodifiableList(caseIds.toList())
    init {
        require(occurrenceCount == caseIds.size && occurrenceCount >= 2)
        require(revision > 0)
        require(absoluteDifference.currency == currency)
        require(firstSeenAt.nano % 1_000 == 0 && lastSeenAt.nano % 1_000 == 0)
    }

    companion object {
        fun deterministicId(organizationId: OrganizationId, policy: SystemicDivergencePolicy, stage: FinancialLedgerStage, currency: MarketplaceCurrency): SystemicDivergenceSignalId =
            SystemicDivergenceSignalId.of(UUID.nameUUIDFromBytes(
                "${organizationId.value}:${policy.version}:${stage.name}:${currency.code}".toByteArray(StandardCharsets.UTF_8)
            ))
    }
}

@JvmInline
value class SystemicDivergenceSignalId(val value: UUID) {
    fun valueForPersistence(): UUID = value
    override fun toString(): String = "[INTERNAL]"
    companion object { fun of(value: UUID) = SystemicDivergenceSignalId(value) }
}

data class SystemicDivergenceSignalCursor(val lastSeenAt: Instant, val signalId: SystemicDivergenceSignalId)
data class SystemicDivergenceSignalPage(val signals: List<SystemicDivergenceSignal>, val nextCursor: SystemicDivergenceSignalCursor?)

interface SystemicDivergenceSignalRepository {
    fun save(value: SystemicDivergenceSignal): SystemicDivergenceSignal
    fun find(organizationId: OrganizationId, signalId: SystemicDivergenceSignalId): SystemicDivergenceSignal?
    fun list(organizationId: OrganizationId, cursor: SystemicDivergenceSignalCursor?, limit: Int): SystemicDivergenceSignalPage
}

sealed interface SystemicDivergenceAnalysisResult {
    data class Created(val value: SystemicDivergenceSignal) : SystemicDivergenceAnalysisResult
    data class Revised(val value: SystemicDivergenceSignal) : SystemicDivergenceAnalysisResult
    data class Unchanged(val value: SystemicDivergenceSignal) : SystemicDivergenceAnalysisResult
    data object NoSignal : SystemicDivergenceAnalysisResult
    data class Failed(val category: FailureCategory) : SystemicDivergenceAnalysisResult
}

enum class FailureCategory { PERSISTENCE }

class DeterministicSystemicDivergenceDetector(
    private val signalRepository: SystemicDivergenceSignalRepository
) {
    fun analyze(
        organizationId: OrganizationId,
        cases: Collection<DurableReconciliationCase>,
        policy: SystemicDivergencePolicy,
        evaluatedAt: Instant
    ): List<SystemicDivergenceAnalysisResult> {
        require(evaluatedAt.nano % 1_000 == 0)
        val cutoff = evaluatedAt.minus(policy.window)
        val valid = cases.filter { it.organizationId == organizationId && !it.lastObservedAt.isBefore(cutoff) && !it.lastObservedAt.isAfter(evaluatedAt) }
        return FinancialLedgerStage.entries.flatMap { stage ->
            valid.mapNotNull { case ->
                val detail = case.stages.firstOrNull { it.stage == stage } ?: return@mapNotNull null
                val amount = detail.absoluteDifference ?: return@mapNotNull null
                Triple(case, detail, amount)
            }.groupBy { it.first.currency to it.first.policyVersion }
                .mapNotNull { (key, members) ->
                    val currency = key.first
                    if (currency != policy.minimumAbsoluteDifference.currency) return@mapNotNull null
                    if (members.size < policy.minimumCases) return@mapNotNull null
                    val total = members.fold(MarketplaceMoney.zero(currency)) { sum, item -> sum + item.third }
                    if (total.amount < policy.minimumAbsoluteDifference.amount) return@mapNotNull null
                    val ids = members.map { it.first.caseId }.distinct().sortedBy { it.valueForPersistence().toString() }
                    val id = SystemicDivergenceSignal.deterministicId(organizationId, policy, stage, currency)
                    val first = members.minOf { it.first.lastObservedAt }
                    val last = members.maxOf { it.first.lastObservedAt }
                    val existing = signalRepository.find(organizationId, id)
                    val candidate = SystemicDivergenceSignal(id, organizationId, stage, currency, policy.version, policy.window, first, last, ids.size, total, SystemicDivergenceSignalStatus.ACTIVE, ids, existing?.revision ?: 1)
                    try {
                        when {
                            existing == null -> SystemicDivergenceAnalysisResult.Created(signalRepository.save(candidate))
                            existing.caseIds == candidate.caseIds && existing.absoluteDifference == candidate.absoluteDifference && existing.firstSeenAt == candidate.firstSeenAt && existing.lastSeenAt == candidate.lastSeenAt -> SystemicDivergenceAnalysisResult.Unchanged(existing)
                            else -> SystemicDivergenceAnalysisResult.Revised(signalRepository.save(candidate.copyRevision(existing.revision + 1)))
                        }
                    } catch (_: RuntimeException) {
                        SystemicDivergenceAnalysisResult.Failed(FailureCategory.PERSISTENCE)
                    }
                }
        }
    }
}

private fun SystemicDivergenceSignal.copyRevision(revision: Long) = SystemicDivergenceSignal(signalId, organizationId, stage, currency, policyVersion, window, firstSeenAt, lastSeenAt, occurrenceCount, absoluteDifference, status, caseIds, revision)
