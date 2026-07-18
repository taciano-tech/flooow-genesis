package io.flooow.kernel.reasoning

import io.flooow.kernel.model.Evidence

data class EvidenceSet(
    val evidences: Set<Evidence>
) {
    init {
        require(evidences.isNotEmpty()) {
            "EvidenceSet must contain at least one evidence"
        }
    }

    fun size(): Int = evidences.size

    fun isEmpty(): Boolean = evidences.isEmpty()
}
