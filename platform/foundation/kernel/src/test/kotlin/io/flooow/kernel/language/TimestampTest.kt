package io.flooow.kernel.language

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimestampTest {

    @Test
    fun `creates timestamp using the supplied clock`() {
        val instant = Instant.parse("2026-07-18T12:00:00Z")
        val clock = Clock.fixed(instant, ZoneOffset.UTC)

        assertEquals(instant, Timestamp.now(clock).value)
    }

    @Test
    fun `parses an ISO instant`() {
        val timestamp = Timestamp.parse("2026-07-18T12:00:00Z")

        assertEquals(
            Instant.parse("2026-07-18T12:00:00Z"),
            timestamp.value
        )
    }

    @Test
    fun `supports chronological comparison`() {
        val earlier = Timestamp.parse("2026-07-18T12:00:00Z")
        val later = Timestamp.parse("2026-07-18T13:00:00Z")

        assertTrue(earlier < later)
    }
}
