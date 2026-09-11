# TASK-0160 — Systemic Divergence Detection

## Evidence

- ADR-0062/SPEC-0062 define explicit policy, bounded window, deterministic
  grouping and explainable case references.
- `DeterministicSystemicDivergenceDetector` consumes only durable cases,
  preserves missing values, and returns created/unchanged/revised/fail-closed
  results without mutating cases or lower-layer truth.
- PostgreSQL V025 provides organization-scoped identity, revision guard,
  restart-safe case references and pagination indexes.
- Authenticated read-only systemic-divergence list/detail endpoints and a
  Decision Room panel expose policy, stage, count, window and observed impact.

TASK-0160 authorizes systemic detection, derived persistence and read-only
visibility only. It does not authorize recovery, claims, refunds, disputes,
settlement/payment/marketplace mutation, provider calls, autonomous action or
any recoverable-amount assertion.
