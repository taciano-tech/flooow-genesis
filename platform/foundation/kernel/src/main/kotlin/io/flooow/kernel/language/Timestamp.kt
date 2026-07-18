package io.flooow.kernel.language

import java.time.Clock
import java.time.Instant

/**
 * A precise point on the organizational timeline.
 */
@JvmInline
value class Timestamp(
    val value: Instant
) : Comparable<Timestamp> {

    override fun compareTo(other: Timestamp): Int =
        value.compareTo(other.value)

    override fun toString(): String =
        value.toString()

    companion object {
        fun now(clock: Clock = Clock.systemUTC()): Timestamp =
            Timestamp(clock.instant())

        fun parse(value: String): Timestamp =
            Timestamp(Instant.parse(value))
    }
}
