package io.flooow.research.exp0008

import io.flooow.marketplace.operations.economics.*
import io.flooow.marketplace.operations.economics.evidence.*
import io.flooow.organization.OrganizationId
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EconomicTruthAssemblySemanticGapsTest {

    private val subject = MarketplaceEconomicEvidenceSubject(
        organizationId = OrganizationId(UUID.fromString("30000000-0000-0000-0000-000000000001")),
        orderId = MarketplaceOrderId.parse("40000000-0000-0000-0000-000000000001"),
        marketplace = MarketplaceKey("mercado-livre"),
        externalOrderId = MarketplaceExternalOrderId("order-semantic-gaps"),
        currency = MarketplaceCurrency("BRL")
    )

    @Test
    fun `gate 5 observation coverage claims do not encode an order level reducer`() {
        val complete = componentFact(
            observation = 1,
            component = 1,
            family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            type = EconomicComponentType.SHIPPING,
            reference = "shipping-a",
            coverage = EconomicComponentCoverage.COMPLETE
        )

        val partial = componentFact(
            observation = 2,
            component = 2,
            family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            type = EconomicComponentType.SHIPPING,
            reference = "shipping-b",
            coverage = EconomicComponentCoverage.PARTIAL
        )

        var evidence = MarketplaceIndependentEconomicEvidence.empty(subject)
        evidence = applied(evidence, MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(complete))
        evidence = applied(evidence, MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(partial))

        val claims = evidence.activeFacts
            .filterIsInstance<MarketplaceIndependentEconomicFact.Component>()
            .map { it.observation.coverageClaim }
            .toSet()

        assertEquals(
            setOf(EconomicComponentCoverage.COMPLETE, EconomicComponentCoverage.PARTIAL),
            claims
        )
        assertEquals(2, evidence.activeFacts.size)
    }

    @Test
    fun `gate 6 multiple active facts of one economic type coexist when source facts differ`() {
        val first = componentFact(
            observation = 10,
            component = 10,
            family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            type = EconomicComponentType.SHIPPING,
            reference = "shipment-line-a"
        )

        val second = componentFact(
            observation = 11,
            component = 11,
            family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            type = EconomicComponentType.SHIPPING,
            reference = "shipment-line-b"
        )

        var evidence = MarketplaceIndependentEconomicEvidence.empty(subject)
        evidence = applied(evidence, MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(first))
        evidence = applied(evidence, MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(second))

        val shippingFacts = evidence.activeFacts
            .filterIsInstance<MarketplaceIndependentEconomicFact.Component>()
            .filter { it.observation.component.type == EconomicComponentType.SHIPPING }

        assertEquals(2, shippingFacts.size)
    }

    @Test
    fun `gate 7 production evidence contract cannot represent financial cost or other adjustment components`() {
        val representable = mutableSetOf<EconomicComponentType>()

        MarketplaceEconomicEvidenceFamily.entries.forEachIndexed { familyIndex, family ->
            EconomicComponentType.entries.forEachIndexed { typeIndex, type ->
                val id = 100 + familyIndex * 100 + typeIndex
                try {
                    componentFact(
                        observation = id,
                        component = id,
                        family = family,
                        type = type,
                        reference = "probe-$id"
                    )
                    representable += type
                } catch (_: IllegalArgumentException) {
                    // Rejection is the production contract evidence for an incompatible family/type pair.
                }
            }
        }

        assertEquals(
            setOf(
                EconomicComponentType.REVENUE,
                EconomicComponentType.MARKETPLACE_COMMISSION,
                EconomicComponentType.MARKETPLACE_FEE,
                EconomicComponentType.SHIPPING,
                EconomicComponentType.ADVERTISING,
                EconomicComponentType.TAX,
                EconomicComponentType.PRODUCT_COST
            ),
            representable
        )

        assertTrue(EconomicComponentType.FINANCIAL_COST !in representable)
        assertTrue(EconomicComponentType.OTHER_ADJUSTMENT !in representable)
    }

    @Test
    fun `gate 8 no evidence attempt does not manufacture component or coverage`() {
        val before = MarketplaceIndependentEconomicEvidence.empty(subject)

        val after = applied(
            before,
            MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
                attempt(200, MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE)
            )
        )

        assertTrue(after.activeFacts.isEmpty())
        assertTrue(after.facts.isEmpty())
        assertEquals(1, after.attempts.size)
        assertEquals(MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE, after.attempts.single().outcome)
    }

    @Test
    fun `gate 9 ambiguous and temporary failure attempts do not change active economic facts`() {
        val known = componentFact(
            observation = 300,
            component = 300,
            family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            type = EconomicComponentType.SHIPPING,
            reference = "known-shipping"
        )

        var evidence = applied(
            MarketplaceIndependentEconomicEvidence.empty(subject),
            MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(known)
        )

        val activeBefore = evidence.activeFacts

        evidence = applied(
            evidence,
            MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
                attempt(301, MarketplaceEconomicEvidenceAttemptOutcome.AMBIGUOUS)
            )
        )

        evidence = applied(
            evidence,
            MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
                attempt(302, MarketplaceEconomicEvidenceAttemptOutcome.TEMPORARY_FAILURE)
            )
        )

        assertEquals(activeBefore, evidence.activeFacts)
        assertEquals(2, evidence.attempts.size)
    }

    private fun componentFact(
        observation: Int,
        component: Int,
        family: MarketplaceEconomicEvidenceFamily,
        type: EconomicComponentType,
        reference: String,
        coverage: EconomicComponentCoverage = EconomicComponentCoverage.COMPLETE
    ): MarketplaceIndependentEconomicFact.Component {
        val occurredAt = Instant.parse("2026-08-27T10:00:00Z")

        val source = EconomicSource(
            kind = EconomicSourceKind.MARKETPLACE,
            systemKey = EconomicSourceSystemKey("mercado-livre"),
            externalReference = EconomicExternalReferenceState.Present(
                EconomicExternalReference(reference)
            )
        )

        val value = EconomicComponent(
            organizationId = subject.organizationId,
            id = EconomicComponentId(UUID.fromString(uuid(component))),
            orderId = subject.orderId,
            type = type,
            direction = if (type == EconomicComponentType.REVENUE)
                EconomicDirection.ADDITION
            else
                EconomicDirection.DEDUCTION,
            magnitude = MarketplaceMoney.parse(subject.currency, "10.00"),
            source = source,
            occurredAt = occurredAt,
            quality = EconomicEvidenceQuality.CONFIRMED
        )

        return MarketplaceIndependentEconomicFact.Component(
            MarketplaceEconomicComponentObservation(
                id = observationId(observation),
                subject = subject,
                family = family,
                component = value,
                coverageClaim = coverage,
                observedAt = occurredAt.plusSeconds(60)
            )
        )
    }

    private fun attempt(
        id: Int,
        outcome: MarketplaceEconomicEvidenceAttemptOutcome
    ) = MarketplaceEconomicEvidenceCollectionAttempt(
        id = observationId(id),
        subject = subject,
        family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
        sourceSystemKey = EconomicSourceSystemKey("mercado-livre"),
        outcome = outcome,
        attemptedAt = Instant.parse("2026-08-27T11:00:00Z")
    )

    private fun applied(
        current: MarketplaceIndependentEconomicEvidence,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ): MarketplaceIndependentEconomicEvidence {
        val result = MarketplaceIndependentEconomicEvidenceMerger.apply(current, update)
        return assertIs<MarketplaceIndependentEconomicEvidenceResult.Applied>(result).evidence
    }

    private fun observationId(value: Int): MarketplaceEconomicEvidenceObservationId =
        MarketplaceEconomicEvidenceObservationId.parse(uuid(value + 5000))

    private fun uuid(value: Int): String =
        "00000000-0000-0000-0000-${value.toString().padStart(12, '0')}"
}
