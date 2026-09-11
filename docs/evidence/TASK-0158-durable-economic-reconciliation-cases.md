# TASK-0158 — Durable Economic Reconciliation Cases

## Evidence

- ADR-0060 and SPEC-0060 authorize the incremental case boundary.
- `DurableReconciliationCaseProcessor` creates cases only for deterministic
  `DIVERGENCE` assessments and derives a stable organization/trace/policy ID.
- PostgreSQL migration V024 stores cases, stage details and evidence references
  with organization-scoped uniqueness, indexes and revision-guarded upsert.
- Authenticated API reads derive organization scope from the service principal;
  request payloads contain no organization or connection identifier.
- Decision Room labels `Divergência detectada`, `Resolvido`, and the explicit
  `Evidence → reconciliation → case` chain. Recovery is described only as a
  future capability and has no action control.

## Boundary

Economic Truth and ledger evidence remain canonical and immutable. This task
does not wire live case creation, acknowledge/resolve writes, recovery,
refunds, claims, marketplace/payment mutation, or autonomous action. The next
orchestration task must connect accepted reconciliation results to the processor
under an explicitly governed ingestion seam.
