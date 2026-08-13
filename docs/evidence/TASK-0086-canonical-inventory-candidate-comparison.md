# TASK-0086 — Canonical inventory candidate comparison

## Result

Implemented the pure comparison boundary authorized by SPEC-0018. One validated
V012 snapshot can now be classified as single evidence, measure mismatch, exact
rational agreement, or exact rational divergence without choosing or mutating
anything.

## Delivered

- Added `applications:inventory-candidate-comparison` with only the authorized
  candidate-snapshot dependency.
- Added defensive snapshot-scope, uniqueness, count, and canonical-order checks.
- Added controlled redacted results and exact signed-rational tests.

## Boundary confirmation

No persistence, migration, Kernel, runtime, API, provider, authority, freshness,
tolerance, reconciliation, aggregation, rounding, business stock, recommendation,
event, or action was introduced.
