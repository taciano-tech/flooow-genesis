package io.flooow.marketplace.operations.economics.pricing

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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MarketplacePricingProductCostBasisSelectionTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val scenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val evaluatedAt = Instant.parse("2026-08-14T13:00:00.123456Z")

    @Test
    fun `compiled selection boundary contains no Kernel reference`() {
        val classes = java.nio.file.Path.of(
            MarketplacePricingProductCostBasisSelection::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter {
                it.fileName.toString().contains("CostBasisSelection") && it.toString().endsWith(".class")
            }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
            }
        }
    }

    @Test
    fun `policy and selection time are explicit bounded microsecond values`() {
        assertFailsWith<IllegalArgumentException> { PricingCostBasisSelectionPolicyVersion("Policy 1") }
        assertFailsWith<IllegalArgumentException> { policy(maximumAge = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { policy(maximumAge = Duration.ofDays(32)) }
        assertFailsWith<IllegalArgumentException> { policy(maximumAge = Duration.ofNanos(1)) }
        assertFailsWith<IllegalArgumentException> {
            select(selectedAt = Instant.parse("2026-08-14T13:00:00.123456789Z"))
        }
        assertEquals("[REDACTED]", policy().toString())
        assertEquals("[REDACTED]", policy().version.toString())
    }

    @Test
    fun `policy selects exactly each named basis and retains complete lineage`() {
        val assessment = assessment()
        PricingProductCostBasis.entries.forEach { basis ->
            val selection = selected(assessment, basis)
            val expected = when (basis) {
                PricingProductCostBasis.HISTORICAL_ACQUISITION -> assessment.historicalEvidence
                PricingProductCostBasis.CURRENT_REPLACEMENT -> assessment.currentReplacementEvidence
                PricingProductCostBasis.FORWARD_REPLACEMENT -> assessment.forwardReplacementEvidence
            }
            assertEquals(basis, selection.selectedBasis)
            assertEquals(expected, selection.selectedEvidence)
            assertEquals(assessment, selection.sourceAssessment)
            assertEquals(policy(basis).version, selection.selectionPolicyVersion)
        }
    }

    @Test
    fun `assessment selection window accepts both inclusive boundaries`() {
        val assessment = assessment()
        val age = Duration.ofDays(31)
        assertIs<PricingProductCostBasisSelectionResult.Selected>(
            select(assessment, policy(maximumAge = age), assessment.evaluatedAt)
        )
        assertIs<PricingProductCostBasisSelectionResult.Selected>(
            select(
                assessment,
                policy(PricingProductCostBasis.HISTORICAL_ACQUISITION, age),
                assessment.evaluatedAt.plus(age)
            )
        )
    }

    @Test
    fun `selection before assessment or after maximum age fails closed`() {
        val assessment = assessment()
        val policy = policy(maximumAge = Duration.ofDays(2))
        assertEquals(
            PricingProductCostBasisSelectionResult.AssessmentOutsideSelectionWindow,
            select(assessment, policy, assessment.evaluatedAt.minusNanos(1_000))
        )
        assertEquals(
            PricingProductCostBasisSelectionResult.AssessmentOutsideSelectionWindow,
            select(assessment, policy, assessment.evaluatedAt.plus(Duration.ofDays(2)).plusNanos(1_000))
        )
    }

    @Test
    fun `assessment selection upper-bound overflow fails closed`() {
        val maximumMicrosecond = Instant.MAX.minusNanos(999)
        val nearMaximum = maximumMicrosecond.minusNanos(1_000)
        val assessment = assessment(
            at = nearMaximum,
            historicalApplicableAt = nearMaximum.minusNanos(1_000),
            currentOccurredAt = nearMaximum,
            currentApplicableAt = nearMaximum,
            forwardApplicableAt = maximumMicrosecond,
            maximumCurrentAge = Duration.ofNanos(1_000),
            maximumForwardHorizon = Duration.ofNanos(1_000)
        )
        assertEquals(
            PricingProductCostBasisSelectionResult.AssessmentOutsideSelectionWindow,
            select(
                assessment,
                policy(PricingProductCostBasis.HISTORICAL_ACQUISITION, Duration.ofDays(31)),
                nearMaximum
            )
        )
    }

    @Test
    fun `current evidence accepts upper and lower freshness boundaries`() {
        val assessment = assessment(currentOccurredAt = evaluatedAt, currentApplicableAt = evaluatedAt)
        assertIs<PricingProductCostBasisSelectionResult.Selected>(
            select(assessment, policy(maximumAge = Duration.ofDays(31)), evaluatedAt)
        )
        assertIs<PricingProductCostBasisSelectionResult.Selected>(
            select(
                assessment,
                policy(maximumAge = Duration.ofDays(31)),
                evaluatedAt.plus(Duration.ofDays(30))
            )
        )
    }

    @Test
    fun `stale current source occurrence fails without fallback`() {
        val assessment = assessment(
            currentOccurredAt = evaluatedAt.minus(Duration.ofDays(1)),
            currentApplicableAt = evaluatedAt
        )
        assertEquals(
            PricingProductCostBasisSelectionResult.SelectedEvidenceOutsideApplicability,
            select(
                assessment,
                policy(maximumAge = Duration.ofDays(31)),
                evaluatedAt.plus(Duration.ofDays(30))
            )
        )
    }

    @Test
    fun `stale current applicability fails without fallback`() {
        val assessment = assessment(
            currentOccurredAt = evaluatedAt,
            currentApplicableAt = evaluatedAt.minus(Duration.ofDays(1))
        )
        assertEquals(
            PricingProductCostBasisSelectionResult.SelectedEvidenceOutsideApplicability,
            select(
                assessment,
                policy(maximumAge = Duration.ofDays(31)),
                evaluatedAt.plus(Duration.ofDays(30))
            )
        )
    }

    @Test
    fun `forward evidence must remain strictly after selection`() {
        val forwardAt = evaluatedAt.plus(Duration.ofDays(10))
        val assessment = assessment(forwardApplicableAt = forwardAt)
        assertIs<PricingProductCostBasisSelectionResult.Selected>(
            select(
                assessment,
                policy(PricingProductCostBasis.FORWARD_REPLACEMENT, Duration.ofDays(31)),
                forwardAt.minusNanos(1_000)
            )
        )
        assertEquals(
            PricingProductCostBasisSelectionResult.SelectedEvidenceOutsideApplicability,
            select(
                assessment,
                policy(PricingProductCostBasis.FORWARD_REPLACEMENT, Duration.ofDays(31)),
                forwardAt
            )
        )
        assertEquals(
            PricingProductCostBasisSelectionResult.SelectedEvidenceOutsideApplicability,
            select(
                assessment,
                policy(PricingProductCostBasis.FORWARD_REPLACEMENT, Duration.ofDays(31)),
                forwardAt.plusNanos(1_000)
            )
        )
    }

    @Test
    fun `historical selection makes no current or forward applicability claim`() {
        val assessment = assessment(forwardApplicableAt = evaluatedAt.plus(Duration.ofDays(1)))
        val selection = selected(
            assessment,
            PricingProductCostBasis.HISTORICAL_ACQUISITION,
            evaluatedAt.plus(Duration.ofDays(31))
        )
        assertEquals(PricingProductCostBasis.HISTORICAL_ACQUISITION, selection.selectedBasis)
        assertEquals(money("41.00"), selection.selectedEvidence.unitCost)
    }

    @Test
    fun `explicit zero selected cost remains exact evidence`() {
        val assessment = assessment(historicalCost = "0")
        val selection = selected(assessment, PricingProductCostBasis.HISTORICAL_ACQUISITION)
        assertEquals(money("0"), selection.selectedEvidence.unitCost)
    }

    @Test
    fun `selected evidence and complete assessment qualities remain separate`() {
        val assessment = assessment(forwardQuality = EconomicEvidenceQuality.ESTIMATED)
        val current = selected(assessment, PricingProductCostBasis.CURRENT_REPLACEMENT)
        assertEquals(EconomicEvidenceQuality.CONFIRMED, current.selectedEvidenceQuality)
        assertEquals(EconomicEvidenceQuality.ESTIMATED, current.basisAssessmentQuality)

        val forward = selected(assessment, PricingProductCostBasis.FORWARD_REPLACEMENT)
        assertEquals(EconomicEvidenceQuality.ESTIMATED, forward.selectedEvidenceQuality)
        assertEquals(EconomicEvidenceQuality.ESTIMATED, forward.basisAssessmentQuality)
    }

    @Test
    fun `value equal input is deterministic and aggregate rendering is redacted`() {
        val assessment = assessment()
        val first = select(assessment)
        val second = select(assessment.copy())
        assertEquals(first, second)
        val selection = assertIs<PricingProductCostBasisSelectionResult.Selected>(first).selection
        val renderings = listOf(
            first.toString(),
            selection.toString(),
            PricingProductCostBasisSelectionResult.AssessmentOutsideSelectionWindow.toString(),
            PricingProductCostBasisSelectionResult.SelectedEvidenceOutsideApplicability.toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach {
            assertNotEquals("48.00", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    private fun selected(
        assessment: PricingProductCostBasisAssessment,
        basis: PricingProductCostBasis = PricingProductCostBasis.CURRENT_REPLACEMENT,
        selectedAt: Instant = assessment.evaluatedAt
    ) = assertIs<PricingProductCostBasisSelectionResult.Selected>(
        select(assessment, policy(basis), selectedAt)
    ).selection

    private fun select(
        assessment: PricingProductCostBasisAssessment = assessment(),
        policy: PricingCostBasisSelectionPolicy = policy(),
        selectedAt: Instant = assessment.evaluatedAt
    ) = MarketplacePricingProductCostBasisSelection.select(assessment, policy, selectedAt)

    private fun policy(
        basis: PricingProductCostBasis = PricingProductCostBasis.CURRENT_REPLACEMENT,
        maximumAge: Duration = Duration.ofDays(31)
    ) = PricingCostBasisSelectionPolicy(
        PricingCostBasisSelectionPolicyVersion("pricing-cost-selection/1"),
        basis,
        maximumAge
    )

    private fun assessment(
        at: Instant = evaluatedAt,
        historicalApplicableAt: Instant = Instant.parse("2026-05-01T00:00:00.000000Z"),
        currentOccurredAt: Instant = at,
        currentApplicableAt: Instant = at,
        forwardApplicableAt: Instant = at.plus(Duration.ofDays(90)),
        historicalCost: String = "41.00",
        forwardQuality: EconomicEvidenceQuality = EconomicEvidenceQuality.ESTIMATED,
        maximumCurrentAge: Duration = Duration.ofDays(30),
        maximumForwardHorizon: Duration = Duration.ofDays(180)
    ): PricingProductCostBasisAssessment {
        val evidences = listOf(
            evidence(
                PricingProductCostBasis.HISTORICAL_ACQUISITION,
                historicalCost,
                at,
                historicalApplicableAt,
                EconomicEvidenceQuality.CONFIRMED
            ),
            evidence(
                PricingProductCostBasis.CURRENT_REPLACEMENT,
                "48.00",
                currentOccurredAt,
                currentApplicableAt,
                EconomicEvidenceQuality.CONFIRMED
            ),
            evidence(
                PricingProductCostBasis.FORWARD_REPLACEMENT,
                "52.00",
                at,
                forwardApplicableAt,
                forwardQuality
            )
        )
        val result = MarketplacePricingProductCostBasis.evaluate(
            evidences,
            PricingCostBasisPolicy(
                PricingCostBasisPolicyVersion("pricing-cost-basis/1"),
                maximumCurrentAge,
                maximumForwardHorizon
            ),
            at
        )
        return assertIs<PricingProductCostBasisResult.Assessed>(result).assessment
    }

    private fun evidence(
        basis: PricingProductCostBasis,
        cost: String,
        occurredAt: Instant,
        applicableAt: Instant,
        quality: EconomicEvidenceQuality
    ) = PricingProductCostEvidence(
        organizationId,
        scenarioId,
        marketplace,
        PricingProductCostEvidenceId.of(UUID(3, (basis.ordinal + 1).toLong())),
        PricingCostUnitKey("each"),
        basis,
        money(cost),
        source(basis.ordinal + 1),
        occurredAt,
        applicableAt,
        quality,
        PricingCostAssumptionVersion("cost-assumptions/${basis.ordinal + 1}")
    )

    private fun source(number: Int) = EconomicSource(
        EconomicSourceKind.ERP,
        EconomicSourceSystemKey("erp"),
        EconomicExternalReferenceState.Present(EconomicExternalReference("product-cost-$number"))
    )

    private fun money(value: String) = MarketplaceMoney.parse(brl, value)
}
