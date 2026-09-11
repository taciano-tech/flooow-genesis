package io.flooow.marketplace.operations.economics.provider

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class ProviderEconomicEvidenceRecordsTest {
    @Test
    fun `provider values are bounded exact and redacted`() {
        val decimal = ProviderSourceDecimal.parse("21.817094")
        assertEquals("21.817094", decimal.canonicalValue())
        assertEquals("[REDACTED]", decimal.toString())

        val record = OmieProductCostSourceRecord(
            OmieProductReference.of("3415304571"),
            OmieIntegrationReference.of("integration-1"),
            OmieDisplayedProductCode.of("SKU-1"),
            OmieLocationReference.of("3415174133"),
            decimal,
            ProviderSourceDecimal.parse("13"),
            ProviderSourceDecimal.parse("13"),
            ProviderSourceDecimal.parse("0"),
            LocalDate.parse("2026-09-06"),
            Instant.parse("2026-09-06T14:00:00Z")
        )

        assertFalse(record.toString().contains("3415304571"))
        assertFalse(record.toString().contains("21.817094"))
        assertEquals(
            MarketplaceEconomicProductCostCapability.VALUE,
            MarketplaceEconomicProductCostCapability.KEY.value
        )
    }

    @Test
    fun `provider decimal rejects exponent excessive scale and binary style input`() {
        assertFails { ProviderSourceDecimal.parse("1e3") }
        assertFails { ProviderSourceDecimal.parse("0.0000001") }
        assertFails { ProviderSourceDecimal.parse("+1") }
        assertFails { ProviderSourceDecimal.parse("01") }
    }

    @Test
    fun `provider text rejects blank control and oversized values`() {
        assertFails { OmieProductReference.of("") }
        assertFails { OmieProductReference.of(" a") }
        assertFails { OmieDisplayedProductCode.of("bad\nvalue") }
        assertFails { OmieIntegrationReference.of("x".repeat(61)) }
    }
}