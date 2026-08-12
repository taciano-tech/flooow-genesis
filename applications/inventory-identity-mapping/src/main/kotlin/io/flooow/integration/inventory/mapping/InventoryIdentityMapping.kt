package io.flooow.integration.inventory.mapping

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.source.InventorySourceBalanceCapability
import io.flooow.integration.inventory.source.SourceItemReference
import io.flooow.integration.inventory.source.SourceLocationReference
import io.flooow.integration.inventory.source.SourceUnitCode
import io.flooow.organization.OrganizationId
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

sealed class InternalUuid protected constructor(private val value: UUID) {
    fun valueForPersistence(): UUID = value

    protected fun sameValue(other: InternalUuid): Boolean = value == other.value
    protected fun valueHash(): Int = value.hashCode()
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun canonical(value: String): UUID {
            val parsed = UUID.fromString(value)
            require(parsed.toString() == value) { "Invalid inventory identifier" }
            return parsed
        }
    }
}

class InventoryItemId private constructor(value: UUID) : InternalUuid(value) {
    override fun equals(other: Any?) = other is InventoryItemId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = InventoryItemId(value)
        fun parse(value: String) = InventoryItemId(canonical(value))
    }
}

class InventoryLocationId private constructor(value: UUID) : InternalUuid(value) {
    override fun equals(other: Any?) = other is InventoryLocationId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = InventoryLocationId(value)
        fun parse(value: String) = InventoryLocationId(canonical(value))
    }
}

class InventoryUnitId private constructor(value: UUID) : InternalUuid(value) {
    override fun equals(other: Any?) = other is InventoryUnitId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = InventoryUnitId(value)
        fun parse(value: String) = InventoryUnitId(canonical(value))
    }
}

class InventoryMappingDecisionId private constructor(value: UUID) : InternalUuid(value) {
    override fun equals(other: Any?) = other is InventoryMappingDecisionId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = InventoryMappingDecisionId(value)
        fun parse(value: String) = InventoryMappingDecisionId(canonical(value))
    }
}

class InventoryMappingCorrelationId private constructor(value: UUID) : InternalUuid(value) {
    override fun equals(other: Any?) = other is InventoryMappingCorrelationId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = InventoryMappingCorrelationId(value)
        fun parse(value: String) = InventoryMappingCorrelationId(canonical(value))
    }
}

enum class InventoryIdentityState { ACTIVE, RETIRED }

data class InventoryItemIdentity(
    val organizationId: OrganizationId,
    val id: InventoryItemId,
    val state: InventoryIdentityState,
    val createdAt: Instant,
    val retiredAt: Instant?
) {
    init { requireLifecycle(state, createdAt, retiredAt) }
    override fun toString() = "InventoryItemIdentity([INTERNAL])"
}

data class InventoryLocationIdentity(
    val organizationId: OrganizationId,
    val id: InventoryLocationId,
    val state: InventoryIdentityState,
    val createdAt: Instant,
    val retiredAt: Instant?
) {
    init { requireLifecycle(state, createdAt, retiredAt) }
    override fun toString() = "InventoryLocationIdentity([INTERNAL])"
}

data class InventoryUnitIdentity(
    val organizationId: OrganizationId,
    val id: InventoryUnitId,
    val state: InventoryIdentityState,
    val createdAt: Instant,
    val retiredAt: Instant?
) {
    init { requireLifecycle(state, createdAt, retiredAt) }
    override fun toString() = "InventoryUnitIdentity([INTERNAL])"
}

private fun requireLifecycle(
    state: InventoryIdentityState,
    createdAt: Instant,
    retiredAt: Instant?
) {
    require(
        (state == InventoryIdentityState.ACTIVE && retiredAt == null) ||
            (state == InventoryIdentityState.RETIRED && retiredAt != null && retiredAt >= createdAt)
    ) { "Invalid inventory identity lifecycle" }
}

class InventoryMappingPrincipalReference private constructor(private val value: String) {
    fun encodedForPersistence(): String = value
    override fun equals(other: Any?) =
        other is InventoryMappingPrincipalReference && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = "[REDACTED]"

    companion object {
        fun of(value: String): InventoryMappingPrincipalReference {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            require(normalized.isNotEmpty() && normalized == normalized.trim()) {
                "Invalid mapping principal"
            }
            require(normalized.none(Char::isISOControl)) { "Invalid mapping principal" }
            require(normalized.toByteArray(Charsets.UTF_8).size <= 128) {
                "Invalid mapping principal"
            }
            return InventoryMappingPrincipalReference(normalized)
        }
    }
}

@ConsistentCopyVisibility
data class QuantityFactor private constructor(
    val numerator: Long,
    val denominator: Long
) {
    override fun toString() = "[INTERNAL]"

    companion object {
        const val MAX_COMPONENT = 1_000_000_000L

        fun of(numerator: Long, denominator: Long): QuantityFactor {
            require(numerator in 1..MAX_COMPONENT && denominator in 1..MAX_COMPONENT) {
                "Invalid quantity factor"
            }
            val divisor = gcd(numerator, denominator)
            return QuantityFactor(numerator / divisor, denominator / divisor)
        }

        private tailrec fun gcd(left: Long, right: Long): Long =
            if (right == 0L) left else gcd(right, left % right)
    }
}

data class InventorySourceSelector(
    val connectionId: IntegrationConnectionId,
    val capability: String = InventorySourceBalanceCapability.VALUE,
    val sourceItemReference: SourceItemReference,
    val sourceLocationReference: SourceLocationReference? = null,
    val sourceUnitCode: SourceUnitCode? = null
) {
    init {
        require(capability == InventorySourceBalanceCapability.VALUE) {
            "Inventory mapping capability unavailable"
        }
    }

    override fun toString() = "InventorySourceSelector([REDACTED])"
}

data class InventoryMappingTarget(
    val itemId: InventoryItemId,
    val locationId: InventoryLocationId?,
    val unitId: InventoryUnitId,
    val quantityFactor: QuantityFactor
) {
    override fun toString() = "InventoryMappingTarget([INTERNAL])"
}

data class InventoryMappingEvidence(
    val connectionId: IntegrationConnectionId,
    val capability: String = InventorySourceBalanceCapability.VALUE,
    val inputProgressVersion: Long,
    val recordOrdinal: Int
) {
    init {
        require(capability == InventorySourceBalanceCapability.VALUE) {
            "Inventory mapping capability unavailable"
        }
        require(inputProgressVersion >= 0) { "Invalid mapping evidence version" }
        require(recordOrdinal in 0..999) { "Invalid mapping evidence ordinal" }
    }

    override fun toString() = "InventoryMappingEvidence([INTERNAL])"
}

enum class InventoryMappingReason {
    INITIAL_ASSIGNMENT,
    IDENTITY_CORRECTION,
    LOCATION_CORRECTION,
    UNIT_CORRECTION,
    CATALOG_REPLACEMENT,
    SOURCE_MODEL_CHANGE
}

enum class InventoryMappingState { ACTIVE, RETIRED }

data class InventoryMappingDecision(
    val id: InventoryMappingDecisionId,
    val organizationId: OrganizationId,
    val selector: InventorySourceSelector,
    val target: InventoryMappingTarget,
    val evidence: InventoryMappingEvidence,
    val revision: Int,
    val state: InventoryMappingState,
    val principalReference: InventoryMappingPrincipalReference,
    val reason: InventoryMappingReason,
    val correlationId: InventoryMappingCorrelationId,
    val decidedAt: Instant,
    val retiredAt: Instant? = null,
    val supersedesDecisionId: InventoryMappingDecisionId? = null
) {
    init {
        require(revision > 0) { "Invalid mapping revision" }
        require(selector.connectionId == evidence.connectionId &&
            selector.capability == evidence.capability) { "Invalid mapping evidence scope" }
        require((selector.sourceLocationReference == null) == (target.locationId == null)) {
            "Invalid mapping location shape"
        }
        require(
            (revision == 1 && supersedesDecisionId == null &&
                reason == InventoryMappingReason.INITIAL_ASSIGNMENT) ||
                (revision > 1 && supersedesDecisionId != null &&
                    reason != InventoryMappingReason.INITIAL_ASSIGNMENT)
        ) { "Invalid mapping revision shape" }
        require(
            (state == InventoryMappingState.ACTIVE && retiredAt == null) ||
                (state == InventoryMappingState.RETIRED && retiredAt != null &&
                    retiredAt >= decidedAt)
        ) { "Invalid mapping lifecycle" }
    }

    override fun toString() = "InventoryMappingDecision([REDACTED])"
}

enum class IdentityWriteResult { APPLIED, ALREADY_APPLIED, UNAVAILABLE, STALE }

enum class MappingWriteResult {
    APPLIED,
    ALREADY_APPLIED,
    CONFLICT,
    UNAVAILABLE,
    EVIDENCE_MISMATCH,
    TARGET_UNAVAILABLE,
    INTEGRITY_FAILURE
}

sealed interface InventoryMappingResolution {
    class Resolved(
        val target: InventoryMappingTarget,
        val decisionId: InventoryMappingDecisionId,
        val revision: Int
    ) : InventoryMappingResolution {
        override fun toString() = "InventoryMappingResolution.Resolved([INTERNAL])"
    }

    data object Unmapped : InventoryMappingResolution
    data object IntegrityFailure : InventoryMappingResolution
}

fun interface MappingIdentifierFactory<T> { fun create(): T }

interface InventoryIdentityMappingRepository {
    fun createItem(identity: InventoryItemIdentity): IdentityWriteResult
    fun createLocation(identity: InventoryLocationIdentity): IdentityWriteResult
    fun createUnit(identity: InventoryUnitIdentity): IdentityWriteResult

    fun retireItem(
        organizationId: OrganizationId,
        id: InventoryItemId,
        retiredAt: Instant
    ): IdentityWriteResult

    fun retireLocation(
        organizationId: OrganizationId,
        id: InventoryLocationId,
        retiredAt: Instant
    ): IdentityWriteResult

    fun retireUnit(
        organizationId: OrganizationId,
        id: InventoryUnitId,
        retiredAt: Instant
    ): IdentityWriteResult

    fun activateInitial(decision: InventoryMappingDecision): MappingWriteResult

    fun replace(
        expectedDecisionId: InventoryMappingDecisionId,
        expectedRevision: Int,
        decision: InventoryMappingDecision
    ): MappingWriteResult

    fun retireMapping(
        organizationId: OrganizationId,
        selector: InventorySourceSelector,
        expectedDecisionId: InventoryMappingDecisionId,
        expectedRevision: Int,
        principalReference: InventoryMappingPrincipalReference,
        reason: InventoryMappingReason,
        correlationId: InventoryMappingCorrelationId,
        retiredAt: Instant
    ): MappingWriteResult

    fun resolve(
        organizationId: OrganizationId,
        selector: InventorySourceSelector
    ): InventoryMappingResolution

    fun history(
        organizationId: OrganizationId,
        selector: InventorySourceSelector
    ): List<InventoryMappingDecision>
}

class InventoryIdentityMappingService(
    private val repository: InventoryIdentityMappingRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val itemIds: MappingIdentifierFactory<InventoryItemId> =
        MappingIdentifierFactory { InventoryItemId.of(UUID.randomUUID()) },
    private val locationIds: MappingIdentifierFactory<InventoryLocationId> =
        MappingIdentifierFactory { InventoryLocationId.of(UUID.randomUUID()) },
    private val unitIds: MappingIdentifierFactory<InventoryUnitId> =
        MappingIdentifierFactory { InventoryUnitId.of(UUID.randomUUID()) },
    private val decisionIds: MappingIdentifierFactory<InventoryMappingDecisionId> =
        MappingIdentifierFactory { InventoryMappingDecisionId.of(UUID.randomUUID()) },
    private val correlationIds: MappingIdentifierFactory<InventoryMappingCorrelationId> =
        MappingIdentifierFactory { InventoryMappingCorrelationId.of(UUID.randomUUID()) }
) {
    fun createItem(organizationId: OrganizationId): Pair<IdentityWriteResult, InventoryItemId> {
        val id = itemIds.create()
        return repository.createItem(
            InventoryItemIdentity(organizationId, id, InventoryIdentityState.ACTIVE, now(), null)
        ) to id
    }

    fun createLocation(
        organizationId: OrganizationId
    ): Pair<IdentityWriteResult, InventoryLocationId> {
        val id = locationIds.create()
        return repository.createLocation(
            InventoryLocationIdentity(organizationId, id, InventoryIdentityState.ACTIVE, now(), null)
        ) to id
    }

    fun createUnit(organizationId: OrganizationId): Pair<IdentityWriteResult, InventoryUnitId> {
        val id = unitIds.create()
        return repository.createUnit(
            InventoryUnitIdentity(organizationId, id, InventoryIdentityState.ACTIVE, now(), null)
        ) to id
    }

    fun retireItem(organizationId: OrganizationId, id: InventoryItemId) =
        repository.retireItem(organizationId, id, now())

    fun retireLocation(organizationId: OrganizationId, id: InventoryLocationId) =
        repository.retireLocation(organizationId, id, now())

    fun retireUnit(organizationId: OrganizationId, id: InventoryUnitId) =
        repository.retireUnit(organizationId, id, now())

    fun activateInitial(
        organizationId: OrganizationId,
        selector: InventorySourceSelector,
        target: InventoryMappingTarget,
        evidence: InventoryMappingEvidence,
        principalReference: InventoryMappingPrincipalReference,
        decisionId: InventoryMappingDecisionId = decisionIds.create(),
        correlationId: InventoryMappingCorrelationId = correlationIds.create()
    ): MappingWriteResult = repository.activateInitial(
        InventoryMappingDecision(
            decisionId, organizationId, selector, target, evidence, 1,
            InventoryMappingState.ACTIVE, principalReference,
            InventoryMappingReason.INITIAL_ASSIGNMENT, correlationId, now()
        )
    )

    fun replace(
        organizationId: OrganizationId,
        selector: InventorySourceSelector,
        target: InventoryMappingTarget,
        evidence: InventoryMappingEvidence,
        expectedDecisionId: InventoryMappingDecisionId,
        expectedRevision: Int,
        principalReference: InventoryMappingPrincipalReference,
        reason: InventoryMappingReason,
        decisionId: InventoryMappingDecisionId = decisionIds.create(),
        correlationId: InventoryMappingCorrelationId = correlationIds.create()
    ): MappingWriteResult {
        require(reason != InventoryMappingReason.INITIAL_ASSIGNMENT) {
            "Invalid replacement reason"
        }
        val decision = InventoryMappingDecision(
            decisionId, organizationId, selector, target, evidence, expectedRevision + 1,
            InventoryMappingState.ACTIVE, principalReference, reason, correlationId, now(),
            supersedesDecisionId = expectedDecisionId
        )
        return repository.replace(expectedDecisionId, expectedRevision, decision)
    }

    fun retireMapping(
        organizationId: OrganizationId,
        selector: InventorySourceSelector,
        expectedDecisionId: InventoryMappingDecisionId,
        expectedRevision: Int,
        principalReference: InventoryMappingPrincipalReference,
        reason: InventoryMappingReason,
        correlationId: InventoryMappingCorrelationId = correlationIds.create()
    ): MappingWriteResult {
        require(reason != InventoryMappingReason.INITIAL_ASSIGNMENT) {
            "Invalid retirement reason"
        }
        return repository.retireMapping(
            organizationId, selector, expectedDecisionId, expectedRevision,
            principalReference, reason, correlationId, now()
        )
    }

    fun resolve(organizationId: OrganizationId, selector: InventorySourceSelector) =
        repository.resolve(organizationId, selector)

    fun history(organizationId: OrganizationId, selector: InventorySourceSelector) =
        repository.history(organizationId, selector)

    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MICROS)
}
