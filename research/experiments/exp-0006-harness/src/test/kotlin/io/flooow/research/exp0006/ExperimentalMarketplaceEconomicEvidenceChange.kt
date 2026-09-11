package io.flooow.research.exp0006

import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion

enum class ExperimentalMarketplaceEconomicEvidenceChangeKind {
    FACT,
    ATTEMPT,
    CORRECTION
}

data class ExperimentalMarketplaceEconomicEvidenceChange(
    val subject: MarketplaceEconomicEvidenceSubject,
    val evidenceVersion: MarketplaceEconomicEvidenceVersion,
    val changeSequence: ExperimentalChangeSequenceCheckpoint,
    val changeKind: ExperimentalMarketplaceEconomicEvidenceChangeKind
) {
    override fun toString(): String = "[REDACTED]"
}
