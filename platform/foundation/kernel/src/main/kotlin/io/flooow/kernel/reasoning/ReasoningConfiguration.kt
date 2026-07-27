package io.flooow.kernel.reasoning

import java.time.Clock

/**
 * Defines the explicit dependencies used to compose a reasoning pipeline.
 *
 * Configuration remains independent from component discovery,
 * registries, plugins, and external configuration formats.
 */
data class ReasoningConfiguration(
    val confidencePolicy: ConfidencePolicy =
        AggregatedEvidenceConfidencePolicy(),
    val clock: Clock = Clock.systemUTC()
)
