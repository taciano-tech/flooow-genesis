package io.flooow.research.exp0006

sealed interface ExperimentalCheckpointAdvanceResult {
    data class Advanced(
        val checkpoint: ExperimentalChangeSequenceCheckpoint
    ) : ExperimentalCheckpointAdvanceResult

    data class Stale(
        val current: ExperimentalChangeSequenceCheckpoint
    ) : ExperimentalCheckpointAdvanceResult

    data object Regression : ExperimentalCheckpointAdvanceResult
}
