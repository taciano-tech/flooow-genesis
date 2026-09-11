package io.flooow.marketplace.operations.identity

import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommerceIdentityBridgeTest {
    private val org = OrganizationId.parse("11111111-1111-4111-8111-111111111111")
    private val other = OrganizationId.parse("22222222-2222-4222-8222-222222222222")
    private val at = Instant.parse("2026-09-08T12:00:00Z")
    private val policy = CommerceIdentityPolicy("MGI_GENESIS_IDENTITY_V1")

    @Test fun `declared external reference is exact and confirmed`() {
        val result = CommerceIdentityBridge.assess(ml(), listOf(omie(integration = "ERP-1", refs = setOf("#123456789012"))), policy, at)
        assertEquals(CommerceIdentityMatchState.EXACT_CONFIRMED, result.transaction.state)
        assertEquals(CommerceIdentityConfirmationState.GOVERNED_CONFIRMED, result.transaction.confirmationState)
    }

    @Test fun `deterministic composite is candidate and not confirmed`() {
        val result = CommerceIdentityBridge.assess(ml(), listOf(omie(integration = "ERP-2")), policy, at)
        assertEquals(CommerceIdentityMatchState.CANDIDATE, result.transaction.state)
        assertEquals(CommerceIdentityConfirmationState.SUGGESTED, result.transaction.confirmationState)
    }

    @Test fun `ambiguous candidates stay ambiguous`() {
        val result = CommerceIdentityBridge.assess(ml(), listOf(omie("A"), omie("B")), policy, at)
        assertEquals(CommerceIdentityMatchState.AMBIGUOUS, result.transaction.state)
    }

    @Test fun `conflicting exact references are preserved`() {
        val result = CommerceIdentityBridge.assess(ml(), listOf(omie("A", setOf("123456789012")), omie("B", setOf("123456789012"))), policy, at)
        assertEquals(CommerceIdentityMatchState.CONFLICT, result.transaction.state)
    }

    @Test fun `sku alone cannot confirm transaction identity`() {
        val result = CommerceIdentityBridge.assess(ml(amount = null), listOf(omie("ERP-3", date = at.plusSeconds(172800), quantity = BigDecimal("2"), amount = null)), policy, at)
        assertEquals(CommerceIdentityMatchState.UNRESOLVED, result.transaction.state)
        assertTrue(result.productCandidates.all { it.confirmationState == CommerceIdentityConfirmationState.SUGGESTED })
    }

    @Test fun `organization mismatch is rejected`() {
        val error = runCatching { CommerceIdentityBridge.assess(ml(), listOf(omie("ERP-4", organizationId = other)), policy, at) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test fun `missing amount is not zero`() {
        val result = CommerceIdentityBridge.assess(ml(amount = null), listOf(omie("ERP-5", amount = null)), policy, at)
        assertTrue(result.transaction.evidence.none { it.kind == CommerceIdentityEvidenceKind.EXACT_ORDER_VALUE })
    }

    private fun ml(amount: CommerceIdentityAmount? = CommerceIdentityAmount("BRL", BigDecimal("10.00"))) = MercadoLivreTransactionEvidence(
        org, "123456789012", null, null, setOf("ML-ITEM"), setOf("SKU-1"), mapOf("SKU-1" to BigDecimal.ONE), amount, at, setOf("ml:order:123456789012")
    )

    private fun omie(integration: String, refs: Set<String> = emptySet(), organizationId: OrganizationId = org,
        date: Instant = at, amount: CommerceIdentityAmount? = CommerceIdentityAmount("BRL", BigDecimal("10.00")),
        quantity: BigDecimal = BigDecimal.ONE) = OmieSalesOrderEvidence(
        organizationId, setOf("SKU-1"), mapOf("SKU-1" to quantity), integration, null, amount, date,
        setOf("omie:order:$integration"), refs, OmieEvidenceScope(organizationId, "omie-connection")
    )
}
