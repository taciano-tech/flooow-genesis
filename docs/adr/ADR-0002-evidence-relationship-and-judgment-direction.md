# ADR-0002: Contextual Evidence Relationship and Structured Judgment Direction

Status: Accepted

Date: 2026-08-04

Accepted: 2026-08-04 through PR #42

## Context

The executable reasoning Kernel currently records canonical `Evidence` with
observation references, confidence, and time. It records a reasoning
`Judgment` with a hypothesis reference, free-text conclusion, confidence, and
time.

The contracts do not state how one evidence item relates to a particular
hypothesis, and the judgment does not expose supported, contradicted, or
unresolved direction as structured data.

EXP-0002 characterized the consequence. Supporting, contradicting, and
conflicting inputs produced the same constant supportive conclusion. The result
was deterministic but could not faithfully represent semantic conflict.

RFC-0004 proposed two candidate reasoning semantics:

1. a contextual evidence-to-hypothesis relationship whose direction is
   `SUPPORTS | CONTRADICTS`;
2. a structured judgment direction
   `SUPPORTED | CONTRADICTED | UNRESOLVED`.

EXP-0003 tested those semantics in an isolated harness under:

- two deterministic policies;
- six core scenarios;
- Marketplace Replenishment and Service Reliability fixtures;
- twelve context and integrity scenarios;
- a neutral reduction test;
- three semantic ablations;
- context-independent replication.

The complete evidence reproduced without semantic divergence:

- 24 of 24 core traces;
- 46 of 46 integrity keys;
- 101 of 101 normalized snapshot lines;
- 73 of 73 repository tests;
- three existing-Kernel output collisions;
- all three predeclared ablation failures.

The final EXP-0003 decision authorized preparation of an ADR without
authorizing production implementation or primitive promotion.

## Decision

Flooow Genesis will adopt contextual evidence relationship and structured
judgment direction as explicit reasoning contracts, subject to the staged
specification and migration constraints in this ADR.

### 1. Evidence Remains Canonical and Hypothesis-Independent

The canonical `Evidence` concept will continue to identify information derived
from observations. It will not receive an intrinsic support/contradiction field
or a default hypothesis reference.

The same evidence may support one hypothesis and contradict another without
being copied or mutated.

### 2. Relationship Direction Is Contextual

The reasoning model will introduce a derived relationship value conceptually
equivalent to:

```text
EvidenceRelationship
  id
  evidenceId
  hypothesisId
  direction: SUPPORTS | CONTRADICTS
```

The relationship belongs to the reasoning context. It is not accepted as a new
generic Kernel `Relationship` primitive.

A relationship states how evidence bears on one hypothesis. It does not claim
that the evidence is true, sufficient, independent, or decisive.

### 3. Judgment Direction Is Structured

A reasoning judgment will expose an explicit direction:

```text
JudgmentDirection = SUPPORTED | CONTRADICTED | UNRESOLVED
```

This direction is the executable source of truth.

A human-readable conclusion may remain as explanatory output, but executable
consumers must not infer direction by parsing or matching conclusion text. The
conclusion must not contradict the structured direction.

### 4. Unresolved Is an Evaluation Result

`UNRESOLVED` belongs to judgment evaluation, not evidence relationship.

It may represent conflict, numerical tie, or insufficient weighted evidence.
An evaluation trace must distinguish those reasons structurally. The exact
reason vocabulary will be defined by the implementation specification.

Absence of support is not automatically contradiction, and absence of a
relationship is never silently treated as support.

### 5. Confidence and Direction Remain Independent

`Confidence` remains a normalized magnitude. It must not encode relationship
polarity through sign, range, threshold, sentinel value, or default.

A low-confidence supporting relationship is still supporting. A
zero-confidence relationship does not change its declared direction.

Any judgment confidence or policy-specific numerical assessment must define its
formula and interpretation explicitly. This ADR does not select a universal
judgment-confidence formula.

### 6. Conflict Resolution Is a Replaceable Policy

The contextual relationship and judgment result are stable reasoning contracts.
Conflict resolution remains an explicit, replaceable strategy.

The strict-conflict and weighted-balance policies used by EXP-0003 remain
experimental evidence. Neither is selected as the universal or default
production policy by this ADR.

A policy must:

- consume the same validated relationship contract;
- retain every evaluated relationship;
- produce structured direction and reason;
- be deterministic for identical input, configuration, and clock;
- preserve canonical ordering in its trace;
- declare every numerical formula and threshold.

### 7. Validation Precedes Policy Evaluation

Invalid input must be rejected before a policy executes.

The specification must cover at least:

- no relationships;
- missing evidence references;
- hypothesis mismatch;
- duplicate relationship identifiers;
- identical duplicate evidence-hypothesis pairs;
- contradictory duplicate evidence-hypothesis pairs.

Validation must not deduplicate, reinterpret, or default invalid input silently.

### 8. Evaluated Contributions Remain Auditable

A successful result or its trace must retain every evaluated relationship,
including evidence reference, hypothesis reference, relationship direction, and
the confidence magnitude used by the policy.

Input permutation must not change direction, numerical result, reason, or
canonical trace. Explanation text is not a substitute for retained structured
contributions.

### 9. Candidate Classification

The accepted classifications are:

| Candidate | Classification |
|---|---|
| Evidence-to-hypothesis relationship | Derived reasoning concept |
| Relationship direction | Closed reasoning vocabulary |
| Judgment direction | Derived evaluation result |
| Conflict-resolution policy | Replaceable strategy |
| Human-readable conclusion | Explanatory projection |

No candidate in this ADR is promoted as a new Kernel primitive.

## Compatibility and Migration

Implementation must be staged. This ADR does not authorize a single breaking
rewrite.

### Stage 1: Additive Contracts

Introduce the new reasoning contracts and validation behavior alongside the
current executable API.

Requirements:

- preserve the canonical `Evidence` constructor and meaning;
- keep experimental and production names distinct until the specification is
  accepted;
- add no implicit `SUPPORTS` default;
- provide complete contract and invariant tests;
- characterize source and binary compatibility explicitly.

### Stage 2: Parallel Evaluation Path

Introduce an explicit evaluation request/path that carries contextual
relationships and returns structured judgment direction and retained
contributions.

The existing evaluator may remain temporarily for compatibility, but it must be
identified as legacy behavior and may not be presented as conflict-aware.

No adapter may manufacture supportive relationships from legacy evidence.
Callers without relationship semantics must fail explicitly or use a separately
specified unresolved/legacy path.

### Stage 3: Consumer Migration

Migrate Kernel tests and application consumers deliberately.

Each migration must:

- supply explicit relationships;
- assert structured direction and reason;
- preserve or deliberately version snapshots;
- verify complete trace retention;
- remove executable dependence on conclusion prose;
- document behavioral differences from the constant supportive evaluator.

### Stage 4: Legacy Retirement Decision

Retirement or removal of the current constant supportive behavior requires a
separate compatibility review after all known consumers migrate.

This ADR does not decide when the legacy conclusion field is removed. It may
remain as a derived explanation if its consistency is enforced.

## Required Implementation Specification

Before production code changes, a separate specification must define:

- exact package ownership and type names;
- identifiers and construction invariants;
- request, validation, result, and trace shapes;
- structured unresolved-reason vocabulary;
- compatibility and deprecation strategy;
- error behavior;
- deterministic ordering and serialization;
- chosen initial production policy or explicit absence of a default;
- confidence semantics;
- regression scenarios derived from EXP-0002 and EXP-0003;
- rollout, rollback, and legacy retirement criteria.

The specification must keep the Kernel independent from Marketplace
Replenishment and Service Reliability vocabulary.

## Alternatives Considered

### Add Direction Directly to Evidence

Rejected because evidence direction is hypothesis-dependent. Intrinsic polarity
would prevent one evidence item from bearing differently on distinct
hypotheses.

### Parse Observation or Conclusion Text

Rejected because prose would become a hidden executable API, weaken
determinism, and transfer reasoning responsibility to domain language.

### Encode Polarity in Confidence

Rejected because magnitude and semantic direction are independent dimensions.
Signed, thresholded, or sentinel confidence would violate the current
confidence contract.

### Add Only Judgment Direction

Rejected because a final direction without input relationships cannot explain
how conflicting evidence contributed to the result.

### Keep All Relationship Semantics in Applications

Rejected as the default architecture because EXP-0003 reproduced the same
semantic contract in two fixture domains and the current Kernel failed the
neutral reduction oracle. Application-owned copies would duplicate a reasoning
responsibility.

This evidence establishes initial portability, not proof that the concepts are
universal primitives. They are therefore accepted as derived reasoning
contracts rather than primitives.

### Promote a Generic Relationship Primitive

Rejected. The experiments validate a specific contextual reasoning
relationship, not the universality, irreducibility, or independence of a generic
Kernel relationship primitive.

### Select Weighted Balance as the Universal Policy

Rejected. EXP-0003 used it to test policy separation. The experiment did not
establish its formula as universally correct.

### Preserve the Constant Supportive Evaluator as the Default

Rejected. EXP-0002 and EXP-0003 reproduced cases in which that behavior maps
different required directions to identical public judgments.

## Consequences

### Positive

- support, contradiction, and unresolved direction become executable and
  type-safe;
- canonical evidence remains reusable across hypotheses;
- confidence no longer risks carrying hidden polarity;
- conflict policies can evolve independently from semantic contracts;
- judgments retain auditable contributions;
- applications share reasoning semantics without parsing prose;
- EXP-0002 and EXP-0003 become permanent regression evidence.

### Negative

- the reasoning API and judgment model become more explicit and more complex;
- current constructors, equality, snapshots, and consumers require staged
  migration;
- callers must supply contextual relationships rather than bare evidence sets;
- no universal default policy is available from this decision;
- compatibility code may exist temporarily;
- the two fixture domains do not eliminate the need for future cross-domain
  observation.

### Risks

- relationship and judgment direction could become duplicated sources of truth
  if policy output is not derived and validated consistently;
- conclusion prose could drift from structured direction;
- a policy threshold could be mistaken for ontology;
- duplicate or dependent evidence could inflate numerical policies;
- unresolved reasons could grow into an unstable public vocabulary;
- implementation could accidentally promote derived concepts as primitives.

The required specification and regression suite must address these risks.

## Validation

The accepted architecture will be validated by an implementation specification
followed by small implementation tasks.

The implementation evidence must include:

1. all 24 EXP-0003 core traces;
2. I1–I12 integrity behavior;
3. the three reduction collisions as legacy characterization;
4. all three ablation invariants as contract tests;
5. deterministic permutation and repetition;
6. full repository build;
7. context-independent replication of the migrated production path.

## Governance

Acceptance of this ADR authorizes preparation of the implementation
specification.

It does not directly authorize production code, primitive promotion, selection
of a universal policy, consumer migration, or legacy removal.

Each subsequent implementation PR must identify the accepted specification
section it implements and preserve a buildable repository.
