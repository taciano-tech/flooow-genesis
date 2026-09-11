package io.flooow.research.exp0008

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GovernedIntelligenceOrchestratorTest {
    @Test
    fun `proposal is downstream of governed evidence and cannot become canonical truth`() {
        val reads = AtomicInteger(0)
        val tool =
            ReadOnlyFlooowTool { request ->
                reads.incrementAndGet()
                ToolReadResult(
                    key = request.key,
                    value = "18.42",
                    evidenceReference = "ledger:order-margin:123",
                    freshness = "as-of-2026-09-07T12:00:00Z",
                )
            }

        val orchestrator =
            GovernedIntelligenceOrchestrator(
                tool = tool,
                intelligence = FakeIntelligenceAdapter(),
            )

        val proposal =
            orchestrator.propose(
                question =
                    IntelligenceQuestion(
                        organizationId = "org-001",
                        question = "Should we investigate margin compression?",
                        requestedFactKey = "contribution_margin_pct",
                    ),
                projection = sampleProjection(),
            )

        assertEquals(1, reads.get())
        assertFalse(proposal.canonicalTruth)
        assertFalse(proposal.executable)
        assertTrue("ledger:order-margin:123" in proposal.evidenceReferences)
        assertTrue("projection:sales-intelligence:456" in proposal.evidenceReferences)
    }

    @Test
    fun `organization boundary mismatch is rejected before intelligence runs`() {
        var intelligenceCalled = false
        val orchestrator =
            GovernedIntelligenceOrchestrator(
                tool = ReadOnlyFlooowTool { error("tool must not be called") },
                intelligence = IntelligencePort { intelligenceCalled = true; error("intelligence must not be called") },
            )

        assertFailsWith<IllegalArgumentException> {
            orchestrator.propose(
                IntelligenceQuestion("org-other", "Analyze", "revenue"),
                sampleProjection(),
            )
        }
        assertFalse(intelligenceCalled)
    }

    @Test
    fun `adapter attempting to promote model output to truth is rejected`() {
        val orchestrator =
            GovernedIntelligenceOrchestrator(
                tool = ReadOnlyFlooowTool { ToolReadResult(it.key, "100", "evidence:1", "fresh") },
                intelligence = IntelligencePort {
                    IntelligenceProposal(
                        hypothesis = "invalid promotion",
                        rationale = "model tried to become truth",
                        evidenceReferences = setOf("evidence:1"),
                        generatedBy = "unsafe-test-adapter",
                        canonicalTruth = true,
                    )
                },
            )
        val error = assertFailsWith<IllegalStateException> {
            orchestrator.propose(IntelligenceQuestion("org-001", "Analyze", "revenue"), sampleProjection())
        }
        assertTrue(error.message!!.contains("cannot become canonical truth"))
    }

    @Test
    fun `adapter attempting autonomous execution is rejected`() {
        val orchestrator =
            GovernedIntelligenceOrchestrator(
                tool = ReadOnlyFlooowTool { ToolReadResult(it.key, "100", "evidence:2", "fresh") },
                intelligence = IntelligencePort {
                    IntelligenceProposal(
                        hypothesis = "unsafe execution",
                        rationale = "model attempted autonomous execution",
                        evidenceReferences = setOf("evidence:2"),
                        generatedBy = "unsafe-test-adapter",
                        executable = true,
                    )
                },
            )
        val error = assertFailsWith<IllegalStateException> {
            orchestrator.propose(IntelligenceQuestion("org-001", "Analyze", "revenue"), sampleProjection())
        }
        assertTrue(error.message!!.contains("cannot execute"))
    }

    private fun sampleProjection() =
        GovernedProjection(
            organizationId = "org-001",
            projectionName = "sales-intelligence",
            asOf = Instant.parse("2026-09-07T12:00:00Z"),
            facts = mapOf("revenue" to "12500.00", "contribution_margin_pct" to "18.42"),
            evidence = listOf(EvidenceReference("projection:sales-intelligence:456", Instant.parse("2026-09-07T11:59:00Z"), "flooow-governed-projection")),
        )
}
