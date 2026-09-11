package io.flooow.marketplace.operations.economics

import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicComponentObservation
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceAttemptOutcome
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceCollectionAttempt
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceCorrection
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceCorrectionReason
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceFamily
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceObservationId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicOrderOccurrenceObservation
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidence
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceMerger
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceUpdate
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicFact
import io.flooow.organization.OrganizationId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarketplaceEconomicTruthAssemblerTest {
    private val subject = MarketplaceEconomicEvidenceSubject(
        OrganizationId.parse("10000000-0000-0000-0000-000000000001"),
        MarketplaceOrderId.parse("20000000-0000-0000-0000-000000000001"),
        MarketplaceKey("mercado-livre"),
        MarketplaceExternalOrderId("order-1"),
        MarketplaceCurrency("BRL")
    )

    private val occurredAt = Instant.parse("2026-08-27T10:00:00.123456Z")
    private val observedAt = Instant.parse("2026-08-27T10:01:00.654321Z")

    @Test
    fun `no active order occurrence is unresolved`() {
        val result = assertIs<MarketplaceEconomicTruthAssemblyResult.NotReady>(
            MarketplaceEconomicTruthAssembler.assemble(empty())
        )
        assertEquals(
            setOf(MarketplaceEconomicTruthAssemblyNotReadyReason.ORDER_OCCURRED_AT_UNRESOLVED),
            result.reasons
        )
        assertEquals(MarketplaceEconomicTruthAssembler.POLICY_VERSION, result.assemblyPolicyVersion)
    }

    @Test
    fun `one occurrence creates a ready marketplace order`() {
        val evidence = applied(empty(), observe(occurrenceFact(1)))
        val result = assertIs<MarketplaceEconomicTruthAssemblyResult.Ready>(
            MarketplaceEconomicTruthAssembler.assemble(evidence)
        )
        assertEquals(subject.organizationId, result.order.organizationId)
        assertEquals(subject.orderId, result.order.id)
        assertEquals(subject.marketplace, result.order.marketplace)
        assertEquals(subject.externalOrderId, result.order.externalOrderId)
        assertEquals(subject.currency, result.order.currency)
        assertEquals(occurredAt, result.order.occurredAt)
    }

    @Test
    fun `equal independent occurrence times remain ready`() {
        val first = occurrenceFact(10, reference = "occurrence-a")
        val second = occurrenceFact(11, reference = "occurrence-b")
        val evidence = applied(applied(empty(), observe(first)), observe(second))
        val result = assertIs<MarketplaceEconomicTruthAssemblyResult.Ready>(
            MarketplaceEconomicTruthAssembler.assemble(evidence)
        )
        assertEquals(occurredAt, result.order.occurredAt)
    }

    @Test
    fun `distinct active occurrence times fail closed`() {
        val first = occurrenceFact(20, reference = "occurrence-a")
        val second = occurrenceFact(
            21,
            reference = "occurrence-b",
            occurred = occurredAt.plusSeconds(1)
        )
        val evidence = applied(applied(empty(), observe(first)), observe(second))
        val result = assertIs<MarketplaceEconomicTruthAssemblyResult.NotReady>(
            MarketplaceEconomicTruthAssembler.assemble(evidence)
        )
        assertEquals(
            setOf(MarketplaceEconomicTruthAssemblyNotReadyReason.ORDER_OCCURRED_AT_CONFLICT),
            result.reasons
        )
    }

    @Test
    fun `corrected occurrence uses replacement and ignores superseded time`() {
        val source = externalSource("corrected-occurrence")
        val original = occurrenceFact(30, source = source)
        val before = applied(empty(), observe(original))
        val replacement = occurrenceFact(
            31,
            source = source,
            occurred = occurredAt.plusSeconds(5),
            observed = observedAt.plusSeconds(1)
        )
        val correction = MarketplaceEconomicEvidenceCorrection(
            observationId(32),
            subject,
            replacement,
            original.id,
            MarketplaceEconomicEvidenceCorrectionReason.SOURCE_CORRECTION,
            observedAt.plusSeconds(2)
        )
        val evidence = applied(
            before,
            MarketplaceIndependentEconomicEvidenceUpdate.Correct(correction)
        )
        assertTrue(original in evidence.historicalFacts)
        assertFalse(original in evidence.activeFacts)
        val result = assertIs<MarketplaceEconomicTruthAssemblyResult.Ready>(
            MarketplaceEconomicTruthAssembler.assemble(evidence)
        )
        assertEquals(occurredAt.plusSeconds(5), result.order.occurredAt)
    }

    @Test
    fun `version one coverage is missing or partial only`() {
        val occurrenceOnly = applied(empty(), observe(occurrenceFact(40)))
        val emptyResult = assertIs<MarketplaceEconomicTruthAssemblyResult.Ready>(
            MarketplaceEconomicTruthAssembler.assemble(occurrenceOnly)
        )
        EconomicComponentType.entries.forEach { type ->
            assertEquals(EconomicComponentCoverage.MISSING, emptyResult.order.coverage.getValue(type))
        }

        val shipping = componentFact(41, coverageClaim = EconomicComponentCoverage.COMPLETE)
        val withShipping = applied(occurrenceOnly, observe(shipping))
        val result = assertIs<MarketplaceEconomicTruthAssemblyResult.Ready>(
            MarketplaceEconomicTruthAssembler.assemble(withShipping)
        )
        assertEquals(
            EconomicComponentCoverage.PARTIAL,
            result.order.coverage.getValue(EconomicComponentType.SHIPPING)
        )
        assertTrue(result.order.coverage.values.none { it == EconomicComponentCoverage.COMPLETE })
        assertTrue(result.order.coverage.values.none { it == EconomicComponentCoverage.NOT_APPLICABLE })
        assertEquals(
            EconomicComponentCoverage.MISSING,
            result.order.coverage.getValue(EconomicComponentType.FINANCIAL_COST)
        )
        assertEquals(
            EconomicComponentCoverage.MISSING,
            result.order.coverage.getValue(EconomicComponentType.OTHER_ADJUSTMENT)
        )
    }

    @Test
    fun `active economic component is preserved exactly`() {
        val occurrence = occurrenceFact(50)
        val component = componentFact(51)
        val evidence = applied(applied(empty(), observe(occurrence)), observe(component))
        val result = assertIs<MarketplaceEconomicTruthAssemblyResult.Ready>(
            MarketplaceEconomicTruthAssembler.assemble(evidence)
        )
        assertEquals(1, result.order.components.size)
        assertSame(component.observation.component, result.order.components.single())
    }

    @Test
    fun `collection attempts cannot manufacture or change assembly`() {
        val occurrence = occurrenceFact(60)
        val component = componentFact(61)
        val baseline = applied(applied(empty(), observe(occurrence)), observe(component))
        val attempt = MarketplaceEconomicEvidenceCollectionAttempt(
            observationId(62),
            subject,
            MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            EconomicSourceSystemKey("mercado-livre"),
            MarketplaceEconomicEvidenceAttemptOutcome.TEMPORARY_FAILURE,
            observedAt.plusSeconds(10)
        )
        val withAttempt = applied(
            baseline,
            MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(attempt)
        )
        assertEquals(
            MarketplaceEconomicTruthAssembler.assemble(baseline),
            MarketplaceEconomicTruthAssembler.assemble(withAttempt)
        )
    }

    @Test
    fun `ready assembly may still calculate incomplete`() {
        val evidence = applied(
            applied(empty(), observe(occurrenceFact(70))),
            observe(componentFact(71))
        )
        val ready = assertIs<MarketplaceEconomicTruthAssemblyResult.Ready>(
            MarketplaceEconomicTruthAssembler.assemble(evidence)
        )
        assertIs<MarketplaceEconomicTruthCalculationResult.Incomplete>(
            MarketplaceEconomicTruthCalculator.calculate(ready.order)
        )
    }

    @Test
    fun `equivalent legal insertion orders produce equal results`() {
        val occurrence = occurrenceFact(80)
        val first = componentFact(81, reference = "shipping-a", componentIdNumber = 81)
        val second = componentFact(82, reference = "shipping-b", componentIdNumber = 82)
        val left = applied(
            applied(applied(empty(), observe(second)), observe(occurrence)),
            observe(first)
        )
        val right = applied(
            applied(applied(empty(), observe(first)), observe(second)),
            observe(occurrence)
        )
        val leftResult = MarketplaceEconomicTruthAssembler.assemble(left)
        val rightResult = MarketplaceEconomicTruthAssembler.assemble(right)
        assertEquals(leftResult, rightResult)
        assertEquals(leftResult, MarketplaceEconomicTruthAssembler.assemble(left))
    }

    @Test
    fun `duplicate component ids produce inconsistent active facts`() {
        val occurrence = occurrenceFact(90)
        val first = componentFact(91, reference = "shipping-a", componentIdNumber = 999)
        val second = componentFact(92, reference = "shipping-b", componentIdNumber = 999)
        val evidence = applied(
            applied(applied(empty(), observe(occurrence)), observe(first)),
            observe(second)
        )
        val result = assertIs<MarketplaceEconomicTruthAssemblyResult.NotReady>(
            MarketplaceEconomicTruthAssembler.assemble(evidence)
        )
        assertEquals(
            setOf(MarketplaceEconomicTruthAssemblyNotReadyReason.INCONSISTENT_ACTIVE_FACTS),
            result.reasons
        )
    }

    @Test
    fun `assembly policy and controlled renderings are exact and redacted`() {
        val unresolved = MarketplaceEconomicTruthAssembler.assemble(empty())
        assertEquals(
            MarketplaceEconomicTruthAssemblyPolicyVersion("marketplace-economic-truth-assembly/1"),
            MarketplaceEconomicTruthAssembler.POLICY_VERSION
        )
        assertEquals("[REDACTED]", MarketplaceEconomicTruthAssembler.POLICY_VERSION.toString())
        assertEquals("[REDACTED]", unresolved.toString())
        MarketplaceEconomicTruthAssemblyNotReadyReason.entries.forEach { reason ->
            assertEquals("[REDACTED]", reason.toString())
        }
        assertEquals("[REDACTED]", MarketplaceEconomicTruthAssembler.toString())
    }

    private fun empty() = MarketplaceIndependentEconomicEvidence.empty(subject)

    private fun observe(fact: MarketplaceIndependentEconomicFact) =
        MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(fact)

    private fun applied(
        current: MarketplaceIndependentEconomicEvidence,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ): MarketplaceIndependentEconomicEvidence =
        assertIs<MarketplaceIndependentEconomicEvidenceResult.Applied>(
            MarketplaceIndependentEconomicEvidenceMerger.apply(current, update)
        ).evidence

    private fun occurrenceFact(
        id: Int,
        reference: String = "occurrence-$id",
        source: EconomicSource = externalSource(reference),
        occurred: Instant = occurredAt,
        observed: Instant = observedAt
    ) = MarketplaceIndependentEconomicFact.OrderOccurrence(
        MarketplaceEconomicOrderOccurrenceObservation(
            observationId(id),
            subject,
            source,
            occurred,
            observed
        )
    )

    private fun componentFact(
        id: Int,
        reference: String = "shipping-$id",
        componentIdNumber: Int = id,
        coverageClaim: EconomicComponentCoverage = EconomicComponentCoverage.COMPLETE
    ) = MarketplaceIndependentEconomicFact.Component(
        MarketplaceEconomicComponentObservation(
            observationId(id),
            subject,
            MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            EconomicComponent(
                subject.organizationId,
                componentId(componentIdNumber),
                subject.orderId,
                EconomicComponentType.SHIPPING,
                EconomicDirection.DEDUCTION,
                MarketplaceMoney.parse(subject.currency, "10.00"),
                externalSource(reference),
                occurredAt,
                EconomicEvidenceQuality.CONFIRMED
            ),
            coverageClaim,
            observedAt
        )
    )

    private fun externalSource(reference: String) = EconomicSource(
        EconomicSourceKind.MARKETPLACE,
        EconomicSourceSystemKey("mercado-livre"),
        EconomicExternalReferenceState.Present(EconomicExternalReference(reference))
    )

    private fun observationId(number: Int) =
        MarketplaceEconomicEvidenceObservationId.parse(
            "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
        )

    private fun componentId(number: Int) = EconomicComponentId.parse(
        "30000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    )
}
