package io.flooow.marketplace.operations.economics.evidence

import java.util.UUID

class MarketplaceEconomicEvidenceVersion(private val value: Long) :
    Comparable<MarketplaceEconomicEvidenceVersion> {
    init {
        require(value >= 0) { "Economic evidence version must not be negative" }
    }

    override fun compareTo(other: MarketplaceEconomicEvidenceVersion): Int =
        value.compareTo(other.value)

    fun next(): MarketplaceEconomicEvidenceVersion {
        check(value != Long.MAX_VALUE) { "Economic evidence version overflow" }
        return MarketplaceEconomicEvidenceVersion(value + 1)
    }

    fun valueForPersistence(): Long = value

    override fun equals(other: Any?): Boolean =
        other is MarketplaceEconomicEvidenceVersion && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "[INTERNAL]"

    companion object {
        val ZERO: MarketplaceEconomicEvidenceVersion = MarketplaceEconomicEvidenceVersion(0)
    }
}

data class VersionedMarketplaceIndependentEconomicEvidence(
    val evidence: MarketplaceIndependentEconomicEvidence,
    val version: MarketplaceEconomicEvidenceVersion
) {
    override fun toString(): String = "[REDACTED]"
}

sealed interface MarketplaceIndependentEconomicEvidenceReadResult {
    data class Found(
        val versionedEvidence: VersionedMarketplaceIndependentEconomicEvidence
    ) : MarketplaceIndependentEconomicEvidenceReadResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object NotFound : MarketplaceIndependentEconomicEvidenceReadResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object IntegrityFailure : MarketplaceIndependentEconomicEvidenceReadResult {
        override fun toString(): String = "[REDACTED]"
    }
}

sealed interface MarketplaceIndependentEconomicEvidencePersistResult {
    data class Applied(
        val versionedEvidence: VersionedMarketplaceIndependentEconomicEvidence
    ) : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class Duplicate(
        val versionedEvidence: VersionedMarketplaceIndependentEconomicEvidence
    ) : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class StaleVersion(
        val currentVersion: MarketplaceEconomicEvidenceVersion
    ) : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object OrganizationUnavailable : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SubjectMismatch : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object IdentifierConflict : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SourceFactConflict : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SupersededFactNotFound : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SupersededTargetNotFact : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object FactAlreadySuperseded : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object ReplacementIdentifierConflict : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object ReplacementSourceFactConflict : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object IntegrityFailure : MarketplaceIndependentEconomicEvidencePersistResult {
        override fun toString(): String = "[REDACTED]"
    }
}

interface MarketplaceIndependentEconomicEvidenceRepository {
    fun find(
        subject: MarketplaceEconomicEvidenceSubject
    ): MarketplaceIndependentEconomicEvidenceReadResult

    fun apply(
        expectedVersion: MarketplaceEconomicEvidenceVersion,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ): MarketplaceIndependentEconomicEvidencePersistResult
}

fun MarketplaceEconomicEvidenceObservationId.valueForPersistence(): UUID = value
