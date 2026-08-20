package io.flooow.integration.inventory.authority

import io.flooow.integration.inventory.observation.CanonicalInventoryObservation
import io.flooow.integration.inventory.observation.ExactInventoryQuantity
import io.flooow.integration.inventory.selection.CanonicalInventoryMeasure

@ConsistentCopyVisibility
data class AuthorizedCanonicalInventoryObservationEvidence internal constructor(
    val authority: CanonicalInventorySourceAuthorityAssessment,
    val observation: CanonicalInventoryObservation
) {
    init {
        val candidate = authority.candidate
        require(observation.organizationId == candidate.organizationId) {
            "Authorized observation organization mismatch"
        }
        require(observation.id == candidate.observationId) {
            "Authorized observation identity mismatch"
        }
        require(observation.sourcePointer == candidate.sourcePointer) {
            "Authorized observation source mismatch"
        }
        require(observation.projectionRevision == candidate.projectionRevision) {
            "Authorized observation revision mismatch"
        }
        require(
            observation.mappingDecisionId == candidate.mappingDecisionId &&
                observation.mappingRevision == candidate.mappingRevision
        ) { "Authorized observation mapping mismatch" }
        require(observation.target == candidate.target) {
            "Authorized observation target mismatch"
        }
        require(selectedQuantity(observation, candidate.measure) == candidate.exactQuantity) {
            "Authorized observation selected quantity mismatch"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface AuthorizedCanonicalInventoryObservationResult {
    data class Linked(val evidence: AuthorizedCanonicalInventoryObservationEvidence) :
        AuthorizedCanonicalInventoryObservationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object OrganizationMismatch : AuthorizedCanonicalInventoryObservationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object ObservationIdentityMismatch : AuthorizedCanonicalInventoryObservationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SourcePointerMismatch : AuthorizedCanonicalInventoryObservationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object ProjectionRevisionMismatch : AuthorizedCanonicalInventoryObservationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object MappingLineageMismatch : AuthorizedCanonicalInventoryObservationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object TargetMismatch : AuthorizedCanonicalInventoryObservationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SelectedMeasureUnavailable : AuthorizedCanonicalInventoryObservationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SelectedQuantityMismatch : AuthorizedCanonicalInventoryObservationResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object AuthorizedCanonicalInventoryObservationLinker {
    fun link(
        authority: CanonicalInventorySourceAuthorityAssessment,
        observation: CanonicalInventoryObservation
    ): AuthorizedCanonicalInventoryObservationResult {
        val candidate = authority.candidate
        if (observation.organizationId != candidate.organizationId) {
            return AuthorizedCanonicalInventoryObservationResult.OrganizationMismatch
        }
        if (observation.id != candidate.observationId) {
            return AuthorizedCanonicalInventoryObservationResult.ObservationIdentityMismatch
        }
        if (observation.sourcePointer != candidate.sourcePointer) {
            return AuthorizedCanonicalInventoryObservationResult.SourcePointerMismatch
        }
        if (observation.projectionRevision != candidate.projectionRevision) {
            return AuthorizedCanonicalInventoryObservationResult.ProjectionRevisionMismatch
        }
        if (
            observation.mappingDecisionId != candidate.mappingDecisionId ||
            observation.mappingRevision != candidate.mappingRevision
        ) {
            return AuthorizedCanonicalInventoryObservationResult.MappingLineageMismatch
        }
        if (observation.target != candidate.target) {
            return AuthorizedCanonicalInventoryObservationResult.TargetMismatch
        }
        val quantity = selectedQuantity(observation, candidate.measure)
            ?: return AuthorizedCanonicalInventoryObservationResult.SelectedMeasureUnavailable
        if (quantity != candidate.exactQuantity) {
            return AuthorizedCanonicalInventoryObservationResult.SelectedQuantityMismatch
        }
        return AuthorizedCanonicalInventoryObservationResult.Linked(
            AuthorizedCanonicalInventoryObservationEvidence(authority, observation)
        )
    }

    override fun toString(): String = "[REDACTED]"
}

private fun selectedQuantity(
    observation: CanonicalInventoryObservation,
    measure: CanonicalInventoryMeasure
): ExactInventoryQuantity? = when (measure) {
    CanonicalInventoryMeasure.AVAILABLE_TO_SELL -> observation.measures.availableToSell
    CanonicalInventoryMeasure.ON_HAND -> observation.measures.onHand
    CanonicalInventoryMeasure.RESERVED -> observation.measures.reserved
    CanonicalInventoryMeasure.PENDING_INBOUND -> observation.measures.pendingInbound
    CanonicalInventoryMeasure.PENDING_OUTBOUND -> observation.measures.pendingOutbound
}
