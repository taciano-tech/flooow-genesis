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
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarketplaceNetBackCostBasisFloorDeltaTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val sourceScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val targetScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000002")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val each = PricingCostUnitKey("each")
    private val evaluatedAt = Instant.parse("2026-08-15T13:00:00.123456Z")

    @Test
    fun `projection bytecode contains no Kernel reference and accepts only complete source floor`() {
        val classes = java.nio.file.Path.of(
            MarketplaceNetBackCostBasisFloorDelta::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter {
                it.fileName.toString().contains("NetBackCostBasisFloorDelta") &&
                    it.toString().endsWith(".class")
            }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
            }
        }

        val method = MarketplaceNetBackCostBasisFloorDelta::class.java.declaredMethods
            .single { it.name == "calculate" }
        assertContentEquals(arrayOf(NetBackSourceScenarioFloor::class.java), method.parameterTypes)
        assertEquals(NetBackCostBasisFloorDelta::class.java, method.returnType)
    }

    @Test
    fun `accepted fixture returns exact negative deltas and retains both floors`() {
        val input = completeSourceScenarioFloor(sourceProductCost = "143.20")
        val sourceSnapshot = input.sourceFloor
        val derivedSnapshot = input.appliedScenarioFloor.floor

        val result = MarketplaceNetBackCostBasisFloorDelta.calculate(input)

        assertSame(input, result.sourceScenarioFloor)
        assertEquals(money("-95.20"), result.absoluteFloorDelta)
        assertEquals(money("-95.20"), result.economicFloorDelta)
        assertSame(sourceSnapshot, input.sourceFloor)
        assertSame(derivedSnapshot, input.appliedScenarioFloor.floor)
        assertEquals(money("143.20"), input.sourceFloor.absoluteFloor)
        assertEquals(money("48.00"), input.appliedScenarioFloor.floor.absoluteFloor)
    }

    @Test
    fun `equal source and selected Product Cost returns exact zero deltas`() {
        val result = MarketplaceNetBackCostBasisFloorDelta.calculate(
            completeSourceScenarioFloor(sourceProductCost = "48.00")
        )

        assertEquals(money("0"), result.absoluteFloorDelta)
        assertEquals(money("0"), result.economicFloorDelta)
    }

    @Test
    fun `higher selected Product Cost returns exact positive deltas`() {
        val result = MarketplaceNetBackCostBasisFloorDelta.calculate(
            completeSourceScenarioFloor(sourceProductCost = "30.00")
        )

        assertEquals(money("18.00"), result.absoluteFloorDelta)
        assertEquals(money("18.00"), result.economicFloorDelta)
    }

    @Test
    fun `absolute and economic deltas use their matching floors independently`() {
        val input = completeSourceScenarioFloor(
            sourceProductCost = "100.00",
            additionalComponents = listOf(
                rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.20")
            ),
            target = NetBackContributionTarget.MarginRate(NetBackRate.parse("0.10"))
        )

        val result = MarketplaceNetBackCostBasisFloorDelta.calculate(input)

        assertEquals(money("125.00"), input.sourceFloor.absoluteFloor)
        assertEquals(money("142.86"), input.sourceFloor.economicFloor)
        assertEquals(money("60.00"), input.appliedScenarioFloor.floor.absoluteFloor)
        assertEquals(money("68.58"), input.appliedScenarioFloor.floor.economicFloor)
        assertEquals(money("-65.00"), result.absoluteFloorDelta)
        assertEquals(money("-74.28"), result.economicFloorDelta)
        assertNotEquals(result.absoluteFloorDelta, result.economicFloorDelta)
    }

    @Test
    fun `source derived evidence unit currency and policy lineage remain exact`() {
        val input = completeSourceScenarioFloor(sourceProductCost = "143.20")
        val sourceProduct = input.appliedScenarioFloor.appliedScenario.originalProductCostComponent
        val derivedProduct = input.appliedScenarioFloor.appliedScenario.appliedProductCostComponent

        val result = MarketplaceNetBackCostBasisFloorDelta.calculate(input)

        assertEquals(brl, result.absoluteFloorDelta.currency)
        assertEquals(brl, result.economicFloorDelta.currency)
        assertEquals(each, result.sourceScenarioFloor.sourceFloor.unitKey)
        assertEquals(
            input.sourceFloor.normalizationPolicyVersion,
            input.appliedScenarioFloor.floor.normalizationPolicyVersion
        )
        assertEquals(
            input.sourceFloor.calculationPolicyVersion,
            input.appliedScenarioFloor.floor.calculationPolicyVersion
        )
        assertEquals(sourceProduct, input.sourceFloor.components.single {
            it.economicType == EconomicComponentType.PRODUCT_COST
        })
        assertEquals(derivedProduct, input.appliedScenarioFloor.floor.components.single {
            it.economicType == EconomicComponentType.PRODUCT_COST
        })
        assertNotEquals(sourceProduct.source, derivedProduct.source)
    }

    @Test
    fun `internal construction rejects either mismatched delta`() {
        val input = completeSourceScenarioFloor(sourceProductCost = "143.20")
        val exact = MarketplaceNetBackCostBasisFloorDelta.calculate(input)

        assertFailsWith<IllegalArgumentException> {
            NetBackCostBasisFloorDelta(input, money("-1.00"), exact.economicFloorDelta)
        }
        assertFailsWith<IllegalArgumentException> {
            NetBackCostBasisFloorDelta(input, exact.absoluteFloorDelta, money("-1.00"))
        }
    }

    @Test
    fun `component permutations are deterministic and inputs remain unchanged`() {
        val product = fixed(1, EconomicComponentType.PRODUCT_COST, "143.20")
        val commission = rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.17")
        val firstComponents = listOf(product, commission)
        val secondComponents = listOf(commission, product)
        val first = completeSourceScenarioFloor(components = firstComponents)
        val second = completeSourceScenarioFloor(components = secondComponents)
        val firstSnapshot = first.appliedScenarioFloor.appliedScenario.sourceProfile.components.toList()
        val secondSnapshot = second.appliedScenarioFloor.appliedScenario.sourceProfile.components.toList()

        assertEquals(
            MarketplaceNetBackCostBasisFloorDelta.calculate(first),
            MarketplaceNetBackCostBasisFloorDelta.calculate(second)
        )
        assertEquals(firstSnapshot, first.appliedScenarioFloor.appliedScenario.sourceProfile.components)
        assertEquals(secondSnapshot, second.appliedScenarioFloor.appliedScenario.sourceProfile.components)
    }

    @Test
    fun `aggregate exposes facts only and all new renderings are redacted`() {
        val result = MarketplaceNetBackCostBasisFloorDelta.calculate(
            completeSourceScenarioFloor(sourceProductCost = "143.20")
        )

        assertEquals(
            setOf("sourceScenarioFloor", "absoluteFloorDelta", "economicFloorDelta"),
            NetBackCostBasisFloorDelta::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet()
        )
        val bytecode = String(
            Files.readAllBytes(
                java.nio.file.Path.of(
                    NetBackCostBasisFloorDelta::class.java.protectionDomain.codeSource.location.toURI()
                ).resolve(
                    "io/flooow/marketplace/operations/economics/pricing/NetBackCostBasisFloorDelta.class"
                )
            ),
            StandardCharsets.ISO_8859_1
        )
        listOf("Percentage", "Ratio", "Direction", "Classification", "Recommendation").forEach {
            assertTrue(it !in bytecode)
        }
        assertEquals("[REDACTED]", result.toString())
        assertEquals("[REDACTED]", MarketplaceNetBackCostBasisFloorDelta.toString())
        assertNotEquals("-95.20", result.toString())
        assertNotEquals(organizationId.value.toString(), result.toString())
    }

    private fun completeSourceScenarioFloor(
        sourceProductCost: String = "143.20",
        additionalComponents: List<NetBackCostComponent> = emptyList(),
        target: NetBackContributionTarget = NetBackContributionTarget.AbsoluteAmount(money("0")),
        components: List<NetBackCostComponent> =
            listOf(fixed(1, EconomicComponentType.PRODUCT_COST, sourceProductCost)) +
                additionalComponents
    ): NetBackSourceScenarioFloor {
        val sourceProfile = profile(components, target)
        val selection = assertSelectedCurrentCost()
        val application = (MarketplaceNetBackCostBasisScenarioApplication.apply(
            sourceProfile,
            selection,
            targetScenarioId,
            NetBackCostBasisApplicationPolicy(
                NetBackCostBasisApplicationPolicyVersion("net-back-cost-application/1"),
                Duration.ofDays(31)
            ),
            evaluatedAt
        ) as NetBackCostBasisScenarioApplicationResult.Applied).appliedScenario
        val appliedFloor = (MarketplaceNetBackAppliedScenarioFloor.calculate(application)
            as NetBackAppliedScenarioFloorResult.Calculated).evaluation
        return (MarketplaceNetBackSourceScenarioFloor.calculate(appliedFloor)
            as NetBackSourceScenarioFloorResult.Calculated).evaluation
    }

    private fun assertSelectedCurrentCost(): PricingProductCostBasisSelection {
        val evidences = listOf(
            costEvidence(PricingProductCostBasis.HISTORICAL_ACQUISITION, "41.00", -90),
            costEvidence(PricingProductCostBasis.CURRENT_REPLACEMENT, "48.00", 0),
            costEvidence(PricingProductCostBasis.FORWARD_REPLACEMENT, "52.00", 90)
        )
        val assessment = (MarketplacePricingProductCostBasis.evaluate(
            evidences,
            PricingCostBasisPolicy(
                PricingCostBasisPolicyVersion("pricing-cost-basis/1"),
                Duration.ofDays(30),
                Duration.ofDays(180)
            ),
            evaluatedAt
        ) as PricingProductCostBasisResult.Assessed).assessment
        return (MarketplacePricingProductCostBasisSelection.select(
            assessment,
            PricingCostBasisSelectionPolicy(
                PricingCostBasisSelectionPolicyVersion("pricing-cost-selection/1"),
                PricingProductCostBasis.CURRENT_REPLACEMENT,
                Duration.ofDays(31)
            ),
            evaluatedAt
        ) as PricingProductCostBasisSelectionResult.Selected).selection
    }

    private fun costEvidence(
        basis: PricingProductCostBasis,
        cost: String,
        dayOffset: Long
    ) = PricingProductCostEvidence(
        organizationId,
        sourceScenarioId,
        marketplace,
        PricingProductCostEvidenceId.of(UUID(3, (basis.ordinal + 1).toLong())),
        each,
        basis,
        money(cost),
        source("product-cost-${basis.ordinal + 1}", EconomicSourceKind.ERP),
        evaluatedAt,
        evaluatedAt.plus(Duration.ofDays(dayOffset)),
        if (basis == PricingProductCostBasis.FORWARD_REPLACEMENT) {
            EconomicEvidenceQuality.ESTIMATED
        } else {
            EconomicEvidenceQuality.CONFIRMED
        },
        PricingCostAssumptionVersion("cost-assumptions/${basis.ordinal + 1}")
    )

    private fun profile(
        components: Collection<NetBackCostComponent>,
        target: NetBackContributionTarget
    ) = NetBackPricingProfile(
        organizationId,
        sourceScenarioId,
        marketplace,
        brl,
        each,
        money("0.01"),
        NetBackNormalizationPolicyVersion("meli-rules/1"),
        components,
        coverageFor(components),
        target
    )

    private fun coverageFor(
        components: Collection<NetBackCostComponent>
    ): MutableMap<EconomicComponentType, EconomicComponentCoverage> =
        EconomicComponentType.entries.filter { it != EconomicComponentType.REVENUE }
            .associateWithTo(linkedMapOf()) { type ->
                if (components.any { it.economicType == type }) {
                    EconomicComponentCoverage.COMPLETE
                } else {
                    EconomicComponentCoverage.NOT_APPLICABLE
                }
            }

    private fun fixed(number: Int, type: EconomicComponentType, amount: String) =
        component(number, type, NetBackCostValue.FixedAmount(money(amount)))

    private fun rate(number: Int, type: EconomicComponentType, value: String) =
        component(number, type, NetBackCostValue.RevenueRate(NetBackRate.parse(value)))

    private fun component(number: Int, type: EconomicComponentType, value: NetBackCostValue) =
        NetBackCostComponent(
            organizationId,
            sourceScenarioId,
            NetBackCostComponentId.of(UUID(1, number.toLong())),
            type,
            EconomicDirection.DEDUCTION,
            value,
            source("fact-$number", EconomicSourceKind.MARKETPLACE),
            EconomicEvidenceQuality.CONFIRMED
        )

    private fun source(reference: String, kind: EconomicSourceKind) = EconomicSource(
        kind,
        EconomicSourceSystemKey(if (kind == EconomicSourceKind.ERP) "erp" else "meli-br"),
        EconomicExternalReferenceState.Present(EconomicExternalReference(reference))
    )

    private fun money(value: String) = MarketplaceMoney.parse(brl, value)
}
