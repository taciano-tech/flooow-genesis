package io.flooow.integration.control

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class IntegrationControlPlaneValuesTest {
    @Test
    fun `provider and destination identifiers accept only canonical lowercase ascii`() {
        assertEquals("br.com.mercadolivre", ProviderKey.of("br.com.mercadolivre").value)
        assertEquals("warehouse_primary-1", IntegrationDestinationId.of("warehouse_primary-1").value)

        listOf("MercadoLivre", "provider/key", "á", "").forEach {
            assertFailsWith<IllegalArgumentException> { ProviderKey.of(it) }
        }
        listOf("Destination", "destination/key", "á", "").forEach {
            assertFailsWith<IllegalArgumentException> { IntegrationDestinationId.of(it) }
        }
    }

    @Test
    fun `default destination is stable for its connection`() {
        val connectionId = IntegrationConnectionId(
            UUID.fromString("773afbc1-6e04-41ef-9f30-0974d7b31a90")
        )

        assertEquals(
            "connection.773afbc1-6e04-41ef-9f30-0974d7b31a90",
            IntegrationDestinationId.forConnection(connectionId).value
        )
    }

    @Test
    fun `secret reference is redacted while preserving opaque persistence value`() {
        val reference = SecretReference.of("vault://opaque/random-reference")

        assertEquals("[REDACTED]", reference.toString())
        assertEquals("vault://opaque/random-reference", reference.encodedForPersistence())
        assertFalse(reference.toString().contains("opaque"))
        assertFailsWith<IllegalArgumentException> { SecretReference.of("") }
        assertFailsWith<IllegalArgumentException> { SecretReference.of("x".repeat(513)) }
    }
}
