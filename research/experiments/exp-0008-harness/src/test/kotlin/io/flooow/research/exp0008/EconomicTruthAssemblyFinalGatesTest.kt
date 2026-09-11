package io.flooow.research.exp0008

import io.flooow.marketplace.operations.economics.*
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EconomicTruthAssemblyFinalGatesTest {

    private val organizationId = OrganizationId.parse("70000000-0000-0000-0000-000000000001")
    private val orderId = MarketplaceOrderId.parse("80000000-0000-0000-0000-000000000001")
    private val currency = MarketplaceCurrency("BRL")
    private val occurredAt = Instant.parse("2026-08-13T12:00:00Z")

    @Test
    fun `gate 12 accepted economic truth control fixture remains exact`() {
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
    fun `gate 13 marketplace order result is deterministic under component insertion order`() {
        val components = acceptanceComponents()
        val coverage = coverageFor(components)

        val first = MarketplaceOrder(
            organizationId = organizationId,
            id = orderId,
            marketplace = MarketplaceKey("mercado-livre"),
            externalOrderId = MarketplaceExternalOrderId("order-final-gates"),
            occurredAt = occurredAt,
            currency = currency,
            components = components,
            coverage = coverage
        )

        val second = MarketplaceOrder(
            organizationId = organizationId,
            id = orderId,
            marketplace = MarketplaceKey("mercado-livre"),
            externalOrderId = MarketplaceExternalOrderId("order-final-gates"),
            occurredAt = occurredAt,
            currency = currency,
            components = components.reversed(),
            coverage = coverage
        )

        assertEquals(first, second)
        assertEquals(
            MarketplaceEconomicTruthCalculator.calculate(first),
            MarketplaceEconomicTruthCalculator.calculate(second)
        )
    }

    @Test
    fun `gate 14 unresolved coverage fails closed instead of manufacturing complete truth`() {
        val revenue = component(
            number = 1,
            type = EconomicComponentType.REVENUE,
            amount = "100.00",
            direction = EconomicDirection.ADDITION
        )

        val coverage = coverageFor(listOf(revenue)).apply {
            this[EconomicComponentType.ADVERTISING] = EconomicComponentCoverage.MISSING
        }

        val order = MarketplaceOrder(
            organizationId = organizationId,
            id = orderId,
            marketplace = MarketplaceKey("mercado-livre"),
            externalOrderId = MarketplaceExternalOrderId("order-fail-closed"),
            occurredAt = occurredAt,
            currency = currency,
            components = listOf(revenue),
            coverage = coverage
        )

        val result = assertIs<MarketplaceEconomicTruthCalculationResult.Incomplete>(
            MarketplaceEconomicTruthCalculator.calculate(order)
        )

        assertEquals(listOf(EconomicComponentType.ADVERTISING), result.missingTypes)
    }

    @Test
    fun `gate 14 invalid coverage cannot be used to force a marketplace order`() {
        val revenue = component(
            number = 2,
            type = EconomicComponentType.REVENUE,
            amount = "100.00",
            direction = EconomicDirection.ADDITION
        )

        val invalidCoverage = coverageFor(listOf(revenue)).apply {
            this[EconomicComponentType.FINANCIAL_COST] = EconomicComponentCoverage.COMPLETE
        }

        assertFailsWith<IllegalArgumentException> {
            MarketplaceOrder(
                organizationId = organizationId,
                id = orderId,
                marketplace = MarketplaceKey("mercado-livre"),
                externalOrderId = MarketplaceExternalOrderId("order-invalid-coverage"),
                occurredAt = occurredAt,
                currency = currency,
                components = listOf(revenue),
                coverage = invalidCoverage
            )
        }
    }

    private fun acceptanceOrder(): MarketplaceOrder {
        val components = acceptanceComponents()
        return MarketplaceOrder(
            organizationId = organizationId,
            id = orderId,
            marketplace = MarketplaceKey("mercado-livre"),
            externalOrderId = MarketplaceExternalOrderId("order-acceptance"),
            occurredAt = occurredAt,
            currency = currency,
            components = components,
            coverage = coverageFor(components)
        )
    }

    private fun acceptanceComponents(): List<EconomicComponent> = listOf(
        component(10, EconomicComponentType.REVENUE, "299.90", EconomicDirection.ADDITION),
        component(11, EconomicComponentType.MARKETPLACE_COMMISSION, "31.99", EconomicDirection.DEDUCTION),
        component(12, EconomicComponentType.MARKETPLACE_FEE, "10.00", EconomicDirection.DEDUCTION),
        component(13, EconomicComponentType.SHIPPING, "18.40", EconomicDirection.DEDUCTION),
        component(14, EconomicComponentType.ADVERTISING, "7.20", EconomicDirection.DEDUCTION),
        component(15, EconomicComponentType.TAX, "24.30", EconomicDirection.DEDUCTION),
        component(16, EconomicComponentType.PRODUCT_COST, "143.20", EconomicDirection.DEDUCTION)
    )

    private fun coverageFor(
        components: Collection<EconomicComponent>
    ): MutableMap<EconomicComponentType, EconomicComponentCoverage> =
        EconomicComponentType.entries.associateWith { type ->
            if (components.any { it.type == type })
                EconomicComponentCoverage.COMPLETE
            else
                EconomicComponentCoverage.NOT_APPLICABLE
        }.toMutableMap()

    private fun component(
        number: Int,
        type: EconomicComponentType,
        amount: String,
        direction: EconomicDirection
    ): EconomicComponent = EconomicComponent(
        organizationId = organizationId,
        id = EconomicComponentId(UUID.fromString(uuid(number))),
        orderId = orderId,
        type = type,
        direction = direction,
        magnitude = money(amount),
        source = EconomicSource(
            kind = EconomicSourceKind.MARKETPLACE,
            systemKey = EconomicSourceSystemKey("mercado-livre"),
            externalReference = EconomicExternalReferenceState.Present(
                EconomicExternalReference("fixture-$number")
            )
        ),
        occurredAt = occurredAt,
        quality = EconomicEvidenceQuality.CONFIRMED
    )

    private fun money(amount: String): MarketplaceMoney =
        MarketplaceMoney.parse(currency, amount)

    private fun uuid(value: Int): String =
        "00000000-0000-0000-0000-${value.toString().padStart(12, '0')}"
}
