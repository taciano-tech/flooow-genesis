package io.flooow.integration.inventory.comparison

import io.flooow.integration.inventory.observation.ExactInventoryQuantity
import io.flooow.integration.inventory.selection.CanonicalInventoryMeasure
import io.flooow.integration.inventory.snapshot.CanonicalInventoryCandidateLineageOrder
import io.flooow.integration.inventory.snapshot.CanonicalInventoryCandidateSnapshotId
import io.flooow.integration.inventory.snapshot.CanonicalInventoryCandidateSnapshotView

sealed interface CanonicalInventoryCandidateComparisonResult {
    class SingleCandidate(
        val snapshotId: CanonicalInventoryCandidateSnapshotId,
        val measure: CanonicalInventoryMeasure,
        val exactQuantity: ExactInventoryQuantity
    ) : CanonicalInventoryCandidateComparisonResult {
        override fun toString() = "SingleCandidate([REDACTED])"
    }

    class MeasureMismatch(
        val snapshotId: CanonicalInventoryCandidateSnapshotId,
        val memberCount: Int,
        val distinctMeasureCount: Int
    ) : CanonicalInventoryCandidateComparisonResult {
        override fun toString() =
            "MeasureMismatch(memberCount=$memberCount, distinctMeasureCount=$distinctMeasureCount)"
    }

    class ExactAgreement(
        val snapshotId: CanonicalInventoryCandidateSnapshotId,
        val memberCount: Int,
        val measure: CanonicalInventoryMeasure,
        val exactQuantity: ExactInventoryQuantity
    ) : CanonicalInventoryCandidateComparisonResult {
        override fun toString() = "ExactAgreement(memberCount=$memberCount)"
    }

    class ExactDivergence(
        val snapshotId: CanonicalInventoryCandidateSnapshotId,
        val memberCount: Int,
        val measure: CanonicalInventoryMeasure,
        val distinctQuantityCount: Int
    ) : CanonicalInventoryCandidateComparisonResult {
        override fun toString() =
            "ExactDivergence(memberCount=$memberCount, distinctQuantityCount=$distinctQuantityCount)"
    }

    data object IntegrityFailure : CanonicalInventoryCandidateComparisonResult
}

object CanonicalInventoryCandidateComparator {
    fun compare(
        view: CanonicalInventoryCandidateSnapshotView
    ): CanonicalInventoryCandidateComparisonResult {
        val snapshot = view.snapshot
        val members = view.members
        if (snapshot.memberCount <= 0 || snapshot.memberCount != members.size ||
            members.any {
                it.organizationId != snapshot.organizationId ||
                    it.snapshotId != snapshot.id || it.target != snapshot.target
            } || members.map { it.lineageRootDecisionId }.toSet().size != members.size ||
            members.map { it.lineageRootDecisionId } !=
                members.map { it.lineageRootDecisionId }
                    .sortedWith(CanonicalInventoryCandidateLineageOrder)
        ) return CanonicalInventoryCandidateComparisonResult.IntegrityFailure

        val first = members.firstOrNull()
            ?: return CanonicalInventoryCandidateComparisonResult.IntegrityFailure
        if (members.size == 1) return CanonicalInventoryCandidateComparisonResult.SingleCandidate(
            snapshot.id, first.measure, first.exactQuantity
        )

        val measures = members.map { it.measure }.toSet()
        if (measures.size != 1) return CanonicalInventoryCandidateComparisonResult.MeasureMismatch(
            snapshot.id, members.size, measures.size
        )

        val quantities = members.map { it.exactQuantity }.toSet()
        return if (quantities.size == 1) {
            CanonicalInventoryCandidateComparisonResult.ExactAgreement(
                snapshot.id, members.size, first.measure, first.exactQuantity
            )
        } else {
            CanonicalInventoryCandidateComparisonResult.ExactDivergence(
                snapshot.id, members.size, first.measure, quantities.size
            )
        }
    }
}
