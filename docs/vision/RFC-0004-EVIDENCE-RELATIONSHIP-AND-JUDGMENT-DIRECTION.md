# RFC-0004: Evidence Relationship and Judgment Direction

**Version:** 0.1

**Status:** Draft for Experimental Validation

## Objective

Define the smallest domain-independent reasoning semantics that could represent
how evidence relates to a hypothesis and how a judgment reports the evaluated
direction.

This RFC proposes concepts and an experimental validation plan. It does not
authorize a Kernel implementation, migration, or primitive promotion.

## Context

EXP-0002 evaluated supporting, contradicting, equal-conflict, unequal-conflict,
and permuted evidence using the frozen executable Kernel. The baseline and an
independent fresh replication established that:

- `Evidence` records observations, confidence, and time but no relationship to
  a hypothesis;
- `Judgment` records a free-text conclusion and confidence but no explicit
  supported, contradicted, or unresolved direction;
- the deterministic aggregator averages confidence magnitude without semantic
  direction;
- the deterministic evaluator always emits a supportive conclusion;
- opposite and conflicting inputs therefore produce the same semantic result;
- insertion-order determinism is preserved, but semantic fidelity is not.

The independent replication passed one isolated characterization test and a
fresh clean build of 72 tests. It reproduced all five scenarios and the
committed snapshot without semantic or test-count divergence.

EXP-0002 consequently supported its null hypothesis and authorized a separate
RFC under its decision matrix.

## Problem Statement

Confidence answers how strongly a statement is held. It does not answer whether
an evidence item supports or contradicts a particular hypothesis.

Free-text observation descriptions cannot safely supply that relationship:
parsing prose would create a hidden, application-dependent reasoning API.
Likewise, a free-text judgment conclusion cannot provide a stable executable
contract for downstream reasoning.

The model needs to determine whether direction is:

1. an intrinsic property of evidence;
2. a relationship between evidence and a hypothesis;
3. a derived evaluation result;
4. some smaller composition of existing concepts.

## Semantic Principles

Any accepted design must preserve these distinctions:

- an observation records a claim, perception, or measurement, not truth;
- evidence is derived from observations;
- confidence is a normalized degree, not semantic polarity;
- the same evidence may relate differently to different hypotheses;
- absence of support is not automatically contradiction;
- conflict is not automatically resolved by averaging confidence;
- deterministic output must remain explainable from retained inputs and rules;
- application vocabulary must not redefine universal reasoning semantics.

## Proposed Conceptual Model

### Evidence-to-Hypothesis Relationship

The leading proposal treats direction as a relationship scoped to one evidence
item and one hypothesis, rather than as an intrinsic field on `Evidence`.

Conceptually:

```text
EvidenceRelationship
  evidence reference
  hypothesis reference
  direction: SUPPORTS | CONTRADICTS
```

This relationship states how the evidence bears on that hypothesis. It does
not claim that the evidence is true, sufficient, or decisive.

`UNRESOLVED` is intentionally excluded from the relationship. It is an
evaluation outcome produced when the available relationships and policy do not
justify either supported or contradicted judgment direction.

### Judgment Direction

A judgment should expose an explicit evaluated direction:

```text
JudgmentDirection = SUPPORTED | CONTRADICTED | UNRESOLVED
```

The direction is distinct from:

- the human-readable conclusion;
- the confidence assigned to the judgment;
- the evidence relationships retained in the evaluation trace.

The conclusion may explain the result, but executable behavior must depend on
the explicit direction rather than matching conclusion text.

### Evaluation Policy

The relationship alone does not define how multiple evidence items resolve to
a judgment. A separate, explicit evaluation policy must specify:

- how supporting and contradicting contributions are retained;
- whether and how confidence affects each contribution;
- what constitutes `SUPPORTED`, `CONTRADICTED`, or `UNRESOLVED`;
- how ties, missing relationships, and insufficient evidence are handled;
- how the complete calculation is represented in an explainable trace.

This RFC does not select a numerical conflict-resolution formula. Such a
formula must be falsifiable and experimentally validated rather than embedded
as an unexamined architectural assumption.

## Scope Boundaries

This proposal does not define source authority, probabilistic or Bayesian
reasoning, machine learning, persistence, natural-language interpretation,
universal thresholds, autonomous action, or decision selection. Those concerns
may use reasoning results later, but they cannot determine this minimal
semantic contract.

## Candidate Classification

| Candidate | Initial classification | Rationale |
|---|---|---|
| Evidence-to-hypothesis relationship | Candidate derived reasoning concept | EXP-0002 demonstrates necessity in reasoning, but universality and irreducibility still require validation. |
| Relationship direction | Candidate closed semantic vocabulary | Support and contradiction are domain-independent, but must be tested beyond replenishment. |
| Judgment direction | Candidate derived evaluation result | It reports reasoning output and may not be an independent primitive. |
| Conflict-resolution policy | Strategy, not primitive | Different valid policies may exist while sharing the same semantic contracts. |
| Human-readable conclusion | Explanatory projection | It remains useful but must not be the executable direction contract. |

No row in this table promotes a Kernel primitive.

## Required Invariants

1. Every evidence relationship references exactly one evidence item and one
   hypothesis.
2. Direction cannot be encoded through confidence sign, range, or sentinel
   value.
3. Relationship direction is restricted to `SUPPORTS` or `CONTRADICTS`.
4. Judgment direction is restricted to `SUPPORTED`, `CONTRADICTED`, or
   `UNRESOLVED`.
5. `UNRESOLVED` is an evaluation result, not an intrinsic evidence direction.
6. Every evaluated relationship remains available in the result or trace.
7. Identical inputs, policy, and clock produce structurally identical output.
8. Permuting an evidence set does not change direction, confidence, or trace.
9. Changing only conclusion prose does not change executable direction.
10. Evidence without an explicit relationship is rejected or handled through
    an explicit policy; it is never silently treated as supporting.
11. A relationship to one hypothesis does not imply the same direction for a
    different hypothesis.
12. Application code does not parse observation prose to infer direction.
13. Orphaned relationships and duplicate contradictory relationships for the
    same evidence-hypothesis pair are rejected unless a future explicit model
    gives them distinct identity and semantics.
14. Structured direction is the executable source of truth; explanatory text
    cannot contradict it.

## Compatibility and Migration Constraints

The current contracts are already used by Kernel tests and Marketplace
Operations. Any future implementation proposal must explicitly address:

- construction of existing `Evidence` values;
- `EvidenceSet` and `AggregatedEvidence` compatibility;
- callers of `HypothesisEvaluator` and consumers of `Judgment`;
- preservation of existing evidence, confidence, timestamps, and identifiers;
- versioning or staged migration without ambiguous default direction;
- characterization of the current constant supportive conclusion;
- source and binary compatibility expectations for the current project stage.

A default value of `SUPPORTS` is prohibited because it would preserve the exact
semantic defect demonstrated by EXP-0002.

The preferred experimental migration is additive: retain the canonical
`Evidence` constructor and introduce contextual relationships at the reasoning
boundary. Because an explicit direction on the current `Judgment` would change
constructors, equality, and snapshots, experiments should use a parallel value
or adapter until an ADR authorizes a production migration. Missing relationship
data must fail explicitly or produce `UNRESOLVED` only when a validated policy
declares that behavior.

## Alternatives Considered

### Add direction directly to `Evidence`

Not selected as the leading proposal. The same evidence can support one
hypothesis and contradict another, so intrinsic polarity would conflate the
evidence with a contextual relationship.

### Infer direction from observation text

Rejected. This creates a hidden natural-language API, is not reliably
deterministic across interpreters, and transfers universal reasoning semantics
to application prose.

### Use confidence below a threshold as contradiction

Rejected. Confidence magnitude and semantic direction are independent. A
low-confidence supporting item is not contradicting evidence.

### Represent support and contradiction as positive and negative confidence

Rejected. It violates the normalized confidence contract and combines two
dimensions into one opaque value.

### Keep direction only in application code

Not accepted as a final answer. EXP-0002 demonstrates a reasoning requirement,
but further cross-domain evidence is required before concluding that the
relationship belongs in the Kernel.

### Encode only judgment direction

Insufficient. A final direction without retained input relationships cannot
explain how conflicting evidence contributed to the judgment.

### Introduce one generic universal `Relationship` primitive first

Deferred. RFC-0002 requires universality, irreducibility, necessity,
independence, and stability before primitive promotion. EXP-0002 establishes a
specific expressive gap, not the validity of a generic relationship primitive.

## Experimental Validation Plan

The proposal must be tested before an ADR or Kernel change.

### Track A: Controlled Reasoning Semantics

Re-run the five EXP-0002 scenarios through a prototype owned outside the
production Kernel:

- supporting-only produces `SUPPORTED`;
- contradicting-only produces `CONTRADICTED`;
- equal conflict produces `UNRESOLVED` under an explicit baseline policy;
- unequal conflict retains both contributions and applies a declared policy;
- permutation produces an identical result and trace.

The prototype must not parse prose or encode polarity in confidence.

It must also exercise missing relationships, duplicate pair relationships,
three or more evidence items, confidence extremes, and the distinction between
an actual conflict, an exact tie, and insufficient evidence.

### Track B: Cross-Hypothesis Context

Use one evidence item against two distinct hypotheses and demonstrate that its
relationship can differ without copying or mutating the evidence.

### Track C: Cross-Domain Validation

Exercise the same semantic contracts in at least one non-marketplace domain.
Domain vocabulary may differ; the relationship and judgment semantics must not.

### Track D: Reduction Test

Attempt to express the required behavior using existing Kernel concepts without
new semantic fields or application-owned reasoning rules. Record exactly why
each reduction succeeds or fails.

### Track E: Policy Separation

Compare at least two explicit deterministic policies, such as a contribution
balance and a conservative conflict policy, without changing the relationship
or judgment-direction contracts. The comparison must establish that policy is
a replaceable strategy rather than hidden ontology.

## Acceptance Criteria

This RFC may advance toward an architectural decision only if reproducible
evidence shows that:

- all required invariants hold;
- all controlled directions are represented explicitly;
- conflict resolution retains both supporting and contradicting contributions;
- confidence remains independent from direction;
- one evidence item can relate differently to distinct hypotheses;
- the semantics reproduce in a second domain;
- at least two policies use the same semantic contracts and produce fully
  explainable traces;
- another engineer independently reproduces the results;
- the proposed concepts satisfy RFC-0002 validation or are explicitly
  classified as derived concepts or strategies;
- compatibility and migration are specified without a supportive default.

## Rejection Criteria

The proposal must be rejected or revised if:

- application vocabulary is required to interpret direction;
- prose parsing affects executable semantics;
- confidence is overloaded as polarity;
- evidence relationships are discarded during aggregation;
- unresolved results cannot be expressed explicitly;
- insertion order changes the result;
- the model assumes one evidence item has universal polarity;
- a simpler composition of existing concepts passes all scenarios;
- cross-domain validation shows the semantics are marketplace-specific;
- independent replication contradicts the recorded evidence.

## Risks and Open Questions

- Is `EvidenceRelationship` a derived reasoning value or evidence that a more
  general Kernel relationship concept is necessary?
- Does `SUPPORTS | CONTRADICTS` cover every required relationship, or must
  relevance and independence be represented separately?
- Should missing relationship data make an evaluation invalid or unresolved?
- Which conflict-resolution policies are universal, and which belong to an
  application or runtime configuration?
- How should source authority, dependence between observations, and duplicated
  evidence affect later policies without entering this minimal model?
- Can judgment confidence be explained independently from direction under every
  accepted policy?
- What trace structure is minimal while remaining auditable?

These questions must be answered by experiments, not by implementation
convenience.

## Governance and Next Decision

This RFC complies with the governance sequence:

```text
EXP-0002 evidence -> RFC-0004 proposal -> validation experiment -> ADR -> Kernel
```

The immediate next task after RFC review is to specify the controlled prototype
experiment. No production Kernel source may change until reproducible evidence
supports the proposal and an ADR accepts the architectural decision.

## Decision Requested

Approve this document only as a **draft experimental proposal**. Approval would
authorize design and execution of the validation experiment. It would not
authorize implementation in the Kernel or acceptance of any new primitive.
