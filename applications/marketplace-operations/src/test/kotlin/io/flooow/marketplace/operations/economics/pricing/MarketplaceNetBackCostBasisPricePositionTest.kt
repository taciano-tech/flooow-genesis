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

class MarketplaceNetBackCostBasisPricePositionTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val foreignOrganizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000002")
    private val sourceScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val targetScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000002")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val usd = MarketplaceCurrency("USD")
    private val each = PricingCostUnitKey("each")
    private val evaluatedAt = Instant.parse("2026-08-20T12:00:00.123456Z")

    @Test
    fun `projection bytecode contains no Kernel reference and accepts only authorized inputs`() {
        val classes = java.nio.file.Path.of(
            MarketplaceNetBackCostBasisPricePosition::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter {
                it.fileName.toString().contains("NetBackCostBasisPricePosition") &&
                    it.toString().endsWith(".class")
            }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
            }
        }

        val method = MarketplaceNetBackCostBasisPricePosition::class.java.declaredMethods
            .single { it.name == "evaluate" }
        assertContentEquals(
            arrayOf(
                NetBackCostBasisFloorDelta::class.java,
                ObservedMarketplacePrice::class.java
            ),
            method.parameterTypes
        )
        assertEquals(NetBackCostBasisPricePositionResult::class.java, method.returnType)
    }

    @Test
    fun `accepted fixture delegates exact assessment and retains complete lineage`() {
        val floorDelta = completeFloorDelta()
        val observation = observation("100.00")
        val derivedFloor = floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor
        val generic = assertIs<EconomicPricePositionResult.Assessed>(
            MarketplaceEconomicPricePosition.evaluate(derivedFloor, observation)
        ).assessment

        val result = assertIs<NetBackCostBasisPricePositionResult.Assessed>(
            MarketplaceNetBackCostBasisPricePosition.evaluate(floorDelta, observation)
        ).evaluation

        assertSame(floorDelta, result.floorDelta)
        assertSame(observation, result.observation)
        assertEquals(generic, result.assessment)
        assertEquals(targetScenarioId, result.assessment.scenarioId)
        assertEquals(money("52.00"), result.assessment.absoluteFloorGap)
        assertEquals(money("52.00"), result.assessment.economicFloorGap)
        assertEquals(EconomicPricePosition.ABOVE_ECONOMIC_FLOOR, result.assessment.position)
        assertEquals(MarketplaceEconomicTruthQuality.CONFIRMED, result.assessment.quality)
    }

    @Test
    fun `source floor and exact deltas remain context and are not evaluated`() {
        val floorDelta = completeFloorDelta()
        val result = assertIs<NetBackCostBasisPricePositionResult.Assessed>(
            MarketplaceNetBackCostBasisPricePosition.evaluate(floorDelta, observation("100.00"))
        ).evaluation

        assertEquals(money("143.20"), floorDelta.sourceScenarioFloor.sourceFloor.absoluteFloor)
        assertEquals(money("143.20"), floorDelta.sourceScenarioFloor.sourceFloor.economicFloor)
        assertEquals(money("48.00"), result.assessment.absoluteFloor)
        assertEquals(money("48.00"), result.assessment.economicFloor)
        assertEquals(money("-95.20"), floorDelta.absoluteFloorDelta)
        assertEquals(money("-95.20"), floorDelta.economicFloorDelta)
        assertNotEquals(
            floorDelta.sourceScenarioFloor.sourceFloor.scenarioId,
            result.assessment.scenarioId
        )
    }

    @Test
    fun `all accepted economic positions map without change`() {
        val floorDelta = completeFloorDelta(
            target = NetBackContributionTarget.AbsoluteAmount(money("20.00"))
        )
        val fixtures = listOf(
            "40.00" to EconomicPricePosition.BELOW_ABSOLUTE_FLOOR,
            "50.00" to EconomicPricePosition.BELOW_ECONOMIC_FLOOR,
            "68.00" to EconomicPricePosition.AT_ECONOMIC_FLOOR,
            "70.00" to EconomicPricePosition.ABOVE_ECONOMIC_FLOOR
        )

        fixtures.forEach { (price, expectedPosition) ->
            val observed = observation(price)
            val generic = MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
                observed
            )
            val projected = assertIs<NetBackCostBasisPricePositionResult.Assessed>(
                MarketplaceNetBackCostBasisPricePosition.evaluate(floorDelta, observed)
            ).evaluation

            assertEquals(assertIs<EconomicPricePositionResult.Assessed>(generic).assessment, projected.assessment)
            assertEquals(expectedPosition, projected.assessment.position)
        }
    }

    @Test
    fun `derived scenario gaps policies source time and estimated quality map exactly`() {
        val floorDelta = completeFloorDelta(
            target = NetBackContributionTarget.AbsoluteAmount(money("20.00"))
        )
        val observed = observation("50.00", quality = EconomicEvidenceQuality.ESTIMATED)
        val result = assertIs<NetBackCostBasisPricePositionResult.Assessed>(
            MarketplaceNetBackCostBasisPricePosition.evaluate(floorDelta, observed)
        ).evaluation.assessment
        val derivedFloor = floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor

        assertEquals(money("2.00"), result.absoluteFloorGap)
        assertEquals(money("-18.00"), result.economicFloorGap)
        assertEquals(EconomicPricePosition.BELOW_ECONOMIC_FLOOR, result.position)
        assertEquals(MarketplaceEconomicTruthQuality.ESTIMATED, result.quality)
        assertEquals(derivedFloor.normalizationPolicyVersion, result.floorNormalizationPolicyVersion)
        assertEquals(derivedFloor.calculationPolicyVersion, result.floorCalculationPolicyVersion)
        assertEquals(observed.source, result.source)
        assertEquals(observed.occurredAt, result.observedAt)
        assertEquals(observed.id, result.observationId)
    }

    @Test
    fun `ownership currency and quantum mismatches map one for one`() {
        val floorDelta = completeFloorDelta()
        val fixtures = listOf(
            observation("100.00", organization = foreignOrganizationId) to
                NetBackCostBasisPricePositionResult.OwnershipMismatch,
            observation("100.00", scenario = sourceScenarioId) to
                NetBackCostBasisPricePositionResult.OwnershipMismatch,
            observation("100.00", currency = usd) to
                NetBackCostBasisPricePositionResult.CurrencyMismatch,
            observation("100.001") to
                NetBackCostBasisPricePositionResult.PriceQuantumMismatch
        )

        fixtures.forEach { (observed, expected) ->
            val generic = MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
                observed
            )
            val projected = MarketplaceNetBackCostBasisPricePosition.evaluate(floorDelta, observed)
            assertEquals(expected, projected)
            when (generic) {
                EconomicPricePositionResult.OwnershipMismatch ->
                    assertEquals(NetBackCostBasisPricePositionResult.OwnershipMismatch, projected)
                EconomicPricePositionResult.CurrencyMismatch ->
                    assertEquals(NetBackCostBasisPricePositionResult.CurrencyMismatch, projected)
                EconomicPricePositionResult.PriceQuantumMismatch ->
                    assertEquals(NetBackCostBasisPricePositionResult.PriceQuantumMismatch, projected)
                is EconomicPricePositionResult.Assessed -> error("Expected mismatch")
            }
        }
    }

    @Test
    fun `source scenario observation is rejected rather than rebound`() {
        val floorDelta = completeFloorDelta()
        val sourceObservation = observation("100.00", scenario = sourceScenarioId)
        val snapshot = sourceObservation.copy()

        assertEquals(
            NetBackCostBasisPricePositionResult.OwnershipMismatch,
            MarketplaceNetBackCostBasisPricePosition.evaluate(floorDelta, sourceObservation)
        )
        assertEquals(snapshot, sourceObservation)
        assertEquals(sourceScenarioId, sourceObservation.scenarioId)
    }

    @Test
    fun `internal construction rejects an assessment from another observation`() {
        val floorDelta = completeFloorDelta()
        val retainedObservation = observation("100.00")
        val otherObservation = observation("99.00", idTail = 2L)
        val otherAssessment = assertIs<EconomicPricePositionResult.Assessed>(
            MarketplaceEconomicPricePosition.evaluate(
                floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
                otherObservation
            )
        ).assessment

        assertFailsWith<IllegalArgumentException> {
            NetBackCostBasisPricePosition(floorDelta, retainedObservation, otherAssessment)
        }
    }

    @Test
    fun `value equal inputs are deterministic immutable and all renderings are redacted`() {
        val firstFloorDelta = completeFloorDelta()
        val secondFloorDelta = completeFloorDelta()
        val firstObservation = observation("100.00")
        val secondObservation = firstObservation.copy()
        val floorSnapshot = firstFloorDelta.copy()
        val observationSnapshot = firstObservation.copy()

        val first = MarketplaceNetBackCostBasisPricePosition.evaluate(
            firstFloorDelta,
            firstObservation
        )
        val second = MarketplaceNetBackCostBasisPricePosition.evaluate(
            secondFloorDelta,
            secondObservation
        )

        assertEquals(first, second)
        assertEquals(floorSnapshot, firstFloorDelta)
        assertEquals(observationSnapshot, firstObservation)
        val assessed = assertIs<NetBackCostBasisPricePositionResult.Assessed>(first)
        val renderings = listOf(
            assessed.toString(),
            assessed.evaluation.toString(),
            MarketplaceNetBackCostBasisPricePosition.toString(),
            NetBackCostBasisPricePositionResult.OwnershipMismatch.toString(),
            NetBackCostBasisPricePositionResult.CurrencyMismatch.toString(),
            NetBackCostBasisPricePositionResult.PriceQuantumMismatch.toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach {
            assertNotEquals("100.00", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    @Test
    fun `successful aggregate adds no source position preference recommendation or action`() {
        assertEquals(
            setOf("floorDelta", "observation", "assessment"),
            NetBackCostBasisPricePosition::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet()
        )
        val bytecode = String(
            Files.readAllBytes(
                java.nio.file.Path.of(
                    NetBackCostBasisPricePosition::class.java.protectionDomain.codeSource.location.toURI()
                ).resolve(
                    "io/flooow/marketplace/operations/economics/pricing/NetBackCostBasisPricePosition.class"
                )
            ),
            StandardCharsets.ISO_8859_1
        )
        listOf("SourcePosition", "Preference", "Recommendation", "Authority", "Action").forEach {
            assertTrue(it !in bytecode)
        }
    }

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
        price: String,
        organization: OrganizationId = organizationId,
        scenario: NetBackPricingScenarioId = targetScenarioId,
        currency: MarketplaceCurrency = brl,
        quality: EconomicEvidenceQuality = EconomicEvidenceQuality.CONFIRMED,
        idTail: Long = 1L
    ) = ObservedMarketplacePrice(
        organization,
        scenario,
        EconomicPriceObservationId.of(UUID(4, idTail)),
        MarketplaceMoney.parse(currency, price),
        source("observed-price-$idTail", EconomicSourceKind.MARKETPLACE),
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
