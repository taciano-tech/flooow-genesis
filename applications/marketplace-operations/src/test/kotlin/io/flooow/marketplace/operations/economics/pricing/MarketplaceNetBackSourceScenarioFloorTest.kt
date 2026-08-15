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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarketplaceNetBackSourceScenarioFloorTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val sourceScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val targetScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000002")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val each = PricingCostUnitKey("each")
    private val evaluatedAt = Instant.parse("2026-08-15T13:00:00.123456Z")

    @Test
    fun `projection bytecode contains no Kernel reference and accepts only complete derived floor`() {
        val classes = java.nio.file.Path.of(
            MarketplaceNetBackSourceScenarioFloor::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter {
                it.fileName.toString().contains("NetBackSourceScenarioFloor") &&
                    it.toString().endsWith(".class")
            }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
            }
        }

        val method = MarketplaceNetBackSourceScenarioFloor::class.java.declaredMethods
            .single { it.name == "calculate" }
        assertContentEquals(
            arrayOf(NetBackAppliedScenarioFloor::class.java),
            method.parameterTypes
        )
    }

    @Test
    fun `accepted fixture calculates exact source floor and retains derived floor`() {
        val derived = completeDerivedFloor()
        val derivedSnapshot = derived.floor
        val sourceComponents = derived.appliedScenario.sourceProfile.components.toList()
        val result = calculate(derived)
        val evaluation = assertIs<NetBackSourceScenarioFloorResult.Calculated>(result).evaluation
        val sourceFloor = evaluation.sourceFloor

        assertSame(derived, evaluation.appliedScenarioFloor)
        assertEquals(sourceScenarioId, sourceFloor.scenarioId)
        assertEquals(money("143.20"), sourceFloor.absoluteFloor)
        assertEquals(money("143.20"), sourceFloor.economicFloor)
        assertEquals(targetScenarioId, derived.floor.scenarioId)
        assertEquals(money("48.00"), derived.floor.absoluteFloor)
        assertEquals(money("48.00"), derived.floor.economicFloor)
        assertSame(derivedSnapshot, derived.floor)
        assertEquals(sourceComponents, derived.appliedScenario.sourceProfile.components)
    }

    @Test
    fun `source result equals generic source calculation exactly`() {
        val derived = completeDerivedFloor()
        val generic = assertIs<NetBackCalculationResult.Complete>(
            MarketplaceNetBackEconomicFloor.calculate(derived.appliedScenario.sourceProfile)
        )
        val source = assertIs<NetBackSourceScenarioFloorResult.Calculated>(
            calculate(derived)
        ).evaluation.sourceFloor

        assertEquals(generic.floor, source)
        assertEquals(derived.appliedScenario.sourceProfile.organizationId, source.organizationId)
        assertEquals(derived.appliedScenario.sourceProfile.marketplace, source.marketplace)
        assertEquals(derived.appliedScenario.sourceProfile.currency, source.currency)
        assertEquals(derived.appliedScenario.sourceProfile.unitKey, source.unitKey)
        assertEquals(derived.appliedScenario.sourceProfile.priceQuantum, source.priceQuantum)
        assertEquals(
            derived.appliedScenario.sourceProfile.normalizationPolicyVersion,
            source.normalizationPolicyVersion
        )
        assertEquals(MarketplaceNetBackEconomicFloor.POLICY_VERSION, source.calculationPolicyVersion)
        assertEquals(derived.appliedScenario.sourceProfile.target, source.target)
        assertEquals(derived.appliedScenario.sourceProfile.components, source.components)
    }

    @Test
    fun `source and derived Product Cost evidence stay distinct and unchanged`() {
        val derived = completeDerivedFloor()
        val sourceProduct = derived.appliedScenario.originalProductCostComponent
        val appliedProduct = derived.appliedScenario.appliedProductCostComponent
        val source = assertIs<NetBackSourceScenarioFloorResult.Calculated>(
            calculate(derived)
        ).evaluation.sourceFloor

        assertEquals(money("143.20"), (sourceProduct.value as NetBackCostValue.FixedAmount).magnitude)
        assertEquals(money("48.00"), (appliedProduct.value as NetBackCostValue.FixedAmount).magnitude)
        assertEquals(
            sourceProduct,
            source.components.single { it.economicType == EconomicComponentType.PRODUCT_COST }
        )
        assertEquals(
            appliedProduct,
            derived.floor.components.single { it.economicType == EconomicComponentType.PRODUCT_COST }
        )
        assertNotEquals(sourceProduct.source, appliedProduct.source)
    }

    @Test
    fun `successful aggregate contains no delta classification or recommendation`() {
        val evaluation = assertIs<NetBackSourceScenarioFloorResult.Calculated>(calculate()).evaluation
        assertEquals(
            setOf("appliedScenarioFloor", "sourceFloor"),
            NetBackSourceScenarioFloor::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet()
        )
        val bytecode = String(
            Files.readAllBytes(
                java.nio.file.Path.of(
                    NetBackSourceScenarioFloor::class.java.protectionDomain.codeSource.location.toURI()
                ).resolve(
                    "io/flooow/marketplace/operations/economics/pricing/NetBackSourceScenarioFloor.class"
                )
            ),
            StandardCharsets.ISO_8859_1
        )
        listOf("Delta", "Comparison", "Recommendation").forEach {
            assertTrue(it !in bytecode)
        }
        assertEquals(money("143.20"), evaluation.sourceFloor.absoluteFloor)
    }

    @Test
    fun `source may be out of range while lower-cost derived scenario remains complete`() {
        val sourceProfile = profile(
            listOf(
                fixed(1, EconomicComponentType.PRODUCT_COST, "999999999999999999"),
                rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.99999999")
            )
        )
        val derived = completeDerivedFloor(sourceProfile)
        assertEquals(money("4800000000.00"), derived.floor.absoluteFloor)

        val generic = assertIs<NetBackCalculationResult.Unachievable>(
            MarketplaceNetBackEconomicFloor.calculate(sourceProfile)
        )
        val projected = assertIs<NetBackSourceScenarioFloorResult.Unachievable>(calculate(derived))
        assertSame(derived, projected.appliedScenarioFloor)
        assertEquals(generic, projected.calculation)
        assertEquals(NetBackUnachievableReason.FLOOR_OUT_OF_RANGE, projected.calculation.reason)
    }

    @Test
    fun `internal construction rejects mismatched complete incomplete and unachievable calculations`() {
        val derived = completeDerivedFloor()
        val otherDerived = completeDerivedFloor(
            profile(listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "200.00")))
        )
        val otherSource = assertIs<NetBackSourceScenarioFloorResult.Calculated>(
            calculate(otherDerived)
        ).evaluation.sourceFloor
        assertFailsWith<IllegalArgumentException> {
            NetBackSourceScenarioFloor(derived, otherSource)
        }

        val incomplete = NetBackCalculationResult.Incomplete(
            listOf(EconomicComponentType.MARKETPLACE_COMMISSION),
            emptyList(),
            derived.appliedScenario.sourceProfile.components,
            each,
            derived.appliedScenario.sourceProfile.normalizationPolicyVersion,
            MarketplaceNetBackEconomicFloor.POLICY_VERSION
        )
        assertFailsWith<IllegalArgumentException> {
            NetBackSourceScenarioFloorResult.Incomplete(derived, incomplete)
        }

        val unachievable = NetBackCalculationResult.Unachievable(
            NetBackUnachievableReason.FLOOR_OUT_OF_RANGE,
            each,
            derived.appliedScenario.sourceProfile.normalizationPolicyVersion,
            MarketplaceNetBackEconomicFloor.POLICY_VERSION
        )
        assertFailsWith<IllegalArgumentException> {
            NetBackSourceScenarioFloorResult.Unachievable(derived, unachievable)
        }
    }

    @Test
    fun `component permutations are deterministic and inputs remain unchanged`() {
        val product = fixed(1, EconomicComponentType.PRODUCT_COST, "143.20")
        val commission = rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.17")
        val firstProfile = profile(listOf(product, commission))
        val secondProfile = profile(listOf(commission, product))
        val firstSnapshot = firstProfile.components.toList()
        val secondSnapshot = secondProfile.components.toList()

        val first = calculate(completeDerivedFloor(firstProfile))
        val second = calculate(completeDerivedFloor(secondProfile))
        assertEquals(first, second)
        assertEquals(firstSnapshot, firstProfile.components)
        assertEquals(secondSnapshot, secondProfile.components)
    }

    @Test
    fun `new calculated and unachievable renderings are redacted`() {
        val calculated = assertIs<NetBackSourceScenarioFloorResult.Calculated>(calculate())
        val unachievable = calculate(
            completeDerivedFloor(
                profile(
                    listOf(
                        fixed(1, EconomicComponentType.PRODUCT_COST, "999999999999999999"),
                        rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.99999999")
                    )
                )
            )
        )
        val renderings = listOf(
            calculated.toString(),
            calculated.evaluation.toString(),
            unachievable.toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach {
            assertNotEquals("143.20", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    private fun calculate(
        derived: NetBackAppliedScenarioFloor = completeDerivedFloor()
    ) = MarketplaceNetBackSourceScenarioFloor.calculate(derived)

    private fun completeDerivedFloor(
        sourceProfile: NetBackPricingProfile = profile(
            listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"))
        )
    ): NetBackAppliedScenarioFloor {
        val assessment = assessment()
        val selection = assertIs<PricingProductCostBasisSelectionResult.Selected>(
            MarketplacePricingProductCostBasisSelection.select(
                assessment,
                PricingCostBasisSelectionPolicy(
                    PricingCostBasisSelectionPolicyVersion("pricing-cost-selection/1"),
                    PricingProductCostBasis.CURRENT_REPLACEMENT,
                    Duration.ofDays(31)
                ),
                evaluatedAt
            )
        ).selection
        val application = assertIs<NetBackCostBasisScenarioApplicationResult.Applied>(
            MarketplaceNetBackCostBasisScenarioApplication.apply(
                sourceProfile,
                selection,
                targetScenarioId,
                NetBackCostBasisApplicationPolicy(
                    NetBackCostBasisApplicationPolicyVersion("net-back-cost-application/1"),
                    Duration.ofDays(31)
                ),
                evaluatedAt
            )
        ).appliedScenario
        return assertIs<NetBackAppliedScenarioFloorResult.Calculated>(
            MarketplaceNetBackAppliedScenarioFloor.calculate(application)
        ).evaluation
    }

    private fun assessment(): PricingProductCostBasisAssessment {
        val evidences = listOf(
            costEvidence(
                PricingProductCostBasis.HISTORICAL_ACQUISITION,
                "41.00",
                evaluatedAt.minus(Duration.ofDays(90)),
                EconomicEvidenceQuality.CONFIRMED
            ),
            costEvidence(
                PricingProductCostBasis.CURRENT_REPLACEMENT,
                "48.00",
                evaluatedAt,
                EconomicEvidenceQuality.CONFIRMED
            ),
            costEvidence(
                PricingProductCostBasis.FORWARD_REPLACEMENT,
                "52.00",
                evaluatedAt.plus(Duration.ofDays(90)),
                EconomicEvidenceQuality.ESTIMATED
            )
        )
        return assertIs<PricingProductCostBasisResult.Assessed>(
            MarketplacePricingProductCostBasis.evaluate(
                evidences,
                PricingCostBasisPolicy(
                    PricingCostBasisPolicyVersion("pricing-cost-basis/1"),
                    Duration.ofDays(30),
                    Duration.ofDays(180)
                ),
                evaluatedAt
            )
        ).assessment
    }

    private fun costEvidence(
        basis: PricingProductCostBasis,
        cost: String,
        applicableAt: Instant,
        quality: EconomicEvidenceQuality
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
        applicableAt,
        quality,
        PricingCostAssumptionVersion("cost-assumptions/${basis.ordinal + 1}")
    )

    private fun profile(
        components: Collection<NetBackCostComponent>
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
        NetBackContributionTarget.AbsoluteAmount(money("0"))
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
