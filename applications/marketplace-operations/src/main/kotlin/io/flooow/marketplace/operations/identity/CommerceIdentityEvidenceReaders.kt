package io.flooow.marketplace.operations.identity

import io.flooow.organization.OrganizationId

/** Read-only, organization-scoped ports for durable source evidence. */
fun interface MercadoLivreIdentityEvidenceReader {
    fun read(organizationId: OrganizationId, limit: Int): MercadoLivreIdentityEvidenceRead
}

data class MercadoLivreIdentityEvidenceRead(
    val records: List<MercadoLivreTransactionEvidence>,
    val persistedRows: Int,
    val sellerSkuRows: Int,
    val usableAmountDateRows: Int
) {
    init {
        require(persistedRows >= records.size)
        require(sellerSkuRows in 0..persistedRows)
        require(usableAmountDateRows in 0..persistedRows)
    }
}

data class OmieIdentityEvidenceRead(
    val records: List<OmieSalesOrderEvidence>,
    val persistedRows: Int,
    val skippedRows: Int,
    val integrationReferenceRows: Int = 0,
    val customerOrderReferenceRows: Int = 0,
    val productEvidenceRows: Int = 0,
    val amountEvidenceRows: Int = 0,
    val explicitMarketplaceOrderReferenceRows: Int = 0
) {
    init {
        require(persistedRows >= records.size)
        require(skippedRows == persistedRows - records.size)
        require(integrationReferenceRows in 0..persistedRows)
        require(customerOrderReferenceRows in 0..persistedRows)
        require(productEvidenceRows in 0..persistedRows)
        require(amountEvidenceRows in 0..persistedRows)
        require(explicitMarketplaceOrderReferenceRows in 0..persistedRows)
    }
}

fun interface OmieIdentityEvidenceReader {
    fun read(organizationId: OrganizationId, limit: Int): OmieIdentityEvidenceRead
}
