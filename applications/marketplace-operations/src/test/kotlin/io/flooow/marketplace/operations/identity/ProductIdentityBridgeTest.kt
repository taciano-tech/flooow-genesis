package io.flooow.marketplace.operations.identity

import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ProductIdentityBridgeTest {
    private val organization = OrganizationId.parse("11111111-1111-4111-8111-111111111111")
    private val at = Instant.parse("2026-09-09T12:00:00Z")
    private val policy = CommerceIdentityPolicy("MGI_GENESIS_IDENTITY_V1")

    @Test
    fun `exact seller sku and Omie product code remains a suggested product relation`() {
        val relations = ProductIdentityBridge.candidates(
            marketplace = marketplace(setOf("SYNTH-SHARED-SKU")),
            omieOrders = listOf(omie(setOf("SYNTH-SHARED-SKU"))),
            policy = policy,
            evaluatedAt = at
        )
        assertEquals(1, relations.size)
        assertEquals(CommerceIdentityMatchState.CANDIDATE, relations.single().state)
        assertEquals(CommerceIdentityConfirmationState.SUGGESTED, relations.single().confirmationState)
        assertEquals(CommerceIdentityType.SELLER_SKU, relations.single().sourceIdentityType)
        assertEquals(CommerceIdentityType.ERP_PRODUCT_INTEGRATION_CODE, relations.single().targetIdentityType)
    }

    @Test
    fun `non matching product references remain absent`() {
        assertEquals(
            emptyList(),
            ProductIdentityBridge.candidates(marketplace(setOf("ML-SKU")), listOf(omie(setOf("OMIE-CODE"))), policy, at)
        )
    }

    @Test
    fun `organization mismatch fails closed`() {
        assertFails {
            ProductIdentityBridge.candidates(
                marketplace(setOf("SKU")),
                listOf(omie(setOf("SKU"), OrganizationId.parse("22222222-2222-4222-8222-222222222222"))),
                policy,
                at
            )
        }
    }

    @Test
    fun `mixed Omie connections fail closed`() {
        assertFails {
            ProductIdentityBridge.candidates(
                marketplace(setOf("SKU")),
                listOf(
                    omie(setOf("SKU")),
                    omie(setOf("SKU")).copy(scope = OmieEvidenceScope(organization, "other-connection"))
                ),
                policy,
                at
            )
        }
    }

    private fun marketplace(skus: Set<String>) = MercadoLivreTransactionEvidence(
        organization, "SYNTH-ML-ORDER", "SYNTH-ML-PACK", "SYNTH-ML-SHIPMENT", setOf("SYNTH-ML-ITEM"),
        skus, skus.associateWith { BigDecimal.ONE }, CommerceIdentityAmount("BRL", BigDecimal("58.28")), at,
        setOf("ml:order:SYNTH-ML-ORDER")
    )

    private fun omie(codes: Set<String>, org: OrganizationId = organization) = OmieSalesOrderEvidence(
        org, codes, codes.associateWith { BigDecimal.ONE }, "OMIE-ORDER", null,
        CommerceIdentityAmount("BRL", BigDecimal("58.28")), at, setOf("omie:order:OMIE-ORDER"), emptySet(),
        OmieEvidenceScope(org, "omie-connection"),
        codes.mapTo(linkedSetOf()) {
            OmieProductIdentifier(OmieProductIdentifierKind.INTEGRATION_PRODUCT_CODE, it)
        }
    )
}
