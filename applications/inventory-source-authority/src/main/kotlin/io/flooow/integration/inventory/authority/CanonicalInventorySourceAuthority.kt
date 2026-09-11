package io.flooow.integration.inventory.authority

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.mapping.InventoryMappingTarget
import io.flooow.integration.inventory.selection.CanonicalInventoryMeasure
import io.flooow.integration.inventory.selection.SelectedCanonicalInventoryMeasure
import io.flooow.organization.OrganizationId
import java.text.Normalizer
import java.time.Instant

private const val INVENTORY_SOURCE_BALANCE_CAPABILITY = "inventory.source-balance.read"

class CanonicalInventorySourceAuthorityPolicyVersion private constructor(
    private val value: String
) {
    fun encodedForPersistence(): String = value

    override fun equals(other: Any?) =
        other is CanonicalInventorySourceAuthorityPolicyVersion && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "[REDACTED]"

    companion object {
        fun of(value: String): CanonicalInventorySourceAuthorityPolicyVersion {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            require(normalized.isNotEmpty() && normalized == normalized.trim()) {
                "Invalid source authority policy version"
            }
            require(normalized.none(Char::isISOControl)) {
                "Invalid source authority policy version"
            }
            require(normalized.toByteArray(Charsets.UTF_8).size <= 64) {
                "Invalid source authority policy version"
            }
            return CanonicalInventorySourceAuthorityPolicyVersion(normalized)
        }
    }
}

data class CanonicalInventorySourceAuthorityPolicy(
    val version: CanonicalInventorySourceAuthorityPolicyVersion,
    val organizationId: OrganizationId,
    val connectionId: IntegrationConnectionId,
    val capability: String = INVENTORY_SOURCE_BALANCE_CAPABILITY,
    val target: InventoryMappingTarget,
    val measure: CanonicalInventoryMeasure,
    val effectiveFrom: Instant,
    val effectiveUntil: Instant
) {
    init {
        require(capability == INVENTORY_SOURCE_BALANCE_CAPABILITY) {
            "Inventory source authority capability unavailable"
        }
        require(effectiveUntil > effectiveFrom) {
            "Invalid source authority policy interval"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

@ConsistentCopyVisibility
data class CanonicalInventorySourceAuthorityAssessment internal constructor(
    val candidate: SelectedCanonicalInventoryMeasure,
    val policy: CanonicalInventorySourceAuthorityPolicy,
    val evaluatedAt: Instant
) {
    init {
        require(candidate.organizationId == policy.organizationId) {
            "Source authority organization mismatch"
        }
        require(candidate.connectionId == policy.connectionId) {
            "Source authority connection mismatch"
        }
        require(
            candidate.capability == INVENTORY_SOURCE_BALANCE_CAPABILITY &&
                policy.capability == INVENTORY_SOURCE_BALANCE_CAPABILITY
        ) { "Source authority capability mismatch" }
        require(candidate.target == policy.target) {
            "Source authority target mismatch"
        }
        require(candidate.measure == policy.measure) {
            "Source authority measure mismatch"
        }
        require(evaluatedAt >= policy.effectiveFrom && evaluatedAt < policy.effectiveUntil) {
            "Source authority policy inactive"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface CanonicalInventorySourceAuthorityResult {
    data class Authorized(val assessment: CanonicalInventorySourceAuthorityAssessment) :
        CanonicalInventorySourceAuthorityResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object OrganizationMismatch : CanonicalInventorySourceAuthorityResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object ConnectionMismatch : CanonicalInventorySourceAuthorityResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object TargetMismatch : CanonicalInventorySourceAuthorityResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object MeasureMismatch : CanonicalInventorySourceAuthorityResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object PolicyNotYetEffective : CanonicalInventorySourceAuthorityResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object PolicyExpired : CanonicalInventorySourceAuthorityResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object CanonicalInventorySourceAuthorityAssessor {
    fun assess(
        candidate: SelectedCanonicalInventoryMeasure,
        policy: CanonicalInventorySourceAuthorityPolicy,
        evaluatedAt: Instant
    ): CanonicalInventorySourceAuthorityResult {
        if (candidate.organizationId != policy.organizationId) {
            return CanonicalInventorySourceAuthorityResult.OrganizationMismatch
        }
        if (candidate.connectionId != policy.connectionId) {
            return CanonicalInventorySourceAuthorityResult.ConnectionMismatch
        }
        check(
            candidate.capability == INVENTORY_SOURCE_BALANCE_CAPABILITY &&
                policy.capability == INVENTORY_SOURCE_BALANCE_CAPABILITY
        ) { "Inventory source authority capability invariant violated" }
        if (candidate.target != policy.target) {
            return CanonicalInventorySourceAuthorityResult.TargetMismatch
        }
        if (candidate.measure != policy.measure) {
            return CanonicalInventorySourceAuthorityResult.MeasureMismatch
        }
        if (evaluatedAt < policy.effectiveFrom) {
            return CanonicalInventorySourceAuthorityResult.PolicyNotYetEffective
        }
        if (evaluatedAt >= policy.effectiveUntil) {
            return CanonicalInventorySourceAuthorityResult.PolicyExpired
        }
        return CanonicalInventorySourceAuthorityResult.Authorized(
            CanonicalInventorySourceAuthorityAssessment(candidate, policy, evaluatedAt)
        )
    }

    override fun toString(): String = "[REDACTED]"
}
