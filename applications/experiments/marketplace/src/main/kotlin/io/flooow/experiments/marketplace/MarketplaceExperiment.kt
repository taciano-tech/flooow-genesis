package io.flooow.experiments.marketplace

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import io.flooow.kernel.model.Observation

object MarketplaceExperiment {

    val orderCreatedObservation: Observation =
        Observation(
            id = Identifier("observation-marketplace-order-created"),
            description = "A customer created a marketplace order.",
            observedAt = Timestamp.parse("2026-07-29T18:00:00Z")
        )

    val orderCreatedEvidence: Evidence =
        Evidence(
            id = Identifier("evidence-marketplace-order-created"),
            observationIds = setOf(orderCreatedObservation.id),
            confidence = Confidence.CERTAIN,
            recordedAt = Timestamp.parse("2026-07-29T18:00:01Z")
        )
}
