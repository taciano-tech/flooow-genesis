package io.flooow.marketplace.operations.identity

import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OmieProductIdentityTest {
    private val organization = OrganizationId.parse("11111111-1111-4111-8111-111111111111")
    private val otherOrganization = OrganizationId.parse("22222222-2222-4222-8222-222222222222")
    private val scope = OmieEvidenceScope(organization, "connection-x")
    private val at = Instant.parse("2026-09-11T12:00:00Z")
    private val policy = CommerceIdentityPolicy("test-policy")

    @Test
    fun `unique same-kind match resolves exactly within Omie`() {
        val result = WithinOmieProductResolver.resolve(
            transaction(identifier(OmieProductIdentifierKind.INTEGRATION_PRODUCT_CODE, "INT-1")),
            catalog(product("101", integration = setOf("INT-1")))
        ).single()

        assertEquals(CommerceIdentityMatchState.EXACT_CONFIRMED, result.state)
        assertEquals(setOf("101"), result.providerProductIds)
    }

    @Test
    fun `same text across different kinds does not resolve`() {
        val result = WithinOmieProductResolver.resolve(
            transaction(identifier(OmieProductIdentifierKind.DISPLAY_PRODUCT_CODE, "101")),
            catalog(product("101"))
        ).single()

        assertEquals(CommerceIdentityMatchState.UNRESOLVED, result.state)
    }

    @Test
    fun `unknown legacy evidence cannot resolve exactly`() {
        val result = WithinOmieProductResolver.resolve(
            transaction(identifier(OmieProductIdentifierKind.UNKNOWN_LEGACY, "INT-1")),
            catalog(product("101", integration = setOf("INT-1")))
        ).single()

        assertEquals(CommerceIdentityMatchState.UNRESOLVED, result.state)
    }

    @Test
    fun `duplicate same-kind provider candidates fail closed as ambiguous`() {
        val result = WithinOmieProductResolver.resolve(
            transaction(identifier(OmieProductIdentifierKind.INTEGRATION_PRODUCT_CODE, "DUP")),
            catalog(product("101", integration = setOf("DUP")), product("102", integration = setOf("DUP")))
        ).single()

        assertEquals(CommerceIdentityMatchState.AMBIGUOUS, result.state)
        assertEquals(setOf("101", "102"), result.providerProductIds)
    }

    @Test
    fun `contradictory catalog identity evidence fails closed as conflict`() {
        val conflicting = OmieCatalogProductEvidence(
            scope, "101", setOf("INT-OLD", "INT-NEW"), emptySet(),
            OmieCatalogIdentityState.CONFLICT, setOf("catalog:one", "catalog:two")
        )
        val result = WithinOmieProductResolver.resolve(
            transaction(identifier(OmieProductIdentifierKind.INTEGRATION_PRODUCT_CODE, "INT-NEW")),
            catalog(conflicting)
        ).single()

        assertEquals(CommerceIdentityMatchState.CONFLICT, result.state)
    }

    @Test
    fun `organization and connection mismatches fail closed`() {
        assertFailsWith<IllegalArgumentException> {
            WithinOmieProductResolver.resolve(
                transaction(identifier(OmieProductIdentifierKind.INTERNAL_PRODUCT_ID, "101")),
                OmieProductCatalogEvidenceRead(
                    OmieEvidenceScope(otherOrganization, "connection-x"), emptyList(), 0
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WithinOmieProductResolver.resolve(
                transaction(identifier(OmieProductIdentifierKind.INTERNAL_PRODUCT_ID, "101")),
                OmieProductCatalogEvidenceRead(
                    OmieEvidenceScope(organization, "connection-y"), emptyList(), 0
                )
            )
        }
    }

    @Test
    fun `within Omie exact resolution never upgrades marketplace candidate confidence`() {
        val typed = identifier(OmieProductIdentifierKind.INTEGRATION_PRODUCT_CODE, "SHARED")
        val transaction = transaction(typed)
        assertEquals(
            CommerceIdentityMatchState.EXACT_CONFIRMED,
            WithinOmieProductResolver.resolve(
                transaction, catalog(product("101", integration = setOf("SHARED")))
            ).single().state
        )

        val candidate = ProductIdentityBridge.candidates(
            marketplace("SHARED"), listOf(transaction), policy, at
        ).single()
        assertEquals(CommerceIdentityMatchState.CANDIDATE, candidate.state)
        assertEquals(CommerceIdentityConfirmationState.SUGGESTED, candidate.confirmationState)
        assertEquals(
            CommerceIdentityEvidenceKind.EXACT_SELLER_SKU_TO_OMIE_PRODUCT_REFERENCE_TEXT,
            candidate.evidence.single().kind
        )
    }

    @Test
    fun `bounded metrics preserve typed catalog resolver and cross-system counts`() {
        val transaction = transaction(
            identifier(OmieProductIdentifierKind.INTEGRATION_PRODUCT_CODE, "SHARED")
        )
        val metrics = ProductIdentityMetricsEvaluator.evaluate(
            listOf(marketplace("SHARED")),
            listOf(transaction),
            catalog(product("101", integration = setOf("SHARED"))),
            policy,
            at
        )

        assertEquals(1, metrics.totalTypedTransactionProductReferences)
        assertEquals(1, metrics.transactionReferencesByKind.getValue(OmieProductIdentifierKind.INTEGRATION_PRODUCT_CODE))
        assertEquals(1, metrics.distinctProviderProductsEvaluated)
        assertEquals(1, metrics.withinOmieByState?.get(CommerceIdentityMatchState.EXACT_CONFIRMED))
        assertEquals(1, metrics.crossSystemCandidateCount)
    }

    private fun identifier(kind: OmieProductIdentifierKind, value: String) = OmieProductIdentifier(kind, value)

    private fun transaction(identifier: OmieProductIdentifier) = OmieSalesOrderEvidence(
        organization, setOf(identifier.value), mapOf(identifier.value to BigDecimal.ONE), "ORDER-1", null,
        null, at, setOf("omie:ORDER-1:fingerprint"), emptySet(), scope,
        setOf(identifier), mapOf(identifier to BigDecimal.ONE), at
    )

    private fun product(
        internal: String,
        integration: Set<String> = emptySet(),
        display: Set<String> = emptySet()
    ) = OmieCatalogProductEvidence(
        scope, internal, integration, display, OmieCatalogIdentityState.VALID, setOf("catalog:$internal")
    )

    private fun catalog(vararg products: OmieCatalogProductEvidence) =
        OmieProductCatalogEvidenceRead(scope, products.toList(), products.size)

    private fun marketplace(sku: String) = MercadoLivreTransactionEvidence(
        organization, "ML-ORDER", null, null, emptySet(), setOf(sku), mapOf(sku to BigDecimal.ONE),
        null, at, setOf("ml:ML-ORDER")
    )
}
