package io.flooow.kernel.language

import java.time.Instant

@JvmInline
value class Timestamp(
    val value: Instant
)
