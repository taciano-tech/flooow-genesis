package io.flooow.marketplace.operations.economics.pricing

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

class MarketplaceCompetitiveMarketReferenceTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val scenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val evaluatedAt = Instant.parse("2026-08-14T13:00:00.123456Z")
    private val comparisonPolicyVersion =
        CompetitivePriceComparisonPolicyVersion("competitor-price-comparison/1")
    private val maximumObservationAge = Duration.ofHours(2)
    private val policy = CompetitiveMarketReferencePolicy(
        CompetitiveMarketReferencePolicyVersion("competitive-market-reference/1"),
        2
    )

    @Test
    fun `compiled market reference boundary contains no Kernel reference`() {
        val classes = java.nio.file.Path.of(
            MarketplaceCompetitiveMarketReference::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter {
                it.fileName.toString().startsWith("MarketplaceCompetitiveMarketReference") &&
                    it.toString().endsWith(".class")
            }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
            }
        }
    }

    @Test
    fun `policy is bounded versioned immutable and redacted`() {
        assertFailsWith<IllegalArgumentException> {
            CompetitiveMarketReferencePolicyVersion("Market Reference")
        }
        assertFailsWith<IllegalArgumentException> { referencePolicy(1) }
        assertFailsWith<IllegalArgumentException> { referencePolicy(101) }
        assertEquals(referencePolicy(2), referencePolicy(2))
        assertEquals("[REDACTED]", policy.toString())
        assertEquals("[REDACTED]", policy.version.toString())
    }

    @Test
    fun `one exact lowest reference per seller retains every lowest tie`() {
        val input = listOf(
            observation(3, "seller-a", "285.00"),
            observation(2, "seller-a", "280.00"),
            observation(1, "seller-a", "280.00"),
            observation(4, "seller-b", "300.00"),
            observation(5, "seller-c", "310.00")
        )
        val inputBefore = input.toList()
        val result = referenced(input)

        assertEquals(5, result.observedOfferCount)
        assertEquals(3, result.observedSellerCount)
        assertEquals(listOf(money("280.00"), money("300.00"), money("310.00")),
            result.sellerReferences.map { it.grossPrice })
        val sellerA = result.sellerReferences.first()
        assertEquals(CompetitorSellerKey.parse("seller-a"), sellerA.sellerKey)
        assertEquals(listOf(observationId(1), observationId(2)), sellerA.supportingObservationIds)
        assertEquals(inputBefore, input)
        assertEquals(5, competitiveAssessment(input).competitorObservations.size)
    }

    @Test
    fun `insufficient seller diversity returns typed evidence and no reference`() {
        val assessment = competitiveAssessment(
            listOf(observation(1, "seller-a", "280.00"), observation(2, "seller-b", "300.00"))
        )
        val result = assertIs<CompetitiveMarketReferenceResult.InsufficientSellerDiversity>(
            MarketplaceCompetitiveMarketReference.evaluate(assessment, referencePolicy(3))
        )
        assertEquals(2, result.evidence.observedOfferCount)
        assertEquals(2, result.evidence.observedSellerCount)
        assertEquals(3, result.evidence.requiredSellerCount)
        assertEquals(comparisonPolicyVersion, result.evidence.comparisonPolicyVersion)
        assertEquals(referencePolicy(3).version, result.evidence.referencePolicyVersion)
        assertEquals(evaluatedAt, result.evidence.evaluatedAt)
        assertEquals("[REDACTED]", result.toString())
        assertEquals("[REDACTED]", result.evidence.toString())
    }

    @Test
    fun `policy threshold is accepted exactly`() {
        val result = MarketplaceCompetitiveMarketReference.evaluate(
            competitiveAssessment(
                listOf(observation(1, "seller-a", "280.00"), observation(2, "seller-b", "300.00"))
            ),
            referencePolicy(2)
        )
        assertIs<CompetitiveMarketReferenceResult.Referenced>(result)
    }

    @Test
    fun `odd even equal and zero cohorts produce exact observed median bands`() {
        val odd = referenced(
            listOf(
                observation(1, "seller-a", "280.00"),
                observation(2, "seller-b", "300.00"),
                observation(3, "seller-c", "310.00")
            )
        )
        assertEquals(money("300.00"), odd.lowerMedianPrice)
        assertEquals(money("300.00"), odd.upperMedianPrice)

        val even = referenced(
            listOf(
                observation(1, "seller-a", "280.00"),
                observation(2, "seller-b", "300.00"),
                observation(3, "seller-c", "310.00"),
                observation(4, "seller-d", "320.00")
            )
        )
        assertEquals(money("300.00"), even.lowerMedianPrice)
        assertEquals(money("310.00"), even.upperMedianPrice)
        assertTrue(even.sellerReferences.none { it.grossPrice == money("305.00") })

        val equal = referenced(
            listOf(
                observation(1, "seller-a", "280.00"),
                observation(2, "seller-b", "300.00"),
                observation(3, "seller-c", "300.00"),
                observation(4, "seller-d", "320.00")
            )
        )
        assertEquals(money("300.00"), equal.lowerMedianPrice)
        assertEquals(money("300.00"), equal.upperMedianPrice)

        val zero = referenced(
            listOf(observation(1, "seller-a", "0"), observation(2, "seller-b", "0.01"))
        )
        assertEquals(money("0"), zero.lowerMedianPrice)
        assertEquals(money("0.01"), zero.upperMedianPrice)
    }

    @Test
    fun `market evidence quality excludes own economics and includes every price and match fact`() {
        val confirmedMarketEstimatedOwn = referenced(
            listOf(observation(1, "seller-a"), observation(2, "seller-b")),
            ownQuality = MarketplaceEconomicTruthQuality.ESTIMATED
        )
        assertEquals(EconomicEvidenceQuality.CONFIRMED, confirmedMarketEstimatedOwn.marketEvidenceQuality)

        val estimatedPrice = referenced(
            listOf(
                observation(1, "seller-a", priceQuality = EconomicEvidenceQuality.ESTIMATED),
                observation(2, "seller-b")
            )
        )
        assertEquals(EconomicEvidenceQuality.ESTIMATED, estimatedPrice.marketEvidenceQuality)

        val estimatedMatch = referenced(
            listOf(
                observation(1, "seller-a", matchQuality = EconomicEvidenceQuality.ESTIMATED),
                observation(2, "seller-b")
            )
        )
        assertEquals(EconomicEvidenceQuality.ESTIMATED, estimatedMatch.marketEvidenceQuality)
    }

    @Test
    fun `source lineage counts time spread currency and quantum remain exact`() {
        val earliest = evaluatedAt.minus(Duration.ofMinutes(90))
        val latest = evaluatedAt.minus(Duration.ofMinutes(5))
        val source = competitiveAssessment(
            listOf(
                observation(1, "seller-a", sourceTime = latest),
                observation(2, "seller-b", sourceTime = earliest)
            )
        )
        val result = referenced(source)
        assertEquals(source.organizationId, result.organizationId)
        assertEquals(source.scenarioId, result.scenarioId)
        assertEquals(source.ownObservationId, result.ownObservationId)
        assertEquals(source.marketplace, result.marketplace)
        assertEquals(brl, result.currency)
        assertEquals(money("0.01"), result.priceQuantum)
        assertEquals(comparisonPolicyVersion, result.comparisonPolicyVersion)
        assertEquals(maximumObservationAge, result.maximumObservationAge)
        assertEquals(policy.version, result.referencePolicyVersion)
        assertEquals(earliest, result.earliestOccurredAt)
        assertEquals(latest, result.latestOccurredAt)
        assertEquals(evaluatedAt, result.evaluatedAt)
    }

    @Test
    fun `permutations are value equal and output collections are immutable`() {
        val observations = listOf(
            observation(1, "seller-b", "300.00"),
            observation(2, "seller-a", "280.00"),
            observation(3, "seller-c", "310.00")
        )
        val forward = referenced(observations)
        val reverse = referenced(observations.reversed())
        assertEquals(forward, reverse)
        assertEquals(
            listOf("seller-a", "seller-b", "seller-c").map(CompetitorSellerKey::parse),
            forward.sellerReferences.map { it.sellerKey }
        )
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (forward.sellerReferences as MutableList<SellerCompetitivePriceReference>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (forward.sellerReferences.first().supportingObservationIds as
                MutableList<CompetitorPriceObservationId>).clear()
        }
    }

    @Test
    fun `seller tie ordering validation and aggregate rendering are controlled`() {
        assertFailsWith<IllegalArgumentException> {
            SellerCompetitivePriceReference(
                CompetitorSellerKey.parse("seller-a"),
                money("280.00"),
                emptyList()
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SellerCompetitivePriceReference(
                CompetitorSellerKey.parse("seller-a"),
                money("280.00"),
                listOf(observationId(2), observationId(1))
            )
        }

        val result = MarketplaceCompetitiveMarketReference.evaluate(
            competitiveAssessment(
                listOf(observation(1, "seller-a"), observation(2, "seller-b"))
            ),
            policy
        )
        val assessment = assertIs<CompetitiveMarketReferenceResult.Referenced>(result).assessment
        val renderings = listOf(
            result.toString(),
            assessment.toString(),
            assessment.sellerReferences.first().toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach {
            assertNotEquals("299.90", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    private fun referenced(
        observations: Collection<AvailableMatchedCompetitorPrice>,
        ownQuality: MarketplaceEconomicTruthQuality = MarketplaceEconomicTruthQuality.CONFIRMED
    ) = referenced(competitiveAssessment(observations, ownQuality))

    private fun referenced(
        assessment: CompetitivePricePositionAssessment
    ) = assertIs<CompetitiveMarketReferenceResult.Referenced>(
        MarketplaceCompetitiveMarketReference.evaluate(assessment, policy)
    ).assessment

    private fun competitiveAssessment(
        observations: Collection<AvailableMatchedCompetitorPrice>,
        ownQuality: MarketplaceEconomicTruthQuality = MarketplaceEconomicTruthQuality.CONFIRMED
    ): CompetitivePricePositionAssessment {
        require(observations.isNotEmpty())
        val sorted = observations.sortedBy { it.observationId.value.toString() }
        val lowest = sorted.minBy { it.grossPrice.amount }.grossPrice
        val lowestIds = sorted.filter { it.grossPrice == lowest }.map { it.observationId }
        val ownPrice = money("299.90")
        val combinedQuality = if (
            ownQuality == MarketplaceEconomicTruthQuality.CONFIRMED &&
            sorted.all {
                it.priceEvidenceQuality == EconomicEvidenceQuality.CONFIRMED &&
                    it.matchEvidenceQuality == EconomicEvidenceQuality.CONFIRMED
            }
        ) MarketplaceEconomicTruthQuality.CONFIRMED else MarketplaceEconomicTruthQuality.ESTIMATED
        val position = when {
            ownPrice.amount < lowest.amount -> CompetitivePricePosition.BELOW_LOWEST_COMPETITOR
            ownPrice.amount.compareTo(lowest.amount) == 0 ->
                CompetitivePricePosition.TIED_LOWEST_COMPETITOR
            else -> CompetitivePricePosition.ABOVE_LOWEST_COMPETITOR
        }
        return CompetitivePricePositionAssessment(
            organizationId,
            scenarioId,
            EconomicPriceObservationId.of(UUID(2, 1)),
            marketplace,
            brl,
            money("0.01"),
            ownPrice,
            EconomicPricePosition.ABOVE_ECONOMIC_FLOOR,
            ownQuality,
            sorted,
            lowest,
            lowestIds,
            ownPrice - lowest,
            position,
            combinedQuality,
            comparisonPolicyVersion,
            maximumObservationAge,
            evaluatedAt
        )
    }

    private fun observation(
        number: Int,
        seller: String,
        price: String = "299.90",
        sourceTime: Instant = Instant.parse("2026-08-14T12:30:00.123456Z"),
        priceQuality: EconomicEvidenceQuality = EconomicEvidenceQuality.CONFIRMED,
        matchQuality: EconomicEvidenceQuality = EconomicEvidenceQuality.CONFIRMED
    ) = AvailableMatchedCompetitorPrice(
        organizationId,
        scenarioId,
        marketplace,
        observationId(number),
        CompetitiveProductMatchId.of(UUID(4, number.toLong())),
        CompetitorSellerKey.parse(seller),
        money(price),
        EconomicSource(
            EconomicSourceKind.MARKETPLACE,
            EconomicSourceSystemKey("meli-br"),
            EconomicExternalReferenceState.Present(EconomicExternalReference("price-$number"))
        ),
        sourceTime,
        priceQuality,
        matchQuality
    )

    private fun observationId(number: Int) =
        CompetitorPriceObservationId.of(UUID(3, number.toLong()))

    private fun referencePolicy(minimum: Int) = CompetitiveMarketReferencePolicy(
        CompetitiveMarketReferencePolicyVersion("competitive-market-reference/1"),
        minimum
    )

    private fun money(value: String) = MarketplaceMoney.parse(brl, value)
}
