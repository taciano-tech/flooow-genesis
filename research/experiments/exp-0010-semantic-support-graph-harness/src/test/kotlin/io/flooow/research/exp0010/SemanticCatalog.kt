package io.flooow.research.exp0010

class SemanticEvidenceCatalog(
    facts: List<EvidenceFact>,
) {
    private val byReference = facts.associateBy { it.reference }

    init {
        require(byReference.size == facts.size) {
            "evidence references must be unique"
        }
    }

    fun contains(reference: String): Boolean = byReference.containsKey(reference)

    fun find(reference: String): EvidenceFact? = byReference[reference]
}
