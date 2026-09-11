package io.flooow.integration.inventory.authority

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.acceptance.CanonicalInventoryAcceptanceId
import io.flooow.integration.inventory.mapping.InventoryItemId
import io.flooow.integration.inventory.mapping.InventoryLocationId
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.mapping.InventoryMappingTarget
import io.flooow.integration.inventory.mapping.InventoryUnitId
import io.flooow.integration.inventory.mapping.QuantityFactor
import io.flooow.integration.inventory.observation.CanonicalInventoryMeasures
import io.flooow.integration.inventory.observation.CanonicalInventoryObservation
import io.flooow.integration.inventory.observation.CanonicalInventoryObservationCorrelationId
import io.flooow.integration.inventory.observation.CanonicalInventoryObservationId
import io.flooow.integration.inventory.observation.CanonicalInventorySourcePointer
import io.flooow.integration.inventory.observation.ExactInventoryQuantity
import io.flooow.integration.inventory.selection.CanonicalInventoryMeasure
import io.flooow.integration.inventory.selection.CanonicalInventoryMeasureSelectionId
import io.flooow.integration.inventory.selection.SelectedCanonicalInventoryMeasure
import io.flooow.organization.OrganizationId
import java.math.BigInteger
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class AuthorizedCanonicalInventoryObservationEvidenceTest {
    @Test
    fun `exact evidence links and retains the same instances`() {
        val candidate = candidate()
        val authority = authority(candidate)
        val observation = observation(candidate)

        val linked = assertIs<AuthorizedCanonicalInventoryObservationResult.Linked>(
            AuthorizedCanonicalInventoryObservationLinker.link(authority, observation)
        )

        assertSame(authority, linked.evidence.authority)
        assertSame(observation, linked.evidence.observation)
    }

    @Test
    fun `identity and lineage mismatches fail in deterministic order`() {
        val candidate = candidate()
        val authority = authority(candidate)
        val allDifferent = observation(
            candidate,
            organizationId = OrganizationId(uuid(201)),
            observationId = CanonicalInventoryObservationId.of(uuid(202)),
            sourcePointer = CanonicalInventorySourcePointer(
                IntegrationConnectionId(uuid(203)),
                inputProgressVersion = 99,
                recordOrdinal = 1
            ),
            projectionRevision = 2,
            mappingDecisionId = InventoryMappingDecisionId.of(uuid(204)),
            mappingRevision = 2,
            target = target(205)
        )
        assertEquals(
            AuthorizedCanonicalInventoryObservationResult.OrganizationMismatch,
            AuthorizedCanonicalInventoryObservationLinker.link(authority, allDifferent)
        )
        assertEquals(
            AuthorizedCanonicalInventoryObservationResult.ObservationIdentityMismatch,
            AuthorizedCanonicalInventoryObservationLinker.link(
                authority,
                observation(candidate, observationId = CanonicalInventoryObservationId.of(uuid(202)))
            )
        )
        assertEquals(
            AuthorizedCanonicalInventoryObservationResult.SourcePointerMismatch,
            AuthorizedCanonicalInventoryObservationLinker.link(
                authority,
                observation(
                    candidate,
                    sourcePointer = CanonicalInventorySourcePointer(
                        candidate.connectionId,
                        inputProgressVersion = 2,
                        recordOrdinal = 0
                    )
                )
            )
        )
        assertEquals(
            AuthorizedCanonicalInventoryObservationResult.ProjectionRevisionMismatch,
            AuthorizedCanonicalInventoryObservationLinker.link(
                authority,
                observation(candidate, projectionRevision = 2)
            )
        )
        assertEquals(
            AuthorizedCanonicalInventoryObservationResult.MappingLineageMismatch,
            AuthorizedCanonicalInventoryObservationLinker.link(
                authority,
                observation(candidate, mappingRevision = 2)
            )
        )
        assertEquals(
            AuthorizedCanonicalInventoryObservationResult.TargetMismatch,
            AuthorizedCanonicalInventoryObservationLinker.link(
                authority,
                observation(candidate, target = target(205))
            )
        )
    }

    @Test
    fun `all five selected measures link through exhaustive extraction`() {
        CanonicalInventoryMeasure.entries.forEach { measure ->
            val candidate = candidate(measure = measure)
            assertIs<AuthorizedCanonicalInventoryObservationResult.Linked>(
                AuthorizedCanonicalInventoryObservationLinker.link(
                    authority(candidate),
                    observation(candidate)
                )
            )
        }
    }

    @Test
    fun `unavailable selected measure fails without fallback`() {
        val candidate = candidate(measure = CanonicalInventoryMeasure.ON_HAND)
        val onlyReserved = CanonicalInventoryMeasures(reserved = quantity(1))

        assertEquals(
            AuthorizedCanonicalInventoryObservationResult.SelectedMeasureUnavailable,
            AuthorizedCanonicalInventoryObservationLinker.link(
                authority(candidate),
                observation(candidate, measures = onlyReserved)
            )
        )
    }

    @Test
    fun `selected quantities compare as exact signed rationals`() {
        listOf(
            quantity(-5, 6) to quantity(-10, 12),
            quantity(0) to quantity(0, 99),
            quantity(5, 6) to quantity(10, 12)
        ).forEach { (candidateQuantity, observationQuantity) ->
            val candidate = candidate(exactQuantity = candidateQuantity)
            assertIs<AuthorizedCanonicalInventoryObservationResult.Linked>(
                AuthorizedCanonicalInventoryObservationLinker.link(
                    authority(candidate),
                    observation(
                        candidate,
                        measures = selectedMeasures(candidate.measure, observationQuantity)
                    )
                )
            )
        }

        val candidate = candidate(exactQuantity = quantity(5, 6))
        assertEquals(
            AuthorizedCanonicalInventoryObservationResult.SelectedQuantityMismatch,
            AuthorizedCanonicalInventoryObservationLinker.link(
                authority(candidate),
                observation(candidate, measures = selectedMeasures(candidate.measure, quantity(4, 6)))
            )
        )
    }

    @Test
    fun `changing an unselected measure cannot change linking`() {
        val candidate = candidate(measure = CanonicalInventoryMeasure.ON_HAND)
        val first = observation(
            candidate,
            measures = CanonicalInventoryMeasures(onHand = candidate.exactQuantity, reserved = quantity(1))
        )
        val second = first.copy(
            measures = CanonicalInventoryMeasures(onHand = candidate.exactQuantity, reserved = quantity(999))
        )

        assertIs<AuthorizedCanonicalInventoryObservationResult.Linked>(
            AuthorizedCanonicalInventoryObservationLinker.link(authority(candidate), first)
        )
        assertIs<AuthorizedCanonicalInventoryObservationResult.Linked>(
            AuthorizedCanonicalInventoryObservationLinker.link(authority(candidate), second)
        )
    }

    @Test
    fun `internal construction reproduces exact evidence invariants`() {
        val candidate = candidate()
        val authority = authority(candidate)

        assertFailsWith<IllegalArgumentException> {
            AuthorizedCanonicalInventoryObservationEvidence(
                authority,
                observation(candidate, mappingRevision = 2)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AuthorizedCanonicalInventoryObservationEvidence(
                authority,
                observation(candidate, measures = CanonicalInventoryMeasures(reserved = quantity(1)))
            )
        }
    }

    @Test
    fun `value equal inputs are deterministic immutable and minimally shaped`() {
        val firstCandidate = candidate()
        val secondCandidate = candidate()
        val first = AuthorizedCanonicalInventoryObservationLinker.link(
            authority(firstCandidate), observation(firstCandidate)
        )
        val second = AuthorizedCanonicalInventoryObservationLinker.link(
            authority(secondCandidate), observation(secondCandidate)
        )

        assertEquals(first, second)
        assertEquals(
            setOf("authority", "observation"),
            AuthorizedCanonicalInventoryObservationEvidence::class.java.declaredFields
                .map { it.name }
                .filterNot { it.startsWith("\$") }
                .toSet()
        )
    }

    @Test
    fun `all new public renderings are redacted`() {
        val candidate = candidate()
        val linked = assertIs<AuthorizedCanonicalInventoryObservationResult.Linked>(
            AuthorizedCanonicalInventoryObservationLinker.link(
                authority(candidate), observation(candidate)
            )
        )
        val renderings = listOf(
            linked.evidence.toString(), linked.toString(),
            AuthorizedCanonicalInventoryObservationResult.OrganizationMismatch.toString(),
            AuthorizedCanonicalInventoryObservationResult.ObservationIdentityMismatch.toString(),
            AuthorizedCanonicalInventoryObservationResult.SourcePointerMismatch.toString(),
            AuthorizedCanonicalInventoryObservationResult.ProjectionRevisionMismatch.toString(),
            AuthorizedCanonicalInventoryObservationResult.MappingLineageMismatch.toString(),
            AuthorizedCanonicalInventoryObservationResult.TargetMismatch.toString(),
            AuthorizedCanonicalInventoryObservationResult.SelectedMeasureUnavailable.toString(),
            AuthorizedCanonicalInventoryObservationResult.SelectedQuantityMismatch.toString(),
            AuthorizedCanonicalInventoryObservationLinker.toString()
        )
        assertEquals(setOf("[REDACTED]"), renderings.toSet())
    }

    @Test
    fun `production boundary references neither kernel nor marketplace`() {
        listOf(
            AuthorizedCanonicalInventoryObservationEvidence::class.java,
            AuthorizedCanonicalInventoryObservationResult::class.java,
            AuthorizedCanonicalInventoryObservationLinker::class.java
        ).forEach { type ->
            val resource = "/${type.name.replace('.', '/')}.class"
            val bytecode = requireNotNull(type.getResourceAsStream(resource))
                .readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(bytecode.contains("io/flooow/kernel"))
            assertFalse(bytecode.contains("io/flooow/marketplace"))
        }
    }

    private fun authority(candidate: SelectedCanonicalInventoryMeasure):
        CanonicalInventorySourceAuthorityAssessment {
        val policy = CanonicalInventorySourceAuthorityPolicy(
            CanonicalInventorySourceAuthorityPolicyVersion.of("authority-v1"),
            candidate.organizationId,
            candidate.connectionId,
            candidate.capability,
            candidate.target,
            candidate.measure,
            Instant.parse("2026-08-20T10:00:00Z"),
            Instant.parse("2026-08-21T10:00:00Z")
        )
        return assertIs<CanonicalInventorySourceAuthorityResult.Authorized>(
            CanonicalInventorySourceAuthorityAssessor.assess(
                candidate, policy, Instant.parse("2026-08-20T12:00:00Z")
            )
        ).assessment
    }

    private fun candidate(
        measure: CanonicalInventoryMeasure = CanonicalInventoryMeasure.ON_HAND,
        exactQuantity: ExactInventoryQuantity = quantity(-5, 6)
    ) = SelectedCanonicalInventoryMeasure(
        organizationId, connectionId, "inventory.source-balance.read",
        InventoryMappingDecisionId.of(uuid(10)),
        CanonicalInventoryMeasureSelectionId.of(uuid(11)), 1,
        CanonicalInventoryAcceptanceId.of(uuid(12)), 1,
        observationId, sourcePointer, 1,
        mappingDecisionId, 1, target, measure, exactQuantity
    )

    private fun observation(
        candidate: SelectedCanonicalInventoryMeasure,
        organizationId: OrganizationId = candidate.organizationId,
        observationId: CanonicalInventoryObservationId = candidate.observationId,
        sourcePointer: CanonicalInventorySourcePointer = candidate.sourcePointer,
        projectionRevision: Int = candidate.projectionRevision,
        mappingDecisionId: InventoryMappingDecisionId = candidate.mappingDecisionId,
        mappingRevision: Int = candidate.mappingRevision,
        target: InventoryMappingTarget = candidate.target,
        measures: CanonicalInventoryMeasures = selectedMeasures(
            candidate.measure, candidate.exactQuantity
        )
    ) = CanonicalInventoryObservation(
        observationId, organizationId, sourcePointer, projectionRevision,
        mappingDecisionId, mappingRevision, target, measures,
        Instant.parse("2026-08-20T11:57:00Z"),
        Instant.parse("2026-08-20T11:58:00Z"),
        Instant.parse("2026-08-20T11:59:00Z"),
        CanonicalInventoryObservationCorrelationId.of(uuid(20)),
        if (projectionRevision > 1) CanonicalInventoryObservationId.of(uuid(21)) else null
    )

    private fun selectedMeasures(
        measure: CanonicalInventoryMeasure,
        quantity: ExactInventoryQuantity
    ) = when (measure) {
        CanonicalInventoryMeasure.AVAILABLE_TO_SELL ->
            CanonicalInventoryMeasures(availableToSell = quantity)
        CanonicalInventoryMeasure.ON_HAND -> CanonicalInventoryMeasures(onHand = quantity)
        CanonicalInventoryMeasure.RESERVED -> CanonicalInventoryMeasures(reserved = quantity)
        CanonicalInventoryMeasure.PENDING_INBOUND ->
            CanonicalInventoryMeasures(pendingInbound = quantity)
        CanonicalInventoryMeasure.PENDING_OUTBOUND ->
            CanonicalInventoryMeasures(pendingOutbound = quantity)
    }

    private fun quantity(numerator: Long, denominator: Long = 1) =
        ExactInventoryQuantity.fromPersistence(BigInteger.valueOf(numerator), denominator)

    private fun target(seed: Long) = InventoryMappingTarget(
        InventoryItemId.of(uuid(seed)), InventoryLocationId.of(uuid(seed + 1)),
        InventoryUnitId.of(uuid(seed + 2)), QuantityFactor.of(1, 1)
    )

    private val organizationId = OrganizationId(uuid(1))
    private val connectionId = IntegrationConnectionId(uuid(2))
    private val target = target(3)
    private val observationId = CanonicalInventoryObservationId.of(uuid(6))
    private val sourcePointer = CanonicalInventorySourcePointer(
        connectionId,
        inputProgressVersion = 1,
        recordOrdinal = 0
    )
    private val mappingDecisionId = InventoryMappingDecisionId.of(uuid(7))

    private fun uuid(value: Long) = UUID(0, value)
}
