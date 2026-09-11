package io.flooow.marketplace.operations.identity

import io.flooow.organization.OrganizationId

enum class OmieProductIdentifierKind {
    INTERNAL_PRODUCT_ID,
    INTEGRATION_PRODUCT_CODE,
    DISPLAY_PRODUCT_CODE,
    UNKNOWN_LEGACY
}

data class OmieProductIdentifier(val kind: OmieProductIdentifierKind, val value: String) {
    init {
        require(value.isNotBlank() && value == value.trim())
        require(value.none(Char::isISOControl) && value.length <= 128)
    }
    override fun toString() = "OmieProductIdentifier($kind,[REDACTED])"
}

data class OmieEvidenceScope(val organizationId: OrganizationId, val connectionId: String) {
    init {
        require(connectionId.isNotBlank() && connectionId == connectionId.trim())
        require(connectionId.none(Char::isISOControl) && connectionId.length <= 128)
    }
}

enum class OmieCatalogIdentityState { VALID, CONFLICT }

data class OmieCatalogProductEvidence(
    val scope: OmieEvidenceScope,
    val internalProductId: String,
    val integrationProductCodes: Set<String>,
    val displayProductCodes: Set<String>,
    val identityState: OmieCatalogIdentityState,
    val evidenceReferences: Set<String>
) {
    init {
        require(internalProductId.isNotBlank())
        require(integrationProductCodes.none { it.isBlank() })
        require(displayProductCodes.none { it.isBlank() })
        require(evidenceReferences.isNotEmpty())
        require(identityState == OmieCatalogIdentityState.CONFLICT ||
            (integrationProductCodes.size <= 1 && displayProductCodes.size <= 1))
    }

    fun identifiers(): Set<OmieProductIdentifier> = buildSet {
        add(OmieProductIdentifier(OmieProductIdentifierKind.INTERNAL_PRODUCT_ID, internalProductId))
        integrationProductCodes.forEach {
            add(OmieProductIdentifier(OmieProductIdentifierKind.INTEGRATION_PRODUCT_CODE, it))
        }
        displayProductCodes.forEach {
            add(OmieProductIdentifier(OmieProductIdentifierKind.DISPLAY_PRODUCT_CODE, it))
        }
    }
}

data class OmieProductCatalogEvidenceRead(
    val scope: OmieEvidenceScope,
    val products: List<OmieCatalogProductEvidence>,
    val persistedRows: Int
) {
    init {
        require(persistedRows >= products.size)
        require(products.all { it.scope == scope })
    }
}

fun interface OmieProductCatalogEvidenceReader {
    fun read(organizationId: OrganizationId, connectionId: String, limit: Int): OmieProductCatalogEvidenceRead
}

data class OmieProductResolution(
    val transactionIdentifier: OmieProductIdentifier,
    val state: CommerceIdentityMatchState,
    val providerProductIds: Set<String>
)

object WithinOmieProductResolver {
    fun resolve(
        transaction: OmieSalesOrderEvidence,
        catalog: OmieProductCatalogEvidenceRead
    ): List<OmieProductResolution> {
        val transactionScope = transaction.scope
        require(transaction.organizationId == catalog.scope.organizationId) {
            "Omie product identity organization mismatch"
        }
        require(transactionScope == catalog.scope) {
            "Omie product identity connection mismatch"
        }
        return transaction.productIdentifiers.sortedWith(
            compareBy<OmieProductIdentifier> { it.kind.name }.thenBy { it.value }
        ).map { identifier ->
            if (identifier.kind == OmieProductIdentifierKind.UNKNOWN_LEGACY) {
                OmieProductResolution(identifier, CommerceIdentityMatchState.UNRESOLVED, emptySet())
            } else {
                val matches = catalog.products.filter { identifier in it.identifiers() }
                val state = when {
                    matches.isEmpty() -> CommerceIdentityMatchState.UNRESOLVED
                    matches.any { it.identityState == OmieCatalogIdentityState.CONFLICT } ->
                        CommerceIdentityMatchState.CONFLICT
                    matches.size == 1 -> CommerceIdentityMatchState.EXACT_CONFIRMED
                    else -> CommerceIdentityMatchState.AMBIGUOUS
                }
                OmieProductResolution(identifier, state, matches.mapTo(sortedSetOf()) { it.internalProductId })
            }
        }
    }
}

data class ProductIdentityMetrics(
    val totalTypedTransactionProductReferences: Int,
    val transactionReferencesByKind: Map<OmieProductIdentifierKind, Int>,
    val distinctProviderProductsEvaluated: Int?,
    val catalogProductsWithInternalId: Int?,
    val catalogProductsWithIntegrationCode: Int?,
    val catalogProductsWithDisplayCode: Int?,
    val withinOmieByState: Map<CommerceIdentityMatchState, Int>?,
    val crossSystemCandidateCount: Int
)

object ProductIdentityMetricsEvaluator {
    fun evaluate(
        marketplace: List<MercadoLivreTransactionEvidence>,
        transactions: List<OmieSalesOrderEvidence>,
        catalog: OmieProductCatalogEvidenceRead?,
        policy: CommerceIdentityPolicy,
        evaluatedAt: java.time.Instant
    ): ProductIdentityMetrics {
        val identifiers = transactions.flatMap { it.productIdentifiers }
        val resolutions = catalog?.let { read ->
            transactions.flatMap { WithinOmieProductResolver.resolve(it, read) }
        }
        return ProductIdentityMetrics(
            identifiers.size,
            OmieProductIdentifierKind.entries.associateWith { kind -> identifiers.count { it.kind == kind } },
            catalog?.products?.size,
            catalog?.products?.size,
            catalog?.products?.count { it.integrationProductCodes.isNotEmpty() },
            catalog?.products?.count { it.displayProductCodes.isNotEmpty() },
            resolutions?.groupingBy { it.state }?.eachCount(),
            marketplace.sumOf { ml -> ProductIdentityBridge.candidates(ml, transactions, policy, evaluatedAt).size }
        )
    }
}
