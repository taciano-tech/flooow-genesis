# EXP-0003 — Evidence Relationship Validation

**Status:** Planned — Protocol Review

**Protocol Version:** 1.0

**Source RFC:** RFC-0004

## 1. Objective

Determine whether a contextual evidence-to-hypothesis relationship and an
explicit judgment direction provide a deterministic, explainable, and
domain-portable solution to the semantic gap reproduced by EXP-0002, and
whether each proposed semantic dimension is necessary.

## 2. Research Question

Can a prototype outside the production Kernel represent support,
contradiction, and unresolved judgment direction across multiple hypotheses,
policies, and domains without changing canonical evidence, parsing prose, or
encoding polarity in confidence?

## 3. Hypothesis

A contextual relationship with direction `SUPPORTS | CONTRADICTS`, combined
with an explicit judgment direction `SUPPORTED | CONTRADICTED | UNRESOLVED`,
is sufficient to reproduce the required reasoning semantics under multiple
deterministic policies and domains while preserving every contribution.
Neither relationship direction nor judgment direction can be removed without
losing required executable semantics.

## 4. Null Hypothesis

The proposed contextual relationship and explicit judgment direction are not
both sufficient and necessary to represent the required reasoning semantics
under the declared experimental conditions.

## 5. Experimental Boundary

The first execution must use an experimental harness outside production Kernel
sources. It may reuse public Kernel language and model contracts, but it must
not modify them or create production substitutes.

The experiment does not authorize:

- a Kernel implementation or migration;
- primitive promotion;
- an ADR;
- natural-language interpretation;
- probabilistic or Bayesian inference;
- source-authority ranking;
- persistence, external integrations, or autonomous action;
- a universal conflict threshold or confidence formula.

## 6. Frozen Baseline

Protocol design begins from main commit
`af9fbd1c33a9e274319d179052da3d5a1c171257`, which contains:

- the completed and independently reproduced EXP-0002 evidence;
- RFC-0004 as the accepted draft experimental proposal;
- the unchanged production reasoning Kernel.

Execution must record its own exact source commit before the first test run.

## 7. Experimental Vocabulary

The harness must use explicit experimental types whose names cannot be confused
with accepted Kernel concepts.

```text
ExperimentalEvidenceRelationship
  evidenceId
  hypothesisId
  direction: SUPPORTS | CONTRADICTS

ExperimentalJudgment
  hypothesisId
  direction: SUPPORTED | CONTRADICTED | UNRESOLVED
  reasonCode
  evaluatedRelationships
  policyId
  trace
```

The existing canonical `Evidence` value remains unchanged. Relationship
direction is contextual to an evidence-hypothesis pair. The same evidence may
therefore participate in multiple relationships without being copied or
mutated.

`reasonCode` must distinguish at least unanimous direction, conflict, numerical
tie, and insufficient weighted evidence without parsing trace prose.

The frozen reason vocabulary for this experiment is:

```text
UNANIMOUS_SUPPORT
UNANIMOUS_CONTRADICTION
CONFLICT
POSITIVE_BALANCE
NEGATIVE_BALANCE
BALANCED_CONFLICT
INSUFFICIENT_WEIGHT
```

The harness may record a policy-specific numeric assessment only when its name,
formula, range, and interpretation are declared in the trace. It must not call
that assessment universal judgment confidence or alter the existing
`Confidence` contract.

## 8. Prototype Location and Ownership

The implementation task must propose a clearly isolated experimental module or
source set. Production packages under
`platform/foundation/kernel/src/main` are forbidden.

The harness must not be published as an application API. Its types remain
experimental until evidence and an ADR authorize another classification.

## 9. Required Invariants

1. Every relationship references one existing evidence item and the evaluated
   hypothesis.
2. Relationship direction is exactly `SUPPORTS` or `CONTRADICTS`.
3. Judgment direction is exactly `SUPPORTED`, `CONTRADICTED`, or `UNRESOLVED`.
4. `UNRESOLVED` is produced by evaluation and is never stored as relationship
   direction.
5. Confidence is never used as a polarity flag.
6. Every input relationship remains present in the result and trace.
7. Identical inputs, policy, and clock produce structurally identical results.
8. Input permutation does not change direction, assessments, or canonical
   trace ordering.
9. Observation or conclusion prose cannot change executable direction.
10. Missing, orphaned, identical duplicate, or contradictory duplicate pair
    relationships are handled explicitly and never silently treated as
    support or counted more than once.
11. A relationship for one hypothesis has no implicit effect on another.
12. Domain vocabulary does not enter experimental reasoning contracts or
    policies.

## 10. Experimental Policies

The same relationships and judgment-result shape must be evaluated with two
policies. Policy choice is an independent variable.

### Policy P1: Strict Conflict

- only supporting relationships: `SUPPORTED`;
- only contradicting relationships: `CONTRADICTED`;
- both directions present: `UNRESOLVED`;
- no valid relationship: explicit invalid input; no judgment is produced.

This policy tests conservative semantics without comparing magnitudes.
Relationships with confidence `0.0` still count as direction presence under
P1 because confidence does not encode polarity.

### Policy P2: Weighted Balance

- sum confidence magnitudes separately for supporting and contradicting
  relationships;
- supporting total greater than contradicting total: `SUPPORTED`;
- contradicting total greater than supporting total: `CONTRADICTED`;
- equal totals: `UNRESOLVED`;
- retain both totals and every contribution in the trace.

P2 evaluates only relationships whose `hypothesisId` equals the single
hypothesis being evaluated; any relationship for another hypothesis makes the
request invalid rather than being silently filtered. Each valid pair
contributes exactly once using the confidence of its referenced evidence.
Identical and contradictory duplicate pairs are invalid.

Totals are computed after sorting by `evidenceId`, converting each finite
`Confidence.value` with `BigDecimal.valueOf`, and using exact decimal addition.
Totals are compared exactly, without a tolerance. The trace retains canonical
inputs, each decimal contribution, and both totals. Confidence construction
already prevents NaN and out-of-range fixture values.

When relationships exist but every contribution has magnitude `0.0`, P2 emits
`UNRESOLVED` with reason `INSUFFICIENT_WEIGHT`. Equal non-zero opposing totals
emit `UNRESOLVED` with reason `BALANCED_CONFLICT`.

This policy is experimental. Passing results do not make its formula universal
or authorize it for production.

Both policies must define any numeric assessment independently. Their formulas
and interpretations must not become hidden parts of direction semantics.

## 11. Domain Tracks

### Domain M: Marketplace Replenishment

Hypothesis M1:

```text
Replenishment will arrive before the projected stockout.
```

This track reuses the semantic fixture meanings from EXP-0002 without parsing
their descriptions.

### Domain S: Service Reliability

Hypothesis S1:

```text
The payment service will remain within its availability objective during the
next observation window.
```

Controlled operational observations will include a healthy synthetic probe and
an elevated error-rate measurement. These values are fixtures only; no live
monitoring occurs.

The experimental contracts and policies must be identical across domains M and
S. Only fixture vocabulary and identifiers may differ.

## 12. Scenario Matrix

### Core Direction Scenarios

| ID | Relationships | P1 expected | P2 expected |
|---|---|---|---|
| C1 | one support at `0.8` | `SUPPORTED` | `SUPPORTED` |
| C2 | one contradiction at `0.8` | `CONTRADICTED` | `CONTRADICTED` |
| C3 | support `0.75`, contradiction `0.75` | `UNRESOLVED` | `UNRESOLVED` |
| C4 | support `0.8`, contradiction `0.5` | `UNRESOLVED` | `SUPPORTED` |
| C5 | support `0.4`, contradiction `0.9` | `UNRESOLVED` | `CONTRADICTED` |
| C6 | supports `0.2`, `0.3`, `0.5`; contradictions `0.4`, `0.6` | `UNRESOLVED` | `UNRESOLVED` |

Run C1–C6 in both domains.

### Context and Integrity Scenarios

| ID | Condition | Expected observation |
|---|---|---|
| I1 | Same evidence relates to M1 and M2: “Replenishment will arrive after the projected stockout.” | It `SUPPORTS` M1 and `CONTRADICTS` M2, or the reverse for opposite evidence, without copying or mutating evidence. |
| I2 | Relationship references another hypothesis during evaluation | Explicit rejection; no partial judgment. |
| I3 | Relationship references absent evidence | Explicit rejection; no partial judgment. |
| I4 | Same pair appears twice with opposite direction | Explicit rejection under this protocol. |
| I5 | No relationships are supplied | Both policies reject the request and produce no judgment. |
| I6a | support `0.0`, no contradiction | P1: `SUPPORTED`; P2: `UNRESOLVED/INSUFFICIENT_WEIGHT`. |
| I6b | support `1.0`, no contradiction | Both policies: `SUPPORTED`. |
| I6c | contradiction `0.0`, no support | P1: `CONTRADICTED`; P2: `UNRESOLVED/INSUFFICIENT_WEIGHT`. |
| I6d | support `1.0`, contradiction `0.0` | P1: `UNRESOLVED/CONFLICT`; P2: `SUPPORTED/POSITIVE_BALANCE`. |
| I7 | Observation prose is changed but relationships are unchanged | Structurally identical executable result. |
| I8 | Relationship insertion order is permuted | Structurally identical result and canonical trace. |
| I9 | Execution is repeated with identical input and fixed clock | Structurally identical result. |
| I10 | All supplied relationships are valid but reference another hypothesis | Explicit request rejection; distinct from an orphaned evidence reference. |
| I11 | Identical pair and direction are supplied twice | Explicit rejection; contribution is not inflated. |
| I12 | Existing Evidence fixtures enter the adapter | IDs, observation IDs, confidence, and timestamps remain unchanged; no default relationship is created. |

### Reduction Scenarios

Attempt C1–C5 using only the existing executable `Evidence`, `EvidenceSet`,
`AggregatedEvidence`, `Judgment`, aggregator, evaluator, confidence policies,
and free-text fields, while respecting all invariants.

Use a neutral oracle: record `PASS` if existing contracts express every
direction and complete trace without adding equivalent relationship or
judgment-direction semantics, and `FAIL` otherwise. Both outcomes are valid
evidence. Do not modify the frozen baseline to influence the result.

### Ablation Scenarios

Run the prototype after independently removing relationship direction,
judgment direction, and retained relationships from the result. A dimension is
necessary only if its removal causes at least one predeclared scenario or
invariant to fail and no existing contract supplies equivalent semantics.

## 13. Independent Variables

- relationship direction;
- evidence confidence magnitude;
- relationship composition and count;
- hypothesis reference;
- input ordering;
- policy P1 or P2;
- marketplace or service-reliability fixture domain;
- observation prose while semantic relationships remain fixed.

## 14. Dependent Variables

- judgment direction;
- complete retained relationships;
- canonical trace;
- policy-specific totals and assessments;
- validation errors;
- equality across repeated and permuted executions;
- equality of contract shape across domains;
- ability to reduce the behavior to existing contracts.

## 15. Controlled Variables

- source commit and dependency versions;
- production Kernel implementation;
- experimental contract definitions;
- policy definitions;
- identifiers, timestamps, and fixed clock;
- fixtures within each scenario;
- serialization and canonical ordering rules;
- test and build commands.

## 16. Evidence Collection

Commit all of the following:

- human-readable input fixtures for every scenario;
- expected results declared before writing the executable harness;
- observed snapshots containing relationships, direction, policy, totals,
  assessments, validation errors, and trace;
- automated tests that compare complete observed snapshots;
- repeated and permutation results;
- cross-domain contract comparison;
- reduction-attempt record;
- source commit and complete environment metadata;
- isolated test and full-build commands with task outputs forced fresh;
- test counts, failures, errors, and skipped counts;
- every semantic and environmental divergence.

## 17. Success Criteria

The experiment hypothesis gains support only if:

- C1–C6 match their declared result under both policies and domains;
- I1–I12 satisfy their expected observations;
- all invariants hold;
- both policies consume identical relationship contracts and return identical
  judgment-result shapes;
- every contribution remains auditable;
- confidence magnitude is demonstrably independent from direction;
- no prose parsing or domain-specific branch affects reasoning semantics;
- cross-domain execution requires no semantic contract change and provides
  initial evidence against marketplace specificity, without claiming universal
  proof;
- the reduction result follows its neutral oracle and is preserved regardless
  of outcome;
- ablation results identify which proposed dimensions are necessary and which
  are reducible;
- fresh execution and independent replication reproduce the evidence.

Passing these criteria supports the RFC's proposed semantics. It does not by
itself promote a primitive or authorize a Kernel implementation.

## 18. Failure Criteria

The null hypothesis gains support if any of the following occurs:

- a required direction cannot be represented explicitly;
- the same evidence cannot relate differently to separate hypotheses;
- policy selection requires changing semantic contracts;
- either policy discards a contribution;
- confidence is used as polarity;
- prose or domain vocabulary affects direction;
- insertion order or repetition changes the result;
- validation accepts orphaned, identical duplicate, or contradictory duplicate
  pairs silently;
- the service-reliability track requires different semantics;
- existing contracts pass the reduction test without equivalent new semantics;
- independent replication contradicts the primary evidence.

Failure is preserved as a valid experimental result and must not be fixed by
weakening fixtures or acceptance criteria.

## 19. Threats to Validity

- the two selected domains may not establish full universality;
- the logical alternative in I1 may conceal hypothesis-dependency assumptions;
- P1 and P2 may not represent useful real-world policies;
- arithmetic summation may expose floating-point ordering effects;
- canonical serialization may hide rather than solve nondeterminism;
- duplicate evidence may inflate totals without source-dependence modeling;
- `UNRESOLVED` may conflate conflict, tie, and insufficient evidence;
- experimental type names may bias later primitive classification;
- passing prototype tests may be mistaken for production readiness.
- using two domains may be mistaken for proof of universality rather than
  initial portability evidence.

## 20. Execution Phases

### Phase 1: Protocol Freeze

Review and merge this protocol with no executable prototype.

### Phase 2: Fixture Freeze

Commit inputs and expected semantic snapshots before writing the executable
harness. Expected values must be implementation-independent, and observed
snapshots must be separate artifacts that never overwrite expected snapshots.

### Phase 3: Prototype Characterization

Implement the smallest isolated harness, execute all tracks fresh, and preserve
observed results without changing production Kernel sources.

### Phase 4: Independent Replication

A context-independent engineer executes isolated tests and the full build from
the recorded commit with `--rerun-tasks`, compares every snapshot, and reports
all divergences.

### Phase 5: Evaluation

Assess the hypothesis, null hypothesis, RFC-0002 candidate criteria, migration
constraints, and whether evidence is sufficient for an ADR, rejection, or
continued investigation.

## 21. Architectural Decision Matrix

| Reproduced observation | Decision |
|---|---|
| Semantics pass all tracks and candidate classification is supported | Prepare an ADR; do not implement yet |
| Semantics pass but universality or irreducibility remains uncertain | Continue cross-domain investigation |
| Application-local composition is sufficient without universal duplication | Keep the concepts outside the Kernel |
| One policy passes only by changing shared contracts | Revise or reject policy separation |
| Relationship or judgment direction is insufficient | Revise or reject RFC-0004 |
| Evidence or replication is incomplete | Continue investigation |

## 22. Replication Requirement

The independent replicator must receive only the frozen commit, this protocol,
the committed fixtures and snapshots, and execution instructions. The
replicator must not receive the primary engineer's reasoning narrative before
recording results.

Replication must report every core, integrity, domain, policy, permutation, and
reduction scenario separately.

## 23. Initial Disposition

**Ready for protocol review only.**

No fixture, prototype, result, architectural decision, or Kernel change is
authorized by this document. After protocol approval, the next task is Phase 2:
freeze implementation-independent fixtures and expected semantic snapshots.
