package io.flooow.research.exp0011

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiHopSupportValidatorTest {
    private val validator = MultiHopSupportValidator(
        evidence = listOf(EvidenceFact("ledger:margin:123", Proposition("contribution_margin_pct", "18.42"))),
        rules = MultiHopRuleRegistry.experimentalDefaults(),
    )

    @Test
    fun `two hop chain succeeds`() {
        val graph = validator.validate(listOf(
            DerivedNode("claim:below20", Proposition("contribution_margin_below_20", "true"),
                "margin_below_20", setOf("ledger:margin:123"), "first hop"),
            DerivedNode("claim:attention", Proposition("margin_attention_required", "true"),
                "margin_attention_required", setOf("claim:below20"), "second hop"),
        ))
        assertEquals(NodeDecision.DERIVED, graph.requireNode("claim:below20").decision)
        assertEquals(NodeDecision.DERIVED, graph.requireNode("claim:attention").decision)
        assertEquals(2, graph.edges.size)
        assertFalse(graph.requireNode("claim:attention").canonicalTruth)
        assertFalse(graph.requireNode("claim:attention").executable)
    }

    @Test
    fun `unsupported intermediate poisons downstream`() {
        val graph = validator.validate(listOf(
            DerivedNode("claim:wrong", Proposition("contribution_margin_below_20", "false"),
                "margin_below_20", setOf("ledger:margin:123"), "wrong"),
            DerivedNode("claim:attention", Proposition("margin_attention_required", "true"),
                "margin_attention_required", setOf("claim:wrong"), "downstream"),
        ))
        assertEquals(NodeDecision.UNSUPPORTED, graph.requireNode("claim:wrong").decision)
        assertEquals(NodeDecision.UNSUPPORTED, graph.requireNode("claim:attention").decision)
    }

    @Test
    fun `missing premise fails`() {
        val error = assertFailsWith<MissingPremiseException> {
            validator.validate(listOf(
                DerivedNode("claim:x", Proposition("margin_attention_required", "true"),
                    "margin_attention_required", setOf("invented:premise"), "missing")
            ))
        }
        assertEquals("invented:premise", error.premiseId)
    }

    @Test
    fun `unknown rule fails`() {
        val error = assertFailsWith<UnknownRuleException> {
            validator.validate(listOf(
                DerivedNode("claim:x", Proposition("something", "true"),
                    "model_invented_rule", setOf("ledger:margin:123"), "unknown")
            ))
        }
        assertEquals("model_invented_rule", error.ruleId)
    }

    @Test
    fun `cycle fails`() {
        val error = assertFailsWith<SupportCycleException> {
            validator.validate(listOf(
                DerivedNode("claim:a", Proposition("a", "true"), "margin_attention_required", setOf("claim:b"), "a"),
                DerivedNode("claim:b", Proposition("b", "true"), "margin_attention_required", setOf("claim:a"), "b"),
            ))
        }
        assertTrue(error.cycle.isNotEmpty())
    }
}
