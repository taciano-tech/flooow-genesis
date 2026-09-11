package io.flooow.research.exp0015

import java.time.Instant

class BeliefRevisionEngine(
    private val policy: RevisionPolicy = RevisionPolicy(),
) {
    fun establish(
        beliefId: String,
        proposition: BeliefProposition,
        evidence: BeliefEvidenceProfile,
        recordedAt: Instant,
    ): BeliefRevisionResult {
        val reasonCodes =
            when {
                evidence.semanticSupport != SemanticSupport.SUPPORTED ->
                    setOf(
                        RevisionReasonCode.CANDIDATE_UNSUPPORTED,
                        RevisionReasonCode.INSUFFICIENT_INFORMATION,
                    )

                evidence.temporalValidity != TemporalValidity.VALID ->
                    setOf(
                        RevisionReasonCode.CANDIDATE_TEMPORALLY_INVALID,
                        RevisionReasonCode.INSUFFICIENT_INFORMATION,
                    )

                evidence.confidence == ConfidenceBand.INSUFFICIENT ->
                    setOf(RevisionReasonCode.INSUFFICIENT_INFORMATION)

                evidence.contradictionState == ContradictionState.UNRESOLVED ->
                    setOf(
                        RevisionReasonCode.CANDIDATE_CONTRADICTED,
                        RevisionReasonCode.INSUFFICIENT_INFORMATION,
                    )

                else ->
                    setOf(RevisionReasonCode.INITIAL_ESTABLISHMENT)
            }

        val state =
            if (reasonCodes == setOf(RevisionReasonCode.INITIAL_ESTABLISHMENT)) {
                BeliefState.ESTABLISHED
            } else {
                BeliefState.INSUFFICIENT_INFORMATION
            }

        return BeliefRevisionResult(
            previous = null,
            current =
                BeliefVersion(
                    beliefId = beliefId,
                    version = 1,
                    proposition = proposition,
                    state = state,
                    evidence = evidence,
                    priorVersion = null,
                    revisionReasonCodes = reasonCodes,
                    recordedAt = recordedAt,
                ),
        )
    }

    fun revise(
        current: BeliefVersion,
        candidateProposition: BeliefProposition,
        candidateEvidence: BeliefEvidenceProfile,
        recordedAt: Instant,
    ): BeliefRevisionResult {
        requireComparable(current.proposition, candidateProposition)

        val nextVersion = current.version + 1

        if (candidateEvidence.semanticSupport != SemanticSupport.SUPPORTED) {
            return preserveCurrent(
                current = current,
                candidateProposition = candidateProposition,
                candidateEvidence = candidateEvidence,
                nextVersion = nextVersion,
                recordedAt = recordedAt,
                state = BeliefState.SUSPENDED,
                reasons = setOf(RevisionReasonCode.CANDIDATE_UNSUPPORTED),
            )
        }

        if (candidateEvidence.temporalValidity != TemporalValidity.VALID) {
            return preserveCurrent(
                current = current,
                candidateProposition = candidateProposition,
                candidateEvidence = candidateEvidence,
                nextVersion = nextVersion,
                recordedAt = recordedAt,
                state = BeliefState.SUSPENDED,
                reasons = setOf(RevisionReasonCode.CANDIDATE_TEMPORALLY_INVALID),
            )
        }

        if (candidateEvidence.confidence == ConfidenceBand.INSUFFICIENT) {
            return preserveCurrent(
                current = current,
                candidateProposition = candidateProposition,
                candidateEvidence = candidateEvidence,
                nextVersion = nextVersion,
                recordedAt = recordedAt,
                state = BeliefState.INSUFFICIENT_INFORMATION,
                reasons = setOf(RevisionReasonCode.INSUFFICIENT_INFORMATION),
            )
        }

        if (candidateEvidence.contradictionState == ContradictionState.UNRESOLVED) {
            val sameValue = candidateProposition.value == current.proposition.value

            return contested(
                current = current,
                candidateProposition = candidateProposition,
                candidateEvidence = candidateEvidence,
                nextVersion = nextVersion,
                recordedAt = recordedAt,
                reasons =
                    buildSet {
                        if (!sameValue) {
                            add(RevisionReasonCode.CONFLICT_DETECTED)
                        }
                        add(RevisionReasonCode.CANDIDATE_CONTRADICTED)
                    },
            )
        }

        if (candidateProposition.value == current.proposition.value) {
            val reinforcedEvidence =
                candidateEvidence.copy(
                    supportingClaimIds =
                        current.evidence.supportingClaimIds +
                            candidateEvidence.supportingClaimIds,
                    opposingClaimIds =
                        current.evidence.opposingClaimIds +
                            candidateEvidence.opposingClaimIds,
                )

            return BeliefRevisionResult(
                previous = current,
                current =
                    BeliefVersion(
                        beliefId = current.beliefId,
                        version = nextVersion,
                        proposition = current.proposition,
                        state = BeliefState.REINFORCED,
                        evidence = reinforcedEvidence,
                        priorVersion = current.version,
                        revisionReasonCodes =
                            setOf(
                                RevisionReasonCode.AGREEMENT_REINFORCES,
                            ),
                        recordedAt = recordedAt,
                    ),
            )
        }

        if (
            candidateEvidence.confidence.rank <
            policy.minimumSupersessionConfidence.rank
        ) {
            return contested(
                current = current,
                candidateProposition = candidateProposition,
                candidateEvidence = candidateEvidence,
                nextVersion = nextVersion,
                recordedAt = recordedAt,
                reasons =
                    setOf(
                        RevisionReasonCode.CONFLICT_DETECTED,
                        RevisionReasonCode.CANDIDATE_BELOW_MINIMUM_CONFIDENCE,
                    ),
            )
        }

        if (
            candidateEvidence.confidence.rank <=
            current.evidence.confidence.rank
        ) {
            return contested(
                current = current,
                candidateProposition = candidateProposition,
                candidateEvidence = candidateEvidence,
                nextVersion = nextVersion,
                recordedAt = recordedAt,
                reasons =
                    setOf(
                        RevisionReasonCode.CONFLICT_DETECTED,
                        RevisionReasonCode.CANDIDATE_NOT_STRONGER,
                    ),
            )
        }

        val currentObservedAt = current.evidence.observedAt
        val candidateObservedAt = candidateEvidence.observedAt

        if (
            currentObservedAt == null ||
            candidateObservedAt == null
        ) {
            return contested(
                current = current,
                candidateProposition = candidateProposition,
                candidateEvidence = candidateEvidence,
                nextVersion = nextVersion,
                recordedAt = recordedAt,
                reasons =
                    setOf(
                        RevisionReasonCode.CONFLICT_DETECTED,
                        RevisionReasonCode.INSUFFICIENT_INFORMATION,
                    ),
            )
        }

        if (!candidateObservedAt.isAfter(currentObservedAt)) {
            return contested(
                current = current,
                candidateProposition = candidateProposition,
                candidateEvidence = candidateEvidence,
                nextVersion = nextVersion,
                recordedAt = recordedAt,
                reasons =
                    setOf(
                        RevisionReasonCode.CONFLICT_DETECTED,
                        RevisionReasonCode.CANDIDATE_NOT_NEWER,
                    ),
            )
        }

        return BeliefRevisionResult(
            previous = current,
            current =
                BeliefVersion(
                    beliefId = current.beliefId,
                    version = nextVersion,
                    proposition = candidateProposition,
                    state = BeliefState.SUPERSEDED,
                    evidence = candidateEvidence,
                    priorVersion = current.version,
                    revisionReasonCodes =
                        setOf(
                            RevisionReasonCode.CONFLICT_DETECTED,
                            RevisionReasonCode.CANDIDATE_SUPERSEDES,
                        ),
                    recordedAt = recordedAt,
                ),
        )
    }

    private fun preserveCurrent(
        current: BeliefVersion,
        candidateProposition: BeliefProposition,
        candidateEvidence: BeliefEvidenceProfile,
        nextVersion: Int,
        recordedAt: Instant,
        state: BeliefState,
        reasons: Set<RevisionReasonCode>,
    ): BeliefRevisionResult =
        BeliefRevisionResult(
            previous = current,
            current =
                BeliefVersion(
                    beliefId = current.beliefId,
                    version = nextVersion,
                    proposition = current.proposition,
                    state = state,
                    evidence =
                        current.evidence.copy(
                            opposingClaimIds =
                                current.evidence.opposingClaimIds +
                                    candidateEvidence.supportingClaimIds +
                                    candidateEvidence.opposingClaimIds,
                        ),
                    priorVersion = current.version,
                    revisionReasonCodes = reasons,
                    recordedAt = recordedAt,
                ),
        )

    private fun contested(
        current: BeliefVersion,
        candidateProposition: BeliefProposition,
        candidateEvidence: BeliefEvidenceProfile,
        nextVersion: Int,
        recordedAt: Instant,
        reasons: Set<RevisionReasonCode>,
    ): BeliefRevisionResult =
        BeliefRevisionResult(
            previous = current,
            current =
                BeliefVersion(
                    beliefId = current.beliefId,
                    version = nextVersion,
                    proposition = current.proposition,
                    state = BeliefState.CONTESTED,
                    evidence =
                        current.evidence.copy(
                            opposingClaimIds =
                                current.evidence.opposingClaimIds +
                                    candidateEvidence.supportingClaimIds +
                                    candidateEvidence.opposingClaimIds,
                        ),
                    priorVersion = current.version,
                    revisionReasonCodes = reasons,
                    recordedAt = recordedAt,
                ),
        )

    private fun requireComparable(
        current: BeliefProposition,
        candidate: BeliefProposition,
    ) {
        require(current.organizationId == candidate.organizationId) {
            "belief revision cannot cross organizations"
        }

        require(current.subjectId == candidate.subjectId) {
            "belief revision requires same subject"
        }

        require(current.factKey == candidate.factKey) {
            "belief revision requires same fact key"
        }

        require(current.scope == candidate.scope) {
            "belief revision requires same governed scope"
        }

        require(current.value::class == candidate.value::class) {
            "belief revision requires same typed value domain"
        }
    }
}
