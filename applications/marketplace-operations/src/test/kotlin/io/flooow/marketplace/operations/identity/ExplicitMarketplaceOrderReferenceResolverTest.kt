package io.flooow.marketplace.operations.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExplicitMarketplaceOrderReferenceResolverTest {
    private val observed = setOf("SYNTH-ML-ORDER")

    @Test
    fun `exact and representation-only forms resolve`() {
        assertEquals(setOf("SYNTH-ML-ORDER"), ExplicitMarketplaceOrderReferenceResolver.resolve(observed, listOf("SYNTH-ML-ORDER")))
        assertEquals(setOf("SYNTH-ML-ORDER"), ExplicitMarketplaceOrderReferenceResolver.resolve(observed, listOf("#SYNTH-ML-ORDER")))
        assertEquals(setOf("SYNTH-ML-ORDER"), ExplicitMarketplaceOrderReferenceResolver.resolve(observed, listOf(" SYNTH-ML-ORDER ")))
    }

    @Test
    fun `unknown and embedded values do not resolve`() {
        assertTrue(ExplicitMarketplaceOrderReferenceResolver.resolve(observed, listOf("2000017885956380")).isEmpty())
        assertTrue(ExplicitMarketplaceOrderReferenceResolver.resolve(observed, listOf("ML-SYNTH-ML-ORDER", "pedido SYNTH-ML-ORDER", "xSYNTH-ML-ORDER")).isEmpty())
        assertTrue(ExplicitMarketplaceOrderReferenceResolver.resolve(observed, listOf("##SYNTH-ML-ORDER")).isEmpty())
    }

    @Test
    fun `missing remains absent`() {
        assertTrue(ExplicitMarketplaceOrderReferenceResolver.resolve(observed, listOf(null, "   ")).isEmpty())
    }
}
