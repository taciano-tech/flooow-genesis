package io.flooow.research.exp0009

fun interface ClaimDraftPort {
    fun draft(
        question: String,
        catalog: EvidenceCatalog,
    ): List<DraftClaim>
}
