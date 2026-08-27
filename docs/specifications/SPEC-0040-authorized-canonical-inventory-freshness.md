# SPEC-0040: Authorized Canonical Inventory Freshness

Status: Proposed

Date: 2026-08-20

Source decision: ADR-0040

## Objective

Assess whether one linked, authorized canonical inventory observation satisfies
one exact, versioned temporal policy at the same instant used by its retained
source-authority assessment, without inferring health, current state,
confidence, business availability, or Safe ATP.

## Authorized next implementation

Acceptance authorizes TASK-0137 only:

1. add one pure freshness production file to the existing
   `inventory-source-authority` module;
2. add a redacted policy version, provider-timestamp mode, scoped policy,
   successful assessment, controlled result, and assessor;
3. use only the linked evidence and its retained authority evaluation time;
4. enforce exact scope, policy-version, effective-time, precision, timestamp,
   ordering, and inclusive-window rules in deterministic order;
5. retain the same evidence and policy instances on success;
6. prove both provider-time modes, every boundary and failure, determinism,
   privacy, minimal shape, and module isolation with focused tests;
7. leave all persistence, runtime, API, connector, Marketplace, and Kernel
   behavior unchanged.

No connection-health signal, source rank, winner, reconciliation, aggregate,
reservation, unconfirmed demand, confidence, ATP, persistence, API, runtime,
AI, or Kernel change is authorized.

## Policy version

```text
CanonicalInventoryFreshnessPolicyVersion
```

The version uses the same rules as the existing source-authority policy
version: caller-supplied, NFC-normalized, non-empty, trimmed, control-free,
and at most 64 UTF-8 bytes. It provides value equality, an internal persistence
encoding, and `[REDACTED]` rendering. No random or clock-derived value is
created.

## Provider timestamp mode

```text
CanonicalInventorySourceTimestampMode
  REQUIRE_SOURCE_UPDATED
  DO_NOT_ASSESS_SOURCE_UPDATED
```

The enum is exhaustive and has no fallback or ordinal persistence contract.

## Scoped freshness policy

```text
CanonicalInventoryFreshnessPolicy(
  version,
  authorityPolicyVersion,
  organizationId,
  connectionId,
  target,
  measure,
  sourceTimestampMode,
  maximumSourceAge?,
  maximumSourceFutureLead?,
  maximumCommitAge,
  maximumProjectionAge,
  effectiveFrom,
  effectiveUntil
)
```

The policy requires:

- the canonical inventory balance capability indirectly through the retained
  authority policy; no duplicate capability string is added;
- `effectiveUntil > effectiveFrom`;
- positive `maximumCommitAge` and `maximumProjectionAge`;
- both maximum ages at most 31 days and aligned to whole microseconds;
- under `REQUIRE_SOURCE_UPDATED`, a positive `maximumSourceAge` at most 31
  days and a non-negative `maximumSourceFutureLead` at most five minutes;
- under `DO_NOT_ASSESS_SOURCE_UPDATED`, both nullable source durations absent;
- every present source duration aligned to whole microseconds.

Zero future lead is valid. Zero source, commit, or projection age is invalid.
Negative, nanosecond-only, excessive, mismatched-null, infinite, or implicit
default durations are invalid.

The aggregate is immutable, value-equal, and renders `[REDACTED]`.

## Assessment API

```text
CanonicalInventoryFreshnessAssessor.assess(
  evidence: AuthorizedCanonicalInventoryObservationEvidence,
  policy: CanonicalInventoryFreshnessPolicy
): CanonicalInventoryFreshnessResult
```

Define:

```text
authority  = evidence.authority
candidate  = authority.candidate
observation = evidence.observation
evaluatedAt = authority.evaluatedAt
```

The assessor accepts no `Clock`, second evaluation time, repository, mutable
state, caller ordering, or environmental input.

## Deterministic validation order

Checks occur in this exact order:

1. candidate organization equals policy organization;
2. candidate connection equals policy connection;
3. candidate target equals policy target;
4. candidate selected measure equals policy measure;
5. authority policy version equals the policy's retained authority-policy
   version;
6. `evaluatedAt >= effectiveFrom`;
7. `evaluatedAt < effectiveUntil`;
8. evaluation time, commit time, projection time, and the provider timestamp
   when its mode requires assessment are aligned to whole microseconds;
9. required `sourceUpdatedAt` is present;
10. required source time is not after
    `evaluatedAt + maximumSourceFutureLead`;
11. required source time is not before
    `evaluatedAt - maximumSourceAge`;
12. `sourceCommittedAt <= projectedAt`;
13. source commit time is not after `evaluatedAt`;
14. source commit time is not before
    `evaluatedAt - maximumCommitAge`;
15. projection time is not after `evaluatedAt`;
16. projection time is not before
    `evaluatedAt - maximumProjectionAge`;
17. return successful assessment.

The lower and upper limits are inclusive. Comparisons must be overflow-safe at
the supported `Instant` extremes. The first disagreement returns its controlled
result. No timestamp is rounded, truncated, replaced, reordered, or ignored
except that provider time is deliberately outside assessment when the accepted
mode is `DO_NOT_ASSESS_SOURCE_UPDATED`.

## Controlled result

```text
sealed interface CanonicalInventoryFreshnessResult {
  Fresh(assessment)
  OrganizationMismatch
  ConnectionMismatch
  TargetMismatch
  MeasureMismatch
  AuthorityPolicyVersionMismatch
  PolicyNotYetEffective
  PolicyExpired
  TemporalPrecisionMismatch
  SourceTimestampRequired
  SourceTimestampTooFarInFuture
  SourceTimestampTooOld
  CommitAfterProjection
  CommitTimestampInFuture
  CommitTimestampTooOld
  ProjectionTimestampInFuture
  ProjectionTimestampTooOld
}
```

Every result renders `[REDACTED]`. Failures retain no partial assessment,
timestamp, duration, age, identifier, target, measure, or policy detail.

## Successful assessment

```text
CanonicalInventoryFreshnessAssessment(
  evidence,
  policy
)
```

Internal construction reproduces every successful invariant. The assessment
retains the exact two input instances and has exactly those two fields.

It adds no copied evaluation time, timestamp, age, duration, quantity, source,
quality, score, health, status, reason text, current-state marker, or derived
business value. Its rendering is `[REDACTED]`.

## Provider-time semantics

### Required provider time

With `evaluatedAt = 12:00:00Z`, `maximumSourceAge = 10 minutes`, and
`maximumSourceFutureLead = 2 minutes`, source time is accepted inclusively from
`11:50:00Z` through `12:02:00Z`.

Missing source time, `11:49:59.999999Z`, and `12:02:00.000001Z` fail with the
corresponding controlled result.

### Provider time not assessed

Under `DO_NOT_ASSESS_SOURCE_UPDATED`, both source durations are absent. A
missing, old, or future provider timestamp cannot change the outcome. Commit
and projection windows still apply. The successful result means only that the
observation satisfies this policy; it makes no provider-occurrence freshness
claim.

No automatic source-to-commit fallback exists in either mode.

## Commit and projection semantics

Commit and projection are always assessed. Each must lie in its own inclusive
window ending at `evaluatedAt`. Commit must not occur after projection.

The contract imposes no ordering between provider time and either Genesis
time. It performs no duration arithmetic between provider time and commit time.

## Precision and privacy

- accepted timestamps and durations use whole-microsecond precision;
- values are compared exactly and never formatted or parsed for decisions;
- exception messages are generic and contain no value;
- all policy, assessment, assessor, and result renderings are redacted;
- no organization, connection, target, measure, quantity, policy version,
  timestamp, duration, or age is exposed by rendering.

## Implementation scope

TASK-0137 may add only:

- `CanonicalInventoryFreshness.kt` in the existing authority package;
- `CanonicalInventoryFreshnessTest.kt`;
- TASK-0137 evidence.

No Gradle dependency, settings entry, existing production type, migration,
runtime wiring, or other module may change.

## Test plan

TASK-0137 proves at least:

1. the module dependency allow-lists remain unchanged and exact;
2. production bytecode references no Kernel or Marketplace type;
3. policy-version normalization, length, equality, encoding, and redaction;
4. policy interval and every duration invariant;
5. both provider timestamp modes and their exact nullable-duration coupling;
6. organization, connection, target, measure, and authority-policy version
   mismatches fail in accepted order;
7. policy not-yet-effective and expired boundaries use `[from, until)`;
8. non-microsecond evaluation or evidence time fails closed;
9. required provider time is never replaced by commit or projection time;
10. source age and future-lead lower/upper boundaries are inclusive;
11. provider time cannot affect `DO_NOT_ASSESS_SOURCE_UPDATED` results;
12. commit-after-projection fails before age classification;
13. commit and projection future and old boundaries are independent and
    inclusive;
14. supported `Instant` extremes produce controlled results without arithmetic
    overflow;
15. success retains the same evidence and policy instances;
16. inconsistent internal assessment construction is rejected;
17. value-equal inputs are deterministic and immutable;
18. successful assessment has exactly evidence and policy fields;
19. every new rendering is `[REDACTED]`;
20. no clock, health, rank, winner, reconciliation, availability, confidence,
    ATP, persistence, API, runtime, action, AI, or Kernel change is introduced;
21. `git diff --check` and the complete repository build remain green.

## Remaining boundary

Connection and provider health, execution history, source succession, priority,
canonical current-state selection, tolerance, reconciliation, aggregation,
reservations, unconfirmed demand, business availability, Inventory Confidence,
Safe ATP, graceful degradation, Seller Entitlement, publication, mutation,
recommendation, authority to act, outcome, and learning require later accepted
specifications.

## Acceptance

Merging ADR-0040 and SPEC-0040 authorizes TASK-0137 only. It changes no runtime
behavior and authorizes no source-health conclusion, current-state winner,
business-stock decision, external action, AI, or Kernel modification.
