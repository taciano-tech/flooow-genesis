package io.flooow.integration.inventory.authority

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.acceptance.CanonicalInventoryAcceptanceId
import io.flooow.integration.inventory.mapping.InventoryItemId
import io.flooow.integration.inventory.mapping.InventoryLocationId
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.mapping.InventoryMappingTarget
import io.flooow.integration.inventory.mapping.InventoryUnitId
import io.flooow.integration.inventory.mapping.QuantityFactor
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

class CanonicalInventorySourceAuthorityTest {
    @Test
    fun `policy version is normalized bounded and redacted`() {
        val composed = CanonicalInventorySourceAuthorityPolicyVersion.of("política-v1")
        val decomposed = CanonicalInventorySourceAuthorityPolicyVersion.of("política-v1")

        assertEquals(composed, decomposed)
        assertEquals("política-v1", decomposed.encodedForPersistence())
        assertEquals("[REDACTED]", composed.toString())
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventorySourceAuthorityPolicyVersion.of("")
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventorySourceAuthorityPolicyVersion.of(" v1")
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventorySourceAuthorityPolicyVersion.of("x\u0000")
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventorySourceAuthorityPolicyVersion.of("á".repeat(33))
        }
    }

    @Test
    fun `policy rejects unsupported capability and empty or inverted intervals`() {
        assertFailsWith<IllegalArgumentException> {
            policy(capability = "inventory.write")
        }
        assertFailsWith<IllegalArgumentException> {
            policy(effectiveUntil = effectiveFrom)
        }
        assertFailsWith<IllegalArgumentException> {
            policy(effectiveUntil = effectiveFrom.minusSeconds(1))
        }
    }

    @Test
    fun `exact scope is authorized at inclusive start and excludes end`() {
        val candidate = candidate()
        val policy = policy()

        val atStart = assertIs<CanonicalInventorySourceAuthorityResult.Authorized>(
            CanonicalInventorySourceAuthorityAssessor.assess(candidate, policy, effectiveFrom)
        )
        assertSame(candidate, atStart.assessment.candidate)
        assertSame(policy, atStart.assessment.policy)
        assertEquals(effectiveFrom, atStart.assessment.evaluatedAt)
        assertEquals(
            CanonicalInventorySourceAuthorityResult.PolicyExpired,
            CanonicalInventorySourceAuthorityAssessor.assess(candidate, policy, effectiveUntil)
        )
    }

    @Test
    fun `scope mismatches fail in exact deterministic order`() {
        val candidate = candidate()
        val allDifferent = policy(
            organizationId = OrganizationId(uuid(201)),
            connectionId = IntegrationConnectionId(uuid(202)),
            target = target(203),
            measure = CanonicalInventoryMeasure.RESERVED,
            effectiveFrom = effectiveFrom.plusSeconds(10)
        )
        assertEquals(
            CanonicalInventorySourceAuthorityResult.OrganizationMismatch,
            CanonicalInventorySourceAuthorityAssessor.assess(candidate, allDifferent, evaluatedAt)
        )
        assertEquals(
            CanonicalInventorySourceAuthorityResult.ConnectionMismatch,
            CanonicalInventorySourceAuthorityAssessor.assess(
                candidate,
                policy(connectionId = IntegrationConnectionId(uuid(202))),
                evaluatedAt
            )
        )
        assertEquals(
            CanonicalInventorySourceAuthorityResult.TargetMismatch,
            CanonicalInventorySourceAuthorityAssessor.assess(
                candidate,
                policy(target = target(203)),
                evaluatedAt
            )
        )
        assertEquals(
            CanonicalInventorySourceAuthorityResult.MeasureMismatch,
            CanonicalInventorySourceAuthorityAssessor.assess(
                candidate,
                policy(measure = CanonicalInventoryMeasure.RESERVED),
                evaluatedAt
            )
        )
    }

    @Test
    fun `not yet effective and expired are distinct`() {
        val candidate = candidate()
        val policy = policy()

        assertEquals(
            CanonicalInventorySourceAuthorityResult.PolicyNotYetEffective,
            CanonicalInventorySourceAuthorityAssessor.assess(
                candidate,
                policy,
                effectiveFrom.minusNanos(1)
            )
        )
        assertEquals(
            CanonicalInventorySourceAuthorityResult.PolicyExpired,
            CanonicalInventorySourceAuthorityAssessor.assess(candidate, policy, effectiveUntil)
        )
    }

    @Test
    fun `signed exact quantity cannot change authority`() {
        listOf(BigInteger.valueOf(-1), BigInteger.ZERO, BigInteger.ONE).forEach { numerator ->
            assertIs<CanonicalInventorySourceAuthorityResult.Authorized>(
                CanonicalInventorySourceAuthorityAssessor.assess(
                    candidate(numerator),
                    policy(),
                    evaluatedAt
                )
            )
        }
    }

    @Test
    fun `assessment construction reproduces all authority invariants`() {
        val policy = policy()
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventorySourceAuthorityAssessment(
                candidate(organizationId = OrganizationId(uuid(301))),
                policy,
                evaluatedAt
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventorySourceAuthorityAssessment(
                candidate(measure = CanonicalInventoryMeasure.RESERVED),
                policy,
                evaluatedAt
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalInventorySourceAuthorityAssessment(candidate(), policy, effectiveUntil)
        }
    }

    @Test
    fun `value equal inputs are deterministic immutable and minimally shaped`() {
        val first = CanonicalInventorySourceAuthorityAssessor.assess(
            candidate(),
            policy(),
            evaluatedAt
        )
        val second = CanonicalInventorySourceAuthorityAssessor.assess(
            candidate(),
            policy(),
            evaluatedAt
        )

        assertEquals(first, second)
        assertEquals(
            setOf("candidate", "policy", "evaluatedAt"),
            CanonicalInventorySourceAuthorityAssessment::class.java.declaredFields
                .map { it.name }
                .filterNot { it.startsWith("\$") }
                .toSet()
        )
    }

    @Test
    fun `all public renderings are redacted`() {
        val authorized = assertIs<CanonicalInventorySourceAuthorityResult.Authorized>(
            CanonicalInventorySourceAuthorityAssessor.assess(candidate(), policy(), evaluatedAt)
        )
        val renderings = listOf(
            policy().toString(),
            authorized.assessment.toString(),
            authorized.toString(),
            CanonicalInventorySourceAuthorityResult.OrganizationMismatch.toString(),
            CanonicalInventorySourceAuthorityResult.ConnectionMismatch.toString(),
            CanonicalInventorySourceAuthorityResult.TargetMismatch.toString(),
            CanonicalInventorySourceAuthorityResult.MeasureMismatch.toString(),
            CanonicalInventorySourceAuthorityResult.PolicyNotYetEffective.toString(),
            CanonicalInventorySourceAuthorityResult.PolicyExpired.toString(),
            CanonicalInventorySourceAuthorityAssessor.toString()
        )
        assertEquals(setOf("[REDACTED]"), renderings.toSet())
    }

    @Test
    fun `production boundary references neither kernel nor marketplace`() {
        val classes = listOf(
            CanonicalInventorySourceAuthorityPolicyVersion::class.java,
            CanonicalInventorySourceAuthorityPolicy::class.java,
            CanonicalInventorySourceAuthorityAssessment::class.java,
            CanonicalInventorySourceAuthorityResult::class.java,
            CanonicalInventorySourceAuthorityAssessor::class.java
        )
        classes.forEach { type ->
            val resource = "/${type.name.replace('.', '/')}.class"
            val bytes = requireNotNull(type.getResourceAsStream(resource)).readBytes()
            val bytecode = bytes.toString(Charsets.ISO_8859_1)
            assertFalse(bytecode.contains("io/flooow/kernel"))
            assertFalse(bytecode.contains("io/flooow/marketplace"))
        }
    }

    private fun policy(
        organizationId: OrganizationId = this.organizationId,
        connectionId: IntegrationConnectionId = this.connectionId,
        capability: String = "inventory.source-balance.read",
        target: InventoryMappingTarget = this.target,
        measure: CanonicalInventoryMeasure = CanonicalInventoryMeasure.ON_HAND,
        effectiveFrom: Instant = this.effectiveFrom,
        effectiveUntil: Instant = this.effectiveUntil
    ) = CanonicalInventorySourceAuthorityPolicy(
        CanonicalInventorySourceAuthorityPolicyVersion.of("authority-v1"),
        organizationId,
        connectionId,
        capability,
        target,
        measure,
        effectiveFrom,
        effectiveUntil
    )

    private fun candidate(
        numerator: BigInteger = BigInteger.ONE,
        organizationId: OrganizationId = this.organizationId,
        connectionId: IntegrationConnectionId = this.connectionId,
        target: InventoryMappingTarget = this.target,
        measure: CanonicalInventoryMeasure = CanonicalInventoryMeasure.ON_HAND
    ) = SelectedCanonicalInventoryMeasure(
        organizationId,
        connectionId,
        "inventory.source-balance.read",
        InventoryMappingDecisionId.of(uuid(10)),
        CanonicalInventoryMeasureSelectionId.of(uuid(11)),
        1,
        CanonicalInventoryAcceptanceId.of(uuid(12)),
        1,
        CanonicalInventoryObservationId.of(uuid(13)),
        CanonicalInventorySourcePointer(connectionId, inputProgressVersion = 1, recordOrdinal = 0),
        1,
        InventoryMappingDecisionId.of(uuid(14)),
        1,
        target,
        measure,
        ExactInventoryQuantity.fromPersistence(numerator, 1)
    )

    private fun target(seed: Long) = InventoryMappingTarget(
        InventoryItemId.of(uuid(seed)),
        InventoryLocationId.of(uuid(seed + 1)),
        InventoryUnitId.of(uuid(seed + 2)),
        QuantityFactor.of(1, 1)
    )

    private val organizationId = OrganizationId(uuid(1))
    private val connectionId = IntegrationConnectionId(uuid(2))
    private val target = target(3)
    private val effectiveFrom = Instant.parse("2026-08-20T10:00:00Z")
    private val effectiveUntil = Instant.parse("2026-08-21T10:00:00Z")
    private val evaluatedAt = Instant.parse("2026-08-20T12:00:00Z")

    private fun uuid(value: Long) = UUID(0, value)
}
