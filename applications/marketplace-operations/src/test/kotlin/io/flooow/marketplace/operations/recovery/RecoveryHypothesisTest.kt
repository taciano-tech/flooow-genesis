package io.flooow.marketplace.operations.recovery

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCaseId
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignal
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalId
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalStatus
import io.flooow.marketplace.operations.identity.CommerceIdentityMatchState
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecoveryHypothesisTest {
    private val org = OrganizationId.parse("11111111-1111-4111-8111-111111111111")
    private val at = Instant.parse("2026-09-08T12:00:00Z")
    private val currency = MarketplaceCurrency("BRL")

    @Test fun `candidate identity is blocked`() {
        val h = RecoveryHypothesisFactory.fromSignal(signal(), CommerceIdentityMatchState.CANDIDATE, null, policy(), at)
        assertEquals(RecoveryHypothesisStatus.BLOCKED_IDENTITY, h.status)
        assertEquals(RecoverabilityValidationStatus.BLOCKED, h.validation.status)
        assertNull(h.validation.validatedAmount)
    }

    @Test fun `exact identity without explicit potential remains insufficient`() {
        val h = RecoveryHypothesisFactory.fromSignal(signal(), CommerceIdentityMatchState.EXACT_CONFIRMED, "erp-1", policy(), at)
        assertEquals(RecoveryHypothesisStatus.READY_FOR_VALIDATION, h.status)
        assertEquals(RecoverabilityValidationStatus.INSUFFICIENT, h.validation.status)
    }

    @Test fun `explicit potential is validated only with exact identity and policy`() {
        val h = RecoveryHypothesisFactory.fromSignal(signal(), CommerceIdentityMatchState.EXACT_CONFIRMED, "erp-1", policy(BigDecimal("4.00")), at)
        assertEquals(RecoveryHypothesisStatus.VALIDATED, h.status)
        assertEquals(0, BigDecimal("4.00").compareTo(h.validation.validatedAmount!!.value.amount))
    }

    @Test fun `observed and potential amounts are distinct types`() {
        val h = RecoveryHypothesisFactory.fromSignal(signal(), CommerceIdentityMatchState.EXACT_CONFIRMED, "erp-1", policy(BigDecimal("4.00")), at)
        assertEquals(0, BigDecimal("10.00").compareTo(h.observedDivergenceAmount.value.amount))
        assertEquals(0, BigDecimal("4.00").compareTo(h.potentiallyRecoverableAmount!!.value.amount))
    }

    @Test fun `hypothesis identity is deterministic`() {
        val one = RecoveryHypothesisFactory.fromSignal(signal(), CommerceIdentityMatchState.CANDIDATE, null, policy(), at)
        val two = RecoveryHypothesisFactory.fromSignal(signal(), CommerceIdentityMatchState.CANDIDATE, null, policy(), at.plusSeconds(1))
        assertEquals(one.hypothesisId, two.hypothesisId)
    }

    private fun policy(amount: BigDecimal? = null) = RecoverabilityPolicy("recovery/1", true, true, true, true, true, true, amount?.let { RecoveryAmount.PotentiallyRecoverableAmount(MarketplaceMoney.parse(currency, it.toPlainString())) })
    private fun signal() = SystemicDivergenceSignal(SystemicDivergenceSignalId.of(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")), org, FinancialLedgerStage.MARKETPLACE_FEE, currency, "systemic/1", Duration.ofDays(7), at, at, 2, MarketplaceMoney.parse(currency, "10.00"), SystemicDivergenceSignalStatus.ACTIVE, listOf(ReconciliationCaseId.of(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")), ReconciliationCaseId.of(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"))), 1)
}
