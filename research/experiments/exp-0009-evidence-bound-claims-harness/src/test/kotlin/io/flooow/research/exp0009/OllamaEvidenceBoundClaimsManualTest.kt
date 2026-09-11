package io.flooow.research.exp0009

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class OllamaEvidenceBoundClaimsManualTest {
    @Test
    fun `local model output is classified by deterministic claim validator`() {
        assumeTrue(
            System.getenv("FLOOOW_OLLAMA_TEST") == "true",
            "Set FLOOOW_OLLAMA_TEST=true to run the local Ollama claim test",
        )

        val evidence =
            EvidenceFact(
                reference = "lab:margin:1",
                factKey = "contribution_margin_pct",
                value = "18.42",
                observedAt = Instant.now(),
                source = "exp-0009-governed-sample",
            )
        val catalog = EvidenceCatalog(listOf(evidence))

        val claims =
            OllamaClaimDraftAdapter.local().draft(
                question =
                    "Given contribution margin 18.42, what is observed and what should we investigate?",
                catalog = catalog,
            )

        val report = EvidenceBoundClaimValidator(catalog).validate(claims)

        println()
        println("=== EXP-0009 MODEL CLAIM VALIDATION ===")
        report.claims.forEach { claim ->
            println("${claim.decision}: ${claim.draft.narrative}")
            println("  factKey=${claim.draft.factKey}")
            println("  refs=${claim.draft.evidenceReferences}")
            println("  reasons=${claim.reasons}")
        }
        println("=======================================")
        println()

        assertTrue(report.claims.isNotEmpty())
        assertTrue(report.claims.all { !it.canonicalTruth && !it.executable })
        assertFalse(
            report.claims.any {
                it.decision == ClaimDecision.EVIDENCE_BACKED &&
                    it.draft.factKey != "contribution_margin_pct"
            },
            "no invented fact may be promoted to EVIDENCE_BACKED",
        )
    }
}
