package io.flooow.kernel.reasoning

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class ReasoningConfigurationTest {

    @Test
    fun `uses default reasoning dependencies`() {
        val configuration =
            ReasoningConfiguration()

        assertIs<AggregatedEvidenceConfidencePolicy>(
            configuration.confidencePolicy
        )

        assertEquals(
            ZoneOffset.UTC,
            configuration.clock.zone
        )
    }

    @Test
    fun `preserves supplied reasoning dependencies`() {
        val confidencePolicy =
            WeightedConfidencePolicy(
                hypothesisWeight = 0.25,
                aggregatedEvidenceWeight = 0.75
            )

        val clock =
            Clock.fixed(
                Instant.parse("2026-07-20T12:00:00Z"),
                ZoneOffset.UTC
            )

        val configuration =
            ReasoningConfiguration(
                confidencePolicy = confidencePolicy,
                clock = clock
            )

        assertSame(
            confidencePolicy,
            configuration.confidencePolicy
        )

        assertSame(
            clock,
            configuration.clock
        )
    }

    @Test
    fun `copies configuration with selected dependency replaced`() {
        val confidencePolicy =
            WeightedConfidencePolicy(
                hypothesisWeight = 0.40,
                aggregatedEvidenceWeight = 0.60
            )

        val originalClock =
            Clock.fixed(
                Instant.parse("2026-07-20T12:00:00Z"),
                ZoneOffset.UTC
            )

        val replacementClock =
            Clock.fixed(
                Instant.parse("2026-07-21T12:00:00Z"),
                ZoneOffset.UTC
            )

        val original =
            ReasoningConfiguration(
                confidencePolicy = confidencePolicy,
                clock = originalClock
            )

        val copied =
            original.copy(
                clock = replacementClock
            )

        assertSame(
            confidencePolicy,
            copied.confidencePolicy
        )

        assertSame(
            replacementClock,
            copied.clock
        )

        assertSame(
            originalClock,
            original.clock
        )
    }
}
