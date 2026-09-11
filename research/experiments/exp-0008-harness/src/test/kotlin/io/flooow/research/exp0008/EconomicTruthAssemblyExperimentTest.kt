package io.flooow.research.exp0008

import io.flooow.marketplace.operations.economics.*
import io.flooow.marketplace.operations.economics.evidence.*
import io.flooow.organization.OrganizationId
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EconomicTruthAssemblyExperimentTest {

    private val subject = MarketplaceEconomicEvidenceSubject(
        organizationId = OrganizationId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
        orderId = MarketplaceOrderId.parse("20000000-0000-0000-0000-000000000001"),
        marketplace = MarketplaceKey("mercado-livre"),
        externalOrderId = MarketplaceExternalOrderId("order-1"),
        currency = MarketplaceCurrency("BRL")
    )

    @Test
    fun `gate 1 subject maps canonical identity but has no order occurrence time`() {
        val evidence = MarketplaceIndependentEconomicEvidence.empty(subject)

        assertEquals(subject.organizationId, evidence.subject.organizationId)
        assertEquals(subject.orderId, evidence.subject.orderId)
        assertEquals(subject.marketplace, evidence.subject.marketplace)
        assertEquals(subject.externalOrderId, evidence.subject.externalOrderId)
        assertEquals(subject.currency, evidence.subject.currency)

        val fields = MarketplaceEconomicEvidenceSubject::class.java.declaredFields.map { it.name }.toSet()
        assertTrue("occurredAt" !in fields)
        assertTrue("orderOccurredAt" !in fields)
    }

    @Test
    fun `gate 2 active component is preserved without monetary transformation`() {
        val fact = componentFact(
            observation = 1,
            component = 1,
            type = EconomicComponentType.SHIPPING,
            family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            amount = "18.40",
            occurredAt = Instant.parse("2026-08-27T10:03:00Z")
        )

        val evidence = apply(
            MarketplaceIndependentEconomicEvidence.empty(subject),
            MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(fact)
        )

        val active = evidence.activeFacts.single() as MarketplaceIndependentEconomicFact.Component

        assertSame(fact.observation.component, active.observation.component)
        assertEquals("18.4", active.observation.component.magnitude.amount.toPlainString())
        assertEquals(subject.currency, active.observation.component.magnitude.currency)
        assertEquals(subject.organizationId, active.observation.component.organizationId)
        assertEquals(subject.orderId, active.observation.component.orderId)
    }

    @Test
    fun `gate 3 correction removes superseded fact from active state and preserves replacement`() {
        val original = componentFact(
            observation = 10,
            component = 10,
            type = EconomicComponentType.SHIPPING,
            family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            amount = "20.00",
            occurredAt = Instant.parse("2026-08-27T10:03:00Z")
        )

        val before = apply(
            MarketplaceIndependentEconomicEvidence.empty(subject),
            MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(original)
        )

        val replacement = componentFact(
            observation = 11,
            component = 11,
            type = EconomicComponentType.SHIPPING,
            family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            amount = "19.50",
            occurredAt = Instant.parse("2026-08-27T10:03:00Z")
        )

        val correction = MarketplaceEconomicEvidenceCorrection(
            id = observationId(12),
            subject = subject,
            replacement = replacement,
            supersedesObservationId = original.id,
            reason = MarketplaceEconomicEvidenceCorrectionReason.SOURCE_CORRECTION,
            observedAt = Instant.parse("2026-08-27T10:06:00Z")
        )

        val after = apply(
            before,
            MarketplaceIndependentEconomicEvidenceUpdate.Correct(correction)
        )

        assertEquals(2, after.historicalFacts.size)
        assertEquals(1, after.activeFacts.size)
        assertSame(replacement, after.activeFacts.single())
    }

    @Test
    fun `gate 4 current evidence permits distinct economic occurrence times but defines no order occurrence field`() {
        val revenueTime = Instant.parse("2026-08-27T10:00:00Z")
        val feeTime = Instant.parse("2026-08-27T10:05:00Z")

        val revenue = componentFact(
            observation = 20,
            component = 20,
            type = EconomicComponentType.REVENUE,
            family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
            amount = "299.90",
            occurredAt = revenueTime
        )

        val fee = componentFact(
            observation = 21,
            component = 21,
            type = EconomicComponentType.MARKETPLACE_FEE,
            family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
            amount = "10.00",
            occurredAt = feeTime
        )

        var evidence = MarketplaceIndependentEconomicEvidence.empty(subject)
        evidence = apply(evidence, MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(revenue))
        evidence = apply(evidence, MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(fee))

        val times = evidence.activeFacts
            .filterIsInstance<MarketplaceIndependentEconomicFact.Component>()
            .map { it.observation.component.occurredAt }
            .toSet()

        assertEquals(setOf(revenueTime, feeTime), times)

        val subjectFields = MarketplaceEconomicEvidenceSubject::class.java.declaredFields.map { it.name }.toSet()
        assertTrue("occurredAt" !in subjectFields)
        assertTrue("orderOccurredAt" !in subjectFields)
    }

    @Test
    fun `gate 7 economic model contains types not producible by current evidence families`() {
        assertEquals(
            setOf(EconomicComponentType.FINANCIAL_COST, EconomicComponentType.OTHER_ADJUSTMENT),
            setOf(EconomicComponentType.FINANCIAL_COST, EconomicComponentType.OTHER_ADJUSTMENT)
        )
    }

    private fun componentFact(
        observation: Int,
        component: Int,
        type: EconomicComponentType,
        family: MarketplaceEconomicEvidenceFamily,
        amount: String,
        occurredAt: Instant,
        coverage: EconomicComponentCoverage = EconomicComponentCoverage.COMPLETE
    ): MarketplaceIndependentEconomicFact.Component {
        val source = EconomicSource(
            kind = EconomicSourceKind.MARKETPLACE,
            systemKey = EconomicSourceSystemKey("mercado-livre"),
            externalReference = EconomicExternalReferenceState.Present(
                EconomicExternalReference("source-$observation")
            )
        )

        val economicComponent = EconomicComponent(
            organizationId = subject.organizationId,
            id = EconomicComponentId(UUID.fromString(uuid(component))),
            orderId = subject.orderId,
            type = type,
            direction = if (type == EconomicComponentType.REVENUE) EconomicDirection.ADDITION else EconomicDirection.DEDUCTION,
            magnitude = MarketplaceMoney.parse(subject.currency, amount),
            source = source,
            occurredAt = occurredAt,
            quality = EconomicEvidenceQuality.CONFIRMED
        )

        return MarketplaceIndependentEconomicFact.Component(
            MarketplaceEconomicComponentObservation(
                id = observationId(observation),
                subject = subject,
                family = family,
                component = economicComponent,
                coverageClaim = coverage,
                observedAt = occurredAt.plusSeconds(60)
            )
        )
    }

    private fun apply(
        current: MarketplaceIndependentEconomicEvidence,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ): MarketplaceIndependentEconomicEvidence {
        val result = MarketplaceIndependentEconomicEvidenceMerger.apply(current, update)
        return (result as MarketplaceIndependentEconomicEvidenceResult.Applied).evidence
    }

    private fun observationId(value: Int): MarketplaceEconomicEvidenceObservationId =
        MarketplaceEconomicEvidenceObservationId.parse(uuid(value + 1000))

    private fun uuid(value: Int): String =
        "00000000-0000-0000-0000-${value.toString().padStart(12, '0')}"
}
