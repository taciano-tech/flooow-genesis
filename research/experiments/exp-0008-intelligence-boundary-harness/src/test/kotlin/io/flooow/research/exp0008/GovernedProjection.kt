package io.flooow.research.exp0008

import java.time.Instant

data class EvidenceReference(
    val reference: String,
    val observedAt: Instant,
    val source: String,
)

data class GovernedProjection(
    val organizationId: String,
    val projectionName: String,
    val asOf: Instant,
    val facts: Map<String, String>,
    val evidence: List<EvidenceReference>,
)
