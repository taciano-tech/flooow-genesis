package io.flooow.kernel.model

import io.flooow.kernel.language.Concept
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp

/**
 * A recorded act of perceiving a portion of organizational reality.
 *
 * An observation records what was perceived without asserting that the
 * perception is already an established fact.
 */
data class Observation(
    val id: Identifier,
    val description: String,
    val observedAt: Timestamp
) : Concept {
    init {
        require(description.isNotBlank()) {
            "Observation description must not be blank"
        }

        require(description == description.trim()) {
            "Observation description must not contain surrounding whitespace"
        }
    }
}
