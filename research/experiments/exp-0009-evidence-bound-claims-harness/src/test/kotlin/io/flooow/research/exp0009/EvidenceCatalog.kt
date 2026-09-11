package io.flooow.research.exp0009

import java.time.Instant

data class EvidenceFact(
    val reference: String,
    val factKey: String,
    val value: String,
    val observedAt: Instant,
    val source: String,
)

class EvidenceCatalog(
    facts: List<EvidenceFact>,
) {
    private val byReference: Map<String, EvidenceFact> =
        facts.associateBy { it.reference }

    init {
        require(byReference.size == facts.size) {
            "evidence references must be unique"
        }
    }

    fun find(reference: String): EvidenceFact? = byReference[reference]

    fun contains(reference: String): Boolean = reference in byReference

    fun references(): Set<String> = byReference.keys
}
