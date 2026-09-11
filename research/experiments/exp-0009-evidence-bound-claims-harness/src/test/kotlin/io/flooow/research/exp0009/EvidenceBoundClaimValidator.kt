package io.flooow.research.exp0009

class EvidenceBoundClaimValidator(
    private val catalog: EvidenceCatalog,
) {
    fun validate(claims: List<DraftClaim>): ClaimValidationReport {
        claims.forEach(::rejectUnknownEvidenceReferences)

        return ClaimValidationReport(
            claims = claims.map(::validateKnownReferences),
        )
    }

    private fun rejectUnknownEvidenceReferences(claim: DraftClaim) {
        val unknown = claim.evidenceReferences.filterNot(catalog::contains).toSet()
        if (unknown.isNotEmpty()) {
            throw UnknownEvidenceReferenceException(unknown)
        }
    }

    private fun validateKnownReferences(claim: DraftClaim): ValidatedClaim {
        require(claim.confidence in 0.0..1.0) {
            "confidence must be between 0.0 and 1.0"
        }

        return when (claim.kind) {
            ClaimKind.OBSERVATION -> validateObservation(claim)
            ClaimKind.INFERENCE -> validateInference(claim)
            ClaimKind.HYPOTHESIS ->
                ValidatedClaim(
                    draft = claim,
                    decision = ClaimDecision.HYPOTHETICAL,
                    reasons =
                        listOf(
                            "hypotheses remain non-canonical even when informed by evidence",
                        ),
                )
            ClaimKind.ASSUMPTION ->
                ValidatedClaim(
                    draft = claim,
                    decision = ClaimDecision.ASSUMED,
                    reasons =
                        listOf(
                            "assumptions are explicitly non-evidentiary",
                        ),
                )
        }
    }

    private fun validateObservation(claim: DraftClaim): ValidatedClaim {
        if (claim.predicate != "EQUALS") {
            return unsupported(claim, "observation predicate must be EQUALS in EXP-0009")
        }

        if (claim.evidenceReferences.isEmpty()) {
            return unsupported(claim, "observation requires at least one evidence reference")
        }

        val matchingEvidence =
            claim.evidenceReferences
                .mapNotNull(catalog::find)
                .any { evidence ->
                    evidence.factKey == claim.factKey && evidence.value == claim.value
                }

        return if (matchingEvidence) {
            ValidatedClaim(
                draft = claim,
                decision = ClaimDecision.EVIDENCE_BACKED,
                reasons = listOf("factKey and value match governed evidence"),
            )
        } else {
            unsupported(
                claim,
                "no referenced evidence supports ${claim.factKey}=${claim.value}",
            )
        }
    }

    private fun validateInference(claim: DraftClaim): ValidatedClaim {
        if (claim.evidenceReferences.isEmpty()) {
            return unsupported(claim, "inference requires governed evidence references")
        }

        return ValidatedClaim(
            draft = claim,
            decision = ClaimDecision.INFERRED,
            reasons =
                listOf(
                    "inference is evidence-linked but remains non-canonical",
                ),
        )
    }

    private fun unsupported(
        claim: DraftClaim,
        reason: String,
    ): ValidatedClaim =
        ValidatedClaim(
            draft = claim,
            decision = ClaimDecision.UNSUPPORTED,
            reasons = listOf(reason),
        )
}
