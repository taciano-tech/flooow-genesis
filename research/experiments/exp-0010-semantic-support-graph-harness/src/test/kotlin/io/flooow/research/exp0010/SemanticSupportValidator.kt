package io.flooow.research.exp0010

class SemanticSupportValidator(
    private val catalog: SemanticEvidenceCatalog,
    private val rules: SemanticRuleRegistry,
) {
    fun validate(claim: DraftSemanticClaim): ValidatedSemanticClaim {
        require(claim.confidence in 0.0..1.0) {
            "confidence must be between 0.0 and 1.0"
        }

        rejectUnknownEvidenceReferences(claim.support.evidenceReferences)

        return when (val support = claim.support) {
            is SupportDeclaration.Direct -> validateDirect(claim, support)
            is SupportDeclaration.Derived -> validateDerived(claim, support)
            SupportDeclaration.Hypothetical -> hypothetical(claim)
        }
    }

    private fun rejectUnknownEvidenceReferences(references: Set<String>) {
        val unknown = references.filterNot(catalog::contains).toSet()
        if (unknown.isNotEmpty()) {
            throw UnknownEvidenceReferenceException(unknown)
        }
    }

    private fun validateDirect(
        claim: DraftSemanticClaim,
        support: SupportDeclaration.Direct,
    ): ValidatedSemanticClaim {
        if (support.evidenceReferences.isEmpty()) {
            return unsupported(claim, "direct support requires governed evidence")
        }

        val exactMatch =
            support.evidenceReferences
                .mapNotNull(catalog::find)
                .any { it.proposition == claim.proposition }

        return if (exactMatch) {
            accepted(
                claim = claim,
                decision = SupportDecision.DIRECTLY_SUPPORTED,
                reason = "typed proposition exactly matches governed evidence",
                ruleId = null,
            )
        } else {
            unsupported(
                claim,
                "valid evidence reference does not semantically match the typed proposition",
            )
        }
    }

    private fun validateDerived(
        claim: DraftSemanticClaim,
        support: SupportDeclaration.Derived,
    ): ValidatedSemanticClaim {
        if (support.evidenceReferences.isEmpty()) {
            return unsupported(claim, "derived support requires governed evidence")
        }

        val evidence = support.evidenceReferences.mapNotNull(catalog::find)
        val rule = rules.require(support.ruleId)

        return if (rule.supports(claim.proposition, evidence)) {
            accepted(
                claim = claim,
                decision = SupportDecision.DERIVED_SUPPORTED,
                reason = "registered Flooow semantic rule supports the typed conclusion",
                ruleId = support.ruleId,
            )
        } else {
            unsupported(
                claim,
                "registered rule does not support the typed conclusion from supplied evidence",
            )
        }
    }

    private fun hypothetical(claim: DraftSemanticClaim): ValidatedSemanticClaim =
        ValidatedSemanticClaim(
            draft = claim,
            decision = SupportDecision.HYPOTHETICAL,
            reasons = listOf("hypothesis has no evidentiary support status"),
            graph =
                SupportGraph(
                    claimId = claim.id,
                    evidenceReferences = emptySet(),
                    ruleId = null,
                    edges = emptyList(),
                ),
        )

    private fun accepted(
        claim: DraftSemanticClaim,
        decision: SupportDecision,
        reason: String,
        ruleId: String?,
    ): ValidatedSemanticClaim {
        val refs = claim.support.evidenceReferences
        val edges =
            refs.map { reference ->
                SupportEdge(
                    from = reference,
                    to = claim.id,
                    kind = if (ruleId == null) "DIRECT" else "VIA_RULE:$ruleId",
                )
            }

        return ValidatedSemanticClaim(
            draft = claim,
            decision = decision,
            reasons = listOf(reason),
            graph =
                SupportGraph(
                    claimId = claim.id,
                    evidenceReferences = refs,
                    ruleId = ruleId,
                    edges = edges,
                ),
        )
    }

    private fun unsupported(
        claim: DraftSemanticClaim,
        reason: String,
    ): ValidatedSemanticClaim =
        ValidatedSemanticClaim(
            draft = claim,
            decision = SupportDecision.UNSUPPORTED,
            reasons = listOf(reason),
            graph =
                SupportGraph(
                    claimId = claim.id,
                    evidenceReferences = claim.support.evidenceReferences,
                    ruleId = (claim.support as? SupportDeclaration.Derived)?.ruleId,
                    edges = emptyList(),
                ),
        )
}
