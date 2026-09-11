package io.flooow.marketplace.operations.identity

/**
 * Interprets provider references only when their normalized representation is
 * present in the observed Mercado Livre order set for the same evaluation.
 * This is deliberately not a matching heuristic.
 */
object ExplicitMarketplaceOrderReferenceResolver {
    fun resolve(
        observedMercadoLivreOrderIds: Set<String>,
        providerReferences: Iterable<String?>
    ): Set<String> {
        val observed = observedMercadoLivreOrderIds.mapNotNull { normalize(it) }.toSet()
        return providerReferences.mapNotNull { it?.let(::normalize) }
            .filter { it in observed }
            .toSortedSet()
    }

    fun normalize(reference: String): String? = reference.trim()
        .let { if (it.startsWith("#")) it.substring(1).trim() else it }
        .takeIf { it.isNotEmpty() }
}
