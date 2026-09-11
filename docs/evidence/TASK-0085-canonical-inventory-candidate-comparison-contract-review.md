# TASK-0085 — Canonical inventory candidate comparison contract review

## Decision

ADR-0018 and SPEC-0018 define the next smallest boundary after V012: a pure,
derived comparison that distinguishes single evidence, incompatible measures,
exact rational agreement, and exact rational divergence.

## Essence preserved

- V008 remains the exact quantity ledger.
- V012 remains the immutable candidate-set provenance boundary.
- Comparison does not select, rank, aggregate, round, or mutate.
- No source authority, freshness, tolerance, business-stock, or economic policy
  is inferred.
- No Kernel, runtime, API, persistence, connector, or provider change is allowed.

## Authorization

Acceptance authorizes TASK-0086 only: the pure
`applications:inventory-candidate-comparison` module and deterministic tests.
