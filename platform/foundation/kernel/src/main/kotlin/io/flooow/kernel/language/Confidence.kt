package io.flooow.kernel.language

@JvmInline
value class Confidence(
    val value: Double
) {
    init {
        require(value in 0.0..1.0) {
            "Confidence must be between 0.0 and 1.0"
        }
    }
}
