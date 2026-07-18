package io.flooow.kernel.language

/**
 * Normalized degree of confidence in a statement or body of evidence.
 *
 * The value is always finite and constrained to the inclusive range
 * from zero to one.
 */
@JvmInline
value class Confidence(
    val value: Double
) : Comparable<Confidence> {
    init {
        require(value.isFinite()) {
            "Confidence must be finite"
        }

        require(value in MINIMUM_VALUE..MAXIMUM_VALUE) {
            "Confidence must be between 0.0 and 1.0"
        }
    }

    override fun compareTo(other: Confidence): Int =
        value.compareTo(other.value)

    companion object {
        const val MINIMUM_VALUE: Double = 0.0
        const val MAXIMUM_VALUE: Double = 1.0

        val NONE: Confidence = Confidence(MINIMUM_VALUE)
        val CERTAIN: Confidence = Confidence(MAXIMUM_VALUE)
    }
}
