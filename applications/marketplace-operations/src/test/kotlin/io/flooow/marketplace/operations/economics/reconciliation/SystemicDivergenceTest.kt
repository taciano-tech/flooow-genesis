package io.flooow.marketplace.operations.economics.reconciliation

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerEntryId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceId
import io.flooow.organization.OrganizationId
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SystemicDivergenceTest {
    private val org = OrganizationId(UUID.fromString("10000000-0000-0000-0000-000000000001"))
    private val currency = MarketplaceCurrency("BRL")
    private val at = Instant.parse("2026-09-08T12:00:00Z")

    @Test fun `below threshold is absent and threshold replay is idempotent`() {
        val repo = MemorySignalRepository(); val detector = DeterministicSystemicDivergenceDetector(repo)
        val policy = SystemicDivergencePolicy("systemic/1", Duration.ofDays(7), 2, money("5"))
        assertEquals(0, detector.analyze(org, listOf(case("1")), policy, at).size)
        val first = assertIs<SystemicDivergenceAnalysisResult.Created>(detector.analyze(org, listOf(case("3"), case("3", 2)), policy, at).single())
        val repeat = assertIs<SystemicDivergenceAnalysisResult.Unchanged>(detector.analyze(org, listOf(case("3"), case("3", 2)), policy, at).single())
        assertEquals(first.value.signalId, repeat.value.signalId)
    }

    @Test fun `outside window and other organization do not contaminate`() {
        val repo = MemorySignalRepository(); val policy = SystemicDivergencePolicy("systemic/1", Duration.ofDays(1), 2, money("1"))
        val old = case("3").let { DurableReconciliationCase(it.caseId, it.organizationId, it.orderId, it.traceId, it.policyVersion, it.currency, it.status, it.openedAt.minusSeconds(86400 * 2), it.lastObservedAt.minusSeconds(86400 * 2), it.resolvedAt, it.revision, it.absoluteDifferenceSummary, it.stages, it.evidenceEntryIds) }
        val other = case("3", 3).let { DurableReconciliationCase(it.caseId, OrganizationId(UUID.fromString("10000000-0000-0000-0000-000000000002")), it.orderId, it.traceId, it.policyVersion, it.currency, it.status, it.openedAt, it.lastObservedAt, it.resolvedAt, it.revision, it.absoluteDifferenceSummary, it.stages, it.evidenceEntryIds) }
        assertEquals(0, DeterministicSystemicDivergenceDetector(repo).analyze(org, listOf(old, other), policy, at).size)
    }

    private fun case(amount: String, suffix: Int = 1): DurableReconciliationCase {
        val id = FinancialLedgerEntryId.of(UUID.fromString("40000000-0000-0000-0000-${suffix.toString().padStart(12, '0')}"))
        val actual = FinancialReconciliationSide.Observed(money((10 + amount.toInt()).toString()), listOf(id))
        val expected = FinancialReconciliationSide.Observed(money("10"), listOf(FinancialLedgerEntryId.of(UUID.fromString("50000000-0000-0000-0000-${suffix.toString().padStart(12, '0')}"))))
        val diff = FinancialReconciliationDifference.Compared(money(amount), money(amount), money("0"))
        val line = FinancialReconciliationLine(FinancialLedgerStage.SALE, expected, actual, diff, FinancialReconciliationStatus.DIVERGENCE)
        val assessment = FinancialReconciliationAssessment(org, FinancialTraceId.of(UUID.nameUUIDFromBytes("trace$suffix".toByteArray())), MarketplaceOrderId(UUID.nameUUIDFromBytes("order$suffix".toByteArray())), currency, FinancialReconciliationPolicyVersion("case/1"), listOf(line), FinancialReconciliationStatus.DIVERGENCE)
        return DurableReconciliationCase.fromAssessment(DurableReconciliationCase.deterministicId(assessment), assessment, at, at)
    }
    private fun money(v: String) = MarketplaceMoney.calculated(currency, v.toBigDecimal())
    private class MemorySignalRepository : SystemicDivergenceSignalRepository {
        val values = mutableMapOf<SystemicDivergenceSignalId, SystemicDivergenceSignal>()
        override fun save(value: SystemicDivergenceSignal) = value.also { values[it.signalId] = it }
        override fun find(organizationId: OrganizationId, signalId: SystemicDivergenceSignalId) = values[signalId]?.takeIf { it.organizationId == organizationId }
        override fun list(organizationId: OrganizationId, cursor: SystemicDivergenceSignalCursor?, limit: Int) = SystemicDivergenceSignalPage(values.values.filter { it.organizationId == organizationId }, null)
    }
}
