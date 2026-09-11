package io.flooow.research.exp0006

import io.flooow.organization.OrganizationId

interface ExperimentalMarketplaceEconomicEvidenceChangeFeed {
    fun changesSince(
        organizationId: OrganizationId,
        checkpoint: ExperimentalChangeSequenceCheckpoint,
        limit: Int
    ): List<ExperimentalMarketplaceEconomicEvidenceChange>

    fun organizationsWithPendingChanges(
        projectionName: ExperimentalProjectionName,
        limit: Int
    ): List<OrganizationId>

    fun currentCheckpoint(
        organizationId: OrganizationId,
        projectionName: ExperimentalProjectionName
    ): ExperimentalChangeSequenceCheckpoint

    fun advanceCheckpoint(
        organizationId: OrganizationId,
        projectionName: ExperimentalProjectionName,
        expected: ExperimentalChangeSequenceCheckpoint,
        next: ExperimentalChangeSequenceCheckpoint
    ): ExperimentalCheckpointAdvanceResult
}
