package io.flooow.research.exp0008

data class IntelligenceQuestion(
    val organizationId: String,
    val question: String,
    val requestedFactKey: String,
)

data class IntelligenceContext(
    val question: IntelligenceQuestion,
    val projection: GovernedProjection,
    val governedToolResult: ToolReadResult,
)

data class IntelligenceProposal(
    val hypothesis: String,
    val rationale: String,
    val evidenceReferences: Set<String>,
    val generatedBy: String,
    val canonicalTruth: Boolean = false,
    val executable: Boolean = false,
)

fun interface IntelligencePort {
    fun propose(context: IntelligenceContext): IntelligenceProposal
}
