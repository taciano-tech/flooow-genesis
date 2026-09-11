package io.flooow.marketplace.operations.economics.evidence

import io.flooow.organization.OrganizationId

class ChangeSequenceCheckpoint(private val value: Long) :
    Comparable<ChangeSequenceCheckpoint> {
    init {
        require(value >= 0) { "Change sequence checkpoint must not be negative" }
    }

    override fun compareTo(other: ChangeSequenceCheckpoint): Int =
        value.compareTo(other.value)

    fun valueForPersistence(): Long = value

    override fun equals(other: Any?): Boolean =
        other is ChangeSequenceCheckpoint && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "[INTERNAL]"

    companion object {
        val NONE: ChangeSequenceCheckpoint = ChangeSequenceCheckpoint(0)
        val ZERO: ChangeSequenceCheckpoint = ChangeSequenceCheckpoint(0)
    }
}

class ProjectionName(private val value: String) {
    init {
        require(value.isNotBlank()) { "Projection name must not be blank" }
        require(value.length in 1..100) { "Projection name length must be from 1 through 100" }
        require(VALID_VALUE.matches(value)) { "Projection name must use the canonical format" }
    }

    fun valueForPersistence(): String = value

    override fun equals(other: Any?): Boolean =
        other is ProjectionName && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "[INTERNAL]"

    companion object {
        private val VALID_VALUE: Regex = Regex("^[a-z0-9][a-z0-9-]*$")
    }
}

enum class MarketplaceEconomicEvidenceChangeKind {
    FACT,
    ATTEMPT,
    CORRECTION;

    override fun toString(): String = "[REDACTED]"
}

data class MarketplaceEconomicEvidenceChange(
    val subject: MarketplaceEconomicEvidenceSubject,
    val evidenceVersion: MarketplaceEconomicEvidenceVersion,
    val changeSequence: ChangeSequenceCheckpoint,
    val changeKind: MarketplaceEconomicEvidenceChangeKind
) {
    override fun toString(): String = "[REDACTED]"
}

sealed interface MarketplaceEconomicEvidenceChangeFeedResult<out T> {
    data class Success<T>(val value: T) : MarketplaceEconomicEvidenceChangeFeedResult<T> {
        override fun toString(): String = "[REDACTED]"
    }

    data object IntegrityFailure : MarketplaceEconomicEvidenceChangeFeedResult<Nothing> {
        override fun toString(): String = "[REDACTED]"
    }
}

sealed interface CheckpointAdvanceResult {
    data class Advanced(
        val checkpoint: ChangeSequenceCheckpoint
    ) : CheckpointAdvanceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class Stale(
        val currentCheckpoint: ChangeSequenceCheckpoint
    ) : CheckpointAdvanceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object Regression : CheckpointAdvanceResult {
        override fun toString(): String = "[REDACTED]"
    }
}

interface MarketplaceEconomicEvidenceChangeFeed {
    fun changesSince(
        organizationId: OrganizationId,
        checkpoint: ChangeSequenceCheckpoint,
        limit: Int
    ): MarketplaceEconomicEvidenceChangeFeedResult<List<MarketplaceEconomicEvidenceChange>>

    fun organizationsWithPendingChanges(
        projectionName: ProjectionName,
        limit: Int
    ): MarketplaceEconomicEvidenceChangeFeedResult<List<OrganizationId>>

    fun currentCheckpoint(
        organizationId: OrganizationId,
        projectionName: ProjectionName
    ): MarketplaceEconomicEvidenceChangeFeedResult<ChangeSequenceCheckpoint>

    fun advanceCheckpoint(
        organizationId: OrganizationId,
        projectionName: ProjectionName,
        expected: ChangeSequenceCheckpoint,
        next: ChangeSequenceCheckpoint
    ): MarketplaceEconomicEvidenceChangeFeedResult<CheckpointAdvanceResult>

    companion object {
        const val MIN_LIMIT: Int = 1
        const val MAX_LIMIT: Int = 1_000

        fun requireValidLimit(limit: Int): Int {
            require(limit in MIN_LIMIT..MAX_LIMIT) {
                "Marketplace economic evidence change feed limit must be from 1 through 1000"
            }
            return limit
        }
    }
}
