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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MarketplaceCompetitivePricePositionTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val scenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val evaluatedAt = Instant.parse("2026-08-14T13:00:00.123456Z")
    private val policy = CompetitivePriceComparisonPolicy(
        CompetitivePriceComparisonPolicyVersion("competitor-price-comparison/1"),
        Duration.ofHours(2)
    )

    @Test
    fun `compiled competitor boundary contains no Kernel reference`() {
        val classes = java.nio.file.Path.of(
            MarketplaceCompetitivePricePosition::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter { it.toString().endsWith(".class") }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
            }
        }
    }

    @Test
    fun `economic position carries the exact validated floor quantum`() {
        val assessment = ownAssessment()
        assertEquals(money("0.01"), assessment.priceQuantum)
        assertEquals(EconomicPricePosition.AT_ECONOMIC_FLOOR, assessment.position)
        assertEquals(money("0"), assessment.economicFloorGap)
    }

    @Test
    fun `competitor identity seller price provenance time and rendering are controlled`() {
        assertFailsWith<IllegalArgumentException> {
            CompetitorPriceObservationId.parse("30000000-0000-0000-0000-00000000000A")
        }
        assertFailsWith<IllegalArgumentException> {
            CompetitiveProductMatchId.parse("not-an-id")
        }
        assertFailsWith<IllegalArgumentException> { CompetitorSellerKey.parse("Seller 1") }
        assertFailsWith<IllegalArgumentException> { competitor(1, "-0.01") }
        assertFailsWith<IllegalArgumentException> {
            competitor(1, sourceTime = Instant.parse("2026-08-14T12:30:00.123456789Z"))
        }
        assertFailsWith<IllegalArgumentException> {
            competitor(1, source = source(1, EconomicSourceKind.ERP))
        }
        assertEquals("[INTERNAL]", competitor(1).observationId.toString())
        assertEquals("[INTERNAL]", competitor(1).productMatchId.toString())
        assertEquals("[INTERNAL]", competitor(1).sellerKey.toString())
        assertEquals("[REDACTED]", competitor(1).toString())
    }

    @Test
    fun `policy and evaluation times are explicit bounded and microsecond precise`() {
        assertFailsWith<IllegalArgumentException> { policy(Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { policy(Duration.ofDays(32)) }
        assertFailsWith<IllegalArgumentException> { policy(Duration.ofNanos(1)) }
        assertFailsWith<IllegalArgumentException> {
            compare(
                listOf(competitor(1)),
                at = Instant.parse("2026-08-14T13:00:00.123456789Z")
            )
        }
        assertEquals("[REDACTED]", policy.toString())
        assertEquals("[REDACTED]", policy.version.toString())
    }

    @Test
    fun `inclusive time boundaries are accepted`() {
        val atUpper = competitor(1, sourceTime = evaluatedAt)
        val atLower = competitor(2, sourceTime = evaluatedAt.minus(policy.maximumObservationAge))
        val assessment = compared(listOf(atUpper, atLower))
        assertEquals(2, assessment.competitorObservations.size)

        val ownAtLower = ownAssessment(observedAt = evaluatedAt.minus(policy.maximumObservationAge))
        assertIs<CompetitivePricePositionResult.Compared>(
            compare(listOf(atUpper), own = ownAtLower)
        )
    }

    @Test
    fun `stale and future own or competitor evidence fails closed`() {
        val beforeWindow = evaluatedAt.minus(policy.maximumObservationAge).minusNanos(1_000)
        val afterEvaluation = evaluatedAt.plusNanos(1_000)
        assertEquals(
            CompetitivePricePositionResult.OwnObservationOutsideWindow,
            compare(listOf(competitor(1)), own = ownAssessment(observedAt = beforeWindow))
        )
        assertEquals(
            CompetitivePricePositionResult.OwnObservationOutsideWindow,
            compare(listOf(competitor(1)), own = ownAssessment(observedAt = afterEvaluation))
        )
        assertEquals(
            CompetitivePricePositionResult.CompetitorObservationOutsideWindow,
            compare(listOf(competitor(1, sourceTime = beforeWindow)))
        )
        assertEquals(
            CompetitivePricePositionResult.CompetitorObservationOutsideWindow,
            compare(listOf(competitor(1, sourceTime = afterEvaluation)))
        )
    }

    @Test
    fun `ownership marketplace currency and quantum mismatches are controlled`() {
        assertEquals(
            CompetitivePricePositionResult.OwnershipMismatch,
            compare(listOf(competitor(1).copy(organizationId = OrganizationId(UUID(0, 9)))))
        )
        assertEquals(
            CompetitivePricePositionResult.OwnershipMismatch,
            compare(listOf(competitor(1).copy(scenarioId = NetBackPricingScenarioId.of(UUID(0, 9)))))
        )
        assertEquals(
            CompetitivePricePositionResult.MarketplaceMismatch,
            compare(listOf(competitor(1).copy(marketplace = MarketplaceKey("amazon"))))
        )
        assertEquals(
            CompetitivePricePositionResult.CurrencyMismatch,
            compare(
                listOf(
                    competitor(1).copy(
                        grossPrice = MarketplaceMoney.parse(MarketplaceCurrency("USD"), "299.90")
                    )
                )
            )
        )
        assertEquals(
            CompetitivePricePositionResult.PriceQuantumMismatch,
            compare(listOf(competitor(1, "299.901")))
        )
    }

    @Test
    fun `duplicate observation match and seller source evidence fails closed`() {
        val first = competitor(1)
        assertEquals(
            CompetitivePricePositionResult.DuplicateEvidence,
            compare(listOf(first, competitor(2).copy(observationId = first.observationId)))
        )
        assertEquals(
            CompetitivePricePositionResult.DuplicateEvidence,
            compare(listOf(first, competitor(2).copy(productMatchId = first.productMatchId)))
        )
        assertEquals(
            CompetitivePricePositionResult.DuplicateEvidence,
            compare(
                listOf(
                    first,
                    competitor(2).copy(sellerKey = first.sellerKey, source = first.source)
                )
            )
        )
    }

    @Test
    fun `empty evidence produces no comparable offers and no market position`() {
        val result = assertIs<CompetitivePricePositionResult.NoComparableOffers>(compare(emptyList()))
        assertEquals(ownAssessment().observationId, result.evidence.ownObservationId)
        assertEquals(policy.version, result.evidence.policyVersion)
        assertEquals(evaluatedAt, result.evidence.evaluatedAt)
        assertEquals("[REDACTED]", result.toString())
        assertEquals("[REDACTED]", result.evidence.toString())
    }

    @Test
    fun `accepted fixtures reproduce exact below tied and above positions`() {
        val below = compared(listOf(competitor(1, "310.00"), competitor(2, "305.00")))
        assertEquals(CompetitivePricePosition.BELOW_LOWEST_COMPETITOR, below.position)
        assertEquals(money("305.00"), below.lowestCompetitorPrice)
        assertEquals(money("-5.10"), below.gapToLowestCompetitor)

        val tied = compared(
            listOf(competitor(3, "310.00"), competitor(2, "299.90"), competitor(1, "299.90"))
        )
        assertEquals(CompetitivePricePosition.TIED_LOWEST_COMPETITOR, tied.position)
        assertEquals(money("0"), tied.gapToLowestCompetitor)
        assertEquals(
            listOf(competitorId(1), competitorId(2)),
            tied.lowestCompetitorObservationIds
        )

        val above = compared(listOf(competitor(1, "280.00"), competitor(2, "310.00")))
        assertEquals(CompetitivePricePosition.ABOVE_LOWEST_COMPETITOR, above.position)
        assertEquals(money("19.90"), above.gapToLowestCompetitor)
    }

    @Test
    fun `explicit zero prices remain exact evidence`() {
        val ownZero = ownAssessment(price = "0")
        val tied = compared(listOf(competitor(1, "0")), own = ownZero)
        assertEquals(CompetitivePricePosition.TIED_LOWEST_COMPETITOR, tied.position)
        assertEquals(money("0"), tied.lowestCompetitorPrice)
    }

    @Test
    fun `confirmed quality requires confirmed own price match and competitor price`() {
        assertEquals(
            MarketplaceEconomicTruthQuality.CONFIRMED,
            compared(listOf(competitor(1))).quality
        )
        assertEquals(
            MarketplaceEconomicTruthQuality.ESTIMATED,
            compared(listOf(competitor(1, priceQuality = EconomicEvidenceQuality.ESTIMATED))).quality
        )
        assertEquals(
            MarketplaceEconomicTruthQuality.ESTIMATED,
            compared(listOf(competitor(1, matchQuality = EconomicEvidenceQuality.ESTIMATED))).quality
        )
        assertEquals(
            MarketplaceEconomicTruthQuality.ESTIMATED,
            compared(listOf(competitor(1)), ownAssessment(ownEstimated = true)).quality
        )
    }

    @Test
    fun `permutations are deterministic and output collections are immutable`() {
        val first = competitor(1, "310.00")
        val second = competitor(2, "299.90")
        val forward = compared(listOf(first, second))
        val reverse = compared(listOf(second, first))
        assertEquals(forward, reverse)
        assertEquals(listOf(first, second), forward.competitorObservations)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (forward.competitorObservations as MutableList<AvailableMatchedCompetitorPrice>)
                .add(competitor(3))
        }
    }

    @Test
    fun `aggregate rendering and failures disclose no sensitive value`() {
        val result = compare(listOf(competitor(1)))
        val assessment = assertIs<CompetitivePricePositionResult.Compared>(result).assessment
        val renderings = listOf(
            result.toString(),
            assessment.toString(),
            CompetitivePricePositionResult.OwnObservationOutsideWindow.toString(),
            CompetitivePricePositionResult.CompetitorObservationOutsideWindow.toString(),
            CompetitivePricePositionResult.OwnershipMismatch.toString(),
            CompetitivePricePositionResult.MarketplaceMismatch.toString(),
            CompetitivePricePositionResult.CurrencyMismatch.toString(),
            CompetitivePricePositionResult.PriceQuantumMismatch.toString(),
            CompetitivePricePositionResult.DuplicateEvidence.toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach {
            assertNotEquals("299.90", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    private fun compared(
        competitors: Collection<AvailableMatchedCompetitorPrice>,
        own: EconomicPricePositionAssessment = ownAssessment()
    ) = assertIs<CompetitivePricePositionResult.Compared>(compare(competitors, own)).assessment

    private fun compare(
        competitors: Collection<AvailableMatchedCompetitorPrice>,
        own: EconomicPricePositionAssessment = ownAssessment(),
        at: Instant = evaluatedAt
    ) = MarketplaceCompetitivePricePosition.evaluate(own, competitors, policy, at)

    private fun ownAssessment(
        price: String = "299.90",
        observedAt: Instant = Instant.parse("2026-08-14T12:00:00.123456Z"),
        ownEstimated: Boolean = false
    ): EconomicPricePositionAssessment {
        val components = listOf(
            component(1, EconomicComponentType.MARKETPLACE_COMMISSION, "41.99", ownEstimated),
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
            organizationId,
            scenarioId,
            marketplace,
            brl,
            PricingCostUnitKey("each"),
            money("0.01"),
            NetBackNormalizationPolicyVersion("meli-rules/1"),
            components,
            coverage,
            NetBackContributionTarget.AbsoluteAmount(money("64.81"))
        )
        val floor = assertIs<NetBackCalculationResult.Complete>(
            MarketplaceNetBackEconomicFloor.calculate(profile)
        ).floor
        val observation = ObservedMarketplacePrice(
            organizationId,
            scenarioId,
            EconomicPriceObservationId.of(UUID(2, 1)),
            money(price),
            source(99),
            observedAt,
            if (ownEstimated) EconomicEvidenceQuality.ESTIMATED
            else EconomicEvidenceQuality.CONFIRMED
        )
        return assertIs<EconomicPricePositionResult.Assessed>(
            MarketplaceEconomicPricePosition.evaluate(floor, observation)
        ).assessment
    }

    private fun competitor(
        number: Int,
        price: String = "299.90",
        sourceTime: Instant = Instant.parse("2026-08-14T12:30:00.123456Z"),
        source: EconomicSource = source(number),
        priceQuality: EconomicEvidenceQuality = EconomicEvidenceQuality.CONFIRMED,
        matchQuality: EconomicEvidenceQuality = EconomicEvidenceQuality.CONFIRMED
    ) = AvailableMatchedCompetitorPrice(
        organizationId,
        scenarioId,
        marketplace,
        competitorId(number),
        CompetitiveProductMatchId.of(UUID(4, number.toLong())),
        CompetitorSellerKey.parse("seller-$number"),
        money(price),
        source,
        sourceTime,
        priceQuality,
        matchQuality
    )

    private fun competitorId(number: Int) =
        CompetitorPriceObservationId.of(UUID(3, number.toLong()))

    private fun policy(age: Duration) = CompetitivePriceComparisonPolicy(
        CompetitivePriceComparisonPolicyVersion("competitor-price-comparison/1"),
        age
    )

    private fun component(
        number: Int,
        type: EconomicComponentType,
        amount: String,
        estimated: Boolean = false
    ) = NetBackCostComponent(
        organizationId,
        scenarioId,
        NetBackCostComponentId.of(UUID(1, number.toLong())),
        type,
        EconomicDirection.DEDUCTION,
        NetBackCostValue.FixedAmount(money(amount)),
        source(number),
        if (estimated) EconomicEvidenceQuality.ESTIMATED else EconomicEvidenceQuality.CONFIRMED
    )

    private fun source(
        number: Int,
        kind: EconomicSourceKind = EconomicSourceKind.MARKETPLACE
    ) = EconomicSource(
        kind,
        EconomicSourceSystemKey(if (kind == EconomicSourceKind.MARKETPLACE) "meli-br" else "erp"),
        EconomicExternalReferenceState.Present(EconomicExternalReference("price-$number"))
    )

    private fun money(value: String) = MarketplaceMoney.parse(brl, value)
}
