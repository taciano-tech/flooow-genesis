package io.flooow.research.exp0006

class ExperimentalChangeSequenceCheckpoint(private val value: Long) :
    Comparable<ExperimentalChangeSequenceCheckpoint> {
    init {
        require(value >= 0) { "Change sequence checkpoint must not be negative" }
    }

    override fun compareTo(other: ExperimentalChangeSequenceCheckpoint): Int =
        value.compareTo(other.value)

    fun valueForPersistence(): Long = value

    override fun equals(other: Any?): Boolean =
        other is ExperimentalChangeSequenceCheckpoint && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "[INTERNAL]"

    companion object {
        val NONE = ExperimentalChangeSequenceCheckpoint(0)
    }
}
