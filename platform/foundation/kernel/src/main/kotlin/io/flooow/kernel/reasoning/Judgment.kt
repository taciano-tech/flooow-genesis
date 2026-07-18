package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp

data class Judgment(
    val id: Identifier,
    val hypothesisId: Identifier,
    val conclusion: String,
    val confidence: Confidence,
    val createdAt: Timestamp
) : io.flooow.kernel.model.Judgment {
    init {
        require(conclusion.isNotBlank()) {
            "Judgment conclusion must not be blank"
        }
    }
}
