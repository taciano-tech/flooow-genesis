package io.flooow.integration.inventory.acceptance

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.mapping.*
import io.flooow.integration.inventory.observation.CanonicalInventoryObservationId
import io.flooow.integration.inventory.observation.CanonicalInventorySourcePointer
import io.flooow.organization.OrganizationId
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class CanonicalInventoryAcceptanceTest {
    @Test
    fun `principal and identifiers are canonical bounded and redacted`() {
        val principal = InventoryAcceptancePrincipalReference.of("operador-é")
        assertEquals("operador-é", principal.encodedForPersistence())
        assertEquals("[REDACTED]", principal.toString())
        assertFailsWith<IllegalArgumentException> {
            InventoryAcceptancePrincipalReference.of(" operador ")
        }
        assertFailsWith<IllegalArgumentException> {
            InventoryAcceptancePrincipalReference.of("x\n")
        }
        assertFailsWith<IllegalArgumentException> {
            InventoryAcceptancePrincipalReference.of("x".repeat(129))
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventoryAcceptanceId.parse("550E8400-E29B-41D4-A716-446655440000")
        }
        assertEquals(
            "[INTERNAL]",
            CanonicalInventoryAcceptanceId.parse("550e8400-e29b-41d4-a716-446655440000")
                .toString()
        )
    }

    @Test
    fun `decision enforces initial replacement and lifecycle shapes without quantities`() {
        val initial = decision(1, CanonicalInventoryAcceptanceReason.INITIAL_ACCEPTANCE)
        assertEquals(CanonicalInventoryAcceptanceState.ACTIVE, initial.state)
        assertEquals("CanonicalInventoryAcceptance([REDACTED])", initial.toString())

        val successor = decision(
            2, CanonicalInventoryAcceptanceReason.NEW_SOURCE_EVIDENCE,
            CanonicalInventoryAcceptanceId.of(uuid(20))
        )
        assertEquals(2, successor.revision)
        assertFailsWith<IllegalArgumentException> {
            decision(1, CanonicalInventoryAcceptanceReason.OPERATOR_CORRECTION)
        }
        assertFailsWith<IllegalArgumentException> {
            decision(2, CanonicalInventoryAcceptanceReason.SOURCE_REVOKED,
                CanonicalInventoryAcceptanceId.of(uuid(20)))
        }
        val exposed = AcceptedCanonicalInventoryObservation::class.java.declaredFields
            .map { it.name }
        assertFalse(exposed.any {
            it.contains("quantity", ignoreCase = true) ||
                it.contains("measure", ignoreCase = true)
        })
    }

    @Test
    fun `service rejects reason categories before repository mutation`() {
        var called = false
        val repository = object : CanonicalInventoryAcceptanceRepository {
            override fun acceptInitial(
                organizationId: OrganizationId,
                lineageRootDecisionId: InventoryMappingDecisionId,
                candidateObservationId: CanonicalInventoryObservationId,
                acceptanceId: CanonicalInventoryAcceptanceId,
                principal: InventoryAcceptancePrincipalReference,
                correlationId: CanonicalInventoryAcceptanceCorrelationId
            ) = CanonicalInventoryAcceptanceResult.IntegrityFailure

            override fun replace(
                organizationId: OrganizationId,
                lineageRootDecisionId: InventoryMappingDecisionId,
                expectedAcceptanceId: CanonicalInventoryAcceptanceId,
                expectedRevision: Int,
                candidateObservationId: CanonicalInventoryObservationId,
                acceptanceId: CanonicalInventoryAcceptanceId,
                principal: InventoryAcceptancePrincipalReference,
                reason: CanonicalInventoryAcceptanceReason,
                correlationId: CanonicalInventoryAcceptanceCorrelationId
            ): CanonicalInventoryAcceptanceResult {
                called = true
                return CanonicalInventoryAcceptanceResult.IntegrityFailure
            }

            override fun withdraw(
                organizationId: OrganizationId,
                lineageRootDecisionId: InventoryMappingDecisionId,
                expectedAcceptanceId: CanonicalInventoryAcceptanceId,
                expectedRevision: Int,
                principal: InventoryAcceptancePrincipalReference,
                reason: CanonicalInventoryAcceptanceReason,
                correlationId: CanonicalInventoryAcceptanceCorrelationId
            ): CanonicalInventoryAcceptanceResult {
                called = true
                return CanonicalInventoryAcceptanceResult.IntegrityFailure
            }

            override fun head(
                organizationId: OrganizationId,
                lineageRootDecisionId: InventoryMappingDecisionId
            ) = null

            override fun history(
                organizationId: OrganizationId,
                lineageRootDecisionId: InventoryMappingDecisionId
            ) = emptyList<CanonicalInventoryAcceptance>()
        }
        val service = CanonicalInventoryAcceptanceService(repository)
        val organization = OrganizationId(uuid(1))
        val root = InventoryMappingDecisionId.of(uuid(2))
        val expected = CanonicalInventoryAcceptanceId.of(uuid(3))
        val observation = CanonicalInventoryObservationId.of(uuid(4))
        val principal = InventoryAcceptancePrincipalReference.of("operator")
        assertFailsWith<IllegalArgumentException> {
            service.replace(
                organization, root, expected, 1, observation, principal,
                CanonicalInventoryAcceptanceReason.SOURCE_REVOKED
            )
        }
        assertFailsWith<IllegalArgumentException> {
            service.withdraw(
                organization, root, expected, 1, principal,
                CanonicalInventoryAcceptanceReason.NEW_SOURCE_EVIDENCE
            )
        }
        assertFalse(called)
    }

    private fun decision(
        revision: Int,
        reason: CanonicalInventoryAcceptanceReason,
        predecessor: CanonicalInventoryAcceptanceId? = null
    ): CanonicalInventoryAcceptance {
        val connection = IntegrationConnectionId(uuid(3))
        return CanonicalInventoryAcceptance(
            CanonicalInventoryAcceptanceId.of(uuid(4 + revision.toLong())),
            OrganizationId(uuid(1)), connection,
            lineageRootDecisionId = InventoryMappingDecisionId.of(uuid(2)),
            revision = revision,
            state = CanonicalInventoryAcceptanceState.ACTIVE,
            acceptedObservation = AcceptedCanonicalInventoryObservation(
                CanonicalInventoryObservationId.of(uuid(10 + revision.toLong())),
                CanonicalInventorySourcePointer(
                    connection, inputProgressVersion = revision.toLong(), recordOrdinal = 0
                ),
                projectionRevision = revision,
                mappingDecisionId = InventoryMappingDecisionId.of(uuid(12 + revision.toLong())),
                mappingRevision = revision,
                target = InventoryMappingTarget(
                    InventoryItemId.of(uuid(30)), null, InventoryUnitId.of(uuid(31)),
                    QuantityFactor.of(1, 1)
                )
            ),
            principalReference = InventoryAcceptancePrincipalReference.of("operator"),
            reason = reason,
            correlationId = CanonicalInventoryAcceptanceCorrelationId.of(uuid(40)),
            acceptedAt = Instant.parse("2026-08-12T20:00:00Z"),
            supersedesAcceptanceId = predecessor
        )
    }

    private fun uuid(value: Long) = UUID(88, value)
}
