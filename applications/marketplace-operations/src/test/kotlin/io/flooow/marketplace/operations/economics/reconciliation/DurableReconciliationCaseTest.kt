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

class DurableReconciliationCaseTest {
    private val currency = MarketplaceCurrency("BRL")
    private val organization = OrganizationId(UUID.fromString("10000000-0000-0000-0000-000000000001"))
    private val trace = FinancialTraceId.of(UUID.fromString("20000000-0000-0000-0000-000000000001"))
    private val order = MarketplaceOrderId(UUID.fromString("30000000-0000-0000-0000-000000000001"))
    private val time = Instant.parse("2026-09-08T12:00:00Z")

    @Test
    fun `within tolerance does not create a case`() {
        val assessment = assessment(FinancialReconciliationStatus.FULLY_RECONCILED, absolute = "0")
        assertEquals(ReconciliationCaseDecision.NoCase, DurableReconciliationCaseProcessor.process(assessment, time))
    }

    @Test
    fun `divergence is deterministic and repeated observation advances revision`() {
        val assessment = assessment(FinancialReconciliationStatus.DIVERGENCE, absolute = "5")
        val first = assertIs<ReconciliationCaseDecision.Open>(DurableReconciliationCaseProcessor.process(assessment, time)).value
        val repeated = assertIs<ReconciliationCaseDecision.Open>(DurableReconciliationCaseProcessor.process(assessment, time.plusSeconds(1), first)).value
        assertEquals(first.caseId, repeated.caseId)
        assertEquals(1, repeated.revision)
        assertEquals(organization, repeated.organizationId)
        assertEquals("5", repeated.absoluteDifferenceSummary.amount.toPlainString())
    }

    @Test
    fun `missing side remains absent in case stage`() {
        val assessment = assessment(FinancialReconciliationStatus.DIVERGENCE, absolute = "5", expected = null)
        val value = assertIs<ReconciliationCaseDecision.Open>(DurableReconciliationCaseProcessor.process(assessment, time)).value
        assertEquals(null, value.stages.single().expected)
        assertEquals("5", value.stages.single().actual?.amount?.toPlainString())
    }

    private fun assessment(status: FinancialReconciliationStatus, absolute: String, expected: String? = "10"): FinancialReconciliationAssessment {
        val id = FinancialLedgerEntryId.of(UUID.fromString("40000000-0000-0000-0000-000000000001"))
        val expectedSide = expected?.let { FinancialReconciliationSide.Observed(money(it), listOf(id)) } ?: FinancialReconciliationSide.NotObserved
        val actualAmount = if (expected == null) "5" else if (status == FinancialReconciliationStatus.FULLY_RECONCILED) "10" else "15"
        val actualSide = FinancialReconciliationSide.Observed(money(actualAmount), listOf(FinancialLedgerEntryId.of(UUID.fromString("40000000-0000-0000-0000-000000000002"))))
        val difference = if (expected == null) FinancialReconciliationDifference.NotComparable else FinancialReconciliationDifference.Compared(money(if (status == FinancialReconciliationStatus.FULLY_RECONCILED) "0" else "5"), money(absolute), money("0"))
        val line = FinancialReconciliationLine(FinancialLedgerStage.SALE, expectedSide, actualSide, difference, status)
        return FinancialReconciliationAssessment(organization, trace, order, currency, FinancialReconciliationPolicyVersion("case/1"), listOf(line), status)
    }

    private fun money(value: String) = MarketplaceMoney.calculated(currency, value.toBigDecimal())
}
