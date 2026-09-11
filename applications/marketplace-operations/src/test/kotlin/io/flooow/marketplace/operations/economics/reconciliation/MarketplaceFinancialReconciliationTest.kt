package io.flooow.marketplace.operations.economics.reconciliation

import io.flooow.marketplace.operations.economics.EconomicDirection
import io.flooow.marketplace.operations.economics.EconomicExternalReference
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerAppendRequestId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerBasis
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerEntryId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.ledger.FinancialTrace
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceId
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceOpenRequestId
import io.flooow.marketplace.operations.economics.ledger.RecordedFinancialLedgerEntry
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MarketplaceFinancialReconciliationTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val traceId = FinancialTraceId.parse("20000000-0000-0000-0000-000000000001")
    private val orderId = MarketplaceOrderId.parse("30000000-0000-0000-0000-000000000001")
    private val brl = MarketplaceCurrency("BRL")

    @Test
    fun `compiled reconciliation boundary contains no Kernel reference`() {
        val productionClasses = java.nio.file.Path.of(
            MarketplaceFinancialReconciliation::class.java.protectionDomain.codeSource.location.toURI()
        )
        val packageDirectory = productionClasses.resolve(
            "io/flooow/marketplace/operations/economics/reconciliation"
        )

        Files.walk(packageDirectory).use { files ->
            val classFiles = files.filter { it.toString().endsWith(".class") }.toList()
            assertTrue(classFiles.isNotEmpty())
            classFiles.forEach { classFile ->
                val bytecodeText = String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1)
                assertTrue(
                    "io/flooow/kernel" !in bytecodeText,
                    "Financial reconciliation bytecode must not reference Kernel: ${classFile.fileName}"
                )
            }
        }
    }

    @Test
    fun `policy is complete exact immutable currency safe and redacted`() {
        assertFailsWith<IllegalArgumentException> {
            FinancialReconciliationPolicyVersion("financial reconciliation 1")
        }
        assertFailsWith<IllegalArgumentException> {
            policy().toMutableMap().also { it.remove(FinancialLedgerStage.BANK) }.let {
                FinancialReconciliationPolicy(policyVersion(), brl, it)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            policy().toMutableMap().also {
                it[FinancialLedgerStage.BANK] = MarketplaceMoney.parse(
                    MarketplaceCurrency("USD"),
                    "0"
                )
            }.let { FinancialReconciliationPolicy(policyVersion(), brl, it) }
        }
        assertFailsWith<IllegalArgumentException> {
            FinancialReconciliationPolicy(
                policyVersion(),
                brl,
                policy(mapOf(FinancialLedgerStage.BANK to "-0.01"))
            )
        }

        val mutable = policy().toMutableMap()
        val frozen = FinancialReconciliationPolicy(policyVersion(), brl, mutable)
        mutable.clear()
        assertEquals(FinancialLedgerStage.entries.size, frozen.tolerancesByStage.size)
        assertEquals("[REDACTED]", frozen.toString())
        assertEquals("[REDACTED]", frozen.version.toString())
        assertEquals(frozen, policyObject())
    }

    @Test
    fun `empty trace is not assessable and currency mismatch is controlled`() {
        val empty = MarketplaceFinancialReconciliation.assess(trace(emptyList()), policyObject())
        assertEquals(
            FinancialReconciliationNotAssessableReason.NO_FINANCIAL_FACTS,
            assertIs<FinancialReconciliationResult.NotAssessable>(empty).reason
        )

        val usdPolicy = FinancialReconciliationPolicy(
            policyVersion(),
            MarketplaceCurrency("USD"),
            FinancialLedgerStage.entries.associateWith {
                MarketplaceMoney.parse(MarketplaceCurrency("USD"), "0")
            }
        )
        assertEquals(
            FinancialReconciliationResult.PolicyCurrencyMismatch,
            MarketplaceFinancialReconciliation.assess(trace(emptyList()), usdPolicy)
        )
    }

    @Test
    fun `exact and inclusive tolerance completion preserve signed difference`() {
        val exact = assessment(
            listOf(
                entry(1, FinancialLedgerStage.SALE, FinancialLedgerBasis.EXPECTED, "299.90"),
                entry(2, FinancialLedgerStage.SALE, FinancialLedgerBasis.ACTUAL, "299.90")
            )
        )
        assertEquals(FinancialReconciliationStatus.FULLY_RECONCILED, exact.status)
        val exactLine = exact.lines.single()
        val exactDifference = assertIs<FinancialReconciliationDifference.Compared>(
            exactLine.difference
        )
        assertEquals(money("0"), exactDifference.signedDifference)
        assertEquals(money("0"), exactDifference.absoluteDifference)

        val withinTolerance = assessment(
            listOf(
                entry(3, FinancialLedgerStage.BANK, FinancialLedgerBasis.EXPECTED, "65.31"),
                entry(4, FinancialLedgerStage.BANK, FinancialLedgerBasis.ACTUAL, "65.30")
            ),
            mapOf(FinancialLedgerStage.BANK to "0.01")
        )
        val difference = assertIs<FinancialReconciliationDifference.Compared>(
            withinTolerance.lines.single().difference
        )
        assertEquals(money("-0.01"), difference.signedDifference)
        assertEquals(money("0.01"), difference.absoluteDifference)
        assertEquals(FinancialReconciliationStatus.FULLY_RECONCILED, withinTolerance.status)
    }

    @Test
    fun `multiple actual facts progress from partial to complete settlement`() {
        val expected = entry(
            1,
            FinancialLedgerStage.SETTLEMENT,
            FinancialLedgerBasis.EXPECTED,
            "65.31"
        )
        val first = entry(2, FinancialLedgerStage.SETTLEMENT, FinancialLedgerBasis.ACTUAL, "20")
        val second = entry(3, FinancialLedgerStage.SETTLEMENT, FinancialLedgerBasis.ACTUAL, "30")
        val partial = assessment(listOf(second, expected, first))
        val line = partial.lines.single()
        assertEquals(FinancialReconciliationStatus.PARTIALLY_RECONCILED, line.status)
        assertEquals(
            money("50"),
            assertIs<FinancialReconciliationSide.Observed>(line.actual).netAmount
        )
        assertEquals(
            money("-15.31"),
            assertIs<FinancialReconciliationDifference.Compared>(line.difference).signedDifference
        )

        val final = entry(4, FinancialLedgerStage.SETTLEMENT, FinancialLedgerBasis.ACTUAL, "15.31")
        assertEquals(
            FinancialReconciliationStatus.FULLY_RECONCILED,
            assessment(listOf(expected, first, second, final)).status
        )
    }

    @Test
    fun `negative commission net exposes exact divergence`() {
        val assessment = assessment(
            listOf(
                entry(
                    1,
                    FinancialLedgerStage.MARKETPLACE_COMMISSION,
                    FinancialLedgerBasis.EXPECTED,
                    "41.99",
                    EconomicDirection.DEDUCTION
                ),
                entry(
                    2,
                    FinancialLedgerStage.MARKETPLACE_COMMISSION,
                    FinancialLedgerBasis.ACTUAL,
                    "42.49",
                    EconomicDirection.DEDUCTION
                )
            ),
            mapOf(FinancialLedgerStage.MARKETPLACE_COMMISSION to "0.01")
        )
        val line = assessment.lines.single()
        assertEquals(FinancialReconciliationStatus.DIVERGENCE, line.status)
        assertEquals(
            money("-41.99"),
            assertIs<FinancialReconciliationSide.Observed>(line.expected).netAmount
        )
        val difference = assertIs<FinancialReconciliationDifference.Compared>(line.difference)
        assertEquals(money("-0.50"), difference.signedDifference)
        assertEquals(money("0.5"), difference.absoluteDifference)
    }

    @Test
    fun `absence stays distinct from observed zero and unexpected actual diverges`() {
        val expectedOnly = assessment(
            listOf(entry(1, FinancialLedgerStage.SHIPPING, FinancialLedgerBasis.EXPECTED, "10"))
        ).lines.single()
        assertIs<FinancialReconciliationSide.NotObserved>(expectedOnly.actual)
        assertIs<FinancialReconciliationDifference.NotComparable>(expectedOnly.difference)
        assertEquals(FinancialReconciliationStatus.PENDING, expectedOnly.status)

        val actualOnly = assessment(
            listOf(entry(2, FinancialLedgerStage.TAX, FinancialLedgerBasis.ACTUAL, "0.01")),
            mapOf(FinancialLedgerStage.TAX to "100")
        ).lines.single()
        assertEquals(FinancialReconciliationStatus.DIVERGENCE, actualOnly.status)

        val observedZero = assessment(
            listOf(
                entry(3, FinancialLedgerStage.BANK, FinancialLedgerBasis.EXPECTED, "10"),
                entry(4, FinancialLedgerStage.BANK, FinancialLedgerBasis.ACTUAL, "10"),
                entry(
                    5,
                    FinancialLedgerStage.BANK,
                    FinancialLedgerBasis.ACTUAL,
                    "10",
                    EconomicDirection.DEDUCTION
                )
            )
        ).lines.single()
        assertEquals(
            money("0"),
            assertIs<FinancialReconciliationSide.Observed>(observedZero.actual).netAmount
        )
        assertEquals(FinancialReconciliationStatus.DIVERGENCE, observedZero.status)
    }

    @Test
    fun `underpayment is partial for either sign while overpayment and opposite sign diverge`() {
        val negativePartial = assessment(
            listOf(
                entry(
                    1,
                    FinancialLedgerStage.MARKETPLACE_FEE,
                    FinancialLedgerBasis.EXPECTED,
                    "65",
                    EconomicDirection.DEDUCTION
                ),
                entry(
                    2,
                    FinancialLedgerStage.MARKETPLACE_FEE,
                    FinancialLedgerBasis.ACTUAL,
                    "50",
                    EconomicDirection.DEDUCTION
                )
            )
        )
        assertEquals(FinancialReconciliationStatus.PARTIALLY_RECONCILED, negativePartial.status)

        val overpayment = assessment(
            listOf(
                entry(3, FinancialLedgerStage.PAYMENT_ACCOUNT, FinancialLedgerBasis.EXPECTED, "50"),
                entry(4, FinancialLedgerStage.PAYMENT_ACCOUNT, FinancialLedgerBasis.ACTUAL, "51")
            )
        )
        assertEquals(FinancialReconciliationStatus.DIVERGENCE, overpayment.status)

        val opposite = assessment(
            listOf(
                entry(5, FinancialLedgerStage.SALE, FinancialLedgerBasis.EXPECTED, "50"),
                entry(
                    6,
                    FinancialLedgerStage.SALE,
                    FinancialLedgerBasis.ACTUAL,
                    "20",
                    EconomicDirection.DEDUCTION
                )
            )
        )
        assertEquals(FinancialReconciliationStatus.DIVERGENCE, opposite.status)
    }

    @Test
    fun `correction leaves replace ancestors while reversals remain effective`() {
        val expectedBank = entry(1, FinancialLedgerStage.BANK, FinancialLedgerBasis.EXPECTED, "65.31")
        val original = entry(2, FinancialLedgerStage.BANK, FinancialLedgerBasis.ACTUAL, "60")
        val correction = entry(
            3,
            FinancialLedgerStage.BANK,
            FinancialLedgerBasis.ACTUAL,
            "65.31",
            correctsEntryId = original.id
        )
        val bankLine = assessment(listOf(correction, original, expectedBank)).lines.single()
        val actual = assertIs<FinancialReconciliationSide.Observed>(bankLine.actual)
        assertEquals(listOf(correction.id), actual.effectiveEntryIds)
        assertEquals(money("65.31"), actual.netAmount)
        assertEquals(FinancialReconciliationStatus.FULLY_RECONCILED, bankLine.status)

        val expectedFee = entry(
            4,
            FinancialLedgerStage.FINANCIAL_COST,
            FinancialLedgerBasis.EXPECTED,
            "8",
            EconomicDirection.DEDUCTION
        )
        val charged = entry(
            5,
            FinancialLedgerStage.FINANCIAL_COST,
            FinancialLedgerBasis.ACTUAL,
            "10",
            EconomicDirection.DEDUCTION
        )
        val reversal = entry(
            6,
            FinancialLedgerStage.FINANCIAL_COST,
            FinancialLedgerBasis.ACTUAL,
            "2",
            EconomicDirection.ADDITION
        )
        val feeLine = assessment(listOf(expectedFee, charged, reversal)).lines.single()
        assertEquals(
            money("-8"),
            assertIs<FinancialReconciliationSide.Observed>(feeLine.actual).netAmount
        )
        assertEquals(FinancialReconciliationStatus.FULLY_RECONCILED, feeLine.status)
    }

    @Test
    fun `aggregate precedence distinguishes pending partial full and divergence`() {
        val pending = assessment(
            listOf(
                entry(1, FinancialLedgerStage.SALE, FinancialLedgerBasis.EXPECTED, "10"),
                entry(2, FinancialLedgerStage.SHIPPING, FinancialLedgerBasis.EXPECTED, "2")
            )
        )
        assertEquals(FinancialReconciliationStatus.PENDING, pending.status)

        val mixed = assessment(
            listOf(
                entry(3, FinancialLedgerStage.SALE, FinancialLedgerBasis.EXPECTED, "10"),
                entry(4, FinancialLedgerStage.SALE, FinancialLedgerBasis.ACTUAL, "10"),
                entry(5, FinancialLedgerStage.SHIPPING, FinancialLedgerBasis.EXPECTED, "2")
            )
        )
        assertEquals(FinancialReconciliationStatus.PARTIALLY_RECONCILED, mixed.status)

        val divergent = assessment(
            listOf(
                entry(6, FinancialLedgerStage.SALE, FinancialLedgerBasis.EXPECTED, "10"),
                entry(7, FinancialLedgerStage.SALE, FinancialLedgerBasis.ACTUAL, "10"),
                entry(8, FinancialLedgerStage.TAX, FinancialLedgerBasis.ACTUAL, "1")
            )
        )
        assertEquals(FinancialReconciliationStatus.DIVERGENCE, divergent.status)
    }

    @Test
    fun `line and evidence ordering are deterministic under shuffled inputs`() {
        val unsignedHigh = entry(
            1,
            FinancialLedgerStage.SALE,
            FinancialLedgerBasis.ACTUAL,
            "5",
            id = FinancialLedgerEntryId.parse("80000000-0000-0000-0000-000000000000")
        )
        val unsignedLow = entry(
            2,
            FinancialLedgerStage.SALE,
            FinancialLedgerBasis.ACTUAL,
            "5",
            id = FinancialLedgerEntryId.parse("7fffffff-ffff-ffff-ffff-ffffffffffff")
        )
        val expectedSale = entry(3, FinancialLedgerStage.SALE, FinancialLedgerBasis.EXPECTED, "10")
        val expectedTax = entry(4, FinancialLedgerStage.TAX, FinancialLedgerBasis.EXPECTED, "1")
        val entries = listOf(unsignedHigh, expectedTax, expectedSale, unsignedLow)

        val first = assessment(entries)
        val second = assessment(entries.reversed())
        assertEquals(first, second)
        assertEquals(
            listOf(FinancialLedgerStage.SALE, FinancialLedgerStage.TAX),
            first.lines.map { it.stage }
        )
        val saleActual = assertIs<FinancialReconciliationSide.Observed>(first.lines.first().actual)
        assertEquals(unsignedLow.id, saleActual.effectiveEntryIds[0])
        assertEquals(unsignedHigh.id, saleActual.effectiveEntryIds[1])
    }

    @Test
    fun `aggregate rendering discloses no organizational or financial value`() {
        val result = MarketplaceFinancialReconciliation.assess(
            trace(
                listOf(
                    entry(1, FinancialLedgerStage.SALE, FinancialLedgerBasis.EXPECTED, "10"),
                    entry(2, FinancialLedgerStage.SALE, FinancialLedgerBasis.ACTUAL, "10")
                )
            ),
            policyObject()
        )
        val assessed = assertIs<FinancialReconciliationResult.Assessed>(result)
        val line = assessed.assessment.lines.single()
        val observed = assertIs<FinancialReconciliationSide.Observed>(line.expected)
        val compared = assertIs<FinancialReconciliationDifference.Compared>(line.difference)
        val renderings = listOf(
            result.toString(),
            assessed.assessment.toString(),
            line.toString(),
            observed.toString(),
            compared.toString(),
            FinancialReconciliationResult.NotAssessable(
                FinancialReconciliationNotAssessableReason.NO_FINANCIAL_FACTS
            ).toString(),
            FinancialReconciliationResult.PolicyCurrencyMismatch.toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
        renderings.forEach { assertNotEquals(organizationId.value.toString(), it) }
    }

    private fun assessment(
        entries: Collection<RecordedFinancialLedgerEntry>,
        overrides: Map<FinancialLedgerStage, String> = emptyMap()
    ): FinancialReconciliationAssessment = assertIs<FinancialReconciliationResult.Assessed>(
        MarketplaceFinancialReconciliation.assess(trace(entries), policyObject(overrides))
    ).assessment

    private fun trace(entries: Collection<RecordedFinancialLedgerEntry>) = FinancialTrace(
        organizationId = organizationId,
        id = traceId,
        requestId = FinancialTraceOpenRequestId.of(UUID(0, 1)),
        orderId = orderId,
        marketplace = MarketplaceKey("mercado-livre"),
        externalOrderId = MarketplaceExternalOrderId("order-001"),
        currency = brl,
        openedAt = Instant.parse("2026-08-13T13:00:00Z"),
        entries = entries
    )

    private fun policyObject(
        overrides: Map<FinancialLedgerStage, String> = emptyMap()
    ) = FinancialReconciliationPolicy(policyVersion(), brl, policy(overrides))

    private fun policy(
        overrides: Map<FinancialLedgerStage, String> = emptyMap()
    ): Map<FinancialLedgerStage, MarketplaceMoney> =
        FinancialLedgerStage.entries.associateWith { stage ->
            money(overrides[stage] ?: "0")
        }

    private fun policyVersion() =
        FinancialReconciliationPolicyVersion("marketplace-financial-reconciliation/1")

    private fun entry(
        number: Int,
        stage: FinancialLedgerStage,
        basis: FinancialLedgerBasis,
        amount: String,
        direction: EconomicDirection = EconomicDirection.ADDITION,
        id: FinancialLedgerEntryId = FinancialLedgerEntryId.of(UUID(1, number.toLong())),
        correctsEntryId: FinancialLedgerEntryId? = null
    ) = RecordedFinancialLedgerEntry(
        organizationId = organizationId,
        id = id,
        requestId = FinancialLedgerAppendRequestId.of(UUID(2, number.toLong())),
        traceId = traceId,
        stage = stage,
        basis = basis,
        direction = direction,
        magnitude = money(amount),
        source = EconomicSource(
            EconomicSourceKind.MARKETPLACE,
            EconomicSourceSystemKey("meli-br"),
            EconomicExternalReferenceState.Present(EconomicExternalReference("fact-$number"))
        ),
        occurredAt = Instant.parse("2026-08-13T14:00:00Z").plusSeconds(number.toLong()),
        recordedAt = Instant.parse("2026-08-13T15:00:00Z").plusSeconds(number.toLong()),
        correctsEntryId = correctsEntryId
    )

    private fun money(amount: String) = MarketplaceMoney.parse(brl, amount)
}
