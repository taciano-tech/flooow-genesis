package io.flooow.marketplace.operations.identity

import io.flooow.organization.OrganizationId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CommerceIdentityHealthTest {
    private val at = Instant.parse("2026-01-01T00:00:00Z")
    private val org = OrganizationId.parse("11111111-1111-4111-8111-111111111111")
    @Test fun `health counts exact and unresolved without inventing coverage`() {
        val ml = MercadoLivreTransactionEvidence(org, "123456789012", null, null, emptySet(), setOf("SKU"), mapOf("SKU" to java.math.BigDecimal.ONE), null, at, setOf("ml"))
        val omie = OmieSalesOrderEvidence(org, setOf("SKU"), mapOf("SKU" to java.math.BigDecimal.ONE), "ERP-1", null, null, at, setOf("omie"), setOf("123456789012"), OmieEvidenceScope(org, "omie-connection"))
        val result = CommerceIdentityHealthEvaluator.evaluate(listOf(ml), listOf(omie), CommerceIdentityPolicy("v1"), at)
        assertEquals(1, result.health.exactConfirmed)
        assertEquals(java.math.BigDecimal("100.00"), result.health.coveragePercentage)
    }
}
