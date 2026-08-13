package io.flooow.marketplace.operations.economics.ledger

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
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarketplaceFinancialLedgerTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val traceId = FinancialTraceId.parse("20000000-0000-0000-0000-000000000001")
    private val orderId = MarketplaceOrderId.parse("30000000-0000-0000-0000-000000000001")
    private val brl = MarketplaceCurrency("BRL")
    private val occurredAt = Instant.parse("2026-08-13T14:00:00.123456Z")

    @Test
    fun `compiled financial ledger boundary contains no Kernel reference`() {
        val productionClasses = java.nio.file.Path.of(
            FinancialTrace::class.java.protectionDomain.codeSource.location.toURI()
        )
        val packageDirectory = productionClasses.resolve(
            "io/flooow/marketplace/operations/economics/ledger"
        )

        Files.walk(packageDirectory).use { files ->
            val classFiles = files.filter { it.toString().endsWith(".class") }.toList()
            assertTrue(classFiles.isNotEmpty())
            classFiles.forEach { classFile ->
                val bytecodeText = String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1)
                assertTrue(
                    "io/flooow/kernel" !in bytecodeText,
                    "Financial ledger bytecode must not reference Kernel: ${classFile.fileName}"
                )
            }
        }
    }

    @Test
    fun `identities parse canonically and render internally`() {
        assertFailsWith<IllegalArgumentException> {
            FinancialTraceId.parse("20000000-0000-0000-0000-00000000000A")
        }
        assertFailsWith<IllegalArgumentException> { FinancialLedgerEntryId.parse("invalid") }

        assertEquals("[INTERNAL]", traceId.toString())
        assertEquals("[INTERNAL]", entryId(1).toString())
        assertEquals("[INTERNAL]", appendRequestId(1).toString())
        assertEquals(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            traceId.valueForPersistence()
        )
    }

    @Test
    fun `draft requires nonnegative magnitude and exact database time precision`() {
        assertFailsWith<IllegalArgumentException> { draft(1, amount = "-0.01") }
        assertFailsWith<IllegalArgumentException> {
            draft(1, occurredAt = Instant.parse("2026-08-13T14:00:00.123456789Z"))
        }
        assertEquals("[REDACTED]", draft(1).toString())
    }

    @Test
    fun `trace canonicalizes entry order by database time then unsigned UUID`() {
        val unsignedHigh = recorded(
            1,
            id = FinancialLedgerEntryId.parse("80000000-0000-0000-0000-000000000000"),
            recordedAt = Instant.parse("2026-08-13T15:00:00Z")
        )
        val unsignedLow = recorded(
            2,
            id = FinancialLedgerEntryId.parse("7fffffff-ffff-ffff-ffff-ffffffffffff"),
            recordedAt = Instant.parse("2026-08-13T15:00:00Z")
        )
        val earlier = recorded(
            3,
            recordedAt = Instant.parse("2026-08-13T14:59:59.999999Z")
        )

        val trace = trace(listOf(unsignedHigh, earlier, unsignedLow))
        assertSame(earlier, trace.entries[0])
        assertSame(unsignedLow, trace.entries[1])
        assertSame(unsignedHigh, trace.entries[2])
    }

    @Test
    fun `trace rejects foreign scope currency identity request and source duplicates`() {
        val base = recorded(1)
        assertFailsWith<IllegalArgumentException> {
            trace(listOf(base.copy(organizationId = OrganizationId(UUID(0, 99)))))
        }
        assertFailsWith<IllegalArgumentException> {
            trace(listOf(base.copy(traceId = FinancialTraceId.of(UUID(0, 99)))))
        }
        assertFailsWith<IllegalArgumentException> {
            trace(
                listOf(
                    base.copy(
                        magnitude = MarketplaceMoney.parse(MarketplaceCurrency("USD"), "10")
                    )
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            trace(listOf(base, recorded(2, id = base.id)))
        }
        assertFailsWith<IllegalArgumentException> {
            trace(listOf(base, recorded(2, requestId = base.requestId)))
        }
        assertFailsWith<IllegalArgumentException> {
            trace(listOf(base, recorded(2, sourceReference = "fact-1")))
        }
    }

    @Test
    fun `correction chains preserve originals and enforce linear same-stage basis`() {
        val original = recorded(1, stage = FinancialLedgerStage.SHIPPING)
        val correction = recorded(
            2,
            stage = FinancialLedgerStage.SHIPPING,
            correctsEntryId = original.id
        )
        val secondCorrection = recorded(
            3,
            stage = FinancialLedgerStage.SHIPPING,
            correctsEntryId = correction.id
        )
        assertEquals(3, trace(listOf(secondCorrection, original, correction)).entries.size)

        assertFailsWith<IllegalArgumentException> {
            trace(listOf(correction))
        }
        assertFailsWith<IllegalArgumentException> {
            trace(
                listOf(
                    original,
                    correction.copy(stage = FinancialLedgerStage.TAX)
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            trace(
                listOf(
                    original,
                    correction,
                    recorded(
                        4,
                        stage = FinancialLedgerStage.SHIPPING,
                        correctsEntryId = original.id
                    )
                )
            )
        }
    }

    @Test
    fun `all accepted stages bases and directions remain explicit facts`() {
        val entries = FinancialLedgerStage.entries.flatMapIndexed { index, stage ->
            FinancialLedgerBasis.entries.mapIndexed { basisIndex, basis ->
                recorded(
                    number = index * 10 + basisIndex + 1,
                    stage = stage,
                    basis = basis,
                    direction = if (basis == FinancialLedgerBasis.EXPECTED) {
                        EconomicDirection.DEDUCTION
                    } else {
                        EconomicDirection.ADDITION
                    }
                )
            }
        }
        val trace = trace(entries)
        assertEquals(FinancialLedgerStage.entries.size * 2, trace.entries.size)
        assertEquals(FinancialLedgerStage.entries.toSet(), trace.entries.map { it.stage }.toSet())
        assertEquals(FinancialLedgerBasis.entries.toSet(), trace.entries.map { it.basis }.toSet())
        assertEquals(EconomicDirection.entries.toSet(), trace.entries.map { it.direction }.toSet())
    }

    @Test
    fun `aggregate and result rendering disclose no financial values`() {
        val trace = trace(listOf(recorded(1)))
        val renderings = listOf(
            trace.toString(),
            trace.entries.single().toString(),
            FinancialTraceOpenResult.Opened(traceId).toString(),
            FinancialLedgerAppendResult.Appended(entryId(1)).toString(),
            FinancialTraceReadResult.Found(trace).toString()
        )
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)
    }

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

    private fun draft(
        number: Int,
        stage: FinancialLedgerStage = FinancialLedgerStage.SALE,
        basis: FinancialLedgerBasis = FinancialLedgerBasis.EXPECTED,
        direction: EconomicDirection = EconomicDirection.ADDITION,
        amount: String = "10",
        sourceReference: String = "fact-$number",
        occurredAt: Instant = this.occurredAt,
        correctsEntryId: FinancialLedgerEntryId? = null
    ) = FinancialLedgerEntryDraft(
        organizationId = organizationId,
        requestId = appendRequestId(number),
        traceId = traceId,
        stage = stage,
        basis = basis,
        direction = direction,
        magnitude = MarketplaceMoney.parse(brl, amount),
        source = source(sourceReference),
        occurredAt = occurredAt,
        correctsEntryId = correctsEntryId
    )

    private fun recorded(
        number: Int,
        id: FinancialLedgerEntryId = entryId(number),
        requestId: FinancialLedgerAppendRequestId = appendRequestId(number),
        stage: FinancialLedgerStage = FinancialLedgerStage.SALE,
        basis: FinancialLedgerBasis = FinancialLedgerBasis.EXPECTED,
        direction: EconomicDirection = EconomicDirection.ADDITION,
        sourceReference: String = "fact-$number",
        recordedAt: Instant = occurredAt.plusSeconds(number.toLong()),
        correctsEntryId: FinancialLedgerEntryId? = null
    ) = RecordedFinancialLedgerEntry(
        organizationId = organizationId,
        id = id,
        requestId = requestId,
        traceId = traceId,
        stage = stage,
        basis = basis,
        direction = direction,
        magnitude = MarketplaceMoney.parse(brl, "10"),
        source = source(sourceReference),
        occurredAt = occurredAt,
        recordedAt = recordedAt,
        correctsEntryId = correctsEntryId
    )

    private fun source(reference: String) = EconomicSource(
        EconomicSourceKind.MARKETPLACE,
        EconomicSourceSystemKey("meli-br"),
        EconomicExternalReferenceState.Present(EconomicExternalReference(reference))
    )

    private fun entryId(number: Int) = FinancialLedgerEntryId.of(UUID(1, number.toLong()))

    private fun appendRequestId(number: Int) =
        FinancialLedgerAppendRequestId.of(UUID(2, number.toLong()))
}
