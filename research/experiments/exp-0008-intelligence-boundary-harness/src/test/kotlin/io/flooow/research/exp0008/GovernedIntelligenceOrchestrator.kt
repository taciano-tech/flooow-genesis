package io.flooow.research.exp0008

class GovernedIntelligenceOrchestrator(
    private val tool: ReadOnlyFlooowTool,
    private val intelligence: IntelligencePort,
) {
    fun propose(
        question: IntelligenceQuestion,
        projection: GovernedProjection,
    ): IntelligenceProposal {
        require(question.organizationId == projection.organizationId) {
            "organization boundary mismatch"
        }

        val toolResult =
            tool.read(
                ToolReadRequest(
                    organizationId = question.organizationId,
                    key = question.requestedFactKey,
                ),
            )

        val proposal =
            intelligence.propose(
                IntelligenceContext(
                    question = question,
                    projection = projection,
                    governedToolResult = toolResult,
                ),
            )

        check(!proposal.canonicalTruth) {
            "intelligence output cannot become canonical truth"
        }
        check(!proposal.executable) {
            "intelligence output cannot execute by itself"
        }

        return proposal
    }
}
