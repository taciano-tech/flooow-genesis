package io.flooow.kernel.model

import io.flooow.kernel.language.Concept
import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp

/**
 * Information derived from one or more observations that can support
 * or weaken a hypothesis.
 */
data class Evidence(
    val id: Identifier,
    val observationIds: Set<Identifier>,
    val confidence: Confidence,
    val recordedAt: Timestamp
) : Concept {
    init {
        require(observationIds.isNotEmpty()) {
            "Evidence must reference at least one observation"
        }
    }
}
