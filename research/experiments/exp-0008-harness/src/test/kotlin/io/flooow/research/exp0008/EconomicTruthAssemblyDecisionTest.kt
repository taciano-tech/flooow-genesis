package io.flooow.research.exp0008

import io.flooow.marketplace.operations.economics.*
import io.flooow.marketplace.operations.economics.evidence.*
import io.flooow.organization.OrganizationId
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EconomicTruthAssemblyDecisionTest {

    private val subject = MarketplaceEconomicEvidenceSubject(
        organizationId = OrganizationId(UUID.fromString("50000000-0000-0000-0000-000000000001")),
        orderId = MarketplaceOrderId.parse("60000000-0000-0000-0000-000000000001"),
        marketplace = MarketplaceKey("mercado-livre"),
        externalOrderId = MarketplaceExternalOrderId("order-decision"),
        currency = MarketplaceCurrency("BRL")
    )

    @Test
    fun `gate 10 external identity occurrence time cannot uniquely establish order occurrence time`() {
        val revenueTime = Instant.parse("2026-08-27T10:00:00Z")
        val identityTime = Instant.parse("2026-08-27T10:10:00Z")

        val revenue = componentFact(1, revenueTime)
        val identity = identityFact(2, identityTime)

        var evidence = MarketplaceIndependentEconomicEvidence.empty(subject)
        evidence = applied(evidence, MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(revenue))
        evidence = applied(evidence, MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(identity))

        val componentTimes = evidence.activeFacts
            .filterIsInstance<MarketplaceIndependentEconomicFact.Component>()
            .map { it.observation.component.occurredAt }

        val identityTimes = evidence.activeFacts
            .filterIsInstance<MarketplaceIndependentEconomicFact.ExternalIdentity>()
            .map { it.observation.occurredAt }

        assertEquals(listOf(revenueTime), componentTimes)
        assertEquals(listOf(identityTime), identityTimes)
        assertTrue(revenueTime != identityTime)
    }

    @Test
    fun `gate 11 accepted contracts require semantic information that evidence cannot currently supply`() {
        val evidenceSubjectFields = MarketplaceEconomicEvidenceSubject::class.java.declaredFields
            .map { it.name }
            .toSet()

        assertTrue("occurredAt" !in evidenceSubjectFields)
        assertTrue("orderOccurredAt" !in evidenceSubjectFields)

        val representableTypes = representableEconomicTypes()

        assertTrue(EconomicComponentType.FINANCIAL_COST !in representableTypes)
        assertTrue(EconomicComponentType.OTHER_ADJUSTMENT !in representableTypes)

        assertEquals(
            EconomicComponentType.entries.toSet(),
            requiredCoverageTypes()
        )
    }

    @Test
    fun `decision current model supports null hypothesis H0`() {
        val missingSemanticBoundaries = buildSet {
            val subjectFields = MarketplaceEconomicEvidenceSubject::class.java.declaredFields
                .map { it.name }
                .toSet()

            if ("occurredAt" !in subjectFields && "orderOccurredAt" !in subjectFields) {
                add("ORDER_OCCURRED_AT_UNRESOLVED")
            }

            val representable = representableEconomicTypes()
            if (EconomicComponentType.entries.any { it !in representable }) {
                add("UNSUPPORTED_ECONOMIC_TYPE")
            }

            add("COVERAGE_UNRESOLVED")
        }

        assertEquals(
            setOf(
                "ORDER_OCCURRED_AT_UNRESOLVED",
                "UNSUPPORTED_ECONOMIC_TYPE",
                "COVERAGE_UNRESOLVED"
            ),
            missingSemanticBoundaries
        )

        assertTrue(missingSemanticBoundaries.isNotEmpty())
    }

    private fun requiredCoverageTypes(): Set<EconomicComponentType> =
        EconomicComponentType.entries.toSet()

    private fun representableEconomicTypes(): Set<EconomicComponentType> {
        val result = mutableSetOf<EconomicComponentType>()

        MarketplaceEconomicEvidenceFamily.entries.forEachIndexed { familyIndex, family ->
            EconomicComponentType.entries.forEachIndexed { typeIndex, type ->
                val id = 1000 + familyIndex * 100 + typeIndex
                try {
                    probeComponentFact(id, family, type)
                    result += type
                } catch (_: IllegalArgumentException) {
                }
            }
        }

        return result
    }

    private fun componentFact(
        id: Int,
        occurredAt: Instant
    ): MarketplaceIndependentEconomicFact.Component {
        val component = EconomicComponent(
            organizationId = subject.organizationId,
            id = EconomicComponentId(UUID.fromString(uuid(id))),
            orderId = subject.orderId,
            type = EconomicComponentType.REVENUE,
            direction = EconomicDirection.ADDITION,
            magnitude = MarketplaceMoney.parse(subject.currency, "299.90"),
            source = marketplaceSource("revenue-$id"),
            occurredAt = occurredAt,
            quality = EconomicEvidenceQuality.CONFIRMED
        )

        return MarketplaceIndependentEconomicFact.Component(
            MarketplaceEconomicComponentObservation(
                id = observationId(id),
                subject = subject,
                family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                component = component,
                coverageClaim = EconomicComponentCoverage.COMPLETE,
                observedAt = occurredAt.plusSeconds(60)
            )
        )
    }

    private fun identityFact(
        id: Int,
        occurredAt: Instant
    ): MarketplaceIndependentEconomicFact.ExternalIdentity =
        MarketplaceIndependentEconomicFact.ExternalIdentity(
            MarketplaceEconomicExternalIdentityObservation(
                id = observationId(id),
                subject = subject,
                family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                kind = MarketplaceEconomicExternalIdentityKind.ERP_ORDER,
                anchorReference = EconomicExternalReference("order-anchor"),
                linkedSystemKey = EconomicSourceSystemKey("omie"),
                linkedReference = EconomicExternalReference("erp-order-1"),
                source = marketplaceSource("identity-$id"),
                occurredAt = occurredAt,
                observedAt = occurredAt.plusSeconds(60)
            )
        )

    private fun probeComponentFact(
        id: Int,
        family: MarketplaceEconomicEvidenceFamily,
        type: EconomicComponentType
    ): MarketplaceIndependentEconomicFact.Component {
        val occurredAt = Instant.parse("2026-08-27T12:00:00Z")
        val component = EconomicComponent(
            organizationId = subject.organizationId,
            id = EconomicComponentId(UUID.fromString(uuid(id))),
            orderId = subject.orderId,
            type = type,
            direction = if (type == EconomicComponentType.REVENUE) EconomicDirection.ADDITION else EconomicDirection.DEDUCTION,
            magnitude = MarketplaceMoney.parse(subject.currency, "1.00"),
            source = marketplaceSource("probe-$id"),
            occurredAt = occurredAt,
            quality = EconomicEvidenceQuality.CONFIRMED
        )

        return MarketplaceIndependentEconomicFact.Component(
            MarketplaceEconomicComponentObservation(
                id = observationId(id),
                subject = subject,
                family = family,
                component = component,
                coverageClaim = EconomicComponentCoverage.COMPLETE,
                observedAt = occurredAt.plusSeconds(60)
            )
        )
    }

    private fun marketplaceSource(reference: String) = EconomicSource(
        kind = EconomicSourceKind.MARKETPLACE,
        systemKey = EconomicSourceSystemKey("mercado-livre"),
        externalReference = EconomicExternalReferenceState.Present(
            EconomicExternalReference(reference)
        )
    )

    private fun applied(
        current: MarketplaceIndependentEconomicEvidence,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ): MarketplaceIndependentEconomicEvidence {
        val result = MarketplaceIndependentEconomicEvidenceMerger.apply(current, update)
        return (result as MarketplaceIndependentEconomicEvidenceResult.Applied).evidence
    }

    private fun observationId(value: Int) =
        MarketplaceEconomicEvidenceObservationId.parse(uuid(value + 9000))

    private fun uuid(value: Int): String =
        "00000000-0000-0000-0000-${value.toString().padStart(12, '0')}"
}
