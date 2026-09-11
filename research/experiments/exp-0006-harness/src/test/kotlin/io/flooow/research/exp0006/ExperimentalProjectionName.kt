package io.flooow.research.exp0006

class ExperimentalProjectionName(private val value: String) {
    init {
        require(value.isNotBlank()) { "Projection name must not be blank" }
        require(value.length <= 100) { "Projection name exceeds maximum length" }
        require(PATTERN.matches(value)) { "Invalid projection name" }
    }

    fun valueForPersistence(): String = value

    override fun equals(other: Any?): Boolean =
        other is ExperimentalProjectionName && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "[REDACTED]"

    private companion object {
        val PATTERN = Regex("^[a-z0-9][a-z0-9-]*$")
    }
}
