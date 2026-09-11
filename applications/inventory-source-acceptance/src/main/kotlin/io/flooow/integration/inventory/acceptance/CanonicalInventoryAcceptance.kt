package io.flooow.integration.inventory.acceptance

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.mapping.InventoryMappingTarget
import io.flooow.integration.inventory.observation.CanonicalInventoryObservationId
import io.flooow.integration.inventory.observation.CanonicalInventorySourcePointer
import io.flooow.organization.OrganizationId
import java.text.Normalizer
import java.time.Instant
import java.util.UUID

private const val INVENTORY_SOURCE_BALANCE_CAPABILITY = "inventory.source-balance.read"

sealed class AcceptanceUuid protected constructor(private val value: UUID) {
    fun valueForPersistence(): UUID = value
    protected fun sameValue(other: AcceptanceUuid) = value == other.value
    protected fun valueHash() = value.hashCode()
    override fun toString() = "[INTERNAL]"

    companion object {
        fun canonical(value: String): UUID {
            val parsed = UUID.fromString(value)
            require(parsed.toString() == value) { "Invalid acceptance identifier" }
            return parsed
        }
    }
}

class CanonicalInventoryAcceptanceId private constructor(value: UUID) : AcceptanceUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryAcceptanceId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = CanonicalInventoryAcceptanceId(value)
        fun parse(value: String) = CanonicalInventoryAcceptanceId(canonical(value))
    }
}

class CanonicalInventoryAcceptanceCorrelationId private constructor(value: UUID) :
    AcceptanceUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryAcceptanceCorrelationId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = CanonicalInventoryAcceptanceCorrelationId(value)
        fun parse(value: String) = CanonicalInventoryAcceptanceCorrelationId(canonical(value))
    }
}

class InventoryAcceptancePrincipalReference private constructor(private val value: String) {
    fun encodedForPersistence(): String = value
    override fun equals(other: Any?) =
        other is InventoryAcceptancePrincipalReference && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = "[REDACTED]"

    companion object {
        fun of(value: String): InventoryAcceptancePrincipalReference {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            require(normalized.isNotEmpty() && normalized == normalized.trim()) {
                "Invalid acceptance principal"
            }
            require(normalized.none(Char::isISOControl)) { "Invalid acceptance principal" }
            require(normalized.toByteArray(Charsets.UTF_8).size <= 128) {
                "Invalid acceptance principal"
            }
            return InventoryAcceptancePrincipalReference(normalized)
        }
    }
}

data class AcceptedCanonicalInventoryObservation(
    val observationId: CanonicalInventoryObservationId,
    val sourcePointer: CanonicalInventorySourcePointer,
    val projectionRevision: Int,
    val mappingDecisionId: InventoryMappingDecisionId,
    val mappingRevision: Int,
    val target: InventoryMappingTarget
) {
    init { require(projectionRevision > 0 && mappingRevision > 0) { "Invalid accepted observation" } }
    override fun toString() = "AcceptedCanonicalInventoryObservation([INTERNAL])"
}

enum class CanonicalInventoryAcceptanceReason {
    INITIAL_ACCEPTANCE,
    NEW_SOURCE_EVIDENCE,
    MAPPING_REINTERPRETATION,
    OPERATOR_CORRECTION,
    SOURCE_REVOKED,
    EVIDENCE_INVALIDATED,
    OPERATOR_WITHDRAWAL;

    fun isReplacement() = this in setOf(
        NEW_SOURCE_EVIDENCE, MAPPING_REINTERPRETATION, OPERATOR_CORRECTION
    )

    fun isWithdrawal() = this in setOf(
        SOURCE_REVOKED, EVIDENCE_INVALIDATED, OPERATOR_WITHDRAWAL
    )
}

enum class CanonicalInventoryAcceptanceState { ACTIVE, RETIRED }

data class CanonicalInventoryAcceptance(
    val id: CanonicalInventoryAcceptanceId,
    val organizationId: OrganizationId,
    val connectionId: IntegrationConnectionId,
    val capability: String = INVENTORY_SOURCE_BALANCE_CAPABILITY,
    val lineageRootDecisionId: InventoryMappingDecisionId,
    val revision: Int,
    val state: CanonicalInventoryAcceptanceState,
    val acceptedObservation: AcceptedCanonicalInventoryObservation,
    val principalReference: InventoryAcceptancePrincipalReference,
    val reason: CanonicalInventoryAcceptanceReason,
    val correlationId: CanonicalInventoryAcceptanceCorrelationId,
    val acceptedAt: Instant,
    val retiredAt: Instant? = null,
    val supersedesAcceptanceId: CanonicalInventoryAcceptanceId? = null
) {
    init {
        require(capability == INVENTORY_SOURCE_BALANCE_CAPABILITY) {
            "Inventory acceptance capability unavailable"
        }
        require(connectionId == acceptedObservation.sourcePointer.connectionId &&
            capability == acceptedObservation.sourcePointer.capability) {
            "Accepted observation scope mismatch"
        }
        require(revision > 0) { "Invalid acceptance revision" }
        require(
            (revision == 1 && supersedesAcceptanceId == null &&
                reason == CanonicalInventoryAcceptanceReason.INITIAL_ACCEPTANCE) ||
                (revision > 1 && supersedesAcceptanceId != null && reason.isReplacement())
        ) { "Invalid acceptance predecessor" }
        require(
            (state == CanonicalInventoryAcceptanceState.ACTIVE && retiredAt == null) ||
                (state == CanonicalInventoryAcceptanceState.RETIRED &&
                    retiredAt != null && retiredAt >= acceptedAt)
        ) { "Invalid acceptance lifecycle" }
    }

    override fun toString() = "CanonicalInventoryAcceptance([REDACTED])"
}

sealed interface CanonicalInventoryAcceptanceResult {
    class Accepted(val acceptanceId: CanonicalInventoryAcceptanceId, val revision: Int) :
        CanonicalInventoryAcceptanceResult {
        override fun toString() = "Accepted([INTERNAL])"
    }
    class AlreadyAccepted(val acceptanceId: CanonicalInventoryAcceptanceId, val revision: Int) :
        CanonicalInventoryAcceptanceResult {
        override fun toString() = "AlreadyAccepted([INTERNAL])"
    }
    class Withdrawn(val revision: Int) : CanonicalInventoryAcceptanceResult
    data object Unaccepted : CanonicalInventoryAcceptanceResult
    data object CandidateUnavailable : CanonicalInventoryAcceptanceResult
    data object LineageUnavailable : CanonicalInventoryAcceptanceResult
    data object TargetUnavailable : CanonicalInventoryAcceptanceResult
    data object Stale : CanonicalInventoryAcceptanceResult
    data object Conflict : CanonicalInventoryAcceptanceResult
    data object IntegrityFailure : CanonicalInventoryAcceptanceResult
}

fun interface AcceptanceIdentifierFactory<T> { fun create(): T }

interface CanonicalInventoryAcceptanceRepository {
    fun acceptInitial(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        candidateObservationId: CanonicalInventoryObservationId,
        acceptanceId: CanonicalInventoryAcceptanceId,
        principal: InventoryAcceptancePrincipalReference,
        correlationId: CanonicalInventoryAcceptanceCorrelationId
    ): CanonicalInventoryAcceptanceResult

    fun replace(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedAcceptanceId: CanonicalInventoryAcceptanceId,
        expectedRevision: Int,
        candidateObservationId: CanonicalInventoryObservationId,
        acceptanceId: CanonicalInventoryAcceptanceId,
        principal: InventoryAcceptancePrincipalReference,
        reason: CanonicalInventoryAcceptanceReason,
        correlationId: CanonicalInventoryAcceptanceCorrelationId
    ): CanonicalInventoryAcceptanceResult

    fun withdraw(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedAcceptanceId: CanonicalInventoryAcceptanceId,
        expectedRevision: Int,
        principal: InventoryAcceptancePrincipalReference,
        reason: CanonicalInventoryAcceptanceReason,
        correlationId: CanonicalInventoryAcceptanceCorrelationId
    ): CanonicalInventoryAcceptanceResult

    fun head(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): CanonicalInventoryAcceptance?

    fun history(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): List<CanonicalInventoryAcceptance>
}

class CanonicalInventoryAcceptanceService(
    private val repository: CanonicalInventoryAcceptanceRepository,
    private val acceptanceIds: AcceptanceIdentifierFactory<CanonicalInventoryAcceptanceId> =
        AcceptanceIdentifierFactory { CanonicalInventoryAcceptanceId.of(UUID.randomUUID()) },
    private val correlationIds:
        AcceptanceIdentifierFactory<CanonicalInventoryAcceptanceCorrelationId> =
        AcceptanceIdentifierFactory {
            CanonicalInventoryAcceptanceCorrelationId.of(UUID.randomUUID())
        }
) {
    fun acceptInitial(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        candidateObservationId: CanonicalInventoryObservationId,
        principal: InventoryAcceptancePrincipalReference,
        correlationId: CanonicalInventoryAcceptanceCorrelationId = correlationIds.create()
    ) = repository.acceptInitial(
        organizationId, lineageRootDecisionId, candidateObservationId,
        acceptanceIds.create(), principal, correlationId
    )

    fun replace(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedAcceptanceId: CanonicalInventoryAcceptanceId,
        expectedRevision: Int,
        candidateObservationId: CanonicalInventoryObservationId,
        principal: InventoryAcceptancePrincipalReference,
        reason: CanonicalInventoryAcceptanceReason,
        correlationId: CanonicalInventoryAcceptanceCorrelationId = correlationIds.create()
    ): CanonicalInventoryAcceptanceResult {
        require(reason.isReplacement()) { "Invalid replacement reason" }
        return repository.replace(
            organizationId, lineageRootDecisionId, expectedAcceptanceId, expectedRevision,
            candidateObservationId, acceptanceIds.create(), principal, reason, correlationId
        )
    }

    fun withdraw(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedAcceptanceId: CanonicalInventoryAcceptanceId,
        expectedRevision: Int,
        principal: InventoryAcceptancePrincipalReference,
        reason: CanonicalInventoryAcceptanceReason,
        correlationId: CanonicalInventoryAcceptanceCorrelationId = correlationIds.create()
    ): CanonicalInventoryAcceptanceResult {
        require(reason.isWithdrawal()) { "Invalid withdrawal reason" }
        return repository.withdraw(
            organizationId, lineageRootDecisionId, expectedAcceptanceId, expectedRevision,
            principal, reason, correlationId
        )
    }

    fun head(organizationId: OrganizationId, lineageRootDecisionId: InventoryMappingDecisionId) =
        repository.head(organizationId, lineageRootDecisionId)

    fun history(organizationId: OrganizationId, lineageRootDecisionId: InventoryMappingDecisionId) =
        repository.history(organizationId, lineageRootDecisionId)
}
