package io.flooow.marketplace.operations.economics.reconciliation

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerEntryId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceId
import io.flooow.organization.OrganizationId
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GovernedReconciliationCaseOrchestratorTest {
    private val currency = MarketplaceCurrency("BRL")
    private val orgA = OrganizationId(UUID.fromString("10000000-0000-0000-0000-000000000001"))
    private val orgB = OrganizationId(UUID.fromString("10000000-0000-0000-0000-000000000002"))
    private val trace = FinancialTraceId.of(UUID.fromString("20000000-0000-0000-0000-000000000001"))
    private val order = MarketplaceOrderId(UUID.fromString("30000000-0000-0000-0000-000000000001"))
    private val at = Instant.parse("2026-09-08T12:00:00Z")

    @Test
    fun `accepted divergence creates and replays without duplicate`() {
        val repository = MemoryRepository()
        val orchestrator = GovernedReconciliationCaseOrchestrator(repository)
        val accepted = AcceptedFinancialReconciliationAssessment.accept(assessment("5"), at)
        val created = assertIs<ReconciliationCaseOrchestrationResult.Created>(orchestrator.process(accepted, orgA))
        val unchanged = assertIs<ReconciliationCaseOrchestrationResult.Unchanged>(
            orchestrator.process(accepted, orgA)
        )
        assertEquals(created.value.caseId, unchanged.value.caseId)
        assertEquals(1, unchanged.value.revision)
        assertEquals(1, repository.values.size)
    }

    @Test
    fun `material observation revises and other organization is rejected`() {
        val repository = MemoryRepository()
        val orchestrator = GovernedReconciliationCaseOrchestrator(repository)
        val first = AcceptedFinancialReconciliationAssessment.accept(assessment("5"), at)
        orchestrator.process(first, orgA)
        val revised = assertIs<ReconciliationCaseOrchestrationResult.Revised>(
            orchestrator.process(AcceptedFinancialReconciliationAssessment.accept(assessment("7"), at.plusSeconds(1)), orgA)
        )
        assertEquals(2, revised.value.revision)
        assertIs<ReconciliationCaseOrchestrationResult.Rejected>(orchestrator.process(first, orgB))
    }

    @Test
    fun `within tolerance is not eligible and missing remains missing`() {
        val repository = MemoryRepository()
        val orchestrator = GovernedReconciliationCaseOrchestrator(repository)
        val within = assessment("0", expected = "10", status = FinancialReconciliationStatus.FULLY_RECONCILED)
        assertEquals(
            ReconciliationCaseOrchestrationResult.NotEligible,
            orchestrator.process(AcceptedFinancialReconciliationAssessment.accept(within, at), orgA)
        )
        val divergence = assertIs<ReconciliationCaseOrchestrationResult.Created>(
            orchestrator.process(AcceptedFinancialReconciliationAssessment.accept(assessment("5", expected = null), at), orgA)
        )
        assertEquals(null, divergence.value.stages.single().expected)
        assertEquals("5", divergence.value.stages.single().actual?.amount?.toPlainString())
    }

    @Test
    fun `persistence failure is retryable and does not call any provider`() {
        val repository = MemoryRepository(failSaves = true)
        val result = GovernedReconciliationCaseOrchestrator(repository).process(
            AcceptedFinancialReconciliationAssessment.accept(assessment("5"), at), orgA
        )
        assertEquals(ReconciliationCaseOrchestrationResult.Failed(ReconciliationCaseOrchestrationFailure.PERSISTENCE), result)
    }

    private fun assessment(
        absolute: String,
        expected: String? = "10",
        status: FinancialReconciliationStatus = FinancialReconciliationStatus.DIVERGENCE
    ): FinancialReconciliationAssessment {
        val expectedSide = expected?.let { FinancialReconciliationSide.Observed(money(it), listOf(entry(1))) }
            ?: FinancialReconciliationSide.NotObserved
        val actual = if (expected == null) "5" else if (status == FinancialReconciliationStatus.FULLY_RECONCILED) "10" else (expected!!.toBigDecimal() + absolute.toBigDecimal()).toPlainString()
        val actualSide = FinancialReconciliationSide.Observed(money(actual), listOf(entry(2)))
        val difference = if (expected == null) FinancialReconciliationDifference.NotComparable else
            FinancialReconciliationDifference.Compared(money(if (status == FinancialReconciliationStatus.FULLY_RECONCILED) "0" else absolute), money(absolute), money("0"))
        val line = FinancialReconciliationLine(FinancialLedgerStage.SALE, expectedSide, actualSide, difference, status)
        return FinancialReconciliationAssessment(orgA, trace, order, currency, FinancialReconciliationPolicyVersion("case/1"), listOf(line), status)
    }

    private fun money(value: String) = MarketplaceMoney.calculated(currency, value.toBigDecimal())
    private fun entry(value: Long) = FinancialLedgerEntryId.of(UUID.fromString("40000000-0000-0000-0000-${value.toString().padStart(12, '0')}"))

    private class MemoryRepository(private val failSaves: Boolean = false) : DurableReconciliationCaseRepository {
        val values = mutableMapOf<ReconciliationCaseId, DurableReconciliationCase>()
        override fun save(value: DurableReconciliationCase): DurableReconciliationCase {
            if (failSaves) error("persistence unavailable")
            values[value.caseId] = value
            return value
        }
        override fun find(organizationId: OrganizationId, caseId: ReconciliationCaseId) =
            values[caseId]?.takeIf { it.organizationId == organizationId }
        override fun list(organizationId: OrganizationId, cursor: ReconciliationCaseCursor?, limit: Int) =
            ReconciliationCasePage(values.values.filter { it.organizationId == organizationId }, null)
    }
}
