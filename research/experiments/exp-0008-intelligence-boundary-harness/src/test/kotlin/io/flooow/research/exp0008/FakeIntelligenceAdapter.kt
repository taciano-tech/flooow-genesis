package io.flooow.research.exp0008

class FakeIntelligenceAdapter : IntelligencePort {
    override fun propose(context: IntelligenceContext): IntelligenceProposal =
        IntelligenceProposal(
            hypothesis = "Investigate whether the observed margin compression is persistent.",
            rationale =
                "The governed read returned ${context.governedToolResult.key}=" +
                    "${context.governedToolResult.value}; this is a proposal, not Economic Truth.",
            evidenceReferences =
                setOf(
                    context.governedToolResult.evidenceReference,
                    *context.projection.evidence.map { it.reference }.toTypedArray(),
                ),
            generatedBy = "fake-model",
        )
}
