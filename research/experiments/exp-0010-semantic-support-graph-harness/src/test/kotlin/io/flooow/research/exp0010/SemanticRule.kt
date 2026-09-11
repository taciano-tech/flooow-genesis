package io.flooow.research.exp0010

fun interface SemanticRule {
    fun supports(
        conclusion: Proposition,
        evidence: List<EvidenceFact>,
    ): Boolean
}

class SemanticRuleRegistry(
    private val rules: Map<String, SemanticRule>,
) {
    fun require(ruleId: String): SemanticRule =
        rules[ruleId] ?: throw UnknownSemanticRuleException(ruleId)

    companion object {
        fun experimentalDefaults(): SemanticRuleRegistry =
            SemanticRuleRegistry(
                mapOf(
                    "margin_below_20" to
                        SemanticRule { conclusion, evidence ->
                            val margin =
                                evidence
                                    .map { it.proposition }
                                    .firstOrNull {
                                        it.factKey == "contribution_margin_pct" &&
                                            it.relation == Relation.EQUALS
                                    }
                                    ?.value
                                    ?.toBigDecimalOrNull()

                            conclusion ==
                                Proposition(
                                    factKey = "contribution_margin_below_20",
                                    relation = Relation.EQUALS,
                                    value = "true",
                                ) &&
                                margin != null &&
                                margin < "20.00".toBigDecimal()
                        },
                ),
            )
    }
}
