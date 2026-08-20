# SPEC-0038: Canonical Inventory Source Authority

Status: Proposed

Date: 2026-08-20

Source decision: ADR-0038

## Objective

Determine whether one accepted and selected canonical inventory source
candidate is authorized by one exact, versioned organizational policy at one
explicit evaluation time, without assessing freshness, health, correctness,
priority, current state, or business availability.

## Authorized next implementation

Acceptance authorizes TASK-0132 only:

1. add a pure `inventory-source-authority` application module;
2. depend only on the existing measure-selection contract and its transitive
   inventory evidence;
3. add immutable policy-version, policy, assessment, and controlled-result
   values;
4. evaluate exact organization, connection, capability, target, measure, and
   policy interval scope in deterministic order;
5. retain the exact selected candidate, policy, and evaluation time on
   success;
6. reproduce every successful invariant internally;
7. redact policy, assessment, assessor, and controlled results;
8. prove boundary instants, every mismatch, determinism, immutability,
   privacy, and scope with focused tests and complete repository verification.

No freshness duration, source timestamp, health signal, score, priority,
provider succession, reconciliation, current-state selection, aggregation,
business quantity, Inventory Confidence, Safe ATP, persistence, API,
connector, runtime, AI, or Kernel change is authorized.

## Module and package

```text
applications/inventory-source-authority
io.flooow.integration.inventory.authority
```

The module may depend on `applications:inventory-measure-selection`. It must
not depend on Marketplace Operations, persistence, API, connectors, or the
Kernel.

## Policy version

```text
CanonicalInventorySourceAuthorityPolicyVersion
```

The version is an immutable normalized string with these rules:

- Unicode NFC;
- non-empty;
- no leading or trailing whitespace;
- no ISO control character;
- at most 64 UTF-8 bytes;
- redacted rendering.

The value is supplied by trusted policy administration. It is not generated
by the assessor and carries no ordinal ordering semantics.

## Exact policy

```text
CanonicalInventorySourceAuthorityPolicy(
  version,
  organizationId,
  connectionId,
  capability,
  target,
  measure,
  effectiveFrom,
  effectiveUntil
)
```

Rules:

- `capability` is exactly `inventory.source-balance.read`;
- `effectiveUntil` is strictly later than `effectiveFrom`;
- the interval is half-open: `[effectiveFrom, effectiveUntil)`;
- all fields are mandatory;
- rendering is `[REDACTED]`;
- no source rank, weight, confidence, fallback, provider name, credential,
  quantity, freshness threshold, health state, owner, or action exists.

The exact `InventoryMappingTarget` and `CanonicalInventoryMeasure` types are
reused. The contract introduces no parallel item, location, unit, or measure
vocabulary.

## Assessment API

```text
CanonicalInventorySourceAuthorityAssessor.assess(
  candidate: SelectedCanonicalInventoryMeasure,
  policy: CanonicalInventorySourceAuthorityPolicy,
  evaluatedAt: Instant
): CanonicalInventorySourceAuthorityResult
```

The assessor reads no ambient clock. `evaluatedAt` is exact caller-supplied
evidence and is retained unchanged on success.

## Deterministic validation order

Checks occur in this exact order:

1. organization;
2. connection;
3. target;
4. measure;
5. `evaluatedAt < effectiveFrom`;
6. `evaluatedAt >= effectiveUntil`.

Both policy and candidate construction already guarantee the exact
`inventory.source-balance.read` capability. The assessor may reproduce that
invariant internally, but it defines no unreachable capability-mismatch
result.

The first disagreement returns its controlled result. There is no exception
fallback, null, permissive default, collection search, or caller-supplied
precedence.

## Controlled result

```text
sealed interface CanonicalInventorySourceAuthorityResult {
  Authorized(assessment)
  OrganizationMismatch
  ConnectionMismatch
  TargetMismatch
  MeasureMismatch
  PolicyNotYetEffective
  PolicyExpired
}
```

Every result renders `[REDACTED]`. Mismatch results retain no partial
assessment and disclose no identifiers, policy fields, target, measure, or
time.

## Successful assessment

```text
CanonicalInventorySourceAuthorityAssessment(
  candidate,
  policy,
  evaluatedAt
)
```

Internal construction requires all exact scope equalities and the half-open
interval invariant. The assessment retains the same candidate and policy
instances and adds no generated ID, clock time, copied quantity, score,
status, reason text, source quality, or derived policy.

Rendering is `[REDACTED]`.

## Accepted fixture

Given one selected `ON_HAND` candidate for organization `O`, connection `C`,
target `T`, and the source-balance capability, plus a policy with the exact
same scope and interval:

```text
effectiveFrom  = 2026-08-20T10:00:00Z
effectiveUntil = 2026-08-21T10:00:00Z
evaluatedAt    = 2026-08-20T10:00:00Z
```

the result is `Authorized`. At exactly `effectiveUntil`, the result is
`PolicyExpired`.

The candidate quantity is irrelevant to authorization. Negative, zero, and
positive exact quantities with identical scope produce the same authority
result.

## Security and privacy

- a credential, connection lifecycle, provider response, acceptance,
  selection, or adjudication cannot create policy inside the assessor;
- no raw identifier, target, measure, quantity, principal, or time appears in
  rendering;
- no policy is loaded from an environment variable or external system;
- no result grants inventory mutation or external execution authority.

## Implementation scope

TASK-0132 may add only:

- the new module declaration and build file;
- `CanonicalInventorySourceAuthority.kt` in the authority package;
- `CanonicalInventorySourceAuthorityTest.kt`;
- TASK-0132 evidence.

No existing production type requires modification.

## Test plan

TASK-0132 proves at least:

1. the module depends only on accepted inventory contracts;
2. production bytecode references no Kernel or Marketplace type;
3. policy version normalization and byte bound;
4. invalid capability and empty or inverted intervals fail construction;
5. exact matching scope is authorized;
6. start is inclusive and end is exclusive;
7. organization mismatch wins before every later mismatch;
8. connection, target, and measure mismatch each fail closed;
9. not-yet-effective and expired policy are distinct;
10. negative, zero, and positive quantity do not change authorization;
11. successful construction reproduces every invariant;
12. successful output retains the same candidate and policy instances;
13. value-equal inputs are deterministic and immutable;
14. policy, assessment, assessor, and every result render `[REDACTED]`;
15. assessment fields are exactly candidate, policy, and evaluated time;
16. no health, freshness, rank, weight, score, reconciliation, current-state,
    business quantity, confidence, ATP, recommendation, action, or AI exists;
17. no persistence, API, event, connector, runtime, migration, or UI is added;
18. no file under `platform/foundation/kernel` changes;
19. `git diff --check` and the complete repository build remain green.

## Remaining boundary

Policy administration and persistence, source ownership, connection health,
source/commit/projection freshness, authorized-candidate freshness assessment,
provider succession, priority, canonical current-state selection, tolerance,
materiality, reconciliation, aggregation, location/channel rollup, operational
reservations, unconfirmed demand, business availability, Inventory Confidence,
Safe ATP, Seller Entitlement, publication, mutation, recommendation, authority
to act, outcome, and learning require later accepted specifications.

## Acceptance

Merging ADR-0038 and SPEC-0038 authorizes TASK-0132 only. It changes no runtime
behavior and authorizes no source winner, business-stock decision, external
action, AI, or Kernel modification.
