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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarketplaceNetBackCostBasisScenarioApplicationTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val sourceScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val targetScenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000002")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val each = PricingCostUnitKey("each")
    private val evaluatedAt = Instant.parse("2026-08-14T13:00:00.123456Z")

    @Test
    fun `compiled application boundary contains no Kernel reference`() {
        val classes = java.nio.file.Path.of(
            MarketplaceNetBackCostBasisScenarioApplication::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter {
                it.fileName.toString().contains("NetBackCostBasis") &&
                    it.toString().endsWith(".class")
            }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
                assertTrue("MarketplaceNetBackEconomicFloor" !in text)
            }
        }
    }

    @Test
    fun `policy and application time are canonical bounded microsecond values`() {
        assertFailsWith<IllegalArgumentException> { NetBackCostBasisApplicationPolicyVersion("Policy 1") }
        assertFailsWith<IllegalArgumentException> { policy(Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { policy(Duration.ofDays(32)) }
        assertFailsWith<IllegalArgumentException> { policy(Duration.ofNanos(1)) }
        assertFailsWith<IllegalArgumentException> {
            apply(appliedAt = Instant.parse("2026-08-14T13:00:00.123456789Z"))
        }
        assertEquals("[REDACTED]", policy().toString())
        assertEquals("[REDACTED]", policy().version.toString())
    }

    @Test
    fun `application substitutes only Product Cost evidence into distinct scenario`() {
        val source = profile(
            listOf(
                fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"),
                rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.17")
            )
        )
        val sourceComponents = source.components.toList()
        val selection = selection()

        val applied = applied(source, selection)
        val original = source.components.single { it.economicType == EconomicComponentType.PRODUCT_COST }
        val derivedProduct = applied.derivedProfile.components.single {
            it.economicType == EconomicComponentType.PRODUCT_COST
        }
        val sourceCommission = source.components.single {
            it.economicType == EconomicComponentType.MARKETPLACE_COMMISSION
        }
        val derivedCommission = applied.derivedProfile.components.single {
            it.economicType == EconomicComponentType.MARKETPLACE_COMMISSION
        }

        assertEquals(sourceScenarioId, source.scenarioId)
        assertEquals(sourceComponents, source.components)
        assertEquals(money("143.20"), (original.value as NetBackCostValue.FixedAmount).magnitude)
        assertSame(source, applied.sourceProfile)
        assertSame(selection, applied.costSelection)
        assertEquals(original, applied.originalProductCostComponent)
        assertEquals(targetScenarioId, derivedProduct.scenarioId)
        assertEquals(original.id, derivedProduct.id)
        assertEquals(money("48.00"), (derivedProduct.value as NetBackCostValue.FixedAmount).magnitude)
        assertEquals(selection.selectedEvidence.source, derivedProduct.source)
        assertEquals(selection.selectedEvidenceQuality, derivedProduct.evidenceQuality)
        assertEquals(derivedProduct, applied.appliedProductCostComponent)
        assertEquals(sourceCommission.copy(scenarioId = targetScenarioId), derivedCommission)
    }

    @Test
    fun `derived profile preserves every field outside scenario and selected cost evidence`() {
        val source = profile(
            listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20")),
            target = NetBackContributionTarget.MarginRate(NetBackRate.parse("0.15")),
            quantum = money("0.05")
        )
        val derived = applied(source).derivedProfile

        assertEquals(source.organizationId, derived.organizationId)
        assertEquals(targetScenarioId, derived.scenarioId)
        assertEquals(source.marketplace, derived.marketplace)
        assertEquals(source.currency, derived.currency)
        assertEquals(source.unitKey, derived.unitKey)
        assertEquals(source.priceQuantum, derived.priceQuantum)
        assertEquals(source.normalizationPolicyVersion, derived.normalizationPolicyVersion)
        assertEquals(source.coverage, derived.coverage)
        assertEquals(source.target, derived.target)
    }

    @Test
    fun `target scenario reuse has first precedence`() {
        val otherOrganization = OrganizationId.parse("10000000-0000-0000-0000-000000000099")
        val incompatible = profile(
            listOf(
                fixed(
                    1,
                    EconomicComponentType.PRODUCT_COST,
                    "143.20",
                    organizationId = otherOrganization
                )
            ),
            organizationId = otherOrganization
        )
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.TargetScenarioReusesSource,
            apply(incompatible, targetScenarioId = incompatible.scenarioId)
        )
    }

    @Test
    fun `ownership and source scenario mismatches retain precedence`() {
        val otherOrganization = OrganizationId.parse("10000000-0000-0000-0000-000000000099")
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.OwnershipMismatch,
            apply(
                profile(
                    listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20", organizationId = otherOrganization)),
                    organizationId = otherOrganization
                )
            )
        )

        val otherScenario = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000099")
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.SourceScenarioMismatch,
            apply(
                profile(
                    listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20", scenarioId = otherScenario)),
                    scenarioId = otherScenario
                )
            )
        )
    }

    @Test
    fun `marketplace currency and normalized unit mismatches fail explicitly`() {
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.MarketplaceMismatch,
            apply(
                profile(
                    listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20")),
                    marketplace = MarketplaceKey("amazon")
                )
            )
        )
        val usd = MarketplaceCurrency("USD")
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.CurrencyMismatch,
            apply(
                profile(
                    listOf(
                        fixed(
                            1,
                            EconomicComponentType.PRODUCT_COST,
                            "143.20",
                            currency = usd
                        )
                    ),
                    currency = usd,
                    quantum = money("0.01", usd)
                )
            )
        )
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.UnitMismatch,
            apply(
                profile(
                    listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20")),
                    unitKey = PricingCostUnitKey("kit")
                )
            )
        )
    }

    @Test
    fun `application window accepts both inclusive boundaries`() {
        val historical = selection(PricingProductCostBasis.HISTORICAL_ACQUISITION)
        assertIs<NetBackCostBasisScenarioApplicationResult.Applied>(
            apply(selection = historical, policy = policy(Duration.ofDays(31)), appliedAt = historical.selectedAt)
        )
        assertIs<NetBackCostBasisScenarioApplicationResult.Applied>(
            apply(
                selection = historical,
                policy = policy(Duration.ofDays(31)),
                appliedAt = historical.selectedAt.plus(Duration.ofDays(31))
            )
        )
    }

    @Test
    fun `application before selection or after configured age fails closed`() {
        val selection = selection(PricingProductCostBasis.HISTORICAL_ACQUISITION)
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.SelectionOutsideApplicationWindow,
            apply(selection = selection, appliedAt = selection.selectedAt.minusNanos(1_000))
        )
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.SelectionOutsideApplicationWindow,
            apply(
                selection = selection,
                policy = policy(Duration.ofDays(2)),
                appliedAt = selection.selectedAt.plus(Duration.ofDays(2)).plusNanos(1_000)
            )
        )
    }

    @Test
    fun `application upper bound overflow fails closed`() {
        val maximumMicrosecond = Instant.MAX.minusNanos(999)
        val at = maximumMicrosecond.minusNanos(1_000)
        val assessment = assessment(
            at = at,
            currentOccurredAt = at,
            currentApplicableAt = at,
            forwardApplicableAt = maximumMicrosecond,
            maximumCurrentAge = Duration.ofNanos(1_000),
            maximumForwardHorizon = Duration.ofNanos(1_000)
        )
        val selection = selection(
            PricingProductCostBasis.HISTORICAL_ACQUISITION,
            assessment,
            at,
            Duration.ofNanos(1_000)
        )
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.SelectionOutsideApplicationWindow,
            apply(
                profile = profile(listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"))),
                selection = selection,
                policy = policy(Duration.ofDays(31)),
                appliedAt = at
            )
        )
    }

    @Test
    fun `current evidence becoming stale fails selection reproduction`() {
        val assessment = assessment(maximumCurrentAge = Duration.ofDays(1))
        val selection = selection(PricingProductCostBasis.CURRENT_REPLACEMENT, assessment)
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.SelectionNoLongerApplicable,
            apply(
                selection = selection,
                policy = policy(Duration.ofDays(3)),
                appliedAt = evaluatedAt.plus(Duration.ofDays(2))
            )
        )
    }

    @Test
    fun `forward evidence reaching applicability fails selection reproduction`() {
        val forwardAt = evaluatedAt.plus(Duration.ofDays(2))
        val assessment = assessment(forwardApplicableAt = forwardAt)
        val selection = selection(PricingProductCostBasis.FORWARD_REPLACEMENT, assessment)
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.SelectionNoLongerApplicable,
            apply(
                selection = selection,
                policy = policy(Duration.ofDays(3)),
                appliedAt = forwardAt
            )
        )
    }

    @Test
    fun `unsupported Product Cost coverage and cardinality fail closed`() {
        val noProduct = listOf(rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.17"))
        assertUnsupported(profile(noProduct))
        assertUnsupported(
            profile(
                noProduct,
                coverage = coverageFor(noProduct).apply {
                    this[EconomicComponentType.PRODUCT_COST] = EconomicComponentCoverage.MISSING
                }
            )
        )
        assertUnsupported(
            profile(
                listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20")),
                coverage = coverageFor(listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"))).apply {
                    this[EconomicComponentType.PRODUCT_COST] = EconomicComponentCoverage.PARTIAL
                }
            )
        )
        assertUnsupported(
            profile(
                listOf(
                    fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"),
                    fixed(3, EconomicComponentType.PRODUCT_COST, "10.00")
                )
            )
        )
    }

    @Test
    fun `unsupported Product Cost rate and addition fail closed`() {
        assertUnsupported(profile(listOf(rate(1, EconomicComponentType.PRODUCT_COST, "0.25"))))
        assertUnsupported(
            profile(
                listOf(
                    fixed(
                        1,
                        EconomicComponentType.PRODUCT_COST,
                        "143.20",
                        direction = EconomicDirection.ADDITION
                    )
                )
            )
        )
    }

    @Test
    fun `explicit zero selected cost remains a fixed evidence component`() {
        val zeroSelection = selection(
            PricingProductCostBasis.HISTORICAL_ACQUISITION,
            assessment(historicalCost = "0")
        )
        val product = applied(selection = zeroSelection).appliedProductCostComponent
        assertEquals(money("0"), (product.value as NetBackCostValue.FixedAmount).magnitude)
    }

    @Test
    fun `component permutations are deterministic and do not mutate inputs`() {
        val product = fixed(1, EconomicComponentType.PRODUCT_COST, "143.20")
        val commission = rate(2, EconomicComponentType.MARKETPLACE_COMMISSION, "0.17")
        val firstSource = profile(listOf(product, commission))
        val secondSource = profile(listOf(commission, product))
        val firstSnapshot = firstSource.components.toList()
        val secondSnapshot = secondSource.components.toList()

        val first = applied(firstSource)
        val second = applied(secondSource)
        assertEquals(first, second)
        assertEquals(firstSnapshot, firstSource.components)
        assertEquals(secondSnapshot, secondSource.components)
    }

    @Test
    fun `aggregate and every controlled result render redacted`() {
        val success = apply()
        val applied = assertIs<NetBackCostBasisScenarioApplicationResult.Applied>(success).appliedScenario
        val renderings = listOf(
            success,
            applied,
            NetBackCostBasisScenarioApplicationResult.TargetScenarioReusesSource,
            NetBackCostBasisScenarioApplicationResult.OwnershipMismatch,
            NetBackCostBasisScenarioApplicationResult.SourceScenarioMismatch,
            NetBackCostBasisScenarioApplicationResult.MarketplaceMismatch,
            NetBackCostBasisScenarioApplicationResult.CurrencyMismatch,
            NetBackCostBasisScenarioApplicationResult.UnitMismatch,
            NetBackCostBasisScenarioApplicationResult.SelectionOutsideApplicationWindow,
            NetBackCostBasisScenarioApplicationResult.SelectionNoLongerApplicable,
            NetBackCostBasisScenarioApplicationResult.UnsupportedProductCostShape
        ).map { it.toString() }
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach {
            assertNotEquals("48.00", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    private fun assertUnsupported(profile: NetBackPricingProfile) {
        assertEquals(
            NetBackCostBasisScenarioApplicationResult.UnsupportedProductCostShape,
            apply(profile)
        )
    }

    private fun applied(
        profile: NetBackPricingProfile = profile(
            listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"))
        ),
        selection: PricingProductCostBasisSelection = selection()
    ) = assertIs<NetBackCostBasisScenarioApplicationResult.Applied>(
        apply(profile, selection)
    ).appliedScenario

    private fun apply(
        profile: NetBackPricingProfile = profile(
            listOf(fixed(1, EconomicComponentType.PRODUCT_COST, "143.20"))
        ),
        selection: PricingProductCostBasisSelection = selection(),
        targetScenarioId: NetBackPricingScenarioId = this.targetScenarioId,
        policy: NetBackCostBasisApplicationPolicy = policy(),
        appliedAt: Instant = selection.selectedAt
    ) = MarketplaceNetBackCostBasisScenarioApplication.apply(
        profile,
        selection,
        targetScenarioId,
        policy,
        appliedAt
    )

    private fun policy(maximumAge: Duration = Duration.ofDays(31)) =
        NetBackCostBasisApplicationPolicy(
            NetBackCostBasisApplicationPolicyVersion("net-back-cost-application/1"),
            maximumAge
        )

    private fun selection(
        basis: PricingProductCostBasis = PricingProductCostBasis.CURRENT_REPLACEMENT,
        assessment: PricingProductCostBasisAssessment = assessment(),
        selectedAt: Instant = assessment.evaluatedAt,
        maximumAssessmentAge: Duration = Duration.ofDays(31)
    ) = assertIs<PricingProductCostBasisSelectionResult.Selected>(
        MarketplacePricingProductCostBasisSelection.select(
            assessment,
            PricingCostBasisSelectionPolicy(
                PricingCostBasisSelectionPolicyVersion("pricing-cost-selection/1"),
                basis,
                maximumAssessmentAge
            ),
            selectedAt
        )
    ).selection

    private fun assessment(
        at: Instant = evaluatedAt,
        historicalApplicableAt: Instant = at.minus(Duration.ofDays(90)),
        currentOccurredAt: Instant = at,
        currentApplicableAt: Instant = at,
        forwardApplicableAt: Instant = at.plus(Duration.ofDays(90)),
        historicalCost: String = "41.00",
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
                EconomicEvidenceQuality.ESTIMATED
            )
        )
        return assertIs<PricingProductCostBasisResult.Assessed>(
            MarketplacePricingProductCostBasis.evaluate(
                evidences,
                PricingCostBasisPolicy(
                    PricingCostBasisPolicyVersion("pricing-cost-basis/1"),
                    maximumCurrentAge,
                    maximumForwardHorizon
                ),
                at
            )
        ).assessment
    }

    private fun evidence(
        basis: PricingProductCostBasis,
        cost: String,
        occurredAt: Instant,
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
        EconomicSource(
            EconomicSourceKind.ERP,
            EconomicSourceSystemKey("erp"),
            EconomicExternalReferenceState.Present(
                EconomicExternalReference("product-cost-${basis.ordinal + 1}")
            )
        ),
        occurredAt,
        applicableAt,
        quality,
        PricingCostAssumptionVersion("cost-assumptions/${basis.ordinal + 1}")
    )

    private fun profile(
        components: Collection<NetBackCostComponent>,
        coverage: Map<EconomicComponentType, EconomicComponentCoverage> = coverageFor(components),
        organizationId: OrganizationId = this.organizationId,
        scenarioId: NetBackPricingScenarioId = sourceScenarioId,
        marketplace: MarketplaceKey = this.marketplace,
        currency: MarketplaceCurrency = brl,
        unitKey: PricingCostUnitKey = each,
        quantum: MarketplaceMoney = money("0.01", currency),
        target: NetBackContributionTarget = NetBackContributionTarget.AbsoluteAmount(money("0", currency))
    ) = NetBackPricingProfile(
        organizationId,
        scenarioId,
        marketplace,
        currency,
        unitKey,
        quantum,
        NetBackNormalizationPolicyVersion("meli-rules/1"),
        components,
        coverage,
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

    private fun fixed(
        number: Int,
        type: EconomicComponentType,
        amount: String,
        direction: EconomicDirection = EconomicDirection.DEDUCTION,
        organizationId: OrganizationId = this.organizationId,
        scenarioId: NetBackPricingScenarioId = sourceScenarioId,
        currency: MarketplaceCurrency = brl
    ) = component(
        number,
        type,
        direction,
        NetBackCostValue.FixedAmount(money(amount, currency)),
        organizationId,
        scenarioId
    )

    private fun rate(
        number: Int,
        type: EconomicComponentType,
        value: String,
        direction: EconomicDirection = EconomicDirection.DEDUCTION
    ) = component(
        number,
        type,
        direction,
        NetBackCostValue.RevenueRate(NetBackRate.parse(value)),
        organizationId,
        sourceScenarioId
    )

    private fun component(
        number: Int,
        type: EconomicComponentType,
        direction: EconomicDirection,
        value: NetBackCostValue,
        organizationId: OrganizationId,
        scenarioId: NetBackPricingScenarioId
    ) = NetBackCostComponent(
        organizationId,
        scenarioId,
        NetBackCostComponentId.of(UUID(1, number.toLong())),
        type,
        direction,
        value,
        EconomicSource(
            EconomicSourceKind.MARKETPLACE,
            EconomicSourceSystemKey("meli-br"),
            EconomicExternalReferenceState.Present(EconomicExternalReference("fact-$number"))
        ),
        EconomicEvidenceQuality.CONFIRMED
    )

    private fun money(value: String, currency: MarketplaceCurrency = brl) =
        MarketplaceMoney.parse(currency, value)
}
