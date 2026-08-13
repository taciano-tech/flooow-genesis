package io.flooow.integration.inventory.selection

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.acceptance.CanonicalInventoryAcceptanceId
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.mapping.InventoryMappingTarget
import io.flooow.integration.inventory.observation.CanonicalInventoryObservationId
import io.flooow.integration.inventory.observation.CanonicalInventorySourcePointer
import io.flooow.integration.inventory.observation.ExactInventoryQuantity
import io.flooow.organization.OrganizationId
import java.text.Normalizer
import java.time.Instant
import java.util.UUID

private const val INVENTORY_CAPABILITY = "inventory.source-balance.read"

sealed class SelectionUuid protected constructor(private val value: UUID) {
    fun valueForPersistence(): UUID = value
    protected fun sameValue(other: SelectionUuid) = value == other.value
    protected fun valueHash() = value.hashCode()
    override fun toString() = "[INTERNAL]"

    companion object {
        fun canonical(value: String): UUID {
            val parsed = UUID.fromString(value)
            require(parsed.toString() == value) { "Invalid selection identifier" }
            return parsed
        }
    }
}

class CanonicalInventoryMeasureSelectionId private constructor(value: UUID) :
    SelectionUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryMeasureSelectionId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = CanonicalInventoryMeasureSelectionId(value)
        fun parse(value: String) = CanonicalInventoryMeasureSelectionId(canonical(value))
    }
}

class CanonicalInventoryMeasureSelectionCorrelationId private constructor(value: UUID) :
    SelectionUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryMeasureSelectionCorrelationId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = CanonicalInventoryMeasureSelectionCorrelationId(value)
        fun parse(value: String) = CanonicalInventoryMeasureSelectionCorrelationId(canonical(value))
    }
}

class InventoryMeasureSelectionPrincipalReference private constructor(private val value: String) {
    fun encodedForPersistence(): String = value
    override fun equals(other: Any?) =
        other is InventoryMeasureSelectionPrincipalReference && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = "[REDACTED]"

    companion object {
        fun of(value: String): InventoryMeasureSelectionPrincipalReference {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            require(normalized.isNotEmpty() && normalized == normalized.trim()) {
                "Invalid measure selection principal"
            }
            require(normalized.none(Char::isISOControl)) {
                "Invalid measure selection principal"
            }
            require(normalized.toByteArray(Charsets.UTF_8).size <= 128) {
                "Invalid measure selection principal"
            }
            return InventoryMeasureSelectionPrincipalReference(normalized)
        }
    }
}

enum class CanonicalInventoryMeasure {
    AVAILABLE_TO_SELL,
    ON_HAND,
    RESERVED,
    PENDING_INBOUND,
    PENDING_OUTBOUND
}

enum class CanonicalInventoryMeasureSelectionReason {
    INITIAL_SELECTION,
    SOURCE_SEMANTICS_CORRECTION,
    OPERATOR_CORRECTION,
    SOURCE_SEMANTICS_REVOKED,
    OPERATOR_WITHDRAWAL;

    fun isReplacement() = this in setOf(SOURCE_SEMANTICS_CORRECTION, OPERATOR_CORRECTION)
    fun isWithdrawal() = this in setOf(SOURCE_SEMANTICS_REVOKED, OPERATOR_WITHDRAWAL)
}

enum class CanonicalInventoryMeasureSelectionState { ACTIVE, RETIRED }

data class CanonicalInventoryMeasureSelection(
    val id: CanonicalInventoryMeasureSelectionId,
    val organizationId: OrganizationId,
    val connectionId: IntegrationConnectionId,
    val capability: String = INVENTORY_CAPABILITY,
    val lineageRootDecisionId: InventoryMappingDecisionId,
    val revision: Int,
    val state: CanonicalInventoryMeasureSelectionState,
    val measure: CanonicalInventoryMeasure,
    val anchorAcceptanceId: CanonicalInventoryAcceptanceId,
    val anchorAcceptanceRevision: Int,
    val anchorObservationId: CanonicalInventoryObservationId,
    val principalReference: InventoryMeasureSelectionPrincipalReference,
    val reason: CanonicalInventoryMeasureSelectionReason,
    val correlationId: CanonicalInventoryMeasureSelectionCorrelationId,
    val selectedAt: Instant,
    val retiredAt: Instant? = null,
    val supersedesSelectionId: CanonicalInventoryMeasureSelectionId? = null
) {
    init {
        require(capability == INVENTORY_CAPABILITY) { "Inventory selection capability unavailable" }
        require(revision > 0 && anchorAcceptanceRevision > 0) { "Invalid selection revision" }
        require(
            (revision == 1 && supersedesSelectionId == null &&
                reason == CanonicalInventoryMeasureSelectionReason.INITIAL_SELECTION) ||
                (revision > 1 && supersedesSelectionId != null && reason.isReplacement())
        ) { "Invalid selection predecessor" }
        require(
            (state == CanonicalInventoryMeasureSelectionState.ACTIVE && retiredAt == null) ||
                (state == CanonicalInventoryMeasureSelectionState.RETIRED &&
                    retiredAt != null && retiredAt >= selectedAt)
        ) { "Invalid selection lifecycle" }
    }

    override fun toString() = "CanonicalInventoryMeasureSelection([REDACTED])"
}

data class SelectedCanonicalInventoryMeasure(
    val organizationId: OrganizationId,
    val connectionId: IntegrationConnectionId,
    val capability: String,
    val lineageRootDecisionId: InventoryMappingDecisionId,
    val selectionId: CanonicalInventoryMeasureSelectionId,
    val selectionRevision: Int,
    val acceptanceId: CanonicalInventoryAcceptanceId,
    val acceptanceRevision: Int,
    val observationId: CanonicalInventoryObservationId,
    val sourcePointer: CanonicalInventorySourcePointer,
    val projectionRevision: Int,
    val mappingDecisionId: InventoryMappingDecisionId,
    val mappingRevision: Int,
    val target: InventoryMappingTarget,
    val measure: CanonicalInventoryMeasure,
    val exactQuantity: ExactInventoryQuantity
) {
    init {
        require(capability == INVENTORY_CAPABILITY && connectionId == sourcePointer.connectionId &&
            capability == sourcePointer.capability) { "Selected measure scope mismatch" }
        require(selectionRevision > 0 && acceptanceRevision > 0 && projectionRevision > 0 &&
            mappingRevision > 0) { "Invalid selected measure revision" }
    }

    override fun toString() = "SelectedCanonicalInventoryMeasure([REDACTED])"
}

sealed interface CanonicalInventoryMeasureSelectionResult {
    class Selected(val selectionId: CanonicalInventoryMeasureSelectionId, val revision: Int) :
        CanonicalInventoryMeasureSelectionResult {
        override fun toString() = "Selected([INTERNAL])"
    }
    class AlreadySelected(val selectionId: CanonicalInventoryMeasureSelectionId, val revision: Int) :
        CanonicalInventoryMeasureSelectionResult {
        override fun toString() = "AlreadySelected([INTERNAL])"
    }
    class Withdrawn(val revision: Int) : CanonicalInventoryMeasureSelectionResult
    data object Unaccepted : CanonicalInventoryMeasureSelectionResult
    data object CandidateUnavailable : CanonicalInventoryMeasureSelectionResult
    data object LineageUnavailable : CanonicalInventoryMeasureSelectionResult
    data object TargetUnavailable : CanonicalInventoryMeasureSelectionResult
    data object MeasureUnavailable : CanonicalInventoryMeasureSelectionResult
    data object Conflict : CanonicalInventoryMeasureSelectionResult
    data object IntegrityFailure : CanonicalInventoryMeasureSelectionResult
}

sealed interface CanonicalInventoryMeasureResolutionResult {
    class Resolved(val selectedCandidate: SelectedCanonicalInventoryMeasure) :
        CanonicalInventoryMeasureResolutionResult {
        override fun toString() = "Resolved([REDACTED])"
    }
    data object Unselected : CanonicalInventoryMeasureResolutionResult
    data object Unaccepted : CanonicalInventoryMeasureResolutionResult
    data object MeasureUnavailable : CanonicalInventoryMeasureResolutionResult
    data object IntegrityFailure : CanonicalInventoryMeasureResolutionResult
}

fun interface SelectionIdentifierFactory<T> { fun create(): T }

interface CanonicalInventoryMeasureSelectionRepository {
    fun selectInitial(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        measure: CanonicalInventoryMeasure,
        selectionId: CanonicalInventoryMeasureSelectionId,
        principal: InventoryMeasureSelectionPrincipalReference,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId
    ): CanonicalInventoryMeasureSelectionResult

    fun replace(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedSelectionId: CanonicalInventoryMeasureSelectionId,
        expectedRevision: Int,
        measure: CanonicalInventoryMeasure,
        selectionId: CanonicalInventoryMeasureSelectionId,
        principal: InventoryMeasureSelectionPrincipalReference,
        reason: CanonicalInventoryMeasureSelectionReason,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId
    ): CanonicalInventoryMeasureSelectionResult

    fun withdraw(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedSelectionId: CanonicalInventoryMeasureSelectionId,
        expectedRevision: Int,
        principal: InventoryMeasureSelectionPrincipalReference,
        reason: CanonicalInventoryMeasureSelectionReason,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId
    ): CanonicalInventoryMeasureSelectionResult

    fun resolve(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): CanonicalInventoryMeasureResolutionResult

    fun head(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): CanonicalInventoryMeasureSelection?

    fun history(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId
    ): List<CanonicalInventoryMeasureSelection>
}

class CanonicalInventoryMeasureSelectionService(
    private val repository: CanonicalInventoryMeasureSelectionRepository,
    private val selectionIds: SelectionIdentifierFactory<CanonicalInventoryMeasureSelectionId> =
        SelectionIdentifierFactory { CanonicalInventoryMeasureSelectionId.of(UUID.randomUUID()) },
    private val correlationIds:
        SelectionIdentifierFactory<CanonicalInventoryMeasureSelectionCorrelationId> =
        SelectionIdentifierFactory {
            CanonicalInventoryMeasureSelectionCorrelationId.of(UUID.randomUUID())
        }
) {
    fun selectInitial(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        measure: CanonicalInventoryMeasure,
        principal: InventoryMeasureSelectionPrincipalReference,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId = correlationIds.create()
    ) = repository.selectInitial(
        organizationId, lineageRootDecisionId, measure, selectionIds.create(), principal,
        correlationId
    )

    fun replace(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedSelectionId: CanonicalInventoryMeasureSelectionId,
        expectedRevision: Int,
        measure: CanonicalInventoryMeasure,
        principal: InventoryMeasureSelectionPrincipalReference,
        reason: CanonicalInventoryMeasureSelectionReason,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId = correlationIds.create()
    ): CanonicalInventoryMeasureSelectionResult {
        require(reason.isReplacement()) { "Invalid measure replacement reason" }
        return repository.replace(
            organizationId, lineageRootDecisionId, expectedSelectionId, expectedRevision, measure,
            selectionIds.create(), principal, reason, correlationId
        )
    }

    fun withdraw(
        organizationId: OrganizationId,
        lineageRootDecisionId: InventoryMappingDecisionId,
        expectedSelectionId: CanonicalInventoryMeasureSelectionId,
        expectedRevision: Int,
        principal: InventoryMeasureSelectionPrincipalReference,
        reason: CanonicalInventoryMeasureSelectionReason,
        correlationId: CanonicalInventoryMeasureSelectionCorrelationId = correlationIds.create()
    ): CanonicalInventoryMeasureSelectionResult {
        require(reason.isWithdrawal()) { "Invalid measure withdrawal reason" }
        return repository.withdraw(
            organizationId, lineageRootDecisionId, expectedSelectionId, expectedRevision,
            principal, reason, correlationId
        )
    }

    fun resolve(organizationId: OrganizationId, lineageRootDecisionId: InventoryMappingDecisionId) =
        repository.resolve(organizationId, lineageRootDecisionId)

    fun head(organizationId: OrganizationId, lineageRootDecisionId: InventoryMappingDecisionId) =
        repository.head(organizationId, lineageRootDecisionId)

    fun history(organizationId: OrganizationId, lineageRootDecisionId: InventoryMappingDecisionId) =
        repository.history(organizationId, lineageRootDecisionId)
}
