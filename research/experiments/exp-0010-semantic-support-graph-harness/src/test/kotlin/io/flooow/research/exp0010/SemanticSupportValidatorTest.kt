package io.flooow.research.exp0010

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemanticSupportValidatorTest {
    private val marginEvidence =
        EvidenceFact(
            reference = "ledger:contribution-margin:123",
            proposition =
                Proposition(
                    factKey = "contribution_margin_pct",
                    relation = Relation.EQUALS,
                    value = "18.42",
                ),
        )

    private val catalog = SemanticEvidenceCatalog(listOf(marginEvidence))
    private val validator =
        SemanticSupportValidator(
            catalog = catalog,
            rules = SemanticRuleRegistry.experimentalDefaults(),
        )

    @Test
    fun `direct fact match is supported but never canonical or executable`() {
        val result =
            validator.validate(
                claim(
                    id = "claim-1",
                    proposition = marginEvidence.proposition,
                    support = SupportDeclaration.Direct(setOf(marginEvidence.reference)),
                ),
            )

        assertEquals(SupportDecision.DIRECTLY_SUPPORTED, result.decision)
        assertFalse(result.canonicalTruth)
        assertFalse(result.executable)
        assertEquals(setOf(marginEvidence.reference), result.graph.evidenceReferences)
        assertEquals(1, result.graph.edges.size)
    }

    @Test
    fun `valid reference cannot support unrelated labor cost claim`() {
        val result =
            validator.validate(
                claim(
                    id = "claim-2",
                    proposition =
                        Proposition(
                            factKey = "labor_cost_pct",
                            relation = Relation.EQUALS,
                            value = "25.00",
                        ),
                    support = SupportDeclaration.Direct(setOf(marginEvidence.reference)),
                ),
            )

        assertEquals(SupportDecision.UNSUPPORTED, result.decision)
        assertTrue(result.reasons.single().contains("does not semantically match"))
    }

    @Test
    fun `registered rule derives margin below threshold from matching evidence`() {
        val result =
            validator.validate(
                claim(
                    id = "claim-3",
                    proposition =
                        Proposition(
                            factKey = "contribution_margin_below_20",
                            relation = Relation.EQUALS,
                            value = "true",
                        ),
                    support =
                        SupportDeclaration.Derived(
                            ruleId = "margin_below_20",
                            evidenceReferences = setOf(marginEvidence.reference),
                        ),
                ),
            )

        assertEquals(SupportDecision.DERIVED_SUPPORTED, result.decision)
        assertEquals("margin_below_20", result.graph.ruleId)
        assertEquals(1, result.graph.edges.size)
    }

    @Test
    fun `registered rule cannot derive unrelated conclusion from valid evidence`() {
        val result =
            validator.validate(
                claim(
                    id = "claim-4",
                    proposition =
                        Proposition(
                            factKey = "labor_cost_pressure",
                            relation = Relation.EQUALS,
                            value = "true",
                        ),
                    support =
                        SupportDeclaration.Derived(
                            ruleId = "margin_below_20",
                            evidenceReferences = setOf(marginEvidence.reference),
                        ),
                ),
            )

        assertEquals(SupportDecision.UNSUPPORTED, result.decision)
    }

    @Test
    fun `unknown evidence fails deterministically`() {
        val error =
            assertFailsWith<UnknownEvidenceReferenceException> {
                validator.validate(
                    claim(
                        id = "claim-5",
                        proposition = marginEvidence.proposition,
                        support = SupportDeclaration.Direct(setOf("invented:evidence:999")),
                    ),
                )
            }

        assertEquals(setOf("invented:evidence:999"), error.references)
    }

    @Test
    fun `unknown semantic rule fails deterministically`() {
        val error =
            assertFailsWith<UnknownSemanticRuleException> {
                validator.validate(
                    claim(
                        id = "claim-6",
                        proposition =
                            Proposition(
                                factKey = "margin_pressure",
                                relation = Relation.EQUALS,
                                value = "true",
                            ),
                        support =
                            SupportDeclaration.Derived(
                                ruleId = "model_invented_rule",
                                evidenceReferences = setOf(marginEvidence.reference),
                            ),
                    ),
                )
            }

        assertEquals("model_invented_rule", error.ruleId)
    }

    @Test
    fun `narrative wording has zero authority over support decision`() {
        val proposition =
            Proposition(
                factKey = "labor_cost_pct",
                relation = Relation.EQUALS,
                value = "25.00",
            )

        val first =
            validator.validate(
                claim(
                    id = "claim-7a",
                    proposition = proposition,
                    support = SupportDeclaration.Direct(setOf(marginEvidence.reference)),
                    narrative = "Labor cost is certainly 25 percent.",
                ),
            )

        val second =
            validator.validate(
                claim(
                    id = "claim-7b",
                    proposition = proposition,
                    support = SupportDeclaration.Direct(setOf(marginEvidence.reference)),
                    narrative = "The evidence strongly proves this claim beyond doubt.",
                ),
            )

        assertEquals(SupportDecision.UNSUPPORTED, first.decision)
        assertEquals(first.decision, second.decision)
    }

    @Test
    fun `hypothesis remains explicitly hypothetical`() {
        val result =
            validator.validate(
                claim(
                    id = "claim-8",
                    proposition =
                        Proposition(
                            factKey = "labor_cost_relationship",
                            relation = Relation.EQUALS,
                            value = "investigate",
                        ),
                    support = SupportDeclaration.Hypothetical,
                ),
            )

        assertEquals(SupportDecision.HYPOTHETICAL, result.decision)
        assertFalse(result.canonicalTruth)
        assertFalse(result.executable)
    }

    private fun claim(
        id: String,
        proposition: Proposition,
        support: SupportDeclaration,
        narrative: String = "model-authored prose",
    ): DraftSemanticClaim =
        DraftSemanticClaim(
            id = id,
            proposition = proposition,
            support = support,
            confidence = 0.8,
            narrative = narrative,
        )
}
