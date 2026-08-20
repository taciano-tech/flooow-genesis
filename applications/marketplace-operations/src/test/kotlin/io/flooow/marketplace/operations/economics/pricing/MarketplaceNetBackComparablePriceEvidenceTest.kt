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

class MarketplaceNetBackComparablePriceEvidenceTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val foreignOrganizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000002")
    private val sourceScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val targetScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000002")
    private val otherScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000003")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val usd = MarketplaceCurrency("USD")
    private val each = PricingCostUnitKey("each")
    private val evaluatedAt = Instant.parse("2026-08-20T12:00:00.123456Z")

    @Test
    fun `projection bytecode contains no Kernel reference and accepts only authorized inputs`() {
        val classes = java.nio.file.Path.of(
            MarketplaceNetBackComparablePriceEvidence::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter {
                it.fileName.toString().contains("NetBackComparablePriceEvidence") &&
                    it.toString().endsWith(".class")
            }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
            }
        }

        val method = MarketplaceNetBackComparablePriceEvidence::class.java.declaredMethods
            .single { it.name == "evaluate" }
        assertContentEquals(
            arrayOf(
                NetBackCostBasisFloorDelta::class.java,
                ObservedMarketplacePrice::class.java,
                ObservedMarketplacePrice::class.java
            ),
            method.parameterTypes
        )
        assertEquals(NetBackComparablePriceEvidenceResult::class.java, method.returnType)
    }

    @Test
    fun `accepted fixture retains exact dual assessments and complete lineage`() {
        val floorDelta = completeFloorDelta()
        val sourceObservation = observation(sourceScenarioId, "100.00")
        val derivedObservation = sourceObservation.copy(scenarioId = targetScenarioId)
        val expectedSource = assertIs<EconomicPricePositionResult.Assessed>(
            MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.sourceFloor,
                sourceObservation
            )
        ).assessment
        val expectedDerived = assertIs<EconomicPricePositionResult.Assessed>(
            MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
                derivedObservation
            )
        ).assessment

        val evidence = assertIs<NetBackComparablePriceEvidenceResult.Assessed>(
            MarketplaceNetBackComparablePriceEvidence.evaluate(
                floorDelta,
                sourceObservation,
                derivedObservation
            )
        ).evidence

        assertSame(floorDelta, evidence.floorDelta)
        assertSame(sourceObservation, evidence.sourceObservation)
        assertSame(derivedObservation, evidence.derivedObservation)
        assertEquals(expectedSource, evidence.sourceAssessment)
        assertEquals(expectedDerived, evidence.derivedAssessment)
        assertEquals(EconomicPricePosition.BELOW_ABSOLUTE_FLOOR, evidence.sourceAssessment.position)
        assertEquals(money("-43.20"), evidence.sourceAssessment.absoluteFloorGap)
        assertEquals(money("-43.20"), evidence.sourceAssessment.economicFloorGap)
        assertEquals(EconomicPricePosition.ABOVE_ECONOMIC_FLOOR, evidence.derivedAssessment.position)
        assertEquals(money("52.00"), evidence.derivedAssessment.absoluteFloorGap)
        assertEquals(money("52.00"), evidence.derivedAssessment.economicFloorGap)
    }

    @Test
    fun `same fact requires exact organization identity price source time and quality`() {
        val floorDelta = completeFloorDelta()
        val sourceObservation = observation(sourceScenarioId, "100.00")
        val validDerived = sourceObservation.copy(scenarioId = targetScenarioId)
        val mismatches = listOf(
            validDerived.copy(organizationId = foreignOrganizationId),
            validDerived.copy(id = EconomicPriceObservationId.of(UUID(4, 2))),
            validDerived.copy(grossPrice = money("99.00")),
            validDerived.copy(source = source("other-price", EconomicSourceKind.MARKETPLACE)),
            validDerived.copy(occurredAt = evaluatedAt.plusSeconds(1)),
            validDerived.copy(evidenceQuality = EconomicEvidenceQuality.ESTIMATED)
        )

        mismatches.forEach { derivedObservation ->
            assertEquals(
                NetBackComparablePriceEvidenceResult.EvidenceMismatch,
                MarketplaceNetBackComparablePriceEvidence.evaluate(
                    floorDelta,
                    sourceObservation,
                    derivedObservation
                )
            )
        }
    }

    @Test
    fun `scenario ownership stays explicit and is never rebound`() {
        val floorDelta = completeFloorDelta()
        val sourceObservation = observation(sourceScenarioId, "100.00")

        assertEquals(
            NetBackComparablePriceEvidenceResult.EvidenceMismatch,
            MarketplaceNetBackComparablePriceEvidence.evaluate(
                floorDelta,
                sourceObservation,
                sourceObservation.copy()
            )
        )
        assertEquals(
            NetBackComparablePriceEvidenceResult.SourceOwnershipMismatch,
            MarketplaceNetBackComparablePriceEvidence.evaluate(
                floorDelta,
                sourceObservation.copy(scenarioId = otherScenarioId),
                sourceObservation.copy(scenarioId = targetScenarioId)
            )
        )
        assertEquals(
            NetBackComparablePriceEvidenceResult.DerivedOwnershipMismatch,
            MarketplaceNetBackComparablePriceEvidence.evaluate(
                floorDelta,
                sourceObservation,
                sourceObservation.copy(scenarioId = otherScenarioId)
            )
        )
        assertEquals(sourceScenarioId, sourceObservation.scenarioId)
    }

    @Test
    fun `currency and price quantum mismatches use shared reachable results`() {
        val floorDelta = completeFloorDelta()
        val sourceUsd = observation(sourceScenarioId, "100.00", currency = usd)
        val sourceMisaligned = observation(sourceScenarioId, "100.001")

        assertEquals(
            NetBackComparablePriceEvidenceResult.CurrencyMismatch,
            MarketplaceNetBackComparablePriceEvidence.evaluate(
                floorDelta,
                sourceUsd,
                sourceUsd.copy(scenarioId = targetScenarioId)
            )
        )
        assertEquals(
            NetBackComparablePriceEvidenceResult.PriceQuantumMismatch,
            MarketplaceNetBackComparablePriceEvidence.evaluate(
                floorDelta,
                sourceMisaligned,
                sourceMisaligned.copy(scenarioId = targetScenarioId)
            )
        )
        val resultNames = NetBackComparablePriceEvidenceResult::class.java.declaredClasses
            .map { it.simpleName }
            .toSet()
        assertTrue("SourceCurrencyMismatch" !in resultNames)
        assertTrue("DerivedCurrencyMismatch" !in resultNames)
        assertTrue("SourcePriceQuantumMismatch" !in resultNames)
        assertTrue("DerivedPriceQuantumMismatch" !in resultNames)
    }

    @Test
    fun `source and derived evaluator policies provenance time and quality remain exact`() {
        val floorDelta = completeFloorDelta(
            target = NetBackContributionTarget.AbsoluteAmount(money("20.00"))
        )
        val sourceObservation = observation(
            sourceScenarioId,
            "50.00",
            quality = EconomicEvidenceQuality.ESTIMATED
        )
        val derivedObservation = sourceObservation.copy(scenarioId = targetScenarioId)
        val evidence = assertIs<NetBackComparablePriceEvidenceResult.Assessed>(
            MarketplaceNetBackComparablePriceEvidence.evaluate(
                floorDelta,
                sourceObservation,
                derivedObservation
            )
        ).evidence

        assertEquals(MarketplaceEconomicTruthQuality.ESTIMATED, evidence.sourceAssessment.quality)
        assertEquals(MarketplaceEconomicTruthQuality.ESTIMATED, evidence.derivedAssessment.quality)
        assertEquals(sourceObservation.id, evidence.sourceAssessment.observationId)
        assertEquals(derivedObservation.id, evidence.derivedAssessment.observationId)
        assertEquals(sourceObservation.source, evidence.sourceAssessment.source)
        assertEquals(derivedObservation.source, evidence.derivedAssessment.source)
        assertEquals(evaluatedAt, evidence.sourceAssessment.observedAt)
        assertEquals(evaluatedAt, evidence.derivedAssessment.observedAt)
        assertEquals(
            floorDelta.sourceScenarioFloor.sourceFloor.calculationPolicyVersion,
            evidence.sourceAssessment.floorCalculationPolicyVersion
        )
        assertEquals(
            floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor.calculationPolicyVersion,
            evidence.derivedAssessment.floorCalculationPolicyVersion
        )
    }

    @Test
    fun `internal construction rejects changed evidence and mismatched assessments`() {
        val floorDelta = completeFloorDelta()
        val sourceObservation = observation(sourceScenarioId, "100.00")
        val derivedObservation = sourceObservation.copy(scenarioId = targetScenarioId)
        val sourceAssessment = assessment(
            floorDelta.sourceScenarioFloor.sourceFloor,
            sourceObservation
        )
        val derivedAssessment = assessment(
            floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
            derivedObservation
        )

        assertFailsWith<IllegalArgumentException> {
            NetBackComparablePriceEvidence(
                floorDelta,
                sourceObservation,
                derivedObservation.copy(grossPrice = money("99.00")),
                sourceAssessment,
                derivedAssessment
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NetBackComparablePriceEvidence(
                floorDelta,
                sourceObservation,
                derivedObservation,
                assessment(
                    floorDelta.sourceScenarioFloor.sourceFloor,
                    observation(sourceScenarioId, "99.00")
                ),
                derivedAssessment
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NetBackComparablePriceEvidence(
                floorDelta,
                sourceObservation,
                derivedObservation,
                sourceAssessment,
                assessment(
                    floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
                    observation(targetScenarioId, "99.00")
                )
            )
        }
    }

    @Test
    fun `value equal inputs are deterministic immutable and all renderings are redacted`() {
        val firstFloorDelta = completeFloorDelta()
        val secondFloorDelta = completeFloorDelta()
        val firstSource = observation(sourceScenarioId, "100.00")
        val firstDerived = firstSource.copy(scenarioId = targetScenarioId)
        val secondSource = firstSource.copy()
        val secondDerived = firstDerived.copy()
        val floorSnapshot = firstFloorDelta.copy()
        val sourceSnapshot = firstSource.copy()
        val derivedSnapshot = firstDerived.copy()

        val first = MarketplaceNetBackComparablePriceEvidence.evaluate(
            firstFloorDelta,
            firstSource,
            firstDerived
        )
        val second = MarketplaceNetBackComparablePriceEvidence.evaluate(
            secondFloorDelta,
            secondSource,
            secondDerived
        )

        assertEquals(first, second)
        assertEquals(floorSnapshot, firstFloorDelta)
        assertEquals(sourceSnapshot, firstSource)
        assertEquals(derivedSnapshot, firstDerived)
        val assessed = assertIs<NetBackComparablePriceEvidenceResult.Assessed>(first)
        val renderings = listOf(
            assessed.toString(),
            assessed.evidence.toString(),
            MarketplaceNetBackComparablePriceEvidence.toString(),
            NetBackComparablePriceEvidenceResult.EvidenceMismatch.toString(),
            NetBackComparablePriceEvidenceResult.SourceOwnershipMismatch.toString(),
            NetBackComparablePriceEvidenceResult.DerivedOwnershipMismatch.toString(),
            NetBackComparablePriceEvidenceResult.CurrencyMismatch.toString(),
            NetBackComparablePriceEvidenceResult.PriceQuantumMismatch.toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach {
            assertNotEquals("100.00", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    @Test
    fun `aggregate adds no transition preference recommendation authority or action`() {
        assertEquals(
            setOf(
                "floorDelta",
                "sourceObservation",
                "derivedObservation",
                "sourceAssessment",
                "derivedAssessment"
            ),
            NetBackComparablePriceEvidence::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet()
        )
        val bytecode = String(
            Files.readAllBytes(
                java.nio.file.Path.of(
                    NetBackComparablePriceEvidence::class.java.protectionDomain.codeSource.location.toURI()
                ).resolve(
                    "io/flooow/marketplace/operations/economics/pricing/NetBackComparablePriceEvidence.class"
                )
            ),
            StandardCharsets.ISO_8859_1
        )
        listOf(
            "Transition",
            "Percentage",
            "Materiality",
            "Preference",
            "Recommendation",
            "Authority",
            "Action"
        ).forEach { forbidden ->
            assertTrue(forbidden !in bytecode)
        }
    }

    private fun assessment(
        floor: NetBackEconomicFloor,
        observation: ObservedMarketplacePrice
    ) = assertIs<EconomicPricePositionResult.Assessed>(
        MarketplaceEconomicPricePosition.evaluate(floor, observation)
    ).assessment

    private fun completeFloorDelta(
        sourceProductCost: String = "143.20",
        target: NetBackContributionTarget = NetBackContributionTarget.AbsoluteAmount(money("0"))
    ): NetBackCostBasisFloorDelta {
        val components = listOf(fixed(1, EconomicComponentType.PRODUCT_COST, sourceProductCost))
        val sourceProfile = profile(components, target)
        val appliedScenario = assertIs<NetBackCostBasisScenarioApplicationResult.Applied>(
            MarketplaceNetBackCostBasisScenarioApplication.apply(
                sourceProfile,
                selectedCurrentCost(),
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

    private fun selectedCurrentCost(): PricingProductCostBasisSelection {
        val evidences = listOf(
            costEvidence(PricingProductCostBasis.HISTORICAL_ACQUISITION, "41.00", -90),
            costEvidence(PricingProductCostBasis.CURRENT_REPLACEMENT, "48.00", 0),
            costEvidence(PricingProductCostBasis.FORWARD_REPLACEMENT, "52.00", 90)
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
        price: String,
        currency: MarketplaceCurrency = brl,
        quality: EconomicEvidenceQuality = EconomicEvidenceQuality.CONFIRMED
    ) = ObservedMarketplacePrice(
        organizationId,
        scenario,
        EconomicPriceObservationId.of(UUID(4, 1)),
        MarketplaceMoney.parse(currency, price),
        source("observed-price", EconomicSourceKind.MARKETPLACE),
        evaluatedAt,
        quality
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
}
