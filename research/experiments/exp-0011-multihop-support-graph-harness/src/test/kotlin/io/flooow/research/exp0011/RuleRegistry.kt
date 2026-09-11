package io.flooow.research.exp0011

fun interface MultiHopRule {
    fun derive(premises: List<Proposition>): Proposition?
}
class MultiHopRuleRegistry(private val rules: Map<String, MultiHopRule>) {
    fun require(ruleId: String): MultiHopRule = rules[ruleId] ?: throw UnknownRuleException(ruleId)
    companion object {
        fun experimentalDefaults() = MultiHopRuleRegistry(
            mapOf(
                "margin_below_20" to MultiHopRule { premises ->
                    val margin = premises.firstOrNull { it.factKey == "contribution_margin_pct" }
                        ?.value?.toBigDecimalOrNull()
                    if (margin != null && margin < "20.00".toBigDecimal())
                        Proposition("contribution_margin_below_20", "true")
                    else null
                },
                "margin_attention_required" to MultiHopRule { premises ->
                    if (premises.any { it == Proposition("contribution_margin_below_20", "true") })
                        Proposition("margin_attention_required", "true")
                    else null
                },
            )
        )
    }
}
