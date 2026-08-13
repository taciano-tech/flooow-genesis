package io.flooow.marketplace.operations.economics

import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarketplaceEconomicTruthTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val otherOrganizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000002")
    private val orderId = MarketplaceOrderId.parse("20000000-0000-0000-0000-000000000001")
    private val otherOrderId = MarketplaceOrderId.parse("20000000-0000-0000-0000-000000000002")
    private val occurredAt = Instant.parse("2026-08-13T12:00:00Z")
    private val brl = MarketplaceCurrency("BRL")

    @Test
    fun `compiled economics boundary contains no Kernel reference`() {
        val productionClasses = java.nio.file.Path.of(
            MarketplaceOrder::class.java.protectionDomain.codeSource.location.toURI()
        )
        val packageDirectory = productionClasses.resolve(
            "io/flooow/marketplace/operations/economics"
        )

        Files.walk(packageDirectory).use { files ->
            val classFiles = files.filter { it.toString().endsWith(".class") }.toList()
            assertTrue(classFiles.isNotEmpty())
            classFiles.forEach { classFile ->
                val bytecodeText = String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1)
                assertTrue(
                    "io/flooow/kernel" !in bytecodeText,
                    "Economics bytecode must not reference Kernel: ${classFile.fileName}"
                )
            }
        }
    }

    @Test
    fun `canonical identifiers and bounded values reject non-canonical input and render safely`() {
        assertFailsWith<IllegalArgumentException> {
            MarketplaceOrderId.parse("20000000-0000-0000-0000-00000000000A")
        }
        assertFailsWith<IllegalArgumentException> { MarketplaceKey(" Mercado-livre") }
        assertFailsWith<IllegalArgumentException> { MarketplaceKey("MERCADO_LIVRE") }
        assertFailsWith<IllegalArgumentException> { MarketplaceExternalOrderId("e\u0301") }
        assertFailsWith<IllegalArgumentException> { EconomicExternalReference("line\nreference") }
        assertFailsWith<IllegalArgumentException> { EconomicExternalReference("\uD800") }
        assertFailsWith<IllegalArgumentException> { MarketplaceCurrency("brl") }
        assertFailsWith<IllegalArgumentException> {
            EconomicCalculationPolicyVersion("marketplace truth 1")
        }

        assertEquals("[INTERNAL]", orderId.toString())
        assertEquals("[REDACTED]", MarketplaceKey("mercado-livre").toString())
        assertEquals("[REDACTED]", MarketplaceExternalOrderId("order-42").toString())
        assertEquals("[REDACTED]", EconomicSourceSystemKey("meli-br").toString())
        assertEquals("[REDACTED]", EconomicExternalReference("charge-42").toString())
        assertEquals("[REDACTED]", EconomicExternalReference("order-🚀").toString())
        assertEquals("[REDACTED]", brl.toString())
    }

    @Test
    fun `money is exact bounded numeric value and never accepts binary or exponent text`() {
        assertEquals(money("299.90"), money("299.9"))
        assertEquals(money("0"), money("-0.000000"))
        assertEquals(money("299.9").hashCode(), money("299.900000").hashCode())

        listOf("1e2", "+1", ".5", "01", "1.0000000", "NaN").forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { money(invalid) }
        }
        assertFailsWith<IllegalArgumentException> { money("1000000000000000000") }
        assertFailsWith<IllegalArgumentException> {
            component(1, EconomicComponentType.REVENUE, "-0.01")
        }
        assertEquals("[REDACTED]", money("64.81").toString())
    }

    @Test
    fun `external sources require stable references while internal origins may declare absence`() {
        assertFailsWith<IllegalArgumentException> {
            EconomicSource(
                kind = EconomicSourceKind.MARKETPLACE,
                systemKey = EconomicSourceSystemKey("meli-br"),
                externalReference = EconomicExternalReferenceState.Absent(
                    EconomicExternalReferenceAbsenceReason.INTERNAL_ORIGIN
                )
            )
        }

        val manual = EconomicSource(
            kind = EconomicSourceKind.MANUAL,
            systemKey = EconomicSourceSystemKey("operations-desk"),
            externalReference = EconomicExternalReferenceState.Absent(
                EconomicExternalReferenceAbsenceReason.INTERNAL_ORIGIN
            )
        )
        assertIs<EconomicExternalReferenceState.Absent>(manual.externalReference)
        assertEquals("[REDACTED]", manual.toString())
    }

    @Test
    fun `order rejects foreign ownership order and currency`() {
        val revenue = component(1, EconomicComponentType.REVENUE, "10")

        assertFailsWith<IllegalArgumentException> {
            order(listOf(revenue.copy(organizationId = otherOrganizationId)))
        }
        assertFailsWith<IllegalArgumentException> {
            order(listOf(revenue.copy(orderId = otherOrderId)))
        }
        assertFailsWith<IllegalArgumentException> {
            order(
                listOf(
                    revenue.copy(
                        magnitude = MarketplaceMoney.parse(MarketplaceCurrency("USD"), "10")
                    )
                )
            )
        }
    }

    @Test
    fun `order rejects duplicate component identities and present source facts`() {
        val revenue = component(1, EconomicComponentType.REVENUE, "10")
        val sameId = component(1, EconomicComponentType.REVENUE, "20", referenceNumber = 2)
        assertFailsWith<IllegalArgumentException> { order(listOf(revenue, sameId)) }

        val sameFact = component(2, EconomicComponentType.REVENUE, "20", referenceNumber = 1)
        assertFailsWith<IllegalArgumentException> { order(listOf(revenue, sameFact)) }

        val otherTypeSameReference = component(
            3,
            EconomicComponentType.SHIPPING,
            "2",
            referenceNumber = 1
        )
        order(listOf(revenue, otherTypeSameReference))
    }

    @Test
    fun `coverage must classify every type and agree with supplied facts`() {
        val revenue = component(1, EconomicComponentType.REVENUE, "10")
        val missingKey = coverageFor(listOf(revenue)).apply {
            remove(EconomicComponentType.TAX)
        }
        assertFailsWith<IllegalArgumentException> {
            order(listOf(revenue), missingKey)
        }

        val absentButComplete = coverageFor(listOf(revenue)).apply {
            this[EconomicComponentType.TAX] = EconomicComponentCoverage.COMPLETE
        }
        assertFailsWith<IllegalArgumentException> {
            order(listOf(revenue), absentButComplete)
        }

        val presentButMissing = coverageFor(listOf(revenue)).apply {
            this[EconomicComponentType.REVENUE] = EconomicComponentCoverage.MISSING
        }
        assertFailsWith<IllegalArgumentException> {
            order(listOf(revenue), presentButMissing)
        }

        val revenueNotApplicable = coverageFor(listOf(revenue)).apply {
            this[EconomicComponentType.REVENUE] = EconomicComponentCoverage.NOT_APPLICABLE
        }
        assertFailsWith<IllegalArgumentException> {
            order(emptyList(), revenueNotApplicable)
        }
    }

    @Test
    fun `acceptance fixture explains exact contribution and margin`() {
        val calculation = assertIs<MarketplaceEconomicTruthCalculationResult.Complete>(
            MarketplaceEconomicTruthCalculator.calculate(acceptanceOrder())
        )
        val result = calculation.result

        assertEquals(money("299.90"), result.grossRevenue)
        assertEquals(money("41.99"), result.totalMarketplaceFees)
        assertEquals(money("18.40"), result.totalShipping)
        assertEquals(money("7.20"), result.totalAdvertising)
        assertEquals(money("24.30"), result.totalTaxes)
        assertEquals(money("143.20"), result.totalProductCost)
        assertEquals(money("0"), result.totalFinancialCost)
        assertEquals(money("0"), result.totalOtherAdjustments)
        assertEquals(money("64.81"), result.contribution)
        val margin = assertIs<ContributionMargin.Defined>(result.contributionMargin)
        assertEquals(BigDecimal("0.21610537"), margin.decimalValue)
        assertEquals(MarketplaceEconomicTruthQuality.CONFIRMED, result.truthQuality)
        assertEquals(
            EconomicCalculationPolicyVersion("marketplace-economic-truth/1"),
            result.calculationPolicyVersion
        )
    }

    @Test
    fun `not applicable is exact zero while missing blocks economic truth`() {
        val revenue = component(1, EconomicComponentType.REVENUE, "100")
        val complete = assertIs<MarketplaceEconomicTruthCalculationResult.Complete>(
            MarketplaceEconomicTruthCalculator.calculate(order(listOf(revenue)))
        )
        assertEquals(money("0"), complete.result.totalAdvertising)

        val missingCoverage = coverageFor(listOf(revenue)).apply {
            this[EconomicComponentType.ADVERTISING] = EconomicComponentCoverage.MISSING
        }
        val incomplete = assertIs<MarketplaceEconomicTruthCalculationResult.Incomplete>(
            MarketplaceEconomicTruthCalculator.calculate(order(listOf(revenue), missingCoverage))
        )
        assertEquals(listOf(EconomicComponentType.ADVERTISING), incomplete.missingTypes)
        assertTrue(incomplete.partialTypes.isEmpty())
        assertEquals(listOf(revenue), incomplete.suppliedComponents)
    }

    @Test
    fun `partial facts block totals while preserving exact canonical provenance`() {
        val revenue = component(1, EconomicComponentType.REVENUE, "100")
        val fee = component(2, EconomicComponentType.MARKETPLACE_FEE, "10")
        val components = listOf(fee, revenue)
        val partialCoverage = coverageFor(components).apply {
            this[EconomicComponentType.MARKETPLACE_FEE] = EconomicComponentCoverage.PARTIAL
        }

        val incomplete = assertIs<MarketplaceEconomicTruthCalculationResult.Incomplete>(
            MarketplaceEconomicTruthCalculator.calculate(order(components, partialCoverage))
        )
        assertEquals(listOf(EconomicComponentType.MARKETPLACE_FEE), incomplete.partialTypes)
        assertSame(revenue, incomplete.suppliedComponents[0])
        assertSame(fee, incomplete.suppliedComponents[1])
    }

    @Test
    fun `explicit zero revenue produces typed undefined margin`() {
        val calculation = assertIs<MarketplaceEconomicTruthCalculationResult.Complete>(
            MarketplaceEconomicTruthCalculator.calculate(
                order(listOf(component(1, EconomicComponentType.REVENUE, "0")))
            )
        )
        val margin = assertIs<ContributionMargin.Undefined>(calculation.result.contributionMargin)
        assertEquals(
            ContributionMarginUndefinedReason.NON_POSITIVE_GROSS_REVENUE,
            margin.reason
        )
    }

    @Test
    fun `costs may produce an exact negative contribution`() {
        val components = listOf(
            component(1, EconomicComponentType.REVENUE, "10"),
            component(2, EconomicComponentType.PRODUCT_COST, "12")
        )
        val result = assertIs<MarketplaceEconomicTruthCalculationResult.Complete>(
            MarketplaceEconomicTruthCalculator.calculate(order(components))
        ).result

        assertEquals(money("-2"), result.contribution)
        assertEquals(BigDecimal("-0.20000000"),
            assertIs<ContributionMargin.Defined>(result.contributionMargin).decimalValue)
    }

    @Test
    fun `additions deductions reversals and multiple facts net exactly`() {
        val components = listOf(
            component(1, EconomicComponentType.REVENUE, "100"),
            component(2, EconomicComponentType.REVENUE, "20"),
            component(
                3,
                EconomicComponentType.REVENUE,
                "5",
                direction = EconomicDirection.DEDUCTION
            ),
            component(4, EconomicComponentType.MARKETPLACE_FEE, "10"),
            component(
                5,
                EconomicComponentType.MARKETPLACE_FEE,
                "2",
                direction = EconomicDirection.ADDITION
            )
        )
        val result = assertIs<MarketplaceEconomicTruthCalculationResult.Complete>(
            MarketplaceEconomicTruthCalculator.calculate(order(components))
        ).result

        assertEquals(money("115"), result.grossRevenue)
        assertEquals(money("8"), result.totalMarketplaceFees)
        assertEquals(money("107"), result.contribution)
    }

    @Test
    fun `estimated component derives estimated truth without a score`() {
        val result = assertIs<MarketplaceEconomicTruthCalculationResult.Complete>(
            MarketplaceEconomicTruthCalculator.calculate(
                order(
                    listOf(
                        component(
                            1,
                            EconomicComponentType.REVENUE,
                            "10",
                            quality = EconomicEvidenceQuality.ESTIMATED
                        )
                    )
                )
            )
        ).result

        assertEquals(MarketplaceEconomicTruthQuality.ESTIMATED, result.truthQuality)
    }

    @Test
    fun `component input order is irrelevant and canonical objects are preserved`() {
        val unsignedHigh = component(
            1,
            EconomicComponentType.REVENUE,
            "10",
            id = EconomicComponentId.parse("80000000-0000-0000-0000-000000000000")
        )
        val unsignedLow = component(
            2,
            EconomicComponentType.SHIPPING,
            "1",
            id = EconomicComponentId.parse("7fffffff-ffff-ffff-ffff-ffffffffffff")
        )

        val first = order(listOf(unsignedHigh, unsignedLow))
        val second = order(listOf(unsignedLow, unsignedHigh))
        assertEquals(first, second)
        assertSame(unsignedLow, first.components[0])
        assertSame(unsignedHigh, first.components[1])

        val firstResult = MarketplaceEconomicTruthCalculator.calculate(first)
        val secondResult = MarketplaceEconomicTruthCalculator.calculate(second)
        assertEquals(firstResult, secondResult)
        val complete = assertIs<MarketplaceEconomicTruthCalculationResult.Complete>(firstResult)
        assertSame(unsignedLow, complete.result.components[0])
        assertSame(unsignedHigh, complete.result.components[1])
    }

    @Test
    fun `order copies caller collections`() {
        val revenue = component(1, EconomicComponentType.REVENUE, "10")
        val mutableComponents = mutableListOf(revenue)
        val mutableCoverage = coverageFor(mutableComponents)
        val normalized = order(mutableComponents, mutableCoverage)

        mutableComponents.clear()
        mutableCoverage.clear()

        assertEquals(listOf(revenue), normalized.components)
        assertEquals(EconomicComponentType.entries.size, normalized.coverage.size)
    }

    @Test
    fun `aggregate renderings disclose no economic or organizational values`() {
        val normalized = acceptanceOrder()
        val calculation = MarketplaceEconomicTruthCalculator.calculate(normalized)
        val complete = assertIs<MarketplaceEconomicTruthCalculationResult.Complete>(calculation)

        listOf(
            normalized.toString(),
            normalized.components.first().toString(),
            complete.toString(),
            complete.result.toString(),
            complete.result.contributionMargin.toString()
        ).forEach { rendering ->
            assertEquals("[REDACTED]", rendering)
            assertNotEquals(organizationId.value.toString(), rendering)
        }
    }

    private fun acceptanceOrder(): MarketplaceOrder {
        val components = listOf(
            component(1, EconomicComponentType.REVENUE, "299.90"),
            component(2, EconomicComponentType.MARKETPLACE_COMMISSION, "41.99"),
            component(3, EconomicComponentType.SHIPPING, "18.40"),
            component(4, EconomicComponentType.ADVERTISING, "7.20"),
            component(5, EconomicComponentType.TAX, "24.30"),
            component(6, EconomicComponentType.PRODUCT_COST, "143.20")
        )
        return order(components)
    }

    private fun order(
        components: Collection<EconomicComponent>,
        coverage: Map<EconomicComponentType, EconomicComponentCoverage> = coverageFor(components)
    ): MarketplaceOrder = MarketplaceOrder(
        organizationId = organizationId,
        id = orderId,
        marketplace = MarketplaceKey("mercado-livre"),
        externalOrderId = MarketplaceExternalOrderId("order-2026-0001"),
        occurredAt = occurredAt,
        currency = brl,
        components = components,
        coverage = coverage
    )

    private fun coverageFor(
        components: Collection<EconomicComponent>
    ): MutableMap<EconomicComponentType, EconomicComponentCoverage> =
        EconomicComponentType.entries.associateWithTo(linkedMapOf()) { type ->
            if (components.any { it.type == type }) {
                EconomicComponentCoverage.COMPLETE
            } else {
                EconomicComponentCoverage.NOT_APPLICABLE
            }
        }

    private fun component(
        number: Int,
        type: EconomicComponentType,
        amount: String,
        direction: EconomicDirection = defaultDirection(type),
        quality: EconomicEvidenceQuality = EconomicEvidenceQuality.CONFIRMED,
        id: EconomicComponentId = componentId(number),
        referenceNumber: Int = number
    ): EconomicComponent = EconomicComponent(
        organizationId = organizationId,
        id = id,
        orderId = orderId,
        type = type,
        direction = direction,
        magnitude = money(amount),
        source = EconomicSource(
            kind = EconomicSourceKind.MARKETPLACE,
            systemKey = EconomicSourceSystemKey("meli-br"),
            externalReference = EconomicExternalReferenceState.Present(
                EconomicExternalReference("fact-$referenceNumber")
            )
        ),
        occurredAt = occurredAt.plusSeconds(number.toLong()),
        quality = quality
    )

    private fun defaultDirection(type: EconomicComponentType): EconomicDirection =
        if (type == EconomicComponentType.REVENUE) {
            EconomicDirection.ADDITION
        } else {
            EconomicDirection.DEDUCTION
        }

    private fun componentId(number: Int): EconomicComponentId = EconomicComponentId(
        UUID.fromString("30000000-0000-0000-0000-${number.toString().padStart(12, '0')}")
    )

    private fun money(amount: String): MarketplaceMoney = MarketplaceMoney.parse(brl, amount)
}
