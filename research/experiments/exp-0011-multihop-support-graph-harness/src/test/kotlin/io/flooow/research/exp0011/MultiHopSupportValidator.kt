package io.flooow.research.exp0011

class MultiHopSupportValidator(
    evidence: List<EvidenceFact>,
    private val rules: MultiHopRuleRegistry,
) {
    private val evidenceById = evidence.associateBy { it.reference }

    init { require(evidenceById.size == evidence.size) { "evidence references must be unique" } }

    fun validate(derived: List<DerivedNode>): ValidatedSupportGraph {
        val derivedById = derived.associateBy { it.id }
        require(derivedById.size == derived.size) { "derived node ids must be unique" }
        detectCycles(derivedById)

        val validated = linkedMapOf<String, ValidatedNode>()
        val edges = mutableListOf<SupportEdge>()

        evidenceById.values.forEach { fact ->
            validated[fact.reference] = ValidatedNode(
                id = fact.reference,
                proposition = fact.proposition,
                decision = NodeDecision.DIRECT,
                reasons = listOf("governed evidence"),
            )
        }

        fun resolve(id: String): ValidatedNode {
            validated[id]?.let { return it }
            val node = derivedById[id] ?: throw MissingPremiseException(id)
            val rule = rules.require(node.ruleId)
            val premises = node.premises.map(::resolve)
            node.premises.forEach { edges += SupportEdge(it, node.id, "VIA_RULE:${node.ruleId}") }

            val unsupported = premises.firstOrNull { it.decision == NodeDecision.UNSUPPORTED }
            val result =
                if (unsupported != null) {
                    ValidatedNode(node.id, node.proposition, NodeDecision.UNSUPPORTED,
                        listOf("unsupported premise: ${unsupported.id}"))
                } else {
                    val out = rule.derive(premises.map { it.proposition })
                    if (out == node.proposition)
                        ValidatedNode(node.id, node.proposition, NodeDecision.DERIVED,
                            listOf("all premises supported and registered rule derives proposition"))
                    else
                        ValidatedNode(node.id, node.proposition, NodeDecision.UNSUPPORTED,
                            listOf("registered rule does not derive requested proposition"))
                }
            validated[node.id] = result
            return result
        }

        derived.forEach { resolve(it.id) }
        return ValidatedSupportGraph(validated, edges)
    }

    private fun detectCycles(nodes: Map<String, DerivedNode>) {
        val state = mutableMapOf<String, Int>()
        val path = mutableListOf<String>()
        fun visit(id: String) {
            if (id !in nodes) return
            when (state[id]) {
                2 -> return
                1 -> {
                    val start = path.indexOf(id).coerceAtLeast(0)
                    throw SupportCycleException(path.drop(start) + id)
                }
            }
            state[id] = 1
            path += id
            nodes.getValue(id).premises.forEach(::visit)
            path.removeAt(path.lastIndex)
            state[id] = 2
        }
        nodes.keys.forEach(::visit)
    }
}
