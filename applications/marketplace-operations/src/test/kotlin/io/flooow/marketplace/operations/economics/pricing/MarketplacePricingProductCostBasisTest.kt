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

class MarketplacePricingProductCostBasisTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val scenarioId = NetBackPricingScenarioId.parse("20000000-0000-0000-0000-000000000001")
    private val marketplace = MarketplaceKey("mercado-livre")
    private val brl = MarketplaceCurrency("BRL")
    private val evaluatedAt = Instant.parse("2026-08-14T13:00:00.123456Z")
    private val policy = PricingCostBasisPolicy(
        PricingCostBasisPolicyVersion("pricing-cost-basis/1"),
        Duration.ofDays(30),
        Duration.ofDays(180)
    )

    @Test
    fun `compiled cost basis boundary contains no Kernel reference`() {
        val classes = java.nio.file.Path.of(
            MarketplacePricingProductCostBasis::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/pricing")
        Files.walk(classes).use { files ->
            files.filter { it.toString().endsWith(".class") }.forEach {
                val text = String(Files.readAllBytes(it), StandardCharsets.ISO_8859_1)
                assertTrue("io/flooow/kernel" !in text)
            }
        }
    }

    @Test
    fun `identities unit assumptions money time and rendering are controlled`() {
        assertFailsWith<IllegalArgumentException> {
            PricingProductCostEvidenceId.parse("30000000-0000-0000-0000-00000000000A")
        }
        assertFailsWith<IllegalArgumentException> { PricingCostUnitKey("SKU 1") }
        assertFailsWith<IllegalArgumentException> { PricingCostUnitKey("a".repeat(101)) }
        assertFailsWith<IllegalArgumentException> { PricingCostAssumptionVersion("Rules 1") }
        assertFailsWith<IllegalArgumentException> { evidence(PricingProductCostBasis.CURRENT_REPLACEMENT, "-0.01") }
        assertFailsWith<IllegalArgumentException> {
            evidence(
                PricingProductCostBasis.CURRENT_REPLACEMENT,
                occurredAt = Instant.parse("2026-08-13T13:00:00.123456789Z")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            evidence(
                PricingProductCostBasis.CURRENT_REPLACEMENT,
                applicableAt = Instant.parse("2026-08-14T13:00:00.123456789Z")
            )
        }
        val current = evidence(PricingProductCostBasis.CURRENT_REPLACEMENT)
        assertEquals("[INTERNAL]", current.evidenceId.toString())
        assertEquals("[REDACTED]", current.unitKey.toString())
        assertEquals("[REDACTED]", current.assumptionVersion.toString())
        assertEquals("[REDACTED]", current.toString())
    }

    @Test
    fun `policy durations and evaluation time are explicit bounded and microsecond precise`() {
        assertFailsWith<IllegalArgumentException> { policy(currentAge = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { policy(forwardHorizon = Duration.ofDays(731)) }
        assertFailsWith<IllegalArgumentException> { policy(currentAge = Duration.ofNanos(1)) }
        assertFailsWith<IllegalArgumentException> { PricingCostBasisPolicyVersion("Policy 1") }
        assertFailsWith<IllegalArgumentException> {
            evaluate(at = Instant.parse("2026-08-14T13:00:00.123456789Z"))
        }
        assertEquals("[REDACTED]", policy.toString())
        assertEquals("[REDACTED]", policy.version.toString())
    }

    @Test
    fun `every missing combination is exact deterministic absence and zero is not missing`() {
        for (retainedMask in 0 until 7) {
            val retained = completeEvidence().filter {
                retainedMask and (1 shl it.basis.ordinal) != 0
            }.map {
                if (it.basis == PricingProductCostBasis.HISTORICAL_ACQUISITION) {
                    it.copy(unitCost = money("0"))
                } else it
            }
            val expected = PricingProductCostBasis.entries.filter {
                retainedMask and (1 shl it.ordinal) == 0
            }
            assertEquals(expected, missing(retained.reversed()).missingBases)
        }
        val none = missing(emptyList())
        val onlyForwardMissing = missing(completeEvidence().filter {
            it.basis != PricingProductCostBasis.FORWARD_REPLACEMENT
        }.reversed())
        assertEquals(listOf(PricingProductCostBasis.FORWARD_REPLACEMENT), onlyForwardMissing.missingBases)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (none.missingBases as MutableList<PricingProductCostBasis>).clear()
        }
    }

    @Test
    fun `duplicate basis and duplicate identity fail closed`() {
        val historical = evidence(PricingProductCostBasis.HISTORICAL_ACQUISITION)
        assertEquals(
            PricingProductCostBasisResult.DuplicateCostBasis,
            evaluate(
                listOf(
                    historical,
                    historical.copy(evidenceId = evidenceId(9), source = source(9))
                )
            )
        )
        val current = evidence(PricingProductCostBasis.CURRENT_REPLACEMENT)
        assertEquals(
            PricingProductCostBasisResult.DuplicateEvidence,
            evaluate(completeEvidence().map {
                if (it.basis == PricingProductCostBasis.FORWARD_REPLACEMENT) {
                    it.copy(evidenceId = current.evidenceId)
                } else it
            })
        )
        assertEquals(
            PricingProductCostBasisResult.DuplicateEvidence,
            evaluate(listOf(historical, historical.copy(evidenceId = evidenceId(10))))
        )
    }

    @Test
    fun `ownership marketplace currency and unit mismatch are controlled`() {
        assertMismatch(
            PricingProductCostBasisResult.OwnershipMismatch,
            PricingProductCostBasis.FORWARD_REPLACEMENT
        ) { it.copy(organizationId = OrganizationId(UUID(0, 9))) }
        assertMismatch(
            PricingProductCostBasisResult.OwnershipMismatch,
            PricingProductCostBasis.FORWARD_REPLACEMENT
        ) { it.copy(scenarioId = NetBackPricingScenarioId.of(UUID(0, 9))) }
        assertMismatch(
            PricingProductCostBasisResult.MarketplaceMismatch,
            PricingProductCostBasis.FORWARD_REPLACEMENT
        ) { it.copy(marketplace = MarketplaceKey("amazon")) }
        assertMismatch(
            PricingProductCostBasisResult.CurrencyMismatch,
            PricingProductCostBasis.FORWARD_REPLACEMENT
        ) { it.copy(unitCost = MarketplaceMoney.parse(MarketplaceCurrency("USD"), "52.00")) }
        assertMismatch(
            PricingProductCostBasisResult.UnitMismatch,
            PricingProductCostBasis.FORWARD_REPLACEMENT
        ) { it.copy(unitKey = PricingCostUnitKey("case-12")) }
    }

    @Test
    fun `source occurrence boundaries are inclusive while future and stale current fail`() {
        val lower = evaluatedAt.minus(policy.maximumCurrentReplacementAge)
        assertIs<PricingProductCostBasisResult.Assessed>(
            evaluate(replace(PricingProductCostBasis.CURRENT_REPLACEMENT) {
                it.copy(occurredAt = lower)
            })
        )
        assertEquals(
            PricingProductCostBasisResult.SourceTimeViolation,
            evaluate(replace(PricingProductCostBasis.CURRENT_REPLACEMENT) {
                it.copy(occurredAt = lower.minusNanos(1_000))
            })
        )
        assertEquals(
            PricingProductCostBasisResult.SourceTimeViolation,
            evaluate(replace(PricingProductCostBasis.HISTORICAL_ACQUISITION) {
                it.copy(occurredAt = evaluatedAt.plusNanos(1_000))
            })
        )
    }

    @Test
    fun `current applicability boundaries are inclusive and stale or future current fails`() {
        val lower = evaluatedAt.minus(policy.maximumCurrentReplacementAge)
        assertIs<PricingProductCostBasisResult.Assessed>(
            evaluate(replace(PricingProductCostBasis.CURRENT_REPLACEMENT) {
                it.copy(applicableAt = lower)
            })
        )
        assertEquals(
            PricingProductCostBasisResult.ApplicabilityViolation,
            evaluate(replace(PricingProductCostBasis.CURRENT_REPLACEMENT) {
                it.copy(applicableAt = lower.minusNanos(1_000))
            })
        )
        assertEquals(
            PricingProductCostBasisResult.ApplicabilityViolation,
            evaluate(replace(PricingProductCostBasis.CURRENT_REPLACEMENT) {
                it.copy(applicableAt = evaluatedAt.plusNanos(1_000))
            })
        )
    }

    @Test
    fun `historical ordering and strict forward horizon fail closed`() {
        assertEquals(
            PricingProductCostBasisResult.ApplicabilityViolation,
            evaluate(replace(PricingProductCostBasis.HISTORICAL_ACQUISITION) {
                it.copy(applicableAt = evaluatedAt.plusNanos(1_000))
            })
        )
        assertEquals(
            PricingProductCostBasisResult.ApplicabilityViolation,
            evaluate(replace(PricingProductCostBasis.FORWARD_REPLACEMENT) {
                it.copy(applicableAt = evaluatedAt)
            })
        )
        assertIs<PricingProductCostBasisResult.Assessed>(
            evaluate(replace(PricingProductCostBasis.FORWARD_REPLACEMENT) {
                it.copy(applicableAt = evaluatedAt.plus(policy.maximumForwardHorizon))
            })
        )
        assertEquals(
            PricingProductCostBasisResult.ApplicabilityViolation,
            evaluate(replace(PricingProductCostBasis.FORWARD_REPLACEMENT) {
                it.copy(applicableAt = evaluatedAt.plus(policy.maximumForwardHorizon).plusNanos(1_000))
            })
        )
        val overflowAt = Instant.MAX.minusSeconds(1).minusNanos(999_999_999)
        val overflowEvidence = completeEvidence().map {
            it.copy(
                occurredAt = overflowAt,
                applicableAt = when (it.basis) {
                    PricingProductCostBasis.HISTORICAL_ACQUISITION -> overflowAt.minus(Duration.ofDays(1))
                    PricingProductCostBasis.CURRENT_REPLACEMENT -> overflowAt
                    PricingProductCostBasis.FORWARD_REPLACEMENT -> overflowAt.plusMillis(500)
                }
            )
        }
        assertEquals(
            PricingProductCostBasisResult.ApplicabilityViolation,
            evaluate(overflowEvidence, at = overflowAt)
        )
    }

    @Test
    fun `accepted fixture derives exact signed cost trajectory`() {
        val assessment = assessed()
        assertEquals(money("41.00"), assessment.historicalEvidence.unitCost)
        assertEquals(money("48.00"), assessment.currentReplacementEvidence.unitCost)
        assertEquals(money("52.00"), assessment.forwardReplacementEvidence.unitCost)
        assertEquals(money("7.00"), assessment.currentChangeFromHistorical)
        assertEquals(money("4.00"), assessment.forwardChangeFromCurrent)
        assertEquals(money("11.00"), assessment.forwardChangeFromHistorical)
        assertEquals(PricingCostUnitKey("each"), assessment.unitKey)
        assertEquals(policy.version, assessment.policyVersion)
        assertEquals(evaluatedAt, assessment.evaluatedAt)
    }

    @Test
    fun `zero and decreasing costs remain exact evidence and signed deltas`() {
        val zero = assessed(
            listOf(
                evidence(PricingProductCostBasis.HISTORICAL_ACQUISITION, "10.00"),
                evidence(PricingProductCostBasis.CURRENT_REPLACEMENT, "0"),
                evidence(PricingProductCostBasis.FORWARD_REPLACEMENT, "0")
            )
        )
        assertEquals(money("0"), zero.currentReplacementEvidence.unitCost)
        assertEquals(money("-10.00"), zero.currentChangeFromHistorical)
        assertEquals(money("0"), zero.forwardChangeFromCurrent)
    }

    @Test
    fun `confirmed assessment requires all three confirmed evidences`() {
        assertEquals(EconomicEvidenceQuality.CONFIRMED, assessed().quality)
        PricingProductCostBasis.entries.forEach { estimatedBasis ->
            val assessment = assessed(replace(estimatedBasis) {
                it.copy(quality = EconomicEvidenceQuality.ESTIMATED)
            })
            assertEquals(EconomicEvidenceQuality.ESTIMATED, assessment.quality)
        }
    }

    @Test
    fun `permutations are value equal deterministic and do not mutate input`() {
        val input = completeEvidence().toMutableList()
        val snapshot = input.toList()
        val forward = assessed(input)
        val reverse = assessed(input.reversed())
        assertEquals(forward, reverse)
        assertEquals(snapshot, input)
    }

    @Test
    fun `aggregate result and failure rendering disclose no sensitive values`() {
        val result = evaluate()
        val assessment = assertIs<PricingProductCostBasisResult.Assessed>(result).assessment
        val missingResult = evaluate(emptyList())
        val renderings = listOf(
            result.toString(),
            assessment.toString(),
            missingResult.toString(),
            assertIs<PricingProductCostBasisResult.MissingCostBasis>(missingResult).evidence.toString(),
            PricingProductCostBasisResult.DuplicateCostBasis.toString(),
            PricingProductCostBasisResult.DuplicateEvidence.toString(),
            PricingProductCostBasisResult.OwnershipMismatch.toString(),
            PricingProductCostBasisResult.MarketplaceMismatch.toString(),
            PricingProductCostBasisResult.CurrencyMismatch.toString(),
            PricingProductCostBasisResult.UnitMismatch.toString(),
            PricingProductCostBasisResult.SourceTimeViolation.toString(),
            PricingProductCostBasisResult.ApplicabilityViolation.toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach {
            assertNotEquals("52.00", it)
            assertNotEquals(organizationId.value.toString(), it)
        }
    }

    private fun assessed(evidences: Collection<PricingProductCostEvidence> = completeEvidence()) =
        assertIs<PricingProductCostBasisResult.Assessed>(evaluate(evidences)).assessment

    private fun missing(evidences: Collection<PricingProductCostEvidence>) =
        assertIs<PricingProductCostBasisResult.MissingCostBasis>(evaluate(evidences)).evidence

    private fun evaluate(
        evidences: Collection<PricingProductCostEvidence> = completeEvidence(),
        at: Instant = evaluatedAt
    ) = MarketplacePricingProductCostBasis.evaluate(evidences, policy, at)

    private fun completeEvidence() = listOf(
        evidence(PricingProductCostBasis.HISTORICAL_ACQUISITION, "41.00"),
        evidence(PricingProductCostBasis.CURRENT_REPLACEMENT, "48.00"),
        evidence(PricingProductCostBasis.FORWARD_REPLACEMENT, "52.00")
    )

    private fun replace(
        basis: PricingProductCostBasis,
        transform: (PricingProductCostEvidence) -> PricingProductCostEvidence
    ) = completeEvidence().map { if (it.basis == basis) transform(it) else it }

    private fun evidence(
        basis: PricingProductCostBasis,
        cost: String = defaultCost(basis),
        occurredAt: Instant = evaluatedAt.minus(Duration.ofDays(1)),
        applicableAt: Instant = when (basis) {
            PricingProductCostBasis.HISTORICAL_ACQUISITION -> Instant.parse("2026-05-01T00:00:00.000000Z")
            PricingProductCostBasis.CURRENT_REPLACEMENT -> evaluatedAt
            PricingProductCostBasis.FORWARD_REPLACEMENT -> Instant.parse("2026-11-12T13:00:00.123456Z")
        },
        quality: EconomicEvidenceQuality = EconomicEvidenceQuality.CONFIRMED
    ) = PricingProductCostEvidence(
        organizationId,
        scenarioId,
        marketplace,
        evidenceId(basis.ordinal + 1),
        PricingCostUnitKey("each"),
        basis,
        money(cost),
        source(basis.ordinal + 1),
        occurredAt,
        applicableAt,
        quality,
        PricingCostAssumptionVersion("cost-assumptions/${basis.ordinal + 1}")
    )

    private fun assertMismatch(
        expected: PricingProductCostBasisResult,
        basis: PricingProductCostBasis,
        transform: (PricingProductCostEvidence) -> PricingProductCostEvidence
    ) = assertEquals(expected, evaluate(replace(basis, transform)))

    private fun policy(
        currentAge: Duration = Duration.ofDays(30),
        forwardHorizon: Duration = Duration.ofDays(180)
    ) = PricingCostBasisPolicy(
        PricingCostBasisPolicyVersion("pricing-cost-basis/1"),
        currentAge,
        forwardHorizon
    )

    private fun source(number: Int) = EconomicSource(
        EconomicSourceKind.ERP,
        EconomicSourceSystemKey("erp"),
        EconomicExternalReferenceState.Present(EconomicExternalReference("product-cost-$number"))
    )

    private fun evidenceId(number: Int) = PricingProductCostEvidenceId.of(UUID(3, number.toLong()))
    private fun money(value: String) = MarketplaceMoney.parse(brl, value)

    private fun defaultCost(basis: PricingProductCostBasis) = when (basis) {
        PricingProductCostBasis.HISTORICAL_ACQUISITION -> "41.00"
        PricingProductCostBasis.CURRENT_REPLACEMENT -> "48.00"
        PricingProductCostBasis.FORWARD_REPLACEMENT -> "52.00"
    }
}
