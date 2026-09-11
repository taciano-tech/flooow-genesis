package io.flooow.marketplace.persistence.postgres

import io.flooow.organization.OrganizationId
import io.flooow.marketplace.operations.economics.provider.OmieTransactionProductIdentifier
import io.flooow.marketplace.operations.economics.provider.OmieTransactionProductIdentifierKind
import io.flooow.marketplace.operations.economics.provider.OmieTransactionProductObservation
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceCapability
import io.flooow.marketplace.operations.economics.provider.ProviderSourceDecimal
import io.flooow.marketplace.operations.identity.OmieEvidenceScope
import io.flooow.marketplace.operations.identity.OmieProductIdentifierKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals

class PostgresOmieIdentityEvidenceReaderTest {
    private val organization = OrganizationId.parse("11111111-1111-4111-8111-111111111111")
    private val occurredAt = Instant.parse("2026-09-09T12:00:00Z")
    private val scope = OmieEvidenceScope(organization, "11111111-1111-4111-8111-111111111112")

    @Test
    fun `each identity bearing shape projects without fabrication`() {
        val integration = row(integration = "ERP-1").toDomain(organization, scope)
        val customer = row(customer = "CUSTOMER-1").toDomain(organization, scope)
        val product = row(products = "[{\"code\":\"SKU-1\",\"quantity\":\"2\"}]").toDomain(organization, scope)

        assertNotNull(integration)
        assertNotNull(customer)
        assertNotNull(product)
        assertEquals(null, product.orderAmount)
        assertEquals(OmieProductIdentifierKind.UNKNOWN_LEGACY, product.productIdentifiers.single().kind)
    }

    @Test
    fun `row without integration customer or products is skipped`() {
        assertNull(row().toDomain(organization, scope))
    }

    @Test
    fun `missing amount and currency remain missing`() {
        val evidence = row(integration = "ERP-1").toDomain(organization, scope)
        assertNotNull(evidence)
        assertNull(evidence.orderAmount)
    }

    @Test
    fun `typed product refs persistence codec roundtrips kind value and quantity`() {
        val encoded = OmieProductRefsJson.encode(listOf(
            OmieTransactionProductObservation(
                OmieTransactionProductIdentifier.of(
                    OmieTransactionProductIdentifierKind.INTERNAL_PRODUCT_ID,
                    "101"
                ),
                ProviderSourceDecimal.parse("2")
            )
        ))
        val evidence = row(products = encoded).toDomain(organization, scope)

        assertNotNull(evidence)
        assertEquals(OmieProductIdentifierKind.INTERNAL_PRODUCT_ID, evidence.productIdentifiers.single().kind)
        assertEquals("101", evidence.productIdentifiers.single().value)
        assertEquals(java.math.BigDecimal("2"), evidence.quantityByProductIdentifier.values.single())
    }

    @Test
    fun `typed revision is selected over untyped revision with the same source fingerprint`() {
        val reader = PostgresOmieIdentityEvidenceReader(
            PostgresConfiguration("jdbc:postgresql://localhost/unused", "unused", "unused")
        )
        val old = row(products = "[{\"code\":\"101\",\"quantity\":\"1\"}]").copy(
            capability = OmieTransactionEvidenceCapability.REACQUISITION_V1_KEY.value
        )
        val typed = row(
            products = "[{\"kind\":\"INTERNAL_PRODUCT_ID\",\"value\":\"101\",\"quantity\":\"1\"}]"
        ).copy(
            observed = occurredAt.minusSeconds(60),
            capability = OmieTransactionEvidenceCapability.REACQUISITION_KEY.value
        )
        val latestTyped = typed.copy(observed = occurredAt.minusSeconds(30), progressVersion = 2)

        assertEquals(latestTyped, reader.selectPreferredRevisions(listOf(old, typed, latestTyped)).single())
        assertEquals(latestTyped, reader.selectPreferredRevisions(listOf(latestTyped, typed, old)).single())
    }

    private fun row(
        integration: String? = null,
        customer: String? = null,
        products: String = "[]"
    ) = PostgresOmieIdentityEvidenceReader.OmieRow(
        "OMIE-1", integration, customer, occurredAt, null, null, products,
        occurredAt, "fingerprint-1"
    )
}
