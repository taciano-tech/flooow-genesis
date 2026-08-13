package io.flooow.marketplace.operations.economics.pricing

import io.flooow.marketplace.operations.economics.EconomicComponentCoverage
import io.flooow.marketplace.operations.economics.EconomicComponentType
import io.flooow.marketplace.operations.economics.EconomicDirection
import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import io.flooow.marketplace.operations.economics.EconomicExternalReference
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthQuality
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MarketplaceEconomicPricePositionTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val scenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val brl = MarketplaceCurrency("BRL")

    @Test
    fun `compiled position boundary contains no Kernel reference`() {
        val classes = java.nio.file.Path.of(
            MarketplaceEconomicPricePosition::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter { it.toString().endsWith(".class") }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
            }
        }
    }

    @Test
    fun `observation validates identity amount precision provenance and rendering`() {
        assertFailsWith<IllegalArgumentException> {
            EconomicPriceObservationId.parse("30000000-0000-0000-0000-00000000000A")
        }
        assertFailsWith<IllegalArgumentException> { observation("-0.01") }
        assertFailsWith<IllegalArgumentException> {
            observation("10", Instant.parse("2026-08-13T12:00:00.123456789Z"))
        }
        assertFailsWith<IllegalArgumentException> {
            EconomicSource(
                EconomicSourceKind.MARKETPLACE,
                EconomicSourceSystemKey("meli-br"),
                io.flooow.marketplace.operations.economics.EconomicExternalReferenceState.Absent(
                    io.flooow.marketplace.operations.economics.EconomicExternalReferenceAbsenceReason.INTERNAL_ORIGIN
                )
            )
        }
        assertEquals("[INTERNAL]", observation("10").id.toString())
        assertEquals("[REDACTED]", observation("10").toString())
    }

    @Test
    fun `ownership currency and quantum mismatches are controlled`() {
        val floor = floor()
        assertEquals(
            EconomicPricePositionResult.OwnershipMismatch,
            MarketplaceEconomicPricePosition.evaluate(
                floor,
                observation("299.90").copy(organizationId = OrganizationId(UUID(0, 9)))
            )
        )
        assertEquals(
            EconomicPricePositionResult.OwnershipMismatch,
            MarketplaceEconomicPricePosition.evaluate(
                floor,
                observation("299.90").copy(scenarioId = NetBackPricingScenarioId.of(UUID(0, 9)))
            )
        )
        assertEquals(
            EconomicPricePositionResult.CurrencyMismatch,
            MarketplaceEconomicPricePosition.evaluate(
                floor,
                observation("299.90").copy(
                    grossPrice = MarketplaceMoney.parse(MarketplaceCurrency("USD"), "299.90")
                )
            )
        )
        assertEquals(
            EconomicPricePositionResult.PriceQuantumMismatch,
            MarketplaceEconomicPricePosition.evaluate(floor, observation("299.901"))
        )
    }

    @Test
    fun `accepted fixture reproduces all four exact positions and gaps`() {
        val cases = listOf(
            Triple("220.00", EconomicPricePosition.BELOW_ABSOLUTE_FLOOR, "-79.90"),
            Triple("235.09", EconomicPricePosition.BELOW_ECONOMIC_FLOOR, "-64.81"),
            Triple("299.90", EconomicPricePosition.AT_ECONOMIC_FLOOR, "0"),
            Triple("310.00", EconomicPricePosition.ABOVE_ECONOMIC_FLOOR, "10.10")
        )
        cases.forEach { (price, position, economicGap) ->
            val assessment = assessed(floor(), observation(price))
            assertEquals(position, assessment.position)
            assertEquals(money(economicGap), assessment.economicFloorGap)
        }
        val below = assessed(floor(), observation("220.00"))
        assertEquals(money("-15.09"), below.absoluteFloorGap)
        val above = assessed(floor(), observation("310.00"))
        assertEquals(money("74.91"), above.absoluteFloorGap)
    }

    @Test
    fun `explicit zero is evidence and equal floors classify equality at economic floor`() {
        assertEquals(
            EconomicPricePosition.BELOW_ABSOLUTE_FLOOR,
            assessed(floor(), observation("0")).position
        )
        val equalFloor = floor(target = "0")
        assertEquals(equalFloor.absoluteFloor, equalFloor.economicFloor)
        assertEquals(
            EconomicPricePosition.AT_ECONOMIC_FLOOR,
            assessed(equalFloor, observation("235.09")).position
        )
    }

    @Test
    fun `quality is confirmed only when floor and observation are confirmed`() {
        assertEquals(
            MarketplaceEconomicTruthQuality.CONFIRMED,
            assessed(floor(), observation("299.90")).quality
        )
        assertEquals(
            MarketplaceEconomicTruthQuality.ESTIMATED,
            assessed(
                floor(),
                observation("299.90").copy(evidenceQuality = EconomicEvidenceQuality.ESTIMATED)
            ).quality
        )
        assertEquals(
            MarketplaceEconomicTruthQuality.ESTIMATED,
            assessed(floor(estimated = true), observation("299.90")).quality
        )
    }

    @Test
    fun `value equal inputs are deterministic and preserve observation evidence`() {
        val observation = observation("299.90")
        val first = MarketplaceEconomicPricePosition.evaluate(floor(), observation)
        val second = MarketplaceEconomicPricePosition.evaluate(floor(), observation.copy())
        assertEquals(first, second)
        val assessment = assertIs<EconomicPricePositionResult.Assessed>(first).assessment
        assertEquals(observation.id, assessment.observationId)
        assertEquals(observation.source, assessment.source)
        assertEquals(observation.occurredAt, assessment.observedAt)
    }

    @Test
    fun `aggregate rendering discloses no price or ownership`() {
        val result = MarketplaceEconomicPricePosition.evaluate(floor(), observation("299.90"))
        val assessment = assertIs<EconomicPricePositionResult.Assessed>(result).assessment
        val renderings = listOf(
            result.toString(), assessment.toString(),
            EconomicPricePositionResult.OwnershipMismatch.toString(),
            EconomicPricePositionResult.CurrencyMismatch.toString(),
            EconomicPricePositionResult.PriceQuantumMismatch.toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach {
            assertNotEquals("299.90", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    private fun assessed(floor: NetBackEconomicFloor, observation: ObservedMarketplacePrice) =
        assertIs<EconomicPricePositionResult.Assessed>(
            MarketplaceEconomicPricePosition.evaluate(floor, observation)
        ).assessment

    private fun floor(target: String = "64.81", estimated: Boolean = false): NetBackEconomicFloor {
        val components = listOf(
            component(1, EconomicComponentType.MARKETPLACE_COMMISSION, "41.99", estimated),
            component(2, EconomicComponentType.SHIPPING, "18.40"),
            component(3, EconomicComponentType.ADVERTISING, "7.20"),
            component(4, EconomicComponentType.TAX, "24.30"),
            component(5, EconomicComponentType.PRODUCT_COST, "143.20")
        )
        val coverage = EconomicComponentType.entries.filter { it != EconomicComponentType.REVENUE }
            .associateWith { type ->
                if (components.any { it.economicType == type }) EconomicComponentCoverage.COMPLETE
                else EconomicComponentCoverage.NOT_APPLICABLE
            }
        val profile = NetBackPricingProfile(
            organizationId, scenarioId, MarketplaceKey("mercado-livre"), brl, money("0.01"),
            NetBackNormalizationPolicyVersion("meli-rules/1"), components, coverage,
            NetBackContributionTarget.AbsoluteAmount(money(target))
        )
        return assertIs<NetBackCalculationResult.Complete>(
            MarketplaceNetBackEconomicFloor.calculate(profile)
        ).floor
    }

    private fun component(
        number: Int,
        type: EconomicComponentType,
        amount: String,
        estimated: Boolean = false
    ) = NetBackCostComponent(
        organizationId, scenarioId, NetBackCostComponentId.of(UUID(1, number.toLong())), type,
        EconomicDirection.DEDUCTION, NetBackCostValue.FixedAmount(money(amount)), source(number),
        if (estimated) EconomicEvidenceQuality.ESTIMATED else EconomicEvidenceQuality.CONFIRMED
    )

    private fun observation(
        price: String,
        occurredAt: Instant = Instant.parse("2026-08-13T12:00:00.123456Z")
    ) = ObservedMarketplacePrice(
        organizationId, scenarioId, EconomicPriceObservationId.of(UUID(2, 1)), money(price),
        source(99), occurredAt, EconomicEvidenceQuality.CONFIRMED
    )

    private fun source(number: Int) = EconomicSource(
        EconomicSourceKind.MARKETPLACE,
        EconomicSourceSystemKey("meli-br"),
        EconomicExternalReferenceState.Present(EconomicExternalReference("price-$number"))
    )

    private fun money(value: String) = MarketplaceMoney.parse(brl, value)
}
