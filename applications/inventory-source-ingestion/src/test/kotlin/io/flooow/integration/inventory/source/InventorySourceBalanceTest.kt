package io.flooow.integration.inventory.source

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class InventorySourceBalanceTest {
    @Test
    fun `source values normalize unicode and reject unsafe or oversized text`() {
        val normalized = SourceItemReference.of("Cafe\u0301")
        assertEquals("Café", normalized.encodedForPersistence())
        assertEquals(SourceItemReference.of("Café"), normalized)
        assertEquals("[REDACTED]", normalized.toString())
        listOf("", " item", "item ", "item\nvalue", "x".repeat(257)).forEach {
            assertFailsWith<IllegalArgumentException> { SourceItemReference.of(it) }
        }
    }

    @Test
    fun `signed decimal quantities normalize without rounding or clamping`() {
        assertEquals("-2.5", SourceQuantity.parse("-2.500000").canonicalValue())
        assertEquals(SourceQuantity.parse("-2.5"), SourceQuantity.parse("-2.500000"))
        assertEquals(BigDecimal.ZERO, SourceQuantity.parse("-0.000000").valueForPersistence())
        assertEquals("999999999999999999.999999",
            SourceQuantity.parse("999999999999999999.999999").canonicalValue())
        listOf("+1", "01", "1e2", "1.0000001", "1000000000000000000").forEach {
            assertFailsWith<IllegalArgumentException> { SourceQuantity.parse(it) }
        }
    }

    @Test
    fun `record requires one measure and redacts all source data`() {
        assertFailsWith<IllegalArgumentException> {
            InventorySourceBalanceRecord(SourceItemReference.of("remote-item"))
        }
        val record = InventorySourceBalanceRecord(
            SourceItemReference.of("remote-item"),
            sourceSku = SourceSku.of("merchant-sku"),
            onHand = SourceQuantity.parse("-1.25")
        )
        assertFalse(record.toString().contains("remote-item"))
        assertFalse(record.toString().contains("merchant-sku"))
    }
}
