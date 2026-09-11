# TASK-0159 — Governed Live Reconciliation Case Orchestration

## Evidence

- ADR-0061/SPEC-0061 authorize only the explicit accepted-assessment seam.
- `AcceptedFinancialReconciliationAssessment` prevents raw assessments from
  entering orchestration.
- `GovernedReconciliationCaseOrchestrator` validates organization and result,
  delegates economic semantics to `DurableReconciliationCaseProcessor`, and
  persists only created/materially revised cases.
- Replay is deterministic and persistence failure is categorized fail-closed;
  the existing organization/trace/policy identity and revision guard remain in
  force.
- Runtime composition uses the existing Postgres case repository and read API.
  No HTTP request supplies organization/connection identifiers.

## Boundary

Economic Truth, ledger and evidence remain canonical and immutable. The case is
derived institutional memory only. TASK-0159 activates governed productive case
creation, but does not authorize recovery, refund, claim, settlement mutation,
marketplace/payment mutation or autonomous action.
