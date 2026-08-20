# TASK-0133: Canonical Inventory Source Authority

## Result

Implemented ADR-0038 / corrected SPEC-0038 as a pure, production-inactive
`inventory-source-authority` module.

The module determines only whether one existing selected canonical inventory
candidate is eligible under one exact organizational source-authority policy
at one explicit time.

## Exact boundary

```text
SelectedCanonicalInventoryMeasure
  + CanonicalInventorySourceAuthorityPolicy
  + evaluatedAt
  -> Authorized assessment or typed mismatch
```

The policy is versioned and bounded over exact organization, connection,
source-balance capability, target, measure, and half-open effective interval.
The successful assessment retains the same candidate and policy instances and
the exact supplied evaluation time.

Validation order is deterministic:

```text
organization
  -> connection
  -> target
  -> measure
  -> not yet effective
  -> expired
  -> authorized
```

The capability is fixed by both candidate and policy construction, so no
unreachable capability-mismatch result exists.

## Dependency boundary

The module build enforces the corrected allow-list:

- production: organization context, integration control plane, inventory
  identity mapping, and inventory measure selection;
- test fixtures only: canonical observation and source acceptance.

No Marketplace, persistence, API, connector runtime, or Kernel project is
allowed.

## Deliberately absent

- source health, freshness, correctness, confidence, or score;
- source priority, weight, fallback, succession, or winner;
- reconciliation, current-state selection, aggregation, or tolerance;
- business availability, Inventory Confidence, Safe ATP, or publication;
- persistence, policy administration, API, runtime, event, connector, UI, AI,
  external action, or Kernel change.

## Validation

Focused tests prove policy normalization and bounds, exact scope, half-open
time boundaries, every reachable mismatch, deterministic precedence, quantity
independence, invariant reproduction, immutability, minimal aggregate shape,
redaction, and bytecode isolation.

Focused suite:

```text
CanonicalInventorySourceAuthorityTest
10 tests
0 failures
0 errors
0 skipped
BUILD SUCCESSFUL in 4m 32s
```

Broad repository build, excluding only the local PostgreSQL/Testcontainers
test task:

```text
BUILD SUCCESSFUL in 54s
82 actionable tasks
```

GitHub CI remains the authority for the complete repository and persistent
runtime package with Docker.

## Boundary conclusion

The inventory path can now distinguish selected evidence from organizationally
authorized evidence without selecting a canonical current value. Freshness and
health remain the next separate Trust dependencies.
