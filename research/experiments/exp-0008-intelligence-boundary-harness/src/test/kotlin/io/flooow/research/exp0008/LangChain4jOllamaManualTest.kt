package io.flooow.research.exp0008

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class LangChain4jOllamaManualTest {
    @Test
    fun `local ollama can produce a governed non canonical proposal`() {
        assumeTrue(
            System.getenv("FLOOOW_OLLAMA_TEST") == "true",
            "Set FLOOOW_OLLAMA_TEST=true to run the local Ollama integration test",
        )

        val projection =
            GovernedProjection(
                organizationId = "org-local-lab",
                projectionName = "economic-observation-sample",
                asOf = Instant.now(),
                facts = mapOf("contribution_margin_pct" to "18.42"),
                evidence = listOf(EvidenceReference("lab:evidence:projection-1", Instant.now(), "exp-0008")),
            )

        var toolReads = 0
        val tool = ReadOnlyFlooowTool {
            toolReads += 1
            ToolReadResult(
                key = it.key,
                value = projection.facts[it.key] ?: "missing",
                evidenceReference = "lab:evidence:tool-1",
                freshness = "current-lab-sample",
            )
        }

        val proposal =
            GovernedIntelligenceOrchestrator(
                tool = tool,
                intelligence = LangChain4jOllamaIntelligenceAdapter.local(),
            ).propose(
                IntelligenceQuestion(
                    organizationId = projection.organizationId,
                    question = "What hypothesis should we investigate about this contribution margin observation?",
                    requestedFactKey = "contribution_margin_pct",
                ),
                projection,
            )

        println("\n=== EXP-0008 LOCAL OLLAMA PROPOSAL ===")
        println(proposal.rationale)
        println("======================================\n")

        assertTrue(proposal.rationale.isNotBlank())
        assertTrue(toolReads == 1)
        assertFalse(proposal.canonicalTruth)
        assertFalse(proposal.executable)
    }
}
