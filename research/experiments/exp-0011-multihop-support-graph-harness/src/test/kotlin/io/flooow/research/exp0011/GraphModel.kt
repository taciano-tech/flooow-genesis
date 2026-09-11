package io.flooow.research.exp0011

data class Proposition(val factKey: String, val value: String)
data class EvidenceFact(val reference: String, val proposition: Proposition)
data class DerivedNode(
    val id: String,
    val proposition: Proposition,
    val ruleId: String,
    val premises: Set<String>,
    val narrative: String,
)
enum class NodeDecision { DIRECT, DERIVED, UNSUPPORTED }
data class ValidatedNode(
    val id: String,
    val proposition: Proposition,
    val decision: NodeDecision,
    val reasons: List<String>,
    val canonicalTruth: Boolean = false,
    val executable: Boolean = false,
)
data class SupportEdge(val from: String, val to: String, val kind: String)
data class ValidatedSupportGraph(
    val nodes: Map<String, ValidatedNode>,
    val edges: List<SupportEdge>,
) {
    fun requireNode(id: String): ValidatedNode = nodes[id] ?: error("missing validated node: $id")
}
class UnknownRuleException(val ruleId: String) : IllegalArgumentException("unknown rule: $ruleId")
class MissingPremiseException(val premiseId: String) : IllegalArgumentException("missing premise: $premiseId")
class SupportCycleException(val cycle: List<String>) :
    IllegalArgumentException("support cycle: ${cycle.joinToString(" -> ")}")
