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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MarketplaceNetBackEconomicFloorTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val scenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val brl = MarketplaceCurrency("BRL")

    @Test
    fun `compiled pricing boundary contains no Kernel reference`() {
        val classes = java.nio.file.Path.of(
            MarketplaceNetBackEconomicFloor::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            val classFiles = files.filter { it.toString().endsWith(".class") }.toList()
            assertTrue(classFiles.isNotEmpty())
            classFiles.forEach {
                val bytes = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in bytes)
            }
        }
    }

    @Test
    fun `canonical values reject malformed input and render safely`() {
        assertFailsWith<IllegalArgumentException> {
            NetBackPricingScenarioId.parse("20000000-0000-0000-0000-00000000000A")
        }
        listOf("-0.1", "1.1", ".1", "01", "1e-1", "0.123456789").forEach {
            assertFailsWith<IllegalArgumentException> { NetBackRate.parse(it) }
        }
        assertEquals(NetBackRate.parse("0.1"), NetBackRate.parse("0.10"))
        assertEquals(NetBackRate.parse("0.1").hashCode(), NetBackRate.parse("0.10").hashCode())
        assertFailsWith<IllegalArgumentException> {
            NetBackContributionTarget.MarginRate(NetBackRate.parse("1"))
        }
        assertEquals("[INTERNAL]", scenarioId.toString())
        assertEquals("[REDACTED]", NetBackRate.parse("0.2").toString())
        assertEquals("[REDACTED]", normalizationVersion().toString())
    }

    @Test
    fun `profile enforces ownership currency cost type identities provenance and coverage`() {
        val base = fixed(1, EconomicComponentType.PRODUCT_COST, "10")
        assertFailsWith<IllegalArgumentException> {
            NetBackCostComponent(
                organizationId, scenarioId, componentId(9), EconomicComponentType.REVENUE,
                EconomicDirection.DEDUCTION, NetBackCostValue.FixedAmount(money("1")),
                source(9), EconomicEvidenceQuality.CONFIRMED
            )
        }
        assertFailsWith<IllegalArgumentException> {
            profile(listOf(base.copy(organizationId = OrganizationId(UUID(0, 9)))))
        }
        assertFailsWith<IllegalArgumentException> {
            profile(listOf(base.copy(value = NetBackCostValue.FixedAmount(usd("10")))))
        }
        assertFailsWith<IllegalArgumentException> {
            profile(listOf(base, fixed(2, EconomicComponentType.PRODUCT_COST, "20", id = base.id)))
        }
        assertFailsWith<IllegalArgumentException> {
            profile(listOf(base, fixed(2, EconomicComponentType.PRODUCT_COST, "20", reference = 1)))
        }
        assertFailsWith<IllegalArgumentException> {
            profile(listOf(base), coverageFor(listOf(base)).also { it.remove(EconomicComponentType.TAX) })
        }
        assertFailsWith<IllegalArgumentException> {
            profile(listOf(base), coverageFor(listOf(base)).also {
                it[EconomicComponentType.TAX] = EconomicComponentCoverage.COMPLETE
            })
        }
        assertFailsWith<IllegalArgumentException> {
            profile(listOf(base), quantum = money("0"))
        }
    }

    @Test
    fun `profile copies and canonicalizes caller collections`() {
        val high = fixed(
            1, EconomicComponentType.PRODUCT_COST, "10",
            id = NetBackCostComponentId.parse("80000000-0000-0000-0000-000000000000")
        )
        val low = fixed(
            2, EconomicComponentType.SHIPPING, "2",
            id = NetBackCostComponentId.parse("7fffffff-ffff-ffff-ffff-ffffffffffff")
        )
        val components = mutableListOf(high, low)
        val coverage = coverageFor(components)
        val frozen = profile(components, coverage)
        components.clear()
        coverage.clear()
        assertEquals(listOf(low, high), frozen.components)
        assertEquals(EconomicComponentType.entries.size - 1, frozen.coverage.size)
    }

    @Test
    fun `incomplete coverage exposes no floor`() {
        val component = fixed(1, EconomicComponentType.PRODUCT_COST, "10")
        val coverage = coverageFor(listOf(component)).also {
            it[EconomicComponentType.PRODUCT_COST] = EconomicComponentCoverage.PARTIAL
            it[EconomicComponentType.TAX] = EconomicComponentCoverage.MISSING
        }
        val result = assertIs<NetBackCalculationResult.Incomplete>(
            MarketplaceNetBackEconomicFloor.calculate(profile(listOf(component), coverage))
        )
        assertEquals(listOf(EconomicComponentType.TAX), result.missingTypes)
        assertEquals(listOf(EconomicComponentType.PRODUCT_COST), result.partialTypes)
        assertEquals(listOf(component), result.suppliedComponents)
    }

    @Test
    fun `reverse economic truth fixture yields exact absolute and economic floors`() {
        val components = listOf(
            fixed(1, EconomicComponentType.MARKETPLACE_COMMISSION, "41.99"),
            fixed(2, EconomicComponentType.SHIPPING, "18.40"),
            fixed(3, EconomicComponentType.ADVERTISING, "7.20"),
            fixed(4, EconomicComponentType.TAX, "24.30"),
            fixed(5, EconomicComponentType.PRODUCT_COST, "143.20")
        )
        val floor = complete(
            profile(
                components,
                target = NetBackContributionTarget.AbsoluteAmount(money("64.81"))
            )
        )
        assertEquals(money("235.09"), floor.netFixedCost)
        assertEquals(money("235.09"), floor.absoluteFloor)
        assertEquals(money("299.90"), floor.economicFloor)
        assertEquals(NetBackSignedRate(java.math.BigDecimal.ZERO), floor.netVariableDeductionRate)
        assertEquals(MarketplaceEconomicTruthQuality.CONFIRMED, floor.truthQuality)
    }

    @Test
    fun `variable margin and absolute amount fixtures solve with conservative ceiling`() {
        val components = listOf(
            fixed(1, EconomicComponentType.PRODUCT_COST, "100"),
            rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.20")
        )
        val margin = complete(
            profile(components, target = NetBackContributionTarget.MarginRate(NetBackRate.parse("0.10")))
        )
        assertEquals(money("125"), margin.absoluteFloor)
        assertEquals(money("142.86"), margin.economicFloor)
        assertEquals(NetBackSignedRate(java.math.BigDecimal("0.2")), margin.netVariableDeductionRate)

        val amount = complete(
            profile(components, target = NetBackContributionTarget.AbsoluteAmount(money("20")))
        )
        assertEquals(money("150"), amount.economicFloor)
    }

    @Test
    fun `fixed and rate additions remain explicit subsidies`() {
        val components = listOf(
            fixed(1, EconomicComponentType.PRODUCT_COST, "100"),
            fixed(2, EconomicComponentType.OTHER_ADJUSTMENT, "10", EconomicDirection.ADDITION),
            rate(3, EconomicComponentType.MARKETPLACE_COMMISSION, "0.20"),
            rate(4, EconomicComponentType.OTHER_ADJUSTMENT, "0.05", EconomicDirection.ADDITION)
        )
        val floor = complete(profile(components))
        assertEquals(money("90"), floor.netFixedCost)
        assertEquals(NetBackSignedRate(java.math.BigDecimal("0.15")), floor.netVariableDeductionRate)
        assertEquals(money("105.89"), floor.absoluteFloor)
    }

    @Test
    fun `zero target aligned quantum and negative fixed net remain controlled`() {
        val aligned = complete(profile(listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "10"))))
        assertEquals(money("10"), aligned.absoluteFloor)
        assertEquals(aligned.absoluteFloor, aligned.economicFloor)

        val credit = fixed(
            2, EconomicComponentType.OTHER_ADJUSTMENT, "10", EconomicDirection.ADDITION
        )
        val negative = complete(profile(listOf(credit)))
        assertEquals(money("-10"), negative.netFixedCost)
        assertEquals(money("0"), negative.absoluteFloor)
        assertEquals(money("0"), negative.economicFloor)
    }

    @Test
    fun `nonpositive denominators and out of range values are typed`() {
        val impossibleAbsolute = MarketplaceNetBackEconomicFloor.calculate(
            profile(listOf(rate(1, EconomicComponentType.MARKETPLACE_COMMISSION, "1")))
        )
        assertEquals(
            NetBackUnachievableReason.NON_POSITIVE_ABSOLUTE_DENOMINATOR,
            assertIs<NetBackCalculationResult.Unachievable>(impossibleAbsolute).reason
        )

        val impossibleMargin = MarketplaceNetBackEconomicFloor.calculate(
            profile(
                listOf(rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.8")),
                target = NetBackContributionTarget.MarginRate(NetBackRate.parse("0.2"))
            )
        )
        assertEquals(
            NetBackUnachievableReason.NON_POSITIVE_ECONOMIC_DENOMINATOR,
            assertIs<NetBackCalculationResult.Unachievable>(impossibleMargin).reason
        )

        val outOfRange = MarketplaceNetBackEconomicFloor.calculate(
            profile(
                listOf(fixed(3, EconomicComponentType.PRODUCT_COST, "999999999999999999.999999")),
                target = NetBackContributionTarget.AbsoluteAmount(money("1"))
            )
        )
        assertEquals(
            NetBackUnachievableReason.FLOOR_OUT_OF_RANGE,
            assertIs<NetBackCalculationResult.Unachievable>(outOfRange).reason
        )
    }

    @Test
    fun `estimated provenance is preserved and input order is deterministic`() {
        val confirmed = fixed(1, EconomicComponentType.PRODUCT_COST, "10")
        val estimated = fixed(2, EconomicComponentType.SHIPPING, "2").copy(
            evidenceQuality = EconomicEvidenceQuality.ESTIMATED
        )
        val first = complete(profile(listOf(estimated, confirmed)))
        val second = complete(profile(listOf(confirmed, estimated)))
        assertEquals(first, second)
        assertEquals(MarketplaceEconomicTruthQuality.ESTIMATED, first.truthQuality)
        assertEquals(listOf(confirmed, estimated), first.components)
    }

    @Test
    fun `aggregate rendering never exposes economics or ownership`() {
        val profile = profile(listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "10")))
        val result = MarketplaceNetBackEconomicFloor.calculate(profile)
        val complete = assertIs<NetBackCalculationResult.Complete>(result)
        val renderings = listOf(
            profile.toString(), profile.components.single().toString(), profile.target.toString(),
            result.toString(), complete.floor.toString(), complete.floor.netFixedCost.toString(),
            NetBackCalculationResult.Unachievable(
                NetBackUnachievableReason.FLOOR_OUT_OF_RANGE,
                normalizationVersion(), MarketplaceNetBackEconomicFloor.POLICY_VERSION
            ).toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach { assertNotEquals(organizationId.value.toString(), it) }
    }

    private fun complete(profile: NetBackPricingProfile) =
        assertIs<NetBackCalculationResult.Complete>(
            MarketplaceNetBackEconomicFloor.calculate(profile)
        ).floor

    private fun profile(
        components: Collection<NetBackCostComponent>,
        coverage: Map<EconomicComponentType, EconomicComponentCoverage> = coverageFor(components),
        quantum: MarketplaceMoney = money("0.01"),
        target: NetBackContributionTarget = NetBackContributionTarget.AbsoluteAmount(money("0"))
    ) = NetBackPricingProfile(
        organizationId, scenarioId, MarketplaceKey("mercado-livre"), brl, quantum,
        normalizationVersion(), components, coverage, target
    )

    private fun coverageFor(
        components: Collection<NetBackCostComponent>
    ): MutableMap<EconomicComponentType, EconomicComponentCoverage> =
        EconomicComponentType.entries.filter { it != EconomicComponentType.REVENUE }
            .associateWithTo(linkedMapOf()) { type ->
                if (components.any { it.economicType == type }) {
                    EconomicComponentCoverage.COMPLETE
                } else EconomicComponentCoverage.NOT_APPLICABLE
            }

    private fun fixed(
        number: Int,
        type: EconomicComponentType,
        amount: String,
        direction: EconomicDirection = EconomicDirection.DEDUCTION,
        id: NetBackCostComponentId = componentId(number),
        reference: Int = number
    ) = component(
        number, type, direction, NetBackCostValue.FixedAmount(money(amount)), id, reference
    )

    private fun rate(
        number: Int,
        type: EconomicComponentType,
        value: String,
        direction: EconomicDirection = EconomicDirection.DEDUCTION
    ) = component(number, type, direction, NetBackCostValue.RevenueRate(NetBackRate.parse(value)))

    private fun component(
        number: Int,
        type: EconomicComponentType,
        direction: EconomicDirection,
        value: NetBackCostValue,
        id: NetBackCostComponentId = componentId(number),
        reference: Int = number
    ) = NetBackCostComponent(
        organizationId, scenarioId, id, type, direction, value,
        source(reference), EconomicEvidenceQuality.CONFIRMED
    )

    private fun source(number: Int) = EconomicSource(
        EconomicSourceKind.MARKETPLACE,
        EconomicSourceSystemKey("meli-br"),
        EconomicExternalReferenceState.Present(EconomicExternalReference("fact-$number"))
    )

    private fun componentId(number: Int) = NetBackCostComponentId.of(UUID(1, number.toLong()))
    private fun normalizationVersion() = NetBackNormalizationPolicyVersion("meli-rules/1")
    private fun money(value: String) = MarketplaceMoney.parse(brl, value)
    private fun usd(value: String) = MarketplaceMoney.parse(MarketplaceCurrency("USD"), value)
}
