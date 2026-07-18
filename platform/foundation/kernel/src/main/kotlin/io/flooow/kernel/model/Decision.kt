package io.flooow.kernel.model

import io.flooow.kernel.language.Concept
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp

/**
 * A selected organizational course of action grounded in available evidence.
 */
data class Decision(
    val id: Identifier,
    val statement: String,
    val evidenceIds: Set<Identifier>,
    val decidedAt: Timestamp
) : Concept {
    init {
        require(statement.isNotBlank()) {
            "Decision statement must not be blank"
        }

        require(statement == statement.trim()) {
            "Decision statement must not contain surrounding whitespace"
        }

        require(evidenceIds.isNotEmpty()) {
            "Decision must reference at least one item of evidence"
        }
    }
}
