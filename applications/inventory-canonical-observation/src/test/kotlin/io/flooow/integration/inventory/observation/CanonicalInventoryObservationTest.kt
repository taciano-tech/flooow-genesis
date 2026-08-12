package io.flooow.integration.inventory.observation

import io.flooow.integration.inventory.mapping.QuantityFactor
import io.flooow.integration.inventory.source.SourceQuantity
import java.math.BigInteger
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CanonicalInventoryObservationTest {
    @Test
    fun `exact conversion reduces rational without decimal division`() {
        val third = ExactInventoryQuantity.from(
            SourceQuantity.parse("1"), QuantityFactor.of(1, 3)
        )
        assertEquals(BigInteger.ONE, third.numeratorForPersistence())
        assertEquals(3, third.denominatorForPersistence())

        val negative = ExactInventoryQuantity.from(
            SourceQuantity.parse("-12.50"), QuantityFactor.of(2, 5)
        )
        assertEquals(BigInteger.valueOf(-5), negative.numeratorForPersistence())
        assertEquals(1, negative.denominatorForPersistence())
    }

    @Test
    fun `zero canonicalizes and null measures remain independent`() {
        val zero = ExactInventoryQuantity.from(
            SourceQuantity.parse("0.000000"), QuantityFactor.of(999_999_937, 1_000_000_000)
        )
        assertEquals(BigInteger.ZERO, zero.numeratorForPersistence())
        assertEquals(1, zero.denominatorForPersistence())
        val measures = CanonicalInventoryMeasures(reserved = zero)
        assertEquals(null, measures.availableToSell)
        assertEquals(zero, measures.reserved)
        assertFailsWith<IllegalArgumentException> { CanonicalInventoryMeasures() }
    }

    @Test
    fun `identifiers and values redact textual rendering`() {
        val canonical = "00000000-0000-0000-0000-000000000001"
        val id = CanonicalInventoryObservationId.parse(canonical)
        assertEquals(UUID.fromString(canonical), id.valueForPersistence())
        assertEquals("[INTERNAL]", id.toString())
        assertEquals(
            "[REDACTED]",
            ExactInventoryQuantity.fromPersistence(BigInteger.ONE, 3).toString()
        )
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventoryObservationId.parse("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")
        }
        assertFalse(id.toString().contains(canonical))
    }

    @Test
    fun `persistence construction enforces exact bounds and reduction`() {
        assertEquals(
            ExactInventoryQuantity.fromPersistence(BigInteger.ONE, 3),
            ExactInventoryQuantity.fromPersistence(BigInteger.valueOf(2), 6)
        )
        assertFailsWith<IllegalArgumentException> {
            ExactInventoryQuantity.fromPersistence(BigInteger.ONE, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ExactInventoryQuantity.fromPersistence(BigInteger.TEN.pow(34), 1)
        }
    }
}
