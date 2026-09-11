# SPEC-0061: Governed Live Reconciliation Case Orchestration

Status: Accepted

Source decision: ADR-0061

## Contract

`AcceptedFinancialReconciliationAssessment` is the only input contract. It is
created explicitly with an assessment and caller-supplied microsecond timestamp.
The orchestrator validates the expected organization, derives the existing
organization + trace + policy identity, invokes `DurableReconciliationCaseProcessor`,
and saves only a new or materially changed case.

Results are `Created`, `Revised`, `Unchanged`, `NotEligible`, `Rejected`, or a
categorized fail-closed `Failed` result. Replaying after a persistence failure
retries the same deterministic identity and revision guard.

## Boundaries

Assessment != Economic Truth != financial evidence != reconciliation case !=
recovery authority. Missing remains missing and observed zero remains zero.
Ledger/evidence are read-only to this seam. No provider, scheduler, recovery,
refund, claim, settlement, payment or marketplace mutation is authorized.
