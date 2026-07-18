package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp

data class Hypothesis(
    val id: Identifier,
    val statement: String,
    val confidence: Confidence,
    val createdAt: Timestamp
) {
    init {
        require(statement.isNotBlank()) {
            "Hypothesis statement must not be blank"
        }
    }
}
