package io.flooow.integration.inventory.adjudication

import io.flooow.integration.inventory.comparison.CanonicalInventoryCandidateComparisonResult
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.snapshot.CanonicalInventoryCandidateSnapshotId
import io.flooow.integration.inventory.snapshot.CanonicalInventoryCandidateSnapshotMember
import io.flooow.organization.OrganizationId
import java.text.Normalizer
import java.time.Instant
import java.util.UUID

sealed class CandidateAdjudicationUuid protected constructor(private val value: UUID) {
    fun valueForPersistence(): UUID = value
    protected fun sameValue(other: CandidateAdjudicationUuid) = value == other.value
    protected fun valueHash() = value.hashCode()
    override fun toString() = "[INTERNAL]"

    companion object {
        fun canonical(value: String): UUID {
            val parsed = UUID.fromString(value)
            require(parsed.toString() == value) { "Invalid candidate adjudication identifier" }
            return parsed
        }
    }
}

class CanonicalInventoryCandidateAdjudicationId private constructor(value: UUID) :
    CandidateAdjudicationUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryCandidateAdjudicationId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = CanonicalInventoryCandidateAdjudicationId(value)
        fun parse(value: String) = CanonicalInventoryCandidateAdjudicationId(canonical(value))
    }
}

class CanonicalInventoryCandidateAdjudicationRequestId private constructor(value: UUID) :
    CandidateAdjudicationUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryCandidateAdjudicationRequestId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = CanonicalInventoryCandidateAdjudicationRequestId(value)
        fun parse(value: String) = CanonicalInventoryCandidateAdjudicationRequestId(canonical(value))
    }
}

class CanonicalInventoryCandidateAdjudicationCorrelationId private constructor(value: UUID) :
    CandidateAdjudicationUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryCandidateAdjudicationCorrelationId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = CanonicalInventoryCandidateAdjudicationCorrelationId(value)
        fun parse(value: String) =
            CanonicalInventoryCandidateAdjudicationCorrelationId(canonical(value))
    }
}

class InventoryCandidateAdjudicationPrincipalReference private constructor(
    private val value: String
) {
    fun encodedForPersistence(): String = value
    override fun equals(other: Any?) =
        other is InventoryCandidateAdjudicationPrincipalReference && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = "[REDACTED]"

    companion object {
        fun of(value: String): InventoryCandidateAdjudicationPrincipalReference {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            require(normalized.isNotEmpty() && normalized == normalized.trim()) {
                "Invalid candidate adjudication principal"
            }
            require(normalized.none(Char::isISOControl)) {
                "Invalid candidate adjudication principal"
            }
            require(normalized.toByteArray(Charsets.UTF_8).size <= 128) {
                "Invalid candidate adjudication principal"
            }
            return InventoryCandidateAdjudicationPrincipalReference(normalized)
        }
    }
}

enum class CanonicalInventoryCandidateAdjudicationReason {
    SINGLE_CANDIDATE_CONFIRMATION,
    EXACT_AGREEMENT_CONFIRMATION,
    MEASURE_POLICY_REVIEW,
    EVIDENCE_QUALITY_REVIEW,
    CONTROLLED_EXCEPTION
}

class AdjudicateCanonicalInventoryCandidate(
    val organizationId: OrganizationId,
    val requestId: CanonicalInventoryCandidateAdjudicationRequestId,
    val snapshotId: CanonicalInventoryCandidateSnapshotId,
    val chosenLineageRootDecisionId: InventoryMappingDecisionId,
    val reason: CanonicalInventoryCandidateAdjudicationReason,
    val principalReference: InventoryCandidateAdjudicationPrincipalReference,
    val correlationId: CanonicalInventoryCandidateAdjudicationCorrelationId
) {
    override fun toString() = "AdjudicateCanonicalInventoryCandidate([REDACTED])"
}

data class CanonicalInventoryCandidateAdjudication(
    val id: CanonicalInventoryCandidateAdjudicationId,
    val organizationId: OrganizationId,
    val requestId: CanonicalInventoryCandidateAdjudicationRequestId,
    val snapshotId: CanonicalInventoryCandidateSnapshotId,
    val chosenLineageRootDecisionId: InventoryMappingDecisionId,
    val reason: CanonicalInventoryCandidateAdjudicationReason,
    val principalReference: InventoryCandidateAdjudicationPrincipalReference,
    val correlationId: CanonicalInventoryCandidateAdjudicationCorrelationId,
    val decidedAt: Instant
) {
    override fun toString() = "CanonicalInventoryCandidateAdjudication([REDACTED])"
}

data class AdjudicatedCanonicalInventoryCandidate(
    val adjudication: CanonicalInventoryCandidateAdjudication,
    val comparison: CanonicalInventoryCandidateComparisonResult,
    val chosenMember: CanonicalInventoryCandidateSnapshotMember
) {
    override fun toString() = "AdjudicatedCanonicalInventoryCandidate([REDACTED])"
}

sealed interface CanonicalInventoryCandidateAdjudicationWriteResult {
    class Adjudicated(val adjudicationId: CanonicalInventoryCandidateAdjudicationId) :
        CanonicalInventoryCandidateAdjudicationWriteResult {
        override fun toString() = "Adjudicated([INTERNAL])"
    }
    class AlreadyAdjudicated(val adjudicationId: CanonicalInventoryCandidateAdjudicationId) :
        CanonicalInventoryCandidateAdjudicationWriteResult {
        override fun toString() = "AlreadyAdjudicated([INTERNAL])"
    }
    data object SnapshotUnavailable : CanonicalInventoryCandidateAdjudicationWriteResult
    data object CandidateUnavailable : CanonicalInventoryCandidateAdjudicationWriteResult
    data object ReasonMismatch : CanonicalInventoryCandidateAdjudicationWriteResult
    data object Conflict : CanonicalInventoryCandidateAdjudicationWriteResult
    data object IntegrityFailure : CanonicalInventoryCandidateAdjudicationWriteResult
}

sealed interface CanonicalInventoryCandidateAdjudicationReadResult {
    class Found(val adjudicatedCandidate: AdjudicatedCanonicalInventoryCandidate) :
        CanonicalInventoryCandidateAdjudicationReadResult {
        override fun toString() = "Found([REDACTED])"
    }
    data object NotFound : CanonicalInventoryCandidateAdjudicationReadResult
    data object IntegrityFailure : CanonicalInventoryCandidateAdjudicationReadResult
}

fun CanonicalInventoryCandidateAdjudicationReason.matches(
    comparison: CanonicalInventoryCandidateComparisonResult
): Boolean = when (comparison) {
    is CanonicalInventoryCandidateComparisonResult.SingleCandidate ->
        this == CanonicalInventoryCandidateAdjudicationReason.SINGLE_CANDIDATE_CONFIRMATION
    is CanonicalInventoryCandidateComparisonResult.ExactAgreement ->
        this == CanonicalInventoryCandidateAdjudicationReason.EXACT_AGREEMENT_CONFIRMATION
    is CanonicalInventoryCandidateComparisonResult.MeasureMismatch ->
        this == CanonicalInventoryCandidateAdjudicationReason.MEASURE_POLICY_REVIEW ||
            this == CanonicalInventoryCandidateAdjudicationReason.CONTROLLED_EXCEPTION
    is CanonicalInventoryCandidateComparisonResult.ExactDivergence ->
        this == CanonicalInventoryCandidateAdjudicationReason.EVIDENCE_QUALITY_REVIEW ||
            this == CanonicalInventoryCandidateAdjudicationReason.CONTROLLED_EXCEPTION
    CanonicalInventoryCandidateComparisonResult.IntegrityFailure -> false
}

fun interface CandidateAdjudicationIdentifierFactory<T> { fun create(): T }

interface CanonicalInventoryCandidateAdjudicationRepository {
    fun adjudicate(
        command: AdjudicateCanonicalInventoryCandidate,
        adjudicationId: CanonicalInventoryCandidateAdjudicationId
    ): CanonicalInventoryCandidateAdjudicationWriteResult

    fun find(
        organizationId: OrganizationId,
        adjudicationId: CanonicalInventoryCandidateAdjudicationId
    ): CanonicalInventoryCandidateAdjudicationReadResult
}

class CanonicalInventoryCandidateAdjudicationService(
    private val repository: CanonicalInventoryCandidateAdjudicationRepository,
    private val adjudicationIds:
        CandidateAdjudicationIdentifierFactory<CanonicalInventoryCandidateAdjudicationId> =
        CandidateAdjudicationIdentifierFactory {
            CanonicalInventoryCandidateAdjudicationId.of(UUID.randomUUID())
        },
    private val correlationIds:
        CandidateAdjudicationIdentifierFactory<CanonicalInventoryCandidateAdjudicationCorrelationId> =
        CandidateAdjudicationIdentifierFactory {
            CanonicalInventoryCandidateAdjudicationCorrelationId.of(UUID.randomUUID())
        }
) {
    fun adjudicate(
        organizationId: OrganizationId,
        requestId: CanonicalInventoryCandidateAdjudicationRequestId,
        snapshotId: CanonicalInventoryCandidateSnapshotId,
        chosenLineageRootDecisionId: InventoryMappingDecisionId,
        reason: CanonicalInventoryCandidateAdjudicationReason,
        principalReference: InventoryCandidateAdjudicationPrincipalReference,
        correlationId: CanonicalInventoryCandidateAdjudicationCorrelationId = correlationIds.create()
    ) = repository.adjudicate(
        AdjudicateCanonicalInventoryCandidate(
            organizationId, requestId, snapshotId, chosenLineageRootDecisionId,
            reason, principalReference, correlationId
        ),
        adjudicationIds.create()
    )

    fun find(
        organizationId: OrganizationId,
        adjudicationId: CanonicalInventoryCandidateAdjudicationId
    ) = repository.find(organizationId, adjudicationId)
}
