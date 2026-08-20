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

class MarketplaceNetBackCostBasisPricePositionTransitionTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val sourceScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val targetScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000002")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val each = PricingCostUnitKey("each")
    private val evaluatedAt = Instant.parse("2026-08-20T12:00:00.123456Z")

    @Test
    fun `projection bytecode contains no Kernel or evaluator reference and accepts only evidence`() {
        val classes = java.nio.file.Path.of(
            MarketplaceNetBackCostBasisPricePositionTransition::class.java
                .protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter {
                it.fileName.toString().contains("NetBackCostBasisPricePositionTransition") &&
                    it.toString().endsWith(".class")
            }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
                assertTrue("MarketplaceEconomicPricePosition" !in text)
            }
        }

        val method = MarketplaceNetBackCostBasisPricePositionTransition::class.java.declaredMethods
            .single { it.name == "classify" }
        assertContentEquals(
            arrayOf(NetBackComparablePriceEvidence::class.java),
            method.parameterTypes
        )
        assertEquals(NetBackCostBasisPricePositionTransition::class.java, method.returnType)
    }

    @Test
    fun `transition taxonomy contains exactly the sixteen accepted ordered pairs`() {
        assertContentEquals(
            arrayOf(
                "BELOW_ABSOLUTE_TO_BELOW_ABSOLUTE",
                "BELOW_ABSOLUTE_TO_BELOW_ECONOMIC",
                "BELOW_ABSOLUTE_TO_AT_ECONOMIC",
                "BELOW_ABSOLUTE_TO_ABOVE_ECONOMIC",
                "BELOW_ECONOMIC_TO_BELOW_ABSOLUTE",
                "BELOW_ECONOMIC_TO_BELOW_ECONOMIC",
                "BELOW_ECONOMIC_TO_AT_ECONOMIC",
                "BELOW_ECONOMIC_TO_ABOVE_ECONOMIC",
                "AT_ECONOMIC_TO_BELOW_ABSOLUTE",
                "AT_ECONOMIC_TO_BELOW_ECONOMIC",
                "AT_ECONOMIC_TO_AT_ECONOMIC",
                "AT_ECONOMIC_TO_ABOVE_ECONOMIC",
                "ABOVE_ECONOMIC_TO_BELOW_ABSOLUTE",
                "ABOVE_ECONOMIC_TO_BELOW_ECONOMIC",
                "ABOVE_ECONOMIC_TO_AT_ECONOMIC",
                "ABOVE_ECONOMIC_TO_ABOVE_ECONOMIC"
            ),
            NetBackCostBasisPricePositionTransitionType.entries.map { it.name }.toTypedArray()
        )
        NetBackCostBasisPricePositionTransitionType::class.java.declaredFields
            .filterNot { it.isSynthetic || it.isEnumConstant }
            .forEach { field ->
                assertTrue(field.name !in setOf("rank", "distance", "severity", "weight", "score"))
            }
    }

    @Test
    fun `all sixteen source and derived position pairs classify in exact order`() {
        val positions = listOf(
            PositionFixture(EconomicPricePosition.BELOW_ABSOLUTE_FLOOR, "110.00", "BELOW_ABSOLUTE"),
            PositionFixture(EconomicPricePosition.BELOW_ECONOMIC_FLOOR, "95.00", "BELOW_ECONOMIC"),
            PositionFixture(EconomicPricePosition.AT_ECONOMIC_FLOOR, "90.00", "AT_ECONOMIC"),
            PositionFixture(EconomicPricePosition.ABOVE_ECONOMIC_FLOOR, "80.00", "ABOVE_ECONOMIC")
        )

        positions.forEach { source ->
            positions.forEach { derived ->
                val evidence = comparableEvidence(
                    sourceProductCost = source.productCost,
                    derivedProductCost = derived.productCost,
                    targetContribution = "10.00",
                    observedPrice = "100.00"
                )
                val result = MarketplaceNetBackCostBasisPricePositionTransition.classify(evidence)
                val expected = NetBackCostBasisPricePositionTransitionType.valueOf(
                    "${source.name}_TO_${derived.name}"
                )

                assertEquals(source.position, evidence.sourceAssessment.position)
                assertEquals(derived.position, evidence.derivedAssessment.position)
                assertEquals(expected, result.transition)
                assertSame(evidence, result.evidence)
            }
        }
    }

    @Test
    fun `accepted fixture classifies below absolute to above economic without changing lineage`() {
        val evidence = comparableEvidence(
            sourceProductCost = "143.20",
            derivedProductCost = "48.00",
            targetContribution = "0",
            observedPrice = "100.00"
        )
        val snapshot = evidence.copy()

        val result = MarketplaceNetBackCostBasisPricePositionTransition.classify(evidence)

        assertSame(evidence, result.evidence)
        assertEquals(
            NetBackCostBasisPricePositionTransitionType.BELOW_ABSOLUTE_TO_ABOVE_ECONOMIC,
            result.transition
        )
        assertEquals(EconomicPricePosition.BELOW_ABSOLUTE_FLOOR, evidence.sourceAssessment.position)
        assertEquals(EconomicPricePosition.ABOVE_ECONOMIC_FLOOR, evidence.derivedAssessment.position)
        assertEquals(snapshot, evidence)
        assertEquals(money("-95.20"), evidence.floorDelta.absoluteFloorDelta)
        assertEquals(money("-95.20"), evidence.floorDelta.economicFloorDelta)
    }

    @Test
    fun `internal construction rejects a transition inconsistent with retained assessments`() {
        val evidence = comparableEvidence("143.20", "48.00", "0", "100.00")

        assertFailsWith<IllegalArgumentException> {
            NetBackCostBasisPricePositionTransition(
                evidence,
                NetBackCostBasisPricePositionTransitionType.ABOVE_ECONOMIC_TO_BELOW_ABSOLUTE
            )
        }
    }

    @Test
    fun `value equal evidence is deterministic immutable and rendering is redacted`() {
        val firstEvidence = comparableEvidence("143.20", "48.00", "0", "100.00")
        val secondEvidence = comparableEvidence("143.20", "48.00", "0", "100.00")
        val snapshot = firstEvidence.copy()

        val first = MarketplaceNetBackCostBasisPricePositionTransition.classify(firstEvidence)
        val second = MarketplaceNetBackCostBasisPricePositionTransition.classify(secondEvidence)

        assertEquals(first, second)
        assertEquals(snapshot, firstEvidence)
        assertSame(firstEvidence, first.evidence)
        val renderings = listOf(
            first.toString(),
            MarketplaceNetBackCostBasisPricePositionTransition.toString()
        )
        assertEquals(listOf("[REDACTED]", "[REDACTED]"), renderings)
        renderings.forEach {
            assertNotEquals("143.20", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    @Test
    fun `aggregate adds no rank materiality preference recommendation authority or action`() {
        assertEquals(
            setOf("evidence", "transition"),
            NetBackCostBasisPricePositionTransition::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet()
        )
        val bytecode = String(
            Files.readAllBytes(
                java.nio.file.Path.of(
                    NetBackCostBasisPricePositionTransition::class.java
                        .protectionDomain.codeSource.location.toURI()
                ).resolve(
                    "io/flooow/marketplace/operations/economics/pricing/" +
                        "NetBackCostBasisPricePositionTransition.class"
                )
            ),
            StandardCharsets.ISO_8859_1
        )
        listOf(
            "Materiality",
            "Preference",
            "Recommendation",
            "Authority",
            "Action"
        ).forEach { forbidden ->
            assertTrue(forbidden !in bytecode)
        }
    }

    private fun comparableEvidence(
        sourceProductCost: String,
        derivedProductCost: String,
        targetContribution: String,
        observedPrice: String
    ): NetBackComparablePriceEvidence {
        val floorDelta = completeFloorDelta(
            sourceProductCost,
            derivedProductCost,
            NetBackContributionTarget.AbsoluteAmount(money(targetContribution))
        )
        val sourceObservation = observation(sourceScenarioId, observedPrice)
        val derivedObservation = sourceObservation.copy(scenarioId = targetScenarioId)
        return assertIs<NetBackComparablePriceEvidenceResult.Assessed>(
            MarketplaceNetBackComparablePriceEvidence.evaluate(
                floorDelta,
                sourceObservation,
                derivedObservation
            )
        ).evidence
    }

    private fun completeFloorDelta(
        sourceProductCost: String,
        derivedProductCost: String,
        target: NetBackContributionTarget
    ): NetBackCostBasisFloorDelta {
        val components = listOf(fixed(1, EconomicComponentType.PRODUCT_COST, sourceProductCost))
        val sourceProfile = profile(components, target)
        val appliedScenario = assertIs<NetBackCostBasisScenarioApplicationResult.Applied>(
            MarketplaceNetBackCostBasisScenarioApplication.apply(
                sourceProfile,
                selectedCurrentCost(derivedProductCost),
                targetScenarioId,
                NetBackCostBasisApplicationPolicy(
                    NetBackCostBasisApplicationPolicyVersion("net-back-cost-application/1"),
                    Duration.ofDays(31)
                ),
                evaluatedAt
            )
        ).appliedScenario
        val appliedFloor = assertIs<NetBackAppliedScenarioFloorResult.Calculated>(
            MarketplaceNetBackAppliedScenarioFloor.calculate(appliedScenario)
        ).evaluation
        val sourceFloor = assertIs<NetBackSourceScenarioFloorResult.Calculated>(
            MarketplaceNetBackSourceScenarioFloor.calculate(appliedFloor)
        ).evaluation
        return MarketplaceNetBackCostBasisFloorDelta.calculate(sourceFloor)
    }

    private fun selectedCurrentCost(currentCost: String): PricingProductCostBasisSelection {
        val evidences = listOf(
            costEvidence(PricingProductCostBasis.HISTORICAL_ACQUISITION, "70.00", -90),
            costEvidence(PricingProductCostBasis.CURRENT_REPLACEMENT, currentCost, 0),
            costEvidence(PricingProductCostBasis.FORWARD_REPLACEMENT, "120.00", 90)
        )
        val assessment = assertIs<PricingProductCostBasisResult.Assessed>(
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
        return assertIs<PricingProductCostBasisSelectionResult.Selected>(
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
    }

    private fun observation(
        scenario: NetBackPricingScenarioId,
        price: String
    ) = ObservedMarketplacePrice(
        organizationId,
        scenario,
        EconomicPriceObservationId.of(UUID(4, 1)),
        money(price),
        source("observed-price", EconomicSourceKind.MARKETPLACE),
        evaluatedAt,
        EconomicEvidenceQuality.CONFIRMED
    )

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
        NetBackCostComponent(
            organizationId,
            sourceScenarioId,
            NetBackCostComponentId.of(UUID(1, number.toLong())),
            type,
            EconomicDirection.DEDUCTION,
            NetBackCostValue.FixedAmount(money(amount)),
            source("fact-$number", EconomicSourceKind.MARKETPLACE),
            EconomicEvidenceQuality.CONFIRMED
        )

    private fun source(reference: String, kind: EconomicSourceKind) = EconomicSource(
        kind,
        EconomicSourceSystemKey(if (kind == EconomicSourceKind.ERP) "erp" else "meli-br"),
        EconomicExternalReferenceState.Present(EconomicExternalReference(reference))
    )

    private fun money(value: String) = MarketplaceMoney.parse(brl, value)

    private data class PositionFixture(
        val position: EconomicPricePosition,
        val productCost: String,
        val name: String
    )
}
