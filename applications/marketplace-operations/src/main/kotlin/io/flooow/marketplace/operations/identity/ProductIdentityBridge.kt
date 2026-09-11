package io.flooow.marketplace.operations.identity

import java.time.Instant

/**
 * Read-only product/offer evidence bridge. An exact seller-SKU/code overlap is
 * deliberately retained as a suggested relation; it never confirms a product
 * mapping or changes Economic Truth.
 */
object ProductIdentityBridge {
    fun candidates(
        marketplace: MercadoLivreTransactionEvidence,
        omieOrders: List<OmieSalesOrderEvidence>,
        policy: CommerceIdentityPolicy,
        evaluatedAt: Instant
    ): List<CommerceIdentityCandidate> {
        require(omieOrders.all { it.organizationId == marketplace.organizationId }) {
            "Product identity evidence organization mismatch"
        }
        val scopes = omieOrders.map { it.scope }.toSet()
        require(scopes.size <= 1) { "Product identity evidence connection scope mixed" }
        return omieOrders.flatMap { omie ->
            omie.productIdentifiers.filter { it.value in marketplace.sellerSkus }
                .sortedWith(compareBy<OmieProductIdentifier> { it.kind.name }.thenBy { it.value })
                .map { identifier ->
                val sellerSku = identifier.value
                CommerceIdentityCandidate(
                    marketplace.organizationId,
                    CommerceIdentitySystem.MERCADO_LIVRE,
                    CommerceIdentityType.SELLER_SKU,
                    sellerSku,
                    CommerceIdentitySystem.OMIE,
                    identifier.kind.commerceIdentityType(),
                    sellerSku,
                    CommerceIdentityMatchState.CANDIDATE,
                    CommerceIdentityConfirmationState.SUGGESTED,
                    listOf(
                        CommerceIdentityEvidence(
                            marketplace.organizationId,
                            CommerceIdentitySystem.MERCADO_LIVRE,
                            CommerceIdentityType.SELLER_SKU,
                            sellerSku,
                            CommerceIdentityEvidenceKind.EXACT_SELLER_SKU_TO_OMIE_PRODUCT_REFERENCE_TEXT,
                            "MGI_EXACT_SELLER_SKU_TO_OMIE_PRODUCT_REFERENCE_TEXT",
                            evaluatedAt
                        )
                    ),
                    policy.version,
                    evaluatedAt
                )
            }
        }
    }

    private fun OmieProductIdentifierKind.commerceIdentityType() = when (this) {
        OmieProductIdentifierKind.INTERNAL_PRODUCT_ID -> CommerceIdentityType.ERP_PRODUCT_ID
        OmieProductIdentifierKind.INTEGRATION_PRODUCT_CODE -> CommerceIdentityType.ERP_PRODUCT_INTEGRATION_CODE
        OmieProductIdentifierKind.DISPLAY_PRODUCT_CODE -> CommerceIdentityType.ERP_PRODUCT_DISPLAY_CODE
        OmieProductIdentifierKind.UNKNOWN_LEGACY -> CommerceIdentityType.ERP_PRODUCT_UNKNOWN_LEGACY
    }
}
