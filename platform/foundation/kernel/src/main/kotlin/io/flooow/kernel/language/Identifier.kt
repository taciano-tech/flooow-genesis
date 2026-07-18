package io.flooow.kernel.language

/**
 * Stable identity assigned to a domain concept.
 *
 * Identifiers cannot be blank, contain surrounding whitespace,
 * or exceed the supported storage boundary.
 */
@JvmInline
value class Identifier(
    val value: String
) {
    init {
        require(value.isNotBlank()) {
            "Identifier must not be blank"
        }

        require(value == value.trim()) {
            "Identifier must not contain surrounding whitespace"
        }

        require(value.length <= MAX_LENGTH) {
            "Identifier must not exceed $MAX_LENGTH characters"
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH: Int = 128
    }
}
