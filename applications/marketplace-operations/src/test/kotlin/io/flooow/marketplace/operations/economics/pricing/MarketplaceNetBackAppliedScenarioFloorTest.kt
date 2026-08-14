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

class MarketplaceNetBackAppliedScenarioFloorTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val sourceScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val targetScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000002")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val each = PricingCostUnitKey("each")
    private val evaluatedAt = Instant.parse("2026-08-14T13:00:00.123456Z")

    @Test
    fun `projection bytecode contains no Kernel reference and accepts only applied scenario`() {
        val classes = java.nio.file.Path.of(
            MarketplaceNetBackAppliedScenarioFloor::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter {
                it.fileName.toString().contains("NetBackAppliedScenarioFloor") &&
                    it.toString().endsWith(".class")
            }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
            }
        }

        val method = MarketplaceNetBackAppliedScenarioFloor::class.java.declaredMethods
            .single { it.name == "calculate" }
        assertContentEquals(
            arrayOf(NetBackCostBasisAppliedScenario::class.java),
            method.parameterTypes
        )
    }

    @Test
    fun `accepted replacement fixture calculates exact target scenario floors`() {
        val application = appliedScenario()
        val sourceSnapshot = application.sourceProfile.components.toList()
        val result = calculate(application)
        val evaluation = assertIs<NetBackAppliedScenarioFloorResult.Calculated>(result).evaluation
        val floor = evaluation.floor

        assertSame(application, evaluation.appliedScenario)
        assertEquals(targetScenarioId, floor.scenarioId)
        assertEquals(money("48.00"), floor.absoluteFloor)
        assertEquals(money("48.00"), floor.economicFloor)
        assertEquals(each, floor.unitKey)
        assertEquals(application.derivedProfile.priceQuantum, floor.priceQuantum)
        assertEquals(application.derivedProfile.normalizationPolicyVersion, floor.normalizationPolicyVersion)
        assertEquals(MarketplaceNetBackEconomicFloor.POLICY_VERSION, floor.calculationPolicyVersion)
        assertEquals(application.derivedProfile.target, floor.target)
        assertEquals(application.derivedProfile.components, floor.components)
        assertEquals(sourceSnapshot, application.sourceProfile.components)
        assertEquals(
            money("143.20"),
            (application.originalProductCostComponent.value as NetBackCostValue.FixedAmount).magnitude
        )
    }

    @Test
    fun `projection output contains only target floor and preserves generic calculation exactly`() {
        val application = appliedScenario()
        val generic = assertIs<NetBackCalculationResult.Complete>(
            MarketplaceNetBackEconomicFloor.calculate(application.derivedProfile)
        )
        val projected = assertIs<NetBackAppliedScenarioFloorResult.Calculated>(
            calculate(application)
        ).evaluation

        assertEquals(generic.floor, projected.floor)
        assertEquals(
            setOf("appliedScenario", "floor"),
            NetBackAppliedScenarioFloor::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet()
        )
    }

    @Test
    fun `explicit zero Product Cost calculates zero floors without becoming absence`() {
        val application = appliedScenario(
            selectedBasis = PricingProductCostBasis.HISTORICAL_ACQUISITION,
            historicalCost = "0"
        )
        val floor = assertIs<NetBackAppliedScenarioFloorResult.Calculated>(
            calculate(application)
        ).evaluation.floor

        assertEquals(money("0"), floor.absoluteFloor)
        assertEquals(money("0"), floor.economicFloor)
        val product = floor.components.single { it.economicType == EconomicComponentType.PRODUCT_COST }
        assertEquals(money("0"), (product.value as NetBackCostValue.FixedAmount).magnitude)
    }

    @Test
    fun `missing coverage retains exact incomplete calculation and application`() {
        val components = listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"))
        val coverage = coverageFor(components).apply {
            this[EconomicComponentType.MARKETPLACE_COMMISSION] = EconomicComponentCoverage.MISSING
        }
        val application = appliedScenario(profile(components, coverage))
        val generic = assertIs<NetBackCalculationResult.Incomplete>(
            MarketplaceNetBackEconomicFloor.calculate(application.derivedProfile)
        )
        val projected = assertIs<NetBackAppliedScenarioFloorResult.Incomplete>(calculate(application))

        assertSame(application, projected.appliedScenario)
        assertEquals(generic, projected.calculation)
        assertEquals(listOf(EconomicComponentType.MARKETPLACE_COMMISSION), projected.calculation.missingTypes)
        assertTrue(projected.calculation.partialTypes.isEmpty())
        assertEquals(application.derivedProfile.components, projected.calculation.suppliedComponents)
    }

    @Test
    fun `partial coverage retains exact incomplete calculation and application`() {
        val components = listOf(
            fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"),
            rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.17")
        )
        val coverage = coverageFor(components).apply {
            this[EconomicComponentType.MARKETPLACE_COMMISSION] = EconomicComponentCoverage.PARTIAL
        }
        val application = appliedScenario(profile(components, coverage))
        val generic = assertIs<NetBackCalculationResult.Incomplete>(
            MarketplaceNetBackEconomicFloor.calculate(application.derivedProfile)
        )
        val projected = assertIs<NetBackAppliedScenarioFloorResult.Incomplete>(calculate(application))

        assertSame(application, projected.appliedScenario)
        assertEquals(generic, projected.calculation)
        assertTrue(projected.calculation.missingTypes.isEmpty())
        assertEquals(listOf(EconomicComponentType.MARKETPLACE_COMMISSION), projected.calculation.partialTypes)
    }

    @Test
    fun `non-positive denominator retains exact unachievable reason and application`() {
        val application = appliedScenario(
            profile(
                listOf(
                    fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"),
                    rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "1")
                )
            )
        )
        val generic = assertIs<NetBackCalculationResult.Unachievable>(
            MarketplaceNetBackEconomicFloor.calculate(application.derivedProfile)
        )
        val projected = assertIs<NetBackAppliedScenarioFloorResult.Unachievable>(calculate(application))

        assertSame(application, projected.appliedScenario)
        assertEquals(generic, projected.calculation)
        assertEquals(NetBackUnachievableReason.NON_POSITIVE_ABSOLUTE_DENOMINATOR, projected.calculation.reason)
        assertEquals(each, projected.calculation.unitKey)
    }

    @Test
    fun `internal result construction rejects calculations from another profile`() {
        val completeApplication = appliedScenario()
        val otherApplication = appliedScenario(
            profile(
                listOf(
                    fixed(1, EconomicComponentType.PRODUCT_COST, "200.00"),
                    rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.10")
                )
            )
        )
        val otherFloor = assertIs<NetBackAppliedScenarioFloorResult.Calculated>(
            calculate(otherApplication)
        ).evaluation.floor
        assertFailsWith<IllegalArgumentException> {
            NetBackAppliedScenarioFloor(completeApplication, otherFloor)
        }

        val missingComponents = listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"))
        val missingApplication = appliedScenario(
            profile(
                missingComponents,
                coverageFor(missingComponents).apply {
                    this[EconomicComponentType.MARKETPLACE_COMMISSION] = EconomicComponentCoverage.MISSING
                }
            )
        )
        val partialComponents = listOf(
            fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"),
            rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.17")
        )
        val partialApplication = appliedScenario(
            profile(
                partialComponents,
                coverageFor(partialComponents).apply {
                    this[EconomicComponentType.MARKETPLACE_COMMISSION] = EconomicComponentCoverage.PARTIAL
                }
            )
        )
        val partial = assertIs<NetBackAppliedScenarioFloorResult.Incomplete>(
            calculate(partialApplication)
        ).calculation
        assertFailsWith<IllegalArgumentException> {
            NetBackAppliedScenarioFloorResult.Incomplete(missingApplication, partial)
        }

        val unachievableApplication = appliedScenario(
            profile(
                listOf(
                    fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"),
                    rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "1")
                )
            )
        )
        val wrongReason = NetBackCalculationResult.Unachievable(
            NetBackUnachievableReason.FLOOR_OUT_OF_RANGE,
            each,
            unachievableApplication.derivedProfile.normalizationPolicyVersion,
            MarketplaceNetBackEconomicFloor.POLICY_VERSION
        )
        assertFailsWith<IllegalArgumentException> {
            NetBackAppliedScenarioFloorResult.Unachievable(unachievableApplication, wrongReason)
        }
    }

    @Test
    fun `component permutations and value-equal applications are deterministic and immutable`() {
        val product = fixed(1, EconomicComponentType.PRODUCT_COST, "143.20")
        val commission = rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.17")
        val firstProfile = profile(listOf(product, commission))
        val secondProfile = profile(listOf(commission, product))
        val firstApplication = appliedScenario(firstProfile)
        val secondApplication = appliedScenario(secondProfile)
        val firstSourceSnapshot = firstProfile.components.toList()
        val secondSourceSnapshot = secondProfile.components.toList()

        assertEquals(calculate(firstApplication), calculate(secondApplication))
        assertEquals(firstSourceSnapshot, firstProfile.components)
        assertEquals(secondSourceSnapshot, secondProfile.components)
    }

    @Test
    fun `every new aggregate rendering is redacted`() {
        val calculated = assertIs<NetBackAppliedScenarioFloorResult.Calculated>(calculate())

        val missingComponents = listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"))
        val incomplete = calculate(
            appliedScenario(
                profile(
                    missingComponents,
                    coverageFor(missingComponents).apply {
                        this[EconomicComponentType.MARKETPLACE_COMMISSION] = EconomicComponentCoverage.MISSING
                    }
                )
            )
        )
        val unachievable = calculate(
            appliedScenario(
                profile(
                    listOf(
                        fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"),
                        rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "1")
                    )
                )
            )
        )
        val renderings = listOf(
            calculated.toString(),
            calculated.evaluation.toString(),
            incomplete.toString(),
            unachievable.toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach {
            assertNotEquals("48.00", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    private fun calculate(
        application: NetBackCostBasisAppliedScenario = appliedScenario()
    ) = MarketplaceNetBackAppliedScenarioFloor.calculate(application)

    private fun appliedScenario(
        sourceProfile: NetBackPricingProfile = profile(
            listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"))
        ),
        selectedBasis: PricingProductCostBasis = PricingProductCostBasis.CURRENT_REPLACEMENT,
        historicalCost: String = "41.00"
    ): NetBackCostBasisAppliedScenario {
        val assessment = assessment(historicalCost)
        val selection = assertIs<PricingProductCostBasisSelectionResult.Selected>(
            MarketplacePricingProductCostBasisSelection.select(
                assessment,
                PricingCostBasisSelectionPolicy(
                    PricingCostBasisSelectionPolicyVersion("pricing-cost-selection/1"),
                    selectedBasis,
                    Duration.ofDays(31)
                ),
                evaluatedAt
            )
        ).selection
        return assertIs<NetBackCostBasisScenarioApplicationResult.Applied>(
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
    }

    private fun assessment(historicalCost: String): PricingProductCostBasisAssessment {
        val evidences = listOf(
            costEvidence(
                PricingProductCostBasis.HISTORICAL_ACQUISITION,
                historicalCost,
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
        components: Collection<NetBackCostComponent>,
        coverage: Map<EconomicComponentType, EconomicComponentCoverage> = coverageFor(components)
    ) = NetBackPricingProfile(
        organizationId,
        sourceScenarioId,
        marketplace,
        brl,
        each,
        money("0.01"),
        NetBackNormalizationPolicyVersion("meli-rules/1"),
        components,
        coverage,
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
        component(
            number,
            type,
            NetBackCostValue.FixedAmount(money(amount))
        )

    private fun rate(number: Int, type: EconomicComponentType, value: String) =
        component(
            number,
            type,
            NetBackCostValue.RevenueRate(NetBackRate.parse(value))
        )

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
