# SPEC-0001: Evidence Semantics Implementation

**Version:** 0.1

**Status:** Accepted

**Date:** 2026-08-04

**Source decision:** ADR-0002

## Objective

Define the exact additive production contracts, validation behavior, migration
sequence, and regression evidence required to implement ADR-0002 without
breaking the current reasoning API or introducing an implicit policy.

This specification does not itself modify production code. Its acceptance
authorizes TASK-0048 only. Each later task requires the previous task to be
merged, validated, and evidence-complete before it may begin.

## Scope

The specification covers:

- contextual evidence-to-hypothesis relationships;
- structured judgment direction and reason;
- validated directional evaluation requests;
- deterministic retained-contribution traces;
- two explicit named policies ported from EXP-0003;
- a parallel API and staged migration;
- compatibility, error, testing, rollout, and rollback requirements.

It does not cover source authority, dependent evidence, Bayesian inference,
persistence, external APIs, autonomous action, a generic Relationship
primitive, or retirement of the legacy evaluator.

## Package Ownership

All accepted production contracts belong to:

```text
io.flooow.kernel.reasoning
```

Canonical `io.flooow.kernel.model.Evidence` remains unchanged.

No Marketplace Operations or Service Reliability vocabulary may appear in
production reasoning contracts.

## Production Types

### RelationshipDirection

```kotlin
enum class RelationshipDirection {
    SUPPORTS,
    CONTRADICTS
}
```

No neutral or unresolved relationship value is permitted in version 1.
Absence, invalid input, and unresolved evaluation remain distinct conditions.

### EvidenceRelationship

```kotlin
data class EvidenceRelationship(
    val id: Identifier,
    val evidenceId: Identifier,
    val hypothesisId: Identifier,
    val direction: RelationshipDirection
)
```

Invariants:

- all identifiers use their existing nonblank invariant;
- relationship identity is unique within one request;
- one evidence-hypothesis pair occurs at most once;
- direction is contextual and does not mutate canonical Evidence.

### JudgmentDirection

```kotlin
enum class JudgmentDirection {
    SUPPORTED,
    CONTRADICTED,
    UNRESOLVED
}
```

### JudgmentReason

```kotlin
enum class JudgmentReason {
    UNANIMOUS_SUPPORT,
    UNANIMOUS_CONTRADICTION,
    CONFLICT,
    POSITIVE_BALANCE,
    NEGATIVE_BALANCE,
    BALANCED_CONFLICT,
    INSUFFICIENT_WEIGHT
}
```

Reason is structured and policy-produced. Version 1 is closed to the reproduced
EXP-0003 vocabulary. Adding a reason requires contract review and regression
evidence.

### EvaluatedRelationship

```kotlin
data class EvaluatedRelationship(
    val relationship: EvidenceRelationship,
    val confidence: Confidence
)
```

The confidence is copied from the referenced canonical Evidence at validation
time. It is magnitude, never polarity.

### PolicyMeasure

```kotlin
data class PolicyMeasure(
    val name: String,
    val value: BigDecimal,
    val interpretation: String
)
```

Names and interpretations must be nonblank. Measures are policy-specific trace
data, not universal Judgment confidence. Measure names must be unique within a
policy decision.

### StructuredJudgment

```kotlin
data class StructuredJudgment(
    val id: Identifier,
    val hypothesisId: Identifier,
    val direction: JudgmentDirection,
    val reason: JudgmentReason,
    val evaluatedRelationships: List<EvaluatedRelationship>,
    val policyId: String,
    val measures: List<PolicyMeasure>,
    val createdAt: Timestamp
)
```

Version 1 deliberately excludes free-text conclusion and universal confidence.
Explanation rendering and compatibility projection belong to later migration
tasks. The legacy `Judgment` remains unchanged during the parallel stage.

Invariants:

- policyId is nonblank;
- evaluatedRelationships is nonempty and canonically ordered by relationship id;
- every relationship references hypothesisId;
- relationship identifiers and evidence-hypothesis pairs are unique;
- measures are canonically ordered by name;
- measure names are unique;
- direction/reason compatibility is validated by the selected policy;
- createdAt comes from the injected clock.

## Request and Result

### DirectionalEvaluationRequest

```kotlin
data class DirectionalEvaluationRequest(
    val hypothesis: Hypothesis,
    val evidenceSet: EvidenceSet,
    val relationships: List<EvidenceRelationship>
)
```

The request contains the canonical evidence set once. Relationships reference
it by identifier.

### ValidationError

```kotlin
enum class DirectionalValidationError {
    NO_RELATIONSHIPS,
    EVIDENCE_NOT_FOUND,
    HYPOTHESIS_MISMATCH,
    DUPLICATE_RELATIONSHIP_ID,
    IDENTICAL_DUPLICATE_RELATIONSHIP,
    CONTRADICTORY_DUPLICATE_RELATIONSHIP
}
```

```kotlin
data class ValidatedDirectionalEvaluationRequest internal constructor(
    val hypothesis: Hypothesis,
    val relationships: List<EvaluatedRelationship>
)

sealed interface DirectionalRequestValidation {
    data class Valid(
        val request: ValidatedDirectionalEvaluationRequest
    ) : DirectionalRequestValidation

    data class Invalid(
        val error: DirectionalValidationError
    ) : DirectionalRequestValidation
}

class DirectionalRequestValidator {
    fun validate(
        request: DirectionalEvaluationRequest
    ): DirectionalRequestValidation
}
```

Only the validator constructs a `ValidatedDirectionalEvaluationRequest`. It
copies confidence from canonical Evidence, applies canonical relationship
ordering, and never creates a judgment.

### DirectionalEvaluationResult

```kotlin
sealed interface DirectionalEvaluationResult {
    data class Success(val judgment: StructuredJudgment) :
        DirectionalEvaluationResult

    data class Invalid(val error: DirectionalValidationError) :
        DirectionalEvaluationResult
}
```

Invalid input produces no partial judgment or policy trace.

## Validation Order

A dedicated `DirectionalRequestValidator` runs before policy evaluation.

The deterministic precedence is:

1. no relationships;
2. duplicate relationship identifier;
3. absent evidence reference;
4. hypothesis mismatch;
5. contradictory duplicate evidence-hypothesis pair;
6. identical duplicate evidence-hypothesis pair.

If multiple invalid conditions exist, the earliest condition wins. Validation
must not deduplicate or repair input.

After validation, relationships are sorted by relationship id and converted to
`EvaluatedRelationship` values using the referenced Evidence confidence.

## Policy Contract

```kotlin
interface DirectionalEvaluationPolicy {
    val id: String
    val supportedOutcomes: Set<DirectionalOutcome>

    fun evaluate(
        hypothesis: Hypothesis,
        relationships: List<EvaluatedRelationship>
    ): DirectionalPolicyDecision
}

data class DirectionalOutcome(
    val direction: JudgmentDirection,
    val reason: JudgmentReason
)

data class DirectionalPolicyDecision(
    val direction: JudgmentDirection,
    val reason: JudgmentReason,
    val measures: List<PolicyMeasure>
)
```

`DirectionalPolicyDecision` requires nonblank measure fields and unique measure
names. A policy returns a decision, not a `StructuredJudgment`.

The engine constructor requires an explicit policy. There is no default
constructor and no service-locator fallback.

```kotlin
class DirectionalHypothesisEvaluator(
    private val validator: DirectionalRequestValidator,
    private val policy: DirectionalEvaluationPolicy,
    private val clock: Clock,
    private val judgmentIdFactory: DirectionalJudgmentIdFactory
) {
    fun evaluate(
        request: DirectionalEvaluationRequest
    ): DirectionalEvaluationResult
}
```

The evaluator validates first, returns `Invalid` unchanged for invalid input,
and invokes the policy only with a validated request. It verifies that the
returned direction/reason pair belongs to `policy.supportedOutcomes`; a policy
contract violation is a programmer error reported as `IllegalStateException`,
never as invalid caller input. The evaluator owns judgment construction,
retained relationships, `createdAt`, and judgment ID generation.

`DirectionalJudgmentIdFactory` is an internal pure helper. It creates
`directional-judgment-<64 lowercase hexadecimal characters>` by SHA-256 hashing
a canonical length-prefixed UTF-8 payload containing, in order:

1. hypothesis identifier;
2. policy identifier;
3. for each relationship in relationship-id order: relationship identifier,
   evidence identifier, hypothesis identifier, direction name, and canonical
   confidence decimal.

The canonical confidence decimal is
`BigDecimal.valueOf(confidence.value).stripTrailingZeros().toPlainString()`;
therefore it has no exponent or insignificant trailing zero, and zero is `0`.
Every field is encoded as an unsigned 32-bit big-endian byte length followed by
exactly that many UTF-8 bytes, so field boundaries cannot collide. The hash is
over the concatenation of those encoded fields, with no header or separator.
`createdAt` is excluded. Identical semantic input and policy produce the same
identifier; changing any included semantic field changes the hashed payload.

Required known-answer vector: hypothesis `h-1`, policy `strict-conflict-v1`,
and one relationship with id `r-1`, evidence id `e-1`, hypothesis id `h-1`,
direction `SUPPORTS`, and confidence `1.0` (canonical decimal `1`) produces:

```text
directional-judgment-638170ba5a20f956792d6d773c3b67468eaa7dbbe54adb3c2a133ea3169c2676
```

## Initial Named Policies

Both policies are explicit named implementations for reproduced behavior. Their
presence does not make either universal or default.

### StrictConflictPolicy

Policy id: `strict-conflict-v1`.

Supported outcomes are exactly the three direction/reason pairs below.

- only SUPPORTS relationships: `SUPPORTED/UNANIMOUS_SUPPORT`;
- only CONTRADICTS relationships: `CONTRADICTED/UNANIMOUS_CONTRADICTION`;
- both directions: `UNRESOLVED/CONFLICT`;
- confidence magnitude does not alter direction;
- measures: empty.

### WeightedBalancePolicy

Policy id: `weighted-balance-v1`.

Supported outcomes are exactly the four direction/reason pairs below.

- sort contributions by evidence id;
- convert `Confidence.value` with `BigDecimal.valueOf`;
- sum SUPPORTS and CONTRADICTS separately with exact decimal addition;
- both totals zero: `UNRESOLVED/INSUFFICIENT_WEIGHT`;
- support greater: `SUPPORTED/POSITIVE_BALANCE`;
- contradiction greater: `CONTRADICTED/NEGATIVE_BALANCE`;
- equal nonzero totals: `UNRESOLVED/BALANCED_CONFLICT`.

Measures:

- `support-total`;
- `contradict-total`.

Every weighted decision contains exactly these two uniquely named measures;
every strict decision contains none.

Interpretations state that the value is the exact sum of confidence magnitudes
for the named relationship direction. No tolerance or hidden threshold is used.

## Determinism and Trace Rules

- relationships are retained by relationship-id order;
- P2 arithmetic iterates by evidence-id order;
- measures are serialized by name order;
- identical request and policy produce the same judgment identifier; with the
  same clock they produce structural equality;
- input permutation cannot affect result;
- no `toString()` output is a public serialization contract;
- no observation, hypothesis, or conclusion prose is parsed;
- no enum ordinal participates in logic or serialization.

A stable external serialization format is out of scope until a real consumer
requires it. Structural contract tests use object equality and explicit fields.

## Compatibility Strategy

### Legacy Contracts Preserved Initially

These remain source-compatible in the first implementation tasks:

- `Evidence`;
- `EvidenceSet`;
- existing `Judgment`;
- `HypothesisEvaluator`;
- `DeterministicHypothesisEvaluator`;
- existing confidence policies and reasoning engine.

The legacy evaluator remains characterized as non-conflict-aware. Documentation
must not describe it as implementing ADR-0002.

TASK-0048 adds classes and files only. It must not change the JVM names or
descriptors of existing public types, so it is expected to preserve both source
and binary compatibility. Verification requires:

- the full clean build;
- compilation and execution of existing consumer tests;
- a before/after public API signature inventory, produced with `javap` or an
  equivalent committed signature snapshot, showing existing JVM descriptors
  unchanged.

Any later binary-incompatible evolution requires a separate accepted
specification or ADR.

### No Implicit Adapter

No adapter may infer relationships from:

- evidence confidence;
- observation descriptions;
- hypothesis text;
- the legacy supportive conclusion;
- insertion order.

A caller that needs directional evaluation must supply explicit relationships.

### Future Compatibility Projection

A later specification may define how `StructuredJudgment` produces a legacy
`Judgment` explanation and confidence. Until then, no production projection is
authorized.

## Implementation Tasks

### TASK-0048: Add Contracts and Validation

Add enums, values, result types, request validator, and unit tests.

Constraints:

- no policy, evaluator, application, or legacy behavior change;
- preserve current constructors;
- capture and compare the existing public JVM signature inventory;
- port validation scenarios I2–I5, I10–I12 and duplicate-ID behavior.

### TASK-0049: Add Explicit Policies

Add the policy interface, StrictConflictPolicy, WeightedBalancePolicy, and unit
tests.

Constraints:

- no default policy;
- exact BigDecimal behavior;
- reject duplicate measure names at decision construction;
- verify that an invalid custom-policy outcome is rejected as a policy contract
  violation;
- port C1–C6 for both policies and confidence extremes.

### TASK-0050: Add Parallel Evaluator

Add DirectionalHypothesisEvaluator with explicit validator, policy, and clock.

Constraints:

- keep legacy evaluator unchanged;
- port all 24 core traces, permutation, repetition, and prose-independence
  regression tests;
- retain every evaluated relationship.

### TASK-0051: Migrate One Experimental Consumer

Migrate only a dedicated regression consumer, not the general Marketplace
Operations production path.

Constraints:

- explicit relationships;
- no legacy projection;
- compare migrated output with committed EXP-0003 evidence;
- independently replicate the migrated path.

### TASK-0052: Compatibility Review

Inventory all remaining legacy consumers, constructors, snapshots, and public
documentation. Decide whether to specify projection, deprecation, or continued
parallel operation.

No legacy removal occurs before this review.

## Test Plan

Minimum contract tests:

- relationship and judgment enum coverage;
- relationship identity and pair invariants;
- deterministic validation precedence;
- all six validation errors;
- zero and one confidence extremes;
- one Evidence related differently to two hypotheses;
- strict and weighted C1–C6;
- exact P2 totals;
- 120 C6 permutations;
- fixed-clock repetition;
- judgment-ID known-answer vector and decimal canonicalization cases;
- retained contribution equality;
- policy independence from prose;
- absence of default policy;
- legacy evaluator unchanged.

Repository validation for every implementation task:

```text
./gradlew clean build --rerun-tasks
```

Implementation tasks that reproduce experimental results also require
context-independent replication before their evidence is treated as complete.

## Rollout

- introduce contracts additively;
- keep new APIs internal to the reasoning module until TASK-0050 passes;
- migrate one controlled consumer only;
- publish no compatibility adapter before TASK-0052;
- retain the experimental harness as regression evidence;
- document every behavior difference from the legacy evaluator.

## Rollback

Each implementation task must remain independently reversible.

- TASK-0048 rollback removes only unused additive contracts;
- TASK-0049 rollback removes named policy implementations;
- TASK-0050 rollback removes the parallel evaluator;
- TASK-0051 rollback restores the controlled consumer to its prior path and
  snapshots and removes only its migration evidence, leaving the parallel API;
- TASK-0052 is review and documentation only; rollback reverts its review
  artifacts and decision without changing runtime behavior. Any proposed
  production change requires a new accepted specification;
- no rollback may modify frozen EXP-0002/EXP-0003 evidence;
- legacy behavior remains available throughout these stages.

## Definition of Done

This specification is complete when:

- ADR-0002 requirements map to exact production contracts;
- policy selection is explicit;
- validation and ordering are deterministic;
- compatibility and rollback are defined;
- implementation is divided into reviewable tasks;
- regression evidence maps to EXP-0002 and EXP-0003;
- independent review finds no architectural authorization gap.

Acceptance of this specification authorizes TASK-0048 only. Later tasks require
the previous task to be merged, validated, and evidence-complete.
