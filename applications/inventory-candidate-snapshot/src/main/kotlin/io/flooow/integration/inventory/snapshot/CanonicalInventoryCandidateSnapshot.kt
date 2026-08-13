package io.flooow.integration.inventory.snapshot

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.acceptance.CanonicalInventoryAcceptanceId
import io.flooow.integration.inventory.mapping.InventoryItemId
import io.flooow.integration.inventory.mapping.InventoryLocationId
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.mapping.InventoryUnitId
import io.flooow.integration.inventory.observation.CanonicalInventoryObservationId
import io.flooow.integration.inventory.observation.CanonicalInventorySourcePointer
import io.flooow.integration.inventory.observation.ExactInventoryQuantity
import io.flooow.integration.inventory.selection.CanonicalInventoryMeasure
import io.flooow.integration.inventory.selection.CanonicalInventoryMeasureSelectionId
import io.flooow.organization.OrganizationId
import java.text.Normalizer
import java.time.Instant
import java.util.UUID

sealed class CandidateSnapshotUuid protected constructor(private val value: UUID) {
    fun valueForPersistence(): UUID = value
    protected fun sameValue(other: CandidateSnapshotUuid) = value == other.value
    protected fun valueHash() = value.hashCode()
    override fun toString() = "[INTERNAL]"

    companion object {
        fun canonical(value: String): UUID {
            val parsed = UUID.fromString(value)
            require(parsed.toString() == value) { "Invalid candidate snapshot identifier" }
            return parsed
        }
    }
}

class CanonicalInventoryCandidateSnapshotId private constructor(value: UUID) :
    CandidateSnapshotUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryCandidateSnapshotId && sameValue(other)
    override fun hashCode() = valueHash()

    companion object {
        fun of(value: UUID) = CanonicalInventoryCandidateSnapshotId(value)
        fun parse(value: String) = CanonicalInventoryCandidateSnapshotId(canonical(value))
    }
}

class CanonicalInventoryCandidateSnapshotRequestId private constructor(value: UUID) :
    CandidateSnapshotUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryCandidateSnapshotRequestId && sameValue(other)
    override fun hashCode() = valueHash()

    companion object {
        fun of(value: UUID) = CanonicalInventoryCandidateSnapshotRequestId(value)
        fun parse(value: String) = CanonicalInventoryCandidateSnapshotRequestId(canonical(value))
    }
}

class CanonicalInventoryCandidateSnapshotCorrelationId private constructor(value: UUID) :
    CandidateSnapshotUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryCandidateSnapshotCorrelationId && sameValue(other)
    override fun hashCode() = valueHash()

    companion object {
        fun of(value: UUID) = CanonicalInventoryCandidateSnapshotCorrelationId(value)
        fun parse(value: String) =
            CanonicalInventoryCandidateSnapshotCorrelationId(canonical(value))
    }
}

class InventoryCandidateSnapshotPrincipalReference private constructor(private val value: String) {
    fun encodedForPersistence(): String = value
    override fun equals(other: Any?) =
        other is InventoryCandidateSnapshotPrincipalReference && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = "[REDACTED]"

    companion object {
        fun of(value: String): InventoryCandidateSnapshotPrincipalReference {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            require(normalized.isNotEmpty() && normalized == normalized.trim()) {
                "Invalid candidate snapshot principal"
            }
            require(normalized.none(Char::isISOControl)) {
                "Invalid candidate snapshot principal"
            }
            require(normalized.toByteArray(Charsets.UTF_8).size <= 128) {
                "Invalid candidate snapshot principal"
            }
            return InventoryCandidateSnapshotPrincipalReference(normalized)
        }
    }
}

data class CanonicalInventoryCandidateTarget(
    val itemId: InventoryItemId,
    val locationId: InventoryLocationId?,
    val unitId: InventoryUnitId
) {
    override fun toString() = "CanonicalInventoryCandidateTarget([INTERNAL])"
}

class CaptureCanonicalInventoryCandidates(
    val organizationId: OrganizationId,
    val requestId: CanonicalInventoryCandidateSnapshotRequestId,
    val target: CanonicalInventoryCandidateTarget,
    lineageRootDecisionIds: Collection<InventoryMappingDecisionId>,
    val principalReference: InventoryCandidateSnapshotPrincipalReference,
    val correlationId: CanonicalInventoryCandidateSnapshotCorrelationId
) {
    val lineageRootDecisionIds: Set<InventoryMappingDecisionId> =
        LinkedHashSet(lineageRootDecisionIds)

    init {
        require(lineageRootDecisionIds.isNotEmpty()) { "Candidate snapshot requires a lineage" }
        require(this.lineageRootDecisionIds.size == lineageRootDecisionIds.size) {
            "Duplicate candidate snapshot lineage"
        }
    }

    override fun toString() = "CaptureCanonicalInventoryCandidates([REDACTED])"
}

data class CanonicalInventoryCandidateSnapshot(
    val id: CanonicalInventoryCandidateSnapshotId,
    val organizationId: OrganizationId,
    val requestId: CanonicalInventoryCandidateSnapshotRequestId,
    val target: CanonicalInventoryCandidateTarget,
    val principalReference: InventoryCandidateSnapshotPrincipalReference,
    val correlationId: CanonicalInventoryCandidateSnapshotCorrelationId,
    val capturedAt: Instant,
    val memberCount: Int
) {
    init { require(memberCount > 0) { "Candidate snapshot must not be empty" } }
    override fun toString() = "CanonicalInventoryCandidateSnapshot([REDACTED])"
}

data class CanonicalInventoryCandidateSnapshotMember(
    val organizationId: OrganizationId,
    val snapshotId: CanonicalInventoryCandidateSnapshotId,
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
    val target: CanonicalInventoryCandidateTarget,
    val measure: CanonicalInventoryMeasure,
    val exactQuantity: ExactInventoryQuantity
) {
    init {
        require(capability == "inventory.source-balance.read") {
            "Candidate snapshot capability unavailable"
        }
        require(connectionId == sourcePointer.connectionId && capability == sourcePointer.capability) {
            "Candidate snapshot source scope mismatch"
        }
        require(selectionRevision > 0 && acceptanceRevision > 0 && projectionRevision > 0 &&
            mappingRevision > 0) { "Invalid candidate snapshot revision" }
    }

    override fun toString() = "CanonicalInventoryCandidateSnapshotMember([REDACTED])"
}

data class CanonicalInventoryCandidateSnapshotView(
    val snapshot: CanonicalInventoryCandidateSnapshot,
    val members: List<CanonicalInventoryCandidateSnapshotMember>
) {
    init {
        require(snapshot.memberCount == members.size && members.all {
            it.organizationId == snapshot.organizationId && it.snapshotId == snapshot.id &&
                it.target == snapshot.target
        }) { "Candidate snapshot content mismatch" }
    }

    override fun toString() = "CanonicalInventoryCandidateSnapshotView([REDACTED])"
}

sealed interface CanonicalInventoryCandidateSnapshotCaptureResult {
    class Captured(
        val snapshotId: CanonicalInventoryCandidateSnapshotId,
        val memberCount: Int
    ) : CanonicalInventoryCandidateSnapshotCaptureResult {
        override fun toString() = "Captured([INTERNAL], memberCount=$memberCount)"
    }

    class AlreadyCaptured(
        val snapshotId: CanonicalInventoryCandidateSnapshotId,
        val memberCount: Int
    ) : CanonicalInventoryCandidateSnapshotCaptureResult {
        override fun toString() = "AlreadyCaptured([INTERNAL], memberCount=$memberCount)"
    }

    data object CandidateUnavailable : CanonicalInventoryCandidateSnapshotCaptureResult
    data object TargetUnavailable : CanonicalInventoryCandidateSnapshotCaptureResult
    data object TargetMismatch : CanonicalInventoryCandidateSnapshotCaptureResult
    data object Conflict : CanonicalInventoryCandidateSnapshotCaptureResult
    data object IntegrityFailure : CanonicalInventoryCandidateSnapshotCaptureResult
}

sealed interface CanonicalInventoryCandidateSnapshotReadResult {
    class Found(val snapshot: CanonicalInventoryCandidateSnapshotView) :
        CanonicalInventoryCandidateSnapshotReadResult {
        override fun toString() = "Found([REDACTED])"
    }

    data object NotFound : CanonicalInventoryCandidateSnapshotReadResult
    data object IntegrityFailure : CanonicalInventoryCandidateSnapshotReadResult
}

object CanonicalInventoryCandidateLineageOrder : Comparator<InventoryMappingDecisionId> {
    override fun compare(left: InventoryMappingDecisionId, right: InventoryMappingDecisionId): Int {
        val leftUuid = left.valueForPersistence()
        val rightUuid = right.valueForPersistence()
        return java.lang.Long.compareUnsigned(leftUuid.mostSignificantBits, rightUuid.mostSignificantBits)
            .takeIf { it != 0 }
            ?: java.lang.Long.compareUnsigned(
                leftUuid.leastSignificantBits,
                rightUuid.leastSignificantBits
            )
    }
}

fun interface CandidateSnapshotIdentifierFactory<T> { fun create(): T }

interface CanonicalInventoryCandidateSnapshotRepository {
    fun capture(
        command: CaptureCanonicalInventoryCandidates,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ): CanonicalInventoryCandidateSnapshotCaptureResult

    fun find(
        organizationId: OrganizationId,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ): CanonicalInventoryCandidateSnapshotReadResult
}

class CanonicalInventoryCandidateSnapshotService(
    private val repository: CanonicalInventoryCandidateSnapshotRepository,
    private val snapshotIds:
        CandidateSnapshotIdentifierFactory<CanonicalInventoryCandidateSnapshotId> =
        CandidateSnapshotIdentifierFactory {
            CanonicalInventoryCandidateSnapshotId.of(UUID.randomUUID())
        },
    private val correlationIds:
        CandidateSnapshotIdentifierFactory<CanonicalInventoryCandidateSnapshotCorrelationId> =
        CandidateSnapshotIdentifierFactory {
            CanonicalInventoryCandidateSnapshotCorrelationId.of(UUID.randomUUID())
        }
) {
    fun capture(
        organizationId: OrganizationId,
        requestId: CanonicalInventoryCandidateSnapshotRequestId,
        target: CanonicalInventoryCandidateTarget,
        lineageRootDecisionIds: Collection<InventoryMappingDecisionId>,
        principalReference: InventoryCandidateSnapshotPrincipalReference,
        correlationId: CanonicalInventoryCandidateSnapshotCorrelationId = correlationIds.create()
    ) = repository.capture(
        CaptureCanonicalInventoryCandidates(
            organizationId, requestId, target, lineageRootDecisionIds,
            principalReference, correlationId
        ),
        snapshotIds.create()
    )

    fun find(
        organizationId: OrganizationId,
        snapshotId: CanonicalInventoryCandidateSnapshotId
    ) = repository.find(organizationId, snapshotId)
}
